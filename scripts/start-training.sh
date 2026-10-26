#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
[ -f .env.training.local ] || { echo "Run scripts/init-training-env.sh and scripts/setup-training-db.sh first."; exit 1; }
[ "$(stat -f '%Lp' .env.training.local)" = 600 ] || { echo "Training credential file must have mode 600."; exit 1; }
unset DB_URL DB_USER DB_PASSWORD PORT BBMS_ADMIN_EMAIL BBMS_ADMIN_PASSWORD
unset PRESENTATION_DB_URL PRESENTATION_DB_USER PRESENTATION_DB_PASSWORD PRESENTATION_DB_NAME PRESENTATION_PORT
set -a; . ./.env.training.local; set +a
export SPRING_PROFILES_ACTIVE=training
exec java -jar target/bbms-1.0.0.jar "$@"
