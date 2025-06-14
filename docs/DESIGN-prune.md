## Design: Corpus Pruning (`prune` tool)

**Version:** 1.0
**Status:** Core Feature Complete

### 1. Purpose and Role in the System

This document describes the `prune` component of the `tract` application. The main pipeline and discovery tools are designed to *add* content to the corpus. The purpose of the `prune` tool is to provide a safe and reliable way to **remove content retroactively**.

This is a critical administrative utility for corpus maintenance. Its primary use case is to enforce the `ignored-domains.txt` list on the existing collection, deleting all data associated with a domain that has been added to the ignore list after its content was already fetched.

The `prune` tool is a **standalone script** and is not part of the main data processing pipeline. It is designed to be run manually by the operator when curation rules change.

### 2. High-Level Workflow

The intended operational workflow for pruning the corpus is:

1.  **User updates ignore list:** The user edits the `ignored-domains.txt` file to add one or more domains they no longer wish to keep in their collection.
2.  **User runs a "Dry Run":** The user runs `make prune` from the command line. The script will analyze the existing data and print a detailed report of every file that *would* be deleted, without actually modifying anything.
3.  **User verifies the plan:** The user reviews the dry run report to ensure that only the intended files are targeted for deletion.
4.  **User executes the deletion:** Once satisfied with the plan, the user runs the command with a force flag (`clj -M:prune --force`). The script then proceeds with the permanent deletion of all identified files and updates the system's logs to reflect the removal.

### 3. Inputs

The `prune` tool reads its state from the `work/` directory and the project's ignore list.

*   **Ignored Domains List:** The source of truth for which domains to prune.
    *   **Path:** `ignored-domains.txt` (in the project root)
    *   **Usage:** The tool reads every domain from this file into a set.

*   **Processed Article Metadata:** The primary source for identifying articles to be pruned. The script iterates through every processed article to check its source domain.
    *   **Path:** `work/3-processed/`
    *   **Usage:** The tool reads every `.md` file, parses its YAML front matter to find its `source_url` and `article_key`, and checks if the URL's domain is in the ignored set.

*   **Completed Articles Log:** The canonical log of all completed articles, which must be updated after a prune operation.
    *   **Path:** `work/completed.log`
    *   **Usage:** After deleting files, the script reads this log, removes the URLs of the pruned articles, and overwrites the file with the updated list.

### 4. Core Logic and Safety Mechanisms

The `prune` utility is designed with safety as its paramount concern.

#### 4.1. Dry Run (Default Behavior)

When run without any flags (`make prune`), the script performs the following steps:
1.  **Load Ignored Domains:** Reads all domains from `ignored-domains.txt` into a set.
2.  **Scan and Identify Candidates:** Iterates through every `.md` file in `work/3-processed/`. For each file:
    a.  It parses the YAML front matter to get the `source_url` and `article_key`.
    b.  It extracts the domain from the `source_url`.
    c.  If the domain is in the ignored set, it marks the article as a "prune candidate."
3.  **Locate All Associated Files:** For each candidate, it programmatically determines the paths of all three associated files:
    *   The `.md` file in `work/3-processed/`.
    *   The `.html` file in `work/parser/done/`.
    *   The `.html.meta` file in `work/parser/done/`.
4.  **Report, Do Not Act:** The script prints a formatted list of all candidate articles and their associated file paths to the console. It performs **no write or delete operations**.

#### 4.2. Force Mode (Destructive Operation)

The script will only perform destructive actions if invoked with a `--force` or `--delete` flag (e.g., `clj -M:prune --force`).

1.  **Identify Candidates:** The script performs the same identification steps as in the dry run.
2.  **Delete Files:** For each candidate, it iterates through the list of associated file paths and attempts to delete each one, reporting the status (`[DELETED]`, `[FAILED]`, `[MISSING]`) of each operation.
3.  **Update Completion Log:** After the file deletion loop is complete, the script performs the final, critical step:
    a.  It reads all URLs from the original `work/completed.log` into a set.
    b.  It creates a new set of URLs by removing all the `source_url`s from the candidates it just deleted.
    c.  It overwrites `work/completed.log` with the new, smaller set of URLs, ensuring the system's state remains consistent.

This two-step process (safe-by-default dry run + explicit force flag) ensures that the operator is always in full control and minimizes the risk of accidental data loss.
