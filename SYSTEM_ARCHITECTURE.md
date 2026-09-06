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

> Mise à jour après les jalons **M1 — Assainissement du socle et sécurisation** (validé le
> 2026-09-04, commits `3025c46` et `e33f668`), **M2 — Schéma de base et référentiels**
> (validé le 2026-09-04, commit `ceb0e77`) et **M3 — Socle transversal** (validé le 2026-09-04,
> commit `57e75c3`). Voir `DEVELOPMENT_ROADMAP.md` pour le détail des jalons et
> `DECISIONS_AND_OPEN_QUESTIONS.md` pour le suivi des risques R-01 à R-05.

### 2.1 Ce qui existe

```
src/main/java/com/afriland/audiences/
├── AudiencesApplication.java
├── config/
│   ├── SecurityConfig.java          ✅ OAuth2 Resource Server Keycloak, @EnableMethodSecurity,
│   │                                    AuthenticationEntryPoint/AccessDeniedHandler (M3) —
│   │                                    401/403 filtre suivent le format {code,message,details}
│   ├── OpenApiConfig.java           ✅ springdoc, schéma bearerAuth global
│   ├── WebSocketConfig.java         ✅ STOMP + SockJS, endpoint /ws
│   └── WebSocketAuthInterceptor.java ✅ valide les JWT Keycloak (JwtDecoder), rejette les
│                                        connexions/trames non authentifiées
├── referentiel/                     ✅ module M2 — schéma + données de référence uniquement
│   ├── domain/                      Profil, Utilisateur, Client, ConfigurationSysteme,
│   │                                TypeClientSensible, NatureDossier, Intervenant (JOINED)
│   │                                → Avocat, AutrePrestataire
│   └── repository/                  un JpaRepository par entité ci-dessus (8 au total)
└── common/                          ✅ module M3 — socle transversal, sans consommateur métier
    ├── error/                       ApiException, ErrorCodes (ERR-001..009 + génériques),
    │                                ErrorResponse, GlobalExceptionHandler (@RestControllerAdvice)
    ├── security/                    CurrentUserService — résolution/provisioning utilisateur
    │                                depuis le jeton Keycloak
    ├── audit/                       HistoriqueAction (entité, JSONB natif Hibernate 6),
    │                                HistoriqueActionRepository, HistoriqueActionService
    ├── web/                         PageResponse<T> — enveloppe de pagination du contrat
    └── config/                      SchedulingConfig (@EnableScheduling)

src/main/resources/db/migration/
├── V1__schema_initial.sql           ✅ 32 tables, contraintes CHECK, FK, index
├── V2__donnees_reference.sql        ✅ profils, configuration_systeme, taxonomie
└── V3__delibere_delai_recours.sql   ✅ M7 : delibere.date_echeance_recours + index (Q-19/Q-42)
```

Le socle générique hors domaine et le chemin d'authentification hérité (HS256 maison) ont été
intégralement supprimés en M1 ; M2 a posé le schéma et les entités des référentiels ; M3 a posé
les briques transversales (erreurs, utilisateur courant, historisation, pagination) ; M4 a livré
le premier module fonctionnel — `dossier/{domain,repository,service,web,mapper}` et
`referentiel/{service,web,mapper}` (contrôleur `ClientController`) — consommant l'ensemble des
briques M3 ; M5 a livré le module `alerte/{domain,repository,service,web,mapper}` (socle
transversal, dépendance des modules M6+) ainsi que `referentiel/{service,web}/Configuration*`, et
a introduit le premier traitement planifié effectif (`dossier/service/ChargeMaxAvocatScheduler`) ;
M6 a livré `audience/{domain,repository,service,web,mapper}` (premier module métier autorisé à
dépendre directement de `dossier` par `@ManyToOne`, contrairement à `alerte`), avec deux nouveaux
traitements planifiés (`RappelAudienceScheduler`, `AlarmeEcheanceScheduler`) et un premier export
de données (PDF/Excel, `CalendrierExportService`) ; M7 a livré
`delibere/{domain,repository,service,web,mapper}` (même principe de dépendance directe vers
`dossier`/`audience`), avec un troisième traitement planifié (`DelaiRecoursScheduler`, bascule
automatique `CLOTURE`).

