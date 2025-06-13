(ns tract.discovery
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [toml-clj.core :as toml]
            [net.cgrand.enlive-html :as html]
            [clj-yaml.core :as yaml]
            [clojure.set :as set]
            [tract.util :as util])
  (:import [java.io StringReader File]
           [java.net URL MalformedURLException]))

(def processed-dir "work/3-processed")
(def parser-done-dir "work/parser/done")
(def fetch-pending-dir "work/fetch/pending")
(def fetch-done-dir "work/fetch/done")
(def known-urls-db-file "work/known-urls.txt")
(def external-links-db-file "work/external-links.csv")
(def ignore-list-file "ignore-list.txt")

(defn- read-ignore-list
  "Reads the ignore-list.txt file into a set of hostnames."
  []
  (let [file (io/file ignore-list-file)]
    (if (.exists file)
      (->> (slurp file)
           str/split-lines
           (remove #(or (str/blank? %) (str/starts-with? % "#")))
           (map str/trim)
           set)
      #{})))

(defn- is-ignored?
  "Checks if a given URL's host is on the ignore list."
  [href-str ignore-set]
  (try
    (let [host (.getHost (new URL href-str))
          substack-subdomain (first (str/split host #"\."))]
      (or (contains? ignore-set host)
          (contains? ignore-set substack-subdomain)))
    (catch MalformedURLException _ true)))

(defn- extract-url-from-md-file [file]
  (try
    (let [content (slurp file)
          front-matter-re #"(?ms)^---\n(.*?)\n---"
          front-matter-str (some-> (re-find front-matter-re content) second)]
      (when front-matter-str
        ;; **FIXED**: Use the correct `read` function with a StringReader.
        (-> (StringReader. front-matter-str)
            (toml/read {:key-fn keyword})
            :source_url)))
    (catch Exception e
      (println (str "WARN: Could not parse TOML from " (.getName file) ": " (.getMessage e)))
      nil)))

(defn- get-files-from-dir [dir-path extension]
  (let [dir (io/file dir-path)]
    (if (.exists dir)
      (->> (.listFiles dir)
           (filter #(and (.isFile %) (str/ends-with? (.getName %) extension))))
      [])))

(defn- build-known-urls-db
  "Scans all relevant directories to build a set of all known URLs."
  []
  (println "-> Building database of known URLs...")
  (let [processed-md-files (get-files-from-dir processed-dir ".md")
        fetch-pending-txt-files (get-files-from-dir fetch-pending-dir ".txt")
        fetch-done-txt-files (get-files-from-dir fetch-done-dir ".txt")

        processed-urls (doall (map extract-url-from-md-file processed-md-files))
        fetch-job-urls (doall (mapcat #(str/split-lines (slurp %))
                                      (concat fetch-pending-txt-files fetch-done-txt-files)))]

    (->> (concat processed-urls fetch-job-urls)
         (map util/canonicalize-url) ; Canonicalize all known URLs
         (remove nil?)
         (into #{}))))

(defn- write-known-urls-db! [known-urls]
  (let [db-file (io/file known-urls-db-file)]
    (println (str "-> Writing " (count known-urls) " unique URLs to " db-file))
    (spit db-file (str/join "\n" (sort known-urls)))))

(defn- extract-links-from-html [html-file]
  (let [html-string (slurp html-file)
        html-resource (html/html-resource (StringReader. html-string))]
    (->> (html/select html-resource [:a])
         (map #(let [href (get-in % [:attrs :href])]
                 (when href {:href href, :text (str/trim (html/text %))})))
         (remove nil?))))

(defn- classify-link
  [{:keys [href]}]
  (try
    (let [url (new URL (str/trim href))
          host (.getHost url)
          path (.getPath url)]
      (cond
        (re-find #"/subscribe|/comments|/comment/|/share|/like|/restack" path) :noise
        (re-find #"cdn\.substack\.com" host) :noise
        (re-find #"^\/p\/" path) :substack_article
        (re-find #"\.substack\.com$" host) :noise
        (re-find #"^https?$" (.getProtocol url)) :external
        :else :noise))
    (catch MalformedURLException _ :noise)))

(defn- write-external-links-csv! [external-links-data]
  (let [csv-file (io/file external-links-db-file)
        header "source_article_key,link_text,external_url\n"]
    (println (str "-> Writing " (count external-links-data) " external link references to " csv-file))
    (spit csv-file header)
    (with-open [writer (io/writer csv-file :append true)]
      (doseq [{:keys [source_key text href]} external-links-data]
        (let [escaped-text (if (str/includes? text ",") (str "\"" text "\"") text)]
          (.write writer (str/join "," [source_key escaped-text href]))
          (.write writer "\n"))))))

(defn- write-job-file! [new-urls]
  (if (seq new-urls)
    (let [timestamp (.format (java.text.SimpleDateFormat. "yyyyMMdd'T'HHmmss") (new java.util.Date))
          job-filename (str "discovery-job-" timestamp ".yaml")
          job-content {"urls" (vec (sort new-urls))}
          job-file-path (io/file "work/job/pending" job-filename)]
      (println (str "-> Found " (count new-urls) " new articles to fetch. Writing job to " job-file-path))
      (spit job-file-path (yaml/generate-string job-content)))
    (println "-> No new undiscovered articles found.")))

(defn -main
  "Main entry point for the discovery tool."
  [& args]
  (println "--- Running Discovery Tool ---")
  (let [ignore-list (read-ignore-list)
        known-urls (build-known-urls-db)]
    (println (str "-> Loaded " (count ignore-list) " domains to ignore."))
    (write-known-urls-db! known-urls)
    (println "\n-> Scanning processed HTML files for new links...")
    (let [html-files (->> (io/file parser-done-dir) file-seq (filter #(.isFile %)))
          unfiltered-links (for [html-file html-files
                                 :let [source-key (-> (.getName html-file) (str/replace #"\.html$" ""))]
                                 link (extract-links-from-html html-file)]
                             (assoc link :source_key source-key))
          all-links (remove #(is-ignored? (:href %) ignore-list) unfiltered-links)
          classified-links (group-by classify-link all-links)]
      (let [discovered-articles (->> (:substack_article classified-links)
                                     (map :href)
                                     (map util/canonicalize-url)
                                     set)
            external-links (:external classified-links)
            new-articles (set/difference discovered-articles known-urls)]
        (write-external-links-csv! external-links)
        (write-job-file! new-articles))))
  (println "\n--- Discovery Tool Finished ---"))
