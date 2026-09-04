# FUNCTIONAL_MAP

> Carte fonctionnelle complète : Exigence → User Story → Cas d'utilisation → Règles de gestion →
> Domaine/Données → Module backend → Endpoint(s) → Comportement attendu → Tests.
> Dernière mise à jour : 2026-09-04 (après le jalon M4 — Épic 1 implémenté).

**Légende statut** : ❌ non implémenté · 🟡 partiel · ✅ implémenté et testé
**Épic 1 (Gestion des dossiers) : ✅ toutes les lignes, jalon M4. Épics 2 à 8 : ❌, non démarrés.**

⚠️ Deux systèmes de numérotation des UC coexistent dans la documentation :
REF-03 §8 (`UC-01`…`UC-17`) et REF-04 §3 (`UC-DOS-01`…`UC-REP-03`).
**Ce document utilise la numérotation REF-04** (plus complète et détaillée). Cf. Q-09.

---

## 0. Modules et dépendances

```
                         ┌──────────────────────┐
                         │  common (socle)      │  sécurité, erreurs, historique, pagination
                         │  + referentiel       │  profil, utilisateur, client, configuration
                         └──────────┬───────────┘
                                    │
                         ┌──────────▼───────────┐
                         │      dossier         │  dossier, etape_procedure, affectation
                         └──┬────┬────┬────┬────┘
              ┌─────────────┘    │    │    └──────────────┐
        ┌─────▼─────┐    ┌───────▼──┐ │           ┌───────▼──────┐
        │ audience  │    │ delibere │ │           │ intervenant  │
        └─────┬─────┘    └────┬─────┘ │           └──┬────────┬──┘
              │               │       │              │        │
              │        ┌──────▼─────┐ │        ┌─────▼──┐ ┌───▼─────────┐
              │        │  decision  │ │        │ frais  │ │ publication │
              │        └────────────┘ │        └────────┘ └─────────────┘
              │                       │
              │              ┌────────▼────────┐
              │              │      ged        │
              │              └─────────────────┘
              └──────────────┬──────────────────┬──────────────┐
                     ┌───────▼──────┐   ┌───────▼───────┐
                     │    alerte    │◀──│   reporting   │  (lecture de tous les modules)
                     └──────────────┘   └───────────────┘
```

**Ordre d'implémentation imposé par les dépendances** :
`common + referentiel` → `dossier` → `alerte` (socle) → `audience` → `delibere` →
`ged` → `intervenant` → `frais` → `publication` → `decision` → `reporting`.

`alerte` est placé tôt car **8 modules en dépendent** (FR-ALR-02 est une dépendance de
FR-AUD-01, FR-AUD-03, FR-DEL-02, FR-DEL-04, FR-INT-01, FR-INT-05, FR-GED-01, FR-GED-03).

---

## 1. ÉPIC 1 — Gestion des dossiers (socle)

**Module** : `dossier` · **Dépend de** : `common`, `referentiel`

