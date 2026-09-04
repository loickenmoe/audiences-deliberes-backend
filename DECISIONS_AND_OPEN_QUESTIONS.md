# DECISIONS_AND_OPEN_QUESTIONS

> Contradictions documentaires, ambiguïtés et lacunes nécessitant un **arbitrage explicite**.
> Aucune de ces questions n'a été tranchée silencieusement.
> Dernière mise à jour : 2026-09-04 (après jalon M1 — voir statuts R-01 à R-05 ci-dessous).

**Convention de lecture de chaque fiche :**
- **A — Ce que la documentation exige** (avec la source exacte)
- **B — Ce que le code implémente** (le cas échéant)
- **C — Recommandation** — *proposition, jamais présentée comme une exigence*

**Priorités** : `P0` bloquant avant le jalon indiqué · `P1` à trancher avant le module concerné ·
`P2` peut attendre.

---

## RISQUES — statut après le jalon M1 (2026-09-04)

### R-01 🟡 Identifiants réels committés dans le dépôt git — partiellement résolu

**Constat.** `env.template` est **versionné** et contient des secrets de production réels :

| Secret | Valeur exposée |
|---|---|
| Mot de passe PostgreSQL Neon | présent en clair |
| Access key + Secret key iDrive e2 (stockage GED) | présents en clair |
| `JWT_SECRET_KEY` | présent en clair |

Présents dans **l'historique git** (commits `6436048 setup code base` et `898b453`). Retirer le
fichier ne suffit pas : l'historique conserve les valeurs.

**Impact.** Accès en lecture/écriture à la base et au bucket de stockage documentaire par
quiconque a accès au dépôt ou à son historique. En contexte bancaire, incident de sécurité.

**C — Recommandation** (à valider) :
1. **Révoquer et faire tourner immédiatement** les trois secrets, côté Neon et iDrive e2 ;
2. remplacer les valeurs de `env.template` par des **placeholders** ; ✅ **fait (M1)**
3. purger l'historique (`git filter-repo` / BFG) **ou**, si le dépôt n'a jamais été poussé
   hors du poste, réécrire l'historique — à confirmer avec la DSI ;
4. signaler l'incident selon la procédure interne AFB.

**Statut (2026-09-04, M1)** : point 2 fait — `env.template`/`.env` ne contiennent plus que des
valeurs locales, aucune fonctionnalité du code ne dépend plus de ces secrets. **Points 1, 3, 4
restent des actions du porteur du projet**, hors du périmètre d'un dépôt de code — les secrets
déjà exposés avant M1 doivent être considérés compromis tant qu'ils n'ont pas été révoqués.

**Décision attendue :** confirmation de la rotation effective + choix sur la purge d'historique.

---

### R-02 ✅ Base de données et stockage hébergés hors du périmètre local prévu — résolu (M1)

**A.** REF-02 §5.2 : « le développement et les tests sont conduits **intégralement en environnement
local** ». REF-05 §8.2 : PostgreSQL en réseau de données interne, accessible **uniquement** depuis
le backend ; MinIO en réseau interne, aucune exposition externe.

**B.** `.env` / `env.template` : base applicative sur **Neon Cloud (AWS us-east-2)**, stockage GED
sur **iDrive e2** (`b5l0.or.idrivee2-33.com`). Le PostgreSQL conteneurisé ne sert qu'à Keycloak.
`nginx` (obligatoire selon la mitigation RSK-06) est absent du `docker-compose.yml`.

**Impact.** Des données judiciaires nominatives d'une banque camerounaise transiteraient et
résideraient chez des hébergeurs tiers hors zone, hors de tout cadre validé par la DSI. Contredit
frontalement le zonage réseau et l'hypothèse de conception validée le 28/07/2026.

**C — Recommandation** : basculer la base applicative et le stockage GED sur les conteneurs
locaux du `docker-compose.yml` ; conserver Neon/iDrive uniquement, si nécessaire, pour une
démonstration sur données **fictives**, et le documenter explicitement.

**Statut (2026-09-04, M1)** : fait et validé manuellement de bout en bout — base applicative
`audiences_app` et MinIO conteneurisés en local, plus aucune dépendance à un service cloud.
nginx/TLS restent absents (non requis pour le développement local, à traiter avant un
déploiement pilote/production).

---

### R-03 ✅ Chemin d'authentification parallèle non sécurisé — résolu (M1)

