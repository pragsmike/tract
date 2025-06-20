Excellent. I agree this is the most effective and least intrusive solution. It addresses the inefficiency without altering the core logic of the `fetch` stage.

Here is the plan to implement this change in the `parser` stage.

### The Plan

1.  **Modify `tract.stages.parser/process-html-file!`:** This is the only function that needs to be changed.
2.  **Add an early exit check:** Right after the HTML is parsed and the metadata (including the canonical `:post-id`/slug) is determined, we will perform a check.
3.  **The Check:**
    *   Load the set of completed post IDs from `db/read-completed-post-ids`.
    *   See if the newly parsed `:post-id` already exists in that set.
4.  **Logic Branching:**
    *   **If the ID is new (not in the set):** Proceed with the existing logic: compile the markdown, download images, write the `.md` file, and record completion in the database.
    *   **If the ID is already completed:** This means we've found a new alias for an existing article. We will:
        *   Print a log message indicating that a duplicate was found and that we are updating the database.
        *   Call `db/record-completion!` with the new metadata. This is crucial because it will add the *new* `source-url` to the `url-to-id.map`, making the system smarter for the future.
        *   **Skip** the expensive `compile-to-article`, image downloading, and file writing steps.
        *   Move the source symlink to `done` as usual.

This approach is efficient, safe, and improves the system's "knowledge" without redundant work.

### Proposed Code Change

I will now generate the modified `tract.stages.parser.clj` file. The changes will be localized within the `process-html-file!` function.

```clojure
;; File: src/tract/stages/parser.clj

(ns tract.stages.parser
  (:require [tract.pipeline :as pipeline]
            [tract.parser :as parser-logic]
            [tract.compiler :as compiler]
            [tract.io :as io]
            [tract.config :as config]
            [clojure.java.io :as jio]
            [clojure.string :as str]
            [tract.db :as db]
            [cheshire.core :as json]))

(def ^:private stage-name :parser)
(def ^:private output-dir (config/processed-dir-path))

(defn ->fetcher-metadata
  "Returns fetcher's metadata for the given html file.
      :source-url
      :fetch-timestamp
  "
  [html-filename]
  (let [meta-filename (str html-filename ".meta.json")
        meta-file (jio/file (config/metadata-dir-path) meta-filename)]
    (when-not (.exists meta-file)
      (throw (ex-info (str "Missing metadata file: " meta-filename) {:html-file html-filename})))
    (json/parse-string (slurp meta-file) true)))

(defn full-metadata [metadata fetcher-metadata]
  ;; CORRECTED: Use kebab-case keys for all metadata access and creation.
  (let [article-key (let [slug (:post-id metadata)]
                      (if (str/blank? slug)
                        (str "unknown-article_" (System/currentTimeMillis))
                        slug))
        metadata (assoc metadata
                        :title (or (:title metadata) "Untitled")
                        :publication-date (or (:publication-date metadata) "unknown")
                        :source-url (or (:source-url metadata) "unknown")
                        :article-key article-key)]
    (merge metadata fetcher-metadata)))

(defn- process-html-file!
  "Processes a single HTML file from the pending directory."
  [html-file]
  (println (str "-> Processing HTML file: " (.getName html-file)))
  (try
    (let [;; --- Start of new logic ---
          completed-ids (db/read-completed-post-ids)
          ;; --- End of new logic ---
          {:keys [metadata body-nodes]} (parser-logic/parse-html (slurp html-file))
          fetcher-meta-data (->fetcher-metadata (.getName html-file))
          metadata (full-metadata metadata fetcher-meta-data)
          post-id (:post-id metadata)]

      ;; --- Start of new logic ---
      (if (and post-id (contains? completed-ids post-id))
        (do
          (println (str "\t-> Duplicate article found (ID: " post-id ")."))
          (println "\t-> Updating URL map and skipping re-compilation.")
          (db/record-completion! (select-keys metadata [:post-id :source-url :canonical-url])))
        (do
          (println (str "\t-> New article (ID: " post-id "). Compiling to Markdown."))
          (let [{:keys [markdown images]} (compiler/compile-to-article metadata body-nodes)
                output-path (jio/file output-dir)]

            (.mkdirs output-path)
            (let [md-file (jio/file output-path (str (:article-key metadata) ".md"))]
              (io/write-article! md-file markdown))

            (println (str "\t-> Processing " (count images) " images for " (:article-key metadata)))
            (doseq [job images]
              (let [job-with-output-dir (update job :image-path #(jio/file output-path %))]
                (io/download-image! job-with-output-dir)))

            (db/record-completion! (select-keys metadata [:post-id :source-url :canonical-url])))))
      ;; --- End of new logic ---
      )
    (pipeline/move-to-done! html-file stage-name)
    (catch Exception e
      (pipeline/move-to-error! html-file stage-name e))))

(defn run-stage!
  "Main entry point for the parser stage. Scans for and processes HTML files."
  []
  (println "--- Running Parser Stage ---")
  (let [pending-files (pipeline/get-pending-files stage-name)]
    (if (empty? pending-files)
      (println "No HTML files to process.")
      (do
        ;; --- Start of new logic ---
        ;; Optimization: Load the completed IDs set once for the entire batch.
        (println "Found" (count pending-files) "HTML file(s).")
        (doseq [file pending-files]
           (process-html-file! file))
        ;; --- End of new logic ---
        ))
  (println "--- Parser Stage Complete ---"))
```

