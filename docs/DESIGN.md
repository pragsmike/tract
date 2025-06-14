## `tract`: A Developer's Guide & Design Document

**Version:** 1.2
**Status:** Core Engine Complete, Canonical ID Refactor Complete

### 1. Introduction

`tract` is a command-line application designed to extract content from web articles, primarily targeting Substack publications. Its purpose is to convert web articles into clean, LLM-ingestible formats: a Markdown file for the text content and a collection of all associated images and their metadata.

The system is built as a robust, resilient, file-based pipeline, capable of handling both public and authenticated (subscriber-only) content. It is designed to be polite by throttling its network requests and to be extensible for supporting new sources and features in the future.

This document serves as the primary **technical guide** for developers working on `tract`. For a user-focused guide on how to operate the application, please see **[USAGE.md](../USAGE.md)**.

### Project Design Documents

This document describes the core architecture. For detailed designs of specific, standalone components, please see:

*   **[This Document](./DESIGN.md):** The core architecture, philosophy, and pipeline design.
*   **[Article Discovery](./DESIGN-discovery.md):** The design of the `discover` tool.
*   **[Corpus Pruning](./DESIGN-prune.md):** The design of the `prune` tool.

### 2. Core Philosophy & Architecture

The design of `tract` is guided by a few key principles that are idiomatic to functional programming and robust system design.

#### 2.1. The File-Based Pipeline

The entire workflow is modeled as a series of independent stages that communicate through the filesystem. Each stage reads files from a `pending` directory, processes them, and writes its output to the `pending` directory of the *next* stage.

*   `work/job/pending` -> **Job Stage** -> `work/fetch/pending`
*   `work/fetch/pending` -> **Fetch Stage** -> `work/parser/pending`
*   `work/parser/pending` -> **Parser Stage** -> `work/3-processed/` (Final Output)

**Why this approach?**
*   **Resilience:** If one stage fails while processing a file, it moves the file to an `error` directory without halting the entire system. The rest of the batch can continue.
*   **Testability & Debuggability:** We can manually inspect the output of any stage by looking at the files in the next stage's `pending` directory. We can also manually place files to test a specific stage in isolation.
*   **Idempotency:** A stage can be re-run safely. It will only process files that are currently in its `pending` directory.

#### 2.2. Separation of Concerns (Namespaces)

The codebase is strictly organized into namespaces, each with a single, clear responsibility. This makes the system easier to understand, maintain, and extend.

*   **Logic vs. Orchestration:** We separate the pure data-transformation *logic* (e.g., parsing HTML, compiling Markdown) from the *orchestration* of the pipeline stages.
*   **Pure vs. Impure Functions:** We isolate side-effects (like writing files or making network calls) into specific namespaces (`tract.io`, `tract.db`). The core transformation logic (`tract.compiler`) is a set of pure functions that simply take data and return new data, making it extremely easy to unit test.
*   **Clojure Convention:** Functions that perform side-effects are named with a trailing `!` (e.g., `write-article!`, `download-image!`). This is a community convention that signals to the developer that the function interacts with the outside world.

### 3. The Pipeline Stages in Detail

The pipeline is coordinated by the main function in `tract.core`, which runs each stage in sequence.

#### Stage 1: The `job` Stage
*   **Runner:** `tract.stages.job/run-stage!`
*   **Input:** A `.yaml` file in `work/job/pending/`.
*   **Function:** Translates a high-level request (e.g., "all articles by this author") into a concrete list of URLs. It uses a throttled HTTP client (`clj-http-lite`) to efficiently fetch and parse Atom/RSS feeds. **It filters this list against `work/completed-post-ids.log` to ensure articles are not re-processed.**
*   **Output:** A `.txt` file containing a list of new URLs, written to `work/fetch/pending/`.

#### Stage 2: The `fetch` Stage
*   **Runner:** `tract.stages.fetch/run-stage!`
*   **Input:** A `.txt` file from `work/fetch/pending/`.
*   **Function:** Intelligently fetches the full HTML content for each URL. It first performs a lightweight check against `work/url-to-id.map` and `HEAD` requests to see if the article has already been completed. Only if the article is new does it use a persistent, authenticated browser session to perform the full, expensive fetch.
*   **Output:** One `.html` file per URL and a corresponding `.html.meta` companion file, written to `work/parser/pending/`.

#### Stage 3: The `parser` Stage
*   **Runner:** `tract.stages.parser/run-stage!`
*   **Input:** An `.html` file from `work/parser/pending/`.
*   **Function:** The core extraction engine. It orchestrates the `parser`, `compiler`, and `io` namespaces to perform the full extraction.
*   **Output:** The final artifacts (`.md` file, images, and `.json` metadata) written to the `work/3-processed/` directory. **Upon successful processing, it records the article's canonical ID and URL mappings via the `tract.db` namespace.**

### 4. Namespace Breakdown (The Code Map)

*   `tract.core`: The master orchestrator. Its `-main` function initializes the pipeline, manages the browser driver lifecycle, and runs each stage in order.
*   `tract.pipeline`: Provides the common utility functions for the file-based workflow (`get-pending-files`, `move-to-done!`, `move-to-error!`, etc.).
*   `tract.config`: Safely reads the `config.edn` file and provides access to configuration values with sensible defaults.

*   `tract.stages.job`: The runner for the job stage.
*   `tract.stages.fetch`: The runner for the fetch stage.
*   `tract.stages.parser`: The runner for the parser stage.

