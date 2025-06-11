Of course. Based on the finalized design, here is a detailed, step-by-step implementation plan for the `discovery` tool. This plan breaks the work into logical, verifiable steps, starting with the core data gathering and progressively building towards the final output.

---

### Implementation Plan: `discovery` Tool

Our goal is to create a new, standalone tool that can be run via a `clj -M:discover` alias. This tool will scan our existing corpus of articles, discover new ones, and create a job file to feed back into our main pipeline.

**Step 1: Setup and The "Known URLs" Database**

The first and most fundamental piece is to know which URLs are already in our system. We will create the main namespace for the discovery tool and implement the logic to build this database.

*   **Action 1:** Create a new file for our tool's main entry point: `src/tract/discovery.clj`.
*   **Action 2:** Add a new `:discover` alias to `deps.edn` that points to this new namespace: `:main-opts ["-m" "tract.discovery"]`.
*   **Action 3:** In `discovery.clj`, implement a function `build-known-urls-db`. This function will:
    *   Scan `work/3-processed/` for all `.md` files, read their TOML front matter, and extract the `source_url`.
    *   Scan `work/fetch/pending/` and `work/fetch/done/` for all `.txt` files, read them, and add every URL found to the set.
    *   Return a `set` of all unique, known URLs.
*   **Action 4:** Implement a function `write-known-urls-db! [known-urls]` that writes the contents of the set to `work/known-urls.txt`, one URL per line.
*   **Verification:** The `-main` function for this step will simply call these two functions and print the count of known URLs found. This verifies our core data collection is working.

**Step 2: Link Extraction from a Single HTML File**

Next, we need the core logic for finding all the links within a single article. This is the heart of the discovery process.

*   **Action 1:** In `discovery.clj`, create a new function `extract-links-from-html [html-file]`. This function will:
    *   Take a single HTML file path as input.
    *   Use `enlive` to parse the HTML.
    *   Select all `<a>` tags and extract their `href` attribute and their text content.
    *   Return a list of maps, e.g., `[{:href "..." :text "..."}]`.
*   **Action 2:** Implement a filtering function `filter-and-classify-links [links]`. This function will take the raw list of links and partition it into two lists:
    *   **Substack Links:** URLs that point to `*.substack.com/*` or a known custom domain. It will filter out common noise like `/subscribe`, `/share`, etc.
    *   **External Links:** All other valid `http/https` URLs.
*   **Verification:** We will temporarily update `-main` to run these functions on a single known HTML file (e.g., one from `work/parser/done/`) and print the classified links to the console.

**Step 3: Full Scan and In-Memory Database Creation**

Now we combine the previous steps to scan all processed articles and build our complete link databases in memory.

*   **Action 1:** In `discovery.clj`, update the `-main` function.
*   **Action 2:** It will first get the list of all HTML files to process from `work/parser/done/`.
*   **Action 3:** It will loop through each file, calling `extract-links-from-html` and `filter-and-classify-links` on each one.
*   **Action 4:** It will aggregate the results into two in-memory collections: a `set` of all unique Substack URLs found, and a `list` of all `{:source_article_key, :link_text, :external_url}` maps. The `source_article_key` will need to be derived from the filename of the HTML file being scanned.
*   **Verification:** The `-main` function will print the total count of unique Substack URLs and external links found across all processed articles.

**Step 4: Generate Final Output Files**

This is the final step where the tool produces its valuable output.

*   **Action 1:** Implement `write-external-links-csv! [external-links-data]` that writes the collected external link data to `work/external-links.csv`.
*   **Action 2:** In `-main`, perform the final filtering logic: take the set of all discovered Substack URLs and remove all the URLs present in our "known URLs" database. The result is the set of **new, undiscovered** articles.
*   **Action 3:** Implement `write-job-file! [new-urls]`. This function will:
    *   Take the list of new URLs.
    *   Construct a YAML string in the format `{urls: [...]}`.
    *   Write this YAML to a new job file in `work/job/pending/`, named with the current timestamp to ensure uniqueness (e.g., `discovery-job-20231028T123000.yaml`).
*   **Verification:** After running the tool, we will have three new/updated files: `work/known-urls.txt`, `work/external-links.csv`, and a new job file in `work/job/pending/`. The next run of our main pipeline (`clj -M:run`) will then automatically pick up and process this new job.

This plan systematically builds the discovery tool from the ground up, starting with data collection and ending with job creation, with a clear verification step at each stage.