I have slightly modified the `run-stage!` function to pass the `completed-ids` set into `process-html-file!`. This is a minor optimization to avoid re-reading the log file from disk for every single HTML file in a batch. I will update the `process-html-file!` definition to reflect this.

Here is the final, optimized version.

```clojure
;; File: src/tract/stages/parser.clj

(ns tract.stages.parser
  (:require [tract.pipeline :as pipeline]
            [tract.parser :as parser-logic]
            [tract.compiler :as compiler]
            [tract.io :as io]
            [tract.config :as config]
            [clojure.java.io :as jio]
            [clojure.string :as str]
            [tract.db :as db]
            [cheshire.core :as json]))

(def ^:private stage-name :parser)
(def ^:private output-dir (config/processed-dir-path))

(defn ->fetcher-metadata
  "Returns fetcher's metadata for the given html file.
      :source-url
      :fetch-timestamp
  "
  [html-filename]
  (let [meta-filename (str html-filename ".meta.json")
        meta-file (jio/file (config/metadata-dir-path) meta-filename)]
    (when-not (.exists meta-file)
      (throw (ex-info (str "Missing metadata file: " meta-filename) {:html-file html-filename})))
    (json/parse-string (slurp meta-file) true)))

(defn full-metadata [metadata fetcher-metadata]
  ;; CORRECTED: Use kebab-case keys for all metadata access and creation.
  (let [article-key (let [slug (:post-id metadata)]
                      (if (str/blank? slug)
                        (str "unknown-article_" (System/currentTimeMillis))
                        slug))
        metadata (assoc metadata
                        :title (or (:title metadata) "Untitled")
                        :publication-date (or (:publication-date metadata) "unknown")
                        :source-url (or (:source-url metadata) "unknown")
                        :article-key article-key)]
    (merge metadata fetcher-metadata)))

(defn- process-html-file!
  "Processes a single HTML file from the pending directory."
  [html-file completed-ids-set]
  (println (str "-> Processing HTML file: " (.getName html-file)))
  (try
    (let [{:keys [metadata body-nodes]} (parser-logic/parse-html (slurp html-file))
          fetcher-meta-data (->fetcher-metadata (.getName html-file))
          metadata (full-metadata metadata fetcher-meta-data)
          post-id (:post-id metadata)]

      (if (and post-id (contains? completed-ids-set post-id))
        ;; Case: Article is a duplicate.
        (do
          (println (str "\t-> Duplicate article found (ID: " post-id ")."))
          (println "\t-> Updating URL map and skipping re-compilation.")
          (db/record-completion! (select-keys metadata [:post-id :source-url :canonical-url])))

        ;; Case: Article is new.
        (do
          (println (str "\t-> New article (ID: " post-id "). Compiling to Markdown."))
          (let [{:keys [markdown images]} (compiler/compile-to-article metadata body-nodes)
                output-path (jio/file output-dir)]

            (.mkdirs output-path)
            (let [md-file (jio/file output-path (str (:article-key metadata) ".md"))]
              (io/write-article! md-file markdown))

            (println (str "\t-> Processing " (count images) " images for " (:article-key metadata)))
            (doseq [job images]
              (let [job-with-output-dir (update job :image-path #(jio/file output-path %))]
                (io/download-image! job-with-output-dir)))

            (db/record-completion! (select-keys metadata [:post-id :source-url :canonical-url])))))
      )
    (pipeline/move-to-done! html-file stage-name)
    (catch Exception e
      (pipeline/move-to-error! html-file stage-name e))))

(defn run-stage!
  "Main entry point for the parser stage. Scans for and processes HTML files."
  []
  (println "--- Running Parser Stage ---")
  (let [pending-files (pipeline/get-pending-files stage-name)]
    (if (empty? pending-files)
      (println "No HTML files to process.")
      (do
        (println "Found" (count pending-files) "HTML file(s).")
        ;; Optimization: Load the completed IDs set once for the entire batch.
        (let [completed-ids (db/read-completed-post-ids)]
          (println (str "-> Loaded " (count completed-ids) " completed article IDs for de-duplication."))
          (doseq [file pending-files]
            (process-html-file! file completed-ids))))))
  (println "--- Parser Stage Complete ---"))

```
This final version is clean, efficient, and adheres to the established architectural principles. I have verified parenthesis balance and nil-safety (the `and post-id` part of the `if` condition handles cases where no slug can be found). The logic is sound.
