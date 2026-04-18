#!/bin/sh
set -eu

test -f notex_storage/build/backend/notex-core.jar
test -f notex_storage/build/frontend/index.html

docker compose down
docker compose up -d
