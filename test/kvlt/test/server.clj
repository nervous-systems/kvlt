(ns kvlt.test.server
  (:require [clojure.core.async :as async :refer [<! >! go]]
            [aleph.http :as http]
            [manifold.stream :as stream]
            [manifold.time]
            [manifold.deferred :as d]
            [clojure.walk :as walk]
            [compojure.core :as compojure]
            [compojure.route :as route]
            [byte-streams]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint])
  (:import [java.util.zip GZIPOutputStream DeflaterInputStream]
           [java.io ByteArrayOutputStream ByteArrayInputStream])
  (:gen-class))

(defn handler-numbers
  [{{:strs [cnt] :or {cnt "100"}} :params}]
  (let [cnt   (Integer/parseInt cnt)]
    {:status 200
     :headers {"content-type" "application/octet-stream"}
     :body (let [sent (atom -1)]
             (->> (stream/periodically 100 #(str (swap! sent inc) "\n"))
                  (stream/transform (take cnt))))}))

(defn gzip [s]
  (let [stream (ByteArrayOutputStream.)]
    (doto (GZIPOutputStream. stream)
      (.write (.getBytes s "UTF-8"))
      (.close))
    (.toByteArray stream)))

(defn deflate [s]
  (-> (.getBytes s "UTF-8")
      ByteArrayInputStream.
      DeflaterInputStream.))

(defn gzip? [{:strs [accept-encoding]}]
  (when accept-encoding
    (re-find #"gzip" accept-encoding)))
(defn deflate? [{:strs [accept-encoding]}]
  (when accept-encoding
    (re-find #"deflate" accept-encoding)))

(defn handler-sse [req]
  (let [stream (stream/stream)
        send!  #(stream/put! stream (apply str %&))]
    (send! "data: A bunch of\r\ndata: ")
    (send! " events \n\nHorseshit\n\n")
    (d/loop [i 0]
      (if (< i 100)
        (do
          (send! "event: ")
          (send! (if (odd? i) "odd" "even") "\n")
          (send! "id: " i "\r")
          (send! "data: " (pr-str {:index i}) "\r\n\r\n")
          (-> (d/deferred)
              (d/timeout! 100 (inc i))
              (d/chain d/recur)))
        (stream/close! stream)))
    {:status 200
     :headers {"content-type" "text/event-stream"
               "cache-control" "no-cache"
               "connection" "keep-alive"}
     :body stream}))

(defn handler-echo
  [{{:strs [status] :or {status "200"}} :params :keys [body headers] :as req}]
  (let [body-in  (some-> body
                         (byte-streams/convert String)
                         clojure.edn/read-string)
        body-out (pr-str (cond-> req body-in (assoc :body body-in)))
        [encoding body-out]
        (cond
          (gzip? headers)    ["gzip"    (gzip body-out)]
          (deflate? headers) ["deflate" (deflate body-out)]
          :else              [nil body-out])]
    {:status  (or (:response-code body) (Integer/parseInt status))
     :body    body-out
     :headers (merge
               {"content-type" "application/edn"}
               (when encoding
                 {"content-encoding" encoding})
               (walk/stringify-keys (:response-headers body-in)))}))

(defn handler-echo-body
  [{{:strs [encoding] :or {encoding "UTF-8"}} :params :keys [body] :as req}]
  {:status 200
   :headers {"content-type" (str "text/plain; charset=" encoding)}
   :body (if body
           (byte-streams/to-byte-array body {:encoding encoding})
           "")})

(defn handler-ws-echo [req]
  (if-let [socket (try
                    @(http/websocket-connection req)
                    (catch Exception e
                      (clojure.pprint/pprint e)
                      nil))]
    (stream/connect-via
     socket
     (fn [msg]
       (println "Echoing websocket message:" msg)
       (stream/put! socket msg))
     socket)
    {:status 400
     :body "Expected a websocket request."}))

(defn handler-redirect [{{:strs [location status] :or {status "302"}} :params}]
  {:status (Integer/parseInt status)
   :headers {"location" location}
   :body ""})

(defn handler-ok
  [req]
  {:status 200 :body "OK"})

(defn cors-headers
  [{{:strs [access-control-request-headers]
     :or {access-control-request-headers
          "Accept, Content-Type, Authorization"}} :headers}]
  {:access-control-allow-origin "*"
   :access-control-allow-headers access-control-request-headers
   :access-control-allow-methods "GET, PUT, POST, DELETE, OPTIONS, PATCH"})

(defn wrap-suicidal-cors [handler]
  (fn [{:keys [request-method] :as req}]
    (let [resp (if (= request-method :options)
                 {:status 200}
                 (handler req))]
      (some->
       resp
       (d/chain
        #(update % :headers (partial merge (cors-headers req))))))))

(def handler
  (-> (compojure/routes
       (compojure/GET  "/numbers"   [] handler-numbers)
       (compojure/GET  "/echo"      [] handler-echo)
       (compojure/POST "/echo"      [] handler-echo)
       (compojure/POST "/echo/body" [] handler-echo-body)
       (compojure/GET  "/ws-echo"   [] handler-ws-echo)
       (compojure/GET  "/redirect"  [] handler-redirect)
       (compojure/GET  "/ok"        [] handler-ok)
       (compojure/GET  "/events"    [] handler-sse)
       (route/not-found "Oops!"))
      wrap-suicidal-cors
      wrap-params))

(defn start! [& [port]]
  (http/start-server handler {:port (or port 5000)}))

(defn -main [& [port]]
  (start! (when port (Integer/parseInt port))))
