# DECISIONS_AND_OPEN_QUESTIONS

> Contradictions documentaires, ambiguïtés et lacunes nécessitant un **arbitrage explicite**.
> Aucune de ces questions n'a été tranchée silencieusement.
> Dernière mise à jour : 2026-09-06 (après jalon M9 — voir statuts R-01 à R-06, Q-01/02/03/04/07/10/11/12/13/17-19/22/23/26/28/32/34-53).

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

### R-06 🔴 Provisionnement d'un utilisateur en première connexion incompatible avec un endpoint en lecture seule — découvert en validation manuelle M7

`CurrentUserService.getCurrentUser()` (M3) **provisionne** (INSERT) l'utilisateur en base à sa
toute première requête authentifiée (aucun compte n'étant créé côté Keycloak par l'application).
Or toute méthode appelante marquée `@Transactional(readOnly = true)` propage ce mode à cet INSERT
imbriqué (propagation par défaut `REQUIRED`) : PostgreSQL rejette l'écriture avec `ERROR: cannot
execute INSERT in a read-only transaction`, et la première requête de **tout nouvel utilisateur**
échoue en `500 ERR-INTERNAL` si cette requête tombe sur un endpoint en lecture seule (constaté ici
sur `GET /alertes/mes-notifications`, M5, mais le défaut est structurel à `CurrentUserService`,
pas propre à ce seul endpoint — tout `@Transactional(readOnly = true)` appelant
`getCurrentUser()`/`resolveOrProvision()` est concerné). Une fois l'utilisateur provisionné par un
premier appel en écriture, le problème disparaît pour ses appels suivants — d'où sa découverte
tardive (les utilisateurs de test des jalons précédents avaient déjà été provisionnés par des
appels en écriture antérieurs).
**C — Recommandation** : isoler le provisionnement dans sa propre transaction en écriture (ex.
`@Transactional(propagation = Propagation.REQUIRES_NEW)` sur `provisionFromJwt`), indépendante du
mode de la transaction appelante.
**Statut (2026-09-06)** : non corrigé — hors périmètre du jalon M7 (défaut du socle M3, aucun
module M7 n'y touche). Contourné pour la validation manuelle (provisionnement direct en base du
test `dja.test`). **Recommandé en correction dès que possible**, un premier appel en lecture de
n'importe quel nouvel utilisateur (DJA, ASSISTANTE, SH, AVOCAT dans cette session) y étant exposé.

---

## QUESTIONS BLOQUANTES (P0)

### Q-00 — Ordre de préséance entre documents — `P0`
Sept documents se recoupent et se contredisent ponctuellement. Aucun ne se déclare prioritaire.
**C — Recommandation** : `User stories ≻ Contrat d'API ≻ Dictionnaire ≻ Conception ≻ Analyse ≻
Spécifications`, **sauf** pour une règle métier où `Spécifications`/`Analyse` priment sur la forme
technique. → À confirmer, car toutes les autres questions en dépendent.
**Décision attendue :** …

---

### Q-10 ✅ Circuit de validation de la sensibilité d'un dossier — tranché et implémenté (M4)

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

**Statut (2026-09-04, M4)** : option A implémentée. `audit_seuil_dossier` sert de file d'attente
**unique** de propositions en attente, partagée avec Q-26 (voir plus bas) : `POST /dossiers` avec
`estSensible=true` crée une ligne `audit_seuil_dossier` (`statut=EN_ATTENTE`) portant la
proposition de seuil/type de client, sans jamais écrire directement `dossier.seuil_montant` ni
`dossier.type_client_sensible`. `POST /dossiers/{id}/sensibilite/validation` (endpoint #8, DJ/DJA
uniquement) approuve (renseigne alors ces colonnes + `sensibilite_statut=VALIDEE`) ou rejette
(`sensibilite_statut=REJETEE`, motif obligatoire, `est_sensible` repassé à `false`). Vérifié de
bout en bout par appels HTTP réels (dossier 3 : seuil `null` → `80 000 000` après validation).

---

### Q-26 ✅ Contrainte SQL `chk_seuil_coherence` incompatible avec RG-DOS-09 — tranché et implémenté (M4)

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

**Statut (2026-09-04, M4)** : appliqué. `dossier.seuil_montant`/`seuil_origine` restent
`GLOBAL`/`null` jusqu'à validation d'une proposition (Q-10) **ou** d'une dérogation dédiée —
`POST /dossiers/{id}/seuil-derogation` (crée une nouvelle ligne `audit_seuil_dossier`) puis
`PUT /dossiers/{id}/seuil-derogation/{auditId}` (DJ/DJA, approuve → `seuil_origine=DEROGATION` +
`seuil_montant` renseigné, ou rejette). Les deux mécanismes (Q-10 et Q-26) partagent donc la même
table de file d'attente `audit_seuil_dossier` — c'est la **seule** voie d'écriture de ces deux
colonnes, jamais un accès direct depuis `POST /dossiers` ou un endpoint générique. Vérifié
en conditions réelles : dossier 3, seuil `null` → `80 000 000` (validation sensibilité) →
`100 000 000` (dérogation ultérieure), avec `ancienSeuil` capturant correctement la valeur
précédente à chaque étape.

**Décision attendue :** …

---

### Q-01 ✅ Tables d'affectation juristes/avocats absentes du modèle — créées et implémentées (M2/M4)

**A.** RG-DOS-03 : « un dossier est affecté à **un ou plusieurs juristes** et à **un ou plusieurs
avocats** ». `POST /dossiers` exige `juristesAffectes[]` et `avocatsAffectes[]`.
FR-DOS-02 critère 3 : « l'historique des affectations est conservé ».

**B / lacune.** Le dictionnaire (REF-06) et le script SQL (REF-05 §12.1) ne contiennent **aucune
table d'association**. Le module dossier est inconstruisible en l'état.

**C — Recommandation** : créer `dossier_juriste (dossier_id, utilisateur_id, date_affectation,
affecte_par)` et `dossier_avocat (dossier_id, avocat_id, date_affectation, affecte_par)`,
PK composite, l'historique étant porté par `historique_action`.

**Statut (2026-09-04, M2/M4)** : tables créées telles quelles en M2 (`@EmbeddedId`/`@MapsId`).
Endpoints d'affectation implémentés en M4 : `juristesAffectes`/`avocatsAffectes` à la création
(`POST /dossiers`), `PUT /dossiers/{id}/affectation` pour les modifications ultérieures (voir
Q-12 pour la gestion du doublon). Chaque affectation/désaffectation génère une entrée
`historique_action` dédiée.

---

### Q-04 ✅ Entité « alarme » : absente du modèle de données — créée (M2) et implémentée (M6)

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

**Statut (2026-09-04, M2/M6)** : option A appliquée — table `alarme` créée en M2 ; `AlarmeService`
(`creerAlarme`, `reprogrammerAlarme`) et `AlarmeEcheanceScheduler` (génère une `Alerte` de type
`ALARME` quand `date_echeance` est atteinte, une seule fois par alarme — cf. Q-39) livrés en M6.
L'alarme reste `ACTIVE` après le déclenchement de l'alerte ; seule une reprogrammation la marque
`TRAITEE`, exactement comme FR-AUD-04 le décrit.

**Décision attendue :** …

---

### Q-22 ✅ Énumération des types d'alerte : quatre sources divergentes — tranché et implémenté (M2/M5)

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

**Statut (2026-09-04, M2/M5)** : liste réunie (17 valeurs, `ALARME` incluse pour Q-04) appliquée
à la contrainte `chk_alerte_type` dès M2 (`V1__schema_initial.sql`) ; l'énumération Java
`TypeAlerte` correspondante et son usage effectif (déclenchement, filtrage) livrés en M5.

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

### Q-11 ✅ Champs conditionnels par catégorie : `ET` ou `OU` ? et catégorie saisie ou déduite ? — tranché et implémenté (M4)

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

**Statut (2026-09-04, M4)** : (a) et (b) appliqués, avec une nuance sur (a) — la validation `ET`
est appliquée à la **couche service** (`DossierService.validerChampsConditionnels`, plus stricte
que le contrat OpenAPI qui ne liste pas ces champs en `required`), tandis que la contrainte SQL
`chk_categorie_coherence` (M2, en `OR`) est **conservée telle quelle** comme filet de sécurité de
dernier recours au niveau base — un choix de validation en couches, non une incohérence non
résolue : le service métier porte la règle stricte, la base garantit un minimum même en cas de
contournement de la couche service. (b) `nature_dossier` (M2) porte la catégorie associée à
chaque nature ; `DossierService.resoudreCategorie` la dérive via
`NatureDossierRepository.findByLibelleIgnoreCase` et rejette (400) toute incohérence si le client
envoie malgré tout un champ `categorie` divergent.

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

**Statut (2026-09-06, M7)** : les deux recommandations C appliquées telles quelles.
`POST /deliberes/{id}/prorogations` retourne toujours `201 Created` ; `ProrogationResponse` porte
un champ `alerte` — `true` dès que `compteur > SEUIL_PROROGATIONS` (pas seulement à la prorogation
qui franchit le seuil, pour que le frontend puisse afficher l'état à tout moment). Le
déclenchement effectif de l'alerte `PROROGATION` (vers chaque DJ/DJA actif) reste protégé par
`AlerteService.aDejaEteDeclenchee` avec un marqueur `[delibereId:X]`, garantissant l'unicité
« une seule fois » même si de nouveaux DJ/DJA deviennent actifs après le franchissement.

---

## QUESTIONS IMPORTANTES (P1)

### Q-02 ✅ Table `demande_suppression` absente — créée (M2)
FR-GED-03 critère 1 cite explicitement « la table `demande_suppression` », qui **n'existe ni au
dictionnaire ni au script SQL**. UC-GED-03 alt. 6-a ajoute une **relance automatique à 72 h**.
**C** : créer `demande_suppression (id, document_id, demandeur_id, motif, statut
[EN_ATTENTE|APPROUVEE|REJETEE], valide_par, motif_rejet, date_demande, date_decision,
date_derniere_relance)`.
**Statut (2026-09-04, M2)** : appliqué tel quel — `V1__schema_initial.sql`. La relance à 72 h
(planificateur) reste à implémenter en M8 (module GED).

**Statut (2026-09-06, M8)** : relance à 72 h implémentée — `SuppressionRelanceScheduler`, même
principe que les planificateurs des jalons précédents (Q-38/Q-39/Q-43). Vérifiée en conditions
réelles (`date_demande` antidatée de 73 h, cron accéléré) : relance déclenchée vers DJ et DJA,
`date_derniere_relance` mise à jour, aucune resignalisation avant le prochain cycle de 72 h.

### Q-03 ✅ Table `demande_constitution` absente — créée (M2)
FR-INT-06/07, UC-INT-05/06, US 5.6/5.7 (« Tables `constitution` (à créer) »).
**C** : `demande_constitution (id, dossier_id, prestataire_id, demandeur_id, motif, statut
[EN_ATTENTE|VALIDEE|REJETEE], valide_par, motif_rejet, lettre_document_id, date_demande,
date_decision)`.
**Statut (2026-09-04, M2)** : appliqué tel quel — `V1__schema_initial.sql`.

**Statut (2026-09-06, M9)** : entité JPA `DemandeConstitution` et circuit complet implémentés
(`ConstitutionService`) — cf. Q-52/Q-53.

### Q-05 ✅ Traçabilité des validations de frais insuffisante — corrigée (M2)
`demande_frais` ne porte qu'un `statut_circuit` et un `motif_rejet` unique. Or il faut tracer :
qui a contrôlé la conformité, qui a contrôlé l'opportunité, **les deux accords distincts** de la
validation conjointe (US 4.5 critère 2 : « le système enregistre **mon** accord ; si les **deux**
accords sont obtenus… »), avec dates et motifs. Impossible en l'état.
**C** : `validation_frais (id, demande_frais_id, etape [CONFORMITE|OPPORTUNITE|CONJOINTE_DJ|
CONJOINTE_DJA], utilisateur_id, decision [ACCORD|REJET], motif, date_validation)`.
**Statut (2026-09-04, M2)** : appliqué tel quel — `V1__schema_initial.sql`.

### Q-06 ✅ Référentiel `type_client_sensible` — tranché et créé (M2)
RG-DOS-09 (REF-04) : « liste **prédéfinie**, gérée par le DJ ». Le dictionnaire n'a qu'un
`VARCHAR(50)` libre. **C** : table de référentiel + endpoints d'administration DJ, ou clé
`configuration_systeme` contenant une liste.
**Statut (2026-09-04, M2)** : première option retenue — table `type_client_sensible (id, code,
libelle, actif)`, non FK-contrainte depuis `dossier.type_client_sensible` (même traitement que
`dossier.nature` vis-à-vis de sa taxonomie). Entité JPA + repository créés. Valeurs initiales
indicatives (VIP, ENTREPRISE, PARTICULIER) — aucune liste n'étant fournie par la documentation,
à ajuster par le DJ via une future interface d'administration.

### Q-07 ✅ `document.chemin_stockage` absent du script SQL — corrigé (M2)
Colonne `VARCHAR(255) NOT NULL` (« chemin ou référence MinIO ») présente au dictionnaire REF-06
§2.3.1, **omise** du `CREATE TABLE document` de REF-05 §12.1. Sans elle, aucun fichier n'est
récupérable. **C** : l'ajouter (le dictionnaire fait foi sur les données).
**Statut (2026-09-04, M2)** : appliqué — colonne ajoutée à `V1__schema_initial.sql`.

### Q-13 ✅ Trois endpoints listés au §2 mais absents de la spécification OpenAPI — 3/3 implémentés (M6/M7)
`POST /dossiers/{id}/alarmes`, `PUT /alarmes/{id}/reprogrammer`, `PUT /deliberes/{id}/expedition`
figurent dans le tableau récapitulatif REF-07 §2 et dans les user stories (2.3, 2.4, 3.6) mais
**pas dans la spécification OpenAPI §3** (pas de schéma de requête ni de réponse).
**C** : les implémenter selon les critères Gherkin des US et compléter le contrat.

**Statut (2026-09-04, M6)** : `POST /dossiers/{id}/alarmes` et `PUT /alarmes/{id}/reprogrammer`
implémentés selon les critères Gherkin de US 2.3/2.4 (`AlarmeController`). `PUT
/deliberes/{id}/expedition` reste ouvert, prévu au module M7 (Délibérés).

**Statut (2026-09-06, M7)** : `PUT /deliberes/{id}/expedition` implémenté (`DelibereController`) —
bascule `statutExpedition` entre `A_LEVER`/`LEVEE`, historisée. Les 3 endpoints du contrat sont
désormais couverts.

### Q-19 ✅ `GET /deliberes/{id}/recours` utilisé pour *créer* une alerte — tranché (M7)
US 3.5 critère 1 : « **When** je soumets une `dateEcheance` **via GET** `/deliberes/{id}/recours`
(création d'alerte) ». Un `GET` ne doit pas créer de ressource ni accepter de corps.
**C** : conserver `GET /deliberes/{id}/recours` en **consultation** et créer le suivi via
`POST /dossiers/{id}/alarmes` (Q-04) avec un type `DELAI_RECOURS`.

**Statut (2026-09-06, M7)** : recommandation C **révisée**. L'alarme M6 (`objet` en texte libre,
sans notion de type) ne porte pas de sémantique `DELAI_RECOURS` propre, et un délibéré ne devrait
pas nécessiter deux mécanismes de suivi distincts (alarme libre + échéance de recours) pour la
même information. Choix final : `Delibere.dateEcheanceRecours` est saisie **directement** dans
`POST /dossiers/{id}/deliberes` (requise et postérieure à `dateDeliberee` si `resultat` ∈
{DEFAVORABLE, MIXTE} — Q-42), suivie par un planificateur dédié `DelaiRecoursScheduler` qui
déclenche `TypeAlerte.DELAI_RECOURS` (déjà réservé en base depuis M2) à l'échéance. `GET
/deliberes/{id}/recours` reste strictement une consultation (`RecoursResponse`), conforme à C.

### Q-12 ✅ « 409 avec possibilité de confirmer ou annuler » : mécanisme non spécifié — tranché et implémenté (M4 volet dossier, M6 volet audience)
RG-DOS-07 et RG-AUD-06 : le doublon d'affectation / d'audience **n'est pas bloquant** — le juriste
peut confirmer (UC-DOS-01 alt. 7-a, UC-DOS-02 alt. 7-a, UC-AUD-01 alt. 7-a). Un `409` seul ne
permet pas de confirmer. **C** : paramètre `?forcer=true` (ou champ `confirmerDoublon: true`) ;
la confirmation crée une alerte `DOUBLON_*` et une entrée d'historique (US 2.6 critère 2).

**Statut (2026-09-04, M4)** : appliqué pour le volet dossier — `ModifierAffectationRequest.forcer`
(booléen, défaut `false`). `DossierService.modifierAffectation` renvoie `409` sur une affectation
déjà existante sauf si `forcer=true`, auquel cas elle est acceptée et une entrée `historique_action`
la trace explicitement comme doublon confirmé. La génération d'une alerte `DOUBLON_AFFECTATION`
(référentiel de types Q-22) câblée en M5 (`DossierService.signalerDoublonAffectation`, destinataire
DJ — Q-36).

**Statut (2026-09-04, M6)** : volet audience appliqué à l'identique — `PlanifierAudienceRequest`
via `?forcer=true` sur `POST /dossiers/{id}/audiences` ; `AudienceService.planifierAudience`
renvoie `409/ERR-005` sur un doublon (même `etapeId` + `datePlanifiee`, RG-AUD-06) sauf
confirmation, auquel cas une alerte `DOUBLON_AUDIENCE` est déclenchée vers les DJ actifs (même
mécanisme que Q-36). Q-12 est donc intégralement clos sur ses deux volets.
**Décision attendue :** …

### Q-28 ✅ `avocatsAffectes` obligatoire à la création vs circuit de constitution — tranché (M9)
US 1.1 : `avocatsAffectes (≥1)` est **obligatoire** à la création du dossier.
RG-INT-08 : la constitution d'un prestataire requiert la **seconde signature du SH** avant l'envoi
de la lettre. Comment un avocat peut-il être affecté avant sa constitution validée ?
**C** : distinguer *affectation* (interne, immédiate) et *constitution* (formalité externe,
validée par le SH) — ou rendre `avocatsAffectes` facultatif à la création.
**Statut (2026-09-06, M9)** : première branche de C retenue — les deux restent des circuits
indépendants et non séquencés l'un par rapport à l'autre. `avocatsAffectes` (M4) désigne qui **suit
le dossier en interne** (déjà référencé dans `avocat`, aucune formalité requise) ; `POST
/constitutions/prestataires` (M9) est une démarche **distincte** d'engagement formel d'un
prestataire pour ce dossier (avocat déjà affecté ou non, ou tout autre prestataire), au besoin
après coup. Rien n'empêche ni n'exige l'ordre entre les deux — `ConstitutionService.creerDemande`
n'exige pas que le prestataire soit déjà dans `avocatsAffectes`.

### Q-16 ✅ Ports — tranché et appliqué (M1)
REF-07 : `http://localhost:8080/api/v1` ; REF-05 §8.1 : backend 8080, Keycloak 8081.
Le code utilisait auparavant backend 8888, Keycloak 8080. **C** : aligner sur la documentation.
**Statut (2026-09-04, M1)** : appliqué — backend 8080, Keycloak 8081, vérifié en conditions
réelles (démarrage de l'application, obtention de jetons, appels API).

### Q-23 ✅ Aucun endpoint de création de client — créé et implémenté (M4)
`dossier.client_id` est `NOT NULL` et `GET /clients` existe, mais **aucun `POST /clients`**.
US §11.3 (INT-05) : « les données clients peuvent provenir d'un référentiel externe **ou être
saisies manuellement** ». Sans création, aucun dossier n'est créable. **C** : ajouter
`POST /clients` et `PUT /clients/{id}` (`ROLE_SAISIE`).

**Statut (2026-09-04, M4)** : `POST /clients` implémenté et marqué 🆕 (hors contrat OpenAPI
formel) dans `ClientController`/`API_IMPLEMENTATION_STATUS.md`, `ROLE_SAISIE` requis, vérifié en
conditions réelles. `PUT /clients/{id}` **non implémenté** — aucune US ni cas d'usage M4 ne
l'exigeait explicitement (seule la création bloquait `POST /dossiers`) ; à réévaluer si un besoin
de modification client émerge dans un module ultérieur.

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

### Q-29 ✅ `audit_seuil_dossier.statut` : présent à l'API, absent de la base — corrigé (M2)
Le schéma `AuditSeuil` (REF-07) expose `statut ∈ {EN_ATTENTE, VALIDEE, REJETEE}` ; la table
(REF-06 et REF-05 §12.1) **n'a pas cette colonne**. **C** : l'ajouter.
**Statut (2026-09-04, M2)** : appliqué — colonne ajoutée à `V1__schema_initial.sql`.

### Q-34 ✅ Positionnement direct d'une étape sans antécédents (UC-DOS-04 alt. 3-b) — endpoint 🆕 créé (M4)
UC-DOS-04 alt. 3-b permet de positionner directement une étape (ex. `RECOURS_2`) sans que les
étapes intermédiaires (`RECOURS_1`) aient été créées au préalable. Le contrat d'API ne prévoit
que `PATCH /dossiers/{id}/etapes/{etapeId}/statut`, qui exige structurellement un `etapeId`
**existant** — il ne peut donc pas créer une étape qui n'existe pas encore.
**C — Recommandation** : ajouter un nouvel endpoint, hors des 12 endpoints du contrat formel
(`API_IMPLEMENTATION_STATUS.md` §1), clairement marqué 🆕.
**Statut (2026-09-04, M4)** : implémenté — `POST /dossiers/{id}/etapes`
(`CreerEtapeRequest{type, statutCycleVie}`, `ROLE_SAISIE`). `DossierService.creerEtape` crée
automatiquement, à `OUVERTURE`, toute étape intermédiaire manquante entre `INSTANCE` et l'étape
demandée (ex. demander directement `RECOURS_2` crée aussi `RECOURS_1` si absente), chacune tracée
dans l'historique. Vérifié en conditions réelles (dossier 4 : positionnement direct sur
`RECOURS_2`, `RECOURS_1` créée automatiquement avec le statut `OUVERTURE`).

