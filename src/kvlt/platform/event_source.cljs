(ns ^:no-doc kvlt.platform.event-source
  (:require [kvlt.event-source :refer [format-event]]
            [cljs.core.async :as async]
            [taoensso.timbre :as log]
            [kvlt.util :as util]))

(def EventSource
  (if (exists? js/EventSource)
    js/EventSource
    (js/require "eventsource")))

(defn event->map [e format]
  (format-event
   format
   {:id   (not-empty (.. e -lastEventId))
    :data (.. e -data)
    :type (keyword (.. e -type))}))

(defn add-listeners! [source chan types format]
  (doseq [t types]
    (.addEventListener
     source
     (name t)
     (fn [e]
       (when-not (async/put! chan (event->map e format))
         (.close source))))))

(defn request!
  [url & [{:keys [events format chan close?]
           :or {events #{:message} format :string close? true}}]]
  (let [chan   (or chan (async/chan))
        source (EventSource. url)]
    (add-listeners! source chan events format)
    (set! (.. source -onerror)
          (fn [_]
            (log/warn "SSE error, closing source" url)
            (.close source)
            (when close?
              (async/close! chan))))
    (util/read-proxy-chan chan #(.close source) {:close? close?})))
