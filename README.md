# Your Car Your Way — PoC de tchat

Cette preuve de concept OpenClassrooms (option B, étape 4) démontre seulement le tchat privé : authentification, messages persistants PostgreSQL, HTTP + SSE, historique et reprise. Elle ne développe ni réservation ni paiement. L’interface privilégie la démonstration technique et l’utilisation au clavier.

## Démarrage local avec Docker Compose

Prérequis : Docker Engine/Desktop démarré et Compose v2, Git ou un outil de décompression. Depuis la racine `etape4-poc`, arrêter le JAR local avant ce mode : Docker et le JAR ne peuvent pas publier simultanément 8081. Cette voie construit et lance toute la PoC :

```sh
docker compose up --build -d
docker compose logs -f app
```

Ouvrir http://127.0.0.1:8081 après le message de démarrage Spring Boot. Les ports sont publiés sur l’interface locale uniquement. La base utilise un volume persistant. Une simple commande `docker compose down` ne supprime pas les messages.

## Comptes fictifs de démonstration

| Compte | Identifiant | Mot de passe local |
| --- | --- | --- |
| Client A | alice@example.test | Demo-Alice-2026! |
| Client B | bob@example.test | Demo-Bob-2026! |
| Support affectable | support@example.test | Demo-Support-2026! |
| Autre support | other-support@example.test | Demo-Other-2026! |

Ces identités et mots de passe sont publics et fictifs. Le démarrage refuse un profil autre que `local` ou `test`. Ce dépôt ne doit pas être exposé sur Internet.

## Première démonstration

1. Se connecter comme Alice, créer une conversation et envoyer un message.
2. Ouvrir un autre navigateur/profil privé, se connecter comme support et prendre la demande en charge.
3. Échanger sans recharger la page, puis couper/reprendre la réception dans l’interface.
4. Se connecter comme Bob dans une troisième session ; il ne voit pas la conversation d’Alice et son URL API lui est refusée.

Pour la soutenance, suivre [DEMONSTRATION](docs/DEMONSTRATION.md), puis présenter [TRACABILITE](docs/TRACABILITE.md). Consulter [VERIFICATIONS](docs/VERIFICATIONS.md) avant de citer un résultat : sept tests d’intégration et persistance après redémarrage vérifiés sur la fixture PostgreSQL WASM ; validation sur PostgreSQL 17 natif, navigateur et Docker encore nécessaire. Les ZIP de la série des commits contiennent les sources et un manifeste SHA-256 ; ils ne recréent pas votre historique Git local. Les rapports sont historiques tant que de nouvelles preuves ne sont pas jointes. Le démarrage avec PostgreSQL 17.11 et Flyway a été montré dans les journaux Windows de l’utilisateur, et les tests du commit 3 ont été annoncés fonctionnels ; cela ne remplace pas les preuves actualisées à réunir pour le commit 6, ni une validation des changements ultérieurs.

## Versions et prérequis du développement

| Outil | Version du projet | Usage |
| --- | --- | --- |
| Java JDK | 21 | Compiler et exécuter le backend |
| Spring Boot | 4.1.2 | API, sécurité, sessions et transactions |
| Maven | 3.9.11, wrapper fourni | Aucun Maven global obligatoire |
| Node.js | 24.22.1 recommandé | Angular et ses outils |
| Angular | 21.2.25 ; CLI/build 21.2.29 | Interface standalone |
| TypeScript / RxJS | 5.9.3 / 7.8.2 | Versions verrouillées dans le lockfile |
| PostgreSQL | 17.11 dans Compose et CI | Persistance et verrous transactionnels |
| Playwright | 1.62.1 | Tests navigateur |

