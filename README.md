# Audiences et Délibérés - Backend

REST API of the hearings and deliberations platform of the **Legal Department of Afriland First
Bank Cameroon** (version 1.0, branch `quality_gates`).

## 👥 Team

- Loïc Luc KENMOE MBEUKEM - developer (KFOKAM-48 end-of-training project)
- Mr. DEUMO DEUMO Léonce - operational supervisor, Head of the Development Department (DSI)

## 🚀 Getting started

### Prerequisites
- Java 21 or later
- Docker and Docker Compose (PostgreSQL, Keycloak and MinIO run in containers)
- Maven is not required: the repository ships the `mvnw` wrapper

### Installation

1. **Clone the repository**
   ```bash
   git clone -b quality_gates https://github.com/loickenmoe/audiences-deliberes-backend.git
   cd audiences-deliberes-backend
   ```

2. **Configure the application**
   - Copy `env.template` to `.env`: `cp env.template .env`
   - The default values all point to the local Docker infrastructure; no cloud service is needed.
   - The Notification Engine block at the end of the file stays commented out outside production:
     no message leaves the application while `NOTIFICATION_ENGINE_ACTIF` is `false`.

3. **Start PostgreSQL, Keycloak and MinIO**
   ```bash
   docker compose up postgres keycloak minio -d
   ```
   The `postgres` container hosts the Keycloak database (`audiences_db`) and the application
   database (`audiences_app`, created on first start). Keycloak imports the `audiences-realm` realm
   automatically.

4. **Build and start the application**
   ```bash
   ./mvnw spring-boot:run
   ```
   Flyway applies the 47 migrations on startup: the database goes from empty to its full schema,
   including the branch reference data.

5. **Access the API**
   - API: `http://localhost:8080/api/v1`
   - Swagger UI: `http://localhost:8080/swagger-ui.html`
   - Health check (public): `http://localhost:8080/actuator/health`

### Quick start with Docker

```bash
docker compose up postgres keycloak minio -d   # PostgreSQL 5433, Keycloak 8081, MinIO 9000/9001
./mvnw spring-boot:run                          # API on 8080
```

> **PostgreSQL port**: the container listens on `5433` (`POSTGRES_PORT`) rather than `5432`, because
> a native PostgreSQL installation on Windows usually holds that port. Set `POSTGRES_PORT=5432` in
> `.env` (and adapt `DATABASE_URL`) if that is not your case.

### Test users

Realm `audiences-realm`, password `Password1!` for all:

| User | Profile | Roles in the token |
|---|---|---|
| `juriste.test` | Legal officer | `ROLE_JURISTE`, `ROLE_SAISIE`, `ROLE_CONSULTATION` |
| `dj.test` | Legal Director (DJ) | `ROLE_DJ` (+ `ROLE_JURISTE`), `ROLE_SAISIE`, `ROLE_CONSULTATION` |
| `dja.test` | Deputy Legal Director (DJA) | `ROLE_DJA` (+ `ROLE_JURISTE`), `ROLE_SAISIE`, `ROLE_CONSULTATION` |
| `assistante.test` | Executive Assistant | `ROLE_ASSISTANTE`, `ROLE_SAISIE`, `ROLE_CONSULTATION` |
| `sh.test` | Line manager (SH) | `ROLE_SH`, `ROLE_SAISIE`, `ROLE_CONSULTATION` |
| `avocat.test` | Partner lawyer (external) | `ROLE_AVOCAT` only |

These accounts have a public password: they have no place on an environment reachable from outside.

---

# Functional documentation - Audiences et Délibérés

## 🎯 Objective

The platform digitalises the litigation follow-up of the Legal Department: case files, hearings,
deliberations, final decisions, lawyers' fees, lawyers' publications, appointments of external
providers, documents and steering. It centralises the case files, automates the alerts on critical
deadlines, produces consolidated reporting and traces every action.

## 🏗️ Key functional principles

### Business workflow
1. **Opening**: a legal officer creates the case file (recovery or operational litigation), with its
   client, parties, originating branch, mandatory documents and assignment (legal officers and
   lawyers). The reference `DOS-YYYY-NNNN` is assigned by the system.
2. **Procedure**: the case file goes through degrees of jurisdiction - first instance, appeal,
   cassation appeal, CCJA appeal - each step with its own life cycle.
3. **Hearings**: scheduled on a step in progress, reminded automatically, held (report entered) or
   cancelled with a reason.
