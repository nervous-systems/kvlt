(ns ^:no-doc kvlt.platform.xhr
  (:require [cljs.core.async :as async]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [kvlt.util :as util]
            [promesa.core :as p])
  (:import [goog.Uri]
           [goog.net XmlHttp XmlHttpFactory EventType ErrorCode XhrIo]))

(defn- tidy-http-error [{:keys [error-code error-text status] :as m}]
  (-> m
      (dissoc :error-text :error-code)
      (assoc
        :type    error-code
        :error   error-code
        :message error-text)))

(defn req->url [{:keys [scheme server-name server-port uri query-string]}]
  (str (doto (goog.Uri.)
         (.setScheme (name (or scheme :http)))
         (.setDomain server-name)
         (.setPort server-port)
         (.setPath uri)
         (.setQuery query-string true))))

(defn req->xhr
  [{:keys [kvlt.platform/credentials? timeout as]
    :or {timeout 0} :as request}]
  (let [xhr (doto (XhrIo.)
              (.setTimeoutInterval timeout)
              (.setWithCredentials credentials?))]
    (when (= as :byte-array)
      (.setResponseType xhr (.. XhrIo -ResponseType -ARRAY_BUFFER)))
    xhr))

(def code->error
  {0 :no-error
   1 :access-denied
   2 :file-not-found
   3 :ff-silent-error
   4 :custom-error
   5 :exception
   6 :http-error
   7 :abort
   8 :timeout
   9 :offline})

(defn headers->map [headers]
  (reduce
   #(let [[k v] (str/split %2 #":\s+")]
      (if (or (str/blank? k) (str/blank? v))
        %1 (assoc %1 (str/lower-case k) v)))
   {} (str/split (or headers "") #"(\n)|(\r)|(\r\n)|(\n\r)")))

(defn response->map [resp req]
  (let [{:keys [status] :as m}
        {:status     (.getStatus resp)
         :success    (.isSuccess resp)
         :body       (.getResponse resp)
         :headers    (headers->map (.getAllResponseHeaders resp))
         :error-code (code->error (.getLastErrorCode resp))
         :error-text (.getLastError resp)}
        m (-> m
              (cond-> (= status 0) tidy-http-error)
              (vary-meta assoc :kvlt/request req))]
    (log/debug "Received response\n" (util/pprint-str m))
    m))

(defn filter-headers [m]
  (into {}
    (for [[k v] m
          :when (not (#{:accept-encoding "accept-encoding"} k))]
      [k v])))

(defn request! [{:keys [request-method headers body credentials?] :as req}]
  (let [url     (req->url req)
        method  (name (or request-method :get))
        headers (clj->js (filter-headers headers))
        xhr     (req->xhr req)]
    (p/promise
     (fn [resolve reject]
       (.listen xhr EventType.COMPLETE
                #(resolve (response->map (.. % -target) req)))
       (.send xhr url method body headers)))))
