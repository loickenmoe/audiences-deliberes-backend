# DEVELOPMENT_ROADMAP

> Feuille de route séquentielle du backend. Un jalon = un objectif, un périmètre fermé,
> une validation manuelle par le porteur du projet avant passage au suivant.
> Dernière mise à jour : 2026-09-04 (M1 à M6 terminés et validés — M7 en attente d'autorisation).

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
| **M6** | Module Audiences + Alarmes | Q-04 ✅, Q-12 ✅, Q-13 ✅, Q-39 ✅, Q-40 ✅, Q-41 ✅ | ✅ **Terminé et validé manuellement** (2026-09-04) |
| **M7** | Module Délibérés | Q-17, Q-18, Q-19, Q-13 | ⏸️ **prêt à démarrer, en attente d'autorisation explicite** |
| **M8** | Module GED | Q-02, Q-07, Q-24, R-02 | ⏸️ |
| **M9** | Module Intervenants (avocats, répertoire, constitutions) | Q-03, Q-28 | ⏸️ |
| **M10** | Module Frais (circuit de validation) | Q-05, Q-24, Q-26 | ⏸️ |
| **M11** | Module Publications et correspondances | Q-20, Q-24, Q-25 | ⏸️ |
| **M12** | Module Décisions définitives et jurisprudence | — | ⏸️ |
| **M13** | Module Reporting et tableau de bord | Q-14 | ⏸️ |
| **M14** | Durcissement, tests d'intégration, vérification finale de l'API | toutes | ⏸️ |

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

## M7 — Module Délibérés
Épic 3 (6 US). Enregistrement, prorogations illimitées + alerte au seuil (Q-17, Q-18),
rabattement, suivi du délai de recours avec bascule automatique en `CLOTURE`, levée d'expédition.

## M8 — Module GED
Épic 6 (6 US). Intégration MinIO (URLs pré-signées), contrôle format/poids, notification des
juristes cotés, circuit de suppression avec approbation et relance 72 h, suppression directe DJ/DJA,
rapport journalier.

## M9 — Module Intervenants
Répertoire des avocats avec indicateurs de charge et de performance, gestion des prestataires,
demandes de constitution + seconde signature SH + génération de la lettre (US 5.6, 5.7, 5.8).

## M10 — Module Frais
Épic 4 (6 US). Circuit complet dépôt → conformité → opportunité → validation conjointe → paiement,
anti-doublon de facture, résolution du seuil applicable, traçabilité par étape (Q-05).

## M11 — Module Publications
US 5.1 à 5.5. Comptes rendus d'avocat + validation Assistante, autres publications, restriction
d'accès, commentaires centralisés et correspondance DJ/DJA.

## M12 — Module Décisions définitives
Épic 8 partiel (US 8.1, 8.2, 8.3). Adjudications, condamnations pécuniaires, base jurisprudentielle
indexée et consultable.

## M13 — Module Reporting
US 8.4, 8.5, 8.6. Rapports périodiques avec exports PDF/Excel/CSV < 10 s, tableau de bord exécutif
temps réel < 2 s, vue consolidée par client.

## M14 — Durcissement et vérification finale
Tests d'intégration bout en bout des 9 parcours de `FUNCTIONAL_MAP.md` §9 ; couverture cible > 80 %
(REF-02 §10.2) ; vérification de conformité endpoint par endpoint au contrat d'API ; campagne de
performance (< 2 s / 50 utilisateurs simultanés) ; revue de sécurité ; documentation technique et
mise à jour finale des fichiers de contexte.

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
