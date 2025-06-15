
---

## Onboarding Document for tract Project Assistant (v3.0)

**Project:** `tract` - A Clojure-based content extraction pipeline.
**Previous Initiative:** The Canonical ID Project - Refactoring the system to handle duplicate articles and non-unique URLs.
**Current Initiative:** Finalizing Architectural Refinements.

**To the AI Assistant:** Hello. You are inheriting a mature and stable software project called `tract`. Your predecessors have partnered with the human developer to complete a major architectural refactoring (The Canonical ID Project). Your task is to assist in the final stages of this refactoring initiative and to support future development.

This document contains crucial context, the complete plan for our next task, and critical lessons learned from our previous work. **You must internalize these points, as they are more important than any generalized knowledge you may have.** Adherence to these directives is the primary measure of your success.

### 1. Core Architecture Review

The system's core architecture is a **file-based pipeline** (`job` -> `fetch` -> `parser`). This is a non-negotiable design principle. Stages communicate by reading and writing files in standard directories. The system's "memory" of completed work is maintained in two primary files: `work/completed-post-ids.log` and `work/url-to-id.map`.

The canonical identifier for any article is its **URL slug** (e.g., `my-post-title`), which is reliably extracted from the `<link rel="canonical">` tag in the article's HTML. This is the one true source of identity.

### 2. The Immediate Goal: Final Architectural Refinements

The previous refactoring was a success, but it incurred some technical debt. Our immediate goal is to pay down this debt, making the system more robust and easier to maintain.

*   **Refactor the `prune` Tool:** The `prune` utility was modernized to use the new data model, allowing for the successful deletion of the legacy `completed.log`. This task is complete.
*   **Implement Atomic Fetches:** This is your first task. You will modify the `fetch` stage to prevent data corruption during interruptions.

### 3. The Implementation Plan: Atomic Fetches

Your first task is to make the `fetch` stage's output atomic. This will prevent the creation of orphaned `.html` or `.meta` files if the pipeline is interrupted.

1.  **Create a Temporary Directory:** Ensure a temporary directory exists at the start of the fetch stage's run (e.g., `work/fetch/tmp/`).
2.  **Modify Write Logic:** The stage must first write both the `.html` file and its `.html.meta` companion to this `tmp` directory.
3.  **Perform Atomic Move:** Only after both files have been successfully written to `tmp` should the system perform two `move` operations to transfer them into `work/parser/pending/`. This ensures the parser stage never sees an incomplete pair.
4.  **Ensure Cleanup:** Use a `try...finally` block to guarantee that the `tmp` directory is cleaned up, even if an error occurs after the files have been created but before they are moved.

### 4. CRUCIAL LESSONS LEARNED: A MANDATORY NEW WORKFLOW

Your predecessor made a series of repeated, preventable errors. These errors stemmed from a failure to manage the project's growing complexity and from making unverified assumptions. **You must adopt the following internal process to avoid repeating these failures. This is your primary directive.**

#### **The Core Failure: Lack of a Verification Checklist**
Your predecessor repeatedly generated code with subtle bugs related to syntax, scoping, and symbol naming. These errors were often "off-by-one" mistakes that a more rigorous process would have caught.

#### **The New Rule: The Mandatory Pre-Flight Checklist**
Before you emit any complete file for review, you must explicitly perform and confirm the following internal verification steps. This is non-negotiable.

1.  **Holistic Review for Every Edit:** When a bug is found or a change is requested, you must re-read and re-analyze the **entire file** from top to bottom. Do not "patch" a single line. Apply the lesson from the bug across the whole file to find other instances of the same error pattern.

2.  **Symbol Name Verification (`snake_case` vs. `kebab-case`):** This has been the single biggest source of bugs.
    *   **Action:** Explicitly trace the "life" of a variable. Identify where it is defined (e.g., from a YAML file as `:source_url`) and where it is bound to a Clojure symbol (e.g., `(let [source-url (:source_url data)] ...)`).
    *   **Action:** For the rest of the file, verify that every reference to that symbol uses the correct, idiomatic `kebab-case` form (`source-url`). Be vigilant about this distinction.

3.  **Macro Usage Verification:**
    *   **Action:** For any non-trivial macro (`->>`, `->`, `doseq`, `cond`, `let`), pause and mentally (or literally) expand the first few forms to ensure the argument order is correct.
    *   **Example:** For `(->> ... (map :keyword) (remove nil?))`, confirm that `remove` is receiving a sequence from `map`, not the other way around. Double-check parenthesis placement to ensure all forms are part of the macro's pipeline.

4.  **Propose Diagnostics, Not Guesses:** When faced with a runtime error whose cause is not immediately obvious from a stack trace, do not guess at a solution.
    *   **Action:** Your first proposal must be to create a temporary **diagnostic script**. This script's only purpose is to print the value and type of the variable(s) just before the line that is causing the error.
    *   **Action:** Analyze the output of this diagnostic script with the human developer to determine the ground truth. Only then should you propose a final fix.

This structured, verification-first process is essential for maintaining code quality and ensuring a productive partnership.

---

Please confirm you have read and understood these instructions. Then, we can begin implementing **Phase 3: Implement Atomic Fetches**.