4. **Deliberation**: placed under deliberation, extended, reversed or delivered (favourable,
   unfavourable, mixed); an unfavourable or mixed result opens an appeal deadline.
5. **Final decision**: adjudication or monetary conviction on a closed step, then indexing in the
   case-law base.
6. **Around the case file**: lawyers' fees, publications, appointment letters, documents and
   explanation requests, each with its own validation circuit.
7. **Steering**: dashboard, activity reports, audit and system parameters.

### Validation flow

| Circuit | Steps |
|---|---|
| Special diligence of a case file | Proposed by the legal officer → arbitrated by the DJ or the DJA |
| Threshold derogation | Requested by the legal officer → validated or rejected by the DJ or the DJA |
| Lawyer's fee request | Filed by the lawyer → compliance (Assistant) → appropriateness (DJA) → joint validation DJ + DJA when required → payment (Assistant) |
| Lawyer's "other" publication | Filed by the lawyer → validated or rejected by the Assistant |
| Lawyer's hearing report | Readable at once by the Legal Department → acknowledged by the Assistant |
| Appointment of a provider | Requested by a legal officer or the Assistant → letter written by the requester → corrected if needed and signed by the SH |
| Document deletion | Requested by the legal officer who uploaded it → arbitrated by the DJ or the DJA |
| Sharing a document with the lawyer | Direct on a standard case file; proposed then validated by the DJ or the DJA on a special-diligence case file |

### Validation steps
1. **Authentication**: Keycloak token, checked on every request.
2. **Authorisation**: role required by the endpoint (`@PreAuthorize`).
3. **Business validation**: the service checks the rule and answers with a readable message and an
   `ERR-xxx` code when it refuses.
4. **Traceability**: every change writes a line in the case file history (`historique_action`).
5. **Notification**: the people concerned receive an in-app alert, and external messages when the
   grid allows it.

### Validator groups

| Profile | Role in the application |
|---|---|
| Legal officer | Creates and follows the case files: steps, hearings, deliberations, decisions, documents; proposes special diligence and document sharing; requests appointments |
| Legal Director (DJ) | Steers the activity: dashboard, reports, audit; arbitrates deletions, derogations and shares; gives the joint agreement on fees; maintains the system parameters and the branch reference data |
| Deputy Legal Director (DJA) | Same arbitrations and steering as the DJ; checks the appropriateness of fee requests and gives the joint agreement |
| Executive Assistant | Checks the compliance of fee requests and records their payment; acknowledges hearing reports and validates documents filed by lawyers; requests appointments; consults the calendar, hearings log, publications, directory and reports |
| Line manager (SH) | Corrects and signs appointment letters; shares documents with the appointed lawyer; consults the calendar, hearings log, publications, directory, reports and daily DMS report |
| Partner lawyer | External user: reads his own case files and hearings, files his hearing reports, documents and fee requests, answers explanation requests |

### Permission hierarchy
- The DJ and the DJA carry `ROLE_JURISTE` through Keycloak composite roles: they reach every legal
  officer's screen and API.
- Every internal profile carries `ROLE_SAISIE` and `ROLE_CONSULTATION`.
- The lawyer carries `ROLE_AVOCAT` only: his scope is deliberately narrow.

### Functional restrictions
- A legal officer never deletes a document himself: he requests it, the DJ or the DJA decides.
- An appointment letter belongs to its requester: he alone writes it; the SH may correct it before
  signing.
- A lawyer only sees the case files he is assigned to, without client nor amount, and the shared
  documents only once his appointment is signed.
- A hearing report of a lawyer cannot be rejected: it is contested through a correspondence.
- Only one explanation request can be pending per lawyer; the lawyer answers once.
- Only one correspondence per publication.

## 📊 Functional model

### Case files and procedure
- **Case file**: reference, nature and category (recovery or operational litigation), court,
  client, parties (plaintiffs and defendants), amount at risk, originating and litigation branches,
  special diligence (threshold, client type, status), assigned legal officers and lawyers.
- **Procedure step**: first instance, appeal, cassation appeal, CCJA appeal; status opening → in
  progress → under deliberation → deliberation delivered → closed → archived.
- **Hearing**: date, court, status (scheduled, held, cancelled), legal officer's report, note to
  the lawyer.
- **Deliberation**: date, extensions, reversal, result, appeal deadline, enforcement copy (grosse).
- **Alarm**: follow-up reminder with a subject and a deadline, reschedulable.

