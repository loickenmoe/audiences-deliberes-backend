# PROJECT_CONTEXT

> Contexte projet persistant — à lire en début de chaque session avant toute intervention.
> Dernière mise à jour : 2026-09-04 (audit initial, aucun code modifié).

---

## 1. Identité du projet

| Élément | Valeur |
|---|---|
| Nom | Digitalisation des Audiences et Délibérés — Direction Juridique |
| Organisation | Afriland First Bank Cameroun — DSI |
| Commanditaire métier | Direction Juridique (DJ) |
| Cadre | Mémoire de fin d'études KFOKAM-48, KENMOE MBEUKEM Loïc Luc |
| Encadreur DSI | DEUMO DEUMO Léonce, Chef de Département Développement |
| Dépôt courant | `audiences-deliberes-backend` — **backend uniquement** |
| Branche de travail | `dev` (branche principale : `main`) |

## 2. Raison d'être

La Direction Juridique suit manuellement ses dossiers contentieux (recouvrement de crédit,
litiges d'exploitation, incidents de compte). Conséquences documentées :

- audiences et **délais de recours manqués** → décisions défavorables devenues définitives,
  créances perdues ;
- **adjudications favorables non remontées** à la comptabilité → provisions immobilisées inutilement ;
- doublons d'attribution, absence de GED, absence de base jurisprudentielle, aucun reporting consolidé.

La plateforme doit **centraliser** le suivi, **automatiser les alertes** sur les échéances critiques,
et produire un **reporting consolidé** du risque crédit résiduel en contentieux.

**Trois piliers fonctionnels** : suivi des audiences · suivi des délibérés · gestion des décisions
devenues définitives.

## 3. Périmètre

### Dans le périmètre (V1)
Dossiers · Étapes de procédure · Audiences · Délibérés (prorogations, rabattements) ·
Décisions définitives (adjudications, condamnations, jurisprudence) · Intervenants
(avocats, demandes de frais, publications, constitutions) · GED · Alertes · Reporting ·
Authentification/RBAC via Keycloak.

### Hors périmètre (explicitement exclu)
- Intégration technique avec **Core Banking**, **comptabilité/DCPO**, **INTRA V2** → saisie manuelle.
- **Paiement effectif** des frais → circuits bancaires existants ; seule la traçabilité du statut est requise.
- **Portail client** (V2).
- **Gestion des mots de passe / credentials** → entièrement déléguée à Keycloak
  (Conception §1.3). Le backend ne stocke aucun credential.
- **Frontend** (Next.js) → travail ultérieur, hors du présent dépôt.
- Déploiement production et intégration au SI → équipe dédiée DSI, phase ultérieure.

## 4. Acteurs et profils

| Profil | Code | Type | Rôle principal |
|---|---|---|---|
| Juriste | `JURISTE` | Interne | Crée/suit les dossiers, planifie audiences, enregistre délibérés, alarmes, GED, sollicite constitutions |
| Directeur Juridique | `DJ` | Interne | Tous droits juriste + **administration** (seuils, taxonomie), validation conjointe frais, suppression directe documents, tableau de bord |
| Directeur Juridique Adjoint | `DJA` | Interne | Tous droits juriste + **contrôle d'opportunité** des frais, validation conjointe, suppression directe |
| Assistante de Direction | `ASSISTANTE` | Interne | Tous droits juriste + **contrôle de conformité** des frais, validation des CR d'avocats, statut de paiement |
| Supérieur Hiérarchique (Chef de département) | `SH` | Interne | **Seconde signature** des lettres de constitution |
| Avocat partenaire | `AVOCAT` | **Externe** | Dépose demandes de frais, dépose CR d'audience et publications, reçoit lettres de constitution |
| Autres prestataires (notaire, huissier, greffier, conservateur foncier) | — | Externe | **Aucun compte.** Notification email uniquement (RG-INT-02) |
| GFC (Gestionnaire de fonds de commerce) | — | Externe | **Aucun accès.** Transmet physiquement le dossier de crédit + PV de transfert |
| Comité de transfert | — | Externe | Hors système ; valide le transfert du dossier au contentieux |

**Point d'entrée réel du système** = la saisie du juriste (UC-DOS-01). Tout ce qui précède est hors système.

## 5. Glossaire

| Sigle | Signification |
|---|---|
| DJ / DJA | Directeur Juridique / Directeur Juridique Adjoint |
| SH | Supérieur Hiérarchique désigné (Chef de département) |
| GFC | Gestionnaire de fonds de commerce |
| GED | Gestion Électronique des Documents |
| DCPO | Direction du Contrôle Permanent des Opérations |
| CCJA | Cour Commune de Justice et d'Arbitrage (OHADA) |
| CJCEMAC | Cour de Justice de la CEMAC |
| Grosse / Expédition | Copie exécutoire d'une décision de justice |
| Rabattement | Décision du juge de rouvrir les débats après mise en délibéré |
| Prorogation | Report de la date de délibéré |
| Adjudication | Attribution d'un bien saisi lors d'une vente judiciaire |
| Soulte | Somme compensatoire versée lors d'un partage/adjudication |
| Reliquat | Solde restant dû après imputation du produit de l'adjudication |
| RG / FR / NF / UC / US | Règle de Gestion / Functional Requirement / Non-Functional / Use Case / User Story |

## 6. Sources documentaires (`Doc/` = source de vérité métier)

| # | Fichier | Version / date | Rôle | Autorité |
|---|---|---|---|---|
| REF-01 | `cahier Audiences et Délibérés_doc1_client.pdf` | validé 22/07/2026 | Expression initiale du besoin client (**PDF scanné, 9 p.**) | Historique — **superseded** par les spécifications |
| REF-02 | `Final_Note_Presentation_Memoire_...docx` | 28/07/2026 | Cadrage stratégique, hypothèses validées, planning | Cadrage |
| REF-03 | `Final_Specifications_Fonctionnelles_...V1_6.docx` | V1.6 | **Exigences FR/NF, règles de gestion RG, taxonomie** | **Référence métier n°1** |
| REF-04 | `Final_Document_Analyse_Final-...V1_3.docx` | V1.3 | UML : acteurs, UC détaillés, RG affinées, états-transitions | **Référence métier n°2** (affine REF-03) |
| REF-05 | `Final_Document_Conception-...V1.0.docx` | V1.0 | Architecture, stack, déploiement, **script SQL** | Référence technique |
| REF-06 | `Final_Dictionnaire des donnees-...V1.0.docx` | V1.0 | **Schéma de données détaillé** (tables, colonnes, FK, index) | Référence données |
| REF-07 | `Final_Contract_API-...V1.0 - Copy.docx` | V1.0 | **Contrat d'API OpenAPI 3.0** | Référence API |
| REF-08 | `Final_User_stories-...V1.0.docx` | V1.0 | 49 US, 8 épics, critères Gherkin, **traçabilité complète** | **Référence de réalisation** |

### Ordre de préséance retenu (à confirmer — cf. `DECISIONS_AND_OPEN_QUESTIONS.md` Q-00)
`REF-08 (US)` ≻ `REF-07 (API)` ≻ `REF-06 (Dictionnaire)` ≻ `REF-05 (Conception)` ≻
`REF-04 (Analyse)` ≻ `REF-03 (Spécifications)` ≻ `REF-02` ≻ `REF-01`
— du plus aval (le plus proche de la réalisation) au plus amont.
**Exception** : pour une *règle métier*, REF-03/REF-04 priment ; pour une *forme technique*
(nom de colonne, verbe HTTP), REF-06/REF-07 priment.

### Ré-extraction du texte des documents
Les `.docx` ne sont pas lisibles directement. Script d'extraction (Python 3, sans dépendance) :
décompresser `word/document.xml` et parcourir `w:p` / `w:tbl`. Un extracteur jetable a été utilisé
lors de l'audit ; le PDF scanné a été lu en décodant les couches image MRC (CCITT G4 → TIFF → PNG).
Les faits utiles sont consolidés dans les fichiers de contexte — **ne pas re-scanner les documents
sauf besoin de vérification ponctuelle.**

## 7. Contraintes non fonctionnelles retenues

| ID | Exigence | Cible |
|---|---|---|
| NF-PERF-01 | Temps de réponse requêtes courantes | < 2 s |
| NF-PERF-02 | Génération d'un rapport | < 10 s |
| NF-DISP-01 | Disponibilité | **contradiction : > 99,5 % (REF-03/04/08) vs 95 % pilote / 98 % prod (REF-05)** — cf. Q-14 |
| NF-SEC-01 | Authentification | OIDC / JWT via Keycloak, RBAC |
| NF-SEC-02 | Chiffrement | TLS 1.2+ ; pas de credentials en base applicative |
| NF-SCAL-01 | Utilisateurs simultanés | > 50 |
| NF-ALR-01/03 | Alertes | délivrance > 99 %, délai < 1 min |
| NF-CONF-01 | RGPD / conservation | à définir avec la DRH |

## 8. Conventions de développement

### Données (REF-06 §1.3)
- Tables : `snake_case`, **pluriel** annoncé mais **le dictionnaire et le SQL utilisent le singulier**
  (`dossier`, `audience`…) → **le singulier fait foi** (usage réel du script SQL).
- Colonnes : `snake_case` · PK : `id` (BIGSERIAL) · FK : `{table}_id`.
- Énumérations : `VARCHAR` + `CHECK`, jamais d'entier.
- `TIMESTAMP` pour l'historique, `DATE` pour les dates métier.

### API (REF-07 §1.3)
- Base locale : `http://localhost:8080/api/v1` — le code est aligné sur ce port depuis M1 (Q-16).
- JSON `camelCase`, dates ISO `YYYY-MM-DD`, UTF-8.
- Auth : `Authorization: Bearer <JWT Keycloak>` sur **tous** les endpoints sauf health checks.
- Erreurs : HTTP standard + corps `{code, message, details}` avec codes `ERR-0xx`.

### Code
- Java 21, Spring Boot 3.3.11, monolithe modulaire.
- Découpage **par module fonctionnel** puis par couche
  (`controller` → `service` → `repository` → `model`), conformément à la classification
  d'Ivar Jacobson retenue en conception (`<<Dialogue>>` / `<<Contrôleur>>` / `<<Entité>>`).
- Messages métier et libellés en **français** ; identifiants techniques en français
  (aligné sur le dictionnaire de données : `Dossier`, `EtapeProcedure`, `DemandeFrais`…).

### Git
- Convention observée : **Conventional Commits** avec scope
  (`feat(config):`, `fix(keycloak):`, `chore(cleanup):`). À conserver.
- Ne jamais committer sans demande explicite.
- **Ne pas ajouter de ligne `Co-Authored-By: Claude ...` aux messages de commit** (préférence
  utilisateur explicite, 2026-09-04) — déroge à la convention par défaut de l'outil.

## 9. État actuel (2026-09-07, après le jalon M13)

- **Documentation** : complète et validée en amont (8 documents).
- **Code métier** : modules **Dossiers + Clients** (M4, Épic 1), **Alertes + Configurations**
  (M5, Épic 7), **Audiences + Alarmes** (M6, Épic 2), **Délibérés** (M7, Épic 3), **GED** (M8,
  Épic 6), **Intervenants** (M9, volet constitutions/répertoire de l'Épic 5), **Frais** (M10,
  Épic 4), **Publications** (M11, volet publications de l'Épic 5), **Décisions définitives**
  (M12, volet adjudications/condamnations/jurisprudence de l'Épic 8) et **Reporting** (M13, volet
  rapports/tableau de bord de l'Épic 8) livrés. **Les 8 épics fonctionnels sont désormais
  implémentés** ; seul M14 (durcissement et vérification finale) reste à faire.
- **Module `reporting`** : ✅ posé au jalon **M13** : constat de périmètre — US 8.6 (vue
  consolidée par client) était déjà couverte depuis M4 (`GET /clients/{clientId}/dossiers`),
  erreur de suivi corrigée à cette occasion ; le périmètre réel de M13 se limitait donc à US
  8.4/8.5. `ReportingService.tableauDeBord()` : 6 indicateurs temps réel (RG-REP-03),
  `tauxSuccesRecouvrement`/`provisionsReprises` définis en Q-64 (non précisés par le contrat).
  `ReportingService.genererRapport(...)` : mêmes indicateurs restreints à une période (FR-REP-01
  ne nomme aucun champ, contenu dérivé du tableau de bord — Q-63), 204 si aucune donnée, export
  JSON/PDF/Excel/CSV (`RapportExportService`, même stack PDFBox/POI que le calendrier des
  audiences). 2 endpoints — **100 % de la surface OpenAPI et « à proposer » du contrat désormais
  implémentés**. Validé de bout en bout (curl + JWT Keycloak réels : tableau de bord cohérent avec
  les données de test M12, rapport avec/sans activité, les 4 formats d'export, restrictions de
  rôle) et par 4 tests automatisés dédiés. Voir `DEVELOPMENT_ROADMAP.md` §M13 et
  `DECISIONS_AND_OPEN_QUESTIONS.md` (Q-63, Q-64).
- **Module `decision`** : ✅ posé au jalon **M12** : hiérarchie `DecisionDefinitive`/
  `Adjudication`/`CondamnationPecuniaire` (`JOINED`, même principe que `Publication`/
  `Intervenant`), entité `Jurisprudence` indépendante. Dépend de `dossier` et `ged`
  (`MinioStorageService`), **pas** de `intervenant` (même raisonnement que Q-54/Q-56).
  `DecisionDefinitiveService` : adjudication (étape `CLOTURE` requise, RG-DEF-01), condamnation
  (aucune précondition d'étape, asymétrie assumée — Q-59), mise à jour du statut de paiement.
  `JurisprudenceService` : indexation obligatoire, PDF ≤ 10 Mo (littéral — Q-61), `dossierId`
  optionnel avec stockage dédié sans dossier (Q-58), recherche paginée par mots-clés/nature/
  juridiction. 5 endpoints (4 « à proposer » + 1 🆕) — 100 % des endpoints « à proposer » du
  contrat désormais implémentés. **Bug corrigé en validation manuelle (Q-62)** :
  `decision_definitive.type VARCHAR(20)` trop étroite pour sa propre contrainte `CHECK`
  (`'CONDAMNATION_PECUNIAIRE'` fait 23 caractères) — toute condamnation échouait en 500 ;
  `V6__decision_definitive_type_largeur.sql` élargit la colonne à `VARCHAR(30)`. Validé de bout en
  bout (curl + JWT Keycloak réels, sur un environnement Docker reconstruit en cours de session
  après redémarrage des conteneurs) et par 13 tests automatisés dédiés. Voir
  `DEVELOPMENT_ROADMAP.md` §M12 et `DECISIONS_AND_OPEN_QUESTIONS.md` (Q-58 à Q-62).
- **Module `publication`** : ✅ posé au jalon **M11** : hiérarchie `Publication`/
  `CompteRenduAudience`/`AutrePublication` (`JOINED`, même principe que `Intervenant`/`Avocat`,
  M2), `CommentairePublication`, `CorrespondanceAvocat` (tables ajoutées en M11 —
  `autre_publication.chemin_stockage` manquant au schéma initial, Q-56 ; aucune table pour les
  commentaires/correspondances, Q-20). Dépend de `dossier`, `audience` et `ged`
  (`MinioStorageService`, réutilisé sans créer de `document` GED — Q-56), **pas** de `intervenant`
  (même raisonnement que Q-54 pour `frais`). `PublicationService` : dépôt d'un compte rendu
  d'audience (audience rattachée au dossier — 404 sinon), dépôt d'une autre publication
  (multipart), validation générique aux deux sous-types (Q-55), consultation restreinte aux
  publications `VALIDE` avec restriction d'accès individuelle (403/ERR-009 — Q-57), commentaire
  d'un juriste transmis au DJ/DJA (jamais l'avocat directement), correspondance unique DJ/DJA →
  avocat (Q-20). 6 endpoints — 100 % du contrat OpenAPI désormais implémenté. Validé de bout en
  bout (curl + JWT Keycloak réels : dépôt CR sur audience d'un autre dossier rejeté, validation
  généralisée aux deux sous-types, consultation avec/sans restriction d'accès, commentaire notifiant
  DJ+DJA, correspondance unique avec doublon refusé) et par 16 tests automatisés dédiés. Voir
  `DEVELOPMENT_ROADMAP.md` §M11 et `DECISIONS_AND_OPEN_QUESTIONS.md` (Q-20, Q-25, Q-55, Q-56, Q-57).
- **Module `frais`** : ✅ posé au jalon **M10** : entités `DemandeFrais`/`ValidationFrais`
  (`@ManyToOne Dossier`/`Avocat` direct, `validation_frais` distinct pour tracer conformité,
  opportunité et les deux accords DJ/DJA de la validation conjointe — Q-05 ; dépend de `dossier`
  et `referentiel`, **pas** de `intervenant` — Q-54), `FraisService` (circuit complet dépôt →
  conformité → opportunité → validation conjointe si sensible/montant > seuil → paiement,
  anti-doublon de facture ERR-006, notification systématique de l'avocat sur tout rejet). 6
  endpoints (5 du contrat formel + 1 🆕 `GET /frais/demandes`, Q-24). Validé de bout en bout (curl +
  JWT Keycloak réels : dépôt/doublon de facture, conformité, opportunité sensible/non sensible,
  validation conjointe avec double-vote refusé et refus unique, rejets avec notification de
  l'avocat, paiement, liste filtrée par profil) et par 17 tests automatisés dédiés. Voir
  `DEVELOPMENT_ROADMAP.md` §M10 et `DECISIONS_AND_OPEN_QUESTIONS.md` (Q-05, Q-24, Q-26, Q-54, R-07).
- **Module `intervenant`** : ✅ posé au jalon **M9** : entité `DemandeConstitution` (`@ManyToOne
  Dossier`/`Intervenant` polymorphe/`Document` direct — dépend de `dossier`, `delibere` et `ged`
  en plus de `referentiel`/`alerte`, cf. Q-53), `ConstitutionService` (demande → SH, approbation
  générant la lettre via PDFBox et l'attachant comme `Document` GED, notification de l'avocat s'il
  a un compte, rejet avec motif obligatoire), `RepertoireAvocatsService` (indicateurs de charge et
  de performance calculés à la demande, triés par charge croissante, recherche par nom). 3
  endpoints du contrat (#35-37, tous hors OpenAPI formel). Validé de bout en bout (curl + JWT
  Keycloak réels : répertoire correct, circuit de constitution complet avec lettre PDF vérifiée
  dans la GED, rejet et décision déjà prise) et par 11 tests automatisés dédiés. Voir
  `DEVELOPMENT_ROADMAP.md` §M9 et `DECISIONS_AND_OPEN_QUESTIONS.md` (Q-03, Q-28, Q-52, Q-53).
- **Module `ged`** : ✅ posé au jalon **M8** : entités `Document`/`DemandeSuppression`
  (`@ManyToOne Dossier` direct — module dépendant de `dossier`/`audience`, même principe que
  `delibere` en M7), `MinioConfig`/`MinioStorageService` (bootstrap du bucket, dépôt, URL
  pré-signée, suppression), `GedService` (dépôt avec validation format/poids, consultation,
  double comportement de suppression DJ/DJA vs juriste auteur, approbation/rejet, rapport
  journalier), `SuppressionRelanceScheduler` (relance à 72 h, placé dans `ged` — jamais dans
  `alerte`). 6 endpoints du contrat (#38-43). Deux bugs détectés et corrigés en validation
  manuelle (référence circulaire au bootstrap MinIO, conflit de gestionnaire d'exception pour les
  fichiers volumineux). Validé de bout en bout (curl + JWT Keycloak réels : rejets format/poids,
  circuit de suppression complet, relance à 72 h en conditions réelles, rapport journalier) et par
  16 tests automatisés dédiés. Voir `DEVELOPMENT_ROADMAP.md` §M8 et
  `DECISIONS_AND_OPEN_QUESTIONS.md` (Q-02, Q-45 à Q-51).
- **Module `delibere`** : ✅ posé au jalon **M7** : entités `Delibere`/`Prorogation`
  (`@ManyToOne` directe vers `Dossier`/`EtapeProcedure` — module dépendant de `dossier`/
  `audience`, même principe que `audience` en M6), `DelibereService` (enregistrement avec
  transition `EN_DELIBERE→DELIBERE_VIDE`, prorogations avec alerte au seuil, rabattement sans
  réouverture de l'étape suivante, consultation du recours, levée d'expédition),
  `DelaiRecoursScheduler` (bascule automatique `CLOTURE` + alerte `DELAI_RECOURS`, placé dans le
  module `delibere` — jamais dans `alerte`). 5 endpoints du contrat (#18-22, dont le dernier volet
  de Q-13). Validé de bout en bout (curl + JWT Keycloak réels, seuil de prorogation franchi une
  seule fois vers DJ et DJA, cycle rabattement→nouveau délibéré, bascule `CLOTURE` observée en
  conditions réelles) et par 15 tests automatisés dédiés. Voir `DEVELOPMENT_ROADMAP.md` §M7 et
  `DECISIONS_AND_OPEN_QUESTIONS.md` (Q-13, Q-17, Q-18, Q-19, Q-35, Q-42, Q-43, Q-44).
- **Module `audience`** : ✅ posé au jalon **M6** : entités `Audience`/`Alarme` (`@ManyToOne`
  directe vers `Dossier`/`EtapeProcedure` — module dépendant de `dossier`, contrairement à
  `alerte`), `AudienceService` (planification avec doublon confirmable — Q-12, compte rendu),
  `AlarmeService` (création, reprogrammation en chaîne), `RappelAudienceScheduler` (rappels
  J-7/J-3/J-1), `AlarmeEcheanceScheduler` (alerte à l'échéance), `CalendrierExportService`
  (JSON/PDF/Excel). 5 endpoints du contrat (#13-17). Validé de bout en bout (curl + JWT Keycloak
  réels, déclenchements planifiés observés avec anti-doublon, export testé dans les 3 formats) et
  par 24 tests automatisés dédiés. Voir `DEVELOPMENT_ROADMAP.md` §M6 et
  `DECISIONS_AND_OPEN_QUESTIONS.md` (Q-04, Q-12, Q-13, Q-39, Q-40, Q-41).
- **Module `alerte`** : ✅ posé au jalon **M5** : entité `Alerte` (référence `dossierId` libre,
  sans dépendance vers `dossier` — même principe que `HistoriqueAction`), `AlerteService`
  (déclenchement, consultation, traitement, anti-doublon, push WebSocket best-effort avec repli
  garanti sur `GET /alertes/mes-notifications`), `ChargeMaxAvocatScheduler` (placé dans le module
  `dossier`, qui dépend de `alerte` — jamais l'inverse), `ConfigurationService`/`Controller`
  (CONF01-07). 5 endpoints du contrat (#44-48). Validé de bout en bout (curl + JWT Keycloak réels,
  déclenchement planifié observé avec anti-doublon) et par 15 tests automatisés dédiés. Voir
  `DEVELOPMENT_ROADMAP.md` §M5 et `DECISIONS_AND_OPEN_QUESTIONS.md` (Q-22, Q-32, Q-36, Q-37, Q-38).
- **Module `dossier`** : ✅ posé au jalon **M4** (commit `955aa4c`, PR #2 mergée dans `dev`) :
  entités (`Dossier`, `EtapeProcedure`, affectations juriste/avocat, `AuditSeuilDossier`),
  machine à états des étapes (`EtapeTransitionValidator`), `DossierService`
  (création/consultation/affectation/transitions/circuit de sensibilité et de dérogation de
  seuil), `DossierController` (9 endpoints) + `ClientController` (4 endpoints, dont `POST
  /clients` 🆕). Validé de bout en bout (curl + JWT Keycloak réels) et par 31 tests automatisés
  dédiés. Voir `DEVELOPMENT_ROADMAP.md` §M4 et `DECISIONS_AND_OPEN_QUESTIONS.md`
  (Q-01, Q-10, Q-11, Q-12, Q-23, Q-26, Q-34, Q-35) pour le détail des arbitrages.
- **Socle technique** : ✅ assaini au jalon **M1** (commits `3025c46` / `e33f668`) :
  - squelette générique hors domaine et authentification héritée (HS256 maison) supprimés ;
  - authentification exclusivement via Keycloak (OAuth2 Resource Server), y compris WebSocket ;
  - infrastructure 100 % locale (PostgreSQL + MinIO conteneurisés, plus aucune dépendance cloud) ;
  - ports alignés sur le contrat d'API (backend 8080, Keycloak 8081) ;
  - `ROLE_SAISIE` ajouté comme rôle composite Keycloak (Q-08) ;
  - `pom.xml` nettoyé (doublons, licence AGPL retirée, MapStruct câblé).
- **Base de données** : ✅ posée au jalon **M2** (commit `ceb0e77`) : Flyway,
  `ddl-auto=validate`, 32 tables (`src/main/resources/db/migration/V1__schema_initial.sql`),
  données de référence (`V2__donnees_reference.sql`). Validé de bout en bout contre PostgreSQL 17
  réel — migrations idempotentes, correspondance Hibernate/schéma confirmée.
- **Module `referentiel`** : entités JPA + repositories pour `Profil`, `Utilisateur`, `Client`,
  `ConfigurationSysteme`, `TypeClientSensible`, `NatureDossier`, `Intervenant`/`Avocat`/
  `AutrePrestataire` ; `ClientService`/`Controller` (M4) et `ConfigurationService`/`Controller`
  (M5) pour les parties exposées à ce jour.
- **Socle transversal** : ✅ posé au jalon **M3** (commit `57e75c3`) : `common/error`
  (`ApiException`, `GlobalExceptionHandler`, format `{code, message, details}` uniforme y
  compris au niveau du filtre de sécurité), `common/security/CurrentUserService` (provisioning
  utilisateur depuis Keycloak), `common/audit/HistoriqueActionService`,
  `common/web/PageResponse<T>`, `common/config/SchedulingConfig` (dont l'usage `@Scheduled`
  effectif depuis M5, `ChargeMaxAvocatScheduler`, étendu en M6 avec `RappelAudienceScheduler`/
  `AlarmeEcheanceScheduler`, en M7 avec `DelaiRecoursScheduler`, puis en M8 avec
  `SuppressionRelanceScheduler`). Consommé par les modules `dossier` (M4), `alerte` (M5),
  `audience` (M6), `delibere` (M7), `ged` (M8), `intervenant` (M9), `frais` (M10),
  `publication` (M11), `decision` (M12) et `reporting` (M13, lecture seule).
- **Tests** : **179 tests au total après M13** (20 socle M1-M3 + 31 M4 + 15 M5 + 24 M6 + 14 M7 +
  16 M8 + 11 M9 (`ConstitutionServiceTest`, `RepertoireAvocatsServiceTest`,
  `LettreConstitutionServiceTest`) + 17 M10 (`FraisServiceTest`) + 16 M11
  (`PublicationServiceTest`) + 13 M12 (`DecisionDefinitiveServiceTest`,
  `JurisprudenceServiceTest`) + 4 nouveaux M13 (`ReportingServiceTest`)), aucune régression.
  `contextLoads()` intermittent dans cet environnement de session (R-07, pré-existant, sans lien
  avec le code applicatif) — vert lors de la dernière exécution complète de M13.
- **Risques** : voir `DECISIONS_AND_OPEN_QUESTIONS.md` §Risques — R-01 (secrets exposés)
  résolu dans les fichiers versionnés, mais la rotation des secrets déjà exposés côté
  Neon/iDrive e2 reste une action utilisateur en attente ; R-02, R-03, R-04 résolus par M1 ;
  R-05 (schéma non versionné) résolu par M2 ; R-06 (provisionnement d'un utilisateur en première
  connexion incompatible avec un endpoint `@Transactional(readOnly = true)`) découvert en
  validation manuelle M7, non corrigé (défaut du socle M3, hors périmètre de M7-M13) ; R-07
  (démarrage intermittent de l'application dans cet environnement de session,
  `NoClassDefFoundError` sur `AlerteMapperImpl`, reproduit sur `dev` non modifié, sans impact final
  sur M10 à M13 — chaque validation manuelle a pu être menée à bien) découvert en validation
  manuelle M10, non corrigé (indépendant du code applicatif, hors périmètre).

## 10. Où aller ensuite

| Besoin | Fichier |
|---|---|
| Architecture cible, modules, modèle de données, sécurité | `SYSTEM_ARCHITECTURE.md` |
| Exigence → US → UC → RG → entités → endpoints → tests | `FUNCTIONAL_MAP.md` |
| Inventaire des endpoints et état d'implémentation | `API_IMPLEMENTATION_STATUS.md` |
| Contradictions à arbitrer, décisions validées, risques | `DECISIONS_AND_OPEN_QUESTIONS.md` |
| Jalons, ordre d'implémentation, avancement | `DEVELOPMENT_ROADMAP.md` |
