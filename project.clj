(defproject eq "0.1.0"
  :description "A cli tool for processing EDN data."
  :url "https://github.com/jwhitbeck/eq"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/jwhitbeck/eq"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2371"]
                 [org.clojure/tools.cli "0.3.1"]]
  :plugins [[lein-cljsbuild "1.0.3"]]
  :source-paths ["src"]
  :hooks [leiningen.cljsbuild]
  :cljsbuild {:builds [{:source-paths ["src"]
                        :notify-command ["./post-build.sh"]
                        :compiler {:target :nodejs
                                   :output-to "eq"
                                   :externs ["src/eq/externs.js"]
                                   :pretty-print false
                                   :optimizations :advanced}}]})
