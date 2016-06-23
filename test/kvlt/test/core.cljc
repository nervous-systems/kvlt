(ns kvlt.test.core
  (:require
   [#? (:clj clojure.edn :cljs cljs.reader) :as edn]
   #? (:clj  [clojure.test :refer [is]]
       :cljs [cljs.test :refer-macros [is]])
   [kvlt.core :as kvlt]
   [clojure.walk :refer [keywordize-keys]]
   [kvlt.test.util :as util
    #?(:clj :refer :cljs :refer-macros) [deftest is=]]
   [cats.core :as m]
   [#? (:clj
        clojure.core.async
        :cljs
        cljs.core.async) :as async :refer [<! >! #? (:clj go)]]
   [promesa.core :as p]))

(deftest error-middleware-cooperates
  (util/with-result
    (p/branch (kvlt/request!
               {:url (str "http://localhost:" util/local-port "/echo")
                :query-params {:status 400}})
      (constantly nil)
      ex-data)
    (fn [{:keys [status headers]}]
      (is= 400 status)
      (is (some keyword? (keys headers))))))

(defn edn-req [m]
  (util/with-result
    (kvlt/request!
     (merge
      {:url (str "http://localhost:" util/local-port "/echo")
       :method :post
       :accept-encoding "gzip"
       :content-type :edn
       :form {:hello 'world}}
      m))
    (fn [{:keys [status reason headers body] :as resp}]
      (is= 200 status)
      (is= :ok reason)
      (is= "application/edn" (headers :content-type))
      (is= {:hello 'world} (:body body)))))

(deftest edn-as-edn
  (edn-req {:as :edn}))
(deftest edn-as-auto
  (edn-req {:as :auto}))

(defn un-byte-array [x]
  #? (:clj  (map identity x)
      :cljs (if (= *target* "nodejs")
              (for [i (range (.. x -length))]
                (.readInt8 x i))
              (let [x (js/Int8Array. x)]
                (for [i (range (.. x -length))]
                  (aget x i))))))

(def hexagram-bytes [-2 -1 -40 52 -33 6])
(def hexagram-byte-array
  #? (:clj  (byte-array hexagram-bytes)
      :cljs (if (= *target* "nodejs")
              (js/Buffer.    (clj->js hexagram-bytes))
              (js/Int8Array. (clj->js hexagram-bytes)))))

(def byte-req
  {:url (str "http://localhost:" util/local-port "/echo/body?encoding=UTF-16")
   :method :post
   :content-type "text/plain"
   :character-encoding "UTF-16"
   :body hexagram-byte-array})

(deftest ^{:kvlt/skip #{:phantom}} bytes->bytes
  (util/with-result
    (kvlt/request! (assoc byte-req :as :byte-array))
    (fn [{:keys [body] :as resp}]
      (is= (un-byte-array body) hexagram-bytes))))

(deftest jumbled-middleware
  (util/with-result
    (kvlt/request!
     {:headers    {"X-HI" "OK" :x-garbage "text/"}
      :url        (str "http://localhost:" util/local-port "/echo")
      :accept     :text/plain
      :basic-auth ["moe@nervous.io" "TOP_SECRET"]
      :query      {:Q :two}
      :as         :auto})
    (fn [{:keys [body] :as resp}]
      (let [{:keys [headers] :as req} (keywordize-keys body)]
        (is (headers :authorization))
        (is= "OK" (headers :x-hi))
        (is= "text/plain" (headers :accept))
        (is= {:Q ":two"}  (req :query-params))))))

#? (:clj
    (deftest deflate
      (util/with-result
        (kvlt/request!
         {:url (str "http://localhost:" util/local-port "/echo")
          :accept-encoding :deflate
          :body   "Hello"
          :type   :edn
          :method :post
          :as     :edn})
        (fn [{{:keys [headers body]} :body}]
          (is= "deflate" (headers "accept-encoding"))
          (is= 'Hello body)))))

(defn json-req []
  (kvlt/request!
   {:url    (str "http://localhost:" util/local-port "/echo/body")
    :method :post
    :body   "{\"x\": 1}"
    :as     :json}))

#? (:clj
    (deftest json-without
      (is (try
            @(json-req)
            nil
            (catch Exception e
              true)))))

#? (:clj
    (deftest json-with
      (with-redefs [kvlt.platform.util/parse-json (constantly {:x 1})]
        (is= {:x 1} (:body @(json-req)))))
    :cljs
    (deftest json-with
      (util/with-result (json-req)
        (fn [{:keys [body]}]
          (is= {:x 1} body)))))

(defn responder [resp]
  (reduce
   #(%2 %1)
   (fn [req]
     (p/resolved
      (with-meta resp {:kvlt/request req})))
   kvlt/default-middleware))

(defmethod kvlt.middleware/as-type :xxx [_]
  (throw #? (:clj (Exception. "LOL JVM") :cljs (js/Error. "OK JS"))))

(deftest parse-error-preserves-existing
  (let [request! (responder {:status 400 :body "..." :error :http-error})]
    (util/with-result
      (p/branch
        (request! {:url "http://localhost"
                   :as  :xxx})
        (constantly nil)
        ex-data)
      (fn [{e :error}]
        (is (= e :http-error))))))

(deftest parse-error
  (let [request! (responder {:status 200 :body "..."})]
    (util/with-result
      (p/branch
        (request! {:url "http://localhost"
                   :as  :xxx})
        (constantly nil)
        ex-data)
      (fn [{e :error}]
        (is (= e :middleware-error))))))
