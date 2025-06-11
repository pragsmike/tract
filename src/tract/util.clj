(ns tract.util
  (:require [clojure.string :as str])
  (:import [java.net URL URLDecoder]
           [java.io File]))

(defn generate-article-key
  "Generates a unique file-safe key from article metadata."
  [{:keys [publication_date title]}]
  (let [date-part (or publication_date (.toString (java.time.LocalDate/now)))
        full-slug (-> (str/lower-case (or title "untitled"))
                      (str/replace #"[^a-z0-9\s-]" "")
                      (str/replace #"\s+" "-"))
        slug (subs full-slug 0 (min (count full-slug) 50))]
    (str date-part "_" slug)))

(defn url->local-path
  "Converts a potentially complex image URL into a clean local file path string."
  [image-url-str]
  {:pre [(not (str/blank? image-url-str))]} ;; **FIXED**: Add pre-condition for safety
  (let [cdn-prefix "/https%3A"
        clean-url-str (if (str/includes? image-url-str cdn-prefix)
                        (let [start-index (str/last-index-of image-url-str cdn-prefix)]
                          (URLDecoder/decode (subs image-url-str (inc start-index)) "UTF-8"))
                        image-url-str)
        url (new URL clean-url-str)
        host (.getHost url)
        path (subs (.getPath url) 1)]
    (str host File/separator path)))

(defn url->filename
  "Creates a safe filename from a URL's path, suitable for fetched HTML."
  [url-str]
  {:pre [(not (str/blank? url-str))]} ;; **FIXED**: Add pre-condition for safety
  (let [path (-> (new URL url-str) (.getPath))]
    (-> path
        (str/replace #"^/p/" "")
        (str/replace #"[^a-zA-Z0-9-]" "_")
        (str ".html"))))
