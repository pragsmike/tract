(ns tract.compiler
  (:require [tract.util :as util]
            [clojure.string :as str]
            [net.cgrand.enlive-html :as html]))

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

(defn- format-toml-front-matter [metadata]
  (str "---\n"
       (format "title = \"%s\"\n" (:title metadata))
       (format "author = \"%s\"\n" (:author metadata))
       (format "article_key = \"%s\"\n" (:article_key metadata))
       (format "publication_date = \"%s\"\n" (or (:publication_date metadata) "unknown"))
       (format "source_url = \"%s\"\n" (:source_url metadata))
       "---\n\n"))

(defn compile-to-article
  "Takes parsed data and returns a map of final article data and image jobs."
  [{:keys [metadata body-nodes]}]
  (let [;; **FIXED**: The fallback logic is now handled here, at the correct level.
        article-key (if (str/blank? (:title metadata))
                      (-> (:source_url metadata)
                          util/url->filename
                          (str/replace #"\.html$" ""))
                      (util/generate-article-key metadata))
        full-metadata (assoc metadata :article_key article-key)
        {:keys [markdown images]} (process-node {:tag :div :content body-nodes} article-key)
        final-markdown (-> markdown
                           (str/replace #"(?m)^\s*$\n" "")
                           str/trim)
        front-matter (format-toml-front-matter full-metadata)]
    {:article {:metadata full-metadata
               :markdown (str front-matter final-markdown)}
     :images images}))
