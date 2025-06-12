.PHONY: test
run:
	clojure -M:run

test:
	clj -X:test

fetch:
	clj -M:fetch https://www.mind-war.com/p/testing-testing-los-angeles-under
parse:
	clj -M -m tract.stages.parser

discover:
	clj -M:discover
recover:
	clj -M:recover

chrome:
	google-chrome --remote-debugging-port=9222 --user-data-dir=/tmp/chrome-data &
