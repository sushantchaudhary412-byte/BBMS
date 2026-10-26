# BBMS — Blood Bank Management System

Local Java/Spring MVC application for blood inventory, donation appointments and records, and hospital requests. Stack: Java 21, Spring Boot 3.4, Thymeleaf, Spring Security, JPA/Hibernate, MySQL, and Maven. The interface uses a local responsive CSS design system without runtime CDN dependencies.

> **Academic prototype.** This application is not certified for real-world blood bank operations. It does not provide medical advice or determine clinical compatibility. Qualified staff must make all clinical decisions.

## Local Demo Setup (for Group Members)

1. **Database Creation:** Open a MySQL terminal as root and run the following. **IMPORTANT: Replace `<your-strong-password>` with a unique, strong password of your choice before executing.**
   ```sql
   CREATE DATABASE bbms_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'bbms_demo_app'@'localhost' IDENTIFIED BY '<your-strong-password>';
   GRANT ALL PRIVILEGES ON bbms_demo.* TO 'bbms_demo_app'@'localhost';
   FLUSH PRIVILEGES;
   ```
   *Note: `GRANT ALL PRIVILEGES` is used strictly on the isolated `bbms_demo` schema to allow Hibernate `ddl-auto=update` to manage tables. This grants the app user permission to create and drop objects within the demo database only.*

2. **Environment Configuration:** In your terminal, run the following commands:
   ```zsh
   export DB_URL='jdbc:mysql://localhost:3306/bbms_demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
   export DB_USER='bbms_demo_app'
   read -s 'DB_PASSWORD?Enter your chosen bbms_demo_app password: '
   echo
   export DB_PASSWORD
   export PORT=8080
   ```

3. **Launch:**
   ```bash
   mvn spring-boot:run
   ```

## Create the initial Admin

Public registration offers exactly **Blood donor** and **Hospital / requester**. It rejects `ADMIN` even if submitted directly. Provision the first Admin with environment variables before starting Spring Boot:

```bash
export BBMS_ADMIN_EMAIL='admin@your-local-domain.test'
export BBMS_ADMIN_PASSWORD='your-own-random-password-at-least-12-characters'
```

The startup initializer BCrypt-hashes the password and creates the account only when that email does not already exist. It does not reset or overwrite an existing account. Keep the variables private. If an Admin already exists, use its existing credentials; this bootstrap is not a password-reset mechanism.

## Add a new local Admin when the existing password is unknown

Use a new, unused email address; the original Admin and all other rows are left in place. Check the address before restarting, because bootstrap intentionally does nothing when any account already has that email:

```bash
mysql -h localhost -u bbms_app -p bbms_local -Nse "SELECT COUNT(*) FROM app_users WHERE LOWER(email)=LOWER('bbms-admin-setup-20261025@example.test');"
```

The result must be `0`. If it is not, choose another unused address and repeat the check. In the same shell where the existing database environment is configured, set the new email and enter a new password at a hidden prompt (macOS default zsh syntax):

```zsh
export DB_URL='jdbc:mysql://localhost:3306/bbms_local?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USER='bbms_app'
read -s 'DB_PASSWORD?Existing bbms_app database password: '
print
export DB_PASSWORD
export BBMS_ADMIN_EMAIL='bbms-admin-setup-20261025@example.test'
read -s 'BBMS_ADMIN_PASSWORD?New local Admin password (12–72 ASCII characters): '
print
export BBMS_ADMIN_PASSWORD
# Stop the current BBMS process first if it is already using port 8080.
mvn spring-boot:run
```

Do not type the password into the command itself or send it in chat. The app must be restarted for bootstrap to run. After startup, verify the row without selecting its password column:

```bash
mysql -h localhost -u bbms_app -p bbms_local -e "SELECT email, role, enabled FROM app_users WHERE LOWER(email)=LOWER('bbms-admin-setup-20261025@example.test');"
```

Expect the chosen email, `ADMIN`, and `1`. Then open `http://localhost:8080/login` and sign in with the new email and the password entered at the hidden prompt. A successful sign-in redirects to `/admin/dashboard`. This creates a second Admin; it does not recover or change the original account.

## Run the application

Set the database variables in the same shell (replace the password locally; do not paste it into source files):

