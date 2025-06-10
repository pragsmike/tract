(ns tract.core
  (:require [tract.parser :as parser]
            [tract.compiler :as compiler]
            [tract.io :as io]
            [clojure.java.io :as jio])
  (:import [java.io File])
  (:gen-class))

(def input-dir "work/1-fetched/pending")
(def done-dir "work/1-fetched/done")
(def output-dir "work/2-processed")

(defn- process-file!
  "Processes a single HTML file from the input directory."
  [input-file]
  (println "-> Processing file:" (.getName input-file))
  (try
    (let [html-string (slurp input-file)
          parsed-data (parser/parse-html html-string)
          {:keys [article images]} (compiler/compile-to-article parsed-data)
          output-path (jio/file output-dir)
          md-file (jio/file output-path (str (:article_key (:metadata article)) ".md"))]

      (.mkdirs output-path)
      ;; **FIXED**: Pass the generated file object directly in the map
      (io/write-article! (assoc article :output-file md-file))

      (println "-> Processing" (count images) "images...")
      (doseq [job images]
        (let [job-with-output-dir (update job :image_path #(jio/file output-path %))]
          (io/download-image! job-with-output-dir)))

      (let [dest-file (jio/file done-dir (.getName input-file))]
        (println "-> Archiving processed file to:" dest-file)
        (.renameTo input-file dest-file)))

    (catch Exception e
      (println (str "ERROR: Failed to process " (.getName input-file) ". See details below."))
      (.printStackTrace e))))

(defn -main
  "Main entry point for the processor step. Scans a directory for work."
  [& args]
  (println "--- Starting Processor Step ---")
  (let [pending-dir (jio/file input-dir)
        files-to-process (when (.exists pending-dir)
                           (->> (.listFiles pending-dir)
                                (filter #(and (.isFile %) (.endsWith (.getName %) ".html")))))]
    (.mkdirs (jio/file done-dir))

    (if (empty? files-to-process)
      (println "No new files to process in" input-dir)
      (do
        (println "Found" (count files-to-process) "file(s) to process...")
        (doseq [file files-to-process]
          (process-file! file)))))
  (println "--- Processor Step Complete ---"))
