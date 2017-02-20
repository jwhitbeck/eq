(defproject eq "0.2.5"
  :description "A cli tool for processing EDN data."
  :url "https://github.com/jwhitbeck/eq"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/jwhitbeck/eq"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.473"]
                 [org.clojure/core.async "0.2.395"]
                 [org.clojure/tools.cli "0.3.5"]
                 [cljsjs/nodejs-externs "1.0.4-1"]]
  :plugins [[lein-cljsbuild "1.1.5"]]
  :source-paths ["src"]
  :hooks [leiningen.cljsbuild]
  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.1"]
                                  [org.clojure/tools.nrepl "0.2.12"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}
  :cljsbuild {:builds [{:source-paths ["src"]
                        :compiler {:target :nodejs
                                   :output-to "eq.js"
                                   :optimizations :advanced}}]})
