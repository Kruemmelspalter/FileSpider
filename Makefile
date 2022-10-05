lint:
	make -C frontend lint
	make -C backend lint

lint_fix:
	make -C frontend lint_fix
	make -C backend lint_fix

build:
	make -C frontend build
	make -C backend build

docker:
	make -C frontend build
	make -C backend docker

run:
	make -C frontend build
	make -C backend run