### Final decisions
- **Adjudication**: beneficiary, accounting status, provision release, balance refund, transfer,
  remainder.
- **Monetary conviction**: direction (bank or third party liable), payment nature and status.
- **Case law**: archived decision with keywords, nature, court and date.

### Stakeholders
- **Client**: reference `CLI-YYYY-NNNN`, name, e-mail, phone.
- **Intervenant**: partner lawyer (linked to his Keycloak account) or other provider (bailiff,
  notary), with his notification consent.
- **Branch**: bank branch reference data; its code never changes.

### Lawyers' contributions
- **Fee request**: invoice reference (unique), amount, supporting documents, circuit and payment
  statuses.
- **Publication**: hearing report (with an optional attachment) or document (exhibit, decision,
  submissions).
- **Appointment and letter**: request, letter written then signed, generated in PDF and stored in
  the case file documents.
- **Explanation request**: question of the direction and the lawyer's single answer.

### Documents and traceability
- **Document**: file stored in MinIO, type, format, size, sharing status with the lawyer.
- **History and audit**: every change, its author and date; sealed audit exports (SHA-256).
- **Alert**: in-app notification to a user, then external messages queued for the Notification
  Engine.

## 💻 Functional interfaces

### Legal officer
- Case files: creation, search with filters and sorting, file with its tabs (summary, steps,
  hearings, deliberations, alarms, decisions, history, documents, publications).
- Hearings calendar (week, month, quarter) with PDF and Excel exports; hearings log.
- Publications of the lawyers, appointments, lawyers' directory, case law, reports, daily DMS report.

### Legal Director and Deputy
- Everything the legal officer does, plus: dashboard with nine indicators and their detail,
  activity per person and per lawyer, audit with sealed extraction, deletion requests, shares to
  validate, explanation requests, system parameters, notification grid and supervision, branches.

### Executive Assistant and Line manager
- Assistant: fee circuit (compliance, payment), validation of the lawyers' publications,
  appointments, and the consultation screens listed above.
- SH: appointments to sign, document sharing, and the same consultation screens.

### Partner lawyer
- "My space": his filings and their status, his case files with their hearings, the note of the
  Legal Department and the shared documents, his appointment letters and his activity.
- Filing of fee requests and publications, explanation requests.

## 🧾 Business decision rules

| Rule | Value (system parameter) |
|---|---|
| Joint DJ + DJA validation of a fee request on a standard case file | from 50,000,000 FCFA (`SEUIL_MONTANT_DEFAUT`) |
| Extension alert to the DJ and the DJA | beyond 10 extensions (`SEUIL_PROROGATIONS`) |
| Maximum active case files per lawyer before alert | 20 (`CHARGE_MAX_AVOCAT`) |
| Reminder of a lawyer whose hearing report is missing | 2 days after the hearing, then every 2 days, 5 times at most (`DELAI_RAPPEL_CR_AVOCAT`) |
| Joint validation deadline before reminder | 5 days (`DELAI_VALIDATION_CONJOINTE`) |
| Accepted document formats | PDF, PNG, JPG, TXT, XLSX, DOCX, DOC (`FORMATS_GED`) |
| Maximum document size | 10 MB (`TAILLE_MAX_GED`) |

Other rules:
- **References**: `DOS-YYYY-NNNN` and `CLI-YYYY-NNNN`, assigned by the system, restarting at 1 each
  1 January; an invoice reference is used only once.
- **Hearings**: reminders at D-7, D-3 and D-1 to the assigned legal officers (in-app) and lawyers
  (external channels); a duplicate on the same step and date requires a confirmation; a hearing
  cannot precede the opening of its case file.
- **Steps**: under deliberation requires a held hearing and no scheduled one; an appeal requires an
  unfavourable or mixed result within its deadline; closing requires the result; archiving requires
  the final decision and, on the last step, every fee request paid or rejected.
- **Final decisions**: an adjudication or a conviction is recorded on a closed step; a decision
  joins the case-law base only when no step is still in progress.
- **Phones**: a Cameroonian mobile has nine digits starting with 6, optionally preceded by 237 or
  +237; abroad, the country code is mandatory; numbers are stored in international format.
- **Signatures**: PNG or JPEG, 200 KB at most, managed by their owner alone.
- **Rejections** of a fee request, publication, share or deletion always require a reason.

## 🔑 Permissions and access rights

### Permission matrix (screens)

