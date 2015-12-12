![Hail](https://raw.githubusercontent.com/nervous-systems/kvlt/master/doc/assets/kvlt.png) [![Build Status](https://travis-ci.org/nervous-systems/kvlt.svg?branch=master)](https://travis-ci.org/nervous-systems/kvlt)

Attempts to present a uniform, asychronous client interface for HTTP across JVM / Node / browsers.

[Latest documentation / examples](//nervous.io/doc/kvlt)

[![Clojars Project](http://clojars.org/io.nervous/kvlt/latest-version.svg)](http://clojars.org/io.nervous/kvlt)

### Features
 - Supports Clojure/JVM, Clojurescript/Node and Clojurescript/Browser
 - Individual deferred values are exposed via promises ([kvlt.core](//nervous.io/doc/kvlt/kvlt.core.html)), or asynchronous channels ([kvlt.chan](//nervous.io/doc/kvlt/kvlt.chan.html))
 - `core.async`-based support for Websockets and Server-sent Events
 - Raw responses available as Javascript typed arrays (on Node, and in browsers with [XHR Level 2](https://www.w3.org/TR/XMLHttpRequest2/) support)
 - Ring-like API

### Requirements

 - Clojure use requires JDK8

### Todo / Notes
 - Automated/CI testing is currently limited to JVM, Node and recent Chrome & Firefox builds
 - No support for streamed requests/responses.  Open to suggestions about how this might be handled across platforms
 - Young project, etc. - please file issues

# Examples

[kvlt.core/request!](//nervous.io/doc/kvlt/kvlt.core.html#var-request.21)
returns a [promesa](https://github.com/funcool/promesa) promise, which
can be manipulated using promise-specific (e.g. `promesa/then`)
operations, or treated as a monad using the primitives from
[cats](https://github.com/funcool/cats).  Below, we're working with
something like:

```clojure
(ns kvlt.examples
  (:require [cats.core :as m]
            [kvlt.core :as kvlt]
            [promesa.core :as promesa]))
```

The default `:method` is `:get`:

```clojure
(m/mlet [{:keys [status]} (kvlt/request! {:url url})]
  (is (= status 200)))
```

## Explicit Callback

```clojure
(promesa/then
 (kvlt/request! {:url url})
 (fn [{:keys [status]}]
   (is (= status 200))))
```

## core.async

The [kvlt.chan](//nervous.io/doc/kvlt/kvlt.chan.html) namespace
parallels the promise-driven `kvlt.core`, using asynchronous channels
to communicate deferred values.

```clojure
(go
  (let [{:keys [status]} (<! (kvlt.chan/request! {:url url}))]
    (is (= status 200))))
```

## Writing Data

In addition to Ring-style `:form-params`/`:type`, metadata may be
applied to `:body`, indicating the desired content-type and body
serialization:

```clojure
(m/mlet [{:keys [body]}
         (kvlt/request!
          {:url    url
           :method :post
           :body   ^:kvlt.body/edn {:hello "world"}
           :as     :auto})]
  (is (= (body :hello) "world")))
```

`:as :auto` causes the `:body` key in the response to be processed in
accord with the response's content-type.  `:as :edn`, in this case,
would have the same effect.

## Errors

```clojure
(promesa/catch
 (kvlt/request! {:url (str url "/404")})
 (fn [e]
   (is (= :not-found ((ex-data e) :type)))))
```

All requests resulting in exceptional response codes, or more
fundamental (e.g. transport) errors will cause the returned promise's
error branch to be followed with an
[ExceptionInfo](https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/ExceptionInfo.java)
instance - i.e. an Exception/Error with an associated response map
retrievable via `ex-data`.

### Example Map

``` clojure
{:headers {:content-type "text/plain" ...}
 :type   :not-found
 :reason :not-found
 :status 404
 ...}
```

[More request/response examples](https://nervous.io/doc/kvlt/01-req-resp-examples.html)

## [Server-sent events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)

```clojure
(def events (kvlt.core/event-source! url))
```

`events` is a `core.async` channel (rather, something a lot like a
`core.async` channel, which'll terminate the SSE connection when
`async/close!`'d).

Assuming no event types or identifiers are supplied by the server, a
value on `events` looks something like:

```clojure
{:type :message
 :data "String\nfrom\nthe server"}
```

[More SSE examples](//nervous.io/doc/kvlt/02-event-source-examples.html)

## Websockets

[kvlt.core/websocket!](https://nervous.io/doc/kvlt/kvlt.core.html#var-websocket.21)
takes a URL, and returns a promise which'll resolve to a `core.async`
channel:

``` clojure
(m/mlet [ws (kvlt/websocket!
              "http://localhost:5000/ws" {:format :edn})]
  (go
    (>! ws {:climate :good, :bribery :tolerated})
    (let [instructions (<! ws)]
      (is (instructions :proceed?)))))
```

Closing the `ws` channel will terminate the websocket connection.

[More Websocket examples](https://nervous.io/doc/kvlt/03-websocket-examples.html)

# License

kvlt is free and unencumbered public domain software. For more
information, see http://unlicense.org/ or the accompanying UNLICENSE
file.
