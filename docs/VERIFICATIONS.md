# Rapport des vérifications — 28 octobre 2026

## Portée et environnement réellement utilisé

Audit d’exécution de la PoC, sans certificat de conformité. Java OpenJDK 21.0.12, Maven 3.9.11, Node 24.22.1, Angular 21.2.25/CLI 21.2.29, TypeScript 5.9.3 et Playwright 1.62.1. Le backend Spring Boot 4.1.2 et les dépendances npm ont été effectivement récupérés, compilés et assemblés.

**La base des essais exécutés est PostgreSQL 18.6 compilé en WebAssembly**, via PGlite 0.5.13 et pglite-socket 0.2.16, sur disque. Il ne s’agit pas d’une base H2, SQLite ou d’un tableau mémoire. Ce mode est un outil d’essai externe au produit livré, dont la configuration normale utilise PostgreSQL 17.11 natif.

Docker n’est pas installé dans cet environnement. Le binaire officiel PostgreSQL 17.11 a été téléchargé et son numéro de version exécuté ; `initdb` exige un utilisateur non root et le conteneur ne permet pas de changer d’UID. Le serveur natif n’a donc pas pu être initialisé. Aucun mécanisme de sécurité système n’a été désactivé pour contourner cette restriction.

La passerelle WASM ne reproduit pas toutes les connexions concurrentes PostgreSQL. Pour les tests concluants, le pool JDBC a été limité à **une connexion** par variable d’environnement. Le fichier SQL V1 a été exécuté directement sur une base neuve et Flyway désactivé **uniquement dans le lanceur d’essai**, après qu’un essai d’application par Flyway sur cette passerelle est resté bloqué. Aucune désactivation n’est livrée dans `application.yml`, Compose ou CI. Pour le redémarrage de deux JVM successives, le lanceur désactive également les requêtes préparées nommées avec `prepareThreshold=0`, afin d’éviter les noms de statement réutilisés par la passerelle.

## Résultats

| Vérification | État | Preuve et portée |
| --- | --- | --- |
| Dépendances frontend et lockfile | Réussie | Installation npm effective ; package-lock.json livré ; build exécutable |
| Compilation Java et packaging Spring Boot | Réussie | `evidence/backend-package.txt` ; build Maven réussi |
| Build Angular optimisé | Réussie | `evidence/frontend-build.txt` ; initial 194,59 kB brut, transfert estimé 55,16 kB ; estimation du build, pas mesure réseau |
| Schéma SQL V1 | Réussie sur WASM | Exécution du SQL exact sur une base vide ; comptes, contraintes et index utilisés par les tests |
| Flyway sur PostgreSQL 17 natif | Non exécutée | Serveur natif impossible à initialiser ici ; pipeline et commande de reproduction fournis |
| Tests d’intégration | Réussis avec réserve d’environnement | **7 tests, 0 échec, 0 erreur, 0 ignoré** ; `evidence/junit-integration.xml` et journal associé |
| Démarrage du JAR avec interface assemblée | Réussi | Processus Java lancé, point de santé accessible ; aucune exposition publique |
| Authentification et CSRF | Réussis | Mauvais mot de passe 401 ; absence de session 401 ; POST sans token 403 ; déconnexion puis lecture 401 |
| Confidentialité et affectation | Réussies | Bob et autre agent refusés ; support refusé avant affectation ; ancien agent refusé après libération |
| Expiration de session | Réussie | Horodatages de session rendus expirés dans la fixture ; lecture privée ensuite refusée en 401 |
| Déduplication / clôture | Réussies | Même clé et texte : même id et une ligne ; texte différent 409 ; nouvel envoi clôturé 409 ; répétition déjà enregistrée après clôture acceptée |
| Entrées | Réussies côté serveur | Vide et 2 001 caractères refusés ; script conservé comme chaîne de texte |
| Historique paginé | Réussi | 105 messages, pages arrière 50/50/5 et rattrapage avant 100/5 ; pas de perte dans le jeu de test |
| Reprise SSE | Réussie côté HTTP | Connexion avec curseur 1 reçoit événement `message.created` de séquence 2, déjà persisté |
| Appels concurrents | Réussis dans la fixture sérialisée | 12 appels depuis 4 threads, 12 séquences/événements distincts avec pool1 ; **ne valide pas les verrous de PostgreSQL 17 natif** |
| Redémarrage et persistance | Réussis sur WASM | `evidence/restart-result.json` : même message commité retrouvé, même session restaurée après arrêt/relance de la JVM ; interface assemblée servie en HTTP |
| Tests navigateur Playwright | Bloqués au lancement | Deux scénarios lancés, deux erreurs de lancement Chrome ; aucune assertion de parcours exécutée. `evidence/browser-launch-failure.txt` et rapport JSON |
| Clavier, focus, rendu XSS et zoom | Non validés en navigateur | Interface compilée et code inspecté ; scénario et tests fournis, lancement natif bloqué |
| Lecteur d’écran et audit RGAA | Non exécutés | Nécessitent environnement d’assistance et recette dédiée ; aucune conformité certifiée annoncée |
| Compose / arrêt-relance du volume Docker | Non exécutés | Docker absent ; volume nommé et commandes fournis, à vérifier dans l’environnement développeur |
| Tests de charge, 10 conversations, seuil de 2 s | Non exécutés | L’objectif exploratoire V-13 n’est pas une performance démontrée |
| Fermeture d’un flux ouvert après révocation/expiration | Non vérifiée de bout en bout | Contrôle périodique présent dans StreamService ; refus des accès HTTP vérifié ; compléter sur natif |
| GitHub Actions | Configuration livrée, pas de run attesté | PostgreSQL17, build, tests Java et navigateur prévus ; absence de résultat distant tant que dépôt non publié |

