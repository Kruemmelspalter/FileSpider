commitHash := $(shell git rev-parse --short HEAD)
branchName := $(shell git rev-parse --abbrev-ref HEAD)
tagName := $(shell git describe --abbrev=0)

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
