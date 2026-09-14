# API_IMPLEMENTATION_STATUS

> Inventaire exhaustif des endpoints et état d'implémentation.
> Dernière mise à jour : 2026-09-14 (M18 — tableau de bord exécutif ouvert à la DJA, Q-95).
> Précédente : 2026-09-14 (M17 — paramètres système validés par clé Q-91 ; listes triées Q-92/Q-93 ; vue consolidée d'un client sans N+1 Q-94).
> Précédente : 2026-09-11 (M16 — cycle du délibéré Q-78 ; GED : demande orpheline et file nommée Q-79, rapport journalier Q-80).
> **Conformité vérifiée endpoint par endpoint en M14** : les 56 endpoints du code ont été audités
> contre ce tableau — 54 strictement conformes, 2 écarts de rôle corrigés ici (`POST
> /dossiers/{id}/etapes` et `DELETE /ged/documents/{id}` : `ROLE_JURISTE`, non `ROLE_SAISIE`) — Q-66.
> Les 11 endpoints de M15 (#56-#66) portent tous un `@PreAuthorize` explicite, vérifié par
> `HabilitationFrontendIntegrationTest` (servi au profil attendu **et refusé aux autres**).

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
| 📋 Spécifiés dans l'OpenAPI | 37 | **37 (100 %)** (§1-2, §3 #13-15, #18-21, §5 #23-27, §6 #29-33, §8 #38-40 #42, §9 #44-45 #47-50) |
| 📄 Cités §2 mais absents de l'OpenAPI | 3 | 3 (§3 #16-17 alarmes, #22 expédition — Q-13, close) |
| 💡 « À proposer » (user stories) | 10 | **10 (100 %)** (§6 #34 correspondance ; §7 #35-37 constitutions/répertoire ; §8 #41 approbation-suppression, #46 traiter alerte ; §10 #51-54 décisions définitives) |
| 🆕 Manquants dans toute la documentation | 2 | 1 (`POST /clients`) |
| **Surface cible totale (inventaire initial)** | **~52** | **51 (98 %)** |

**Tous les endpoints spécifiés (OpenAPI + « à proposer ») sont désormais implémentés** ; seul
`POST /clients` (🆕, manquant de toute documentation source, cf. Q-23) atteint la barre des 100 %
qu'après une décision d'ajout — traité comme tel dès M4.

**+ 14 endpoints hors inventaire initial**, ajoutés faute d'alternative dans le contrat :

- **M4 à M12 (4)** : `POST /dossiers/{id}/etapes` (Q-34), `GET /dossiers/{id}/documents` (#43),
  `GET /frais/demandes` (#28, Q-24), `GET /jurisprudences` (#55, UC-DEF-03).
- **M15 (11)** — jalon « habilitation frontend » (Q-68, Q-69) : trois onglets d'une fiche dossier
  (#56 audiences, #57 alarmes, #58 délibérés), trois files de travail fermant Q-24 (#59
  publications, #60 constitutions, #63 demandes de suppression), l'administration du référentiel
  des intervenants (#61, #62), les deux référentiels de formulaire (#64, #65) et le répertoire des
  utilisateurs internes (#66, Q-69).

- **M16 (7)** — corrections issues de l'intégration frontend : `GET /dossiers/{id}/seuil-derogation`
  (Q-74), `PATCH /audiences/{id}/annulation` (Q-76), `PATCH /alarmes/{id}/traiter` (Q-77),
  `PUT /deliberes/{id}/resultat` et `GET /deliberes/{id}/prorogations` (Q-78),
  `GET /frais/demandes/{id}` (Q-82), `GET /dossiers/{id}/decisions` (Q-88).

Soit **74 endpoints HTTP exposés au total** : les 66 numérotés, `POST /dossiers/{id}/etapes` et les
sept ajouts de M16 — décompte des annotations de mapping du code, le 2026-09-11 (le « 66 » inscrit
ici jusqu'au 2026-09-10 n'avait pas suivi les ajouts non numérotés).

Le squelette de starter hors périmètre (Template/Admin/Staff/Dashboard/Chat, auth JWT maison) a
été intégralement supprimé en M1 (cf. `DECISIONS_AND_OPEN_QUESTIONS.md` R-03/R-04) — l'ancienne
§11 « endpoints actuellement exposés hors périmètre » n'a donc plus d'objet et a été retirée.

---

## 1. Dossiers

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 1 | POST | `/dossiers` | `ROLE_SAISIE` | FR-DOS-01 / US 1.1 | 📋 | ✅ M4 |
| 2 | GET | `/dossiers` | `ROLE_CONSULTATION` | FR-DOS-03 / US 1.3 | 📋 | ✅ M4 · **trié M17** (Q-93) |
| 3 | GET | `/dossiers/{id}` | `ROLE_CONSULTATION` | FR-DOS-03 / US 1.3 | 📋 | ✅ M4 |
| 4 | PUT | `/dossiers/{id}/affectation` | `ROLE_JURISTE`, `ROLE_DJ` | FR-DOS-02 / US 1.2 | 📋 | ✅ M4 |
| 5 | PATCH | `/dossiers/{id}/etapes/{etapeId}/statut` | `ROLE_JURISTE` | FR-DOS-04 / US 1.4 | 📋 | ✅ M4 |
| 6 | POST | `/dossiers/{id}/seuil-derogation` | `ROLE_SAISIE` | RG-DOS-09 | 📋 | ✅ M4 |
| 7 | PUT | `/dossiers/{id}/seuil-derogation/{auditId}` | `ROLE_DJ`, `ROLE_DJA` | RG-DOS-09 | 📋 | ✅ M4 |
| 8 | POST | `/dossiers/{id}/sensibilite/validation` | `ROLE_DJ`, `ROLE_DJA` | RG-DOS-09 / UC-DOS-01 alt. 6-a | 🆕 | ✅ M4 |
| — | POST | `/dossiers/{id}/etapes` | `ROLE_JURISTE` | UC-DOS-04 alt. 3-b | 🆕 | ✅ M4 |

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
| 9 | GET | `/clients` | `ROLE_CONSULTATION` | US 8.6 | 📋 | ✅ M4 · **trié M17** (Q-93) |
| 10 | GET | `/clients/{id}` | `ROLE_CONSULTATION` | US 8.6 | 📋 | ✅ M4 |
| 11 | GET | `/clients/{clientId}/dossiers` | `ROLE_CONSULTATION` | FR-REP-03 / US 8.6 | 📋 | ✅ M4 · **N+1 supprimé M17** (Q-94) |
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
| 56 | GET | `/dossiers/{id}/audiences` | `ROLE_CONSULTATION` | US 1.3 (onglet « Audiences ») | 🆕 | ✅ M15 |
| 57 | GET | `/dossiers/{id}/alarmes` | `ROLE_CONSULTATION` | US 2.3/2.4 (onglet « Alarmes ») | 🆕 | ✅ M15 |
| — | PATCH | `/audiences/{id}/annulation` | `ROLE_JURISTE` | Q-76 (renvoi, date erronée) | 🆕 | ✅ M16 |
| — | PATCH | `/alarmes/{id}/traiter` | `ROLE_JURISTE` | Q-77 | 🆕 | ✅ M16 |

> **Annulation d'audience (M16, Q-76).** Motif obligatoire ; seule une audience `PLANIFIEE`
> s'annule. L'audience annulée sort du calendrier et de ses exports (#15), ne reçoit plus ses
> rappels, ne compte plus comme doublon (#13) et refuse un compte rendu (#14). Un report = annuler,
> puis planifier la nouvelle date. `AudienceResponse` gagne `motifAnnulation` et `dateAnnulation`.
>
> **Traitement d'alarme (M16, Q-77).** Sans corps : une alarme `ACTIVE` passe `TRAITEE` sans être
> remplacée — jusqu'ici, seule la reprogrammation (#17) pouvait la clore.

> **#56/#57 (M15, Q-68)** : aucune lecture des audiences ni des alarmes d'un dossier n'existait —
> elles ne pouvaient être créées puis jamais relues. 404 si le dossier est inconnu ; audiences
> triées par date planifiée décroissante, alarmes par échéance croissante (ordre d'usage de chaque
> écran).

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
| 58 | GET | `/dossiers/{id}/deliberes` | `ROLE_CONSULTATION` | US 1.3 (onglet « Délibérés ») | 🆕 | ✅ M15 |
| — | PUT | `/deliberes/{id}/resultat` | `ROLE_JURISTE` | FR-DEL-01 / US 3.1 (vider le délibéré, Q-78) | 🆕 | ✅ M16 |
| — | GET | `/deliberes/{id}/prorogations` | `ROLE_CONSULTATION` | FR-DEL-04 critère 2 (historique, Q-78) | 🆕 | ✅ M16 |

> **Cycle (M16, Q-78)** — conforme aux sources (US 3.2/3.4, UC-DEL-04/05, RG-DEL-06/07) : mise en
> délibéré sans résultat → prorogations et rabattement pendant l'attente → résultat, qui vide le
> délibéré. `DelibereResponse` porte `etat` (`EN_ATTENTE`/`VIDE`/`RABATTU`, dérivé),
> `nombreProrogations`, `dateRabattement`, `motifRabattement` ; `resultat` et `statutExpedition`
> sont nuls tant que le délibéré n'est pas vidé.
> **#18** : **sans** `resultat`, mise en délibéré — l'étape reste `EN_DELIBERE`, aucune échéance
> admise ; **avec** `resultat`, délibéré déjà rendu, enregistré et vidé d'un même geste
> (`dateEcheanceRecours` requise et postérieure à `dateDeliberee` si DEFAVORABLE/MIXTE, Q-42,
> absente sinon ; étape → `DELIBERE_VIDE`). Un seul délibéré en attente par étape (409).
> **#19** : réponse toujours `201 Created`, corps `{...prorogation, alerte}` — `alerte:true` dès
> que le compteur dépasse le seuil configurable `SEUIL_PROROGATIONS` (Q-17/Q-18). Depuis Q-78 :
> délibéré en attente et étape `EN_DELIBERE` exigés, date future ; elle devient la date annoncée.
> **#20** : depuis Q-78, délibéré en attente et étape `EN_DELIBERE` exigés (le juge rouvre les
> débats avant de statuer) ; le délibéré garde sa date et son motif, l'étape revient `EN_COURS`.
> Ne rouvre jamais l'étape suivante — distinct de l'exercice d'un recours (endpoint générique #5, Q-42).
> **#21** : strictement consultatif (Q-19) ; le suivi de l'échéance est assuré par
> `DelaiRecoursScheduler` (bascule `CLOTURE` + alerte `DELAI_RECOURS`, Q-43).
> **#22** : dernier volet de Q-13, absent de l'OpenAPI formel mais implémenté selon les critères
> Gherkin de l'US 3.6. Depuis Q-78, refusé tant que le délibéré n'est pas vidé.
> **#58 (M15, Q-68)** : symétrique de #56/#57 — 404 si le dossier est inconnu, tri par date
> délibérée décroissante.

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
| 23 | POST | `/frais/demandes` | `ROLE_AVOCAT` | FR-INT-01 / US 4.1 | 📋 | ✅ |
| 24 | PUT | `/frais/demandes/{id}/conformite` | `ROLE_ASSISTANTE` | FR-INT-02 / US 4.2 | 📋 | ✅ |
| 25 | PUT | `/frais/demandes/{id}/opportunite` | `ROLE_DJA` | FR-INT-03 / US 4.4 | 📋 | ✅ |
| 26 | POST | `/frais/demandes/{id}/validation-conjointe` | `ROLE_DJ` **et** `ROLE_DJA` | FR-INT-04 / US 4.5 | 📋 | ✅ |
| 27 | PATCH | `/frais/demandes/{id}/paiement` | `ROLE_ASSISTANTE` | FR-INT-05 / US 4.6 | 📋 | ✅ |
| 28 | GET | `/frais/demandes` | selon profil | UC-INT-02/03/04 (« liste des demandes en attente ») | 🆕 | ✅ |
| — | GET | `/frais/demandes/{id}` | `ROLE_AVOCAT` (les siennes), `ROLE_ASSISTANTE`, `ROLE_DJA`, `ROLE_DJ` | UC-INT-02 étape 3 (détail, Q-82) | 🆕 | ✅ M16 |

> **#28** : les UC-INT-02/03/04 supposent tous une **file de travail** (« le système affiche la liste
> des demandes validées en attente de paiement », « aucune demande en attente »). Implémenté en M10
> — `GET /frais/demandes?statut=`, filtré sur les propres demandes pour `ROLE_AVOCAT`, transverse
> pour le personnel interne. Cf. Q-24. Depuis Q-82, triée de la plus récente à la plus ancienne.
>
> **Réponse enrichie (M16, Q-82)** : `DemandeFraisResponse` porte `referenceDossier`, `avocatNom`,
> `dossierSensible`, `seuilApplicable`, `validationConjointeRequise` (RG-INT-03 appliquée) et
> `validations` (étape, décision, motif, auteur nommé, date) — sur toutes les réponses du module.
> **Détail** `GET /frais/demandes/{id}` : 403 pour un avocat sur la demande d'un confrère.
>
> **Relance CONF04 (M16, Q-83)** : `FraisRelanceScheduler`, horaire — une validation conjointe
> restée sans suite `DELAI_VALIDATION_CONJOINTE` jours (5 par défaut) après l'accord d'opportunité,
> puis après chaque relance, relance le DJ et/ou la DJA qui n'ont pas encore voté (alerte
> `VALIDATION_CONJOINTE_REQUISE`). Migration `V11__frais_relance_validation_conjointe.sql`.
>
> **Avocat connecté (M16, Q-84)** : reconnu par son identifiant de connexion (`preferred_username`,
> enregistré sur `utilisateur.identifiant`, V12) — c'est ce que le DJ saisit comme `compteKeycloak` —,
> à défaut par son `sub`. Vaut pour #23, #28, le détail et les publications de l'avocat (#29, #31),
> ainsi que pour les alertes qui lui sont destinées.

**Circuit implémenté (M10)** : `FraisService` — dépôt (avocat affecté au dossier, RG-INT-01/Q-54),
anti-doublon de facture (`ERR-006`, unique tous statuts confondus, y compris `REJETEE` — US 4.3),
conformité (Assistante), opportunité (DJA, seuil dossier/global — Q-26), validation conjointe DJ+DJA
si sensible ou montant > seuil, paiement (Assistante). Traçabilité complète via `validation_frais`
(Q-05). 17 tests automatisés dédiés. Voir `DECISIONS_AND_OPEN_QUESTIONS.md` Q-05, Q-24, Q-26, Q-54.

## 6. Publications

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 29 | POST | `/publications/cr-audience` | `ROLE_AVOCAT` | FR-INT-09 / US 5.1 | 📋 | ✅ |
| 30 | PUT | `/publications/{id}/validation` | `ROLE_ASSISTANTE` | FR-INT-09 / US 5.2 | 📋 | ✅ |
| 31 | POST | `/publications/autres` (multipart) | `ROLE_AVOCAT` | FR-INT-09 / US 5.3 | 📋 | ✅ |
| 32 | GET | `/publications/{id}` | `ROLE_CONSULTATION` | US 5.4 | 📋 | ✅ |
| 33 | POST | `/publications/{id}/commentaires` | `ROLE_JURISTE` | FR-INT-09 / US 5.5 | 📋 | ✅ |
| 34 | POST | `/publications/{id}/correspondance` | `ROLE_DJ`, `ROLE_DJA` | RG-INT-07 / US 5.5 | 💡 | ✅ |
| 59 | GET | `/publications?dossierId=&statut=` | `ROLE_ASSISTANTE`, `ROLE_JURISTE`, `ROLE_DJ`, `ROLE_DJA` | UC-INT-09 (« file des publications à valider ») | 🆕 | ✅ M15 |

> **Lecture et instruction (M16, Q-86).** `PublicationResponse` porte `referenceDossier`,
> `avocatNom`, `audienceDate`, `restrictionAccesNom`, `nomFichier` ; le **détail #32** seul ajoute
> `urlTelechargement` (pré-signée, 10 min), `commentaires` (auteur nommé) et `correspondance`.
> **#32** : l'Assistante lit une publication `DEPOSE`/`REJETE` (404 pour les autres profils) ; la
> restriction d'accès ne s'oppose ni à l'Assistante ni au DJ/DJA. **#59** : `statut=DEPOSE` en ordre
> d'arrivée, sinon la plus récente d'abord. **#30** : une validation notifie les juristes du dossier,
> ou le seul destinataire désigné.

> **#30** : chemin généralisé, sans le segment `cr-audience` du contrat — s'applique aux deux
> sous-types de publication (Q-55), aucun second endpoint n'étant prévu pour valider une « autre »
> publication (US 5.3).
>
> ⚠️ L'OpenAPI référence les schémas `Publication`, `CompteRenduAudience` et `AutrePublication`
> via `$ref` mais **ne les définit jamais** dans `components/schemas`. Contrat incomplet (Q-25) —
> nos propres DTOs (`PublicationResponse`, `CommentaireResponse`, `CorrespondanceResponse`) en
> tiennent lieu côté implémentation.
>
> **#59 (M15, Q-68)** : dernier volet de Q-24 pour les publications — `statut=DEPOSE` alimente la
> file de l'Assistante, `dossierId` l'onglet publications d'une fiche dossier. Contrairement à #32,
> cette liste **n'applique pas** la restriction d'accès individuelle (Q-57) : elle sert des profils
> internes chargés d'instruire les publications et n'expose que des métadonnées, le contenu détaillé
> restant soumis au contrôle unitaire.

**Circuit implémenté (M11)** : `PublicationService` — dépôt d'un compte rendu d'audience (avocat,
audience rattachée au dossier — 404 sinon, US 5.1), dépôt d'une autre publication (PIECE/DECISION/
CONCLUSION, fichier stocké via le `MinioStorageService` du module `ged` sans création de `document`
GED — Q-56), validation générique aux deux sous-types (Q-55, motif obligatoire au rejet, notifie
l'avocat), consultation restreinte aux publications `VALIDE` avec restriction d'accès individuelle
optionnelle (403/ERR-009 — Q-57), commentaire d'un juriste transmis au DJ/DJA (jamais directement à
l'avocat, RG-INT-07), correspondance unique DJ/DJA → avocat (409 si déjà transmise — Q-20). 16 tests
automatisés dédiés. Voir `DECISIONS_AND_OPEN_QUESTIONS.md` Q-20, Q-25, Q-55, Q-56, Q-57.

## 7. Constitutions et répertoire

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 35 | POST | `/constitutions/prestataires` | `ROLE_JURISTE` | FR-INT-06 / US 5.6 | 💡 | ✅ M9 |
| 36 | PUT | `/constitutions/prestataires/{id}/validation` | `ROLE_SH` | FR-INT-07 / US 5.7 | 💡 | ✅ M9 |
| 37 | GET | `/repertoire/avocats` | `ROLE_JURISTE`, `ROLE_ASSISTANTE` | FR-INT-08 / US 5.8 | 💡 | ✅ M9 |
| 60 | GET | `/constitutions/prestataires?statut=` | `ROLE_SH`, `ROLE_JURISTE` | UC-INT-07 (« constitutions à signer ») | 🆕 | ✅ M15 |

> **M16 (Q-86, Q-87).** `DemandeConstitutionResponse` porte `referenceDossier`, `prestataireNom`,
> `prestataireType`, `demandeurNom`, `valideParNom`. **#36** : l'avocat constitué est désormais
> réellement notifié — le prestataire, chargé à la demande, était un proxy d'`Intervenant` que
> `instanceof Avocat` ne reconnaissait jamais (`Hibernate.unproxy`).
| 61 | POST | `/intervenants` | `ROLE_DJ`, `ROLE_DJA` | RG-INT-01/02 (administration du référentiel) | 🆕 | ✅ M15 |
| 62 | GET | `/intervenants?type=` | `ROLE_CONSULTATION` | RG-DOS-03 (sélecteurs d'affectation) | 🆕 | ✅ M15 |

> **#35/#36** : `demande_constitution.motif` obligatoire ; l'approbation génère la lettre de
> constitution (PDFBox) et l'attache comme `Document` GED (M8) ; seul un `Avocat` avec compte
> applicatif reçoit une notification de validation (Q-52), un `AutrePrestataire` n'en reçoit
> aucune (pas de compte, pas d'infrastructure email — même limite que Q-37). **#37** :
> `indicateurCharge`/`indicateurPerformance` calculés à la demande (Q-53), triés par charge
> croissante, recherche par `?nom=`.
>
> **#60 (M15, Q-68)** : dernier volet de Q-24 pour les constitutions — `statut=EN_ATTENTE` alimente
> la file de travail du SH ; sans filtre, l'historique complet (plus récentes d'abord).
> **#61/#62 (M15, Q-68)** : **aucun endpoint ne permettait de créer un avocat** — le référentiel ne
> pouvait être alimenté qu'en SQL, alors qu'un avocat est obligatoire à la création d'un dossier
> (`avocatsAffectes` est `@NotEmpty`) et conditionne frais, publications et constitutions. Même
> raisonnement que `POST /clients` (Q-23, M4). Les invariants existants sont portés par le service :
> un `AVOCAT` exige un compte Keycloak **unique** (400 s'il manque, 409 s'il est déjà pris,
> RG-INT-01) et refuse le champ `notification` ; un `AUTRE_PRESTATAIRE` refuse tout compte
> applicatif (RG-INT-02). Aucune modification ni suppression : non demandées par les user stories.

Testé manuellement de bout en bout (curl + JWT Keycloak réels) : répertoire avec charge/performance
corrects et tri croissant, recherche par nom, demande de constitution notifiant les SH, approbation
générant une lettre PDF consultable dans la GED et notifiant l'avocat, rejet exigeant un motif et
notifiant le demandeur, décision déjà prise refusée (409). 11 tests automatisés dédiés. Voir
`DECISIONS_AND_OPEN_QUESTIONS.md` Q-03, Q-28, Q-52, Q-53.

## 8. GED

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 38 | POST | `/ged/documents` | `ROLE_SAISIE` | FR-GED-01 / US 6.1 | 📋 | ✅ M8 |
| 39 | GET | `/ged/documents/{id}` | `ROLE_CONSULTATION` | FR-GED-02 / US 6.3 | 📋 | ✅ M8 |
| 40 | DELETE | `/ged/documents/{id}` | `ROLE_JURISTE` (202) / `ROLE_DJ`,`ROLE_DJA` (200) | FR-GED-03 / US 6.4, 6.5 | 📋 | ✅ M8 |
| 41 | PUT | `/ged/documents/{id}/approbation-suppression` | `ROLE_DJ`, `ROLE_DJA` | FR-GED-03 / US 6.4 | 💡 | ✅ M8 |
| 42 | GET | `/ged/rapport-journalier` | `ROLE_JURISTE`, `ROLE_DJ`, `ROLE_DJA`, `ROLE_ASSISTANTE` (Q-80) | FR-GED-04 / US 6.6 | 📋 | ✅ M8 |
| 43 | GET | `/dossiers/{id}/documents` | `ROLE_CONSULTATION` | UC-GED-02 | 🆕 | ✅ M8 |
| 63 | GET | `/ged/demandes-suppression?statut=` | `ROLE_DJ`, `ROLE_DJA` | UC-GED-04 (« demandes en attente ») | 🆕 | ✅ M15 |

> **M16 (Q-79, Q-80)** : `DemandeSuppressionResponse` (#40 en 202, #41, #63) porte `documentNom`,
> `typeDocument`, `dossierId`, `referenceDossier` — la file dit quoi supprimer, historique compris.
> Une suppression directe (#40 en 200) ferme la demande en attente sur le même document ; elle
> restait sinon indécidable (404) et relancée toutes les 72 h (V10 ferme les orphelines existantes).
> #42 est ouvert au DJ, à la DJA et à l'Assistante (« tout juriste », FR-GED-04 ; cf. Q-81).

> **#38** : format dérivé de l'extension du fichier (pas du `Content-Type` déclaré, Q-46) ;
> `typeDocument` reste un texte libre (Q-45). **#40** : double comportement selon le profil résolu
> côté serveur — juriste auteur du chargement → demande (202) ; DJ/DJA → suppression immédiate
> (200) ; 409 si une demande est déjà en attente (Q-47). **#41** : absent du contrat formel,
> implémenté selon les critères Gherkin de l'US 6.4 ; motif de rejet obligatoire. **#42** :
> `dossiersManipules`/`piecesAjoutees`/`affairesNouvelles`/`planificationActes[]` définis en Q-49
> (dépendance étendue vers le module `audience`). **#43** : UC-GED-02 et US 6.3 supposent la liste
> des documents d'un dossier, absente du contrat qui ne prévoit que la consultation unitaire.
> **#63 (M15, Q-68)** : dernier volet de Q-24 pour la GED — le DJ/DJA devait approuver (#41) des
> demandes qu'aucun endpoint ne lui permettait de lister.

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
| 45 | GET | `/alertes/mes-notifications` | authentifié | FR-ALR-02 / US 7.3 | 📋 | ✅ M5 · **trié M17** (Q-92) |
| 46 | PATCH | `/alertes/{id}/traiter` | destinataire | US 7.4 | 💡 | ✅ M5 |
| 47 | GET | `/configurations` | `ROLE_CONSULTATION` | CONF01-07 | 📋 | ✅ M5 · **enrichi M17** (Q-91) |
| 48 | PUT | `/configurations/{cle}` | `ROLE_DJ` | CONF01-07 | 📋 | ✅ M5 · **validé par clé M17** (Q-91) |
| 49 | GET | `/rapports` | `ROLE_JURISTE` | FR-REP-01 / US 8.4 | 📋 | ✅ |
| 50 | GET | `/tableau-de-bord` | `ROLE_DJ` **et `ROLE_DJA`** | FR-REP-02 / US 8.5 | 📋 | ✅ · **élargi M18** (Q-95) |

> **#44** : `type` doit valoir `CHARGE_MAX_AVOCAT` (400 sinon) — seul type d'alerte du contrat
> adossé à un seuil configurable (RG-ALR-02) ; délègue à `PUT /configurations/{cle}` en interne.
> Le déclenchement lui-même est planifié (`ChargeMaxAvocatScheduler`, module `dossier`, cf. Q-38),
> pas un endpoint. **#46** : rôle « destinataire » vérifié en service (403 sinon), pas par
> `@PreAuthorize` — cf. Q-36. Testé manuellement de bout en bout (déclenchement planifié avec
> anti-doublon, doublon d'affectation M4→M5, traitement par le seul destinataire, push WebSocket
> best-effort) + 15 tests automatisés (`AlerteServiceTest`, `ChargeMaxAvocatSchedulerTest`,
> `ConfigurationServiceTest`). Voir Q-22, Q-32, Q-36, Q-37, Q-38.
>
> **#47/#48 (M17, Q-91)** : chaque clé porte une règle de saisie appliquée dans
> `ConfigurationService.modifier` — donc aussi sur **#44**, qui y délègue. Une valeur incohérente est
> refusée en **400** au lieu de ressortir en 500 depuis un autre module (frais, délibéré, GED) ; une
> valeur valide est **normalisée** avant enregistrement. La règle est publiée dans la réponse
> (`typeValeur`, `valeurMinimale`, `valeurMaximale`, `valeursPossibles`) pour que l'écran
> d'administration construise son champ à partir du contrat. +26 tests (`RegleConfigurationTest`,
> `ConfigurationServiceTest`).
>
> **#45 (M17, Q-92)** : plus récente d'abord (`dateDeclenchement DESC, id DESC`). Sans tri, la
> requête paginée n'avait aucun `ORDER BY` et l'ordre des lignes était laissé à PostgreSQL.
>
> **#49/#50 (M13)** : US 8.6 (vue consolidée par client) était déjà couverte depuis M4 par
> `GET /clients/{clientId}/dossiers` (#11) — seuls le rapport périodique et le tableau de bord
> restaient réellement à construire. `GET /rapports` : FR-REP-01 ne nomme aucun champ de sortie
> (contrairement à FR-REP-02) — indicateurs dérivés de ceux du tableau de bord mais restreints à la
> période demandée (Q-63) ; 204 si les quatre compteurs sont nuls ; formats JSON/PDF/EXCEL/CSV
> (PDFBox + Apache POI, même stack que le calendrier des audiences, Épic 2). `GET /tableau-de-bord` :
> les 6 indicateurs de RG-REP-03, calculés en temps réel (`tauxSuccesRecouvrement`/
> `provisionsReprises` définis en Q-64). 4 tests automatisés (`ReportingServiceTest`) + validation
> manuelle de bout en bout. Voir Q-63, Q-64.

## 10. Décisions définitives

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 51 | POST | `/dossiers/{id}/adjudications` | `ROLE_JURISTE` | FR-DEF-01 / US 8.1 | 💡 | ✅ |
| 52 | POST | `/dossiers/{id}/condamnations` | `ROLE_JURISTE` | FR-DEF-02 / US 8.2 | 💡 | ✅ |
| 53 | PATCH | `/condamnations/{id}/statut` | `ROLE_JURISTE` | FR-DEF-02 / US 8.2 | 💡 | ✅ |
| 54 | POST | `/jurisprudences` (multipart) | `ROLE_JURISTE` | FR-DEF-03 / US 8.3 | 💡 | ✅ |
| 55 | GET | `/jurisprudences` | `ROLE_CONSULTATION` | UC-DEF-03 (« consultable dans la base ») | 🆕 | ✅ |
| — | GET | `/dossiers/{id}/decisions` | `ROLE_CONSULTATION` | US 8.1/8.2 (onglet « Décisions » de la fiche, Q-88) | 🆕 | ✅ M16 |

> **Lecture des décisions (M16, Q-88).** `{adjudications, condamnations}`, chaque liste de la plus
> récente à la plus ancienne ; 404 si le dossier est inconnu — symétrique de #56/#57/#58.
> **#55 (M16, Q-89)** : filtres partiels et insensibles à la casse (un mot-clé est retenu dès qu'un
> mot indexé le contient), paramètre `dossierId` pour les décisions archivées d'un dossier.
> Les accents comptent (pas d'`unaccent`).

**Circuit implémenté (M12)** : `DecisionDefinitiveService` — adjudication (étape `CLOTURE`
requise, RG-DEF-01), condamnation pécuniaire (aucune précondition d'étape, asymétrie assumée —
Q-59), mise à jour du statut de paiement. `JurisprudenceService` — indexation obligatoire
(mots-clés/nature/juridiction), fichier PDF ≤ 10 Mo (littéral, Q-61), `dossierId` optionnel avec
stockage dédié sans dossier (Q-58), recherche paginée par mots-clés/nature/juridiction. Aucune
transition automatique d'étape vers `ARCHIVE` (Q-60, reste manuelle via l'endpoint générique M4).
13 tests automatisés dédiés. Voir `DECISIONS_AND_OPEN_QUESTIONS.md` Q-58, Q-59, Q-60, Q-61.

## 11. Référentiels de formulaire et utilisateurs internes (M15)

| # | Méthode | Endpoint | Rôle requis | FR / US | Src | Statut |
|---|---|---|---|---|---|---|
| 64 | GET | `/referentiels/natures-dossier?categorie=` | `ROLE_CONSULTATION` | RG-DOS-02 / US 1.1 | 🆕 | ✅ M15 |
| 65 | GET | `/referentiels/types-client-sensible` | `ROLE_CONSULTATION` | RG-DOS-09 / US 1.1 | 🆕 | ✅ M15 |
| 66 | GET | `/utilisateurs?profil=&inclureInactifs=` | `ROLE_CONSULTATION` | RG-DOS-03 / US 1.1, 1.2 | 🆕 | ✅ M15 |

> **#64/#65 (M15, Q-68)** : le backend **impose** ces référentiels fermés en écriture — `POST
> /dossiers` rejette en 400 toute nature hors taxonomie (RG-DOS-02) — mais ne les exposait nulle
> part en lecture : le formulaire de création ne pouvait pas peupler ses listes déroulantes.
> Lecture seule : ces référentiels sont administrés par le DJ via la base (CONF02), aucune écriture
> n'est demandée par les user stories. `natures-dossier` est filtrable par catégorie, le formulaire
> s'adaptant à la catégorie choisie ; seules les entrées actives sont retournées.
>
> **#66 (M15, Q-69)** : `POST /dossiers` exige `juristesAffectes` (`@NotEmpty`, des
> `utilisateur.id`) et **aucun endpoint n'exposait la table `utilisateur`** — le point d'entrée de
> toute l'application était donc inconstructible depuis une interface. Pendant de #62 pour le
> personnel interne. Lecture seule : les comptes naissent du provisionnement au premier appel
> authentifié, Keycloak restant la source de vérité des identités (REF-05 §1.3). `keycloakId`
> **n'est pas exposé** ; comptes actifs seuls par défaut ; `ROLE_CONSULTATION` — porté par les six
> profils internes mais **pas par `AVOCAT`**, qui ne doit pas énumérer le personnel interne.

---

## 12. Codes d'erreur du contrat (à implémenter dans le `GlobalExceptionHandler`)

| Code | HTTP | Description | Endpoint | Statut |
|---|---|---|---|---|
| `ERR-001` | 400 | Métadonnées manquantes | `POST /dossiers` | ✅ M4 |
| `ERR-002` | 409 | Référence de dossier déjà utilisée | `POST /dossiers` | ✅ M4 (`champ: reference` depuis M16) |
| `ERR-003` | 409 | Doublon d'affectation | `PUT /dossiers/{id}/affectation` | ✅ M4 (`forcer` — Q-12) |
| `ERR-004` | 400 | Transition non autorisée | `PATCH …/statut` | ✅ M4 |
| `ERR-005` | 409 | Doublon d'audience | `POST /dossiers/{id}/audiences` | ✅ M6 (`forcer` — Q-12) |
| `ERR-006` | 409 | Facture en double | `POST /frais/demandes` | ✅ M10 |
| `ERR-007` | 409 / 422 / 201 ? | Plus de 10 prorogations | `POST /deliberes/{id}/prorogations` — **Q-17** | ✅ M7 (201 + `alerte:true`) |
| `ERR-008` | 400 | Format ou poids du document non conforme | `POST /ged/documents` | ✅ M8 |
| `ERR-009` | 403 | Accès restreint à la publication | `GET /publications/{id}` | ✅ M11 |

Codes génériques : 400 requête invalide · 401 non authentifié · 403 droits insuffisants ·
404 non trouvé · 409 conflit d'unicité · 422 règle métier violée.

**Lecture des demandes de seuil (M16, Q-74).** `GET /dossiers/{id}/seuil-derogation[?statut=]`
(`ROLE_CONSULTATION`) renvoie les `AuditSeuilResponse` du dossier, la plus récente d'abord — seule
source de l'`auditId` qu'exige l'arbitrage. L'arbitrage n'est recevable que sur une sensibilité
validée.

**Champ fautif (M16, Q-70).** `ERR-CONFLICT` est partagé par neuf conflits sans rapport entre eux ;
le code seul ne dit donc pas quel champ corriger. Le corps d'erreur porte pour cela un champ `champ`,
distinct de `details` et **absent du JSON** lorsque l'erreur ne vise aucun champ :

```json
{"code":"ERR-CONFLICT","message":"La référence client 'CLI-001' est déjà utilisée","details":null,"champ":"reference"}
```

Renseigné par `ApiException.conflictSurChamp` sur : référence client, `compteKeycloak` d'un avocat,
`ERR-002` référence de dossier, `type` d'une étape déjà ouverte, `ERR-006` référence de facture. Les
conflits d'**état** (« cette demande a déjà été traitée ») n'en nomment aucun, volontairement.

---

## 13. Hors périmètre V1 (REF-07 §6.2)

| Endpoint | Statut |
|---|---|
| `POST /dossiers/{id}/suppression` (suppression d'un dossier avec workflow) | À étudier — V2 |
| `GET /integrations/corebanking` | Hors périmètre V1 |
| `POST /integrations/comptabilite` | Hors périmètre V1 |
