(ns kvlt.core
  (:require [kvlt.platform.http :as platform.http]
            [kvlt.platform.websocket :as platform.websocket]
            [kvlt.platform.event-source :as platform.event-source]
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

(defn websocket!
  "Initiates a websocket connection with the given \"ws:\" or \"wss:\"
  URL and returns a promise resolving to a `core.async` channel.  If a
  connection cannot be established, the promise'll be rejected with an
  `ExceptionInfo` instance.

  Reads and writes on the resulting channel are delegated to distinct
  read/write channels - the \"read\" side being by default an
  unbuffered channel populated with messages from the websocket, and
  the \"write\" side, also unbuffered, being drained into the
  websocket itself.  The `read-chan` and `write-chan` options can be
  specified to e.g. apply a transducer to incoming/outgoing values.

  Closing the returned channel terminates the websocket connection,
  and will close the underlying read & write channels (unless `close?`
  is false, in which event it'll close neither).  The channel will be
  closed (and the same `close?` behaviour applied) if a transport
  error occurs after the connection has been established."
  [url & [{:keys [read-chan write-chan close? format] :as opts}]]
  (platform.websocket/request! url opts))

(defn event-source!
  "[Server-sent Events](https://html.spec.whatwg.org/multipage/comms.html#server-sent-events) client.

  Initiates a long-lived HTTP connection with `url`, placing maps
  representing incoming events onto a `core.async` channel.

  By default, only events of type `:message` will be considered (per
  spec).  To listen to a set of specific event types, `events` (a set
  of keywords) may be specified.

  The returned channel, when closed, will terminate the underlying SSE
  connection.  By default, the channel is unbuffered - though an
  arbitrary channel can be passed in via `chan` - and will be closed
  when the connection channel closes (or on error), unless `close?` is
  false.  On error, the connection will not be automatically
  re-established.

  `as` is some symbolic value (defaulting to `:string` - no-op) which
  is used as [[kvlt.event-source/format-event]]'s dispatch value.  ```
  "
  [url & [{:keys [events as chan close?]
           :or {events #{:message}
                as     :string
                close? true}}]]
  (platform.event-source/request!
   url {:events events
        :format as
        :chan   chan
        :close? close?}))
