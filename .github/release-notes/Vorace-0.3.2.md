## Vorace 0.3.2 : meubles

Un meuble est un objet Vorace avec une section `meuble:`. Quatre exemples sont créés au démarrage (chaise, lampe, caisse de rangement, grande table), avec leurs recettes.

- **Pose** : clic droit sur un bloc, le meuble se tourne vers toi (4, 8, 16 directions ou rotation libre), au sol, contre un mur ou au plafond.
- **Cassage** : clic gauche, l'objet est rendu. Les protections du serveur (WorldGuard, claims...) sont respectées.
- **Hitbox** solide sur plusieurs blocs, ou hitbox non solide de la taille voulue (tapis, plantes).
- **Sièges**, **lumière**, **coffre intégré** (le contenu reste dans le meuble), **commandes** au clic droit, sons, taille, décalage.
- Les meubles posés se mettent à jour quand tu modifies leur objet.
- `/vorace meuble [liste|poser|info|retirer|rafraichir]` ; `poser` pose le meuble que tu regardes même dans une zone où on ne peut pas construire (hub).
- API : `VoraceAPI.poserMeuble`, `VoraceAPI.meuble`, `VoraceAPI.retirerMeuble`, événements `VoraceMeublePoseEvent` et `VoraceMeubleCasseEvent`.

Rien à changer dans tes fichiers : remplace le .jar et redémarre. Le fichier `objets/exemple-meubles.yml` et ses modèles sont ajoutés une seule fois (supprime-les si tu n'en veux pas, ils ne reviendront pas).

> **Code écrit par une IA.** Vorace est développé par Claude (l'IA d'Anthropic), à la demande et sous la direction de ORA, qui teste et décide de ce qui est ajouté. Le code est ouvert pour que chacun puisse le relire, le corriger et l'améliorer.
