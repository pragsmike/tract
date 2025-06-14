(ns prune-ignored
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [tract.config :as config]
            [tract.util :as util]
            [clj-yaml.core :as yaml])
  (:import [java.net URL]) ; <--- THIS LINE WAS MISSING
  (:gen-class))

(def ^:private ignored-domains-file "ignored-domains.txt")
(def ^:private completed-log-file (str (io/file (config/work-dir) "completed.log")))
(def ^:private processed-dir (config/processed-dir-path))
(def ^:private parser-done-dir (config/stage-dir-path :parser "done"))

(defn- extract-domain [url-str]
  (try (.getHost (new URL (str/trim url-str))) (catch Exception _ nil)))

(defn- read-ignored-domains []
  (let [file (io/file ignored-domains-file)]
    (if (.exists file)
      (->> (slurp file)
           str/split-lines
           (remove #(or (str/blank? %) (str/starts-with? % "#")))
           (map str/trim)
           set)
      #{})))

(defn- find-prune-candidates
  "Scans all processed .md files and returns a list of candidates for pruning.
  A candidate is a map containing the source_url and a list of all associated file paths."
  [ignored-domains]
  (let [md-files (->> (io/file processed-dir)
                      (.listFiles)
                      (filter #(str/ends-with? (.getName %) ".md")))]
    (->> md-files
         (map (fn [md-file]
                (try
                  (let [content (slurp md-file)
                        front-matter-re #"(?ms)^---\n(.*?)\n---"
                        front-matter-str (some-> (re-find front-matter-re content) second)
                        metadata (when front-matter-str (yaml/parse-string front-matter-str :keywords true))
                        source-url (:source_url metadata)
                        domain (extract-domain source-url)]
                    (when (contains? ignored-domains domain)
                      {:source_url source-url
                       :files      [(.getPath md-file)
                                    (.getPath (io/file parser-done-dir (str (:article_key metadata) ".html")))
                                    (.getPath (io/file parser-done-dir (str (:article_key metadata) ".html.meta")))]}))
                  (catch Exception _ nil))))
         (remove nil?))))

(defn- perform-dry-run
  "Prints the files that would be deleted."
  [candidates]
  (println "\n--- DRY RUN: The following files would be deleted ---")
  (if (empty? candidates)
    (println "No files from ignored domains found.")
    (doseq [{:keys [source_url files]} candidates]
      (println (str "\n[Ignored] " source_url))
      (doseq [f files]
        (println (str "  - " f))))))

(defn- perform-delete
  "Deletes the files for the given candidates and updates the completed.log."
  [candidates]
  (println "\n--- FORCE MODE: Deleting files from ignored domains ---")
  (if (empty? candidates)
    (println "No files to delete.")
    (do
      (let [deleted-urls (atom #{})]
        (doseq [{:keys [source_url files]} candidates]
          (println (str "\n[Deleting] " source_url))
          (doseq [f files]
            (let [file-obj (io/file f)]
              (if (.exists file-obj)
                (if (.delete file-obj)
                  (println (str "  - [DELETED] " f))
                  (println (str "  - [FAILED] " f)))
                (println (str "  - [MISSING] " f)))))
          (swap! deleted-urls conj source_url))

        (println "\n-> Updating completed.log...")
        (let [original-urls (->> (slurp completed-log-file) str/split-lines set)
              updated-urls (set/difference original-urls @deleted-urls)]
          (spit completed-log-file (str/join "\n" (sort updated-urls)))
          (println (str "-> Removed " (count @deleted-urls) " URLs from completed.log.")))))))

(defn -main
  [& args]
  (let [force-mode? (some #{"--force" "--delete"} args)
        ignored-domains (read-ignored-domains)]
    (println "--- Pruning Utility for Ignored Domains ---")
    (println (str "-> Loaded " (count ignored-domains) " domains from " ignored-domains-file))
    (println "-> Scanning for matching files...")

    (let [candidates (find-prune-candidates ignored-domains)]
      (if force-mode?
        (perform-delete candidates)
        (perform-dry-run candidates)))

    (println "\n--- Prune Finished ---")))
