(ns kvlt.test.middleware.util
  (:require
   [cats.core :as m]
   [cats.monad.identity :as monad.identity]))

(defn mw-req [mw & kvs]
  (let [req (if kvs (apply assoc nil kvs) {})]
    (m/extract
     ((mw (fn [in]
            (monad.identity/identity (vary-meta in assoc :kvlt/request in)))) req))))
