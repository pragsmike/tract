(ns tract.util-test
  (:require [clojure.test :refer :all]
            [tract.util :as util]
            [clojure.string :as str])
  (:import [java.io File]
           [java.time LocalDate]))

(deftest canonicalize-url-test
  (testing "Canonicalization of URLs"
    (is (= "https://example.com/page" (util/canonicalize-url "https://example.com/page?query=1#fragment")))
    (is (= "https://example.com/page" (util/canonicalize-url "https://example.com/page?query=1")))
    (is (= "https://example.com/page" (util/canonicalize-url "https://example.com/page#fragment")))
    (is (= "https://example.com/page" (util/canonicalize-url "https://example.com/page")))
    (is (nil? (util/canonicalize-url nil)))
    (is (= "" (util/canonicalize-url "")))))

(deftest generate-article-key-test
  (testing "Generation of file-safe article keys"
    (testing "with full metadata"
      (let [metadata {:publication_date "2023-10-27", :title "My Awesome Post!"}]
        (is (= "2023-10-27_my-awesome-post" (util/generate-article-key metadata)))))

    (testing "with a long title"
      (let [metadata {:publication_date "2023-10-27"
                      :title "This is a very very long title that absolutely must be truncated for filesystem safety"}]
        (is (= "2023-10-27_this-is-a-very-very-long-title-that-absolutely-m"
               (util/generate-article-key metadata)))
        (is (< 60 (count (util/generate-article-key metadata))))))

    (testing "with only a date"
      (let [metadata {:publication_date "2023-10-27"}]
        (is (= "2023-10-27_untitled" (util/generate-article-key metadata)))))

    (testing "with only a title"
      ;; Use with-redefs to make the test deterministic
      (with-redefs [java.time.LocalDate/now (fn [] (LocalDate/parse "2025-01-01"))]
        (let [metadata {:title "A Title With No Date"}]
          (is (= "2025-01-01_a-title-with-no-date" (util/generate-article-key metadata))))))

    (testing "with no metadata"
      (with-redefs [java.time.LocalDate/now (fn [] (LocalDate/parse "2025-01-01"))]
        (let [metadata {}]
          (is (= "2025-01-01_untitled" (util/generate-article-key metadata))))))))

(deftest url->local-path-test
  (testing "Conversion of image URLs to local file paths"
    (testing "a simple URL"
      (is (= (str "example.com" File/separator "images" File/separator "test.jpg")
             (util/url->local-path "http://example.com/images/test.jpg"))))

    (testing "a Substack CDN URL"
      (let [cdn-url "https://substackcdn.com/image/fetch/w_1456,c_limit,f_webp,q_auto:good,fl_progressive:steep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2F123.png"]
        (is (= (str "substack-post-media.s3.amazonaws.com" File/separator "public" File/separator "images" File/separator "123.png")
               (util/url->local-path cdn-url)))))

    (testing "a URL with query parameters"
      (is (= (str "example.com" File/separator "path" File/separator "image.png")
             (util/url->local-path "https://example.com/path/image.png?version=2&source=test"))))

    (testing "preconditions (invalid input)"
      (is (thrown? AssertionError (util/url->local-path nil)))
      (is (thrown? AssertionError (util/url->local-path "")))
      (is (thrown? AssertionError (util/url->local-path "   "))))))

(deftest url->filename-test
  (testing "Conversion of article URLs to safe HTML filenames"
    (testing "a standard substack post URL"
      (is (= "my-great-article.html"
             (util/url->filename "https://author.substack.com/p/my-great-article"))))

    (testing "a custom domain post URL"
      (is (= "another-great-article.html"
             (util/url->filename "https://www.custom.com/p/another-great-article"))))

    (testing "a URL with a trailing slash"
      (is (= "article-with-slash_.html"
             (util/url->filename "https://author.substack.com/p/article-with-slash/"))))

    (testing "a URL with characters needing replacement"
      (is (= "_some_weird_path_.html"
             (util/url->filename "https://example.com/some/weird_path/"))))

    (testing "preconditions (invalid input)"
      (is (thrown? AssertionError (util/url->filename nil)))
      (is (thrown? AssertionError (util/url->filename "")))
      (is (thrown? AssertionError (util/url->filename "   "))))))
