(ns tract.parser
  (:require [net.cgrand.enlive-html :as html]
            [cheshire.core :as json]
            [tract.util :as util]
            [clojure.string :as str])
  (:import [java.io StringReader]))


(defn- extract-metadata [html-resource source-url]
  (let [;; --- NEW PRIMARY METHOD: Find Canonical URL and extract slug ---
        canonical-url (some-> (html/select html-resource [[:link (html/attr= :rel "canonical")]])
                              first :attrs :href)
        canonical-id (util/extract-slug-from-url canonical-url)

        ;; --- Fallback Methods ---
        json-ld-node (first (html/select html-resource [[:script (html/attr= :type "application/ld+json")]]))
        parsed-json (when json-ld-node (json/parse-string (html/text json-ld-node) true))
        title-fallback (some-> (html/select html-resource [:title]) first html/text str/trim)
        author-fallback (some-> (html/select html-resource [[:meta (html/attr= :name "author")]]) first :attrs :content)
        date-fallback (some-> (html/select html-resource [[:meta (html/attr= :property "article:published_time")]])
                              first :attrs :content (subs 0 10))]

    ;; CORRECTED: This map now uses the mandatory kebab-case standard for all keys.
    {:title            (or (:headline parsed-json) title-fallback)
     :author           (or (get-in parsed-json [:author 0 :name]) author-fallback "unknown")
     :publication-date (or (some-> (:datePublished parsed-json) (subs 0 10)) date-fallback)
     :source-url       source-url
     :canonical-url    (or canonical-url source-url)
     :post-id          canonical-id})) ; The post-id is the canonical slug.

(defn- extract-body-nodes [html-resource]
  (let [substack-body (-> (html/select html-resource [:div.body.markup]) first :content)
        article-body (-> (html/select html-resource [:article]) first :content)
        generic-body (-> (html/select html-resource [:body]) first :content)]
    (or substack-body article-body generic-body)))

(defn parse-html
  "Takes an HTML string and its original source URL,
  and returns a map of {:metadata ... :body-nodes ...}"
  [html-string source-url]
  (let [html-resource (html/html-resource (StringReader. html-string))]
    {:metadata   (extract-metadata html-resource source-url)
     :body-nodes (extract-body-nodes html-resource)}))
