;; File: src/tract/db.clj
(ns tract.db
  (:require [clojure.java.io :as io]
            [tract.config :as config]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def ^:private ignored-domains-file (config/ignored-domains-path))

(defn record-completion!
  "Records a completed article's identity in the new data model.
  - Appends the Post ID to completed-post-ids.log.
  - Appends URL-to-ID mappings to url-to-id.map for both
    the source and canonical URLs."
  [{:keys [post-id source-url canonical-url]}]
  (when post-id
    (try
      ;; 1. Append the post-id to the log of completed IDs
      (with-open [writer (io/writer (config/completed-post-ids-log-path) :append true)]
        (.write writer (str post-id "\n")))

      ;; 2. Append the URL-to-ID mappings to the lookup map
      (with-open [writer (io/writer (config/url-to-id-map-path) :append true)]
        ;; Write the source_url -> post_id mapping
        (when source-url
          (.write writer (str (pr-str {:url source-url :id post-id}) "\n")))
        ;; Write the canonical_url -> post_id mapping
        (when canonical-url
          (.write writer (str (pr-str {:url canonical-url :id post-id}) "\n"))))
      (catch Exception e
        (println (str "WARN: Could not write to completion data files: " (.getMessage e)))))))


;; --- NEW READER FUNCTIONS FOR PHASE 3 ---

(defn read-completed-post-ids
  "Reads the completed-post-ids.log file into a set for fast lookups."
  []
  (let [file (io/file (config/completed-post-ids-log-path))]
    (if (.exists file)
      (->> (slurp file)
           (str/split-lines)
           (remove str/blank?)
           (into #{}))
      #{})))

(defn read-url-to-id-map
  "Reads the url-to-id.map file into a Clojure map for {url -> id} lookups."
  []
  (let [file (io/file (config/url-to-id-map-path))]
    (if (.exists file)
      (try
        (let [content (slurp file)
              ;; The file is a sequence of maps, so wrap it in brackets to make it a valid EDN vector
              edn-vec (edn/read-string (str "[" content "]"))]
          (->> edn-vec
               ;; Create the final {url -> id} map
               (reduce (fn [acc {:keys [url id]}] (assoc acc url id)) {})))
        (catch Exception e
          (println (str "WARN: Could not read or parse url-to-id.map: " (.getMessage e)))
          {}))
      {})))

(defn read-ignore-list
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

