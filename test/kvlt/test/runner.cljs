(ns kvlt.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [kvlt.test.core]
            [kvlt.test.middleware]
            [kvlt.test.middleware.params]
            [kvlt.test.platform.http]
            [taoensso.timbre :as log]))

(when (= "nodejs" *target*)
  (try
    (.install (js/require "source-map-support"))
    (catch :default e
      (log/error e "No source map support"))))

;; This is to hack around a combination of issues in Doo and xhr2
(set! (.. js/console -warn)  (.. js/console -log))
(set! (.. js/console -error) (.. js/console -log))

(doo-tests
 'kvlt.test.core
 'kvlt.test.middleware
 'kvlt.test.middleware.params
 'kvlt.test.platform.http)
