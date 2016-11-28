(ns ^:no-doc kvlt.middleware.util
  (:require [kvlt.platform.util :as platform.util]
            [cats.labs.promise]
            [clojure.string :as str]
            [cats.core :as m]
            [taoensso.timbre :as log]
            #? (:cljs [goog.crypt.base64 :as base64]))
  #? (:clj
      (:import [java.net URL URLEncoder URLDecoder]
               [org.apache.commons.codec.binary Base64])
      :cljs
      (:require-macros [kvlt.middleware.util]))
  #? (:cljs (:import [goog.Uri])))

(defn ->content-type
  ([t]
   (if (keyword? t)
     (let [major (or (namespace t) :application)]
       (str (name major) "/" (name t)))
     t))
  ([t charset]
   (cond-> (->content-type t) charset (str "; charset=" charset))))

(defn spec->fn [spec]
  (cond (nil?  spec) identity
        (coll? spec) (let [[k f] spec]
                       #(update % k f))
        :else spec))

(defn- clean-req [r]
  (dissoc r
          :kvlt.middleware/request
          :kvlt.middleware/response
          :kvlt/trace))

(defn wrap-before [f]
  (let [{:keys [has removing]} (meta f)
        f (if has
            (fn [{v has :as req}]
              (cond-> req
                v f))
            f)]
    (if removing
      (fn [req]
        (dissoc (f req) removing))
      f)))

(defn ->mw [helpful-name before & [after]]
  (let [after  (spec->fn after)
        before (-> before spec->fn wrap-before)]
    (fn [issue!]
      (fn [{:keys [kvlt/trace] :as req}]
        (let [req (before req)
              req (cond-> req
                    trace (update :kvlt.middleware/request
                                  (fnil conj [])
                                  [helpful-name (clean-req req)]))]
          (m/>>=
           (issue! req)
           (comp m/return after)
           (comp m/return
                 #(cond-> %
                    trace
                    (update :kvlt.middleware/response
                            (fnil conj [])
                            [helpful-name (clean-req req)])))))))))

#? (:clj
    (defmacro defmw [varname doc before & [after]]
      `(def ~varname ~doc
         (->mw ~(keyword varname) ~before ~after))))

;; More or less all from clj-http, with portability adjustments

(defn url-decode [encoded & [encoding]]
  (let [encoding (or encoding "UTF-8")]
    #? (:clj
        (URLDecoder/decode encoded encoding)
        :cljs
        (do
          (when (not= "UTF-8" encoding)
            (log/warn "url-decode ignoring encoding" encoding))
          (js/decodeURIComponent encoded)))))

(defn url-encode [unencoded & [encoding]]
  (let [encoding (or encoding "UTF-8")]
    #? (:clj
        (URLEncoder/encode unencoded encoding)
        :cljs
        (do
          (when (not= "UTF-8" encoding)
            (log/warn "url-encode ignoring encoding" encoding))
          (js/encodeURIComponent unencoded)))))

(defn url-encode-illegal-characters
  "Takes a raw url path or query and url-encodes any illegal characters.
  Minimizes ambiguity by encoding space to %20."
  [path-or-query]
  (when path-or-query
    (-> path-or-query
        (str/replace " " "%20")
        (str/replace #"[^a-zA-Z0-9\.\-\_\~\!\$\&\'\(\)\*\+\,\;\=\:\@\/\%\?]"
                     url-encode))))

(defn parse-content-type
  "Parse `s` as an RFC 2616 media type."
  [s]
  (if-let [m (re-matches #"\s*(([^/]+)/([^ ;]+))\s*(\s*;.*)?" (str s))]
    {:content-type (keyword (nth m 1))
     :content-type-params
     (->> (str/split (str (nth m 4)) #"\s*;\s*")
          (identity)
          (remove str/blank?)
          (map #(str/split % #"="))
          (mapcat (fn [[k v]] [(keyword (str/lower-case k)) (str/trim v)]))
          (apply hash-map))}))

(let [pattern #"(?i)charset\s*=\s*([^\s]+)"]
  (defn charset
    [content-type & [{:keys [fallback]}]]
    (let [charset (some->> content-type name (re-find pattern) second)]
      (or charset fallback "UTF-8"))))

(defn string->base64 [x]
  #? (:clj
      (-> x (.getBytes "UTF-8") Base64/encodeBase64 (String. "UTF-8"))
      :cljs
      (base64/encodeString x)))

(defn basic-auth [v]
  (let [[user pass]
        (if (map? v)
          [(v :username) (v :password)]
          v)]
    (str "Basic " (string->base64 (str user ":" pass)))))

(defn ^:no-doc parse-url [url]
  (let [url #? (:clj (java.net.URL. url) :cljs (goog.Uri. url))]
    {:scheme       (-> url #? (:clj .getProtocol :cljs .getScheme) keyword)
     :server-name  (.. url #? (:clj getHost :cljs getDomain))
     :server-port  (when-let [port (.getPort url)]
                     (when (pos? port) port))
     :uri          (some-> url .getPath  url-encode-illegal-characters)
     :query-string (some-> url .getQuery not-empty url-encode-illegal-characters)
     :user-info    (some-> url .getUserInfo not-empty url-decode)}))
