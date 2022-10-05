sources = $(shell find assets static -type f) $(shell find pages components layouts -type f -name "*.vue")

build: dist/

dist/: $(sources)
	yarn generate

lint: $(sources)
	yarn lint

lint_fix: $(sources)
	yarn lint --fix