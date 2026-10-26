#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
file=.env.training.local
[ -f "$file" ] || { echo "Run scripts/init-training-env.sh first."; exit 1; }
[ "$(stat -f '%Lp' "$file")" = 600 ] || { echo "Training credential file must have mode 600."; exit 1; }
set -a; . "./$file"; set +a
[ "$TRAINING_DB_NAME" = bbms_training ] || { echo "Training database name must be bbms_training."; exit 1; }
[ "$TRAINING_DB_USER" = bbms_training_app ] || { echo "Training account name must be bbms_training_app."; exit 1; }
MYSQL="mysql --protocol=SOCKET --socket=/tmp/mysql.sock -u root"
schema_exists=$($MYSQL -Nse "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='bbms_training'")
if [ "$schema_exists" -eq 0 ]; then
  $MYSQL -e "CREATE DATABASE bbms_training CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
  mysql --protocol=SOCKET --socket=/tmp/mysql.sock -u root bbms_training < src/main/resources/db/training/schema.sql
  echo "Created an empty bbms_training schema from reviewed DDL."
else
  table_count=$($MYSQL -Nse "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='bbms_training' AND TABLE_TYPE='BASE TABLE'")
  [ "$table_count" -eq 10 ] || { echo "Existing bbms_training schema is not the expected ten-table schema; no changes made to it."; exit 1; }
  table_names=$($MYSQL -Nse "SELECT GROUP_CONCAT(TABLE_NAME ORDER BY TABLE_NAME) FROM information_schema.TABLES WHERE TABLE_SCHEMA='bbms_training' AND TABLE_TYPE='BASE TABLE'")
  [ "$table_names" = "app_users,blood_issues,blood_request_events,blood_requests,blood_units,donation_appointment_events,donation_appointments,donation_records,donation_reminders,inventory_movements" ] || { echo "Existing bbms_training table names differ from the reviewed schema; no changes made."; exit 1; }
  echo "bbms_training already has ten tables; no schema statements were run."
fi
user_exists=$($MYSQL -Nse "SELECT COUNT(*) FROM mysql.user WHERE User='bbms_training_app' AND Host='127.0.0.1'")
if [ "$user_exists" -eq 0 ]; then
  printf "CREATE USER 'bbms_training_app'@'127.0.0.1' IDENTIFIED BY '%s';\nGRANT SELECT, INSERT, UPDATE ON bbms_training.* TO 'bbms_training_app'@'127.0.0.1';\n" "$TRAINING_DB_PASSWORD" | $MYSQL
  echo "Created the dedicated training account with schema-scoped SELECT, INSERT, UPDATE."
else
  grantee="CONCAT(CHAR(39),'bbms_training_app',CHAR(39),'@',CHAR(39),'127.0.0.1',CHAR(39))"
  globals=$($MYSQL -Nse "SELECT COUNT(*) FROM information_schema.USER_PRIVILEGES WHERE GRANTEE=$grantee AND PRIVILEGE_TYPE<>'USAGE'")
  schema_privs=$($MYSQL -Nse "SELECT GROUP_CONCAT(PRIVILEGE_TYPE ORDER BY PRIVILEGE_TYPE) FROM information_schema.SCHEMA_PRIVILEGES WHERE GRANTEE=$grantee")
  other_schema=$($MYSQL -Nse "SELECT COUNT(*) FROM information_schema.SCHEMA_PRIVILEGES WHERE GRANTEE=$grantee AND TABLE_SCHEMA<>'bbms_training'")
  table_privs=$($MYSQL -Nse "SELECT COUNT(*) FROM information_schema.TABLE_PRIVILEGES WHERE GRANTEE=$grantee")
  column_privs=$($MYSQL -Nse "SELECT COUNT(*) FROM information_schema.COLUMN_PRIVILEGES WHERE GRANTEE=$grantee")
  [ "$globals" -eq 0 ] && [ "$schema_privs" = "INSERT,SELECT,UPDATE" ] && [ "$other_schema" -eq 0 ] && [ "$table_privs" -eq 0 ] && [ "$column_privs" -eq 0 ] || { echo "Existing account grants exceed or differ from the approved minimum; no grants changed."; exit 1; }
  echo "Existing training account grants match the approved schema-scoped minimum; no grants changed."
fi
echo "Schema provisioning finished. Runtime account is not granted DDL or DELETE."
