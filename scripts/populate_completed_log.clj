(ns populate-completed-log
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tract.config :as config]
            [clj-yaml.core :as yaml])
  (:gen-class))

(defn- get-markdown-files []
  "Finds all .md files in the processed output directory."
  (let [processed-dir (io/file (config/processed-dir-path))]
    (if (.exists processed-dir)
      (->> (.listFiles processed-dir)
           (filter #(.isFile %))
           (filter #(str/ends-with? (.getName %) ".md")))
      [])))

(defn- extract-url-from-md [md-file]
  "Safely extracts the source_url from a markdown file's YAML front matter."
  (try
    (let [content (slurp md-file)
          front-matter-re #"(?ms)^---\n(.*?)\n---"
          front-matter-str (some-> (re-find front-matter-re content) second)]
      (when front-matter-str
        (:source_url (yaml/parse-string front-matter-str :keywords true))))
    (catch Exception e
      (println (str "\nWARN: Could not parse YAML from " (.getName md-file) ". Skipping."))
      nil)))

(defn -main [& args]
  (println "--- Running 'completed.log' Backfill Script ---")
  (let [md-files (get-markdown-files)
        total-files (count md-files)
        completed-log-path (str (io/file (config/work-dir) "completed.log"))]
    (if (zero? total-files)
      (println "-> No processed markdown files found. Nothing to do.")
      (do
        (println (str "-> Found " total-files " processed markdown files to scan..."))
        (let [urls (->> md-files
                        (map extract-url-from-md)
                        (remove nil?)
                        (sort)
                        (dedupe))
              url-count (count urls)]
          (println (str "-> Extracted " url-count " unique source URLs."))
          (println (str "-> Writing to " completed-log-path))
          (spit completed-log-path (str/join "\n" urls))
          (println "\n--- Backfill Complete ---")))))
  (shutdown-agents))
