## Vorace 0.6.2 : modèles animés

Mets tes projets Blockbench (`.bbmodel`, format « Modèle générique », avant du modèle vers le nord) dans `plugins/Vorace/modeles-animes/` : Vorace lit les os, les cubes, les textures et les animations et fabrique le pack tout seul. Aucun export, aucune conversion. Exemple créé au démarrage : un golem de rubis (repos, marche, attaque, salut, dégât, mort).

- **Modèles posés** : `/vorace modele poser <modèle> [animation]`, sauvegardés avec le monde, hitbox à la taille du modèle, animation `clic` au clic droit.
- **Modèles sur des mobs** : `/vorace modele sur <modèle>` ou `/vorace modele invoquer <modèle> [mob]`. Le mob devient invisible, le modèle le suit et joue tout seul `repos`, `marche`, `course`, `attaque`, `degat` et `mort` (ou les noms anglais de ModelEngine : idle, walk, run, attack, hurt, death).
- Animations Blockbench : rotation, position, échelle, interpolation linéaire, Catmull-Rom et en escalier, boucle / une fois / figée.
- Os imbriqués, pivots, cubes tournés dans tous les sens, grands modèles : tout est géré.
- Une entité d'affichage par os, animée avec l'interpolation du jeu (fluide, réglable avec `modeles.intervalle-tics`).
- `/vorace modele [liste|poser|anim|stop|info|sur|invoquer|retirer]`. `info` montre l'état réel du modèle le plus proche (animation en cours, temps, parties, erreur éventuelle). Les commandes `poser`, `anim`, `stop`, `info` et `retirer` marchent aussi depuis la console (autour du point d'apparition).
- API : `VoraceAPI.poserModele`, `attacherModele`, `jouerAnimation`, `modeleAnime`, `retirerModele`.

Corrigé aussi : les balises `<glyphe:…>` et `<decalage:…>` des glyphes (0.5) ne se chargeaient pas sur les versions récentes de Paper.

Chaque version est maintenant essayée automatiquement sur un vrai serveur Paper 1.21.4 avec un faux joueur (client 1.21.4, et client 26.1 via ViaVersion) : le golem d'exemple doit s'animer.

Pas encore pris en charge : expressions Molang dans les animations, maillages (meshes), teinte rouge des dégâts.

Rien à changer dans tes fichiers : remplace le .jar et redémarre.

> **Code écrit par une IA.** Vorace est développé par Claude (l'IA d'Anthropic), à la demande et sous la direction de ORA, qui teste et décide de ce qui est ajouté. Le code est ouvert pour que chacun puisse le relire, le corriger et l'améliorer.
