## Design: Article Discovery (`discover` tool)

**Version:** 1.1
**Status:** Core Feature Complete

### 1. Purpose and Role in the System

This document describes the "discovery" component of the `tract` application. While the main pipeline is designed to process a known list of URLs, the purpose of the discovery tool is to **expand this list of known articles** by finding links to new, un-fetched articles within the content that has already been processed.

The discovery tool is a **standalone utility**, not a formal stage in the main scraping pipeline. It is designed to be run manually by the user after a scraping run has completed. It acts as a "job factory," consuming the output of the pipeline and producing new input for it, thus creating a cyclical workflow for corpus expansion.

### 2. High-Level Workflow

The intended operational cycle is as follows:

1.  **User initiates scraping:** The user provides an initial job file (e.g., specifying a new author's feed) to the main pipeline (`make run`). The pipeline runs, populating the `work/` directory with fetched HTML, processed Markdown, and updating the `work/completed.log`.
2.  **User runs discovery:** After the pipeline finishes, the user runs the discovery tool (`make discover`).
3.  **Discovery tool operates:** The tool scans for new article links. By default, it restricts itself to domains it already knows about. It then generates a new job file (e.g., `discovery-job-....yaml`) in `work/job/pending/` containing only genuinely new and approved article URLs.
4.  **User initiates next scrape:** The user runs `make run` again. The pipeline automatically finds and processes the new job file created by the discovery tool, fetching the newly discovered articles.

This cycle can be repeated to incrementally grow the collection of processed articles from known authors.

### 3. Inputs

The discovery tool reads its state from the existing `work/` directory and a user-configurable ignore list.

*   **Source HTML Files:** The primary source of new links.
    *   **Path:** `work/parser/done/`
    *   **Usage:** The tool reads every `.html` file in this directory to extract all `<a>` (anchor) tags.

*   **Completed Articles Log:** The canonical source of truth for which articles have been successfully processed and which domains are "known".
    *   **Path:** `work/completed.log`
    *   **Usage:** Used to build the set of "known URLs" to avoid re-discovering completed articles. Also used to build the set of "known domains" for the default restrictive filtering mode.

*   **In-Flight URL Lists:** Used to augment the "known URLs" database with articles that are currently in the pipeline but not yet complete.
    *   **Paths:** `work/fetch/pending/` and `work/fetch/done/`
    *   **Usage:** The tool reads all `.txt` files in these directories to get a list of URLs that should not be re-queued.

*   **User-Configurable Ignored Domains:** A plaintext file for permanently excluding specific domains from discovery.
    *   **Path:** `ignored-domains.txt` (in the project root)
    *   **Format:** One domain or Substack subdomain per line. Lines starting with `#` are treated as comments and ignored.

### 4. Outputs

The discovery tool produces two distinct output files.

*   **External Links Catalog:** A catalog of all outgoing links to non-Substack resources found within the processed articles. This is useful for analysis but is not directly consumed by the pipeline.
    *   **Path:** `work/external-links.csv`
    *   **Format:** A standard CSV file with the header `source_article_key,link_text,external_url`.

*   **New Article Job:** A job file for the main pipeline, containing only the newly discovered, unique, and non-ignored article URLs.
    *   **Path:** `work/job/pending/discovery-job-<timestamp>.yaml`
    *   **Format:** A YAML file containing a single `urls` key.

### 5. Core Logic and Filtering

The discovery process follows these logical steps:

1.  **Build "Known URLs" Database:** The tool first establishes a baseline of all URLs it already knows about by reading every URL from `work/completed.log` and all URLs from the `fetch` stage directories (`pending` and `done`). All URLs are canonicalized.
2.  **Scan and Extract:** The tool iterates through every `.html` file in `work/parser/done/`. From each file, it extracts every link (`<a href="...">`) and its associated link text.
3.  **Initial Link Classification:** Each extracted link is passed through a series of rules:
    *   Malformed URLs are ignored.
    *   If the URL's domain matches an entry in `ignored-domains.txt`, the link is **completely ignored**.
    *   Links with paths containing noise patterns like `/subscribe`, `/share`, `/comment`, etc., are classified as **Noise** and ignored.
    *   A link whose path starts with `/p/` is classified as a potential **`Substack Article`**.
    *   Any other link is classified as an **`External Link`**.
4.  **Domain-Based Filtering (The Default Mode):**
    *   The tool builds a set of "approved domains" by reading `work/completed.log` and extracting the unique domains from every URL.
    *   The list of potential `Substack Article` links is then filtered. **Only articles whose domain is present in the "approved domains" set are kept.** This prevents discovery of articles from new authors.
5.  **Generate Outputs:**
    *   The list of all `External Link`s is written to `external-links.csv`.
    *   The filtered list of approved `Substack Article` URLs is collected.
    *   A final set difference is performed between this list and the "Known URLs" database to ensure only genuinely new articles are queued.
    *   The resulting set of new URLs is written to a `discovery-job-*.yaml` file.

### 6. Invocation and "Expand" Mode

The tool is designed to be run via the `Makefile`:
```bash
make discover
```
By default, it runs in the restrictive "known domains only" mode. To override this and allow the discovery of articles from new domains, a command-line flag must be used:

```bash
clj -M:discover --expand
```

When the `--expand` flag is present, **Step 4 (Domain-Based Filtering)** is skipped entirely, allowing the tool to find and queue articles from any domain not on the permanent ignore list.
