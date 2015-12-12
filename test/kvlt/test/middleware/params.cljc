(ns kvlt.test.middleware.params
  (:require [kvlt.test.middleware.util :refer [mw-req]]
            [kvlt.middleware.params :as params]
            #? (:clj  [clojure.test :refer [deftest is]]
                :cljs [cljs.test :refer-macros [deftest is]])
            [kvlt.test.util #?(:clj :refer :cljs :refer-macros) [is=]]))

(deftest query-params
  (is (nil? (-> (mw-req params/query) :query-string)))

  (let [qp (comp
            :query-string
            (partial mw-req params/query :query-params))]

    (is= "qux=quux"  (qp {:qux "quux"}))
    (is= "qux=quux"  (qp {"qux" "quux"}))
    (is= "a=b&c=d"   (qp (into (sorted-map) {"a" "b" "c" "d"})))
    (is= "a=b&c=%3C" (qp {:c "<"} :query-string "a=b"))

    (is= "x=%E3%82%AB" (qp {:x "カ"}))
    #? (:clj
        (is= "x=%3F"
             (qp {:x "カ"} :content-type "text/plain;charset=US-ASCII")))))

(deftest form-params
  (is (nil? (:body (mw-req params/form))))

  (let [fp (comp
            #(select-keys % #{:body :content-type})
            (partial mw-req params/form
                     :request-method :post
                     :form-params))]

    (is (nil? (:body (fp {:x 1} :request-method :get))))

    (doseq [method [:post :put :patch]]
      (is (:body (fp {:x 1} :request-method method))))

    (is= {:body "qux=quux"
          :content-type "application/x-www-form-urlencoded"}
         (fp {:qux "quux"}))

    (is= {:body "{:x 1}"
          :content-type "application/edn"}
         (fp {:x 1} :content-type :edn))

    (is= "x=%E3%82%AB" (:body (fp {:x "カ"})))

    #? (:clj
        (is= "x=%3F" (:body (fp {:x "カ"}
                                :form-param-encoding "US-ASCII"))))))
