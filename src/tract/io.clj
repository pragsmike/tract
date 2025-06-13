(ns tract.io
  (:require [clj-http.lite.client :as client]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [tract.config :as config]))

(defn throttled-fetch!
  "Fetches the body of a URL after a polite, randomized delay.
  Returns the response body as a string. Throws on error."
  [url]
  (let [base-ms (config/http-throttle-base-ms)
        random-ms (config/http-throttle-random-ms)
        sleep-duration (+ base-ms (rand-int random-ms))]
       (println (str "\t-> Waiting for " sleep-duration "ms..."))
       (Thread/sleep sleep-duration)
       (println (str "\t-> Fetching feed/data from " url))
       (:body (client/get url))))

(defn write-article!
  "Writes the complete article markdown file."
  [{:keys [markdown output-file]}]
  (println "-> Writing article to:" output-file)
  (spit output-file markdown))

(defn download-image!
  "Downloads an image and writes its metadata JSON file."
  [image-job]
  (let [local-img-file (:image_path image-job)
        img-dir (.getParentFile local-img-file)]
    (try
      ;; This function fetches many small files, so we use a shorter throttle.
      ;; The main article fetch has its own, longer throttle.
      (Thread/sleep 50)
      (println (str "\t-> Downloading " (:image_source_url image-job)))
      (.mkdirs img-dir)
      (with-open [out-stream (io/output-stream local-img-file)]
        (let [response (client/get (:image_source_url image-job) {:as :stream})]
          (io/copy (:body response) out-stream)))

      (let [json-filename (str (:article_key image-job) "_" (hash image-job) ".json")
            json-file (io/file img-dir json-filename)
            serializable-job (update image-job :image_path str)
            json-data (json/generate-string serializable-job {:pretty true})]
        (println (str "\t-> Writing metadata to " json-file))
        (spit json-file json-data))
      (catch Exception e
        (println (str "ERROR: Failed to download " (:image_source_url image-job) ": " (.getMessage e)))))))
