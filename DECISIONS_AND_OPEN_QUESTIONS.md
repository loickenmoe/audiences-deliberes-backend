# DECISIONS_AND_OPEN_QUESTIONS

> Contradictions documentaires, ambiguïtés et lacunes nécessitant un **arbitrage explicite**.
> Aucune de ces questions n'a été tranchée silencieusement.
> Dernière mise à jour : 2026-09-15 (jalon M22 — Q-100 à Q-106 : pagination généralisée, filtres, recherche au fil de la frappe).
> Précédente : 2026-09-15 (jalon M21 — Q-99, index adossés aux tris des listes paginées).
> Précédente : 2026-09-14 (jalon M20 — Q-98, pièce jointe facultative au compte rendu
> d'audience). Q-70 à Q-98 ont toutes été ouvertes par la consommation réelle de l'API par le
> frontend, jalons M16 à M20 : ce sont les défauts qu'aucun test backend ne pouvait voir.
> Précédente : 2026-09-07 (après jalon M15 — voir statuts R-01 à R-08, Q-01 à Q-69 ;
> **R-06 corrigé**, **Q-24 close**, et **Q-59/Q-60/Q-67 arbitrées** à ce jalon).

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

### R-06 ✅ Provisionnement d'un utilisateur en première connexion incompatible avec un endpoint en lecture seule — découvert en validation manuelle M7, **corrigé en M15**

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
**✅ Statut final (2026-09-07, M15)** : **corrigé**. La création est extraite dans
`UtilisateurProvisioningService`, porteur de `@Transactional(propagation = REQUIRES_NEW)` — une
classe distincte étant nécessaire, un appel interne n'aurait pas traversé le proxy transactionnel.
`CurrentUserService` reste en lecture seule et délègue. Le service reteste l'existence dans la
nouvelle transaction (deux requêtes concurrentes du même nouvel utilisateur ne créent qu'une ligne).
Non-régression verrouillée par `ProvisionnementLectureSeuleIntegrationTest` : un appelant
`@Transactional(readOnly = true)` provisionne désormais avec succès contre un PostgreSQL **réel**
— seul contexte où le défaut se manifeste, ce qui explique qu'il ait survécu à 8 jalons de tests
unitaires (repository mocké) et de validations manuelles (utilisateurs déjà provisionnés).
**Confirmé en conditions réelles** lors de la validation manuelle M15, la base étant repartie
vierge : la première requête de `sh.test` (jamais provisionné) sur `GET /alertes/mes-notifications`
— endpoint en lecture seule — a répondu **200**, et la table `utilisateur` contenait ensuite
exactement une ligne, créée par cet appel.

### R-07 🟡 Démarrage complet de l'application intermittent dans cet environnement de session — non lié au code applicatif, découvert en validation M10
`mvn test` échoue systématiquement sur `AudiencesApplicationTests.contextLoads` (le seul test à
démarrer le contexte Spring complet) avec `NoClassDefFoundError: Alerte` lors de l'introspection de
`AlerteMapperImpl` (généré par MapStruct) par `AutowiredAnnotationBeanPostProcessor` —
`ClassNotFoundException: Alerte` en cause racine, alors que
`com.afriland.audiences.alerte.domain.Alerte` est bien présent, non corrompu (taille normale), sur
le classpath compilé (`target/classes`), et que `AlerteMapperImpl` fonctionne normalement dans tous
les autres tests (`AlerteServiceTest`, etc., qui le mockent plutôt que de charger tout le contexte).
`mvn spring-boot:run` a échoué à l'identique sur **trois tentatives successives** (avec puis sans
DevTools/`RestartClassLoader`, puis en réutilisant les classes déjà compilées) avant qu'une
**quatrième tentative démarre normalement** et reste stable — comportement **intermittent**, pas un
blocage dur : une fois démarrée, l'application fonctionne normalement (validation manuelle complète
réalisée dans la foulée, voir ci-dessous).
**Reproduit à l'identique sur `dev` non modifié** (`fa52178`, avant tout code M10) pour le test
`contextLoads` — donc **structurel à cet environnement de session**, pas une régression introduite
par un module applicatif. Signature typique d'une classe ayant échoué à s'initialiser une première
fois dans la JVM (le nom court sans package dans `NoClassDefFoundError` en est l'indice classique) ;
le caractère intermittent suggère une cause transitoire (verrouillage antivirus/OneDrive d'un
fichier `.class` fraîchement écrit, ou concurrence de classloading) plutôt qu'un défaut de code.
**C — Recommandation** : investigation dédiée hors périmètre d'un module fonctionnel si le problème
persiste ; en pratique, **réessayer le démarrage** (`mvn spring-boot:run`) suffit à contourner
l'échec quand il se produit.
**Statut (2026-09-07, M10)** : non corrigé — pré-existant (reproduit sur `dev`), hors périmètre.
Impact sur ce jalon : nul au final — après plusieurs tentatives, l'application a démarré
normalement et la **validation manuelle complète de bout en bout a été réalisée** (voir §M10 de
`DEVELOPMENT_ROADMAP.md`). Reste un point de vigilance pour M14 (durcissement final) : fiabiliser
`contextLoads` (qui échoue, lui, de façon systématique dans les runs `mvn test`) et surveiller la
récurrence de l'intermittence au démarrage.
**Suivi (2026-09-07, M14)** : l'intermittence ne s'est **plus manifestée** sur les jalons M11 à M14
(`contextLoads` vert à chaque exécution complète, et le nouveau
`ParcoursEndToEndIntegrationTest` démarre lui aussi le contexte complet, 10/10 verts). Risque
maintenu en veille — aucune correction n'a été apportée, la cause racine n'ayant jamais été
identifiée ; sa disparition n'est pas expliquée non plus.

### R-08 🟡 Documentation d'API exposée sans authentification — relevé par la revue de sécurité M14
`SecurityConfig.PUBLIC_PATHS` laisse `/v3/api-docs/**`, `/swagger-ui/**` et `/swagger-ui.html`
en accès **anonyme**. En développement c'est le comportement attendu ; en production, cela publie
à tout visiteur la surface complète de l'API (56 endpoints, schémas de requêtes/réponses, rôles
attendus) — une aide directe à la reconnaissance pour un attaquant, sans qu'aucune donnée métier
ne soit exposée pour autant.
**C — Recommandation** : conditionner ces chemins à un profil Spring (`@Profile("!prod")` sur une
configuration dédiée, ou `springdoc.api-docs.enabled=false` en production), ou les protéger par
authentification. Correction hors périmètre de M14 (elle suppose de trancher la stratégie de
profils d'exécution, non définie à ce stade du projet) — mais **à traiter avant toute mise en
production**.
**Statut (2026-09-07, M14)** : non corrigé, documenté. Aucun autre défaut d'autorisation relevé :
les 56 endpoints métier portent tous un `@PreAuthorize` explicite, `anyRequest().authenticated()`
couvre le reste, et l'endpoint WebSocket public (`/ws/**`, nécessaire aux transports de repli
SockJS) est authentifié au niveau de la trame STOMP `CONNECT` par `WebSocketAuthInterceptor`.

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

### Q-24 ✅ Files de travail (listes) non spécifiées — close (M15)
UC-INT-02/03/04, UC-GED-03, UC-INT-06/10 supposent tous des **listes d'éléments en attente**
(« aucune demande en attente de contrôle »). Aucun endpoint de liste n'est spécifié pour les
demandes de frais, les publications, les demandes de suppression, les constitutions.
**C** : ajouter un `GET` de liste filtrable par statut pour chacun de ces workflows.
**Statut (2026-09-07, M10)** : volet **frais** résolu — `GET /frais/demandes?statut=` (endpoint 🆕),
transverse au personnel interne (Assistante/DJA/DJ), restreint à ses propres demandes pour
`ROLE_AVOCAT`. Volets publications, demandes de suppression (GED) et constitutions **toujours
ouverts**, différés aux jalons correspondants (M11 pour les publications ; suppression/constitution
hors périmètre M10, à réévaluer si redemandé).
**✅ Statut final (2026-09-07, M15)** : **close** — les trois volets restants sont livrés (Q-68) :
`GET /publications?statut=` (Assistante), `GET /ged/demandes-suppression?statut=` (DJ/DJA),
`GET /constitutions/prestataires?statut=` (SH). Chaque écran « … en attente » de
`FUNCTIONAL_MAP.md` a désormais sa source de données.

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
**Statut (2026-09-04, M4 ; rôle corrigé en M14)** : implémenté — `POST /dossiers/{id}/etapes`
(`CreerEtapeRequest{type, statutCycleVie}`, **`ROLE_JURISTE`** — cette fiche annonçait
`ROLE_SAISIE`, écart relevé par l'audit de conformité M14 et tranché en faveur du code, cohérent
avec l'endpoint frère `PATCH …/etapes/{etapeId}/statut` du même UC-DOS-04, cf. Q-66).
`DossierService.creerEtape` crée
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
**Révisé par Q-78 (2026-09-11)** : le rabattement part désormais de « En délibéré », sur un délibéré
en attente (US 3.4, UC-DEL-05, RG-DEL-07) — le juge rouvre les débats *avant* de statuer. La
distinction avec l'exercice d'un recours demeure, et devient plus nette : `DELIBERE_VIDE → EN_COURS`
n'a plus qu'un seul sens, le recours.

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
**Révisé par Q-78 (2026-09-11)** : la précondition « En délibéré » est rétablie — elle était
impossible tant qu'un délibéré naissait vidé ; il naît désormais à la mise en délibéré, sans
résultat. Une prorogation ne porte plus que sur un délibéré en attente.

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

### Q-54 ✅ Frais : « avocat constitué », anti-doublon de facture et notifications de rejet — tranché (M10)
FR-INT-01 exige un avocat « **constitué** sur le dossier », mais Q-28 a déjà établi qu'un avocat
peut être **affecté** en interne (`dossier_avocat`) avant même l'envoi de sa lettre de constitution
formelle (module `intervenant`, M9) — les deux notions (affectation, constitution signée) sont
distinctes et il n'existe aucune contrainte documentaire imposant que la seconde ait eu lieu avant
qu'un avocat puisse déposer des frais.
**C — Recommandation** : retenir `dossier_avocat` (affectation) comme critère opérationnel de
« constitué sur le dossier », par cohérence avec Q-28 plutôt que d'ajouter une dépendance dure au
statut `VALIDEE` d'une `demande_constitution` (qui n'est même pas obligatoire pour tous les
prestataires, cf. `AutrePrestataire`).
**Statut (2026-09-07, M10)** : appliqué — `FraisService.deposer` vérifie
`DossierAvocatRepository.existsByDossierIdAndAvocatId`.

Par ailleurs, FR-INT-02 mentionne un second contrôle « facture en double → blocage 409 » au niveau
de l'endpoint de conformité, alors que FR-INT-01 impose déjà l'unicité de `referenceFacture` au
dépôt (contrainte SQL `UNIQUE`, portant sur **toutes** les demandes quel que soit leur statut, y
compris `REJETEE` — US 4.3). Comme `referenceFacture` est fixée au dépôt et jamais modifiée
ensuite, un doublon ne peut structurellement plus être détecté à l'étape de conformité (il aurait
déjà bloqué le dépôt) — même situation que Q-44 (précondition déjà garantie en amont).
**Statut** : la mention de FR-INT-02 est traitée comme une reformulation de la même contrainte, pas
comme un second contrôle distinct ; aucune vérification supplémentaire n'est implémentée à
`PUT /frais/demandes/{id}/conformite`.

Enfin, seul FR-INT-02 mentionne explicitement « notifie l'avocat » lors d'un rejet ; FR-INT-03 et
FR-INT-04 se limitent à « rejet → motif obligatoire » sans préciser de destinataire, bien que les
trois renvoient à la **même** règle RG-INT-03. Ne notifier l'avocat qu'au premier des trois rejets
possibles laisserait le déposant sans nouvelle de sa demande dans les deux autres cas.
**C — Recommandation** : notifier systématiquement l'avocat sur **toute** issue `REJETEE`, quelle
que soit l'étape, par cohérence de traitement de RG-INT-03 (complète le texte plutôt que de le
contredire).
**Statut (2026-09-07, M10)** : appliqué — `FraisService.rejeter` (méthode commune aux trois
branches de rejet) notifie systématiquement l'avocat, type `NOUVELLE_DEMANDE_FRAIS` (réutilisation
du type unique du workflow frais, même principe que Q-48/Q-52). `DOUBLON_FACTURE` reste défini
dans l'énumération `TypeAlerte` (Q-22) mais **inutilisé** par ce jalon : aucun texte fonctionnel ne
décrit de destinataire à notifier pour l'anti-doublon (simple blocage HTTP 409, pas d'alerte).

