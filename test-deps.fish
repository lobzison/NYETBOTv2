#!/usr/bin/fish

ollama serve > /dev/null 2>&1 &
podman start postgres-test
