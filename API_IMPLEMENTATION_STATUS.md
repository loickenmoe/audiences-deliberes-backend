# API_IMPLEMENTATION_STATUS

> Inventaire exhaustif des endpoints et état d'implémentation.
> Dernière mise à jour : 2026-09-04 (audit initial).

**Base URL** : `http://localhost:8080/api/v1` (contrat REF-07) — **le code écoute sur `8888`** (Q-16).
**Authentification** : `Authorization: Bearer <JWT Keycloak>` sur tous les endpoints sauf health checks.

**Légende** :
`✅` implémenté et testé · `🟡` partiel · `❌` non implémenté ·
`📋` spécifié dans l'OpenAPI REF-07 §3 · `📄` cité dans le tableau REF-07 §2 mais **absent de l'OpenAPI** ·
`💡` « à proposer » (identifié par les user stories, absent du contrat) · `🆕` manquant partout, déduit d'une règle

---

## Synthèse

| Catégorie | Nombre | Implémentés |
|---|---|---|
| 📋 Spécifiés dans l'OpenAPI | 37 | **0** |
| 📄 Cités §2 mais absents de l'OpenAPI | 3 | 0 |
| 💡 « À proposer » (user stories) | 10 | 0 |
| 🆕 Manquants dans toute la documentation | 2 | 0 |
| **Surface cible totale** | **~52** | **0 (0 %)** |

Endpoints **actuellement exposés** par le code : **12**, tous hors périmètre fonctionnel
(squelette de starter) — voir §10.

---

## 1. Dossiers

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 1 | POST | `/dossiers` | `ROLE_SAISIE` | FR-DOS-01 / US 1.1 | 📋 | ❌ |
| 2 | GET | `/dossiers` | `ROLE_CONSULTATION` | FR-DOS-03 / US 1.3 | 📋 | ❌ |
| 3 | GET | `/dossiers/{id}` | `ROLE_CONSULTATION` | FR-DOS-03 / US 1.3 | 📋 | ❌ |
| 4 | PUT | `/dossiers/{id}/affectation` | `ROLE_JURISTE`, `ROLE_DJ` | FR-DOS-02 / US 1.2 | 📋 | ❌ |
| 5 | PATCH | `/dossiers/{id}/etapes/{etapeId}/statut` | `ROLE_JURISTE` | FR-DOS-04 / US 1.4 | 📋 | ❌ |
| 6 | POST | `/dossiers/{id}/seuil-derogation` | `ROLE_SAISIE` | RG-DOS-09 | 📋 | ❌ |
| 7 | PUT | `/dossiers/{id}/seuil-derogation/{auditId}` | `ROLE_DJ`, `ROLE_DJA` | RG-DOS-09 | 📋 | ❌ |
| 8 | POST | `/dossiers/{id}/sensibilite/validation` | `ROLE_DJ`, `ROLE_DJA` | RG-DOS-09 / UC-DOS-01 alt. 6-a | 🆕 | ❌ |

> **#8** : RG-DOS-09 et UC-DOS-01 exigent que la proposition de sensibilité du juriste soit
> **validée par le DJ ou le DJA** (approuver / rejeter avec motif / ajuster) avant enregistrement
> définitif. **Aucun endpoint, aucune colonne de statut n'existe pour cela.** Cf. Q-10.

## 2. Clients

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 9 | GET | `/clients` | `ROLE_CONSULTATION` | US 8.6 | 📋 | ❌ |
| 10 | GET | `/clients/{id}` | `ROLE_CONSULTATION` | US 8.6 | 📋 | ❌ |
| 11 | GET | `/clients/{clientId}/dossiers` | `ROLE_CONSULTATION` | FR-REP-03 / US 8.6 | 📋 | ❌ |
| 12 | POST | `/clients` | à définir | INT-05 | 🆕 | ❌ |

> **#12** : le contrat expose la lecture des clients mais **aucun endpoint de création**, alors que
> `dossier.client_id` est `NOT NULL` et que « les données clients peuvent être saisies manuellement »
> (US §11.3, INT-05). Sans création de client, aucun dossier n'est créable. Cf. Q-23.

