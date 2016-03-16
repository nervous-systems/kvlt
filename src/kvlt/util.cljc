(ns ^:no-doc kvlt.util
  (:require
   [clojure.string :as str]
   #? (:clj  [clojure.core.async.impl.protocols :as p]
       :cljs [cljs.core.async.impl.protocols :as p])
   #? (:clj  [clojure.pprint :as pprint]
       :cljs [cljs.pprint :as pprint]))
  #? (:cljs (:require-macros [kvlt.util])))

(defn map-keys [f m]
  (into {} (for [[k v] m] [(f k) v])))

(defn map-vals [f m]
  (into {} (for [[k v] m] [k (f v)])))

;; Taken from Plumbing
(let [+none+ ::none]
  (defn update-when [m key f & args]
    (let [found (m key +none+)]
      (if-not (identical? +none+ found)
        (assoc m key (apply f found args))
        m))))

;; Taken from Chord, more or less
(defn bidirectional-chan
  [read-ch write-ch & [{:keys [on-close close?] :or {close? true}}]]
  (reify
    p/ReadPort
    (take! [_ handler]
      (p/take! read-ch handler))

    p/WritePort
    (put! [_ msg handler]
      (p/put! write-ch msg handler))

    p/Channel
    (close! [_]
      (when close?
        (p/close! read-ch)
        (p/close! write-ch))
      (when on-close
        (on-close)))))

(defn read-proxy-chan [read-ch on-close & [{:keys [close?] :or {close? true}}]]
  (reify
    p/ReadPort
    (take! [_ handler]
      (p/take! read-ch handler))

    p/Channel
    (close! [_]
      (on-close)
      (when close?
        (p/close! read-ch)))))

(defn pprint-str [x]
  (str/trimr (with-out-str (pprint/pprint x))))

(defn doc-examples! [vvar examples]
  (alter-meta!
   vvar update :doc str
   "\n\n```clojure\n"
   (str/join
    "\n\n"
    (for [[before after] examples]
      (cond-> (pprint-str before)
        after (str "\n  =>\n" (pprint-str after)))))
   "\n```"))

#? (:clj
    (defmacro fn-when [[binding] & body]
      `(fn [~binding]
         (when ~binding
           ~@body))))

#? (:clj
    (defmacro with-doc-examples! [vvar & examples]
      `(doc-examples! #'~vvar (quote ~examples))))
