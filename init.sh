#!/bin/sh
set -eu

if [ ! -f .env ]; then
  cp .env.example .env
fi

mkdir -p \
  notex_storage/build/frontend \
  notex_storage/build/backend \
  notex_storage/data/postgres \
  notex_storage/data/redis \
  notex_storage/runtime \
  notex_storage/files

docker compose -f docker-compose.build.yaml build
docker compose -f docker-compose.build.yaml up
docker compose down
docker compose up -d