## 3. Audiences

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 13 | POST | `/dossiers/{id}/audiences` | `ROLE_JURISTE` | FR-AUD-01 / US 2.1, 2.6 | 📋 | ❌ |
| 14 | PUT | `/audiences/{id}/compte-rendu` | `ROLE_JURISTE` | FR-AUD-02 / US 2.2 | 📋 | ❌ |
| 15 | GET | `/audiences/calendrier` | `ROLE_JURISTE`, `ROLE_DJ` | FR-AUD-05 / US 2.5 | 📋 | ❌ |
| 16 | POST | `/dossiers/{id}/alarmes` | `ROLE_JURISTE` | FR-AUD-03 / US 2.3 | **📄** | ❌ |
| 17 | PUT | `/alarmes/{id}/reprogrammer` | `ROLE_JURISTE` | FR-AUD-04 / US 2.4 | **📄** | ❌ |

## 4. Délibérés

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 18 | POST | `/dossiers/{id}/deliberes` | `ROLE_JURISTE` | FR-DEL-01 / US 3.1 | 📋 | ❌ |
| 19 | POST | `/deliberes/{id}/prorogations` | `ROLE_JURISTE` | FR-DEL-04 / US 3.2, 3.3 | 📋 | ❌ |
| 20 | POST | `/deliberes/{id}/rabattement` | `ROLE_JURISTE` | FR-DEL-05 / US 3.4 | 📋 | ❌ |
| 21 | GET | `/deliberes/{id}/recours` | `ROLE_JURISTE` | FR-DEL-02 / US 3.5 | 📋 | ❌ |
| 22 | PUT | `/deliberes/{id}/expedition` | `ROLE_JURISTE` | FR-DEL-03 / US 3.6 | **📄** | ❌ |

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
| 38 | POST | `/ged/documents` | `ROLE_SAISIE` | FR-GED-01 / US 6.1 | 📋 | ❌ |
| 39 | GET | `/ged/documents/{id}` | `ROLE_CONSULTATION` | FR-GED-02 / US 6.3 | 📋 | ❌ |
| 40 | DELETE | `/ged/documents/{id}` | `ROLE_SAISIE` (202) / `ROLE_DJ`,`ROLE_DJA` (200) | FR-GED-03 / US 6.4, 6.5 | 📋 | ❌ |
| 41 | PUT | `/ged/documents/{id}/approbation-suppression` | `ROLE_DJ`, `ROLE_DJA` | FR-GED-03 / US 6.4 | 💡 | ❌ |
| 42 | GET | `/ged/rapport-journalier` | `ROLE_JURISTE` | FR-GED-04 / US 6.6 | 📋 | ❌ |
| 43 | GET | `/dossiers/{id}/documents` | `ROLE_CONSULTATION` | UC-GED-02 | 🆕 | ❌ |

> **#43** : UC-GED-02 et US 6.3 supposent la liste des documents d'un dossier. Le contrat ne
> prévoit que la consultation unitaire. Peut être couvert par la réponse de `GET /dossiers/{id}`.

## 9. Alertes, configurations, reporting

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 44 | PUT | `/alertes/seuils` | `ROLE_DJ` | FR-ALR-01 / US 7.1 | 📋 | ❌ |
| 45 | GET | `/alertes/mes-notifications` | authentifié | FR-ALR-02 / US 7.3 | 📋 | ❌ |
| 46 | PATCH | `/alertes/{id}/traiter` | destinataire | US 7.4 | 💡 | ❌ |
| 47 | GET | `/configurations` | `ROLE_CONSULTATION` | CONF01-07 | 📋 | ❌ |
| 48 | PUT | `/configurations/{cle}` | `ROLE_DJ` | CONF01-07 | 📋 | ❌ |
| 49 | GET | `/rapports` | `ROLE_JURISTE` | FR-REP-01 / US 8.4 | 📋 | ❌ |
| 50 | GET | `/tableau-de-bord` | `ROLE_DJ` | FR-REP-02 / US 8.5 | 📋 | ❌ |

