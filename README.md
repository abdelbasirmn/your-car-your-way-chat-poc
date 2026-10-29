# Your Car Your Way — PoC de tchat

Cette preuve de concept OpenClassrooms (option B, étape 4) démontre seulement le tchat privé : authentification, messages persistants PostgreSQL, HTTP + SSE, historique et reprise. Elle ne développe ni réservation ni paiement. L’interface privilégie la démonstration technique et l’utilisation au clavier.

## Démarrage local avec Docker Compose

Prérequis : Docker Engine/Desktop et Compose v2, Git ou un outil de décompression. Depuis le dossier décompressé :

```sh
docker compose up --build -d
docker compose logs -f app
```

Ouvrir http://localhost:8080 après le message de démarrage Spring Boot. Les ports sont publiés sur l’interface locale uniquement. La base utilise un volume persistant. Une simple commande `docker compose down` ne supprime pas les messages.

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

Pour la soutenance, suivre [DEMONSTRATION](docs/DEMONSTRATION.md), puis présenter [TRACABILITE](docs/TRACABILITE.md). Consulter [VERIFICATIONS](docs/VERIFICATIONS.md) avant de citer un résultat : sept tests d’intégration et persistance après redémarrage vérifiés sur la fixture PostgreSQL WASM ; validation sur PostgreSQL 17 natif, navigateur et Docker encore nécessaire. La livraison inclut un bundle Git et un manifeste SHA-256 pour contrôler son intégrité.

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

Pour la voie Docker, seuls Docker/Compose sont nécessaires. Pour modifier le code hors conteneur, installer Git, un JDK 21, Node 24 et Docker pour la base. Vérifier `java -version`, `node --version`, `npm --version`, `git --version`, `docker compose version`. Le rapport [VERIFICATIONS](docs/VERIFICATIONS.md) distingue ce qui a réellement été exécuté ici des essais restant nécessaires.

## Récupérer et configurer le code

Décompresser le ZIP, puis ouvrir le dossier `etape4-poc` dans un terminal. Les commandes suivantes partent de ce dossier. Aucun dépôt distant fictif n’est utilisé. Si l’archive contient `historique-git.bundle`, on peut restaurer son historique dans un nouveau dossier avec `git clone historique-git.bundle ycyw-chat` ; cela n’est pas nécessaire pour lancer le projet.

Le fichier `.env.example` est un exemple sans secret réel. Par défaut, aucune copie n’est nécessaire. Pour changer le mot de passe local de la base, copier ce fichier en `.env` (`cp .env.example .env`, ou `Copy-Item .env.example .env` sous PowerShell) **avant la première création du volume**. Modifier cette variable ne change pas le mot de passe d’un volume PostgreSQL déjà initialisé.

| Variable | Défaut hors Compose | Explication |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | aucun, démarrage refusé | Définir `local` pour la démonstration ; les tests choisissent `test` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/ycyw` | URL JDBC de la base |
| `DB_USER` | `ycyw` | Utilisateur local |
| `DB_PASSWORD` | `local-demo-only` | Mot de passe fictif de la base |
| `SERVER_ADDRESS` | `127.0.0.1` | Compose utilise `0.0.0.0` dans le conteneur ; port publié localement |
| `SESSION_TIMEOUT` | `30m` | Inactivité maximale de session |
| `STREAM_POLL_MS` | `1000` | Intervalle serveur de lecture du journal SSE ; limite pédagogique |
| `POC_BASE_URL` | `http://127.0.0.1:8080` | Cible des tests Playwright |
| `CHROMIUM_EXECUTABLE` | Chromium installé par Playwright | Option pour une installation de navigateur existante |

Ports : base 5432, application assemblée 8080, serveur Angular de développement 4200. Utiliser systématiquement le même nom d’hôte dans un navigateur ; `localhost` et `127.0.0.1` n’ont pas les mêmes cookies.

## Développer dans un IDE

Ouvrir la racine du projet dans VS Code, IntelliJ ou un autre IDE. Importer `backend/pom.xml` comme projet Maven et sélectionner le **JDK 21**, y compris pour Maven. Pour le frontend, utiliser le TypeScript du dossier `frontend/node_modules/typescript`. Les extensions Java et Angular sont facultatives. Aucune configuration propre à un IDE n’est indispensable.

Démarrer seulement la base :

```sh
docker compose up -d db
```

Terminal 1, Linux/macOS :

```sh
cd backend
export SPRING_PROFILES_ACTIVE=local
./mvnw spring-boot:run
```

