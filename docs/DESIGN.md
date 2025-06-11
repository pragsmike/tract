## `tract`: A Developer's Guide & Design Document

**Version:** 1.0
**Status:** Core Engine Complete

### 1. Introduction

`tract` is a command-line application designed to extract content from web articles, primarily targeting Substack publications. Its purpose is to convert web articles into clean, LLM-ingestible formats: a Markdown file for the text content and a collection of all associated images and their metadata.

The system is built as a robust, resilient, file-based pipeline, capable of handling both public and authenticated (subscriber-only) content. It is designed to be polite by throttling its network requests and to be extensible for supporting new sources and features in the future.

This document serves as the primary technical guide for developers working on `tract`. It assumes a developer may be new to Clojure and explains the architectural decisions and conventions used throughout the codebase.

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
*   **Pure vs. Impure Functions:** We isolate side-effects (like writing files or making network calls) into a specific namespace (`tract.io`). The core transformation logic (`tract.compiler`) is a set of pure functions that simply take data and return new data, making it extremely easy to unit test.
*   **Clojure Convention:** Functions that perform side-effects are named with a trailing `!` (e.g., `write-article!`, `download-image!`). This is a community convention that signals to the developer that the function interacts with the outside world.

### 3. The Pipeline Stages in Detail

The pipeline is coordinated by the main function in `tract.core`, which runs each stage in sequence.

#### Stage 1: The `job` Stage
*   **Runner:** `tract.stages.job/run-stage!`
*   **Input:** A `.yaml` file in `work/job/pending/`.
*   **Function:** Translates a high-level request (e.g., "all articles by this author") into a concrete list of URLs. It uses a throttled HTTP client (`clj-http-lite`) to efficiently fetch and parse Atom/RSS feeds.
*   **Output:** A `.txt` file containing one URL per line, written to `work/fetch/pending/`.

#### Stage 2: The `fetch` Stage
*   **Runner:** `tract.stages.fetch/run-stage!`
*   **Input:** A `.txt` file from `work/fetch/pending/`.
*   **Function:** Fetches the full HTML content for each URL. This stage uses **`etaoin` to drive a persistent, authenticated browser session**, which is essential for bypassing anti-bot measures and accessing subscriber-only content. It throttles its requests to be polite.
*   **Output:** One `.html` file per URL, written to `work/parser/pending/`.

#### Stage 3: The `parser` Stage
*   **Runner:** `tract.stages.parser/run-stage!`
*   **Input:** An `.html` file from `work/parser/pending/`.
*   **Function:** The core extraction engine. It orchestrates the `parser`, `compiler`, and `io` namespaces to perform the full extraction.
*   **Output:** The final artifacts (`.md` file, images, and `.json` metadata) written to the `work/3-processed/` directory.

### 4. Namespace Breakdown (The Code Map)

*   `tract.core`: The master orchestrator. Its `-main` function initializes the pipeline and runs each stage in order. It also manages the lifecycle of the persistent browser driver.
*   `tract.pipeline`: Provides the common utility functions for the file-based workflow (`get-pending-files`, `move-to-done!`, `move-to-error!`, etc.).
*   `tract.config`: Safely reads the `~/secrets/tract-config.edn` file for authentication credentials.

*   `tract.stages.job`: The runner for the job stage.
*   `tract.stages.fetch`: The runner for the fetch stage. Contains the public `login!` function.
*   `tract.stages.parser`: The runner for the parser stage.

*   `tract.parser`: (Logic) Contains pure functions for parsing an HTML string into structured Clojure data (`{:metadata ... :body-nodes ...}`). Includes fallbacks for non-Substack pages.
*   `tract.compiler`: (Logic) The pure, unit-tested core of the application. Takes the data from the parser and "compiles" it into a final map containing the complete Markdown string and a list of image-processing jobs.
*   `tract.io`: (Side-Effects) The only place where we interact with the network or write final files. Contains `throttled-fetch!`, `write-article!`, and `download-image!`.
*   `tract.util`: (Logic) A collection of small, pure helper functions for generating keys and manipulating paths (`generate-article-key`, `url->local-path`, etc.).

### 5. How to Run the Application

#### 5.1. One-Time Setup: Browser Authentication

`tract` uses a persistent, authenticated browser session to avoid anti-bot systems. This requires a one-time manual setup.

