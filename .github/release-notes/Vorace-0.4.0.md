## Vorace 0.4.0 : blocs personnalisés

Un bloc est un objet Vorace avec une section `bloc:`. Deux exemples sont créés au démarrage : un minerai de rubis (généré sous terre dans les nouveaux chunks, lâche des rubis) et un bloc de rubis (recette : 9 rubis).

- **Vrais blocs pleins**, sans entité : jusqu'à 550 blocs, une texture pour tout le bloc, une par face, ou un modèle Blockbench.
- **Minage calculé par le serveur** : vitesse selon l'outil et la dureté, Efficacité, Célérité, Fatigue, fissures visibles par tous les joueurs proches.
- **Butin** configurable (quantités, chances), Fortune, Toucher de soie, expérience, usure de l'outil, niveau d'outil minimum. Les protections du serveur sont respectées.
- Redstone, pistons, explosions et blocs voisins ne changent jamais l'apparence d'un bloc Vorace. Vorace active seul le réglage Paper `block-updates.disable-noteblock-updates` (pris en compte au redémarrage suivant).
- **Génération** de filons : mondes, hauteur, nombre et taille des filons, blocs remplacés.
- `/vorace bloc [liste|poser|casser|info]` ; `poser` et `casser` marchent même dans une zone où on ne peut pas construire (hub).
- API : `VoraceAPI.bloc`, `VoraceAPI.poserBloc`, événement `VoraceBlocCasseEvent` (butin modifiable, annulable).

Rien à changer dans tes fichiers : remplace le .jar et redémarre. Le fichier `objets/exemple-blocs.yml` et ses textures sont ajoutés une seule fois.

Limite connue (la même qu'avec les autres plugins de blocs) : les blocs Vorace utilisent les états du bloc à note, donc les blocs à note normaux ne changent plus d'instrument selon le bloc posé dessous.

> **Code écrit par une IA.** Vorace est développé par Claude (l'IA d'Anthropic), à la demande et sous la direction de ORA, qui teste et décide de ce qui est ajouté. Le code est ouvert pour que chacun puisse le relire, le corriger et l'améliorer.
