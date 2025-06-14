# `tract`: A Resilient Content Extraction Pipeline

> **Note:** `tract` is a tool for developers and power users. It requires comfort with the command line and managing a multi-step workflow. For a simple, one-click archiving tool, browser extensions like SingleFile may be more suitable.

`tract` is a command-line application for extracting content from web articles, primarily targeting Substack publications. It is designed to be a robust, resilient, and extensible pipeline that converts articles into clean, LLM-ingestible formats: a Markdown file for text and a well-organized collection of all associated images and metadata.

It is built on a philosophy of simple, independent stages that communicate via the filesystem, making the entire process transparent, debuggable, and fault-tolerant.

## Table of Contents
- [Project Philosophy](#project-philosophy)
- [Core Features](#core-features)
- [Getting Started](#getting-started)
- [Core Workflow](#core-workflow)
- [The `tract` Toolkit](#the-tract-toolkit)
- [For Users & Developers](#for-users--developers)
- [Limitations & Important Considerations](#limitations-and-important-considerations)

## Project Philosophy

The design of `tract` is guided by a few key principles:

-   **Resilience over Speed:** The entire system is a file-based pipeline (`job` -> `fetch` -> `parser`). If a step fails on one article, it is moved to an `error` directory, and the rest of the batch continues processing. The system is designed to be stopped and restarted safely at any time.
-   **Transparency:** Every stage reads and writes files to the `work/` directory. You can inspect the state of the pipeline at any time by simply looking at the files.
-   **Testability:** Each stage can be tested in isolation by manually placing files in its `pending` directory.

## Core Features

-   **File-Based Pipeline:** A robust, multi-stage architecture that is fault-tolerant and easy to debug.
-   **Authenticated Fetching:** Uses a persistent, pre-authenticated browser session to access subscriber-only content and appear as a legitimate user, minimizing the risk of being blocked.
-   **Efficient Processing:** Maintains a log of completed articles (`work/completed.log`) and automatically skips fetching or processing any article it has already successfully completed.
-   **Article Discovery:** Includes a powerful discovery tool (`make discover`) that scans processed articles for links to new, undiscovered content from authors you already follow.
-   **Curated Corpus Growth:** By default, discovery is restricted to domains you have already processed, preventing uncontrolled "domain drift." An `--expand` flag is available to discover articles from new domains.
-   **Corpus Pruning:** Includes a utility (`make prune`) to retroactively remove all data associated with domains you've added to an ignore list.
-   **Polite Throttling & Error Handling:** Implements randomized delays and exponential backoff for rate-limiting errors to be a good web citizen.

## Getting Started

`tract` is a Clojure application and requires Java and the Clojure CLI tools.

#### 1. Prerequisites
-   **Java Development Kit (JDK):** Version 11 or higher.
-   **Clojure CLI:** Follow the official instructions at [clojure.org](https://clojure.org/guides/getting_started).
-   **Google Chrome:** The browser itself must be installed.

#### 2. Installation
```bash
git clone https://github.com/your-repo/tract.git
cd tract
```

#### 3. Configuration
The project is configured via `config.edn`. A sample file is provided.
```bash
# Create your personal configuration from the example
cp config.edn.example config.edn
```
> **Note:** I am assuming we will create a `config.edn.example` file based on our `config.edn`. This is a standard best practice.

#### 4. Authentication (One-Time Setup)
`tract` connects to a browser you launch and control. Run this command in a separate terminal:
```bash
make chrome
```
A new Chrome window will open. In this window, **manually log into your Substack account**. You can then minimize this window and leave it running. `tract` will connect to it automatically.

## Core Workflow

The typical way to use `tract` is a cyclical process of running the pipeline and discovering new content.

**1. Onboard a New Author:**
To start, you must manually tell `tract` about an author. Create a `.yaml` file in `work/job/pending/`. For example:
```yaml
# file: work/job/pending/add-author.yaml
author: "www.mind-war.com"
```

**2. Run the Pipeline:**
This command will process your job file, fetch all articles from the author's feed, and parse them into the final format.
```bash
make run
```

**3. Discover New Articles:**
Once the initial run is complete, ask `tract` to find new articles linked from the content you just downloaded.
```bash
make discover
```
This will create a new job file in `work/job/pending/` containing only new articles from authors you already follow.

**4. Run the Pipeline Again:**
Simply run `make run` again. The pipeline will automatically find the new job file created by the discovery tool and process it.

This `run -> discover -> run` cycle can be repeated to incrementally grow your corpus.

## The `tract` Toolkit

The `Makefile` provides commands for all major operations:

| Command         | Description                                                                                             |
| --------------- | ------------------------------------------------------------------------------------------------------- |
| `make run`      | Runs the entire data processing pipeline.                                                               |
| `make discover` | Scans for new articles and creates a new job file. Use `clj -M:discover --expand` to find new authors.    |
| `make prune`    | **(Dry Run)** Reports which files would be deleted based on `ignored-domains.txt`.                      |
| `make test`     | Runs the project's unit tests.                                                                          |
| `make chrome`   | Launches the persistent Chrome browser for `tract` to connect to.                                       |

There are also one-time utility scripts in the `scripts/` directory for migrating data between versions. See the User Manual for details.

## For Users & Developers

This README provides a high-level overview. For more detailed information, please see:

-   **[USAGE.md](./USAGE.md): The User Manual.** For a detailed guide on configuration, all command-line options, and managing your corpus.
-   **[DESIGN.md](./docs/DESIGN.md): The Developer's Guide.** For a deep dive into the software architecture, namespace responsibilities, and contribution guidelines.

## Limitations and Important Considerations

-   **User Responsibility:** You are responsible for using this tool ethically and in compliance with website terms of service. `tract` is a powerful tool; use it respectfully.
-   **Parser Specificity:** The parser is heavily optimized for Substack. Its performance on other sites may vary.
-   **Environmental "Hangs":** As noted in development, the connection to the browser can occasionally become stale. Our robust error handling will detect this, exit gracefully, and inform you. The solution is to restart the browser with `make chrome` and re-run the pipeline.



