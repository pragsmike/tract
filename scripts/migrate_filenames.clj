;; File: scripts/migrate_filenames.clj
(ns migrate-filenames
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tract.config :as config]
            [tract.util :as util]
            [tract.parser :as parser] ;; <-- NEW require
            [clj-yaml.core :as yaml])
  (:gen-class))

(defn- front-matter-map
  "Safely parses the YAML front matter from a markdown file's content string."
  [content file-name]
  (when-let [yaml-str (second (re-find #"(?ms)^---\n(.*?)\n---" content))]
    (try
      (yaml/parse-string yaml-str :keywords true)
      (catch Exception e
        (println (str "  -> WARN: Could not parse YAML for " file-name ". Skipping. Error: " (.getMessage e)))
        nil))))

(defn- backup-processed-directory! [processed-dir backup-dir]
  (println (str "-> Backing up '" (.getPath processed-dir) "' to '" (.getPath backup-dir) "'..."))
  (.mkdirs (.getParentFile backup-dir))
  (with-open [out (io/output-stream (str backup-dir ".zip"))
              zos (java.util.zip.ZipOutputStream. out)]
    (doseq [file (file-seq processed-dir)
            :when (.isFile file)]
      (let [entry-name (.substring (.getPath file) (inc (count (.getPath processed-dir))))
            zip-entry (java.util.zip.ZipEntry. entry-name)]
        (.putNextEntry zos zip-entry)
        (io/copy file zos)
        (.closeEntry zos))))
  (println "-> Backup complete."))

(defn -main [& args]
  (println "--- Corpus Filename Migration Script (v2.0 - Robust) ---")
  (println "This script will rename *.md files and associated image metadata")
  (println "from the date-and-title format to the canonical slug format.")

  (let [processed-dir (io/file (config/processed-dir-path))
        parser-done-dir (io/file (config/stage-dir-path :parser "done"))
        backup-dir (io/file (config/work-dir) (str "backup-" (System/currentTimeMillis) "-processed"))
        stats (atom {:processed 0 :migrated 0 :skipped 0 :conflict 0 :no-html 0})]

    (if-not (.exists processed-dir)
      (println (str "ERROR: Processed directory '" (.getPath processed-dir) "' does not exist. Nothing to do."))
      (do
        (backup-processed-directory! processed-dir backup-dir)

        (println "\n-> Beginning migration...")
        (let [md-files (filter #(str/ends-with? (.getName %) ".md") (.listFiles processed-dir))]
          (doseq [md-file md-files]
            (swap! stats update :processed inc)
            (let [old-md-name (.getName md-file)
                  content (slurp md-file)
                  front-matter (front-matter-map content old-md-name)
                  old-key (:article_key front-matter)
                  source-url (:source_url front-matter)]

              (if (or (str/blank? old-key) (str/blank? source-url))
                (do (println (str "- Skipping " old-md-name " (missing :article_key or :source_url)"))
                    (swap! stats update :skipped inc))
                ;; Find the HTML file and get the slug from it
                (let [html-filename (util/url->filename source-url)
                      html-file (io/file parser-done-dir html-filename)]
                  (if-not (.exists html-file)
                    (do (println (str "- Skipping " old-md-name " (could not find corresponding HTML file: " html-filename ")"))
                        (swap! stats update :no-html inc))
                    ;; We found the HTML, now parse it for the real slug
                    (let [new-key (-> (slurp html-file)
                                      (parser/parse-html source-url)
                                      :metadata
                                      :post-id)]
                      (if (str/blank? new-key)
                        (do (println (str "- Skipping " old-md-name " (could not extract slug from " html-filename ")"))
                            (swap! stats update :skipped inc))
                        (let [new-md-file (io/file processed-dir (str new-key ".md"))]
                          (if (and (not= md-file new-md-file) (.exists new-md-file))
                            (do (println (str "- CONFLICT: Cannot rename " old-md-name " to " (.getName new-md-file) ". It already exists."))
                                (swap! stats update :conflict inc))
                            (do
                              (println (str "+ Migrating " old-key " -> " new-key))
                              (.renameTo md-file new-md-file)
                              (let [;; Add the new post_id and update the article_key
                                    new-front-matter (str "post_id: " new-key "\n"
                                                          (str/replace content
                                                                       (str "article_key: " old-key)
                                                                       (str "article_key: " new-key)))
                                    new-content (str/replace-first content content new-front-matter)]
                                (spit new-md-file new-content))
                              (let [img-meta-prefix (str old-key "_")
                                    files-to-rename (filter #(and (.isFile %)
                                                                  (str/ends-with? (.getName %) ".json")
                                                                  (str/starts-with? (.getName %) img-meta-prefix))
                                                            (file-seq processed-dir))]
                                (doseq [img-meta-file files-to-rename]
                                  (let [new-img-meta-name (str/replace (.getName img-meta-file) old-key new-key)
                                        new-img-meta-file (io/file (.getParentFile img-meta-file) new-img-meta-name)]
                                    (.renameTo img-meta-file new-img-meta-file))))
                              (swap! stats update :migrated inc))))))))))))))

        (println "\n--- Migration Complete ---")
        (println "Summary:")
        (println "--------------------------")
        (println (str "Markdown files processed:  " (:processed @stats)))
        (println (str "Successfully migrated:     " (:migrated @stats)))
        (println (str "Skipped (invalid front matter): " (:skipped @stats)))
        (println (str "Skipped (no HTML found):   " (:no-html @stats)))
        (println (str "Skipped (conflict):        " (:conflict @stats)))
        (println "--------------------------")
        (println "Corpus has been updated to the new slug-based naming scheme."))
  (shutdown-agents))