```bash
export DB_URL='jdbc:mysql://localhost:3306/bbms_local?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USER='bbms_app'
export DB_PASSWORD='the-password-configured-for-bbms_app'
export PORT=8080
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). In IntelliJ IDEA or Eclipse, import this folder as a Maven project and set the same variables in the run configuration for `com.bbms.BbmsApplication`.

Build and test:

```bash
mvn test
mvn package
```

JPA uses Hibernate `ddl-auto=update` for this local prototype. Entity changes are additive where possible: for example, the donor blood-group column and donation-record table are added without dropping existing rows. A legacy request stored with priority `EMERGENCY` remains unchanged in the database and is displayed as **Urgent**; new requests accept only **Routine** or **Urgent**. Back up important data before applying application schema updates.

## Isolated presentation profile (setup is not yet provisioned)

The `presentation` profile is reserved for a separate, synthetic-data database. It requires `PRESENTATION_DB_URL`, `PRESENTATION_DB_NAME`, `PRESENTATION_DB_USER`, and `PRESENTATION_DB_PASSWORD`; it has no `bbms_local` fallback. Its JDBC URL must explicitly include the expected database and `createDatabaseIfNotExist=false`. Startup checks both the configured URL and the connected `SELECT DATABASE()` identity, and refuses `bbms_local` or any mismatch. The profile uses port `8081` by default and Hibernate `ddl-auto=validate`; it cannot create or update the schema. Its settings are in [application-presentation.properties](src/main/resources/application-presentation.properties) and placeholders are in [.env.presentation.example](.env.presentation.example). Spring does not load either example file automatically.

Do not start this profile until a dedicated presentation schema and restricted user have been created and the schema has been provisioned through a reviewed migration/restore plan. No presentation database, user, grant, migration, or records have been created by this safeguard change. The operational startup runners for constraint changes, Admin bootstrap, and history backfill are disabled under the presentation profile; ordinary application behavior remains unchanged when the profile is not active.

For a future presentation-only MySQL account, the minimum runtime grants are `SELECT`, `INSERT`, and `UPDATE` on `bbms_presentation.*`. Do not grant access to `bbms_local`, and do not grant `CREATE`, `ALTER`, `DROP`, `INDEX`, or global privileges to the app account. Database/schema creation and schema provisioning must be performed separately by an authorized setup account. During the future setup, verify with `SHOW GRANTS FOR ...`, confirm the app account cannot select from `bbms_local`, and verify the presentation app reports only `bbms_presentation` through its guarded connection check. Stop setup if any of these checks fail.

Future launch outline after the separate schema and restricted account have been approved and prepared:

```zsh
set -a
source .env.presentation.example
set +a
read -s 'PRESENTATION_DB_PASSWORD?Presentation database password: '
print
export PRESENTATION_DB_PASSWORD
export SPRING_PROFILES_ACTIVE=presentation
mvn spring-boot:run
```

The example URL and account name are placeholders for that future setup. This launch outline does not create the schema or prove the MySQL grant boundary; the connected identity guard is an additional safeguard, not a substitute for restricted database privileges.

## Features

- **Admin:** dashboard counts, donor/requester access management, batch inventory, donation records, appointment review, request approval/rejection, atomic issue transactions, low-stock/near-expiry alerts, and operational reports.
- **Donor:** registration with blood group, own profile editing, appointment requests/status, and donation history.
- **Hospital/requester:** organization registration/profile, blood request submission, own request history/status.
- **Security:** BCrypt password hashes, server-side role guards, CSRF protection for state-changing forms, public roles restricted to DONOR/HOSPITAL, and owner-scoped request/appointment/donation history.
- **Inventory:** blood group, component, batch, collection/expiry dates, testing/release status, and quantity. Issuance locks the request and eligible stock, consumes earliest expiry first, rejects unavailable/unreleased/expired stock, and records only one issue per request.

Operational priority is **Routine** or **Urgent**. Urgent is the expedited handling label and may cover a request reported as an emergency. The application does not determine clinical urgency; qualified medical staff do. It does not make compatibility decisions.

## Project modules

- `src/main/java/com/bbms/domain` — JPA entities
- `src/main/java/com/bbms/repo` — Spring Data repositories
- `src/main/java/com/bbms/service` — account registration and transactional issuance
- `src/main/java/com/bbms/web` — routes and form binding
- `src/main/resources/templates` — role pages and shared fragments
- `src/main/resources/static` — responsive design system and table/confirmation behavior
- `src/test/java` — unit tests and MVC/workflow integration tests isolated on a guarded in-memory H2 database

## Limitations

- A completed donation record is tracked separately. Staff must enter a tested/released batch into inventory after the appropriate checks; creating a donation record does not add stock.
- The prototype does not store donor eligibility or medical screening information and is not a certified production system.
- An exhausted inventory batch may have quantity zero after issue; zero quantity is not counted as available stock. On startup, the application updates the legacy `quantity >= 1` check constraint to `quantity >= 0` when that exact legacy constraint is present. The migration preserves all existing batch rows and quantities.

## Existing archived records

BBMS does not seed sample accounts or records on startup. Existing rows previously marked as generated placeholders remain in the database and are preserved; the application excludes these archived rows from normal accounts, stock, requests, donations, and reports. Their provenance flags remain in the schema for safe isolation. No database cleanup is performed automatically.

## Upgraded workflows

- Inventory separates all recorded quantities from issue-eligible quantities, shows eligible totals and batch status separately, provides group/component summaries and filters, and links to batch movement history. Existing batches receive an additive opening-balance entry the first time the movement tracker starts; this is a baseline, not a claim about earlier activity.
- Request review requires an approval or rejection reason and stores timestamped events. The request page and requester dashboard show eligibility for the eligible stock available for the request. Issuance remains transactional, FEFO ordered, and restricted to released, unexpired, matching component and group stock.
- Appointment scheduling records date, time, location, and optional 3-day, 1-day, and appointment-day reminders. Reminders are in-app only; no email or SMS provider is configured. Cancellation and rescheduling update reminder rows. Appointment completion requires a linked donation record.
- Donation records have a separate `COLLECTED → TESTING → RELEASED` or `REJECTED` status. A released donation record does not automatically create inventory; qualified staff must enter a separately verified batch.
- Prior request and appointment statuses receive additive baseline history entries at startup; earlier transitions cannot be reconstructed.

URLs: landing `http://localhost:8080/`, sign-in `http://localhost:8080/login`, admin `/admin/dashboard`, donor `/donor/dashboard`, and requester `/hospital/dashboard`.

