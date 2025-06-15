;; File: src/tract/stages/job.clj
(ns tract.stages.job
  (:require [tract.pipeline :as pipeline]
            [tract.io :as tio]
            [tract.config :as config]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.data.xml :as xml]
            ;; Require the db namespace for reading completion data
            [tract.db :as db])
  (:import [java.time LocalDate]
           [java.time.format DateTimeFormatter]))

(def ^:private stage-name :job)
(def ^:private next-stage-name :fetch)

;; The old `completed-log-file` def and `read-completed-urls` function are removed.

(def ^:private date-parsers
  {:atom-date-parser (fn [entry]
                       (->> entry :content (filter #(#{:updated :published} (:tag %))) first :content first))
   :rss-date-parser  (fn [item]
                       (let [date-str (->> item :content (filter #(= :pubDate (:tag %))) first :content first)
                             formatter DateTimeFormatter/RFC_1123_DATE_TIME
                             temporal-accessor (.parse formatter date-str)]
                         (.format (LocalDate/from temporal-accessor) DateTimeFormatter/ISO_LOCAL_DATE)))})

(defn- extract-post-id-from-feed-entry
  [xml-entry]
  (let [id-tag-str (or (->> xml-entry :content (filter #(= :id (:tag %))) first :content first)
                       (->> xml-entry :content (filter #(= :guid (:tag %))) first :content first))]
    (when id-tag-str
      (or (second (re-find #"post/(\d+)$" id-tag-str))
          (second (re-find #"substack:post:(\d+)$" id-tag-str))))))

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
                   {:link         (->> % :content (filter tag-link?) first :attrs :href)
                    :published-date (subs date 0 10)
                    :post-id      (extract-post-id-from-feed-entry %)}))
           (filter #(is-in-date-range? (:published-date %) date-range)))

      (= :rss root-tag)
      (->> (xml-seq parsed-xml)
           (filter #(= :item (:tag %)))
           (map #(let [date ((:rss-date-parser date-parsers) %)]
                   {:link         (->> % :content (filter tag-link?) first :content first)
                    :published-date date
                    :post-id      (extract-post-id-from-feed-entry %)}))
           (filter #(is-in-date-range? (:published-date %) date-range)))

      :else (throw (ex-info (str "Unknown feed type for root tag: " root-tag) {:url feed-url})))))

(defn- process-job-spec!
  [job-spec]
  (cond
    (:urls job-spec)
    (do (println "\t-> Found 'urls' job type.")
        (map #(hash-map :link % :post-id nil) (:urls job-spec)))

    (:author job-spec)
    (let [author (:author job-spec)
          domain (if (str/includes? author ".") author (str author ".substack.com"))
          feed-url (str "https://" domain "/feed")
          date-range (:date_range job-spec)]
      (println (str "\t-> Found 'author' job type. Fetching feed for " author))
      (process-feed-url! feed-url date-range))
    ;; other cases like :atom, :rss...
    :else (throw (ex-info "Unknown job type in job-spec" {:job-spec job-spec}))))

(defn- process-job-file!
  "Processes a single .yaml job file."
  [job-file]
  (println (str "-> Processing job file: " (.getName job-file)))
  (try
    (let [job-spec (yaml/parse-string (slurp job-file))
          candidate-articles (process-job-spec! job-spec)
          ;; --- MODIFIED LOGIC: Use new DB for filtering ---
          completed-ids (db/read-completed-post-ids)
          new-articles (->> candidate-articles
                            (remove #(-> % :post-id (contains? completed-ids))))
          ;; --- END MODIFIED LOGIC ---
          new-urls (map :link new-articles)
          url-list-string (str/join "\n" new-urls)
          output-filename (-> (.getName job-file)
                              (str/replace #"\.ya?ml$" ".txt"))]
      (println (str "\t-> Found " (count candidate-articles) " candidate articles. "
                    (- (count candidate-articles) (count new-articles)) " already completed based on Post ID."))
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
