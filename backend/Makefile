build: app/build/distributions/app.tar

katex:
	mkdir -pv /tmp/katex katex
	curl $(shell npm view katex dist.tarball) --output - | tar -C /tmp/katex -xzvf - package/dist
	cp -r /tmp/katex/package/dist/* katex
	rm -r /tmp/katex

app/build/distributions/app.tar: $(shell find app/src/main/kotlin/me/kruemmelspalter/file_spider/backend -type f -name "*.kt") $(shell find app/src/main/resources -type f) app/build.gradle.kts ../frontend/dist katex
	./gradlew build

docker: app/build/distributions/app.tar
	docker build . -t filespider-backend

run: docker
	docker run --rm filespider-backend

lint:
	./gradlew ktlintKotlinScriptCheck

lint_fix: 
	./gradlew ktlintFormat
