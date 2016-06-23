(ns kvlt.test.platform.http
  (:require [#? (:clj clojure.edn :cljs cljs.reader) :as edn]
            #? (:clj  [clojure.test :refer [is]]
                :cljs [cljs.test :refer-macros [is]])
            [clojure.string :as str]
            [kvlt.platform.http :as http]
            [kvlt.platform.util :as platform.util]
            [kvlt.test.util :as util #?(:clj :refer :cljs :refer-macros) [deftest after-> is=]]
            [cats.core :as m]
            [#? (:clj
                 clojure.core.async
                 :cljs
                 cljs.core.async) :as async :refer [<! >!]]
            [promesa.core :as p]
            #? (:clj [manifold.stream])))

(defn url [& [m]]
  (merge {:scheme :http
          :server-name "localhost"
          :server-port util/local-port} m))

(defn body [m]
  (-> m :body (platform.util/byte-array->str "UTF-8")))

(defn throw-error [{:keys [error message] :as m}]
  (when error
    (throw (ex-info message m)))
  m)

(defn echo! [& [req]]
  (after-> (http/request! (merge {:request-method :get} (url {:uri "/echo"}) req))
    throw-error
    body
    edn/read-string))

(defn echo-header! [h v]
  (after-> (echo! {:headers {h v}}) :headers (get h)))

(deftest headers
  (after-> (echo-header! "x-greeting" "Hello")
    (is= "Hello")))

(deftest post
  (after-> (echo!
            {:request-method :post
             :headers {"content-type" "application/edn"}
             :body "[:html]"})
    :body
    (is= [:html])))

(deftest server-error
  (after-> (http/request!
            (assoc (url {:uri "/echo"})
              :request-method :get
              :query-string "status=500"))
    throw-error
    :status
    (is= 500)))

(deftest ^{:kvlt/skip #{:phantom}} redirect
  (after-> (http/request!
            (assoc (url {:uri "/redirect"})
              :request-method :get
              :query-string (str "status=302&location=" (util/local-url "ok"))))
    throw-error
    body
    (is= "OK")))

(deftest streamed
  (after-> (http/request!
            (assoc (url {:uri "/numbers"})
              :request-method :get :query-string "cnt=10"))
    throw-error
    body
    (is= (str (str/join "\n" (range 10)) "\n"))))

(deftest http-error
  (util/is-http-error
   (http/request!
    (assoc (url {:server-name "rofl"})
      :request-method :get
      :kvlt.platform/timeout 2000))))
