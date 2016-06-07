(ns ^:no-doc kvlt.platform.util
  (:require [byte-streams]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayInputStream BufferedInputStream]
           [java.util.zip DeflaterInputStream GZIPInputStream InflaterInputStream]))

(defn- exception->keyword [^Class class]
  (let [t (-> class .getSimpleName (str/replace #"Exception$" "")
              (->> (re-seq #"[A-Z]+[^A-Z]*")
                   (map str/lower-case)
                   (str/join "-")))]
    (or (not-empty t) :generic)))

(defn- unwrap-exception [e]
  (if-let [{:keys [status] :as data} (ex-data e)]
    {:type   status
     :error  status
     :status status
     :kvlt.platform/error e}
    (let [{:keys [class message]} (bean e)
          type (exception->keyword class)]
      {:status 0
       :type   :http-error
       :error  :http-error
       :kvlt.platform/error type})))

(defn exception->map [e & [hints]]
  (merge (unwrap-exception e) hints))

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
