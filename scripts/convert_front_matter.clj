;; File: scripts/convert_front_matter.clj
(ns convert-front-matter
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tract.config :as config]
            [toml-clj.core :as toml]
            [clj-yaml.core :as yaml])
  (:import [java.io StringReader])
  (:gen-class))

(defn- get-markdown-files []
  "Finds all .md files in the processed output directory."
  (let [processed-dir (io/file (config/processed-dir-path))]
    (if (.exists processed-dir)
      (->> (.listFiles processed-dir)
           (filter #(.isFile %))
           (filter #(str/ends-with? (.getName %) ".md")))
      [])))

(defn- process-file!
  "Reads a file's front matter, determines its format (YAML or TOML),
  and re-writes it as standardized YAML. This makes the script idempotent
  and useful as a 'cleaner' for existing YAML files."
  [file]
  (let [content (slurp file)
        match (re-find #"(?ms)^---\n(.*?)\n---(.*)" content)]
    (if-not match
      :skipped-no-front-matter
      (let [[_ front-matter-str body-str] match
            data (try
                   ;; First, try to parse it as YAML. This will succeed for new files.
                   (yaml/parse-string front-matter-str :keywords true)
                   (catch Exception _
                     ;; If YAML parsing fails, try to parse it as TOML.
                     (try
                       (toml/read (StringReader. front-matter-str) {:key-fn keyword})
                       (catch Exception _
                         ;; If both fail, it's a corrupt file.
                         nil))))]
        (if data
          ;; If we successfully parsed data (either YAML or TOML)...
          (let [;; Re-write it as clean, standardized YAML.
                new-yaml-str (yaml/generate-string data :dumper-options {:flow-style :block})
                new-content (str "---\n" new-yaml-str "---\n" (str/triml body-str))]
            (spit file new-content)
            :converted)
          ;; If data is nil, parsing both ways failed.
          :skipped-corrupt-front-matter)))))

(defn -main [& args]
  (println "--- Running Front Matter Conversion/Cleanup Script ---")
  (let [files (get-markdown-files)
        total-files (count files)]
    (if (zero? total-files)
      (println "-> No markdown files found to process.")
      (do
        (println (str "-> Found " total-files " markdown files in " (config/processed-dir-path)))
        (println "-> Starting conversion/cleanup...")

        ;; CORRECTED AND IDIOMATIC LOOP
        (let [results (doall (map-indexed
                               (fn [idx file]
                                 (print (str "\r-> Processing file " (inc idx) "/" total-files " (" (.getName file) ")...                                  "))
                                 (flush)
                                 (process-file! file))
                               files))
              summary (frequencies results)]
          (println "\n\n--- Conversion Complete ---")
          (println "Summary:")
          (println "--------------------------")
          (println (str "Processed/Converted to YAML: " (get summary :converted 0)))
          (println (str "Skipped (No Front Matter): " (get summary :skipped-no-front-matter 0)))
          (println (str "Skipped (Corrupt Front Matter): " (get summary :skipped-corrupt-front-matter 0)))
          (println "--------------------------")
          (println "Note: Files already in valid YAML are re-written to ensure standard formatting."))))
  (shutdown-agents))
