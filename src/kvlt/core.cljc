(ns kvlt.core
  (:require [kvlt.platform.http :as platform.http]
            [kvlt.middleware :as mw]
            [kvlt.middleware.params :as mw.params]
            [taoensso.timbre :as log]))

(def ^:no-doc default-middleware
  [mw/decompress
   mw/as

   mw.params/form
   mw.params/short-form
   mw.params/query
   mw.params/short-query

   mw/port
   mw/method
   mw/default-method
   mw/accept
   mw/accept-encoding
   mw/keyword-headers
   mw/lower-case-headers
   mw/content-type
   mw/default-content-type
   mw/body-type-hint
   mw/basic-auth
   mw/oauth-token
   mw/url

   mw/error])

(def ^:private request* (reduce #(%2 %1) platform.http/request! default-middleware))

(defn quiet! "Disable request/response logging" []
  (log/merge-config! {:ns-blacklist ["kvlt.*"]}))

(defn request!
  "Issues the HTTP request described by the given map, returning a
  promise resolving to a map describing the response, or rejected with
  an `ExceptionInfo` instance having a similar map associated with it.
  See [[kvlt.middleware/error]] for more details of the error
  conditions & behaviour.

  This function applies a variety of middleware to
  `kvlt.platform.http/request!`, in order to transform the input map
  into something Ring-like - and to perform similar transformations to
  the response."
  [req]
  (request* req))
