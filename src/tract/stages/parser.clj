;; File: src/tract/stages/parser.clj
(ns tract.stages.parser
  (:require [tract.pipeline :as pipeline]
            [tract.parser :as parser-logic]
            [tract.compiler :as compiler]
            [tract.io :as io]
            [tract.config :as config]
            [clojure.java.io :as jio]
            [tract.db :as db]
            [cheshire.core :as json]))

(def ^:private stage-name :parser)
(def ^:private output-dir (config/processed-dir-path))

(defn- process-html-file!
  "Processes a single HTML file from the pending directory."
  [html-file]
  (println (str "-> Processing HTML file: " (.getName html-file)))
  (try
    (let [;; --- MODIFIED METADATA LOOKUP LOGIC ---
          html-string (slurp html-file)
          html-filename (.getName html-file)
          ;; Derive the metadata filename and look in the central directory
          meta-filename (str html-filename ".meta.json")
          meta-file (jio/file (config/metadata-dir-path) meta-filename)]

      (if-not (.exists meta-file)
        (throw (ex-info (str "Missing metadata file: " meta-filename) {:html-file html-filename}))
        (let [meta-data (json/parse-string (slurp meta-file) true)
              source-url (:source_url meta-data)
              ;; --- END MODIFIED LOGIC ---
              parsed-data (parser-logic/parse-html html-string source-url)
              {:keys [article images]} (compiler/compile-to-article parsed-data)
              output-path (jio/file output-dir)]
          (.mkdirs output-path)
          (let [md-file (jio/file output-path (str (:article_key (:metadata article)) ".md"))]
            (io/write-article! (assoc article :output-file md-file)))

          (println (str "\t-> Processing " (count images) " images for " (:article_key (:metadata article))))
          (doseq [job images]
            (let [job-with-output-dir (update job :image_path #(jio/file output-path %))]
              (io/download-image! job-with-output-dir)))

          (let [metadata (:metadata article)]
            (db/record-completion! {:post-id       (:post_id metadata)
                                    :source-url    (:source_url metadata)
                                    :canonical-url (:canonical_url metadata)})))))

    (pipeline/move-to-done! html-file stage-name)
    (catch Exception e
      (pipeline/move-to-error! html-file stage-name e))))

(defn run-stage!
  "Main entry point for the parser stage. Scans for and processes HTML files."
  []
  (println "--- Running Parser Stage ---")
  (let [pending-files (pipeline/get-pending-files stage-name)]
    (if (empty? pending-files)
      (println "No HTML files to process.")
      (do
        (println "Found" (count pending-files) "HTML file(s).")
        (doseq [file pending-files]
          (process-html-file! file)))))
  (println "--- Parser Stage Complete ---"))
