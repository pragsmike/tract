(ns tract.compiler-test
  (:require [clojure.test :refer :all]
            [tract.compiler :as compiler]
            [clojure.string :as str]))

(deftest compile-to-article-test
  (testing "Conversion of parsed data to a final article map"
    (let [;; 1. Define sample input data that mimics the parser's output
          parsed-data {:metadata {:title "My Test Title"
                                  :publication_date "2023-01-01"}
                       :body-nodes [{:tag :h2, :attrs nil, :content ["A Subheading"]}
                                    {:tag :p, :attrs nil, :content ["A paragraph with a "
                                                                    {:tag :a, :attrs {:href "https://clojure.org"}, :content ["link"]}
                                                                    " and "
                                                                    {:tag :strong, :attrs nil, :content ["bold text"]}
                                                                    "."]}
                                    {:tag :figure, :attrs nil,
                                     :content [{:tag :div, :attrs nil,
                                                :content [{:tag :img, :attrs {:src "http://example.com/test.png"}, :content nil}
                                                          {:tag :figcaption, :attrs nil, :content ["A test image"]}]}]}
                                    {:tag :blockquote, :attrs nil, :content [{:tag :p, :attrs nil, :content ["A quote."]}]}]}
          ;; 2. Call the function under test
          result (compiler/compile-to-article parsed-data)
          article (:article result)
          images (:images result)
          image-job (first images)]

      (testing "Markdown content is generated correctly"
        (is (str/includes? (:markdown article) "## A Subheading"))
        (is (str/includes? (:markdown article) "paragraph with a [link](https://clojure.org)"))
        (is (str/includes? (:markdown article) "and **bold text**."))
        (is (str/includes? (:markdown article) "> A quote."))
        (is (str/includes? (:markdown article) "![A test image](example.com/test.png \"\")")))

      (testing "Image job data is extracted correctly"
        (is (= 1 (count images)))
        (is (= "2023-01-01_my-test-title" (:article_key image-job)))
        (is (= "http://example.com/test.png" (:image_source_url image-job)))
        (is (= "example.com/test.png" (:image_path image-job)))
        (is (= "A test image" (:caption image-job)))))))
