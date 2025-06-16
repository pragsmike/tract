;; File: scripts/prune-ignored.clj
(ns prune-ignored
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [tract.config :as config]
            [tract.util :as util]
            [tract.parser :as parser]
            [tract.db :as db]
            [clj-yaml.core :as yaml])
  (:gen-class))

(defn- read-ignored-domains []
  (let [file (io/file (config/ignored-domains-path))]
    (if (.exists file)
      (->> (slurp file)
           str/split-lines
           (remove #(or (str/blank? %) (str/starts-with? % "#")))
           (map str/trim)
           set)
      #{})))

(defn front-matter [md-file]
  (->> (slurp md-file)
       (re-find #"(?ms)^---\n(.*?)\n---")
       second))
(defn front-matter-map [md-file]
  (-> md-file
      front-matter
      (yaml/parse-string :keywords true)))

(defn- find-prune-candidates [ignored-domains]
  (let [processed-dir (io/file (config/processed-dir-path))
        parser-done-dir (io/file (config/stage-dir-path :parser "done"))
        md-files (->> (.listFiles processed-dir)
                      (filter #(str/ends-with? (.getName %) ".md")))]
    (->> md-files
         (map (fn [md-file]
                (try
                  (let [front-matter (front-matter-map md-file)
                        source-url (:source_url front-matter)]
                    ;; If the source_url is not a string, skip this record entirely.
                    (when (string? source-url)
                      (let [domain (util/extract-domain source-url)
                            html-filename (util/url->filename source-url)
                            html-file (io/file parser-done-dir html-filename)]
                        (when (and (contains? ignored-domains domain) (.exists html-file))
                          (let [parsed-data (parser/parse-html (slurp html-file) source-url)]
                            {:source-url source-url
                             :post-id    (get-in parsed-data [:metadata :post_id])
                             :files      [(.getPath md-file)
                                          (.getPath html-file)
                                          (str (.getPath html-file) ".meta")]})))))
                  (catch Exception e
                    (println (str "\nWARN: Unhandled error in file '" (.getName md-file) "'." (.getMessage e) " Skipping."))
                    nil))))
         (remove nil?)
         (doall))))

(defn- perform-dry-run [candidates]
  (println "\n--- DRY RUN: The following files would be deleted ---")
  (if (empty? candidates)
    (println "No files from ignored domains found.")
    (doseq [{:keys [source-url files]} candidates]
      (println (str "\n[Ignored] " source-url))
      (doseq [f files]
        (println (str "  - " f))))))

(defn- perform-delete! [candidates]
  (println "\n--- FORCE MODE: Deleting files from ignored domains ---")
  (if (empty? candidates)
    (println "No files to delete.")
    (do
      (doseq [{:keys [source-url files]} candidates]
        (println (str "\n[Deleting] " source-url))
        (doseq [f files]
          (let [file-obj (io/file f)]
            (if (.exists file-obj)
              (if (.delete file-obj)
                (println (str "  - [DELETED] " f))
                (println (str "  - [FAILED] " f)))
              (println (str "  - [MISSING] " f))))))
      (let [condemned-urls (set (map :source-url candidates))
            condemned-ids  (set (map :post-id candidates))]
        (println "\n-> Updating all data files to reflect deletions...")

        (let [ids-log-path (config/completed-post-ids-log-path)
              original-ids (db/read-completed-post-ids)
              updated-ids (set/difference original-ids condemned-ids)]
          (spit ids-log-path (str/join "\n" (sort updated-ids)))
          (println (str "- Removed " (count condemned-ids) " IDs from completed-post-ids.log")))
        (let [map-path (config/url-to-id-map-path)
              original-map (db/read-url-to-id-map)
              updated-map (apply dissoc original-map condemned-urls)]
          (spit map-path (str/join "" (for [[k v] updated-map] (str (pr-str {:url k :id v}) "\n"))))
          (println (str "- Removed " (count condemned-urls) " entries from url-to-id.map")))))))

(defn -main [& args]
  (let [force-mode? (some #{"--force" "--delete"} args)]
    (println "--- Pruning Utility for Ignored Domains (v10-final) ---")
    (let [ignored-domains (read-ignored-domains)]
      (println (str "-> Loaded " (count ignored-domains) " domains from " (config/ignored-domains-path)))
      (println "-> Scanning for matching files...")
      (let [candidates (find-prune-candidates ignored-domains)]
        (if force-mode?
          (perform-delete! candidates)
          (perform-dry-run candidates))))
    (println "\n--- Prune Finished ---")))