| FR | US | UC | RG | Entités | Endpoint | Comportement attendu | Statut |
|---|---|---|---|---|---|---|---|
| FR-DOS-01 | 1.1, 1.5, 1.7 | UC-DOS-01 | RG-DOS-01,02,03,07,08,09 | `dossier`, `client`, `etape_procedure`, `historique_action`, **`dossier_juriste`/`dossier_avocat` (Q-01)** | `POST /dossiers` | Valide les champs obligatoires ; référence unique (409/ERR-002) ; `clientId` existant (404) ; champs conditionnels selon `categorie` (Q-11) ; crée l'étape `INSTANCE` au statut `OUVERTURE` ; historise ; **201**. Si `estSensible=true` → circuit de validation DJ/DJA (Q-10) | ✅ |
| FR-DOS-01 | 1.3 | UC-DOS-03 | RG-DOS-06 | idem | `GET /dossiers` (filtres nature/catégorie/juridiction + pagination) | Liste paginée `{content,totalPages,totalElements}` | ✅ |
| FR-DOS-03 | 1.3 | UC-DOS-03 | RG-DOS-06 | + `document`, `historique_action` | `GET /dossiers/{id}` | Métadonnées + étapes avec statuts + historique chronologique + documents, < 2 s ; 404 si absent | ✅ |
| FR-DOS-02 | 1.2, 1.7 | UC-DOS-02 | RG-DOS-03, RG-DOS-07 | `dossier_juriste`, `dossier_avocat`, `historique_action` | `PUT /dossiers/{id}/affectation` | Détecte les doublons → **409/ERR-003 avec possibilité de confirmer** (Q-12) ; historise ancienne + nouvelle affectation | ✅ |
| FR-DOS-04 | 1.4 | UC-DOS-04 | RG-DOS-04, RG-DOS-06 | `etape_procedure`, `historique_action` | `PATCH /dossiers/{id}/etapes/{etapeId}/statut` | Vérifie que la transition est autorisée (400/ERR-004 sinon) ; création directe d'une étape ultérieure → crée les étapes intermédiaires en `OUVERTURE` ; `DELIBERE_VIDE` + recours → ouvre automatiquement `RECOURS_1`/`RECOURS_2` | ✅ |
| — | — | — | RG-DOS-09 | `dossier`, `audit_seuil_dossier` | `POST /dossiers/{id}/seuil-derogation` | Juriste propose un nouveau seuil + motif obligatoire → `EN_ATTENTE` ; 400 si dossier non sensible ; **201** | ✅ |
| — | — | — | RG-DOS-09 | `audit_seuil_dossier` | `PUT /dossiers/{id}/seuil-derogation/{auditId}` | DJ/DJA valide ou rejette (motif obligatoire si rejet) ; si validée → `dossier.seuil_montant` + `seuil_origine='DEROGATION'` | ✅ |
| FR-DOS-01 | 1.6 | toutes | RG-DOS-06 | `historique_action` | *(transversal)* | Chaque opération de modification insère une ligne `{dossier_id, utilisateur_id, action, details JSONB, date_action}` | ✅ |
| FR-REP-03 | 8.6 | UC-REP-03 | RG-REP-04 | `client`, `dossier` | `GET /clients`, `GET /clients/{id}` | Recherche par nom (partielle) / référence, paginée | ✅ |

**Tests attendus** : création nominale, référence en doublon, client inexistant, champs
conditionnels manquants par catégorie, dossier sensible, doublon d'affectation, transition de
statut interdite, création directe de `RECOURS_2`, historisation systématique.

**Écrans frontend impliqués** : *Liste des dossiers* (recherche/filtres) · *Création de dossier*
(formulaire adaptatif Recouvrement/Litiges, section affectation, case « dossier sensible ») ·
*Fiche dossier* (onglets Métadonnées / Étapes / Audiences / Délibérés / Documents / Historique) ·
*Modification des affectations* (modale) · *Validation des dérogations de seuil* (DJ/DJA).

---

## 2. ÉPIC 2 — Suivi des audiences

**Module** : `audience` · **Dépend de** : `dossier`, `alerte`

| FR | US | UC | RG | Entités | Endpoint | Comportement attendu | Statut |
|---|---|---|---|---|---|---|---|
| FR-AUD-01 | 2.1, 2.6 | UC-AUD-01 | RG-AUD-01,03,06,07 | `audience`, `alerte` | `POST /dossiers/{id}/audiences` | Étape au statut `EN_COURS` ; date future (400 sinon) ; crée l'audience `PLANIFIEE` ; **programme 3 alertes `RAPPEL_AUDIENCE` à J-7, J-3, J-1** ; doublon (même `etapeId` + `datePlanifiee`) → 409/ERR-005 avec confirmation possible ; **201** | ❌ |
| FR-AUD-02 | 2.2 | UC-AUD-02 | RG-AUD-01 | `audience`, `historique_action` | `PUT /audiences/{id}/compte-rendu` | Date passée requise ; compte rendu non vide (400 sinon) ; statut → `TENUE` ; historise | ❌ |
| FR-AUD-03 | 2.3 | UC-AUD-03 | RG-AUD-02, RG-DEL-02, RG-DEL-05 | **`alarme` (Q-04)** | `POST /dossiers/{id}/alarmes` | Objet + échéance future + étape ; crée une alarme active rattachée au dossier **et à l'étape** ; **⚠️ endpoint absent de la spécification OpenAPI (Q-13)** | ❌ |
| FR-AUD-04 | 2.4 | UC-AUD-04 | RG-AUD-04 | `alarme`/`alerte` | `PUT /alarmes/{id}/reprogrammer` | Marque l'ancienne `TRAITEE` + `date_traitement` ; crée la nouvelle ; historise ; **⚠️ absent de l'OpenAPI (Q-13)** | ❌ |
| FR-AUD-05 | 2.5 | UC-AUD-05 | RG-AUD-05, RG-REP-01 | `audience`, `dossier`, `etape_procedure` | `GET /audiences/calendrier` | Période HEBDOMADAIRE/MENSUELLE/TRIMESTRIELLE + `dateDebut` ; retourne juridiction, étape, juriste, avocat, unité d'origine, compte client, objet, risque, prochaine date ; export JSON/PDF/EXCEL ; < 5 s | ❌ |

