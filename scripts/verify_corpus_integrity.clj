;; File: scripts/verify_corpus_integrity.clj
(ns verify-corpus-integrity
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [tract.config :as config]
            [tract.util :as util]
            [cheshire.core :as json])
  (:gen-class))

(defn- get-slug-from-filename [file-name]
  (cond
    (str/ends-with? file-name ".html.meta.json")
    (str/replace file-name #"\.html\.meta\.json$" "")
    (str/ends-with? file-name ".html")
    (str/replace file-name #"\.html$" "")
    :else file-name))

(defn- check-html-content [file]
  (let [content (slurp file)]
    (cond
      (str/includes? content "502 Bad Gateway") "Title contains '502 Bad Gateway'"
      (str/includes? content "Page not found") "Title contains 'Page not found'"
      :else nil)))

(defn- run-verification []
  (let [parser-done-dir (io/file (config/stage-dir-path :parser "done"))
        metadata-dir (io/file (config/metadata-dir-path))
        html-files (when (.exists parser-done-dir) (->> (.listFiles parser-done-dir) (filter #(str/ends-with? (.getName %) ".html"))))
        meta-files (when (.exists metadata-dir) (->> (.listFiles metadata-dir) (filter #(str/ends-with? (.getName %) ".meta.json"))))
        html-slugs (set (map #(get-slug-from-filename (.getName %)) html-files))
        meta-slugs (set (map #(get-slug-from-filename (.getName %)) meta-files))
        orphaned-meta-slugs (set/difference meta-slugs html-slugs)
        orphaned-html-slugs (set/difference html-slugs meta-slugs)
        matched-slugs (set/intersection html-slugs meta-slugs)
        corrupt-html-files (atom [])
        corrupt-meta-files (atom [])
        mismatched-files (atom [])]

    (println "--- Corpus Integrity Report ---")
    (println (str "Found " (count html-slugs) " HTML files and " (count meta-slugs) " Metadata files."))
    (println "--------------------------------------------------")

    (println (str "\nOrphaned Metadata Files (" (count orphaned-meta-slugs) "): (Have metadata but no HTML)"))
    (doseq [slug orphaned-meta-slugs] (println (str "  - " slug ".html.meta.json")))

    (println (str "\nOrphaned HTML Files (" (count orphaned-html-slugs) "): (Have HTML but no metadata)"))
    (doseq [slug orphaned-html-slugs] (println (str "  - " slug ".html")))

    (println "\nScanning content of" (count matched-slugs) "matched files for corruption...")
    (doseq [slug matched-slugs]
      (let [html-file (io/file parser-done-dir (str slug ".html"))
            meta-file (io/file metadata-dir (str slug ".html.meta.json"))]
        (when (< (.length html-file) 300)
          (swap! corrupt-html-files conj {:slug slug :reason "File size is less than 300 bytes"}))
        (when-let [reason (check-html-content html-file)]
          (swap! corrupt-html-files conj {:slug slug :reason reason}))
        (try
          (let [meta-data (json/parse-string (slurp meta-file) true)]
            (when-not (contains? meta-data :source-url)
              (swap! corrupt-meta-files conj {:slug slug :reason "Missing kebab-case :source-url key"}))
            (when (contains? meta-data :source_url)
              (swap! corrupt-meta-files conj {:slug slug :reason "Contains snake_case :source_url key"}))
            (when-let [url (:source-url meta-data)]
              ;; --- CORRECTED LOGIC ---
              ;; Compare the expected filename from the URL to the actual filename.
              (let [expected-filename (util/url->filename url)]
                (when (not= (str slug ".html") expected-filename)
                  (swap! mismatched-files conj {:slug slug :url url :expected expected-filename})))))
          (catch Exception e
            (swap! corrupt-meta-files conj {:slug slug :reason (str "JSON parse error: " (.getMessage e))})))))

    (println (str "\nCorrupt HTML Files (" (count @corrupt-html-files) "):"))
    (doseq [{:keys [slug reason]} @corrupt-html-files] (println (str "  - " slug ".html (Reason: " reason ")")))

    (println (str "\nCorrupt Metadata Files (" (count @corrupt-meta-files) "):"))
    (doseq [{:keys [slug reason]} @corrupt-meta-files] (println (str "  - " slug ".html.meta.json (Reason: " reason ")")))

    (println (str "\nMismatched Source URL Files (" (count @mismatched-files) "): (Metadata URL does not match HTML filename)"))
    (doseq [{:keys [slug url expected]} @mismatched-files] (println (str "  - " slug ".html.meta.json -> :source-url is " url ", but expected filename " expected))))

  (println "\n--- Verification Complete ---"))

(defn -main [& args]
  (run-verification)
  (shutdown-agents))
