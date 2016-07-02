(ns ^:no-doc kvlt.platform.http
  (:require [cljs.core.async :as async]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [kvlt.util :as util]
            [promesa.core :as p]
            [kvlt.platform.xhr :as xhr]
            [kvlt.middleware.util :refer [charset]]))
(defn ->response [req m]
  (vary-meta m assoc :kvlt/request req))

(defn error->map [e]
  (let [code (or (keyword (.. e -code)) :unknown)]
    {:type    code
     :error   code
     :message (.. e -message)
     :status  0}))

(defn- compose-url [{:keys [query-string server-port] :as req}]
  (str (name (req :scheme))
       "://"
       (req :server-name)
       (when server-port
         (str ":" server-port))
       (req :uri)
       (when query-string
         (str "?" query-string))))

(defn req->node [{:keys [body kvlt.platform/timeout kvlt.platform/insecure?] :as req}]
  (cond->
      {:uri      (compose-url req)
       :method   (-> req :request-method name str/upper-case)
       :headers  (req :headers)
       :encoding nil
       :gzip     true}
    body      (assoc :body body)
    timeout   (assoc :timeout timeout)
    insecure? (assoc :rejectUnauthorized false)))

(defn- maybe-encode [buffer as headers]
  (if (= as :byte-array)
    buffer
    (let [cs (-> headers :content-type charset)]
      (.toString buffer cs))))

(when (= *target* "nodejs")
  (let [request! (js/require "request")]
    (defn request-node! [req]
      (p/promise
       (fn [resolve _]
         (let [respond (comp resolve #(->response req %))]
           (request!
            (clj->js (req->node req))
            (fn [error node-resp buffer]
              (if error
                (respond (error->map error))
                (let [headers (js->clj (.. node-resp -headers) :keywordize-keys true)
                      resp    {:headers headers
                               :status  (.. node-resp -statusCode)
                               :body    (maybe-encode buffer (req :as) headers)}]
                  (log/debug "Received response\n" (util/pprint-str resp))
                  (respond resp)))))))))))

(defn request! [req]
  (log/debug "Issuing request\n" (util/pprint-str req))
  (if (= *target* "nodejs")
    (request-node! req)
    (xhr/request! req)))
