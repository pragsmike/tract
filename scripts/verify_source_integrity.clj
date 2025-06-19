;; File: scripts/verify_source_integrity.clj
(ns verify-source-integrity
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [tract.config :as config]
            [tract.util :as util]
            [tract.parser :as parser]
            [cheshire.core :as json])
  (:gen-class))

(defn- get-slug-from-html-filename [file]
  (-> (.getName file) (str/replace #"\.html$" "")))

(defn- check-html-content [file-content]
  (cond
    (str/includes? file-content "502 Bad Gateway") "Title contains '502 Bad Gateway'"
    (str/includes? file-content "Page not found") "Title contains 'Page not found'"
    (str/includes? file-content "Too Many Requests") "Content is 'Too Many Requests' error page"
    :else nil))

(defn- run-verification []
  (let [html-dir (io/file (config/html-dir-path))
        metadata-dir (io/file (config/metadata-dir-path))
        html-files (when (.exists html-dir) (->> (.listFiles html-dir) (filter #(.isFile %))))
        meta-files (when (.exists metadata-dir) (->> (.listFiles metadata-dir) (filter #(.isFile %))))

        html-slugs (set (map get-slug-from-html-filename html-files))
        meta-slugs (set (map util/get-slug-from-meta-filename meta-files))

        orphaned-html-slugs (set/difference html-slugs meta-slugs)
        orphaned-meta-slugs (set/difference meta-slugs html-slugs)
        matched-slugs (set/intersection html-slugs meta-slugs)

        corrupt-html-files (atom [])
        corrupt-meta-files (atom [])
        mismatched-files (atom [])]

    (println "--- Source of Truth Integrity Report ---")
    (println (str "Found " (count html-slugs) " HTML files and " (count meta-slugs) " Metadata files."))
    (println "--------------------------------------------------")

    (println (str "\nOrphaned HTML Files (" (count orphaned-html-slugs) "): (Have HTML but no metadata)"))
    (doseq [slug orphaned-html-slugs] (println (str "  - " slug ".html")))

    (println (str "\nOrphaned Metadata Files (" (count orphaned-meta-slugs) "): (Have metadata but no HTML)"))
    (doseq [slug orphaned-meta-slugs] (println (str "  - " slug ".html.meta.json")))

    (println (str "\nScanning content of " (count matched-slugs) " matched file pairs for corruption and disagreement..."))
    (doseq [slug matched-slugs]
      (let [html-file (io/file html-dir (str slug ".html"))
            meta-file (io/file metadata-dir (str slug ".html.meta.json"))]
        ;; 1. Check HTML file validity
        (when (< (.length html-file) 500)
          (swap! corrupt-html-files conj {:slug slug :reason "File size is less than 500 bytes"}))
        (let [html-content (slurp html-file)]
          (when-let [reason (check-html-content html-content)]
            (swap! corrupt-html-files conj {:slug slug :reason reason}))

          ;; 2. Check Metadata file validity and cross-reference
          (try
            (let [meta-data (json/parse-string (slurp meta-file) true)]
              (if-let [source-url (:source-url meta-data)]
                ;; Check if metadata URL matches HTML filename
                (let [expected-filename (util/url->filename source-url)]
                  (when (not= (str slug ".html") expected-filename)
                    (swap! mismatched-files conj {:slug slug :reason (str ":source-url implies filename should be " expected-filename)})))
                ;; Check for presence of mandatory key
                (swap! corrupt-meta-files conj {:slug slug :reason "Missing mandatory :source-url key"})))
            (catch Exception e
              (swap! corrupt-meta-files conj {:slug slug :reason (str "JSON parse error: " (.getMessage e))})))

          ;; 3. Check if HTML content's canonical slug matches filename
          (let [parsed-slug (-> (parser/parse-html html-content nil) :metadata :post-id)]
            (when (and parsed-slug (not= slug parsed-slug))
              (swap! mismatched-files conj {:slug slug :reason (str "Filename is '" slug "' but canonical slug in HTML is '" parsed-slug "'")})))))
        )

    (println (str "\nCorrupt HTML Files (" (count @corrupt-html-files) "):"))
    (doseq [{:keys [slug reason]} @corrupt-html-files] (println (str "  - " slug ".html (Reason: " reason ")")))

    (println (str "\nCorrupt/Invalid Metadata Files (" (count @corrupt-meta-files) "):"))
    (doseq [{:keys [slug reason]} @corrupt-meta-files] (println (str "  - " slug ".html.meta.json (Reason: " reason ")")))

    (println (str "\nMismatched Files (" (count @mismatched-files) "): (Content does not agree with filename)"))
    (doseq [{:keys [slug reason]} @mismatched-files] (println (str "  - " slug " (Reason: " reason ")")))

    (println "\n--- Verification Complete ---")))

(defn -main [& args]
  (run-verification)
  (shutdown-agents))