## 10. Décisions définitives

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 51 | POST | `/dossiers/{id}/adjudications` | `ROLE_JURISTE` | FR-DEF-01 / US 8.1 | 💡 | ❌ |
| 52 | POST | `/dossiers/{id}/condamnations` | `ROLE_JURISTE` | FR-DEF-02 / US 8.2 | 💡 | ❌ |
| 53 | PATCH | `/condamnations/{id}/statut` | `ROLE_JURISTE` | FR-DEF-02 / US 8.2 | 💡 | ❌ |
| 54 | POST | `/jurisprudences` | `ROLE_JURISTE` | FR-DEF-03 / US 8.3 | 💡 | ❌ |
| 55 | GET | `/jurisprudences` | `ROLE_CONSULTATION` | UC-DEF-03 (« consultable dans la base ») | 🆕 | ❌ |

---

## 11. Endpoints actuellement exposés par le code (hors périmètre)

| Méthode | Endpoint | Contrôleur | Sécurité effective | Verdict |
|---|---|---|---|---|
| GET | `/api/v1/templates` | `TemplateController` | authentifié | 🗑️ squelette — renvoie `"Liste des templates"` |
| GET | `/api/v1/templates/{id}` | `TemplateController` | authentifié | 🗑️ squelette |
| POST | `/api/v1/templates` | `TemplateController` | authentifié | 🗑️ squelette |
| PUT | `/api/v1/templates/{id}` | `TemplateController` | authentifié | 🗑️ squelette |
| DELETE | `/api/v1/templates/{id}` | `TemplateController` | authentifié | 🗑️ squelette |
| GET | `/api/v1/admin/users` | `AdminUserController` | **authentifié seulement** | 🔴 `@PreAuthorize` commenté |
| GET | `/api/v1/admin/users/{id}` | `AdminUserController` | **authentifié seulement** | 🔴 |
| POST | `/api/v1/admin/users` | `AdminUserController` | **authentifié seulement** | 🔴 création d'utilisateur libre |
| PUT | `/api/v1/admin/users/{id}` | `AdminUserController` | **authentifié seulement** | 🔴 |
| DELETE | `/api/v1/admin/users/{id}` | `AdminUserController` | **authentifié seulement** | 🔴 |
| GET | `/api/v1/staff` | `StaffController` | `hasAnyRole('ADMIN','USER')` | ⚠️ rôles inexistants → 403 systématique |
| GET | `/api/v1/staff/{role}` | `StaffController` | `hasAnyRole('ADMIN','USER')` | ⚠️ idem |

Contrôleurs déclarés **sans aucun endpoint** : `AdminDashboardController`, `UserDashboardController`.
Contrôleur désactivé : `AuthController` (`@RestController` commenté).
Endpoints STOMP hors périmètre : `/app/chat.public`, `/app/chat.private`, `/app/chat.join`, `/app/chat.leave`.

**Recommandation** : supprimer l'intégralité de ce squelette au premier jalon
(voir `DEVELOPMENT_ROADMAP.md`, jalon M1).

---

## 12. Codes d'erreur du contrat (à implémenter dans le `GlobalExceptionHandler`)

| Code | HTTP | Description | Endpoint |
|---|---|---|---|
| `ERR-001` | 400 | Métadonnées manquantes | `POST /dossiers` |
| `ERR-002` | 409 | Référence de dossier déjà utilisée | `POST /dossiers` |
| `ERR-003` | 409 | Doublon d'affectation | `PUT /dossiers/{id}/affectation` |
| `ERR-004` | 400 | Transition non autorisée | `PATCH …/statut` |
| `ERR-005` | 409 | Doublon d'audience | `POST /dossiers/{id}/audiences` |
| `ERR-006` | 409 | Facture en double | `POST /frais/demandes` |
| `ERR-007` | 409 / 422 / 201 ? | Plus de 10 prorogations | `POST /deliberes/{id}/prorogations` — **Q-17** |
| `ERR-008` | 400 | Format ou poids du document non conforme | `POST /ged/documents` |
| `ERR-009` | 403 | Accès restreint à la publication | `GET /publications/{id}` |

Codes génériques : 400 requête invalide · 401 non authentifié · 403 droits insuffisants ·
404 non trouvé · 409 conflit d'unicité · 422 règle métier violée.

---

## 13. Hors périmètre V1 (REF-07 §6.2)

| Endpoint | Statut |
|---|---|
| `POST /dossiers/{id}/suppression` (suppression d'un dossier avec workflow) | À étudier — V2 |
| `GET /integrations/corebanking` | Hors périmètre V1 |
| `POST /integrations/comptabilite` | Hors périmètre V1 |
