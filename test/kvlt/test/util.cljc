(ns kvlt.test.util
  (:require [promesa.core :as p]
            [cats.core]
            #? (:clj  [clojure.test :refer [is]]
                :cljs [cljs.test :refer-macros [is]])
            #? (:clj  [clojure.core.async :as async :refer [<!! go alt!]]
                :cljs [cljs.core.async :as async :refer [<!]]))
  #? (:cljs (:require-macros [kvlt.test.util :refer [is=]]
                             [cljs.core.async.macros :refer [go alt!]])))

(def local-port 5000) ;; env this
(def local-event-port 5001)
(def local-url (partial str "http://localhost:" local-port "/"))

(defn node? []
  #?(:clj  false
     :cljs (= cljs.core/*target* "nodejs")))

(defn phantom? []
  #? (:clj  false
      :cljs (try
              (.. js/window -_phantom)
              (catch :default _
                false))))

(def promise* (fnil p/promise {}))

#? (:cljs (enable-console-print!))

(defn- any-env? [envs]
  (some
   #(case %
      :phantom (phantom?)
      :node    (node?)
      :browser #? (:clj false :cljs true))
   envs))

#? (:clj
    (defmacro deftest [t-name & forms]
      (if (:ns &env)
        `(cljs.test/deftest ~t-name
           (if-let [env# (-> ~t-name var meta :kvlt/skip any-env?)]
             (println "Skipping" ~(str t-name) "on" env#)
             (cljs.test/async
              done#
              (p/branch
                (promise* (do ~@forms))
                #(done#)
                (fn [e#]
                  (println (.. e# -stack))
                  (cljs.test/is (nil? e#))
                  (done#))))))
        `(clojure.test/deftest ~t-name
           (-> (do ~@forms) promise* deref)))))

(defn channel-promise [ch]
  (p/promise
   (fn [resolve reject]
     (go
       (let [timeout (async/timeout (* 20 1000))
             result  (alt! timeout ::timeout ch ([v] v))]
         (async/close! ch)
         (if (= result ::timeout)
           (reject (ex-info "timeout" {}))
           (resolve result)))))))

(defn with-result [m f]
  #? (:clj
      (-> m p/promise deref f)
      :cljs
      (p/then (p/promise m) f)))

#? (:clj
    (defmacro after-> [m & forms]
      (if (:ns &env)
        `(cats.core/>>= ~m ~@(map (fn [form] `#(-> % ~form)) forms))
        `(-> ~m p/promise deref ~@forms))))

#? (:clj
    (defmacro is= [x y & [msg]]
      (if (:ns &env)
        `(cljs.test/is (= ~x ~y) ~msg)
        `(clojure.test/is (= ~x ~y) ~msg))))

(defn is-http-error [p]
  (with-result p
    (fn [{:keys [status type error] :as m}]
      (is= status 0)
      (is= type error :http-error)
      #? (:clj (is (m :kvlt.platform/error))))))
