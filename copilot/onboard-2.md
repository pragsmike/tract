I like that approach.   I'd like to also incorporate the rel="canonical" link somehow in case we download a full HTML that we already have.  Also, we'll need to have a "backfill" tool to remove duplicate articles.

Your UI is getting slower, and I think you approaching your limit of context comprehension as the project size has grown.  It is time to switch to a fresh instance.  Write a document to explain to that new instance how to proceed, similar to the one you were first given.  Include explicit instructions on how to avoid the mistakes that you have made, and the dead ends that we have worked past.  Finally, If I turn on tool access for that new instance to read URL and use google, will I be able to tell it to read the source from a public github repository?

__

You are absolutely right. Your diagnosis of my performance degradation and the increasing risk of context errors is spot on. Starting with a fresh instance is the correct and most effective way to proceed with a major architectural change like this. It's the "reboot" our collaboration needs.

Thank you for your clear-sightedness and for your excellent new requirements. The idea of incorporating `rel="canonical"` and creating a backfill tool for deduplication are essential additions that will make the new system truly robust.

Here is the onboarding document for the new instance. It incorporates the complete plan for the "Canonical ID" project, a summary of hard-won lessons, and explicit instructions on how to avoid the specific errors I have been making.

---

## Onboarding Document for tract Project Assistant (v2.0)

**Project:** `tract` - A Clojure-based content extraction pipeline.
**Current Initiative:** The Canonical ID Project - Refactoring the system to handle duplicate articles and non-unique URLs.

**To the AI Assistant:** Hello. We are continuing a software development project called `tract`. I am providing you with the complete, current source code and design documents. Your predecessor helped refactor the project to a stable state, and your task is to partner with me on the next major architectural enhancement.

This document contains crucial context, the complete plan for our next task, and critical lessons learned from our previous work. Please internalize these points, as they are more important than any generalized knowledge you may have.

### 1. Core Architecture Review

The system's core architecture is a **file-based pipeline** (`job` -> `fetch` -> `parser`). This is a non-negotiable design principle. Stages communicate by reading and writing files in standard directories. The system's "memory" of completed work is maintained in `work/completed.log`. The full design is detailed in the provided `DESIGN.md`.

### 2. The Immediate Goal: The Canonical ID Project

The single most significant limitation of the current system is its reliance on URLs as unique identifiers. As the human developer has correctly identified, the same article can appear under multiple URLs (e.g., `author.substack.com/p/post-slug` vs. `www.custom-domain.com/p/post-slug`). Our goal is to fix this.

**The Solution: A Hybrid "Canonical Post ID" Strategy**
We will refactor the system to use the internal **Substack Post ID** (a unique number like `12345678`) as the one true identifier for an article. This will be supplemented by the `<link rel="canonical">` tag as a robust fallback and verification mechanism.

### 3. The Implementation Plan

Here is the complete, multi-phase plan to implement this feature.

#### Phase 1: Data Model Changes
We must change our "source of truth" files.
1.  **Create `work/completed-post-ids.log`:** This new file will replace `work/completed.log`. It will be a simple, append-only log of the numeric **Post IDs** that have been successfully processed.
2.  **Create `work/url-to-id.map`:** This new file will be a lookup map (e.g., a CSV or EDN file) that stores mappings from known URLs to their canonical Post ID. This is our system's "memory" for resolving URLs without a network request.

