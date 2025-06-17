;; File: scripts/recover_md_front_matter.clj
(ns recover-md-front-matter
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tract.config :as config]
            [cheshire.core :as json]
            [clj-yaml.core :as yaml])
  (:gen-class))

(defn- get-slug-from-key
  "Extracts the slug part from a 'YYYY-MM-DD_slug' key."
  [article-key]
  (when article-key
    (second (str/split article-key #"_" 2))))

(defn- get-correct-url-from-slug
  "Finds the correct source_url from the canonical metadata file."
  [slug]
  (when slug
    (let [meta-filename (str slug ".html.meta.json")
          meta-file (io/file (config/metadata-dir-path) meta-filename)]
      (when (.exists meta-file)
        (try
          (let [meta-data (json/parse-string (slurp meta-file) true)]
            ;; The key here is :source-url, as written by the fetcher.
            (:source-url meta-data))
          (catch Exception e
            (println (str "WARN: Could not parse metadata for " slug))
            nil))))))

(defn- rewrite-file-with-correct-url!
  "Performs a surgical string replacement on the file content."
  [md-file correct-url]
  (let [original-content (slurp md-file)
        target-string "source_url: unknown"
        replacement-string (str "source_url: " correct-url)
        new-content (str/replace original-content target-string replacement-string)]
    (spit md-file new-content)))

(defn -main [& args]
  (println "--- Markdown Front Matter Recovery Script ---")
  (let [processed-dir (io/file (config/processed-dir-path))
        backup-dir (io/file (config/work-dir) "3-processed-backup")]

    (if (.exists backup-dir)
      (println (str "-> Backup directory already exists at " backup-dir ". Skipping backup."))
      (do
        (println (str "-> Creating backup of processed directory at " backup-dir))
        (.mkdirs backup-dir)
        (doseq [f (.listFiles processed-dir)]
          (io/copy f (io/file backup-dir (.getName f))))))

    (let [md-files (->> (.listFiles processed-dir)
                        (filter #(str/ends-with? (.getName %) ".md")))]
      (println (str "-> Scanning " (count md-files) " markdown files for 'source_url: unknown'..."))
      (let [files-to-fix (filter #(str/includes? (slurp %) "source_url: unknown") md-files)
            fix-count (count files-to-fix)]
        (if (zero? fix-count)
          (println "-> No corrupted files found.")
          (do
            (println (str "-> Found " fix-count " files to correct."))
            (doseq [md-file files-to-fix]
              (println (str "  -> Fixing " (.getName md-file)))
              (let [s (->> (slurp md-file)
                           (re-find #"(?ms)^---\n(.*?)\n---"))
                    front-matter (-> s
                                     second
                                     (yaml/parse-string :keywords true))
                    article-key (:article_key front-matter)
                    slug (get-slug-from-key article-key)
                    correct-url (get-correct-url-from-slug slug)]
                (if correct-url
                  (rewrite-file-with-correct-url! md-file correct-url)
                  (println (str "     WARN: Could not find metadata for slug '" slug "'. Skipping file.")))))))
          (println "\n--- Recovery Complete ---")
          (println (str "Successfully processed and rewrote " fix-count " files."))
        )))
  (shutdown-agents))
