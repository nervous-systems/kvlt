(ns ^:no-doc kvlt.platform.websocket
  (:require [cljs.core.async :as async]
            [kvlt.util :as util]
            [kvlt.websocket :refer [format-outgoing format-incoming]]
            [taoensso.timbre :as log]
            [promesa.core :as p])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; delay the require so that code running in browsers without
;; WebSocket will fail only if it actually tries to use it.
(def websocket-node (delay (.. (js/require "websocket") -w3cwebsocket)))

(defn websocket [url]
  (if (exists? js/WebSocket)
    (js/WebSocket. url)
    (try
      (let [ws @websocket-node]
        (ws. url))
      (catch js/Error e
        (log/error "WebSocket is not available")
        (throw e)))))

(defn- ws->chan! [ws chan format]
  (set! (.. ws -onmessage) #(async/put! chan (format-incoming format (.. % -data)))))

(defn- chan->ws! [chan ws format]
  (go
    (loop []
      (when-let [msg (<! chan)]
        (.send ws (format-outgoing format msg))
        (recur)))))

(defn- close-event->maybe-error [ev]
  (when-not (.. ev -wasClean)
    (let [reason (.. ev -reason)
          code   (.. ev -code)]
      (ex-info reason {:message reason :error code :type code :status 0}))))

(defn request! [url & [{:keys [read-chan format write-chan close?] :or {close? true}}]]
  (let [ws   (websocket url)
        in   (or read-chan  (async/chan))
        out  (or write-chan (async/chan))
        chan (util/bidirectional-chan in out {:on-close #(.close ws) :close? close?})
        resolved? (atom false)]
    (p/promise
     (fn [resolve reject]
       (ws->chan!  ws in format)
       (chan->ws! out ws format)

       (set! (.. ws -onopen)
             (fn []
               (reset! resolved? true)
               (resolve chan)))

       (set! (.. ws -onclose)
             (fn [event]
               (when-let [error (close-event->maybe-error event)]
                 (log/error "Websocket onclose error" error)
                 (when-not @resolved?
                   (reject error))
                 (async/close! chan))))))))
