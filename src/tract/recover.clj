(ns tract.recover
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tract.util :as util]
            [clj-yaml.core :as yaml]
            [net.cgrand.enlive-html :as html])
  (:import [java.io StringReader File])
  (:gen-class))

(defn- is-short-error-page? [file]
  (let [html-string (slurp file)]
    (if (> 5000 (count html-string))
      (try
        (= "Too Many Requests" (-> html-string
                                   StringReader.
                                   html/html-resource
                                   (html/select [:body])
                                   first
                                   html/text
                                   str/trim))
        (catch Exception _ false))
      false)))

(defn- build-url-map
  "Scans all job files to build a map of {filename -> original_url}."
  []
  (let [job-files (->> (io/file "work/fetch/done")
                       file-seq
                       (filter #(.isFile %)))]
    (->> job-files
         (mapcat (fn [job-file]
                   (let [urls (->> (slurp job-file) str/split-lines (remove str/blank?))]
                     (map #(vector (util/url->filename %) %) urls))))
         (into {}))))

(defn -main [& args]
  (println "--- Running Short Page Recovery Tool ---")

  (println "-> Building map of all known filenames and URLs...")
  (let [url-map (build-url-map)]
    (println (str "-> Found " (count url-map) " URL/filename pairs in completed fetch jobs."))

    (println "-> Scanning for short pages in the parser error and done directories...")
    (let [all-html-files (concat (file-seq (io/file "work/parser/done"))
                                 (file-seq (io/file "work/parser/error")))
          short-page-files (->> all-html-files
                                (filter #(.isFile %))
                                (filter is-short-error-page?))
          urls-to-recover (->> short-page-files
                               (map #(.getName %))
                               (keep url-map) ; Look up each filename in our map
                               vec
                               sort)]

      (if (seq urls-to-recover)
        (let [timestamp (.format (java.text.SimpleDateFormat. "yyyyMMdd'T'HHmmss") (new java.util.Date))
              job-filename (str "recovery-job-" timestamp ".yaml")
              job-content {"urls" urls-to-recover}
              job-file-path (io/file "work/job/pending" job-filename)]
          (println (str "-> Found " (count urls-to-recover) " URLs to recover. Writing job to " job-file-path))
          (spit job-file-path (yaml/generate-string job-content))

          (println "-> Moving processed short pages to work/parser/recovered/")
          (let [recovered-dir (io/file "work/parser/recovered")]
            (.mkdirs recovered-dir)
            (doseq [file short-page-files]
              (.renameTo file (io/file recovered-dir (.getName file))))))
        (println "-> No short pages found to recover."))))

  (println "--- Recovery Tool Finished ---"))
