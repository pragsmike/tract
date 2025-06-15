Excellent question. A clear understanding of the data flow and the artifacts created by each part of the system is essential. Creating this "data map" is a perfect way to verify our shared understanding before we proceed.

Here are the two tables you requested.

---

### Table 1: Records of the `tract` System

This table describes the purpose and origin of every significant file or directory in the project.

| Record Type | Pre-existing Human Input? | Description |
| :--- | :--- | :--- |
| **Configuration** | | |
| `config.edn` | **Yes (Primary)** | The core configuration file for the entire pipeline, controlling behavior like throttling and browser settings. Modified by the user. |
| `ignored-domains.txt` | **Yes** | A simple text file where a user lists domains to be permanently ignored by the `discover` and `prune` tools. |
| **Job Specification** | | |
| `work/job/pending/*.yaml` | **Yes** | User-created YAML files that define a job, such as fetching all articles from a new author or a list of specific URLs. |
| **Pipeline Stage Artifacts**| | |
| `work/job/done/*.yaml` | No | Job files that were successfully processed by the `job` stage are moved here for archival. |
| `work/fetch/pending/*.txt`| No | A simple list of URLs to be fetched. This is the output of the `job` stage and the input for the `fetch` stage. |
| `work/fetch/done/*.txt` | No | URL list files that were successfully processed by the `fetch` stage are moved here. |
| `work/parser/pending/*.html`| No | The raw HTML content of a fetched article. Output of the `fetch` stage, input for the `parser` stage. |
| `work/parser/pending/*.html.meta`| No | A JSON file containing the `source_url` and `fetch_timestamp`. It provides context for its companion `.html` file. |
| `work/parser/done/*` | No | All `.html` and `.html.meta` files that were successfully processed by the `parser` stage are moved here for archival. |
| **Final Output** | | |
| `work/3-processed/*.md` | No | The final, clean Markdown version of a processed article, including YAML front matter. **This is the primary valuable output.** |
| `work/3-processed/assets/*`| No | All images associated with a processed article are downloaded and stored here, organized by article. |
| **System State & Logs** | | |
| `work/completed-post-ids.log`| No | The canonical log of all completed article slugs (our "Post ID"). Read by the `job` and `fetch` stages to prevent re-processing. |
| `work/url-to-id.map` | No | A lookup map that connects known URLs to their canonical Post ID (slug). Read by the `fetch` stage. |
| `work/external-links.csv`| No | A catalog of all outgoing external links found by the `discover` tool. Primarily for user analysis. |
| **Error Directories** | | |
| `work/*/error/*` | No | Any file that causes its stage to fail is moved to the corresponding `error` directory for later inspection. |

---

### Table 2: File Creation and Consumption by Stage

This table shows the data flow in a different way, detailing which stage reads and writes each file.

| File Path Pattern | Produced By | Read By |
| :--- | :--- | :--- |
| `config.edn` | Human | All Stages, All Tools |
| `ignored-domains.txt`| Human | `discover`, `prune-ignored` |
| `work/job/pending/*.yaml`| Human, `discover` | `job` |
| `work/fetch/pending/*.txt`| `job` | `fetch` |
| `work/parser/pending/*.html`| `fetch` | `parser` |
| `work/parser/pending/*.html.meta`| `fetch` | `parser` |
| `work/3-processed/*.md` | `parser` | `discover`, `prune-ignored` |
| `work/completed-post-ids.log`| `parser` (via `tract.db`) | `job`, `fetch` |
| `work/url-to-id.map` | `parser` (via `tract.db`) | `fetch`, `prune-ignored`|

This map of the system's data confirms the critical role of the `.html` and `.html.meta` files as the bridge between the `fetch` and `parser` stages. Our plan to make their creation atomic is a direct improvement to the reliability of this bridge.

This exercise was very helpful. I have confirmed my understanding of the data flow. We are ready to proceed with generating the code for **Step 1 of Phase 3**.