Terminal 1, Windows PowerShell :

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = 'local'
.\mvnw.cmd spring-boot:run
```

Si `.env` contient un mot de passe différent, définir aussi `DB_PASSWORD` dans ce terminal : Spring Boot exécuté hors Docker ne lit pas automatiquement `.env`. Si l’application Compose utilise déjà 8080, l’arrêter par `docker compose stop app` avant de lancer le backend de l’IDE.

Terminal 2, tous systèmes :

```sh
cd frontend
npm ci --no-audit --no-fund
npm start
```

Ouvrir **http://localhost:4200**. Le proxy de développement transmet `/api` au backend 8080 ; les cookies et SSE passent par la même origine du navigateur. Ne pas ouvrir directement `index.html` depuis le disque. Le premier démarrage de Maven/Node nécessite un accès aux registres publics.

## Construire l’application assemblée

Depuis la racine sous Linux/macOS :

```sh
sh scripts/build-local.sh
SPRING_PROFILES_ACTIVE=local java -jar backend/target/chat-poc-1.0.0.jar
```

Sous PowerShell :

```powershell
.\scripts\build-local.ps1
$env:SPRING_PROFILES_ACTIVE = 'local'
java -jar backend/target/chat-poc-1.0.0.jar
```

La base doit déjà fonctionner. Ouvrir http://localhost:8080. Le script compile Angular, copie ses fichiers dans les ressources du backend puis fabrique le JAR. `./mvnw package` seul ne reconstruit pas Angular. Les fichiers assemblés sont régénérables et exclus de Git. Le Dockerfile effectue les mêmes étapes dans ses images de construction.

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

Démarrer une base PostgreSQL locale vide ou réservée aux tests avec `docker compose up -d db`. Les tests utilisent des comptes fictifs et créent de nouvelles conversations : ne jamais pointer sur une base réelle de clients. Depuis `backend` :

```sh
./mvnw -B verify
```

Sous Windows remplacer `./mvnw` par `.\mvnw.cmd`. Les variables `DB_URL`, `DB_USER`, `DB_PASSWORD` peuvent désigner une base de test séparée ; les mêmes valeurs par défaut s’appliquent. Le profil `test` est déclaré par la classe de tests. Les migrations restent actives dans la configuration livrée.

Pour les tests navigateur, démarrer l’application assemblée sur 8080, puis depuis `frontend` :

```sh
npm ci --no-audit --no-fund
npx playwright install chromium
npm run test:e2e
```

Sur Linux, Playwright peut demander `npx playwright install --with-deps chromium` avec les droits d’installation système appropriés. Aucun outil n’est installé silencieusement par le projet. Le résultat JSON est écrit dans `docs/evidence/playwright-results.json`. Les tests navigateur échangent uniquement des données fictives. Le service de backend doit déjà être lancé ; Playwright ne le démarre pas.

Pour une cible 4200, définir `POC_BASE_URL=http://localhost:4200` dans le terminal avant les tests. La CI fournie compile Angular, exécute les tests Java avec PostgreSQL 17 puis les tests navigateur ; elle n’est pas présentée comme exécutée tant qu’un run distant n’est pas disponible.

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

Si la publication distante n’a pas été réalisée, créer un dépôt GitHub vide dans son compte, puis depuis le projet restauré avec le bundle :

```sh
git remote add origin URL_REELLE_DU_DEPOT_CREE
git push -u origin main
```

Remplacer le paramètre par l’URL réellement donnée par GitHub. L’archive contient les sources publiables et l’historique local ; la remise OpenClassrooms requiert également le lien du dépôt GitHub réel. Ne pas publier `.env`, dépendances installées ou données de base.

## Références officielles

Consultation et versions vérifiées le 28 octobre 2026 ; les lockfiles et le POM font foi pour cette livraison.

- [Compatibilité Angular, Node, TypeScript et RxJS](https://angular.dev/reference/versions).
- [Spring Security : CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
- [Spring Security : stockage des mots de passe](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
- [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html).
- [Spring Boot](https://docs.spring.io/spring-boot/).
- [PostgreSQL 17 : verrous explicites](https://www.postgresql.org/docs/17/explicit-locking.html).
- [PostgreSQL : dépôt officiel de paquets Ubuntu](https://www.postgresql.org/download/linux/ubuntu/).
- [Maven Wrapper](https://maven.apache.org/wrapper/).
- [Playwright : installation](https://playwright.dev/docs/intro).