1.  **Launch Chrome in Debugging Mode:** From your terminal (WSL2 in our case), start Chrome with the remote debugging flag.
    ```bash
    # Adjust path as needed for your Windows system
    /mnt/c/Program\ Files/Google/Chrome/Application/chrome.exe --remote-debugging-port=9222
    ```
2.  **Manually Log In:** A new Chrome window will open. In this window, navigate to `substack.com` and log in with the account you want to use for scraping. Solve any CAPTCHAs that appear.
3.  **Leave it Running:** You can minimize this browser window. `tract` will connect to it whenever it runs. You only need to do this once per login session (i.e., every few days or weeks).

#### 5.2. Running a Job

1.  Create a `.yaml` jobspec file in `work/job/pending/`.
2.  From the project root, run `clj -M:run` or `make`.
3.  The pipeline will execute, and final artifacts will appear in `work/3-processed/`.

### 6. The "Graveyard": Lessons Learned & Paths Not Taken

Our development process was iterative and involved several dead ends. These are documented here so that future developers can understand the "why" behind our final design and avoid repeating these mistakes.

*   **Problem: Brittle Login Automation vs. Anti-Bot**
    *   **Path Taken:** We initially tried to fully automate the login flow by finding and filling the email/password fields. This repeatedly failed because Substack's anti-bot layer (Kasada) is designed to detect exactly this kind of headless automation. It would not display the password field, or it would present a CAPTCHA.
    *   **Lesson & Final Solution:** Automating a login against a modern, protected site is extremely fragile. The final architecture, which **connects to a pre-authenticated, persistent browser session**, is vastly more robust, simpler to code, and more reliable.

*   **Problem: `etaoin` Driver Lifecycle & Zombie Processes**
    *   **Path Taken:** We first tried creating and destroying a new browser instance for every single fetch. In the WSL2 environment, if the script crashed, the background `chromedriver` process was orphaned, leading to a "user data directory is already in use" error on the next run.
    *   **Lesson & Final Solution:** The browser driver's lifecycle must be managed at the highest possible level. Our final design creates a single driver in `tract.core/-main`, wraps the entire pipeline run in a `try...finally` block, and guarantees the driver is properly disconnected (`e/quit`) no matter what happens.

*   **Problem: Clojure/`etaoin` API & Syntax Errors**
    *   **Path Taken:** Early development was plagued by compilation errors due to using incorrect or non-existent function names from the `etaoin` library (`e/fill-by-keys`, `e/find-element`, etc.).
    *   **Lesson & Final Solution:** An AI assistant's knowledge of a specific library's API can be faulty. The final, working code was achieved by consulting the official documentation and correcting the function calls (`e/chrome` with `:debuggerAddress`, using `e/wait` and `e/fill-el` for typing). Always trust the compiler and verify with official docs.

*   **Problem: Brittle Parser Logic**
    *   **Path Taken:** The initial parser was designed only for Substack's structure. When it encountered a non-Substack page or a server error page, it returned `nil` for metadata fields, causing `NullPointerException`s downstream.
    *   **Lesson & Final Solution:** A robust parser must be defensive. The final parser now has multiple fallbacks (JSON-LD -> Meta Tags -> `<title>`) and the compiler has last-resort fallbacks for generating keys (e.g., using a timestamp). This ensures the pipeline can process any valid HTML file without crashing.

### 7. How to Extend `tract`

The current architecture is designed for extension.

*   **To Add a New Job Type:**
    1.  Go to `src/tract/stages/job.clj`.
    2.  Add a new clause to the `cond` in `process-job-spec!`.
    3.  Implement a new helper function to handle that job type, which should return a list of URLs.

*   **To Improve Parsing for a New Site (e.g., Medium):**
    1.  Go to `src/tract/parser.clj`.
    2.  Add new fallback logic to the `extract-metadata` and `extract-body-nodes` functions to recognize the specific HTML structure of the new site.

*   **To Add a New Pipeline Stage (e.g., a "Notifier"):**
    1.  Create `src/tract/stages/notifier.clj` with a `run-stage!` function.
    2.  Update `tract.core` to add `:notifier` to the `stages` vector and call its `run-stage!` function at the end of the pipeline.

*   **To Add a Caching Proxy:**
    1.  The place to configure this would be in `tract.core`, where the `etaoin` driver is created, and in `tract.io`, where the `clj-http-lite` calls are made. Both tools would need to be configured to use the proxy address.
