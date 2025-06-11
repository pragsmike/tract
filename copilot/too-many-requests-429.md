`HTTP 429 “Too Many Requests”` is Substack’s way of telling your browser or script that it’s hitting the site harder or in a more suspicious pattern than the service (or the infrastructure it sits on) is willing to accept right now.
Here are the most common reasons and what you can do about them:

| What triggers the 429?                                                                                                                     | Why Substack (or the layer in front of it) cares                                                                                                                                                                                        | Typical fixes / work-arounds                                                                                                                                                                                    |
| ------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **You really are sending lots of traffic** – e.g. a migration script, an RSS–to–PDF tool, a crawler, dozens of tabs refreshing at once     | Substack enforces rate limits both at its own application layer and via Cloudflare’s global limit (≈1 200 requests per 5 min per user or 200 req/s per IP) to keep the service responsive for everyone ([developers.cloudflare.com][1]) | Throttle yourself (sleep/exponential back-off); reuse connections; batch work in smaller chunks (Ghost’s importer ultimately split its job into 20-post batches to stay under the limit) ([forum.ghost.org][2]) |
| **Anti-bot protection thinks you look automated** – headless browser, datacentre VPN, unusual headers, or an abnormally fast first request | Substack has an additional bot-mitigation layer (Kasada) that responds with 429 on the *very first* request until the browser proves it’s human ([substack.thewebscraping.club][3])                                                     | Use a real browser profile (JS + cookies), avoid datacentre IPs, slow initial page load, or simply read the post via email export instead of scraping                                                           |
| **Burst of identical API calls** – preview images, newsletter-signup endpoint in a loop, analytics plug-in stuck in retry                  | The platform blocks bursts to guard against accidental loops and brute-force attacks; Cloudflare documents that it will block for \~5 minutes once the ceiling is crossed ([developers.cloudflare.com][4])                              | Fix the loop or plug-in; check the `Retry-After` header and wait that long before retrying; add client-side caching                                                                                             |
| **Many users behind one NAT / campus Wi-Fi / corporate proxy**                                                                             | All traffic appears to come from one IP, so Substack’s rate-counter adds your requests to everyone else’s                                                                                                                               | Switch to a different network or mobile data; ask IT to split traffic across more external IPs                                                                                                                  |
| **Aggressive opening of every image/audio asset** by a feed reader or ad blocker                                                           | Each asset counts toward the per-IP limit                                                                                                                                                                                               | Tell the tool to respect robots.txt or Substack’s RSS, or throttle asset prefetching                                                                                                                            |

### How to diagnose

1. **Look at the response headers**

   * `Retry-After` tells you how long to back off.
   * `cf-ray`, `cf-cache-status`, or a `kasada-*` header reveals whether Cloudflare or Kasada blocked you.
2. **Check whether a single page load is spawning hundreds of sub-requests.** Browser dev-tools’ Network tab or `curl -v` helps spot loops.
3. **Verify you’re not sharing an IP with thousands of other users** (VPN, Tor exit, campus NAT).

### Good-citizen mitigation patterns

```bash
# Bash example: polite scraper
while read -r url; do
  curl -o "$(basename "$url").html" --compressed \
       --retry 5 --retry-delay 10 --retry-connrefused \
       --limit-rate 100k "$url"
  sleep 1   # keep well under 1 req/s
done < urls.txt
```

```python
# Python example: exponential back-off around 429
import requests, time, random, math

def get(url, tries=5):
    for n in range(tries):
        r = requests.get(url, timeout=30)
        if r.status_code != 429:
            return r
        wait = int(r.headers.get('Retry-After', 0)) or 2**n + random.random()
        time.sleep(wait)
    raise RuntimeError("Still rate-limited after %s tries" % tries)
```

### When it isn’t you

If you hit 429 while **just reading a Substack**:

* Disable browser extensions that prefetch or repeatedly reload content.
* Clear cookies/local-storage – corrupted session tokens can loop failed calls.
* Try a different network; public VPN exit-nodes are often on Cloudflare’s naughty list.
* If the block persists despite normal usage, email Substack support with the `cf-ray` ID shown on the error page so they can whitelist or adjust the rule.

---

**Bottom line:** 429 on Substack almost always means “slow down or look more human.” Respect the back-off, throttle your tool, or ask Substack for higher limits if you have a legitimate high-volume need.

[1]: https://developers.cloudflare.com/fundamentals/api/reference/limits/ "Rate limits · Cloudflare Fundamentals docs"
[2]: https://forum.ghost.org/t/substack-import-429-error/21297 "Substack import 429 error - Migrations - Ghost Forum"
[3]: https://substack.thewebscraping.club/p/scraping-a-kasada-website "Wanted a parka and got an \"Error 429: Too many requests\""
[4]: https://developers.cloudflare.com/support/troubleshooting/http-status-codes/4xx-client-error/error-429/ "Error 429 · Cloudflare Support docs"
