;; File: src/tract/stages/fetch.clj
(ns tract.stages.fetch
  (:require [tract.pipeline :as pipeline]
            [tract.util :as util]
            [tract.config :as config]
            [tract.db :as db]
            [etaoin.api :as e]
            [clojure.string :as str]
            [net.cgrand.enlive-html :as html]
            [cheshire.core :as json]
            [clj-http.lite.client :as client]
            [clojure.java.io :as io])
  (:import [java.io StringReader]))

(def ^:private stage-name :fetch)
(def ^:private next-stage-name :parser)

;; The delete-recursively! function is no longer needed and has been removed.

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

(defn- get-post-id-from-head-request [url-str]
  (try
    (let [response (client/head url-str {:throw-exceptions false})
          post-id (get-in response [:headers "x-substack-post-id"])]
      (when post-id
        (println (str "\t-> Discovered Post ID via HEAD request: " post-id))
        post-id))
    (catch Exception e
      (println (str "\t-> WARN: HEAD request failed for " url-str ": " (.getMessage e)))
      nil)))

(defn- is-url-already-completed? [url-str url->id-map completed-ids-set]
  (let [post-id (or (get url->id-map url-str)
                    (get-post-id-from-head-request url-str))]
    (if (and post-id (contains? completed-ids-set post-id))
      (do
        (println (str "\t-> Skipping URL for completed Post ID " post-id ": " url-str))
        true)
      false)))

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

;; --- NEW METADATA WRITE HELPER ---
(defn- write-metadata-file!
  "Writes the metadata to a file in the central metadata directory."
  [content filename]
  (let [metadata-dir (config/metadata-dir-path)
        output-file (io/file metadata-dir filename)]
    (println (str "\t-> Writing metadata to " output-file))
    (spit output-file content)))
;; --- END NEW HELPER ---

(defn- process-url-list-file!
  [driver file url->id-map completed-ids-set]
  (println (str "-> Processing url-list file: " (.getName file)))
  (let [urls (->> (slurp file)
                  (str/split-lines)
                  (remove str/blank?))]
    (doseq [url urls]
      (when-not (is-url-already-completed? url url->id-map completed-ids-set)
        (try
          (let [base-ms (config/fetch-throttle-base-ms)
                random-ms (config/fetch-throttle-random-ms)
                sleep-duration (+ base-ms (rand-int random-ms))]
            (println (str "\t-> Waiting for " sleep-duration "ms..."))
            (Thread/sleep sleep-duration))

          (let [html-content (fetch-html-with-retry! driver url)
                output-filename (util/url->filename url)
                meta-filename (str output-filename ".meta.json") ; Use a clearer extension
                meta-content {:source-url url
                              :fetch-timestamp (.toString (java.time.Instant/now))}
                json-content (json/generate-string meta-content {:pretty true})]

            ;; 1. Write the HTML file to the next stage's pending directory.
            (pipeline/write-to-next-stage! html-content next-stage-name output-filename)
            ;; 2. Write the metadata to the central metadata directory.
            (write-metadata-file! json-content meta-filename))

          (catch Exception e
            (let [ex-data-map (ex-data e)]
              (if (= (:type ex-data-map) :etaoin/http-ex)
                (do
                  (println "\nFATAL ERROR: The connection to the browser appears to be broken.")
                  (throw e))
                (println (str "ERROR: Failed to process URL [" url "]. Skipping. Reason: " (.getMessage e))))))))))
  (pipeline/move-to-done! file stage-name))

(defn run-stage!
  "Main entry point for the fetch stage. Receives a pre-configured driver."
  [driver]
  (println "--- Running Fetch Stage ---")
  (let [pending-files (pipeline/get-pending-files stage-name)]
    ;; The temporary directory setup is no longer needed here.
    (if (seq pending-files)
      (do
        (println "-> Loading completion databases for pre-fetch checks...")
        (let [url->id-map (db/read-url-to-id-map)
              completed-ids-set (db/read-completed-post-ids)]
          (println "-> Beginning to process" (count pending-files) "url-list file(s) with shared browser session...")
          (try
            (doseq [file pending-files]
              (process-url-list-file! driver file url->id-map completed-ids-set))
            (catch Exception e
              (println "-> Fatal error detected. Propagating to main process to exit.")
              (throw e)))))
      (println "No url-list files to process.")))
  (println "--- Fetch Stage Complete ---"))
