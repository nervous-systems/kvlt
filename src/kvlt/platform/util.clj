(ns ^:no-doc kvlt.platform.util
  (:require [byte-streams]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayInputStream BufferedInputStream]
           [java.util.zip DeflaterInputStream GZIPInputStream InflaterInputStream]))

(def json-enabled?
  (try
    (require 'cheshire.core)
    true
    (catch Throwable _ false)))

(defn gunzip "Returns a gunzip'd version of the given byte array." [b]
  (-> b ByteArrayInputStream. GZIPInputStream. byte-streams/to-byte-array))

(defn inflate [b]
  (let [s (-> b ByteArrayInputStream. BufferedInputStream.)]
    (.mark s 512)
    (let [readable?
          (try
            (.read (InflaterInputStream. s))
            true
            (catch java.util.zip.ZipException _
              false))]
      (.reset s)
      (byte-streams/to-byte-array
       (if readable?
         (InflaterInputStream. s)
         (InflaterInputStream. s (java.util.zip.Inflater. true)))))))

(defn- array-ctor->type-checker [t]
  (partial instance? (type (t []))))

(def byte-array? (array-ctor->type-checker byte-array))

(defn byte-array->str [ba encoding]
  (if (byte-array? ba)
    (String. ^"[B" ba encoding)
    ba))

(defn parse-json [s]
  {:pre [json-enabled?]}
  ((ns-resolve (symbol "cheshire.core") (symbol "parse-string")) s keyword))

(defn encode-json [x]
  {:pre [json-enabled?]}
  ((ns-resolve (symbol "cheshire.core") (symbol "generate-string")) x))
