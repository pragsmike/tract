(ns tract.discovery
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [net.cgrand.enlive-html :as html]
            [clj-yaml.core :as yaml]
            [clojure.set :as set]
            [tract.config :as config]
            [tract.util :as util])
  (:import [java.io StringReader]
           [java.net URL MalformedURLException]))

(def parser-done-dir (config/stage-dir-path :parser "done"))
(def fetch-pending-dir (config/stage-dir-path :fetch "pending"))
(def fetch-done-dir (config/stage-dir-path :fetch "done"))
(def completed-log-file (str (io/file (config/work-dir) "completed.log")))
(def external-links-db-file (str (io/file (config/work-dir) "external-links.csv")))
(def ignored-domains-file "ignored-domains.txt")

;; vvvv CORRECTED HELPER FUNCTION vvvv
(defn- extract-domain
  "Safely extracts the host/domain from a URL string."
  [url-str]
  (try
    ;; The threading macro doesn't work with `new`. This is the direct, correct way.
    (.getHost (new URL (str/trim url-str)))
    (catch Exception _ nil)))
;; ^^^^ CORRECTED HELPER FUNCTION ^^^^

(defn- build-known-domains-db
  "Reads completed.log and returns a set of all domains we have successfully processed."
  []
  (let [file (io/file completed-log-file)]
    (if (.exists file)
      (->> (slurp file)
           str/split-lines
           (map extract-domain)
           (remove nil?)
           (into #{}))
      #{})))

(defn- read-ignore-list
  "Reads the ignored-domains.txt file into a set of hostnames."
  []
  (let [file (io/file ignored-domains-file)]
    (if (.exists file)
      (->> (slurp file)
           str/split-lines
           (remove #(or (str/blank? %) (str/starts-with? % "#")))
           (map str/trim)
           set)
      #{})))

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
  (let [completed-urls (let [file (io/file completed-log-file)]
                         (if (.exists file)
                           (->> (slurp file) str/split-lines (remove str/blank?))
                           []))
        fetch-pending-txt-files (get-files-from-dir fetch-pending-dir ".txt")
        fetch-done-txt-files (get-files-from-dir fetch-done-dir ".txt")
        in-flight-urls (mapcat #(str/split-lines (slurp %))
                               (concat fetch-pending-txt-files fetch-done-txt-files))]

    (->> (concat completed-urls in-flight-urls)
         (map util/canonicalize-url) ; Canonicalize all known URLs
         (remove nil?)
         (into #{}))))

(defn- is-ignored?
  "Checks if a given URL's host is on the ignore list."
  [href-str ignore-set]
  (try
    (let [host (.getHost (new URL href-str))
          substack-subdomain (first (str/split host #"\."))]
      (or (contains? ignore-set host)
          (contains? ignore-set substack-subdomain)))
    (catch MalformedURLException _ true)))

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
  (let [expand-mode? (some #{"--expand"} args)
        ignore-list (read-ignore-list)
        known-urls (build-known-urls-db)
        known-domains (if-not expand-mode? (build-known-domains-db) #{})]

    (if expand-mode?
      (println "-> Running in EXPAND mode: will discover articles from new domains.")
      (println "-> Running in default 'known domains only' mode. Use --expand to discover from new domains."))
    (println (str "-> Loaded " (count ignore-list) " domains to ignore."))
    (println (str "-> Assembled " (count known-urls) " known URLs from logs and in-flight jobs."))
    (when-not expand-mode?
      (println (str "-> Restricting discovery to " (count known-domains) " known domains.")))

    (println "\n-> Scanning processed HTML files for new links...")
    (let [html-files (->> (io/file parser-done-dir) file-seq (filter #(.isFile %)))
          unfiltered-links (for [html-file html-files
                                 :let [source-key (-> (.getName html-file) (str/replace #"\.html$" ""))]
                                 link (extract-links-from-html html-file)]
                             (assoc link :source_key source-key))
          all-links (remove #(is-ignored? (:href %) ignore-list) unfiltered-links)
          classified-links (group-by classify-link all-links)
          potential-articles (get classified-links :substack_article [])
          approved-articles (cond->> potential-articles
                              (not expand-mode?) (filter #(contains? known-domains (extract-domain (:href %)))))
          discovered-articles (->> approved-articles
                                   (map :href)
                                   (map util/canonicalize-url)
                                   set)
          external-links (:external classified-links)
          new-articles (set/difference discovered-articles known-urls)]

      (println (str "-> Found " (count potential-articles) " potential articles, "
                    (count approved-articles) " approved for consideration."))
      (write-external-links-csv! external-links)
      (write-job-file! new-articles)))
    (println "\n--- Discovery Tool Finished ---"))
