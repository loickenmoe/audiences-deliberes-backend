# API_IMPLEMENTATION_STATUS

> Inventaire exhaustif des endpoints et état d'implémentation.
> Dernière mise à jour : 2026-09-06 (après jalon M8 — module GED).

**Base URL** : `http://localhost:8080/api/v1` — aligné sur le contrat REF-07 depuis M1 (Q-16 résolue).
**Authentification** : `Authorization: Bearer <JWT Keycloak>` sur tous les endpoints sauf health checks.

**Légende** :
`✅` implémenté et testé · `🟡` partiel · `❌` non implémenté ·
`📋` spécifié dans l'OpenAPI REF-07 §3 · `📄` cité dans le tableau REF-07 §2 mais **absent de l'OpenAPI** ·
`💡` « à proposer » (identifié par les user stories, absent du contrat) · `🆕` manquant partout, déduit d'une règle

---

## Synthèse

| Catégorie | Nombre | Implémentés |
|---|---|---|
| 📋 Spécifiés dans l'OpenAPI | 37 | **27** (§1-2, §3 #13-15, #18-21, §8 #38-40 #42, §9 #44-48) |
| 📄 Cités §2 mais absents de l'OpenAPI | 3 | 3 (§3 #16-17 alarmes, #22 expédition — Q-13, close) |
| 💡 « À proposer » (user stories) | 10 | 1 (§8 #41 approbation-suppression) |
| 🆕 Manquants dans toute la documentation | 2 | 1 (`POST /clients`) |
| 🆕 Ajoutés en cours d'implémentation (hors inventaire initial) | 1 | 2 (`POST /dossiers/{id}/etapes` — Q-34 ; `GET /dossiers/{id}/documents` — #43) |
| **Surface cible totale** | **~53** | **34 (≈64 %)** |

Le squelette de starter hors périmètre (Template/Admin/Staff/Dashboard/Chat, auth JWT maison) a
été intégralement supprimé en M1 (cf. `DECISIONS_AND_OPEN_QUESTIONS.md` R-03/R-04) — l'ancienne
§11 « endpoints actuellement exposés hors périmètre » n'a donc plus d'objet et a été retirée.

---

## 1. Dossiers

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 1 | POST | `/dossiers` | `ROLE_SAISIE` | FR-DOS-01 / US 1.1 | 📋 | ✅ M4 |
| 2 | GET | `/dossiers` | `ROLE_CONSULTATION` | FR-DOS-03 / US 1.3 | 📋 | ✅ M4 |
| 3 | GET | `/dossiers/{id}` | `ROLE_CONSULTATION` | FR-DOS-03 / US 1.3 | 📋 | ✅ M4 |
| 4 | PUT | `/dossiers/{id}/affectation` | `ROLE_JURISTE`, `ROLE_DJ` | FR-DOS-02 / US 1.2 | 📋 | ✅ M4 |
| 5 | PATCH | `/dossiers/{id}/etapes/{etapeId}/statut` | `ROLE_JURISTE` | FR-DOS-04 / US 1.4 | 📋 | ✅ M4 |
| 6 | POST | `/dossiers/{id}/seuil-derogation` | `ROLE_SAISIE` | RG-DOS-09 | 📋 | ✅ M4 |
| 7 | PUT | `/dossiers/{id}/seuil-derogation/{auditId}` | `ROLE_DJ`, `ROLE_DJA` | RG-DOS-09 | 📋 | ✅ M4 |
| 8 | POST | `/dossiers/{id}/sensibilite/validation` | `ROLE_DJ`, `ROLE_DJA` | RG-DOS-09 / UC-DOS-01 alt. 6-a | 🆕 | ✅ M4 |
| — | POST | `/dossiers/{id}/etapes` | `ROLE_SAISIE` | UC-DOS-04 alt. 3-b | 🆕 | ✅ M4 |

> **#8** : RG-DOS-09 et UC-DOS-01 exigent que la proposition de sensibilité du juriste soit
> **validée par le DJ ou le DJA** (approuver / rejeter avec motif / ajuster) avant enregistrement
> définitif. Implémenté via `audit_seuil_dossier` comme file de propositions en attente — cf. Q-10.
>
> **Endpoint additionnel non numéroté** : `POST /dossiers/{id}/etapes` (positionnement direct
> d'une étape sans antécédents, UC-DOS-04 alt. 3-b), absent des 12 endpoints du contrat formel
> mais nécessaire — cf. Q-34.

Testé manuellement de bout en bout (curl + JWT Keycloak réels, DB Postgres locale) pour les 12
endpoints du contrat + les 2 🆕, ainsi que via 51 tests automatisés (`DossierServiceTest`,
`EtapeTransitionValidatorTest`, `TypeEtapeTest`). Voir `DECISIONS_AND_OPEN_QUESTIONS.md` Q-01,
Q-10, Q-11, Q-12, Q-26, Q-34, Q-35 pour le détail des arbitrages pris pendant l'implémentation.

## 2. Clients

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 9 | GET | `/clients` | `ROLE_CONSULTATION` | US 8.6 | 📋 | ✅ M4 |
| 10 | GET | `/clients/{id}` | `ROLE_CONSULTATION` | US 8.6 | 📋 | ✅ M4 |
| 11 | GET | `/clients/{clientId}/dossiers` | `ROLE_CONSULTATION` | FR-REP-03 / US 8.6 | 📋 | ✅ M4 |
| 12 | POST | `/clients` | `ROLE_SAISIE` | INT-05 | 🆕 | ✅ M4 |

> **#12** : le contrat expose la lecture des clients mais **aucun endpoint de création**, alors que
> `dossier.client_id` est `NOT NULL` et que « les données clients peuvent être saisies manuellement »
> (US §11.3, INT-05). Implémenté sous `ROLE_SAISIE` — cf. Q-23. `PUT /clients/{id}` non retenu,
> aucun besoin identifié en M4.

## 3. Audiences

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 13 | POST | `/dossiers/{id}/audiences` | `ROLE_JURISTE` | FR-AUD-01 / US 2.1, 2.6 | 📋 | ✅ M6 |
| 14 | PUT | `/audiences/{id}/compte-rendu` | `ROLE_JURISTE` | FR-AUD-02 / US 2.2 | 📋 | ✅ M6 |
| 15 | GET | `/audiences/calendrier` | `ROLE_JURISTE`, `ROLE_DJ` | FR-AUD-05 / US 2.5 | 📋 | ✅ M6 |
| 16 | POST | `/dossiers/{id}/alarmes` | `ROLE_JURISTE` | FR-AUD-03 / US 2.3 | **📄** | ✅ M6 |
| 17 | PUT | `/alarmes/{id}/reprogrammer` | `ROLE_JURISTE` | FR-AUD-04 / US 2.4 | **📄** | ✅ M6 |

> Testé manuellement de bout en bout (curl + JWT Keycloak réels) : doublon d'audience confirmé
> (`?forcer=true`, Q-12), compte rendu refusé sur audience future, rappels J-7/J-3/J-1 déclenchés
> par `RappelAudienceScheduler` (cron accéléré pour le test) avec anti-doublon, alarme reprogrammée
> (chaîne `alarme_precedente_id`), échéance d'alarme déclenchant une alerte `ALARME` une seule fois
> (`AlarmeEcheanceScheduler`), export du calendrier dans les 3 formats (signatures de fichier
> vérifiées). 24 tests automatisés dédiés. Voir `DECISIONS_AND_OPEN_QUESTIONS.md` Q-04, Q-12, Q-13,
> Q-39, Q-40, Q-41.

## 4. Délibérés

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 18 | POST | `/dossiers/{id}/deliberes` | `ROLE_JURISTE` | FR-DEL-01 / US 3.1 | 📋 | ✅ M7 |
| 19 | POST | `/deliberes/{id}/prorogations` | `ROLE_JURISTE` | FR-DEL-04 / US 3.2, 3.3 | 📋 | ✅ M7 |
| 20 | POST | `/deliberes/{id}/rabattement` | `ROLE_JURISTE` | FR-DEL-05 / US 3.4 | 📋 | ✅ M7 |
| 21 | GET | `/deliberes/{id}/recours` | `ROLE_JURISTE` | FR-DEL-02 / US 3.5 | 📋 | ✅ M7 |
| 22 | PUT | `/deliberes/{id}/expedition` | `ROLE_JURISTE` | FR-DEL-03 / US 3.6 | **📄** | ✅ M7 |

> **#18** : `dateEcheanceRecours` requise et postérieure à `dateDeliberee` si `resultat` ∈
> {DEFAVORABLE, MIXTE} (Q-42), absente sinon ; l'enregistrement fait basculer l'étape en
> `DELIBERE_VIDE`.
> **#19** : réponse toujours `201 Created`, corps `{...prorogation, alerte}` — `alerte:true` dès
> que le compteur dépasse le seuil configurable `SEUIL_PROROGATIONS` (Q-17/Q-18) ; aucune
> précondition de statut d'étape (Q-44).
> **#20** : réservé aux délibérés dont l'étape est encore `DELIBERE_VIDE` (RG-DEL-07) ; ne rouvre
> jamais l'étape suivante — distinct de l'exercice d'un recours (endpoint générique #5, Q-42).
> **#21** : strictement consultatif (Q-19) ; le suivi de l'échéance est assuré par
> `DelaiRecoursScheduler` (bascule `CLOTURE` + alerte `DELAI_RECOURS`, Q-43).
> **#22** : dernier volet de Q-13, absent de l'OpenAPI formel mais implémenté selon les critères
> Gherkin de l'US 3.6.

Testé manuellement de bout en bout (curl + JWT Keycloak réels) : rejet d'un délibéré sur étape non
`EN_DELIBERE`, échéance de recours refusée/exigée selon le résultat, 10 puis 11 prorogations
(alerte `PROROGATION` déclenchée une seule fois vers DJ et DJA actifs), rabattement refusé hors
`DELIBERE_VIDE` puis accepté (nouveau cycle `EN_COURS`→`EN_DELIBERE`→second délibéré sur la même
étape), expiration du délai de recours déclenchée en conditions réelles (cron accéléré) avec
bascule `CLOTURE` et alerte `DELAI_RECOURS` (anti-doublon vérifié sur plusieurs cycles), levée
d'expédition (403 hors rôle JURISTE / 200 sinon). 14 tests automatisés dédiés. Voir
`DECISIONS_AND_OPEN_QUESTIONS.md` Q-13, Q-17, Q-18, Q-19, Q-35, Q-42, Q-43, Q-44 — ainsi que R-06
(défaut préexistant du socle M3 découvert en validation, hors périmètre de ce module).

## 5. Frais

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 23 | POST | `/frais/demandes` | `ROLE_AVOCAT` | FR-INT-01 / US 4.1 | 📋 | ❌ |
| 24 | PUT | `/frais/demandes/{id}/conformite` | `ROLE_ASSISTANTE` | FR-INT-02 / US 4.2 | 📋 | ❌ |
| 25 | PUT | `/frais/demandes/{id}/opportunite` | `ROLE_DJA` | FR-INT-03 / US 4.4 | 📋 | ❌ |
| 26 | POST | `/frais/demandes/{id}/validation-conjointe` | `ROLE_DJ` **et** `ROLE_DJA` | FR-INT-04 / US 4.5 | 📋 | ❌ |
| 27 | PATCH | `/frais/demandes/{id}/paiement` | `ROLE_ASSISTANTE` | FR-INT-05 / US 4.6 | 📋 | ❌ |
| 28 | GET | `/frais/demandes` | selon profil | UC-INT-02/03/04 (« liste des demandes en attente ») | 🆕 | ❌ |

> **#28** : les UC-INT-02/03/04 supposent tous une **file de travail** (« le système affiche la liste
> des demandes validées en attente de paiement », « aucune demande en attente »). Aucun endpoint de
> liste n'est spécifié. Cf. Q-24.

## 6. Publications

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 29 | POST | `/publications/cr-audience` | `ROLE_AVOCAT` | FR-INT-09 / US 5.1 | 📋 | ❌ |
| 30 | PUT | `/publications/cr-audience/{id}/validation` | `ROLE_ASSISTANTE` | FR-INT-09 / US 5.2 | 📋 | ❌ |
| 31 | POST | `/publications/autres` | `ROLE_AVOCAT` | FR-INT-09 / US 5.3 | 📋 | ❌ |
| 32 | GET | `/publications/{id}` | `ROLE_CONSULTATION` | US 5.4 | 📋 | ❌ |
| 33 | POST | `/publications/{id}/commentaires` | `ROLE_JURISTE` | FR-INT-09 / US 5.5 | 📋 | ❌ |
| 34 | POST | `/publications/{id}/correspondance` | `ROLE_DJ`, `ROLE_DJA` | RG-INT-07 / US 5.5 | 💡 | ❌ |

> ⚠️ L'OpenAPI référence les schémas `Publication`, `CompteRenduAudience` et `AutrePublication`
> via `$ref` mais **ne les définit jamais** dans `components/schemas`. Contrat incomplet (Q-25).

## 7. Constitutions et répertoire

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 35 | POST | `/constitutions/prestataires` | `ROLE_JURISTE` | FR-INT-06 / US 5.6 | 💡 | ❌ |
| 36 | PUT | `/constitutions/prestataires/{id}/validation` | `ROLE_SH` | FR-INT-07 / US 5.7 | 💡 | ❌ |
| 37 | GET | `/repertoire/avocats` | `ROLE_JURISTE`, `ROLE_ASSISTANTE` | FR-INT-08 / US 5.8 | 💡 | ❌ |

## 8. GED

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 38 | POST | `/ged/documents` | `ROLE_SAISIE` | FR-GED-01 / US 6.1 | 📋 | ✅ M8 |
| 39 | GET | `/ged/documents/{id}` | `ROLE_CONSULTATION` | FR-GED-02 / US 6.3 | 📋 | ✅ M8 |
| 40 | DELETE | `/ged/documents/{id}` | `ROLE_SAISIE` (202) / `ROLE_DJ`,`ROLE_DJA` (200) | FR-GED-03 / US 6.4, 6.5 | 📋 | ✅ M8 |
| 41 | PUT | `/ged/documents/{id}/approbation-suppression` | `ROLE_DJ`, `ROLE_DJA` | FR-GED-03 / US 6.4 | 💡 | ✅ M8 |
| 42 | GET | `/ged/rapport-journalier` | `ROLE_JURISTE` | FR-GED-04 / US 6.6 | 📋 | ✅ M8 |
| 43 | GET | `/dossiers/{id}/documents` | `ROLE_CONSULTATION` | UC-GED-02 | 🆕 | ✅ M8 |

> **#38** : format dérivé de l'extension du fichier (pas du `Content-Type` déclaré, Q-46) ;
> `typeDocument` reste un texte libre (Q-45). **#40** : double comportement selon le profil résolu
> côté serveur — juriste auteur du chargement → demande (202) ; DJ/DJA → suppression immédiate
> (200) ; 409 si une demande est déjà en attente (Q-47). **#41** : absent du contrat formel,
> implémenté selon les critères Gherkin de l'US 6.4 ; motif de rejet obligatoire. **#42** :
> `dossiersManipules`/`piecesAjoutees`/`affairesNouvelles`/`planificationActes[]` définis en Q-49
> (dépendance étendue vers le module `audience`). **#43** : UC-GED-02 et US 6.3 supposent la liste
> des documents d'un dossier, absente du contrat qui ne prévoit que la consultation unitaire.

Testé manuellement de bout en bout (curl + JWT Keycloak réels) : rejet d'un format non conforme et
d'un fichier trop volumineux, dépôt avec notification des juristes cotés, URL pré-signée MinIO,
liste des documents d'un dossier, demande de suppression par l'auteur (202, 409 en doublon), refus
pour un juriste non auteur (403), suppression directe DJ/DJA (200), approbation et rejet (motif
obligatoire) d'une demande, relance à 72 h déclenchée en conditions réelles avec anti-doublon,
rapport journalier correct (actif et à zéro). 16 tests automatisés dédiés. Voir
`DECISIONS_AND_OPEN_QUESTIONS.md` Q-02, Q-45, Q-46, Q-47, Q-48, Q-49, Q-50, Q-51.

## 9. Alertes, configurations, reporting

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 44 | PUT | `/alertes/seuils` | `ROLE_DJ` | FR-ALR-01 / US 7.1 | 📋 | ✅ M5 |
| 45 | GET | `/alertes/mes-notifications` | authentifié | FR-ALR-02 / US 7.3 | 📋 | ✅ M5 |
| 46 | PATCH | `/alertes/{id}/traiter` | destinataire | US 7.4 | 💡 | ✅ M5 |
| 47 | GET | `/configurations` | `ROLE_CONSULTATION` | CONF01-07 | 📋 | ✅ M5 |
| 48 | PUT | `/configurations/{cle}` | `ROLE_DJ` | CONF01-07 | 📋 | ✅ M5 |
| 49 | GET | `/rapports` | `ROLE_JURISTE` | FR-REP-01 / US 8.4 | 📋 | ❌ |
| 50 | GET | `/tableau-de-bord` | `ROLE_DJ` | FR-REP-02 / US 8.5 | 📋 | ❌ |

> **#44** : `type` doit valoir `CHARGE_MAX_AVOCAT` (400 sinon) — seul type d'alerte du contrat
> adossé à un seuil configurable (RG-ALR-02) ; délègue à `PUT /configurations/{cle}` en interne.
> Le déclenchement lui-même est planifié (`ChargeMaxAvocatScheduler`, module `dossier`, cf. Q-38),
> pas un endpoint. **#46** : rôle « destinataire » vérifié en service (403 sinon), pas par
> `@PreAuthorize` — cf. Q-36. Testé manuellement de bout en bout (déclenchement planifié avec
> anti-doublon, doublon d'affectation M4→M5, traitement par le seul destinataire, push WebSocket
> best-effort) + 15 tests automatisés (`AlerteServiceTest`, `ChargeMaxAvocatSchedulerTest`,
> `ConfigurationServiceTest`). Voir Q-22, Q-32, Q-36, Q-37, Q-38.

## 10. Décisions définitives

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 51 | POST | `/dossiers/{id}/adjudications` | `ROLE_JURISTE` | FR-DEF-01 / US 8.1 | 💡 | ❌ |
| 52 | POST | `/dossiers/{id}/condamnations` | `ROLE_JURISTE` | FR-DEF-02 / US 8.2 | 💡 | ❌ |
| 53 | PATCH | `/condamnations/{id}/statut` | `ROLE_JURISTE` | FR-DEF-02 / US 8.2 | 💡 | ❌ |
| 54 | POST | `/jurisprudences` | `ROLE_JURISTE` | FR-DEF-03 / US 8.3 | 💡 | ❌ |
| 55 | GET | `/jurisprudences` | `ROLE_CONSULTATION` | UC-DEF-03 (« consultable dans la base ») | 🆕 | ❌ |

---

## 11. Codes d'erreur du contrat (à implémenter dans le `GlobalExceptionHandler`)

| Code | HTTP | Description | Endpoint | Statut |
|---|---|---|---|---|
| `ERR-001` | 400 | Métadonnées manquantes | `POST /dossiers` | ✅ M4 |
| `ERR-002` | 409 | Référence de dossier déjà utilisée | `POST /dossiers` | ✅ M4 |
| `ERR-003` | 409 | Doublon d'affectation | `PUT /dossiers/{id}/affectation` | ✅ M4 (`forcer` — Q-12) |
| `ERR-004` | 400 | Transition non autorisée | `PATCH …/statut` | ✅ M4 |
| `ERR-005` | 409 | Doublon d'audience | `POST /dossiers/{id}/audiences` | ✅ M6 (`forcer` — Q-12) |
| `ERR-006` | 409 | Facture en double | `POST /frais/demandes` | ❌ M8 |
| `ERR-007` | 409 / 422 / 201 ? | Plus de 10 prorogations | `POST /deliberes/{id}/prorogations` — **Q-17** | ❌ M7 |
| `ERR-008` | 400 | Format ou poids du document non conforme | `POST /ged/documents` | ❌ M9 |
| `ERR-009` | 403 | Accès restreint à la publication | `GET /publications/{id}` | ❌ M9 |

Codes génériques : 400 requête invalide · 401 non authentifié · 403 droits insuffisants ·
404 non trouvé · 409 conflit d'unicité · 422 règle métier violée.

---

## 12. Hors périmètre V1 (REF-07 §6.2)

| Endpoint | Statut |
|---|---|
| `POST /dossiers/{id}/suppression` (suppression d'un dossier avec workflow) | À étudier — V2 |
| `GET /integrations/corebanking` | Hors périmètre V1 |
| `POST /integrations/comptabilite` | Hors périmètre V1 |
