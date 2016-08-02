(ns kvlt.middleware
  (:require [#?(:clj clojure.edn :cljs cljs.reader) :as edn]
            [kvlt.middleware.util :as util #? (:clj :refer :cljs :refer-macros) [defmw]]
            [kvlt.util #? (:clj :refer :cljs :refer-macros) [with-doc-examples!]]
            [kvlt.platform.util :as platform.util]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [clojure.string :as str]
            [cats.core :as m]
            [kvlt.util :refer [map-keys]]))

(defn ^:no-doc header
  ([{hs :headers :as resp} k]
   (and hs (some hs [k (name k)])))
  ([m k v]
   (update m :headers
           (fn [h]
             (-> h
                 (dissoc k (name k))
                 (assoc (name k) v))))))

(defn- body->string [{:keys [body] :as resp}]
  (platform.util/byte-array->str
   body (util/charset (header resp :content-type))))

(defmulti from-content-type
  "Used by [[as]] to transform an incoming response.  Dispatches on
  `:content-type`' header, as a namespace-qualified
  keyword (e.g. `:application/edn`).  The input and output are the
  top-level response map, not only the response body.

  The default implementation (i.e. unrecognized content-type) returns
  the response map unmodified."
  (fn [resp]
    (-> resp (header :content-type) util/->content-type keyword)))

(defmethod from-content-type :default [resp]
  resp)
(defmethod from-content-type :application/edn [resp]
  (assoc resp :body (edn/read-string (body->string resp))))
(defmethod from-content-type :application/json [{:keys [body] :as resp}]
  (assoc resp :body (platform.util/parse-json (body->string resp))))

(defn- hint->body-type [x]
  (when (and (keyword? x) (= "kvlt.body" (namespace x)))
    (keyword (name x))))

(defmw body-type-hint
  "Look for a body with a `:kvlt.body/`-prefixed metadata key, setting
  the request's `:type` and `:form-params` keys
  accordingly (e.g. `:body ^:kvlt/edn {:x 1}`)"
  ^{:has :body}
  (fn [{:keys [body] :as req}]
    (if-let [t (->> body meta keys (some hint->body-type))]
      (-> req
          (assoc  :type (keyword t) :form-params body)
          (dissoc :body))
      req)))

(with-doc-examples! body-type-hint
  [{:method :post
    :body ^:kvlt.body/edn [1 2 3]}
   {:method :post
    :form-params [1 2 3]
    :type :edn}])

(defmw content-type
  "Turn request's `:content-type` (or `:type`), if any, and
   `:character-encoding`, if any, into a \"content-type\" header & leave
   top-level `:content-type` key in place. "
  (fn [{:keys [type body character-encoding] :as req}]
    (let [{:keys [content-type] :as req}
          (cond-> req type (assoc :content-type type))]
      (cond-> req
        content-type
        (header :content-type
                (util/->content-type content-type character-encoding))))))

(with-doc-examples!
  content-type
  [{:content-type "text/html"
    :character-encoding "US-ASCII"}
   {:headers {:content-type "text/html; charset=US-ASCII"}
    :content-type "text/html"}])

(defmw accept
  "Turn request's `:accept` value, if any, into an \"accept\" header &
  remove the top-level key."
  ^{:has :accept :removing :accept}
  (fn [{:keys [accept] :as req}]
    (header req :accept (util/->content-type accept))))

(with-doc-examples! accept
  [{:accept :edn} {:headers {"accept" "application/edn"}}])

(defn- as-key [resp]
  (-> resp meta :kvlt/request :as))

(defmulti ^:no-doc as-type as-key)
(defmethod as-type :string [{:keys [body] :as resp}]
  #? (:clj
      (update resp :body platform.util/byte-array->str
              (util/charset (header resp :content-type)))
      :cljs resp))

(defmethod as-type :byte-array [{:keys [body] :as resp}]
  (assert
   (platform.util/byte-array? body)
   "For platform-specific reasons, :as :byte-array is special-cased in
  kvlt.platform.http/request")
  resp)
(defmethod as-type :auto [resp] (from-content-type resp))
(defmethod as-type :default [{:keys [headers] :as resp}]
  (let [t    (header resp :content-type)
        resp (assoc resp :orig-content-type t)]
    (from-content-type
     (header resp :content-type (util/->content-type (as-key resp))))))

(defn- parsing-error [resp e]
  (let [error (platform.util/exception->map
               e {:error :middleware-error
                  :type  :middleware-error})]
    (cond-> resp
      (not (resp :error)) (merge error))))

(defmw as
  "Response body type conversion --- `:string` `:byte-array` `:auto` `:json` `:edn` etc..

  See [[from-content-type]] for custom conversions."
  #(merge {:as :string} %)
  (fn [resp]
    (try
      (as-type resp)
      (catch #? (:clj Exception :cljs js/Error) e
        (parsing-error resp e)))))

(defmw accept-encoding
  "Convert the `:accept-encoding` option (keyword/str, or collection of) to an
  acceptable `Accept-Encoding` header.

  This middleware is not likely to have any effect in a browser
  environment."
  ^{:has :accept-encoding :removing :accept-encoding}
  (fn [{v :accept-encoding :as req}]
    (header
     req
     :accept-encoding
     (if (coll? v)
       (str/join ", " (map name v))
       (name v)))))

(with-doc-examples! accept-encoding
  [{:accept-encoding :origami}
   {:headers {:accept-encoding "origami"}}]
  [{:accept-encoding [:a :b]}
   {:headers {:accept-encoding "a, b"}}])

(defmw method
  "Rename request's `:method` key to `:request-method`"
  ^{:has :method :removing :method}
  (fn [{m :method :as req}]
    (assoc req :request-method m)))

(defmw port
  "Rename request's `:port` key to `:server-port`"
  ^{:has :port :removing :port}
  (fn [{port :port :as req}]
    (assoc req :server-port port)))

(with-doc-examples! method
  [{:method :get} {:request-method :get}])

(defmw url
  "Turn request's `:url` value, if any, into top-level `:scheme`,
  `:server-name`, `:server-port`, `:uri`, `:query-string`, and
  `:user-info` keys"
  ^{:has :url :removing :url}
  (fn [{url :url :as req}]
    (merge req (util/parse-url url))))

(with-doc-examples! url
  [{:url "ftp://localhost:9/x?x=1"}
   {:scheme :ftp
    :server-name "localhost"
    :server-port 9
    :uri "/x"
    :user-info nil
    :query-string "x=1"}])

(defmw default-content-type
  "Add `:content-type` key having value `:text/plain`, if no `:content-type` present.

  Assumes placement before [[content-type]]."
  (fn [req]
    (if (and (req :body) (not (or (req :content-type) (header req :content-type))))
      (assoc req :content-type :text/plain)
      req)))

(defmw keyword-headers
  "Convert keys within request's `:headers` value to strings, and
response's `:headers` values to keywords. "
  [:headers walk/stringify-keys]
  [:headers walk/keywordize-keys])

(def ^:private lower-case
  #(cond-> (str/lower-case (name %)) (keyword? %) keyword))

(defmw lower-case-headers
  "Convert keys within request & response's `:headers` value to lower
  case."
  [:headers #(map-keys lower-case %)]
  [:headers #(map-keys lower-case %)])

(defmw basic-auth
  "Convert `:basic-auth` values (vector or map) into an
 `:authorization` header."
  ^{:has :basic-auth :removing :basic-auth}
  (fn [{:keys [basic-auth] :as req}]
    (header req :authorization (util/basic-auth basic-auth))))

(with-doc-examples! basic-auth
  [{:basic-auth ["user" "pass"]} {:headers {:authorization "Basic ..."}}]
  [{:basic-auth {:username "user" :password "pass"}}
   {:headers {:authorization "Basic ..."}}])

(defmw oauth-token
  "Convert `:oauth-token` value into an `:authorization` header"
  ^{:has :oauth-token :removing :oauth-token}
  (fn [{:keys [oauth-token] :as req}]
    (header req :authorization (str "Bearer " oauth-token))))

(with-doc-examples! oauth-token
  [{:oauth-token "xyz"} {:headers {:authorization "Bearer xyz"}}])

(defmw default-method
  "Merge request map with `{:method :get}`.

  Assumes placement before [[method]]."
  #(merge {:method :get} %))

(with-doc-examples! default-method
  [{} {:method :get}])

(defmulti decompress-body
  "Dispatch on the response's `:content-encoding` header value.
  Clojure implementations exist for \"gzip\" and \"deflate\"."
  (fn [resp]
    (and (:body resp) (header resp :content-encoding))))

(defn ^:no-doc lift-content-encoding [{{:strs [content-encoding]} :headers :as resp}]
  (-> resp
      (assoc :orig-content-encoding content-encoding)
      (update :headers dissoc "content-encoding")))

(defmethod decompress-body "gzip" [{:keys [body] :as resp}]
  (let [body (platform.util/gunzip body)]
    (lift-content-encoding (assoc resp :body body))))

(defmethod decompress-body "deflate" [{:keys [body] :as resp}]
  (let [body (platform.util/inflate body)]
    (lift-content-encoding (assoc resp :body body))))

(defmethod decompress-body :default [resp]
  (lift-content-encoding resp))

(defmw decompress
  "Response body decompression.  Defaults request's \"Accept-Encoding\" header.
  Can be disabled per-request via `:decompress-body? false'"
  ^{:removing :accept-encoding}
  (fn [req]
    (cond-> req
      (and (not (false? (req :decompress-body?)))
           (not (header req :accept-encoding)))
      (header :accept-encoding "gzip, deflate")))
  (fn [resp]
    #? (:clj  (let [decomp? (-> resp meta :kvlt/request :decompress-body? false? not)]
                (cond-> resp (and decomp? (not-empty (resp :body))) decompress-body))
        :cljs resp)))

(def ^:no-doc unexceptional-status?
  #{200 201 202 203 204 205 206 207 300 301 302 303 304 307})

(def ^:no-doc status->reason
  {100 :continue
   101 :switching-protocols
   200 :ok
   201 :created
   202 :accepted
   203 :non-authoritative-information
   204 :no-content
   205 :reset-content
   206 :partial-content
   300 :multiple-choices
   301 :moved-permanently
   302 :found
   303 :see-other
   304 :not-modified
   305 :use-proxy
   307 :temporary-redirect
   400 :bad-request
   401 :unauthorized
   402 :payment-required ;; serious business
   403 :forbidden
   404 :not-found
   405 :method-not-allowed
   406 :not-acceptable
   407 :proxy-authentication-required
   408 :request-timeout
   409 :conflict
   410 :gone
   411 :length-required
   412 :precondition-failed
   413 :request-entity-too-large
   414 :request-uri-too-large
   415 :unsupported-media-type
   416 :requested-range-not-satisfiable
   417 :expectation-failed
   500 :internal-server-error
   501 :not-implemented
   502 :bad-gateway
   503 :service-unavailable
   504 :gateway-timeout
   505 :http-version-not-supported})

(defmw error
  "Turn error responses into `ExceptionInfo` instances, with the full
  response map as the attached data.  Additionally, a `:reason`
  value (e.g. `:service-unavailable`) will be used to augment the
  `:status` key.

  For uniformity, `:type` is provided as an alias for `:reason`, e.g."
  nil
  (fn [{:keys [message status cause error] :as resp}]
    (let [reason (status->reason status error)]
      (if (and (not error) (unexceptional-status? status))
        (assoc resp :reason reason)
        (ex-info message
                 (assoc resp
                   :error  (or error reason)
                   :type   reason
                   :reason reason)
                 cause)))))

(with-doc-examples! error
  [{:status  500
    :reason  :internal-server-error
    :type    :internal-server-error
    :cause   error?
    :headers ...}])