**B.** Le code conserve un dispositif JWT HS256 maison (`JwtService`, `JwtAuthenticationFilter`,
`UserDetailsServiceImpl`, entité `User` avec mot de passe BCrypt) en parallèle du Resource Server
Keycloak. Les logs confirment l'enregistrement du filtre sur `/*`
(`jwtAuthenticationFilter urls=[/*] order=2147483647`). L'endpoint `/ws/**` est en `permitAll`
et `WebSocketAuthInterceptor` **laisse passer les connexions non authentifiées**, tout en validant
des jetons signés avec le secret exposé en R-01.

**A.** REF-05 §1.3 : « le stockage et le hachage des mots de passe sont **entièrement délégués à
Keycloak** ; l'application Spring Boot **n'implémente aucune gestion de credentials** ».

**C — Recommandation** : supprimer `User`, `UserRepository`, `UserService(Impl)`, `JwtService`,
`AuthService`, `AuthController`, `UserDetailsServiceImpl`, `JwtAuthenticationFilter`, les DTO
`auth/`, et les dépendances `jjwt`. Réécrire `WebSocketAuthInterceptor` sur le `JwtDecoder`
Keycloak et **refuser** les connexions anonymes.

**Statut (2026-09-04, M1)** : fait. Tout le chemin hérité supprimé ; `WebSocketAuthInterceptor`
réécrit sur `JwtDecoder`/`JwtAuthenticationConverter` Keycloak, refuse toute trame STOMP sans
session authentifiée. Vérifié en conditions réelles (authentification requise, jetons Keycloak
valides acceptés).

---

### R-04 ✅ `AdminUserController` non protégé — résolu (M1)

`@PreAuthorize("hasRole('ADMIN')")` était **commenté** (l. 16). Tout utilisateur authentifié —
y compris un **avocat externe** — pouvait lister, créer, modifier et supprimer des utilisateurs.
**Statut** : résolu par la suppression du contrôleur (hors domaine, cf. R-03 / jalon M1).

---

### R-05 🔴 Aucun schéma de base versionné — reporté à `M2`

`spring.jpa.hibernate.ddl-auto=update`. REF-05 §12.1 fournit un script SQL complet avec
contraintes `CHECK` et index que Hibernate ne générera pas. **Recommandation** : Flyway +
`ddl-auto=validate`. Le script de REF-05 §12.1 comporte par ailleurs deux défauts à corriger
au moment de la transcription : un `);` surnuméraire après `CREATE TABLE dossier`, et l'absence
de la colonne `document.chemin_stockage` pourtant exigée par le dictionnaire (**Q-07**).

