#!/bin/sh
set -eu

mkdir -p \
  notex_storage/build/frontend \
  notex_storage/build/backend \
  notex_storage/data/postgres \
  notex_storage/data/redis \
  notex_storage/runtime \
  notex_storage/files

rm -rf \
  notex_storage/build/frontend/* \
  notex_storage/build/backend/*

docker compose down
docker compose -f docker-compose.build.yaml build --no-cache
docker compose -f docker-compose.build.yaml up --force-recreate
docker compose down
docker compose up -d --force-recreate
