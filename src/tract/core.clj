(ns tract.core
  (:require [net.cgrand.enlive-html :as html]
            [cheshire.core :as json]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clj-http.lite.client :as client])
  (:import [java.io StringReader File]
           [java.net URL]))

(defonce test-html-file "test/page-1.html")

;; --- FORWARD DECLARATION ---
(declare html-to-markdown)

;; --- URL & PATH HELPERS ---

(defn- url->local-path [image-url-str]
  "Converts a clean image URL into a local file path."
  (let [url (new URL image-url-str)
        host (.getHost url)
        ;; Remove the leading '/' from the path to make it relative
        path (subs (.getPath url) 1)]
    (io/file host path)))

;; --- NODE PROCESSING LOGIC ---

(defn process-image-node [node image-jobs-atom article-key]
  "Processes an image, adds its metadata to the jobs atom, and returns markdown."
  (let [img-node (-> (html/select node [:img]) first)
        caption-node (-> (html/select node [:figcaption]) first)
        ;; **FIXED**: Prioritize the real src from data-attrs
        data-attrs-json (get-in img-node [:attrs :data-attrs])
        img-src (if data-attrs-json
                  (:src (json/parse-string data-attrs-json true))
                  (get-in img-node [:attrs :src])) ; Fallback
        alt-text (get-in img-node [:attrs :alt] "")
        title-text (get-in img-node [:attrs :title] "")
        caption (if caption-node (html/text caption-node) "")]
    (if-not img-src
      "" ; Don't process nodes without an image source
      (let [local-path (url->local-path img-src)
            image-job {:article_key article-key
                       :image_source_url img-src
                       :image_path (str local-path)
                       :alt alt-text
                       :title title-text
                       :caption caption}]
        (swap! image-jobs-atom conj image-job)
        (str "\n" (format "![%s](%s \"%s\")" (or (not-empty alt-text) caption) (str local-path) title-text) "\n"
             (when (not-empty caption) (str "*" caption "*\n")))))))

(defn process-node [node image-jobs-atom article-key]
  (if (string? node)
    (str/trim node)
    (let [tag (:tag node)
          attrs (:attrs node)
          content (html-to-markdown (:content node) image-jobs-atom article-key)]
      (case tag
        :p (str "\n" content "\n")
        :h1 (str "\n# " content "\n") :h2 (str "\n## " content "\n") :h3 (str "\n### " content "\n")
        :strong (str "**" content "**") :b (str "**" content "**")
        :em (str "*" content "*") :i (str "*" content "*")
        :a (format "[%s](%s)" content (:href attrs))
        :li (str "* " content "\n")
        :ul (str "\n" content) :ol (str "\n" content)
        :hr "\n---\n"
        :blockquote (str "\n> " (str/replace content #"\n" "\n> ") "\n")
        :figure (process-image-node node image-jobs-atom article-key)
        (str content " ")))))

(defn html-to-markdown [nodes image-jobs-atom article-key]
  (->> nodes
       (map #(process-node % image-jobs-atom article-key))
       (apply str)))

;; --- EXTRACTION & FILE I/O LOGIC ---

(defn generate-article-key [{:keys [publication_date title]}]
  (let [full-slug (-> (str/lower-case title)
                      (str/replace #"[^a-z0-9\s-]" "")
                      (str/replace #"\s+" "-"))
        slug (subs full-slug 0 (min (count full-slug) 50))]
    (str publication_date "_" slug)))

(defn format-toml-front-matter [metadata]
  (str "---\n"
       (format "title = \"%s\"\n" (:title metadata))
       (format "author = \"%s\"\n" (:author metadata))
       (format "article_key = \"%s\"\n" (:article_key metadata))
       (format "publication_date = \"%s\"\n" (:publication_date metadata))
       (format "source_url = \"%s\"\n" (:source_url metadata))
       "---\n\n"))

(defn extract-article-data [html-resource]
  (let [metadata-base (let [json-ld-selector [[:script (html/attr= :type "application/ld+json")]]
                            json-str (-> (html/select html-resource json-ld-selector) first html/text)
                            parsed-json (json/parse-string json-str true)]
                        {:title (:headline parsed-json)
                         :author (get-in parsed-json [:author 0 :name])
                         :publication_date (some-> (:datePublished parsed-json) (subs 0 10))
                         :source_url (:url parsed-json)})
        article-key (generate-article-key metadata-base)
        metadata (assoc metadata-base :article_key article-key)
        image-jobs (atom [])
        body-nodes (-> (html/select html-resource [:div.body.markup]) first :content)
        nodes-to-remove [[:div.subscribe-widget]
                         [:div.digestPostEmbed-flwiST]
                         [:p (html/attr? :data-component-name)]]
        cleaned-nodes (reduce (fn [nodes sel] (html/transform nodes sel (constantly nil)))
                              body-nodes nodes-to-remove)
        body-markdown (-> (html-to-markdown cleaned-nodes image-jobs article-key)
                          (str/replace #"(?m)^\s*$\n" "")
                          str/trim)]
    {:metadata metadata
     :markdown body-markdown
     :images @image-jobs}))

;; --- SIDE-EFFECTING FUNCTIONS ---

(defn process-image-job [job]
  (let [local-img-file (io/file (:image_path job))
        img-dir (.getParentFile local-img-file)]
    (println (str "\t-> Downloading " (:image_source_url job) " to " local-img-file))
    (.mkdirs img-dir)
    (with-open [out-stream (io/output-stream local-img-file)]
      (let [response (client/get (:image_source_url job) {:as :stream})]
        (io/copy (:body response) out-stream)))
    (let [json-filename (str (:article_key job) "_" (hash job) ".json")
          json-file (io/file img-dir json-filename)
          json-data (-> job
                        (dissoc :metadata) ; Clean up the job map before writing
                        (json/generate-string {:pretty true}))]
      (println (str "\t-> Writing metadata to " json-file))
      (spit json-file json-data))))

;; --- MAIN ---

(defn -main [& args]
  (println "-> Reading and parsing local file:" test-html-file)
  (try
    (let [html-string (slurp test-html-file)
          html-resource (html/html-resource (StringReader. html-string))
          {:keys [metadata markdown images]} (extract-article-data html-resource)
          front-matter (format-toml-front-matter metadata)
          final-content (str front-matter markdown)
          output-filename (str (:article_key metadata) ".md")]
      (println "-> Writing article to:" output-filename)
      (spit output-filename final-content)
      (println "-> Processing" (count images) "images...")
      (doseq [job images]
        (process-image-job job)) ; Pass the simpler job map
      (println "-> Done."))
    (catch Exception e
      (println "\nAn error occurred:")
      (.printStackTrace e))))