*   `tract.parser`: (Logic) Contains pure functions for parsing an HTML string into structured Clojure data (`{:metadata ... :body-nodes ...}`).
*   `tract.compiler`: (Logic) The pure, unit-tested core of the application. Takes the data from the parser and "compiles" it into a final map containing the complete Markdown string (with YAML front matter) and a list of image-processing jobs.
*   `tract.io`: (Side-Effects) The only place where we interact with the network for non-browser requests or write final files.
*   `tract.db`: (Side-Effects) The namespace responsible for reading from and writing to the system's "database" files (`completed-post-ids.log`, `url-to-id.map`).
*   `tract.util`: (Logic) A collection of small, pure helper functions for generating keys, canonicalizing URLs, and manipulating paths.

### 5. How to Run the Application

The `tract` application is operated via a set of `make` commands that provide a simple interface to the underlying Clojure tools. For a detailed guide on setup, configuration, and day-to-day operation, please see the **[USAGE.md](./USAGE.md)** user manual.

### 6. The "Graveyard": Lessons Learned & Paths Not Taken

Our development process was iterative and involved several dead ends. These are documented here so that future developers can understand the "why" behind our final design and avoid repeating these mistakes.

*   **Problem: Brittle Login Automation vs. Anti-Bot**
    *   **Path Taken:** We initially tried to fully automate the login flow. This repeatedly failed because Substack's anti-bot layer (Kasada) is designed to detect exactly this kind of headless automation.
    *   **Lesson & Final Solution:** The final architecture, which **connects to a pre-authenticated, persistent browser session**, is vastly more robust, simpler to code, and more reliable.

*   **Problem: `etaoin` Driver Lifecycle & Zombie Processes**
    *   **Path Taken:** We first tried creating and destroying a new browser instance for every single fetch. In the WSL2 environment, this led to orphaned `chromedriver` processes.
    *   **Lesson & Final Solution:** The browser driver's lifecycle must be managed at the highest possible level. Our final design creates a single driver in `tract.core/-main`, wraps the entire pipeline run in a `try...finally` block, and guarantees the driver is properly disconnected or killed, no matter what happens.

*   **Problem: AI Assistant API & Syntax Errors**
    *   **Path Taken:** Early development was plagued by compilation errors due to using incorrect or non-existent function names from libraries, or by generating syntactically invalid Clojure code.
    *   **Lesson & Final Solution:** An AI assistant's knowledge can be flawed. The final, working code was achieved through a process of generating proposals, having a human test them rigorously, and iterating on the bug reports. Always trust the compiler over the AI's confidence.

*   **Problem: Manual vs. Library-based Data Serialization**
    *   **Path Taken:** The first version of the Markdown compiler used manual string formatting to create TOML front matter. This was brittle and failed to correctly escape special characters.
    *   **Lesson & Final Solution:** Never build your own serializer for a standard format. The final design uses the `clj-yaml` library to robustly generate YAML front matter, which is guaranteed to be syntactically correct.

*   **Problem: URLs are not Unique; Finding a Canonical ID**
    *   **Path Taken (Dead End #1 - Numeric ID):** The project's primary goal was to solve the problem of duplicate articles appearing under different URLs. The initial design, based on the project brief, specified using an internal, numeric "Substack Post ID" as the canonical identifier. We implemented the entire data model (`completed-post-ids.log`, `url-to-id.map`) and pipeline changes based on this assumption. However, repeated attempts to reliably extract this numeric ID from the final HTML files failed. We searched for it in various `<meta>` tags (e.g., `parsely-post-id`, embedded in `twitter:image` URLs) but could not find a consistent pattern across all articles in the corpus. The migration script's failure to find any IDs proved this approach was a dead end.
    *   **Lesson & Final Solution (The Slug is the ID):** The key insight was that Substack's public, user-facing **URL slug** (e.g., `my-post-slug`) is the true canonical identifier used by their system. The most reliable way to find this is by parsing the `<link rel="canonical">` tag from the article's HTML. This tag provides the definitive, original URL for the content, from which the slug can be extracted. We pivoted the entire implementation to use this slug as the `:post_id`. This approach is simpler, more robust, and works for all Substack content, including those on custom domains.

*   **Problem: Filesystem Duplication due to Canonical IDs**
    *   **Path Taken (Dead End #2 - File-based Deduplication):** A logical consequence of the Canonical ID project was the need for a tool to find and delete duplicate *files* on the filesystem. We assumed that having multiple URLs for the same article would have created multiple `.html` and `.md` files that needed to be pruned. We designed and built a `deduplicate_corpus.clj` script specifically for this purpose.
    *   **Lesson & Final Solution (No Duplicates to Find):** The script repeatedly and correctly reported "No duplicate articles found." A diagnostic tool revealed the truth: the pipeline's existing design already prevented file-level duplication. The `tract.util/url->filename` function creates an HTML filename from the URL's slug. Since two URLs for the same article share the same slug, the `fetch` stage would simply overwrite the first HTML file with the second. Because there is only ever one `.html` file per canonical article, the parser stage would only ever generate one corresponding `.md` file. The deduplication script was unnecessary because the core pipeline was already behaving correctly. This confirmed the soundness of the original fetch-and-compile architecture.

### 7. How to Extend `tract`

The current architecture is designed for extension.

*   **To Add a New Job Type:**
    1.  Go to `src/tract/stages/job.clj`.
    2.  Add a new clause to the `cond` in `process-job-spec!`.
    3.  Implement a new helper function to handle that job type, which should return a list of URLs.

*   **To Improve Parsing for a New Site (e.g., Medium):**
    1.  Go to `src/tract/parser.clj`.
    2.  Add new fallback logic to the `extract-metadata` and `extract-body-nodes` functions to recognize the specific HTML structure of the new site.

*   **To Add a New Standalone Utility (like `discover` or `prune`):**
    1.  Create a new main namespace in the `scripts/` directory.
    2.  Create a new `DESIGN-[utility-name].md` document to describe its purpose and architecture.
    3.  Add a corresponding alias to `deps.edn` and a command to the `Makefile`.