### Q-55 ✅ Publications : un seul point de validation pour les deux sous-types, chemin généralisé — tranché (M11)
Le contrat ne numérote que `PUT /publications/cr-audience/{id}/validation` (#30, US 5.2) pour
valider un compte rendu d'audience, mais US 5.3 (`POST /publications/autres`) dépose aussi des
publications au statut `DEPOSE` qui notifient l'Assistante — sans qu'aucun endpoint ne soit prévu
pour les faire passer à `VALIDE`/`REJETE`. RG-INT-06 (autres publications) et RG-INT-10 (validation)
ne distinguent pourtant pas le sous-type : les deux partagent la même table `publication` et le même
`statut_validation`.
**C — Recommandation** : un point d'entrée unique `PUT /publications/{id}/validation`, sans le
segment `cr-audience` du chemin contracté (qui n'aurait plus de sens appliqué à une « autre »
publication), plutôt que d'inventer un second endpoint non prévu pour ce seul sous-type.
**Statut (2026-09-07, M11)** : appliqué — `PublicationController.valider`/`PublicationService.valider`
opèrent sur `Publication` (base), quel que soit le sous-type concret.

### Q-56 ✅ `autre_publication` sans colonne de stockage — corrigé, sans dépendance à un `document` GED — tranché (M11)
Le script SQL de `autre_publication` (M2) ne porte que `type_document` : aucune colonne ne permet
de retrouver le fichier déposé par l'avocat (US 5.3) — même lacune que `document.chemin_stockage`
(Q-07) à l'origine.
**C — Recommandation** : ajouter `autre_publication.chemin_stockage`, alimentée via le
`MinioStorageService` du module `ged` (dépendance déjà déclarée pour l'Épic 5), **sans** créer de
ligne `document` dans la table GED — une publication a son propre cycle de vie (validation Assistante,
restriction d'accès) distinct de celui d'un document GED (suppression avec accord DJ/DJA, M8),
contrairement à la lettre de constitution (M9, Q-52) qui, elle, devait rester consultable dans la
GED du dossier au même titre que n'importe quelle autre pièce.
**Statut (2026-09-07, M11)** : appliqué — `V4__publication_commentaires_correspondances.sql`,
`AutrePublication.cheminStockage`.

### Q-57 ✅ `restriction_acces` : renseignée par l'avocat au dépôt, aucun endpoint de modification a posteriori — tranché (M11)
US 5.4 exploite `publication.restriction_acces` (403/ERR-009 si renseignée et différente de
l'utilisateur courant) mais aucune US ne précise ni qui la renseigne, ni via quel endpoint.
**C — Recommandation** : champ optionnel du corps de dépôt (`POST /publications/cr-audience` et
`POST /publications/autres`), laissé à la discrétion de l'avocat déposant ; aucun endpoint de
modification a posteriori n'étant demandé, aucun n'est ajouté.
**Statut (2026-09-07, M11)** : appliqué — `restrictionAcces` (identifiant `Utilisateur`) sur les
deux requêtes de dépôt.

### Q-58 ✅ `jurisprudence` sans colonne de stockage + dossier optionnel — tranché (M12)
FR-DEF-03 exige un fichier PDF pour chaque jurisprudence, mais le script SQL de `jurisprudence`
(M2) n'a aucune colonne de stockage — même lacune que `autre_publication` (Q-56) et
`document.chemin_stockage` (Q-07). De plus, `dossierId` est explicitement **optionnel** pour une
jurisprudence (précédent documenté sans dossier interne associé), alors que `document.dossier_id`
est `NOT NULL` — une jurisprudence sans dossier ne pourrait donc **jamais** devenir un `document`
GED, contrairement à `autre_publication` (toujours rattachée à un dossier).
**C — Recommandation** : ajouter `jurisprudence.chemin_stockage`, déposée via le
`MinioStorageService` du module `ged`, sans ligne `document` GED (même principe que Q-56) ; pour
le cas sans dossier, une nouvelle méthode de stockage générique (préfixe `jurisprudence/` au lieu
de `dossiers/{id}/`) plutôt que de forcer un `dossierId` arbitraire.
**Statut (2026-09-07, M12)** : appliqué — `V5__jurisprudence_stockage.sql`,
`MinioStorageService.deposerSansDossier` (la méthode de dépôt privée commune prend désormais un
préfixe de clé en paramètre au lieu d'un `dossierId`, sans changer le format de clé des appelants
existants).

### Q-59 ✅ Adjudication vs condamnation : précondition d'étape non symétrique — tranché (M12, **confirmé M15**)
FR-DEF-01 (adjudication) exige explicitement une étape `CLOTURE` et un `etapeId`. FR-DEF-02
(condamnation) ne mentionne ni précondition d'étape ni `etapeId` dans son texte, alors que le
parcours P5 (§9) présente les deux comme des alternatives équivalentes suivant l'épuisement des
recours (« Recours épuisés → adjudication **ou** condamnation pécuniaire »), ce qui suggérerait la
même précondition.
**C — Recommandation** : implémenter chaque règle **littéralement** telle qu'écrite plutôt que
d'harmoniser silencieusement — `etapeId` optionnel et aucune précondition de statut pour la
condamnation, à la différence de l'adjudication. Une condamnation pécuniaire peut en effet naître
indépendamment de la clôture complète du dossier (ex. jugement exécutoire par provision).
**Statut (2026-09-07, M12)** : appliqué tel quel (asymétrie assumée, pas une omission de code) —
`DecisionDefinitiveService.creerCondamnation` n'impose aucune précondition d'étape.
**✅ Statut final (2026-09-07, M15)** : **close** — recommandation **retenue par le porteur du
projet**. L'asymétrie est donc intentionnelle et devient la règle : une condamnation pécuniaire
peut naître indépendamment de la clôture du dossier, `etapeId` restant optionnel. Aucune
modification de code (le comportement livré en M12 est déjà celui-ci, revérifié en M15). Si la
Direction Juridique demandait plus tard l'harmonisation, elle constituerait un **durcissement** de
l'API (des appels aujourd'hui acceptés seraient refusés) et devrait être versionnée comme telle.

### Q-60 ✅ Adjudication/condamnation n'archivent pas automatiquement l'étape — tranché (M12, **confirmé M15**)
P5 (§9) enchaîne « adjudication/condamnation → archivage jurisprudentiel → étape `ARCHIVE` », ce qui
pourrait suggérer un effet de bord automatique. Aucune FR-DEF-0x ne décrit explicitement cette
transition comme résultant de l'enregistrement d'une décision définitive.
**C — Recommandation** : ne pas inventer de transition automatique non spécifiée — l'endpoint
générique existant (`PATCH /dossiers/{id}/etapes/{etapeId}/statut`, M4) reste la voie manuelle pour
faire passer l'étape à `ARCHIVE` une fois la décision et l'archivage jurisprudentiel effectués.
**Statut (2026-09-07, M12)** : appliqué tel quel — aucun effet de bord ajouté sur `EtapeProcedure`
lors de la création d'une adjudication ou d'une condamnation.
**✅ Statut final (2026-09-07, M15)** : **close** — recommandation **retenue par le porteur du
projet**. Le passage à `ARCHIVE` reste **manuel**, via `PATCH /dossiers/{id}/etapes/{etapeId}/statut`
(M4) : le parcours P5 décrit un enchaînement métier, pas un automatisme, et l'archivage
jurisprudentiel qu'il intercale est lui-même une action distincte (`POST /jurisprudences`).
Aucune modification de code. Une automatisation reste possible en jalon ultérieur si le besoin est
exprimé ; elle ne casserait aucun contrat existant (ajout d'un effet de bord, pas d'une contrainte).

### Q-61 ✅ Jurisprudence : contraintes de format/poids littérales, sans clé `configuration_systeme` — tranché (M12)
FR-DEF-03 fixe « fichier PDF ≤ 10 Mo » directement dans le texte fonctionnel, contrairement à GED
(RG-GED-02) dont les contraintes analogues (`FORMATS_GED`, `TAILLE_MAX_GED`) sont des clés
`configuration_systeme` modifiables par le DJ (CONF01-07). Aucune clé équivalente n'est listée pour
la jurisprudence.
**C — Recommandation** : contraintes **littérales** dans `JurisprudenceService` (PDF uniquement,
10 Mo), sans ajouter de clé de configuration non demandée.
**Statut (2026-09-07, M12)** : appliqué tel quel. Observation : la limite globale
`spring.servlet.multipart.max-file-size` (M1, Q-51) vaut déjà 10 Mo — un fichier dépassant cette
taille est donc intercepté par `GlobalExceptionHandler` avant même d'atteindre le contrôle du
service (même code `ERR-008` dans les deux cas) ; le contrôle applicatif reste néanmoins utile et
testé unitairement (indépendant de la couche servlet), en défense en profondeur.

### Q-62 ✅ Bug — `decision_definitive.type VARCHAR(20)` trop étroite pour sa propre contrainte CHECK — corrigé (M12)
`V1__schema_initial.sql` (M2) déclare `decision_definitive.type VARCHAR(20)` avec
`CHECK (type IN ('ADJUDICATION', 'CONDAMNATION_PECUNIAIRE'))` — or `'CONDAMNATION_PECUNIAIRE'` fait
23 caractères, dépassant la largeur de sa propre colonne. Toute tentative d'enregistrement d'une
condamnation échouait avec `PSQLException: value too long for type character varying(20)` (500),
alors qu'une adjudication (`'ADJUDICATION'`, 12 caractères) fonctionnait normalement — détecté en
validation manuelle M12 (le premier test réel du chemin condamnation).
**C** : élargir la colonne à `VARCHAR(30)`.
**Statut (2026-09-07, M12)** : appliqué — `V6__decision_definitive_type_largeur.sql`
(`ALTER COLUMN type TYPE VARCHAR(30)`), `DecisionDefinitive` : `@DiscriminatorColumn(..., length = 30)`
mis à jour en cohérence (`ddl-auto=validate` aurait sinon rejeté le décalage de longueur au
démarrage). Revérifié en conditions réelles : condamnation créée et son statut mis à jour avec succès.

### Q-63 ✅ Contenu de `GET /rapports` : aucun champ nommé par le contrat — tranché (M13)
FR-REP-01 (US 8.4) ne nomme aucun champ de sortie (contrairement à FR-REP-02, qui liste
explicitement 6 indicateurs pour `GET /tableau-de-bord`) — seuls `type`, `dateDebut`, `dateFin`,
`format` et les contraintes de performance/204 sont spécifiés. « toutes » (colonne Entités) suggère
un rapport transversal, sans détail de contenu.
**C — Recommandation** : dériver le contenu du rapport des mêmes indicateurs métier que le tableau
de bord, mais **restreints à la période** `[dateDebut, dateFin]` plutôt que temps réel — cohérent
avec le seul autre précédent de « rapport périodique » du projet (`GedService.rapportJournalier`,
M8, également scopé à une date). `type` (JOURNALIER…ANNUEL) reste alors une simple étiquette de
période sans effet de calcul, `dateDebut`/`dateFin` étant explicitement fournies par l'appelant
(contrairement à `PeriodeCalendrier`, Épic 2, qui calcule sa propre `dateFin` à partir du type).
**Statut (2026-09-07, M13)** : appliqué — `RapportResponse` : `nbNouveauxDossiers`,
`nbAudiencesTenues`, `nbDemandesFraisDeposees`, `nbDeliberesEnregistres`, `tauxSuccesRecouvrement`,
`provisionsReprises`, tous scopés à la période. **204** si les quatre compteurs sont nuls
(RG-REP-02).

### Q-64 ✅ « `tauxSuccesRecouvrement` » et « `provisionsReprises` » : définitions non précisées — tranché (M13)
FR-REP-02 nomme ces deux indicateurs sans en définir le mode de calcul.
**C — Recommandation** :
- `tauxSuccesRecouvrement` = proportion de délibérés `FAVORABLE` parmi les délibérés rendus sur des
  dossiers de catégorie `RECOUVREMENT` — même principe que l'indicateur de performance par avocat
  (Q-53), mais agrégé toutes affectations confondues plutôt que par avocat ; `0` si aucun délibéré
  (absence de donnée, pas une contre-performance, même convention qu'en Q-53).
- `provisionsReprises` = somme des `reliquat` des `Adjudication` dont `repriseProvision = true` —
  seule donnée monétaire liée à une « reprise de provision » disponible dans le schéma (M12).
**Statut (2026-09-07, M13)** : appliqué — `DelibereRepository.calculerTauxSuccesRecouvrement(Periode)`,
`DecisionDefinitiveRepository.sommeProvisionsReprises(Periode)`.

### Q-65 ✅ Tests d'intégration bout en bout : deux substitutions assumées — tranché (M14)
Les 9 parcours de `FUNCTIONAL_MAP.md` §9 sont testés contre le **contexte Spring complet** et un
**PostgreSQL réel** (Testcontainers, migré par Flyway), via des appels HTTP réels (`MockMvc`).
Deux dépendances externes ne sont pas conteneurisées :
- **Keycloak** : l'authentification passe par un JWT simulé (`spring-security-test`). Les claims
  produits sont identiques à ceux du vrai jeton (`sub`, `roles`, `given_name`, `family_name`,
  `email`) ; seule la vérification cryptographique est court-circuitée. Motif : le démarrage à
  froid de l'image Keycloak dépasse 5 minutes (mesuré en session), incompatible avec une suite de
  tests. **Conséquence** : le `JwtAuthenticationConverter` applicatif n'étant pas exécuté par le
  post-processeur `jwt()`, les autorités sont fournies explicitement dans le test — le mapping
  claim `roles` → autorité est donc couvert par la validation manuelle (curl + vrais jetons
  Keycloak, faite à chaque jalon), pas par ces tests.
- **MinIO** : `MinioClient` est mocké. Le stockage objet lui-même a été vérifié en conditions
  réelles à chaque jalon concerné (M8 à M12, y compris URL pré-signées et signatures de fichiers).
**C — Recommandation** : substitutions acceptées, périmètre explicite — ces tests valident le
**câblage** (routes, sécurité, transactions, interactions inter-modules, persistance réelle), pas
les intégrations externes ni les règles métier unitaires (déjà couvertes par les tests de service).
**Statut (2026-09-07, M14)** : appliqué — `ParcoursEndToEndIntegrationTest`.

### Q-66 ✅ Audit de conformité endpoint par endpoint : 2 écarts doc/code — corrigés (M14)
L'audit systématique des 56 endpoints (annotations `@PreAuthorize` du code vs tableau
`API_IMPLEMENTATION_STATUS.md`) a relevé **2 divergences**, de même nature : la documentation
annonçait `ROLE_SAISIE`, le code exige `ROLE_JURISTE` (plus strict — `ROLE_JURISTE` est un rôle
composite qui **contient** `ROLE_SAISIE`, l'écart est donc restrictif, jamais permissif) :
- `POST /dossiers/{id}/etapes` (Q-34, M4) ;
- `DELETE /ged/documents/{id}`, branche « demande de suppression » (FR-GED-03, M8).
**C — Recommandation** : aligner la **documentation sur le code** dans les deux cas — le texte
fonctionnel appuie `ROLE_JURISTE` (FR-GED-03 : « Juriste **auteur du chargement** → crée une
demande » ; UC-DOS-04 : la gestion des étapes est l'acte du juriste, comme son endpoint frère
`PATCH …/etapes/{etapeId}/statut`, déjà `ROLE_JURISTE` des deux côtés). Aucun changement de
comportement, donc aucun risque de régression.
**Statut (2026-09-07, M14)** : appliqué — lignes corrigées dans `API_IMPLEMENTATION_STATUS.md`
§1/§8 et dans la fiche Q-34. **Les 54 autres endpoints sont conformes** (chemin, méthode, rôle).

### Q-67 ✅ `DossierResponse.documents` toujours vide : dette de M4 non résorbée en M8 — tranché (M14)
`DossierResponse` porte un champ `documents` depuis M4, dont la Javadoc annonçait « reste vide tant
que le module GED n'existe pas, **M8** ». M8 a livré la GED mais ce champ n'a **jamais** été
raccordé : `GET /dossiers/{id}` renvoie toujours `documents: []`, alors que FR-DOS-03 énonce
« Métadonnées + étapes + historique + **documents** ». Défaut invisible des tests unitaires (le
mapper est mocké) et de la validation manuelle (le champ n'avait jamais été inspecté avec une pièce
réellement chargée) — relevé par les tests bout en bout de M14.
**C — Recommandation** : ne **pas** raccorder le champ. Le faire imposerait une dépendance
`dossier → ged`, inversant la hiérarchie de couches tenue depuis M6 (Q-49, Q-53, Q-54, Q-56) pour
une information déjà servie par l'endpoint dédié `GET /dossiers/{id}/documents` (#43), créé en M8
précisément pour ce besoin. Le champ est conservé (aucune rupture du contrat de réponse déjà
publié) mais documenté comme **vide par construction, à ne pas consommer**.
**Statut (2026-09-07, M14)** : appliqué — Javadoc de `DossierResponse` corrigée, ligne FR-DOS-03 de
`FUNCTIONAL_MAP.md` précisée, assertion explicite dans `ParcoursEndToEndIntegrationTest` (P1).
**✅ Statut final (2026-09-07, M15)** : **close** — recommandation **retenue par le porteur du
projet**. Le champ `documents` reste présent et **vide par construction** ; les pièces d'un dossier
se consomment exclusivement via `GET /dossiers/{id}/documents` (#43). Aucune modification de code.
Le champ n'est **pas** retiré du contrat de réponse : le supprimer serait une rupture pour un gain
nul, la hiérarchie de couches interdisant de toute façon de le peupler. **Point d'attention pour
l'équipe frontend** : ne pas lire `dossier.documents`, appeler l'endpoint dédié.

### Q-68 ✅ Habilitation du frontend : jalon M15 ajouté hors feuille de route initiale — tranché (M15)
La feuille de route s'arrêtait à M14. À la question « le frontend fonctionnerait-il branché en
l'état ? », l'analyse du code a répondu **non** : la surface exposée couvrait strictement la liste
d'endpoints du contrat, laquelle omet plusieurs lectures qu'une interface réelle exige. Trois
familles de manques, toutes vérifiées dans le code avant décision :

1. **Blocage à la première connexion** — R-06 : le provisionnement (INSERT) héritait de la
   transaction de l'appelant ; or un frontend appelle d'abord des endpoints en lecture
   (`GET /alertes/mes-notifications`, `GET /dossiers`). **Tout nouvel utilisateur se prenait un
   500 à sa première connexion.** Défaut connu depuis M7, resté hors périmètre à chaque jalon.
2. **Écrans sans source de données** — `FUNCTIONAL_MAP.md` décrit des écrans (onglets Audiences et
   Délibérés de la fiche dossier, files de travail de l'Assistante, du DJ/DJA et du SH) qu'aucun
   endpoint n'alimente : on pouvait **créer** une audience ou un délibéré, jamais les **relire**
   par dossier. Q-24 n'avait été refermée que pour le volet `frais` (M10).
3. **Impasses fonctionnelles** — aucun endpoint ne permettait de créer un avocat ou un prestataire
   (insertion SQL manuelle à chaque validation de jalon), ni de lire la taxonomie
   `nature_dossier` alors que le backend rejette (400) toute nature hors taxonomie : le formulaire
   de création de dossier ne pouvait pas peupler sa liste déroulante.

**C — Recommandation** : ouvrir un jalon **M15 « habilitation frontend »** plutôt que d'étendre
M14 (déjà clos et mergé) ou de laisser ces manques au frontend. Périmètre strictement limité au
correctif R-06 et aux lectures/administrations manquantes — **aucune règle métier nouvelle**, aucun
changement de comportement des endpoints existants.
**Statut (2026-09-07, M15)** : appliqué — 1 correctif (R-06) et 10 endpoints 🆕 ; Q-24 est
désormais **entièrement close** (les trois volets restants — publications, suppressions,
constitutions — ont leur file de travail). 247 tests verts (couverture 85,8 %) et validation
manuelle de bout en bout sur base vierge avec jetons Keycloak réels : R-06 prouvé corrigé (la ligne
`utilisateur` est créée par une requête sur un endpoint en lecture seule), les 10 endpoints servis
au bon profil et refusés (403) aux autres, filtres et 404 conformes.

**Deux invariants tranchés ici**, faute de user story les décrivant :
- **Création d'un intervenant** — les règles existantes du modèle sont portées par le service :
  un `AVOCAT` exige un compte Keycloak **unique** (400 s'il manque, 409 s'il est déjà pris,
  RG-INT-01) et refuse `notification` ; un `AUTRE_PRESTATAIRE` refuse tout compte applicatif
  (RG-INT-02). Ni modification ni suppression : aucune user story ne les demande.
- **`GET /publications`** — la liste **n'applique pas** la restriction d'accès individuelle de
  Q-57 (que `GET /publications/{id}` applique) : elle sert des profils internes chargés
  d'instruire les publications et n'expose que des métadonnées ; le contenu détaillé reste soumis
  au contrôle unitaire. **À confirmer par la Direction Juridique** si la restriction devait
  s'étendre aux listes.

### Q-69 ✅ Aucun endpoint ne renvoyait les utilisateurs internes : création de dossier impossible depuis une interface — tranché (M15)
Découvert en revisitant la question « le frontend peut-il consommer l'API ? » **après** la
livraison de M15 — c'est-à-dire par le même contrôle qui avait produit Q-68, mais poussé plus loin.

**A — Ce que la documentation exige.** `POST /dossiers` (FR-DOS-01) et
`PUT /dossiers/{id}/affectation` (FR-DOS-02) prennent une liste de juristes affectés ; RG-DOS-03
impose au moins un juriste par dossier. L'écran « Création de dossier » de `FUNCTIONAL_MAP.md` §1
comporte une « section affectation ».

**B — Ce que le code implémente.** `CreerDossierRequest.juristesAffectes` est `@NotEmpty` et attend
des `utilisateur.id` (`DossierService.affecterJuristes` les résout par
`utilisateurRepository.findById`). Or **aucun endpoint n'exposait la table `utilisateur`** : ni
`UtilisateurController`, ni DTO, ni requête. Un frontend ne pouvait donc **pas construire un appel
de création de dossier valide** — le point d'entrée de toute l'application. Symétriquement,
`DossierResponse.juristesAffectes` est un `List<Long>` nu : une fiche dossier ne pouvait afficher
que des identifiants opaques.

Le manque est de la même nature que ceux de Q-68 : M15 avait traité les **avocats**
(`GET /intervenants`) et omis le **personnel interne**. Il n'avait été révélé ni par les tests
(qui lisent l'identifiant en base) ni par les validations manuelles (qui font de même).

**C — Recommandation** : exposer `GET /utilisateurs?profil=` en lecture seule, pendant de
`GET /intervenants`. **Ne pas** exposer de création/modification : les comptes naissent du
provisionnement au premier appel authentifié, Keycloak restant la source de vérité des identités
(REF-05 §1.3).

**Statut (2026-09-07, M15)** : appliqué — `UtilisateurService`/`UtilisateurController`,
`GET /utilisateurs?profil=&inclureInactifs=` (#66), `ROLE_CONSULTATION`. Trois choix tranchés ici :
- **`keycloakId` n'est pas exposé** — identifiant d'authentification sans usage pour le frontend,
  qui ne manipule que l'`id` applicatif attendu par `juristesAffectes`.
- **Comptes actifs seulement par défaut** (`inclureInactifs=false`) : seuls des comptes actifs sont
  affectables à un dossier ; l'historique reste consultable à la demande.
- **`ROLE_CONSULTATION`**, porté par les six profils internes mais **pas par `AVOCAT`** : un
  intervenant externe ne doit pas pouvoir énumérer le personnel de la Direction Juridique. Vérifié
  par un test d'intégration (403).

**Non retenu à ce stade — enrichir `DossierResponse.juristesAffectes`** en objets `{id, nom,
prenom}` plutôt qu'en identifiants nus. Ce serait une **rupture du contrat de réponse déjà publié**
(REF-07), pour un besoin que le frontend couvre en appelant `GET /utilisateurs` une fois et en
mettant la correspondance en cache. **Décision attendue :** si l'équipe frontend juge ce
double-appel gênant, l'enrichissement devra être arbitré et versionné comme une rupture assumée —
même raisonnement que Q-67.

### Q-70 ✅ `ERR-CONFLICT` ne nommait jamais le champ fautif : un client devait le deviner — tranché (M16)

Remonté par le développement du frontend (**QF-21** de son dépôt), en provoquant pour de vrai un
doublon de référence client plutôt qu'en lisant le contrat d'API.

**A — Le constat.** `POST /clients` avec une référence existante renvoyait :

```json
{"code":"ERR-CONFLICT","message":"La référence client 'CLI-001' est déjà utilisée","details":null}
```

`ERR-002`, que le contrat associe à une référence dupliquée, est en réalité réservé aux **dossiers**
(`DossierService`). Les clients — comme huit autres cas — tombaient sur le code générique
`ERR-CONFLICT` : compte Keycloak déjà rattaché, étape déjà ouverte, accord de frais déjà enregistré,
suppression déjà demandée, publication déjà traitée, correspondance déjà transmise, constitution déjà
traitée, statut de demande inattendu. `details` valait toujours `null`.

**B — Pourquoi c'est un défaut du backend et pas du client.** Le champ à corriger n'était déductible
que de l'endpoint appelé. Un frontend devait donc maintenir une table endpoint → champ, correcte
aujourd'hui et fausse le jour où un endpoint gagne un second conflit possible — **sans que rien
n'échoue**, l'utilisateur voyant simplement le mauvais champ surligné.

**C — Ce qui a été écarté.** Mettre le nom du champ dans `details` : ce champ porte déjà du texte
destiné à un humain (`"reference: must not be blank"`, produit par `handleMethodArgumentNotValid`).
Lui confier en plus un identifiant machine aurait rendu les deux inexploitables.

**D — Tranché.** `ErrorResponse` gagne un champ **`champ`**, distinct, annoté
`@JsonInclude(NON_NULL)` : il est **absent** du JSON quand l'erreur ne vise aucun champ. L'ajout est
donc rétrocompatible — aucune réponse existante ne change. `ApiException.conflictSurChamp(code,
message, champ)` le renseigne ; `ApiException.conflict` reste pour les conflits d'**état**, qui ne
visent légitimement rien : désigner un champ au hasard serait pire que se taire.

Cinq sites nomment leur champ : référence client, `compteKeycloak` d'un avocat, référence de dossier
(`ERR-002`), `type` d'une étape déjà ouverte, référence de facture (`ERR-006`).

**Vérifié** par deux tests unitaires sur le gestionnaire (présence et absence), trois assertions
renforcées sur les services concernés, une assertion sur le corps JSON de `ERR-006`, et un test
d'intégration dédié qui contrôle `$.champ` **et** son absence sur une erreur de validation. 258
tests au vert, plus une vérification manuelle contre l'instance locale.


### Q-71 ✅ Trois natures de la taxonomie rendaient la création de dossier impossible — tranché (M16)

Remonté par le jalon F6 du frontend, en sondant l'endpoint plutôt qu'en lisant le contrat.

**A — Le constat.** `dossier.nature` est `VARCHAR(50)` depuis `V1__schema_initial.sql`, et
`CreerDossierRequest.nature` porte le `@Size(max = 50)` correspondant. Or le service résout la
nature par `findByLibelleIgnoreCase` — c'est-à-dire sur `nature_dossier.libelle`, colonne
`VARCHAR(255)`. La contrainte imposée à l'entrée était donc **plus étroite que le référentiel qui
l'alimente**.

Trois des dix-sept natures de l'Annexe A dépassent 50 caractères :

| Longueur | Libellé |
|---|---|
| 71 | Hypothèque judiciaire (inscription provisoire, définitive, réalisation) |
| 67 | Autres procédures spécifiques (arbitral, contentieux administratif) |
| 54 | Requête aux fins de saisie de créances ou contestation |

Les choisir renvoyait `400 ERR-VALIDATION — nature: size must be between 0 and 50`. « Hypothèque
judiciaire » n'est pas un cas marginal en recouvrement bancaire.

**B — Pourquoi les tests ne l'avaient pas vu.** Tous les tests de création utilisent
« Assignation en paiement » (25 caractères). Aucun ne parcourait la taxonomie ; le défaut ne
pouvait apparaître qu'en construisant un formulaire à partir du référentiel réel.

**C — Ce qui a été écarté.** Faire porter le `code` plutôt que le libellé : cela changerait la
sémantique de l'endpoint, romprait les appels existants, et stockerait dans `dossier.nature` une
valeur différente de celle affichée partout ailleurs.

**D — Tranché.** Migration `V7__dossier_nature_largeur.sql` alignant la colonne sur sa source
(`VARCHAR(255)`), avec les annotations correspondantes sur l'entité et le DTO. C'est un alignement,
pas un élargissement de confort — même nature de correctif que `V6` pour
`decision_definitive.type`.

**Vérifié** par un test d'intégration créant un dossier avec le libellé de 71 caractères et
contrôlant la catégorie déduite, plus une vérification manuelle contre l'instance locale (201).

### Q-72 ✅ `GET /dossiers` ne permettait pas de retrouver un dossier — tranché (M16)

**A — Le constat.** L'endpoint n'acceptait que `nature`, `categorie` et `juridiction`. Ni référence,
ni client, ni « mes dossiers ». Un juriste suivant plusieurs dizaines de dossiers n'avait aucun
moyen direct de retrouver le sien, alors que la liste des dossiers est l'écran d'entrée du métier
(`FUNCTIONAL_MAP.md` §1, écran 05).

**B — Pourquoi le frontend ne pouvait pas compenser.** Filtrer côté navigateur sur une liste
paginée n'aurait cherché que dans la page affichée : l'utilisateur aurait reçu « aucun résultat »
pour un dossier bien présent en base. Un filtre faux est pire qu'un filtre absent.

**C — Tranché.** Trois paramètres optionnels ajoutés à `DossierSpecifications` et au contrôleur :
· `reference` — recherche **partielle**, insensible à la casse : une référence se retient rarement
  en entier ;
· `clientId` — tous les dossiers portés contre un client ;
· `mesDossiers` — booléen, résolu sur **l'utilisateur authentifié** et non sur un identifiant reçu.
  Ce choix n'est pas cosmétique : il rend impossible de consulter le portefeuille d'un collègue par
  ce biais.

L'appartenance au portefeuille passe par une sous-requête `EXISTS` sur `dossier_juriste`, et non par
une jointure : `Dossier` ne mappe aucune collection d'affectations, et une jointure aurait fait
remonter un dossier autant de fois qu'il compte de juristes — faussant le total de la pagination.

**Vérifié** par trois assertions unitaires sur la composition des prédicats et un test d'intégration
qui contrôle, sur PostgreSQL réel, qu'un second juriste ne voit rien via `mesDossiers`.

### Q-73 ✅ `typeClientSensible` n'était jamais confronté à son référentiel — tranché (M16)

**A — Le constat.** Q-06 a délibérément laissé `dossier.type_client_sensible` en `VARCHAR` libre,
sans clé étrangère — mais en confiant la validation au service applicatif, comme pour
`dossier.nature`. Elle a été faite pour `nature`, oubliée pour `typeClientSensible` : n'importe
quelle chaîne de 50 caractères était enregistrée en silence, y compris une faute de frappe, et
ressortait telle quelle dans les écrans et les rapports.

**B — Code ou libellé ?** Le contrôle porte sur le **code**. Il est unique, reste valable si le
libellé est reformulé, et tient dans la colonne cible — un libellé peut atteindre 100 caractères
pour une colonne de 50, ce qui reproduirait exactement le défaut de Q-71.

**C — Tranché.** `TypeClientSensibleRepository.findByCodeIgnoreCase` et un contrôle dans
`DossierService`, appliqué **dès que le champ est renseigné** — et non seulement dans la branche
sensible, puisque le champ est enregistré dans tous les cas. Le même contrôle couvre l'ajustement
du type par le DJ au moment de valider la sensibilité.

**Vérifié** par un test unitaire de rejet et un test d'intégration contrôlant le 400 sur un code
inconnu, plus une vérification manuelle : `Type de client sensible inconnu du référentiel : VIPP`.

### Q-74 ✅ Une dérogation de seuil demandée ne pouvait pas être arbitrée — tranché (M16)

Remonté par le jalon F7 du frontend (QF-23), et vérifié sur le contrat OpenAPI réel.

**A — Le constat.** L'arbitrage (`PUT /dossiers/{id}/seuil-derogation/{auditId}`) exige
l'identifiant de la demande. Une fois celle-ci créée, cet identifiant n'était exposé **nulle part** :
aucun `GET` des demandes, aucun champ dans `DossierResponse`, et l'historique n'en consigne que
`{nouveauSeuil, motif}`. Seul le demandeur le recevait, dans la réponse du `POST` — or c'est le
DJ/DJA, et lui seul, qui arbitre. L'endpoint était donc inatteignable depuis une interface, et une
demande restait bloquée pour toujours.

**B — Tranché.** `GET /dossiers/{id}/seuil-derogation[?statut=]`, sous `ROLE_CONSULTATION`, renvoie
les demandes de seuil du dossier, la plus récente d'abord. `statut=EN_ATTENTE` donne la file à
arbitrer. C'est une lecture, sans règle métier nouvelle.

**C — Défaut latent révélé par cet ajout, corrigé dans le même geste.** La proposition initiale de
sensibilité, créée avec le dossier, est **elle aussi** un `AuditSeuilDossier` en `EN_ATTENTE`. Tant
que la sensibilité n'était pas validée, `validerDerogationSeuil` acceptait de l'arbitrer comme une
dérogation : il fixait le seuil et l'origine `DEROGATION` en laissant la sensibilité en attente, et la
validation de sensibilité ne retrouvait plus sa proposition. Inatteignable tant qu'aucun identifiant
n'était exposé, ce contournement devenait atteignable avec la lecture. L'arbitrage exige désormais
une sensibilité **validée** — exactement la condition déjà imposée à la demande.

**Vérifié** par deux tests unitaires (garde, filtre de statut) et un test d'intégration de bout en
bout sur PostgreSQL réel : proposition initiale lisible mais non arbitrable (400), validation de
sensibilité, demande de dérogation, lecture de la file, arbitrage, file vide ensuite.

### Q-75 ✅ L'affectation pouvait vider un dossier de ses juristes — tranché (M16)

Remonté par le jalon F7 du frontend (QF-25).

**A — Le constat.** `PUT /dossiers/{id}/affectation` **remplace** les deux listes, et
`ModifierAffectationRequest` ne portait aucune contrainte — le contrôleur n'avait d'ailleurs pas
`@Valid`, si bien qu'une annotation aurait été ignorée. Une liste vide ou absente retirait donc tous
les juristes, ou tous les avocats, d'un dossier : contre RG-DOS-03, alors que la création exige déjà
les deux listes non vides (`CreerDossierRequest`).

**B — Tranché.** `@NotEmpty` sur les deux listes et `@Valid` sur le paramètre : même règle à la
création et à la modification. Le refus prend la forme standard `ERR-VALIDATION`, avec le champ en
cause dans `details`.

**C — Non traité, volontairement.** Conserver une personne déjà affectée déclenche `ERR-003`, puis
sur confirmation une alerte au DJ (RG-DOS-07). L'endpoint remplaçant tout, presque toute modification
conserve quelqu'un : le DJ risque d'être alerté à chaque changement. C'est une règle métier, à
confirmer avec la Direction Juridique avant d'y toucher.

**Vérifié** par un test d'intégration : liste vide refusée, liste absente refusée, cas nominal
accepté.

### Q-76 ✅ Une audience ne pouvait être ni annulée ni reportée — tranché (M16)

Remonté par le jalon F8 du frontend (QF-30), décidé par le porteur du projet le 2026-09-10.

**A — Le constat.** `StatutAudience` prévoit `ANNULEE` (et `chk_audience_statut` l'autorise), mais
aucun endpoint ne le posait, et aucun ne modifiait la date d'une audience. Une audience planifiée à
la mauvaise date, ou renvoyée par le tribunal — cas très fréquent —, restait `PLANIFIEE` : au
calendrier, dans ses exports, et déclenchant ses rappels J-7/J-3/J-1 à une date qui n'avait plus
lieu d'être. Planifier la nouvelle date n'y changeait rien : les deux coexistaient.

**B — Tranché.** `PATCH /audiences/{id}/annulation` (`ROLE_JURISTE`, comme la planification), motif
obligatoire (`@NotBlank`, 1 000 caractères au plus). Seule une audience `PLANIFIEE` s'annule ; son
motif et sa date d'annulation sont conservés (`V8__audience_annulation.sql`, contrainte : une
audience `ANNULEE` porte les deux). Un **report** consiste à annuler, puis à planifier la nouvelle
date — pas d'endpoint dédié : le lien entre les deux resterait à définir avec la Direction Juridique.

**C — Ce que l'annulation entraîne, vérifié site par site.** Une annulation qui ne serait qu'un
statut resterait à moitié faite :
- **rappels** : `RappelAudienceScheduler` ne vise que `PLANIFIEE` — rien à changer ;
- **calendrier et exports** : l'audience annulée en est écartée (`findByDatePlanifieeBetweenAndStatutNot`) ;
- **doublon** (RG-AUD-06) : une audience annulée ne compte plus — sans cela, replanifier à la même
  date après un renvoi aurait demandé confirmation **et alerté le DJ** ;
- **compte rendu** : refusé sur une audience annulée, qui serait sinon passée `TENUE`.

**Vérifié** : 4 tests unitaires et un test d'intégration de bout en bout sur PostgreSQL réel
(chacun des quatre effets ci-dessus, plus le motif exigé et le rôle).

### Q-77 ✅ Une alarme n'était close qu'en la reprogrammant — tranché (M16)

Remonté par le jalon F8 du frontend (QF-29), décidé par le porteur du projet le 2026-09-10.

**A — Le constat.** Une alarme n'avait que deux actions : la créer, la reprogrammer (qui clôt
l'ancienne et en crée une nouvelle). À l'échéance, `AlarmeEcheanceScheduler` la **signale** sans la
clore. Une alarme traitée par le juriste restait donc `ACTIVE` pour toujours, et la liste mêlait ce
qui reste à faire et ce qui est fait.

**B — Tranché.** `PATCH /alarmes/{id}/traiter` (`ROLE_JURISTE`, sans corps) : une alarme `ACTIVE`
passe `TRAITEE` avec sa date de traitement, **sans** en créer de nouvelle. Une alarme déjà close est
refusée (`REQUETE_INVALIDE`, même message que la reprogrammation). Le scheduler, qui ne surveille
que les alarmes actives, n'a pas eu à changer.

**Vérifié** : 2 tests unitaires et un test d'intégration (rôle, clôture unique, aucune remplaçante).

### Q-78 ✅ Prorogation et rabattement placés après la décision, contre toutes les sources — tranché (M16)

Remonté par l'audit du jalon F9 du frontend (QF-33), décidé par le porteur du projet le 2026-09-11.

**A — Le constat, source par source.** Le backend ne créait un délibéré qu'**avec** son résultat, qui
faisait aussitôt passer l'étape « Délibéré vidé » ; prorogation (Q-44) et rabattement (Q-42) ne
portaient donc que sur une décision déjà rendue. Or toutes les sources les placent **pendant** la
délibération :
- **US 3.2** (prorogation) : « Given que l'étape est EN_DELIBERE et qu'un délibéré existe », date
  **future** ; **UC-DEL-04** : précondition « l'étape concernée est au statut En délibéré » ;
  **RG-DEL-06** : « Depuis l'étape En délibéré… mention DÉLIBÉRÉ PROROGÉ AU [date] » ;
- **US 3.4 / UC-DEL-05 / RG-DEL-07** (rabattement) : précondition « En délibéré », « lorsque le juge
  décide de réouvrir les débats », mention « DÉLIBÉRÉ RABATTU LE [date, motif] » ;
- **diagramme d'états** (document d'analyse) : prorogations et rabattement « depuis l'état En
  délibéré » ; classe *Délibéré* : « mise en délibéré d'une affaire, **son issue**, et l'étape ».
Q-44 avait vu le paradoxe et l'avait levé en retirant la vérification — ce qui rendait l'appel
*possible*, pas *juste* : pour noter « délibéré prorogé au 15 octobre », un juriste devait d'abord
inventer un résultat. S'y ajoutaient deux manques : aucune lecture des prorogations (l'écran
« Proroger, avec historique » de FUNCTIONAL_MAP §3 était irréalisable), et un délibéré rabattu
indiscernable des autres.

**B — Tranché : le délibéré en deux temps.**
- `POST /dossiers/{id}/deliberes` **sans** résultat = mise en délibéré : `dateDeliberee` est la date
  annoncée, l'étape reste `EN_DELIBERE`. **Avec** résultat, le délibéré déjà rendu est enregistré et
  vidé d'un même geste (comportement historique, compatible avec les appels existants). Au plus un
  délibéré en attente par étape (409 sinon).
- `POST /deliberes/{id}/prorogations` : seulement sur un délibéré en attente, étape `EN_DELIBERE`,
  date **future** (US 3.2), qui devient la date annoncée. Seuil et alerte inchangés (Q-17/Q-18).
- `POST /deliberes/{id}/rabattement` : mêmes préconditions ; le délibéré garde sa date et son motif
  de rabattement, l'étape revient `EN_COURS`. Une décision rendue ne se rabat plus : la voie qui
  ramène « Délibéré vidé » à « En cours » est l'exercice d'un recours (Q-42, inchangé).
- 🆕 `PUT /deliberes/{id}/resultat` : le tribunal a statué — résultat, échéance de recours (mêmes
  règles qu'avant), expédition ouverte « à lever », étape `DELIBERE_VIDE`.
- 🆕 `GET /deliberes/{id}/prorogations` (`ROLE_CONSULTATION`) : l'historique.
- `DelibereResponse` gagne `etat` (`EN_ATTENTE`/`VIDE`/`RABATTU`, **dérivé**, jamais stocké),
  `nombreProrogations`, `dateRabattement`, `motifRabattement`. Migration `V9__delibere_cycle.sql` :
  `resultat` devient facultatif, colonnes de rabattement, trois contraintes de cohérence.

**C — Ce que le changement entraîne.** Tout ce qui supposait « un délibéré a un résultat » a été
repris : les trois indicateurs de succès (tableau de bord, rapport, performance des avocats) et le
compteur de délibérés du rapport ignorent désormais les délibérés en attente ou rabattus — sans
quoi ils compteraient comme des échecs ; l'expédition est refusée avant la décision. Le
planificateur du délai de recours ne voit que des délibérés vidés : inchangé.

### Q-79 ✅ Une suppression directe laissait la demande de suppression orpheline — tranché (M16)

Remonté par l'audit du jalon F10 du frontend (QF-34), décidé par le porteur du projet le 2026-09-11.

**A — Le constat, vérifié dans le code.** Un juriste demande la suppression d'une pièce (202), puis
le DJ ou la DJA la supprime directement (200, US 6.5). `supprimerDirectement` ne touchait pas à la
demande : elle restait `EN_ATTENTE` dans la file, **indécidable** (`approuverOuRejeter` cherche un
document actif → 404) et **relancée toutes les 72 h sans fin** par `SuppressionRelanceScheduler`.
Le parcours e2e « le juriste demande, le DJ supprime » du frontend en créait une à chaque exécution.
S'y ajoutait un manque : `DemandeSuppressionResponse` ne portait que `documentId` — la file du DJ ne
disait pas **quoi** supprimer, et l'historique d'une demande approuvée restait anonyme (document
supprimé → 404 à la consultation).

**B — Tranché.** La suppression directe ferme la demande en attente sur le même document :
`APPROUVEE`, validée par l'auteur de la suppression, datée. La réponse porte `documentNom`,
`typeDocument`, `dossierId`, `referenceDossier`. Migration `V10__demandes_suppression_orphelines.sql` :
ferme les demandes déjà orphelines (validateur inconnu après coup, laissé nul).

**Vérifié** : un test unitaire par volet, un test d'intégration de bout en bout sur PostgreSQL ;
en base locale, V10 a fermé une orpheline et n'en laisse aucune.

### Q-80 ✅ Le rapport journalier GED n'était servi qu'au rôle juriste — tranché (M16)

Remonté par l'audit F10 (QF-35), décidé par le porteur du projet le 2026-09-11. FR-GED-04 : « tout
juriste » ; les user stories donnent au DJ et à la DJA « tous les droits d'un juriste » ; le document
d'analyse attribue le rapport à l'Assistante. Or aucun de ces trois profils ne porte `ROLE_JURISTE`
dans le realm (cf. Q-81). **Tranché** : `GET /ged/rapport-journalier` sous
`hasAnyRole('JURISTE', 'DJ', 'DJA', 'ASSISTANTE')`. **Vérifié** : test d'intégration — 200 pour les
quatre profils, 403 pour le SH.

### Q-81 ✅ Le DJ et la DJA ne portaient pas `ROLE_JURISTE` — tranché (M16)

Constaté pendant l'audit F10 (QF-36). Les user stories et le document d'analyse donnent au DJ et à
la DJA « tous les droits d'un juriste » (en plus des leurs). Dans `audiences-realm.json`, leurs rôles
composites n'incluent que `ROLE_SAISIE` : **toute** action réservée à `ROLE_JURISTE` leur est
refusée — planifier une audience, gérer les étapes, les délibérés, les alarmes, les décisions
définitives… Deux voies : ajouter `ROLE_JURISTE` aux composites de `ROLE_DJ`/`ROLE_DJA` (une ligne
par rôle, effet sur tous les endpoints), ou élargir endpoint par endpoint comme Q-80.

**Tranché par le porteur du projet (2026-09-11)** : `ROLE_JURISTE` ajouté aux composites de
`ROLE_DJ` et `ROLE_DJA`, dans `audiences-realm.json` (futurs imports) **et** dans le Keycloak en
service (`kcadm add-roles` : l'import au démarrage ignore un realm existant). Une seule règle, au
même endroit, fidèle aux sources ; un nouvel endpoint réservé au juriste leur est ouvert d'office.

**Précaution indispensable, appliquée avec** : le provisionnement déduisait le profil du **premier**
rôle métier du jeton (`findFirst()`). Un jeton de DJ portant désormais `ROLE_DJ` et `ROLE_JURISTE`,
dans un ordre que Keycloak ne garantit pas, un nouveau DJ aurait pu être enregistré comme juriste —
perdant la suppression directe, les alertes réservées au DJ, etc. `UtilisateurProvisioningService`
choisit désormais par **priorité** (DJ, DJA, SH, ASSISTANTE, JURISTE, AVOCAT), le même ordre que
`profilPrincipal` côté frontend. Les comptes déjà provisionnés gardent leur profil (il est en base).
Effet à connaître : un jeton émis avant le changement ne porte pas le nouveau rôle — se reconnecter.
Q-80 (rapport journalier ouvert nommément au DJ et à la DJA) devient redondant pour eux, sans effet.

### Q-82 ✅ Une demande de frais ne se lisait ni seule, ni avec ses décisions — tranché (M16)

Remonté par l'audit du jalon F11 du frontend, décidé par le porteur du projet le 2026-09-11.

**A — Le constat, vérifié dans le code.** Aucun `GET /frais/demandes/{id}` : le détail d'une demande
ne pouvait être retrouvé qu'en fouillant la file paginée. Les décisions du circuit, pourtant tracées
dans `validation_frais` (Q-05), n'étaient exposées nulle part : le DJ ne pouvait pas voir que la DJA
avait déjà donné son accord (US 4.5), ni personne savoir s'il avait déjà voté — l'interface aurait
proposé un vote que le backend refuse (409). UC-INT-02 étape 3 veut pourtant « la demande, ses pièces
justificatives et le résultat du contrôle de conformité ». La réponse ne portait que des
identifiants (dossier, avocat), rien de ce qui explique la validation conjointe, et la file n'avait
aucun ordre stable (`PageRequest` sans tri).

**B — Tranché.** `GET /frais/demandes/{id}` (mêmes rôles que la file ; l'avocat ne lit que les
siennes, 403 sinon). `DemandeFraisResponse` gagne `referenceDossier`, `avocatNom`,
`dossierSensible`, `seuilApplicable` (Q-26), `validationConjointeRequise` (RG-INT-03, même fonction
que la décision d'opportunité) et `validations` (étape, décision, motif, auteur nommé, date), sur
toutes les réponses du module. File triée par date de dépôt décroissante. La réponse étant désormais
calculée, `DemandeFraisMapper` (MapStruct) est remplacé par une construction dans le service, comme
`DelibereMapper` en Q-78. Les décisions d'une page de la file sont chargées en une requête.

**Vérifié** : 3 tests unitaires (avocat étranger refusé, décisions nommées et règle conjointe,
seuil de dérogation) ; parcours d'intégration `p6` étendu (détail nommé, décisions dans l'ordre,
lecture par l'avocat propriétaire, 403 pour un confrère).

### Q-83 ✅ La relance de validation conjointe (CONF04) n'existait pas — tranché (M16)

Constaté au même audit. CONF04 : « délai maximal (en jours) pour que le DJ et le DJA valident
conjointement une demande sensible avant qu'une alerte de rappel soit déclenchée » — 5 jours,
modifiable par le DJ. La clé `DELAI_VALIDATION_CONJOINTE` existait (V2), aucun code ne la lisait.

**Tranché** : `FraisRelanceScheduler` (module `frais`, même principe que
`SuppressionRelanceScheduler`, Q-02), horaire. Une demande `OPPORTUNITE` est relancée quand le délai
est écoulé depuis l'accord d'opportunité, puis depuis la dernière relance
(`demande_frais.date_derniere_relance`, migration `V11__frais_relance_validation_conjointe.sql`).
Seul celui qui n'a pas encore voté est relancé (alerte `VALIDATION_CONJOINTE_REQUISE`). Une valeur
non numérique du paramètre est journalisée et la relance sautée, sans faire échouer la tâche.

**Vérifié** : 4 tests unitaires (relance du seul non-votant, délai non écoulé, délai compté depuis
la dernière relance, rien à relancer) ; V11 appliquée à la base locale.

### Q-84 ✅ Un avocat n'était reconnu que par son `sub` Keycloak — tranché (M16)

Constaté en préparant les parcours e2e du jalon F11 du frontend (QF-38), décidé par le porteur du
projet le 2026-09-11.

**A — Le constat, vérifié en base et dans le code.** `FraisService` et `PublicationService`
reconnaissaient l'avocat connecté en comparant `avocat.compte_keycloak` au `sub` du jeton, un UUID.
Or le DJ saisit, dans le formulaire des intervenants, l'**identifiant de connexion** de l'avocat
(`avocat.dupont`) : tous les avocats de la base locale en portaient un, aucun n'était reconnu —
aucun dépôt de frais ni de publication possible, et les alertes destinées à l'avocat (rejet,
paiement, correspondance, constitution) ne partaient pas, `utilisateurRepository.findByKeycloakId`
étant appelé avec cet identifiant.

**B — Tranché.** `utilisateur.identifiant` (claim `preferred_username`, migration
`V12__utilisateur_identifiant.sql`, unique) : renseigné au provisionnement, et pour un utilisateur
déjà provisionné à sa requête suivante (`CurrentUserService`, écriture dans une transaction à part
comme en R-06). `CompteAvocatService` fait le lien dans les deux sens — identifiant de connexion
d'abord, `sub` en second pour les comptes déjà rattachés ainsi — et remplace les quatre recherches
de `FraisService`, `PublicationService` et `ConstitutionService`.

**Vérifié** : 5 tests unitaires (`CompteAvocatServiceTest`), 2 (`CurrentUserServiceTest` :
identifiant enregistré, identifiant à jour laissé tel quel), 1 test d'intégration sur PostgreSQL réel
(avocat reconnu par son identifiant de connexion, identifiant conservé sur l'utilisateur, compte sans
avocat refusé).

### Q-85 ✅ Deux créations concurrentes rendaient 500 au lieu de 409 — tranché (M16)

Constaté au même moment : deux parcours parallèles créant le même avocat franchissaient tous deux le
contrôle d'unicité de `IntervenantService` ; la contrainte `avocat_compte_keycloak_key` rejetait la
seconde insertion, remontée en `DataIntegrityViolationException` puis en **500**. Le même cas
existe pour toute création soumise à une contrainte d'unicité (facture, référence de dossier…).

**Tranché** : `GlobalExceptionHandler` rend **409 `ERR-CONFLICT`** pour une violation d'unicité
(SQLState `23505`), avec un message générique qui ne divulgue rien de la base ; toute autre violation
d'intégrité reste une erreur interne. **Vérifié** : 2 tests unitaires (23505 → 409, 23503 → 500).

### Q-86 ✅ Publications et constitutions : illisibles pour qui devait les instruire — tranché (M16)

Remonté par l'audit du jalon F12 du frontend, décidé par le porteur du projet le 2026-09-11.

**A — Les constats, vérifiés dans le code et les sources.**
1. Le fichier d'une autre publication (pièce, décision, conclusion) n'était exposé nulle part :
   `cheminStockage` enregistré, aucune URL de téléchargement — l'Assistante validait un fichier
   qu'elle ne pouvait pas lire, les juristes ne le liraient jamais.
2. `GET /publications/{id}` rendait 404 tant que la publication n'était pas `VALIDE` — y compris à
   l'Assistante, qui doit la voir pour la valider (UC-INT-10 étape 3).
3. Les commentaires des juristes n'étaient lisibles par personne : le DJ et la DJA étaient notifiés,
   sans pouvoir lire ce qu'ils devaient centraliser (RG-INT-07) ; la correspondance transmise n'était
   visible que par le 409 d'un second envoi.
4. Publications et demandes de constitution ne portaient que des identifiants (dossier, avocat,
   audience, prestataire, demandeur, signataire) ; la liste des publications n'avait pas d'ordre
   stable.
5. Aucune notification n'annonçait aux juristes une publication validée (UC-INT-08 étape 1, avec
   restriction au seul destinataire désigné, alt. 3-a).

**B — Tranché.**
- `PublicationResponse` porte `referenceDossier`, `avocatNom`, `audienceDate`,
  `restrictionAccesNom`, `nomFichier` ; sur le **détail** seulement, `urlTelechargement`
  (pré-signée, 10 minutes), `commentaires` (auteur nommé) et `correspondance`.
- L'Assistante lit une publication `DEPOSE` ou `REJETE`. La restriction d'accès, qui désigne un
  juriste lecteur (RG-INT-06), ne s'oppose pas aux profils qui instruisent la publication :
  l'Assistante (validation) et le DJ/DJA (centralisation, RG-INT-07).
- File des publications : `statut=DEPOSE` en ordre d'arrivée, sinon la plus récente d'abord.
- `DemandeConstitutionResponse` porte `referenceDossier`, `prestataireNom`, `prestataireType`,
  `demandeurNom`, `valideParNom`.
- À la validation, les juristes affectés au dossier sont notifiés (`NOUVELLE_PUBLICATION`) — ou le
  seul destinataire désigné si l'accès est restreint.
- Hors périmètre, reporté au portail avocat (F16) : l'observation du DJ/DJA de sa propre initiative,
  sans publication (UC-INT-08 étapes 8 à 12), et la lecture de ses correspondances par l'avocat.

**Vérifié** : 5 tests unitaires (`PublicationServiceTest` : Assistante et fichier d'une pièce
déposée, DJ sur une publication restreinte, commentaires et correspondance nommés, notification des
juristes du dossier, du seul destinataire désigné) ; parcours d'intégration `p2` et `p7` étendus sur
PostgreSQL réel (détail lu par l'Assistante avant validation et refusé au juriste, juriste notifié
après validation, constitution nommée).

### Q-87 ✅ Un avocat constitué n'était jamais notifié — corrigé (M16)

Constaté en lisant le code pendant l'audit F12. `DemandeConstitution.prestataire` est une
association `LAZY` vers la classe abstraite `Intervenant` (héritage `JOINED`) : chargée à la
demande, elle est un **proxy d'`Intervenant`**, jamais une instance d'`Avocat`. Or
`ConstitutionService.notifierPrestataire` testait `prestataire instanceof Avocat` sur une demande
relue par `findById` — test toujours faux : la signature du SH ne prévenait jamais l'avocat
(FR-INT-07 critère 3). **Corrigé** : `Hibernate.unproxy` avant le test, dans le service et dans le
calcul de `prestataireType`. **Vérifié** : parcours d'intégration `p7` sur PostgreSQL réel — après
la signature, l'avocat a bien une alerte `CONSTITUTION_A_SIGNER`, et la demande se dit `AVOCAT`.
Le défaut a été établi à la lecture du code ; le parcours n'a pas été rejoué sur le code d'avant le
correctif.

### Q-88 ✅ Les décisions définitives ne se relisaient pas — tranché (M16)

Remonté dès l'audit frontend (QF-03), décidé par le porteur du projet à l'ouverture du jalon F13 le
2026-09-11. `POST /dossiers/{id}/adjudications`, `POST /dossiers/{id}/condamnations` et
`PATCH /condamnations/{id}/statut` existaient sans **aucune** lecture : l'onglet « Décisions » d'une
fiche restait en écriture seule, et la mise à jour du statut d'une condamnation (UC-DEF-02 étape 8)
devenait inatteignable après un rechargement — son identifiant n'était connu qu'à la création.
**Tranché** : `GET /dossiers/{id}/decisions` (`ROLE_CONSULTATION`, 404 si le dossier est inconnu),
symétrique de #56/#57/#58 : `{adjudications, condamnations}`, chaque liste de la plus récente à la
plus ancienne. **Vérifié** : test d'intégration sur PostgreSQL réel (condamnation créée puis passée
en recouvrement forcé, relue avec son statut par l'Assistante ; 404 pour un dossier inconnu).

### Q-89 ✅ La recherche jurisprudentielle n'était qu'exacte — tranché (M16)

Constaté au même audit. `GET /jurisprudences` comparait le mot-clé à l'un des mots indexés par
égalité stricte, casse comprise (`:motCle = ANY(mots_cles)`), et de même la nature et la
juridiction : « saisie » ne retrouvait pas « Saisie immobilière », « tgi douala » pas « TGI Douala ».
Or FR-DEF-03 veut une décision « consultable dans la base » et la recherche par mots-clés est la
voie d'accès. Et la fiche ne pouvait pas relister les décisions qu'on y archivait (UC-DEF-03 les
« rattache au dossier »). **Tranché** : chaque filtre textuel est un motif « contient », insensible à
la casse (jokers saisis échappés) ; un paramètre `dossierId` restreint la recherche au dossier.
**Vérifié** : 3 tests unitaires (motif, filtres vides ignorés, échappement), 1 test d'intégration
sur PostgreSQL réel (fragment de mot-clé en minuscules, juridiction en minuscules restreinte au
dossier, autre dossier sans résultat). Limite : les accents comptent (« hypotheque » ne retrouve
pas « hypothèque ») — l'extension `unaccent` n'est pas installée.

### Q-90 — Suivi d'une adjudication dans le temps, statut initial d'une condamnation — `P2`, ouvert

Constaté au même audit, **laissé ouvert sur décision du porteur du projet** (2026-09-11). RG-DEF-02
dit que le système « suit » la comptabilisation, la reprise de provision, la restitution de soulte,
la mutation et le reliquat d'une adjudication, mais aucune source ne décrit leur mise à jour — seule
la condamnation en a une (UC-DEF-02 étape 8, #53). S'en tenir à la lettre, comme pour Q-59 : aucune
mise à jour d'adjudication n'est ajoutée. **À confirmer avec la Direction Juridique.** Même famille :
US 8.2 fait saisir `statutPaiement` à la création d'une condamnation, que `CreerCondamnationRequest`
ne reçoit pas (toujours `EN_ATTENTE`, QF-40 du frontend) — un champ facultatif suffirait.

### Q-91 ✅ Une valeur de configuration erronée cassait d'autres modules — tranché (M17)

Constaté à l'audit d'ouverture du jalon F14 du frontend (écran 41, « Configurations et seuils »).
`PUT /configurations/{cle}` n'exigeait que `@NotBlank` sur la valeur, alors que cinq points de code
la reconvertissent : `FraisService.seuilGlobal` (`Double.parseDouble`, appelé sur **chaque** réponse
de frais depuis Q-82), `DelibereService` (`Integer.parseInt` de `SEUIL_PROROGATIONS`), `GedService`
(`Long.parseLong` de `TAILLE_MAX_GED`, découpage de `FORMATS_GED` par virgules **sans retirer les
espaces**). Seuls `ChargeMaxAvocatScheduler` et `FraisRelanceScheduler` se protégeaient d'un
`NumberFormatException`. Conséquence : une saisie « 50 000 000 » ou « pdf, xlsx » par le DJ passait
en 200, puis ressortait en **erreur 500 dans un module sans rapport avec l'écran de configuration** —
diagnostic à la charge de l'utilisateur, à distance de la cause.

**Tranché** : chaque clé porte une règle (`RegleConfiguration`), appliquée dans
`ConfigurationService.modifier` — donc aussi sur `PUT /alertes/seuils`, qui y délègue. Une valeur
invalide est refusée en **400** et nommée ; une valeur valide est **normalisée** avant enregistrement
(espaces retirés, liste en majuscules, sans doublon, ordre de l'énumération). La règle est
**publiée** dans `ConfigurationResponse` (`typeValeur`, `valeurMinimale`, `valeurMaximale`,
`valeursPossibles`) : le frontend construit son champ de saisie à partir du contrat au lieu d'en
redéclarer une copie qui divergerait.

**Origine des bornes** — les minima (≥ 1) et les ensembles fermés viennent du code consommateur ou
des énumérations du domaine (`FormatDocument`, `CanalAlerte`) ; `APPLICATIF` ne peut pas être retiré
des canaux (RG-ALR-03, « applicatif obligatoire »). Le maximum de `TAILLE_MAX_GED` est la limite
`spring.servlet.multipart.max-file-size` (10 Mo) — au-delà, Spring rejette le fichier avant le
contrôle métier, le réglage serait sans effet. Celui de `SEUIL_MONTANT_DEFAUT` (12 chiffres) tient à
l'exactitude du `double` du calcul de seuil. En revanche les maxima de `SEUIL_PROROGATIONS` (1 000),
`DELAI_VALIDATION_CONJOINTE` (365) et `CHARGE_MAX_AVOCAT` (1 000) sont des **garde-fous que je
propose**, aucune source ne fixant de plafond : sans eux, un délai de 2 000 000 000 jours ferait
déborder le calcul de date du planificateur de relance. Ils sont regroupés en un point unique, à
ajuster si la Direction Juridique les précise. **À confirmer.**

**Vérifié** : 24 tests unitaires sur les règles, 2 sur le service, et en réel contre le backend
démarré — « 50 000 000 » refusé en 400, `OUTLOOK` seul refusé, `" pdf , png "` enregistré
`PDF,PNG`.

### Q-92 ✅ Les notifications revenaient sans ordre — tranché (M17)

Constaté en construisant l'écran 03 du frontend. `GET /alertes/mes-notifications` paginait
(`PageRequest.of(page, size)`) **sans aucun tri** : la requête JPA ne portait pas d'`ORDER BY`, et
PostgreSQL ne garantit alors aucun ordre entre deux requêtes. Une notification pouvait donc atterrir
sur n'importe quelle page ; pire, une même alerte pouvait apparaître sur deux pages ou n'apparaître
sur aucune, selon le plan d'exécution choisi. L'écran « Mes notifications » n'est utilisable qu'à
partir du moment où la plus récente est en tête.

**Tranché** (arbitrage du porteur du projet, 2026-09-12) : tri `dateDeclenchement DESC, id DESC`,
comme la file des demandes de frais depuis Q-82. L'`id` départage les alertes déclenchées dans la
même transaction, qui partagent l'horodatage. **Vérifié** : 1 test d'intégration sur PostgreSQL réel
(trois alertes créées dans la même seconde, relues dans l'ordre inverse de création).

### Q-93 ✅ Les listes de clients et de dossiers revenaient sans ordre — tranché (M17)

Même défaut que Q-92, constaté juste après, et cette fois **visible en usage** : `GET /clients`
paginait sans `ORDER BY`. Passé vingt clients — seuil franchi le 2026-09-12, 23 clients en base de
développement — celui qu'un juriste venait de créer basculait en page 2, donc **invisible pour son
auteur** au retour sur la liste. Deux parcours de bout en bout du frontend l'ont démontré, en
exécution parallèle **et** en exécution seule. `GET /dossiers` (Specification + `findAll`) portait le
même défaut, encore masqué par les filtres de l'écran.

`GET /jurisprudences` a été vérifié à cette occasion : il est **déjà** trié
(`ORDER BY j.date_decision DESC, j.id DESC`, posé en Q-89). `GET /frais/demandes` l'est depuis Q-82
et `GET /publications` depuis M16. Il ne restait donc que ces deux-là.

**Tranché** (arbitrage du porteur du projet, 2026-09-12) : tri `createdAt DESC, id DESC` sur les deux,
comme partout ailleurs. **Vérifié** : 1 test d'intégration sur PostgreSQL réel (le client et le
dossier créés à l'instant reviennent en tête d'une page d'un seul élément).

### Q-94 ✅ La vue consolidée d'un client interrogeait la base dossier par dossier — tranché (M17)

Mesuré le 2026-09-12 sur la base de développement, en marge du jalon F14 du frontend (signalé
QF-41 côté frontend). `GET /clients/{id}/dossiers` mettait **11 à 19 secondes**, jusqu'à 70 s sous
charge, pour un client portant 310 dossiers : l'écran 13 restait en chargement, et l'appel occupait
assez longtemps une connexion pour faire expirer des requêtes voisines dans la suite de bout en bout.

**Cause.** `construireReponseLegere` allait chercher étapes, juristes et avocats **par dossier** —
trois requêtes chacun, soit 930 pour 310 dossiers. Un N+1 classique, invisible tant que les clients
de test portaient trois dossiers.

**Ce qu'il ne fallait pas faire.** La première piste — alléger la réponse — était fausse : le contrat
d'API inclut `etapes` dans le schéma `Dossier`, et l'écran 13 s'en sert pour afficher l'étape
courante. Paginer ne convenait pas davantage : le contrat décrit la vue consolidée comme « toutes les
procédures d'un client, par catégorie », sans pagination. C'était donc le **nombre de requêtes** qu'il
fallait réduire, pas la réponse qu'il fallait amputer.

**Tranché.** `construireReponses(List<Dossier>)` charge les trois collections en **trois requêtes pour
la liste entière** (`findByDossierIdIn`), puis les apparie en mémoire par identifiant. Les
identifiants composites `DossierJuristeId`/`DossierAvocatId` portent déjà `dossierId` : les lire
n'initialise aucun proxy. `GET /dossiers` emprunte le même chemin et passe de soixante requêtes par
page à trois.

**Mesuré après correction**, même base, client entre-temps passé à **355 dossiers** :
**0,12 s** (0,17 s et 1,05 s au premier appel, JIT compris) pour 306 Ko — contre 11 à 19 s avant.
**Vérifié** : 1 test d'intégration sur PostgreSQL réel qui garantit l'appariement — deux dossiers du
même client affectés à des avocats différents gardent chacun le sien, seul vrai risque de la bascule.

### Q-95 ✅ Le tableau de bord exécutif s'ouvre à la DJA — tranché (M18)

Soulevé à l'ouverture du jalon F15 du frontend (écran 39), en reprenant QF-04.

**A — Ce que la documentation exige.** Deux passages en tension. US 8.5, critère d'acceptation :
« *Given que je suis le DJ (**ROLE_DJ**), When j'appelle GET /tableau-de-bord…* » — d'où le
`hasRole('DJ')` d'origine. Mais les spécifications fonctionnelles, table des parties prenantes
(STK-06, Direction Générale), attendent de « *disposer d'un tableau de bord consolidé du risque
crédit contentieux **à travers le DJ/DJA*** ». La première nomme un rôle technique, la seconde
désigne les deux responsables au niveau des objectifs.

**B — Ce que le code implémentait.** `@PreAuthorize("hasRole('DJ')")`, sans `ROLE_DJA`. La DJA
recevait 403 sur le seul écran qui lui aurait donné une vue d'ensemble, alors qu'elle contrôle
l'opportunité de toutes les demandes de frais et valide conjointement les demandes sensibles.

**C — Tranché** (arbitrage du porteur du projet, 2026-09-14) : `hasAnyRole('DJ', 'DJA')`. Les autres
profils restent exclus — RG-REP-03 en fait un tableau de pilotage, pas un écran d'équipe.

**Vérifié** : `ParcoursEndToEndIntegrationTest.p9_pilotage` étendu (DJA en 200, juriste et Assistante
en 403) sur PostgreSQL réel, et en réel contre le backend démarré — `dja.test` → 200,
`assistante.test` → 403.

**À noter, découvert au passage** : `ROLE_DJ` et `ROLE_DJA` étant composites depuis Q-81, la
direction atteint déjà tout ce qui est ouvert au juriste — `GET /rapports`, `GET /audiences/calendrier`,
`GET /repertoire/avocats`. C'est ce qui a clos l'essentiel de QF-04 sans modification, et c'est un
effet de bord de Q-81 qui n'avait pas été consigné.

### Q-96 ✅ L'avocat ne pouvait pas désigner son dossier — tranché (M19)

Ouverture du jalon F16 du frontend (portail Avocat), sur la lacune QF-01 relevée dès l'audit initial.

**A — Ce que la documentation exige.** US 4.1 : « je soumets **dossierId**, montant,
referenceFacture, piecesJustificatives via POST /frais/demandes ». US 5.1 : « je veux déposer un
compte rendu d'audience **en choisissant le dossier et l'audience** ». Les deux stories supposent
donc que l'avocat connaisse ses dossiers, et leurs audiences.

**B — Ce que le code implémentait.** L'avocat ne porte pas {@code ROLE_CONSULTATION} : il recevait
403 sur {@code GET /dossiers} comme sur {@code GET /dossiers/{id}/audiences}. Il devait fournir des
identifiants qu'aucun endpoint ne lui permettait de découvrir — l'exigence était **inapplicable**.

**C — Tranché** (arbitrage du porteur du projet, 2026-09-14) : un module {@code portail}, quatre
lectures sous {@code /avocat}, toutes {@code hasRole('AVOCAT')} :

| Endpoint | Objet | Exigé par |
|---|---|---|
| `GET /avocat/mes-dossiers` | Ses dossiers, **identification seule** | US 4.1, US 5.1 |
| `GET /avocat/mes-dossiers/{id}/audiences` | Les audiences d'un de ses dossiers | US 5.1 |
| `GET /avocat/mes-publications` | Ses dépôts, statut et motif de rejet | — *(périmètre ajouté)* |
| `GET /avocat/mes-constitutions` | Les lettres le désignant | — *(périmètre ajouté)* |

**Deux partis pris, et leurs raisons.**

1. **Une surface distincte, plutôt que l'élargissement des endpoints internes.** Ouvrir
   {@code GET /dossiers} à {@code ROLE_AVOCAT} aurait fait reposer la confidentialité sur un filtre
   à l'intérieur d'un service partagé — un oubli, et le portefeuille entier passait à un tiers. Ici
   la restriction est **dans la requête** ({@code SELECT da.dossier FROM DossierAvocat da WHERE
   da.avocat.id = :avocatId}), et le périmètre est impossible à confondre avec celui du personnel.
2. **Un dossier réduit à son identification** — référence, nature, juridiction. Ni client, ni risque
   encouru, ni sensibilité, ni juristes affectés : un prestataire externe n'a pas à connaître le
   portefeuille de la banque, et l'exposer le rendrait extractible en masse.

**Périmètre à confirmer.** Les deux dernières lectures ne répondent à aucune exigence écrite — US 5.4
vise le juriste. Elles sont livrées au motif qu'un avocat notifié du rejet d'une publication ne
pouvait pas relire ce qui avait été rejeté, ni le motif à corriger. **À confirmer avec la Direction
Juridique.**

**Vérifié** : 3 tests d'intégration sur PostgreSQL réel — un dossier auquel il n'est pas affecté
n'apparaît ni dans sa liste ni par son identifiant (403), la réponse ne porte aucun champ du
portefeuille, le portail est fermé aux profils internes — et en réel contre le backend démarré.

### Q-97 ✅ Les pièces justificatives d'une demande de frais n'étaient jamais téléversées — tranché (M19)

Signalé par le porteur du projet le 2026-09-14, en fin de jalon F16 du frontend, sur une lecture trop
étroite de ma part (QF-26 côté frontend, que j'avais close à tort comme « conforme »).

**A — Ce que la documentation exige.** UC-INT-01 étape 4 : « L'avocat **charge** les pièces
justificatives. » UC-INT-02 étape 3 : « Le système affiche la demande, **ses pièces justificatives**
et le résultat du contrôle de conformité. » Et le dictionnaire des données décrit
`pieces_justificatives TEXT[]` comme « **Chemins ou références** des pièces (tableau) ».

**B — Ce que le code implémentait.** `DeposerFraisRequest.piecesJustificatives` acceptait n'importe
quelle liste de chaînes non vide, et aucun endpoint ne permettait de téléverser quoi que ce soit. Le
tableau contenait donc des libellés arbitraires, sur lesquels l'Assistante était censée exercer un
contrôle de conformité — ce qui n'a pas de sens.

**C — Tranché** (arbitrage du porteur du projet) : **téléversement en deux temps**.

1. `POST /avocat/pieces-justificatives` (multipart, `ROLE_AVOCAT`) range le fichier sous le préfixe
   `frais/` et renvoie sa **clé**. Mêmes contrôles de format et de poids que la GED — le service de
   validation est extrait de `GedService` en `ValidationFichierService` pour que `FORMATS_GED` et
   `TAILLE_MAX_GED` restent la **seule** source de vérité des deux.
2. L'avocat reporte ces clés dans `piecesJustificatives`. **Le contrat de `POST /frais/demandes` ne
   change pas** : le document d'API le décrit en JSON avec un tableau de chaînes, et un envoi qui
   échoue ne fait pas reperdre les pièces déjà transmises.
3. Le **détail** d'une demande (`GET /frais/demandes/{id}`) expose `piecesTelechargeables`, avec une
   URL pré-signée par pièce — UC-INT-02 étape 3. Vide sur la liste : les URL ne vivent que dix
   minutes et coûteraient un aller-retour MinIO par pièce, pour un écran qui ne les affiche pas.

**Compatibilité.** Seules les entrées préfixées `frais/` sont signées : le tableau peut contenir des
libellés antérieurs, qui ne désignent aucun objet stocké et pour lesquels une URL échouerait.

**Vérifié** : 2 tests d'intégration sur PostgreSQL réel — téléversement, dépôt, relecture par
l'Assistante ; format non autorisé refusé en 400. Le client MinIO étant mocké en test, l'URL
pré-signée y est nulle : le test vérifie la présence du champ, pas une valeur que le mock ne produit
pas.

### Q-98 ✅ Un compte rendu d'audience ne pouvait pas porter sa pièce — tranché (M20)

Question du porteur du projet le 2026-09-14, après le jalon F17 du frontend : l'avocat qui dépose un
compte rendu d'audience dispose souvent du **plumitif** ou d'une note d'audience, et n'avait aucun
moyen de la joindre — il devait la déposer séparément en « autre publication », où elle perdait son
rattachement à l'audience.

**A — Ce que le modèle permettait.** `CompteRenduAudience` ne portait que `contenu` (texte) et
`audience_id` ; seule `AutrePublication` avait un `chemin_stockage`. La hiérarchie `JOINED` rendait
pourtant l'ajout trivial : une colonne sur la table du sous-type, rien à déplacer.

**B — Tranché** (« Applique ta recommandation ») : **pièce facultative sur le compte rendu**.

1. `V13__compte_rendu_piece_jointe.sql` ajoute `chemin_stockage VARCHAR(255)` à
   `compte_rendu_audience`, **nullable** — les comptes rendus existants restent valides tels quels.
2. `POST /publications/cr-audience` passe en **multipart** : métadonnées en paramètres, fichier en
   partie `file` facultative. Le contrat d'API décrivait un corps JSON ; **l'écart est assumé**, au
   même titre que `/publications/autres` qui était déjà multipart. Un dépôt sans pièce se comporte
   exactement comme avant, et le récit reste **obligatoire** : la pièce étaye, elle ne remplace pas.
3. La pièce passe par `ValidationFichierService` (Q-97) : mêmes `FORMATS_GED` et `TAILLE_MAX_GED` que
   la GED, une seule source de vérité pour les trois points d'entrée de fichier.
4. `GET /publications/{id}` renvoie `nomFichier` et `urlTelechargement` pour **les deux** sous-types
   — la lecture ne distingue plus un compte rendu d'une autre publication.

**Pourquoi pas une publication unique.** Fusionner les deux sous-types aurait fait porter à un seul
objet trois combinaisons que le métier n'accepte pas (fichier seul rattaché à une audience, texte
seul sans audience, audience avec un `type_document`), et effacé la distinction métier — **relater**
une audience contre **transmettre** un document du dossier — au profit d'une règle devinée.

**Vérifié** : 2 tests unitaires (dépôt avec pièce nommée, refus d'un format hors `FORMATS_GED`), le
parcours d'intégration P2 basculé en multipart avec pièce et relecture du `nomFichier` sur
PostgreSQL réel, le refus d'un récit vide en **400** (et non en 500 au flush), et le test sans pièce
conservé (`verifyNoInteractions(minioStorageService)`).

**Vérifié en réel** contre l'instance locale (2026-09-14), ce que le client MinIO mocké ne pouvait
pas montrer : dépôt avec pièce **201**, `nomFichier` restitué à l'avocat comme à l'Assistante, URL
pré-signée sur `dossiers/795/{uuid}-plumitif-….pdf` **effectivement téléchargeable** (HTTP 200) ;
dépôt sans pièce **201** avec `nomFichier: null` ; `.exe` refusé en **400** ; récit vide **400**.

### Q-99 ✅ Six listes paginées triaient sans index — tranché (M21)

Relevé le 2026-09-15 lors du recensement d'améliorations demandé par le porteur du projet, et
**appliqué sur sa décision**.

**A — Le constat.** M17 (Q-92, Q-93) puis M19 ont donné un ordre stable aux listes paginées. Six
d'entre elles trient sur une colonne qu'**aucun index ne portait** : `dossier` et `client` par
`created_at DESC, id DESC`, `demande_frais` de même, `alerte` par `date_declenchement DESC, id DESC`,
et `publication` par `date_depot` dans les deux sens (file interne #59 et portail avocat). Les
13 migrations comptaient 37 index, **aucun sur une colonne de tri** — vérifiable par
`grep "CREATE INDEX" | grep -c created_at` → `0`.

PostgreSQL devait donc trier l'intégralité du jeu filtré à **chaque page** pour n'en rendre que
vingt lignes.

**B — Pourquoi le corriger alors que rien ne se voyait.** C'est la forme exacte du défaut Q-94 : la
vue consolidée d'un client tenait parfaitement jusqu'à ce qu'un client atteigne 310 dossiers, et elle
est alors passée à **19 secondes**. Le remède est ici gratuit — des index, aucune ligne de code
applicatif, aucun changement de contrat.

**C — Mesuré**, sur une table jetable de **200 000 lignes** reproduisant la forme et le tri de
`dossier` (table supprimée après mesure) :

| | Plan | Temps |
|---|---|---|
| Sans index | `Parallel Seq Scan` + `top-N heapsort`, 200 000 lignes lues | **62,5 ms** |
| Avec index | `Index Scan`, 20 lignes touchées | **0,094 ms** |

Soit un facteur **665** sur la première page. Sur une page profonde (`OFFSET 5000`), l'index évite
toujours le tri complet : **5,4 ms**.

**Appliqué** : `V14__index_tris_pagination.sql`, neuf index reproduisant **exactement** l'ORDER BY de
chaque requête, colonne de filtre en tête quand il y en a une — un index dont l'ordre diverge du tri
demandé ne serait pas retenu par le planificateur. Deux index sur `alerte` et sur `demande_frais` :
la forme filtrée (par statut, par destinataire) et la forme complète ne peuvent pas être servies par
le même. `publication` reçoit un index ascendant, que PostgreSQL parcourt à l'envers pour le tri
descendant.

**Vérifié** : 358 tests au vert, `BUILD SUCCESS`. Aucun contrat modifié, aucun impact frontend.

**Vérifié en réel** (2026-09-15) : Flyway a appliqué la version 14 au démarrage
(`flyway_schema_history` → `14 | index tris pagination | t`) et les **neuf** index sont présents dans
`pg_indexes`. Le smoke et la suite de bout en bout passent sur la base migrée.

**Une précision qui évite un contresens.** Sur la base de développement — quelques dizaines de lignes
— le planificateur **ignorera ces index**, et il aura raison : un balayage séquentiel y est plus
rapide qu'un parcours d'index. Ce n'est pas un échec de la migration. Le gain se mesure au volume,
et c'est pourquoi la mesure ci-dessus a été faite sur 200 000 lignes.

### Q-106 ✅ Un index B-tree ne peut rien pour une recherche « contient » — tranché (M22)

Trouvé en écrivant les index des filtres de M22, et c'est un défaut **antérieur** qui passait
inaperçu depuis M16.

**A — Le constat.** Trois recherches de l'application cherchent un fragment **au milieu** d'un
texte : la référence d'un dossier (`LIKE '%…%'`, Q-72), celle d'une facture (Q-103) et le nom d'un
intervenant (Q-105). Or un index B-tree ordonne les chaînes **par leur début** : il sert
`LIKE 'DOS%'` et ne peut **rien** pour `LIKE '%DOS%'`.

`idx_dossier_reference` existait donc depuis V1 et semblait couvrir le filtre de référence — il ne
l'a jamais servi. Personne ne pouvait s'en apercevoir : la requête est correcte, elle est seulement
exécutée par balayage complet.

**B — Tranché** : extension `pg_trgm` et index **GIN trigramme** sur les trois colonnes. Les
trigrammes découpent le texte en triplets de caractères et les indexent ; la recherche d'un fragment
interne devient alors une recherche indexée.

**C — Prérequis d'exploitation, à porter à la DSI.** `CREATE EXTENSION` demande des droits élevés.
L'utilisateur applicatif les a sur le poste de développement ; **en production, l'extension devra
être créée par la DSI avant le premier démarrage**, sans quoi la migration échouera. L'échec est
franc et visible au démarrage — jamais silencieux, contrairement au défaut qu'il corrige.

### Q-100 à Q-105 ✅ Pagination généralisée, filtres et recherche au fil de la frappe — tranché (M22)

Demandé par le porteur du projet le 2026-09-15 : **dix lignes par tableau au maximum**, et des
filtres partout où ils manquent. Objectifs énoncés : « ni perte de données, ni lenteur », pour une
application « amenée à recevoir beaucoup de données ».

**Q-100 — pagination.** Huit endpoints renvoyaient une liste entière : `/repertoire/avocats`,
`/constitutions/prestataires`, `/intervenants`, `/ged/demandes-suppression`, et les onglets
Documents, Audiences et Alarmes d'un dossier. Tous passent en `PageResponse`. S'y ajoute
`GET /dossiers/{id}/historique`, créé : RG-DOS-06 historise toute action, et la fiche transportait
l'historique **entier** à chaque ouverture. La fiche n'en embarque plus que les **dix entrées les
plus récentes** — le champ du contrat garde sa forme, l'onglet pagine pour le reste, aucune entrée
n'est perdue.

**Deux limites assumées, écrites dans le code.** Le **répertoire des avocats** n'est paginé qu'à
moitié : US 5.8 le classe par charge croissante, or la charge vient d'un comptage agrégé joint en
Java et non d'une colonne. Aucun `ORDER BY` SQL ne peut produire cet ordre, donc la base ne peut pas
découper ce qu'elle ne sait pas trier — la pagination réduit ce qui **voyage**, pas ce que la base
lit. Et **trois listes restent entières** parce qu'elles sont structurellement bornées : étapes (trois
au maximum), délibérés et décisions d'un dossier. Y ajouter une pagination serveur aurait été du
contrat à maintenir pour toujours en échange de rien.

**Q-101 — filtrer les dossiers sensibles**, sur rappel explicite du porteur du projet. `estSensible`
et `sensibiliteStatut` s'ajoutent à `GET /dossiers`. Le second ne relève pas du confort :
`EN_ATTENTE` donne au DJ et à la DJA la **file des sensibilités à valider**, que rien n'exposait.
C'est la moitié de **QF-08**, que j'avais close à tort en concluant que la notification suffisait —
une notification prévient d'un événement, elle ne dit pas ce qui reste en attente.

**Q-102 — notifications** : `type` et `dossierId`. Avec dix-sept types d'alerte et des rappels à
J-7, J-3 et J-1, la liste se remplit vite et rien ne permettait d'y chercher.

**Q-103 — circuit des frais** : `dossierId`, `avocatId`, `referenceFacture`, `dateDebut`, `dateFin`.

**Q-104 — portail** : l'avocat filtre ses dépôts par `statut`.

**Q-105 — recherche au fil de la frappe** : `nom` sur `/intervenants`. C'est la réponse **durable**
au problème des sélecteurs, autorisée par le porteur du projet hors du périmètre initial. Une liste
déroulante qui demandait « une grande page » cassait silencieusement dès que l'annuaire la dépassait ;
elle interroge désormais le serveur pendant la frappe et n'a plus besoin de tout détenir. La taille
par défaut de `/intervenants` est donc **redescendue de 200 à 20** : ce qu'affiche une liste
déroulante, non ce qu'elle contient.

**Deux verrous de sécurité, posés parce que ces filtres touchent des données restreintes.** Le
**destinataire** d'une notification n'est pas filtrable : il reste résolu sur le jeton. Et pour un
avocat, `avocatId` est **imposé** à son propre identifiant, jamais lu depuis l'URL — sans quoi le
filtre destiné à l'instruction serait devenu un moyen de lire les demandes d'un confrère en changeant
un chiffre.

**Trois pièges de sélecteur, dont deux signalés par le compilateur.** `GET /intervenants` alimente
trois listes déroulantes et un seul tableau ; `PortailAvocatService` réutilise la requête des
audiences pour le sélecteur du formulaire de dépôt. Paginer sans regarder aurait amputé ces listes en
silence — exactement la « perte de données » que le porteur du projet demandait d'écarter, et
invisible en recette. Le sélecteur d'audiences passe donc explicitement `Pageable.unpaged()`.

### Q-107 ✅ Un avocat non inscrit au répertoire recevait un refus de droits — tranché (M22)

Signalé par le porteur du projet le 2026-09-15, après une réinitialisation de la base de
développement : connecté en avocat, il voyait « Le chargement a échoué — Aucun avocat du référentiel
n'est rattaché à ce compte » sur *Mon espace* et sur *Frais d'avocats*.

**A — Ce n'était pas un défaut de comportement.** Deux choses différentes portent le mot « avocat ».
Le **compte de connexion** vit dans Keycloak, et le backend crée à la volée la ligne `utilisateur`
correspondante. L'**avocat du référentiel** est une fiche métier, créée par le DJ ou la DJA
(UC-INT-01) et portant son identifiant de connexion (`compteKeycloak`, RG-INT-01). Le portail ne
travaille que sur la seconde. Sur une base vierge, la table est vide : le refus est donc conforme aux
sources — un avocat existe pour le métier parce qu'un juriste l'a inscrit, jamais parce qu'il possède
un compte.

**B — Le défaut était dans ce que l'application en disait.** Elle présentait un **état d'inscription**
comme une **panne**. L'avocat lisait un message technique, ne pouvait rien y faire, et ne savait pas
à qui s'adresser. Or ce cas n'est pas rare : il se produit à chaque mise en service, et pour chaque
nouvel avocat, tant que la Direction Juridique ne l'a pas inscrit.

**C — Tranché** : un code d'erreur **dédié**, `ERR-AVOCAT-NON-RATTACHE`, lève les quatre points
concernés (portail, frais ×2, publications), et l'interface l'affiche comme un état, avec ce qui
manque et à qui s'adresser.

**Pourquoi un code et non une lecture du message.** `ERR-FORBIDDEN` couvre aussi les **vrais** refus
de droits : s'en servir aurait fait dire « votre compte n'est pas rattaché » à un avocat qui force
une URL interdite — un mensonge. Et comparer des phrases se briserait à la première reformulation ou
traduction. C'est le parti déjà retenu pour `ERR-009` (accès restreint à une publication), qui isole
un état légitime d'un refus.

**Au passage**, les quatre points levaient **deux formulations** pour une cause unique (« Aucun avocat
associé à cet utilisateur » et « Aucun avocat du référentiel n'est rattaché à ce compte »). Elles
sont unifiées.

**Symétrique de QF-16.** Là, un juriste jamais connecté reste inaffectable ; ici, un avocat jamais
inscrit ne voit rien. Dans les deux cas le système a raison — c'est son explication qui manquait.

### Q-115 · A1 ✅ Les PDF cessent d'échouer sur ce qu'on y écrit — tranché (2026-09-18)

**Premier jalon du chantier de mise en forme des documents.** Il ne touche **à aucune mise en page** :
c'est le socle sur lequel les jalons visuels deviennent vérifiables. Une seule chose change à l'œil,
et elle est assumée plus bas : la typographie se replie désormais partout, non plus sur le seul audit.

**Le défaut trouvé, et pourquoi il dépassait la mise en forme.** Les quatre documents du système sont
écrits en Helvetica, encodée en `WinAnsiEncoding`. PDFBox **lève** — il ne remplace pas
silencieusement — dès qu'on lui demande un caractère absent de cet encodage. Seul le rendu d'audit
(Q-113) s'en protégeait ; les trois autres appelaient `showText` en direct. Sur la lettre de
constitution, quatre saisies parfaitement ordinaires faisaient échouer la génération, prouvées par un
test écrit avant le correctif — **4 échecs sur 4** :

| Point de code | Origine |
|---|---|
| `U+000A` | la touche Entrée : le motif est saisi dans un `<Textarea>` |
| `U+0009` | une tabulation, invisible à l'écran |
| `U+202F` | l'espace fine insécable, que tout collage depuis un traitement de texte apporte |
| `U+1EC5` | la lettre « ễ » d'un nom vietnamien |

La lettre est produite **dans la transaction de validation** (`ConstitutionService#valider`) :
l'exception annulait la validation elle-même. La demande restait `EN_ATTENTE`, le SH recevait une
erreur technique, le prestataire n'était pas notifié — et comme le motif est enregistré, chaque
nouvelle tentative échouait à l'identique. **Un processus métier bloqué par une saisie légitime**, et
non un export raté.

**Le repli partagé** (`common/pdf/TextePdf`) applique trois gestes, dans cet ordre : les caractères de
contrôle deviennent une espace — une ligne de PDF ne se coupe pas toute seule ; la typographie est
repliée sur l'ASCII, ce qui sert au passage QF-48 ; ce qui reste hors encodage est **décomposé**,
« Nguyễn » s'écrivant « Nguyen » plutôt qu'un point d'interrogation qui effacerait l'information. Un
idéogramme, lui, n'a pas de lettre de base : il devient `?`, seul cas de perte, et toujours préférable
à une exception.

La copie privée qui vivait dans `AuditExportRenderer` est supprimée : elle ne protégeait qu'un
générateur sur quatre. Elle retenait « inférieur à 256 », intervalle qui **laisse passer** `U+007F` à
`U+009F` — des caractères de contrôle que la police refuse tout autant.

**Les métadonnées** (`common/pdf/MetadonneesPdf`). Aucun des quatre documents n'en portait : dans un
explorateur de fichiers, une messagerie ou un moteur documentaire, ils étaient anonymes. Chacun porte
désormais un titre, un producteur et sa date. L'auteur de la lettre est le **validateur**, jamais le
juriste demandeur : c'est la seconde signature qui engage la banque (RG-INT-08).

**Les tests montent d'un cran, et c'est la moitié du jalon.** Les trois générateurs historiques
n'étaient éprouvés que par « non vide, commence par `%PDF-` » : un document ayant perdu toutes ses
données passait au vert. Ils lisent désormais le contenu avec `PDFTextStripper`, comme
`AuditExportRendererTest` le faisait déjà. Vérification faite en cassant volontairement le calendrier :
le test tombe. Sans cela, « ne rien casser » serait resté une affirmation au lieu d'être une mesure.

**Une micro-conséquence assumée** : le repli de la typographie s'applique maintenant aux quatre
documents, donc `…` devient `.` et `’` devient `'` hors de l'audit aussi. C'est le comportement que
l'audit a depuis Q-113, et la cohérence valait mieux que deux typographies selon le document.

### Q-114 ✅ L'empreinte devient utilisable — tranché (2026-09-17)

**Le constat de recette.** Q-113 scellait correctement les archives, mais l'écran affichait
`empreinte 59bde5c1748ce074555d0329ec96fbfb0cf270815292619023866c8fd04b807e` — soixante-quatre
caractères d'un seul tenant, que personne ne compare et que personne ne dicte. Une empreinte que nul
ne vérifie n'est pas une preuve : c'est une décoration. Trois manques, trois réponses.

**1. Vérifier depuis l'application.** `GET /api/historique/exports/verification?empreinte=` répond
`{reconnue, archive}`. **Seule l'empreinte circule** : elle est calculée par le navigateur
(`crypto.subtle.digest`), et un document d'audit n'a aucune raison de traverser le réseau pour être
comparé à lui-même. Un fichier inconnu **n'est pas une erreur, c'est une réponse** — `reconnue:false`
et non un 404 : « je ne reconnais pas ce document » et « la vérification a échoué » n'engagent pas
les mêmes suites. Le 400 reste réservé à ce qui n'est pas une empreinte du tout.

**2. Une référence lisible à côté de l'empreinte.** `AUD-2026-0042` s'écrit, se prononce, se
retrouve. **L'empreinte prouve, la référence désigne** : ce sont deux métiers, et les confondre est
ce qui rendait l'empreinte pénible à l'usage.

*Pourquoi une séquence dédiée et non l'identifiant de la ligne.* La référence doit figurer **dans le
PDF**, donc être connue **avant** de produire le fichier — alors que l'identifiant n'existe qu'après
l'insertion, laquelle exige l'empreinte, laquelle exige le fichier. Une séquence indépendante rompt
ce cercle. C'est la même raison qui interdit de dériver la référence de l'empreinte : rien de ce qui
décrit le contenu d'un fichier ne peut être écrit dedans sans le changer. Pas de compteur remis à
zéro chaque année — il exigerait un verrou pour un gain nul ; l'année dit quand, le numéro dit lequel.

**3. Un colophon dans le PDF.** Dernière page : période, nombre de lignes, auteur, instant de
génération, et l'explication de ce que l'empreinte est et pourquoi elle ne peut pas figurer dans le
fichier qu'elle scelle. Sans cette page, celui qui reçoit la pièce ignore qu'une empreinte existe.

**Le nom du fichier porte la référence** (`aud-2026-0042-2026-09-01-2026-09-17.pdf`) : c'est ce nom
qu'on cite dans un courrier de transmission.

**Défaut trouvé en chemin, et corrigé (CORS).** `Content-Disposition` et `X-Empreinte-SHA256`
n'étaient **pas exposés** : CORS ne laisse lire qu'une poignée d'en-têtes, les autres existent sur le
fil mais restent invisibles au code appelant. Conséquence, depuis Q-113 : le bandeau de succès
affichait « empreinte … » vide, et le front reconstruisait un nom de fichier plausible mais
différent de celui sous lequel l'archive est consignée. `setExposedHeaders` le corrige. Le parcours
e2e de Q-113 ne l'avait pas vu parce qu'il se contentait de la phrase d'accompagnement sans regarder
la valeur — assertion resserrée depuis. **Une assertion qui ne regarde pas la valeur ne garde rien.**

**Le classeur porte désormais sa provenance, comme le PDF.** Une feuille *Provenance*, en seconde
position pour ne pas s'interposer entre celui qui ouvre le classeur et les lignes qu'il vient
chercher : référence, période, nombre de lignes, auteur, instant. L'asymétrie précédente — colophon
dans le PDF, rien dans l'Excel — laissait la matière qu'on trie sans provenance dès qu'on la
renommait, alors qu'elle en a autant besoin que la pièce qu'on remet.

**Ce que la vérification ne prouve pas, et l'écran le dit.** « Reconnue » démontre que le fichier n'a
pas changé depuis son scellement. Elle n'établit pas son antériorité face à un tiers : l'application
détient à la fois le fichier et la ligne qui l'atteste. L'horodatage opposable relèverait d'un tiers
de confiance — hors périmètre, et énoncé plutôt que tu.

### Q-113 ✅ L'export de l'audit, scellé — tranché (2026-09-17)

**Jalon 3, et dernier du chantier d'audit.** Un inspecteur ne consulte pas un écran : il emporte une
pièce. Mais une pièce qu'on peut rééditer en trois minutes ne prouve rien — c'était l'objection
retenue contre l'archivage tel qu'il avait d'abord été envisagé.

**La chaîne complète fait la valeur du jalon** : produire, calculer une **empreinte SHA-256**,
déposer dans MinIO, consigner l'empreinte en base avec la période, l'auteur et l'instant. Le fichier
remis et le fichier scellé sont alors démontrablement le même, des années plus tard. L'empreinte
voyage aussi dans un en-tête de réponse : le destinataire peut la comparer sans interroger
l'application.

**Les deux formats, jamais l'un ou l'autre.** Le PDF produit une pièce figée qu'on remet ; l'Excel
une matière qu'on trie et recoupe. Retenir un seul format aurait obligé l'un des deux usages à se
contenter d'un outil qui ne lui convient pas.

**Les six raccourcis de période sont calculés par l'écran, pas par l'API.** Ce sont des commodités de
saisie : les exposer au serveur aurait figé six calendriers et interdit la **période libre**, celle
que réclame la première inspection qui demande « du 12 mars au 4 juin ». Chaque raccourci couvre
l'unité calendaire **écoulée** et s'arrête aujourd'hui — une archive ne doit pas prétendre couvrir
des jours à venir.

**La période est obligatoire à l'export, facultative à la consultation.** Une archive sans période ne
dit pas ce qu'elle contient : son empreinte ne prouverait que son intégrité, jamais son exhaustivité.

**Le plafond refuse plutôt que de tronquer.** Au-delà de 20 000 lignes, l'extraction est refusée avec
la conduite à tenir — restreindre la période. Tronquer silencieusement aurait produit une archive qui
ment sur ce qu'elle couvre, et dont l'empreinte aurait scellé ce mensonge.

**Deux actions de plus sont historisées.** `AUDIT_EXPORTE` — exporter fait sortir le contenu de
l'application, acte plus lourd que consulter. `ARCHIVE_TELECHARGEE` — sans elle, l'archive serait un
trou plus large que la table qu'elle double, puisqu'on pourrait y puiser sans que rien ne le dise.
La clé de stockage n'est jamais exposée : elle ne servirait qu'à contourner l'endpoint tracé.

**Deux défauts rencontrés, et ce qu'ils ont appris.** La colonne d'empreinte était déclarée
`CHAR(64)` en SQL face à un `@Column` ordinaire : PostgreSQL la rend en `bpchar` et la validation de
schéma Hibernate l'a refusée — **62 erreurs d'un coup, sur un détail de type**. Corriger la migration
après qu'elle eut été appliquée en base locale a ensuite fait échouer la validation Flyway : dérive
locale sans effet sur une base neuve, nettoyée en retirant la ligne d'historique et la table.

**Statut (2026-09-17)** : appliqué — V20, `GET /historique/export`, `GET /historique/exports`,
`GET /historique/exports/{id}/telechargement`, bloc d'extraction sur l'écran 43.

### Q-112 ✅ L'écran d'audit — tranché (2026-09-17)

**Jalon 2 du chantier d'audit**, rendu possible par Q-111 : sans code d'action typé, les filtres
auraient été approximatifs, et un écran approximatif ne se retire plus une fois entre les mains du
métier.

**Ce qu'il ouvre, et que Q-110 ne pouvait pas.** Le journal répondait à une question de
*couverture* — des lignes existaient sans point de lecture. Restait une question de *point
d'entrée* : « qu'a fait X le mois dernier ? », « toutes les suppressions du trimestre ? », « que
s'est-il passé entre le 1er et le 15 ? ». Aucune ne part d'un dossier ; aucune n'était possible.

**`GET /historique`** remplace `GET /historique/systeme`, supprimé. Le périmètre — tout /
hors dossier / un dossier — devient un **critère** parmi les autres, et la vue « hors dossier » une
valeur de filtre. Deux endroits où chercher la même trace étaient exactement la fragmentation qu'on
voulait supprimer ; le journal était un palliatif assumé, il n'en était plus qu'un sous-ensemble.

**Le périmètre cesse d'être structurel, et c'est assumé.** En Q-110, l'exclusivité de `Perimetre`
protégeait l'interim : ouvrir la vue transversale devait être une décision, pas l'effet d'un
paramètre oublié. La décision est prise (NF-CONF-01, filtre par personne autorisé). Ce qui protège
désormais est explicite : la garde de rôle, et la trace de chaque consultation.

**La consultation de l'audit s'audite elle-même** (`AUDIT_CONSULTE`, V19), avec ses critères.
Ce n'est pas une contrainte imposée au DJ et à la DJA : c'est ce qui leur permet de **démontrer**
qu'ils n'ont regardé que ce qu'ils devaient. Deux décisions de granularité : historisé **avant** la
lecture, pour que la trace existe même si la requête échoue ; et **sur la première page seulement**,
feuilleter un résultat n'étant pas une nouvelle consultation — compter chaque page produirait un
bruit qui noierait ce qu'on cherche à voir. Le critère « personne » est consigné sous
`auteurRecherche` et non `utilisateurId`, ce dernier désignant déjà celui qui a consulté.

**Un défaut trouvé par les tests, et la propriété qu'il révèle.** La première version du test
d'intégration lisait `content[0]` pour retrouver la trace : or **lire l'audit crée une ligne
d'audit**, qui prend aussitôt la tête du classement. Le défaut était dans le test ; la propriété
méritait d'être affirmée plutôt que contournée, et elle l'est désormais.

**Le journal déménage, mais sa moitié utile reste.** La question qui naît *en regardant un réglage* —
« pourquoi vaut-il 14, et qui l'a mis là ? » — mérite une réponse sur place. L'écran des paramètres
affiche donc « modifié le … par … » sur chaque ligne (`configuration_systeme.dernier_auteur_id`,
V19), et le tableau paginé à filtres s'en va. Administrer et enquêter sont deux tâches ; un outil
d'enquête n'avait pas sa place sur un écran d'administration.

**Habilitation** : capacité dédiée `consulterAudit`, **distincte** d'`administrerConfigurations` et
de `superviserAlertes` bien qu'elle porte les mêmes rôles — lire l'activité nommée d'un collègue
n'est ni écrire un seuil ni lire un taux global, et un assouplissement futur de l'une ne doit pas
déplacer l'autre.

**Ce qui reste, et l'écran le dit lui-même** : seules les **modifications** sont historisées. Aucune
consultation ne l'est, hormis celle de cet écran. L'absence de ligne ne prouve pas l'absence
d'action.

**Statut (2026-09-17)** : appliqué — V19, `GET /historique`, écran 43, rappel contextuel sur
l'écran 41.

### Q-111 ✅ La nature des actions historisées est typée — tranché (2026-09-17)

**Jalon 1 du chantier d'audit.** `historique_action.action` était une phrase française libre,
parfois concaténée — « Modification de la configuration SEUIL_PROROGATIONS ». Filtrer sur une telle
colonne suppose un `LIKE '%…%'`, qui paraît commode et **ment par omission** dès qu'un libellé change
d'un mot : la ligne cesse d'être trouvée sans que rien ne le signale. C'est le préalable à toute vue
d'audit, et la raison pour laquelle ce jalon **précède** l'écran au lieu de le suivre — un écran
livré d'abord serait un outil dont les filtres sont approximatifs, et qu'on ne retire plus une fois
entre les mains du métier.

**Mesuré avant d'agir** : 42 points d'écriture, **un seul chemin** (`HistoriqueActionService` —
aucune insertion directe, aucune ligne semée par migration), dont **36 libellés littéraux** et
**6 construits**.

**Le libellé est porté par l'énumération, pas transmis à l'appel.** Décision prise en cours de
route, meilleure que ce qui avait été annoncé : les 42 sites ne passent plus qu'un code, et le
service en dérive la phrase. Deux arguments côte à côte — un code et le texte censé le décrire —
auraient fini par diverger ; l'écart est désormais impossible. La signature garde son arité.

**Les six parties variables descendent dans `details`**, où elles deviennent interrogeables :

| Avant | Code | `details` reçoit |
|---|---|---|
| « Changement de statut de l'étape INSTANCE » | `ETAPE_STATUT_MODIFIE` | `etape`, `ancienStatut`, `nouveauStatut` |
| « Ouverture automatique de l'étape … » | `ETAPE_OUVERTE_AUTOMATIQUEMENT` | `etape`, `etapeId` |
| « Positionnement direct à l'étape … » | `ETAPE_POSITIONNEE_DIRECTEMENT` | `etape`, `etapeId` |
| « Décision sur la dérogation de seuil : … » | `DEROGATION_SEUIL_ARBITREE` | `decision`, `auditId`, `motif` |
| « Décision sur la sensibilité du dossier : … » | `SENSIBILITE_ARBITREE` | `decision`, `motifRejet` |
| « Modification de la configuration SEUIL_X » | `CONFIGURATION_MODIFIEE` | `cle`, `ancienneValeur`, `nouvelleValeur` |

**Quatre décisions de conception.** La colonne est **obligatoire**, avec `NON_CLASSEE` pour ce que la
reprise ne saurait rattacher — « on ne sait pas » doit se filtrer et se compter, là où un nul se
glisserait hors de chaque requête. **La phrase reste en base** à côté du code : l'affichage existant
ne change pas, et traduire un jour ce libellé (QF-24) sera un travail purement frontend, sans
repasser sur les 42 sites. **Un code par action métier distincte**, soit 43 valeurs — regrouper pour
faire court aurait rendu les filtres approximatifs. **Contrainte `CHECK`** en base, sur le modèle de
`chk_alerte_type` : toute action auditée nouvelle demandera une migration, et un test rappelle ce
lien, qu'une compilation réussie ne signale pas.

**Reprise mesurée en réel** : 193 lignes existantes, **zéro `NON_CLASSEE`**. Les 41 libellés
distincts se réduisent à 40 codes — la concaténation des clés de configuration est absorbée, et le
filtre du journal ne propose plus une entrée par paramètre.

**Statut (2026-09-17)** : appliqué — V18, énumération, 42 sites, filtre du journal basculé sur les
codes. Backend 388/388, frontend 299/299.

### Q-110 ✅ Les actions historisées hors dossier deviennent lisibles — tranché (2026-09-17)

**Point de départ** : une analyse de l'auditabilité demandée par le porteur du projet. RG-DOS-06
historise chaque modification — **42 points d'écriture, 11 services, 45 libellés distincts, 100 %
des lignes portant des `details`** — mais l'unique point de lecture, `GET /dossiers/{id}/historique`,
part d'un dossier. Les actions qui n'en ont pas étaient donc **écrites et jamais relues** :
modifications de paramètres système, et indexations de jurisprudence sans dossier. Mesuré sur la base
de recette : **26 lignes sur 657, soit 4 %**.

**Ce que la mesure a corrigé dans l'analyse.** Une lecture statique du code n'avait trouvé qu'une
famille d'actions concernée (`ConfigurationService`). La base en a révélé une seconde —
`JurisprudenceService` passe un `dossierId` qui peut être nul, ce qu'aucun `grep` ne voit.

**Pourquoi c'était devenu pressant.** Q-108 a ouvert l'administration des paramètres à la DJA. Il
avait alors été argumenté que « la traçabilité n'y perd rien, chaque écriture étant historisée avec
son auteur » : exact en base, mais le nombre de détenteurs de ce pouvoir doublait sans qu'aucun écran
ne permette d'en relire l'exercice. « Qui a abaissé le seuil de validation conjointe ? » exigeait une
requête SQL.

**Livré** : `GET /historique/systeme`, réservé au **DJ et à la DJA**, filtrable par auteur, libellé
et période, et un bloc sur l'écran 41, sous les paramètres — l'adjacence est la bonne, vingt-deux des
vingt-six lignes étant des modifications de réglages.

**Quatre décisions de conception.**

1. **Le périmètre est structurel, pas conventionnel.** `HistoriqueActionSpecifications.Perimetre`
   est une énumération à trois valeurs exclusives ; l'endpoint fige `HORS_DOSSIER`. Ouvrir un jour la
   vue transversale serait une décision, pas l'effet d'un paramètre oublié.
2. **Le filtre par libellé est une liste fermée**, construite à partir des libellés réellement
   présents en base, et **indépendante des filtres en cours** — sinon choisir un libellé ferait
   disparaître les autres choix. `action` étant une phrase française libre (QF-24), un `LIKE '%…%'`
   aurait paru commode et menti par omission dès qu'un libellé change d'un mot.
3. **DJ et DJA, pas `ROLE_CONSULTATION`**, alors que l'historique d'un dossier est ouvert à tout
   profil interne. Sur une fiche, on regarde un dossier ; ici la lecture est agrégée et filtrable par
   auteur — même contenu, autre nature. La restreindre est le minimum tant que **NF-CONF-01**
   (RGPD, conservation) reste « à définir avec la DRH » dans les sources.
4. **« Journal », jamais « Audit ».** Seules les modifications sont historisées ; **aucune
   consultation ne l'est**. Ouvrir un dossier sensible ou télécharger une pièce ne laisse aucune
   trace. L'écran le dit lui-même, plutôt que de laisser le défaut se découvrir au moment où l'on en
   aurait besoin.

**Ce que cela ne règle pas, et qui reste ouvert.** Le manque comblé était une question de
*couverture* ; il reste une question de *point d'entrée*. Trois questions demeurent sans réponse :
« qu'a fait X le mois dernier ? », « toutes les suppressions du trimestre ? », « que s'est-il passé
entre le 1er et le 15 ? ». Y répondre suppose deux préalables — un **code d'action typé** (QF-24), et
**NF-CONF-01** tranché. La décision d'ouvrir ou non cette vue est laissée au porteur du projet.

**Statut (2026-09-17)** : appliqué — V17 (deux index partiels), spécification, endpoint, écran,
tests. Backend 385/385, frontend 299/299, bout en bout vérifié contre la pile réelle.

### Q-109 ✅ Le taux de délivrance des alertes devient mesurable — tranché (2026-09-16)

**Point de départ** : un recensement de l'observabilité de l'application, demandé par le porteur du
projet. NF-ALR-02 fait du canal applicatif le canal **obligatoire**, avec une délivrance supérieure
à 99 %. Ce taux n'était mesuré **nulle part** : un échec de diffusion produisait une ligne de journal,
que personne ne lit. C'était la seule exigence non fonctionnelle dont la rupture était **silencieuse
pour tout le monde**, y compris pour la personne qu'elle pénalise — un juriste qui découvre après
coup l'audience qu'on ne lui a pas rappelée.

**Le piège, et la raison d'être de la conception retenue.**
`SimpMessagingTemplate.convertAndSendToUser` **ne lève aucune exception** lorsque le destinataire
est déconnecté : elle route vers une file sans abonné et retourne normalement. La mesure évidente —
« pas d'exception, donc délivré » — aurait donc affiché **100 % en permanence, panne comprise**.
Une mesure qui ment est pire que pas de mesure. Il faut interroger
{@code SimpUserRegistry} **avant** de pousser, pour distinguer l'absence de destinataire, qui est
normale, de la rupture du canal, qui est le défaut.

**Trois issues, enregistrées sur l'alerte** (`statut_diffusion`, migration V16) :

| Issue | Sens | Délivrée ? |
|---|---|---|
| `DIFFUSEE` | session WebSocket vivante, poussée aboutie | oui |
| `DIFFEREE` | aucune session ; l'alerte attend dans `GET /alertes/mes-notifications` | **oui** |
| `ECHEC` | courtier indisponible ou transport rompu | **non** |

**Deux taux, et les confondre serait la première erreur.** Le **taux de délivrance**
(`1 − ECHEC/observées`) répond à NF-ALR-02 : le canal a-t-il fait son travail ? Le **taux de
diffusion temps réel** (`DIFFUSEE/observées`) répond à tout autre chose : à quel point les gens
sont-ils connectés quand on les alerte. Un second taux bas n'est pas un défaut du système — c'est
une information sur les usages, et souvent la raison de brancher enfin un second canal. Aucun seuil
ne lui est opposé, et l'écran le dit explicitement.

**Ce qui est délibérément exclu du calcul.** Les alertes antérieures à V16 n'ont pas d'issue
observée : les compter comme réussies gonflerait le taux d'une valeur inventée, les compter comme
échouées l'effondrerait autant. Elles sont hors dénominateur — le taux ne porte que sur ce qui a été
réellement mesuré. De même, une **fenêtre sans aucune alerte** est déclarée conforme : il n'y a rien
à respecter ni à enfreindre, et afficher « 0 % » ferait sonner une alarme sur du vide.

**`nonTraitees` ne participe à aucun taux**, et c'est voulu : une alerte techniquement délivrée que
personne n'a ouverte relève d'un autre problème que la délivrance. Les mélanger brouillerait les
deux signaux.

**Statut (2026-09-16)** : appliqué — V16, `GET /alertes/statistiques-diffusion` (DJ/DJA), bloc de
supervision sur l'écran 41. Backend 378/378, frontend 294/294.

### Q-108 ✅ L'administration des paramètres est ouverte à la DJA — tranché (2026-09-16)

**Point de départ** : le porteur du projet demande un relevé complet des habilitations du DJ et de
la DJA, partant de l'hypothèse que « la DJA peut tout ce que fait le DJ ».

**Ce que le relevé a montré — et l'hypothèse était presque juste.** `ROLE_DJ` et `ROLE_DJA` sont
**tous deux** composites de `ROLE_JURISTE` + `ROLE_SAISIE` : vérifié sur les jetons réels, les deux
profils portent exactement les mêmes rôles à un près. Sur les 80 `@PreAuthorize` du backend, **65
leur sont communs**. Il ne restait que **deux écarts, symétriques** :

| Capacité | Endpoints | DJ | DJA |
|---|---|---|---|
| Écriture des paramètres | `PUT /configurations/{cle}`, `PUT /alertes/seuils` | ✅ | ❌ |
| Contrôle d'opportunité | `PUT /frais/demandes/{id}/opportunite` | ❌ | ✅ |

**Décision du porteur du projet** : ouvrir l'écriture des paramètres à la DJA. Le motif est la
**continuité de service** — le DJ absent, plus personne ne pouvait relever un seuil de prorogation
ou la taille maximale d'un document, alors qu'un Adjoint existe précisément pour suppléer. La
traçabilité ne s'y perd pas : `ConfigurationService.modifier` historise déjà chaque écriture avec
son auteur.

**Pourquoi les deux endpoints et pas un seul.** Une ouverture limitée à `PUT /alertes/seuils` avait
été envisagée puis écartée : `CHARGE_MAX_AVOCAT` est une clé de `configuration_systeme` comme les
autres et s'affiche sur le **même écran**. La DJA aurait pu enregistrer un champ et pas ses voisins,
ce qui aurait imposé d'inventer une permission par clé.

**L'ouverture est à sens unique, et c'est délibéré.** Le contrôle d'opportunité **reste réservé à la
DJA** : le donner aussi au DJ effondrerait sur un même profil les deux étapes que RG-INT-03 sépare —
préparer l'avis d'opportunité, puis voter la validation conjointe. Un test dédié verrouille ce refus,
pour qu'on ne « harmonise » pas les deux rôles par symétrie.

**Défaut de couverture révélé au passage** : ces deux endpoints n'avaient **aucun** test
d'habilitation. La restriction ne tenait qu'à une annotation, sur une règle pourtant écrite deux fois
dans les sources. Trois tests d'intégration comblent le manque.

**Statut (2026-09-16)** : appliqué — `hasAnyRole('DJ', 'DJA')` sur les deux endpoints,
`PROJECT_CONTEXT.md` §4 et `FUNCTIONAL_MAP.md` (FR-ALR-01, UC-ALR-01) mis à jour.

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

### Q-20 ✅ `POST /publications/{id}/correspondance` — implémenté (M11)
« À proposer » (US 5.5). RG-INT-07 impose que seul le DJ/DJA transmette une correspondance unique.
**C** : créer l'endpoint et une table `correspondance_avocat`.
**Statut (2026-09-07, M11)** : appliqué — `correspondance_avocat` (`UNIQUE(publication_id)`,
`V4__publication_commentaires_correspondances.sql`), `PublicationService.transmettreCorrespondance`
(409 si déjà transmise), notifie l'avocat.

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
