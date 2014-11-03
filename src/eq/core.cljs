;; Copyright (c) 2014 John Whitbeck. All rights reserved.
;;
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.txt at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;;
;; You must not remove this notice, or any other, from this software.

(ns eq.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.nodejs :as nodejs]
            [cljs.tools.cli :as cli]
            [cljs.reader :as edn]
            [cljs.core.async :refer [>! chan <! close!]]))

(nodejs/enable-util-print!)
(.on js/process.stdout "error" js/process.exit) ; exit on closed stdout

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

(extend-protocol ILookup
  Tagged
  (-lookup [tagged-obj k]
    (-lookup (.-obj tagged-obj) k))
  (-lookup [tagged-obj k not-found]
    (-lookup (.-obj tagged-obj) k not-found)))

(defn tagged? [x] (instance? Tagged x))

(edn/register-default-tag-parser!
 (fn [tag x]
   (Tagged. x tag)))

;;; Primitive pretty-printing functionality.
;;; TODO: Replace with cljs.core.pprint once it is implemented
(declare pprint)

(def indent "  ")

(defn pprint-list-delimited [coll begin-delim end-delim current-indent indent?]
  (if (seq coll)
    (do
      (when indent?
        (print current-indent))
      (println begin-delim)
      (let [ind (str current-indent indent)]
        (doseq [x coll]
          (pprint x ind true)
          (println)))
      (print current-indent)
      (print end-delim))
    (print (str begin-delim end-delim))))

(defn pprint-map [coll current-indent indent?]
  (if (seq coll)
    (do
      (when indent?
        (print current-indent))
      (println "{")
      (let [ind (str current-indent indent)]
        (doseq [[k v] coll]
          (pprint k ind true)
          (print " ")
          (pprint v ind false)
          (println)))
      (print current-indent)
      (print "}"))
    (print (str "{}"))))

(defn pprint-tagged [obj current-indent indent?]
  (when indent?
    (print current-indent))
  (print (str "#" (.-tag obj) " "))
  (pprint (.-obj obj) current-indent false))

(defn pprint-default [obj current-indent indent?]
  (when indent?
    (print current-indent))
  (pr obj))

(defn pprint [obj current-indent indent?]
  (cond
   (map? obj) (pprint-map obj current-indent indent?)
   (vector? obj) (pprint-list-delimited obj "[" "]" current-indent indent?)
   (set? obj) (pprint-list-delimited obj "#{" "}" current-indent indent?)
   (list? obj) (pprint-list-delimited obj "(" ")" current-indent indent?)
   (tagged? obj) (pprint-tagged obj current-indent indent?)
   :else (pprint-default obj current-indent indent?)))

(defn escape-nil [x] (if (nil? x) ::nil x))
(defn unescape-nil [x] (if (= ::nil x) nil x))

(defn edn-ch [pbrdr]
  (let [ch (chan)
        read-next #(edn/read pbrdr false ::eof false)]
    (go (loop [obj (read-next)]
          (if (= obj ::eof)
            (close! ch)
            (do (>! ch (escape-nil obj))
                (recur (read-next))))))
    ch))

(def cli-options
  [["-c" "--compact" "Compact output, don't pretty-print"]
   ["-g" "--get-in KS"
    (str "Apply #(get-in % KS) to each parsed edn object. Multiple such --get-in options may be passed, "
         "in which case each get-in will output to a separate line.")
    :default []
    :assoc-fn (fn [m k v] (update-in m [k] conj (edn/read-string v)))]
   ["-v" "--version" "Prints the eq version"]
   ["-h" "--help"]])

(defn print-usage [summary]
  (println (str "Usage: eq [OPTIONS]" "\n" summary)))

(defn print-fn [options]
  (let [get-ins (:get-in options)
        pr-fn (if (:compact options) pr #(pprint % "" false))]
    (if (seq get-ins)
      (fn [obj]
        (doseq [ks get-ins]
          (pr-fn (get-in obj ks))
          (println)))
      (fn [obj]
        (pr-fn obj)
        (println)))))

(defn -main [& args]
  (let [parsed-opts (cli/parse-opts args cli-options)
        options (:options parsed-opts)]
    (cond
     (or (:help options) (-> parsed-opts :errors empty? not)) (print-usage (:summary parsed-opts))
     (:version options) (println "0.1.1")
     :else (let [pr-fn (print-fn options)
                 ch (-> (.openSync fs "/dev/stdin" "rs") buffered-reader pushback-reader edn-ch)]
             (go (loop [obj (<! ch)]
                   (when obj
                     (pr-fn (unescape-nil obj))
                     (recur (<! ch)))))))))

(set! *main-cli-fn* -main)