### Q-35 ✅ Frontière entre transitions génériques (M4) et transitions dédiées délibéré/rabattement (M7) — tranché (M4)
`EtapeTransitionValidator` (M4) doit couvrir les transitions génériques de cycle de vie d'une
étape, mais certaines transitions (`EN_DELIBERE→DELIBERE_VIDE`, l'enregistrement d'un délibéré ;
`EN_DELIBERE→EN_COURS`, un rabattement) nécessitent de collecter des données métier
supplémentaires (résultat du délibéré, motif de rabattement) que l'endpoint générique
`PATCH .../etapes/{etapeId}/statut` ne reçoit pas — ni le contrat d'API ni le dictionnaire ne
précisaient explicitement cette frontière.
**C — Recommandation** : exclure ces deux transitions de la table `TRANSITIONS_AUTORISEES` de M4
et les réserver à des endpoints dédiés du module Délibérés (M7).
**Statut (2026-09-04, M4)** : appliqué — `EtapeTransitionValidator.TRANSITIONS_AUTORISEES` exclut
explicitement les deux transitions (commentées dans le code et testées négativement, cf.
`EtapeTransitionValidatorTest`). Seule `DELIBERE_VIDE→EN_COURS` (exercice d'un recours) reste
autorisée par l'endpoint générique M4, avec ouverture automatique de l'étape suivante si absente
(cf. Q-34) — vérifié en conditions réelles dans les deux cas (étape suivante déjà existante :
aucune duplication, aucune entrée d'historique parasite ; étape suivante absente : création
automatique tracée).

**Statut (2026-09-06, M7)** : la cible du rabattement (RG-DEL-07/FR-DEL-05) est **corrigée** en
implémentant le module : ce n'est pas `EN_DELIBERE→EN_COURS` mais `DELIBERE_VIDE→EN_COURS` — la
**même** valeur de transition que l'exercice d'un recours (l'endpoint `POST /deliberes/{id}/...`
suppose un délibéré déjà enregistré, donc une étape déjà `DELIBERE_VIDE`, cf. Q-42). Les deux cas
partagent la même transition de statut mais sont distingués par l'endpoint appelé et par leurs
effets de bord : le rabattement (`POST /deliberes/{id}/rabattement`, dédié M7) ne rouvre jamais
l'étape suivante et exige un motif ; l'exercice d'un recours (endpoint générique M4) ouvre
toujours l'étape suivante et ne prend aucun motif. `EN_DELIBERE→EN_COURS` reste, elle, exclue de
l'endpoint générique et n'est utilisée par aucun endpoint (le rabattement s'applique après
enregistrement du délibéré, jamais avant).

### Q-36 ✅ Destinataire de l'alerte `DOUBLON_AFFECTATION` — tranché (M5)
Q-12 (M4) a implémenté la confirmation d'un doublon d'affectation (`forcer=true`) et prévu qu'elle
« crée une alerte `DOUBLON_*` », sans que RG-DOS-07/US 2.6 ne précisent le **destinataire** de
cette alerte (le juriste qui vient de confirmer, ou un profil de supervision distinct ?).
**C — Recommandation** : destinataire = utilisateurs de profil **DJ** actifs — c'est une alerte de
supervision d'un contournement de règle, pas une confirmation adressée à son propre auteur.
**Statut (2026-09-04, M5)** : appliqué — `DossierService.signalerDoublonAffectation` déclenche une
alerte `DOUBLON_AFFECTATION` vers chaque DJ actif (`UtilisateurRepository
.findByProfilCodeAndActifTrue("DJ")`) dès qu'une affectation en doublon est confirmée. Vérifié en
conditions réelles (`PUT /dossiers/{id}/affectation?forcer=true` → alerte visible dans `GET
/alertes/mes-notifications` du DJ).

### Q-37 ✅ Canaux Outlook/Teams de l'alerte : hors périmètre M5, aucune infrastructure fournie — tranché (M5)
RG-ALR-03 / `configuration_systeme.CANAUX_NOTIFICATION_DEFAUT` évoquent plusieurs canaux
(`APPLICATIF,OUTLOOK`) pour une même alerte, mais la colonne `alerte.canal` ne porte qu'**une**
valeur par ligne (contrainte `chk_alerte_canal`) — aucune des deux tables ne permet de savoir si
« plusieurs canaux » signifie plusieurs lignes `alerte` ou plusieurs envois d'une même ligne.
Par ailleurs, aucune configuration SMTP ni webhook Teams n'a jamais été fournie au projet (le
bloc Javamail d'`application.properties` est resté commenté depuis l'audit initial).
**C — Recommandation** : traiter `alerte` comme la représentation du canal applicatif uniquement
(seul canal réellement obligatoire, NF-ALR-02) ; Outlook/Teams sont des canaux de délivrance
**supplémentaires** d'un même événement, pas des lignes `alerte` distinctes — à implémenter
seulement si une infrastructure SMTP/webhook est un jour fournie par la DSI.
**Statut (2026-09-04, M5)** : appliqué — `AlerteService.declencher` persiste toujours
`canal = APPLICATIF` ; `CANAUX_NOTIFICATION_DEFAUT` reste consultable/modifiable via
`GET/PUT /configurations` (CONF01-07) mais n'est pas encore consommé pour déclencher un envoi
Outlook/Teams réel — documenté explicitement dans le Javadoc d'`AlerteService`, pas une omission
silencieuse.

