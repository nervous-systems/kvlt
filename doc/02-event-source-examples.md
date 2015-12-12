# SSE Examples

[Server-sent events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events) from Clojure/script.

The simplest possible example looks something like:

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

## Event Types / Formats

```clojure
(def events
  (kvlt.core/event-source!
   url
   {:events #{:verbose :debug}
    :as     :edn}))
```

Assuming the server is writing EDN strings, each entry on `events`'ll
look like:

```clojure
{:id   "optional-event-id"
 :type :verbose
 :data {:business :good
        :intrigue :high}}
```

It's possible to define application specific formats:

```clojure
(defmethod kvlt.event-source/format-event :app/log-message
  [format event]
  (assoc event :data "I'm overwriting your data"))
```

Incoming events from sources created with `:as :app/log-message` will be
mauled by the function above.

`:as` is a convenience - it's also possible to process events with
custom transducers:

```clojure
(def events
  (kvlt.core/event-source!
   url
   {:events #{:verbose :debug}
    :as     :edn
    :chan   (async/chan 1 (map :data))}))
```

Following on from the topmost example, `events` will now contain e.g.

```clojure
{:business :good, :intrigue :high}
```

Eliding the event envelope.
