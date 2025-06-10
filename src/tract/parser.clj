(ns tract.parser
  (:require [net.cgrand.enlive-html :as html]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.core :as c])
  (:import [java.io StringReader]))

(defn- extract-metadata [html-resource]
  (let [;; Primary Method: JSON-LD
        json-ld-selector [[:script (html/attr= :type "application/ld+json")]]
        json-ld-node (first (html/select html-resource json-ld-selector))
        parsed-json (when json-ld-node (json/parse-string (html/text json-ld-node) true))

        ;; **FIXED**: Fallback Method: Standard HTML tags
        title-fallback (-> (html/select html-resource [:title]) first html/text str/trim)
        date-fallback (-> (html/select html-resource [[:meta (html/attr= :property "article:published_time")]])
                          first :attrs :content (some-> (subs 0 10)))
        ]
    {:title (or (:headline parsed-json) title-fallback "untitled")
     :author (get-in parsed-json [:author 0 :name] "unknown")
     :publication_date (or (c/some-> (:datePublished parsed-json) (subs 0 10))
                           date-fallback)
     :source_url (:url parsed-json)}))

(defn- extract-body-nodes [html-resource]
  ;; This remains specific. We'll attempt it, but it might fail gracefully.
  (let [body-selector [:div.body.markup] ; Substack specific
        body-nodes (-> (html/select html-resource body-selector) first :content)]
    (if (seq body-nodes)
      (let [nodes-to-remove [[:div.subscribe-widget]
                             [:div.digestPostEmbed-flwiST]
                             [:p (html/attr? :data-component-name)]]]
        (reduce (fn [nodes sel] (html/transform nodes sel (constantly nil)))
                body-nodes nodes-to-remove))
      ;; **FIXED**: If our specific selectors fail, fall back to the whole body.
      (-> (html/select html-resource [:body]) first :content))))

(defn parse-html
  "Takes an HTML string and returns a map of {:metadata ... :body-nodes ...}"
  [html-string]
  (let [html-resource (html/html-resource (StringReader. html-string))]
    {:metadata (extract-metadata html-resource)
     :body-nodes (extract-body-nodes html-resource)}))
