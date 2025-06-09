(ns tract.core
  (:require [net.cgrand.enlive-html :as html]
            [cheshire.core :as json]
            [clojure.pprint :as pp]
            [clojure.string :as str])
  (:import [java.io StringReader File]))

(defonce test-html-file "test/page-1.html")

;; --- FORWARD DECLARATION ---
(declare html-to-markdown)

;; --- NODE PROCESSING LOGIC ---

(defn process-image-node [node]
  (let [img-node (-> (html/select node [:img]) first)
        caption-node (-> (html/select node [:figcaption]) first)
        img-src (get-in img-node [:attrs :src])
        alt-text (get-in img-node [:attrs :alt] "")
        caption (if caption-node (html/text caption-node) "")]
    (str "\n" (format "![%s](%s)" (or (not-empty alt-text) caption) img-src) "\n"
         (when (not-empty caption) (str "*" caption "*\n")))))

(defn process-node [node]
  (if (string? node)
    (str/trim node)
    (let [tag (:tag node)
          attrs (:attrs node)
          content (html-to-markdown (:content node))]
      (case tag
        :p (str "\n" content "\n")
        :h1 (str "\n# " content "\n")
        :h2 (str "\n## " content "\n")
        :h3 (str "\n### " content "\n")
        :strong (str "**" content "**")
        :em (str "*" content "*") :i (str "*" content "*") :b (str "**" content "**")
        :a (format "[%s](%s)" content (:href attrs))
        :li (str "* " content "\n")
        :ul (str "\n" content) :ol (str "\n" content)
        :hr "\n---\n"
        :blockquote (str "\n> " (str/replace content #"\n" "\n> ") "\n")
        :figure (process-image-node node)
        (str content " ")))))

(defn html-to-markdown [nodes]
  (-> (->> nodes (map process-node) (apply str))
      (str/replace #"(?m)^\s*$\n" "") ; Collapse multiple blank lines
      (str/trim)))

;; --- EXTRACTION & FILE I/O LOGIC ---

(defn generate-article-key [{:keys [publication_date title]}]
  "Generates a unique file-safe key from article metadata."
  (let [full-slug (-> (str/lower-case title)
                      (str/replace #"[^a-z0-9\s-]" "") ; remove unsafe chars
                      (str/replace #"\s+" "-"))        ; collapse whitespace
        ;; **FIXED HERE**: Truncate the generated slug, not the original title.
        slug (subs full-slug 0 (min (count full-slug) 50))]
    (str publication_date "_" slug)))

(defn format-toml-front-matter [metadata]
  "Formats the metadata map into a TOML string for the front matter."
  (str "---\n"
       (format "title = \"%s\"\n" (:title metadata))
       (format "author = \"%s\"\n" (:author metadata))
       (format "article_key = \"%s\"\n" (:article_key metadata))
       (format "publication_date = \"%s\"\n" (:publication_date metadata))
       (format "source_url = \"%s\"\n" (:source_url metadata))
       "---\n\n"))

(defn extract-metadata [html-resource]
  (let [json-ld-selector [[:script (html/attr= :type "application/ld+json")]]
        json-str (-> (html/select html-resource json-ld-selector) first html/text)
        parsed-json (json/parse-string json-str true)
        published-date-raw (:datePublished parsed-json)]
    {:title (:headline parsed-json)
     :author (get-in parsed-json [:author 0 :name])
     :publication_date (when published-date-raw (subs published-date-raw 0 10))
     :source_url (:url parsed-json)}))

(defn extract-body-markdown [html-resource]
  (let [body-selector [:div.body.markup]
        nodes-to-remove [[:div.subscribe-widget]
                         [:div.digestPostEmbed-flwiST]
                         [:p (html/attr? :data-component-name)]]
        body-nodes (-> (html/select html-resource body-selector) first :content)
        cleaned-nodes (reduce
                        (fn [nodes-so-far selector]
                          (html/transform nodes-so-far selector (constantly nil)))
                        body-nodes
                        nodes-to-remove)]
    (html-to-markdown cleaned-nodes)))

;; --- MAIN ---

(defn -main
  "Reads a local HTML file and writes a complete Markdown article file."
  [& args]
  (println "-> Reading and parsing local file:" test-html-file)
  (try
    (let [html-string (slurp test-html-file)
          html-resource (html/html-resource (StringReader. html-string))
          metadata-base (extract-metadata html-resource)
          article-key (generate-article-key metadata-base)
          metadata (assoc metadata-base :article_key article-key)
          body-markdown (extract-body-markdown html-resource)
          front-matter (format-toml-front-matter metadata)
          final-content (str front-matter body-markdown)
          output-filename (str article-key ".md")]

      (println "-> Writing extracted article to:" output-filename)
      (spit output-filename final-content)
      (println "-> Done."))

    (catch java.io.FileNotFoundException e
      (println "\nERROR: File not found ->" (.getMessage e))
      (println "Please ensure 'test/page-1.html' exists."))))
