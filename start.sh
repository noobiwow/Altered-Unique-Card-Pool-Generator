#!/bin/sh
set -e

java -jar /app/backend.jar --server.port=8081 &
nginx -g "daemon off;" &
wait -n
