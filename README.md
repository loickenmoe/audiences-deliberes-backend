# Audiences et Délibérés - Backend

REST API of the hearings and deliberations platform of the **Legal Department of Afriland First
Bank Cameroon**.

## 👥 Team
- Loïc Luc KENMOE MBEUKEM

## 🏗️ Tech stack
- Java 21 / Spring Boot 3.3
- PostgreSQL 17 (local Docker container - `audiences_app` database for the API, `audiences_db` for Keycloak)
- Keycloak 24 (IAM / OAuth2)
- MinIO (file storage, local Docker container)
- WebSocket (STOMP, authenticated with the Keycloak token)
- Flyway (database migrations)

## 📚 Documentation
- `Doc/` - reference documentation V1.1 (functional specifications, user stories, analysis,
  design, data dictionary, API contract), the client's original specification and the project
  presentation note.
- `design-notes/` - internal design notes, in French:

| File | Content |
|---|---|
| `design-notes/PROJECT_CONTEXT.md` | Project context and reference documents |
| `design-notes/SYSTEM_ARCHITECTURE.md` | Architecture, data model, security model |
| `design-notes/FUNCTIONAL_MAP.md` | Features mapped to the code |
| `design-notes/DECISIONS_AND_OPEN_QUESTIONS.md` | Every business rule decided, with its rationale; open questions and risks |
| `design-notes/NOTIFICATION_ENGINE_PLAN.md` | Integration plan of the bank's Notification Engine |

## ⚠️ Security - action required before taking over the repository

Earlier versions of `env.template` (and the git history) contained **real credentials** (Neon
PostgreSQL database, iDrive e2 MinIO keys, JWT secret). These values have been removed and replaced
with local placeholders, but if you held the exposed credentials:
1. **Rotate them immediately** in the Neon and iDrive e2 consoles.
2. Consider purging the git history (`git filter-repo` / BFG) if the repository has been shared.

See `design-notes/DECISIONS_AND_OPEN_QUESTIONS.md` (risk R-01) for details.

## 🚀 Getting started

### Prerequisites
- Java 21 or later
- Docker and Docker Compose
- Maven is not required: the repository ships the `mvnw` wrapper.

### Installation

**1. Clone the repository**
```bash
git clone https://github.com/your-user/audiences-deliberes-backend.git
cd audiences-deliberes-backend
```

**2. Configure the environment**

Copy `env.template` to `.env`:
```bash
cp env.template .env
```

The default values of `env.template` all point to the local Docker infrastructure (see
`docker-compose.yml`) - no external cloud service is required for development.

| Variable | Description |
|---|---|
| `DATABASE_URL` | Local PostgreSQL - `audiences_app` database (`jdbc:postgresql://localhost:5433/audiences_app`) |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Local PostgreSQL credentials |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8081/realms/audiences-realm` |
| `KEYCLOAK_JWK_SET_URI` | `http://localhost:8081/realms/audiences-realm/protocol/openid-connect/certs` |
| `MINIO_API_URL` / `MINIO_USERNAME` / `MINIO_PASSWORD` | Local MinIO (`http://localhost:9000`) |
| `BACKEND_PORT` | API port (default: `8080`) |

The Notification Engine block at the end of the file stays commented out outside production: no
message leaves the application while `NOTIFICATION_ENGINE_ACTIF` is `false`.

**3. Start PostgreSQL, Keycloak and MinIO (Docker)**

> The `postgres` container hosts both the Keycloak database (`audiences_db`) and the application
> database (`audiences_app`, created automatically on first start). Keycloak imports the
> `audiences-realm` realm automatically.

```bash
docker compose up postgres keycloak minio -d
```

Check that Keycloak is ready: `http://localhost:8081` (admin / see `.env`)

> **PostgreSQL port**: the container exposes its port on `5433` by default (`POSTGRES_PORT`)
> rather than the standard `5432`, because a native PostgreSQL installation on the workstation
> (common on Windows, e.g. the `postgresql-x64-17` service) usually already holds that port. If
> that is not your case, set `POSTGRES_PORT=5432` in `.env` (and adapt `DATABASE_URL` accordingly).

**4. Start the Spring Boot application**
```bash
./mvnw spring-boot:run
```
Flyway applies the migrations on startup: the database goes from empty to its full schema,
including the branch reference data.

**5. Access the API**
- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health check (public): `http://localhost:8080/actuator/health`

## 🔐 Authentication

Authentication is handled **exclusively** by **Keycloak** (OIDC / JWT) - the backend implements no
password management. To obtain a token:

```bash
curl -s -X POST http://localhost:8081/realms/audiences-realm/protocol/openid-connect/token \
  -d "client_id=audiences-api" -d "client_secret=audiences-api-secret" \
  -d "username=juriste.test" \
  -d "password=Password1!" \
  -d "grant_type=password" | jq .access_token
```

Use the token in requests:
```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/...
```

The real-time channel (`/ws`, STOMP) requires the same token, sent in the `Authorization` header
of the STOMP `CONNECT` frame (not in the SockJS negotiation HTTP request).

