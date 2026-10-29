# Your Car Your Way — PoC de tchat

Preuve de concept du projet OpenClassrooms 10, option B : une conversation privée entre un client et un agent de support, avec authentification réelle, persistance PostgreSQL, historique et reprise après interruption. Les comptes sont fictifs et l’exécution est locale.

## Contenu du dépôt

| Dossier ou fichier | Rôle |
| --- | --- |
| `backend/src/main/java` | API HTTP, authentification, droits, transactions et diffusion SSE |
| `backend/src/main/resources/db/migration/V1__chat_and_sessions.sql` | Structure SQL versionnée : tables, contraintes et index |
| `backend/src/test` | Tests d’intégration |
| `frontend/src` | Interface Angular responsive |
| `frontend/e2e` | Tests navigateur Playwright |
| `scripts` | Construction locale Linux/macOS et Windows |
| `compose.yaml`, `Dockerfile` | PostgreSQL et application conteneurisée |
| `.github/workflows/verify.yml` | Compilation et vérifications automatisées prévues sur GitHub |

Les fichiers compilés, dépendances installées, caches et rapports générés ne sont pas versionnés. Les rapports d’architecture de la mission sont des livrables distincts du dépôt de la PoC.

## Outils requis

Pour tout lancer avec Docker : Docker Desktop/Engine démarré et Compose v2.
Pour travailler dans VS Code : JDK **21**, Node.js **24**, npm et Git, avec Docker pour PostgreSQL. Maven **3.9.11** est téléchargé par le wrapper : aucun Maven global n’est requis.

Stack verrouillée : Spring Boot 4.1.2 ; Angular 21.2.25 (CLI/build 21.2.29), TypeScript 5.9.3, RxJS 7.8.2, Playwright 1.62.1 et PostgreSQL 17.11.

```powershell
java -version
javac -version
node --version
npm --version
git --version
docker compose version
docker info
```

Après installation d’un outil, rouvrir VS Code. Ouvrir le dossier racine du projet, celui qui contient compose.yaml, backend et frontend. Les extensions Extension Pack for Java, Spring Boot Extension Pack et Angular Language Service sont utiles ; sélectionner le JDK 21 avec Java: Configure Java Runtime.

## Choisir un mode de démarrage

| Mode | Base | Backend et interface | Adresse |
| --- | --- | --- | --- |
| Docker complet | Conteneur db | Conteneur app | http://127.0.0.1:8081 |
| JAR local | Conteneur db | Java sert l’interface compilée | http://127.0.0.1:8081 |
| Développement séparé | Conteneur db | Maven sur 8081 et Angular sur 4200 | http://127.0.0.1:4200 |

Ne pas lancer le conteneur app et le JAR local simultanément sur 8081. Utiliser des terminaux distincts pour le serveur et les tests. Garder les terminaux des serveurs ouverts.

### Docker complet

Depuis la racine :

```powershell
docker compose up --build -d
docker compose logs -f app
```

Après Started ChatApplication, ouvrir http://127.0.0.1:8081. Les ports Windows sont 8081 pour l’application et 5433 pour PostgreSQL. Les ports internes restent 8080 et 5432. Pour quitter l’affichage des journaux, Ctrl+C ne coupe pas les conteneurs lancés avec -d.

### JAR local dans VS Code / PowerShell

1. Démarrer seulement la base :

```powershell
docker compose up -d db
docker compose exec db pg_isready -U ycyw -d ycyw
```

2. Arrêter tout Angular et ancien JAR avec Ctrl+C avant de reconstruire. Depuis la racine :

```powershell
.\scripts\build-local.ps1
```

Le script installe les dépendances, compile Angular, copie l’interface dans les ressources Java puis construit le JAR. Il restaure le dossier d’appel, même si une étape échoue. Si Windows bloque le script téléchargé, le consulter puis utiliser `Unblock-File -Path .\scripts\build-local.ps1` si approuvé.

