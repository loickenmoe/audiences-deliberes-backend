# Audiences et Deliberes Backend

## 👥 Membres du groupe
- Loïc Luc KENMOE MBEUKEM

## 🚀 Lancement du projet

### Prérequis
- Java 17 ou supérieur
- Maven 3.8 ou supérieur
- Base de données PostgreSQL
- Compte SMTP pour les envois d'emails (optionnel)

### Installation

1. **Cloner le dépôt**
   ```bash
   git clone https://github.com/votre-utilisateur/audiences-deliberes-backend.git
   cd audiences-deliberes-backend
   ```

2. **Configurer l'application**
   - Copier le fichier `env.template` vers `.env`
   - Remplir les variables d'environnement selon votre configuration


3. **Construire et démarrer l'application**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

4. **Accéder à l'API**
   - L'API sera disponible sur `http://localhost:8888`
   - Documentation Swagger: `http://localhost:8888/swagger-ui.html`

### Démarrage rapide avec Docker

```bash
docker-compose up grade-app minio -d
```

---