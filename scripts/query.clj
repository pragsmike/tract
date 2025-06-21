(ns query
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.tools.cli :as cli]
            [tract.config :as config])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute])
  (:gen-class))

(def cli-options
  [["-a" "--author AUTHOR" "Filter by author (case-insensitive substring match)"]
   ["-d" "--domain DOMAIN" "Filter by domain (exact match)"]
   ["-s" "--since YYYY-MM-DD" "Filter for articles published on or after this date"]
   ["-u" "--until YYYY-MM-DD" "Filter for articles published on or before this date"]
   ["-t" "--title-contains TEXT" "Filter by title (case-insensitive substring match)"]
   ["-o" "--output-dir DIR" "Required. Directory to place symlinks to matching articles."]
   ["-h" "--help" "Show this help message"]])

(defn- apply-filters [articles {:keys [author domain since until title-contains]}]
  (cond->> articles
    author
    (filter #(and (:author %) (str/includes? (str/lower-case (:author %))
                                             (str/lower-case author))))
    domain
    (filter #(= domain (:domain %)))
    since
    (filter #(and (:publication-date %) (>= 0 (compare since (:publication-date %)))))
    until
    (filter #(and (:publication-date %) (<= 0 (compare until (:publication-date %)))))
    title-contains
    (filter #(and (:title %) (str/includes? (str/lower-case (:title %))
                                            (str/lower-case title-contains))))))

(defn- create-symlinks! [articles output-dir-str]
  (let [output-dir (io/file output-dir-str)]
    (println (str "-> Creating " (count articles) " symlinks in '" output-dir-str "'..."))
    (.mkdirs output-dir)
    ;; Clear any existing symlinks for a clean run
    (doseq [f (.listFiles output-dir)]
      (when (Files/isSymbolicLink (.toPath f))
        (.delete f)))

    (doseq [{:keys [path slug]} articles]
      (let [source-path (.toPath (io/file path))
            link-path (.toPath (io/file output-dir (str slug ".md")))]
        (try
          ;; Adhering to Java Interop best practices for varargs
          (Files/createSymbolicLink link-path source-path (make-array FileAttribute 0))
          (catch Exception e
            (println (str "WARN: Could not create symlink for " slug ". Error: " (.getMessage e)))))))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      (do (println "Usage: clj -M:query [options]")
          (println summary)
          (System/exit 0))

      errors
      (do (println "ERROR:" (str/join "\n" errors))
          (System/exit 1))

      (not (:output-dir options))
      (do (println "ERROR: --output-dir is a required argument.")
          (println summary)
          (System/exit 1)))

    (println "--- Tract Article Query Tool ---")
    (let [cache-file (io/file (config/work-dir) "metadata-cache.edn")]
      (if-not (.exists cache-file)
        (println "ERROR: Metadata cache not found. Please run 'make build-cache' first.")
        (let [all-articles (edn/read-string (slurp cache-file))
              filtered-articles (doall (apply-filters all-articles options))]
          (println (str "-> Found " (count filtered-articles) " matching articles."))
          (when (seq filtered-articles)
            (create-symlinks! filtered-articles (:output-dir options))))))
    (println "--- Query Finished ---")
    (shutdown-agents)))
