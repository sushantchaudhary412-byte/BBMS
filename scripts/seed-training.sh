#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
[ -f .env.training.local ] || { echo "Run scripts/init-training-env.sh and scripts/setup-training-db.sh first."; exit 1; }
echo "Starting BBMS on port 8082 and running the idempotent training seed. Keep this terminal open; use Ctrl-C to stop the training app."
exec ./scripts/start-training.sh --bbms.training.provision-admin=true --bbms.training.seed=true
