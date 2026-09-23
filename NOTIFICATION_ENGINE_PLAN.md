# Plan d'intégration du Notification Engine

Document de travail vivant. Il est **relu avant chaque étape**, puis **mis à jour à la fin de chaque étape** :
statut, écarts constatés, décisions nouvelles. Branche `feat/notification`, dans le backend et le frontend.

Source de référence : `../Guide_V1_Integration_Notification_Engine.docx`, désigné ci-dessous par « le Guide ».

> **Aucun secret ni aucune coordonnée personnelle dans ce document.** La clé d'API, le secret JWT et
> les destinataires de test vivent uniquement dans le `.env` du backend, qui n'est pas versionné.

---

## 1. Règles de travail

- Une étape à la fois. Chaque étape se termine par : les tests, la suite complète verte, une recette
  manuelle, un message de commit, puis **STOP** jusqu'à la validation.
- Je ne fais jamais de commit ni de push : je fournis le message et l'utilisateur committe.
- Avant tout `./mvnw clean`, je vérifie que les ports 8080 et 3000 sont libres (vérification bloquante).
  En fin de travail, je libère ces ports.
- Je n'invente aucune règle métier : ce qui n'est pas tranché va dans « Points ouverts » (§ 7).
- Les libellés de l'interface sont en français et en anglais (`messages/fr.json` et `messages/en.json`).

### Utilisation du `.env`

L'administrateur a fourni le compte de service. Il est collé **en commentaire** dans le `.env` du
backend, section « Notification engine », sous forme de bloc JSON. Ce bloc contient `name`, `code`,
`apiKey`, `jwtSecret`, `channels` et `priority`.

L'application ne lit pas ce bloc commenté. Le jour où une valeur devient nécessaire :

1. je la **copie** depuis le bloc commenté et je la **colle dans le même `.env`**, sous forme de
   variable active, sous le bloc, qui reste intact ;
2. je ne l'affiche jamais, ni dans une réponse, ni dans un log, ni dans un fichier versionné ;
3. je contrôle la variable par assertion : présence et longueur, jamais la valeur elle-même ;
4. je note ici quelles variables ont été activées, mais jamais leurs valeurs (§ 6).

Les variables prévues sont les suivantes :

| Variable | Origine | Nécessaire à partir de |
|---|---|---|
| `NOTIFICATION_ENGINE_ACTIF` | décision (`false` par défaut) | étape 8, premier envoi réel |
| `NOTIFICATION_ENGINE_CODE_SERVICE` | `code` du bloc | étape 8 |
| `NOTIFICATION_ENGINE_JWT_SECRET` | `jwtSecret` du bloc | étape 8 |
| `NOTIFICATION_ENGINE_API_KEY` | `apiKey` du bloc | seulement si l'API REST devient joignable (aujourd'hui, non) |
| `NOTIFICATION_REDIRECTION_*` | destinataires de test (§ 3) | étape 8 |
| `NOTIFICATION_ENGINE_KAFKA_*` / Dapr | Guide, environnement DEV | étape 8 |

---

## 2. Faits établis (ne pas redemander)

### Compte de service

- Code `audience-deliberer`.
- Quatre canaux : SMS, EMAIL, WHATSAPP et TEAMS.
- Priorité STANDARD.
- La clé d'API est un UUID. Le secret JWT fait 43 caractères ; le Guide exige au moins 32 caractères.

### Réseau mesuré depuis le poste (2026-09-22)

| Cible | Résultat |
|---|---|
| Kafka DEV `172.21.88.31:32092` | **joignable** |
| Kafka LOCAL `172.21.88.110:9092` | injoignable |
| Kafka TEST `172.25.5.84:32039` | injoignable |
| API REST et gRPC (`notification-engine`) | injoignables : ce nom n'est connu qu'à l'intérieur du cluster |

On en tire deux conséquences :
- le suivi de la délivrance passe par les topics `notification-sent` et `notification-failed` ;
- la consultation REST, qui s'authentifie avec l'en-tête `X-Service-Key`, est inutilisable depuis le poste.

### Contrat du Guide

- **Canaux** : `SMS`, `EMAIL`, `WHATSAPP` et `TEAMS`. Le destinataire TEAMS est l'adresse Azure AD de la
  personne. Le téléphone s'écrit en E.164, ou commence par `237`, ou par `6`.
- **Champ `channelRecipients`** : un destinataire par canal. Quand ce champ est présent, `recipient`
  est ignoré.
- **Champ `eventId`** : la déduplication repose dessus. Un message qui reprend un `eventId` déjà traité
  est ignoré. Il doit donc être déterministe et dérivé de la référence métier.
- **Champ `token`** : jeton JWT HS256 signé avec `jwtSecret`, avec `sub` = code du service et une
  durée de vie de 5 minutes (`exp` = `iat` + 5 min). **Un jeton invalide est rejeté sans aucun retour.**
- **Topic d'envoi STANDARD** : `notification-send-standard`, via le composant pubsub Dapr
  `notification-pubsub-standard`. Le Guide ne montre que le composant `notification-pubsub` : nous
  écrirons le second sur son modèle.
- **Suivi** : livraison au moins une fois (un même accusé peut arriver deux fois), avec un filtre sur
  `serviceId`.
- **Pièces jointes** : par dépôt préalable dans MinIO, puis envoi de la seule référence
  (« claim-check »), et uniquement sur EMAIL.

### Système d'alertes existant

- Point d'entrée unique : `AlerteService.declencher(TypeAlerte, dossierId, destinataire, declencheur)`,
  exécuté dans la transaction de l'action métier.
- Cette méthode crée l'alerte en canal APPLICATIF, puis l'envoie par STOMP sur
  `/user/{keycloakId}/queue/alertes`.
- Les prestataires ne sont notifiés que si l'avocat a un compte applicatif. L'autre prestataire n'est
  jamais notifié.
- Les messages contiennent des marqueurs internes (`[audienceId:5;J-7]`) et des codes de règle
  (« (RG-INT-04) »). **Ils ne doivent jamais sortir vers un canal externe.**

---

## 3. Décisions validées

