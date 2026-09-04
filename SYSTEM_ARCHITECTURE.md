# SYSTEM_ARCHITECTURE

> Architecture documentée (A), architecture réellement implémentée (B), architecture cible proposée (C).
> Dernière mise à jour : 2026-09-04 (audit initial).

---

## 1. Architecture documentée (A — REF-05 §3)

**Monolithe modulaire n-tiers**, déployé comme artefact unique, inspiré des principes SOA.
Justification retenue : équipe d'un seul ingénieur, délai 10-14 semaines, < 2 s / 50 utilisateurs,
simplicité opérationnelle, extraction ultérieure possible en microservices.

| Couche | Rôle | Technologies |
|---|---|---|
| Présentation | IHM | Next.js (React), TypeScript, Tailwind — **hors dépôt** |
| Coordination | Contrôleurs, logique applicative | Spring Boot 3, Java 21, Spring Security |
| Services | API REST, transactions, règles métier | Spring Boot, Spring Data JPA |
| Persistance | Données métier + GED | PostgreSQL 17, MinIO (S3) |

### Classification d'Ivar Jacobson (UP7) retenue en conception

| Stéréotype | Rôle | Règle d'association | Correspondance Spring |
|---|---|---|---|
| `<<Dialogue>>` | IHM | uniquement `<<Dialogue>>` ou `<<Contrôleur>>` | Frontend Next.js |
| `<<Contrôleur>>` | Coordination, enchaînement | tous types | `@RestController` + `@Service` |
| `<<Entité>>` | Informations durables | uniquement `<<Entité>>` ou `<<Contrôleur>>` | `@Entity` + `Repository` |

Contrôleurs nommés en conception : `CTRLGestionDossiers`, `CTRLAudience`, `CTRLDelibere`,
`CTRLFrais`, `CTRLGED`, `CTRLReporting`.

### Déploiement documenté (REF-05 §8)

```
Internet → HTTPS:443 → nginx (DMZ, terminaison TLS)
                          ↓
                    Backend Spring Boot :8080 (réseau interne)
                          ↓
        ┌─────────────────┼─────────────────┐
   PostgreSQL:5432   Keycloak:8081     MinIO:9000
   (réseau données)  (réseau interne)  (réseau interne)
```

| Service | Image documentée | Port |
|---|---|---|
| frontend | node:18-alpine (Next.js) | 3000 |
| backend | openjdk:21-jdk-slim | 8080 |
| postgres | postgres:17-alpine **(§8.1)** / postgres:15-alpine **(§12.2)** — contradiction Q-15 | 5432 |
| keycloak | keycloak:26.6.4 **(§8.1)** / `:latest` **(§12.2)** — contradiction Q-15 | 8081 |
| minio | minio RELEASE.2024-11-07 | 9000 |
| nginx | nginx:alpine — **obligatoire** (mitigation RSK-06) | 443 / 80 |

### Systèmes externes

| Système | Nature de l'intégration |
|---|---|
| Keycloak | **Intégration technique réelle** — SSO OIDC/JWT, RBAC, tous utilisateurs y compris avocats externes |
| MinIO | **Intégration technique réelle** — stockage GED, URLs pré-signées |
| SMTP (Outlook) | Envoi d'emails (alertes, confirmations, lettres de constitution) |
| Microsoft Teams | Webhook sortant, canal d'alertes critiques (optionnel) |
| Core Banking / Comptabilité-DCPO / INTRA V2 / Portail | **Aucune intégration** — saisie manuelle |

---

## 2. Architecture réellement implémentée (B)

### 2.1 Ce qui existe

