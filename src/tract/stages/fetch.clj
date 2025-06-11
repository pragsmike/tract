(ns tract.stages.fetch
  (:require [tract.pipeline :as pipeline]
            [tract.util :as util]
            [etaoin.api :as e]
            [clojure.string :as str]))

(def ^:private stage-name :fetch)
(def ^:private next-stage-name :parser)

(def ^:private max-fetch-retries 5)
(def ^:private base-throttle-ms 2500)
(def ^:private random-throttle-ms 2000)

;; --- The login! function is now completely removed. ---

(defn- exponential-backoff-ms [attempt]
  (let [base-wait (* (long (Math/pow 2 attempt)) 1000)
        random-jitter (rand-int 1500)]
    (+ base-wait random-jitter)))

(defn- detect-429-error? [driver]
  (try
    (str/includes? (or (e/get-title driver) "") "Too Many Requests")
    (catch Exception _ false)))

(defn- fetch-html-with-retry! [driver url-str]
  (loop [attempt 0]
    (if (>= attempt max-fetch-retries)
      (throw (ex-info (str "Failed to fetch after " max-fetch-retries " attempts.")
                      {:url url-str :reason "Persistent 429 errors"}))
      (do
        (println (str "\t-> Fetching article: " url-str))
        (e/go driver url-str)
        (if (detect-429-error? driver)
          (let [wait-ms (exponential-backoff-ms attempt)]
            (println (str "\t-> Detected 429. Waiting for " wait-ms "ms before retrying..."))
            (Thread/sleep wait-ms)
            (recur (inc attempt)))
          (e/get-source driver))))))

(defn- process-url-list-file! [driver file]
  (println (str "-> Processing url-list file: " (.getName file)))
  (try
    (let [urls (->> (slurp file)
                    (str/split-lines)
                    (remove str/blank?))]
      (doseq [url urls]
        (let [sleep-duration (+ base-throttle-ms (rand-int random-throttle-ms))]
          (println (str "\t-> Waiting for " sleep-duration "ms..."))
          (Thread/sleep sleep-duration))
        (let [html-content (fetch-html-with-retry! driver url)
              output-filename (util/url->filename url)]
          (pipeline/write-to-next-stage! html-content next-stage-name output-filename))))
    (pipeline/move-to-done! file stage-name)
    (catch Exception e
      (pipeline/move-to-error! file stage-name e))))

(defn run-stage!
  "Main entry point for the fetch stage.
  Receives a pre-connected driver and processes all pending files."
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
