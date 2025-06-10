(ns tract.parser
  (:require [net.cgrand.enlive-html :as html]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [java.io StringReader]))

(defn- extract-metadata [html-resource]
  (let [json-ld-selector [[:script (html/attr= :type "application/ld+json")]]
        json-str (-> (html/select html-resource json-ld-selector) first html/text)
        parsed-json (json/parse-string json-str true)]
    {:title (:headline parsed-json)
     :author (get-in parsed-json [:author 0 :name])
     :publication_date (some-> (:datePublished parsed-json) (subs 0 10))
     :source_url (:url parsed-json)}))

(defn- extract-body-nodes [html-resource]
  (let [body-selector [:div.body.markup]
        nodes-to-remove [[:div.subscribe-widget]
                         [:div.digestPostEmbed-flwiST]
                         [:p (html/attr? :data-component-name)]]
        body-nodes (-> (html/select html-resource body-selector) first :content)]
    (reduce (fn [nodes sel] (html/transform nodes sel (constantly nil)))
            body-nodes nodes-to-remove)))

(defn parse-html
  "Takes an HTML string and returns a map of {:metadata ... :body-nodes ...}"
  [html-string]
  (let [html-resource (html/html-resource (StringReader. html-string))]
    {:metadata (extract-metadata html-resource)
     :body-nodes (extract-body-nodes html-resource)}))