**Tests attendus** : planification nominale + 3 alertes créées, date passée refusée, étape non
`EN_COURS`, doublon d'audience, compte rendu vide, compte rendu sur audience future,
reprogrammation (ancienne `TRAITEE`), calendrier vide, chaque format d'export.

**Écrans frontend** : *Planifier une audience* · *Détail audience / Saisie du compte rendu* ·
*Calendrier des audiences* (vue semaine/mois/trimestre + export) · *Configurer une alarme* ·
*Reprogrammer une action* (depuis une notification).

---

## 3. ÉPIC 3 — Suivi des délibérés

**Module** : `delibere` · **Dépend de** : `dossier`, `audience`, `alerte`, `configuration_systeme`

| FR | US | UC | RG | Entités | Endpoint | Comportement attendu | Statut |
|---|---|---|---|---|---|---|---|
| FR-DEL-01 | 3.1 | UC-DEL-01 | RG-DEL-01, RG-DEL-05 | `delibere`, `etape_procedure` | `POST /dossiers/{id}/deliberes` | Étape `EN_DELIBERE` requise (400 sinon) ; `resultat` ∈ {FAVORABLE, DEFAVORABLE, MIXTE} ; **étape → `DELIBERE_VIDE`** ; historise ; 201 | ❌ |
| FR-DEL-04 | 3.2, 3.3 | UC-DEL-04 | RG-DEL-06, RG-ALR-01 | `prorogation`, `alerte`, `configuration_systeme` | `POST /deliberes/{id}/prorogations` | Nombre **illimité**, jamais écrasé ; incrémente le compteur ; **l'étape reste `EN_DELIBERE`** ; au franchissement du seuil (`SEUIL_PROROGATIONS`, défaut 10) → alerte `PROROGATION` vers **DJ et DJA**, une seule fois ; **⚠️ code de retour contradictoire : 201 vs 409 vs 422 (Q-17) ; seuil atteint à 10 ou à 11 (Q-18)** | ❌ |
| FR-DEL-05 | 3.4 | UC-DEL-05 | RG-DEL-07 | `etape_procedure`, `historique_action` | `POST /deliberes/{id}/rabattement` | Date + **motif obligatoire** (400 sinon) ; étape → `EN_COURS` ; historique du parcours conservé ; nouvelles audiences planifiables ; l'étape peut repasser n fois en `EN_DELIBERE` | ❌ |
| FR-DEL-02 | 3.5 | UC-DEL-02 | RG-DEL-02, RG-DEL-04 | `alerte`, `etape_procedure` | `GET /deliberes/{id}/recours` | Consulte l'échéance + état d'alerte. **⚠️ Les US demandent une *création* d'alerte via un GET — incohérent REST (Q-19).** À l'échéance sans recours : étape → `CLOTURE`, bascule décisions définitives, notification | ❌ |
| FR-DEL-03 | 3.6 | UC-DEL-03 | RG-DEL-03 | `delibere` | `PUT /deliberes/{id}/expedition` | `statutExpedition` ∈ {A_LEVER, LEVEE} ; historise ; **⚠️ absent de l'OpenAPI (Q-13)** | ❌ |