## Isolated synthetic training environment

The `training` profile runs on port **8082** and is hard-guarded to the separate MySQL schema `bbms_training` and dedicated user `bbms_training_app`. The operational `bbms_local` app remains on port 8080. Training startup validates the provisioned schema and does not run Hibernate updates, SQL initialization, Admin bootstrap, or workflow-history backfills. The presentation profile and port 8081 are independent.

All training names, organizations, appointments, requests, opening batches, receipts, and issues are synthetic and marked `demoData`; the page banner states **DEMO DATA — NOT FOR CLINICAL USE**. Training inventory is scoped separately from operational stock and is only eligible when released and unexpired. Baseline inventory is generated as 32 group/component batches totaling exactly 300 units. The seed creates 10 donor accounts, 4 requester accounts, 10 future appointments, and 4 pending requests through application services. Donation entry remains an explicit Admin action after attendance. Training-only simulated screening is clearly marked as simulation and never represents a clinical test; inventory receipt is a separate explicit action after that simulated pass.

### First-time setup

1. `./scripts/init-training-env.sh` creates `.env.training.local` with fresh local passwords and mode 600. This file is git-ignored; keep it local.
2. `./scripts/setup-training-db.sh` creates only `bbms_training` if absent, imports the checked-in schema-only DDL, and creates the dedicated app user with only `SELECT`, `INSERT`, and `UPDATE` on `bbms_training.*`. It refuses unexpected existing schemas or excess grants.
3. Build with `mvn package`.
4. Start and seed once with `./scripts/seed-training.sh`. It explicitly provisions the training Admin only if that email is unused and seeds data idempotently. Keep the terminal open; Ctrl-C stops only this training process.
5. Later starts use `./scripts/start-training.sh` without seed/admin flags. No seed runs on ordinary startup. The Admin login email is `bbms-training-admin@example.test`; sample donor emails are `aditi.shah@training.example.test`, `kavya.patel@training.example.test`, `meera.joshi@training.example.test`, `ananya.desai@training.example.test`, `riya.mehta@training.example.test`, `sneha.verma@training.example.test`, `ishita.gupta@training.example.test`, `tanvi.shah@training.example.test`, `simran.kaur@training.example.test`, and `nisha.rao@training.example.test`. Requester emails are `riddhi.hospitals@training.example.test`, `lifecare.hospital@training.example.test`, `citycare.hospital@training.example.test`, and `sunrise.multispeciality@training.example.test`. Their generated passwords are stored only in the ignored local credential file. To copy one password without printing it, run `awk -F= '$1=="TRAINING_ADMIN_PASSWORD" {print $2}' .env.training.local | pbcopy` (replace the key with `TRAINING_DONOR_1_PASSWORD`, etc.).

Open **http://127.0.0.1:8082**. Training workflow example: donor books a future appointment, Admin confirms it, then advances the visible simulation clock past the scheduled time and marks attendance; Admin records the linked donation; the separate training simulation action marks a simulated pass; the separate receipt action creates a labeled synthetic batch. Hospitals submit the four labeled requests, Admin reviews them with a reason and issues them through the normal FEFO transaction against eligible training-only stock. Every workflow form remains behind normal role authorization and CSRF protection.

The completed local workflow rehearsal advanced the in-memory training clock to **15 Nov 2026**. This makes the 64 opening platelet units expired for that simulation date: the training schema therefore has **299 units on hand** after one synthetic receipt and a two-unit issue, with **235 units currently eligible** across released, unexpired batches. Restarting the app resets the in-memory training clock to the current system date; expiry eligibility will then be recalculated from that date.

`./scripts/reset-training-db.sh` is a manual destructive reset for `bbms_training` only. It verifies the fixed schema identity and requires typing `bbms_training` before it drops that training schema and reimports DDL. Never run it to troubleshoot the operational database. No reset occurs on application startup.
