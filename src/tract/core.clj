(ns tract.core
  (:require [tract.pipeline :as pipeline]
            [tract.stages.fetch :as fetch]
            [tract.stages.parser :as parser])
  (:gen-class))

;; Define all stages of the pipeline in order.
(def stages [:job :fetch :parser])

(defn -main
  "Main entry point for the entire `tract` application.
  Initializes and runs all pipeline stages in sequence."
  [& args]
  (println "--- Initializing Tract Pipeline ---")
  (doseq [stage-name stages]
    (println (str "Initializing directories for stage: " (name stage-name)))
    (pipeline/initialize-stage-dirs! stage-name))
  (println "--- Initialization Complete ---\n")

  ;; Run each stage in order.
  ;; In the future, we could add command-line args to run only specific stages.
  (fetch/run-stage!)
  (println) ; Add a blank line for readability
  (parser/run-stage!)

  ;; Shut down the agent pool used by clj-http-lite and other async libs.
  (shutdown-agents)
  (println "\n--- Tract Pipeline Run Finished ---"))
