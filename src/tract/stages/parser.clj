;; File: src/tract/stages/parser.clj
(ns tract.stages.parser
  (:require [tract.pipeline :as pipeline]
            [tract.parser :as parser-logic]
            [tract.compiler :as compiler]
            [tract.io :as io]
            [tract.config :as config]
            [clojure.java.io :as jio]
            [clojure.string :as str]
            [tract.db :as db]
            [cheshire.core :as json]))

(def ^:private stage-name :parser)
(def ^:private output-dir (config/processed-dir-path))

(defn ->fetcher-metadata
  "Returns fetcher's metadata for the given html file.
      :source-url
      :fetch-timestamp
  "
  [html-filename]
  (let [meta-filename (str html-filename ".meta.json")
        meta-file (jio/file (config/metadata-dir-path) meta-filename)]
    (when-not (.exists meta-file)
      (throw (ex-info (str "Missing metadata file: " meta-filename) {:html-file html-filename})))
    (json/parse-string (slurp meta-file) true)))

(defn full-metadata [metadata fetcher-metadata]
  ;; CORRECTED: Use kebab-case keys for all metadata access and creation.
  (let [article-key (let [slug (:post-id metadata)]
                      (if (str/blank? slug)
                        (str "unknown-article_" (System/currentTimeMillis))
                        slug))
        metadata (assoc metadata
                        :title (or (:title metadata) "Untitled")
                        :publication-date (or (:publication-date metadata) "unknown")
                        :source-url (or (:source-url metadata) "unknown")
                        :article-key article-key)]
    (merge metadata fetcher-metadata)))

(defn- process-html-file!
  "Processes a single HTML file from the pending directory."
  [html-file]
  (println (str "-> Processing HTML file: " (.getName html-file)))
  (try
    (let [{:keys [metadata body-nodes]} (parser-logic/parse-html (slurp html-file))
          fetcher-meta-data (->fetcher-metadata (.getName html-file))
          metadata (full-metadata metadata fetcher-meta-data)
          {:keys [markdown images]} (compiler/compile-to-article metadata body-nodes)
          output-path (jio/file output-dir)]

      (.mkdirs output-path)
      (let [md-file (jio/file output-path (str (:article-key metadata) ".md"))]
        (io/write-article! md-file markdown))

      (println (str "\t-> Processing " (count images) " images for " (:article-key metadata)))
      (doseq [job images]
        (let [job-with-output-dir (update job :image-path #(jio/file output-path %))]
          (io/download-image! job-with-output-dir)))

      (db/record-completion! (select-keys metadata [:post-id :source-url :canonical-url])))

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
