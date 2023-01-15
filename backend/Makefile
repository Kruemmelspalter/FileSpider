build: app/build/distributions/app.tar

katex:
	mkdir -pv /tmp/katex katex
	curl $(shell npm view katex dist.tarball) --output - | tar -C /tmp/katex -xzvf - package/dist
	cp -r /tmp/katex/package/dist/* katex
	rm -r /tmp/katex

app/build/distributions/app.tar: $(shell find app/src/main/kotlin/me/kruemmelspalter/file_spider/backend -type f -name "*.kt") $(shell find app/src/main/resources -type f) app/build.gradle.kts katex
	./gradlew build

docker: app/build/distributions/app.tar
	docker build . -t ghcr.io/kruemmelspalter/filespider-backend:$(shell git rev-parse --short HEAD)

push: docker
	docker push ghcr.io/kruemmelspalter/filespider-backend:$(commitHash)

	docker tag ghcr.io/kruemmelspalter/filespider-backend:$(commitHash) ghcr.io/kruemmelspalter/filespider-backend:$(branchName)
	docker push ghcr.io/kruemmelspalter/filespider-backend:$(branchName)
	
ifeq "$(branchName)" "main"
	docker tag ghcr.io/kruemmelspalter/filespider-backend:$(commitHash) ghcr.io/kruemmelspalter/filespider-backend:latest
	docker push ghcr.io/kruemmelspalter/filespider-backend:main
endif
ifneq "$(tagName)" ""
	docker tag ghcr.io/kruemmelspalter/filespider-backend:$(commitHash) ghcr.io/kruemmelspalter/filespider-backend:$(tagName)
	docker push ghcr.io/kruemmelspalter/filespider-backend:$(tagName)
	docker tag ghcr.io/kruemmelspalter/filespider-backend:$(commitHash) ghcr.io/kruemmelspalter/filespider-backend:stable
	docker push ghcr.io/kruemmelspalter/filespider-backend:stable
endif


run: docker
	docker run --rm ghcr.io/kruemmelspalter/filespider-backend

lint:
	./gradlew ktlintKotlinScriptCheck

lint_fix: 
	./gradlew ktlintFormat