| Screen | JUR | DJ / DJA | AST | SH | AVO |
|---|:-:|:-:|:-:|:-:|:-:|
| Home, notifications | ✅ | ✅ | ✅ | ✅ | ✅ |
| Case files, clients, case law, my account | ✅ | ✅ | ✅ | ✅ | ❌ |
| Calendar, hearings log | ✅ | ✅ | ✅ | ✅ | ❌ |
| Publications, appointments, lawyers' directory | ✅ | ✅ | ✅ | ✅ | ❌ |
| Reports, daily DMS report | ✅ | ✅ | ✅ | ✅ | ❌ |
| Intervenants, branches, configuration | ✅* | ✅ | ✅* | ✅* | ❌ |
| Lawyers' fees | ❌ | ✅ | ✅ | ❌ | ✅ |
| Explanations | ❌ | ✅ | ❌ | ❌ | ✅ |
| Dashboard, deletion requests, shares to validate, audit | ❌ | ✅ | ❌ | ❌ | ❌ |
| My space, file a request, file a publication | ❌ | ❌ | ❌ | ❌ | ✅ |

\* read only: only the DJ and the DJA modify intervenants, branches and parameters. A screen open to
a profile does not open every action: the Assistant and the SH consult the calendar, log,
publications and reports without the other profiles' actions, and the activity per person stays
with the DJ and the DJA.

### Specific access controls
- **Data isolation**: the lawyer's API (`/avocat/**`) only returns his own case files.
- **Audit trail**: every change is traced; consulting the audit screen is traced as well.
- **Sessions**: Keycloak tokens (access 60 min, refresh 30 min on the development realm).

## 📌 User stories

The reference backlog (V1.1) counts 8 functional epics. Main stories:

**Epic 1 - Case files**
```
As a legal officer,
I want to create a case file with its mandatory information and assign it,
So that its follow-up starts on a complete and traceable basis.

Acceptance criteria:
- The reference DOS-YYYY-NNNN is assigned by the system
- Documents, parties and originating branch are mandatory; nothing is created otherwise
- A duplicate assignment requires a confirmation
- Every change is traced in the case file history
```

**Epic 2 - Hearings**
```
As a legal officer,
I want to schedule a hearing on a step in progress,
So that the deadline is secured by automatic reminders.

Acceptance criteria:
- The hearing appears as scheduled; a duplicate asks for confirmation
- Reminders at D-7, D-3 and D-1
- Cancelling requires a reason; a report turns the hearing into "held"
```

**Epic 3 - Deliberations**
```
As a legal officer,
I want to record, extend, reverse or deliver a deliberation,
So that the outcome of each degree of jurisdiction is followed.

Acceptance criteria:
- Beyond the extension threshold, the DJ and the DJA are alerted
- A reversal reopens the step
- An unfavourable or mixed result opens an appeal deadline
```

**Epic 4 - Lawyers' fees**
```
As a partner lawyer,
I want to file a fee request with its supporting documents,
So that it goes through the validation circuit up to payment.

Acceptance criteria:
- A duplicate invoice reference is refused
- Compliance, appropriateness, joint validation when required, then payment
- A rejection carries a reason that the lawyer sees
```

**Epic 5 - Publications and appointments**
```
As a partner lawyer,
I want to file my hearing reports and the documents of my case files,
So that the Legal Department reads them without delay.

Acceptance criteria:
- A hearing report is readable at once; a document becomes visible once validated
- One single correspondence per publication
- An appointment letter is written by its requester and signed by the SH
```

**Epic 6 - Documents (DMS)**
```
As a legal officer,
I want to upload, preview, share and request the deletion of documents,
So that each case file keeps its exhibits under control.

Acceptance criteria:
- Formats and size are checked
- Deletion is requested, then arbitrated by the DJ or the DJA
- On a special-diligence case file, sharing waits for the direction's validation
```

**Epic 7 - Alerts and notifications**
```
As a user of the platform,
I want to be alerted of the events that concern me,
So that no deadline is missed.

Acceptance criteria:
- In-app alerts in real time, with an unread counter
- External messages according to the notification grid
- An alert is marked as handled by its recipient only
```

**Epic 8 - Final decisions and reporting**
```
As the Legal Director,
I want indicators, reports and an audit of the activity,
So that I steer the litigation and justify it.

Acceptance criteria:
- Nine indicators, each with its exact detail
- Activity report exported in PDF, Excel and CSV
- Sealed audit export, verifiable afterwards
```

## 🚀 Advanced features