Pour la voie Docker, seuls Docker/Compose sont nécessaires. Pour modifier le code hors conteneur, installer Git, un JDK 21, Node 24 et Docker pour la base. Node 22 peut satisfaire Angular, mais le package.json de cette PoC déclare explicitement Node >=24 et <25 ; utiliser Node 24 pour respecter le projet. Vérifier `java -version`, `node --version`, `npm --version`, `git --version`, `docker compose version`. Le rapport [VERIFICATIONS](docs/VERIFICATIONS.md) distingue ce qui a réellement été exécuté ici des essais restant nécessaires.

## Récupérer et configurer le code

Décompresser le ZIP, puis ouvrir le dossier `etape4-poc` dans un terminal. Les commandes suivantes partent de ce dossier. Aucun dépôt distant fictif n’est utilisé. Si l’archive contient `historique-git.bundle`, on peut restaurer son historique dans un nouveau dossier avec `git clone historique-git.bundle ycyw-chat` ; cela n’est pas nécessaire pour lancer le projet.

Le fichier `.env.example` est un exemple sans secret réel. Par défaut, aucune copie n’est nécessaire. Pour changer le mot de passe local de la base, copier ce fichier en `.env` (`cp .env.example .env`, ou `Copy-Item .env.example .env` sous PowerShell) **avant la première création du volume**. Modifier cette variable ne change pas le mot de passe d’un volume PostgreSQL déjà initialisé.

| Variable | Défaut du code / des outils | Valeur pour ce guide hors Docker |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Aucun, démarrage refusé | `local` ; les tests Java choisissent `test` |
| `SERVER_PORT` | `8080` | `8081` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/ycyw` | `jdbc:postgresql://localhost:5433/ycyw` |
| `DB_USER` | `ycyw` | `ycyw` |
| `DB_PASSWORD` | `local-demo-only` | Identique au mot de passe du volume PostgreSQL |
| `SERVER_ADDRESS` | `127.0.0.1` | Conserver ; Compose définit `0.0.0.0` dans son conteneur |
| `SESSION_TIMEOUT` | `30m` | Conserver pour la démonstration |
| `STREAM_POLL_MS` | `1000` | Conserver ; intervalle serveur, pas polling navigateur |
| `POC_BASE_URL` | `http://127.0.0.1:8080` dans Playwright | Définir explicitement `http://127.0.0.1:8081` |
| `CHROMIUM_EXECUTABLE` | Chromium de Playwright | Facultatif : navigateur existant compatible |

| Composant | Depuis Windows | À l’intérieur des conteneurs |
| --- | --- | --- |
| PostgreSQL | `127.0.0.1:5433` | Service `db`, port 5432 |
| Application Docker | `http://127.0.0.1:8081` | Service `app`, port 8080 |
| JAR ou backend IDE | `http://127.0.0.1:8081` avec SERVER_PORT | Exécution Windows, pas le conteneur app |
| Angular de développement | `http://127.0.0.1:4200` | Proxy `/api` vers `http://127.0.0.1:8081` |

Les valeurs par défaut du code restent inchangées : les commandes ci-dessous fixent explicitement celles du guide. Dans Compose, conserver `DB_URL=jdbc:postgresql://db:5432/ycyw` : 5433 est le port Windows, pas celui à utiliser entre conteneurs.
Utiliser toujours le même nom d’hôte dans les navigateurs et les tests ; `localhost` et `127.0.0.1` ne partagent pas leurs cookies.

## VS Code et terminaux PowerShell

Ouvrir **le dossier etape4-poc** avec Fichier → Ouvrir le dossier. Installer si besoin Extension Pack for Java, Spring Boot Extension Pack et Angular Language Service. Dans Ctrl+Maj+P → Java: Configure Java Runtime, sélectionner le JDK 21. L’import Maven porte sur backend/pom.xml ; Maven Wrapper est fourni.

Ouvrir Terminal → Nouveau terminal et sélectionner PowerShell. Renommer les terminaux Application et Tests permet de distinguer le serveur qui reste ouvert des commandes de test. Une variable `$env:...` ne s’applique qu’au terminal où elle est définie et aux processus qu’il lance : la redéfinir dans un nouveau terminal. Spring Boot hors Docker ne charge pas automatiquement .env.

