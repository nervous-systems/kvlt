(ns kvlt.middleware.params
  (:require [clojure.string :as str]
            [kvlt.middleware.util :as util
             #? (:clj :refer :cljs :refer-macros) [defmw]]
            [kvlt.middleware.util :as util
             :refer [->mw ->content-type url-encode charset]]))

(defn ^:no-doc query-string+encoding [params encoding]
  (str/join
   "&"
   (mapcat
    (fn [[k v]]
      (if (sequential? v)
        (map #(str (url-encode (name %1) encoding)
                   "="
                   (url-encode (str %2) encoding))
             (repeat k) v)
        [(str (url-encode (name k) encoding)
              "="
              (url-encode (str v) encoding))]))
    params)))

(defn ^:no-doc query-string [params & [content-type]]
  (let [encoding (charset content-type)]
    (query-string+encoding params encoding)))

(defmw query
  "Given a request having a `:query-params` map, append to the URL's
  query (`:query-string`) its URL-encoded string representation. "
  (fn [{:keys [query-params content-type]
        :or {content-type :x-www-form-urlencoded} :as req}]
    (cond-> req
      query-params
      (-> (dissoc :query-params)
          (update
           :query-string
           (fn [old new] (if-not (empty? old) (str old "&" new) new))
           (query-string query-params (->content-type content-type)))))))

(defmulti coerce-form-params
  "Turn a `:form-params` map into a string request body, dispatching
  on the qualified content type, as a namespaced
  keyword (e.g. `:application/edn`).

  The baseline implementation (for
  `:application/x-www-form-urlencoded`) looks at the request's
  `:form-param-encoding` to determine the character set of the output
  string, on platforms where this is supported."
  (fn [{:keys [content-type]}]
    (keyword (->content-type content-type))))

(defmethod coerce-form-params :application/x-www-form-urlencoded
  [{:keys [content-type form-params form-param-encoding]}]
  (if form-param-encoding
    (query-string+encoding form-params form-param-encoding)
    (query-string form-params (->content-type content-type))))

(defmethod coerce-form-params :application/edn [{:keys [form-params]}]
  (pr-str form-params))

(defmw form
  "Given a request having a `:form-params` map and a method of `POST`,
  `PUT` or `PATCH`, use [[coerce-form-params]] to generate a request
  body.  If no content type is supplied, a default of
  `application/x-www-form-urlencoded` is associated with the request,
  and passed to [[coerce-form-params]].

  Assumes placement after [[kvlt.middleware/method]]"
  (fn [{:keys [form-params content-type request-method]
        :or {content-type :x-www-form-urlencoded}
        :as req}]
    (if (and form-params (#{:post :put :patch} request-method))
      (let [content-type (->content-type content-type)
            req          (assoc req :content-type content-type)]
        (assoc req :body (coerce-form-params req)))
      req)))
