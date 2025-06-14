;; File: scripts/deduplicate_corpus.clj
(ns deduplicate-corpus
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tract.config :as config]
            [tract.parser :as parser]
            [tract.util :as util]
            [clj-yaml.core :as yaml]
            [clojure.edn :as edn]
            [clojure.set :as set])
  (:gen-class))

(defn- find-processed-articles-and-ids
  "Scans all .md files, finds their source HTML, and determines their canonical ID."
  []
  (let [processed-dir (io/file (config/processed-dir-path))
        parser-done-dir (io/file (config/stage-dir-path :parser "done"))
        md-files (->> (.listFiles processed-dir)
                      (filter #(str/ends-with? (.getName %) ".md")))]
    (for [md-file md-files
          :let [front-matter (try
                               (-> (slurp md-file)
                                   (re-find #"(?ms)^---\n(.*?)\n---")
                                   second
                                   (yaml/parse-string :keywords true))
                               (catch Exception _ nil))
                source-url (:source_url front-matter)]
          :when source-url]
      (let [html-filename (util/url->filename source-url)
            html-file (io/file parser-done-dir html-filename)
            canonical-id (when (.exists html-file)
                           (-> (slurp html-file)
                               (parser/parse-html source-url)
                               :metadata
                               :post_id))]
        {:canonical-id  canonical-id
         :source-url    source-url
         :is-canonical? (= source-url (:canonical_url (:metadata (parser/parse-html (slurp html-file) source-url))))
         :paths         {:md   (.getPath md-file)
                         :html (.getPath html-file)
                         :meta (str (.getPath html-file) ".meta")}}))))

(defn- identify-keeper-and-deletions [articles]
  (let [sorted-articles (sort-by :is-canonical? > articles)
        keeper (first sorted-articles)
        deletions (rest sorted-articles)]
    {:keeper keeper, :deletions deletions}))

(defn- perform-dry-run [duplicates]
  (println "\n--- DRY RUN: Found" (count duplicates) "sets of duplicate articles ---")
  (doseq [[id articles] duplicates]
    (let [{:keys [keeper deletions]} (identify-keeper-and-deletions articles)]
      (println "\n--------------------------------------------------")
      (println (str "ID: " id " (" (count articles) " copies)"))
      (println (str "  [KEEP]   " (:source-url keeper) " -> " (-> keeper :paths :md)))
      (doseq [del deletions]
        (println (str "  [DELETE] " (:source-url del) " -> " (-> del :paths :md)))))))

(defn- perform-delete! [duplicates]
  (println "\n--- FORCE MODE: Deleting duplicate files ---")
  (let [deletable-urls (atom #{})]
    (doseq [[id articles] duplicates]
      (let [{:keys [deletions]} (identify-keeper-and-deletions articles)]
        (when (seq deletions)
          (println "\n-> Deleting copies for ID:" id)
          (doseq [del deletions]
            (println "  - Deleting MD file for:" (:source-url del))
            (swap! deletable-urls conj (:source_url del))
            ;; Only delete the .md file. The .html is canonical and must be kept.
            (let [md-path (:md (:paths del))
                  file (io/file md-path)]
              (if (.exists file)
                (if (.delete file)
                  (println (str "    - [DELETED] " md-path))
                  (println (str "    - [FAILED] " md-path)))
                (println (str "    - [MISSING] " md-path))))))))
    (when (seq @deletable-urls)
      (println "\n-> Updating log files to remove references to deleted articles...")
      ;; We can remove the URLs from the logs since their final .md output is gone.
      (let [old-log (io/file (config/work-dir) "completed.log")
            old-urls (set (str/split-lines (slurp old-log)))
            new-urls (set/difference old-urls @deletable-urls)]
        (spit old-log (str/join "\n" (sort new-urls)))
        (println (str "- Removed " (- (count old-urls) (count new-urls)) " URLs from completed.log")))
      (let [map-file-path (config/url-to-id-map-path)
            map-file (io/file map-file-path)
            all-entries (when (.exists map-file)
                          (->> (str "[" (slurp map-file) "]")
                               (edn/read-string)))
            kept-entries (remove #(contains? @deletable-urls (:url %)) all-entries)]
        (spit map-file (str/join "" (map #(str (pr-str %) "\n") kept-entries)))
        (println (str "- Removed " (- (count all-entries) (count kept-entries)) " entries from url-to-id.map"))))))

(defn -main [& args]
  (println "--- Corpus Deduplication Tool (v3) ---")
  (let [force-mode? (some #{"--force"} args)]
    (println "-> Scanning all processed markdown files...")
    (let [articles (find-processed-articles-and-ids)
          grouped (group-by :canonical-id articles)
          duplicates (filter (fn [[k v]] (and (some? k) (> (count v) 1))) grouped)]
      (if (empty? duplicates)
        (println "-> No duplicate articles found. Your corpus is clean.")
        (if force-mode?
          (perform-delete! duplicates)
          (perform-dry-run duplicates)))
      (println "\n--- Deduplication Finished ---"))))
