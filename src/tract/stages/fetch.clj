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
            ;; Add clj-http-lite for HEAD requests
            [clj-http.lite.client :as client]))
  (:import [java.io StringReader]))

(def ^:private stage-name :fetch)
(def ^:private next-stage-name :parser)

(defn- is-short-error-page?
  [html-string]
  (let [html-len (count html-string)]
    (if (and html-string (> 5000 html-len))
      (try
        (let [parsed (html/html-resource (StringReader. html-string))
              body-text (-> (html/select parsed [:body]) first html/text str/trim)]
          (= body-text "Too Many Requests"))
        (catch Exception _ false))
      false)))

(defn- exponential-backoff-ms [attempt]
  (let [base-wait (* (long (Math.pow 2 attempt)) 1000)
        random-jitter (rand-int 1500)]
    (+ base-wait random-jitter)))

;; --- NEW PRE-FETCH CHECK LOGIC ---

(defn- get-post-id-from-head-request
  "Performs a lightweight HEAD request to find the x-substack-post-id header."
  [url-str]
  (try
    (let [response (client/head url-str {:throw-exceptions false})
          post-id (get-in response [:headers "x-substack-post-id"])]
      (when post-id
        (println (str "\t-> Discovered Post ID via HEAD request: " post-id))
        post-id))
    (catch Exception e
      (println (str "\t-> WARN: HEAD request failed for " url-str ": " (.getMessage e)))
      nil)))

(defn- is-url-already-completed?
  "The core pre-fetch check. Determines if a URL's article is already completed."
  [url-str url->id-map completed-ids-set]
  (let [post-id (or (get url->id-map url-str)
                    (get-post-id-from-head-request url-str))]
    (if (and post-id (contains? completed-ids-set post-id))
      (do
        (println (str "\t-> Skipping URL for completed Post ID " post-id ": " url-str))
        true)
      false)))
;; --- END NEW PRE-FETCH CHECK LOGIC ---

(defn- fetch-html-with-retry! [driver url-str]
  ;; This function remains the same as before
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

(defn- write-meta-file!
  "Writes a .meta file for a corresponding HTML file."
  [url output-filename]
  (let [meta-filename (str output-filename ".meta")
        meta-content {:source_url url
                      :fetch_timestamp (.toString (java.time.Instant/now))}
        json-content (json/generate-string meta-content {:pretty true})]
    (pipeline/write-to-next-stage! json-content next-stage-name meta-filename)))

(defn- process-url-list-file!
  "Processes a single file containing a list of URLs using a persistent driver."
  [driver file url->id-map completed-ids-set]
  (println (str "-> Processing url-list file: " (.getName file)))
  (let [urls (->> (slurp file)
                  (str/split-lines)
                  (remove str/blank?))]
    (doseq [url urls]
      ;; --- MODIFIED: Wrap fetch logic in pre-fetch check ---
      (if (is-url-already-completed? url url->id-map completed-ids-set)
        :already-completed
        (try
          (let [base-ms (config/fetch-throttle-base-ms)
                random-ms (config/fetch-throttle-random-ms)
                sleep-duration (+ base-ms (rand-int random-ms))]
            (println (str "\t-> Waiting for " sleep-duration "ms..."))
            (Thread/sleep sleep-duration))

          (let [html-content (fetch-html-with-retry! driver url)
                output-filename (util/url->filename url)]
            (pipeline/write-to-next-stage! html-content next-stage-name output-filename)
            (write-meta-file! url output-filename))

          (catch Exception e
            ;; This error handling for etaoin remains the same
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
    (if (seq pending-files)
      (do
        ;; --- MODIFIED: Load DBs once per run ---
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
