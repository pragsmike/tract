## `tract`: A Developer's Guide & Design Document

**Version:** 2.0
**Status:** Canonical ID & Architectural Refactoring Complete

### 1. Introduction

`tract` is a command-line application designed to extract content from web articles, primarily targeting Substack publications. Its purpose is to convert web articles into clean, LLM-ingestible formats: a Markdown file for the text content and a collection of all associated images.

The system is built as a robust, resilient, file-based pipeline, capable of handling both public and authenticated (subscriber-only) content. It is designed to be polite by throttling its network requests and to be extensible for supporting new sources and features in the future.

This document serves as the primary **technical guide** for developers working on `tract`. For a user-focused guide on how to operate the application, please see **[USAGE.md](../USAGE.md)**.

### 2. Core Philosophy & Architecture

The design of `tract` is guided by key principles that are essential for understanding how to maintain and extend the system.

#### 2.1. The Filesystem is the Pipeline

The entire workflow is modeled as a series of independent stages that communicate through the filesystem. Each stage reads files from a `pending` directory, processes them, and writes its output to the `pending` directory of the *next* stage.

*   `work/job/pending` -> **Job Stage** -> `work/fetch/pending`
*   `work/fetch/pending` -> **Fetch Stage** -> `work/parser/pending`
*   `work/parser/pending` -> **Parser Stage** -> `work/3-processed/`

This file-based approach provides three key benefits:
*   **Resilience:** If one stage fails while processing a file, it moves the file to an `error` directory without halting the entire system. The rest of the batch can continue.
*   **Testability & Debuggability:** The state of the pipeline is transparent. One can inspect the output of any stage by simply looking at the files. A specific stage can be tested in isolation by manually placing files in its `pending` directory.
*   **Idempotency:** A stage can be re-run safely. It will only process files that are currently in its `pending` directory.

#### 2.2. The Slug is the Canonical ID

The single most significant limitation of the original system was its reliance on URLs as unique identifiers. This has been fixed. The one true, unique identifier for an article is now its **URL slug** (e.g., `my-great-post`).

The canonical source for an article's slug is determined by parsing the `<link rel="canonical">` tag from its full HTML content. This approach correctly handles custom domains, tracking parameters, and other URL variations.

#### 2.3. Separation of Concerns (Namespaces)

The codebase is organized into namespaces with single, clear responsibilities.
*   **Logic vs. I/O:** Pure data-transformation logic (e.g., `tract.compiler`) is strictly separated from code that performs side-effects (e.g., `tract.io`, `tract.db`).
*   **Application vs. Tools:** Core application logic resides in `src/tract/`, while standalone administrative and maintenance utilities reside in `scripts/`.

### 3. The Data Model: Source of Truth vs. Derived Data

The system's data is divided into two categories, which is critical for understanding how to repair or regenerate the corpus.

*   **Source of Truth (The "Negatives"):** This data is considered canonical and irreplaceable.
    *   `work/parser/done/*.html`: The raw, unmodified HTML content fetched from the server.
    *   `work/metadata/*.meta.json`: The metadata (source URL, fetch timestamp) associated with each HTML file. Each metadata file's name matches its corresponding HTML file's slug.

*   **Derived Data (The "Build Output"):** This data can be completely regenerated from the Source of Truth.
    *   `work/3-processed/`: The final, user-facing Markdown files and image assets.
    *   `work/completed-post-ids.log`: The canonical log of all completed article slugs. This is an index for fast deduplication.
    *   `work/url-to-id.map`: A lookup map connecting all known URL variations to their canonical slug. This is also an index for deduplication.

### 4. The Pipeline Stages in Detail

#### Stage 1: The `job` Stage
*   **Input:** A `.yaml` file in `work/job/pending/`.
*   **Function:** Translates a high-level request (e.g., "all articles by this author") into a list of URLs. It intelligently filters this list against `work/completed-post-ids.log` to avoid creating work for articles that are already complete.
*   **Output:** A `.txt` file containing a list of new URLs, written to `work/fetch/pending/`.

#### Stage 2: The `fetch` Stage
*   **Input:** A `.txt` file from `work/fetch/pending/`.
*   **Function:** Intelligently fetches the HTML for each URL. It performs a series of checks to avoid redundant work:
    1.  It first checks if an HTML file with the corresponding slug already exists in `work/parser/pending/` (from a previously interrupted run).
    2.  If not, it checks the `url-to-id.map` and `completed-post-ids.log` to see if the article is already complete.
    3.  Only if the article is truly new does it perform the full, expensive browser fetch.
*   **Output (Decoupled):**
    1.  The `.html` file is written to `work/parser/pending/`.
    2.  The `.meta.json` file is written to the central `work/metadata/` directory.

#### Stage 3: The `parser` Stage
*   **Input:** An `.html` file from `work/parser/pending/`.
*   **Function:** The core extraction engine. It reads the `.html` file, then looks up its corresponding `.meta.json` file in the central `work/metadata/` directory to get the necessary context (like the `source-url`).
*   **Output:**
    1.  The final `.md` file and image assets are written to `work/3-processed/`.
    2.  The system's "memory" is updated by writing to `completed-post-ids.log` and `url-to-id.map` via the `tract.db` namespace.

### 5. Namespace Breakdown (The Code Map)

*   `tract.core`: The master orchestrator; runs each stage in order.
*   `tract.pipeline`: Common utilities for the file-based workflow (`get-pending-files`, etc.).
*   `tract.config`: Reads `config.edn` and provides centralized path management functions.
*   `tract.db`: Handles all read/write operations for the system's "database" files (completion logs, URL maps, ignore lists).
*   `tract.io`: Handles all other side-effects, like throttled HTTP requests and writing final article files.
*   `tract.parser`: (Logic) Pure functions for parsing an HTML string into structured Clojure data.
*   `tract.compiler`: (Logic) Pure functions for "compiling" parsed data into a final Markdown string and image jobs.
*   `tract.util`: (Logic) Small, pure helper functions (generating keys, canonicalizing URLs, etc.).
*   `tract.stages.*`: The runners and specific logic for each pipeline stage.
*   `scripts.*`: Standalone administrative or repair tools.

### 6. The Graveyard: Lessons Learned & Paths Not Taken

*   **The Kebab-Case Imperative:** The single biggest source of bugs during development was the inconsistent handling of keywords. Data from external sources (YAML, JSON) is parsed into `snake_case` keywords (e.g., `:source_url`), but idiomatic Clojure code uses `kebab-case` variables (e.g., `source-url`). Failure to consistently manage this conversion led to numerous `ClassCastException` errors and data corruption. **All future code must be vigilant about this distinction.**

*   **The Numeric Post ID Dead End:** The initial design assumed a numeric "Post ID" could be reliably extracted. This proved false. The final, correct design uses the URL slug from the canonical link as the true identifier, which is more robust and universally available.

*   **The Atomicity Problem & Solution:** The original design, where the `fetch` stage wrote two files (`.html` and `.meta`) to the same directory, was not atomic and led to data corruption (orphaned files). The final, superior design decouples them: the state-of-work is represented only by the `.html` file's location, while the metadata is written to a permanent, central store. This resolved the atomicity issue elegantly without requiring a database.

*   **Corpus Repair Philosophy:** When repairing the corpus, a two-step `verify -> repair` process is mandatory. Always build a read-only verification script first to get an accurate report of the system's state before attempting any destructive changes.
