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
  ;; **THE REAL FIX**: Use the standard `e/chrome` function but provide the
  ;; official WebDriver capability to attach to a running instance via
  ;; the Chrome DevTools Protocol debugger address.
  (let [driver (e/chrome {:capabilities
                          {:chromeOptions
                           {:debuggerAddress "127.0.0.1:9222"}}})]
    (try
      ;; No login logic needed here. Assumes manual login in the persistent browser.
      (println "-> Successfully connected to browser.")

      ;; Run each stage in order
      (job/run-stage!)
      (println)
      (fetch/run-stage! driver)
      (println)
      (parser/run-stage!)

      (finally
        ;; When using debuggerAddress, `quit` detaches without closing the browser.
        (println "-> Detaching from browser session (leaving browser open).")
        (e/quit driver)
        (shutdown-agents)
        (println "\n--- Tract Pipeline Run Finished ---")))))
