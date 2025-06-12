(ns tract.core
  (:require [tract.pipeline :as pipeline]
            [tract.stages.job :as job]
            [tract.stages.fetch :as fetch]
            [tract.stages.parser :as parser]
            [etaoin.api :as e]))

(def stages [:job :fetch :parser])

(defn -main
  "Main entry point. Connects to a running browser and runs the pipeline."
  [& args]
  (println "--- Initializing Tract Pipeline ---")
  (doseq [stage-name stages]
    (println (str "Initializing directories for stage: " (name stage-name)))
    (pipeline/initialize-stage-dirs! stage-name))
  (println "--- Initialization Complete ---\n")

  (println "-> Connecting to existing Chrome browser on port 9222...")
  (let [driver (e/chrome {:capabilities
                          {:chromeOptions
                           {:debuggerAddress "127.0.0.1:9222"}}})]
    (try
      (println "-> Successfully connected to browser. Assuming it is already logged in.")

      (job/run-stage!)
      (println)
      (fetch/run-stage! driver)
      (println)
      (parser/run-stage!)

      (finally
        (println "-> Detaching from browser session (leaving browser open).")
        (e/quit driver)
        (shutdown-agents)
        (println "\n--- Tract Pipeline Run Finished ---")))))
