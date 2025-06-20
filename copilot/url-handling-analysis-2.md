These are excellent and highly specific questions that cut to the very heart of the system's data integrity model. Your analysis is sharp, and your concerns are precisely the kinds of issues a robust pipeline must handle.

Let's analyze the code against your points.

### A Quick Clarification on Terminology

First, a small but critical clarification on how the code uses terms, which will be key to the analysis:

*   **`given URL`**: You are correct. This is the URL from the job file or url list.
*   **`source URL`**: In the code, the key `:source-url` actually refers to the **`given URL`**. The `fetch` stage saves the *original* `given URL` in the `.meta.json` file. The system does **not** currently try to resolve or store the final, post-redirect URL. This is a crucial design detail.
*   **`canonical URL`**: You are correct. This is from the `<link rel="canonical">` tag, discovered in the `parser` stage.

---

### Analysis of the Code

#### **URLs known in the fetch stage**

> I suppose the fetch stage could navigate to the given URL, then ask the browser for the source URL. Is this in fact what it does?

**No, it does not.**

Your initial assertion is correct: the `html` and `meta` filenames are derived directly from the `given URL`. The code in `tract.stages.fetch/process-url-list-file!` shows this clearly:

```clojure
;; `url` is the given URL from the input file
(let [expected-filename (util/url->filename url)
      ...
      meta-content {:source-url url ; The given URL is stored here
                    :fetch-timestamp ...}
      ...]
  ...
  (.renameTo temp-file final-html-file) ; `final-html-file` is based on `expected-filename`
  ...)
```

The code navigates to the `given URL` using `(e/go driver url-str)`, letting the browser handle redirects. However, it **never asks the browser for the final URL** after redirection. It only grabs the final HTML content with `(e/get-source driver)`. The `given URL` is the only one it knows about at this stage, and it's used for both the filename and the `:source-url` field in the metadata.

#### **URLs known in the parser stage**

> The markdown file is given a name that is derived from the canonical URL... Or is that in fact the case?

**Yes, that is exactly the case.**

The code in `tract.stages.parser/full-metadata` and `process-html-file!` confirms this:

1.  `parser-logic/parse-html` is called, which extracts the `canonical-url` and derives the `:post-id` (the slug) from it.
2.  `full-metadata` takes this `:post-id` and uses it to create an `:article-key`.
3.  The final markdown file is written using this key: `(jio/file output-path (str (:article-key metadata) ".md"))`.

This part of the pipeline works exactly as you described.

---

### **Concerns over URL Mismatches**

#### **Concern A: The "Never Skipped" Redirect**

> The decision to skip an already-fetched document is made based on the given URL. If the record of the document being fetched holds only the source URL and canonical URLs, then... if the given URL is not equal to the source URL (due to a redirect) then it will never be skipped.

**The code is NOT vulnerable to this problem.**

This is the most critical point. The system is protected from this vulnerability because of the explicit link it creates in the `parser` stage. Here is the data flow that prevents this issue:

1.  **Fetch:** The `fetch` stage receives a `given URL` (e.g., a `t.co` redirect link). It saves the HTML, and importantly, writes `{:source-url "https://t.co/..."}` into the `.meta.json` file.
2.  **Parse:** The `parser` stage reads the HTML and its corresponding `.meta.json` file. It now knows three things:
    *   The `given URL` (which it calls `:source-url`) from the metadata.
    *   The `canonical URL` from the parsed HTML.
    *   The `post-id` (slug) from the `canonical URL`.
3.  **Record:** It calls `db/record-completion!` with all three pieces of information.
4.  **Database Write:** `tract.db/record-completion!` writes **two entries** to `url-to-id.map`: one for the `given URL` and one for the `canonical URL`, both pointing to the same final `post-id` (slug).

Because the original `given URL` is explicitly stored and then mapped to the canonical slug in the database, the `fetch` stage's check (`is-url-already-completed-in-db?`) will find the `t.co` link in `url-to-id.map` on all subsequent runs and correctly skip it.

#### **Concern B: Mismatched Filenames**

> The html+meta files may end up with a different name than the markdown file... because the former is decided at fetch time... and the latter is decided at parser time...

**You are 100% correct. This happens, and it is a fundamental and intentional feature of the architecture.**

This decoupling of filenames is the system's primary mechanism for handling the messiness of the web. It is not a vulnerability but rather the solution to the many-to-one problem where many different URLs (redirects, AMP links, etc.) can point to a single, canonical piece of content.

*   The `html` filename (`t.co_aBc123.html`) is a record of the *work that was done*.
*   The `md` filename (`my-real-article.md`) is the *canonical output*.

The system does not rely on filenames to associate data between stages. The association is made via the pipeline flow itself: the `fetch` stage outputs a symlink, and the `parser` stage consumes that symlink, reads its content, and then generates a new, canonically-named output. The slug acts as the conceptual link, but there is no programmatic assumption that the input and output filenames will match. This is what makes the system resilient.
