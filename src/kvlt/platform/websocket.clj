(ns ^:no-doc kvlt.platform.websocket
  (:require [aleph.http :as http]
            [clojure.core.async :as async]
            [kvlt.platform.util :refer [exception->map]]
            [kvlt.util :as util]
            [kvlt.websocket :refer [format-incoming format-outgoing]]
            [manifold.deferred :as deferred]
            [manifold.stream :as s]
            [promesa.core :as p]))

(defn- connect-chans [stream r w format close?]
  (s/connect-via w #(s/put! stream (format-outgoing format %)) stream)
  (s/connect
   (s/map #(format-incoming format %) stream)
   r
   {:downstream? close?}))

(defn request! [url & [{:keys [read-chan write-chan close? format] :or {close? true}}]]
  (let [read  (or read-chan  (async/chan))
        write (or write-chan (async/chan))]
    (p/promise
     (fn [resolve reject]
       (deferred/on-realized
         (http/websocket-client url)
         (fn [stream]
           (let [chan (util/bidirectional-chan
                       read write
                       {:on-close #(manifold.stream/close! stream)
                        :close? close?})]
             (connect-chans stream read write format close?)
             (-> chan
                 (vary-meta assoc :kvlt.platform/stream stream)
                 resolve)))
         (fn [e]
           (let [{:keys [message] :as e} (exception->map e)]
             (reject (ex-info message e)))))))))
