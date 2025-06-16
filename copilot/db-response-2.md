You have proposed two excellent, creative alternatives that elegantly navigate the trade-offs we've discussed. Both of your ideas are strong improvements over the current system and stay true to the project's file-based philosophy.

Let's analyze them in detail.

---

### Your Idea #1: Decoupled, Centralized Metadata Directory

**How it would work:**
*   **HTML Files:** The `.html` files continue to follow the existing `pending/done/error` workflow. Their location is the sole indicator of their processing state.
*   **Metadata Files:** The `fetch` stage writes the `.html` file to `work/parser/pending/`. Then, *independently*, it writes a single `.meta` file (named after the slug, e.g., `my-post-slug.json`) to a new, permanent directory like `work/metadata/`. This directory is **not** part of the pipeline workflow; it's just a simple, append-only collection of all metadata ever created.
*   **Parser Logic:** When the `parser` stage picks up `my-post-slug.html`, it knows it can find the corresponding metadata by looking for `my-post-slug.json` inside the `work/metadata/` directory.

*   **Pros:**
    *   **Solves the Atomicity Problem (for the pipeline):** This is the key insight. The pipeline only needs to move a single file (`.html`) to signify state changes. The movement of one file is atomic. This completely resolves the core logical complexity.
    *   **High Inspectability:** Metadata is still in clean, individual JSON files. `grep` and `cat` work perfectly.
    *   **Keeps Source Artifacts Separate:** The `.html` files and their metadata are logically linked but not physically co-mingled.
    *   **Resilient:** If the app crashes after writing the HTML but before writing the metadata, the parser will fail gracefully (it won't find the metadata file), and the HTML can be moved to `error` for reprocessing. This is a safe failure mode.
*   **Cons:**
    *   **Potential for Orphaned Metadata:** It's possible for a `.meta` file to be created in `work/metadata/` for an HTML file that never successfully gets created or is later deleted. This is a minor issue, as the metadata file would simply be ignored. A periodic cleanup script could handle this.
    *   **Two Lookups:** The parser needs to perform two lookups: one for the HTML file in its `pending` directory, and a second one for the metadata file in the `metadata` directory. This is a trivial performance consideration.
*   **Risks:**
    *   This is a **very low-risk** approach. Its failure modes are safe and non-destructive.

---

### Your Idea #2: A Single, Append-Only CSV Log for Metadata

**How it would work:**
*   **HTML Files:** The `.html` files continue to follow the `pending/done/error` workflow, same as above.
*   **Metadata File:** There is a single, new, append-only file, perhaps `work/metadata.csv`.
*   **Fetch Logic:** When the `fetch` stage successfully downloads `my-post-slug.html`, it appends a single new row to `work/metadata.csv`. The row would look something like:
    `"my-post-slug","https://.../p/my-post-slug","2025-06-16T..."`
    `(slug,source_url,fetch_timestamp)`
*   **Parser Logic:** When the `parser` picks up `my-post-slug.html`, it needs to find its metadata. It would have to read the *entire* `work/metadata.csv` file into a map (keyed by slug) to look up the correct row.

*   **Pros:**
    *   **Solves the Atomicity Problem (for the pipeline):** Same as Idea #1, this is a huge win.
    *   **Conceptually Simple:** All metadata lives in one well-defined place.
    *   **Excellent for `grep`:** Searching all metadata is now as simple as `grep "some-pattern" work/metadata.csv`.
*   **Cons:**
    *   **Performance at Scale:** This is the most significant drawback. For the parser to process a single file, it must read and parse the *entire* CSV file into memory. As your corpus grows to tens of thousands of articles, this file will become very large, and parsing it on every single article will become a major performance bottleneck for the `parser` stage.
    *   **Concurrency Issues:** Appending to a single file from multiple processes (if you ever decided to parallelize the fetch stage) can be tricky and lead to corrupted writes. This is not a current problem but a future limitation.
*   **Risks:**
    *   The primary risk is **performance degradation over time**. The system will get noticeably slower as the `metadata.csv` file grows.

---

### Comparison and Recommendation

Let's compare your two ideas against the criteria:

| Approach | Solves Atomicity | Inspectability | Performance (Parser) | Risk |
| :--- | :--- | :--- | :--- | :--- |
| **1. Central Metadata Dir** | **Yes** | High | **High** | **Low** |
| **2. Single Metadata CSV** | **Yes** | **Very High** | **Low (at scale)** | **Medium (Perf. trap)** |

**Recommendation: Your Idea #1 (Decoupled, Centralized Metadata Directory) is the superior approach.**

It is the most robust and "tract-like" solution proposed so far. It perfectly solves the core atomicity problem for the pipeline's state machine while retaining high performance and high inspectability. It introduces no new significant risks or performance traps. It is an elegant, pragmatic, and powerful design.

The single CSV file is a very clever idea, but the performance bottleneck it introduces for the parser stage is a serious long-term liability. The "one directory per metadata file" approach avoids this entirely.

This feels like the right path forward.