### Q-38 ✅ Destinataire et propriétaire du calcul de l'alerte `CHARGE_MAX_AVOCAT` — tranché (M5)
RG-ALR-02/FR-ALR-01 (US 7.1) définissent le seuil configurable et l'endpoint `PUT /alertes/seuils`
(réservé au DJ) mais ne précisent ni le destinataire de l'alerte déclenchée, ni quel module la
calcule (elle dépend de `dossier_avocat`, propriété du module `dossier`, alors que `alerte` doit
rester un socle transversal sans dépendance vers les modules métier — cf. Javadoc `Alerte`).
**C — Recommandation** : (a) destinataire = DJ actifs, par cohérence avec `PUT /alertes/seuils` et
Q-36 ; (b) le calcul planifié vit dans le module `dossier` (qui a besoin de
`DossierAvocatRepository`) et appelle `AlerteService.declencher` en sens unique, plutôt que
l'inverse.
**Statut (2026-09-04, M5)** : appliqué — `dossier/service/ChargeMaxAvocatScheduler`
(`@Scheduled(fixedDelayString = "${alerte.charge-max-avocat.interval-ms:3600000}")`) compte les
dossiers par avocat, compare au seuil `CHARGE_MAX_AVOCAT`, et déclenche une alerte par DJ actif —
avec anti-doublon tant qu'une alerte du même avocat reste `DECLENCHEE`
(`AlerteService.estDejaSignalee`). Vérifié en conditions réelles avec un intervalle de test réduit
(5 s) : alerte créée une seule fois malgré plusieurs cycles tant qu'elle n'est pas traitée, puis
une nouvelle alerte générée après traitement de la précédente.

