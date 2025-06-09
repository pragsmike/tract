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

(defn extract-body-html
  "Selects the main article, removes unwanted elements, and returns it as an HTML string."
  [html-resource]
  (let [article-selector [[:article.newsletter-post]]
        selectors-to-remove [[:div.share-dialog]
                             [:div.subscribe-widget]
                             [:p.button-wrapper]
                             [:div.post-footer]
                             [:div.post-ufi]
                             [:div#discussion]]

        ;; 1. Select the article nodes to start with.
        initial-nodes (html/select html-resource article-selector)

        ;; 2. Use `reduce` to apply one transformation for each selector.
        ;; This chains the transforms: transform(transform(transform(nodes, s1), s2), s3)...
        cleaned-nodes (reduce
                        (fn [nodes-so-far selector]
                          (html/transform nodes-so-far selector (constantly nil)))
                        initial-nodes
                        selectors-to-remove)]

    ;; 3. Emit the final, cleaned nodes back to an HTML string.
    (html/emit* cleaned-nodes)))


(defn -main
  "Reads a local HTML file, extracts content, and prints it."
  [& args]
  (println "-> Reading and parsing local file:" test-html-file)
  (try
    (let [html-string (slurp test-html-file)
          html-resource (html/html-resource (StringReader. html-string))
          metadata (extract-metadata html-resource)
          body-html-seq (extract-body-html html-resource)]

      (println "\n--- EXTRACTED METADATA ---")
      (pp/pprint metadata)
      (println "--------------------------")

      (println "\n--- CLEANED ARTICLE HTML ---")
      (doseq [s body-html-seq] (print s))
      (println "\n----------------------------"))
    (catch java.io.FileNotFoundException e
      (println "\nERROR: File not found ->" (.getMessage e))
      (println "Please ensure 'test/page-1.html' exists."))))
