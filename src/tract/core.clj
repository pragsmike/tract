(ns tract.core
  (:require [etaoin.api :as e])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute])
  (:gen-class))

(defonce target-url "https://www.mind-war.com/p/testing-testing-los-angeles-under")

(defn -main
  "Fetches the HTML source of a target URL and prints it to stdout."
  [& args]
  (println "-> Initializing headless browser and fetching page source for:" target-url)

  (let [temp-dir (str (Files/createTempDirectory "etaoin-user-data-" (into-array FileAttribute [])))
        ;; Options for the Chrome driver process.
        ;; :headless true is the key for running without a GUI.
        driver-opts {:headless true
                     :args [(str "--user-data-dir=" temp-dir)
                            "--disable-gpu"
                            "--no-sandbox"]}]

    ;; The e/chrome function with options returns a driver object (a map).
    ;; We must manually manage its lifecycle with a try...finally block.
    (let [driver (e/chrome driver-opts)]
      (try
        (e/go driver target-url)
        (let [html-source (e/get-source driver)]
          (println "\n--- BEGIN HTML SOURCE ---")
          (println html-source)
          (println "--- END HTML SOURCE ---"))
        (finally
          ;; The finally block ensures that e/quit is always called,
          ;; even if an error occurs in the try block. This is crucial
          ;; for preventing orphaned browser processes.
          (e/quit driver)
          (println "-> Driver process terminated. Process complete."))))
  (shutdown-agents)))
