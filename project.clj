(defproject io.nervous/kvlt "0.1.3"
  :url "https://github.com/nervous-systems/kvlt"
  :description "Multi-target Clojure/script HTTP client"
  :license {:name "Unlicense" :url "http://unlicense.org/UNLICENSE"}
  :scm {:name "git" :url "https://github.com/nervous-systems/kvlt"}
  :codox
  {:source-paths ["src"]
   :metadata {:doc/format :markdown}
   :html
   {:transforms
    ~(read-string (slurp "codox-transforms.edn"))}
   :source-uri "https://github.com/nervous-systems/kvlt/blob/master/{filepath}#L{line}"}
  :source-paths ["src"]
  :dependencies [[org.clojure/clojure        "1.8.0"]
                 [org.clojure/clojurescript  "1.8.51"]
                 [org.clojure/core.async     "0.2.374"]

                 [funcool/promesa     "1.1.1"]
                 [funcool/cats        "1.2.1"]
                 [aleph               "0.4.1-beta5"]
                 [com.taoensso/timbre "4.5.0"]
                 [commons-codec/commons-codec "1.9"]]
  :plugins [[lein-npm       "0.6.0"]
            [lein-cljsbuild "1.1.1-SNAPSHOT"]
            [lein-doo       "0.1.7-SNAPSHOT"]
            [lein-codox     "0.9.4"]]
  :npm {:dependencies [[request            "2.72.0"]
                       [websocket          "1.0.22"]
                       [eventsource        "0.1.6"]
                       [source-map-support "0.4.0"]]}
  :cljsbuild {:builds
              [{:id "node-test"
                :source-paths ["src" "test"]
                :compiler {:output-to "target/node-test/kvlt.js"
                           :output-dir "target/node-test"
                           :target :nodejs
                           :optimizations :none
                           :main kvlt.test.runner}}
               {:id "node-test-adv"
                :source-paths ["src" "test"]
                :compiler {:output-to "target/node-test-adv/kvlt.js"
                           :output-dir "target/node-test-adv"
                           :target :nodejs
                           :optimizations :advanced
                           :main kvlt.test.runner}}
               {:id "generic-test"
                :source-paths ["src" "test"]
                :compiler {:output-to "target/generic-test/kvlt.js"
                           :optimizations :simple
                           :main kvlt.test.runner}}]}
  :auto {"codox" {:file-pattern #"\.(clj[cs]?|md)$"
                  :paths ["doc" "src"]}}
  :aliases {"test-server" ["run" "-m" "kvlt.test.server"]}
  :profiles {:dev {:source-paths ["test-server" "test"]
                   :dependencies [[compojure "1.3.3"]
                                  [clj-http  "2.0.0"]]}})
