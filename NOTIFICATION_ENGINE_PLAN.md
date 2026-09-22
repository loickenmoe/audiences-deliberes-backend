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

### ⬜ Étape 3 : clés de configuration du DJ et du DJA, et écran 42

- Nouvelles clés dans `RegleConfiguration` :
  - canaux externes actifs ;
  - grille type × canal.
- Traitement de l'existant :
  - on réconcilie `CANAUX_NOTIFICATION_DEFAUT`, qui n'est lu nulle part aujourd'hui (Q-37) ;
  - on consigne la décision dans `DECISIONS_AND_OPEN_QUESTIONS.md`.
- Dans le frontend, édition de la grille avec ses libellés FR et EN.

### ⬜ Étape 4 : routage

- À partir du type d'alerte et du destinataire (utilisateur interne, avocat ou autre prestataire),
  on calcule les canaux effectifs.
- Le calcul applique la grille, le consentement (D15) et la présence des coordonnées (D14).
- Pour les internes, on ne dispose que de l'adresse Keycloak : pas de téléphone.

### ⬜ Étape 5 : contenus par canal

- Textes versionnés par type d'événement et par canal, en appliquant le niveau de détail de D18.
- Tests qui vérifient l'absence de marqueurs, de codes internes, de noms de client et de montants.

### ⬜ Étape 6 : branchement dans `AlerteService.declencher`

- Une ligne `notification_envoi` par canal externe routé, dans la même transaction que l'action métier.
- Comportement de l'alerte applicative inchangé.

### ⬜ Étape 7 : point d'entrée prestataires

- Les 9 événements de D19.
- Le champ de consentement de l'avocat (D15), dans la migration, l'API et le formulaire.
- L'autre prestataire enfin notifié, par son courriel et son téléphone.

### ⬜ Étape 8 : dispatcher et passerelle réelle

- **Activer dans le `.env`** les variables du § 1, en copiant les valeurs depuis le bloc commenté.
- Dispatcher `@Scheduled` avec les nouvelles tentatives de D13.
- Jeton JWT HS256. **À vérifier par un envoi réel** : le secret est-il utilisé tel quel (les octets
  de la chaîne) ou après décodage base64 ? Le Guide parle de « 32 caractères », ce qui suggère
  « tel quel ».
- Composant Dapr `notification-pubsub-standard` et sidecar dans `docker-compose.yml`. Adaptateur
  Kafka direct en repli.
- Redirection DEV (D10).
- On retire le garde-fou « aucun transport réel » de l'étape 1.

### ⬜ Étape 9 : suivi de la délivrance

- Consommation de `notification-sent` et `notification-failed`, avec un filtre sur `serviceId` et
  un traitement idempotent (même accusé reçu deux fois).
- Correspondance entre les statuts de l'Engine et le statut local.

### ⬜ Étape 10 : supervision sur l'écran 41 (D17)

- Tableau par canal, liste des échecs et relance manuelle.

### ⬜ Étape 11 : validation des coordonnées sur l'écran 42

- Format du téléphone (E.164, `237…` ou `6…`) et du courriel, pour les avocats et les autres
  prestataires.

### ⬜ Étape 12 : pièce jointe (lettre de constitution)

- Dépôt préalable dans MinIO puis envoi de la référence (claim-check), sur EMAIL uniquement.
- La clé de stockage attendue est `pending/<id>/<fichier>`. Le Guide ne précise pas quel bucket
  utiliser : c'est un point à confirmer (§ 7).

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

- **Secret JWT** : est-il utilisé tel quel ou après décodage base64 ? À trancher à l'étape 8 par un
  envoi réel.
- **Enveloppe du message** : l'Engine accepte-t-il un message Kafka publié directement, sans
  l'enveloppe standard ajoutée par Dapr ? Cela conditionne le repli Kafka.
- **Pièces jointes** : quel bucket et quelle instance MinIO le claim-check doit-il utiliser ?
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
