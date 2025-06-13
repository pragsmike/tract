#+TITLE: Design: Article Discovery

### 1. Purpose and Role in the System

This document describes the "discovery" component of the `tract` application. The primary purpose of the scraping pipeline is to process a known list of URLs. The purpose of the discovery tool is to **expand this list of known articles** by finding links to new, un-fetched articles within the content that has already been processed.

The discovery tool is a **standalone utility**, not a formal stage in the main scraping pipeline. It is designed to be run manually by the user after a scraping run has completed. It acts as a "job factory," consuming the output of the pipeline and producing new input for it, thus creating a cyclical workflow for corpus expansion.

### 2. High-Level Workflow

The intended operational cycle is as follows:

1.  **User initiates scraping:** The user provides an initial job file (e.g., specifying an author's feed) to the main pipeline (`clj -M:run`). The pipeline runs, populating the `work/` directory with fetched HTML, processed Markdown, and images.
2.  **User runs discovery:** After the pipeline finishes, the user runs the discovery tool (`clj -M:discover`).
3.  **Discovery tool operates:** The tool scans the results of the previous run, discovers new article URLs, and generates a new job file (e.g., `discovery-job-....yaml`) in `work/job/pending/`.
4.  **User initiates next scrape:** The user runs the main pipeline again. It automatically finds and processes the new job file created by the discovery tool, fetching the newly discovered articles.

This cycle can be repeated to incrementally grow the collection of processed articles.

### 3. Inputs

The discovery tool reads its state from the existing `work/` directory and a user-configurable ignore list.

*   **Source HTML Files:** The primary source of new links.
    *   **Path:** `work/parser/done/`
    *   **Usage:** The tool reads every `.html` file in this directory to extract all `<a>` (anchor) tags.

*   **Processed Article Metadata:** Used to build a database of already-known articles to avoid redundant fetching.
    *   **Path:** `work/3-processed/`
    *   **Usage:** The tool scans all `.md` files, parses their TOML front matter, and extracts the canonical `source_url`.

*   **In-Flight URL Lists:** Used to augment the "known URLs" database with articles that have been scheduled or fetched but not yet fully processed.
    *   **Paths:** `work/fetch/pending/` and `work/fetch/done/`
    *   **Usage:** The tool reads all `.txt` files in these directories to get a list of URLs that are currently in the pipeline.

*   **User-Configurable Ignore List:** A plaintext file for excluding specific domains from discovery.
    *   **Path:** `ignore-list.txt` (in the project root)
    *   **Format:** One domain or Substack subdomain per line. Lines starting with `#` are treated as comments and ignored.
    *   **Example:**
        ```
        # Ignore this entire site
        www.some-uninteresting-site.com
        # Ignore this substack
        boring-author
        ```

### 4. Outputs

The discovery tool produces three distinct output files in the `work/` directory.

*   **Known URLs Database:** A snapshot of all URLs known to the system at the time the tool was run.
    *   **Path:** `work/known-urls.txt`
    *   **Format:** A text file containing one canonical URL per line, sorted alphabetically.

*   **External Links Catalog:** A catalog of all outgoing links to non-Substack resources found within the processed articles.
    *   **Path:** `work/external-links.csv`
    *   **Format:** A standard CSV file with the header `source_article_key,link_text,external_url`.

*   **New Article Job:** A job file for the main pipeline, containing only the newly discovered, unique, and non-ignored article URLs.
    *   **Path:** `work/job/pending/discovery-job-<timestamp>.yaml`
    *   **Format:** A YAML file containing a single `urls` key.
    *   **Example:**
        ```yaml
        urls:
          - "https://author-a.substack.com/p/new-article-1"
          - "https://www.custom-domain.com/p/new-article-2"
        ```

### 5. Core Logic and Link Classification

The discovery process follows these logical steps:

1.  **Build "Known URLs" Database:** The tool first establishes a baseline of all URLs it already knows about by reading the metadata from all processed `.md` files and scanning all URL lists in the `fetch` stage directories. All URLs are canonicalized (fragments and query parameters are removed).
2.  **Scan and Extract:** The tool iterates through every `.html` file in `work/parser/done/`. From each file, it extracts every link (`<a href="...">`) and its associated link text.
3.  **Filter and Classify:** Each extracted link is passed through a series of rules in a specific order:
    *   **Malformed URLs are ignored.**
    *   If the URL's domain matches an entry in `ignore-list.txt`, the link is **completely ignored** and will not appear in any output.
    *   Links with paths containing `/subscribe`, `/share`, `/comment`, etc., are classified as **Noise** and ignored.
    *   Links to the Substack CDN (`cdn.substack.com`) are classified as **Noise**.
    *   A link whose path starts with `/p/` is classified as a **`Substack Article`**. This is the primary heuristic for identifying discoverable content, and it works for both `*.substack.com` domains and custom domains.
    *   Any other link to a `*.substack.com` domain that is not an article (e.g., an author's profile page) is classified as **Noise**.
    *   Any remaining valid `http/https` URL is classified as an **`External Link`**.
4.  **Generate Outputs:**
    *   The list of all `External Link`s is written to `external-links.csv`.
    *   The list of all `Substack Article` URLs is canonicalized and collected.
    *   A set difference is performed between this list of discovered articles and the "Known URLs" database.
    *   The resulting set of genuinely new URLs is written to a new `discovery-job-*.yaml` file.

### 6. Concurrency and Invocation

*   **Invocation:** The tool is designed to be run manually from the command line:
    ```bash
    clj -M:discover
    ```
*   **Concurrency:** The tool does not use complex locking mechanisms. If it is run while the main pipeline is active, it will check for the existence of a `work/pipeline.lock` file and print a warning to the user. The worst-case outcome of concurrent execution is that the discovery run will not see links from articles that are still in-flight, and those links will simply be discovered on the next run. This is an acceptable trade-off for simplicity.
