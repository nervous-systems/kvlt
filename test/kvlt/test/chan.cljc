(ns kvlt.test.chan
  (:require [kvlt.chan :as chan]
            #? (:clj  [clojure.core.async :as async :refer [go]])
            #? (:cljs [cljs.core.async :as async])
            [kvlt.test.util :as util #?(:clj :refer :cljs :refer-macros) [deftest is=]])
  #? (:cljs (:require-macros [cljs.core.async.macros :refer [go]])))

(deftest request!
  (let [chan (async/chan 1 (map (fn [{{x :body} :body}] [x "World"])))]
    (util/with-result
      (util/channel-promise
       (chan/request!
        {:url (str "http://localhost:" util/local-port "/echo")
         :body "Hello"
         :as :edn}
        {:chan chan}))
      #(is= ['Hello "World"] %))))

(deftest websocket!
  (util/with-result
    (util/channel-promise
     (chan/websocket!
      (str "ws://localhost:" util/local-port "/ws-echo")))
    (fn [ch]
      (util/channel-promise
       (go
         (async/>! ch "OK")
         (is= "OK" (async/<! ch)))))))
