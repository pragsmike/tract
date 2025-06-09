(ns tract.core
  (:require [net.cgrand.enlive-html :as html]
            [cheshire.core :as json]
            [clojure.pprint :as pp])
  (:import [java.io StringReader])
  (:gen-class))

(defonce test-html-file "test/page-1.html")

(defn extract-metadata
  "Extracts structured article metadata from a parsed Enlive resource."
  [html-resource]
  (let [json-ld-selector [[:script (html/attr= :type "application/ld+json")]]
        json-str (-> (html/select html-resource json-ld-selector)
                     first
                     html/text)
        parsed-json (json/parse-string json-str true)
        published-date-raw (:datePublished parsed-json)]
    {:title (:headline parsed-json)
     :author (get-in parsed-json [:author 0 :name])
     :publication_date (when published-date-raw
                         (subs published-date-raw 0 10))
     :source_url (:url parsed-json)}))

(defn -main
  "Reads a local HTML file, extracts article metadata, and prints it."
  [& args]
  (println "-> Reading and parsing local file:" test-html-file)
  (try
    (let [html-string (slurp test-html-file)
          ;; Use a StringReader for Enlive to parse from the string
          html-resource (html/html-resource (StringReader. html-string))
          metadata (extract-metadata html-resource)]

      (println "\n--- EXTRACTED METADATA ---")
      (pp/pprint metadata)
      (println "--------------------------"))
    (catch java.io.FileNotFoundException e
      (println "\nERROR: File not found ->" (.getMessage e))
      (println "Please ensure 'test/page-1.html' exists."))))
