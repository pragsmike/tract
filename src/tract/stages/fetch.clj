(ns tract.stages.fetch
  (:require [tract.pipeline :as pipeline]
            [tract.util :as util]
            [etaoin.api :as e]
            [clojure.string :as str]))

(def ^:private stage-name :fetch)
(def ^:private next-stage-name :parser)

(def ^:private base-throttle-ms 2500)
(def ^:private random-throttle-ms 2000)

(defn- fetch-html!
  "Uses a headless browser to fetch the raw HTML from a given URL.
  Includes a polite throttle before fetching."
  [url-str]
  (let [sleep-duration (+ base-throttle-ms (rand-int random-throttle-ms))]
    (println (str "\t-> Waiting for " sleep-duration "ms..."))
    (Thread/sleep sleep-duration)
    (println (str "\t-> Fetching article from " url-str)))
  (let [driver (e/chrome-headless {:args ["--no-sandbox" "--disable-gpu"]})]
    (try
      (e/go driver url-str)
      (e/get-source driver)
      (finally
        (e/quit driver)))))

(defn- process-url-list-file!
  "Processes a single file containing a list of URLs."
  [file]
  (println (str "-> Processing url-list file: " (.getName file)))
  (try
    (let [urls (->> (slurp file)
                    (str/split-lines)
                    (remove str/blank?))]
      (doseq [url urls]
        (let [html-content (fetch-html! url)
              output-filename (util/url->filename url)]
          (pipeline/write-to-next-stage! html-content next-stage-name output-filename))))
    (pipeline/move-to-done! file stage-name)
    (catch Exception e
      (pipeline/move-to-error! file stage-name e))))

(defn run-stage!
  "Main entry point for the fetch stage. Scans for and processes url-list files."
  []
  (println "--- Running Fetch Stage ---")
  (let [pending-files (pipeline/get-pending-files stage-name)]
    (if (empty? pending-files)
      (println "No url-list files to process.")
      (do
        (println "Found" (count pending-files) "url-list file(s).")
        (doseq [file pending-files]
          (process-url-list-file! file)))))
  (println "--- Fetch Stage Complete ---"))
