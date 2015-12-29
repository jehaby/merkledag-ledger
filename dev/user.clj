(ns user
  (:require
    [blocks.core :as block]
    (clj-time
      [coerce :as ctime]
      [core :as time]
      [format :as ftime])
    [clojure.data :refer [diff]]
    [clojure.java.io :as io]
    [clojure.repl :refer :all]
    [clojure.stacktrace :refer [print-cause-trace]]
    [clojure.string :as str]
    [clojure.tools.namespace.repl :refer [refresh]]
    [instaparse.core :as insta]
    (merkledag
      [core :as merkle]
      [graph :as graph])
    (merkledag.data.finance
      [parse :as parse]
      [print :as print]
      [quantity :as quantity])
    [puget.printer :as puget]))


(def print-options
  {:print-color true
   :print-handlers
   {java.util.UUID (puget/tagged-handler 'uuid str)
    merkledag.data.finance.quantity.Quantity (puget/tagged-handler 'finance/$ (juxt :value :commodity))
    org.joda.time.DateTime (puget/tagged-handler 'inst str)
    org.joda.time.LocalDate (puget/tagged-handler 'time/date str)
    org.joda.time.Interval (puget/tagged-handler 'time/interval #(vector (time/start %) (time/end %)))}})


(defn human-duration
  "Converts a time duration in milliseconds to a human-friendly string."
  [elapsed]
  (cond
    (> elapsed 60000)
      (format "%d:%04.1f" (int (/ elapsed 60000)) (mod (/ elapsed 1000) 60))
    (> elapsed 1000)
      (format "%.3f seconds" (/ elapsed 1000))
    :else
      (format "%.3f ms" elapsed)))


(defn find-groups
  "Searches through the groups in a file to find ones which match the given
  pattern. Returns a sequence of indices for the matching groups."
  [file pattern]
  (->> file io/file io/reader line-seq parse/group-lines
       (keep-indexed #(when (re-seq pattern %2) %1))))


(defn get-group
  "Get a line group out of a file for testing."
  [file index]
  (-> file io/file io/reader line-seq parse/group-lines (nth index)))


(defn try-parsing
  "Attempts to parse the given text using the current parser. Returns true if
  the text parsed successfully, false on error."
  ([text]
   (try-parsing text 0 true))
  ([text index show?]
   (try
     ; If showing this example, explicitly print input
     (when show?
       (printf "\nParsing entry %d:\n\n%s\n" index text))
     ; Try parsing the text
     (let [parses (insta/parses parse/ledger-parser text)]
       (cond
         ; On failure, print out input and error message
         (insta/failure? parses)
           (do (printf "\nParsing entry %d failed:\n\n" index)
               (when-not show? (println text ""))
               (puget/cprint (insta/get-failure parses))
               false)

         ; If parsing is ambiguous, print first two and diff
         (< 1 (count parses))
           (do (printf "\nParsing entry %d is ambiguous (%d parses):\n\n"
                       index (count parses))
               (when-not show? (println text ""))
               (puget/cprint (take 2 parses))
               (println "\nDifferences:")
               (puget/cprint (diff (first parses) (second parses)))
               false)

           ; Try interpreting the parse
           :else
             (let [interpreted (parse/interpret-parse (first parses))]
               ; If showing, explicitly print conversion:
               (when show?
                 (println "Parsed:")
                 (puget/cprint (first parses))
                 (println)
                 (println "Interpreted:")
                 (puget/cprint interpreted print-options))
                 true)))
     (catch Exception e
       (printf "\nParsing entry %d failed:\n\n" index)
       (when-not show? (println text ""))
       (print-cause-trace e)
       false))))


(defn inspect-file
  "Inspects the parsing of a group in the given file. If no index is given, one
  is selected at random."
  ([file]
   (inspect-file file nil))
  ([file index]
   (let [groups (-> file io/file io/reader line-seq parse/group-lines)
         index (or index (rand-int (count groups)))]
     (try-parsing (nth groups index) index true))))


(defn test-parser
  "Tests the parser by running it against the line groups in the given file.
  Any extra arguments will explicitly print out the results of parsing the
  groups at those indices."
  [file & show-entries]
  (let [groups (-> file io/file io/reader line-seq parse/group-lines)
        show-entries (set show-entries)
        error-limit 5
        start (System/nanoTime)
        get-elapsed #(/ (- (System/nanoTime) start) 1000000.0)]
    (loop [entries groups
           index 0
           errors 0]
      (cond
        ; Hit error limit
        (>= errors error-limit)
          (let [total (+ index (count entries))]
            (printf "\nStopping after %d errors at entry %d/%d (%.1f%%) in %s\n"
                    error-limit index total (* (/ index total) 100.0)
                    (human-duration (get-elapsed))))

        ; Parse next entry
        (seq entries)
          (let [success? (try-parsing (first entries) index (show-entries index))]
            (recur (rest entries) (inc index) (if success? errors (inc errors))))

        ; Parsed everything without hitting error limit
        :else
          (do (printf "\nParsed %d entries with %d errors in %s\n"
                      index errors (human-duration (get-elapsed)))
              (zero? errors))))))


(defn reload-grammar!
  "Recreate the Ledger parser by loading the grammar file."
  []
  (alter-var-root #'parse/ledger-parser (constantly (insta/parser (io/resource "grammar/ledger.bnf"))))
  :reloaded)
