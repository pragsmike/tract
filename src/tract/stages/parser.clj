(ns tract.stages.parser
  (:require [tract.pipeline :as pipeline]
            [tract.parser :as parser-logic]
            [tract.compiler :as compiler]
            [tract.io :as io]
            [tract.config :as config]
            [clojure.java.io :as jio]))

(def ^:private stage-name :parser)
(def ^:private output-dir (config/processed-dir-path))

(defn- process-html-file!
  "Processes a single HTML file from the pending directory."
  [html-file]
  (println (str "-> Processing HTML file: " (.getName html-file)))
  (try
    (let [html-string (slurp html-file)
          parsed-data (parser-logic/parse-html html-string)
          {:keys [article images]} (compiler/compile-to-article parsed-data)
          output-path (jio/file output-dir)]
      (.mkdirs output-path)
      (let [md-file (jio/file output-path (str (:article_key (:metadata article)) ".md"))]
        (io/write-article! (assoc article :output-file md-file)))

      (println (str "\t-> Processing " (count images) " images for " (:article_key (:metadata article))))
      (doseq [job images]
        (let [job-with-output-dir (update job :image_path #(jio/file output-path %))]
          (io/download-image! job-with-output-dir))))

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
