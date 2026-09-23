#!/bin/sh
set -eu

npm ci --include=optional

exec "$@"
