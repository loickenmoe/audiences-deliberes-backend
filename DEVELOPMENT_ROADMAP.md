# DEVELOPMENT_ROADMAP

> Feuille de route séquentielle du backend. Un jalon = un objectif, un périmètre fermé,
> une validation manuelle par le porteur du projet avant passage au suivant.
> Dernière mise à jour : 2026-09-04 (M1 et M2 terminés et validés — M3 en attente d'autorisation).

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
| **M3** | Socle transversal (erreurs, historique, utilisateur, pagination) | Q-08 ✅ | ⏸️ **prêt à démarrer, en attente d'autorisation explicite** |
| **M4** | Module Dossiers | Q-10, Q-11, Q-12, Q-23, Q-26, Q-28 | ⏸️ |
| **M5** | Module Alertes (moteur + notifications) | Q-22, Q-32 | ⏸️ |
| **M6** | Module Audiences + Alarmes | Q-04, Q-13 | ⏸️ |
| **M7** | Module Délibérés | Q-17, Q-18, Q-19, Q-13 | ⏸️ |
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

## M3 — Socle transversal

**Objectif.** Fournir aux modules métier les briques dont ils dépendent tous.

Contenu : `GlobalExceptionHandler` produisant `{code, message, details}` avec les codes
`ERR-001`…`ERR-009` ; `ApiException` typée ; `CurrentUserService` (résolution du jeton Keycloak →
ligne `utilisateur`, provisioning à la première requête) ; `HistoriqueActionService` ;
utilitaires de pagination et d'export (PDF/Excel/CSV) ; configuration `@EnableScheduling` ;
tests unitaires du socle.

**Validation manuelle** : un appel invalide renvoie le format d'erreur du contrat ; un premier
appel d'un utilisateur Keycloak crée sa ligne `utilisateur` avec le bon `profil_id`.

---

## M4 — Module Dossiers

**Objectif.** Épic 1 complet (7 user stories) + clients.
Endpoints #1 à #12 de `API_IMPLEMENTATION_STATUS.md`.
Inclut : machine à états des étapes de procédure (transitions autorisées, création automatique des
étapes intermédiaires, ouverture automatique d'un recours), affectations N:N avec détection de
doublon et confirmation, circuit de sensibilité (Q-10), dérogation de seuil, historisation.

**Validation manuelle** : les 6 critères Gherkin de US 1.1, plus US 1.2 à 1.7.

---

## M5 — Module Alertes

**Objectif.** Épic 7. Moteur de déclenchement (`@Scheduled` + événements Spring), énumération
arrêtée en Q-22, canaux applicatif (obligatoire) / SMTP / Teams (webhook), configuration des seuils.
Placé avant les modules qui en dépendent (audiences, délibérés, GED, frais, publications).

**Validation manuelle** : création d'une alerte de test, réception sur le canal applicatif < 1 min,
`GET /alertes/mes-notifications`, marquage `TRAITEE` par un non-destinataire → 403.

---

## M6 — Module Audiences + Alarmes
Épic 2 (6 US). Planification + programmation automatique J-7/J-3/J-1, compte rendu juriste,
alarmes libres (Q-04), reprogrammation, calendrier avec exports, détection de doublon.

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
