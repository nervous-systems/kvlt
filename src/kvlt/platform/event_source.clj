(ns ^:no-doc kvlt.platform.event-source
  (:require [kvlt.util :as util]
            [kvlt.event-source :refer [format-event]]
            [kvlt.platform.http :refer [default-request required-middleware]]
            [aleph.http :as http]
            [taoensso.timbre :as log]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [clojure.string :as str]
            [byte-streams]
            [clojure.core.async :as async]))

(defn span->kv [lines]
  (for [line lines
        :let [[h t] (str/split (str/trim-newline line) #":[ ]?" 2)
              k     (keyword h)]
        :when (and (not-empty h) t)]
    [k t]))

(defn kv->event [event-kv]
  (let [event
        (reduce
         (fn [m [k v]]
           (case k
             :data  (update m :data conj v)
             :event (assoc  m :type (keyword v))
             :retry (assoc  m k (Integer/parseInt v))
             :id    (assoc  m k v)
             m))
         {:type :message :data []}
         event-kv)]
    (update event :data #(not-empty (str/join "\n" %)))))

(defn span->event [span format]
  (some->> span span->kv kv->event (format-event format)))

(defn split-after-newline [s]
  (-> s (str/replace #"\r\n|\r" "\n") (str/split #"(?<=\n)")))

(defn- http-response->events [{:keys [body status]} events format]
  (let [out     (s/stream)
        lines   (s/mapcat (comp split-after-newline byte-streams/to-string) body)
        output! (fn [{:keys [type id] :as e} last-id]
                  (if (and e (events type))
                    (s/put! out (assoc e :id (or id last-id)))
                    (d/success-deferred true)))]
    (d/loop [last-id nil span [] line nil]
      (-> (s/take! lines)
          (d/chain
           (util/fn-when [chunk]
             (cond
               (= \newline (first chunk)) ;; Blank
               (let [{:keys [id] :as e} (span->event span format)]
                 (-> (output! e last-id)
                     (d/chain (util/fn-when [_]
                                (d/recur (or id last-id) [] nil)))))

               (not= \newline (last chunk)) ;; Partial
               (d/recur last-id span (str line chunk))

               :else
               (d/recur last-id (conj span (str line chunk)) nil))))))
    out))

(def ^:private sse-pool
  (http/connection-pool {:middleware required-middleware}))

(defn sse-req [url]
  (default-request
   {:url url
    :raw-stream? true
    :request-method :get
    :headers {"cache-control" "no-cache"
              "accept" "text/event-stream"
              "connection" "keep-alive"}}
   sse-pool))

(defn request!
  [url & [{:keys [events format chan close?]
           :or {events #{:message} format :default close? true}}]]
  (let [events (cond->> events (coll? events) (into #{}))
        stream (s/stream)
        chan   (or chan (async/chan))]
    (s/connect stream chan {:downstream? close? :upstream? true})
    (d/on-realized
     (http/request (sse-req url))
     (fn [resp]
       (let [events (http-response->events resp events format)]
         (s/on-closed stream #(s/close! (:body resp)))
         (s/connect events stream)))
     (fn [err]
       (log/error err "SSE error, closing channel" url)
       (s/close! stream)))
    (util/read-proxy-chan chan #(s/close! stream) {:close? close?})))
