#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
file=.env.training.local
if [ -e "$file" ]; then
  printf '%s\n' "Refusing to overwrite existing $file. Preserve its credentials and inspect it locally."
  exit 1
fi
umask 077
password() { openssl rand -hex 24; }
admin_password=$(openssl rand -hex 32)
{
  printf 'TRAINING_DB_NAME=bbms_training\n'
  printf "TRAINING_DB_URL='jdbc:mysql://127.0.0.1:3306/bbms_training?createDatabaseIfNotExist=false&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'\n"
  printf 'TRAINING_DB_USER=bbms_training_app\n'
  printf 'TRAINING_DB_PASSWORD=%s\n' "$(password)"
  printf 'TRAINING_PORT=8082\n'
  printf 'TRAINING_ADMIN_EMAIL=bbms-training-admin@example.test\n'
  printf 'TRAINING_ADMIN_PASSWORD=%s\n' "$admin_password"
  printf 'TRAINING_DONOR_1_EMAIL=aditi.shah@training.example.test\n'
  printf 'TRAINING_DONOR_2_EMAIL=kavya.patel@training.example.test\n'
  printf 'TRAINING_DONOR_3_EMAIL=meera.joshi@training.example.test\n'
  printf 'TRAINING_DONOR_4_EMAIL=ananya.desai@training.example.test\n'
  printf 'TRAINING_DONOR_5_EMAIL=riya.mehta@training.example.test\n'
  printf 'TRAINING_DONOR_6_EMAIL=sneha.verma@training.example.test\n'
  printf 'TRAINING_DONOR_7_EMAIL=ishita.gupta@training.example.test\n'
  printf 'TRAINING_DONOR_8_EMAIL=tanvi.shah@training.example.test\n'
  printf 'TRAINING_DONOR_9_EMAIL=simran.kaur@training.example.test\n'
  printf 'TRAINING_DONOR_10_EMAIL=nisha.rao@training.example.test\n'
  for i in 1 2 3 4 5 6 7 8 9 10; do printf 'TRAINING_DONOR_%s_PASSWORD=%s\n' "$i" "$(password)"; done
  printf 'TRAINING_HOSPITAL_1_EMAIL=riddhi.hospitals@training.example.test\n'
  printf 'TRAINING_HOSPITAL_2_EMAIL=lifecare.hospital@training.example.test\n'
  printf 'TRAINING_HOSPITAL_3_EMAIL=citycare.hospital@training.example.test\n'
  printf 'TRAINING_HOSPITAL_4_EMAIL=sunrise.multispeciality@training.example.test\n'
  for i in 1 2 3 4; do printf 'TRAINING_HOSPITAL_%s_PASSWORD=%s\n' "$i" "$(password)"; done
} > "$file"
chmod 600 "$file"
printf '%s\n' "Created protected local training credentials at $PWD/$file (permissions 600). Values were not displayed."
