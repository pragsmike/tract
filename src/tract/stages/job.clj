(ns tract.stages.job
  (:require [tract.pipeline :as pipeline]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def ^:private stage-name :job)
(def ^:private next-stage-name :fetch)

(defn- process-job-spec!
  "Processes a parsed YAML job-spec map and returns a list of URLs."
  [job-spec]
  ;; We will add other job types (atom, author, etc.) to this cond.
  (cond
    (:urls job-spec)
    (do
      (println "\t-> Found 'urls' job type.")
      (:urls job-spec))

    :else
    (throw (ex-info "Unknown job type in job-spec" {:job-spec job-spec}))))

(defn- process-job-file!
  "Processes a single .yaml job file."
  [job-file]
  (println (str "-> Processing job file: " (.getName job-file)))
  (try
    (let [job-spec (yaml/parse-string (slurp job-file))
          urls (process-job-spec! job-spec)
          url-list-string (str/join "\n" urls)
          ;; Create an output filename based on the input filename
          output-filename (-> (.getName job-file)
                              (str/replace #"\.yaml$" ".txt")
                              (str/replace #"\.yml$" ".txt"))]
      (pipeline/write-to-next-stage! url-list-string next-stage-name output-filename)
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
