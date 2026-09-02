# Audiences et Délibérés Backend

## 👥 Membres du groupe
- Loïc Luc KENMOE MBEUKEM

## 🏗️ Stack technique
- Java 21 / Spring Boot 3.3
- PostgreSQL (Neon cloud pour l'app, Docker local pour Keycloak)
- Keycloak 24 (IAM / OAuth2)
- MinIO (stockage fichiers, iDrive e2)
- WebSocket (STOMP)

## 🚀 Lancement du projet

### Prérequis
- Java 21 ou supérieur
- Maven 3.8 ou supérieur
- Docker & Docker Compose

### Installation

**1. Cloner le dépôt**
```bash
git clone https://github.com/votre-utilisateur/audiences-deliberes-backend.git
cd audiences-deliberes-backend
```

**2. Configurer l'environnement**

Copier `env.template` vers `.env` et renseigner les variables :
```bash
cp env.template .env
```

Variables clés à configurer :
| Variable | Description |
|---|---|
| `DATABASE_URL` | URL JDBC PostgreSQL (Neon) |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Credentials Neon |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8080/realms/audiences-realm` |
| `KEYCLOAK_JWK_SET_URI` | `http://localhost:8080/realms/audiences-realm/protocol/openid-connect/certs` |
| `MINIO_API_URL` / `MINIO_USERNAME` / `MINIO_PASSWORD` | Credentials MinIO |
| `BACKEND_PORT` | Port de l'API (défaut : `8888`) |

**3. Démarrer Keycloak et PostgreSQL (Docker)**

> Keycloak utilise un PostgreSQL local. Le realm `audiences-realm` est importé automatiquement.

```bash
docker-compose up postgres keycloak -d
```

Vérifier que Keycloak est prêt : `http://localhost:8080` (admin / admin)

**4. Démarrer l'application Spring Boot**
```bash
mvn spring-boot:run
```

**5. Accéder à l'API**
- API : `http://localhost:8888`
- Swagger UI : `http://localhost:8888/swagger-ui.html`

## 🔐 Authentification

L'authentification est gérée par **Keycloak**. Pour obtenir un token :

```bash
curl -s -X POST http://localhost:8080/realms/audiences-realm/protocol/openid-connect/token \
  -d "client_id=audiences-api" \
  -d "username=juriste.test" \
  -d "password=Password1!" \
  -d "grant_type=password" | jq .access_token
```

Utiliser le token dans les requêtes :
```bash
curl -H "Authorization: Bearer <token>" http://localhost:8888/api/v1/...
```

### Utilisateurs de test
| Utilisateur | Rôle(s) | Mot de passe |
|---|---|---|
| `juriste.test` | `ROLE_JURISTE`, `ROLE_CONSULTATION` | `Password1!` |
| `dj.test` | `ROLE_DJ`, `ROLE_CONSULTATION` | `Password1!` |
| `dja.test` | `ROLE_DJA`, `ROLE_CONSULTATION` | `Password1!` |
| `assistante.test` | `ROLE_ASSISTANTE`, `ROLE_CONSULTATION` | `Password1!` |
| `sh.test` | `ROLE_SH`, `ROLE_CONSULTATION` | `Password1!` |
| `avocat.test` | `ROLE_AVOCAT` | `Password1!` |
| `gfc.test` | `ROLE_GFC`, `ROLE_CONSULTATION` | `Password1!` |

## 🐳 Docker Compose — services disponibles

| Service | Description | Port |
|---|---|---|
| `postgres` | PostgreSQL pour Keycloak | `5432` |
| `keycloak` | Serveur IAM | `8080` |
| `minio` | Stockage objets (optionnel local) | `9000` / `9001` |

```bash
# Démarrer uniquement Keycloak + sa base
docker-compose up postgres keycloak -d

# Démarrer avec MinIO local
docker-compose up postgres keycloak minio -d
```

---