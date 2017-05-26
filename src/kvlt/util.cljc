(ns ^:no-doc kvlt.util
  (:require
   [clojure.string :as str]
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