```
src/main/java/com/afriland/audiences/
├── AudiencesApplication.java
├── config/
│   ├── SecurityConfig.java          ✅ OAuth2 Resource Server Keycloak, @EnableMethodSecurity
│   ├── CorsConfig.java              ⚠️ doublon CORS avec SecurityConfig, origines divergentes
│   ├── OpenApiConfig.java           ✅ springdoc, schéma bearerAuth global
│   ├── JwtAuthenticationFilter.java ⚠️ filtre JWT HS256 hérité, toujours enregistré sur /*
│   ├── WebSocketConfig.java         ⚠️ STOMP + SockJS, endpoint /ws, origines `*`
│   └── WebSocketAuthInterceptor.java ⚠️ valide des JWT HS256 maison, pas les jetons Keycloak
├── controller/
│   ├── AdminUserController.java     ❌ hors domaine — CRUD utilisateurs SANS @PreAuthorize
│   ├── AdminDashboardController.java ❌ vide, @PreAuthorize("hasRole('ADMIN')")
│   ├── UserDashboardController.java ❌ vide, @PreAuthorize("hasRole('USER')")
│   ├── StaffController.java         ❌ hors domaine, rôles ADMIN/USER inexistants
│   ├── auth/AuthController.java     ❌ désactivé (@RestController commenté)
│   ├── chat/ChatController.java     ❌ hors domaine (chat STOMP)
│   └── core/TemplateController.java ❌ squelette, renvoie des String
├── dto/auth/ + dto/core/            ❌ squelette
├── event/WebSocketEventListener.java ❌ hors domaine
├── model/
│   ├── security/User.java           ❌ entité `_user` avec mot de passe — CONTREDIT REF-05 §1.3
│   ├── security/Role.java           ✅ enum aligné sur les 7 rôles Keycloak
│   ├── core/Template.java           ❌ entité vide
│   └── chat/ChatMessage.java        ❌ hors domaine
├── repository/                      ❌ UserRepository + TemplateRepository seulement
└── service/
    ├── UserService(Impl)            ❌ CRUD utilisateur avec encodage BCrypt
    ├── DashboardService(Impl)       ❌ interface et impl **vides**
    ├── core/TemplateService(Impl)   ❌ impl renvoie null / List.of(), non annotée @Service
    └── security/
        ├── JwtService.java          ⚠️ génération/validation JWT HS256 maison
        ├── AuthService.java         ❌ désactivé
        └── UserDetailsServiceImpl.java ⚠️ actif, charge User depuis la base
```

**Verdict : aucune entité, aucun repository, aucun service, aucun endpoint du domaine métier
n'est implémenté (0 des ~50 endpoints du contrat d'API).** Le dépôt est un starter générique
sur lequel la migration Keycloak a été greffée.

### 2.2 Infrastructure réellement configurée

| Élément | Documenté (A) | Implémenté (B) | Écart |
|---|---|---|---|
| Port backend | 8080 | **8888** | ⚠️ contredit la base URL du contrat d'API |
| Port Keycloak | 8081 | **8080** | ⚠️ inversion |
| Version Keycloak | 26.6.4 | **24.0** | ⚠️ |
| PostgreSQL applicatif | conteneur local :5432 | **Neon Cloud (AWS us-east-2)** | 🔴 majeur |
| Stockage GED | MinIO conteneur local | **iDrive e2 (cloud, `b5l0.or.idrivee2-33.com`)** | 🔴 majeur |
| PostgreSQL conteneur | base applicative | sert **uniquement à Keycloak** | 🔴 |
| nginx / TLS | obligatoire (RSK-06) | **absent** | 🔴 |
| Frontend | service compose | absent (normal — backend seul) | ok |
| Schéma DB | script SQL versionné | `ddl-auto=update`, **aucune migration** | 🔴 |
| Realm Keycloak | — | `audiences-realm`, 7 rôles, 6 users, client `audiences-api` | ✅ |
| Claim des rôles | — | claim `roles` (mapper realm-roles), préfixe déjà `ROLE_` | ✅ cohérent avec `SecurityConfig` |

### 2.3 Constats de sécurité (code actuel)

