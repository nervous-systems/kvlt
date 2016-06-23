(ns ^:no-doc kvlt.platform.util
  (:require [clojure.walk :as walk]))

(defn exception->map [e & [hints]]
  (merge {:message (.. e -message)
          :type    :http-error
          :error   :http-error} hints))

;; These functions oughtn't be invoked currently - accept-encoding
;; isn't ever set on Node, and in the browser, the response will be
;; silently decompressed.
(defn gunzip  [s] s)
(defn inflate [s] s)

(defn byte-array? [x]
  (or (and (exists? js/ArrayBuffer) (= (type x) js/ArrayBuffer))
      (and (exists? js/Buffer) (= (type x) js/Buffer))))

(defn byte-array->str [ba encoding]
  (if (and (exists? js/Buffer) (= (type ba) js/Buffer))
    (.toString ba encoding)
    ba))

(defn parse-json [s]
  (walk/keywordize-keys (js->clj (.parse js/JSON s))))

(defn encode-json [x]
  (.stringify js/JSON (clj->js x)))
