(ns tract.util-test
  (:require [clojure.test :refer :all]
            [tract.util :as util]
            [clojure.java.io :as io])
  (:import [java.io File]))

(deftest extract-slug-from-url-test
  (testing "Extraction of a slug from a URL"
    (is (= "my-great-article" (util/extract-slug-from-url "https://author.substack.com/p/my-great-article")))
    (is (= "article-with-slash" (util/extract-slug-from-url "https://author.substack.com/p/article-with-slash/")))
    (is (nil? (util/extract-slug-from-url "https://example.com/")))
    (is (nil? (util/extract-slug-from-url "not-a-valid-url")))
    (is (nil? (util/extract-slug-from-url nil)))))

(deftest get-slug-from-meta-filename-test
  (testing "Extraction of a slug from a metadata filename"
    (testing "from a string"
      (is (= "my-article-slug" (util/get-slug-from-meta-filename "my-article-slug.html.meta.json"))))
    (testing "from a File object"
      (is (= "another-slug" (util/get-slug-from-meta-filename (io/file "work/metadata/another-slug.html.meta.json")))))
    (testing "with an empty string"
      (is (= "" (util/get-slug-from-meta-filename ""))))))

(deftest extract-domain-test
  (testing "Extraction of a domain from a URL string"
    (is (= "www.example.com" (util/extract-domain "https://www.example.com/path/to/page")))
    (is (= "sub.domain.co.uk" (util/extract-domain "http://sub.domain.co.uk?query=1")))
    (is (nil? (util/extract-domain "www.missing-protocol.com")))
    (is (nil? (util/extract-domain "not a url")))
    (is (nil? (util/extract-domain nil)))))

(deftest canonicalize-url-test
  (testing "Canonicalization of URLs"
    (is (= "https://example.com/page" (util/canonicalize-url "https://example.com/page?query=1#fragment")))
    (is (= "https://example.com/page" (util/canonicalize-url "https://example.com/page?query=1")))
    (is (= "https://example.com/page" (util/canonicalize-url "https://example.com/page#fragment")))
    (is (= "https://example.com/page" (util/canonicalize-url "https://example.com/page")))
    (is (nil? (util/canonicalize-url nil)))
    (is (= "" (util/canonicalize-url "")))))

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
      ;; The slug extraction would be "article-with-slash", but this function is dumber
      ;; and replaces non-alnum characters, so the trailing / becomes _.
      (is (= "article-with-slash_.html"
             (util/url->filename "https://author.substack.com/p/article-with-slash/"))))

    (testing "a URL with characters needing replacement"
      (is (= "_some_weird_path_.html"
             (util/url->filename "https://example.com/some/weird_path/"))))

    (testing "preconditions (invalid input)"
      (is (thrown? AssertionError (util/url->filename nil)))
      (is (thrown? AssertionError (util/url->filename "")))
      (is (thrown? AssertionError (util/url->filename "   "))))))
