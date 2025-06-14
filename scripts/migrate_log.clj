;; File: scripts/migrate_log.clj
(ns migrate-log
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tract.config :as config]
            [tract.parser :as parser]
            [tract.db :as db]
            ;; Require tract.util for filename generation
            [tract.util :as util])
  (:gen-class))

;; The build-url-to-html-map function is no longer needed and has been removed.

(defn- read-old-completed-log []
  (let [file (io/file (config/work-dir) "completed.log")]
    (if (.exists file)
      (->> (slurp file)
           str/split-lines
           (remove str/blank?)
           vec)
      [])))

(defn -main [& args]
  (println "--- Running 'completed.log' Migration Script (v3) ---")
  (let [completed-urls (read-old-completed-log)
        total-urls (count completed-urls)
        parser-done-dir (io/file (config/stage-dir-path :parser "done"))]
    (println (str "-> Found " total-urls " URLs in the old completed.log to migrate."))
    (println "-> Using direct filename calculation strategy.")

    (let [results (doall (map-indexed
                          (fn [idx url]
                            (let [display-url (subs url 0 (min (count url) 60))]
                              (print (str "\r-> Migrating " (inc idx) "/" total-urls ": " display-url "                                "))
                              (flush))
                            (try
                              ;; --- NEW STRATEGY: Directly calculate the filename ---
                              (let [expected-filename (util/url->filename url)
                                    html-file (io/file parser-done-dir expected-filename)]
                                (if (.exists html-file)
                                  (let [html-string (slurp html-file)
                                        parsed-data (parser/parse-html html-string url)
                                        metadata (:metadata parsed-data)]
                                    (if (:post_id metadata)
                                      (do
                                        (db/record-completion! {:post-id       (:post_id metadata)
                                                                :source-url    (:source_url metadata)
                                                                :canonical-url (:canonical_url metadata)})
                                        :migrated)
                                      :skipped-no-id))
                                  :skipped-no-html))
                              (catch Exception e
                                :error-in-url-parsing)))
                          completed-urls))
          summary (frequencies results)]
      (println "\n\n--- Migration Complete ---")
      (println "Summary:")
      (println "-----------------------------------")
      (println (str "Successfully migrated:          " (get summary :migrated 0)))
      (println (str "Skipped (no matching HTML file):" (get summary :skipped-no-html 0)))
      (println (str "Skipped (no ID found in HTML):  " (get summary :skipped-no-id 0)))
      (println (str "Skipped (Malformed URL in log): " (get summary :error-in-url-parsing 0)))
      (println "-----------------------------------")
      (println "The new 'completed-post-ids.log' and 'url-to-id.map' are now populated.")))
  (shutdown-agents))
