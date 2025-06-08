<img src="https://r2cdn.perplexity.ai/pplx-full-logo-primary-dark%402x.png" class="logo" width="120"/>

# Comprehensive Guide to Clojure Libraries for Throttled and Ban-Safe Web Scraping (Including Substack)

## Key Findings Summary

Clojure offers a robust ecosystem for building web scrapers that respect rate limits and avoid detection, particularly when targeting platforms like Substack. The language’s functional programming paradigm and Java interoperability enable developers to combine specialized libraries for HTTP handling, HTML parsing, rate limiting, and anti-bot mitigation. This report synthesizes 20+ sources to identify optimal tools and strategies for Substack scraping while adhering to ethical and technical constraints[^1][^2][^6][^13].

---

## HTTP Client Libraries with Throttling Support

### **Hato**

A modern HTTP client wrapping JDK 11’s HttpClient, supporting both synchronous/asynchronous requests and HTTP/2. Key features for scraping:

- Built-in connection pooling and request throttling via `build-http-client`
- Configurable timeouts and redirect policies to mimic human behavior[^7]
- Native proxy support through Java’s `ProxySelector` interface

Example configuration for Substack:

```clojure
(def substack-client 
  (hato.client/build-http-client 
    {:connect-timeout 5000
     :redirect-policy :normal
     :proxy (java.net.ProxySelector/getDefault)}))
```


### **clj-http**

The de facto standard Clojure HTTP client with mature proxy and throttling integrations:

- Direct proxy configuration per request:

```clojure
(client/get "https://substack.com" 
  {:proxy-host "127.0.0.1" 
   :proxy-port 8118
   :socket-timeout 10000}) [^14]
```

- Automatic cookie management to persist sessions
- Chunked encoding support for large Substack article bodies[^17]

---

## HTML Parsing and DOM Navigation

### **Reaver**

JSoup wrapper optimized for data extraction from HTML:

- CSS selector syntax with `extract-from` macro
- Converts HTML tables to Clojure vectors automatically
- Handles malformed HTML common in older Substack themes[^19]

Substack scraping example:

```clojure
(extract-from (reaver/parse html-content)
  ".post" [:title :content :date]
  "h1.post-title" text
  "div.post-content" text
  "time.post-date" (attr :datetime))
```


### **Enlive**

Templating-focused library with powerful selector engine:

- `select` function uses CSS-like syntax for DOM traversal
- Built-in text extraction and attribute parsing
- Used in production by large-scale scrapers handling 10k+ pages/day[^1][^15]

---

## Rate Limiting and Request Throttling

### **Throttler**

Implements token bucket algorithm for precise rate control:

- Throttle individual functions or core.async channels
- Shared rate pools for coordinated scraping across multiple Substack domains

```clojure
(def throttled-get 
  (throttler/throttle-fn 
    #(hato.client/get %) 
    50 :second)) ; 50 req/sec [^10][^11]
```


### **clj-rate-limiter**

Redis-backed distributed rate limiting:

- Rolling time windows prevent sudden burst bans
- Configurable burst capacity for Substack’s API-like endpoints

```clojure
(def limiter 
  (rate-limiter/create 
    :substack-api 
    {:limit 100 :interval 60})) ; 100 req/min[^16]
```


### **Cooler**

GCRA algorithm implementation for distributed systems:

- Redis-backed tracking of request timestamps
- Automatic retry-after calculations compatible with Substack’s 429 responses[^3]

---

## Anti-Ban Strategies and Tools

### **Safely**

Circuit breaker pattern with adaptive retry policies:

- Exponential backoff with jitter for Substack’s anti-DDoS systems
- Automatic failure rate tracking across scraping nodes

```clojure
(safely 
  (fetch-substack-article url)
  :max-retries 5
  :retry-delay [:random-exp-backoff :base 1000 :+/- 0.3]
  :circuit-breaker :substack-crawler)[^12]
```


### **Proxy Rotation**

Integration patterns with residential proxies:

1. **Proxy middleware for clj-http**:

```clojure
(defn rotating-proxy-request [req]
  (let [proxy (get-next-proxy-from-pool)]
    (assoc req 
      :proxy-host (:host proxy)
      :proxy-port (:port proxy))))
```

2. **Tor network integration** using `tor-clj` library
3. **AWS Lambda rotation** via `amazonica` for IP cycling[^6]

---

## JavaScript Rendering and SPAs

### **Abrade**

HtmlUnit-based scraper for JS-heavy Substack pages:

- Full browser emulation with CSS/JS execution
- Headless mode support for server environments

```clojure
(abrade/with-browser :chrome
  (abrade/open "https://substack.com")
  (abrade/wait-for "#posts-list")
  (abrade/html))
```


### **Etaoin**

WebDriver protocol implementation:

- Chrome/Firefox automation with human-like interaction timings
- Screenshot-based debugging for anti-bot challenges

```clojure
(def driver (etaoin/start-chrome))
(etaoin/go driver "https://substack.com")
(let [posts (etaoin/query-all driver {:class "post"})]
  (map etaoin/get-text-el posts))
```


---

## Substack-Specific Considerations

### **Architecture Patterns**

1. **Incremental scraping**:
    - Use Substack’s `/archive` pagination with exponential backoff
    - Track `Last-Modified` headers to avoid redundant requests
2. **Atom feed parsing**:

```clojure
(feed/parse-substack-feed 
  "https://user.substack.com/feed")
```

3. **Image handling**:
    - Lazy-load avoidance with `window.scroll` emulation
    - Referer header spoofing for hotlinked assets

### **Ethical Guidelines**

1. Respect `robots.txt`:

