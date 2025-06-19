;; File: scripts/prune_ignored.clj
(ns prune-ignored
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [tract.config :as config]
            [tract.util :as util]
            [tract.db :as db]
            [cheshire.core :as json])
  (:gen-class))

(defn- get-slug-from-meta-filename [^java.io.File meta-file]
  (-> (.getName meta-file)
      (str/replace #"\.html\.meta\.json$" "")))

;; REFACTORED: This function now targets the real HTML file in work/html/.
(defn- find-prune-candidates [ignored-domains]
  (let [metadata-dir (io/file (config/metadata-dir-path))
        processed-dir (io/file (config/processed-dir-path))
        ;; ADDED: Get path to new permanent HTML directory.
        html-dir (io/file (config/html-dir-path))
        meta-files (->> (.listFiles metadata-dir)
                        (filter #(str/ends-with? (.getName %) ".meta.json")))]
    (->> meta-files
         (map (fn [meta-file]
                (try
                  (let [meta-data (json/parse-string (slurp meta-file) true)
                        source-url (:source-url meta-data)
                        domain (util/extract-domain source-url)]
                    (when (and source-url (contains? ignored-domains domain))
                      (let [slug (get-slug-from-meta-filename meta-file)
                            html-file-path (.getPath (io/file html-dir (str slug ".html")))]
                        {:slug slug
                         :source-url source-url
                         ;; MODIFIED: List of files to delete now points to work/html/.
                         :files-to-delete [(.getPath meta-file)
                                           html-file-path
                                           (.getPath (io/file processed-dir (str slug ".md")))]})))
                  (catch Exception e
                    (println (str "\nWARN: Could not parse " (.getName meta-file) ". Skipping."))
                    nil))))
         (remove nil?)
         (doall))))

;; ... (perform-dry-run and perform-delete! do not need to change, as they
;;      operate on the list of files generated above.)
;; ... (The rest of the file remains the same)
(defn- perform-dry-run [candidates]
  (println "\n--- DRY RUN: The following files and their data would be pruned ---")
  (if (empty? candidates)
    (println "No files from ignored domains found.")
    (doseq [{:keys [source-url files-to-delete]} candidates]
      (println (str "\n[Ignored Domain] " source-url))
      (doseq [f files-to-delete]
        (println (str "  - [Prune] " f))))))

(defn- perform-delete! [candidates]
  (println "\n--- FORCE MODE: Deleting files and data from ignored domains ---")
  (if (empty? candidates)
    (println "No files to delete.")
    (do
      ;; Delete files
      (doseq [{:keys [source-url files-to-delete]} candidates]
        (println (str "\n[Deleting] " source-url))
        (doseq [f files-to-delete]
          (let [file-obj (io/file f)]
            (if (.exists file-obj)
              (if (.delete file-obj)
                (println (str "  - [DELETED] " f))
                (println (str "  - [FAILED] " f)))
              (println (str "  - [MISSING] " f))))))

      ;; Update database files
      (println "\n-> Updating database files to reflect deletions...")
      (let [condemned-slugs (set (map :slug candidates))
            all-urls-map (db/read-url-to-id-map)
            condemned-urls (->> all-urls-map
                                (filter (fn [[_url slug]] (contains? condemned-slugs slug)))
                                (map key)
                                set)]

        ;; Update completed-post-ids.log
        (let [ids-log-path (config/completed-post-ids-log-path)
              original-ids (db/read-completed-post-ids)
              updated-ids (set/difference original-ids condemned-slugs)]
          (spit ids-log-path (str (str/join "\n" (sort updated-ids)) "\n"))
          (println (str "- Removed " (count condemned-slugs) " IDs from " (last (str/split ids-log-path #"/")))))

        ;; Update url-to-id.map
        (let [map-path (config/url-to-id-map-path)
              updated-map (apply dissoc all-urls-map condemned-urls)]
          (spit map-path (str/join "" (for [[k v] updated-map] (str (pr-str {:url k :id v}) "\n"))))
          (println (str "- Removed " (count condemned-urls) " entries from " (last (str/split map-path #"/")))))))))

(defn -main [& args]
  (let [force-mode? (some #{"--force" "--delete"} args)]
    (println "--- Pruning Utility for Ignored Domains (v2.2-symlink-aware) ---")
    (let [ignored-domains (db/read-ignore-list)]
      (println (str "-> Loaded " (count ignored-domains) " domains from " (config/ignored-domains-path)))
      (println "-> Scanning metadata for matching files...")
      (let [candidates (find-prune-candidates ignored-domains)]
        (if force-mode?
          (perform-delete! candidates)
          (perform-dry-run candidates))))
    (println "\n--- Prune Finished ---")))
