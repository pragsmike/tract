(ns build-metadata-cache
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint]
            [clj-yaml.core :as yaml]
            [tract.config :as config]
            [tract.util :as util])
  (:import [java.io File])
  (:gen-class))

(defn- extract-yaml-front-matter
  "Reads an .md file and returns the YAML front matter block as a single string.
  Returns nil if the file doesn't start with a '---' line."
  [^File md-file]
  (with-open [reader (io/reader md-file)]
    (let [lines (line-seq reader)
          first-line (first lines)]
      (when (= (str/trim first-line) "---")
        (->> (rest lines)
             (take-while #(not= (str/trim %) "---"))
             (str/join "\n"))))))

(defn- process-md-file
  "Processes a single markdown file to extract its metadata for the cache.
  Returns a metadata map on success, or nil on failure."
  [^File md-file]
  (let [filename (.getName md-file)
        slug (str/replace filename #"\.md$" "")]
    (if-let [yaml-str (extract-yaml-front-matter md-file)]
      (try
        (let [;; The :keywords true option is crucial to conform to the
              ;; Kebab-Case Imperative.
              parsed-yaml (yaml/parse-string yaml-str :keywords true)
              source-url (get parsed-yaml :source-url)]
          {:slug             slug
           :path             (.getAbsolutePath md-file)
           :title            (get parsed-yaml :title "Untitled")
           :author           (get parsed-yaml :author "Unknown")
           :publication-date (get parsed-yaml :publication-date)
           :domain           (when source-url (util/extract-domain source-url))})
        (catch Exception e
          (println (str "WARN: Failed to parse YAML for " filename ". Skipping. Error: " (.getMessage e)))
          nil))
      (do
        (println (str "WARN: No YAML front matter found in " filename ". Skipping."))
        nil))))

(defn -main
  [& args]
  (println "--- Building Metadata Cache for Processed Articles ---")
  (let [processed-dir (io/file (config/processed-dir-path))
        output-cache-file (io/file (config/work-dir) "metadata-cache.edn")]

    (if-not (.exists processed-dir)
      (println (str "ERROR: Processed directory not found at '" processed-dir "'. Cannot build cache."))
      (let [md-files (->> (.listFiles processed-dir)
                          (filter #(and (.isFile %) (str/ends-with? (.getName %) ".md"))))
            _ (println (str "-> Found " (count md-files) " markdown files to process..."))
            ;; Using `doall` forces evaluation now, so all warnings appear before the final output.
            metadata-list (->> (map process-md-file md-files)
                               (remove nil?)
                               (doall))]

        (println (str "-> Writing cache for " (count metadata-list) " articles to " output-cache-file))
        ;; Use pr-str for pretty, human-readable EDN output
        (spit output-cache-file (with-out-str (clojure.pprint/pprint metadata-list)))
        (println "--- Cache build complete. ---"))))
  (shutdown-agents))
