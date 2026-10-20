# Audiences et Délibérés Backend

## 👥 Membres du groupe
- Loïc Luc KENMOE MBEUKEM

## 🏗️ Stack technique
- Java 21 / Spring Boot 3.3
- PostgreSQL (conteneur Docker local — base `audiences_app` pour l'API, `audiences_db` pour Keycloak)
- Keycloak 24 (IAM / OAuth2)
- MinIO (stockage fichiers, conteneur Docker local)
- WebSocket (STOMP, authentifié par jeton Keycloak)

## ⚠️ Sécurité — action requise avant toute reprise du dépôt

Des versions antérieures de `env.template` (et de l'historique git) ont contenu des **identifiants
réels** (base PostgreSQL Neon, clés MinIO iDrive e2, secret JWT). Ces valeurs ont été retirées et
remplacées par des placeholders locaux, mais si vous disposiez des identifiants réels exposés :
1. **Faites-les tourner immédiatement** dans les consoles Neon et iDrive e2.
2. Considérez une purge de l'historique git (`git filter-repo` / BFG) si le dépôt a été partagé.

Voir `DECISIONS_AND_OPEN_QUESTIONS.md` (risque R-01) pour le détail.

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

Copier `env.template` vers `.env` :
```bash
cp env.template .env
```

Les valeurs par défaut de `env.template` pointent toutes vers l'infrastructure Docker locale
(voir `docker-compose.yml`) — aucun service cloud externe n'est requis pour développer.

| Variable | Description |
|---|---|
| `DATABASE_URL` | PostgreSQL local — base `audiences_app` (`jdbc:postgresql://localhost:5433/audiences_app`) |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Credentials PostgreSQL local |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8081/realms/audiences-realm` |
| `KEYCLOAK_JWK_SET_URI` | `http://localhost:8081/realms/audiences-realm/protocol/openid-connect/certs` |
| `MINIO_API_URL` / `MINIO_USERNAME` / `MINIO_PASSWORD` | MinIO local (`http://localhost:9000`) |
| `BACKEND_PORT` | Port de l'API (défaut : `8080`) |

**3. Démarrer PostgreSQL, Keycloak et MinIO (Docker)**

> Le conteneur `postgres` héberge à la fois la base de Keycloak (`audiences_db`) et la base
> applicative (`audiences_app`, créée automatiquement au premier démarrage). Le realm
> `audiences-realm` est importé automatiquement par Keycloak.

```bash
docker-compose up postgres keycloak minio -d
```

Vérifier que Keycloak est prêt : `http://localhost:8081` (admin / voir `.env`)

> **Port PostgreSQL** : le conteneur expose son port sur `5433` par défaut (`POSTGRES_PORT`),
> plutôt que le `5432` standard, car une installation PostgreSQL native sur le poste (fréquente
> sous Windows, ex. service `postgresql-x64-17`) occupe généralement déjà ce port. Si ce n'est
> pas votre cas, vous pouvez définir `POSTGRES_PORT=5432` dans `.env` (et adapter `DATABASE_URL`
> en conséquence) pour revenir au port standard.

**4. Démarrer l'application Spring Boot**
```bash
mvn spring-boot:run
```

**5. Accéder à l'API**
- API : `http://localhost:8080`
- Swagger UI : `http://localhost:8080/swagger-ui.html`
- Health check (public) : `http://localhost:8080/actuator/health`

## 🔐 Authentification

L'authentification est **exclusivement** gérée par **Keycloak** (OIDC / JWT) — le backend
n'implémente aucune gestion de mots de passe. Pour obtenir un token :

```bash
curl -s -X POST http://localhost:8081/realms/audiences-realm/protocol/openid-connect/token \
  -d "client_id=audiences-api" \
  -d "username=juriste.test" \
  -d "password=Password1!" \
  -d "grant_type=password" | jq .access_token
```

Utiliser le token dans les requêtes :
```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/...
```

Le canal applicatif temps réel (`/ws`, STOMP) exige le même jeton, transmis dans l'en-tête
`Authorization` de la trame STOMP `CONNECT` (pas dans la requête HTTP de négociation SockJS).

### Utilisateurs de test
| Utilisateur | Rôle(s) | Mot de passe |
|---|---|---|
| `juriste.test` | `ROLE_JURISTE` (+ `ROLE_SAISIE` implicite), `ROLE_CONSULTATION` | `Password1!` |
| `dj.test` | `ROLE_DJ` (+ `ROLE_SAISIE` implicite), `ROLE_CONSULTATION` | `Password1!` |
| `dja.test` | `ROLE_DJA` (+ `ROLE_SAISIE` implicite), `ROLE_CONSULTATION` | `Password1!` |
| `assistante.test` | `ROLE_ASSISTANTE` (+ `ROLE_SAISIE` implicite), `ROLE_CONSULTATION` | `Password1!` |
| `sh.test` | `ROLE_SH` (+ `ROLE_SAISIE` implicite), `ROLE_CONSULTATION` | `Password1!` |
| `avocat.test` | `ROLE_AVOCAT` | `Password1!` |

`ROLE_SAISIE` est un rôle composite Keycloak : tout utilisateur portant JURISTE, DJ, DJA,
ASSISTANTE ou SH le porte automatiquement dans son jeton (cf. `DECISIONS_AND_OPEN_QUESTIONS.md` Q-08).

## 🐳 Docker Compose — services disponibles

| Service | Description | Port |
|---|---|---|
| `postgres` | PostgreSQL — bases `audiences_db` (Keycloak) et `audiences_app` (API) | `5433` (host, cf. `POSTGRES_PORT`) → `5432` (conteneur) |
| `keycloak` | Serveur IAM | `8081` (host) → `8080` (conteneur) |
| `minio` | Stockage objets (GED) | `9000` (API) / `9001` (console) |

```bash
# Démarrer l'ensemble du socle local
docker-compose up postgres keycloak minio -d

# Arrêter et supprimer les volumes (force un ré-import du realm au prochain démarrage)
docker-compose down -v
```

## ✅ Checklist de validation manuelle (jalon M1)

Procédure complète, à exécuter dans l'ordre après un `docker-compose down -v` (redémarrage à
froid). Chaque étape indique le résultat attendu.

**1. Démarrer le socle**
```bash
docker-compose up postgres keycloak minio -d
```
→ `docker ps` doit afficher `postgres` et `keycloak` en `healthy` après quelques minutes
(le premier démarrage de Keycloak, avec construction Quarkus, prend ~2 à 4 minutes ; les
démarrages suivants sont bien plus rapides). `minio` n'a pas de healthcheck défini, il suffit
qu'il soit `Up`.

**2. Vérifier que la base applicative a bien été créée**
```bash
docker exec -it audiences-deliberes-backend-postgres-1 psql -U audiences_user -d postgres -c "\l"
```
→ la liste doit contenir **deux** bases : `audiences_db` (Keycloak) et `audiences_app` (API).

**3. Vérifier Keycloak et le realm**
- Ouvrir `http://localhost:8081` → console d'administration Keycloak accessible.
- Obtenir un jeton pour un utilisateur de test :
```bash
curl -s -X POST http://localhost:8081/realms/audiences-realm/protocol/openid-connect/token \
  -d "client_id=audiences-api" -d "client_secret=audiences-api-secret" \
  -d "username=juriste.test" -d "password=Password1!" -d "grant_type=password"
```
→ doit retourner un JSON avec `access_token`. Décoder le token (ex. sur jwt.io, ou
`echo <partie2> | base64 -d`) et vérifier que `realm_access.roles` contient bien
`ROLE_JURISTE`, `ROLE_SAISIE` (rôle composite, cf. Q-08) et `ROLE_CONSULTATION`.

**4. Démarrer l'application**
```bash
mvn spring-boot:run
```
→ le log doit se terminer par `Started AudiencesApplication in ... seconds`, sans exception
(en particulier, aucune erreur `Unable to determine Dialect` ni `password authentication failed`
— signe que la base est bien jointe sur le bon port).

**5. Vérifier la sécurité HTTP**
```bash
# Sans jeton, endpoint public → 200
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/health

# Sans jeton, endpoint quelconque de l'API → 401
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/dossiers

# Swagger UI → 200
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/swagger-ui/index.html

# Avec un vrai jeton (récupéré automatiquement) → 200 : l'authentification passe et la liste
# des dossiers est renvoyée
# ⚠️ Ne pas coller "<token>" tel quel : c'était un espace réservé dans la doc, pas une valeur.
# La commande ci-dessous récupère elle-même un jeton réel avant de l'utiliser.
TOKEN=$(curl -s -X POST http://localhost:8081/realms/audiences-realm/protocol/openid-connect/token \
  -d "client_id=audiences-api" -d "client_secret=audiences-api-secret" \
  -d "username=juriste.test" -d "password=Password1!" -d "grant_type=password" \
  | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/dossiers
```

**6. Vérifier l'absence de secrets dans les fichiers versionnés**
```bash
# README.md exclu : il documente volontairement ces motifs dans cette checklist elle-même,
# ce qui produirait un faux positif sans rapport avec une fuite réelle.
git grep -iE "npg_|idrivee2|OKfkPo|YpruO6" -- . ':!README.md'
```
→ aucun résultat.

Si toutes ces étapes passent, le jalon M1 peut être considéré comme validé.

---