3. Uniquement après BUILD SUCCESS, dans le terminal Application :

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:SERVER_PORT = '8081'
$env:DB_URL = 'jdbc:postgresql://localhost:5433/ycyw'
$env:DB_USER = 'ycyw'
$env:DB_PASSWORD = 'local-demo-only'
java -jar .\backend\target\chat-poc-1.0.0.jar
```

4. Laisser Java ouvert. Depuis un autre terminal :

```powershell
Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/health'
```

Attendre status UP puis ouvrir http://127.0.0.1:8081. Le JAR inclut Angular : aucun npm start n’est nécessaire dans ce mode.

### Développement séparé

Base démarrée, JAR/conteneur app arrêté : ouvrir un terminal à la racine, définir les cinq variables ci-dessus puis :

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Dans un second terminal à la racine :

```powershell
cd frontend
npm ci --no-audit --no-fund
npm start
```

Ouvrir http://127.0.0.1:4200. Le proxy frontend transmet /api au backend 127.0.0.1:8081. Ne pas lancer npm ci pendant qu’un serveur Angular utilise les mêmes dépendances.

### Linux / macOS

Depuis la racine, avec JDK21 et Node24 :

```sh
docker compose up -d db
sh scripts/build-local.sh
```

Si le build réussit :

```sh
export SPRING_PROFILES_ACTIVE=local SERVER_PORT=8081
export DB_URL=jdbc:postgresql://localhost:5433/ycyw
export DB_USER=ycyw DB_PASSWORD=local-demo-only
java -jar backend/target/chat-poc-1.0.0.jar
```

## Configuration locale

Les variables PowerShell sont propres au terminal courant. Spring Boot exécuté hors Docker ne lit pas automatiquement .env. Le fichier .env.example contient uniquement un exemple de configuration locale. Compose peut lire un .env local, exclu de Git.

| Variable | Configuration conseillée hors Docker |
| --- | --- |
| SPRING_PROFILES_ACTIVE | local ; obligatoire pour cette démonstration |
| SERVER_PORT | 8081 ; le défaut du code est 8080 |
| DB_URL | jdbc:postgresql://localhost:5433/ycyw ; défaut du code sur 5432 |
| DB_USER / DB_PASSWORD | ycyw / local-demo-only ; adapter si le volume a un autre mot de passe |
| POC_BASE_URL | http://127.0.0.1:8081 ; pour les tests navigateur |
| SESSION_TIMEOUT | 30m par défaut |
| STREAM_POLL_MS | 1000 par défaut ; lecture serveur du journal SSE |

Dans Docker, garder DB_URL=jdbc:postgresql://db:5432/ycyw. Changer DB_PASSWORD ne modifie pas le mot de passe d’un volume déjà initialisé. Utiliser le même nom d’hôte dans toutes les sessions : localhost et 127.0.0.1 ne partagent pas leurs cookies.

## Comptes et démonstration

| Profil fictif | Adresse | Mot de passe local |
| --- | --- | --- |
| Alice, cliente | alice@example.test | Demo-Alice-2026! |
| Bob, autre client | bob@example.test | Demo-Bob-2026! |
| Support | support@example.test | Demo-Support-2026! |
| Autre agent | other-support@example.test | Demo-Other-2026! |

1. Chrome normal : connecter Alice, créer une conversation, envoyer un texte.
2. Edge normal : connecter le support, actualiser la liste et prendre la demande en charge.
3. Échanger dans les deux sens sans recharger la page.
4. Suspendre la réception côté Alice, envoyer plusieurs réponses côté support puis reprendre : retrouver les messages dans l’ordre.
5. Chrome privé : connecter Bob ; la conversation d’Alice ne doit pas être visible. Le GET /api/v1/conversations/UUID/messages depuis sa session doit renvoyer 404 pour l’UUID d’Alice.
6. Clôturer : l’historique reste consultable, les nouveaux envois sont refusés.
7. Arrêter puis relancer le backend : retrouver les messages déjà enregistrés.

Des onglets ordinaires partagent la même session ; utiliser des navigateurs/profils distincts. Les mots de passe sont publics et fictifs. Aucun profil de production n’est fourni.

## Structure des données et garanties

La migration SQL V1 constitue la structure exécutable de référence. Flyway l’applique sur une base neuve et valide les migrations déjà appliquées. Pour une évolution, ajouter une migration suivante au lieu de modifier V1 sur une base existante.

| Table | Rôle |
| --- | --- |
| account | Identité, rôle CLIENT/SUPPORT, mot de passe haché PBKDF2 et état actif |
| conversation | Propriétaire, agent affecté, statut, version et compteur de séquence |
| message | Auteur, contenu textuel, date, séquence et identifiant client de réessai |
| conversation_event | Journal ordonné des messages et changements d’état |
| spring_session / spring_session_attributes | Sessions serveur JDBC révocables |

Un client possède ses conversations ; l’agent ne lit que celles qui lui sont affectées. Les clés étrangères assurent les relations. L’unicité conversation/auteur/clé de réessai et conversation/séquence empêche les doublons métier. Message et événement sont enregistrés dans une transaction avec verrou de conversation. Le serveur répond RECORDED après commit.

Le navigateur envoie par POST avec session et CSRF. SSE transmet des références persistées ; l’interface récupère les messages autorisés par HTTP, pagine et déduplique. Un envoi incertain conserve la même clé dans la page. Un réessai avec une autre valeur de texte est refusé. Enregistré ne signifie pas Lu ; aucune garantie « exactement une fois » sur le réseau n’est annoncée.

## Tests

Les tests utilisent seulement des données fictives. Ils peuvent ajouter des conversations à la base locale. Ne jamais les exécuter sur des données clients réelles.

### Intégration Java

Arrêter le JAR avant verify, qui reconstruit le fichier potentiellement verrouillé sous Windows. Base db démarrée, depuis la racine :

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5433/ycyw'
$env:DB_USER = 'ycyw'
$env:DB_PASSWORD = 'local-demo-only'
Push-Location backend
try {
    .\mvnw.cmd -B verify
    if ($LASTEXITCODE -ne 0) { throw 'Tests Java en echec' }
}
finally { Pop-Location }
```

Rapports : backend/target/surefire-reports. Sous Linux/macOS, utiliser ./mvnw -B verify avec les mêmes variables DB. Le profil test est déclaré par les tests.

