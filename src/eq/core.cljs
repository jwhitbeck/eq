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

(enable-console-print!)

(def fs (js/require "fs"))

(defprotocol BufferedReader
  (read-char [_]))

(def buffer-size (* 8 1024))

(deftype FileDescriptorBufferedReader [fd buffer ^:mutable string-buffer ^:mutable pos ^:mutable length]
  BufferedReader
  (read-char [_]
    (if (< pos length)
      (let [ch (get string-buffer pos)]
        (set! pos (inc pos))
        ch)
      (let [num-bytes-read (.readSync fs fd buffer 0 buffer-size nil)]
        (when (pos? num-bytes-read)
          (set! string-buffer (.toString buffer "utf-8" 0 num-bytes-read))
          (set! length (.-length string-buffer))
          (set! pos 1)
          (get string-buffer 0))))))

(defn buffered-reader [fd]
  (FileDescriptorBufferedReader. fd (js/Buffer. buffer-size) nil 0 0))

(deftype BufferedPushbackReader [fdbr buffer]
  edn/PushbackReader
  (read-char [_]
    (if (zero? (alength buffer))
      (read-char fdbr)
      (.pop buffer)))
  (unread [_ ch]
    (.push buffer ch)))

(defn pushback-reader [fdbr]
  (BufferedPushbackReader. fdbr (array)))

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

;;; Workaround the fact that the javascript console always prints a newline
(defn pr-pretty [obj]
  (-> (with-out-str (pprint/pprint obj))
      string/trim-newline
      print))

(defn print-fn [options]
  (let [pr-fn (if (:compact options) pr pr-pretty)
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

(defn -main [& args]
  (let [parsed-opts (cli/parse-opts args cli-options)
        options (:options parsed-opts)]
    (cond
     (or (:help options) (-> parsed-opts :errors empty? not)) (print-usage (:summary parsed-opts))
     (:version options) (println "0.2.4")
     :else (let [pr-fn (print-fn options)
                 pbrdr (-> (.openSync fs "/dev/stdin" "rs") buffered-reader pushback-reader)]
             (go-loop [obj (edn/read pbrdr false ::eof false)]
               (when-not (= obj ::eof)
                 (pr-fn obj)
                 (recur (edn/read pbrdr false ::eof false))))))))

(set! *main-cli-fn* -main)
