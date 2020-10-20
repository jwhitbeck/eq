(ns net.whitbeck.eq
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.tools.cli :as cli]))

(set! *warn-on-reflection* true)

(defn- usage [summary]
  (str "Process EDN data (https://github.com/edn-format/edn) from stdin and
write it to stdout.

Usage: eq [OPTIONS]

" summary "

Multiple --dissoc, --apply-dissoc, --get, --get-in, --select-keys options may be
passed, in which case the output of each will be on a separate line."))

(defn- conj-opt-val [m k v]
  (update m k (fnil conj []) (edn/read-string v)))

(def ^:private option-specs
  [["-h" "--help" "Display this message."
    :id :help?]
   ["-c" "--compact" "Compact output, don't pretty print."
    :id :compact?]
   ["-r" "--readable" "Print readable EDN data."
    :id :readable?]
   ["-d" "--dissoc K" "Apply #(dissoc % K) to each parsed EDN object."
    :id :dissoc-keys
    :assoc-fn conj-opt-val]
   [nil "--apply-dissoc KS" "Apply #(apply dissoc % KS) to each parsed EDN object."
    :id :apply-dissoc-keyvecs
    :assoc-fn conj-opt-val]
   ["-g" "--get K" "Apply #(get % K) to each parsed EDN object. "
    :id :get-keys
    :assoc-fn conj-opt-val]
   [nil "--get-in KS" "Apply #(get-in % KS) to each parsed EDN object."
    :id :get-in-keyvecs
    :assoc-fn conj-opt-val]
   ["-s" "--select-keys KS" "Apply #(select-keys % KS) to each parsed EDN object."
    :id :select-keys-keyvecs
    :assoc-fn conj-opt-val]])

(defn- exit-err! [msg-or-msgs]
  (binding [*out* *err*]
    (if (coll? msg-or-msgs)
      (run! println msg-or-msgs)
      (println msg-or-msgs)))
  (System/exit 1))

(defn- stdin-seq []
  (lazy-seq
    (let [val (edn/read {:default (fn [_ x] x)
                         :eof ::eof}
                        *in*)]
      (when-not (= ::eof val)
        (cons val (stdin-seq))))))

(defn- get-print-fn [options]
  (let [pr-fn (if (:compact? options) prn pp/pprint)
        extractors (vec (concat
                          (map #(fn [x] (dissoc x %))
                               (:dissoc-keys options))
                          (map #(fn [x] (apply dissoc x %))
                               (:apply-dissoc-keyvecs options))
                          (map #(fn [x] (get x %))
                               (:get-keys options))
                          (map #(fn [x] (get-in x %))
                               (:get-in-keyvecs options))
                          (map #(fn [x] (select-keys x %))
                               (:select-keys-keyvecs options))))]
    (if (seq extractors)
      (fn [x]
        (doseq [extractor extractors]
          (pr-fn (extractor x))))
      pr-fn)))

(defn- eq [options]
  (let [print-fn (get-print-fn options)]
    (binding [*print-readably* (:readable? options)]
      (run! print-fn (stdin-seq)))))

(defn -main [& args]
  (let [{:keys [options
                summary
                errors
                arguments]} (cli/parse-opts args option-specs)]
    (when (:help? options)
      (println (usage summary))
      (System/exit 0))
    (when (seq errors)
      (exit-err! errors))
    (when (seq arguments)
      (exit-err! "Too many arguments provided."))
    (eq options)))
