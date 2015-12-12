(ns kvlt.chan
  (:require [#? (:clj  clojure.core.async
                 :cljs cljs.core.async) :as async]
            [kvlt.core :as kvlt]
            [promesa.core :as p]))

(defn- promise->chan [p chan close?]
  (let [chan (or chan (async/chan))
        done (fn [x]
               (async/put! chan x)
               (when close?
                 (async/close! chan)))]
    (p/branch p done done)
    chan))

(defn request!
  "Channeled version of [[kvlt.core/request!]].  Behaviour is identical, however
  the response (or error) will be placed on the returned channel."
  [req & [{:keys [chan close?] :or {close? true}}]]
  (promise->chan (kvlt/request! req) chan close?))

(defn websocket!
  "Channeled version of [[kvlt.core/websocket!]].  Behaviour is identical,
  however the initial deferred value is represented as a channel on which the
  eventual websocket communication channel will be placed."
  [url & [ws-opts {:keys [chan close?] :or {close? true}}]]
  (promise->chan (kvlt/websocket! url ws-opts) chan close?))
