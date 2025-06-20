You've identified another crucial scenario in the pipeline's lifecycle. Let's break down what the code currently does in the case of duplicate articles requested via different URLs and then evaluate the pros and cons of capturing the post-redirect source URL.

### Current Behavior: Duplicate Article via Different URLs

Here's a step-by-step trace of what happens when the same article is requested twice with different `given URLs` (e.g., `url-A` and `url-B`, both pointing to an article with slug `the-real-slug`).

**Run 1 (with `url-A`):**

1.  **`job` stage:** `url-A` is not in the completion DB, so it's written to a url-list for the `fetch` stage.
2.  **`fetch` stage:**
    *   `url->filename(url-A)` creates `url-A.html`.
    *   The symlink `work/parser/pending/url-A.html` is created.
    *   `work/html/url-A.html` is created.
    *   `work/metadata/url-A.html.meta.json` is created with `{:source-url "url-A", ...}`.
3.  **`parser` stage:**
    *   Processes `url-A.html`.
    *   Parses the HTML, finds the canonical URL, and determines the slug is `the-real-slug`.
    *   Generates `work/3-processed/the-real-slug.md`.
    *   Calls `db/record-completion!`, which:
        *   Adds `the-real-slug` to `completed-post-ids.log`.
        *   Adds mappings for `url-A` and the canonical URL to `url-to-id.map`.

**Run 2 (with `url-B`):**

1.  **`job` stage:** Checks `url-B` against `url-to-id.map`. It's not there, so it's passed to the `fetch` stage.
2.  **`fetch` stage:**
    *   `url->filename(url-B)` creates `url-B.html`.
    *   The symlink `work/parser/pending/url-B.html` is created.
    *   `work/html/url-B.html` is created.
    *   `work/metadata/url-B.html.meta.json` is created with `{:source-url "url-B", ...}`.
    *   **Result:** You are correct. We now have two distinct `html+meta` file pairs for the same article.
3.  **`parser` stage:**
    *   Processes `url-B.html`.
    *   Parses the HTML, finds the canonical URL, and determines the slug is `the-real-slug`.
    *   **It does NOT check if the slug is already completed.**
    *   It generates `work/3-processed/the-real-slug.md`, overwriting the previous one.
    *   It calls `db/record-completion!`, which:
        *   Adds `the-real-slug` to `completed-post-ids.log` (a harmless duplicate).
        *   Adds a mapping for `url-B` to `url-to-id.map`.

**Answer:** The code currently **does not** detect the duplication at parse time. It re-parses the content, overwrites the final markdown file, and updates the database with the new `given URL`.

**Is this a problem?**

*   **Minor Inefficiency:** It wastes cycles re-downloading and re-parsing content.
*   **Minor Data Redundancy:** It creates redundant `.html` and `.meta.json` files in the immutable core. This is a small "storage bug" but doesn't harm the integrity of the final output.
*   **No Data Loss:** The final output (`the-real-slug.md`) is correct. The database (`url-to-id.map`) is also correct and even more complete, as it now knows that both `url-A` and `url-B` map to this article.

The system self-corrects at the cost of some duplicated effort and storage.

---

### Pros and Cons of Capturing the Post-Redirect Source URL

Let's evaluate your proposal: capturing the "true" source URL from the browser after redirects and storing all three URLs (`given`, `source`, `canonical`).

We could get the post-redirect URL in the `fetch` stage with `(e/get-url driver)`.

#### **Pros:**

1.  **Improved Fetch-Time De-duplication:** If we knew that `given URL` `url-A` redirected to `source URL` `real-source.com/article`, we could store that mapping. When `given URL` `url-B` also redirects to the same `real-source.com/article`, we could potentially detect the duplication at fetch time, preventing the second download. This would require an extra lookup map (`given-url -> source-url`) or a more complex check.
2.  **Richer Metadata:** Having all three URLs provides a more complete audit trail of how content was discovered and retrieved. This could be valuable for debugging or deep analysis of content sources.
3.  **Potential for Smarter Filenaming:** The `html+meta` files could be named based on the post-redirect source URL, which might be more consistent than using the (potentially transient) given URL.

#### **Cons:**

1.  **Increased Complexity:** This adds a new piece of state to manage. The logic in the `fetch` stage becomes more complex: get `given URL`, navigate, get `source URL`, check if `given URL` or `source URL` are in the DB, then proceed. This adds more points of potential failure.
2.  **Marginal Gain:** The current system *already solves the problem* by the end of the `parser` stage. It correctly maps all `given URLs` to the canonical slug, preventing future re-fetches of *those same `given URLs`*. The proposed change primarily saves a one-time redundant fetch for any *new* alias URLs. The benefit might not outweigh the added complexity.
3.  **Doesn't Solve the Core Issue:** The fundamental problem is that the canonical ID is only known after parsing the HTML. Even if we have the post-redirect source URL, it might *still* not match the canonical URL's path (e.g., `site.com/article?id=123` vs `site.com/p/the-real-slug`). The definitive check can only happen post-parse.
4.  **Risk of Breaking Simplicity:** The current design has a clear, clean boundary: `fetch` deals with URLs and produces files; `parser` deals with content and produces canonical output. Blurring this by having `fetch` do more complex DB lookups based on multiple URL types could violate the principle of separation of concerns.

### Recommendation

While capturing the post-redirect URL is an interesting idea, I would advise **against implementing it for now.**

The current system's behavior is robust enough. The "bug" of re-fetching and overwriting is a minor inefficiency, not a catastrophic data corruption issue. The system self-heals its database (`url-to-id.map`) as it discovers new aliases for an article.

The added complexity of managing a third URL type in the `fetch` stage seems to offer a low return on investment compared to the risk of introducing new bugs into a critical, stable part of the pipeline. A better, simpler improvement would be to add a check at the beginning of the `parser` stage to see if the derived slug is already in the `completed-post-ids.log`, and if so, simply add the new URL mapping and skip the rest of the parsing and writing steps. This would prevent the overwrite and save some processing time without complicating the `fetch` stage.
