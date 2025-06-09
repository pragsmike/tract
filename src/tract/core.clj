(ns tract.core
  (:require [net.cgrand.enlive-html :as html]
            [cheshire.core :as json]
            [clojure.pprint :as pp]
            [clojure.string :as str])
  (:import [java.io StringReader])
  (:gen-class))

(defonce test-html-file "test/page-1.html")

;; --- FORWARD DECLARATION ---
(declare html-to-markdown)

;; --- NODE PROCESSING LOGIC ---

(defn process-image-node [node]
  "Processes an image container (figure) and extracts image and caption info."
  (let [img-node (-> (html/select node [:img]) first)
        caption-node (-> (html/select node [:figcaption]) first)
        img-src (get-in img-node [:attrs :src])
        alt-text (get-in img-node [:attrs :alt] "")
        caption (if caption-node (html/text caption-node) "")]
    (str "\n" (format "![%s](%s)" (or (not-empty alt-text) caption) img-src) "\n"
         (when (not-empty caption) (str "*" caption "*\n")))))

(defn process-node [node]
  "Recursively processes a single Enlive node and its children."
  (if (string? node)
    (str/trim node) ; Trim whitespace from raw text nodes
    (let [tag (:tag node)
          attrs (:attrs node)
          content (html-to-markdown (:content node))]
      (case tag
        :p (str "\n" content "\n")
        :h1 (str "\n# " content "\n")
        :h2 (str "\n## " content "\n")
        :h3 (str "\n### " content "\n")
        :strong (str "**" content "**")
        :em (str "*" content "*")
        :i (str "*" content "*")
        :b (str "**" content "**")
        :a (format "[%s](%s)" content (:href attrs))
        :li (str "* " content "\n")
        :ul (str "\n" content)
        :ol (str "\n" content)
        :hr "\n---\n"
        :blockquote (str "\n> " (str/replace content #"\n" "\n> ") "\n")
        :figure (process-image-node node)
        (str content " ")))))

(defn html-to-markdown
  "Converts a sequence of Enlive nodes to a Markdown string."
  [nodes]
  (-> (->> nodes               ; Use ->> for map and apply
           (map process-node)
           (apply str))
      ;; **FIXED HERE**: Use -> for string functions
      (str/replace #"(?m)^(\s*\n){2,}" "\n") ; Collapse multiple blank lines
      (str/trim)))

;; --- EXTRACTION LOGIC ---

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

(defn extract-body-markdown
  "Selects the main article body and converts it to Markdown."
  [html-resource]
  (let [body-selector [:div.body.markup]
        nodes-to-remove [[:div.subscribe-widget]
                         [:div.digestPostEmbed-flwiST]
                         [:p (html/attr? :data-component-name)]]

        body-nodes (-> (html/select html-resource body-selector)
                       first
                       :content)

        cleaned-nodes (reduce
                        (fn [nodes-so-far selector]
                          (html/transform nodes-so-far selector (constantly nil)))
                        body-nodes
                        nodes-to-remove)]
    (html-to-markdown cleaned-nodes)))

;; --- MAIN ---

(defn -main
  "Reads a local HTML file, extracts content, and prints it as Markdown."
  [& args]
  (try
    (let [html-string (slurp test-html-file)
          html-resource (html/html-resource (StringReader. html-string))
          metadata (extract-metadata html-resource)
          body-markdown (extract-body-markdown html-resource)]

      (println "\n--- EXTRACTED METADATA ---")
      (pp/pprint metadata)
      (println "--------------------------")

      (println "\n--- ARTICLE MARKDOWN ---")
      (println body-markdown)
      (println "------------------------"))
    (catch java.io.FileNotFoundException e
      (println "\nERROR: File not found ->" (.getMessage e))
      (println "Please ensure 'test/page-1.html' exists."))))
