(ns tract.compiler-test
  (:require [clojure.test :refer :all]
            [tract.compiler :as compiler]
            [clojure.string :as str]))

(deftest compile-to-article-test
  (testing "with a standard article (with a post-id)"
    (let [;; 1. Define sample input data that mimics the parser's output,
          ;;    including the new mandatory :post-id.
          parsed-data {:metadata {:title            "My Test Title"
                                  :publication-date "2023-01-01"
                                  :post-id          "my-test-slug"}
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

      (testing "YAML front matter is generated correctly"
        (let [front-matter (-> (:markdown article) (str/split #"---") second)]
          (is (str/includes? front-matter "title: My Test Title"))
          ;; The article-key in the YAML should now match the slug.
          (is (str/includes? front-matter "article-key: my-test-slug"))
          (is (str/includes? front-matter "post-id: my-test-slug"))))

      (testing "Markdown body is generated correctly"
        (is (str/includes? (:markdown article) "## A Subheading"))
        (is (str/includes? (:markdown article) "paragraph with a [link](https://clojure.org)"))
        (is (str/includes? (:markdown article) "and **bold text**."))
        (is (str/includes? (:markdown article) "> A quote."))
        (is (str/includes? (:markdown article) "![A test image](example.com/test.png \"\")")))

      (testing "Image job data is extracted correctly"
        (is (= 1 (count images)))
        ;; The article-key for the image job now comes from the slug.
        (is (= "my-test-slug" (:article-key image-job)))
        ;; All keys are checked using kebab-case.
        (is (= "http://example.com/test.png" (:image-source-url image-job)))
        (is (= "example.com/test.png" (:image-path image-job)))
        (is (= "A test image" (:caption image-job))))))

  (testing "with missing post-id (fallback behavior)"
    (let [;; This data is missing the crucial :post-id
          parsed-data {:metadata {:title "A Title Without A Slug"}
                       :body-nodes [{:tag :p, :content ["Some content."]}]}
          result (compiler/compile-to-article parsed-data)
          article-key (get-in result [:article :metadata :article-key])]
      (testing "A fallback key is generated"
        (is (str/starts-with? article-key "unknown-article_"))))))
