.PHONY: test
run:
	clojure -M:run

test:
	clj -X:test

fetch:
	clj -M:fetch https://www.mind-war.com/p/testing-testing-los-angeles-under
