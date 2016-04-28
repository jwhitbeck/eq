(defproject eq "0.2.1"
  :description "A cli tool for processing EDN data."
  :url "https://github.com/jwhitbeck/eq"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/jwhitbeck/eq"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.145"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/tools.cli "0.3.3"]]
  :plugins [[lein-cljsbuild "1.1.0"]]
  :source-paths ["src"]
  :hooks [leiningen.cljsbuild]
  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.1"]
                                  [org.clojure/tools.nrepl "0.2.12"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}
  :cljsbuild {:builds [{:source-paths ["src"]
                        :notify-command ["./post-build.sh"]
                        :compiler {:target :nodejs
                                   :output-to "eq"
                                   :externs ["src/eq/externs.js"]
                                   :pretty-print false
                                   :optimizations :advanced}}]})
