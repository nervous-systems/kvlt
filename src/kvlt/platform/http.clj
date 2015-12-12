(ns ^:no-doc kvlt.platform.http
  (:require
   [kvlt.middleware :as mw]
   [clojure.string :as str]
   [aleph.http :as http]
   [aleph.http.client-middleware]
   [manifold.deferred :as deferred]
   [byte-streams]
   [promesa.core :as p]
   [clojure.core.async :as async]
   [taoensso.timbre :as log]))

(defn- handle-response [m req]
  (-> m
      (update :body byte-streams/to-byte-array)
      (update :headers (partial into {}))
      (vary-meta assoc :kvlt/request req)))

(defn exception->keyword [^Class class]
  (-> class .getSimpleName (str/replace #"Exception$" "")
      (->> (re-seq #"[A-Z]+[^A-Z]*")
           (map str/lower-case)
           (str/join "-")
           keyword)))

(defn exception->map [e]
  (if-let [{:keys [status] :as data} (ex-data e)]
    (assoc data :type status :error status)
    (let [{:keys [class message] :as data} (bean e)
          type (exception->keyword class)]
      (assoc data
        :status 0
        :type   :http-error
        :error  :http-error
        :kvlt.platform/error type))))

(defn required-middleware [client]
  #(client (aleph.http.client-middleware/decorate-url %)))

(def ^:private boring-connection-pool
  (http/connection-pool {:middleware required-middleware}))

(defn default-request [{:keys [server-name server-port] :as req} & [pool]]
  (merge {:pool (or pool boring-connection-pool)
          :host server-name
          :port server-port} req))

(defn request! [req]
  (let [req (default-request req)]
    (p/promise
     (fn [resolve reject]
       (try
         (deferred/on-realized
          (http/request req)
          #(resolve (handle-response % req))
           (comp resolve exception->map))
         (catch Exception e
           (resolve (exception->map e))))))))