### Test users
| User | Role(s) | Password |
|---|---|---|
| `juriste.test` | `ROLE_JURISTE` (+ implicit `ROLE_SAISIE`), `ROLE_CONSULTATION` | `Password1!` |
| `dj.test` | `ROLE_DJ` (+ implicit `ROLE_SAISIE`), `ROLE_CONSULTATION` | `Password1!` |
| `dja.test` | `ROLE_DJA` (+ implicit `ROLE_SAISIE`), `ROLE_CONSULTATION` | `Password1!` |
| `assistante.test` | `ROLE_ASSISTANTE` (+ implicit `ROLE_SAISIE`), `ROLE_CONSULTATION` | `Password1!` |
| `sh.test` | `ROLE_SH` (+ implicit `ROLE_SAISIE`), `ROLE_CONSULTATION` | `Password1!` |
| `avocat.test` | `ROLE_AVOCAT` | `Password1!` |

`ROLE_SAISIE` is a Keycloak composite role: any user holding JURISTE, DJ, DJA, ASSISTANTE or SH
carries it automatically in their token.

These are test accounts with a public password: they have no place on an environment reachable
from outside.

## 🐳 Docker Compose - available services

| Service | Description | Port |
|---|---|---|
| `postgres` | PostgreSQL - `audiences_db` (Keycloak) and `audiences_app` (API) databases | `5433` (host, see `POSTGRES_PORT`) → `5432` (container) |
| `keycloak` | IAM server | `8081` (host) → `8080` (container) |
| `minio` | Object storage (document management) | `9000` (API) / `9001` (console) |

```bash
# Start the local infrastructure
docker compose up postgres keycloak minio -d

# Stop and remove the volumes (forces a realm re-import on next start)
docker compose down -v
```

## 🧪 Tests

```bash
./mvnw test
```
Integration tests use Testcontainers, which starts its own PostgreSQL: a running Docker daemon is
required. Before any `./mvnw clean`, stop the application and check that ports 8080 and 3000 are
free.

## ✅ Manual validation checklist

Full procedure, to run in order after a `docker compose down -v` (cold restart). Each step gives
the expected result.

**1. Start the infrastructure**
```bash
docker compose up postgres keycloak minio -d
```
→ `docker ps` must show `postgres` and `keycloak` as `healthy` after a few minutes (Keycloak's
first start, with its Quarkus build, takes about 2 to 4 minutes; later starts are much faster).
`minio` has no healthcheck: `Up` is enough.

**2. Check that the application database was created**
```bash
docker exec -it audiences-deliberes-backend-postgres-1 psql -U audiences_user -d postgres -c "\l"
```
→ the list must contain **two** databases: `audiences_db` (Keycloak) and `audiences_app` (API).

**3. Check Keycloak and the realm**
- Open `http://localhost:8081` → the Keycloak admin console is reachable.
- Obtain a token for a test user:
```bash
curl -s -X POST http://localhost:8081/realms/audiences-realm/protocol/openid-connect/token \
  -d "client_id=audiences-api" -d "client_secret=audiences-api-secret" \
  -d "username=juriste.test" -d "password=Password1!" -d "grant_type=password"
```
→ must return a JSON document with `access_token`. Decode the token (e.g. on jwt.io, or
`echo <part2> | base64 -d`) and check that `realm_access.roles` contains `ROLE_JURISTE`,
`ROLE_SAISIE` (composite role) and `ROLE_CONSULTATION`.

**4. Start the application**
```bash
./mvnw spring-boot:run
```
→ the log must end with `Started AudiencesApplication in ... seconds`, without exception (in
particular no `Unable to determine Dialect` nor `password authentication failed` error - a sign
that the database is reached on the right port).

**5. Check HTTP security**
```bash
# Without a token, public endpoint → 200
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/health

# Without a token, any API endpoint → 401
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/dossiers

# Swagger UI → 200
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/swagger-ui/index.html

# With a real token (fetched automatically) → 200: authentication passes and the list of case
# files is returned
# ⚠️ Do not paste "<token>" as is: it was a placeholder, not a value.
# The command below fetches a real token itself before using it.
TOKEN=$(curl -s -X POST http://localhost:8081/realms/audiences-realm/protocol/openid-connect/token \
  -d "client_id=audiences-api" -d "client_secret=audiences-api-secret" \
  -d "username=juriste.test" -d "password=Password1!" -d "grant_type=password" \
  | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/dossiers
```

**6. Check that no secret is versioned**
```bash
# README.md is excluded: it deliberately documents these patterns in this very checklist,
# which would produce a false positive unrelated to an actual leak.
git grep -iE "npg_|idrivee2|OKfkPo|YpruO6" -- . ':!README.md'
```
→ no result.

If all these steps pass, the installation is valid.

## ⚙️ Before production

- Close or restrict `/v3/api-docs` and `/swagger-ui`, open without authentication today.
- Replace the test accounts with the bank's real Keycloak accounts.
- Replace every password of `.env` and keep them out of the repository.
- Organise PostgreSQL and MinIO backups.
- Activate the Notification Engine only after a dedicated test campaign
  (`design-notes/NOTIFICATION_ENGINE_PLAN.md`).