**Verdict : le schéma de données et le socle transversal sont complets et validés, et les modules
Dossiers/Clients (M4), Alertes/Configurations (M5), Audiences/Alarmes (M6) et Délibérés (M7) sont
livrés et testés — 28 des ~53 endpoints du contrat d'API sont implémentés** (102 tests
automatisés, dont un test d'intégration Testcontainers). Les autres modules fonctionnels (M8+)
n'ont pas commencé.

### 2.2 Infrastructure réellement configurée

| Élément | Documenté (A) | Implémenté (B) | Écart |
|---|---|---|---|
| Port backend | 8080 | **8080** | ✅ aligné (M1) |
| Port Keycloak | 8081 | **8081** | ✅ aligné (M1) |
| Version Keycloak | 26.6.4 | 24.0 | 🟡 non traité en M1 (Q-15, P2, hors périmètre du jalon) |
| PostgreSQL applicatif | conteneur local | **conteneur local** (`audiences_app`, port hôte 5433 — cf. note ci-dessous) | ✅ aligné (M1) |
| Stockage GED | MinIO conteneur local | **MinIO conteneur local** | ✅ aligné (M1) |
| PostgreSQL conteneur | base applicative | héberge **`audiences_db`** (Keycloak) et **`audiences_app`** (API) | ✅ aligné (M1) |
| nginx / TLS | obligatoire (RSK-06) | absent | 🟡 non traité en M1, hors périmètre local de développement |
| Frontend | service compose | absent (normal — backend seul) | ok |
| Schéma DB | script SQL versionné | **Flyway, `ddl-auto=validate`, 32 tables** (`V1__schema_initial.sql`) | ✅ aligné (M2) |
| Realm Keycloak | — | `audiences-realm`, 8 rôles (dont `ROLE_SAISIE` composite, Q-08), 6 users, client `audiences-api` | ✅ |
| Claim des rôles | — | claim `roles` (mapper realm-roles), préfixe déjà `ROLE_` — vérifié en conditions réelles (jeton `juriste.test` contient `ROLE_JURISTE, ROLE_SAISIE, ROLE_CONSULTATION`) | ✅ |

**Note port PostgreSQL** : le port hôte par défaut est **5433** et non 5432, pour éviter un
conflit avec une installation PostgreSQL native fréquente sur les postes Windows (constaté sur
le poste de développement : service `postgresql-x64-17`). Le port interne au réseau Docker
(utilisé par Keycloak) reste 5432, inchangé. Configurable via `POSTGRES_PORT`. Voir `README.md`.

### 2.3 Constats de sécurité — état après M1

| # | Constat initial (audit) | Statut après M1 |
|---|---|---|
| S-1 | `env.template` versionné contenait des identifiants cloud réels (Neon, iDrive e2, JWT) | ✅ **Résolu dans les fichiers versionnés** — `env.template`/`.env` ne contiennent plus que des valeurs locales. ⚠️ **Reste à faire par l'utilisateur** : faire tourner les secrets déjà exposés côté Neon/iDrive e2, et statuer sur une purge de l'historique git (cf. R-01 dans `DECISIONS_AND_OPEN_QUESTIONS.md`) |
| S-2 | Données hébergées sur Neon Cloud / iDrive e2 | ✅ Résolu — infrastructure 100 % locale (Postgres + MinIO conteneurisés) |
| S-3 | `AdminUserController` sans `@PreAuthorize` | ✅ Résolu — contrôleur supprimé (hors domaine) |
| S-4 | `/ws/**` laissait passer des connexions non authentifiées | ✅ Résolu — `WebSocketAuthInterceptor` réécrit sur le `JwtDecoder` Keycloak, rejette toute trame STOMP sans jeton valide |
| S-5 | `JwtAuthenticationFilter` HS256 enregistré sur `/*` | ✅ Résolu — supprimé, authentification exclusivement via le Resource Server OAuth2 |
| S-6 | Bug `ROLE_ROLE_JURISTE` dans `User.getAuthorities()` | ✅ Résolu — entité `User` et chemin d'authentification hérité supprimés |
| S-7 | Rôles `ADMIN`/`USER` inexistants dans le realm | ✅ Résolu — contrôleurs concernés supprimés |
| S-8 | Deux configurations CORS concurrentes | ✅ Résolu — `CorsConfig` supprimé, une seule source (`SecurityConfig`) |
| S-9 | Aucun `@ControllerAdvice` | 🔴 **Non traité** — prévu en **M3** (socle transversal) |
| S-10 | Aucun journal d'audit applicatif | 🔴 **Non traité** — prévu en **M3/M4** (`HistoriqueActionService`) |