**Tests attendus** : délibéré sur étape non `EN_DELIBERE`, résultat invalide, transition
`DELIBERE_VIDE`, 10 puis 11 prorogations (déclenchement unique de l'alerte), seuil modifié par le
DJ pris en compte, rabattement sans motif, cycle `EN_DELIBERE`→`EN_COURS`→`EN_DELIBERE`,
expiration du délai de recours (test du planificateur).

**Écrans frontend** : *Enregistrer un délibéré* · *Proroger* (avec historique des prorogations) ·
*Rabattre* · *Suivi du délai de recours* · *Gestion de l'expédition (grosse)*.

---

## 4. ÉPIC 4 — Intervenants : demandes de frais

**Module** : `frais` · **Dépend de** : `dossier`, `intervenant`, `alerte`, `configuration_systeme`

| FR | US | UC | RG | Entités | Endpoint | Comportement attendu | Statut |
|---|---|---|---|---|---|---|---|
| FR-INT-01 | 4.1, 4.3 | UC-INT-01 | RG-INT-01,03,05 | `demande_frais`, `alerte` | `POST /frais/demandes` | `ROLE_AVOCAT` ; avocat **constitué sur le dossier** ; `piecesJustificatives` non vide (400) ; `referenceFacture` unique (409/ERR-006) ; statut `DEPOSEE` ; notifie l'Assistante < 5 s ; 201 | ❌ |
| FR-INT-02 | 4.2, 4.3 | UC-INT-03 | RG-INT-03, RG-INT-05 | `demande_frais`, **`validation_frais` (Q-05)** | `PUT /frais/demandes/{id}/conformite` | `ROLE_ASSISTANTE` ; statut `DEPOSEE` requis ; conforme → `CONFORMITE` + notifie le DJA ; rejet → motif obligatoire + `REJETEE` + notifie l'avocat ; facture en double → blocage 409 + motif automatique « Facture en double » | ❌ |
| FR-INT-03 | 4.4 | UC-INT-02 | RG-INT-03 | idem | `PUT /frais/demandes/{id}/opportunite` | `ROLE_DJA` ; statut `CONFORMITE` requis ; évalue `(estSensible = true) OR (montant > seuilMontant)` → si faux `VALIDEE`, si vrai `OPPORTUNITE` + notifie DJ **et** DJA ; rejet → motif obligatoire | ❌ |
| FR-INT-04 | 4.5 | UC-INT-02 alt. | RG-INT-03 | idem | `POST /frais/demandes/{id}/validation-conjointe` | `ROLE_DJ` **ou** `ROLE_DJA` ; enregistre l'accord individuel ; **les deux accords requis** pour `VALIDEE` ; un seul refus → `REJETEE` | ❌ |
| FR-INT-05 | 4.6 | UC-INT-04 | RG-INT-04 | `demande_frais`, `alerte` | `PATCH /frais/demandes/{id}/paiement` | `ROLE_ASSISTANTE` ; statut `VALIDEE` requis (400 sinon) ; `statutPaiement = PAYEE` ; notifie l'avocat **et** DJ/DJA < 5 s | ❌ |

**Seuil applicable** : `dossier.seuil_montant` si `seuil_origine='DEROGATION'`, sinon
`configuration_systeme['SEUIL_MONTANT_DEFAUT']` (50 000 000 FCFA).

**Tests attendus** : dépôt sans pièces, facture en double (y compris sur une demande déjà rejetée
— US 4.3), rejet sans motif à chaque étape, transitions de statut illégales, cas sensible
déclenchant la double validation, cas non sensible sous seuil (DJA valide seul), montant
strictement supérieur au seuil, un seul des deux accords, paiement d'une demande non validée,
chaque rôle non autorisé → 403.

**Écrans frontend** : *(Avocat)* Mes dossiers → Déposer une demande de frais, Suivi de mes demandes ·
*(Assistante)* File des demandes à contrôler, Contrôle de conformité, Demandes validées à payer ·
*(DJA)* File des demandes à arbitrer, Contrôle d'opportunité · *(DJ)* Validations conjointes en attente.

---

## 5. ÉPIC 5 — Intervenants : publications et constitutions

**Modules** : `publication`, `intervenant` · **Dépend de** : `dossier`, `audience`, `alerte`

| FR | US | UC | RG | Entités | Endpoint | Comportement attendu | Statut |
|---|---|---|---|---|---|---|---|
| FR-INT-09 | 5.1 | UC-INT-09 | RG-INT-09 | `publication`, `compte_rendu_audience` | `POST /publications/cr-audience` | `ROLE_AVOCAT` ; audience **rattachée au dossier** (404 sinon) ; statut `DEPOSE` ; notifie l'Assistante < 5 s ; 201 | ❌ |
| FR-INT-09 | 5.2 | UC-INT-10 | RG-INT-10, RG-INT-06 | `publication` | `PUT /publications/cr-audience/{id}/validation` | `ROLE_ASSISTANTE` ; `VALIDE` → consultable par les juristes du dossier ; `REJETE` + motif → renvoyé à l'avocat, **non consultable** | ❌ |
| FR-INT-09 | 5.3 | UC-INT-09 | RG-INT-06 | `publication`, `autre_publication` | `POST /publications/autres` | `typeDocument` ∈ {PIECE, DECISION, CONCLUSION} ; statut `DEPOSE` ; notifie l'Assistante | ❌ |
| — | 5.4 | UC-INT-08 | RG-INT-06 | `publication` | `GET /publications/{id}` | Consultable si `VALIDE` ; `restriction_acces` non nulle et ≠ utilisateur → **403/ERR-009** ; `REJETE` → 404 | ❌ |
| — | 5.5 | UC-INT-08 | RG-INT-07 | `publication`, `historique_action` | `POST /publications/{id}/commentaires` | Un juriste **ne commente jamais directement** : le commentaire est transmis au DJ/DJA. Le DJ/DJA transmet une **correspondance unique** à l'avocat (`POST /publications/{id}/correspondance` — **à proposer, Q-20**) | ❌ |
| FR-INT-06 | 5.6 | UC-INT-05 | RG-INT-08 | **`demande_constitution` (Q-03)**, `alerte` | `POST /constitutions/prestataires` **(à proposer)** | Juriste ; `dossierId` + `prestataireId` + motif obligatoire ; statut `EN_ATTENTE` ; notifie le SH | ❌ |
| FR-INT-07 | 5.7 | UC-INT-06 | RG-INT-08 | `demande_constitution`, `document`, `alerte` | `PUT /constitutions/prestataires/{id}/validation` **(à proposer)** | `ROLE_SH` ; validation → **génère la lettre de constitution** ; avocat notifié via son compte, autre prestataire par email ; rejet + motif → retour au juriste | ❌ |
| FR-INT-08 | 5.8 | UC-INT-07 | RG-INT-01 | `avocat`, `intervenant`, `dossier` | `GET /repertoire/avocats` **(à proposer)** | Coordonnées + `indicateurCharge` (dossiers actifs) + `indicateurPerformance` (taux de succès), triés par charge croissante ; recherche par nom ; < 2 s | ❌ |

**Tests attendus** : dépôt de CR sur une audience d'un autre dossier, publication non validée
invisible, restriction d'accès respectée, commentaire de juriste **non** transmis directement à
l'avocat, constitution sans motif, validation SH générant la lettre, calcul des indicateurs de charge.

**Écrans frontend** : *(Avocat)* Déposer un compte rendu / une pièce, Mes publications, Mes lettres
de constitution · *(Assistante)* File des publications à valider · *(Juriste)* Publications du
dossier, Commenter (→ DJ/DJA) · *(DJ/DJA)* Correspondances à transmettre · *(Juriste/Assistante)*
Répertoire des avocats · *(SH)* Constitutions à signer.

---

## 6. ÉPIC 6 — Gestion documentaire (GED)

**Module** : `ged` · **Dépend de** : `dossier`, `alerte`, MinIO

| FR | US | UC | RG | Entités | Endpoint | Comportement attendu | Statut |
|---|---|---|---|---|---|---|---|
| FR-GED-01 | 6.1, 6.2 | UC-GED-01 | RG-GED-02,04,06,08 | `document`, `alerte` | `POST /ged/documents` (multipart) | Rôles : Juriste, Assistante, SH, DJ, DJA ; **format ∈ {PDF, PNG, JPG, TXT, XLSX}** et **poids ≤ 10 Mo** (400/ERR-008 sinon) ; stocke dans MinIO ; `estSupprime=false` ; **notifie les juristes cotés au dossier** (`DOCUMENT_CHARGE`) < 5 s ; ≤ 3 s ; 201 | ❌ |
| FR-GED-02 | 6.3 | UC-GED-02 | RG-GED-03 | `document` | `GET /ged/documents/{id}` | 404 si `estSupprime=true` ; récupère depuis MinIO (URL pré-signée) ; < 3 s | ❌ |
| FR-GED-03 | 6.4 | UC-GED-03 | RG-GED-05, RG-GED-09 | **`demande_suppression` (Q-02)**, `alerte` | `DELETE /ged/documents/{id}` | Juriste **auteur du chargement** → crée une demande, notifie DJ **et** DJA, **202** ; document intact. **Relance automatique à 72 h** (UC-GED-03 alt. 6-a) | ❌ |
| FR-GED-03 | 6.4 | UC-GED-03 | RG-GED-09 | `demande_suppression`, `document` | `PUT /ged/documents/{id}/approbation-suppression` **(à proposer)** | DJ/DJA approuve → `estSupprime=true` + suppression MinIO + notifie le juriste ; rejet + motif → demande annulée | ❌ |
| FR-GED-03 | 6.5 | UC-GED-03 alt. | RG-GED-05 | `document`, `historique_action` | `DELETE /ged/documents/{id}` (DJ/DJA) | **Suppression immédiate sans approbation** ; historise ; notifie l'auteur du chargement ; 200 | ❌ |
| FR-GED-04 | 6.6 | UC-GED-04 | RG-GED-07 | `document`, `dossier`, `historique_action`, `audience` | `GET /ged/rapport-journalier` | `dossiersManipules`, `piecesAjoutees`, `affairesNouvelles`, `planificationActes[]` pour une date ; zéros si aucune donnée ; < 5 s | ❌ |

**Point non tranché** : RG-GED-01 « le dossier de crédit est mis à disposition **manuellement** par
le GFC ». Le GFC n'ayant pas de compte, c'est le **juriste** qui charge ces pièces. Aucune
fonctionnalité système spécifique n'en découle — à confirmer (Q-21).

**Tests attendus** : chaque format autorisé et un format refusé, fichier de 10 Mo puis de 10 Mo + 1 o,
notification des juristes cotés, suppression par un juriste non auteur (403), circuit
demande→approbation, suppression directe DJ/DJA, consultation d'un document supprimé (404),
rapport journalier vide.

**Écrans frontend** : *Documents du dossier* (liste, aperçu, chargement, demande de suppression) ·
*(DJ/DJA)* Demandes de suppression en attente · *Rapport journalier GED*.

---

## 7. ÉPIC 7 — Alertes et notifications

**Module** : `alerte` · **Socle transversal — dépendance de 8 modules**

| FR | US | UC | RG | Entités | Endpoint | Comportement attendu | Statut |
|---|---|---|---|---|---|---|---|
| FR-ALR-01 | 7.1 | UC-ALR-01 | RG-ALR-02 | `configuration_systeme` | `PUT /alertes/seuils` | `ROLE_DJ` ; `type = CHARGE_MAX_AVOCAT`, `seuil` entier positif ; alerte déclenchée quand un avocat atteint le seuil | ❌ |
| — | — | UC-ALR-01 | — | `configuration_systeme` | `GET /configurations`, `PUT /configurations/{cle}` | Lecture : `ROLE_CONSULTATION`. Écriture : `ROLE_DJ`. Clés au minimum : `SEUIL_MONTANT_DEFAUT`, `SEUIL_PROROGATIONS`, `CANAUX_NOTIFICATION_DEFAUT`, `DELAI_VALIDATION_CONJOINTE`, `TAILLE_MAX_GED`, `FORMATS_GED` (CONF01…CONF07) | ❌ |
| FR-ALR-02 | 7.2 | UC-ALR-02 | RG-ALR-01, RG-ALR-03 | `alerte` | *(déclenchement interne : `@Scheduled` + événements)* | Détecte la condition → identifie les destinataires → enregistre `DECLENCHEE` → diffuse (applicatif obligatoire, Outlook et Teams optionnels) **< 1 minute**, délivrance > 99 % | ❌ |
| FR-ALR-02 | 7.3 | UC-ALR-02 | RG-ALR-03 | `alerte` | `GET /alertes/mes-notifications` | Alertes de l'utilisateur, filtre `statut`, paginé | ❌ |
| — | 7.4 | UC-ALR-02 | RG-ALR-01 | `alerte`, `historique_action` | `PATCH /alertes/{id}/traiter` **(à proposer)** | Seul le destinataire (403 sinon) ; `TRAITEE` + `date_traitement` | ❌ |

### Types d'alerte — **contradiction majeure (Q-22)**

| Source | Types énumérés |
|---|---|
| REF-06 / REF-07 (enum `alerte.type`) | `RAPPEL_AUDIENCE`, `DELAI_RECOURS`, `PROROGATION`, `DOUBLON` — **4 seulement** |
| REF-03 / REF-08 (critères d'acceptation) | + `NOUVELLE_DEMANDE_FRAIS`, `NOUVELLE_PUBLICATION`, `DOCUMENT_CHARGE`, `SUPPRESSION_DOCUMENT` |
| REF-04 §10 (besoins non fonctionnels) | + dossier incomplet, frais à payer, délibéré attendu, charge à confier à un avocat, lettre de constitution non envoyée |
| REF-01 (cahier des charges) | + « Dossier incomplet », « Frais à payer », « Délibéré attendu », « Charge avocat » |

→ **L'énumération à implémenter doit être arrêtée avant tout développement du module.**

**Tests attendus** : programmation J-7/J-3/J-1, déclenchement à la date, chaque type d'alerte,
canal applicatif, marquage `TRAITEE` par un non-destinataire (403), seuil configuré par le DJ
pris en compte immédiatement, relance 72 h des demandes de suppression.

**Écrans frontend** : *Mes notifications* (badge, liste, marquer comme traitée) ·
*(DJ)* Configuration des seuils et paramètres · *(DSI/DJ)* Supervision des alertes (REF-05 §11.5).

---

## 8. ÉPIC 8 — Décisions définitives et reporting

**Modules** : `decision`, `reporting` · **Dépend de** : tous

| FR | US | UC | RG | Entités | Endpoint | Comportement attendu | Statut |
|---|---|---|---|---|---|---|---|
| FR-DEF-01 | 8.1 | UC-DEF-01 | RG-DEF-01, RG-DEF-02 | `decision_definitive`, `adjudication` | `POST /dossiers/{id}/adjudications` **(à proposer)** | Étape `CLOTURE` requise ; `beneficiaire`, `statutComptabilisation`, `repriseProvision`, `restitutionSoulte`, `mutation`, `reliquat ≥ 0`, `dateDecision`, `etapeId` ; 201 | ❌ |
| FR-DEF-02 | 8.2 | UC-DEF-02 | RG-DEF-03, RG-DEF-04 | `decision_definitive`, `condamnation_pecuniaire` | `POST /dossiers/{id}/condamnations` + `PATCH /condamnations/{id}/statut` **(à proposer)** | `sens` ∈ {BANQUE_REDEVABLE, TIERS_REDEVABLE} ; `statutPaiement` ∈ {EN_ATTENTE, PAYE, RECOUVREMENT_FORCE} ; mise à jour du statut au fil du suivi | ❌ |
| FR-DEF-03 | 8.3 | UC-DEF-03 | RG-DEF-05 | `jurisprudence`, `document` | `POST /jurisprudences` **(à proposer)** | `typeArchive` ∈ {EXTRAIT_MINUTE, EXPEDITION, GROSSE} ; **indexation obligatoire** (`motsCles`, `natureDecision`, `juridiction`, `dateDecision`) ; fichier PDF ≤ 10 Mo ; `dossierId` optionnel | ❌ |
| FR-REP-01 | 8.4 | UC-REP-01 | RG-REP-01, RG-REP-02 | toutes | `GET /rapports` | `type` ∈ {JOURNALIER…ANNUEL}, `dateDebut`, `dateFin`, `format` ∈ {JSON, PDF, EXCEL, CSV} ; **< 10 s** ; **204** si aucune donnée | ❌ |
| FR-REP-02 | 8.5 | UC-REP-02 | RG-REP-03 | `dossier`, `audience`, `demande_frais`, `delibere`, `adjudication` | `GET /tableau-de-bord` | `ROLE_DJ` ; `nbDossiersActifs`, `nbAudiencesPlanifiees`, `nbDemandesFraisEnAttente`, `nbProrogationsAlerte`, `tauxSuccesRecouvrement`, `provisionsReprises` ; **temps réel**, < 2 s | ❌ |
| FR-REP-03 | 8.6 | UC-REP-03 | RG-REP-04 | `client`, `dossier` | `GET /clients/{clientId}/dossiers` | Regroupe par catégorie `{RECOUVREMENT:[], EXPLOITATION_LITIGES:[]}` ; **indépendant de l'affectation du juriste** ; structure vide si aucun dossier (200) ; 404 si client inexistant | ❌ |

**Tests attendus** : adjudication sur étape non clôturée, reliquat négatif, sens de condamnation
invalide, indexation jurisprudence incomplète, rapport sans données (204), chaque format d'export,
performance < 10 s sur volume représentatif, KPI cohérents avec les modules sources,
vue client sans dossier, client inexistant.

**Écrans frontend** : *Enregistrer une adjudication* · *Suivi des condamnations* ·
*Base jurisprudentielle* (recherche par mots-clés/nature/juridiction) · *Générer un rapport* ·
*Tableau de bord exécutif* (DJ/Direction) · *Vue consolidée par client*.

---

## 9. Parcours utilisateurs de bout en bout (à supporter par le backend)

| # | Parcours | Enchaînement |
|---|---|---|
| P1 | **Ouverture d'un dossier de recouvrement** | *(hors système : GFC → comité de transfert)* → Juriste crée le dossier (référence crédit + PV) → affecte juristes/avocats → charge les pièces en GED → étape `INSTANCE` `OUVERTURE`→`EN_COURS` |
| P2 | **Cycle d'une audience** | Juriste planifie → alertes J-7/J-3/J-1 → juriste reçoit la notification → audience tenue → CR juriste **et/ou** CR avocat (validé par l'Assistante) → étape → `EN_DELIBERE` |
| P3 | **Cycle d'un délibéré** | Étape `EN_DELIBERE` → prorogations successives (alerte DJ/DJA au seuil) **ou** rabattement (retour `EN_COURS`, nouvelles audiences) → délibéré enregistré → `DELIBERE_VIDE` |
| P4 | **Voie de recours** | Délibéré défavorable/mixte → alarme délai de recours → recours exercé (ouvre `RECOURS_1`) **ou** délai expiré → `CLOTURE` + bascule décisions définitives |
| P5 | **Décision devenue définitive** | Recours épuisés → adjudication (bénéficiaire, provision, soulte, reliquat) **ou** condamnation pécuniaire → archivage jurisprudentiel → étape `ARCHIVE` |
| P6 | **Demande de frais** | Avocat dépose → Assistante (conformité + anti-doublon) → DJA (opportunité) → [DJ+DJA si sensible/seuil] → Assistante (paiement) → avocat notifié |
| P7 | **Constitution d'un prestataire** | Juriste sollicite → SH signe → lettre générée → avocat notifié dans l'application / autre prestataire par email |
| P8 | **Suppression d'un document** | Juriste demande → DJ/DJA notifiés (relance 72 h) → approbation → suppression MinIO + notification. DJ/DJA : suppression directe |
| P9 | **Pilotage** | DJ consulte le tableau de bord temps réel → génère des rapports → consulte la vue consolidée d'un client → ajuste les seuils |

---

## 10. Couverture globale

| Épic | Stories | Must | Should | Endpoints | Implémenté |
|---|---|---|---|---|---|
| 1 — Dossiers | 7 | 7 | 0 | 9 (+ 4 clients, + 1 🆕) | **✅ 14** (M4) |
| 2 — Audiences | 6 | 5 | 1 | 5 | 0 |
| 3 — Délibérés | 6 | 5 | 1 | 5 | 0 |
| 4 — Frais | 6 | 5 | 1 | 5 | 0 |
| 5 — Publications & constitutions | 8 | 6 | 2 | 8 | 0 |
| 6 — GED | 6 | 5 | 1 | 5 | 0 |
| 7 — Alertes | 4 | 2 | 2 | 5 | 0 |
| 8 — Décisions & reporting | 6 | 4 | 2 | 8 | 0 |
| **Total** | **49** | **39** | **10** | **~54** | **14** |
