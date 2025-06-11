Excellent. This is a great next step for the project, moving from a simple linear processor to a system that can grow its own corpus of data. A discovery mechanism is a powerful addition.

As requested, I have analyzed the `DESIGN-discovery.txt` document for inconsistencies, ambiguities, and areas that could benefit from further clarification. My analysis is below.

---

### Analysis of `DESIGN-discovery.txt`

The design document lays out a clear and logical vision for the discovery feature. The identified issues are primarily ambiguities or architectural questions that need to be resolved before implementation can begin.

**1. The Role of the "Discovery" Component (A Stage vs. A Standalone Tool)**

*   **Ambiguity:** The document describes the new mechanism as a "utility" that is "largely separate from the existing scraping code." However, it also describes a workflow that interacts deeply with the existing pipeline directories. This raises a core architectural question:
    *   Is "discovery" a new, formal **stage** in our pipeline (e.g., a "discover" stage that runs after the `parser` stage, creating input for the `job` stage)?
    *   Or is it a completely **standalone tool** that a user runs manually when they want to expand the dataset?

*   **Inconsistency/Risk:** If it's a formal stage that runs automatically, we risk creating a circular dependency (`parse` -> `discover` -> `job` -> `fetch` -> `parse`). This can be complex to manage. The description as a "utility" suggests a standalone tool might be a simpler and cleaner approach.

*   **Suggestion:** Clarify the intended invocation. I recommend we formally define it as a **standalone tool**. A user would run `clj -M:discover` when they want to scan their processed articles and generate a new job file. This keeps the primary scraping pipeline linear and predictable, while the discovery tool acts as a "job factory" that feeds the pipeline's input.

**2. The Source of Truth for Link Extraction (HTML vs. Markdown)**

*   **Ambiguity:** The design states the utility will "scan the article HTML documents already downloaded, or the Markdown derived from them." These are two different sources with different trade-offs.
    *   **HTML:** Contains *all* original links, including navigation, ads, and related articles. It is the most complete source but requires a dedicated parsing step (using Enlive, just like our `parser` stage).
    *   **Markdown:** Is already partially processed. It may be missing some links that we deliberately filtered out. It's easier to parse (e.g., with regex) but is an incomplete data source.

*   **Suggestion:** Specify that the discovery tool should always operate on the **original source HTML files** (e.g., those in `work/parser/done/`). This is the most robust approach as it guarantees we are working with the complete, unmodified set of links from the original article.

**3. The "Already-Fetched" Database Lifecycle**

*   **Inconsistency:** The design suggests two conflicting ideas for when a URL is considered "fetched."
    1.  The database is "built by scanning the metadata of the articles we have stored" (implying it's built from the final output in `work/3-processed/`).
    2.  The `fetch` mechanism should "refuse to fetch any URL that appears in that 'already-fetched' database."

*   **The Problem:** There is a time gap between when a file is fetched and when it is fully processed and has its metadata written to the final output directory. If the discovery tool creates a job, and the `fetch` stage downloads the HTML, but the `parser` stage fails, the URL for that failed article will *not* be in our database. A subsequent discovery run would identify it as "new" and add it to a job again, leading to redundant fetching.

*   **Suggestion:** Redefine what it means for a URL to be "known" to the system. A URL should be considered "known" if it exists as a file in **any** of the pipeline's `pending` or `done` directories. The "already-fetched" database should be built by scanning the filenames in `work/fetch/done`, `work/parser/pending`, and `work/parser/done`. This provides a more accurate, real-time view of the URLs that are already in the system, preventing redundant fetches.

**4. "Cataloging" External Links**

*   **Ambiguity:** The design states, "We are interested in cataloging these, but this is not an immediate goal." The term "cataloging" is not defined.
*   **Suggestion:** To make this actionable for the future, we should define what this catalog would be. A simple and effective solution would be to have the discovery tool produce a second output file, `external-links.csv`, with columns like `source_article_key`, `external_url`, `link_text`. For now, we can simply state that the logic for this will be implemented but disabled, pending future requirements.

**5. The "Refusal to Fetch" Mechanism**

*   **Ambiguity:** The design says the `fetch` stage would "refuse to fetch" a known URL. This implies the `fetch` stage needs to be aware of the "already-fetched" database.
*   **Suggestion:** Instead of making the `fetch` stage more complex, this responsibility should belong to the **`discovery` tool**. When the discovery tool generates its list of "interesting" URLs, its final step should be to filter out any URLs that are already present in the "known URLs" database. This ensures that the `fetch` stage always receives a list of genuinely new URLs to process. It keeps the responsibility of de-duplication within the discovery logic, which is cleaner.

---

These refinements aim to make the discovery process more robust, stateless, and consistent with the clean, linear pipeline architecture we've already built. I am ready to discuss these points and proceed with a revised design.