| # | Décision |
|---|---|
| D1 | **Utilisateurs internes** : Teams et/ou Outlook (EMAIL professionnel), selon la configuration. |
| D2 | **Prestataires** (avocat, même avec un compte, et autre prestataire) : SMS, WhatsApp et courriel personnel, selon la configuration. |
| D3 | **Messages** rédigés dans l'application, versionnés dans le dépôt. Pas de gabarits côté Engine. |
| D4 | **Configuration par le DJ et le DJA** : les canaux actifs et la grille type d'événement × canal. |
| D5 | **Pièce jointe** : la lettre de constitution, par courriel uniquement. |
| D6 | **Production** : hors du périmètre de ce chantier. |
| D7 | **Transport** : sidecar Dapr (voie documentée), avec Kafka direct en repli, derrière `PasserelleNotificationEngine`. gRPC est exclu : le fichier de contrat n'a pas été fourni. |
| D8 | **Topic** : `notification-send-standard`. Il correspond à la priorité du compte, et ce topic rattrape les messages après une panne. |
| D9 | **Interrupteur général**, désactivé par défaut : l'adaptateur « journal » n'envoie rien. |
| D10 | **Redirection en DEV** : tout envoi part vers les destinataires de test du canal ; le vrai destinataire est seulement journalisé. Les adresses de test sont définies dans le `.env`, jamais dans le code. |
| D11 | **Un compte de service par environnement**, avec des secrets lus uniquement depuis les variables d'environnement. |
| D12 | **Mise en file dans la même transaction** que l'action métier, puis envoi séparé. Une panne de l'Engine ne bloque ni n'annule jamais l'action métier. |
| D13 | **Nouvelles tentatives** : jusqu'à 5, à 1 min, 5 min, 15 min, 1 h puis 6 h. Au-delà, statut ÉCHEC visible sur l'écran 41. Un échec définitif signalé par l'Engine n'est pas retenté. |
| D14 | **Coordonnées manquantes** : on retire le canal concerné et on garde les autres. Sans aucune coordonnée, statut « destinataire non joignable » sur l'écran 41. Pas de basculement automatique côté Engine (statut FALLBACK). |
| D15 | **Consentement** : l'autre prestataire utilise le champ existant `AutrePrestataire.notification`. L'avocat reçoit un nouveau champ équivalent, **coché par défaut**. |
| D16 | **Conservation** : pas de purge dans ce chantier. On conserve canal, statut, dates et destinataire, pas le texte intégral des SMS et WhatsApp. |
| D17 | **Supervision** : sur l'écran 41, un tableau par canal (envoyés, en attente, échecs), la liste des échecs et un bouton « Relancer ». |
| D18 | **Niveau de détail des messages** : complet sur Teams et Outlook, modéré par courriel personnel, minimal par SMS et WhatsApp. **Jamais** de nom de client ou de débiteur, de montant, de motif détaillé, de code interne ni de marqueur. |
| D19 | **Événements prestataires**. Les 5 actuels : constitution validée, frais rejetés, paiement effectué, publication arbitrée, correspondance. Les 4 nouveaux : rappel d'audience J-7/J-3/J-1 à l'avocat affecté, affectation à un dossier, délai de recours du délibéré à l'avocat, suppression de document arbitrée au prestataire demandeur. |

---

## 4. Architecture cible

```
Action métier ──(même transaction)──► AlerteService.declencher ──► alerte (APPLICATIF) + STOMP
      │                                     │
      │                                     └──► notification_envoi (1 ligne par destinataire)
      └─► point d'entrée prestataires ──────────► notification_envoi
                                                     │
                     Dispatcher @Scheduled ◄─────────┘ (EN_ATTENTE, tentatives, prochaine tentative)
                            │  routage (grille type × canal) + contenu par canal + redirection DEV
                            ▼
              PasserelleNotificationEngine ── Journal (défaut) | Dapr | Kafka direct
                            │  JWT HS256, eventId déterministe, channelRecipients
                            ▼
                 notification-send-standard ──► Notification Engine
                            ▲
   Consommateur de suivi ◄── notification-sent / notification-failed (filtre serviceId, idempotent)
                            │
                            └──► statut dans notification_envoi ──► écran 41
```

Le code se trouve dans le paquetage `com.afriland.audiences.notification` :
- `config` : paramètres et choix de la passerelle ;
- `passerelle` : transport ;
- la suite (domaine, dépôt, service, envoi, suivi) est ajoutée au fil des étapes.

---

## 5. Étapes

