(ns kvlt.test.middleware
  (:require
   #? (:clj  [clojure.test :refer [is deftest]]
       :cljs [cljs.test :refer-macros [is deftest]])
   [kvlt.test.middleware.util :refer [mw-req]]
   [kvlt.middleware.util :as mw.util]
   [kvlt.middleware :as mw :refer [header]]
   [kvlt.test.util :as util #?(:clj :refer :cljs :refer-macros) [is=]]
   [promesa.core :as p]
   #? (:clj [kvlt.test.server]))
  #? (:clj (:import [org.apache.commons.codec.binary Base64])))

(defn base64-decode [x]
  #? (:clj
      (String. (Base64/decodeBase64 x))))

(def url->parsed
  {(str "http://www.amazon.com/Songs-Dreamer-Grimscribe-Thomas-Ligotti/"
        "dp/0143107763/ref=sr_1_1?s=books&ie=UTF8&qid=1449962054&sr=1-1&"
        "keywords=ligotti")
   {:scheme :http,
    :server-name "www.amazon.com",
    :server-port nil,
    :uri "/Songs-Dreamer-Grimscribe-Thomas-Ligotti/dp/0143107763/ref=sr_1_1",
    :user-info nil,
    :query-string "s=books&ie=UTF8&qid=1449962054&sr=1-1&keywords=ligotti"}

   "https://カタ:ro@www.urgh/_?x=&x=&x=y%20%26=lol"
   {:scheme :https,
    :server-name "www.urgh",
    :server-port nil,
    :uri "/_",
    :user-info "カタ:ro",
    :query-string "x=&x=&x=y%20%26=lol"}

   "ftp://localhost:9"
   {:scheme :ftp,
    :server-name "localhost",
    :server-port 9,
    :uri "",
    :user-info nil, :query-string nil}})

(deftest url-parsing
  (doseq [[in out] url->parsed]
    (is= out (mw.util/parse-url in))))

(deftest url
  (doseq [[in out] url->parsed]
    (is= out (mw-req mw/url :url in))))

(deftest method
  (is= :put (:request-method (mw-req mw/method :method :put)))
  (is= :put (:request-method (mw-req mw/method :request-method :put))))

(deftest keyword-headers
  (is= {:x-revolting "yes"
        :x-dangerous "medium"} (:headers
                                (mw-req mw/keyword-headers
                                        :headers
                                        {"x-revolting" "yes"
                                         "x-dangerous" "medium"})))
  (is= {:x-revolting "yes"
        :x-dangerous "medium"} (:headers
                                (mw-req mw/keyword-headers
                                        :headers
                                        {:x-revolting "yes"
                                         :x-dangerous "medium"})))
  (is= {"x-revolting" "yes"
        "x-dangerous" "medium"}
       (-> (mw-req mw/keyword-headers
                   :headers
                   {"x-revolting" "yes"
                    "x-dangerous" "medium"})
           meta
           :kvlt/request
           :headers))
  (is= {"x-revolting" "yes"
        "x-dangerous" "medium"}
       (-> (mw-req mw/keyword-headers
                   :headers
                   {:x-revolting "yes"
                    :x-dangerous "medium"})
           meta
           :kvlt/request
           :headers)))

(def input-headers (comp :headers :kvlt/request meta))

(deftest accept
  (let [accept #(-> (mw-req mw/accept :accept %) (header :accept))]
    (is= "application/edn" (accept :edn))
    (is= "application/edn" (accept :application/edn))
    (is= "text/plain"      (accept "text/plain"))
    (is (not (contains? ((mw-req mw/accept) :headers) :accept)))))

(deftest accept-encoding
  (let [encoding #(-> (mw-req mw/accept-encoding :accept-encoding %)
                      (header :accept-encoding))]
    (is (nil? (encoding nil)))
    (is (= "gzip" (encoding :gzip)))
    (is (= "gzip" (encoding [:gzip])))
    (is (= "deflate, gzip" (encoding [:deflate "gzip"])))))

(deftest lower-case-headers
  (is= {"x-ok" "Yes"}
       (:headers (mw-req mw/lower-case-headers :headers {"X-Ok" "Yes"})))
  (is= {"x-ok" "Yes"}
       (-> (mw-req mw/lower-case-headers :headers {"X-Ok" "Yes"})
           input-headers)))

(defmethod mw/from-content-type :application/rtcw [{:keys [body] :as req}]
  (assoc req :body (str "Let's play RTCW, " body)))

(deftest default-content-type
  (is= :text/plain (-> (mw-req mw/default-content-type)
                       :content-type)))

(deftest content-type
  (is= "text/html"
       (-> (mw-req mw/content-type :content-type "text/html")
           (header :content-type)))
  (is= "text/html; charset=US-ASCII"
       (-> (mw-req mw/content-type
                   :content-type "text/html"
                   :character-encoding "US-ASCII")
           (header :content-type))))

(deftest basic-auth
  (let [[u p]    ["basic-auth-user" "rofl"]
        expected (str "Basic " (mw.util/string->base64 (str u ":" p)))]
    (is= expected (-> (mw-req mw/basic-auth :basic-auth {:username u :password p})
                      (header :authorization)))
    (is= expected (-> (mw-req mw/basic-auth :basic-auth [u p])
                      (header :authorization)))))

(deftest oauth-token
  (let [token "TOKEN"]
    (is= (str "Bearer " token)
         (-> (mw-req mw/oauth-token :oauth-token token)
             (header :authorization)))
    (let [resp (mw-req mw/oauth-token)]
      (is (not (header resp :authorization))))))

(deftest default-method
  (is= :get  (-> (mw-req mw/default-method)
                 :method))
  (is= :post (-> (mw-req mw/default-method :method :post)
                 :method)))

(deftest error
  (let [statuses        (keys mw/status->reason)
        [unexceptional] (filter mw/unexceptional-status? statuses)
        [exceptional]   (filter (complement mw/unexceptional-status?) statuses)

        error  (ex-data (mw-req mw/error :status exceptional))
        reason (mw/status->reason exceptional)]

    (is= reason      (:reason error))
    (is= reason      (:type   error))
    (is= exceptional (:status error))

    (let [resp (mw-req mw/error :status unexceptional)]
      (is= unexceptional (:status resp))
      (is= (mw/status->reason unexceptional) (:reason resp))
      (is (nil? (:type resp))))))

#? (:clj
    (let [body    (kvlt.test.server/gzip "OMFG")
          headers {"content-encoding" "gzip"}]
      (deftest gzip
        (is= "OMFG"
             (-> (mw-req mw/decompress :body body :headers headers)
                 :body String.)))))

;; Interactions

(defn keyword+lower-case-headers* [mw]
  (let [{:keys [headers] :as resp}
        (mw-req mw :headers {:X-YY "OK" "X-YZ" "SURE"})]
    (is= {:x-yy  "OK" :x-yz  "SURE"} headers)
    (is= {"x-yy" "OK" "x-yz" "SURE"} (-> resp meta :kvlt/request :headers))))

(deftest keyword+lower-case-headers
  (keyword+lower-case-headers* (comp mw/lower-case-headers mw/keyword-headers))
  (keyword+lower-case-headers* (comp mw/keyword-headers mw/lower-case-headers)))

(deftest default-method+method
  (let [req (partial mw-req (comp mw/default-method mw/method))]
    (is (= :get (:request-method (req))))
    (is (= :put (:request-method (req :method :put))))))
