(ns tract.io
  (:require [clj-http.lite.client :as client]
            [clojure.java.io :as io]
            [cheshire.core :as json]))

(defn write-article!
  "Writes the complete article markdown file."
  [{:keys [markdown output-file]}]
  (println "-> Writing article to:" output-file)
  (spit output-file markdown))

(defn download-image!
  "Downloads an image and writes its metadata JSON file."
  [image-job]
  (let [;; The image_path is now a File object, which is correct for I/O
        local-img-file (:image_path image-job)
        img-dir (.getParentFile local-img-file)]
    (try
      ;; Note: We now get the source URL from the job itself, not the complex path
      (println (str "\t-> Downloading " (:image_source_url image-job)))
      (.mkdirs img-dir)
      (with-open [out-stream (io/output-stream local-img-file)]
        (let [response (client/get (:image_source_url image-job) {:as :stream})]
          (io/copy (:body response) out-stream)))

      (let [json-filename (str (:article_key image-job) "_" (hash image-job) ".json")
            json-file (io/file img-dir json-filename)
            ;; **FIXED**: Convert the File object back to a string before JSON encoding.
            serializable-job (update image-job :image_path str)
            json-data (json/generate-string serializable-job {:pretty true})]
        (println (str "\t-> Writing metadata to " json-file))
        (spit json-file json-data))
      (catch Exception e
        (println (str "ERROR: Failed to download " (:image_source_url image-job) ": " (.getMessage e)))))))
