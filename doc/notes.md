# API Notes

## Request Body

Supported types for the `:body` value of a request are strings & byte arrays.
In Clojurescript, byte arrays are expected to be represented as typed arrays
(available on Node, and in [modern
browsers](http://caniuse.com/#feat=typedarrays)).  All request and response
bodies are immediate - there is currently no partial/stream representation on
either side.

## Response Body

The baseline user-facing response `:body` representation is a UTF-8 encoded
string.  The `:as` request key can be used to change this behaviour.  Helpful
values include:

### `:byte-array`

In Clojurescript, this'll be an `ArrayBuffer` (both in Node, and in browsers
which support that API).  It's _strongly_ suggested to use a byte array
representation for non UTF-8 response bodies, if you find yourself doing that
kind of thing - encoding strings in Javascript is no fun.

### `:auto`

The `kvlt.middleware/from-content-type` multimethod will be deferred to,
dispatching on the `:content-type` header of the response.  There are default
implementations for e.g. "application/edn", "application/json",
"application/x-www-form-urlencoded" and so on.

### `:json`, `:edn`, `:x-www-form-urlencoded`, etc.

These can be considered to force the response to be interpreted as with `:auto`,
if the given value were the minor type of an assumed "application/" content
type.  E.g. `{:as :json}` - regardless of response content type - will behave
like `{:as auto}` in the presence of an "application/json" response content
type.

## Quirks

### `:form-param-encoding`

In Clojurescript, if a value other than "UTF-8" is supplied for this key,
a warning will be printed to the console and the encoding will be ignored.

### Accept-Encoding

The XHR specification forbids clients to specify an `Accept-Encoding` header,
and many (all) browsers quietly supply their own value, in line with the
platform's decompression capabilities.

 - Clojure: `:accept-encoding` is always set to "gzip, deflate"
 - Clojurescript + browser: `:accept-encoding` is likely implicitly set to some superset of "gzip, deflate", depending on the environment
 - Clojurescript + Node: `:accept-encoding` is not ever set.  This is due to the third-party Node XHR shim conforming to the specification, while also neglecting to convey an implicit value, as in the browser.  Easily fixable, though the current behaviour is that Node will not ever request compressed output.

