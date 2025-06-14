(ns tract.stages.job
  (:require [tract.pipeline :as pipeline]
            [tract.io :as tio]
            [clojure.java.io :as io]
            [tract.config :as config]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.data.xml :as xml]
            [clojure.set :as set])
  (:import [java.time LocalDate]
           [java.time.format DateTimeFormatter]))

(def ^:private stage-name :job)
(def ^:private next-stage-name :fetch)
(def ^:private completed-log-file (str (io/file (config/work-dir) "completed.log")))

(def ^:private date-parsers
  {:atom-date-parser (fn [entry]
                       (->> entry :content (filter #(#{:updated :published} (:tag %))) first :content first))
   :rss-date-parser  (fn [item]
                       (let [date-str (->> item :content (filter #(= :pubDate (:tag %))) first :content first)
                             formatter DateTimeFormatter/RFC_1123_DATE_TIME
                             temporal-accessor (.parse formatter date-str)]
                         (.format (LocalDate/from temporal-accessor) DateTimeFormatter/ISO_LOCAL_DATE)))})

(defn- read-completed-urls
  "Reads the completed.log into a set. Handles file-not-found."
  []
  (let [file (io/file completed-log-file)]
    (if (.exists file)
      (->> (slurp file)
           (str/split-lines)
           (remove str/blank?)
           (into #{}))
      #{})))

(defn- is-in-date-range? [date-str {:keys [start end]}]
  (and (or (not start) (>= 0 (compare date-str start)))
       (or (not end)   (<= 0 (compare date-str end)))))

(defn- process-feed-url!
  [feed-url date-range]
  (let [xml-string (tio/throttled-fetch! feed-url)
        parsed-xml (xml/parse-str xml-string)
        root-tag (:tag parsed-xml)
        tag-link? #(= :link (:tag %))]
    (cond
      (= :feed root-tag)
      (->> (xml-seq parsed-xml)
           (filter #(= :entry (:tag %)))
           (map #(let [date ((:atom-date-parser date-parsers) %)]
                   {:link (->> % :content (filter tag-link?) first :attrs :href)
                    :published-date (subs date 0 10)}))
           (filter #(is-in-date-range? (:published-date %) date-range))
           (map :link))

      (= :rss root-tag)
      (->> (xml-seq parsed-xml)
           (filter #(= :item (:tag %)))
           (map #(let [date ((:rss-date-parser date-parsers) %)]
                   {:link (->> % :content (filter tag-link?) first :content first)
                    :published-date date}))
           (filter #(is-in-date-range? (:published-date %) date-range))
           (map :link))

      :else (throw (ex-info (str "Unknown feed type for root tag: " root-tag) {:url feed-url})))))

(defn- process-job-spec!
  [job-spec]
  (cond
    (:urls job-spec)
    (do (println "\t-> Found 'urls' job type.")
        (:urls job-spec))

    (:author job-spec)
    (let [author (:author job-spec)
          domain (if (str/includes? author ".") author (str author ".substack.com"))
          feed-url (str "https://" domain "/feed")
          date-range (:date_range job-spec)]
      (println (str "\t-> Found 'author' job type. Fetching feed for " author))
      (process-feed-url! feed-url date-range))

    (:atom job-spec)
    (let [feed-url (:atom job-spec)
          date-range (:date_range job-spec)]
      (println (str "\t-> Found 'atom' job type. Fetching feed from " feed-url))
      (process-feed-url! feed-url date-range))

    (:rss job-spec)
    (let [feed-url (:rss job-spec)
          date-range (:date_range job-spec)]
      (println (str "\t-> Found 'rss' job type. Fetching feed from " feed-url))
      (process-feed-url! feed-url date-range))

    :else (throw (ex-info "Unknown job type in job-spec" {:job-spec job-spec}))))

(defn- process-job-file!
  "Processes a single .yaml job file."
  [job-file]
  (println (str "-> Processing job file: " (.getName job-file)))
  (try
    (let [job-spec (yaml/parse-string (slurp job-file))
          candidate-urls (process-job-spec! job-spec)
          completed-urls (read-completed-urls)
          new-urls (vec (set/difference (set candidate-urls) completed-urls))
          url-list-string (str/join "\n" new-urls)
          output-filename (-> (.getName job-file)
                              (str/replace #"\.ya?ml$" ".txt"))]
      (println (str "\t-> Found " (count candidate-urls) " candidate URLs. "
                    (count completed-urls) " already completed."))
      (if (empty? new-urls)
        (println "\t-> No new URLs to fetch.")
        (do
          (println (str "\t=> Writing " (count new-urls) " new URLs to "
                        (config/stage-dir-path next-stage-name "pending") "/" output-filename))
          (pipeline/write-to-next-stage! url-list-string next-stage-name output-filename)))
      (pipeline/move-to-done! job-file stage-name))
    (catch Exception e
      (pipeline/move-to-error! job-file stage-name e))))

(defn run-stage!
  "Main entry point for the job stage. Scans for and processes .yaml files."
  []
  (println "--- Running Job Stage ---")
  (let [pending-files (pipeline/get-pending-files stage-name)]
    (if (empty? pending-files)
      (println "No job files to process.")
      (do
        (println "Found" (count pending-files) "job file(s).")
        (doseq [file pending-files]
          (process-job-file! file)))))
  (println "--- Job Stage Complete ---"))
