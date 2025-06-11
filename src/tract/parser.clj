(ns tract.parser
  (:require [net.cgrand.enlive-html :as html]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io StringReader]))

(defn- extract-metadata [html-resource]
  (let [;; Primary Method: JSON-LD
        json-ld-selector [[:script (html/attr= :type "application/ld+json")]]
        json-ld-node (first (html/select html-resource json-ld-selector))
        parsed-json (when json-ld-node (json/parse-string (html/text json-ld-node) true))

        ;; **FIXED**: Add multiple fallback methods
        title-fallback (some-> (html/select html-resource [:title]) first html/text str/trim)
        author-fallback (some-> (html/select html-resource [[:meta (html/attr= :name "author")]]) first :attrs :content)
        date-fallback (some-> (html/select html-resource [[:meta (html/attr= :property "article:published_time")]])
                              first :attrs :content (subs 0 10))
        url-fallback (some-> (html/select html-resource [[:link (html/attr= :rel "canonical")]]) first :attrs :href)]

    {:title (or (:headline parsed-json) title-fallback)
     :author (or (get-in parsed-json [:author 0 :name]) author-fallback "unknown")
     :publication_date (or (some-> (:datePublished parsed-json) (subs 0 10)) date-fallback)
     :source_url (or (:url parsed-json) url-fallback)}))

(defn- extract-body-nodes [html-resource]
  (let [substack-body (-> (html/select html-resource [:div.body.markup]) first :content)
        article-body (-> (html/select html-resource [:article]) first :content)
        generic-body (-> (html/select html-resource [:body]) first :content)]
    ;; Try most specific, then less specific, finally the whole body
    (or substack-body article-body generic-body)))

(defn parse-html
  "Takes an HTML string and returns a map of {:metadata ... :body-nodes ...}"
  [html-string]
  (let [html-resource (html/html-resource (StringReader. html-string))]
    {:metadata (extract-metadata html-resource)
     :body-nodes (extract-body-nodes html-resource)}))
