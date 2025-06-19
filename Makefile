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
recover-md:
	clj -M:recover-md

chrome:
	google-chrome --remote-debugging-port=9222 --user-data-dir=/tmp/chrome-data &


pack:
	(for i in copilot/onboard-4.md README.md USAGE.md docs/DESIGN.md config.edn deps.edn Makefile ;\
	   do echo $$i; cat $$i; echo ---- ; done ;\
	echo Source files; echo -----; \
	cat src/tract/*.clj src/tract/stages/*.clj scripts/*.clj ) >~/tract-pack

