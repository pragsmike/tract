## `tract`: A Developer's Guide & Design Document

**Version:** 2.0
**Status:** Stable. Major architectural refactoring complete.

### 1. Introduction

`tract` is a command-line application designed to extract content from web articles, primarily targeting Substack publications. Its purpose is to convert web articles into clean, LLM-ingestible formats: a Markdown file for the text content and a well-organized collection of all associated images and their metadata.

This document serves as the primary **technical guide** for developers working on `tract`. It details the final, stable architecture, core design principles, and crucial lessons learned during its development. For a user-focused guide on how to operate the application, please see **[USAGE.md](../USAGE.md)**.

### 2. Core Philosophy & Architecture

The design of `tract` is guided by a few key principles that are idiomatic to functional programming and robust system design.

#### 2.1. The Immutable Core & Symlink Pipeline

The workflow is a series of independent stages that communicate through the filesystem. The core of this design is the separation of permanent assets from transient work tokens.

*   **Immutable Assets (The "Source of Truth"):**
    *   `work/html/`: The permanent, canonical archive of all raw `.html` files.
    *   `work/metadata/`: The permanent, canonical archive of all `.meta.json` files.
    *   Once written by the `fetch` stage, files in these directories are **never moved or modified**.

*   **The Symlink Pipeline:**
    *   The `parser` stage does not operate on the real HTML files. Instead, it processes **symlinks** that point to the files in `work/html/`. These symlinks act as lightweight "work tokens."

*   **Workflow:**
    *   `work/job/pending` -> **Job Stage** -> `work/fetch/pending`
    *   `work/fetch/pending` -> **Fetch Stage** -> `work/html/` & `work/metadata/` & creates symlink in `work/parser/pending/`
    *   `work/parser/pending/` (symlinks) -> **Parser Stage** -> `work/3-processed/` (final output) & moves symlinks to `work/parser/done/`

**Why this approach?**
*   **Data Integrity:** The raw source files are protected from accidental modification or deletion by later pipeline stages.
*   **Resilience:** If a stage fails, only the lightweight symlink is moved to an `error` directory, leaving the master asset untouched.
*   **Testability & Debuggability:** The state of the pipeline is transparent. We can test the parser by manually creating a symlink in its `pending` directory.

#### 2.2. The Canonical ID: The URL Slug

The entire system's identity management is built on a single, simple concept: the **URL Slug is the Canonical ID**.

*   **Derivation:** The slug (e.g., `my-great-article`) is extracted from the `<link rel="canonical">` tag within an article's HTML.
*   **Uniqueness:** This slug is the system's unique identifier for a piece of content, stored as `:post-id` in the database logs.
*   **File Naming:** The slug directly determines the filenames for all major assets, making them trivially discoverable:
    *   HTML File: `work/html/my-great-article.html`
    *   Metadata File: `work/metadata/my-great-article.html.meta.json`
    *   Markdown File: `work/3-processed/my-great-article.md`

#### 2.3. Separation of Concerns (Namespaces)

The codebase is strictly organized into namespaces, each with a single, clear responsibility.
*   **Logic vs. Orchestration:** We separate the pure data-transformation *logic* (e.g., `tract.parser`, `tract.compiler`) from the *orchestration* of the pipeline stages (`tract.stages.*`).
*   **Pure vs. Impure Functions:** We isolate side-effects (file writing, network calls) into specific namespaces (`tract.io`, `tract.db`). Core logic is pure and easily testable.
*   **Clojure Convention:** Functions that perform side-effects have a trailing `!` (e.g., `write-article!`).

### 3. The Pipeline Stages in Detail

#### Stage 1: The `job` Stage
*   **Runner:** `tract.stages.job/run-stage!`
*   **Function:** Translates a high-level request (e.g., "all articles by this author") into a concrete list of URLs. It filters this list against `work/completed-post-ids.log` (which contains slugs) to prevent re-processing.
*   **Output:** A `.txt` file containing a list of new URLs, written to `work/fetch/pending/`.

