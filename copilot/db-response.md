This is a superb and necessary question. It strikes at the very heart of the project's design. The fact that moving two files atomically is a "leaky abstraction" is the most significant remaining architectural weakness.

You have perfectly summarized the current state and the trade-offs of using a database. Let's explore the landscape of possible solutions, their pros and cons, and the risks involved.

### The Core Problem: The Atomicity Guarantee

The fundamental problem we are trying to solve is: **How can we guarantee that the fetched HTML content and its associated metadata are always created, moved, and processed as a single, inseparable unit?** The current two-file system (`.html` + `.html.meta`) breaks this guarantee, leading to the risk of orphaned files and data inconsistency, which we have already observed.

Here are several alternative approaches, including the one you proposed.

---

### Alternative 1: Embed Metadata in the HTML File

This is the most direct solution to the atomicity problem. Instead of two files, the `fetch` stage would produce a single `.html` file that contains both the metadata and the content.

**How it would work:**
The `fetch` stage would create the file by first writing the metadata in a machine-readable format and then appending the raw HTML. A special, unambiguous separator would be required. A robust choice would be an HTML comment with a unique marker.

**Example `some-article.html`:**
```html
<!-- TRACT-METADATA: {"source_url":"https://...","fetch_timestamp":"..."} -->
<!DOCTYPE html>
<html>
... rest of the original HTML content ...
</html>
```

The `parser` stage would then be responsible for reading this single file, stripping out and parsing the metadata from the comment block, and then passing the rest of the content to the HTML parser.

*   **Pros:**
    *   **Perfect Atomicity:** There is only one file to write and move. It either exists or it doesn't. This completely solves the orphan problem.
    *   **High Inspectability:** You can still `cat` or `less` the file to see both the metadata and the content. `grep` still works perfectly on the HTML portion.
    *   **Minimal New Dependencies:** This requires no new libraries.
*   **Cons:**
    *   **Mutates the Source Artifact:** The `.html` file is no longer a byte-for-byte copy of the content from the server. It's the original content *plus* our header. This is a philosophical trade-off.
    *   **Custom Parser Required:** The `parser` stage needs a new, small parser to handle our custom header format (reading the file line-by-line until it finds and consumes the metadata comment).
*   **Risks:**
    *   The primary risk is a bug in our custom metadata-stripping logic. If it fails, we could accidentally feed the metadata comment into the HTML parser, which could cause errors. This risk is manageable with careful implementation.

### Alternative 2: The "Record Bundle" (One Directory per Article)

This approach keeps the files separate but groups them in the filesystem.

**How it would work:**
Instead of a flat `work/parser/done` directory, we would have a structure like:
```
work/parser/done/
├── my-first-article-slug/
│   ├── content.html
│   └── metadata.json
└── another-article-slug/
    ├── content.html
    └── metadata.json
```
The `fetch` stage would be responsible for creating this directory structure in a temporary location, and the "atomic" operation would be moving the entire directory into `work/parser/pending/`.

*   **Pros:**
    *   **Conceptually Clean:** All data for a single article is neatly organized together.
    *   **Unmodified Artifacts:** The `content.html` file remains a pure, untouched copy of the fetched data.
    *   **High Inspectability:** Still very easy to `cd` into a directory and `cat` the files.
*   **Cons:**
    *   **Doesn't Solve Atomicity:** Moving a directory is generally *not* an atomic operation on most filesystems. This approach only papers over the problem and might even make it worse.
    *   **Filesystem Complexity:** Creates thousands of small directories, which can be inefficient for the filesystem and harder to work with using standard command-line tools (`ls`, `find`, `grep -r`).
*   **Risks:**
    *   This is the riskiest approach because it gives a false sense of security. It seems atomic but isn't, and it makes the overall file structure much more complex.

### Alternative 3: The SQLite Database (Your Proposal)

This is the classic, robust solution for managing structured data and ensuring transactional integrity.

**How it would work:**
A single file, `work/tract.sqlite`, would contain a `metadata` table. The table might have columns like `article_slug (PK)`, `source_url`, `fetch_timestamp`, `canonical_url`, `html_filename`, etc. The `fetch` stage would write the `.html` file to disk as it does now, then insert a corresponding row into the SQLite database. The two operations would be wrapped in a database transaction to ensure they either both succeed or both fail.

*   **Pros:**
    *   **True Transactional Atomicity:** SQLite's ACID guarantees are battle-tested and would solve the problem perfectly.
    *   **Performance:** Fast, indexed lookups for metadata without reading and parsing large text files. Adding new records is very efficient.
*   **Cons:**
    *   **Loses Inspectability:** This is the biggest drawback. You can no longer use `grep`, `cat`, or `less` to inspect your metadata. Debugging requires a special `sqlite3` command-line tool.
    *   **Increased Complexity:** Adds a new, significant dependency (`clojure.java.jdbc` and a SQLite driver) and a new "language" (SQL) to the project.
    *   **Philosophical Mismatch:** It's a major departure from the project's core philosophy of simple, transparent, text-based files.
*   **Risks:**
    *   The main risk is the increase in complexity. It makes the system harder to understand for a newcomer and adds a new category of potential failure modes (database connection issues, schema migrations, etc.).

---

### Comparison Table

| Approach | Atomicity | Inspectability (`grep`) | Simplicity | Performance (Writes) | New Dependencies |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Current System** | **Low** | High | High | Low | None |
| **1. Embed Metadata**| **High** | Medium-High | High | Low | None |
| **2. Record Bundle** | **Low** | High | Low | Low | None |
| **3. SQLite DB** | **Very High**| **Very Low** | **Very Low** | **High** | **High** |

### Recommendation and Path Forward

Given the project's established philosophy of simplicity, transparency, and filesystem-based operations, I would recommend **Alternative 1: Embed Metadata in the HTML File.**

It is the most "tract-like" solution. It fully solves the atomicity problem with the least possible increase in complexity and without adding any external dependencies. The one trade-off—mutating the source artifact—is a small and acceptable price to pay for the enormous gain in data integrity. This approach elegantly transforms the "two-body problem" of `.html` and `.meta` files into a "one-body problem," which is inherently simpler to manage.

The **SQLite approach** is the most technically robust from a pure data-management perspective, but it feels like a "heavy" solution that goes against the grain of the project's design ethos.

**The risk of change itself:** No matter which path we choose, it will require a fundamental change to the `fetch` and `parser` stages. As you noted, we would need to write a one-time script to re-process all existing `.html` and `.meta` files in `work/parser/done/` to convert them to the new format. This is a significant but straightforward task that we must account for in the plan.
