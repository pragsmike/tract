(ns tract.core
  (:require [tract.pipeline :as pipeline]
            [tract.stages.job :as job] ; <-- Add job stage
            [tract.stages.fetch :as fetch]
            [tract.stages.parser :as parser])
  (:gen-class))

(def stages [:job :fetch :parser])

(defn -main
  "Main entry point for the entire `tract` application."
  [& args]
  (println "--- Initializing Tract Pipeline ---")
  (doseq [stage-name stages]
    (println (str "Initializing directories for stage: " (name stage-name)))
    (pipeline/initialize-stage-dirs! stage-name))
  (println "--- Initialization Complete ---\n")

  ;; Run each stage in order
  (job/run-stage!)
  (println)
  (fetch/run-stage!)
  (println)
  (parser/run-stage!)

  (shutdown-agents)
  (println "\n--- Tract Pipeline Run Finished ---"))
