# `tract` User Manual

This document is the primary user guide for operating the `tract` application. It covers configuration, the standard operational workflow, command-line tools, and how to manage your growing content corpus.

For a high-level overview of the project or information on software architecture, please see the [README.md](./README.md) or [DESIGN.md](./DESIGN.md) respectively.

## Table of Contents
- [Configuration (`config.edn`)](#configuration-configedn)
- [The Core Workflow: A Step-by-Step Guide](#the-core-workflow-a-step-by-step-guide)
- [Command Reference](#command-reference)
  - [Primary Makefile Commands](#primary-makefile-commands)
  - [Advanced CLI Commands & Flags](#advanced-cli-commands--flags)
  - [Utility & Migration Scripts](#utility--migration-scripts)
- [The `work/` Directory: A File System Map](#the-work-directory-a-file-system-map)
- [Corpus Management](#corpus-management)

## Configuration (`config.edn`)

All `tract` behavior is controlled by the `config.edn` file in the project's root directory. When setting up the project, it's recommended to copy the `config.edn.example` to `config.edn` and modify it as needed.

Below is a breakdown of all available configuration keys.

```edn
{
 ;; --- General Settings ---
 ;; The root directory for all pipeline operations.
 :work-dir "work"

 ;; --- Browser & Fetching ---
 ;; The address of the pre-launched Chrome browser in remote debugging mode.
 ;; This must match the port used in the `make chrome` command.
 :browser-debugger-address "127.0.0.1:9222"

 ;; --- Stage-specific Settings ---

 ;; Configuration for the main article fetch stage (using the browser)
 :fetch-stage {
               ;; Base wait time in milliseconds before each fetch.
               :throttle-base-ms 2500
               ;; Random additional wait time (0 to this value).
               :throttle-random-ms 2000
               ;; Max number of retries for a single URL if the server
               ;; returns a "Too Many Requests" error.
               :max-retries 5
               }

 ;; Configuration for simple HTTP requests (feeds, images)
 :http-client {
               ;; Base wait time for feed fetches.
               :throttle-base-ms 2000
               ;; Random additional wait time for feed fetches.
               :throttle-random-ms 1500
               }
}
```

## The Core Workflow: A Step-by-Step Guide

The typical usage of `tract` is a cycle of processing known content and then discovering new content.

#### Step 1: Launch the Authenticated Browser

This is a one-time setup step per session. In a separate terminal, run:
```bash
make chrome
```
A new Chrome window will open. In this window, **manually log into your Substack account**. You can then minimize this window and leave it running. `tract` will connect to it automatically.

#### Step 2: Onboard a New Author (The Seed)

To begin, you must tell `tract` about at least one author. Create a `.yaml` file in `work/job/pending/`. The pipeline will process any `.yaml` file it finds here.

**Example: `work/job/pending/new-author.yaml`**
```yaml
# Fetch all articles from this author's RSS feed.
# This works for standard *.substack.com domains and custom domains.
author: "www.mind-war.com"
```

#### Step 3: Run the Pipeline

Execute the main pipeline. It will find your job file, fetch the articles, and parse them into the final format.
```bash
make run
```
After this completes, the `work/3-processed/` directory will contain your first set of articles, and `work/completed.log` will be populated.

#### Step 4: Discover New Articles

Now, ask `tract` to scan the articles you just processed for links to new articles from authors you already follow.
```bash
make discover
```
This will create a new job file (e.g., `discovery-job-....yaml`) in `work/job/pending/`. By default, this job will only contain new articles from domains already present in your `completed.log`.

#### Step 5: Run the Pipeline Again (The Cycle)

Simply run the pipeline again:
```bash
make run
```
It will automatically find the new job created by `discover` and process it. This `run -> discover -> run` cycle is the primary method for growing your corpus over time.

## Command Reference

### Primary Makefile Commands

| Command         | Description                                                                                             |
| --------------- | ------------------------------------------------------------------------------------------------------- |
| `make run`      | Runs the entire `job -> fetch -> parser` data processing pipeline.                                      |
| `make discover` | Scans for new articles from known authors and creates a new job file.                                   |
| `make prune`    | **(Dry Run)** Reports which files would be deleted based on the `ignored-domains.txt` list.             |
| `make test`     | Runs the project's unit tests to verify core functionality.                                             |
| `make chrome`   | Launches the persistent Chrome browser for `tract` to connect to.                                       |

### Advanced CLI Commands & Flags

For more control, you can invoke the Clojure runner directly.

| Command                           | Description                                                                                                                             |
| --------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `clj -M:discover --expand`        | Runs the discovery tool in "expand" mode. This allows it to find and queue articles from **new domains** not yet in your `completed.log`. |
| `clj -M:prune --force`            | Runs the prune utility and **permanently deletes** the files identified in the dry run. Use with caution.                                 |

### Utility & Migration Scripts

These scripts are located in the `scripts/` directory and are used for one-time data management tasks. They are run via `clj -M:[alias]`.

| Command                           | Description                                                                                                       |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `make populate-completed-log`     | Scans all processed `.md` files and creates the initial `completed.log`. Essential for migrating from an older version. |
| `make backfill-meta`              | Creates `.meta` files for existing data. Useful for data recovery and consistency checks.                             |
| `clj -M:convert-fm`               | (Archive) Converts old TOML front matter to the new standard YAML format.                                         |

## The `work/` Directory: A File System Map

The `work/` directory is the heart of the pipeline. Its structure is:

-   `work/`
    -   `completed.log`: The canonical, append-only log of all successfully processed article URLs. The system's "memory."
    -   `external-links.csv`: A catalog of all outgoing external links found during discovery.
    -   `job/`: The Job Stage
        -   `pending/`: Place new `.yaml` job files here.
        -   `done/`: Processed job files are moved here.
        -   `error/`: Failed job files are moved here.
    -   `fetch/`: The Fetch Stage
        -   `pending/`: Contains `.txt` files with lists of URLs to fetch.
        -   `done/`: Processed `.txt` files are moved here.
        -   `error/`: `.txt` files that failed are moved here.
    -   `parser/`: The Parser Stage
        -   `pending/`: Contains raw `.html` files and their `.meta` companions waiting to be parsed.
        -   `done/`: Successfully parsed `.html` and `.meta` files are moved here for archival.
        -   `error/`: Failed `.html` files are moved here.
    -   `3-processed/`: **Final Output Directory**
        -   Contains the final, clean `.md` files and associated image assets for every successfully processed article.

## Corpus Management

You have several tools to control what content is added to and removed from your collection.

#### Adding New Authors

Create a new `.yaml` file in `work/job/pending/` with an `author:` key. Running `make run` will fetch their articles and add their domain to the list of "known domains," enabling future discovery.

#### Ignoring Domains

To permanently prevent `tract` from discovering or processing content from specific domains, add them to `ignored-domains.txt` (one domain per line). This file is respected by both the `discover` and `prune` tools.

#### Pruning Existing Data

If you add a domain to `ignored-domains.txt` and want to remove any content you've *already* collected from that domain, use the `prune` tool.

1.  **Dry Run (Always do this first):**
    ```bash
    make prune
    ```
    This will print a list of all files that would be deleted, without touching anything.

2.  **Execute Deletion:**
    Once you have reviewed the list and are certain, run the command with the `--force` flag:
    ```bash
    clj -M:prune --force
    ```
    This will permanently delete the associated `.md`, `.html`, and `.meta` files, and will also remove the corresponding URLs from `completed.log`.