### Dashboard and analytics
- **Nine indicators**: active case files, cases delivered, scheduled hearings, pending fees,
  extension alerts, litigation won, lost, mixed decisions and released provisions; each opens its
  exact detail and follows the chosen scope.
- **Activity reports**: per period and scope, per person and per lawyer, in counts and never in
  ratings.

### Communication
- **Real time**: alerts pushed over WebSocket (STOMP), with an unread counter.
- **External notifications**: Outlook and Teams for internal users; personal e-mail, SMS and
  WhatsApp for providers, through the bank's Notification Engine, with a grid per channel and a
  delivery supervision.
- **Automatic reminders**: hearings, appeal deadlines, alarms, missing hearing reports, pending
  deletions and joint validations.

### Security and audit
- **Sealed audit extraction**: the exported file is archived and its SHA-256 fingerprint recorded,
  so that a file received later can be verified.
- **Generated documents**: appointment letters, calendars and reports in PDF with the bank's
  layout.

## 🎯 Success indicators

### Functional
- Response time under 2 seconds on current views, for 50 simultaneous users in the pilot phase.
- Delivery rate of in-app alerts above the required 99 % (supervision screen).

### Technical
- SonarQube quality gate `AFB_JAVA_PROJECT`: passed, coverage 90.5 %, 0 bug, 0 vulnerability,
  0 code smell.
- 1,069 automated tests, all passing.

---

# Technical documentation - Audiences et Délibérés Backend

## 🎯 Objective

REST API serving the Next.js frontend and the lawyers' portal: business rules, permissions,
document storage, real-time alerts, scheduled reminders and the outgoing notifications.

## 🏗️ Architecture and key principles

### General architecture
- **Architecture**: modular monolith (one Spring Boot artefact), one package per business domain
- **Database**: PostgreSQL 17 with JPA/Hibernate, schema versioned by Flyway
- **Security**: Keycloak 24 (OIDC/JWT) with Spring Security resource server
- **File storage**: MinIO (S3), presigned URLs
- **Real time**: WebSocket STOMP, authenticated with the Keycloak token
- **Documentation**: OpenAPI / Swagger UI

### Packages (`com.afriland.audiences`)

| Package | Content |
|---|---|
| `dossier` | Case files, steps, assignment, special diligence, derogations |
| `audience` | Hearings, alarms, hearing reminders |
| `delibere` | Deliberations, extensions, reversals, appeal deadlines |
| `decision` | Adjudications, convictions, case law |
| `frais` | Fee requests and their circuit |
| `publication` | Lawyers' publications, comments, correspondence, missing-report reminders, explanations |
| `intervenant` | Lawyers and providers, appointments and letters, directory |
| `ged` | Documents, deletion requests, sharing with the lawyer, daily report |
| `alerte` / `notification` | In-app alerts; outgoing notifications (Notification Engine gateway) |
| `reporting` | Dashboard, activity reports, hearings log |
| `referentiel` | Users, clients, branches, configuration, reference lists |
| `portail` | Lawyer's API (`/avocat/**`) |
| `common` / `config` | Audit, errors, PDF, references, security, OpenAPI, WebSocket |

### Application layers
1. **Web layer** (`web`): controllers, request validation, `@PreAuthorize`
2. **Service layer** (`service`): business rules, transactions, history
3. **Repository layer** (`repository`): Spring Data JPA
4. **Domain layer** (`domain`, `mapper`): JPA entities, DTOs, MapStruct mappers
5. **Configuration**: security, CORS, WebSocket, MinIO, OpenAPI

### Request flow
- **Authentication**: JWT validated against the Keycloak issuer on every request
- **Authorisation**: roles from `realm_access.roles`, checked by `@PreAuthorize`
- **Validation**: Bean Validation on requests, business checks in services
- **Errors**: global handler, JSON body `{ "code": "ERR-xxx", "message": "...", "details": ... }`

## 📊 Data model

### Main entities (fields as exposed by the API)

#### Dossier (case file)
```java
- id, reference (DOS-YYYY-NNNN), nature, categorie (RECOUVREMENT | EXPLOITATION_LITIGES)
- juridictionSaisie, clientId, parties (demandeurs, defendeurs), risqueEncouru
- codeAgenceOrigine, codeAgenceContentieuse
- estSensible, seuilMontant, typeClientSensible, sensibiliteStatut
- etapes, juristesAffectes, avocatsAffectes, historique, documents
```

