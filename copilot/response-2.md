Excellent. This HTML snippet is exactly what we need. Thank you for providing it.

Based on a thorough analysis of this HTML, I have documented a clear strategy for extracting the metadata and the body content. This fulfills **Step 2** of our plan.

---

### Documented Strategy for Content Extraction

Our analysis reveals that the Substack page structure is well-defined, using semantic HTML tags and specific class names that we can reliably target. Our strategy is divided into three main parts: Metadata Extraction, Content Cleanup, and Body Transformation.

**1. Metadata Extraction**

The most reliable source of metadata is not the visible HTML, but a JSON-LD script tag (`<script type="application/ld+json">`) present in the header. This is a structured data format designed for machines and is less likely to change than visual layout classes.

*   **Strategy:**
    1.  Select the `<script type="application/ld+json">` tag.
    2.  Extract its text content, which is a JSON string.
    3.  Parse this JSON string.
    4.  Extract the required fields to populate the TOML front matter:
        *   **`title`**: from the `headline` key.
        *   **`author`**: from the `author[0].name` key.
        *   **`publication_date`**: from the `datePublished` key. (This will need to be parsed and reformatted to `YYYY-MM-DD`).
        *   **`source_url`**: from the `url` key.

**2. Content Cleanup (Exclusions)**

Before we transform the article body, we must first remove all the unwanted elements (like/share buttons, subscribe forms, popups, etc.) that are nested within the main article container.

*   **Strategy:**
    *   First, we will select the main article container, which is `<article class="newsletter-post post">`.
    *   Within this container, we will use Enlive selectors to find and **delete** the following elements:
        *   Share popups: `[:div.share-dialog]`
        *   Subscribe widgets/forms: `[:div.subscribe-widget]`, `[:div.subscribe-footer]`
        *   Inline "Share" buttons: `[:p.button-wrapper]`
        *   The main post footer containing like/comment/share buttons: `[:div.post-footer]`
        *   Any remaining interactive UI elements that aren't part of the content.

**3. Body Transformation (HTML to Markdown)**

After cleaning the `<article>` element, we are left with the header and the main content body, located in `<div class="body markup">`. We will process the children of this container and convert them to Markdown.

*   **Strategy:** We will write a recursive function that walks the tree of cleaned HTML nodes and transforms each recognized element into its Markdown equivalent.

*   **Transformation Rules:**
    *   **Paragraphs:** `<p>` tags will be converted to plain text paragraphs, separated by newlines.
    *   **Headings:** `<h1>`, `<h2>`, `<h3>`, etc., will be converted to Markdown headings (`#`, `##`, `###`).
    *   **Links:** `<a>` tags will be converted to `[link text](href)`.
    *   **Blockquotes:** `<blockquote>` tags will be converted to `> quoted text`.
    *   **Horizontal Rules:** `<hr>` tags will be converted to `---`.
    *   **Lists:** `<ul>` and `<ol>` tags will be converted to standard Markdown bulleted and numbered lists, respectively, processing the `<li>` items within them.
    *   **Bold/Italics:** `<strong>`/`<b>` will become `**bold text**` and `<em>`/`<i>` will become `*italicized text*`.
    *   **Images:** `<img>` tags, which are typically wrapped in `<figure>` and `<a>`, will be handled as follows:
        1.  Extract the `src` attribute from the `<img>` tag. This is the image's source URL.
        2.  Extract the `alt` and `title` attributes for the Markdown equivalent: `![alt text](path "title text")`.
        3.  The image at the `src` URL will be downloaded.
        4.  The local path for the image will be generated according to the design spec (`hostname/filename.ext`).
        5.  The image metadata JSON file will be created.
    *   **Videos:** `<video>` or `<div data-component-name="VideoEmbedPlayer">` will be handled by creating a simple placeholder link in the Markdown, like: `[Embedded Video](<original_video_source_url>)`. Full video extraction is out of scope for the initial implementation.
    *   **All other tags** that are not explicitly recognized will have their text content extracted but their tags ignored, to prevent losing any text contained in unusual formatting.

---

This documented strategy is now our blueprint for **Step 3**, where we will write the Clojure code to implement this logic using Enlive.

We are ready to proceed to **Step 3**:

> 3) We will write code that applies our strategy to that first page we fetched. If it fails, we'll repeat our analysis. If it succeeds, we'll go on to the next step.

I will now provide an updated `src/tract/core.clj` that includes Enlive for parsing and implements the metadata extraction part of our strategy as a first concrete step.
