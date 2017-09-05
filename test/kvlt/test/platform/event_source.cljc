(ns kvlt.test.platform.event-source
  (:require #? (:clj  [clojure.test :refer [is]]
                :cljs [cljs.test :refer-macros [is]])
            [kvlt.platform.event-source :as event-source]
            [kvlt.test.util :as util
             #?(:clj :refer :cljs :refer-macros) [deftest is= after->]]
            #? (:clj  [clojure.core.async :as async]
                :cljs [cljs.core.async :as async :refer [>! <!]]))
  #? (:cljs (:require-macros [cljs.core.async.macros :refer [go]])))

(def local-url (str "http://localhost:" 5000 "/events"))

(deftest ^{:kvlt/skip #{:phantom}} message-events
  (util/with-result
    (util/channel-promise (event-source/request! local-url))
    (fn [{:keys [data id] :as m}]
      (is= "A bunch of\n events " data)
      (is (nil? id)))))

(deftest ^{:kvlt/skip #{:phantom :browser}} header-events
  (util/with-result
    (util/channel-promise (event-source/request! local-url
                                                 {:events #{:header}
                                                  :options {:headers {"x-kvlt-test" "ok"}}}))
    (fn [{:keys [data id] :as m}]
      (is= "ok" data)
      (is (nil? id)))))

(deftest ^{:kvlt/skip #{:phantom}} odd-events
  (util/with-result
    (util/channel-promise
     (async/into []
       (event-source/request!
        local-url
        {:events #{:odd}
         :chan (async/chan
                1 (comp (take 5)
                        (map #(select-keys % #{:id :data}))))})))
    (fn [results]
      (is= 5 (count results))
      (is= (for [i (range 1 10 2)] {:id (str i) :data (pr-str {:index i})})
           results))))

(deftest ^{:kvlt/skip #{:phantom}} redirect
  (after->
    (util/channel-promise
     (event-source/request!
      (util/local-url "redirect?status=301&location=http://localhost:5000/events")
      {:events #{:even}}))
    :id
    (is= "0")))
