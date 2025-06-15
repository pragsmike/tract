;; File: src/tract/config.clj
(ns tract.config
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:import [java.io File]))

(defn- load-config!
  "Loads config.edn from the project root.
  Returns an empty map if the file doesn't exist or is invalid."
  []
  (let [config-file (io/file "config.edn")]
    (if (.exists config-file)
      (try
        (-> (slurp config-file)
            (edn/read-string))
        (catch Exception e
          (println "WARN: Could not read or parse config.edn. Using default values.")
          (println e)
          {}))
      {})))

;; Load the config only once
(defonce ^:private config (atom (load-config!)))

;; --- Accessor Functions with Defaults ---

(defn work-dir
  "Returns the base directory for all pipeline operations."
  []
  (get @config :work-dir "work"))

(defn browser-debugger-address
  "Returns the address for the remote debugging browser instance."
  []
  (get @config :browser-debugger-address "127.0.0.1:9222"))

;; --- HTTP Client Config ---

(defn http-throttle-base-ms []
  (get-in @config [:http-client :throttle-base-ms] 2000))

(defn http-throttle-random-ms []
  (get-in @config [:http-client :throttle-random-ms] 1500))

;; --- Fetch Stage Config ---

(defn fetch-throttle-base-ms []
  (get-in @config [:fetch-stage :throttle-base-ms] 2500))

(defn fetch-throttle-random-ms []
  (get-in @config [:fetch-stage :throttle-random-ms] 2000))

(defn fetch-max-retries []
  (get-in @config [:fetch-stage :max-retries] 5))

;; --- Derived Path Helpers ---

(defn processed-dir-path
  "Returns the full path to the final output directory."
  []
  (str (io/file (work-dir) "3-processed")))

(defn stage-dir-path
  "Returns the full path to a specific stage's directory or one of its subdirectories."
  ([stage-name]
   (str (io/file (work-dir) (name stage-name))))
  ([stage-name subdir-name]
   (str (io/file (work-dir) (name stage-name) (name subdir-name)))))


(defn completed-post-ids-log-path
  "Returns the full path to the log of completed post IDs."
  []
  (str (io/file (work-dir) "completed-post-ids.log")))

(defn url-to-id-map-path
  "Returns the full path to the URL-to-ID lookup map."
  []
  (str (io/file (work-dir) "url-to-id.map")))

(defn old-completed-log-path
  "Returns the path to the legacy completed.log file.
  This is for use by migration/pruning tools only."
  []
  (str (io/file (work-dir) "completed.log")))

(defn ignored-domains-path
  "Returns the path to the ignored-domains.txt file in the project root."
  []
  "ignored-domains.txt")

(defn external-links-csv-path
  "Returns the path to the external links database."
  []
  (str (io/file (work-dir) "external-links.csv")))
