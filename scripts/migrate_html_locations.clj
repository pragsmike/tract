;; File: scripts/migrate_html_locations.clj
(ns migrate-html-locations
  (:require [clojure.java.io :as io]
            [tract.config :as config])
  (:import [java.nio.file Files Path Paths]
           [java.nio.file.attribute FileAttribute]))

(defn -main [& args]
  (println "--- HTML File Location Migration Script (v3 - Corrected) ---")
  (println "This will move all *.html files from parser/done/ to html/ and replace them with symlinks.")

  (let [parser-done-dir (io/file (config/stage-dir-path :parser "done"))
        html-dir (io/file (config/html-dir-path))
        stats (atom {:processed 0, :moved 0, :skipped 0})]

    (if-not (.exists parser-done-dir)
      (println (str "-> Parser 'done' directory does not exist. Nothing to do."))
      (do
        (.mkdirs html-dir)
        (println (str "-> Scanning " (.getPath parser-done-dir) " for HTML files to migrate..."))
        (let [html-files (filter #(and (.isFile %) (not (Files/isSymbolicLink (.toPath %))))
                                 (.listFiles parser-done-dir))]

          (if (empty? html-files)
            (println "-> No physical HTML files found to migrate. The corpus may already be migrated.")
            (do
              (doseq [original-file html-files]
                (swap! stats update :processed inc)
                (let [filename (.getName original-file)
                      dest-file (io/file html-dir filename)]
                  (if (.exists dest-file)
                    (do
                      (println (str "[SKIPPING] " filename " (already exists in destination)"))
                      (swap! stats update :skipped inc))
                    (do
                      (println (str "[MOVING]   " filename " -> " (.getPath dest-file)))
                      (.renameTo original-file dest-file)

                      ;; --- CORRECTED & ROBUST SYMLINK LOGIC ---
                      (let [;; The absolute path to the link we are creating
                            ^Path link-path (.toPath original-file)
                            ;; The absolute path to the file we are linking to
                            ^Path dest-path (.toPath dest-file)
                            ;; The directory where the link will live
                            ^Path link-dir-path (.getParent link-path)
                            ;; Calculate the correct relative path from the link's dir to the target
                            relative-target (.relativize link-dir-path dest-path)]
                        (println (str "[LINKING]  " link-path " -> " relative-target))
                        (Files/createSymbolicLink link-path
                                                  relative-target
                                                  (make-array FileAttribute 0)))
                      (swap! stats update :moved inc)))))

              (println "\n--- Migration Complete ---")
              (println "Summary:")
              (println "--------------------------")
              (println (str "Files Processed: " (:processed @stats)))
              (println (str "Successfully Moved & Linked: " (:moved @stats)))
              (println (str "Skipped (already exist):     " (:skipped @stats)))
              (println "--------------------------"))))))))