**Découverte additionnelle en validation M1** : le healthcheck Docker de Keycloak levait une
`NullPointerException` (requête HTTP/1.0 sans en-tête `Host`), le conteneur restait `unhealthy`
en permanence bien que fonctionnel — corrigé dans `docker-compose.yml`.

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

## 4. Modèle de données — ✅ implémenté (M2)

**32 tables**, créées par `src/main/resources/db/migration/V1__schema_initial.sql` et vérifiées
par Hibernate `ddl-auto=validate` contre PostgreSQL 17 réel : 24 tables issues de REF-06 +
REF-05 §12.1, **plus 8 tables/colonnes ajoutées** lors de l'audit (§4.2). Voir
`DEVELOPMENT_ROADMAP.md` M2 et `DECISIONS_AND_OPEN_QUESTIONS.md` pour le détail des arbitrages.

### 4.1 Tables spécifiées par la documentation

**Référentiel** — `profil`, `utilisateur`, `client`, `configuration_systeme`, `intervenant`,
`avocat`, `autre_prestataire`
**Cœur métier** — `dossier`, `etape_procedure`, `audience`, `delibere`, `prorogation`,
`decision_definitive`, `adjudication`, `condamnation_pecuniaire`, `jurisprudence`
**Intervenants** — `demande_frais`, `publication`, `compte_rendu_audience`, `autre_publication`
**GED & alertes** — `document`, `alerte`
**Traçabilité** — `historique_action`, `audit_seuil_dossier`

Héritage : `intervenant`→(`avocat`|`autre_prestataire`), `decision_definitive`→
(`adjudication`|`condamnation_pecuniaire`), `publication`→(`compte_rendu_audience`|`autre_publication`)
— **table-par-type avec PK partagée** (JPA : `@Inheritance(JOINED)`). Seule la hiérarchie
`Intervenant` a ses entités JPA à ce stade (M2) ; les deux autres suivront avec leurs modules
respectifs (M11 publications, M12 décisions).

### 4.2 Tables et colonnes ajoutées lors de l'audit — ✅ créées (M2)

| Table / colonne | Justification documentaire | Question | Entité JPA |
|---|---|---|---|
| `dossier_juriste` / `dossier_avocat` (N:N) | RG-DOS-03 « affecté à un ou plusieurs juristes et avocats » ; POST /dossiers reçoit `juristesAffectes[]`, `avocatsAffectes[]` — aucune table d'affectation n'existait | Q-01 | à créer en M4 |
| `demande_suppression` | FR-GED-03 cite explicitement « la table demande_suppression » ; UC-GED-03 (relance 72 h) | Q-02 | à créer en M8 |
| `demande_constitution` | FR-INT-06/07, UC-INT-05/06, US 5.6/5.7 « Tables constitution (à créer) » | Q-03 | à créer en M9 |
| `alarme` (distincte de `alerte`) | FR-AUD-03 : objet + échéance + étape ; `alerte` n'a ni objet ni etape_id | Q-04 | à créer en M6 |
| `validation_frais` (traces par étape) | FR-INT-02/03/04 : traçabilité des accords DJ/DJA distincts, impossible avec le seul `statut_circuit` | Q-05 | à créer en M10 |
| `type_client_sensible` (référentiel) | RG-DOS-09 « liste prédéfinie gérée par le DJ » | Q-06 | ✅ `TypeClientSensible` |
| `nature_dossier` (référentiel, Annexe A) | RG-DOS-02 ; CONF02 (taxonomie gérée par le DJ) | — | ✅ `NatureDossier` |
| `document.chemin_stockage` | Présent au dictionnaire, absent du script SQL REF-05 §12.1 | Q-07 | à créer en M8 |
| `audit_seuil_dossier.statut` | Exposé par le schéma `AuditSeuil` du contrat d'API, absent de la table documentée | Q-29 | à créer en M4 |
| `dossier.sensibilite_statut/valide_par/motif_rejet` | Circuit de validation DJ/DJA de la sensibilité (RG-DOS-09, UC-DOS-01 alt. 6-a) | Q-10 | à créer en M4 |

**Q-11** (cardinalité ET/OU des champs conditionnels par catégorie de dossier) reste **non
tranchée** : la contrainte `chk_categorie_coherence` reprend l'énoncé littéral de REF-05
(sémantique OU) en attendant l'arbitrage, prévu en M4.

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