Statuts possibles : ⬜ à faire · 🔄 en cours · ✅ livrée (validée, et commitée par l'utilisateur).

### ✅ Étape 1 : socle, interrupteur et adaptateur « journal »

Commit `980b0b2` (backend).
- Livré :
  - `CanalEngine`, `DemandeNotification`, `PasserelleNotificationEngine`, `PasserelleJournal`
    (coordonnées masquées dans le journal) ;
  - `NotificationEngineProperties` (préfixe `notification.engine`) ;
  - `NotificationEngineConfig`.
- Si l'interrupteur est activé, le démarrage échoue dans trois cas : code absent, secret de moins de
  32 caractères, ou aucun transport réel disponible. Ce dernier garde-fou est à retirer à l'étape 8.
- Tests : 11 unitaires. Suite complète : 477 tests verts.

### ✅ Étape 2 : table `notification_envoi` (file d'attente)

Livrée, en attente du commit de l'utilisateur.
- Migration `V27__notification_envoi.sql` :
  - une ligne par destinataire et par canal ;
  - références libres, sans clé étrangère (`dossier_id`, `alerte_id`, `destinataire_ref`) ;
  - statuts EN_ATTENTE, PUBLIEE, ENVOYEE, ECHEC et NON_JOIGNABLE ;
  - `event_id` unique ;
  - contraintes sur le statut, le canal, la nature du destinataire, et « destinataire présent sauf
    NON_JOIGNABLE » ;
  - index partiel pour le dispatcher, index pour la supervision.
- Entité `NotificationEnvoi`, créée uniquement par les fabriques `aRemettre` et `nonJoignable`,
  avec `Origine`, `StatutEnvoi`, `TypeDestinataire` et `NotificationEnvoiRepository.findByEventId`.
- Tests : 7 unitaires, et 7 d'intégration sur PostgreSQL réel (`@DataJpaTest` avec Testcontainers).
  Suite complète : 491 tests verts.

### ✅ Étape 3 : réglages du DJ et du DJA (écran 41, paramètres système)

Livrée, en attente du commit de l'utilisateur. Décision consignée : **Q-118**.
- **Backend**
  - `CanalPrestataire` (COURRIEL_PERSONNEL, SMS et WHATSAPP, avec leur canal Engine) et
    `EvenementPrestataire` (les 9 événements de D19).
  - `CANAUX_NOTIFICATION_DEFAUT` garde sa valeur et devient le réglage des internes (Q-37 soldée).
  - Six clés nouvelles : `CANAUX_NOTIFICATION_PRESTATAIRES` et `GRILLE_NOTIFICATION_{OUTLOOK, TEAMS,
    COURRIEL_PERSONNEL, SMS, WHATSAPP}`.
  - Liste vide autorisée pour ces seules clés (`videAutorise`, exposé sous `listeVideAutorisee`).
    La requête passe de `@NotBlank` à `@NotNull`, et une clé sans règle refuse toujours le vide.
  - V28 : `valeur` passe à VARCHAR(1000) et reçoit les valeurs initiales. Les grilles cochent tous
    les événements. Les canaux prestataires sont le courriel personnel et le SMS, **WhatsApp éteint**.
- **Frontend**
  - Écran 41 : les colonnes de grille sont retirées de la liste générique et regroupées dans
    `GrilleNotifications`, un tableau par public (événements × canaux), avec la mention
    « Canal coupé » et un enregistrement colonne par colonne.
  - Les cases des clés `CANAUX_*` affichent un libellé traduit plutôt que le code.
- Tests :
  - backend : unitaires sur les règles et le service, et `ConfigurationMigreeIntegrationTest`
    (chaque règle a sa ligne migrée, et sa valeur initiale est déjà normalisée) ;
  - frontend : Vitest sur la logique et le composant, deux parcours Playwright (grille vidée puis
    rétablie, vide refusé sur `FORMATS_GED`).
  - Suites complètes : backend 519 tests, Vitest 349.

### ✅ Étape 4 : routage

Livrée, en attente du commit de l'utilisateur. Backend seulement ; aucun appelant pour l'instant.
- `ReglagesNotification` : photographie des réglages, lue en une requête par
  `ReglagesNotificationService.charger()`, sans cache, pour qu'un réglage s'applique à l'événement
  suivant. Un canal part si son interrupteur est allumé **et** si l'événement est dans sa grille.
  Une clé absente ou une valeur inconnue ne fait rien partir.
- `RoutageNotification` :
  - `pourUtilisateur(TypeAlerte, Utilisateur)` : Outlook et/ou Teams vers l'adresse Keycloak.
    **Liste vide pour le profil AVOCAT** (D2).
  - `pourPrestataire(EvenementPrestataire, CoordonneesPrestataire)` : courriel personnel, SMS et
    WhatsApp. **Liste vide sans consentement** (D15).
- `Route` : canal réglé, canal Engine, destinataire mis en forme, ou motif COORDONNEE_ABSENTE ou
  COORDONNEE_INVALIDE, pour une ligne NON_JOIGNABLE (D14).
- `Coordonnees` : téléphone accepté seulement sous les trois formes du Guide. Un numéro local
  `6xxxxxxxx` est complété en `2376xxxxxxxx`. Rien d'autre n'est deviné. Le courriel reçoit un
  contrôle de forme minimal.
- Tests : 26 sur les coordonnées, 13 sur le routage, 2 d'intégration sur PostgreSQL (réglages de
  V28). Suite complète : 560 tests verts.

### ✅ Étape 5 : contenus par canal

