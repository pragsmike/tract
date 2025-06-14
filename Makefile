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
populate-completed-log:
	clj -M:populate-completed-log
prune:
	clj -M:prune
migrate-log:
	clj -M:migrate-log

# Runs the deduplication tool in safe, dry-run mode.
dedupe-dry-run:
	clj -M:dedupe

# Runs the deduplication tool and PERMANENTLY DELETES duplicate files.
dedupe-force:
	clj -M:dedupe --force

chrome:
	google-chrome --remote-debugging-port=9222 --user-data-dir=/tmp/chrome-data &
