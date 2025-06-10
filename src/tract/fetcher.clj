(ns tract.fetcher
  (:require [etaoin.api :as e]
            [clojure.java.io :as io]
            [tract.util :as util])) ; <-- Require the util namespace

(def output-dir "work/1-fetched/pending")

(defn- fetch-html! [url-str]
  "Uses a headless browser to fetch the raw HTML from a given URL."
  (println (str "\t-> Fetching " url-str))
  (let [driver (e/chrome-headless {:args ["--no-sandbox" "--disable-gpu"]})]
    (try
      (e/go driver url-str)
      (e/get-source driver)
      (finally
        (e/quit driver)
        (println "\t-> Browser closed.")))))

(defn -main
  "Main entry point for the fetcher. Takes URLs as arguments."
  [& urls]
  (println "--- Starting Fetcher Step ---")
  (.mkdirs (io/file output-dir))
  (if (empty? urls)
    (println "No URLs provided. Exiting.")
    (doseq [url urls]
      ;; Use the function from the util namespace
      (let [filename (util/url->filename url)
            output-file (io/file output-dir filename)]
        (if (.exists output-file)
          (println (str "-> Skipping " filename ", already exists."))
          (do
            (println (str "-> Processing URL: " url))
            (let [html-content (fetch-html! url)]
              (spit output-file html-content)
              (println (str "-> Saved HTML to " output-file))))))))
  (shutdown-agents)
  (println "--- Fetcher Step Complete ---"))
