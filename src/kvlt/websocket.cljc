(ns kvlt.websocket
  (:require [#?(:clj clojure.edn :cljs cljs.reader) :as edn]
            [kvlt.platform.util :as util]))

(defmulti format-outgoing
  "Transform outgoing websocket messages.

  Symbolic format name + arbitrary message -> String"
  (fn [format msg] format))
(defmethod format-outgoing :default [_ x] x)
(defmethod format-outgoing :edn  [_ x] (pr-str x))
(defmethod format-outgoing :json [_ x] (util/encode-json x))

(defmulti format-incoming
  "Transform incoming websocket messages.

  Symbolic format name + string -> arbitrary message"
  (fn [format msg] format))

(defmethod format-incoming :default [_ x] x)
(defmethod format-incoming :edn     [_ x] (edn/read-string x))
(defmethod format-incoming :json    [_ x] (util/parse-json x))
