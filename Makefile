.PHONY: test
run:
	clojure -M:run

test:
	clj -X:test

discover:
	clj -M:discover

recover:
	clj -M:recover

# Runs the prune utility in safe, dry-run mode.
prune-ignored:
	clj -M:prune-ignored
# Runs the prune utility and PERMANENTLY DELETES ignored files.
prune-force:
	clj -M:prune-ignored --force

recover-url-map:
	clj -M:recover-url-map

chrome:
	google-chrome --remote-debugging-port=9222 --user-data-dir=/tmp/chrome-data &