#### EtapeProcedure (procedure step)
```java
- id, dossierId
- type (INSTANCE | RECOURS_1 | RECOURS_2 | RECOURS_2_CCJA)
- statutCycleVie (OUVERTURE | EN_COURS | EN_DELIBERE | DELIBERE_VIDE | CLOTURE | ARCHIVE)
```

#### Audience (hearing)
```java
- id, dossierId, etapeId, datePlanifiee, dateTenue, juridiction
- statut (PLANIFIEE | TENUE | ANNULEE), compteRendu, motifAnnulation, noteAvocat
```

#### Delibere (deliberation)
```java
- id, dossierId, etapeId, dateDeliberee, resultat (FAVORABLE | DEFAVORABLE | MIXTE)
- nombreProrogations, dateRabattement, dateEcheanceRecours, statutExpedition (A_LEVER | LEVEE)
```

#### DemandeFrais (fee request)
```java
- id, dossierId, avocatId, referenceFacture (unique), montant, piecesJustificatives
- statutCircuit, statutPaiement, motifRejet, validationConjointeRequise, validations
```

#### Publication
```java
- id, type (CR audience | other document), dossierId, avocatId, audienceId
- statutValidation, contenu, typeDocument, commentaires, correspondance
```

#### Document
```java
- id, dossierId, nomFichier, typeDocument, format, poids
- auteurChargementId, dateChargement, partageAvocat (NON | PROPOSE | PARTAGE)
```

#### Client, Intervenant
```java
Client      - id, reference (CLI-YYYY-NNNN), nom, email, telephone
Intervenant - id, type (AVOCAT | AUTRE_PRESTATAIRE), nom, email, telephone, compteKeycloak, notification
```

### Relations
- Client ↔ Dossier (1:N) - a client has several case files
- Dossier ↔ EtapeProcedure (1:N) - a case file goes through several steps
- EtapeProcedure ↔ Audience / Delibere (1:N) - hearings and deliberations hang on a step
- Dossier ↔ juristes / avocats (N:N) - assignment tables
- Dossier ↔ Document, DemandeFrais, Publication, Historique (1:N)

The complete data dictionary is in the V1.1 reference documentation (see "Documentation" below).

## 💻 Interfaces and APIs

101 paths and 120 operations under `/api/v1`, all described in Swagger UI. Main groups:

| Group | Paths |
|---|---|
| Case files | `/dossiers`, `/dossiers/{id}/etapes`, `/affectation`, `/sensibilite`, `/seuil-derogation`, `/historique` |
| Hearings and alarms | `/dossiers/{id}/audiences`, `/audiences/{id}/...`, `/audiences/calendrier`, `/audiences/journal`, `/dossiers/{id}/alarmes`, `/alarmes/{id}/...` |
| Deliberations | `/dossiers/{id}/deliberes`, `/deliberes/{id}/prorogations`, `/rabattement`, `/resultat`, `/expedition` |
| Decisions | `/dossiers/{id}/adjudications`, `/condamnations`, `/jurisprudences` |
| Fees | `/frais/demandes`, `.../conformite`, `.../opportunite`, `.../validation-conjointe`, `.../paiement` |
| Publications and explanations | `/publications`, `/publications/cr-audience`, `/publications/autres`, `/demandes-explication` |
| Appointments | `/constitutions/prestataires`, `.../lettre`, `.../validation` |
| Documents | `/ged/documents`, `/ged/demandes-suppression`, `.../partage-avocat`, `/ged/rapport-journalier` |
| Reporting and audit | `/tableau-de-bord`, `/rapports`, `/rapports/activite`, `/rapports/nominatif`, `/historique`, `/historique/export` |
| Reference data | `/clients`, `/intervenants`, `/referentiels/agences`, `/configurations`, `/utilisateurs` |
| Lawyer's portal | `/avocat/mes-dossiers`, `/avocat/mes-publications`, `/avocat/mes-constitutions` |
| Alerts | `/alertes/mes-notifications`, `/alertes/{id}/traiter`, `/notifications/supervision` |

## 🔧 Technical configuration

### Technologies

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.3.11 (Web, Security, Data JPA, Validation, WebSocket, Actuator) |
| PostgreSQL | 17 (Docker) |
| Keycloak | 24.0 (Docker) |
| MinIO | Docker |
| Flyway | 47 migrations |
| Build | Maven wrapper (`mvnw`) |
| Tests | JUnit 5, Mockito, Testcontainers, JaCoCo |

### Environment variables (`env.template` → `.env`)

