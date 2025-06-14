(ns backfill-meta
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tract.config :as config]
            [clj-yaml.core :as yaml]
            [cheshire.core :as json])
  (:gen-class))

(defn- get-markdown-files []
  (let [processed-dir (io/file (config/processed-dir-path))]
    (if (.exists processed-dir)
      (->> (.listFiles processed-dir)
           (filter #(and (.isFile %) (str/ends-with? (.getName %) ".md"))))
      [])))

(defn- extract-data-from-md [md-file]
  (try
    (let [content (slurp md-file)
          front-matter-re #"(?ms)^---\n(.*?)\n---"
          front-matter-str (some-> (re-find front-matter-re content) second)]
      (when front-matter-str
        (select-keys (yaml/parse-string front-matter-str :keywords true)
                     [:article_key :source_url])))
    (catch Exception e
      (println (str "\nWARN: Could not parse YAML from " (.getName md-file)))
      nil)))

(defn -main [& args]
  (println "--- Running Metadata Backfill Script ---")
  (let [md-files (get-markdown-files)
        parser-done-dir (config/stage-dir-path :parser "done")
        total-files (count md-files)]
    (println (str "-> Found " total-files " processed markdown files to check."))
    (println (str "-> Target directory for .meta files: " parser-done-dir))

    (let [results (doall
                   (map-indexed
                    (fn [idx md-file]
                      (print (str "\r-> Checking " (inc idx) "/" total-files ": " (.getName md-file) "    "))
                      (flush)
                      (if-let [{:keys [article_key source_url]} (extract-data-from-md md-file)]
                        (let [meta-file (io/file parser-done-dir (str article_key ".html.meta"))]
                          (if (.exists meta-file)
                            :skipped-exists
                            (do
                              (let [meta-content {:source_url source_url
                                                  :fetch_timestamp "backfilled"}
                                    json-content (json/generate-string meta-content {:pretty true})]
                                (spit meta-file json-content))
                              :backfilled)))
                        :skipped-no-data))
                    md-files))
          summary (frequencies results)]

      (println "\n\n--- Backfill Complete ---")
      (println "Summary:")
      (println "--------------------------")
      (println (str "New .meta files created: " (get summary :backfilled 0)))
      (println (str "Skipped (already existed): " (get summary :skipped-exists 0)))
      (println (str "Skipped (no data in .md): " (get summary :skipped-no-data 0)))
      (println "--------------------------"))))
