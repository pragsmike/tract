.PHONY: test
run:
	clojure -M:run

test:
	clj -X:test

discover:
	clj -M:discover
recover:
	clj -M:recover
backfill-meta:
	clj -M:backfill-meta

chrome:
	google-chrome --remote-debugging-port=9222 --user-data-dir=/tmp/chrome-data &
