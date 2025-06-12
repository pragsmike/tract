(ns tract.compiler
  (:require [tract.util :as util]
            [clojure.string :as str]
            [net.cgrand.enlive-html :as html]))

;; ... (process-node and process-image-node are unchanged) ...
(declare process-node)
(defn- process-image-node [node article-key]
  (let [img-node (-> (html/select node [:img]) first)
        caption-node (-> (html/select node [:figcaption]) first)
        img-src (get-in img-node [:attrs :src])
        alt-text (get-in img-node [:attrs :alt] "")
        title-text (get-in img-node [:attrs :title] "")
        caption (if caption-node (html/text caption-node) "")]
    (if-not img-src
      {:markdown "" :images []}
      (let [local-path (util/url->local-path img-src)
            image-job {:article_key article-key
                       :image_source_url img-src
                       :image_path (str local-path)
                       :alt alt-text
                       :title title-text
                       :caption caption}]
        {:markdown (str "\n" (format "![%s](%s \"%s\")" (or (not-empty alt-text) caption) (str local-path) title-text) "\n"
                        (when (not-empty caption) (str "*" caption "*\n")))
         :images [image-job]}))))
(defn- process-node [node article-key]
  (if (string? node)
    {:markdown node, :images []}
    (let [child-results (map #(process-node % article-key) (:content node))
          content-md (->> child-results (map :markdown) (str/join))
          child-images (->> child-results (map :images) (apply concat))
          tag (:tag node)
          attrs (:attrs node)]
      (case tag
        :figure (let [figure-result (process-image-node node article-key)]
                  (update figure-result :images #(doall (concat % child-images))))
        :p {:markdown (str "\n" content-md "\n"), :images child-images}
        :h1 {:markdown (str "\n# " content-md "\n"), :images child-images}
        :h2 {:markdown (str "\n## " content-md "\n"), :images child-images}
        :h3 {:markdown (str "\n### " content-md "\n"), :images child-images}
        :strong {:markdown (str "**" content-md "**"), :images child-images}
        :b {:markdown (str "**" content-md "**"), :images child-images}
        :em {:markdown (str "*" content-md "*"), :images child-images}
        :i {:markdown (str "*" content-md "*"), :images child-images}
        :a {:markdown (format "[%s](%s)" content-md (:href attrs)), :images child-images}
        :li {:markdown (str "* " content-md "\n"), :images child-images}
        :ul {:markdown (str "\n" content-md), :images child-images}
        :ol {:markdown (str "\n" content-md), :images child-images}
        :hr {:markdown "\n---\n", :images child-images}
        :blockquote {:markdown (str "\n> " (str/replace content-md #"\n" "\n> ") "\n"), :images child-images}
        {:markdown content-md, :images child-images}))))


;; --- NEW HELPER and UPDATED FORMATTER ---

(defn- escape-toml-string [s]
  "Escapes double quotes for TOML string values."
  (when s
    (str/replace s "\"" "\\\"")))

(defn- format-toml-front-matter [metadata]
  (let [title (escape-toml-string (or (:title metadata) "Untitled"))
        author (escape-toml-string (:author metadata))
        source-url (escape-toml-string (or (:source_url metadata) "unknown"))]
    (str "---\n"
         (format "title = \"%s\"\n" title)
         (format "author = \"%s\"\n" author)
         (format "article_key = \"%s\"\n" (:article_key metadata))
         (format "publication_date = \"%s\"\n" (or (:publication_date metadata) "unknown"))
         (format "source_url = \"%s\"\n" source-url)
         "---\n\n")))

(defn compile-to-article
  "Takes parsed data and returns a map of final article data and image jobs."
  [{:keys [metadata body-nodes]}]
  (let [article-key (cond
                      (not (str/blank? (:title metadata)))
                      (util/generate-article-key metadata)

                      (not (str/blank? (:source_url metadata)))
                      (-> (:source_url metadata) util/url->filename (str/replace #"\.html$" ""))

                      :else
                      (str "unknown-article_" (System/currentTimeMillis)))

        full-metadata (assoc metadata :article_key article-key)
        {:keys [markdown images]} (process-node {:tag :div :content body-nodes} article-key)
        final-markdown (-> markdown
                           (str/replace #"(?m)^\s*$\n" "")
                           str/trim)
        front-matter (format-toml-front-matter full-metadata)]
    {:article {:metadata full-metadata
               :markdown (str front-matter final-markdown)}
     :images images}))
