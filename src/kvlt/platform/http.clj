(ns ^:no-doc kvlt.platform.http
  (:require
   [kvlt.middleware :as mw]
   [kvlt.util :refer [pprint-str]]
   [kvlt.platform.util :refer [exception->map]]
   [clojure.string :as str]
   [aleph.http :as http]
   [aleph.http.client-middleware]
   [manifold.deferred :as deferred]
   [byte-streams]
   [promesa.core :as p]
   [taoensso.timbre :as log]))

(defn- handle-response [m req]
  (let [m (-> m
              (update :body byte-streams/to-byte-array)
              (update :headers (partial into {}))
              (vary-meta assoc :kvlt/request req))]
    (log/debug "Received response\n"
               (pprint-str (assoc m :body "(byte array omitted)")))
    m))

(defn required-middleware [client]
  #(client (aleph.http.client-middleware/wrap-url %)))

(def ^:private insecure-connection-pool
  (http/connection-pool {:connection-options {:insecure? true}
                         :middleware         required-middleware}))

(def ^:private boring-connection-pool
  (http/connection-pool {:middleware required-middleware}))

(defn default-request [{:keys [server-name server-port] :as req} & [pool]]
  (merge {:pool (or pool
                    (req :kvlt.platform/pool)
                    (when (req :kvlt.platform/insecure?)
                      insecure-connection-pool)
                    boring-connection-pool)
          :host server-name
          :port server-port} req))

(defn request! [req]
  (log/debug "Issuing request\n" (pprint-str req))
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
