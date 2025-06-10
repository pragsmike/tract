(ns tract.stages.fetch
  (:require [tract.pipeline :as pipeline]
            [tract.util :as util]
            [etaoin.api :as e]
            [clojure.string :as str]))

(def ^:private stage-name :fetch)
(def ^:private next-stage-name :parser)

(defn- fetch-html!
  "Uses a headless browser to fetch the raw HTML from a given URL.
  Throws an exception on failure."
  [url-str]
  (println (str "\t-> Fetching " url-str))
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
    ;; If all URLs in the file were processed successfully, move it to done.
    (pipeline/move-to-done! file stage-name)
    (catch Exception e
      ;; If any URL fails, move the entire list file to the error directory.
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
  ;; The master orchestrator will call shutdown-agents
  (println "--- Fetch Stage Complete ---"))
