app/build/distributions/app.tar: $(shell find app/src/main/kotlin/me/kruemmelspalter/file_spider/backend -type f -name "*.kt") $(shell find app/src/main/resources -type f)
	./gradlew build

docker: app/build/distributions/app.tar
	docker build . -t filespider-backend

run: docker
	docker run --rm filespider-backend