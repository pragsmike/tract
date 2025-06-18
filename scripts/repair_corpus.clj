;; File: scripts/repair_corpus.clj
(ns repair-corpus
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [tract.config :as config]
            [tract.util :as util]
            [tract.parser :as parser]
            [cheshire.core :as json]
            [clj-yaml.core :as yaml])
  (:gen-class))

(defn- get-slug-from-filename [file-name]
  (cond
    (str/ends-with? file-name ".html.meta.json") (str/replace file-name #"\.html\.meta\.json$" "")
    (str/ends-with? file-name ".html") (str/replace file-name #"\.html$" "")
    :else file-name))

(defn- get-corpus-state []
  (let [parser-done-dir (io/file (config/stage-dir-path :parser "done"))
        metadata-dir (io/file (config/metadata-dir-path))
        html-files (when (.exists parser-done-dir) (->> (.listFiles parser-done-dir) (filter #(str/ends-with? (.getName %) ".html"))))
        meta-files (when (.exists metadata-dir) (->> (.listFiles metadata-dir) (filter #(str/ends-with? (.getName %) ".meta.json"))))
        html-slugs (set (map #(get-slug-from-filename (.getName %)) html-files))
        meta-slugs (set (map #(get-slug-from-filename (.getName %)) meta-files))]
    {:html-slugs html-slugs
     :meta-slugs meta-slugs}))

(defn- check-html-content [file]
  (let [content (slurp file)]
    (cond
      (str/includes? content "502 Bad Gateway") "Title contains '502 Bad Gateway'"
      (str/includes? content "Page not found") "Title contains 'Page not found'"
      :else nil)))

(defn- process-slug [slug state force?]
  (let [parser-done-dir (config/stage-dir-path :parser "done")
        metadata-dir (config/metadata-dir-path)
        quarantine-dir (io/file (config/work-dir) "quarantine")
        
        has-html? (contains? (:html-slugs state) slug)
        has-meta? (contains? (:meta-slugs state) slug)
        
        html-file (io/file parser-done-dir (str slug ".html"))
        meta-file (io/file metadata-dir (str slug ".html.meta.json"))]

    (cond
      ;; Case 1: Matched Pair - Check for Mismatch or Corruption
      (and has-html? has-meta?)
      (let [meta-data (json/parse-string (slurp meta-file) true)
            url (:source-url meta-data)
            expected-filename (when url (util/url->filename url))
            actual-filename (.getName html-file)
            is-mismatched? (and expected-filename (not= expected-filename actual-filename))
            is-corrupt? (or (< (.length html-file) 300) (check-html-content html-file))]
        (cond
          is-mismatched?
          (let [new-html-file (io/file parser-done-dir expected-filename)]
            (if (.exists new-html-file)
              (if force? (do (println (str "[DELETING REDUNDANT] " html-file)) (.delete html-file) (.delete meta-file))
                  (println (str "[WOULD DELETE REDUNDANT] " html-file)))
              (if force? (do (println (str "[RENAMING] " html-file " -> " expected-filename)) (.renameTo html-file new-html-file) (.renameTo meta-file (io/file metadata-dir (str expected-filename ".meta.json"))))
                  (println (str "[WOULD RENAME] " html-file " -> " expected-filename)))))

          is-corrupt?
          (let [reason (or (check-html-content html-file) "File size < 300 bytes")
                source-url (:source-url meta-data)]
            (.mkdirs quarantine-dir)
            (if force?
              (do (println (str "[QUARANTINING] " html-file " (Reason: " reason ")"))
                  (.renameTo html-file (io/file quarantine-dir actual-filename))
                  (.renameTo meta-file (io/file quarantine-dir (.getName meta-file)))
                  (when source-url
                    (let [job-file (io/file (config/stage-dir-path :job "pending") (str "refetch-" slug ".yaml"))]
                      (spit job-file (yaml/generate-string {:urls [source-url]})))))
              (println (str "[WOULD QUARANTINE] " html-file " (Reason: " reason ")"))))))

      ;; Case 2: Orphaned HTML
      has-html?
      (let [parsed-data (parser/parse-html (slurp html-file) nil)
            canonical-url (get-in parsed-data [:metadata :canonical-url])]
        (if (and (string? canonical-url) (not (str/blank? canonical-url)))
          (if force? (do (println (str "[ADOPTING] " html-file)) (spit meta-file (json/generate-string {:source-url canonical-url :fetch-timestamp "repaired"} {:pretty true})))
              (println (str "[WOULD ADOPT] " html-file)))
          (if force? (println (str "[CANNOT ADOPT] " html-file)) (println (str "[WOULD ATTEMPT TO ADOPT] " html-file)))))

      ;; Case 3: Orphaned Metadata
      has-meta?
      (let [source-url (:source-url (json/parse-string (slurp meta-file) true))]
        (if (and (string? source-url) (not (str/blank? source-url)))
          (if force?
            (let [job-file (io/file (config/stage-dir-path :job "pending") (str "refetch-" slug ".yaml"))]
              (println (str "[RE-FETCHING] " source-url))
              (spit job-file (yaml/generate-string {:urls [source-url]}))
              (.delete meta-file))
            (println (str "[WOULD RE-FETCH] " source-url)))
          (if force? (do (println (str "[DELETING INVALID META] " meta-file)) (.delete meta-file))
              (println (str "[WOULD DELETE INVALID META] " meta-file))))))))

(defn -main [& args]
  (let [force-mode? (some #{"--force"} args)
        corpus-state (get-corpus-state)
        all-slugs (set/union (:html-slugs corpus-state) (:meta-slugs corpus-state))]
    (println "--- Corpus Repair Tool (v3-safe) ---")
    (when-not force-mode?
      (println "-> Running in DRY-RUN mode. No files will be changed."))

    (doseq [slug all-slugs]
      (process-slug slug corpus-state force-mode?))

    (println "\n--- Repair Script Finished ---")))
