# DEVELOPMENT_ROADMAP

> Feuille de route séquentielle du backend. Un jalon = un objectif, un périmètre fermé,
> une validation manuelle par le porteur du projet avant passage au suivant.
> Dernière mise à jour : 2026-09-04 (audit initial — **aucun jalon démarré**).

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
| **M1** | Assainissement du socle et sécurisation | R-01→R-05, Q-08, Q-16 | ⏸️ en attente de validation |
| **M2** | Schéma de base et référentiels | Q-00, Q-01, Q-02, Q-03, Q-04, Q-05, Q-06, Q-07, Q-22, Q-26, Q-27, Q-29 | ⏸️ |
| **M3** | Socle transversal (erreurs, historique, utilisateur, pagination) | Q-08 | ⏸️ |
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

## M1 — Assainissement du socle et sécurisation — **premier jalon d'implémentation**

**Objectif.** Obtenir un socle Spring Boot propre, sécurisé et conforme aux hypothèses de
conception validées, sans aucune fonctionnalité métier. Le dépôt doit démarrer, exposer Swagger,
authentifier via Keycloak et **rien d'autre**.

### Ce qui change
- **Suppression** du squelette hors périmètre : `TemplateController/Service/ServiceImpl/DTO/Repository/Template`,
  `AdminUserController`, `AdminDashboardController`, `UserDashboardController`, `StaffController`,
  `UserService(Impl)`, `DashboardService(Impl)`, `ChatController`, `ChatMessage`,
  `WebSocketEventListener`.
- **Suppression de l'authentification héritée** (R-03) : `AuthController`, `AuthService`,
  `JwtService`, `JwtAuthenticationFilter`, `UserDetailsServiceImpl`, entité `User`,
  `UserRepository`, DTO `auth/`, dépendances `jjwt` (`api`, `impl`, `jackson`).
  L'enum `Role` est conservée mais déplacée vers `common/security` (valeurs alignées sur le realm).
- **Sécurité** : `WebSocketAuthInterceptor` réécrit sur le `JwtDecoder` Keycloak, refus explicite
  des connexions anonymes ; `/ws/**` retiré de la liste `permitAll`.
- **Secrets (R-01)** : `env.template` réduit à des placeholders ; note de rotation ajoutée au README.
  *(La rotation effective des secrets Neon/iDrive et la purge de l'historique git relèvent d'une
  action manuelle du porteur du projet — hors périmètre de ce jalon.)*
- **Infrastructure (R-02)** : `docker-compose.yml` complété d'un service `postgres` applicatif et
  d'un service `minio` locaux ; `application.properties` pointé dessus.
- **Ports (Q-16)** : backend `8080`, Keycloak `8081` — sous réserve de la décision Q-16.
- **RBAC (Q-08)** : ajout de `ROLE_SAISIE` au realm en rôle composite — sous réserve de Q-08.
- **`pom.xml`** : suppression des doublons (`starter-mail` ×2, `starter-websocket` ×2), retrait
  d'`itextpdf` (Q-31), ajout de `mapstruct` aux `annotationProcessorPaths`, ajout de Flyway et
  Testcontainers.
- **CORS** : `CorsConfig` supprimé, configuration unique dans `SecurityConfig`.
- **Test** : renommage `audiencesApplicationTests` → `AudiencesApplicationTests` (non exécuté par
  Surefire en l'état).

### Ce qui ne change pas
Aucune entité métier, aucun endpoint métier, aucun schéma de base.

### Validation manuelle attendue
`docker compose up -d postgres keycloak minio` → `mvn spring-boot:run` → l'application démarre ;
`GET /actuator/health` = 200 sans jeton ; `GET /api/v1/**` sans jeton = 401 ; obtention d'un jeton
`juriste.test` et appel authentifié ; Swagger UI accessible ; `git grep` ne retrouve aucun secret
dans les fichiers versionnés.

---

## M2 — Schéma de base et référentiels

**Objectif.** Traduire REF-05 §12.1 + REF-06 en migrations Flyway versionnées, complétées des
tables manquantes arbitrées (Q-01 à Q-07, Q-22, Q-26, Q-27, Q-29).

Contenu : `V1__schema_initial.sql` (24 à 28 tables, contraintes `CHECK`, FK avec le comportement
`ON DELETE` du dictionnaire §3, index §4), `V2__donnees_reference.sql` (6 profils,
`configuration_systeme` : `SEUIL_MONTANT_DEFAUT`, `SEUIL_PROROGATIONS`, `CANAUX_NOTIFICATION_DEFAUT`,
`DELAI_VALIDATION_CONJOINTE`, `TAILLE_MAX_GED`, `FORMATS_GED` ; taxonomie des natures, Annexe A),
entités JPA + repositories pour les référentiels (`profil`, `utilisateur`, `client`,
`configuration_systeme`, `intervenant`/`avocat`/`autre_prestataire`), `ddl-auto=validate`.

**Validation manuelle** : `\dt` liste toutes les tables ; les contraintes `CHECK` rejettent les
valeurs hors énumération ; les données de référence sont présentes ; l'application démarre en
`validate` sans erreur.

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
