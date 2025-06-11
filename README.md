# `tract`: A Substack Content Extractor

> **Note:** `tract` is a tool for power users and developers. It requires comfort with the command line, editing configuration files (YAML), and managing a multi-step workflow. If you are looking for a "no-code" or simple browser-extension-based scraping solution, this is not it!

`tract` is a command-line application designed to extract content from web articles, primarily targeting Substack publications. It converts articles into clean, LLM-ingestible formats: a Markdown file for the text content and a well-organized collection of all associated images and their metadata.

The tool is built as a robust, resilient, file-based pipeline, capable of handling both public and authenticated (subscriber-only) content.

## Table of Contents
- [Why Use `tract`?](#why-use-tract)
- [Features](#features)
- [How It Works: The Pipeline](#how-it-works-the-pipeline)
- [Installation and Dependencies](#installation-and-dependencies)
- [How to Run `tract`](#how-to-run-tract)
- [Limitations and Important Considerations](#limitations-and-important-considerations)
- [Alternatives & Further Reading](#alternatives--further-reading)

## Why Use `tract`?

The primary goal of `tract` is to prepare web content for analysis by Large Language Models (LLMs). Raw HTML is noisy and contains many elements (ads, navigation, scripts) that are irrelevant to the core content. `tract` provides a clean, structured, and repeatable way to:

-   Extract the body text of an article into clean Markdown.
-   Download all images referenced in an article.
-   Capture rich metadata for both the article and each image reference (author, date, source URL, caption, etc.).
-   Access and archive subscriber-only content to which you have legitimate access.
-   Process large batches of URLs discovered from RSS/Atom feeds.

## Features

-   **File-Based Pipeline:** A resilient, multi-stage architecture (`job` -> `fetch` -> `parser`) that allows for easy debugging and re-running failed steps.
-   **Multiple Job Sources:** Can be initiated from a simple list of URLs, a Substack author's name, or any standard Atom/RSS feed.
-   **Authenticated Fetching:** Uses a persistent, pre-authenticated browser session to access subscriber-only content and to appear as a legitimate user, minimizing the risk of being blocked.
-   **Polite Throttling:** Implements randomized delays between all network requests to avoid overwhelming the target servers.
-   **Robust Error Handling:** Failed jobs are automatically moved to an `error` directory with a detailed report, allowing the rest of a batch to complete successfully.
-   **LLM-Ready Output:** Produces clean Markdown with TOML front matter and separate JSON metadata for all images.

## How It Works: The Pipeline

`tract` operates as a series of independent stages that communicate via files in the `work/` directory.

1.  **Job Stage:** You create a `.yaml` file in `work/job/pending/` describing the articles you want to fetch. This stage parses the job file, discovers all the article URLs (e.g., by fetching an RSS feed), and writes them to a `.txt` file for the next stage.
2.  **Fetch Stage:** This stage reads the list of URLs. It uses an automated browser (`etaoin`) connected to your persistent, logged-in browser session to visit each URL and save the complete HTML to a file for the next stage.
3.  **Parser Stage:** This stage reads each `.html` file. It uses an HTML parsing library (`enlive`) to intelligently extract the article's metadata and body content, convert it to Markdown, and download all associated images. The final artifacts are placed in the `work/3-processed/` directory.

## Installation and Dependencies

`tract` is a Clojure application and requires a working Java Development Kit (JDK) and the Clojure CLI tools.

1.  **Install Java:** A recent JDK (version 11 or higher) is recommended.
2.  **Install Clojure:** Follow the official instructions at [clojure.org/guides/getting_started](https://clojure.org/guides/getting_started).
3.  **Install a WebDriver:** `tract` uses a real browser to fetch pages. You must install `chromedriver` and ensure it is on your system's `PATH`.
    -   Find your Chrome version (`chrome://settings/help`).
    -   Download the matching `chromedriver` from the [Chrome for Testing availability dashboard](https://googlechromelabs.github.io/chrome-for-testing/).
4.  **Clone this repository:**
    ```bash
    git clone https://github.com/your-repo/tract.git
    cd tract
    ```

## How to Run `tract`

`tract` relies on a persistent browser session for robust, authenticated fetching. This requires a **one-time manual setup.**

#### Step 1: Launch Your Persistent Browser (One-Time Setup)

You must start a Chrome instance with its remote debugging port enabled. Find the path to your Chrome executable and run the following command in a separate terminal.

*On WSL2, referencing the Windows executable:*
```bash
/mnt/c/Program\ Files/Google/Chrome/Application/chrome.exe --remote-debugging-port=9222
```
*On macOS:*
```bash
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --remote-debugging-port=9222
```
*On Linux:*
```bash
google-chrome --remote-debugging-port=9222
```

A new Chrome window will open. In this window, **manually log into your Substack account**. You can then minimize this browser window and leave it running in the background. `tract` will connect to it for all future runs.

#### Step 2: Create a Job

Create a `.yaml` file in the `work/job/pending/` directory. For example, to fetch articles from an author's feed:

```yaml
# work/job/pending/my-author-job.yaml
author: "www.mind-war.com"
```

Or to fetch a specific list of URLs:

```yaml
# work/job/pending/my-url-job.yaml
urls:
  - "https://www.theverge.com/some-article"
  - "https://some-author.substack.com/p/another-article"
```

#### Step 3: Run the Pipeline

From the project's root directory, execute the application:

```bash
clj -M:run
```

The application will run through all the pipeline stages. Final output will appear in the `work/3-processed/` directory.

## Limitations and Important Considerations

### User Responsibility
This tool makes network requests to third-party websites. Although `tract` strives to be a good citizen by implementing polite throttling, **you are ultimately responsible for how you use this tool.** Abusing this tool to violate terms of service or overwhelm a website is strongly discouraged. Use it responsibly and ethically.

### Authentication
The current authentication method relies on you maintaining a logged-in session in the persistent browser instance. The tool does not store your credentials directly. You must use a Substack account that is configured with a password.

### Parser Specificity
The HTML parser is heavily optimized for the structure of Substack pages. While it has fallbacks and can often extract content from other sites, its performance on non-Substack pages may be degraded. The metadata or body content may not be as clean.

## Alternatives & Further Reading

-   **[SingleFile](https://github.com/gildas-lormeau/SingleFile):** A browser extension that saves a complete webpage into a single, self-contained HTML file. Excellent for archiving individual pages with perfect fidelity.
-   **[Scrapy](https://scrapy.org/):** A powerful, full-featured Python framework for large-scale web scraping. It's a much more complex tool but offers immense power and flexibility.
-   **[Reading about Web Scraping Etiquette](https://www.zenrows.com/blog/web-scraping-etiquette):** It is important to understand the best practices for scraping websites politely and ethically.
-   **[The Web Scraping Club](https://substack.thewebscraping.club/):** A Substack publication about techniques and tools for extracting content from web sites.
