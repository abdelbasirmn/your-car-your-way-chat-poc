# Traçabilité du sous-ensemble tchat

Les identifiants proviennent des documents des étapes 1 et 3. Les propositions de support humain, affectation et clôture restent des choix de démonstration ; leur implémentation ne vaut pas validation métier de la cible. Les exigences hors tchat ne sont pas déclarées couvertes.

| Exigence / story / règle | Spécification / décision | Code et donnée | Vérification réalisée ou restant à faire |
| --- | --- | --- | --- |
| EF-16 ; US-16.CA01–02 ; RM13–14 | ET-07 ; ADR-05/06 ; V-02 | create, readable, session, conversation | Test d’intégration : refus de Bob et support non affecté, puis accès de l’agent affecté |
| EF-17 ; US-17.CA01–03 ; RM14–15 | ET-08/14 ; ADR-03/04/05 ; V-03/08 | send transactionnel, message/event, affichage Angular par interpolation | Tests validation, auteur de session, lecture SSE et stockage script comme texte réussis ; rendu inerte dans le navigateur prévu, lancement bloqué |
| EF-18 ; US-18.CA01–03 ; RM15 | ET-09 ; ADR-05 ; V-04/05/06 | clientMessageId, unicité SQL, events, history et catchUp | Réessai même clé/contenu, conflit autre contenu, reprise SSE après curseur et persistance après redémarrage réussis sur fixture WASM ; parcours UI de coupure restant |
| EF-19 ; US-19.CA01–02 ; RM14 | ET-07 ; V-02/10 | claim/release, assigned_agent_id | Accès refusé avant affectation et après libération ; autre agent refusé. Course de deux agents à vérifier sur natif |
| EF-20 ; US-20.CA01–02 ; RM14 | ET-07/08 ; V-10 | close, version, status CLOSED | Nouvel envoi refusé, historique conservé, réessai déjà enregistré accepté après clôture ; 412 prévu pour version périmée |
| EF-27 ; US-05.CA03 / US-27 ; RM14/18 | ET-05/07/14 ; ADR-06 ; V-09/10 | SecurityConfig, session JDBC, contrôle par ressource | Connexion réelle, mauvais mot de passe, POST sans CSRF, déconnexion et session expirée refusés ; fermeture d’un flux déjà ouvert après révocation à vérifier en natif |
| EF-23 ; US-23 ; RM17 | ET-12 ; ADR-02 ; V-12 | Noms de champs/boutons, focus, contrôle clavier natif | Code inspecté et compilé ; scénario Tab/Entrée fourni mais non exécuté, navigateur bloqué |
| EF-24/25 ; US-24/25 ; RM17 | ET-12 ; V-12 | Styles adaptatifs, focus visible, global-error, aria-live séparé | Dispositifs présents ; zoom, contrastes mesurés, lecteurs d’écran et essais PSH restent à réaliser |
| EF-30/31 ; US-30/31 ; RM20 | ET-10/13/17 ; V-11/13 | history 50/max100, older, flux fermés, aucun média | Pagination de 105 messages vérifiée sans perte ; repère visuel, réseau limité et 10 conversations simultanées non mesurés |
| RM15 : ordre stable et atomicité | ET-08 ; ADR-04 ; V-07 | FOR UPDATE, next, contraintes uniques, transaction Spring | 12 appels concurrents depuis 4 threads réussissent avec pool1 ; preuve de verrous/concurrence PostgreSQL17 natif restante |
| Installation et données versionnées | ET-16/18 ; V-01 | POM, lockfile, wrapper, Flyway V1, Compose, README | Builds réussis, SQL V1 exécuté ; Flyway natif et Compose non exécutés dans cet environnement |

## Exclusions maintenues

Pas de paiement/PSP, API d’agence/identité machine, réservation réelle, inscription, suppression du compte, courriel, présence ou pièce jointe. Ces domaines restent décrits dans l’architecture cible. Aucun tiers supplémentaire n’est introduit dans la démonstration du tchat.

## Lecture de la couverture

Les tests HTTP/service apportent des preuves sur la fixture décrite dans VERIFICATIONS. Les tests Playwright livrés couvrent le scénario de deux participants, le contenu inerte, une commande clavier et la perte de réponse HTTP ; leurs assertions n’ont pas pu s’exécuter ici. Aucune conformité réglementaire, performance internationale ou haute disponibilité n’est affirmée.
