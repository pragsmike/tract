;; File: src/tract/stages/fetch.clj
(ns tract.stages.fetch
  (:require [tract.pipeline :as pipeline]
            [tract.util :as util]
            [tract.config :as config]
            [tract.db :as db]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            [etaoin.api :as e])
  (:import [java.io StringReader File]
           [java.nio.file Files Paths]))

(def ^:private stage-name :fetch)
(def ^:private next-stage-name :parser)

 (defn- is-short-error-page? [html-string]
  (let [html-len (count html-string)]
    (if (and html-string (> 5000 html-len))
      (try
        (let [parsed (html/html-resource (StringReader. html-string))
              body-text (-> (html/select parsed [:body]) first html/text str/trim)]
          (= body-text "Too Many Requests"))
        (catch Exception _ false))
      false)))

(defn- exponential-backoff-ms [attempt]
  (let [base-wait (* (long (Math/pow 2 attempt)) 1000)
        random-jitter (rand-int 1500)]
    (+ base-wait random-jitter)))

(defn- is-url-already-completed-in-db?
  "Checks if a URL is already known and completed in the database."
  [url-str url->id-map completed-ids-set]
  (when-let [post-id (get url->id-map url-str)]
    (when (contains? completed-ids-set post-id)
      (println (str "\t-> Skipping URL for completed Post ID " post-id ": " url-str))
      true)))

(defn- fetch-html-with-retry! [driver url-str]
  (loop [attempt 0]
    (if (>= attempt (config/fetch-max-retries))
      (throw (ex-info (str "Failed to fetch " url-str " after " (config/fetch-max-retries) " attempts.")
                      {:url url-str :reason "Persistent network errors or rate-limiting"}))
      (do
        (println (str "\t-> Fetching article (attempt " (inc attempt) "): " url-str))
        (e/go driver url-str)
        (let [html-content (e/get-source driver)]
          (if (is-short-error-page? html-content)
            (let [wait-ms (exponential-backoff-ms attempt)]
              (println (str "\t-> Detected 'Too Many Requests' short page. Waiting for " wait-ms "ms..."))
              (Thread/sleep wait-ms)
              (recur (inc attempt)))
            html-content))))))

(defn- write-metadata-file! [content filename]
  (let [metadata-dir (config/metadata-dir-path)
        output-file (io/file metadata-dir filename)]
    (println (str "\t-> Writing metadata to " output-file))
    (spit output-file content)))


(defn- process-url-list-file!
  [driver file url->id-map completed-ids-set ignored-domains-set]
  (println (str "-> Processing url-list file: " (.getName file)))
  (let [urls (->> (slurp file)
                  (str/split-lines)
                  (remove str/blank?))
        html-dir (io/file (config/html-dir-path))
        tmp-dir (io/file html-dir "tmp")]
    (doseq [url urls]
      (let [expected-filename (util/url->filename url)
            symlink-file (io/file (config/stage-dir-path next-stage-name "pending") expected-filename)
            final-html-file (io/file html-dir expected-filename)]
        (cond
          (or (.exists symlink-file) (.exists final-html-file))
          (println (str "\t-> Skipping URL, output file/symlink already exists: " expected-filename))

          (is-url-already-completed-in-db? url url->id-map completed-ids-set)
          :already-completed

          (contains? ignored-domains-set (.getHost (java.net.URL. url)))
          :ignored-domain

          :else
          (let [temp-file (File/createTempFile "tract-" ".html" tmp-dir)]
            (try
              (let [base-ms (config/fetch-throttle-base-ms)
                    random-ms (config/fetch-throttle-random-ms)
                    sleep-duration (+ base-ms (rand-int random-ms))]
                (println (str "\t-> Waiting for " sleep-duration "ms..."))
                (Thread/sleep sleep-duration))

              (let [html-content (fetch-html-with-retry! driver url)
                    meta-filename (str expected-filename ".meta.json")
                    meta-content {:source-url url
                                  :fetch-timestamp (.toString (java.time.Instant/now))}
                    json-content (json/generate-string meta-content {:pretty true})]

                (spit temp-file html-content)
                (.renameTo temp-file final-html-file)
                (println (str "\t=> Saved HTML to permanent location: " final-html-file))

                (write-metadata-file! json-content meta-filename)

                (let [link-path (.toPath symlink-file)
                      ;; The symlink is in .../parser/pending/, so we go up two levels.
                      relative-target (Paths/get "../../html" (.getName final-html-file))]
                  (Files/createSymbolicLink link-path relative-target)
                  (println (str "\t=> Created symlink for parser: " link-path))))

              (catch Exception e
                (.delete temp-file)
                (let [ex-data-map (ex-data e)]
                  (if (= (:type ex-data-map) :etaoin/http-ex)
                    (do
                      (println "\nFATAL ERROR: The connection to the browser appears to be broken.")
                      (throw e))
                    (println (str "ERROR: Failed to process URL [" url "]. Skipping. Reason: " (.getMessage e)))))))))))
    (pipeline/move-to-done! file stage-name)))


(defn run-stage!
  "Main entry point for the fetch stage. Receives a pre-configured driver."
  [driver]
  (println "--- Running Fetch Stage ---")
  (let [pending-files (pipeline/get-pending-files stage-name)]
    (if (seq pending-files)
      (do
        (println "-> Loading completion databases for pre-fetch checks...")
        (let [url->id-map (db/read-url-to-id-map)
              completed-ids-set (db/read-completed-post-ids)
              ignored-domains-set (db/read-ignore-list)]
          (println "-> Beginning to process" (count pending-files) "url-list file(s) with shared browser session...")
          (try
            (doseq [file pending-files]
              (process-url-list-file! driver file url->id-map completed-ids-set ignored-domains-set))
            (catch Exception e
              (println "-> Fatal error detected. Propagating to main process to exit.")
              (throw e)))))
      (println "No url-list files to process.")))
  (println "--- Fetch Stage Complete ---"))
