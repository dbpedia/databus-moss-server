.PHONY: test generate-api docker-build dev-up

test:
	mvn -q test

generate-api:
	mvn -q openapi-generator:generate

docker-build:
	docker build -t moss-server .

devenv-up:
	docker compose -f devenv/docker-compose.yml up -d
