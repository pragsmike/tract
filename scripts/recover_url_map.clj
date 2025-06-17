;; File: scripts/recover_url_map.clj
(ns recover-url-map
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tract.config :as config]
            [cheshire.core :as json]
            [clojure.edn :as edn])
  (:gen-class))

(defn- read-corrupted-map-entries []
  (let [map-file (io/file (config/url-to-id-map-path))]
    (if (.exists map-file)
      (->> (slurp map-file)
           str/split-lines
           (remove str/blank?)
           (map edn/read-string))
      [])))

(defn -main [& args]
  (println "--- url-to-id.map Recovery Script ---")
  (let [map-path (config/url-to-id-map-path)
        map-file (io/file map-path)
        backup-path (str map-path ".bak")]
    (if-not (.exists map-file)
      (println "-> No url-to-id.map file found. Nothing to do.")
      (do
        (println (str "-> Backing up existing file to " backup-path))
        (io/copy map-file (io/file backup-path))

        (let [all-entries (read-corrupted-map-entries)
              grouped-entries (group-by #(if (= "unknown" (:url %)) :corrupted :good) all-entries)
              good-entries (:good grouped-entries)
              corrupted-entries (:corrupted grouped-entries)]
          (println (str "-> Found " (count all-entries) " total entries."))
          (println (str "-> Found " (count corrupted-entries) " corrupted entries to fix."))

          (let [fixed-entries (atom [])
                unrecoverable-entries (atom [])]
            (doseq [entry corrupted-entries]
              (let [id (:id entry)
                    meta-filename (str id ".html.meta.json")
                    meta-file (io/file (config/metadata-dir-path) meta-filename)]
                (if (.exists meta-file)
                  (try
                    (let [meta-data (json/parse-string (slurp meta-file) true)
                          ;; NOTE: The key here is :source-url, as written by the fetcher.
                          correct-url (:source-url meta-data)
                          fixed-entry {:url correct-url, :id id}]
                      (swap! fixed-entries conj fixed-entry))
                    (catch Exception e
                      (println (str "WARN: Could not parse metadata for " id ". Skipping."))
                      (swap! unrecoverable-entries conj entry)))
                  (do
                    (println (str "WARN: No metadata file found for " id ". Skipping."))
                    (swap! unrecoverable-entries conj entry)))))

            (let [final-entries (concat good-entries @fixed-entries)
                  final-count (count final-entries)]
              (println (str "-> Writing " final-count " corrected entries to " map-path))
              (spit map-path (str/join "" (for [entry final-entries]
                                            (str (pr-str entry) "\n"))))

              (println "\n--- Recovery Complete ---")
              (println "Summary:")
              (println "--------------------------")
              (println (str "Entries successfully fixed: " (count @fixed-entries)))
              (println (str "Unrecoverable entries:      " (count @unrecoverable-entries)))
              (println (str "Total entries in new file:  " final-count))))))))
  (shutdown-agents))
