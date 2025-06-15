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

# Runs the prune utility in safe, dry-run mode.
prune-ignored:
	clj -M:prune-ignored
# Runs the prune utility and PERMANENTLY DELETES ignored files.
prune-force:
	clj -M:prune-ignored --force
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