Vérifier les outils depuis la racine :

```powershell
java -version
javac -version
node --version
npm --version
git --version
docker compose version
docker info
```

Fermer et rouvrir VS Code après un changement de Java/Node pour actualiser son environnement. Si code est dans le PATH, `code .` ouvre le dossier courant ; sinon utiliser le menu Fichier. Aucun IDE particulier n’est obligatoire pour le projet.

## Développer dans un IDE

Ouvrir la racine du projet dans VS Code, IntelliJ ou un autre IDE. Importer `backend/pom.xml` comme projet Maven et sélectionner le **JDK 21**, y compris pour Maven. Pour le frontend, utiliser le TypeScript du dossier `frontend/node_modules/typescript`. Les extensions Java et Angular sont facultatives. Aucune configuration propre à un IDE n’est indispensable.

Démarrer seulement la base :

```sh
docker compose up -d db
```

Terminal 1, Linux/macOS :

```sh
cd backend
export SPRING_PROFILES_ACTIVE=local SERVER_PORT=8081
export DB_URL=jdbc:postgresql://localhost:5433/ycyw DB_USER=ycyw DB_PASSWORD=local-demo-only
./mvnw spring-boot:run
```

Terminal 1, Windows PowerShell :

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:SERVER_PORT = '8081'
$env:DB_URL = 'jdbc:postgresql://localhost:5433/ycyw'
$env:DB_USER = 'ycyw'
$env:DB_PASSWORD = 'local-demo-only'
.\mvnw.cmd spring-boot:run
```

Si `.env` contient un mot de passe différent, définir aussi `DB_PASSWORD` dans ce terminal : Spring Boot exécuté hors Docker ne lit pas automatiquement `.env`. Si l’application Compose utilise déjà 8081, l’arrêter par `docker compose stop app` avant de lancer le backend de l’IDE.

Terminal 2, ouvert à la racine du projet, tous systèmes (installer uniquement si nécessaire et sans autre Angular actif) :

```sh
cd frontend
npm ci --no-audit --no-fund
npm start
```

Ouvrir **http://127.0.0.1:4200**. Le proxy de développement transmet `/api` au backend 8081 ; les cookies et SSE passent par la même origine du navigateur. Ne pas ouvrir directement `index.html` depuis le disque. Le premier démarrage de Maven/Node nécessite un accès aux registres publics.

## Construire l’application assemblée

Depuis la racine sous Linux/macOS :

```sh
sh scripts/build-local.sh
SPRING_PROFILES_ACTIVE=local SERVER_PORT=8081 DB_URL=jdbc:postgresql://localhost:5433/ycyw DB_USER=ycyw DB_PASSWORD=local-demo-only java -jar backend/target/chat-poc-1.0.0.jar
```

Sous PowerShell :

```powershell
.\scripts\build-local.ps1
```

Attendre BUILD SUCCESS. Seulement si le build réussit, dans le même terminal :

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:SERVER_PORT = '8081'
$env:DB_URL = 'jdbc:postgresql://localhost:5433/ycyw'
$env:DB_USER = 'ycyw'
$env:DB_PASSWORD = 'local-demo-only'
java -jar .\backend\target\chat-poc-1.0.0.jar
```

Arrêter Angular et toute ancienne instance du JAR avant de reconstruire pour éviter les fichiers verrouillés. La base doit déjà fonctionner (`docker compose up -d db`). Si un conteneur app fonctionne, exécuter `docker compose stop app` avant de lancer le JAR local. Ouvrir http://127.0.0.1:8081. Le script compile Angular, copie ses fichiers dans les ressources du backend puis fabrique le JAR. `./mvnw package` seul ne reconstruit pas Angular. Les fichiers assemblés sont régénérables et exclus de Git. Le Dockerfile effectue les mêmes étapes dans ses images de construction. Le script PowerShell restaure le dossier d’appel dans un bloc finally, y compris en cas d’erreur. Si Windows affiche un avertissement pour ce script téléchargé, le consulter puis, si approuvé, utiliser `Unblock-File -Path .\scripts\build-local.ps1` ; aucune modification globale de stratégie PowerShell n’est nécessaire.

