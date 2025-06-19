(ns tract.util
  (:require [clojure.string :as str])
  (:import [java.net URL URLDecoder]
           [java.io File]))

(defn extract-slug-from-url
  "Extracts the final path segment (the slug) from a URL string."
  [url-str]
  (when-let [path (try (.getPath (new URL url-str)) (catch Exception _ nil))]
    (-> (str/split path #"/")
        last)))

(defn get-slug-from-meta-filename
  "Extracts the slug from a metadata file's File object or filename string."
  [meta-file-or-name]
  (let [file-name (if (instance? File meta-file-or-name)
                    (.getName ^File meta-file-or-name)
                    (str meta-file-or-name))]
    (str/replace file-name #"\.html\.meta\.json$" "")))

(defn canonicalize-url
  "Removes query parameters and fragments from a URL string."
  [url-str]
  (when url-str
    (-> url-str
        (str/split #"#" 2)
        first
        (str/split #"\?" 2)
        first
        )))

;; REMOVED: The `current-date-string` and `generate-article-key` functions
;; are no longer needed as the markdown filename is now based on the slug.

(defn url->local-path
  "Converts a potentially complex image URL into a clean local file path string."
  [image-url-str]
  {:pre [(not (str/blank? image-url-str))]}
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
  {:pre [(not (str/blank? url-str))]}
  (let [path (-> (new URL url-str) (.getPath))]
    (-> path
        (str/replace #"^/p/" "")
        (str/replace #"[^a-zA-Z0-9-]" "_")
        (str ".html"))))

(defn extract-domain [url-str]
  (try (.getHost (new URL (str/trim url-str))) (catch Exception _ nil)))