**Statut (2026-09-04)** : non traité en M1 (hors périmètre — aucune entité n'existe encore) ;
objet du jalon **M2 — Schéma de base et référentiels**.

---

## QUESTIONS BLOQUANTES (P0)

### Q-00 — Ordre de préséance entre documents — `P0`
Sept documents se recoupent et se contredisent ponctuellement. Aucun ne se déclare prioritaire.
**C — Recommandation** : `User stories ≻ Contrat d'API ≻ Dictionnaire ≻ Conception ≻ Analyse ≻
Spécifications`, **sauf** pour une règle métier où `Spécifications`/`Analyse` priment sur la forme
technique. → À confirmer, car toutes les autres questions en dépendent.
**Décision attendue :** …

---

### Q-10 — Circuit de validation de la sensibilité d'un dossier : totalement non modélisé — `P0`

**A.** RG-DOS-09 (REF-04, version affinée) et UC-DOS-01 alt. 6-a : le juriste **propose** la
sensibilité ; la proposition est **automatiquement soumise à la validation du DJ ou du DJA**, qui
peut *approuver*, *rejeter avec motif obligatoire* (le dossier est alors enregistré non sensible),
ou *ajuster* le seuil / le type de client avant validation. REF-03 §5.1 le confirme :
« avant que le dossier ne soit définitivement enregistré avec ce statut ».

**Contradiction.** FR-DOS-01 critère 3 décrit au contraire un enregistrement **immédiat** :
« si `estSensible = true`, le système rend `seuilMontant` et `typeClientSensible` obligatoires et
**bloque l'enregistrement** s'ils ne sont pas renseignés ». `POST /dossiers` renvoie `201` sans
état d'attente. **Ni le dictionnaire ni le script SQL ne prévoient de statut de validation de la
sensibilité, ni d'endpoint d'approbation.**

**Impact.** Détermine si `POST /dossiers` crée un dossier immédiatement exploitable ou un dossier
en attente ; conditionne `estSensible`, donc tout le circuit de validation des frais (RG-INT-03).

**Options :**
| | Description | Conséquences |
|---|---|---|
| **A** | Le dossier est créé immédiatement ; `estSensible` reste **proposé** (`sensibilite_statut = EN_ATTENTE`) et n'a d'effet sur le circuit des frais qu'une fois validé | ajouter `sensibilite_statut`, `sensibilite_validee_par`, `sensibilite_motif_rejet` + endpoint #8 ; aucune rupture du flux de création |
| **B** | Le dossier n'est enregistré qu'après validation DJ/DJA (`202 Accepted` sur `POST /dossiers`) | rupture du contrat d'API (201) et de US 1.1 |
| **C** | Pas de circuit de validation : `estSensible` est effectif à la saisie | contredit RG-DOS-09 et UC-DOS-01 — à écarter sauf décision explicite |

**C — Recommandation** : **option A**.
**Décision attendue :** …

---

### Q-26 — Contrainte SQL `chk_seuil_coherence` incompatible avec RG-DOS-09 — `P0`

**A.** REF-05 §12.1 :
```sql
CHECK ((seuil_origine='GLOBAL' AND seuil_montant IS NULL)
    OR (seuil_origine='DEROGATION' AND seuil_montant IS NOT NULL))
```
Donc `seuil_montant` **doit être NULL** tant qu'il n'y a pas de dérogation validée.

**Contradiction directe.** FR-DOS-01 critère 3 et RG-DOS-09 (REF-03) : si `estSensible = true`,
`seuilMontant` **devient obligatoire**. Un dossier sensible sans dérogation ne peut donc satisfaire
les deux règles simultanément. L'exemple 4.1 du contrat d'API tranche implicitement dans l'autre
sens (`estSensible: true`, `seuilMontant: null`, `seuilOrigine: "GLOBAL"`).

**C — Recommandation** : retenir la logique de la conception et du contrat d'API —
`seuil_montant` **null par défaut** (le seuil global `SEUIL_MONTANT_DEFAUT` s'applique), renseigné
**uniquement** en cas de dérogation validée ; considérer que « `seuilMontant` obligatoire »
signifie « **un seuil applicable doit être déterminé** », satisfait par la valeur globale.
Le formulaire pré-remplit le seuil par défaut (UC-DOS-01 alt. 6-a) sans le persister.

**Décision attendue :** …

---

### Q-01 — Tables d'affectation juristes/avocats absentes du modèle — `P0`

**A.** RG-DOS-03 : « un dossier est affecté à **un ou plusieurs juristes** et à **un ou plusieurs
avocats** ». `POST /dossiers` exige `juristesAffectes[]` et `avocatsAffectes[]`.
FR-DOS-02 critère 3 : « l'historique des affectations est conservé ».

**B / lacune.** Le dictionnaire (REF-06) et le script SQL (REF-05 §12.1) ne contiennent **aucune
table d'association**. Le module dossier est inconstruisible en l'état.

**C — Recommandation** : créer `dossier_juriste (dossier_id, utilisateur_id, date_affectation,
affecte_par)` et `dossier_avocat (dossier_id, avocat_id, date_affectation, affecte_par)`,
PK composite, l'historique étant porté par `historique_action`.

**Décision attendue :** …

---

### Q-04 — Entité « alarme » : absente du modèle de données — `P0`

**A.** FR-AUD-03 / UC-AUD-03 / US 2.3 : le juriste configure une alarme libre
(**objet** + **échéance**), rattachée **au dossier et à l'étape**. FR-AUD-04 : l'ancienne alarme
passe à `TRAITEE` avec `date_traitement`.

**B / lacune.** Le dictionnaire ne propose que :
- `dossier.champ_alarme TEXT` — un champ **unique** en texte libre, incapable de porter plusieurs
  alarmes ni une échéance exploitable par un planificateur ;
- la table `alerte` — qui n'a **ni `objet` ni `etape_id`**, et dont le `type` est une énumération
  fermée à 4 valeurs.

US 2.3 propose de créer « une alerte de type `DELAI_RECOURS` », ce qui est **sémantiquement faux**
pour une alarme libre (l'objet est arbitraire).

**Options :**
| | Description |
|---|---|
| **A** | Créer une table `alarme (id, dossier_id, etape_id, objet, date_echeance, statut, date_traitement, alarme_precedente_id, created_by)` ; la génération d'une `alerte` à l'échéance |
| **B** | Étendre `alerte` avec `objet`, `etape_id` et un type `ALARME_LIBRE` |

**C — Recommandation** : **option A** — l'alarme est une *intention de suivi*, l'alerte est une
*notification*. Les endpoints `/dossiers/{id}/alarmes` et `/alarmes/{id}/reprogrammer` du
contrat §2 confirment une ressource distincte.

**Décision attendue :** …

---

### Q-22 — Énumération des types d'alerte : quatre sources divergentes — `P0`

| Source | Types |
|---|---|
| REF-06 / REF-07 (enum `alerte.type`) | `RAPPEL_AUDIENCE`, `DELAI_RECOURS`, `PROROGATION`, `DOUBLON` |
| REF-03 / REF-08 (critères d'acceptation) | + `NOUVELLE_DEMANDE_FRAIS`, `NOUVELLE_PUBLICATION`, `DOCUMENT_CHARGE`, `SUPPRESSION_DOCUMENT` |
| REF-04 §10 | + dossier incomplet, frais à payer, délibéré attendu, charge avocat, lettre de constitution non envoyée |
| REF-01 (cahier des charges) | idem REF-04 |

**Impact.** L'énumération est persistée en base avec une contrainte `CHECK` ; la changer plus tard
impose une migration. Elle conditionne aussi le module `alerte`, dépendance de 8 modules.

**C — Recommandation** : retenir la **liste réunie** (13 types), soit :
`RAPPEL_AUDIENCE`, `DELAI_RECOURS`, `PROROGATION`, `DOUBLON_AFFECTATION`, `DOUBLON_AUDIENCE`,
`DOUBLON_FACTURE`, `NOUVELLE_DEMANDE_FRAIS`, `VALIDATION_CONJOINTE_REQUISE`, `PAIEMENT_EFFECTUE`,
`NOUVELLE_PUBLICATION`, `DOCUMENT_CHARGE`, `SUPPRESSION_DOCUMENT`, `CONSTITUTION_A_SIGNER`,
`CHARGE_MAX_AVOCAT`, `DELIBERE_ATTENDU`, `DOSSIER_INCOMPLET`.
*(Le type unique `DOUBLON` du dictionnaire est trop imprécis pour router une notification.)*

**Décision attendue :** liste définitive à arrêter.

---

### Q-08 ✅ Rôles `ROLE_SAISIE` et `ROLE_CONSULTATION` : modèle RBAC — tranché et appliqué (M1)

**A.** REF-07 §5 attribue `ROLE_SAISIE` (« Juriste, DJ, DJA, Assistante, SH ») à
`POST /dossiers`, `POST /ged/documents`, `DELETE /ged/documents/{id}`,
`POST /dossiers/{id}/seuil-derogation`, et `ROLE_CONSULTATION` aux lectures.
REF-08 §11.6 présente une liste « **exhaustive** » de 7 rôles où **`ROLE_SAISIE` ne figure pas**.

**B.** Le realm Keycloak (`keycloak/audiences-realm.json`) déclare les 7 rôles de REF-08 —
**sans `ROLE_SAISIE`**. Les endpoints du contrat qui l'exigent seraient donc inaccessibles.

**C — Recommandation** : ajouter `ROLE_SAISIE` au realm comme **rôle composite** agrégeant
JURISTE, DJ, DJA, ASSISTANTE, SH — le contrat d'API reste applicable tel quel et les
`@PreAuthorize` restent lisibles. Alternative : remplacer partout `ROLE_SAISIE` par
`hasAnyRole('JURISTE','DJ','DJA','ASSISTANTE','SH')`.

**Statut (2026-09-04, M1)** : recommandation appliquée — `ROLE_SAISIE` ajouté au realm comme
rôle composite. Vérifié en conditions réelles : un jeton émis pour `juriste.test` contient bien
`realm_access.roles: [ROLE_JURISTE, ROLE_SAISIE, ROLE_CONSULTATION]`.

---

### Q-11 — Champs conditionnels par catégorie : `ET` ou `OU` ? et catégorie saisie ou déduite ? — `P0`

**Contradiction 1 — cardinalité.**
- US 1.1 critères 2-3 et US 1.5 : pour `RECOUVREMENT`, `dossierCreditOrigine` **et** `pvTransfert`
  sont obligatoires ; pour `EXPLOITATION_LITIGES`, `incidentCompteReference` **et**
  `elementsJustificatifs` le sont.
- Contrainte SQL `chk_categorie_coherence` (REF-05 §12.1) : `dossier_credit_origine IS NOT NULL`
  **OR** `pv_transfert IS NOT NULL` → un seul des deux suffit.
- `POST /dossiers` (REF-07) : **aucun** de ces champs n'est dans `required` → un dossier
  `RECOUVREMENT` conforme au contrat violerait la contrainte SQL (erreur 500).

**Contradiction 2 — origine de la catégorie.**
- RG-DOS-02 version REF-04 : « la catégorie **est déduite de la nature choisie** ».
- RG-DOS-02 version REF-03 : ne mentionne pas cette déduction.
- `POST /dossiers` : `categorie` est un champ du corps de requête — mais **absent de la liste
  `required`**, alors que `dossier.categorie` est `NOT NULL` en base.

**C — Recommandation** : (a) exiger les **deux** champs par catégorie (US = document de
réalisation) et durcir la contrainte SQL en `AND` ; (b) **déduire** la catégorie de la nature via
un référentiel de taxonomie (Annexe A, CONF02), et exposer `categorie` en lecture seule dans la
réponse.

**Décision attendue :** …

---

### Q-17 / Q-18 — Prorogation : code HTTP et valeur du seuil — `P0`

**Q-17 — Code de retour.** Trois réponses différentes pour le même cas :
| Source | Réponse |
|---|---|
| REF-07 §1.4 | « Prorogation > 10 (alerte, mais pas d'erreur) — **à traiter en 400 ou 422** » |
| REF-07 §3, `POST /deliberes/{id}/prorogations` | réponse **`409`** décrite comme « la prorogation est enregistrée mais une alerte est déclenchée » |
| REF-07 §4.3 (exemple) | « **Réponse 201** (avec alerte) », corps avec `compteur: 11, alerte: true` |
| REF-08 US 3.3 critère 1 | « retourne la prorogation avec un champ `alerte: true` (**201**) » |

Renvoyer `4xx` pour une création **réussie** est incorrect (un client REST traiterait un 409 comme
un échec et pourrait rejouer la requête, créant des doublons).

**C — Recommandation** : **`201 Created`**, corps `{...prorogation, alerte: true, message: "..."}`.
Conserver le code `ERR-007` comme code applicatif informatif, non comme erreur HTTP.

**Q-18 — Seuil : 10 ou 11 ?**
| Source | Déclenchement |
|---|---|
| RG-DEL-06 (REF-03 et REF-04) | « lorsque le compteur **atteint** un seuil configurable (défaut : **10**) » |
| UC-DEL-04 alt. 6-a | « si le compteur **atteint ce seuil** » → à 10 |
| FR-DEL-04 critère 4 | « lorsque le compteur **atteint 11** (au-delà de 10 prorogations) » |
| US 3.3 critère 1 | « a déjà 10 prorogations, j'enregistre la **11e** » → à 11 |

**C — Recommandation** : déclencher **quand le compteur dépasse strictement le seuil**
(`compteur > seuilProrogations`), soit à la 11e avec le seuil par défaut 10 — cohérent avec le
document de réalisation (US) et avec la formulation « **plus de** 10 prorogations » du contrat.
L'alerte est déclenchée **une seule fois** (US 3.3 critère 2).

**Décision attendue :** …

---

## QUESTIONS IMPORTANTES (P1)

### Q-02 — Table `demande_suppression` absente — `P1` (module GED)
FR-GED-03 critère 1 cite explicitement « la table `demande_suppression` », qui **n'existe ni au
dictionnaire ni au script SQL**. UC-GED-03 alt. 6-a ajoute une **relance automatique à 72 h**.
**C** : créer `demande_suppression (id, document_id, demandeur_id, motif, statut
[EN_ATTENTE|APPROUVEE|REJETEE], valide_par, motif_rejet, date_demande, date_decision,
date_derniere_relance)`. **Décision attendue :** …

### Q-03 — Table `demande_constitution` absente — `P1` (module intervenant)
FR-INT-06/07, UC-INT-05/06, US 5.6/5.7 (« Tables `constitution` (à créer) »).
**C** : `demande_constitution (id, dossier_id, prestataire_id, demandeur_id, motif, statut
[EN_ATTENTE|VALIDEE|REJETEE], valide_par, motif_rejet, lettre_document_id, date_demande,
date_decision)`. **Décision attendue :** …

### Q-05 — Traçabilité des validations de frais insuffisante — `P1` (module frais)
`demande_frais` ne porte qu'un `statut_circuit` et un `motif_rejet` unique. Or il faut tracer :
qui a contrôlé la conformité, qui a contrôlé l'opportunité, **les deux accords distincts** de la
validation conjointe (US 4.5 critère 2 : « le système enregistre **mon** accord ; si les **deux**
accords sont obtenus… »), avec dates et motifs. Impossible en l'état.
**C** : `validation_frais (id, demande_frais_id, etape [CONFORMITE|OPPORTUNITE|CONJOINTE_DJ|
CONJOINTE_DJA], utilisateur_id, decision [ACCORD|REJET], motif, date_validation)`.
**Décision attendue :** …

### Q-06 — Référentiel `type_client_sensible` — `P1`
RG-DOS-09 (REF-04) : « liste **prédéfinie**, gérée par le DJ ». Le dictionnaire n'a qu'un
`VARCHAR(50)` libre. **C** : table de référentiel + endpoints d'administration DJ, ou clé
`configuration_systeme` contenant une liste. **Décision attendue :** …

### Q-07 — `document.chemin_stockage` absent du script SQL — `P1`
Colonne `VARCHAR(255) NOT NULL` (« chemin ou référence MinIO ») présente au dictionnaire REF-06
§2.3.1, **omise** du `CREATE TABLE document` de REF-05 §12.1. Sans elle, aucun fichier n'est
récupérable. **C** : l'ajouter (le dictionnaire fait foi sur les données). **Décision attendue :** …

### Q-13 — Trois endpoints listés au §2 mais absents de la spécification OpenAPI — `P1`
`POST /dossiers/{id}/alarmes`, `PUT /alarmes/{id}/reprogrammer`, `PUT /deliberes/{id}/expedition`
figurent dans le tableau récapitulatif REF-07 §2 et dans les user stories (2.3, 2.4, 3.6) mais
**pas dans la spécification OpenAPI §3** (pas de schéma de requête ni de réponse).
**C** : les implémenter selon les critères Gherkin des US et compléter le contrat.
**Décision attendue :** …

### Q-19 — `GET /deliberes/{id}/recours` utilisé pour *créer* une alerte — `P1`
US 3.5 critère 1 : « **When** je soumets une `dateEcheance` **via GET** `/deliberes/{id}/recours`
(création d'alerte) ». Un `GET` ne doit pas créer de ressource ni accepter de corps.
**C** : conserver `GET /deliberes/{id}/recours` en **consultation** et créer le suivi via
`POST /dossiers/{id}/alarmes` (Q-04) avec un type `DELAI_RECOURS`.
**Décision attendue :** …

### Q-12 — « 409 avec possibilité de confirmer ou annuler » : mécanisme non spécifié — `P1`
RG-DOS-07 et RG-AUD-06 : le doublon d'affectation / d'audience **n'est pas bloquant** — le juriste
peut confirmer (UC-DOS-01 alt. 7-a, UC-DOS-02 alt. 7-a, UC-AUD-01 alt. 7-a). Un `409` seul ne
permet pas de confirmer. **C** : paramètre `?forcer=true` (ou champ `confirmerDoublon: true`) ;
la confirmation crée une alerte `DOUBLON_*` et une entrée d'historique (US 2.6 critère 2).
**Décision attendue :** …

### Q-28 — `avocatsAffectes` obligatoire à la création vs circuit de constitution — `P1`
US 1.1 : `avocatsAffectes (≥1)` est **obligatoire** à la création du dossier.
RG-INT-08 : la constitution d'un prestataire requiert la **seconde signature du SH** avant l'envoi
de la lettre. Comment un avocat peut-il être affecté avant sa constitution validée ?
**C** : distinguer *affectation* (interne, immédiate) et *constitution* (formalité externe,
validée par le SH) — ou rendre `avocatsAffectes` facultatif à la création.
**Décision attendue :** …

### Q-16 ✅ Ports — tranché et appliqué (M1)
REF-07 : `http://localhost:8080/api/v1` ; REF-05 §8.1 : backend 8080, Keycloak 8081.
Le code utilisait auparavant backend 8888, Keycloak 8080. **C** : aligner sur la documentation.
**Statut (2026-09-04, M1)** : appliqué — backend 8080, Keycloak 8081, vérifié en conditions
réelles (démarrage de l'application, obtention de jetons, appels API).

### Q-23 — Aucun endpoint de création de client — `P1`
`dossier.client_id` est `NOT NULL` et `GET /clients` existe, mais **aucun `POST /clients`**.
US §11.3 (INT-05) : « les données clients peuvent provenir d'un référentiel externe **ou être
saisies manuellement** ». Sans création, aucun dossier n'est créable. **C** : ajouter
`POST /clients` et `PUT /clients/{id}` (`ROLE_SAISIE`). **Décision attendue :** …

### Q-24 — Files de travail (listes) non spécifiées — `P1`
UC-INT-02/03/04, UC-GED-03, UC-INT-06/10 supposent tous des **listes d'éléments en attente**
(« aucune demande en attente de contrôle »). Aucun endpoint de liste n'est spécifié pour les
demandes de frais, les publications, les demandes de suppression, les constitutions.
**C** : ajouter un `GET` de liste filtrable par statut pour chacun de ces workflows.
**Décision attendue :** …

### Q-25 — Schémas OpenAPI référencés mais non définis — `P1`
`Publication`, `CompteRenduAudience`, `AutrePublication` sont utilisés en `$ref` dans les réponses
mais **jamais définis** dans `components/schemas`. Contrat incomplet.
**C** : les définir d'après le dictionnaire de données. **Décision attendue :** …

### Q-29 — `audit_seuil_dossier.statut` : présent à l'API, absent de la base — `P1`
Le schéma `AuditSeuil` (REF-07) expose `statut ∈ {EN_ATTENTE, VALIDEE, REJETEE}` ; la table
(REF-06 et REF-05 §12.1) **n'a pas cette colonne**. **C** : l'ajouter. **Décision attendue :** …

### Q-27 — `dossier.parties` défini deux fois — `P1`
Le dictionnaire REF-06 §2.1.2 déclare `parties JSONB` **deux fois**, avec deux structures
différentes : `{demandeur, defendeur}` et `{demandeur, defendeur, client}`. Le contrat d'API et
le script SQL retiennent `{demandeur, defendeur}`, le client étant porté par `client_id`.
**C** : retenir `{demandeur, defendeur}`. **Décision attendue :** …

---

## QUESTIONS SECONDAIRES (P2)

### Q-09 — Deux numérotations de cas d'utilisation — `P2`
REF-03 §8 (`UC-01`…`UC-17`) et REF-04 §3 (`UC-DOS-01`…`UC-REP-03`) ne se correspondent pas
(REF-04 inverse par ailleurs conformité/opportunité par rapport à la numérotation `FR-INT-02/03`).
**C** : adopter la numérotation REF-04 et le noter dans la matrice de traçabilité.

### Q-14 — Cible de disponibilité — `P2`
REF-03 / REF-04 / REF-08 : **> 99,5 %**. REF-05 §2 : **> 95 %** en pilote / **> 98 %** en production.
**C** : retenir REF-05 (plus récent et argumenté) et corriger les autres documents. Sans impact code.

### Q-15 — Versions d'images divergentes — `P2`
PostgreSQL **17** (REF-05 §3.1 et §8.1) vs **15** (§12.1 en-tête et §12.2) ;
Keycloak **26.6.4** (§8.1) vs `:latest` (§12.2) vs **24.0** (code).
Nom de base : `juridique_db` vs `jurique_db` (faute de frappe §12.2) vs `audiences_db` (code).
**C** : PostgreSQL 17, Keycloak 26.x épinglé, base `audiences_db`.

### Q-20 — `POST /publications/{id}/correspondance` — `P2`
« À proposer » (US 5.5). RG-INT-07 impose que seul le DJ/DJA transmette une correspondance unique.
**C** : créer l'endpoint et une table `correspondance_avocat`.

### Q-21 — Mise à disposition du dossier de crédit par le GFC — `P2`
RG-GED-01 : le GFC verse le dossier de crédit « manuellement ». Le GFC **n'a pas de compte**.
**C** : c'est le juriste qui charge ces pièces via `POST /ged/documents` ; aucune fonctionnalité
spécifique. À confirmer avec la Direction Juridique.

### Q-30 — Sous-workflow « saisie immobilière » (RG-DOS-05) — `P2`
REF-04 §7 précise que ce sous-workflow est « représenté comme une **note** plutôt qu'un sous-état
formel, pour ne pas figer prématurément un modèle **qui reste à confirmer** avec la Direction
Juridique ». **C** : hors périmètre V1 des états ; prévoir un champ libre d'étape détaillée.

### Q-31 — Licence iText 5 (AGPL) — `P2`
`itextpdf 5.5.13.4` est sous **AGPL** : usage dans une application propriétaire non distribuée à
valider avec la DSI. `pdfbox 3.0.2` (Apache 2.0) est également présent.
**C** : ne garder que **PDFBox** et retirer iText.

### Q-32 — Notifications applicatives : WebSocket ou polling ? — `P2`
NF-ALR-02 fait du canal applicatif le canal **obligatoire**, avec délai < 1 min. Le code contient
un socle STOMP/SockJS (utilisé pour un chat hors périmètre).
**C** : conserver l'infrastructure WebSocket pour le push de notifications (sécurisée par Keycloak),
avec `GET /alertes/mes-notifications` comme repli. **Décision attendue :** …

### Q-33 — Périmètre du dépôt : backend seul, ou monorepo ? — `P2`
REF-05 §12.2 décrit un `docker-compose.yml` avec `build: ./backend` et `build: ./frontend`,
alors que le dépôt courant **est** le backend à sa racine.
**C** : conserver deux dépôts ; adapter le compose du document de conception au moment de
l'intégration.

---

## POINTS DÉJÀ TRANCHÉS EN AMONT (rappel — ne pas rouvrir)

| Point | Arbitrage | Source |
|---|---|---|
| Low-code vs sur-mesure | Le cahier des charges porte une **annotation manuscrite du client** : « Utiliser les outils de Power Platform et intégration avec Teams » (REF-01 p. 7). **Écarté** : développement sur mesure conteneurisé, validé par la Direction Juridique puis la DSI | REF-02 §5.2 |
| Stack | Next.js + Spring Boot + PostgreSQL + Keycloak + MinIO, plutôt que les options ouvertes du cahier (React/Angular, Node/Spring/.NET, AD/LDAP/OAuth2) | REF-05 §3 |
| Comptes des prestataires | Seuls les **avocats** ont un compte ; les autres prestataires reçoivent des emails | REF-02 §5.2, RG-INT-02 |
| Paiement des frais | Hors périmètre applicatif — traçabilité du statut uniquement | REF-02 §5.2, RG-INT-04 |
| Intégrations SI | Aucune (corebanking, comptabilité, INTRA V2) — saisie manuelle | REF-02 §5.2, REF-03 §1.3 |
| Délai projet | 14 semaines, en substitution du mois demandé initialement | REF-02 §5.2 |

### Conditions de réception du module (REF-01 §7.1) — critères de recette à respecter
Le module n'est réceptionné que si : toutes les fonctionnalités sont implémentées **et testées** ;
les exigences non fonctionnelles sont vérifiées **et documentées** ; un plan de tests exhaustif a
été exécuté sans anomalie bloquante ; la documentation utilisateur et technique est livrée ;
une formation des utilisateurs clés a été réalisée ; les données historiques ont été migrées.

---

## DÉCISIONS VALIDÉES

| Date | Réf. | Décision | Impact |
|---|---|---|---|
| 2026-09-04 | Q-00 | Ordre de préséance retenu : `US ≻ Contrat API ≻ Dictionnaire ≻ Conception ≻ Analyse ≻ Spécifications`, sauf règle métier où Spécifications/Analyse priment | Sert d'arbitre par défaut pour toute contradiction non listée ici |
| 2026-09-04 | Q-01 | Création de `dossier_juriste` et `dossier_avocat` (association N:N, historique via `historique_action`) | Prévu au schéma M2 |
| 2026-09-04 | Q-04 | Création d'une table `alarme` distincte de `alerte` (objet + échéance + étape ; l'alarme génère une `alerte` à échéance) | Prévu au schéma M2, module `audience`/`delibere` M6/M7 |
| 2026-09-04 | Q-08 | Ajout de `ROLE_SAISIE` au realm Keycloak comme rôle composite (JURISTE, DJ, DJA, ASSISTANTE, SH) | Appliqué en M1 (`keycloak/audiences-realm.json`) |
| 2026-09-04 | Q-10 | Option A retenue : le dossier est créé immédiatement (201) ; `estSensible` reste à l'état proposé (`sensibilite_statut = EN_ATTENTE`) et n'a d'effet sur le circuit des frais qu'une fois validé par le DJ/DJA | Ajout de colonnes + endpoint dédié, prévu M2/M4 |
| 2026-09-04 | Q-17 | Code de retour `201 Created` sur `POST /deliberes/{id}/prorogations`, y compris au-delà du seuil ; `alerte:true` dans le corps | Prévu M7 |
| 2026-09-04 | Q-18 | Le seuil se déclenche quand `compteur > seuilProrogations` (11e prorogation avec le défaut 10), une seule fois | Prévu M7 |
| 2026-09-04 | Q-22 | Liste réunie des 16 types d'alerte retenue (cf. recommandation Q-22) | Prévu M2 (contrainte CHECK) et M5 |
| 2026-09-04 | Q-26 | La logique de la conception/contrat prévaut sur celle de RG-DOS-09 au sens strict : `seuil_montant` reste `NULL` par défaut (seuil global applicable), renseigné uniquement en cas de dérogation validée | Prévu M2 (contrainte SQL), M4 |

Les autres questions (P1/P2) restent ouvertes et seront tranchées au fil des jalons concernés,
au plus tard avant le module qui en dépend (cf. tableau des prérequis dans `DEVELOPMENT_ROADMAP.md`).