Laisser le terminal Java ouvert. Le JAR sert le frontend : ne pas lancer npm start dans ce mode. Dans un autre terminal, vérifier :

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/health"
```

Attendre `status : UP` avant les tests. Le point de santé prouve la réponse du processus, pas à lui seul le fonctionnement complet de la base et des échanges.

## Structure et fonctionnement

| Chemin | Responsabilité |
| --- | --- |
| `backend/.../security/` | Comptes fictifs, mots de passe PBKDF2, session et CSRF |
| `backend/.../web/` | Contrôleur HTTP et réponses d’erreur |
| `backend/.../chat/ChatService.java` | Droits, règles, transactions et requêtes JDBC paramétrées |
| `backend/.../chat/StreamService.java` | Références SSE, reprise et vérification des droits en cours de flux |
| `backend/src/main/resources/db/migration/` | SQL versionné, contraintes, index et sessions |
| `backend/src/test/` | Tests d’intégration avec une vraie base PostgreSQL accessible |
| `frontend/src/` | Connexion, conversations, historique, envoi et réception |
| `frontend/e2e/` | Tests multi-sessions Playwright |
| `docs/` | Architecture réalisée, données, API, traçabilité, démonstration et vérifications |
| `.github/workflows/verify.yml` | Vérification prévue sur PostgreSQL 17 natif dans GitHub Actions |

Un POST est autorisé à partir de l’identité de session. Dans une transaction, le service verrouille la conversation et enregistre le message et l’événement avec une séquence stable. La réponse indique **Enregistré** après commit. Le flux SSE signale un identifiant ; le navigateur relit les messages par HTTP, dans l’ordre, avec pagination et déduplication. Il n’existe aucun état « lu ». Le rôle support ne permet de lire que les conversations affectées à cet agent. La liste des demandes en attente expose des métadonnées, pas leur contenu.

Voir [API et données](docs/API_ET_DONNEES.md) et [adaptations de l’architecture](docs/ARCHITECTURE_ET_ADAPTATIONS.md).

## Migrations et persistance

Flyway applique automatiquement `V1__chat_and_sessions.sql` sur une base vierge et conserve sa version. Les comptes fictifs sont créés au démarrage uniquement s’ils n’existent pas. Ne pas modifier une migration déjà appliquée : créer ensuite un nouveau fichier `V2__description.sql` pour une évolution.

`docker compose stop` arrête les services ; `docker compose start` les relance. `docker compose down` retire les conteneurs et conserve le volume. Les messages persistent aussi après le redémarrage du backend. Les sauvegardes et restaurations de production sont hors PoC.

**Remise à zéro destructive**, uniquement si l’on veut effacer tous les messages, comptes et sessions locaux :

```sh
docker compose down -v
docker compose up --build -d
```

Cette commande ne fait pas partie du démarrage normal.

## Tests automatisés

### Tests Java sur PostgreSQL natif

Arrêter le JAR avec Ctrl+C avant `verify`, qui reconstruit l’archive et peut rencontrer un verrou Windows. Démarrer une base locale réservée aux données fictives : `docker compose up -d db`. Depuis la racine, dans PowerShell :

```powershell
Push-Location backend
try {
    $env:DB_URL = 'jdbc:postgresql://localhost:5433/ycyw'
    $env:DB_USER = 'ycyw'
    $env:DB_PASSWORD = 'local-demo-only'
    .\mvnw.cmd -B verify
    if ($LASTEXITCODE -ne 0) { throw 'Tests Maven en echec' }
}
finally {
    Pop-Location
}
```

Les tests déclarent leur profil test et utilisent Flyway actif. Ils créent des comptes/conversations fictifs ; ne jamais pointer sur une base de clients réelle. Les rapports sont dans backend/target/surefire-reports. Relever les compteurs exacts et le résultat final ; ne pas confondre compilation et réussite des assertions.
Sous Linux/macOS, définir les mêmes variables DB avec export et utiliser ./mvnw -B verify depuis backend.

### Tests navigateur

Relancer l’application assemblée sur 8081 et la laisser ouverte dans un autre terminal. Vérifier `/api/v1/health` avant de commencer. Depuis la racine, PowerShell :

```powershell
Push-Location frontend
try {
    $env:POC_BASE_URL = 'http://127.0.0.1:8081'
    npx playwright install chromium
    if ($LASTEXITCODE -ne 0) { throw 'Installation Chromium en echec' }
    npm run test:e2e
    if ($LASTEXITCODE -ne 0) { throw 'Tests Playwright en echec' }
}
finally {
    Pop-Location
}
```

Si les dépendances locales ne sont pas installées, arrêter tout Angular actif puis exécuter npm ci --no-audit --no-fund depuis frontend avant l’installation du navigateur. Pour diagnostiquer le premier test depuis frontend :

```powershell
npm run test:e2e -- --grep "Échange privé" --headed --trace on
```

Playwright ne démarre pas le backend. La fonction login vérifie le POST de connexion, le bouton de déconnexion et l’identité de session ; le premier scénario sélectionne la conversation par son UUID complet. Après une modification de frontend/src/app.html, reconstruire le JAR et le relancer avant de tester ; une modification du seul fichier e2e ne nécessite pas de rebuild du JAR.
Le rapport JSON est docs/evidence/playwright-results.json. Conserver les preuves locales récentes ; ne pas les remplacer par les rapports historiques d’un ZIP. Un test échoué ne constitue pas une preuve de réussite.
Sur Linux, l’installation peut nécessiter npx playwright install --with-deps chromium avec les droits appropriés. Pour la cible Angular séparée, définir POC_BASE_URL=http://127.0.0.1:4200 dans le terminal de test ; garder Angular et backend démarrés.
La CI fournie utilise ses propres ports internes 8080/5432, compile et prévoit des tests Java et navigateur. Un run GitHub Actions n’est déclaré réussi qu’avec son résultat distant effectif ; ne pas transposer les ports Windows dans ses connexions internes.

## Vérifications manuelles de reprise et d’accessibilité

Le [scénario de démonstration](docs/DEMONSTRATION.md) fournit une séquence reproductible. Utiliser des profils de navigateur distincts : deux fenêtres privées du même navigateur peuvent partager une session. Vérifier l’absence de message dupliqué, la récupération des messages envoyés pendant une suspension et le refus d’une URL de conversation privée depuis Bob.

Au clavier seul, parcourir la connexion puis l’envoi ; le focus doit rester visible. Une réception ne doit pas déplacer le focus de la saisie. Tester le chargement d’historique et le zoom à 200 %. Les messages sont du texte, même s’ils contiennent du HTML. Tester avec un lecteur d’écran les annonces polies des nouveaux messages ; l’historique initial ne doit pas être annoncé comme une nouvelle réception. Les vérifications automatisées de clavier ne remplacent ni un audit RGAA ni des essais avec des personnes en situation de handicap.

## Dépannage

| Symptôme | Vérification et action |
| --- | --- |
| `docker` absent ou démon indisponible | Installer/démarrer Docker Desktop ou Engine ; sinon utiliser un PostgreSQL 17 déjà installé et la voie IDE |
| Java incompatible | Configurer `JAVA_HOME` vers un JDK 21 et vérifier le Java de Maven avec `./mvnw -version` |
| Node incompatible | Utiliser Node 24 ; supprimer les dépendances locales puis refaire `npm ci` si elles ont été installées avec un autre environnement |
| Permission refusée sur `mvnw` | Exécuter `chmod +x backend/mvnw`, ou `sh mvnw` depuis `backend` |
| Port occupé | Arrêter le service concurrent ; ne pas lancer Compose app et l’IDE sur le même port |
| Base inaccessible | Vérifier `docker compose ps`, `docker compose logs db`, l’URL JDBC et le mot de passe ; dans Compose l’hôte est `db`, hors Docker `localhost` |
| Erreur Flyway | Lire la cause SQL ; ne pas désactiver Flyway pour masquer l’erreur et ne pas modifier une migration appliquée |
| 401 | Se reconnecter ; la session peut avoir expiré ou avoir été invalidée |
| 403 / CSRF | Utiliser le même hôte, autoriser les cookies locaux et recharger la page pour récupérer le token ; ne pas désactiver CSRF |
| 404 sur conversation | Ressource absente ou non autorisée ; un support doit d’abord prendre la demande en charge |
| 412 | Version périmée à la clôture ; actualiser la conversation |
| 409 | Conversation clôturée, affectation concurrente ou clé réutilisée avec un autre texte ; actualiser et lire le message |
| SSE interrompu | Réception suspendue ? Session valide ? Onglet réseau : flux `text/event-stream`, même origine ; un proxy ne doit pas mettre ce flux en tampon |
| Messages absents après redémarrage | Vérifier le volume et la base ciblée ; `down -v` efface les données |

## Limites et publication GitHub

Une instance locale, quatre comptes fictifs, texte uniquement, pas de pièces jointes ni de présence, pas de service tiers de tchat. L’authentification est réelle mais il n’existe pas d’inscription ni de récupération de mot de passe dans cette PoC. Le paiement, le fournisseur d’identité des agences et l’hébergement cible sont exclus du scénario ; aucune intégration fictive n’est présentée comme opérationnelle.

Les limites de taille, débit et connexions sont pédagogiques. Le serveur interroge le journal pour les flux actifs ; ce coût n’a pas été validé à l’échelle internationale. Le brouillon et la clé d’envoi incertain sont conservés seulement dans la page courante. Un rechargement demande de consulter l’historique avant un nouvel envoi. HTTP est réservé à la boucle locale. Les cookies Secure, HTTPS, politiques de conservation, durcissement, tests de charge et audits complets restent nécessaires avant tout usage réel.

Si la publication distante n’a pas été réalisée, créer un dépôt GitHub vide dans son compte, puis depuis votre dépôt Git local :

```sh
git remote add origin URL_REELLE_DU_DEPOT_CREE
git push -u origin main
```

Remplacer le paramètre par l’URL réellement donnée par GitHub. Les ZIP de sources ne contiennent pas le dossier .git ; préserver celui de votre dépôt existant. Pour un nouveau dépôt, initialiser Git et créer un premier commit avant le push. La remise OpenClassrooms requiert également le lien du dépôt GitHub réel. Ne pas publier `.env`, dépendances installées ou données de base.

## Références officielles

Références documentaires ; les lockfiles, le POM et les fichiers de configuration font foi pour les versions de cette livraison. Ce commit de documentation ne constitue pas une nouvelle validation de tous les composants.

- [Compatibilité Angular, Node, TypeScript et RxJS](https://angular.dev/reference/versions).
- [Spring Security : CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
- [Spring Security : stockage des mots de passe](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
- [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html).
- [Spring Boot](https://docs.spring.io/spring-boot/).
- [PostgreSQL 17 : verrous explicites](https://www.postgresql.org/docs/17/explicit-locking.html).
- [PostgreSQL : dépôt officiel de paquets Ubuntu](https://www.postgresql.org/download/linux/ubuntu/).
- [Maven Wrapper](https://maven.apache.org/wrapper/).
- [Playwright : installation](https://playwright.dev/docs/intro).

## Dépannage Windows rencontré pendant la mise en place

| Symptôme | Action ciblée |
| --- | --- |
| EPERM sur esbuild.exe ou node.napi.node | Arrêter npm start avec Ctrl+C dans son terminal avant npm ci ou le build. Un processus orphelin de cette PoC peut encore verrouiller un fichier : l’identifier avant de l’arrêter. Ne pas arrêter tous les processus Node des autres projets. |
| Installation partielle | Après fermeture des processus de cette PoC, supprimer seulement frontend/node_modules puis relancer npm ci. Garder package-lock.json. En dernier recours, redémarrer Windows et réessayer avant de relancer Angular. |
| ECONNRESET vers npm | Exécuter npm ping --registry=https://registry.npmjs.org/ et vérifier connexion/proxy autorisé. Reprendre l’installation après rétablissement du réseau ; ne pas désactiver la vérification TLS. |
| playwright non reconnu | L’installation locale n’a pas abouti : npm ci, puis Test-Path .\node_modules\.bin\playwright.cmd depuis frontend. Installer Chromium seulement ensuite. |
| ERR_CONNECTION_REFUSED | Vérifier que le JAR fonctionne encore, que le terminal n’a pas été fermé et que le point de santé répond sur 127.0.0.1:8081. Ouvrir un second terminal pour les tests. |
| 308 avec localhost | Comparer l’adresse IPv4 avec curl.exe --noproxy "*" -I http://127.0.0.1:8081/api/v1/health ; inspecter Location et le processus écoutant avant de conclure. Ne pas ignorer une redirection inconnue. |
| Port 8081 occupé | Get-NetTCPConnection -LocalPort 8081 -State Listen : identifier le PID avant toute interruption ; vérifier qu’un conteneur app et un JAR local ne sont pas démarrés ensemble. |
| Mauvais mot de passe après modification de .env | Un volume PostgreSQL existant conserve son mot de passe ; aligner DB_PASSWORD dans Compose et dans le terminal Java. Ne pas effacer le volume pour masquer le problème. |
| Caractères Ã© dans un rapport | Lire avec Get-Content -Encoding UTF8 -LiteralPath CHEMIN_DU_FICHIER ; vérifier l’encodage dans VS Code avant de modifier les sources. |

### Scénario de démonstration dans des sessions distinctes

Ouvrir http://127.0.0.1:8081 dans Chrome normal (Alice), Edge normal (support), puis Chrome privé (Bob). Alice crée une conversation et envoie un texte. Le support actualise sa liste et prend cette demande en charge. Échanger dans les deux sens, suspendre la réception côté Alice, envoyer plusieurs réponses puis reprendre. Vérifier les messages rattrapés, l’absence de doublons et le statut Enregistré (pas Lu).
Bob ne doit pas voir la conversation d’Alice. Avec son UUID complet, le GET /api/v1/conversations/UUID/messages depuis la session Bob doit être refusé en 404. Vérifier au clavier Tab/Maj+Tab/Entrée, le focus et le texte HTML affiché inerte. Redémarrer uniquement le backend, se reconnecter au besoin, puis retrouver l’historique. Les contrôles lecteur d’écran et RGAA restent distincts de cette démonstration.

### Fin de session

Arrêter Java/Angular avec Ctrl+C. Depuis la racine : docker compose stop db conserve les données ; docker compose start db les remet à disposition. Si toute l’application fonctionne dans Compose, docker compose stop puis docker compose start sont les commandes correspondantes.
Ne jamais utiliser down -v pour un arrêt ordinaire : cette commande efface les données locales du volume.

Documentation officielle complémentaire : [Spring Boot dans VS Code](https://code.visualstudio.com/docs/java/java-spring-boot) et [réseau Docker Compose](https://docs.docker.com/compose/how-tos/networking/).
