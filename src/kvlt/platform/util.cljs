(ns ^:no-doc kvlt.platform.util
  (:require [clojure.walk :as walk]))

;; These functions oughtn't be invoked currently - accept-encoding
;; isn't ever set on Node, and in the browser, the response will be
;; silently decompressed.
(defn gunzip  [s] s)
(defn inflate [s] s)

(defn byte-array? [x]
  (and js/ArrayBuffer (= (type x) js/ArrayBuffer)))

(defn parse-json [s]
  (walk/keywordize-keys (js->clj (.parse js/JSON s))))

(defn encode-json [x]
  (.serialize js/JSON (clj->js x)))