```clojure
(GET "https://substack.com/robots.txt")
```

2. Honor `X-Robots-Tag` headers in responses
3. Limit concurrent connections to 1 per 2 seconds minimum
4. Avoid subscriber-only content scraping[^2][^13]

---

## Performance Optimization

### **Parallelism Control**

```clojure
(->> (pmap throttled-get urls)
     (map parse-substack-html)
     (pmap #(db/insert! :articles %)))
```

1. **core.async pipelines**:

```clojure
(async/pipeline-blocking 5 ; 5 threads
  output-chan
  (comp (map fetch-page)
        (map parse-content))
  input-chan)
```

2. **Java ExecutorServices**:

```clojure
(doto (Executors/newFixedThreadPool 10)
  (.invokeAll (map->callables scraping-tasks)))
```


### **Caching Strategies**

1. **SQLite journal mode**:

```clojure
(jdbc/execute! db ["PRAGMA journal_mode = WAL"])
```

2. **Bloom filter deduplication**:

```clojure
(def url-filter (bloom/bloom-filter 1000000 0.01))
```


---

## Monitoring and Alerting

### **Metrics Collection**

```clojure
(def metrics 
  (atom {:requests 0
         :failures 0
         :retries 0}))

(add-watch metrics :alerting
  (fn [_ _ old new]
    (when (> (:failures new) 100)
      (alert/send! "High failure rate detected"))))
```


### **Prometheus Integration**

```clojure
(def scraper-metrics
  (prometheus/registry
    [(counter :requests_total "Total requests")
     (histogram :request_duration_seconds "Timings")]))
```


---

## Legal and Compliance

### **GDPR Considerations**

1. **Data minimization**:

```clojure
(select-keys article-data [:title :public_date :excerpt])
```

2. **Right to be forgotten**:

```clojure
(defn delete-user-data [user-id]
  (db/delete! :scraped-data {:user-id user-id}))
```


### **DMCA Compliance**

- Automated takedown request detection:

```clojure
(when (re-find #"DMCA" content)
  (log/warn "DMCA notice detected in" url))
```


---

## Conclusion and Recommendations

For Substack scraping in Clojure, adopt a layered architecture combining:

1. **Hato/clj-http** for HTTP with rotating proxies
2. **Throttler + Safely** for adaptive rate control
3. **Reaver/Enlive** for HTML parsing
4. **Abrade** for JavaScript rendering when required

Prioritize using Substack’s official API where available, and always verify compliance with their Terms of Service. For large-scale operations, consider distributed scraping using Onyx or Apache Storm with the listed libraries.

<div style="text-align: center">⁂</div>

[^1]: https://www.reddit.com/r/Clojure/comments/34egzh/why_use_clojure_for_web_scraping/

[^2]: https://github.com/bytewife/substack_scraper

[^3]: https://github.com/dparis/cooler

[^4]: https://github.com/MailOnline/throttle

[^5]: https://github.com/liwp/again

[^6]: https://stackoverflow.com/questions/4868331/could-a-web-scraper-get-around-a-good-throttle-protection

[^7]: https://github.com/gnarroway/hato

[^8]: https://clojureverse.org/t/does-clojure-have-scrapy-like-framework-for-web-scraping/3957

[^9]: https://stackoverflow.com/questions/34691682/some-questions-regarding-a-simple-command-line-web-scraper-clojure-clojurescrip

[^10]: http://brunov.org/clojure/2014/05/14/throttler/

[^11]: https://github.com/brunoV/throttler

[^12]: https://cljdoc.org/d/com.brunobonacci/safely

[^13]: https://www.scrapingdog.com/blog/how-to-avoid-getting-blocked-while-scraping/

[^14]: https://stackoverflow.com/questions/35026145/how-to-specify-http-or-https-proxy-in-clj-http-in-clojure

[^15]: https://practical.li/clojure-data-science/data-mining/webscraping/

[^16]: https://clojars.org/clj-rate-limiter

[^17]: https://github.com/dakrone/clj-http

[^18]: https://github.com/weavejester/abrade

[^19]: https://github.com/mischov/reaver

[^20]: https://nextjournal.com/avidrucker/webscraping-and-data-cleaning-in-clojure-for-nlp

[^21]: https://esrh.me/posts/2023-03-23-clojure

[^22]: https://github.com/practicalli/webscraper

[^23]: https://github.com/sunng87/diehard

[^24]: https://news.ycombinator.com/item?id=7748041

[^25]: https://www.peterjewicz.com/rate-limiting-pedestal-clojure/

[^26]: https://lethain.com/scalable-scraping-in-clojure/

[^27]: https://www.slideshare.net/slideshow/web-scraping-technologies/56093898

[^28]: https://github.com/dparis/clj-http-proxy

[^29]: https://clojureverse.org/t/clj-to-use-a-non-default-maven-repository/11028

[^30]: https://www.promptcloud.com/blog/web-scraping-without-getting-blocked-or-banned/

[^31]: https://www.scrapeless.com/en/blog/rate-limiting

[^32]: https://clojureverse.org/t/first-time-webscraper-could-you-give-any-pointers/7663

[^33]: https://www.reddit.com/r/Clojure/comments/35f9t7/a_streaming_proxy_handler_for_httpkit_using/

[^34]: https://rymndhng.github.io/2020/04/15/production-considerations-for-clj-http/

[^35]: https://cljdoc.org/d/clj-http/clj-http/0.7.6

[^36]: https://cljdoc.org/d/http.async.client/http.async.client/1.4.0/api/http.async.client.request

[^37]: https://yannesposito.com/posts/0025-a-quick-cors-proxy-in-a-few-lines-of-clojure/index.html

