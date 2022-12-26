commitHash := $(shell git rev-parse --short HEAD)
branchName := $(shell git rev-parse --abbrev-ref HEAD)
tagName := $(shell git describe --tags)

lint:
	make -C frontend lint
	make -C backend lint

lint_fix:
	make -C frontend lint_fix
	make -C backend lint_fix

build:
	make -C frontend build
	make -C backend build

docker: build
	make -C backend docker

run: docker
	docker-compose up --force-recreate filespider 

push: docker
	docker push kruemmelspalter/filespider-backend:$(commitHash)

	docker tag kruemmelspalter/filespider-backend:$(commitHash) kruemmelspalter/filespider-backend:$(branchName)
	docker push kruemmelspalter/filespider-backend:$(branchName)
	
ifeq "$(branchName)" "main"
	docker tag kruemmelspalter/filespider-backend:$(commitHash) kruemmelspalter/filespider-backend:latest
	docker push kruemmelspalter/filespider-backend:main
endif
ifneq "$(tagName)" ""
	docker tag kruemmelspalter/filespider-backend:$(commitHash) kruemmelspalter/filespider-backend:$(tagName)
	docker push kruemmelspalter/filespider-backend:$(tagName)
	docker tag kruemmelspalter/filespider-backend:$(commitHash) kruemmelspalter/filespider-backend:stable
	docker push kruemmelspalter/filespider-backend:stable
endif
