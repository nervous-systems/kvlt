# Websocket Examples

`kvlt.core/websocket!` takes a URL, and returns a promise which'll
resolve to a `core.async` channel:

``` clojure
(ns kvlt.examples
  (:require [promesa.core :as p]
            [kvlt.core :as kvlt]
            ...))

(p/alet [ws (p/await (kvlt/websocket! "http://localhost:5000/ws"))]
  (go
    (>! ws "Behind_the_Wall_of_Sleep.wma")
    (is (= "OK" (<! ws)))))
```

Closing the `ws` channel will terminate the websocket connection.

## Formats

Once you've exhausted the possibilities of plain text, feel free to
stretch your legs:

```
(p/alet [ws (p/await (kvlt/websocket!
                      "http://localhost:5000/ws"
                      {:format :edn}))]
  (go
    (>! ws {:climate :good, :bribery :tolerated})
    (let [instructions (<! ws)]
      (is (instructions :proceed?)))))
```


If you're fucking with Clojurescript, or your project depends on
[cheshire](https://github.com/dakrone/cheshire), `:json` is also an
out-of-the-box message `:format`.

Formats may be user-defined:

``` clojure
(defmethod kvlt.websocket/format-outgoing :my-app/junk [fmt body]
  (str "Sending: " body))

(defmethod kvlt.websocket/format-incoming :my-app/junk [fmt body]
  (str "Receiving: " body))
```

## Channels

The channels used for the read and write sides of the connection may
be individually specified.

``` clojure
(kvlt/websocket!
 url
 {:format :edn
  :write-chan (async/to-chan (range 10))})
```

When `write-chan` is closed (as it would be above), the websocket will
be closed.

``` clojure
(kvlt/websocket!
 url
 {:format :json
  :read-chan  (async/chan 1 (map inc))
  :write-chan (async/chan 1 (map dec))})
```
