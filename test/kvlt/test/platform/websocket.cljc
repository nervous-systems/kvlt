(ns kvlt.test.platform.websocket
  (:require #? (:clj  [clojure.test :refer [is]]
                :cljs [cljs.test :refer-macros [is]])
            [kvlt.platform.websocket :as websocket]
            [kvlt.test.util :as util #?(:clj :refer :cljs :refer-macros) [deftest is=]]
            [#? (:clj
                 clojure.core.async
                 :cljs
                 cljs.core.async) :as async :refer [<! >! #? (:clj go)]]
            [promesa.core :as p]
            #? (:clj [manifold.stream]))
  #? (:cljs (:require-macros [cljs.core.async.macros :refer [go]])))

(deftest websocket
  (util/with-result
    (websocket/request!
     (str "ws://localhost:" util/local-port "/ws-echo"))
    (fn [ch]
      (let [numbers (map str (range 10))]
        (util/channel-promise
         (go
           (<! (async/onto-chan ch numbers false))
           (is= numbers (<! (async/into [] (async/take 10 ch))))))))))

(deftest websocket-channels
  (let [read-chan  (async/chan)
        write-chan (async/chan)]
    (util/with-result
      (websocket/request!
       (str "ws://localhost:" util/local-port "/ws-echo")
       {:read-chan  read-chan
        :write-chan write-chan})
      (fn [ch]
        (util/channel-promise
         (go
           (>! write-chan "hello")
           (is= "hello" (<! read-chan))
           (async/close! ch)))))))

(deftest websocket-edn
  (util/with-result
    (websocket/request!
     (str "ws://localhost:" util/local-port "/ws-echo")
     {:format :edn})
    (fn [ch]
      (util/channel-promise
       (go
         (>! ch {:ok 1})
         (is= {:ok 1} (<! ch)))))))

(deftest websocket-host-error
  (util/is-http-error
   (p/branch (websocket/request! (str "ws://rofl"))
     (constantly nil)
     ex-data)))

(deftest websocket-handshake-error
  (util/is-http-error
   (p/branch
     (websocket/request!
      (str "ws://localhost:" util/local-port "/echo"))
     (constantly nil)
     ex-data)))

#? (:clj
    (deftest websocket-stream-closed
      (util/with-result
        (websocket/request!
         (str "ws://localhost:" util/local-port "/ws-echo"))
        (fn [ch]
          (let [{:keys [kvlt.platform/stream]} (meta ch)]
            (async/close! ch)
            (is (manifold.stream/closed? stream)))))))
