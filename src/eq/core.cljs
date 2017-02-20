;; Copyright (c) 2014-2016 John Whitbeck. All rights reserved.
;;
;; The use and distribution terms for this software are covered by the
;; Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt)
;; which can be found in the file al-v20.txt at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;;
;; You must not remove this notice, or any other, from this software.

(ns eq.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [>! chan <! close!]]
            [cljs.pprint :as pprint]
            [cljs.nodejs :as nodejs]
            [cljs.reader :as edn]
            [cljs.tools.cli :as cli]
            [clojure.string :as string]))

(def fs (js/require "fs"))

(deftype PushbackReader [fd pb-buf buf ^:mutable string ^:mutable pos]
  edn/PushbackReader
  (read-char [_]
    (cond
      (pos? (alength pb-buf)) (.pop pb-buf)
      (and string (< pos (.-length string))) (let [c (get string pos)]
                                               (set! pos (inc pos))
                                               c)
      :else (let [nread (.readSync fs fd buf 0 (.-length buf) nil)]
              (when (pos? nread)
                (set! string (.toString buf "utf8" 0 nread))
                (set! pos 1)
                (get string 0)))))
  (unread [_ c]
    (.push pb-buf c)))

(defn pushback-reader [fd]
  (PushbackReader. fd (array) (js/Buffer. (* 8 1024)) nil 0))

;;; Blanket support for preserving edn tags
(deftype Tagged [obj tag])

(extend-protocol IPrintWithWriter
  Tagged
  (-pr-writer [tagged-obj writer opts]
    (-write writer (str "#" (.-tag tagged-obj) " "))
    (-pr-writer (.-obj tagged-obj) writer opts)))

(defmethod pprint/simple-dispatch :default
  [obj]
  (if (instance? Tagged obj)
    (do (-write *out* (str "#" (.-tag obj) " "))
        (pprint/simple-dispatch (.-obj obj)))
    (-write *out* (pr-str obj))))

(extend-type Tagged
  ILookup
  (-lookup
    ([tagged-obj k]
     (-lookup (.-obj tagged-obj) k))
    ([tagged-obj k not-found]
     (-lookup (.-obj tagged-obj) k not-found))))

(defn tagged? [x] (instance? Tagged x))

(edn/register-default-tag-parser!
 (fn [tag x]
   (Tagged. x tag)))

(def cli-options
  [["-c" "--compact" "Compact output, don't pretty-print"]
   ["-d" "--dissoc K" "Apply #(dissoc % K) to each parsed edn object."
    :default []
    :assoc-fn (fn [m k v] (update-in m [k] conj (edn/read-string v)))]
   [nil "--apply-dissoc KS" "Apply #(apply dissoc % KS) to each parsed edn object."
    :default []
    :assoc-fn (fn [m k v] (update-in m [k] conj (edn/read-string v)))]
   ["-g" "--get K" "Apply #(get % K) to each parsed edn object. "
    :default []
    :assoc-fn (fn [m k v] (update-in m [k] conj (edn/read-string v)))]
   [nil "--get-in KS" "Apply #(get-in % KS) to each parsed edn object."
    :default []
    :assoc-fn (fn [m k v] (update-in m [k] conj (edn/read-string v)))]
   ["-s" "--select-keys KS" "Apply #(select-keys % KS) to each parsed edn object."
    :default []
    :assoc-fn (fn [m k v] (update-in m [k] conj (edn/read-string v)))]
   ["-v" "--version" "Prints the eq version"]
   ["-h" "--help"]])

(defn print-usage [summary]
  (println (str "Usage: eq [OPTIONS]" "\n\n" summary "\n\n"
                "Multiple --dissoc, --apply-dissoc, --get, --get-in, --select-keys options may be passed, "
                "in which case the output of each will be on a separate line.")))

(defn print-fn [options]
  (let [pr-fn (if (:compact options) prn pprint/pprint)
        extractors (concat (map #(fn [x] (dissoc x %)) (:dissoc options))
                           (map #(fn [x] (apply dissoc x %)) (:apply-dissoc options))
                           (map #(fn [x] (get x %)) (:get options))
                           (map #(fn [x] (get-in x %)) (:get-in options))
                           (map #(fn [x] (select-keys x %)) (:select-keys options)))]
    (if (seq extractors)
      (fn [x]
        (doseq [extractor extractors]
          (pr-fn (extractor x))))
      pr-fn)))

(defn node-setup! []
  ;; Don't use console.log for stdout to avoid unnecessary newlines.
  (set! *print-fn*
        (fn [& args]
          (.apply (.-write js/process.stdout) js/process.stdout (into-array args))))
  (set! *print-err-fn*
        (fn [& args]
          (.apply (.-write js/process.stderr) js/process.stderr (into-array args))))
  ;; Exit on closed stdout, e.g., `cat <file> | eq | head`.
  (.on js/process.stdout "error" js/process.exit))

(defn -main [& args]
  (node-setup!)
  (let [parsed-opts (cli/parse-opts args cli-options)
        options (:options parsed-opts)]
    (cond
     (or (:help options) (-> parsed-opts :errors empty? not)) (print-usage (:summary parsed-opts))
     (:version options) (println "0.2.4")
     :else (let [pr-fn (print-fn options)
                 fd (.openSync fs "/dev/stdin" "rs")]
             (go (try
                   (loop [r (pushback-reader fd)]
                     (let [obj (<! (go (edn/read r false ::eof false)))]
                       (when-not (= obj ::eof)
                         (pr-fn obj)
                         (recur r))))
                   (finally
                     (.closeSync fs fd))))))))

(set! *main-cli-fn* -main)