### Navigateur

Relancer le backend et vérifier UP avant les tests. Dans un autre terminal, depuis la racine :

```powershell
Push-Location frontend
try {
    $env:POC_BASE_URL = 'http://127.0.0.1:8081'
    npx playwright install chromium
    if ($LASTEXITCODE -ne 0) { throw 'Installation navigateur en echec' }
    npm run test:e2e
    if ($LASTEXITCODE -ne 0) { throw 'Tests navigateur en echec' }
}
finally { Pop-Location }
```

Les dépendances npm doivent avoir été installées par le build ou npm ci. Playwright ne démarre pas le backend. Le rapport est généré dans frontend/test-results/playwright-results.json, exclu de Git. La CI peut l’archiver. Le scénario vérifie les identités, l’UUID exact, la confidentialité, la reprise, le texte inerte, une commande clavier et le réessai après réponse perdue.

Une modification du template ou du CSS exige reconstruction et redémarrage du JAR. Une modification des seuls tests ne l’exige pas. Pour la cible Angular séparée, utiliser POC_BASE_URL=http://127.0.0.1:4200. Les tests doivent être réexécutés après cette refonte : aucun résultat historique n’est présenté comme preuve de la version courante.

## Accessibilité et sobriété

Champs nommés, commandes clavier natives, focus visible, annonces polies distinctes de l’historique, textes inertes et contrôle de suspension de réception. Vérifier manuellement Tab/Maj+Tab/Entrée, zoom 200 %, petit écran et lecteur d’écran. Un audit RGAA complet reste nécessaire ; aucune conformité certifiée n’est annoncée.

L’interface utilise uniquement des polices système, sans image distante, vidéo ni bibliothèque graphique supplémentaire. L’historique est paginé et les flux inutilisés sont fermés. Les performances à grande échelle ne sont pas mesurées par cette PoC.

## Dépannage

| Problème | Action |
| --- | --- |
| Docker indisponible | Démarrer Docker Desktop et vérifier docker info |
| Java/Node incompatibles | Vérifier JDK21 et Node24 dans le terminal puis rouvrir VS Code |
| EPERM sur esbuild ou module natif | Arrêter Angular ; identifier les processus de cette PoC avant de les fermer. Réinstaller ensuite node_modules avec npm ci, sans effacer package-lock.json. Redémarrer Windows si le verrou persiste. |
| ECONNRESET npm | npm ping --registry=https://registry.npmjs.org/ ; vérifier connexion et proxy, puis reprendre l’installation. Ne pas désactiver TLS. |
| playwright non reconnu | Depuis frontend, vérifier Test-Path .\node_modules\.bin\playwright.cmd ; terminer npm ci avant d’installer Chromium |
| Port occupé | Identifier le PID avec Get-NetTCPConnection -LocalPort 8081 -State Listen ; éviter JAR et conteneur app simultanés |
| ERR_CONNECTION_REFUSED | Garder le terminal Java ouvert ; vérifier le point de santé IPv4 avant Playwright |
| 308 avec localhost | Comparer avec curl.exe --noproxy "*" -I http://127.0.0.1:8081/api/v1/health ; examiner Location et le service qui répond |
| Base inaccessible | Vérifier docker compose ps et logs db ; URL Windows sur 5433, URL entre conteneurs sur db:5432 |
| 401 / 403 | Session expirée ou CSRF invalide ; se reconnecter/recharger en conservant la même origine, sans désactiver CSRF |
| 404 conversation | Ressource absente ou non autorisée ; le support doit être affecté |
| 409 / 412 | Conflit de clé/état ou version périmée ; lire le message et actualiser |
| SSE interrompu | Vérifier session, suspension et flux text/event-stream dans l’onglet réseau |

## Arrêt et conservation

Ctrl+C arrête Java/Angular. `docker compose stop db` puis `docker compose start db` arrêtent et relancent la base sans effacer ses données. En mode Docker complet, utiliser `docker compose stop` puis `docker compose start`.

`docker compose down` conserve le volume. **`docker compose down -v` efface les données locales** : réserver cette commande à une remise à zéro volontaire, jamais à un arrêt ordinaire.

## Limites

Une instance locale, comptes fictifs, texte seul, pas de réservation, paiement, pièces jointes, inscription ou récupération de mot de passe. Pas de présence ni d’accusé de lecture. La clé d’un envoi incertain n’est conservée que dans la page courante ; après rechargement, consulter l’historique avant de renvoyer.

Le débit et le nombre de connexions sont bornés pour une démonstration. Une utilisation réelle exige HTTPS, secrets, politique de conservation, revue sécurité, accessibilité et charge. La PoC ne doit pas être exposée publiquement telle quelle.

## Références

- [Angular : compatibilité](https://angular.dev/reference/versions)
- [Spring Security : CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)
- [PostgreSQL 17](https://www.postgresql.org/docs/17/)
- [Playwright](https://playwright.dev/docs/intro)
- [Java/Spring Boot dans VS Code](https://code.visualstudio.com/docs/java/java-spring-boot)
