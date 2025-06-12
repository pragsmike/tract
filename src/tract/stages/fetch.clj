(ns tract.stages.fetch
  (:require [tract.pipeline :as pipeline]
            [tract.util :as util]
            [etaoin.api :as e]
            [clojure.string :as str]
            [net.cgrand.enlive-html :as html]) ; <-- Add require for enlive
  (:import [java.io StringReader])) ;<-- Add require for StringReader

(def ^:private stage-name :fetch)
(def ^:private next-stage-name :parser)

(def ^:private max-fetch-retries 5)
(def ^:private base-throttle-ms 2500)
(def ^:private random-throttle-ms 2000)

(defn- is-short-error-page?
  "Detects if the fetched HTML is a simple 'Too Many Requests' error page."
  [html-string]
  (let [html-len (count html-string)]
    (if (and html-string (> 5000 html-len)) ; Only parse if it's a small page
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

(defn- fetch-html-with-retry! [driver url-str]
  (loop [attempt 0]
    (if (>= attempt max-fetch-retries)
      (throw (ex-info (str "Failed to fetch " url-str " after " max-fetch-retries " attempts.")
                      {:url url-str :reason "Persistent network errors or rate-limiting"}))
      (do
        (println (str "\t-> Fetching article (attempt " (inc attempt) "): " url-str))
        (e/go driver url-str)
        (let [html-content (e/get-source driver)]
          (if (is-short-error-page? html-content) ; <-- Our new check
            (let [wait-ms (exponential-backoff-ms attempt)]
              (println (str "\t-> Detected 'Too Many Requests' short page. Waiting for " wait-ms "ms..."))
              (Thread/sleep wait-ms)
              (recur (inc attempt)))
            ;; Success! Return the valid HTML.
            html-content))))))

(defn- process-url-list-file!
  "Processes a single file containing a list of URLs using a persistent driver."
  [driver file]
  (println (str "-> Processing url-list file: " (.getName file)))
  (let [urls (->> (slurp file)
                  (str/split-lines)
                  (remove str/blank?))]
    (doseq [url urls]
      (try
        (let [sleep-duration (+ base-throttle-ms (rand-int random-throttle-ms))]
          (println (str "\t-> Waiting for " sleep-duration "ms..."))
          (Thread/sleep sleep-duration))
        (let [html-content (fetch-html-with-retry! driver url)
              output-filename (util/url->filename url)]
          (pipeline/write-to-next-stage! html-content next-stage-name output-filename))
        (catch Exception e
          (println (str "ERROR: Failed to process URL [" url "]. Skipping. Reason: " (.getMessage e)))))))
  (pipeline/move-to-done! file stage-name))

(defn run-stage!
  "Main entry point for the fetch stage. Receives a pre-configured driver."
  [driver]
  (println "--- Running Fetch Stage ---")
  (let [pending-files (pipeline/get-pending-files stage-name)]
    (if (seq pending-files)
      (do
        (println "-> Beginning to process" (count pending-files) "url-list file(s) with shared browser session...")
        (doseq [file pending-files]
          (process-url-list-file! driver file)))
      (println "No url-list files to process.")))
  (println "--- Fetch Stage Complete ---"))
