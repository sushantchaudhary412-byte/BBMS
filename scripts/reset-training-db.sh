#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
[ -f .env.training.local ] || { echo "Missing protected training environment file."; exit 1; }
[ "$(stat -f '%Lp' .env.training.local)" = 600 ] || { echo "Training credential file must have mode 600."; exit 1; }
set -a; . ./.env.training.local; set +a
[ "$TRAINING_DB_NAME" = bbms_training ] || { echo "Refusing any target other than bbms_training."; exit 1; }
identity=$(mysql --protocol=SOCKET --socket=/tmp/mysql.sock -u root -Nse "SELECT DATABASE()")
[ -z "$identity" ] || { echo "Unexpected selected schema; stop and inspect the root connection."; exit 1; }
printf 'This will permanently remove all rows in bbms_training only. Type bbms_training to continue: '
read -r confirm
[ "$confirm" = bbms_training ] || { echo "No changes made."; exit 1; }
mysql --protocol=SOCKET --socket=/tmp/mysql.sock -u root -e "DROP DATABASE bbms_training; CREATE DATABASE bbms_training CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
mysql --protocol=SOCKET --socket=/tmp/mysql.sock -u root bbms_training < src/main/resources/db/training/schema.sql
echo "Reset only bbms_training. The schema-scoped account and operational databases were not changed."