| # | Constat | Gravité |
|---|---|---|
| S-1 | `env.template` **versionné** contient des identifiants réels : mot de passe PostgreSQL Neon, access key + secret key iDrive e2, secret JWT. Présents dans l'historique git (`6436048`, `898b453`). | 🔴 Critique |
| S-2 | Données juridiques d'une banque hébergées sur **Neon Cloud (US)** et **iDrive e2** — contredit « développement intégralement en environnement local » (REF-02 §5.2) et le zonage réseau (REF-05 §8.2). | 🔴 Critique |
| S-3 | `AdminUserController` (`/api/v1/admin/users`) : `@PreAuthorize` **commenté** → tout utilisateur authentifié peut créer/modifier/supprimer des utilisateurs. | 🔴 |
| S-4 | `/ws/**` en `permitAll` ; `WebSocketAuthInterceptor` **laisse passer** les connexions non authentifiées (`// let it proceed unauthenticated`) et valide des jetons HS256 signés avec le secret exposé en S-1. | 🔴 |
| S-5 | `JwtAuthenticationFilter` (`@Component`) enregistré sur `/*` (confirmé dans les logs : `order=2147483647`). Chemin d'authentification parallèle, requête base à chaque appel. À supprimer. | 🟠 |
| S-6 | `User.getAuthorities()` renvoie `"ROLE_" + role.name()` alors que `role.name()` vaut déjà `ROLE_JURISTE` → autorité `ROLE_ROLE_JURISTE`. Bug fonctionnel du chemin hérité. | 🟠 |
| S-7 | `StaffController` / dashboards exigent `hasRole('ADMIN')` / `hasRole('USER')` — rôles **inexistants** dans le realm → endpoints inaccessibles (échec silencieux). | 🟡 |
| S-8 | Deux configurations CORS concurrentes (`SecurityConfig` : 3000+4200 ; `CorsConfig` : 4200 uniquement). | 🟡 |
| S-9 | Aucun `@ControllerAdvice` : les exceptions remontent en 500 sans le format d'erreur `{code,message,details}` du contrat. | 🟠 |
| S-10 | Aucun rate limiting, aucun journal d'audit applicatif (exigé : « toute action utilisateur est journalisée »). | 🟠 |

---

## 3. Architecture cible proposée (C — recommandation, à valider)

> **Ceci est une recommandation, pas une exigence documentaire.**

### 3.1 Découpage en packages (par module, puis par couche)

```
com.afriland.audiences
├── AudiencesApplication
├── common/                     # socle transversal
│   ├── config/                 # Security, OpenAPI, CORS, Jackson, Async, Scheduling
│   ├── exception/              # ApiException, ErrorCode(ERR-001..009), GlobalExceptionHandler
│   ├── audit/                  # HistoriqueAction : entité, service, AOP/listener
│   ├── security/               # CurrentUser, UtilisateurProvisioningService (sync Keycloak→utilisateur)
│   └── util/                   # pagination, export (PDF/Excel/CSV)
├── referentiel/                # client, profil, configuration_systeme, taxonomie natures
├── dossier/                    # dossier, etape_procedure, affectation, sensibilité, dérogation seuil
├── audience/                   # audience, alarme
├── delibere/                   # delibere, prorogation, rabattement, expédition, délai de recours
├── decision/                   # decision_definitive, adjudication, condamnation, jurisprudence
├── intervenant/                # intervenant, avocat, autre_prestataire, répertoire, constitution
├── frais/                      # demande_frais + circuit de validation
├── publication/                # publication, compte_rendu_audience, autre_publication, correspondance
├── ged/                        # document, demande_suppression, stockage MinIO, rapport journalier
├── alerte/                     # alerte, moteur de déclenchement, canaux (applicatif/SMTP/Teams)
└── reporting/                  # rapports, tableau de bord, vue consolidée client
```

Chaque module : `web/` (controller + DTO) · `service/` · `domain/` (entités + enums) ·
`repository/`. Les modules ne s'appellent qu'à travers leur couche `service`.

### 3.2 Décisions techniques recommandées