Livrée, en attente du commit de l'utilisateur. Backend seulement ; aucun appelant pour l'instant.
- Gabarits versionnés : `src/main/resources/notifications/textes_fr.properties`. Chaque événement
  a un titre et une phrase générique (17 types d'alerte, 9 événements prestataires), plus des
  formules communes.
- `RedacteurNotification` : `pourUtilisateur` et `pourPrestataire` prennent un `Contexte`, limité à
  la référence du dossier et au lien.
  - Niveau **complet** (Outlook et Teams) : phrase, dossier, lien et pied de message.
  - Niveau **modéré** (courriel personnel) : salutation, phrase et dossier, puis le lien de l'espace
    avocat, ou à défaut une invitation à contacter la Direction Juridique.
  - Niveau **minimal** (SMS et WhatsApp) : titre seul, 160 caractères au plus, sans référence ni lien.
  - Les courriels partent en HTML échappé, Teams, SMS et WhatsApp en texte brut. L'objet est
    `[Audiences et délibérés] <titre> - <référence>`.
- `LiensApplication` : `NOTIFICATION_LIEN_APPLICATION` (par défaut `http://localhost:3000`),
  `/dossiers/{id}`, `/notifications` (alerte hors dossier), `/portail`.
- Tests : 19. Toutes les combinaisons événement × canal sont passées aux garde-fous : ni marqueur,
  ni code de règle, ni montant, ni chiffre hors référence et lien. On vérifie aussi que chaque
  événement a son texte, qu'aucun texte n'est orphelin et que le HTML est échappé.
  Suite complète : 579 tests verts.

### ✅ Étape 6 : branchement dans `AlerteService.declencher`

Livrée, en attente du commit de l'utilisateur.
- `AlerteService.declencher` publie `AlerteDeclenchee` (alerteId, type, dossierId, destinataire),
  **sans le texte `declencheur`**. Le module alerte ne connaît pas le module notification.
- `NotificationAlerteListener` (`@EventListener`, synchrone, même transaction) chaîne le routage,
  la rédaction puis `saveAll`, avec une ligne par canal : `event_id` = `alerte-{id}:{CANAL}`, ou
  NON_JOIGNABLE avec son motif dans `derniere_erreur`. Si la préparation échoue, l'erreur est
  journalisée et l'alerte applicative est conservée.
- `ReferencesDossier`, une interface du module notification, est implémentée par
  `dossier.service.ReferencesDossierJpa` avec `DossierRepository.findReferenceById`.
- `NotificationEnvoi.nonJoignable(origine, canal, motif)`.
- Tests :
  - 4 unitaires sur l'écouteur ;
  - 4 d'intégration sur PostgreSQL : Outlook mis en file avec la référence et sans le texte de
    l'alerte, rien pour un avocat, rien si Outlook est coupé, l'annulation emporte la
    notification ;
  - vérification de l'événement dans `AlerteServiceTest`.
  - Suite complète : 587 tests verts.
- **Contre la vraie pile** : suite Playwright complète (167 sur 169, les 2 échecs sont l'aléa connu
  du sélecteur d'avocats, verts en relance). 60 alertes internes ont donné 60 lignes EMAIL
  EN_ATTENTE, 6 alertes d'avocats aucune, et aucun texte ne contenait de marqueur, de code ni de
  montant. Les 60 lignes de test ont été supprimées de la base locale.

### ✅ Étape 7 : point d'entrée prestataires

Livrée, en attente du commit de l'utilisateur. Décision consignée : **Q-119**.
- `NotificationPrestataireService.notifier(evenement, prestataire, dossierId, referenceEvenement)` :
  routage, rédaction, `event_id` = `<référence>-p<idPrestataire>:<CANAL>`, lien du portail seulement
  pour un avocat qui a un compte, et un événement déjà en file est ignoré (`existsByEventId`).
- **V29** : `avocat.notification`, coché par défaut. `Intervenant.accepteNotificationsExternes()` est
  abstraite et implémentée par les deux types. `POST` et `PUT /intervenants` acceptent le champ pour
  un avocat ; le refus de Q-117 tombe.
- Les 9 événements branchés, avec leur référence :
  | Événement | Appelant | Référence |
  |---|---|---|
  | CONSTITUTION_VALIDEE | `ConstitutionService` (avocat **et** autre prestataire) | `constitution-{id}` |
  | FRAIS_REJETES | `FraisService.rejeter` | `frais-{id}-rejet` |
  | PAIEMENT_EFFECTUE | `FraisService` (paiement) | `frais-{id}-paiement` |
  | PUBLICATION_ARBITREE | `PublicationService.arbitrer` (validation **et** rejet) | `publication-{id}-arbitrage` |
  | CORRESPONDANCE_TRANSMISE | `PublicationService` | `correspondance-{id}` |
  | RAPPEL_AUDIENCE | `RappelAudienceScheduler`, avocats affectés | `audience-{id}-J{n}` |
  | AFFECTATION_DOSSIER | `DossierService.affecterAvocats` | `affectation-{dossierId}` |
  | DELAI_RECOURS | `DelaiRecoursScheduler`, avocats affectés | `delibere-{id}-recours` |
  | SUPPRESSION_DOCUMENT_ARBITREE | `GedService`, si le demandeur est un avocat | `suppression-{id}-arbitrage` |
- **Frontend** : la case de consentement s'affiche pour les deux types, avec son texte d'aide, et
  n'est plus réservée à l'autre prestataire ; libellés FR et EN revus (elle ne parle plus du seul
  courriel).
- Tests : 7 unitaires du point d'entrée, mise à jour des tests des 7 services appelants et des
  règles de Q-117. Backend 594 verts, Vitest 349 verts.
- **Contre la vraie pile** : suite Playwright 168 sur 169 (l'échec est un aléa d'affichage de la
  liste des clients, vert en relance : 11 sur 11). Les avocats des parcours n'ont pas de
  coordonnées, d'où des lignes NON_JOIGNABLE ; en donnant courriel et téléphone à un avocat de test,
  l'affectation a produit un courriel détaillé avec le lien du portail et un SMS minimal. L'état
  initial de la fiche a été remis, et la file de test vidée.

### ✅ Étape 8a : dispatcher, tentatives, redirection et message (aucun envoi)

Livrée, en attente du commit de l'utilisateur. L'étape 8 est coupée en deux : **8a ne fait partir
aucun message**, tout est prêt pour que 8b ne soit plus qu'un branchement de transport.
- `DispatcherNotification` (`@Scheduled`, 60 s par défaut) lit un lot de 50 identifiants ;
  `RemiseNotification` traite chaque ligne **dans sa propre transaction** — classe distincte, sinon
  l'appel interne ne passerait pas par le proxy et la transaction ne s'ouvrirait pas.
- **Péremption** : au-delà de `notification.engine.age-max` (2 h par défaut), la ligne passe en ÉCHEC
  avec le motif `PERIMEE`, sans partir. C'est la réponse au stock accumulé depuis l'étape 6.
- **Tentatives (D13)** : 1 min, 5 min, 15 min, 1 h, puis abandon à la 5ᵉ, avec la cause dans
  `derniere_erreur`.
- **`MessageEngine`** : contrat du Guide (`token`, `channels`, `channelRecipients`, `directSubject`,
  `directBody`, `eventId`, `category=TRANSACTIONAL`, `locale=fr_FR`). Toujours `channelRecipients`,
  jamais `recipient`.
- **`JetonEngine`** : HS256, `sub` = code du service, 5 minutes, secret pris **tel quel** (octets
  UTF-8). Signé seulement si le moteur est actif.
- **`RedirectionDestinataires`** (D10) : cible par canal et par public ; le destinataire réel est
  conservé dans `destinataire_origine`. Elle ne s'applique que moteur allumé, et une cible manquante
  fait échouer la remise au lieu de laisser partir le message.
- Au démarrage, moteur actif et redirection incomplète : le contexte refuse de démarrer.
- Tests : 11 sur la remise, 5 sur la passerelle et le message, 8 sur la configuration.
  Suite complète : 607 tests verts.
- **Contre la vraie pile** : une ligne récente est passée PUBLIEE avec le transport `JOURNAL`
  (destinataire masqué dans le journal, rien envoyé) ; une ligne vieille de 5 h est passée en ÉCHEC
  avec le motif `PERIMEE`. Lignes de recette supprimées ensuite.

### 🔄 Étape 8b : transport réel — code livré, **premier envoi non fait**

Le code est prêt et testé ; l'interrupteur reste à `false`. **Le premier envoi réel demande l'accord
explicite du porteur du projet** : c'est lui qui fera partir un vrai SMS et un vrai courriel.
- `PasserelleDapr` : `POST {daprUrl}/v1.0/publish/{pubsub}/{topic}`, sans bibliothèque Dapr — le
  sidecar expose une API HTTP locale. Voie documentée par le Guide, donc enveloppe CloudEvents
  conforme.
- `PasserelleKafka` : dépôt direct sur le topic, clé = `eventId`, **envoi attendu** (sans cela un
  courtier injoignable passerait pour un succès). Repli, choisi explicitement.
- `notification.engine.transport` : `type` (DAPR par défaut, KAFKA, JOURNAL), `topic`, `pubsub`,
  `dapr-url`, `attente-secondes`. Nouvelle dépendance : `spring-kafka`.
- Le garde-fou « aucun transport réel » de l'étape 1 est retiré ; à sa place, le démarrage échoue si
  le transport KAFKA est demandé sans courtier.
- `docker/dapr/notification-pubsub-standard.yaml` (écrit sur le modèle du composant CRITICAL du
  Guide) et sidecar `daprd` dans `docker-compose.yml`, **sous le profil `notifications`** : la pile
  habituelle démarre sans lui.
- Le numéro de redirection passe par la même mise en forme que celui d'une fiche.
- `.env` complété : interrupteur (à `false`), code de service, secret, clé d'API et les trois
  destinataires de test. Le bloc commenté de l'administrateur reste la référence.
- Tests : 4 sur les transports, 10 sur la configuration. Suite complète : 613 tests verts.

**Le sidecar Dapr n'est pas installable sur les postes.** `docker pull daprio/daprd` échoue : le
réseau de la banque bloque le CDN du registre d'images (`production.cloudfront.docker.com`). La
résolution DNS fonctionne pour `docker.io`, mais pas pour le CDN qui sert les couches.

**Conséquence, appliquée** : `PasserelleKafka` reproduit elle-même l'enveloppe CloudEvents que le
sidecar aurait posée (`EnveloppeCloudEvents` : `specversion` 1.0, `type` `com.dapr.event.sent`,
`topic`, `pubsubname`, `source`, et le message dans `data`). Le moteur, qui consomme par Dapr, reçoit
donc ce qu'il attend. L'envoi brut reste possible (`transport.cloud-events=false`) pour départager les
deux hypothèses si le moteur restait muet.

### ✅ Étape 8c : premier envoi réel — **le courriel et le SMS arrivent** (2026-09-23)

Sept messages de contrôle envoyés vers les destinataires de test, avec l'accord du porteur du projet.

**Les trois premiers n'ont rien donné** : publiés, sans erreur, sans accusé, sans rien dans la boîte.
L'administrateur a fourni le JSON d'un message **réellement accepté**, qui a révélé sept écarts avec
le Guide — voir **Q-121**. Le principal : le topic STANDARD n'est lu par personne, tout passe par
`notification-send-request`. **La décision D8 tombe.**

**Après correction** : courriel reçu (expéditeur `digital-notification@afrilandfirstbank.com`) et SMS
reçu au numéro de test. **Le secret s'utilise tel quel**, la question ouverte depuis le début est
tranchée. `FormatMessageTest` fige désormais le format, champ par champ.

**Le suivi de délivrance n'existe pas ici** : les deux topics d'accusés, relus depuis leur origine,
sont vides pour tous les services. La veille SANS_RETOUR est donc neutralisée par défaut
(`notification.engine.suivi-accuses=false`) et la supervision l'explique — sans quoi tout serait
signalé en défaut alors que les messages arrivent.

**Restent à confirmer** : Outlook et Teams (envoyés vers l'adresse professionnelle) et **WhatsApp, qui
n'a rien délivré** là où le SMS est passé, sur le même numéro.

**Note d'exécution** : la bascule de l'interrupteur dans le `.env` est refusée à l'assistant par le
garde-fou du harnais (elle déclenche des envois réels) tant que le porteur du projet ne l'autorise pas
explicitement dans le fil de la conversation.

### ✅ Étape 9 : suivi de la délivrance

Livrée, en attente du commit de l'utilisateur. Décision consignée : **Q-120**.

**Contradiction relevée dans le Guide** : les topics de suivi ne renvoient **pas** notre `eventId`,
seulement le `notificationId` interne au moteur. Ils disent combien de messages partent, jamais
lequel des nôtres. Trois couches répondent à cela :
1. **`callbackUrl`** porte notre référence (`/notifications/accuses/rappel/{eventId}?signature=…`) :
   rattachement exact, ligne marquée ENVOYEE ou ECHEC. L'URL est signée en HMAC-SHA256 avec le secret
   du compte de service, car le Guide ne décrit aucune authentification du rappel ; signature
   invalide → 404. Désactivé tant que `NOTIFICATION_CALLBACK_BASE_URL` est vide (un poste de travail
   n'est pas joignable depuis le cluster).
2. **`metadata.correlationId`** porte aussi la référence : si le moteur la restitue un jour, le
   rattachement se fera sans rien changer.
3. **Les topics** alimentent `notification_accuse` (V30), filtrés sur `serviceId` et idempotents par
   (notificationId, type) — la livraison est « au moins une fois ». Ils donnent la mesure par canal
   et par fournisseur.
- **Nouveau statut SANS_RETOUR** : une notification publiée depuis plus de
  `notification.engine.delai-accuse` (15 min) sans accusé n'est plus comptée comme partie.
  `VeilleAccusesNotification` s'en charge. C'est ce qui rendra visible un secret mal encodé.
- Deux voies de réception : `AccusesDaprController` (abonnements annoncés sur `/dapr/subscribe`) et
  `AccusesKafkaListener` (monté seulement en transport KAFKA). Routes ouvertes sans authentification
  dans `SecurityConfig` : le sidecar n'a pas de jeton, et elles n'exposent aucune donnée.
- Tests : 15 unitaires. Suite complète : 626 tests verts.
- **Contre la vraie pile** : abonnements annoncés, accusé d'un autre service ignoré, le nôtre
  enregistré, doublon sans effet, rappel sans signature refusé en 404. Lignes de recette supprimées.

### ✅ Étape 10 : supervision sur l'écran 41 (D17)

Livrée, en attente du commit de l'utilisateur.
- **Backend** : `GET /notifications/supervision?dateDebut&dateFin` et
  `POST /notifications/{id}/relancer`, réservés au DJ et à la DJA comme la supervision du canal
  applicatif (Q-109). Comptes en une requête groupée par canal et par statut, plus les 50 derniers
  envois à examiner (échec, sans retour, non joignable).
- **Six colonnes plutôt qu'un taux unique** : en file, remises, confirmées, échecs, sans retour, non
  joignables. « Remise » n'est pas « reçue » (Q-120), et les réunir donnerait un chiffre rassurant
  et faux. Le taux d'aboutissement **exclut les non joignables** : ils parlent d'une fiche à
  compléter, pas d'un canal en panne. Un canal sans envoi ne rend aucun taux.
- **Relance manuelle** : remet en file un envoi abandonné, compteur de tentatives remis à zéro (c'est
  une décision humaine). Refusée sur ce qui est en attente ou déjà confirmé, et sur un destinataire
  sans coordonnée — il faut corriger la fiche.
- **Frontend** : `SupervisionNotifications` sous la supervision des alertes, avec la mention
  « le moteur est désactivé » tant que l'interrupteur est coupé, et le destinataire réel affiché
  sous l'adresse de test quand la redirection a joué. Libellés FR et EN.
- Tests : 8 backend, 10 Vitest, 1 parcours Playwright. Suites complètes : 634 backend, 359 Vitest,
  Playwright complet relancé sur les fichiers touchés (19 verts).
- **Contre la vraie pile** : lecture DJ à 200 avec les comptes attendus, juriste refusé en 403,
  relance d'un échec à 200, relance sans destinataire refusée en 400. Lignes de recette supprimées.
- Piège rencontré : Playwright compare les noms accessibles **par fragment**. « Notifications
  externes » correspondait aussi à « Grille des notifications externes » ; les assertions de titre
  de cet écran utilisent donc `exact: true`.

### ✅ Étape 11 : validation des coordonnées dans le référentiel des intervenants

Livrée, en attente du commit de l'utilisateur.
- **Backend** : `IntervenantService` contrôle et **met en forme** les coordonnées à la création comme
  à la modification, pour les deux types d'intervenant. Le courriel exige un domaine avec point, là
  où `@Email` acceptait « a@b ». Nouvelle fabrique `ApiException.badRequestSurChamp` : l'erreur se
  pose sur le champ fautif.
- **Règle du téléphone, arrêtée avec le porteur du projet** :
  - **Cameroun** : exactement **neuf chiffres commençant par 6**, que l'indicatif soit écrit
    (`+237`, `237`) ou non. Tous les mobiles du pays commencent par 6, **Camtel compris**
    (`620 XX XX XX`). Les lignes fixes, en 2, sont écartées : elles ne reçoivent ni SMS ni WhatsApp.
  - **Hors Cameroun** : les prestataires étrangers existent et le moteur délivre au-delà du pays ;
    l'**indicatif devient obligatoire**, car il n'est pas devinable. Forme internationale contrôlée
    (indicatif puis huit à quinze chiffres), sans vérifier la longueur propre à chaque pays.
  - **Une seule écriture est enregistrée** : le format international, `+237650228936`. Deux formes
    pour la même coordonnée seraient incomparables entre elles.
  - **Pas de bibliothèque de validation** (type libphonenumber) : elle ferait mieux sur les numéros
    étrangers, mais ajoute une dépendance à maintenir pour un besoin encore marginal. À reconsidérer
    si la supervision montre des échecs sur ces numéros — une raison chiffrée plutôt qu'une intuition.
  - **Migration V31** : les numéros déjà saisis passent au format international. Ce qui ne relève
    d'aucune forme connue — un fixe, par exemple — est **laissé tel quel**, visible et corrigible,
    jamais réécrit au jugé. Vérifié sur la base réelle : local et étranger convertis, fixe intact.
- Les coordonnées **restent facultatives** : tout prestataire n'a pas de numéro.
- **Frontend** : même règle à la saisie (`telephoneValide`), format attendu annoncé sous le champ,
  messages FR et EN.
- **Pourquoi à la saisie** : une faute ne se voyait qu'à l'envoi, sous la forme d'une ligne
  « non joignable » en supervision — trop tard, et loin de qui pouvait corriger la fiche.
- Conséquence sur l'audit : le numéro historisé est désormais celui, mis en forme, qui sera utilisé.
- Tests : 5 backend (dont mise en forme et refus), 3 Vitest, 1 parcours Playwright.
  Suites complètes : backend 646, Vitest 363, référentiels Playwright 12 sur 12.

### ✅ Étape 12 : pièce jointe (lettre de constitution)

Livrée, en attente du commit de l'utilisateur.

**Sondé avant d'être écrit.** Les topics de suivi étant muets (Q-121), rien ne disait que celui des
pièces jointes répondrait. Une sonde manuelle (`SondeAttachmentUpload`, désactivée sauf variable
d'environnement) a tranché en quelques minutes : demande acceptée, réponse `READY`, dépôt du fichier
en HTTP 200. **Le moteur publie donc bien sur certains topics** — le silence du suivi est une affaire
de configuration, pas une panne générale.

- **Le fichier ne passe jamais par Kafka** : demande d'adresse sur `attachment-upload-request`,
  réponse sur `attachment-upload-response`, dépôt direct par l'URL à usage unique, puis la
  notification ne cite que la clé de stockage.
- **Courriel uniquement** (D5), ce que le Guide confirme : WhatsApp et Teams perdent la pièce jointe
  en silence, le SMS ne la supporte pas. `NotificationEnvoi.joindre` refuse les autres canaux.
- **V32** : colonnes de pièce jointe et statut `ATTENTE_PIECE_JOINTE`, le temps que la clé arrive.
- **Une pièce jointe ne retient jamais la notification** : dépôt refusé, échec du transfert, ou
  absence de réponse au bout de `notification.engine.delai-depot` (5 min) — elle part sans son
  document, la cause enregistrée. Prévenir sans la pièce vaut mieux que ne pas prévenir.
- Réception sur les deux voies : route Dapr `/notifications/accuses/depots` et consommateur Kafka.
- `ConstitutionService` joint la lettre archivée en GED à l'événement CONSTITUTION_VALIDEE.
- Tests : 5 sur le dépôt (serveur HTTP local), 5 sur les transports. Suite complète : 672 verts.
- **Contre la vraie pile** : une lettre de constitution réelle a été déposée puis citée dans la
  notification (`pending/controle-pj-1-EMAIL/lettre-constitution-25.pdf`), avec les traces attendues
  dans le journal. Interrupteur remis à `false`, file vidée.

### ⬜ Étape 13 : de bout en bout

- Tests Playwright pour la supervision et la configuration.
- Recette réelle en DEV sur les trois destinataires de test, un par canal.

---

## 6. Variables activées dans le `.env`

*(Uniquement les noms et la date d'activation, jamais les valeurs.)*

| Variable | Activée le | Étape |
|---|---|---|
| — | — | — |

---

## 7. Points ouverts

### À poser à l'administrateur (priorité haute, issues du premier envoi réel)

1. **WhatsApp ne délivre pas** là où le SMS passe, sur le même numéro. Modèle de message préapprouvé
   exigé ? Fenêtre de conversation à ouvrir ? Canal non actif pour notre compte ?
2. **Les topics `notification-sent` et `notification-failed` sont vides**, tous services confondus.
   Sont-ils alimentés quelque part ? Sinon, comment suivre la délivrance ?
3. **Le rappel `callbackUrl`** est-il réellement appelé, et avec quel corps ? C'est notre seul moyen
   de rattacher un accusé à un envoi (Q-120).
4. **Le Guide V1 est à corriger** : topic, composant pubsub, `serviceKey`, `priority`, `recipient`,
   absence de `locale` et `metadata` (Q-121).

- **Secret JWT** : est-il utilisé tel quel ou après décodage base64 ? À trancher à l'étape 8 par un
  envoi réel.
- **Enveloppe du message** : l'Engine accepte-t-il un message Kafka publié directement, sans
  l'enveloppe standard ajoutée par Dapr ? Cela conditionne le repli Kafka.
- ~~**Pièces jointes** : quel bucket et quelle instance MinIO ?~~ Résolu par la sonde : le moteur rend
  lui-même une adresse de dépôt à usage unique, sur son propre stockage
  (`172.21.88.31:31784/notification-attachments/...`), joignable depuis les postes. Rien à configurer.
- **WhatsApp** : faut-il des modèles de message préapprouvés, ce qui est la règle habituelle de
  WhatsApp Business ? Le canal reste éteint par défaut (V28) jusqu'à la réponse de l'administrateur.
- **Accusés et référence (Q-120)** : le moteur peut-il ajouter l'`eventId`, ou restituer
  `metadata.correlationId`, dans `notification-sent` et `notification-failed` ? Cela rendrait les
  topics aussi précis que le rappel, sans dépendre d'une application joignable depuis le cluster.
- **Rappel `callbackUrl`** : le Guide n'en décrit ni le corps exact ni l'authentification. Nous
  signons l'URL ; à confirmer avec l'administrateur que le moteur la rappelle telle quelle, avec ses
  paramètres.
- **Référence du dossier** : elle est saisie librement. Peut-elle contenir un nom de client ? Si oui,
  il faut la retirer des courriels personnels (elle n'est déjà jamais dans les SMS et WhatsApp).
- **Limites non documentées dans le Guide** : longueur maximale d'un SMS, gabarits WhatsApp requis ou
  non, format des messages Teams, quotas.
- **Environnements non documentés** : les valeurs de PROD sont absentes du Guide, ce qui est sans
  conséquence puisque la production est hors périmètre (D6).
- **Spécifications** : les spécifications décrivent Teams via un webhook d'équipe et Outlook via
  SMTP ; ce chantier passe par l'Engine. Il faudra consigner cet écart dans
  `DECISIONS_AND_OPEN_QUESTIONS.md` à l'étape 3.

---

## 8. Journal des écarts et enseignements

- 2026-09-22 : étape 1 livrée. Le secret est masqué dans la représentation texte des paramètres, et
  les coordonnées sont masquées dans les journaux.
- Étape 2, choix d'implémentation (à respecter dans les étapes suivantes) :
  - **`event_id` = référence métier + « : » + canal** (par exemple `alerte-42:SMS`). Sans le canal,
    l'Engine dédoublonnerait les canaux d'un même événement entre eux. La référence métier sera
    fixée à l'étape 6 (alertes) et à l'étape 7 (prestataires).
  - **D16 appliquée** : le texte est conservé tant que l'envoi n'est pas confirmé, car il faut
    pouvoir retenter. Il est effacé pour SMS et WhatsApp dès l'accusé positif (`marquerEnvoyee`).
  - **D14 précisée** : chaque canal routé sans coordonnée donne une ligne NON_JOIGNABLE. Rien ne
    part sur ce canal, mais l'écart reste visible canal par canal sur l'écran 41.
  - **`type_evenement`** est une chaîne libre (VARCHAR(60)). L'énumération des événements, avec la
    contrainte CHECK correspondante, arrivera quand la liste sera figée (étapes 4 à 7).
  - Les transitions PUBLIEE, ECHEC et nouvelle tentative sont reportées aux étapes 8 et 9.
    Seule `marquerEnvoyee` existe déjà, car elle porte la règle de conservation D16.
- Piège de test : dans une transaction PostgreSQL, un refus de contrainte fait échouer toutes les
  requêtes suivantes. D'où un test par contrainte.
- Étape 3, choix d'implémentation (à respecter dans les étapes suivantes) :
  - **Routage des internes (étape 4)** : un canal part si `CANAUX_NOTIFICATION_DEFAUT` le contient
    **et** si le type d'alerte figure dans `GRILLE_NOTIFICATION_<CANAL>`. Même principe pour les
    prestataires, avec `CANAUX_NOTIFICATION_PRESTATAIRES` et les grilles des prestataires.
  - **Un avocat qui a un compte** reçoit ses alertes applicatives comme aujourd'hui. Ses canaux
    externes suivent la grille des prestataires (D2), jamais Outlook ou Teams. Le branchement de
    l'étape 6 devra donc écarter les utilisateurs de profil AVOCAT, que l'étape 7 prend en charge.
  - Les grilles internes reprennent les 17 types d'alerte, y compris les 3 jamais déclenchés
    (DOUBLON_FACTURE, DELIBERE_ATTENDU, DOSSIER_INCOMPLET). C'est sans effet tant qu'ils ne se
    déclenchent pas, et cela évite une liste parallèle à maintenir.
  - **Piège** : les tests `@SpringBootTest` sans Testcontainers (`AudiencesApplicationTests`)
    démarrent sur la base locale du `.env`. `./mvnw install` applique donc les nouvelles migrations
    à la base de développement. Dès l'étape 3, V28 y était déjà appliquée avant tout démarrage
    manuel.
- Point ouvert ajouté : WhatsApp exige-t-il des modèles préapprouvés côté Engine ? Le canal reste
  éteint par défaut tant que la réponse n'est pas connue.
- Étape 4, choix d'implémentation (à respecter dans les étapes suivantes) :
  - **Sans consentement, aucune ligne**, pas même NON_JOIGNABLE : le prestataire a refusé, ce n'est
    pas un défaut à superviser. À l'inverse, une coordonnée absente ou invalide donne une ligne
    NON_JOIGNABLE avec son motif. À l'étape 6, ce motif ira dans `derniere_erreur`.
  - **`AutrePrestataire.notification` vaut `false` par défaut** depuis V1. Un autre prestataire
    n'est donc notifié hors de l'application que si sa fiche le demande, ce qui est conforme à D15.
    L'avocat recevra à l'étape 7 un champ coché par défaut.
  - **Aucune collision d'`event_id` entre Outlook et le courriel personnel**, bien que les deux
    donnent le canal Engine EMAIL : un même destinataire est soit interne, soit prestataire.
  - Un utilisateur désactivé n'est pas écarté par le routage : aucune règle ne le dit, et ce sont
    les appelants de `declencher` qui choisissent les destinataires.
- Étape 5, choix d'implémentation (à respecter dans les étapes suivantes) :
  - **Le texte `declencheur` de l'alerte ne sort jamais.** Il est écrit pour l'écran et contient,
    selon les cas, un motif de rejet (frais), l'objet libre d'une alarme, le nom d'un prestataire
    (constitution), des marqueurs `[xxxId:n]` et des codes « (RG-…) ».
  - **Phrases génériques par type** : un même TypeAlerte couvre plusieurs situations
    (SUPPRESSION_DOCUMENT est tour à tour demande, arbitrage et relance ; CONSTITUTION_A_SIGNER est
    demande, validation et rejet ; NOUVELLE_PUBLICATION couvre quatre cas). Le détail se lit dans
    l'application.
  - **Référence du dossier** : elle est saisie librement à la création (`Dossier.reference`), sans
    format imposé. Elle figure dans les messages complets et modérés, jamais dans les minimaux. Si
    la pratique de la banque était d'y mettre un nom de client, il faudrait la retirer aussi des
    courriels personnels (point ouvert).
  - L'étape 6 devra fournir la référence : `AlerteService` ne connaît que `dossierId`, et le module
    alerte ne doit pas dépendre du module dossier. Il faudra une interface dans `notification`,
    implémentée par le module `dossier`.
  - Les gabarits se remplacent par un simple `replace("{0}", …)` et non par `MessageFormat`, qui
    traite l'apostrophe comme un caractère d'échappement.
- Étape 6, choix d'implémentation et pièges (à respecter dans les étapes suivantes) :
  - **Accumulation avant l'étape 8** : dès maintenant, chaque alerte adressée à un interne crée une
    ligne EN_ATTENTE, puisque Outlook est allumé par défaut. Aucun dispatcher ne les consomme
    encore. **À l'étape 8, avant tout envoi réel, il faudra traiter ce stock** : soit le laisser
    consommer par la passerelle journal (interrupteur coupé), soit ne jamais envoyer une ligne trop
    ancienne. Sinon, la redirection DEV inonderait les destinataires de test d'alertes passées.
  - **Horodatage** : les colonnes `TIMESTAMP` reçoivent l'heure locale de Java (`LocalDateTime`,
    UTC+1), alors que `now()` de PostgreSQL est en UTC. Toute requête de contrôle doit comparer à
    une heure locale explicite, pas à `now() - interval`.
  - **L'aléa Playwright** « Me Claire Avocat » (clic sur une option du sélecteur d'avocats sous la
    charge de la suite complète) préexiste à ce chantier. Il est vert en relance, et hors périmètre.
- Étape 7, choix d'implémentation (à respecter dans les étapes suivantes) :
  - **Deux extensions à confirmer en recette** : la publication **validée** notifie désormais
    l'avocat (l'alerte applicative ne le faisait que pour un rejet), et l'autre prestataire est
    notifié de sa constitution validée, ce que Q-52 avait laissé de côté.
  - **`existsByEventId` avant écriture** : sans lui, une relance planifiée ou une réaffectation
    heurterait la contrainte d'unicité et ferait échouer l'action métier.
  - Le rappel d'audience et le délai de recours ne portent pas de garde « déjà déclenchée » propre
    aux prestataires : la clé de déduplication (audience + échéance, délibéré) suffit.
  - Les tests de sept services construisent ces services à la main : toute nouvelle dépendance
    demande de les mettre à jour.
- Étape 8a, deux défauts que seul l'essai contre la pile réelle a révélés :
  - **la redirection bloquait la file moteur coupé** : elle exigeait des destinataires de test alors
    que rien ne partait. Elle ne s'applique désormais que si le moteur est actif ;
  - **le jeton était signé même moteur coupé**, donc impossible sans secret. Il n'est signé que si un
    message part vraiment.
  Tous deux faisaient rester les lignes en attente avec une erreur, au lieu de vider la file.
- Rappel pour 8b : `destinataire` porte la cible **réellement visée** après redirection, et
  `destinataire_origine` le destinataire réel. La supervision (étape 10) doit montrer les deux.
- Points ouverts ajoutés :
  - la référence du dossier peut-elle contenir un nom de client ?
  - quelle est la limite de longueur d'un SMS côté Engine ? Les textes accentués passent en UCS-2,
    soit 70 caractères par segment : nos SMS de 160 caractères au plus peuvent donc faire 2 ou 3
    segments.
