(ns tract.util
  (:require [clojure.string :as str])
  (:import [java.net URL URLDecoder]
           [java.io File]
           [java.time LocalDate]))

(defn canonicalize-url
  "Removes query parameters and fragments from a URL string."
  [url-str]
  (when url-str
    (-> url-str
        (str/split #"#" 2)
        first
        (str/split #"\?" 2)
        first)))

(defn- current-date-string
  "Returns the current date as an ISO_LOCAL_DATE string.
  Exists as a separate function to be mockable in tests."
  []
  (.toString (LocalDate/now)))

(defn generate-article-key
  "Generates a unique file-safe key from article metadata."
  [{:keys [publication_date title]}]
  ;; vvvv MODIFIED TO USE THE NEW HELPER vvvv
  (let [date-part (or publication_date (current-date-string))
        full-slug (-> (str/lower-case (or title "untitled"))
                      (str/replace #"[^a-z0-9\s-]" "")
                      (str/replace #"\s+" "-"))
        slug (subs full-slug 0 (min (count full-slug) 50))]
    (str date-part "_" slug)))
  ;; ^^^^ MODIFIED ^^^^

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
