;; File: scripts/repair_conflicts.clj
(ns repair-conflicts
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tract.config :as config]
            [cheshire.core :as json])
  (:gen-class))

(defn- get-prefixed-files
  "Finds all files in a directory that start with a YYYY-MM-DD_ prefix."
  [dir]
  (let [prefix-regex #"^\d{4}-\d{2}-\d{2}_.*"]
    (when (.exists dir)
      (->> (.listFiles dir)
           (filter #(re-matches prefix-regex (.getName %)))))))

(defn -main [& args]
  (println "--- Metadata Conflict Repair Script ---")
  (let [metadata-dir (io/file (config/metadata-dir-path))
        prefixed-files (get-prefixed-files metadata-dir)
        stats (atom {:conflicts-found 0, :deleted 0, :url-mismatch 0})]

    (println (str "-> Found " (count prefixed-files) " date-prefixed files to check for conflicts..."))

    (doseq [prefixed-file prefixed-files]
      (let [prefixed-name (.getName prefixed-file)
            slug-only-name (str/replace prefixed-name #"^\d{4}-\d{2}-\d{2}_" "")
            slug-only-file (io/file metadata-dir slug-only-name)]

        ;; A conflict exists if a slug-only version of a prefixed file exists.
        (when (.exists slug-only-file)
          (swap! stats update :conflicts-found inc)
          (println (str "\n-> Found Conflict Pair:"))
          (println (str "   - " prefixed-name))
          (println (str "   - " slug-only-name))

          (try
            (let [prefixed-meta (json/parse-string (slurp prefixed-file) true)
                  slug-only-meta (json/parse-string (slurp slug-only-file) true)
                  url1 (:source-url prefixed-meta)
                  url2 (:source-url slug-only-meta)]

              (if (= url1 url2)
                (do
                  (println "   - URLs match. Deleting date-prefixed file.")
                  (if (.delete prefixed-file)
                    (do
                      (println "     - [DELETED]")
                      (swap! stats update :deleted inc))
                    (println "     - [DELETE FAILED]")))
                (do
                  (println "   - WARN: Source URLs do not match. Skipping deletion.")
                  (println (str "     - Prefixed URL: " url1))
                  (println (str "     - Slug-only URL: " url2))
                  (swap! stats update :url-mismatch inc))))
            (catch Exception e
              (println (str "   - ERROR: Could not parse JSON for one of the files. " (.getMessage e))))))))

    (println "\n--- Conflict Repair Complete ---")
    (println "Summary:")
    (println "---------------------------------")
    (println (str "Total Conflicts Found:   " (:conflicts-found @stats 0)))
    (println (str "Redundant Files Deleted: " (:deleted @stats 0)))
    (println (str "Mismatched URLs Skipped: " (:url-mismatch @stats 0)))
    (println "---------------------------------")))