| Variable | Description |
|---|---|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Application database (`jdbc:postgresql://localhost:5433/audiences_app`) |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` | PostgreSQL container |
| `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD` | Keycloak administration console |
| `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWK_SET_URI` | `http://localhost:8081/realms/audiences-realm` and its certificates |
| `BACKEND_PORT` | API port (default `8080`) |
| `JPA_ACTION` | Leftover, not read by the application: Hibernate only validates the schema migrated by Flyway (`ddl-auto=validate`) |
| `CORS_ALLOWED_ORIGINS` | Exact frontend origins, comma-separated; also used by the WebSocket |
| `MINIO_API_URL`, `MINIO_USERNAME`, `MINIO_PASSWORD`, `MINIO_PROJECT_NAME`, `MINIO_API_PORT`, `MINIO_CONSOLE_PORT` | Local MinIO and its bucket |

### Authentication

Authentication is handled **exclusively** by Keycloak; the backend manages no password. To obtain a
token:

```bash
curl -s -X POST http://localhost:8081/realms/audiences-realm/protocol/openid-connect/token \
  -d "client_id=audiences-api" -d "client_secret=audiences-api-secret" \
  -d "username=juriste.test" -d "password=Password1!" -d "grant_type=password" | jq .access_token
```

Then `curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/...`. The real-time
channel (`/ws`, STOMP) requires the same token in the `Authorization` header of the STOMP `CONNECT`
frame.

## 🧾 Validation rules and business logic

- **Requests**: Bean Validation annotations; a malformed body answers 400 with `ERR-VALIDATION`.
- **Business refusals**: `ApiException` with a contract code (`ERR-001` missing metadata, `ERR-002`
  duplicate reference, `ERR-003` duplicate assignment, `ERR-004` forbidden transition, `ERR-005`
  duplicate hearing, `ERR-006` duplicate invoice, `ERR-008` non-compliant document, `ERR-009`
  restricted publication) and a message written for the end user.
- **Step transitions**: centralised in `EtapeTransitionValidator`.
- **History**: `HistoriqueActionService` records each change with a typed action code.
- **Scheduled tasks**: hearing reminders and appeal deadlines (06:00), missing hearing reports
  (06:30) and breaches (06:45), hourly alarm deadlines, lawyer load, joint validation and deletion
  reminders, notification dispatcher every minute.

## 🔑 Security and permissions

### Roles
- `ROLE_JURISTE`, `ROLE_DJ`, `ROLE_DJA`, `ROLE_ASSISTANTE`, `ROLE_SH`, `ROLE_AVOCAT`, plus the
  composites `ROLE_SAISIE` (internal profiles) and `ROLE_CONSULTATION`.
- The DJ and the DJA include `ROLE_JURISTE`.

### Technical security
- **Authentication**: OAuth2 resource server, JWT signed by Keycloak
- **Authorisation**: `@PreAuthorize` on every controller method
- **CORS and WebSocket**: allowed origins read from `CORS_ALLOWED_ORIGINS`
- **Files**: format and size checked before storage; downloads through short-lived presigned URLs
- **Secrets**: none versioned; `.env` is ignored by git

### ⚠️ Security notice - action required before taking over the repository
Earlier versions of `env.template` (and the git history) contained **real credentials** (Neon
PostgreSQL database, iDrive e2 MinIO keys, JWT secret). They were replaced by local placeholders;
if you held them:
1. **Rotate them immediately** in the Neon and iDrive e2 consoles.
2. Consider purging the git history (`git filter-repo` / BFG) if the repository has been shared.

See `design-notes/DECISIONS_AND_OPEN_QUESTIONS.md` (risk R-01).

## 📈 Performance and monitoring

### Optimisations
- **Pagination**: search and list endpoints are paginated (`page`, `size`)
- **Lazy loading** on JPA relations
- **Connection pool**: HikariCP

### Monitoring
- **Health check**: `/actuator/health` (public)
- **Logs**: SLF4J / Logback
- **Notifications**: supervision of deliveries per channel in the Configuration screen

## 🚀 Deployment

### Docker Compose services

| Service | Description | Port |
|---|---|---|
| `postgres` | PostgreSQL - `audiences_db` (Keycloak) and `audiences_app` (API) | `5433` (host) → `5432` |
| `keycloak` | IAM server | `8081` (host) → `8080` |
| `minio` | Object storage (documents) | `9000` (API) / `9001` (console) |

```bash
docker compose up postgres keycloak minio -d   # start the local infrastructure
docker compose down -v                          # stop and remove the volumes (forces a realm re-import)
```