La commande `npm run test:e2e -- --list` a également été exécutée : les deux scénarios sont reconnus sans erreur de syntaxe. Cela ne remplace pas leur exécution en navigateur.

## Échecs et limites conservés

L’essai initial avec plusieurs connexions sur pglite-socket a produit une erreur de transaction lors du test concurrent (5 tests réussis sur 6 à cette étape). Le passage du pool à 1 a permis aux sept tests finaux de passer ; il résout une limite de la fixture, pas une démonstration de montée en charge. Un essai de reconnexion d’une nouvelle JVM à la passerelle a aussi échoué sur un nom de statement préparé déjà utilisé ; l’option JDBC de la fixture est explicitée ci-dessus.

La fermeture du serveur de test avec un flux SSE ouvert retarde son arrêt gracieux ; Surefire termine alors le processus de test après 30 secondes. Les assertions sont toutes réussies et Maven retourne BUILD SUCCESS. Ce journal est conservé ; ce n’est pas une preuve de temps de rétablissement.

Le téléchargement automatique du navigateur Playwright n’a pas fourni une archive exploitable dans ce réseau. Un Chrome for Testing 145.0.7632.6 officiel a ensuite été récupéré, mais son lancement est refusé par la politique des sockets Unix (`socket() failed: Operation not permitted`). Ni un succès navigateur ni un résultat d’accessibilité n’en sont déduits.

## Reproduction sur le socle cible

Suivre README : démarrer PostgreSQL17 via Compose, exécuter `./mvnw -B verify`, construire l’interface et le JAR, lancer l’application locale puis `npm run test:e2e`. Garder **Flyway actif** et le pool livré de 8 connexions. Contrôler ensuite le volume, la concurrence de prise en charge et les flux révoqués. Exécuter la grille clavier/zoom/lecteur d’écran et V-13 séparément.

## Statut GitHub

Installation GitHub confirmée pendant cette session. Ses fonctions de publication et son skill n’ont cependant pas été exposés à l’exécution ; aucun dépôt distant ni run CI n’a donc été créé ou annoncé. Le ZIP et le bundle Git permettent de publier le code sans le recréer. Le lien réel du repository reste requis pour le livrable OpenClassrooms.
