(ns tract.pipeline
  (:require [clojure.java.io :as io]
            [tract.config :as config])
  (:import [java.io File]))

(def base-dir (config/work-dir))

(defn- get-stage-dir ^File [stage-name]
  (io/file base-dir (name stage-name)))

(defn- get-subdir ^File [stage-dir subdir-name]
  (io/file stage-dir (name subdir-name)))

(defn initialize-stage-dirs!
  "Creates the pending, done, and error directories for a given stage.
  This should be called by a master orchestrator before any stage runs."
  [stage-name]
  (let [stage-dir (get-stage-dir stage-name)]
    (doseq [subdir ["pending" "done" "error"]]
      (.mkdirs (get-subdir stage-dir subdir)))))

(defn get-pending-files
  "Returns a sequence of File objects from the <stage-name>/pending directory."
  [stage-name]
  (let [pending-dir (-> stage-name get-stage-dir (get-subdir "pending"))]
    (when (.exists pending-dir)
      (->> (.listFiles pending-dir)
           (filter #(.isFile %))))))

(defn write-to-next-stage!
  "Writes content to a file in the pending directory of the next stage."
  [content-string next-stage-name output-filename]
  (let [output-dir (-> next-stage-name get-stage-dir (get-subdir "pending"))
        output-file (io/file output-dir output-filename)]
    ;; No need to .mkdirs here, as we assume central initialization
    (println (str "\t=> Writing output to " output-file))
    (spit output-file content-string)))

(defn- move-file! [^File source-file ^File dest-dir]
  ;; No need to .mkdirs here, as we assume central initialization
  (let [dest-file (io/file dest-dir (.getName source-file))]
    (.renameTo source-file dest-file)))

(defn move-to-done!
  "Moves a processed file from a stage's pending dir to its done dir."
  [^File file stage-name]
  (println (str "-> Archiving " (.getName file) " to done."))
  (let [done-dir (-> stage-name get-stage-dir (get-subdir "done"))]
    (move-file! file done-dir)))

(defn move-to-error!
  "Moves a file to a stage's error dir and writes an error report."
  [^File file stage-name ^Exception exception]
  (println (str "ERROR: Moving " (.getName file) " to error directory."))
  (let [error-dir (-> stage-name get-stage-dir (get-subdir "error"))
        error-report-file (io/file error-dir (str (.getName file) ".error"))
        error-report-content (with-out-str (.printStackTrace exception))]
    (move-file! file error-dir)
    (spit error-report-file error-report-content)))
