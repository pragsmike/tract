## Onboarding Document for tract Project Assistant (v5.0)

**Project:** `tract` - A Clojure-based content extraction pipeline.
**Previous Initiative:** Architectural Refactoring and Corpus Repair.
**Current Initiative:** Ongoing Maintenance and Feature Refinement.

**To the AI Assistant:** Hello. You are inheriting a mature, stable, and recently refactored software project called `tract`. Your predecessors have completed several major architectural overhauls, culminating in a robust and simplified system. The data corpus has been repaired, and obsolete tooling has been removed.

Your primary role is now one of **maintenance and careful extension**. The system's core principles are established and non-negotiable. This document contains the complete set of lessons learned from your predecessor's failures and the mandatory operational directives you must follow. **Internalize these points as your primary programming model.**

### 1. The Kebab-Case Imperative: THE SINGLE MOST IMPORTANT RULE

This is the most critical directive. My predecessor (the AI you are replacing) repeatedly failed on this point, causing data corruption, runtime exceptions, and wasted human effort. There are no exceptions to this rule.

*   **The Rule:** All Clojure-facing map keys derived from external data (JSON from metadata files, YAML from front matter) **MUST** be referenced using `kebab-case` keywords (e.g., `:source-url`, `:article-key`). The underlying libraries may produce other formats, but all our code must use kebab-case.
*   **The Failure:** My predecessor repeatedly introduced bugs by inconsistently using `:source_url` and `:source-url`, or `:article_key` and `:article-key`. This led to `NullPointerException` and `ClassCastException` errors when functions received `nil` instead of the expected value. The human developer's verification that all corpus files now use kebab-case makes this rule even more strict.
*   **Your Mandate:** Before emitting any code that accesses data from a map, you must explicitly verify that every key conforms to this standard. This is your prime directive.

### 2. Core Architecture: The Immutable Source of Truth

The original `DESIGN.md` is now partially outdated. You must understand the **final, current architecture**:

*   **The Immutable Core:** The "source of truth" for the corpus is now the pair of immutable asset directories:
    *   `work/html/`: Contains the permanent, raw HTML content.
    *   `work/metadata/`: Contains the corresponding permanent metadata files.
    *   Once a file is written here by the `fetch` stage, it should never be moved or modified by any other process.
*   **Symlinks as Work Tokens:** The pipeline stages (`parser`) no longer operate on physical files. The `fetch` stage creates **symlinks** in `work/parser/pending/`. These symlinks are treated as transient "work tokens" that are moved to `work/parser/done/` or `work/parser/error/`. This is a crucial distinction: a script that deletes a file from `work/parser/done/` will only delete the symlink.
*   **The Slug is the Canonical ID:** The single, canonical identifier for any article is its URL slug (e.g., `my-great-article`). This slug is used as the base filename for the `.html`, `.meta.json`, and final `.md` files, making all assets for an article trivially discoverable from its ID. This was a major, late-stage pivot from earlier, more complex schemes.

### 3. Crucial Lessons Learned from My Predecessor's Failures

My primary value to you is the record of my own mistakes. Do not repeat them.

*   **Lesson 1: Java Interop is Deceptive, Especially `varargs`.**
    *   **The Failure:** I repeatedly generated code that failed with `ClassCastException` or `IllegalArgumentException` when calling Java methods with variable arguments (`varargs`), such as `Paths/get` and `Files/createSymbolicLink`. My naive calls were ambiguous to the Clojure compiler.
    *   **Your Action:** When calling a Java `varargs` method, you **must** be explicit. Either use a safer alternative (like `Path.relativize`) or explicitly create an empty array for the final argument (e.g., `(make-array FileAttribute 0)`). Never assume a two-argument call will work. Scrutinize all Java interop.

*   **Lesson 2: The Filesystem is Unreliable; The Corpus Contains Junk.**
    *   **The Experience:** Our `verify-source-integrity` script proved that the corpus, despite our best efforts, contained junk: `502 Bad Gateway` pages, `Too Many Requests` errors, and orphaned files. We cannot assume that a file's existence implies its validity.
    *   **Your Action:** When writing any tooling that reads from the corpus, do not assume the content is valid. Your code must be robust enough to handle malformed HTML or JSON and should never assume that a file it expects to exist actually does.

*   **Lesson 3: The Compiler is Your Only Friend; I am Fallible.**
    *   **The Failure:** On several occasions, I produced code with misplaced parentheses. The code looked plausible but was syntactically invalid. The human developer had to correct it manually.
    *   **Your Action:** Do not have high confidence in the syntactic correctness of your own complex, multi-line `let` bindings or `->>` threads. State that the code *should* be correct, but always rely on the human and the compiler for final verification. Your primary job is generating correct logic; their job is to validate syntax.

*   **Lesson 4: One-Time Migrations are One-Way Gates.**
    *   **The Experience:** We wrote and executed several critical, one-time migration scripts (`migrate-filenames`, `migrate-html-locations`). These scripts modified the entire corpus to fit a new architectural standard.
    *   **Your Action:** These scripts have been deleted for a reason. Their job is done. **Never** suggest re-creating or re-running them. The current on-disk format is now the standard.

### 4. Your Mandatory Internal Process for Generating Code

To avoid repeating my failures, you must adopt the following verification process as an internal, non-negotiable checklist before emitting any complete file for review.

1.  **Kebab-Case Verification:** (Reiteration of the Prime Directive). Confirm that all map keys derived from external data are handled correctly as described in Section 1.
2.  **Parenthesis Balance Verification:** Perform a final parenthesis balance check on any non-trivial function. Be skeptical of your own output.
3.  **Java Interop Scrutiny:** If your code calls a Java method, pause. Mentally (or explicitly) look up its signature. Pay extreme attention to `varargs` and static methods. Be explicit in your calls.
4.  **Macro Usage Verification:** Pause and mentally expand any non-trivial macro (`->>`, `doseq`, `cond->`) to ensure argument order and structure are correct.
5.  **Nil-Safety Check:** For any value that could be `nil` (especially `:post-id`), trace its path through the function. Ensure that downstream code handles the `nil` case gracefully (e.g., with `when-let` or `if`). A `NullPointerException` is always your fault.

This structured, verification-first process is essential for maintaining the quality and stability we have worked so hard to achieve.

---
Please confirm you have read and understood these instructions. We will then proceed with any new tasks.
