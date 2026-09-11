# DEVELOPMENT_ROADMAP

> Feuille de route séquentielle du backend. Un jalon = un objectif, un périmètre fermé,
> une validation manuelle par le porteur du projet avant passage au suivant.
> Dernière mise à jour : 2026-09-07 (M0 à M15 terminés et validés — feuille de route achevée,
> M15 ajouté hors roadmap initiale pour rendre le backend consommable par un frontend).

---

## Principes de conduite

1. **Un jalon à la fois.** Chaque jalon énonce son objectif, ce qui change, ce qui ne change pas,
   les fichiers touchés, les tests, et les **instructions de validation manuelle**.
2. Un jalon n'est **jamais** clos par le seul passage des tests automatiques : il l'est par la
   validation manuelle explicite, puis par le commit du porteur du projet.
3. Aucune amélioration hors périmètre du jalon en cours.
4. Toute ambiguïté découverte en cours de route est remontée dans
   `DECISIONS_AND_OPEN_QUESTIONS.md` — **jamais tranchée silencieusement**.
5. Les fichiers de contexte sont mis à jour à la fin de chaque jalon validé.

---

## État d'avancement

| Jalon | Intitulé | Prérequis de décision | Statut |
|---|---|---|---|
| **M0** | Audit et contexte projet | — | ✅ **Terminé** (2026-09-04) |
| **M1** | Assainissement du socle et sécurisation | R-01→R-05, Q-08, Q-16 | ✅ **Terminé et validé manuellement** (2026-09-04, commits `3025c46`, `e33f668`) |
| **M2** | Schéma de base et référentiels | Q-00 ✅, Q-01 ✅, Q-02 ✅, Q-03 ✅, Q-04 ✅, Q-05 ✅, Q-06 ✅, Q-07 ✅, Q-22 ✅, Q-26 ✅, Q-27 ✅, Q-29 ✅ | ✅ **Terminé et validé** (2026-09-04, commit `ceb0e77`) |
| **M3** | Socle transversal (erreurs, historique, utilisateur, pagination) | Q-08 ✅ | ✅ **Terminé et validé** (2026-09-04, commit `57e75c3`) |
| **M4** | Module Dossiers | Q-10, Q-11, Q-12, Q-23, Q-26, Q-28 | ✅ **Terminé et validé manuellement** (2026-09-04, commit `955aa4c`, PR #2 mergée dans `dev`) |
| **M5** | Module Alertes (moteur + notifications) | Q-22 ✅, Q-32 ✅, Q-36 ✅, Q-37 ✅, Q-38 ✅ | ✅ **Terminé et validé manuellement** (2026-09-04, commit `7cd8954`, PR #4 mergée dans `dev`) |
| **M6** | Module Audiences + Alarmes | Q-04 ✅, Q-12 ✅, Q-13 ✅, Q-39 ✅, Q-40 ✅, Q-41 ✅ | ✅ **Terminé et validé manuellement** (2026-09-04, commit `e427be3`, PR #5 mergée dans `dev`) |
| **M7** | Module Délibérés | Q-13 ✅, Q-17 ✅, Q-18 ✅, Q-19 ✅, Q-35 ✅, Q-42 ✅, Q-43 ✅ | ✅ **Terminé et validé manuellement** (2026-09-06, commit `5a99400`, PR #6 mergée dans `dev`) |
| **M8** | Module GED | Q-02 ✅, Q-07 ✅, Q-45 ✅, Q-46 ✅, Q-47 ✅, Q-48 ✅, Q-49 ✅ | ✅ **Terminé et validé manuellement** (2026-09-06) |
| **M9** | Module Intervenants (avocats, répertoire, constitutions) | Q-03 ✅, Q-28 ✅, Q-52 ✅, Q-53 ✅ | ✅ **Terminé et validé manuellement** (2026-09-06) |
| **M10** | Module Frais (circuit de validation) | Q-05 ✅, Q-24 🟡, Q-26 ✅, Q-54 ✅ | ✅ **Terminé et validé manuellement** (2026-09-07) |
| **M11** | Module Publications et correspondances | Q-20 ✅, Q-24 🟡, Q-25 | ✅ **Terminé et validé manuellement** (2026-09-07) |
| **M12** | Module Décisions définitives et jurisprudence | Q-58 ✅, Q-59 ✅, Q-60 ✅, Q-61 ✅, Q-62 ✅ | ✅ **Terminé et validé manuellement** (2026-09-07) |
| **M13** | Module Reporting et tableau de bord | Q-14 (sans impact code), Q-63 ✅, Q-64 ✅ | ✅ **Terminé et validé manuellement** (2026-09-07) |
| **M14** | Durcissement, tests d'intégration, vérification finale de l'API | Q-65 ✅, Q-66 ✅, Q-67 ✅, R-08 🟡 | ✅ **Terminé et validé manuellement** (2026-09-07) |
| **M15** | Habilitation frontend (correctif R-06 + endpoints de lecture manquants) | Q-24 ✅, Q-68 ✅, Q-69 ✅, R-06 ✅ | ✅ **Terminé et validé manuellement** (2026-09-07) |

**Feuille de route achevée** — les 8 épics fonctionnels, le durcissement final et l'habilitation du
backend pour un frontend sont livrés. **Toutes les questions fonctionnelles sont tranchées** :
Q-59, Q-60 et Q-67, laissées ouvertes pour confirmation, ont été **arbitrées par le porteur du
projet le 2026-09-07 en faveur des recommandations émises** — sans modification de code, le
comportement livré étant déjà celui recommandé.

Points restants, **tous liés à la mise en production et non au développement en cours** : R-01
(rotation des secrets déjà exposés), R-08 (documentation d'API accessible sans authentification),
campagne de performance outillée sur volumétrie réelle.

---

## M0 — Audit et contexte projet ✅

**Réalisé le 2026-09-04.** Lecture intégrale des 8 documents de `Doc/` (y compris le PDF scanné du
cahier des charges), audit complet du code, reconstruction du modèle fonctionnel, création des
6 fichiers de contexte persistant. **Aucun code modifié.**

**Livrables** : `PROJECT_CONTEXT.md`, `SYSTEM_ARCHITECTURE.md`, `FUNCTIONAL_MAP.md`,
`API_IMPLEMENTATION_STATUS.md`, `DECISIONS_AND_OPEN_QUESTIONS.md`, `DEVELOPMENT_ROADMAP.md`.

---

## M1 — Assainissement du socle et sécurisation ✅ **Terminé**

**Objectif.** Obtenir un socle Spring Boot propre, sécurisé et conforme aux hypothèses de
conception validées, sans aucune fonctionnalité métier. Le dépôt doit démarrer, exposer Swagger,
authentifier via Keycloak et **rien d'autre**.

### Réalisé
- **Suppression** du squelette hors périmètre : Template*, Admin/User/Staff/Dashboard
  controllers, chat, événements WebSocket (30 fichiers).
- **Suppression de l'authentification héritée** (R-03) : `User`, `UserRepository`,
  `AuthController/Service`, `JwtService`, `JwtAuthenticationFilter`, `UserDetailsServiceImpl`,
  DTO `auth/`, dépendances `jjwt-*`.
- **Sécurité WebSocket** : `WebSocketAuthInterceptor` réécrit sur le `JwtDecoder`/
  `JwtAuthenticationConverter` Keycloak — la trame STOMP `CONNECT` doit porter un jeton
  Keycloak valide, toute trame ultérieure sans session authentifiée est rejetée.
- **Secrets (R-01)** : `env.template`/`.env` ne contiennent plus de secrets cloud réels.
- **Infrastructure locale (R-02)** : base applicative `audiences_app` créée automatiquement
  (script d'init PostgreSQL), MinIO local activé — Neon et iDrive e2 retirés.
- **Ports (Q-16)** : backend `8080`, Keycloak `8081`.
- **RBAC (Q-08)** : `ROLE_SAISIE` ajouté comme rôle composite (JURISTE, DJ, DJA, ASSISTANTE, SH)
  — vérifié en conditions réelles, présent dans un jeton émis.
- **`pom.xml`** : doublons supprimés, `itextpdf` (AGPL) retiré, MapStruct câblé au compilateur
  (+ `lombok-mapstruct-binding`).
- **Correctif découvert en validation** : healthcheck Keycloak corrigé (`NullPointerException`
  sur requête HTTP/1.0 sans en-tête `Host`, rendait le conteneur `unhealthy` en permanence).
- **Test** : classe renommée `AudiencesApplicationTests` (Java conventionnel).
- **README** : instructions locales + checklist de validation manuelle en 6 étapes.

### Écarts au plan initial (documentés, décisions d'implémentation, pas de nouvelle ambiguïté)
- `Role.java` **supprimé** plutôt que déplacé vers `common/security` : aucun consommateur restant
  après suppression du squelette ; sera réintroduit selon la conception réelle du module
  référentiel en M2/M3.
- `/ws/**` **reste** `permitAll` côté filtre HTTP (nécessaire aux transports de repli SockJS,
  qui ne peuvent pas fixer d'en-tête `Authorization` sur la requête HTTP initiale) ;
  l'authentification réelle est appliquée au niveau de la trame STOMP `CONNECT`, pas au niveau HTTP.
- Flyway et Testcontainers **non ajoutés** en M1 (reportés à M2, où ils deviennent effectivement
  utiles — éviter une dépendance inerte sans migration ni test à faire tourner).
- Port hôte PostgreSQL local : **5433** par défaut (`POSTGRES_PORT`), pas 5432, pour éviter un
  conflit avec une installation PostgreSQL native sur les postes Windows (constaté en conditions
  réelles : service `postgresql-x64-17`).

### Ce qui n'a pas changé
Aucune entité métier, aucun endpoint métier, aucun schéma de base — confirmé.

### Validation manuelle — réalisée et confirmée par le porteur du projet (2026-09-04)
Les 6 étapes de la checklist du README (`docker compose up`, base `audiences_app` créée, jeton
Keycloak avec `ROLE_SAISIE`, `mvn spring-boot:run` sans erreur, sécurité HTTP conforme
— 200/401/200/404 —, aucun secret dans les fichiers versionnés) sont **toutes passées**, y
compris après correction de deux artefacts de la documentation (placeholder `<token>` non
substitué, faux positif du `git grep` sur son propre exemple dans le README).

### Commits
`3025c46` `docs: add persistent project audit context (Phase 0)`
`e33f668` `chore(cleanup): remove starter skeleton and legacy auth, secure socket auth, align local infra`

### Restant
Rien côté code. Reste une action utilisateur hors périmètre du dépôt : faire tourner les secrets
Neon/iDrive déjà exposés avant M1, et statuer sur une éventuelle purge de l'historique git (R-01).

---

## M2 — Schéma de base et référentiels ✅ **Terminé**

**Objectif.** Traduire REF-05 §12.1 + REF-06 en migrations Flyway versionnées, complétées des
tables manquantes arbitrées (Q-01 à Q-07, Q-22, Q-26, Q-27, Q-29).

### Réalisé
- Flyway ajouté (`flyway-core`, `flyway-database-postgresql`), `ddl-auto` basculé sur `validate`.
- `V1__schema_initial.sql` : **32 tables** (24 documentées + 8 ajoutées lors de l'audit :
  `dossier_juriste`, `dossier_avocat`, `alarme`, `type_client_sensible`, `nature_dossier`,
  `demande_suppression`, `demande_constitution`, `validation_frais`), contraintes `CHECK`, FK
  avec comportement `ON DELETE` cohérent, index de performance.
- `V2__donnees_reference.sql` : 7 profils, 7 clés `configuration_systeme`, 3 types de client
  sensible, 17 natures de dossier (Annexe A).
- Module `referentiel` : entités JPA + repositories — `Profil`, `Utilisateur`, `Client`,
  `ConfigurationSysteme`, `TypeClientSensible`, `NatureDossier`, `Intervenant` (héritage
  `JOINED`) → `Avocat`, `AutrePrestataire`.
- **7 questions P1 tranchées et appliquées** : Q-02, Q-03, Q-05, Q-06, Q-07, Q-27, Q-29
  (recommandations déjà documentées à l'audit, appliquées et signalées comme telles).

### Écart au plan initial
**Q-11** (cardinalité ET/OU des champs conditionnels par catégorie) volontairement **non
tranchée** — explicitement hors périmètre de M2 (scopée à M4 dans ce tableau). La contrainte
`chk_categorie_coherence` reprend l'énoncé littéral de REF-05 (sémantique OU), à resserrer si
besoin par une migration ultérieure une fois Q-11 arbitrée.

### Validation manuelle — réalisée et confirmée par le porteur du projet (2026-09-04)
Migrations appliquées à froid sur PostgreSQL 17 réel (`Successfully applied 2 migrations`) ;
**Hibernate `ddl-auto=validate` confirme la correspondance exacte des 8 entités avec le schéma** ;
idempotence vérifiée sur redémarrage à froid ; données de référence vérifiées en base (7/7/3/17) ;
`mvn test` vert (`contextLoads`, contexte Spring complet + DB réelle) ; non-régression sécurité
M1 confirmée (401 sans jeton, 200 sur `/actuator/health`).

### Commit
`ceb0e77` `feat(db): initial Flyway schema and référentiel module (M2)`

### Restant
Rien côté schéma des référentiels. Les entités des autres modules (dossier, audience, délibéré,
frais, GED, alertes, publications, décisions) viendront module par module sur ce même schéma.

---

## M3 — Socle transversal ✅ **Terminé**

**Objectif.** Fournir aux modules métier les briques dont ils dépendent tous.

### Réalisé
- `common/error` : `ApiException`, `ErrorCodes` (`ERR-001`…`ERR-009` du contrat + codes
  génériques), `ErrorResponse`, `GlobalExceptionHandler` (`@RestControllerAdvice`).
- `SecurityConfig` : `AuthenticationEntryPoint`/`AccessDeniedHandler` custom pour que les 401/403
  déclenchés au niveau du filtre (avant tout contrôleur) suivent aussi le format du contrat ;
  suppression d'un bean `PasswordEncoder` mort, reliquat de M1.
- `common/security/CurrentUserService` : résolution + provisioning de l'utilisateur à partir du
  jeton Keycloak, profil déterminé depuis le rôle métier (rôles transverses `ROLE_SAISIE`/
  `ROLE_CONSULTATION` ignorés dans le choix du profil).
- `common/audit` : entité `HistoriqueAction` (JSONB natif Hibernate 6) + `HistoriqueActionService`.
- `common/web/PageResponse<T>` : enveloppe de pagination conforme au contrat.
- `common/config/SchedulingConfig` : `@EnableScheduling`.

### Écart au plan initial
Les **utilitaires d'export PDF/Excel/CSV** initialement prévus dans ce jalon ont été
**volontairement écartés** : sans rapport concret à exporter (aucun module métier n'existe
encore), toute utilité générique serait spéculative et non testable. Reportés au module qui en
aura effectivement besoin (M6 calendrier d'audiences, ou M13 reporting).

### Validation manuelle — adaptée à l'absence de contrôleur
M3 n'introduit aucun endpoint : contrairement à M1/M2, aucun appel HTTP manuel n'était possible.
Validation reposant sur des tests automatisés réels, confirmés par le porteur du projet :
**20/20 tests verts** — 16 tests unitaires (Mockito) sur `GlobalExceptionHandler`,
`CurrentUserService`, `HistoriqueActionService` ; 1 test d'intégration **Testcontainers**
(`CurrentUserServiceIntegrationTest`, PostgreSQL réel migré par Flyway — provisioning,
idempotence, seed des 7 profils vérifiés) ; `contextLoads` toujours vert (aucune régression).

### Commit
`57e75c3` `feat(common): transversal socle — error handling, current user, audit trail (M3)`

### Restant
Rien côté socle transversal. Chaque module métier (M4+) consommera `ApiException`,
`CurrentUserService`, `HistoriqueActionService`, `PageResponse`.

---

## M4 — Module Dossiers ✅ **Terminé**

**Objectif.** Épic 1 complet (7 user stories) + clients.
Endpoints #1 à #12 de `API_IMPLEMENTATION_STATUS.md`.

### Réalisé
- Domaine complet : `Dossier`, `EtapeProcedure`, `DossierJuriste`/`DossierAvocat` (clés
  composites `@EmbeddedId`/`@MapsId`), `AuditSeuilDossier`, enums `TypeEtape`, `StatutCycleVie`,
  `SeuilOrigine`, `StatutValidation`.
- `EtapeTransitionValidator` : machine à états des transitions de cycle de vie d'une étape,
  excluant explicitement `EN_DELIBERE→DELIBERE_VIDE`/`EN_COURS` (réservées au futur module
  Délibérés — Q-35).
- `DossierService` (orchestration) : création avec catégorie déduite de la nature et validation
  `ET` des champs conditionnels (Q-11), affectations N:N avec détection de doublon et
  confirmation via `forcer=true` (Q-12), positionnement direct d'une étape avec création
  automatique des étapes intermédiaires manquantes et ouverture automatique idempotente de
  l'étape suivante sur exercice d'un recours (Q-34), circuit de sensibilité et de dérogation de
  seuil unifiés sur la table `audit_seuil_dossier` comme file de propositions en attente
  (Q-10, Q-26).
- 13 endpoints exposés : les 12 du contrat (`POST/GET /dossiers`, affectation, statut d'étape,
  seuil-dérogation ×2, sensibilité/validation, `GET/POST /clients`, `GET /clients/{id}`,
  `GET /clients/{clientId}/dossiers`) + `POST /dossiers/{id}/etapes` (🆕, Q-34).

### Décisions prises en implémentation (documentées dans `DECISIONS_AND_OPEN_QUESTIONS.md`)
Q-01 (affectations), Q-10 (circuit de sensibilité), Q-11 (validation `ET` en couche service),
Q-12 (`forcer` sur doublon), Q-23 (`POST /clients`), Q-26 (dérogation de seuil, même mécanisme
que Q-10), Q-34 (nouvel endpoint 🆕), Q-35 (frontière avec les endpoints dédiés M7).

### Validation manuelle — réalisée et confirmée par le porteur du projet (2026-09-04)
51/51 tests automatisés verts (`mvn test`, dont 31 nouveaux pour M4, aucune régression M1-M3) ;
validation manuelle de bout en bout (curl + JWT Keycloak réels, DB Postgres locale) des 13
endpoints, y compris les cas limites (doublon d'affectation, transitions bloquées, rollback
transactionnel, ouverture automatique d'étape dans les deux cas — création et idempotence).

### Commit
`955aa4c` `feat(dossier): implement Dossiers and Clients module (M4)` — PR #2 mergée dans `dev`.

### Restant
`PUT /clients/{id}` non implémenté (aucun besoin identifié) ; génération effective d'alertes
`DOUBLON_AFFECTATION` différée au module M5 (le mécanisme de confirmation est complet) ; volet
audience du doublon (Q-12) différé au module M6.

---

## M5 — Module Alertes ✅ **Terminé**

**Objectif.** Épic 7. Moteur de déclenchement (`@Scheduled` + événements Spring), énumération
arrêtée en Q-22, canaux applicatif (obligatoire) / SMTP / Teams (webhook), configuration des seuils.
Placé avant les modules qui en dépendent (audiences, délibérés, GED, frais, publications).

### Réalisé
- Module `alerte` : entité `Alerte` (référence `dossierId` libre, sans dépendance vers `dossier` —
  même principe que `HistoriqueAction`, cf. M3), enums `TypeAlerte` (17 valeurs, Q-22),
  `CanalAlerte`, `StatutAlerte`.
- `AlerteService` : point d'entrée unique de déclenchement (`declencher`, sur le modèle de
  `HistoriqueActionService`), consultation paginée (`mesNotifications`), traitement avec
  vérification du destinataire (`traiter`), anti-doublon (`estDejaSignalee`), push WebSocket
  best-effort (Q-32) avec repli garanti sur `GET /alertes/mes-notifications`.
- `ChargeMaxAvocatScheduler` : vérification planifiée du seuil `CHARGE_MAX_AVOCAT` — placé dans le
  module `dossier` (et non `alerte`) pour ne jamais faire dépendre le socle transversal d'un
  module métier (Q-38).
- `ConfigurationService`/`ConfigurationController` (CONF01-07) : `GET /configurations`,
  `PUT /configurations/{cle}`.
- 5 endpoints du contrat (#44-48).
- Intégration M4→M5 : le doublon d'affectation confirmé (`forcer=true`, Q-12) déclenche désormais
  une alerte `DOUBLON_AFFECTATION` vers les DJ actifs (Q-36).

### Décisions prises en implémentation (documentées dans `DECISIONS_AND_OPEN_QUESTIONS.md`)
Q-22 (liste des types), Q-32 (WebSocket + repli), Q-36 (destinataire du doublon d'affectation),
Q-37 (canal `APPLICATIF` uniquement — Outlook/Teams différés, aucune infrastructure SMTP/webhook
fournie au projet), Q-38 (destinataire et placement du calcul planifié `CHARGE_MAX_AVOCAT`).

### Validation manuelle — réalisée et confirmée par le porteur du projet (2026-09-04)
67/67 tests automatisés verts (`mvn test`, dont 15 nouveaux pour M5, aucune régression M1-M4) ;
validation manuelle de bout en bout (curl + JWT Keycloak réels) : restriction de rôle sur
`GET/PUT /configurations`, rejet 400 de `PUT /alertes/seuils` sur un type non supporté,
déclenchement planifié réel de `CHARGE_MAX_AVOCAT` (intervalle réduit à 5 s pour le test) avec
anti-doublon confirmé (une seule alerte tant que non traitée, nouvelle alerte après traitement),
`PATCH /alertes/{id}/traiter` (403 non-destinataire / 200 destinataire), doublon d'affectation
confirmé déclenchant bien une alerte visible côté DJ.

### Commit
Module livré en un seul commit (docs de contexte incluses, à la demande du porteur du projet).

### Restant
Canaux Outlook/Teams (Q-37, aucune infrastructure fournie) ; alarmes libres (table `alarme`,
prévues M6, Q-04) ; push WebSocket non testé avec un client STOMP réel dans cette session (couvert
par 2 tests unitaires dédiés — succès et échec du push — le repli HTTP est lui testé en conditions
réelles).

---

## M6 — Module Audiences + Alarmes ✅ **Terminé**

**Objectif.** Épic 2 (6 US). Planification + programmation automatique J-7/J-3/J-1, compte rendu
juriste, alarmes libres (Q-04), reprogrammation, calendrier avec exports, détection de doublon.
Endpoints #13 à #17 de `API_IMPLEMENTATION_STATUS.md`.

### Réalisé
- Module `audience` : entités `Audience`/`Alarme` (avec `@ManyToOne Dossier`/`EtapeProcedure`
  directes — module dépendant de `dossier`, cf. FUNCTIONAL_MAP.md §2), `AudienceService`
  (planification avec détection de doublon confirmable, compte rendu), `AlarmeService` (création,
  reprogrammation avec chaîne `alarme_precedente_id`).
- `RappelAudienceScheduler` : rappels J-7/J-3/J-1 vers chaque juriste affecté (Q-39), anti-doublon
  par (audience, décalage, destinataire).
- `AlarmeEcheanceScheduler` : alerte `ALARME` vers le créateur à l'échéance, une seule fois par
  alarme tant qu'elle n'est pas reprogrammée.
- `CalendrierExportService` : export JSON/PDF (PDFBox)/Excel (Apache POI) — Q-41.
- Intégration M4→M6 : le volet audience du doublon confirmé (Q-12, RG-AUD-06, resté ouvert en M4)
  déclenche une alerte `DOUBLON_AUDIENCE` vers les DJ, sur le même mécanisme que
  `DOUBLON_AFFECTATION` (M5).
- 5 endpoints du contrat (#13-17, dont 2 hors OpenAPI formel — Q-13).

### Décisions prises en implémentation (documentées dans `DECISIONS_AND_OPEN_QUESTIONS.md`)
Q-04 (implémentation de la table `alarme` posée en M2), Q-12 (clôture du volet audience), Q-13
(2/3 endpoints absents de l'OpenAPI implémentés), Q-39 (destinataires des alertes
`RAPPEL_AUDIENCE`/`ALARME`), Q-40 (`date_tenue` = date de saisie, « prochaine date » = 
`date_planifiee`), Q-41 (sélection du format d'export par paramètre de requête).

### Validation manuelle — réalisée et confirmée par le porteur du projet (2026-09-04)
88/88 tests automatisés verts (`mvn test`, dont 24 nouveaux pour M6, aucune régression M1-M5) ;
validation manuelle de bout en bout (curl + JWT Keycloak réels) : rejet d'une date passée à la
planification, doublon d'audience confirmé avec alerte DJ, compte rendu refusé sur audience
future puis accepté après passage de la date, alarme créée/reprogrammée avec chaîne de
précédence, échéance d'alarme et rappels J-7 déclenchés en conditions réelles (cron/intervalle
accélérés pour le test) avec anti-doublon vérifié sur plusieurs cycles, export du calendrier dans
les 3 formats (signatures de fichier PDF/XLSX vérifiées).

### Commit
Module livré en un seul commit (docs de contexte incluses, comme pour M5).

### Restant
`PUT /deliberes/{id}/expedition` (Q-13, volet restant) différé au module M7 ; canaux Outlook/Teams
des alertes toujours hors périmètre (Q-37, aucune infrastructure fournie).

---

## M7 — Module Délibérés ✅ **Terminé**

**Objectif.** Épic 3 (6 US). Enregistrement, prorogations illimitées + alerte au seuil (Q-17,
Q-18), rabattement, suivi du délai de recours avec bascule automatique en `CLOTURE`, levée
d'expédition. Dernier volet de Q-13 (`PUT /deliberes/{id}/expedition`).

### Réalisé
- Module `delibere` : entités `Delibere`/`Prorogation` (avec `@ManyToOne Dossier`/`EtapeProcedure`
  directes — module dépendant de `dossier`/`audience`, cf. FUNCTIONAL_MAP.md §3, même principe que
  `audience` en M6), enums `ResultatDelibere`, `StatutExpedition`.
- `DelibereService` : `enregistrerDelibere` (étape `EN_DELIBERE` requise, transition directe vers
  `DELIBERE_VIDE`, échéance de recours requise/interdite selon le résultat — Q-42), `proroger`
  (compteur cumulé, `alerte:true` dès dépassement du seuil configurable — Q-17/Q-18), `rabattre`
  (réouverture de l'étape en `EN_COURS` sans jamais ouvrir l'étape suivante, motif obligatoire —
  distinction avec l'exercice d'un recours, Q-42), `consulterRecours` (lecture seule — Q-19),
  `modifierExpedition`.
- `DelaiRecoursScheduler` : à l'échéance du délai de recours sans recours exercé, déclenche
  `TypeAlerte.DELAI_RECOURS` vers les juristes affectés puis bascule l'étape en `CLOTURE` — placé
  dans le module `delibere` (jamais dans `alerte`), même principe que `ChargeMaxAvocatScheduler`
  (M5) et `RappelAudienceScheduler`/`AlarmeEcheanceScheduler` (M6). Le volet « bascule décisions
  définitives » de FR-DEL-02 est différé au module M12 (Q-43).
- Correction de Q-35 : le rabattement cible en réalité `DELIBERE_VIDE→EN_COURS` (et non
  `EN_DELIBERE→EN_COURS` comme anticipé en M6) — même valeur de transition que l'exercice d'un
  recours (M4), distinguée par l'endpoint appelé et les effets de bord (Q-42).
- 5 endpoints du contrat (#18-22, dont le dernier volet de Q-13, `PUT /deliberes/{id}/expedition`,
  jusque-là hors OpenAPI formel).

### Décisions prises en implémentation (documentées dans `DECISIONS_AND_OPEN_QUESTIONS.md`)
Q-13 (dernier volet, expédition), Q-17/Q-18 (code retour et seuil des prorogations), Q-19 (révisé —
`Delibere.dateEcheanceRecours` dédiée plutôt que réutilisation de l'alarme M6), Q-35 (correction de
la cible du rabattement), Q-42 (distinction rabattement/recours exercé), Q-43 (bascule `CLOTURE`
implémentée, volet décisions définitives différé à M12).

### Validation manuelle — réalisée et confirmée par le porteur du projet (2026-09-06)
102/102 tests automatisés verts (`mvn test`, dont 14 nouveaux pour M7, aucune régression M1-M6) ;
validation manuelle de bout en bout (curl + JWT Keycloak réels) : rejet d'un délibéré sur étape non
`EN_DELIBERE`, échéance de recours refusée sur un résultat FAVORABLE puis exigée sur DEFAVORABLE,
enregistrement faisant basculer l'étape en `DELIBERE_VIDE`, 10 puis 11 prorogations (alerte
`PROROGATION` déclenchée une seule fois vers DJ et DJA), seuil modifié par le DJ pris en compte,
rabattement refusé hors `DELIBERE_VIDE` puis accepté (étape repassée `EN_COURS` sans ouverture de
l'étape suivante), nouveau cycle `EN_COURS`→`EN_DELIBERE`→nouveau délibéré sur la même étape,
`GET /deliberes/{id}/recours` strictement consultatif, expiration du délai de recours déclenchée en
conditions réelles (cron accéléré pour le test) avec bascule `CLOTURE` et alerte `DELAI_RECOURS`
vers les juristes affectés, `PUT /deliberes/{id}/expedition` (403 hors rôle JURISTE / 200 sinon).

### Commit
Module livré en un seul commit (docs de contexte incluses, comme pour M5/M6).

### Restant
Bascule vers le module Décisions définitives à l'expiration du délai de recours (Q-43, différée
à M12, module non encore conçu) ; canaux Outlook/Teams des alertes toujours hors périmètre (Q-37).

---

## M8 — Module GED ✅ **Terminé**

**Objectif.** Épic 6 (6 US). Intégration MinIO (URLs pré-signées), contrôle format/poids,
notification des juristes cotés, circuit de suppression avec approbation et relance 72 h,
suppression directe DJ/DJA, rapport journalier.

### Réalisé
- Module `ged` : entités `Document`/`DemandeSuppression` (`@ManyToOne Dossier` direct — module
  dépendant de `dossier`/`audience`, même principe que `delibere` en M7), enums `FormatDocument`,
  `StatutSuppression`.
- `MinioConfig`/`MinioStorageService` : client MinIO, bootstrap du bucket applicatif au démarrage,
  dépôt/URL pré-signée (10 min)/suppression d'objet (best-effort).
- `GedService` : `uploaderDocument` (format dérivé de l'extension + poids ≤ `TAILLE_MAX_GED`,
  notifie les juristes cotés — Q-45/Q-46), `obtenirDocument`/`listerDocumentsDossier` (404 si
  supprimé), `supprimerOuDemander` (double comportement DJ/DJA vs juriste auteur — Q-47),
  `approuverOuRejeter` (motif obligatoire au rejet), `rapportJournalier` (4 indicateurs — Q-49).
- `SuppressionRelanceScheduler` : relance à 72 h des demandes en attente — placé dans le module
  `ged` (jamais dans `alerte`), même principe que les planificateurs des jalons précédents.
- 2 bugs détectés et corrigés en validation manuelle : référence circulaire au bootstrap MinIO
  (Q-50) et conflit de gestionnaire d'exception pour les fichiers trop volumineux (Q-51).
- 6 endpoints du contrat (#38-43, dont l'approbation « à proposer » et la liste par dossier 🆕).

### Décisions prises en implémentation (documentées dans `DECISIONS_AND_OPEN_QUESTIONS.md`)
Q-02 (relance 72 h), Q-45 (`type_document` texte libre), Q-46 (format dérivé de l'extension),
Q-47 (double comportement de la suppression), Q-48 (réutilisation de `SUPPRESSION_DOCUMENT`),
Q-49 (périmètre du rapport journalier, dépendance étendue vers `audience`), Q-50/Q-51 (bugs
corrigés).

### Validation manuelle — réalisée et confirmée par le porteur du projet (2026-09-06)
118/118 tests automatisés verts (`mvn test`, dont 16 nouveaux pour M8, aucune régression M1-M7) ;
validation manuelle de bout en bout (curl + JWT Keycloak réels) : rejet d'un format non conforme
et d'un fichier trop volumineux, dépôt réussi avec notification des juristes cotés, URL pré-signée
de téléchargement, liste des documents d'un dossier, demande de suppression par l'auteur (202,
409 en cas de doublon), refus pour un juriste non auteur (403), suppression directe DJ/DJA (200),
approbation (motif non requis) et rejet (motif obligatoire) d'une demande, relance à 72 h
déclenchée en conditions réelles (date antidatée, cron accéléré) avec anti-doublon, rapport
journalier correct sur une date active et à zéro sur une date sans donnée.

### Commit
Module livré en un seul commit (docs de contexte incluses, comme pour M5/M6/M7).

### Restant
Canaux Outlook/Teams des alertes toujours hors périmètre (Q-37) ; aucun reste propre à la GED.

---

## M9 — Module Intervenants ✅ **Terminé**

**Objectif.** Répertoire des avocats avec indicateurs de charge et de performance, gestion des
prestataires, demandes de constitution + seconde signature SH + génération de la lettre (US 5.6,
5.7, 5.8).

### Réalisé
- Module `intervenant` : entité `DemandeConstitution` (`@ManyToOne Dossier`/`Intervenant`
  polymorphe/`Document` direct — dépend de `dossier`, `delibere` et `ged`, en plus de
  `referentiel`/`alerte` — cf. Q-53), enum `StatutConstitution`.
- `ConstitutionService` : `creerDemande` (notifie les SH actifs), `valider` (approbation → génère
  la lettre via `LettreConstitutionService` (PDFBox) et l'attache comme `Document` GED (M8),
  notifie l'avocat s'il a un compte ; rejet → motif obligatoire, notifie le demandeur ; 409 si déjà
  décidée).
- `RepertoireAvocatsService` : `indicateurCharge` (dossiers actifs, nouvelle requête dans
  `DossierAvocatRepository`) et `indicateurPerformance` (taux de délibérés `FAVORABLE`, nouvelle
  requête dans `DelibereRepository`) calculés à la demande, triés par charge croissante, recherche
  par nom.
- `IntervenantRepository` (référentiel) : résolution polymorphe d'un prestataire (avocat ou autre).
- `MinioStorageService` étendu d'une variante pour un document généré par le système (sans passer
  par un `MultipartFile` d'upload HTTP).
- 3 endpoints du contrat (#35-37, tous hors OpenAPI formel).

### Décisions prises en implémentation (documentées dans `DECISIONS_AND_OPEN_QUESTIONS.md`)
Q-03 (circuit complet), Q-28 (affectation et constitution restent deux circuits indépendants),
Q-52 (réutilisation de `CONSTITUTION_A_SIGNER`, notification impossible d'un prestataire sans
compte), Q-53 (indicateurs calculés à la demande, dépendances étendues de `intervenant`).

### Validation manuelle — réalisée et confirmée par le porteur du projet (2026-09-06)
129/129 tests automatisés verts (`mvn test`, dont 11 nouveaux pour M9, aucune régression M1-M8) ;
validation manuelle de bout en bout (curl + JWT Keycloak réels) : répertoire correct (charge,
performance à 0 sans délibéré, tri croissant, recherche par nom, rôles JURISTE/ASSISTANTE),
demande de constitution notifiant les SH actifs, approbation générant une lettre PDF valide
enregistrée dans la GED (consultable via `GET /ged/documents/{id}` et `GET
/dossiers/{id}/documents`) et notifiant l'avocat, rejet exigeant un motif et notifiant le
demandeur, décision déjà prise refusée (409), absence de notification silencieuse pour un
prestataire sans compte applicatif.

### Commit
Module livré en un seul commit (docs de contexte incluses, comme pour M5 à M8).

### Restant
Notification effective d'un `AutrePrestataire` (aucun compte, aucune infrastructure email/SMTP —
Q-37/Q-52, à réévaluer si une infrastructure est fournie) ; reste de l'Épic 5 (publications,
comptes rendus, correspondances) différé à M11.

---

## M10 — Module Frais ✅ **Terminé**

**Objectif.** Épic 4 (6 US). Circuit complet dépôt → conformité → opportunité → validation
conjointe → paiement, anti-doublon de facture, résolution du seuil applicable, traçabilité par
étape (Q-05).

### Réalisé
- Module `frais` : entités `DemandeFrais`/`ValidationFrais` (tables déjà créées en M2, Q-05),
  enums `StatutCircuitFrais`, `StatutPaiementFrais`, `EtapeValidationFrais`,
  `DecisionValidationFrais`. Dépend de `dossier` (affectation `dossier_avocat`) et `referentiel`
  (`Avocat`, `ConfigurationService`), **pas** de `intervenant` — correction du schéma de
  dépendances de `FUNCTIONAL_MAP.md` §0 (Q-54).
- `FraisService` : `deposer` (avocat affecté au dossier, anti-doublon `referenceFacture` ERR-006,
  notifie l'Assistante), `controlerConformite` (Assistante, conforme → notifie DJA / rejet → motif
  obligatoire + notifie l'avocat), `controlerOpportunite` (DJA, sensible ou montant > seuil →
  `OPPORTUNITE` + notifie DJ/DJA, sinon `VALIDEE` directe), `validerConjointement` (DJ **et** DJA,
  accord individuel tracé, anti double-vote, deux accords → `VALIDEE`, un refus → `REJETEE`),
  `enregistrerPaiement` (Assistante, `VALIDEE` requis, notifie avocat + DJ/DJA), `lister` (endpoint
  🆕 `GET /frais/demandes?statut=`, Q-24 — propre à l'avocat pour `ROLE_AVOCAT`, transverse pour le
  personnel interne).
- 6 endpoints (5 du contrat formel + 1 🆕).

### Décisions prises en implémentation (documentées dans `DECISIONS_AND_OPEN_QUESTIONS.md`)
Q-05 (traçabilité, déjà tranchée en M2), Q-24 (file de travail frais résolue, autres volets
toujours ouverts), Q-26 (seuil dossier/global, déjà tranchée en M4), Q-54 (« avocat constitué »
interprété comme affectation `dossier_avocat`, anti-doublon de facture unique dès le dépôt tous
statuts confondus, notification systématique de l'avocat sur tout rejet quelle que soit l'étape).

### Validation
17 tests automatisés dédiés (`FraisServiceTest`) + 146 tests au total après M10, tous verts sauf le
test de fumée `AudiencesApplicationTests.contextLoads`, qui échoue systématiquement en `mvn test`
pour une cause **pré-existante et sans lien avec ce module** — reproduite à l'identique sur `dev`
non modifié (R-07). Le démarrage de l'application via `mvn spring-boot:run` a lui aussi échoué à
trois reprises avec la même erreur avant de réussir à la quatrième tentative (comportement
intermittent, pas un blocage dur — R-07) ; une fois démarrée, l'application a fonctionné normalement
et la **validation manuelle de bout en bout par `curl` (JWT Keycloak réels) a été réalisée** :
- dépôt sans pièces (400), dépôt nominal (201), facture en double (409/ERR-006) ;
- contrôle de conformité par un rôle non autorisé (403), conforme → notifie le DJA, demande déjà
  traitée (409) ;
- opportunité non sensible sous seuil → `VALIDEE` directe (DJA seul) ; dossier rendu sensible →
  `OPPORTUNITE` + notifie DJ et DJA ;
- validation conjointe : un seul accord ne valide pas, double-vote refusé (409), le second accord
  (DJA) valide ; un seul refus (DJ) → `REJETEE` immédiate ;
- rejet sans motif refusé (400), rejet avec motif → notifie l'avocat (vérifié à chaque étape :
  conformité et opportunité) ;
- paiement par un rôle non autorisé (403), paiement d'une demande non validée (400), paiement
  nominal → `PAYEE` + notifie l'avocat (`PAIEMENT_EFFECTUE`) ;
- `GET /frais/demandes` : avocat ne voit que ses propres demandes (filtrable par statut), personnel
  interne (Assistante) voit toutes les demandes.

Toutes les preuves attendues (`FUNCTIONAL_MAP.md` §4 « Tests attendus ») sont couvertes, en direct
comme en tests automatisés.

### Commit
Module livré en un seul commit (docs de contexte incluses, comme pour M5 à M9).

### Restant
Reste de l'Épic 8 (Décisions définitives, Reporting) et Épic 5 (publications) différés aux jalons
suivants. R-07 (intermittence de démarrage, sans impact final sur ce jalon) à surveiller en M14.

## M11 — Module Publications ✅ **Terminé**

**Objectif.** US 5.1 à 5.5. Comptes rendus d'avocat + validation Assistante, autres publications,
restriction d'accès, commentaires centralisés et correspondance DJ/DJA.

### Réalisé
- Module `publication` : hiérarchie `Publication`/`CompteRenduAudience`/`AutrePublication`
  (`JOINED`, même principe que `Intervenant`/`Avocat`, M2), `CommentairePublication`,
  `CorrespondanceAvocat` (tables ajoutées en M11, `V4__publication_commentaires_correspondances.sql`
  — `autre_publication.chemin_stockage` manquant au schéma initial, Q-56 ; aucune table pour les
  commentaires/correspondances, Q-20). Dépend de `dossier`, `audience` (rattachement du compte
  rendu) et `ged` (`MinioStorageService`, réutilisé sans créer de `document` GED — Q-56) — **pas**
  de `intervenant` (même raisonnement que Q-54 pour `frais`).
- `PublicationService` : `deposerCompteRendu` (avocat, audience rattachée au dossier — 404 sinon),
  `deposerAutre` (multipart, fichier obligatoire), `valider` (point d'entrée unique pour les deux
  sous-types — Q-55, motif obligatoire au rejet, notifie l'avocat), `obtenir` (seule une publication
  `VALIDE` est consultable, restriction d'accès individuelle optionnelle → 403/ERR-009 — Q-57),
  `commenter` (juriste → notifie DJ et DJA, jamais l'avocat directement, RG-INT-07),
  `transmettreCorrespondance` (DJ/DJA → avocat, unique par publication, 409 si déjà transmise —
  Q-20).
- 6 endpoints (5 du contrat formel + 1 💡 « à proposer » déjà prévu par le contrat pour la
  correspondance) — 100 % des endpoints OpenAPI du contrat sont désormais implémentés.

### Décisions prises en implémentation (documentées dans `DECISIONS_AND_OPEN_QUESTIONS.md`)
Q-20 (correspondance unique, implémentée), Q-55 (point de validation unique généralisé aux deux
sous-types, chemin sans le segment `cr-audience`), Q-56 (`autre_publication.chemin_stockage`
ajoutée, réutilisation du `MinioStorageService` sans document GED), Q-57 (`restrictionAcces`
renseignée par l'avocat au dépôt, aucun endpoint de modification a posteriori).

### Validation
16 tests automatisés dédiés (`PublicationServiceTest`) + 162 tests au total après M11, tous verts
sauf le test de fumée `AudiencesApplicationTests.contextLoads` (R-07, pré-existant, sans lien avec
ce module). Validation manuelle de bout en bout par `curl` (JWT Keycloak réels) réalisée sur le
dossier de test déjà constitué en M10 :
- dépôt d'un compte rendu sur une audience d'un autre dossier (404), dépôt nominal (201) ;
- dépôt d'une autre publication (PIECE) avec fichier, sans fichier (400) ;
- validation par un rôle non autorisé (403), déjà traitée (409), rejet sans motif (400) puis avec
  motif → notifie l'avocat, validation d'une « autre » publication via le même endpoint (Q-55) ;
- consultation d'une publication non validée (404), rejetée (404), restriction d'accès respectée/
  refusée (403/ERR-009) ;
- commentaire d'un juriste → notifie DJ et DJA (jamais l'avocat) ;
- correspondance unique DJ/DJA → avocat, tentative de doublon (409).

### Commit
Module livré en un seul commit (docs de contexte incluses, comme pour M5 à M10).

### Restant
Épic 8 (Décisions définitives, Reporting) différé aux jalons suivants (M12/M13). R-07
(intermittence de démarrage, sans impact sur ce jalon) à surveiller en M14.

## M12 — Module Décisions définitives ✅ **Terminé**

**Objectif.** Épic 8 partiel (US 8.1, 8.2, 8.3). Adjudications, condamnations pécuniaires, base
jurisprudentielle indexée et consultable.

### Réalisé
- Module `decision` : hiérarchie `DecisionDefinitive`/`Adjudication`/`CondamnationPecuniaire`
  (`JOINED`, même principe que `Publication`/`Intervenant`), entité `Jurisprudence` indépendante
  (pas un troisième sous-type). Dépend de `dossier` et `ged` (`MinioStorageService`), **pas** de
  `intervenant` (même raisonnement que Q-54/Q-56).
- `DecisionDefinitiveService` : `creerAdjudication` (étape `CLOTURE` requise, RG-DEF-01),
  `creerCondamnation` (**aucune précondition d'étape**, asymétrie assumée avec l'adjudication —
  Q-59), `modifierStatutCondamnation`.
- `JurisprudenceService` : `creer` (indexation obligatoire, PDF ≤ 10 Mo littéral — Q-61, `dossierId`
  optionnel avec stockage dédié sans dossier — Q-58), `rechercher` (pagination + filtres
  mots-clés/nature/juridiction, requête native Postgres pour `ANY(mots_cles)`).
- 5 endpoints (4 « à proposer » + 1 🆕 `GET /jurisprudences`, UC-DEF-03) — **100 % des endpoints
  « à proposer » du contrat sont désormais implémentés** (10/10).

### Décisions prises en implémentation (documentées dans `DECISIONS_AND_OPEN_QUESTIONS.md`)
Q-58 (jurisprudence sans colonne de stockage + dossier optionnel), Q-59 (asymétrie de précondition
d'étape adjudication/condamnation, ouverte pour confirmation DJ), Q-60 (aucune transition
automatique vers `ARCHIVE`, ouverte pour confirmation DJ), Q-61 (contraintes PDF/10 Mo littérales),
**Q-62 (bug corrigé)** : `decision_definitive.type VARCHAR(20)` trop étroite pour sa propre
contrainte `CHECK` (`'CONDAMNATION_PECUNIAIRE'` fait 23 caractères) — toute condamnation échouait
en 500 ; corrigé par `V6__decision_definitive_type_largeur.sql` (`VARCHAR(30)`).

### Validation
13 tests automatisés dédiés (`DecisionDefinitiveServiceTest`, `JurisprudenceServiceTest`) + 175
tests au total après M12, tous verts (y compris `contextLoads`, R-07 non manifesté sur cette
exécution). Validation manuelle de bout en bout réalisée (curl + JWT Keycloak réels, sur un
environnement Docker entièrement reconstruit après un redémarrage des conteneurs en cours de
session — fixtures recréées) :
- adjudication nominale sur étape `CLOTURE` (201), rejet sur étape non clôturée (400) ;
- condamnation sans `etapeId` (201, confirme Q-59), mise à jour du statut vers `PAYE` (200), 404 sur
  un id d'adjudication et sur un id inexistant ;
- jurisprudence avec `dossierId` + PDF (stockage `dossiers/{id}/...`), sans `dossierId` (stockage
  `jurisprudence/...`, confirme Q-58), rejet fichier non-PDF (400/ERR-008), rejet fichier > 10 Mo
  (400/ERR-008, intercepté par la limite globale `spring.servlet.multipart.max-file-size` avant même
  d'atteindre le contrôle applicatif — Q-61) ;
- recherche par mot-clé, par juridiction, sans filtre — résultats corrects dans les trois cas.

**Le bug Q-62 a été découvert par cette validation manuelle elle-même** (premier test réel du
chemin condamnation) — confirmation que la validation manuelle en conditions réelles reste
nécessaire au-delà des tests automatisés (qui mockent le repository et n'exercent jamais le SQL
réel).

### Commit
Module livré en un seul commit (docs de contexte incluses, comme pour M5 à M11).

### Restant
Épic 8 volet reporting (US 8.4-8.6) différé à M13.

## M13 — Module Reporting ✅ **Terminé**

**Objectif.** US 8.4, 8.5, 8.6. Rapports périodiques avec exports PDF/Excel/CSV < 10 s, tableau de
bord exécutif temps réel < 2 s, vue consolidée par client.

**Constat de périmètre** : US 8.6 (vue consolidée par client) était déjà implémentée depuis M4
(`GET /clients/{clientId}/dossiers`, `DossierService.vueConsolideeParClient`) — erreur de suivi
dans `FUNCTIONAL_MAP.md`/`API_IMPLEMENTATION_STATUS.md`, corrigée à l'occasion de ce jalon. Le
périmètre réel de M13 se limite donc à US 8.4 (`GET /rapports`) et US 8.5 (`GET /tableau-de-bord`).

### Réalisé
- Module `reporting` : `TypeRapport` (JOURNALIER…ANNUEL, étiquette de période sans effet de
  calcul — Q-63), `FormatRapport` (JSON/PDF/EXCEL/CSV, distinct de `FormatExport` de l'Épic 2 qui
  n'a pas CSV). Dépend de `dossier`, `audience`, `frais`, `alerte`, `delibere`, `decision`
  (lecture seule, aucune écriture croisée).
- `ReportingService.tableauDeBord()` : 6 indicateurs temps réel (RG-REP-03) — `nbDossiersActifs`
  (réutilise la définition de Q-53), `nbAudiencesPlanifiees`, `nbDemandesFraisEnAttente` (ni
  payées, ni rejetées), `nbProrogationsAlerte` (alertes `PROROGATION` encore `DECLENCHEE`),
  `tauxSuccesRecouvrement` et `provisionsReprises` (définitions en Q-64, aucune des deux n'étant
  précisée par le contrat).
- `ReportingService.genererRapport(...)` : mêmes indicateurs que le tableau de bord mais restreints
  à `[dateDebut, dateFin]` (FR-REP-01 ne nomme aucun champ, contrairement à FR-REP-02 — Q-63) ; 204
  si les 4 compteurs sont nuls (RG-REP-02).
- `RapportExportService` : PDF (PDFBox) / Excel (Apache POI) / CSV, présentation libellé/valeur —
  même stack que `CalendrierExportService` (Épic 2).
- 6 nouvelles méthodes de repository ajoutées à travers `dossier`, `audience`, `frais`, `alerte`,
  `delibere`, `decision` (comptages/sommes scopés ou non à une période) — aucune modification de
  comportement des modules existants.
- 2 endpoints (tous deux du contrat formel) — **100 % de la surface OpenAPI et « à proposer » du
  contrat sont désormais implémentés**.

### Décisions prises en implémentation (documentées dans `DECISIONS_AND_OPEN_QUESTIONS.md`)
Q-63 (contenu de `GET /rapports`, dérivé du tableau de bord et restreint à la période), Q-64
(définitions de `tauxSuccesRecouvrement` et `provisionsReprises`).

### Validation
4 tests automatisés dédiés (`ReportingServiceTest`) + 179 tests au total après M13, tous verts y
compris `contextLoads`. Validation manuelle de bout en bout réalisée (curl + JWT Keycloak réels,
sur les données de test constituées en M12) : tableau de bord cohérent avec les données en base,
rapport sur une période avec activité (contenu non vide) et sur une période sans activité (204),
les 4 formats d'export vérifiés (JSON, PDF, Excel, CSV).

### Commit
Module livré en un seul commit (docs de contexte incluses, comme pour M5 à M12).

### Restant
Plus aucun module fonctionnel. Seul M14 (durcissement, tests d'intégration bout en bout,
vérification finale de conformité au contrat, campagne de performance, revue de sécurité) reste à
faire.

## M14 — Durcissement et vérification finale ✅ **Terminé**

**Objectif.** Tests d'intégration bout en bout des 9 parcours de `FUNCTIONAL_MAP.md` §9 ; couverture
cible > 80 % (REF-02 §10.2) ; vérification de conformité endpoint par endpoint au contrat d'API ;
campagne de performance (< 2 s / 50 utilisateurs simultanés) ; revue de sécurité ; documentation
technique et mise à jour finale des fichiers de contexte.

### 1. Tests d'intégration bout en bout — ✅ 9 parcours sur 9
`ParcoursEndToEndIntegrationTest` : contexte Spring **complet** (sécurité incluse) + PostgreSQL
**réel** migré par Flyway (Testcontainers), appels HTTP réels (`MockMvc`). 10 tests verts couvrant
P1 à P9 plus un contrôle transverse (401 au format du contrat sans jeton). Premier test du projet à
traverser toute la pile — les 179 tests antérieurs étaient unitaires (services mockés) ou limités à
la couche JPA. Deux substitutions assumées et documentées (Q-65) : JWT simulé au lieu d'un Keycloak
conteneurisé (démarrage à froid > 5 min, incompatible avec une suite de tests) et `MinioClient`
mocké (stockage objet vérifié en conditions réelles à chaque jalon M8-M12).

### 2. Couverture — ✅ 84,9 %, cible > 80 % atteinte
JaCoCo ajouté au `pom.xml` (DTO, entités et code généré exclus : sans logique propre, ils fausseraient
la mesure).

| Étape | Instructions | Branches |
|---|---|---|
| Avant M14 (179 tests unitaires) | 79,2 % (6831/8622) | 69,6 % (295/424) |
| **Après M14 (213 tests)** | **84,9 % (7319/8622)** | **75,9 % (322/424)** |

Le gain vient d'abord des tests bout en bout (qui traversent contrôleurs, sécurité et mappers,
jusque-là non couverts), puis de quatre composants sans **aucun** test, choisis pour leur valeur de
durcissement et non pour le chiffre : `WebSocketAuthInterceptor` (4 % — **seul chemin
d'authentification non couvert**, les tests bout en bout passant par HTTP), `RapportExportService`
(1 % — génération PDF/Excel/CSV, vérifiée jusque-là uniquement en manuel), `ClientService` (23 %) et
`DossierSpecifications` (0 %). La couverture de branches (75,9 %) reste sous les 80 % : l'essentiel
du reste tient aux nombreuses branches de validation conditionnelle de `DossierService` (création de
dossier par catégorie), déjà couvertes fonctionnellement par 31 tests mais pas exhaustivement
combinatoirement.

### 3. Conformité endpoint par endpoint — ✅ 56/56 vérifiés, 2 écarts corrigés
Audit systématique des 56 `@PreAuthorize` du code contre le tableau d'inventaire. **54 endpoints
strictement conformes** (chemin, méthode, rôle). **2 écarts** relevés, de même nature — la
documentation annonçait `ROLE_SAISIE` là où le code exige `ROLE_JURISTE`, plus strict (`ROLE_JURISTE`
étant un rôle composite contenant `ROLE_SAISIE`, l'écart est restrictif, jamais permissif) :
`POST /dossiers/{id}/etapes` et `DELETE /ged/documents/{id}`. Tranchés en faveur du code, appuyé par
le texte fonctionnel ; documentation alignée (Q-66).

### 4. Campagne de performance — ✅ toutes les cibles tenues
50 requêtes **simultanées** par endpoint, jetons Keycloak réels, application et PostgreSQL locaux :

| Endpoint | Cible | Moyenne | p95 | Max | Codes |
|---|---|---|---|---|---|
| `GET /tableau-de-bord` | < 2 s (RG-REP-03) | 0,844 s | 1,512 s | 1,730 s | 50 × 200 |
| `GET /dossiers` | < 2 s (RG-DOS-06) | 0,722 s | 0,953 s | 1,028 s | 50 × 200 |
| `GET /rapports` | < 10 s (RG-REP-01) | 0,600 s | 0,985 s | 1,113 s | 50 × 200 |

**Limites assumées** : mesure par un script `curl` parallèle, pas une campagne outillée (ni JMeter,
ni Gatling, ni k6 disponibles dans cet environnement) ; volumétrie de test réduite (quelques dizaines
de lignes par table, là où REF-02 vise un volume représentatif) ; poste de développement partagé
avec les conteneurs. Les marges sont confortables (p95 à 76 % de la cible dans le pire cas), mais
une campagne outillée sur volumétrie réelle reste à faire avant mise en production.

### 5. Revue de sécurité — ✅ 1 constat, aucun défaut d'autorisation
- **Autorisation** : les 56 endpoints métier portent tous un `@PreAuthorize` explicite ; aucune
  route métier n'est laissée à `permitAll` ; `anyRequest().authenticated()` couvre le reste.
- **WebSocket** : `/ws/**` est public au niveau HTTP (contrainte des transports de repli SockJS,
  qui ne peuvent pas porter d'en-tête `Authorization`) mais **authentifié à la trame STOMP
  `CONNECT`**, et toute trame ultérieure hors session authentifiée est rejetée — comportement
  désormais verrouillé par 7 tests dédiés.
- **Secrets** : `.env` ignoré par Git, aucun secret en dur dans le code. Les identifiants présents
  dans `README.md`, `docker-compose.yml`, `env.template` et `keycloak/audiences-realm.json` sont des
  valeurs de développement local — à ne jamais réutiliser en production (R-01 pour le volet cloud).
- **Injection** : aucune concaténation SQL ; requêtes JPQL/natives toutes paramétrées (`:param`).
- **Constat ouvert (R-08)** : `/v3/api-docs/**` et `/swagger-ui/**` sont accessibles **sans
  authentification**. Acceptable en développement, à fermer avant production (profil Spring ou
  `springdoc.api-docs.enabled=false`).

### 6. Constat fonctionnel révélé par les tests bout en bout (Q-67)
`GET /dossiers/{id}` renvoie **toujours** `documents: []` : le champ, ajouté en M4 avec la mention
« à remplir quand la GED existera, M8 », n'a jamais été raccordé. Invisible des tests unitaires (le
mapper est mocké) et de la validation manuelle (le champ n'avait jamais été inspecté avec une pièce
réellement chargée). **Décision : ne pas le raccorder** — cela imposerait une dépendance
`dossier → ged` inversant la hiérarchie de couches tenue depuis M6, pour une information déjà servie
par l'endpoint dédié `GET /dossiers/{id}/documents` (#43, créé en M8 pour ce besoin). Champ conservé
(aucune rupture de contrat) mais documenté comme vide par construction.

### Décisions prises (documentées dans `DECISIONS_AND_OPEN_QUESTIONS.md`)
Q-65 (substitutions des tests bout en bout), Q-66 (2 écarts de conformité corrigés), Q-67 (champ
`documents` vide par construction), R-08 (documentation d'API exposée sans authentification).

### Commit
Jalon livré en un seul commit (docs de contexte incluses, comme pour M5 à M13).

### Restant *(état à la clôture de M14 — soldé depuis, voir §M15)*
Avant mise en production : fermer R-08 (documentation d'API), corriger R-06 (provisionnement en
transaction lecture seule), mener une campagne de performance outillée sur volumétrie réelle, et
trancher les questions restées ouvertes (Q-59, Q-60, Q-67 côté frontend, Q-21, Q-24 pour les volets
publications/suppressions/constitutions).
> **Soldé en M15** : R-06 corrigé, Q-24 close, Q-59/Q-60/Q-67 arbitrées. Restent R-08 et la
> campagne de performance, tous deux liés à la mise en production.

---

## M15 — Habilitation frontend ✅ **Terminé**

**Origine.** Jalon **hors feuille de route initiale**, ouvert le 2026-09-07 en réponse à une question
du porteur du projet : *« si on branche le frontend à l'état actuel, l'application fonctionnerait-elle
normalement ? »* La réponse, établie par revue du code et non par impression, était **non** — pour
deux raisons distinctes :

1. **Un défaut bloquant (R-06)** : la **toute première requête** de tout nouvel utilisateur
   retombait en 500 dès lors qu'elle touchait un endpoint en lecture seule — c'est-à-dire dans
   presque tous les cas au chargement de l'application.
2. **Des trous de lecture** : plusieurs écrans décrits dans `FUNCTIONAL_MAP.md` n'avaient
   **aucune source de données** (files de travail « … en attente »), les onglets d'une fiche dossier
   n'étaient pas servis, les référentiels fermés que le backend impose en écriture n'étaient pas
   exposés en lecture, et **aucun endpoint ne permettait de créer un avocat** — alors qu'un avocat
   est obligatoire à la création d'un dossier (`avocatsAffectes` est `@NotEmpty`).

**Objectif.** Rendre le backend consommable par un frontend, sans ajouter la moindre règle métier :
1 correctif + **11** endpoints de lecture/administration. Périmètre fermé, arrêté avec le porteur du
projet avant démarrage, **élargi d'un endpoint en cours de jalon** (#66, Q-69 — voir §6).

### 1. Correctif R-06 — provisionnement en transaction lecture seule ✅
Le provisionnement d'un `Utilisateur` à partir du jeton Keycloak était exécuté par
`CurrentUserService` **dans la transaction de l'appelant**. Tout endpoint annoté
`@Transactional(readOnly = true)` propageait donc son mode à cet `INSERT`, que PostgreSQL rejette
(*cannot execute INSERT in a read-only transaction*).

Correction : extraction d'un bean dédié `UtilisateurProvisioningService` portant
`@Transactional(propagation = REQUIRES_NEW)`. **Une classe distincte est nécessaire** : `@Transactional`
s'appuie sur un proxy Spring, un appel interne à `CurrentUserService` n'aurait pas changé de
transaction. La méthode reteste l'existence de l'utilisateur dans la nouvelle transaction, pour que
deux requêtes concurrentes du même nouvel arrivant ne créent pas deux lignes.

Preuve : `ProvisionnementLectureSeuleIntegrationTest` (PostgreSQL réel, appelant annoté
`readOnly = true`) — **rouge avant le correctif, vert après**. Le scénario est en outre rejoué par
HTTP dans `HabilitationFrontendIntegrationTest` sur `GET /alertes/mes-notifications`, l'endpoint que
le frontend appelle effectivement au chargement.

### 2. Endpoints ajoutés — 11 endpoints 🆕

| Endpoint | Rôle | Écran servi |
|---|---|---|
| `GET /referentiels/natures-dossier?categorie=` | `CONSULTATION` | Liste « nature » du formulaire de création (RG-DOS-02) |
| `GET /referentiels/types-client-sensible` | `CONSULTATION` | Volet « dossier sensible » (RG-DOS-09) |
| `POST /intervenants` | `DJ`, `DJA` | Administration du répertoire |
| `GET /intervenants?type=` | `CONSULTATION` | Sélecteurs d'affectation et de constitution |
| `GET /dossiers/{id}/audiences` | `CONSULTATION` | Onglet « Audiences » de la fiche dossier |
| `GET /dossiers/{id}/deliberes` | `CONSULTATION` | Onglet « Délibérés » |
| `GET /dossiers/{id}/alarmes` | `CONSULTATION` | Onglet « Alarmes » |
| `GET /publications?dossierId=&statut=` | `ASSISTANTE`, `JURISTE`, `DJ`, `DJA` | File « publications à valider » |
| `GET /ged/demandes-suppression?statut=` | `DJ`, `DJA` | File « demandes de suppression en attente » |
| `GET /constitutions/prestataires?statut=` | `SH`, `JURISTE` | File « constitutions à signer » |
| `GET /utilisateurs?profil=` | `CONSULTATION` | Sélecteur « juriste affecté » (§6, Q-69) |

Ces endpoints ferment les trois derniers volets de **Q-24** (files de travail sans source de données),
qui passe donc à **close**.

**Aucune règle métier nouvelle.** Deux points ont toutefois dû être tranchés, et le sont
explicitement (Q-68) :
- **Création d'un intervenant** : les invariants existants sont portés par le service — un `AVOCAT`
  exige un compte Keycloak unique (400 s'il manque, 409 s'il est déjà pris, RG-INT-01) et refuse le
  champ `notification` ; un `AUTRE_PRESTATAIRE` refuse tout compte applicatif (RG-INT-02). Même
  raisonnement que `POST /clients` (Q-23, M4) : aucune user story ne décrit cet endpoint, mais sans
  lui le référentiel ne peut être alimenté que par SQL.
- **`GET /publications`** : contrairement à `GET /publications/{id}`, la liste **n'applique pas** la
  restriction d'accès individuelle (Q-57) — elle sert des profils internes chargés d'instruire les
  publications et n'expose que des métadonnées ; le contenu détaillé reste soumis au contrôle
  unitaire.

### 3. Tests — ✅ 255 tests verts, couverture 85,9 %

**42 tests nets ajoutés** (213 → 255) ; couverture JaCoCo **84,9 % → 85,9 %** des instructions
(7852/9139) et **75,9 % → 77,7 %** des branches.

| Test | Portée |
|---|---|
| `HabilitationFrontendIntegrationTest` (13) | Routage, filtres et **habilitations** des 11 endpoints, bout en bout — dont la **chaîne complète** « lister les juristes → créer un dossier avec l'identifiant obtenu » (Q-69) |
| `IntervenantServiceTest` (8) | Invariants RG-INT-01/02, doublon de compte, filtre par type |
| `UtilisateurServiceTest` (6) | Filtres (profil, comptes inactifs), tri, et non-exposition de `keycloakId` |
| `UtilisateurProvisioningServiceTest` (4) | Dérivation du profil depuis le jeton — 3 tests déplacés depuis `CurrentUserServiceTest` avec le code qu'ils couvrent, + le cas de course (utilisateur apparu entre-temps) |
| `ProvisionnementLectureSeuleIntegrationTest` (2) | R-06 contre PostgreSQL réel |
| 11 ajouts dans 6 classes existantes | `listerParDossier` (audiences, délibérés, alarmes) + 404 si dossier absent ; aiguillage des 4 combinaisons de filtres de `PublicationService.lister` ; listes filtrées GED et constitutions |

Le choix d'un **test d'intégration** pour les endpoints eux-mêmes est délibéré : ces endpoints n'ont
presque pas de logique, leur risque réel est l'habilitation — qu'un test de service mocké ne peut
pas vérifier. Chaque file de travail est donc testée servie à son profil **et refusée aux autres**.

**Régression corrigée au passage** : le refactor R-06 laissait `CurrentUserServiceTest` cassé
(3 tests en échec) — `@InjectMocks` ne pouvait plus alimenter le constructeur, et trois tests
portaient sur la dérivation du profil, désormais dans l'autre classe. Ils ont été déplacés dans
`UtilisateurProvisioningServiceTest` avec le code qu'ils couvrent ; `CurrentUserServiceTest` ne
teste plus que la délégation. Le défaut n'a été révélé que par l'exécution de la **suite complète**,
les tests ciblés du jalon passant tous.

### 4. Validation manuelle en conditions réelles — ✅
Docker (PostgreSQL 17 + Keycloak 24 + MinIO), jetons Keycloak **réels** pour les 6 profils, base
repartie **vierge** (volume recréé — circonstance qui a rendu la preuve de R-06 particulièrement
nette).

- **R-06** : première requête de `sh.test`, jamais provisionné → `GET /alertes/mes-notifications`
  (service `@Transactional(readOnly = true)`) répond **200**, et la table `utilisateur` contient
  ensuite exactement une ligne, créée par cette requête. Avant M15, ce même appel retournait 500.
- **Référentiels** : 10 natures pour `?categorie=RECOUVREMENT` (toutes de cette catégorie),
  3 types de client sensible ; 403 sans `ROLE_CONSULTATION`.
- **Intervenants** : avocat créé (201), doublon de compte Keycloak (**409**), avocat sans compte
  (**400**), avocat avec `notification` (**400**), autre prestataire créé (201), autre prestataire
  avec compte (**400**), création par un juriste (**403**) ; listes filtrées par type correctes.
- **Onglets d'une fiche dossier** : les trois répondent `[]` sur un dossier neuf, **404** sur un
  dossier inexistant, puis restituent l'audience, l'alarme et le délibéré réellement créés ; 403
  pour un avocat (sans `ROLE_CONSULTATION`).
- **Files de travail** : les trois vides d'abord, puis alimentées par une constitution, une
  publication et une demande de suppression réelles ; chaque filtre discrimine (`?statut=DEPOSE` →
  1, `?statut=VALIDE` → 0, puis **l'inverse après validation de la publication** ; `?dossierId=1` →
  1, `?dossierId=99` → 0). Chacune est refusée (**403**) aux profils non concernés.
- **Répertoire des utilisateurs (#66)** : liste triée par nom, filtre `?profil=` correct et
  insensible à la casse, `keycloakId` absent de la réponse, **403** pour un avocat. Surtout, la
  **chaîne rompue a été rejouée en entier** : lister les juristes → retenir l'identifiant renvoyé →
  `POST /dossiers` avec ce `juristesAffectes` → **201**, le dossier créé portant bien l'affectation.

### 5. Arbitrages rendus à la clôture du jalon — Q-59, Q-60, Q-67 closes

Le porteur du projet a **retenu les recommandations** de Q-59, Q-60 et Q-67, qui restaient ouvertes
pour confirmation. Les trois sont **closes sans modification de code** — le comportement livré en
M12/M14 était déjà celui recommandé, revérifié dans le code à cette occasion :

| Question | Décision retenue | Conséquence |
|---|---|---|
| **Q-59** | L'asymétrie est intentionnelle : une condamnation pécuniaire n'exige **aucune** précondition d'étape, `etapeId` reste optionnel — à la différence de l'adjudication (`CLOTURE` requise) | Aucune. Une harmonisation ultérieure serait un **durcissement** de l'API, à versionner |
| **Q-60** | Le passage d'une étape à `ARCHIVE` reste **manuel** (`PATCH …/etapes/{id}/statut`) ; P5 décrit un enchaînement métier, pas un automatisme | Aucune. Une automatisation ultérieure n'introduirait aucune rupture de contrat |
| **Q-67** | `DossierResponse.documents` reste présent et **vide par construction** ; les pièces se lisent via `GET /dossiers/{id}/documents` (#43). Le champ n'est **pas** retiré : le supprimer serait une rupture pour un gain nul | **Point d'attention frontend** : ne pas lire `dossier.documents` |

### 6. Élargissement de périmètre en cours de jalon — `GET /utilisateurs` (Q-69)

Le contrôle « le frontend peut-il consommer l'API ? » a été **rejoué après la livraison** du
périmètre initial, et a révélé un manque de la même famille que ceux de Q-68 : `POST /dossiers`
exige `juristesAffectes` (`@NotEmpty`, des `utilisateur.id`), et **aucun endpoint n'exposait la
table `utilisateur`**. Le point d'entrée de toute l'application était donc inconstructible depuis
une interface. M15 avait traité les avocats (`GET /intervenants`) et omis le personnel interne.

Ni les tests ni les validations manuelles ne pouvaient le révéler : les uns comme les autres lisent
l'identifiant directement en base. Seule la question « par quel appel le frontend obtient-il cette
valeur ? » le fait apparaître.

`GET /utilisateurs?profil=&inclureInactifs=` (#66) comble le manque, en lecture seule — les comptes
naissent du provisionnement au premier appel authentifié, Keycloak restant la source de vérité des
identités (REF-05 §1.3). Trois choix tranchés, détaillés en Q-69 : `keycloakId` non exposé, comptes
actifs seuls par défaut, et `ROLE_CONSULTATION` — que les six profils internes portent mais **pas**
`AVOCAT`, un intervenant externe ne devant pas énumérer le personnel de la Direction Juridique.

**Non retenu** : enrichir `DossierResponse.juristesAffectes` (aujourd'hui des identifiants nus) en
objets `{id, nom, prenom}`. Ce serait une **rupture du contrat de réponse déjà publié**, pour un
besoin que le frontend couvre avec un appel à `GET /utilisateurs` mis en cache. Reste à arbitrer si
l'équipe frontend juge le double-appel gênant — même raisonnement que Q-67.

### Décisions prises (documentées dans `DECISIONS_AND_OPEN_QUESTIONS.md`)
Q-68 (périmètre et invariants du jalon), **Q-69** (répertoire des utilisateurs internes),
**Q-24 close**, **R-06 ✅ corrigé**, et les clôtures de **Q-59, Q-60, Q-67** ci-dessus.

### Commit
Jalon livré en un seul commit (docs de contexte incluses, comme pour M5 à M14).

### Restant
**Le projet reste en développement — les points ci-dessous ne se posent qu'à la mise en
production** : R-01 (rotation des secrets déjà exposés côté Neon/iDrive e2), R-08 (documentation
d'API `/v3/api-docs` et `/swagger-ui` accessible sans authentification), et une campagne de
performance outillée sur volumétrie réelle. Plus aucune question fonctionnelle n'est en attente
d'arbitrage.

---

## M16 — Corrections issues de l'intégration frontend ✅ **Terminé**

**Origine.** Jalon **hors feuille de route initiale**, ouvert le 2026-09-09 sur autorisation
explicite du porteur du projet. Il recueille les défauts que seule la consommation réelle de l'API
par un client révèle — ceux qu'aucun test backend ne pouvait voir, puisqu'ils portent sur ce que la
réponse **ne dit pas**.

### 1. `ERR-CONFLICT` ne nommait pas le champ fautif (Q-70) ✅

Remonté par le jalon F5 du frontend, en provoquant un doublon de référence client pour de vrai. Un
client recevant `ERR-CONFLICT` — code partagé par neuf conflits sans rapport, avec `details: null` —
ne pouvait pas savoir quel champ signaler à l'utilisateur, et devait le déduire de l'endpoint appelé.

**Implémenté** : `ErrorResponse` gagne un champ `champ`, distinct de `details` (qui porte du texte
pour un humain) et **absent du JSON** quand aucun champ n'est visé — l'ajout ne modifie donc aucune
réponse existante. `ApiException.conflictSurChamp` le renseigne sur cinq sites : référence client,
`compteKeycloak` d'un avocat, référence de dossier (`ERR-002`), `type` d'une étape déjà ouverte,
référence de facture (`ERR-006`). Les conflits d'**état** conservent `ApiException.conflict` et ne
nomment rien — désigner un champ au hasard serait pire que se taire.

**Testé** : 258 tests au vert (`BUILD SUCCESS`), dont 2 tests neufs sur le gestionnaire d'exceptions
(présence **et** absence de la clé), 3 assertions renforcées sur `ClientService`, `IntervenantService`
et `DossierService`, une assertion sur le corps JSON de `ERR-006`, et un test d'intégration dédié qui
contrôle `$.champ` sur un doublon puis son absence sur une erreur de validation.

**Vérifié en réel** contre l'instance locale : `champ: "reference"` sur un doublon client,
`champ: "compteKeycloak"` sur un compte d'avocat déjà pris, clé absente sur `ERR-FORBIDDEN` et sur
`ERR-VALIDATION`.

**Validation manuelle** : créer deux fois un client de même référence depuis le frontend — le message
doit apparaître **sous le champ Référence**, jamais dans un bandeau en haut du formulaire.

### 2. Trois natures de dossier étaient inutilisables (Q-71) ✅

`dossier.nature` était `VARCHAR(50)` quand le référentiel qui l'alimente est `VARCHAR(255)`, et le
service résout la nature **par son libellé**. Trois des dix-sept natures de l'Annexe A dépassent
50 caractères — dont « Hypothèque judiciaire », qui n'est pas un cas marginal en recouvrement — et
renvoyaient `400 nature: size must be between 0 and 50`.

**Implémenté** : migration `V7__dossier_nature_largeur.sql` alignant la colonne sur sa source, plus
les annotations de l'entité et du DTO. Aucun test ne l'avait vu : tous créaient des dossiers avec
« Assignation en paiement », 25 caractères.

**Testé** : un test d'intégration crée un dossier avec le libellé de 71 caractères et vérifie la
catégorie déduite. **Vérifié en réel** : `201`.

### 3. La liste des dossiers n'était pas cherchable (Q-72) ✅

`GET /dossiers` n'acceptait que `nature`, `categorie` et `juridiction` : impossible de retrouver un
dossier par sa référence, par son client, ou de n'afficher que son propre portefeuille. Le frontend
ne pouvait pas compenser — filtrer une liste paginée côté navigateur n'aurait cherché que dans la
page affichée.

**Implémenté** : `reference` (recherche partielle, insensible à la casse), `clientId`, et
`mesDossiers` — ce dernier résolu sur **l'utilisateur authentifié**, ce qui interdit de consulter le
portefeuille d'un collègue. L'appartenance passe par une sous-requête `EXISTS`, pas une jointure,
qui aurait dupliqué les lignes et faussé le total de la pagination.

**Testé** : trois assertions unitaires sur les prédicats, un test d'intégration sur PostgreSQL réel
vérifiant qu'un second juriste ne voit rien. **Vérifié en réel** sur les trois filtres.

### 4. `typeClientSensible` acceptait n'importe quelle chaîne (Q-73) ✅

Q-06 confie la validation de ce champ au service, comme pour `nature`. Elle avait été faite pour
l'un, oubliée pour l'autre : une faute de frappe était enregistrée en silence et ressortait dans les
rapports.

**Implémenté** : contrôle sur le **code** du référentiel — unique, stable si le libellé est
reformulé, et tenant dans la colonne — appliqué dès que le champ est renseigné, ainsi qu'à
l'ajustement par le DJ lors de la validation de sensibilité.

**Testé** : rejet en unitaire, 400 en intégration. **Vérifié en réel** :
`Type de client sensible inconnu du référentiel : VIPP`.

**Vérifications du jalon** : **265 tests au vert**, `BUILD SUCCESS`.

### 5. Une dérogation demandée ne pouvait pas être arbitrée (Q-74) ✅

L'arbitrage exigeait l'identifiant d'une demande que rien n'exposait. **Implémenté** :
`GET /dossiers/{id}/seuil-derogation[?statut=]`. Au passage, l'arbitrage refuse désormais la
proposition initiale de sensibilité, qu'il acceptait à tort tant que la sensibilité n'était pas
validée — défaut latent que la nouvelle lecture rendait atteignable.

**Testé** : deux tests unitaires, un test d'intégration de bout en bout.

### 6. L'affectation pouvait vider un dossier (Q-75) ✅

`PUT /affectation` remplace les listes sans minimum, et sans `@Valid` : un dossier pouvait perdre
tous ses juristes. **Implémenté** : `@NotEmpty` sur les deux listes, `@Valid` sur le contrôleur —
même règle qu'à la création. **Testé** : un test d'intégration (liste vide, liste absente, nominal).

**Validation manuelle** : depuis le frontend, créer un dossier en choisissant « Hypothèque
judiciaire (inscription provisoire, définitive, réalisation) » — il doit être accepté ; puis
rechercher ce dossier par sa référence dans la liste, et cocher « Mes dossiers uniquement ».

### 7. Une audience ne pouvait être ni annulée ni reportée (Q-76) ✅

Remonté par le jalon F8 du frontend (QF-30). `ANNULEE` existait sans endpoint : une audience
renvoyée par le tribunal restait au calendrier et déclenchait ses rappels. **Implémenté** :
`PATCH /audiences/{id}/annulation`, motif obligatoire, migration `V8__audience_annulation.sql`
(motif et date d'annulation, contrainte de cohérence). L'audience annulée sort du calendrier et des
exports, ne compte plus comme doublon, et refuse un compte rendu ; les rappels l'ignoraient déjà.

### 8. Une alarme n'était close qu'en la reprogrammant (Q-77) ✅

Remonté par le jalon F8 du frontend (QF-29). **Implémenté** : `PATCH /alarmes/{id}/traiter`, qui
clôt une alarme active sans en créer de nouvelle.

**Testé (§7-8)** : 6 tests unitaires (`AudienceServiceTest`, `AlarmeServiceTest`) et 2 tests
d'intégration sur PostgreSQL réel (`HabilitationFrontendIntegrationTest` : motif exigé, rôle,
double annulation refusée, calendrier sans l'annulée mais avec la replanifiée, replanification à la
même date sans `ERR-005`, compte rendu refusé ; alarme close une fois, sans remplaçante). **277 tests
au vert, `BUILD SUCCESS`.** **Vérifié en réel** : V8 appliquée à la base locale (`flyway_schema_history`,
rang 8), colonnes et contrainte présentes.

### 9. Prorogation et rabattement placés après la décision (Q-78) ✅

Remonté par l'audit du jalon F9 du frontend (QF-33) : toutes les sources placent prorogation et
rabattement **pendant** « En délibéré », le backend les plaçait après la décision — pour proroger,
il fallait saisir un résultat. **Implémenté** : le délibéré naît à la mise en délibéré, sans
résultat ; prorogation (date future) et rabattement (date, motif conservés sur le délibéré) ne
portent que sur un délibéré en attente ; 🆕 `PUT /deliberes/{id}/resultat` le vide ; 🆕 `GET
/deliberes/{id}/prorogations` en donne l'historique ; `etat` dérivé dans la réponse. Migration
`V9__delibere_cycle.sql`. Indicateurs de succès et compteur du rapport limités aux délibérés
rendus ; expédition refusée avant la décision. L'enregistrement d'un délibéré déjà rendu, en un
seul geste, reste possible.

**Testé** : `DelibereServiceTest` réécrit (24 tests : mise en délibéré, second délibéré en attente
refusé, vidage, prorogation et rabattement refusés après la décision, date de prorogation future,
expédition avant la décision, historique) ; parcours d'intégration `p3` réécrit sur PostgreSQL
réel (mise en délibéré, 409 sur un second, prorogation et historique, rabattement, prorogation
refusée sur un rabattu, nouvelle mise en délibéré, vidage, rabattement refusé après décision,
états `VIDE`/`RABATTU` dans la liste). **287 tests au vert, `BUILD SUCCESS`.** **Vérifié en réel** :
V9 appliquée à la base locale (rang 9), `resultat` facultatif, colonnes de rabattement présentes.

### 10. GED : demande de suppression orpheline, file anonyme, rapport trop fermé (Q-79, Q-80) ✅

Remonté par l'audit du jalon F10 du frontend (QF-34, QF-35). Une suppression directe (DJ/DJA) laissait
en attente la demande du juriste sur le même document — indécidable (404) et relancée toutes les
72 h sans fin ; la file ne nommait ni le fichier ni le dossier ; le rapport journalier n'était servi
qu'au rôle juriste. **Implémenté** : la suppression directe ferme la demande (`APPROUVEE`, validée par
l'auteur de la suppression) ; `DemandeSuppressionResponse` porte `documentNom`, `typeDocument`,
`dossierId`, `referenceDossier` ; migration `V10__demandes_suppression_orphelines.sql` pour les
orphelines existantes ; rapport journalier ouvert au juriste, au DJ, à la DJA et à l'Assistante.
Q-81 (le DJ et la DJA sans `ROLE_JURISTE`), constaté au même audit, est traité au §11.

**Testé** : 2 tests unitaires (`GedServiceTest`) et 2 tests d'intégration sur PostgreSQL réel
(`HabilitationFrontendIntegrationTest` : demande 202 déjà nommée, suppression directe, plus de
demande en attente, historique `APPROUVEE` nommé ; rapport servi aux quatre profils, refusé au SH).
**291 tests au vert, `BUILD SUCCESS`.** **Vérifié en réel** : V10 appliquée (rang 10), une demande
orpheline fermée — laissée par un parcours e2e —, aucune restante.

### 11. Le DJ et la DJA n'avaient pas les droits d'un juriste (Q-81) ✅

Les sources donnent au DJ et à la DJA « tous les droits d'un juriste, plus les leurs » ; le realm
ne leur donnait que `ROLE_SAISIE`. **Implémenté, sur décision du porteur du projet** : `ROLE_JURISTE`
ajouté aux composites de `ROLE_DJ` et `ROLE_DJA`, dans `keycloak/audiences-realm.json` et dans le
Keycloak en service (`kcadm add-roles` — l'import au démarrage ignore un realm existant).
**Précaution** : `UtilisateurProvisioningService` déduit désormais le profil par priorité (DJ, DJA,
SH, ASSISTANTE, JURISTE, AVOCAT) et non plus par le premier rôle du jeton, dont l'ordre n'est pas
garanti — sans quoi un nouveau DJ aurait pu être enregistré comme juriste.

**Testé** : 2 tests unitaires (jeton juriste + DJ → DJ ; juriste + DJA → DJA). **293 tests au vert,
`BUILD SUCCESS`.** **Vérifié en réel** : composites de `ROLE_DJ` et `ROLE_DJA` = `ROLE_JURISTE`,
`ROLE_SAISIE` dans le Keycloak local. Un jeton émis avant le changement exige une reconnexion.

### 12. Frais : détail, décisions du circuit, relance CONF04 (Q-82, Q-83) ✅

Remonté par l'audit du jalon F11 du frontend, décidé par le porteur du projet le 2026-09-11. Aucun
détail d'une demande de frais, décisions du circuit invisibles (le DJ ne voyait pas l'accord de la
DJA, personne ne savait s'il avait déjà voté), réponse limitée à des identifiants, file sans ordre
stable ; et la relance CONF04 n'était pas implémentée. **Implémenté** : `GET /frais/demandes/{id}`
(l'avocat ne lit que les siennes) ; `DemandeFraisResponse` porte dossier et avocat nommés,
sensibilité, seuil applicable, validation conjointe requise et décisions ; file triée par date
décroissante ; `FraisRelanceScheduler` relance le DJ et/ou la DJA qui n'ont pas voté après
`DELAI_VALIDATION_CONJOINTE` jours ; migration `V11__frais_relance_validation_conjointe.sql`.

**Testé** : 3 tests unitaires (`FraisServiceTest`), 4 (`FraisRelanceSchedulerTest`), parcours
d'intégration `p6` étendu sur PostgreSQL réel. **300 tests au vert, `BUILD SUCCESS`.** **Vérifié en
réel** : V11 appliquée (rang 11), colonne `date_derniere_relance` présente.

### 13. Avocat reconnu par son identifiant de connexion ; unicité concurrente en 409 (Q-84, Q-85) ✅

Constaté en préparant les parcours e2e de F11, corrigé sur décision du porteur du projet avant la
clôture du jalon. Le backend comparait `avocat.compte_keycloak` au `sub` du jeton, alors que le DJ y
saisit l'identifiant de connexion : aucun avocat créé par l'interface n'était reconnu, et ses alertes
ne partaient pas. **Implémenté** : `utilisateur.identifiant` (`preferred_username`, migration
`V12__utilisateur_identifiant.sql`), renseigné au provisionnement ou à la requête suivante ;
`CompteAvocatService` (identifiant de connexion d'abord, `sub` en second) dans `FraisService`,
`PublicationService` et `ConstitutionService`. Et deux créations concurrentes (même avocat, même
facture…) rendent 409 et non plus 500 : `GlobalExceptionHandler` traite la violation d'unicité.

**Testé** : `CompteAvocatServiceTest` (5), `CurrentUserServiceTest` (+2), `GlobalExceptionHandlerTest`
(+2), `HabilitationFrontendIntegrationTest` (+1, PostgreSQL réel). **310 tests au vert,
`BUILD SUCCESS`.**

### 14. Publications et constitutions lisibles par qui les instruit ; avocat constitué notifié (Q-86, Q-87) ✅

Remonté par l'audit du jalon F12 du frontend, décidé par le porteur du projet le 2026-09-11.
**Implémenté** : `GET /publications/{id}` lisible par l'Assistante avant validation, avec l'URL
pré-signée du fichier, les commentaires nommés et la correspondance ; restriction d'accès sans effet
sur les profils instructeurs (Assistante, DJ, DJA) ; publications et demandes de constitution
nommées ; file des publications triée ; notification des juristes du dossier (ou du seul destinataire
désigné) à la validation. **Q-87** : `Hibernate.unproxy` sur le prestataire d'une constitution — un
proxy d'`Intervenant` n'était jamais reconnu comme avocat, qui n'était donc jamais notifié.

**Testé** : `PublicationServiceTest` (+5), parcours d'intégration `p2` et `p7` étendus sur
PostgreSQL réel. **315 tests au vert, `BUILD SUCCESS`.** Un premier passage avait révélé un défaut du
nouveau code (`Set.of(...).contains(null)` lève une exception pour un utilisateur sans profil),
corrigé avant ce résultat.

### 15. Décisions définitives relisibles ; recherche jurisprudentielle souple (Q-88, Q-89) ✅

Ouverture du jalon F13 du frontend, décidé par le porteur du projet le 2026-09-11 (QF-03).
**Implémenté** : `GET /dossiers/{id}/decisions` — adjudications et condamnations du dossier ;
`GET /jurisprudences` partiel et insensible à la casse sur mots-clés, nature et juridiction, avec un
filtre `dossierId`. **Laissé ouvert (Q-90)** : la mise à jour d'une adjudication et le statut
initial d'une condamnation, à confirmer avec la Direction Juridique.

**Testé** : `JurisprudenceServiceTest` (+2, un mis à jour), `HabilitationFrontendIntegrationTest`
(+2, PostgreSQL réel). **319 tests au vert, `BUILD SUCCESS`.**

---

## Chemin critique

```
M1 ──▶ M2 ──▶ M3 ──▶ M4 ──▶ M5 ──┬──▶ M6 ──▶ M7 ──┐
                                  ├──▶ M8 ─────────┤
                                  └──▶ M9 ──▶ M10 ─┼──▶ M12 ──▶ M13 ──▶ M14
                                       └──▶ M11 ───┘
```

M6/M7, M8 et M9/M10/M11 sont indépendants entre eux une fois M5 livré : leur ordre peut être
ajusté selon les priorités métier (les Must Have de l'épic 1, 2, 3, 4 et 6 d'abord).

---

## Format attendu en fin de chaque jalon

```
Implémenté           : …
Testé                : … (tests automatiques exécutés et résultat réel)
Restant              : …
Validation manuelle  : instructions pas à pas
Message de commit    : <type>(<scope>): <description>   (Conventional Commits)
Jalon suivant        : … (non démarré sans autorisation explicite)
```