### Manual validation checklist

To run in order after a `docker compose down -v` (cold restart):

1. **Infrastructure**: `docker compose up postgres keycloak minio -d` → `postgres` and `keycloak`
   become `healthy` (Keycloak's first start takes 2 to 4 minutes); `minio` shows `Up`.
2. **Databases**:
   `docker exec -it audiences-deliberes-backend-postgres-1 psql -U audiences_user -d postgres -c "\l"`
   → lists `audiences_db` and `audiences_app`.
3. **Keycloak**: `http://localhost:8081` answers; the token request above returns an
   `access_token` whose `realm_access.roles` contains `ROLE_JURISTE`, `ROLE_SAISIE` and
   `ROLE_CONSULTATION`.
4. **Application**: `./mvnw spring-boot:run` ends with `Started AudiencesApplication`, without
   `Unable to determine Dialect` nor `password authentication failed`.
5. **HTTP security**:
   ```bash
   curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/health        # 200
   curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/dossiers        # 401
   curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/swagger-ui/index.html  # 200
   # With a real token (fetched by the command itself) → 200
   TOKEN=$(curl -s -X POST http://localhost:8081/realms/audiences-realm/protocol/openid-connect/token \
     -d "client_id=audiences-api" -d "client_secret=audiences-api-secret" \
     -d "username=juriste.test" -d "password=Password1!" -d "grant_type=password" \
     | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
   curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/dossiers
   ```
6. **No versioned secret**: `git grep -iE "npg_|idrivee2|OKfkPo|YpruO6" -- . ':!README.md'` → no
   result (this README is excluded because it documents the patterns).

## 🧪 Tests and quality

```bash
./mvnw test
```
- 1,069 tests (JUnit 5, Mockito), including 123 integration tests on a real PostgreSQL started by
  Testcontainers: a running Docker daemon is required.
- Coverage measured by JaCoCo (`target/site/jacoco/jacoco.xml`) and analysed on the bank's
  SonarQube server (`AFB_JAVA_PROJECT`).
- Before any `./mvnw clean`, stop the application and check that ports 8080 and 3000 are free.

## 📋 Technical recommendations

### Before production
- Close or restrict `/v3/api-docs` and `/swagger-ui`, open without authentication today.
- Replace the test accounts with the bank's real Keycloak accounts.
- Replace every password of `.env` and keep them out of the repository.
- Point the MinIO public URL and CORS policy to the real origin.
- Organise PostgreSQL and MinIO backups.
- Activate the Notification Engine only after a dedicated test campaign
  (`design-notes/NOTIFICATION_ENGINE_PLAN.md`).

### Good practices kept
- Business rules in services, never in controllers.
- One typed history code per action; every refusal with a readable message.
- Comments in English; requirement identifiers (`RG-`, `US`, `UC-`, `Q-`) kept for traceability.
- Conventional Commits with a scope, e.g. `feat(dossiers): ...`.

## 🔗 External integrations

| System | Integration |
|---|---|
| PostgreSQL | Main database |
| Keycloak | Identity, tokens and roles of every user, lawyers included |
| MinIO | Document storage (S3-compatible) |
| Notification Engine (bank) | Outgoing e-mail, SMS, WhatsApp, Outlook and Teams messages, through Dapr; disabled outside production |
| WebSocket (STOMP) | Real-time alerts to the frontend |
| Core Banking | None in version 1: client data is entered manually |

## 📚 Documentation

- `design-notes/` - internal design notes, in French:

| File | Content |
|---|---|
| `design-notes/PROJECT_CONTEXT.md` | Project context and reference documents |
| `design-notes/SYSTEM_ARCHITECTURE.md` | Architecture, data model, security model |
| `design-notes/FUNCTIONAL_MAP.md` | Features mapped to the code |
| `design-notes/DECISIONS_AND_OPEN_QUESTIONS.md` | Every business rule decided, with its rationale; open questions and risks |
| `design-notes/NOTIFICATION_ENGINE_PLAN.md` | Integration plan of the bank's Notification Engine |

- **Reference documentation V1.1** (functional specifications, user stories, analysis, design, data
  dictionary, API contract): kept outside the repository; each file is named in the test request
  (`AFB_PS03_TR_AUDIENCES_DELIBERES_V01_21102026`).
- **Testers' folder**: user guide, deployment guide, developer and functional test sheets, release
  note, acceptance report and architecture document, delivered separately.
