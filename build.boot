(set-env! :dependencies '[[org.clojure/clojure "1.8.0"]
                          [org.clojure/clojurescript "1.9.473"]
                          [org.clojure/core.async "0.2.395"]
                          [org.clojure/tools.cli "0.3.5"]
                          [cljsjs/nodejs-externs "1.0.4-1"]
                          [adzerk/boot-cljs "2.0.0"]
                          [com.cemerick/piggieback "0.2.1"]
                          [org.clojure/tools.nrepl "0.2.12"]]
          :source-paths #{"src"})

(require 'boot.repl
         '[adzerk.boot-cljs :refer [cljs]])

(swap! boot.repl/*default-middleware* conj 'cemerick.piggieback/wrap-cljs-repl)

(deftask build
  []
  (comp (cljs :compiler-options {:target :nodejs :optimizations :advanced})
        (sift :include #{#"main.js"})
        (target :dir #{"js"})))
