(ns repair-markdown
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tract.config :as config]
            [clj-yaml.core :as yaml])
  (:import [java.io File])
  (:gen-class))

(defn- snake->kebab [s]
  (str/replace s #"_" "-"))

(defn- kebab-case-keys [m]
  (reduce-kv (fn [acc k v]
               (assoc acc (-> k name snake->kebab keyword) v))
             {} m))

(defn- is-corrupted?
  "Checks if a markdown file is corrupted (doesn't start with '---')."
  [^File md-file]
  (with-open [rdr (io/reader md-file)]
    (let [first-line (first (line-seq rdr))]
      (not= (str/trim first-line) "---"))))

(defn- repair-file-content
  "Takes the full content of a corrupted file and returns the corrected content.
  This version fixes both the structure and the snake_case keys."
  [content]
  (let [lines (str/split-lines content)
        first-delim-idx (first (keep-indexed #(when (= "---" (str/trim %2)) %1) lines))
        second-delim-idx (when first-delim-idx
                           (->> (subvec lines (inc first-delim-idx))
                                (keep-indexed #(when (= "---" (str/trim %2)) %1))
                                first))]
    (if (and first-delim-idx second-delim-idx)
      (let [actual-second-idx (+ (inc first-delim-idx) second-delim-idx)
            yaml-lines (subvec lines (inc first-delim-idx) actual-second-idx)
            body-lines (subvec lines (inc actual-second-idx))
            misplaced-lines (subvec lines 0 first-delim-idx)

            full-malformed-yaml-str (str/join "\n" (concat misplaced-lines yaml-lines))]
        (try
          (let [parsed-data (yaml/parse-string full-malformed-yaml-str :keywords true)
                corrected-data (kebab-case-keys parsed-data)
                repaired-yaml-str (yaml/generate-string corrected-data
                                                        :dumper-options {:flow-style :block})]
            (str "---\n"
                 repaired-yaml-str
                 "---\n"
                 (str/join "\n" body-lines)))
          (catch Exception e
            (println (str "  - [ERROR] Could not parse/repair YAML block. Error: " (.getMessage e)))
            nil)))
      nil)))

(defn -main
  [& args]
  (println "--- Markdown File Repair Utility (v2: Key-aware) ---")
  (let [force-mode? (some #{"--force" "--repair"} args)
        processed-dir (io/file (config/processed-dir-path))]

    (if-not (.exists processed-dir)
      (println "ERROR: Processed directory not found. Cannot run repair.")
      (let [md-files (->> (.listFiles processed-dir)
                          (filter #(.isFile %))
                          (filter #(str/ends-with? (.getName %) ".md")))
            corrupted-files (filter is-corrupted? md-files)]

        (println (str "-> Scanned " (count md-files) " files. Found " (count corrupted-files) " structurally corrupted files."))

        (if (empty? corrupted-files)
          (println "-> No corrupted files to fix.")
          (if force-mode?
            (do
              (println "-> FORCE mode enabled. Repairing files in place...")
              (doseq [file corrupted-files]
                (let [repaired-content (repair-file-content (slurp file))]
                  (if repaired-content
                    (do (spit file repaired-content)
                        (println (str "  - [REPAIRED] " (.getName file))))
                    (println (str "  - [SKIPPED] Could not safely repair " (.getName file)))))))
            (do
              (println "\n-> DRY RUN mode. The following files are corrupted and would be repaired:")
              (doseq [file corrupted-files]
                (println (str "  - [CORRUPTED] " (.getName file))))
              (println "\n-> Re-run with the --force flag to apply these changes."))))))

  (println "--- Repair Utility Finished ---")
  (shutdown-agents)))
