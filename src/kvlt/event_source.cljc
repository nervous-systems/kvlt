(ns kvlt.event-source
  (:require [#?(:clj clojure.edn :cljs cljs.reader) :as edn]
            [kvlt.platform.util :refer [parse-json]]))

(defmulti format-event
  "Dispatching on the (first) `format` param (corresponding
  to [[kvlt.core/event-source!]]'s `:format` param), transform an incoming event
  prior to placement on the source's channel.

  Implementations receive (and are expected to return the event map) not only
  its body."
  (fn [format event] format))

(defmethod format-event :default [_ e] e)
(defmethod format-event :edn     [_ e] (edn/read-string e))
(defmethod format-event :json    [_ e] (parse-json e))
