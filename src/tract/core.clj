(ns tract.core
  (:require [tract.parser :as parser]
            [tract.compiler :as compiler]
            [tract.io :as io])
  (:gen-class))

(defonce test-html-file "test/page-1.html")

(defn -main
  "Main entry point for processing a single HTML file."
  [& args]
  (println "-> Starting extraction from:" test-html-file)
  (try
    (let [html-string (slurp test-html-file)
          ;; 1. Parse the HTML into structured data
          parsed-data (parser/parse-html html-string)
          ;; 2. Compile the data into a final article map and a list of image jobs
          {:keys [article images]} (compiler/compile-to-article parsed-data)]
      ;; 3. Perform I/O side-effects
      (io/write-article! article)
      (println "-> Processing" (count images) "images...")
      (doseq [job images]
        (io/download-image! job))
      (println "-> Done."))
    (catch Exception e
      (println "\nAn error occurred:")
      (.printStackTrace e))))