#### Phase 2: Implement "Learning" in the Pipeline
We need to teach the application how to discover and record Post IDs.
1.  **Modify `tract.stages.job`:** When processing RSS/Atom feeds, it must now extract both the **URL** and the **Post ID** (if available in the feed's `<id>` tag) for each article.
2.  **Modify `tract.stages.parser`:** This is the primary learning stage. After parsing a full HTML file, it must:
    a. Extract the Post ID from the HTML content (e.g., from a `<meta name="parsely-post-id">` tag).
    b. Extract the canonical URL from the `<link rel="canonical">` tag.
    c. **Write to the new maps:**
        - Add the Post ID to `completed-post-ids.log`.
        - Add the mapping from the original `source_url` to the Post ID into `url-to-id.map`.
        - Add the mapping from the `canonical` URL to the Post ID into `url-to-id.map`.

#### Phase 3: Implement Pre-Fetch Deduplication (The Efficiency Win)
This phase uses our new data to avoid redundant work.
1.  **Modify `tract.stages.job`:** It will now filter its list of candidate articles by checking the **Post ID** (not the URL) against the `completed-post-ids.log`.
2.  **Modify `tract.stages.fetch`:** This is the most critical change. When given a new URL to fetch, it will perform these steps **before** launching a full browser fetch:
    a. Check if the URL exists in `url-to-id.map`. If yes, get the Post ID.
    b. If the URL is not in the map, perform a **lightweight `HEAD` request** to the URL to try and find an `x-substack-post-id` HTTP header.
    c. With the discovered Post ID, check against `completed-post-ids.log`.
    d. **If the ID is already completed, the fetch stage skips the expensive browser fetch entirely.**
    e. Only if the Post ID is new does it proceed with the full `etaoin` fetch.

#### Phase 4: Create the "Deduplication" Backfill Tool
As requested, we need a tool to clean up the existing data.
1.  **Create `scripts/deduplicate_corpus.clj`:** This will be a new standalone utility.
2.  **Logic:** The script will:
    a. Scan all processed `.html` files in `work/parser/done/`.
    b. For each file, parse it to find its Post ID and canonical URL.
    c. Build a map of `{PostID -> [list of associated HTML files]}`.
    d. Identify all Post IDs that are associated with more than one HTML file. These are our duplicates.
    e. **In dry-run mode (default):** Report the duplicate sets and which files it *would* delete.
    f. **In `--force` mode:** For each duplicate set, it will keep one version (e.g., the one with the canonical URL) and delete all other associated files (`.html`, `.html.meta`, `.md`). It will also update the log files to reflect the removals.

### 4. Crucial Lessons Learned: How to Avoid Past Mistakes

Your predecessor made several repeated categories of errors. **You must treat these instructions as a primary directive to avoid repeating them.**

1.  **API Hallucination (The Most Critical Failure):**
    *   **The Mistake:** Your predecessor repeatedly "hallucinated" non-existent library functions (e.g., `toml/write`) or provided incorrect function signatures.
    *   **The Rule:** I, the human, am the final verifier for all third-party library APIs. When you propose using a new function from a library like `etaoin`, `clj-yaml`, etc., you must state your proposed usage clearly. I will confirm if it is correct. **Do not assume your knowledge of a library's API is accurate.**

2.  **Syntactic Errors and Lisp Nuances:**
    *   **The Mistakes:** Your predecessor generated code with unmatched parentheses, incorrect macro usage (e.g., `(with-redefs [#'var ...])`), and flawed Java interop (`(-> ... (new URL) ...)`).
    *   **The Rule:** Your generated code must be treated as a **draft for my review**. I will be the final compiler and tester. Be extra cautious with Lisp macros and any form of Java interop, as these were consistent weak points. Double-check parenthesis balancing before emitting code.

3.  **Incomplete Logic:**
    *   **The Mistake:** Your predecessor would often propose a fix that solved the immediate problem but had unintended side effects (e.g., removing a log line from a utility that other components relied on) or was incomplete (e.g., not accounting for existing data during a migration).
    *   **The Rule:** Before proposing a change, consider its full impact across the entire system. Think about "Day 1" (how it works on new data) and "Day 0" (how we handle existing data). **I will rely on you to help me think through these edge cases and migration paths.**

### 5. Your Question Regarding Tool Access

You asked if enabling URL and Google Search access would allow you to read from a public GitHub repository.

**Yes, this is possible and would be a good workflow for us.**

*   **How it works:** I cannot `git clone` or browse a repository structure. However, you can provide me with the **direct URL to a raw file on GitHub** (e.g., `https://raw.githubusercontent.com/user/tract/main/src/tract/core.clj`).
*   **My Action:** I can use the "Read URL" tool to fetch the text content of that single file.
*   **The Result:** I would then have the full source code of that file in my context, exactly as if you had pasted it. This is a very effective way to provide me with the current state of the code without long copy-pastes.

This will be our preferred method for sharing code going forward.

---

Please confirm you have read and understood these instructions. Then, we can begin implementing **Phase 1: Data Model Changes** for the Canonical ID project.
