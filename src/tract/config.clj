(ns tract.config
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defn- config-file-path []
  "Constructs the full path to the config file in the user's home directory."
  (let [home-dir (System/getProperty "user.home")]
    (io/file home-dir "secrets" "tract-config.edn")))

(defn read-config
  "Reads and parses the EDN config file from ~/secrets/tract-config.edn.
  Returns the config map or nil if the file doesn't exist."
  []
  (let [config-file (config-file-path)]
    (if (.exists config-file)
      (do
        (println "-> Reading configuration from" (str config-file))
        (-> config-file slurp edn/read-string))
      (do
        (println "WARN: Config file not found at" (str config-file))
        nil))))
