Excellent question. A robust system must be validated against its edge cases. I have reviewed the existing codebase, tracing the lifecycle of a URL through the pipeline, to verify its behavior in the four scenarios we identified.

My conclusion is that **yes, the existing code is correctly designed to handle all four cases.** The system's resilience stems from a single, fundamental design decision: **it always privileges the `<link rel="canonical">` tag found in the fetched HTML as the source of truth for an article's identity.**

Here is a detailed breakdown of how the code handles each case:

### The Core Logic Flow

The key is the interaction between the `fetch` and `parser` stages:

1.  **`fetch` Stage (`tract.stages.fetch`)**:
    *   Receives a `source-url` (which could be messy, like `t.co/...` or an AMP link).
    *   It uses `etaoin` (a browser automation library) to navigate to this `source-url`. `etaoin` will automatically follow any HTTP redirects.
    *   It grabs the final HTML content and saves it. Crucially, it also saves a `.meta.json` file containing the *original* `source-url`.
    *   It creates a symlink for the parser stage.

2.  **`parser` Stage (`tract.stages.parser`)**:
    *   It reads the HTML content.
    *   It calls `tract.parser/extract-metadata`, which finds the `<link rel="canonical" href="...">` tag.
    *   It extracts the `canonical-url` and derives the true `post-id` (the slug) from it.
    *   It calls `tract.db/record-completion!` with three key pieces of data:
        1.  The true `:post-id` (slug).
        2.  The original `:source-url` (from the `.meta.json` file).
        3.  The `:canonical-url` (from the HTML).
    *   This function then populates `url-to-id.map` with mappings for **both** the source and canonical URLs to the final slug.

### Verification Against Each Case

#### Case 1: URL Shorteners and Trackers (`t.co`, `bit.ly`)

*   **`fetch` stage:** `(e/go driver "https://t.co/aBcDeF123")` is executed. The automated browser transparently follows the redirect to the real page, e.g., `https://author.substack.com/p/my-real-article`. The fetched HTML is for the real article.
*   **`parser` stage:** The HTML contains `<link rel="canonical" href="https://author.substack.com/p/my-real-article">`. The parser correctly extracts `my-real-article` as the `:post-id`.
*   **Database:** `record-completion!` writes the crucial mapping to `url-to-id.map`: `{:url "https://t.co/aBcDeF123" :id "my-real-article"}`.
*   **Result:** **Correct.** On a future run, `is-url-already-completed-in-db?` in the `fetch` stage will find the `t.co` URL in the map, see that `my-real-article` is complete, and skip the fetch.

#### Case 2: AMP (Accelerated Mobile Pages)

*   **`fetch` stage:** `(e/go driver "https://.../my-article/amp/")` is executed. It fetches the AMP-specific HTML.
*   **`parser` stage:** The AMP HTML standard requires a canonical link. The parser finds `<link rel="canonical" href="https://www.some-blog.com/p/my-article">`. It correctly extracts `my-article` as the `:post-id`.
*   **Database:** `record-completion!` writes the mapping `{:url "https://.../my-article/amp/" :id "my-article"}` to the map.
*   **Result:** **Correct.** The system learns to associate the AMP URL with the canonical ID and will not re-process it.

#### Case 3: Syndicated Content and Archives

*   **`fetch` stage:** Fetches the HTML from the archive/syndication URL (e.g., `web.archive.org/...`).
*   **`parser` stage:** This case relies on the archive service preserving the original page's `canonical` link tag. Reputable services like the Internet Archive do this. The parser finds the original canonical link and extracts the true slug.
*   **Database:** `record-completion!` writes the mapping from the long archive URL to the simple, correct slug.
*   **Result:** **Correct,** with the minor caveat that it depends on the syndicator's behavior. The code itself is robust.

#### Case 4: Complex CMS Routing (`article.php?id=12345`)

*   **`fetch` stage:** Fetches the HTML from `https://site.com/article.php?id=12345`.
*   **`parser` stage:** The HTML contains `<link rel="canonical" href="https://site.com/2024/01/01/a-better-slug">`. The parser correctly extracts `a-better-slug` as the `:post-id`.
*   **Database:** `record-completion!` writes the mapping `{:url "https://site.com/article.php?id=12345" :id "a-better-slug"}`.
*   **Result:** **Correct.** This is the classic use-case the system was designed for. It correctly associates the non-human-readable URL with its canonical identity.

The system is well-architected to handle the unpredictable nature of web URLs by consistently deferring to the canonical link as the single source of truth for identity.