### Q-39 ✅ Destinataires des alertes `RAPPEL_AUDIENCE` et `ALARME` — tranché (M6)
FR-AUD-01 (RG-AUD-03) et FR-AUD-03/04 (Q-04) prévoient le déclenchement de ces alertes mais ne
précisent pas leur destinataire — contrairement à `PUT /alertes/seuils` (Q-38), aucun endpoint
associé n'indique un rôle par élimination.
**C — Recommandation** : (a) `RAPPEL_AUDIENCE` → chaque juriste affecté au dossier (ce sont eux qui
suivent l'audience, cf. `dossier_juriste`) ; (b) `ALARME` → le créateur de l'alarme (`created_by`),
puisque c'est un rappel personnel qu'il s'est lui-même fixé (RG-AUD-02).
**Statut (2026-09-04, M6)** : appliqué — `RappelAudienceScheduler` déclenche une alerte par juriste
affecté (via `DossierJuristeRepository`) ; `AlarmeEcheanceScheduler` déclenche vers
`alarme.created_by`. Vérifié en conditions réelles (cron/intervalle réduits pour le test) : les
deux mécanismes fonctionnent et l'anti-doublon (`AlerteService.aDejaEteDeclenchee`) empêche toute
répétition tant que l'audience/l'alarme concernée n'a pas changé d'état.

### Q-40 ✅ Date de tenue d'une audience et « prochaine date » du calendrier — tranché (M6)
FR-AUD-02 exige une « date passée » pour enregistrer un compte rendu, sans préciser si
`date_tenue` est fournie par l'utilisateur ou déduite ; FR-AUD-05 mentionne une « prochaine date »
par ligne de calendrier sans définir sa source (l'audience elle-même n'a qu'une `date_planifiee`).
**C — Recommandation** : (a) `date_tenue` = date de saisie du compte rendu (le juriste ne peut
saisir un compte rendu qu'après l'audience, donc « aujourd'hui » satisfait
`date_tenue >= date_planifiee`, contrainte SQL `chk_audience_dates`) plutôt qu'un champ demandé au
client, non spécifié par le contrat ; (b) « prochaine date » = `date_planifiee` de l'audience
elle-même — chaque ligne du calendrier représente déjà l'échéance à venir.
**Statut (2026-09-04, M6)** : appliqué — `AudienceService.enregistrerCompteRendu` fixe
`dateTenue = LocalDate.now()` ; `CalendrierEntreeResponse.datePlanifiee` sert de champ unique pour
la date, sans duplication artificielle en un champ « prochaineDate » distinct.

### Q-41 ✅ Formats d'export du calendrier des audiences — tranché (M6)
FR-AUD-05 exige un export JSON/PDF/EXCEL sans préciser le mécanisme de sélection du format
(paramètre de requête, en-tête `Accept`, endpoints distincts ?).
**C — Recommandation** : paramètre de requête `format` (`JSON` par défaut, `PDF`, `EXCEL`) sur
`GET /audiences/calendrier` — le plus simple à documenter et à tester, cohérent avec le reste du
contrat qui n'utilise jamais la négociation de contenu HTTP. PDFBox (déjà au `pom.xml` depuis
l'audit initial, cf. Q-31) et Apache POI pour la génération.
**Statut (2026-09-04, M6)** : appliqué — `CalendrierExportService` (PDFBox pour le PDF, POI pour
le classeur `.xlsx`), export testé en conditions réelles dans les 3 formats (signatures de fichier
`%PDF-`/`PK..` vérifiées, tailles non nulles).

### Q-42 ✅ Rabattement vs. exercice d'un recours : même transition d'étape, deux endpoints — tranché (M7)
RG-DEL-07/FR-DEL-05 (rabattement, `POST /deliberes/{id}/rabattement`) et RG-DOS-04 (exercice d'un
recours, endpoint générique `PATCH /dossiers/{id}/etapes/{etapeId}/statut`, M4) ciblent tous deux
`DELIBERE_VIDE→EN_COURS` — aucun des deux documents ne signale ce chevauchement, ni comment le
système doit distinguer les deux cas (cf. correction de Q-35 ci-dessus).
**C — Recommandation** : distinguer par l'endpoint appelé plutôt que par la valeur de transition,
avec des effets de bord différents : le rabattement rejoue la **même** étape (aucune étape
suivante ouverte, motif obligatoire, `RabattementRequest`) ; l'exercice d'un recours ouvre
**toujours** l'étape suivante (`ouvrirEtapeSuivante`, Q-34), sans motif. Un résultat FAVORABLE
n'ouvrant aucune voie de recours (aucune `dateEcheanceRecours`), seuls DEFAVORABLE/MIXTE peuvent,
en toute rigueur, faire l'objet d'un recours exercé — le rabattement, lui, reste possible quel que
soit le résultat (une décision favorable peut aussi être rabattue pour complément d'instruction).
**Statut (2026-09-06, M7)** : appliqué — `DelibereService.rabattre` vérifie `etape.statutCycleVie
== DELIBERE_VIDE`, bascule directement vers `EN_COURS` sans jamais appeler la logique
d'ouverture d'étape suivante ; testé négativement (étape non `DELIBERE_VIDE` → 400) et
positivement (`verify(etapeProcedureRepository, never()).existsByDossierIdAndType(...)`, preuve
qu'aucune étape suivante n'est consultée/ouverte).

### Q-43 ✅ Bascule automatique en `CLOTURE` à l'expiration du délai de recours — tranché (M7)
FR-DEL-02 : « à l'échéance sans recours : étape → `CLOTURE`, **bascule décisions définitives**,
notification ». Le module Décisions définitives (Épic 8 partiel, M12) n'existe pas encore à ce
stade du roadmap — sa « bascule » ne peut donc pas être implémentée maintenant sans spéculer sur
un schéma non conçu.
**C — Recommandation** : implémenter la partie réalisable dès M7 (notification + bascule
`CLOTURE`) via un planificateur dédié (`DelaiRecoursScheduler`, même principe que
`ChargeMaxAvocatScheduler`/`RappelAudienceScheduler`/`AlarmeEcheanceScheduler` — Q-38/Q-39) ;
différer explicitement le volet « décisions définitives » à M12, plutôt que de l'omettre
silencieusement.
**Statut (2026-09-06, M7)** : appliqué — `DelaiRecoursScheduler` (module `delibere`, dépend de
`dossier` et `alerte`) déclenche `TypeAlerte.DELAI_RECOURS` vers chaque juriste affecté (anti-
doublon `aDejaEteDeclenchee`, marqueur `[delibereId:X]`, même sémantique « une seule fois » que
Q-39) puis bascule l'étape en `CLOTURE`. Aucune entrée `historique_action` pour cette transition
automatique — `historique_action.utilisateur_id` est `NOT NULL` et aucun planificateur M5/M6
n'historise ses propres actions système (le déclenchement de l'alerte, lui, est toujours
persisté et fait foi de traçabilité). Le volet « bascule décisions définitives » reste à faire au
module M12.

### Q-44 ✅ Prorogation : « l'étape reste EN_DELIBERE » — précondition ou constat ? — tranché (M7)
FR-DEL-04 critère 4 : « incrémente le compteur ; **l'étape reste `EN_DELIBERE`** ». Lu comme une
précondition d'entrée, ce critère est **structurellement impossible à satisfaire** : `prorogation.
delibere_id` est `NOT NULL` (schéma M2) et référence donc nécessairement un délibéré déjà
enregistré via `POST /dossiers/{id}/deliberes`, lequel a — par construction (Q-01, FR-DEL-01) —
déjà fait basculer l'étape vers `DELIBERE_VIDE` avant qu'aucune prorogation ne puisse exister. Une
implémentation initiale de ce projet avait pourtant ajouté cette vérification comme garde d'entrée
(rejet 400 si l'étape n'était plus `EN_DELIBERE`), rendant `POST /deliberes/{id}/prorogations`
**inutilisable en pratique** — détecté en validation manuelle (M7).
**C — Recommandation** : lire « l'étape reste EN_DELIBERE » comme un **constat d'absence d'effet
de bord** (la prorogation ne touche jamais au cycle de vie de l'étape), pas comme une précondition
— aucune vérification de statut d'étape sur `proroger`.
**Statut (2026-09-06, M7)** : appliqué — `DelibereService.proroger` ne consulte plus le statut de
l'étape ; seul `prorogationRepository.findById`/`countByDelibereId` conditionnent l'opération.
Revérifié en conditions réelles (curl) : 11 prorogations enregistrées avec succès sur un délibéré
déjà `DELIBERE_VIDE`, alerte `PROROGATION` déclenchée à la 11e.

### Q-45 ✅ `document.type_document` : aucune taxonomie documentée (GED) — tranché (M8)
Contrairement à `autre_publication.type_document` (fermé à {PIECE, DECISION, CONCLUSION} — table
distincte, module publications M11), `document.type_document VARCHAR(50) NOT NULL` (GED, M8) ne
porte **aucun** commentaire ni référentiel dans le dictionnaire ou le script SQL. RG-GED-02/04/06
ne mentionnent que format et poids, jamais de taxonomie de type.
**C — Recommandation** : conserver un champ texte libre saisi par le client (ex. « Pièce
d'identité », « Décision de justice », « Correspondance »), sans validation de valeur — à
distinguer explicitement du champ homonyme de `autre_publication`.
**Statut (2026-09-06, M8)** : appliqué — `GedController.uploaderDocument` accepte `typeDocument`
en texte libre (`@RequestParam String`, aucune contrainte d'énumération).

### Q-46 ✅ Format d'un document : dérivé du fichier ou déclaré par le client ? — tranché (M8)
RG-GED-04/08 exigent un format ∈ {PDF, PNG, JPG, TXT, XLSX} et un poids ≤ `TAILLE_MAX_GED`, sans
préciser la source de vérité du format (extension du nom de fichier, `Content-Type` HTTP déclaré,
ou champ de requête dédié) — un client pourrait déclarer un type MIME ne correspondant pas au
contenu réel.
**C — Recommandation** : dériver le format de l'**extension du nom de fichier** (`nomFichier`
tronqué après le dernier `.`), plus robuste qu'un `Content-Type` déclaratif et plus simple qu'une
inspection du contenu binaire (hors périmètre, aucune bibliothèque de détection fournie au
projet) ; le rejeter (`ERR-008`) si l'extension ne correspond à aucune valeur de
`FormatDocument`, **ou** si elle y correspond mais est désactivée par la configuration
`FORMATS_GED` (paramétrable par le DJ, CONF06).
**Statut (2026-09-06, M8)** : appliqué — `GedService.validerFichier`. Le poids est comparé au
paramètre configurable `TAILLE_MAX_GED` (CONF06) *en plus* du plafond statique Spring
(`spring.servlet.multipart.max-file-size=10MB`, M1) : ce dernier reste la limite absolue
(protection mémoire/réseau, non reconfigurable par le DJ), tandis que `TAILLE_MAX_GED` permet au
DJ d'abaisser le seuil fonctionnel en dessous de ce plafond. Un fichier dépassant le plafond
Spring déclenche `MaxUploadSizeExceededException` **avant** même que le contrôleur ne soit atteint
— gérée séparément (cf. Q-51) pour rester conforme au format d'erreur du contrat.

### Q-47 ✅ `DELETE /ged/documents/{id}` : double comportement, un seul endpoint — tranché (M8)
RG-GED-05/09 : le juriste **auteur du chargement** obtient une demande soumise à approbation
(202) ; le DJ/DJA obtient une suppression immédiate (200) — sur le **même** verbe/chemin, sans
paramètre de requête distinctif dans le contrat.
**C — Recommandation** : brancher sur le profil de l'appelant résolu côté serveur
(`CurrentUserService`), jamais sur une déclaration du client : DJ/DJA → suppression immédiate ;
JURISTE → vérifier qu'il est bien l'auteur (403 sinon) puis créer la demande ; ASSISTANTE/SH
(peuvent charger, RG-GED-02, mais ne sont mentionnés par aucune RG de suppression) → 403.
Garde-fou additionnel non documenté mais nécessaire : refuser (409) une nouvelle demande tant
qu'une demande `EN_ATTENTE` existe déjà pour le document, pour éviter l'accumulation de demandes
concurrentes sur le même document.
**Statut (2026-09-06, M8)** : appliqué — `GedService.supprimerOuDemander` retourne une enveloppe
interne (`SuppressionResult`) que `GedController` traduit en 200/202 ; vérifié en conditions
réelles (juriste non-auteur → 403, auteur → 202, doublon de demande → 409, DJ → 200 direct).

### Q-48 ✅ Un seul type d'alerte pour tout le cycle de vie de la suppression — tranché (M8)
La liste fermée de `TypeAlerte` (Q-22) ne réserve qu'**un** type à la GED-suppression
(`SUPPRESSION_DOCUMENT`), alors que RG-GED-05/09 distinguent au moins quatre événements
notifiables : demande soumise (→ DJ/DJA), demande approuvée (→ demandeur), demande rejetée (→
demandeur), suppression directe DJ/DJA (→ auteur du chargement) — sans compter la relance à 72 h
(UC-GED-03 alt. 6-a).
**C — Recommandation** : réutiliser `SUPPRESSION_DOCUMENT` pour les cinq cas, la nuance portée par
le texte libre `declencheur` — même principe déjà établi pour `TypeAlerte.ALARME` (Q-04, un seul
type pour toute échéance d'alarme, quel que soit son objet).
**Statut (2026-09-06, M8)** : appliqué et vérifié en conditions réelles — les 5 déclenchements
(demande, approbation, rejet, suppression directe, relance) sont bien distinguables côté client
via `declencheur`, tous historisés sous `SUPPRESSION_DOCUMENT`.

### Q-49 ✅ Rapport journalier GED : périmètre des 4 indicateurs — tranché (M8)
FR-GED-04 nomme `dossiersManipules`, `piecesAjoutees`, `affairesNouvelles`, `planificationActes[]`
sans définir leur mode de calcul exact, ni les entités/modules à consulter pour les obtenir.
**C — Recommandation** :
- `dossiersManipules` = nombre de dossiers **distincts** ayant au moins une entrée
  `historique_action` à la date donnée (mesure large de « manipulation », cohérente avec RG-DOS-06
  qui historise déjà toute action sur un dossier) ;
- `piecesAjoutees` = nombre de `document` dont `date_chargement` tombe ce jour-là ;
- `affairesNouvelles` = nombre de `dossier` dont `created_at` tombe ce jour-là ;
- `planificationActes[]` = audiences au statut `PLANIFIEE` dont `date_planifiee` est cette date
  (référence, dossier, date) — réutilise `AudienceRepository` (M6) plutôt que de dupliquer une
  notion de planification propre à la GED.
Conséquence architecturale : `ged` dépend donc aussi du module `audience`, pas seulement de
`dossier`/`alerte` comme déclaré initialement dans `FUNCTIONAL_MAP.md` §6 — mis à jour en
conséquence.
**Statut (2026-09-06, M8)** : appliqué — `GedService.rapportJournalier`, zéros/liste vide
retournés pour une date sans donnée (vérifié en conditions réelles : dossiers/pièces du jour
comptés correctement, date passée sans activité → tous les indicateurs à zéro).

### Q-50 ✅ Bug — référence circulaire au démarrage (`MinioConfig`) — corrigé (M8)
Un `@PostConstruct` sur `MinioConfig` appelant sa propre méthode `@Bean minioClient()` pour créer
le bucket applicatif provoque `BeanCreationException: Requested bean is currently in creation`
(le proxy CGLIB de la classe `@Configuration` tente de résoudre le bean `minioClient` alors que
`MinioConfig` elle-même n'a pas fini de s'instancier) — détecté en validation manuelle M8 (tout
dépôt de document échouait avec « The specified bucket does not exist », le bucket n'ayant jamais
été créé silencieusement en amont).
**C** : déplacer la vérification/création du bucket dans un `ApplicationRunner` **distinct**,
recevant `MinioClient` en paramètre injecté (bean déjà pleinement construit à ce stade), plutôt
qu'un rappel de méthode `@Bean` depuis un hook de cycle de vie de la même classe.
**Statut (2026-09-06, M8)** : appliqué — `MinioConfig.assurerBucketMinio(MinioClient)`. Revérifié
en conditions réelles : bucket créé au premier démarrage, dépôt de document réussi.

### Q-51 ✅ Bug — `@ExceptionHandler(MaxUploadSizeExceededException)` ambigu avec `ResponseEntityExceptionHandler` — corrigé (M8)
Un fichier dépassant `spring.servlet.multipart.max-file-size` (M1) déclenche
`MaxUploadSizeExceededException` **avant** le contrôleur, avec le format d'erreur générique Spring
(`ProblemDetail`), non conforme au contrat (`{code, message, details}`). Ajouter un
`@ExceptionHandler(MaxUploadSizeExceededException.class)` classique sur `GlobalExceptionHandler`
(qui étend `ResponseEntityExceptionHandler`) provoque une `IllegalStateException: Ambiguous
@ExceptionHandler method` au démarrage : `ResponseEntityExceptionHandler` gère déjà ce type via
son dispatcher interne `handleException(Exception, WebRequest)` — détecté par `contextLoads()`
(`AudiencesApplicationTests`) en validation M8.
**C** : `ResponseEntityExceptionHandler` expose un point d'extension protégé dédié,
`handleMaxUploadSizeExceededException(...)`, à **surcharger** (`@Override`) — même mécanisme déjà
utilisé pour `handleHttpMessageNotReadable`/`handleMissingServletRequestParameter`/
`handleHttpRequestMethodNotSupported` — plutôt qu'un nouvel `@ExceptionHandler` concurrent.
**Statut (2026-09-06, M8)** : appliqué — `GlobalExceptionHandler.handleMaxUploadSizeExceededException`
(override). `contextLoads()` de nouveau vert ; testé en conditions réelles (fichier > 10 Mo → 400
`ERR-008`, format conforme au contrat).

### Q-52 ✅ Constitution : un seul type d'alerte, et aucune notification possible du prestataire sans compte — tranché (M9)
Comme pour la suppression de document (Q-48), la liste fermée de `TypeAlerte` (Q-22) ne réserve
qu'**un** type à la constitution (`CONSTITUTION_A_SIGNER`), alors que RG-INT-08 distingue trois
événements : demande soumise (→ SH), validation (→ prestataire constitué), rejet (→ juriste
demandeur). De plus, FR-INT-07 exige de notifier « l'avocat via son compte, **l'autre prestataire
par email** » — mais un `AutrePrestataire` n'a par construction **aucun compte** (RG-INT-02) et
aucune infrastructure SMTP n'est fournie au projet (même constat que Q-37).
**C — Recommandation** : réutiliser `CONSTITUTION_A_SIGNER` pour les trois événements (nuance
portée par `declencheur`, précédent Q-04/Q-48) ; notifier l'`Avocat` s'il possède déjà un
`Utilisateur` provisionné (recherche par `compteKeycloak`) ; ne **pas** tenter de notifier un
`AutrePrestataire` — documenter l'impossibilité plutôt que l'omettre silencieusement.
**Statut (2026-09-06, M9)** : appliqué — `ConstitutionService.notifierPrestataire` ne notifie que
si `prestataire instanceof Avocat` et qu'un `Utilisateur` existe pour son `compteKeycloak` ; testé
en conditions réelles (avocat sans compte réel → aucune notification, sans erreur ; autre
prestataire → aucune notification, sans erreur — comportement identique, vérifié distinctement).

### Q-53 ✅ Répertoire des avocats : calcul des indicateurs, non persistance — tranché (M9)
`Avocat.indicateurCharge`/`indicateurPerformance` sont des colonnes **stockées** (schéma M2,
valeur par défaut 0) mais RG-INT-01 ne précise ni leur mode de calcul ni s'il faut les maintenir à
jour en écriture (à chaque affectation/désaffectation, à chaque délibéré) ou les calculer à la
demande.
**C — Recommandation** : calculer les deux indicateurs **à la demande**, dans `GET
/repertoire/avocats`, plutôt que de maintenir des colonnes dénormalisées à travers plusieurs
modules (`dossier`, `delibere`) — plus simple, toujours à jour, pas de couplage d'écriture entre
modules. Sémantique retenue : `indicateurCharge` = nombre de dossiers **actifs** (au moins une
étape hors `CLOTURE`/`ARCHIVE`), distinct du compteur brut de M5 (`CHARGE_MAX_AVOCAT`, Q-38, qui
compte **toute** affectation) ; `indicateurPerformance` = taux de délibérés `FAVORABLE` sur les
dossiers affectés — seule mesure de « succès » disponible avant le module Décisions définitives
(M12) ; `0` si l'avocat n'a encore aucun délibéré (absence de donnée, pas une contre-performance).
Les colonnes stockées `Avocat.indicateurCharge`/`indicateurPerformance` restent donc **non
utilisées** par ce jalon (vestigiales, comme `dossier.champ_alarme` supplanté par la table
`alarme` en M6).
**Statut (2026-09-06, M9)** : appliqué — `RepertoireAvocatsService.lister`, tri par charge
croissante, recherche par nom (`?nom=`). Extension de dépendance du module `intervenant` vers
`dossier` (`DossierAvocatRepository.compterDossiersActifsParAvocat`, nouvelle méthode) et
`delibere` (`DelibereRepository.calculerPerformanceParAvocat`, nouvelle méthode) — non déclarée
dans `FUNCTIONAL_MAP.md` §5 à l'origine, mise à jour en conséquence. Vérifié en conditions
réelles : charge et performance corrects sur un dossier actif, tri croissant sur plusieurs avocats.

### Q-27 ✅ `dossier.parties` défini deux fois — tranché (M2)
Le dictionnaire REF-06 §2.1.2 déclare `parties JSONB` **deux fois**, avec deux structures
différentes : `{demandeur, defendeur}` et `{demandeur, defendeur, client}`. Le contrat d'API et
le script SQL retiennent `{demandeur, defendeur}`, le client étant porté par `client_id`.
**C** : retenir `{demandeur, defendeur}`.
**Statut (2026-09-04, M2)** : appliqué — `V1__schema_initial.sql` ne retient qu'une définition.

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

### Q-32 ✅ Notifications applicatives : WebSocket ou polling ? — tranché et implémenté (M5)
NF-ALR-02 fait du canal applicatif le canal **obligatoire**, avec délai < 1 min. Le code contient
un socle STOMP/SockJS (utilisé pour un chat hors périmètre).
**C** : conserver l'infrastructure WebSocket pour le push de notifications (sécurisée par Keycloak),
avec `GET /alertes/mes-notifications` comme repli.

**Statut (2026-09-04, M5)** : appliqué. `AlerteService.declencher` persiste systématiquement
l'alerte puis tente un push best-effort via `SimpMessagingTemplate.convertAndSendToUser` (session
STOMP authentifiée par `keycloakId`, cf. M1) ; un échec de push (destinataire hors ligne, broker
indisponible) est journalisé mais ne fait jamais échouer le déclenchement — l'alerte reste dans
tous les cas récupérable via `GET /alertes/mes-notifications`. Voir aussi Q-37 (canal).

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
| 2026-09-04 | Q-17 | Code de retour `201 Created` sur `POST /deliberes/{id}/prorogations`, y compris au-delà du seuil ; `alerte:true` dans le corps | Implémenté M7 |
| 2026-09-04 | Q-18 | Le seuil se déclenche quand `compteur > seuilProrogations` (11e prorogation avec le défaut 10), une seule fois | Implémenté M7 |
| 2026-09-04 | Q-22 | Liste réunie des 16 types d'alerte retenue (cf. recommandation Q-22) | Prévu M2 (contrainte CHECK) et M5 |
| 2026-09-04 | Q-26 | La logique de la conception/contrat prévaut sur celle de RG-DOS-09 au sens strict : `seuil_montant` reste `NULL` par défaut (seuil global applicable), renseigné uniquement en cas de dérogation validée | Prévu M2 (contrainte SQL), M4 |
| 2026-09-04 | Q-11 | Validation `ET` des champs conditionnels appliquée en couche service (contrainte SQL `OR` conservée comme filet de sécurité) ; catégorie déduite de la nature via `nature_dossier` | Implémenté M4 |
| 2026-09-04 | Q-12 | `forcer=true` sur `PUT /dossiers/{id}/affectation` pour confirmer un doublon d'affectation (volet dossier uniquement — le volet audience reste ouvert, M6) | Implémenté M4 |
| 2026-09-04 | Q-23 | `POST /clients` créé (🆕, hors contrat) ; `PUT /clients/{id}` non retenu, aucun besoin identifié à ce stade | Implémenté M4 |
| 2026-09-04 | Q-34 | Nouvel endpoint 🆕 `POST /dossiers/{id}/etapes` pour le positionnement direct d'une étape sans antécédents, avec création automatique des étapes intermédiaires manquantes | Implémenté M4 |
| 2026-09-04 | Q-35 | Les transitions `EN_DELIBERE→DELIBERE_VIDE` et `EN_DELIBERE→EN_COURS` sont exclues de l'endpoint générique de statut et réservées à des endpoints dédiés du module Délibérés | Prévu M7 |
| 2026-09-04 | Q-22 | Liste réunie des 17 types d'alerte (incl. `ALARME`) appliquée à la contrainte `chk_alerte_type` et à l'énumération `TypeAlerte` | Implémenté M2 (schéma) / M5 (usage) |
| 2026-09-04 | Q-32 | Push WebSocket best-effort (`convertAndSendToUser`) à chaque déclenchement, avec `GET /alertes/mes-notifications` comme repli garanti | Implémenté M5 |
| 2026-09-04 | Q-36 | Destinataire de l'alerte `DOUBLON_AFFECTATION` = DJ actifs (supervision) | Implémenté M5 |
| 2026-09-04 | Q-37 | `alerte.canal` reste `APPLICATIF` uniquement en M5 ; Outlook/Teams différés (aucune infrastructure SMTP/webhook fournie au projet) | Implémenté M5, réévaluer si infra fournie |
| 2026-09-04 | Q-38 | Alerte `CHARGE_MAX_AVOCAT` : destinataire = DJ actifs ; calcul planifié placé dans le module `dossier` (dépendance vers `alerte`, jamais l'inverse) | Implémenté M5 |
| 2026-09-04 | Q-04 | Table `alarme` (M2) + `AlarmeService`/`AlarmeEcheanceScheduler` (M6) ; l'alarme reste `ACTIVE` après le déclenchement de son alerte, seule une reprogrammation la marque `TRAITEE` | Implémenté M6 |
| 2026-09-04 | Q-12 | Volet audience du doublon confirmé : `?forcer=true` sur `POST /dossiers/{id}/audiences`, alerte `DOUBLON_AUDIENCE` vers les DJ | Implémenté M6 |
| 2026-09-04 | Q-13 | `POST /dossiers/{id}/alarmes` et `PUT /alarmes/{id}/reprogrammer` implémentés ; `PUT /deliberes/{id}/expedition` reste ouvert | Implémenté M6, reste M7 |
| 2026-09-04 | Q-39 | Destinataires : `RAPPEL_AUDIENCE` → juristes affectés ; `ALARME` → créateur de l'alarme | Implémenté M6 |
| 2026-09-04 | Q-40 | `date_tenue` = date de saisie du compte rendu ; « prochaine date » du calendrier = `date_planifiee` de l'audience | Implémenté M6 |
| 2026-09-04 | Q-41 | Export du calendrier via `?format=JSON\|PDF\|EXCEL` (JSON par défaut), PDFBox + POI | Implémenté M6 |
| 2026-09-06 | Q-13 | `PUT /deliberes/{id}/expedition` implémenté — les 3 endpoints hors OpenAPI sont couverts | Implémenté M7 |
| 2026-09-06 | Q-19 | `Delibere.dateEcheanceRecours` dédiée (pas de réutilisation de l'alarme M6) ; `GET /deliberes/{id}/recours` reste une consultation ; suivi par `DelaiRecoursScheduler` | Implémenté M7 |
| 2026-09-06 | Q-35 | Correction : le rabattement cible `DELIBERE_VIDE→EN_COURS` (pas `EN_DELIBERE→EN_COURS`) — même transition que l'exercice d'un recours, distinguée par l'endpoint et les effets de bord (Q-42) | Implémenté M7 |
| 2026-09-06 | Q-42 | Rabattement (`POST /deliberes/{id}/rabattement`, dédié) vs. exercice d'un recours (endpoint générique) : même transition, endpoints et effets de bord différents (ouverture ou non de l'étape suivante) | Implémenté M7 |
| 2026-09-06 | Q-43 | Bascule automatique `CLOTURE` + alerte `DELAI_RECOURS` implémentées (`DelaiRecoursScheduler`) ; volet « décisions définitives » différé à M12 | Implémenté M7, reste M12 |
| 2026-09-06 | Q-44 | Prorogation : aucune précondition de statut d'étape (structurellement impossible vu le schéma M2) — « l'étape reste EN_DELIBERE » lu comme un constat, pas une garde d'entrée | Implémenté M7 |
| 2026-09-06 | Q-02 | Relance à 72 h des demandes de suppression implémentée (`SuppressionRelanceScheduler`) | Implémenté M8 |
| 2026-09-06 | Q-45 | `document.type_document` : texte libre, aucune taxonomie (à distinguer de `autre_publication.type_document`) | Implémenté M8 |
| 2026-09-06 | Q-46 | Format dérivé de l'extension du fichier (pas du `Content-Type` déclaré) ; poids comparé à `TAILLE_MAX_GED` en complément du plafond Spring statique | Implémenté M8 |
| 2026-09-06 | Q-47 | `DELETE /ged/documents/{id}` : branchement sur le profil résolu serveur (DJ/DJA → immédiat, juriste auteur → demande, sinon 403) ; 409 si demande déjà en attente | Implémenté M8 |
| 2026-09-06 | Q-48 | `TypeAlerte.SUPPRESSION_DOCUMENT` réutilisé pour les 5 événements du cycle de suppression, nuance portée par `declencheur` | Implémenté M8 |
| 2026-09-06 | Q-49 | Rapport journalier : 4 indicateurs définis (historique_action, document, dossier, audience) ; `ged` dépend aussi de `audience` | Implémenté M8 |
| 2026-09-06 | Q-50 | Bug corrigé : bootstrap du bucket MinIO déplacé dans un `ApplicationRunner` dédié (référence circulaire `@PostConstruct`/`@Bean`) | Corrigé M8 |
| 2026-09-06 | Q-51 | Bug corrigé : override du point d'extension `handleMaxUploadSizeExceededException` au lieu d'un `@ExceptionHandler` concurrent | Corrigé M8 |
| 2026-09-06 | Q-03 | Circuit `DemandeConstitution` complet implémenté (`ConstitutionService`) | Implémenté M9 |
| 2026-09-06 | Q-28 | Affectation (interne, M4) et constitution (formalité SH, M9) restent deux circuits indépendants, non séquencés | Tranché M9 |
| 2026-09-06 | Q-52 | `TypeAlerte.CONSTITUTION_A_SIGNER` réutilisé pour les 3 événements du cycle de constitution ; aucune notification pour un prestataire sans compte (documenté, pas implémenté) | Implémenté M9 |
| 2026-09-06 | Q-53 | Indicateurs du répertoire calculés à la demande (non persistés) ; `intervenant` dépend aussi de `dossier`/`delibere` | Implémenté M9 |

Les autres questions (P1/P2) restent ouvertes et seront tranchées au fil des jalons concernés,
au plus tard avant le module qui en dépend (cf. tableau des prérequis dans `DEVELOPMENT_ROADMAP.md`).
