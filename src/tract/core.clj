(ns tract.core
  (:require [etaoin.api :as e])
  (:gen-class))

(defonce target-url "https://www.mind-war.com/p/testing-testing-los-angeles-under")

(defn -main
  "Fetches the HTML source of a target URL and prints it to stdout."
  [& args]
  (println "-> Initializing browser and fetching page source for:" target-url)
  ;; Etaoin requires a WebDriver executable (e.g., chromedriver)
  ;; to be on your system's PATH.
  (with-open [driver (e/chrome)]
    (e/go driver target-url)
    (let [html-source (e/get-source driver)]
      (println "\n--- BEGIN HTML SOURCE ---")
      (println html-source)
      (println "--- END HTML SOURCE ---")))
  (println "-> Browser closed. Process complete.")
  (shutdown-agents))
