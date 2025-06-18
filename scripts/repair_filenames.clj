;; File: scripts/repair_filenames.clj
(ns repair-filenames
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tract.config :as config])
  (:gen-class))

(defn- get-prefixed-files [dir]
  "Finds all files in a directory that start with a YYYY-MM-DD_ prefix."
  (let [prefix-regex #"^\d{4}-\d{2}-\d{2}_.*"]
    (when (.exists dir)
      (->> (.listFiles dir)
           (filter #(re-matches prefix-regex (.getName %)))))))

(defn- repair-directory [dir-path dir-name]
  (println (str "\n--- Scanning directory: " dir-name " ---"))
  (let [dir (io/file dir-path)
        prefixed-files (get-prefixed-files dir)
        stats (atom {:renamed 0, :conflict 0, :failed 0})]
    
    (if (empty? prefixed-files)
      (println "-> No files with date prefixes found.")
      (do
        (println (str "-> Found " (count prefixed-files) " files with date prefixes to rename..."))
        (doseq [source-file prefixed-files]
          (let [source-name (.getName source-file)
                new-name (str/replace source-name #"^\d{4}-\d{2}-\d{2}_" "")
                dest-file (io/file dir new-name)]
            (println (str "  -> Renaming " source-name " to " new-name))
            
            ;; CRITICAL SAFETY CHECK: Do not overwrite an existing file.
            (if (.exists dest-file)
              (do
                (println "     - CONFLICT: Destination file already exists. Skipping.")
                (swap! stats update :conflict inc))
              (if (.renameTo source-file dest-file)
                (swap! stats update :renamed inc)
                (do
                  (println "     - FAILED: The rename operation failed.")
                  (swap! stats update :failed inc))))))))
    @stats))

(defn -main [& args]
  (println "--- Corpus Filename Normalization Script ---")
  (let [html-stats (repair-directory (config/stage-dir-path :parser "done") "parser/done")
        meta-stats (repair-directory (config/metadata-dir-path) "metadata")]
        
    (println "\n--- Repair Complete ---")
    (println "Summary:")
    (println "--------------------------")
    (println (str "HTML Files Renamed:      " (:renamed html-stats 0)))
    (println (str "HTML Conflicts Skipped:  " (:conflict html-stats 0)))
    (println (str "Metadata Files Renamed:  " (:renamed meta-stats 0)))
    (println (str "Metadata Conflicts Skipped:" (:conflict meta-stats 0)))
    (println "--------------------------")
    (println "Please re-run 'make verify-corpus' to see the new integrity report.")))
