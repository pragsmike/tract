;; File: src/tract/core.clj
(ns tract.core
  (:require [tract.pipeline :as pipeline]
            [tract.stages.job :as job]
            [tract.stages.fetch :as fetch]
            [tract.stages.parser :as parser]
            [tract.config :as config] ; <--- ADDED
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
  ;; vvvv MODIFIED vvvv
  (let [driver (e/chrome {:capabilities
                          {:chromeOptions
                           {:debuggerAddress (config/browser-debugger-address)}}})]
  ;; ^^^^ MODIFIED ^^^^
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
