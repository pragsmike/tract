(ns tract.compiler
  (:require [tract.util :as util]
            [clojure.string :as str]
            [net.cgrand.enlive-html :as html]
            [clj-yaml.core :as yaml]))

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
            ;; CORRECTED to kebab-case keys
            image-job {:article-key      article-key
                       :image-source-url img-src
                       :image-path       local-path
                       :alt              alt-text
                       :title            title-text
                       :caption          caption}]
        {:markdown (str "\n" (format "![%s](%s \"%s\")" (or (not-empty alt-text) caption) local-path title-text) "\n"
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

(defn- format-yaml-front-matter
  "Takes a metadata map and returns a YAML front matter string."
  [metadata]
  (let [;; CORRECTED: Select and order kebab-case keys for consistent output.
        front-matter-data (select-keys metadata [:title
                                                 :author
                                                 :publication-date
                                                 :source-url
                                                 :canonical-url
                                                 :fetch-timestamp
                                                 :post-id
                                                 :article-key])
        yaml-string (yaml/generate-string front-matter-data
                                          :dumper-options {:flow-style :block})]
    (str "---\n"
         yaml-string
         "---\n\n")))

(defn compile-to-article
  "Takes parsed data and returns a map of final article data and image jobs."
  [metadata body-nodes]
  (let [article-key (:article-key metadata)
        {:keys [markdown images]} (process-node {:tag :div :content body-nodes} article-key)
        final-markdown (-> markdown
                           (str/replace #"(?m)^\s*$\n" "")
                           str/trim)
        front-matter (format-yaml-front-matter metadata)]
    {:markdown (str front-matter final-markdown)
     :images images}))
