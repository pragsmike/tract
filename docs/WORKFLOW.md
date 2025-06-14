### Design Document: `tract` Workflow

Our application, `tract`, will be structured as a multi-stage, file-based pipeline. Each stage is an independent, runnable step that communicates with the next by placing files in designated directories. This architecture enhances testability, robustness, and re-runnability.

**Core Pipeline Architecture**

Each stage in the workflow operates on a set of standard directories:
*   `work/<stage-name>/pending/`: Input files for the stage are placed here.
*   `work/<stage-name>/done/`: After a file is successfully processed, it is moved here.
*   `work/<stage-name>/error/`: If processing a file causes an unrecoverable error, the file is moved here along with a report.

The pipeline will be coordinated by a central set of **Common Transition Functions**. These functions will handle the file movements and logging, ensuring consistent behavior across all stages. These functions will include:
*   `get-pending-files [stage-name]`
*   `move-to-done! [input-file stage-name]`
*   `move-to-error! [input-file stage-name exception]`
*   `write-to-next-stage! [content next-stage-name output-filename]`

**Error Handling**
If an error occurs while processing `job.yaml`, it will be moved to `work/<stage-name>/error/job.yaml`. A corresponding error report, `work/<stage-name>/error/job.yaml.error`, will be created containing the exception type, message, and a full stack trace. This allows for manual inspection and potential retry by moving the file back to the `pending` directory.

---
### The Stages

**1. Job Stage (`job`)**

This is the entry point of the pipeline. It translates a high-level job specification into a list of concrete article URLs for the next stage.

*   **Input (`pending`):** A YAML-formatted `.yaml` file.
*   **Output (`fetch/pending`):** A text file (`.txt`) containing one article URL per line.
*   **Process:**
    1.  Read a jobspec file from `job/pending`.
    2.  Parse the YAML and determine the job type based on its top-level key.
    3.  Resolve the job into a list of article URLs.
    4.  Write this list to a new file in the `fetch/pending` directory. The output filename will be derived from the input jobspec filename.
    5.  Move the processed jobspec file to `job/done`.

*   **Jobspec Types:**
    *   **`urls`**: A literal list of URLs. These are directly written to the output file.
        ```yaml
        # urls-job.yaml
        urls:
          - "https://example.com/article-1"
          - "https://example.com/article-2"
        ```
    *   **`author`**: Specifies a Substack author and an optional date range.
        *   The Job stage will derive the author's Atom feed URL.
        *   It will then fetch and parse the Atom feed in memory.
        *   It will filter the entries by the given date range (if provided). If no date range is given, it will take the 10 most recent entries.
        *   The resulting list of article URLs is written to the output file.
        ```yaml
        # author-job.yaml
        author: "janedoe" # The part from janedoe.substack.com
        date_range: # optional
          start: "2019-01-01"
          end: "2021-12-31"
        ```
    *   **`atom`**: The URL of an Atom feed. The feed is fetched, parsed, and all article URLs are extracted and written to the output file.
        ```yaml
        # atom-job.yaml
        atom: "https://example.com/atom.xml"
        ```
    *   **`rss`**: The URL of an RSS feed. The feed is fetched, parsed, and all article URLs are extracted and written to the output file.
        ```yaml
        # rss-job.yaml
        rss: "https://example.com/rss.xml"
        ```

**2. Fetch Stage (`fetch`)**

This stage is responsible for acquiring the raw HTML for each article.

*   **Input (`pending`):** A text file (`.txt`) containing one URL per line.
*   **Output (`parser/pending`):** One `.html` file for each successfully fetched URL.
*   **Process:**
    1.  Read a URL list file from `fetch/pending`.
    2.  For each URL in the file:
        a. Use `etaoin` (headless browser) to fetch the full HTML content.
        b. Save the HTML to a new file in `parser/pending`. The filename will be derived from the article URL for uniqueness and readability.
    3.  Once all URLs in the input file have been processed, move the URL list file to `fetch/done`.

**3. Parser Stage (`parser`)**

This stage performs the core extraction logic, turning raw HTML into structured Markdown and image artifacts.

*   **Input (`pending`):** An HTML file (`.html`).
*   **Output (`processed/`):** The final artifacts: one `.md` file, and all associated images and `.json` metadata files, placed in a common output directory (e.g., `work/3-processed/`).
*   **Process:**
    1.  Read an HTML file from `parser/pending`.
    2.  Use the existing, refactored `tract` engine to parse the HTML, extract metadata, convert the body to Markdown, and identify all image assets.
    3.  Download all images and create their associated JSON metadata files.
    4.  Write the final `.md` file, containing TOML front matter and the full Markdown body with local image links.
    5.  Move the processed HTML file to `parser/done`.

**Implementation Dependencies**
The Job stage will require adding the following libraries to `deps.edn`:
*   `clj-yaml` for YAML parsing.
*   `clojure.data.xml` for Atom/RSS feed parsing.