#### Stage 2: The `fetch` Stage
*   **Runner:** `tract.stages.fetch/run-stage!`
*   **Function:** The most critical stage. For each URL:
    1.  Downloads the full HTML to a temporary file in `work/html/tmp/`.
    2.  On success, atomically renames the temp file to its permanent location, `work/html/[slug].html`.
    3.  Writes a corresponding metadata file to `work/metadata/[slug].html.meta.json`.
    4.  Creates a relative **symlink** in `work/parser/pending/[slug].html` that points to the new file in `work/html/`.
*   **Output:** Permanent files in `work/html/` and `work/metadata/`, and a symlink in `work/parser/pending/`.

#### Stage 3: The `parser` Stage
*   **Runner:** `tract.stages.parser/run-stage!`
*   **Input:** A symlink in `work/parser/pending/`.
*   **Function:** Reads the HTML content *through* the symlink. Orchestrates `parser`, `compiler`, and `io` namespaces to generate the final Markdown and download images.
*   **Output:** The final artifacts (`.md` file, images) in `work/3-processed/`. Upon success, it moves the **symlink** to `work/parser/done/` and records the slug in the completion logs.

### 4. On-Disk Data Structure

The `work/` directory is the heart of the pipeline. Its structure is:

*   `work/`
    *   `3-processed/`: Final, clean `.md` files and associated image assets.
    *   `html/`: **Permanent archive** of all raw `.html` source files.
        *   `tmp/`: Temporary storage for in-flight downloads.
    *   `metadata/`: **Permanent archive** of all `.meta.json` source files.
    *   `parser/`: Contains transient "work token" directories.
        *   `done/`: Contains **symlinks** to HTML files in `work/html/` that have been successfully parsed.
        *   `pending/`: Contains symlinks for files waiting to be parsed.
        *   `error/`: Contains symlinks for files that failed parsing.
    *   `completed-post-ids.log`: The canonical log of all completed article slugs.
    *   `url-to-id.map`: A lookup map connecting known URLs to their canonical slug.

### 5. The "Graveyard": Final Lessons Learned & Design Rationale

This section documents the critical lessons learned from past failures. These are not suggestions; they are core design constraints.

*   **The Kebab-Case Imperative:** This is the project's most important rule. External data sources (JSON, YAML) may produce keys in other formats, but all Clojure-facing map access **MUST** use `kebab-case` keywords (e.g., `:source-url`). The AI assistant's predecessor repeatedly failed on this point, causing numerous `nil` pointer bugs. There are no exceptions.

*   **The Slug as the Only Canonical ID:** The project initially pursued using an internal, numeric Substack Post ID. This was a dead end; the ID was not reliably present in all articles. The pivot to using the user-facing URL slug (from the `<link rel="canonical">` tag) as the system's primary key was the single most important architectural simplification. It made file association predictable and robust.

*   **Java Interop for `varargs` is Fragile:** Early versions of the `fetch` stage and migration scripts were plagued by `ClassCastException` and `IllegalArgumentException` errors. This was caused by naive calls to Java methods with variable arguments (`varargs`), like `Paths/get` and `Files/createSymbolicLink`. **Lesson:** The only robust way to call these from Clojure is to be explicit: use a safe alternative (`Path.relativize`) or pass a correctly-typed empty array (e.g., `(make-array FileAttribute 0)`).

*   **Robust Fallback Logic is Non-Negotiable:** The parser stage used to crash with an `NPE` if it encountered an HTML file without a canonical link tag, because the `:post-id` would be `nil`. **Lesson:** The compiler must have a robust fallback. If the incoming slug is `nil` or blank, it must generate a unique, non-nil key (e.g., using a timestamp) to ensure all subsequent file I/O operations are safe. A `nil` filename is an unrecoverable error.

### 6. How to Extend `tract`

*   **To Add a New Job Type:** Go to `src/tract/stages/job.clj` and add a new clause to the `cond` in `process-job-spec!`.
*   **To Improve Parsing:** Go to `src/tract/parser.clj`. Add fallback logic to the `extract-metadata` and `extract-body-nodes` functions to recognize the new site's structure.
*   **To Add a New Utility:** Create a new main namespace in the `scripts/` directory. Add a corresponding alias to `deps.edn` and a command to the `Makefile`. Ensure it respects the immutable core/symlink architecture.
