(ns tract.core
  (:require [tract.pipeline :as pipeline]
            [tract.stages.job :as job]
            [tract.stages.fetch :as fetch]
            [tract.stages.parser :as parser]
            [tract.config :as config]
            [etaoin.api :as e]))

(def stages [:job :fetch :parser])

(defn -main
  "Main entry point. Connects to a running browser and runs the pipeline."
  [& args]
  (println "--- Initializing Tract Pipeline ---")
  (doseq [stage-name stages]
    (println (str "Initializing directories for stage: " (name stage-name)))
    (pipeline/initialize-stage-dirs! stage-name))

  (println "Initializing shared directories...")
  (.mkdirs (io/file (config/metadata-dir-path)))

  (println "--- Initialization Complete ---\n")

  (println "-> Connecting to existing Chrome browser on port 9222...")
  (let [driver (e/chrome {:capabilities
                          {:chromeOptions
                           {:debuggerAddress (config/browser-debugger-address)}}})]
    (try
      (println "-> Successfully connected to browser. Assuming it is already logged in.")

      (job/run-stage!)
      (println)
      (fetch/run-stage! driver)
      (println)
      (parser/run-stage!)

      ;; vvvv ADDED CATCH BLOCK TO HANDLE FATAL ERRORS GRACEFULLY vvvv
      (catch Exception e
        (println "\nERROR: A fatal, unrecoverable error occurred in a pipeline stage.")
        (println (str "       Reason: " (.getMessage e)))
        (println "       Proceeding to shutdown."))
      ;; ^^^^ ADDED CATCH BLOCK ^^^^

      ;; vvvv CORRECTED finally BLOCK STRUCTURE vvvv
      (finally
        (println "-> Detaching from browser session (leaving browser open).")
        (try
          (e/quit driver)
          (println "-> Successfully detached.")
          (catch Exception e
            (println (str "WARN: Graceful driver quit failed (likely already disconnected): " (.getMessage e)))
            (println "-> Forcibly terminating chromedriver process...")
            (when-let [proc (get-in driver [:process :proc])]
              (.destroy proc))))

        ;; These are now correctly inside the single finally block.
        (shutdown-agents)
        (println "\n--- Tract Pipeline Run Finished ---")))))
      ;; ^^^^ CORRECTED finally BLOCK ^^^^
