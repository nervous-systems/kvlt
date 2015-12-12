# Examples

`kvlt.core/request!` returns a
[promesa](https://github.com/funcool/promesa) promise, which can be
manipulated using promise-specific (e.g. `promesa/then`) operations,
or treated as a monad using the primitives from
[cats](https://github.com/funcool/cats).  Below, we're working with
something like:

```clojure
(ns kvlt.examples
  (:require [cats.core :as m]
            [kvlt.core :as kvlt]
            [promesa.core :as promesa]))
```

And assuming a resource at `url` which'll return a response with
identical headers & body to its request.

# Simple GET Requests

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

## Blocking (Clojure only, obvs.)

```clojure
#? (:clj
    (let [{:keys [status]} @(kvlt/request! {:url url})]
      (is (= status 200))))
```

## core.async (via kvlt.chan)

```clojure
(go
  (let [{:keys [status]} (<! (kvlt.chan/request! {:url url}))]
    (is (= status 200))))
```


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

`:type` & `:reason` are synonymous.

# Request Bodies

The default body content type is `text/plain`:

```clojure
(m/mlet [{:keys [body headers]}
         (kvlt/request! {:url url :method :post :body "Hi")]
  (is (= body "Hi"))
  (is (= (headers :content-type) "text/plain")))
```

## Specifying Content Type

In addition to Ring-style `:form-params` & `:type`, metadata may be
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

By default in Clojurescript --- and in Clojure if your project depends
on [cheshire](https://github.com/dakrone/cheshire) ---
`^:kvlt.body/json` / `:as :json` can be used to input/output JSON -
similarly with `x-www-form-urlencoded`.

## Byte Arrays

Request bodies can be supplied as byte arrays (typed arrays, in
Clojurescript).

```clojure
(let [input #? (:clj  (.getBytes "hello!" "UTF-8")
                :cljs (js/Int8Array.
                       (goog.crypt.stringToUtf8ByteArray "hello!")))]
  (m/mlet [{output :body}
           (kvlt/request! {:url    url
                           :method :post
                           :body   input
                           :as     :byte-array})]
    ;; Some code to compare these guys in a cross-platform way
    ))
```

`:as :byte-array` will cause the response body to be returned as ---
wait for it --- a byte array.
