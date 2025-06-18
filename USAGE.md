Of course. A clear, comprehensive `USAGE.md` is essential for making the project accessible to its intended users. This document is written for an end user whose goal is to use the application, not necessarily to understand its internal workings.

Here is a complete `USAGE.md` file that reflects the current, robust state of the `tract` application.

---

# `tract` User Manual

This document is the primary user guide for operating the `tract` application. It covers setup, configuration, the standard operational workflow, and how to manage your growing content corpus.

For a high-level overview of the project's philosophy or a deep dive into its software architecture, please see the [README.md](./README.md) or [DESIGN.md](./docs/DESIGN.md) respectively.

## Table of Contents
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation & Configuration](#installation--configuration)
  - [One-Time Browser Authentication](#one-time-browser-authentication)
- [The Core Workflow: A Step-by-Step Guide](#the-core-workflow-a-step-by-step-guide)
- [Command Reference](#command-reference)
  - [Primary Makefile Commands](#primary-makefile-commands)
  - [Advanced CLI Flags](#advanced-cli-flags)
- [Corpus Management](#corpus-management)
  - [Adding New Authors](#adding-new-authors)
  - [Ignoring Domains](#ignoring-domains)
  - [Pruning Existing Data](#pruning-existing-data)
- [The `work/` Directory: Understanding the Filesystem](#the-work-directory-understanding-the-filesystem)
- [A Note on Responsible Use](#a-note-on-responsible-use)

## Getting Started

### Prerequisites
-   **Java Development Kit (JDK):** Version 11 or higher.
-   **Clojure CLI:** Follow the official instructions at [clojure.org](https://clojure.org/guides/getting_started).
-   **Google Chrome:** The browser application must be installed.

### Installation & Configuration
1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-repo/tract.git
    cd tract
    ```
2.  **Create your configuration:**
    ```bash
    cp config.edn.example config.edn
    ```
    Now, open `config.edn` and review the settings. The default values are sensible, but you can customize them if needed.

### One-Time Browser Authentication
`tract` uses a live browser session that you control to fetch content. This allows it to access subscriber-only articles and appear as a regular user.

This is a one-time setup step per session. In a dedicated terminal window, run:
```bash
make chrome
```
A new Chrome window will open. In this window, **manually log into your Substack account** and any other sites you wish to scrape. You can then minimize this window and leave it running in the background. `tract` will connect to it automatically.

## The Core Workflow: A Step-by-Step Guide

The typical usage of `tract` is a cycle of processing known content and then discovering new content linked from within it.

#### Step 1: Onboard a New Author (The Seed)
To begin, you must tell `tract` about at least one author. Create a `.yaml` file in the `work/job/pending/` directory. The pipeline will process any `.yaml` file it finds here.

**Example: `work/job/pending/add-new-author.yaml`**
```yaml
# Fetch all articles from this author's RSS feed.
# This works for standard *.substack.com domains and custom domains.
author: "www.thebulwark.com"
```

#### Step 2: Run the Pipeline
Execute the main pipeline. It will find your job file, fetch any new articles, and parse them into the final format.
```bash
make run
```
After this completes, the `work/3-processed/` directory will contain your first set of articles.

#### Step 3: Discover New Articles
Now, ask `tract` to scan the articles you just processed for links to new, un-fetched articles. By default, it will only find new articles from authors already in your corpus.
```bash
make discover
```
This will create a new job file (e.g., `discovery-job-....yaml`) in `work/job/pending/`.

#### Step 4: Run the Pipeline Again (The Cycle)
Simply run the pipeline again:
```bash
make run
```
It will automatically find the new job created by `discover` and process it. This `run -> discover -> run` cycle is the primary method for growing your corpus over time.

## Command Reference

The `Makefile` provides simple, memorable commands for all major operations.

### Primary Makefile Commands

| Command         | Description                                                                                             |
| --------------- | ------------------------------------------------------------------------------------------------------- |
| `make run`      | Runs the entire `job -> fetch -> parser` data processing pipeline. This is the main command you will use. |
| `make discover` | Scans for new articles from known authors and creates a new job file in `work/job/pending/`.             |
| `make prune`    | **(Dry Run)** Reports which files would be deleted based on `ignored-domains.txt`, without touching them. |
| `make test`     | Runs the project's unit tests to verify core functionality.                                             |
| `make chrome`   | Launches the persistent Chrome browser for `tract` to connect to for fetching.                          |

### Advanced CLI Flags

For more control, you can invoke the Clojure runner directly with flags.

| Command                           | Description                                                                                                                             |
| --------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `clj -M:discover --expand`        | Runs the discovery tool in "expand" mode. This allows it to find and queue articles from **new domains** not yet in your corpus.        |
| `clj -M:prune-ignored --force`    | Runs the prune utility and **permanently deletes** the files identified in the dry run. Use with extreme caution.                           |

## Corpus Management

You have several tools to control what content is added to and removed from your collection.

### Adding New Authors
Create a new `.yaml` file in `work/job/pending/` with an `author:` key, as described in the Core Workflow.

### Ignoring Domains
To permanently prevent `tract` from discovering or processing content from specific domains, add them to `ignored-domains.txt` (one domain per line).

### Pruning Existing Data
If you add a domain to `ignored-domains.txt` and want to remove content you've *already* collected from that domain, use the `prune` tool.

1.  **Dry Run (Always do this first):**
    ```bash
    make prune
    ```
    This will print a list of all files that would be deleted, without touching anything.

2.  **Execute Deletion:**
    Once you have reviewed the list and are certain, run the command with the `--force` flag:
    ```bash
    clj -M:prune-ignored --force
    ```
    This will permanently delete the associated `.md` and `.html` files, and will also remove all associated records from the system's logs.

## The `work/` Directory: Understanding the Filesystem

The `work/` directory is the heart of the pipeline. Its structure is designed for transparency.

-   `work/`
    -   `3-processed/`: **Final Output Directory.** Contains the final, clean `.md` files and associated image assets for every successfully processed article.
    -   `metadata/`: A central store of all `.meta.json` files, containing the source URL and fetch time for every fetched article.
    -   `parser/done/`: An archive of the raw `.html` source files for every successfully processed article. This is a core part of the "source of truth" for your corpus.
    -   `job/`, `fetch/`, `parser/`: These directories contain `pending/`, `done/`, and `error/` subdirectories that show the state of the pipeline.
    -   `completed-post-ids.log`: The canonical log of all completed article slugs (the system's primary "memory").
    -   `url-to-id.map`: A lookup map connecting known URLs to their canonical slug.

## A Note on Responsible Use
`tract` is a powerful tool for personal archiving. You are responsible for using it ethically and in compliance with the terms of service for any website you scrape. Be a good web citizen: do not scrape excessively, and respect paywalls and copyrighted content.
