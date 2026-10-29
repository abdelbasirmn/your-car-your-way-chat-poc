# Scénario de démonstration

## 1. Préparer

Lancer suivant le README. Utiliser des profils/navigateurs distincts pour Alice, Support et Bob : des onglets ordinaires partagent les mêmes cookies. Les données sont fictives.

## 2. Échange et affectation

Alice crée une conversation. L’agent Support actualise sa liste, prend cette demande en charge et ouvre l’échange. Envoyer un texte dans chaque sens. Les métadonnées distinguent Client/Support et « Enregistré ». Aucun statut « lu » n’est affiché.

## 3. Confidentialité

Bob ne voit pas la conversation d’Alice. Depuis sa session, accéder à l’URL API `/api/v1/conversations/IDENTIFIANT/messages` d’Alice : résultat 404, sans messages. L’autre compte support ne peut pas lire une conversation affectée au premier. Libérer l’affectation : l’ancien agent perd l’accès ; la demande peut être reprise.

## 4. Coupure

Sur Alice, « Suspendre / reprendre la réception ». Le support envoie plusieurs messages. Reprendre la réception : ils sont rattrapés sans rechargement complet et sans doublons. Cette commande coupe le flux SSE ; un test réseau complet peut être effectué avec les outils du navigateur en passant hors ligne puis en ligne.

## 5. Réponse perdue

Le test Playwright prévu intercepte la réponse d’un POST après traitement par le serveur, puis la perd. « Réessayer le même envoi » réutilise le clientMessageId initial et retrouve le message. Il ne s’agit pas d’un second envoi indépendant.

## 6. Persistance

Après un échange, noter le texte et l’identifiant de conversation. Redémarrer uniquement le backend : `docker compose restart app`. Se reconnecter si nécessaire et retrouver les messages. Exécuter `docker compose down`, puis `docker compose up -d` : le volume de base est conservé. Ne pas utiliser `down -v` dans ce scénario.

## 7. Clavier et contenu inerte

Parcourir connexion, liste, historique et formulaire avec Tab/Shift+Tab/Entrée. Le focus est visible. La réception ne le déplace pas. L’historique a une étiquette et la zone d’annonce est distincte des anciennes pages. Envoyer `<script>window.pwned=true</script>` : texte affiché, aucune exécution. Zoomer à 200 % et vérifier le petit écran ; un audit RGAA complet et un test lecteur d’écran restent distincts de ces essais.

## 8. Clôture

Clôturer : l’historique reste lisible et les nouveaux envois sont refusés côté serveur. Une requête If-Match périmée renvoie 412 au lieu d’écraser un changement.