| Sujet | Recommandation | Motif |
|---|---|---|
| Schéma DB | **Flyway** (`db/migration/V1__…sql`), `ddl-auto=validate` | Le script SQL de REF-05 §12.1 est la source ; `update` ne garantit ni les CHECK ni les index |
| Base applicative | **PostgreSQL conteneurisé local** | REF-02 §5.2, REF-05 §8.2 ; retirer Neon |
| Stockage GED | **MinIO conteneurisé local** | idem ; retirer iDrive e2 |
| Secrets | `.env` non versionné + `env.template` **avec placeholders**, rotation des secrets exposés | S-1 |
| Port backend | **8080** (aligner sur le contrat) ; Keycloak sur **8081** | REF-07 §1.3, REF-05 §8.1 |
| Authentification | **Keycloak seul** — supprimer `User`, `Role` (entité), `JwtService`, `AuthService`, `UserDetailsServiceImpl`, `JwtAuthenticationFilter`, `AuthController`, DTO auth | REF-05 §1.3 |
| Table `utilisateur` | Alimentée par **provisioning à la première requête** depuis les claims du jeton (`sub`→`keycloak_id`, `given_name`, `family_name`, `email`, rôle→`profil_id`) | Le dictionnaire exige `utilisateur.id` en FK partout (documents, alertes, historique) |
| WebSocket | **Conserver** pour les notifications applicatives temps réel, mais sécuriser via le jeton Keycloak (`JwtDecoder`), refuser les connexions anonymes. Supprimer le module chat. | NF-ALR-02 « canal applicatif obligatoire » |
| Autorisation | `@PreAuthorize` par endpoint + **vérification métier** (affectation au dossier, auteur du document, destinataire de l'alerte) dans la couche service | Le RBAC seul ne couvre pas RG-INT-06 (restriction d'accès) ni RG-GED-05 (auteur) |
| Erreurs | `@RestControllerAdvice` produisant `{code, message, details}` + codes `ERR-001..ERR-009` | REF-07 §1.4 et §6.1 |
| Historisation | Service `HistoriqueActionService` appelé explicitement par chaque service métier (pas d'AOP magique) — `dossier_id`, `utilisateur_id`, `action`, `details` JSONB | RG-DOS-06, US 1.6 |
| Alertes | Table `alerte` + `@Scheduled` pour les échéances (J-7/J-3/J-1, délais de recours, relance 72 h) + publication d'événements Spring pour les alertes immédiates | RG-ALR-01/03 |
| Exports | POI (Excel) + iText/PDFBox (PDF) — déjà au `pom.xml` | RG-REP-01 |
| Tests | JUnit 5 + Testcontainers (PostgreSQL) + MockMvc avec `@WithMockUser`/jwt ; un test d'intégration par critère Gherkin **Must Have** | US, cible > 80 % (REF-02 §10.2) |

### 3.3 Nettoyage du `pom.xml`

- `spring-boot-starter-mail` déclaré **deux fois** (l. 150-158) — doublon.
- `spring-boot-starter-websocket` déclaré **deux fois** (l. 59-62 et 159-163) — doublon.
- `jjwt-api/impl/jackson` : à retirer avec l'authentification héritée.
- `itextpdf 5.5.13.4` (AGPL) **et** `pdfbox 3.0.2` : deux bibliothèques PDF — n'en garder qu'une.
  ⚠️ iText 5 est sous AGPL : licence à valider avec la DSI en contexte bancaire propriétaire.
- `mapstruct` déclaré mais **absent** des `annotationProcessorPaths` du compiler plugin → les
  mappers ne seraient pas générés.
- `spring-boot-devtools` : à exclure des profils autres que `dev`.

---

## 4. Modèle de données cible

22 tables issues de REF-06 + REF-05 §12.1, **plus 4 tables manquantes identifiées** (§4.2).

### 4.1 Tables spécifiées

**Référentiel** — `profil`, `utilisateur`, `client`, `configuration_systeme`, `intervenant`,
`avocat`, `autre_prestataire`
**Cœur métier** — `dossier`, `etape_procedure`, `audience`, `delibere`, `prorogation`,
`decision_definitive`, `adjudication`, `condamnation_pecuniaire`, `jurisprudence`
**Intervenants** — `demande_frais`, `publication`, `compte_rendu_audience`, `autre_publication`
**GED & alertes** — `document`, `alerte`
**Traçabilité** — `historique_action`, `audit_seuil_dossier`

Héritage : `intervenant`→(`avocat`|`autre_prestataire`), `decision_definitive`→
(`adjudication`|`condamnation_pecuniaire`), `publication`→(`compte_rendu_audience`|`autre_publication`)
— **table-par-type avec PK partagée** (JPA : `@Inheritance(JOINED)`).

### 4.2 Tables manquantes dans la documentation (à créer — cf. Q-01 à Q-05)

| Table | Justification documentaire | Question |
|---|---|---|
| `dossier_juriste` / `dossier_avocat` (N:N) | RG-DOS-03 « affecté à un ou plusieurs juristes et avocats » ; POST /dossiers reçoit `juristesAffectes[]`, `avocatsAffectes[]` — **aucune table d'affectation n'existe** | **Q-01** |
| `demande_suppression` | FR-GED-03 cite explicitement « la table demande_suppression » ; UC-GED-03 (relance 72 h) — **absente du dictionnaire et du SQL** | **Q-02** |
| `demande_constitution` | FR-INT-06/07, UC-INT-05/06, US 5.6/5.7 « Tables constitution (à créer) » | **Q-03** |
| `alarme` (ou extension de `alerte`) | FR-AUD-03 : objet + échéance + étape ; `alerte` n'a ni `objet` ni `etape_id` ; `dossier.champ_alarme TEXT` ne permet pas plusieurs alarmes | **Q-04** |
| `validation_frais` (traces par étape) | FR-INT-02/03/04 : qui a validé quoi, quand, avec quel motif ; `demande_frais` n'a qu'un `statut_circuit` et un `motif_rejet` unique — la validation conjointe DJ **et** DJA est intraçable | **Q-05** |
| `type_client_sensible` (référentiel) | RG-DOS-09 « liste prédéfinie gérée par le DJ » | **Q-06** |
| Champ `document.chemin_stockage` | Présent au dictionnaire, **absent du script SQL** REF-05 §12.1 | **Q-07** |

### 4.3 Cycle de vie d'une étape de procédure (RG-DOS-04, REF-04 §7)

```
OUVERTURE → EN_COURS → EN_DELIBERE → DELIBERE_VIDE → CLOTURE → ARCHIVE
                ↑            │  ↑ ↓
                │            │  └── prorogation (reste EN_DELIBERE, n fois)
                └────────────┘  rabattement RG-DEL-07 (retour EN_COURS, n fois)

DELIBERE_VIDE ──(recours exercé dans le délai)──▶ ouvre RECOURS_1 / RECOURS_2 (statut OUVERTURE)
DELIBERE_VIDE ──(délai expiré sans recours)────▶ CLOTURE + bascule décisions définitives (RG-DEL-04)
```

Un dossier porte **1 à 3 étapes** : `INSTANCE`, `RECOURS_1`, `RECOURS_2`, chacune avec son
propre cycle de vie. Le juriste peut créer directement une étape ultérieure ; les étapes
intermédiaires sont alors créées automatiquement au statut `OUVERTURE` (UC-DOS-04 alt. 3-b).

### 4.4 Circuit de validation d'une demande de frais (RG-INT-03)

```
AVOCAT dépose ──▶ DEPOSEE
                    │ (notification Assistante)
                    ▼
       ASSISTANTE — contrôle de conformité + anti-doublon facture
                    ├── non conforme / doublon ──▶ REJETEE (motif obligatoire, avocat notifié)
                    └── conforme ──▶ CONFORMITE  (notification DJA)
                                        ▼
                          DJA — contrôle d'opportunité
                                        ├── rejet ──▶ REJETEE (motif obligatoire)
                                        └── accord
                                             │
                    ValidationConjointe = (estSensible = true) OR (montant > seuilMontant)
                                             ├── faux ──▶ VALIDEE
                                             └── vrai ──▶ OPPORTUNITE (attente DJ + DJA)
                                                           └── 2 accords ──▶ VALIDEE
                                                           └── 1 refus  ──▶ REJETEE
                                        ▼
       ASSISTANTE — statut de paiement ──▶ statutPaiement = PAYEE (avocat + DJ/DJA notifiés)
```

`seuilMontant` : valeur globale `SEUIL_MONTANT_DEFAUT` = 50 000 000 FCFA
(`configuration_systeme`, modifiable par le DJ) ; valeur au niveau dossier uniquement en cas de
**dérogation validée** (`seuil_origine = 'DEROGATION'`, tracée dans `audit_seuil_dossier`).

### 4.5 Modèle de sécurité cible

| Rôle Keycloak | Attribution |
|---|---|
| `ROLE_JURISTE`, `ROLE_DJ`, `ROLE_DJA`, `ROLE_ASSISTANTE`, `ROLE_SH`, `ROLE_AVOCAT` | rôles de profil |
| `ROLE_CONSULTATION` | rôle transverse, attribué à tous les profils internes |
| `ROLE_SAISIE` | **cité par le contrat d'API mais absent du realm et de la liste « exhaustive » des US §11.6** — cf. **Q-08** |

Le realm actuel attribue déjà `ROLE_CONSULTATION` en second rôle à tous les internes.
Recommandation : ajouter `ROLE_SAISIE` comme rôle composite (JURISTE, DJ, DJA, ASSISTANTE, SH)
pour respecter le contrat d'API sans dupliquer les expressions `@PreAuthorize`.

**Deux niveaux d'autorisation obligatoires** :
1. **RBAC** (`@PreAuthorize`) — le rôle donne accès au type d'opération ;
2. **Contrôle métier** en service — affectation au dossier, auteur du document (RG-GED-05),
   destinataire de l'alerte, `restriction_acces` d'une publication (RG-INT-06),
   statut préalable du circuit (`DEPOSEE` avant conformité, etc.).
