#!/usr/bin/fish

podman run --name postgres-test \
  -e POSTGRES_PASSWORD=test \
  -e POSTGRES_USER=test \
  -e POSTGRES_DB=testdb \
  -p 5432:5432 \
  -d docker.io/library/postgres:17 \
  --replace
