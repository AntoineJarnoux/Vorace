## Vorace 0.8.0 : conversion depuis Nexo, Oraxen, ItemsAdder et ModelEngine

**Une commande pour passer à Vorace.** `/vorace convertir` montre les plugins trouvés dans `plugins/` (un clic pour convertir), `/vorace convertir tout` les fait tous. Vorace lit seulement leurs fichiers de configuration et leurs images, jamais leur code, et ne touche à rien chez eux : tu peux les garder le temps de vérifier.

- **Nexo / Oraxen** : objets (nom, description, texture ou modèle, enchantements, attributs, couleur, durabilité, commandes), modèles d'objets, meubles (hitbox, sièges, lumière, rotation, coffre, sons), blocs (textures par face, dureté, outil, butin, Fortune, Toucher de soie), glyphes, recettes et sons du pack.
- **ItemsAdder** : objets (`resource:` et `graphics:`), variantes, meubles (hitbox, siège, lumière, coffre), blocs, butin des blocs, images de police et recettes.
- **ModelEngine** : les modèles .bbmodel arrivent dans `modeles-animes/modelengine/`. Les os `hitbox` et `shadow` sont cachés, et l'os `hitbox` donne la taille de la hitbox.
- Les textures et modèles sont recopiés et réécrits pour Vorace : plus besoin du pack de l'autre plugin.
- Un objet qui existe déjà dans Vorace n'est jamais écrasé (le converti prend un préfixe, par exemple `nexo_rubis`).
- Ce qui n'a pas pu être repris est listé dans `plugins/Vorace/conversion/rapport-<plugin>.txt`.
- Les objets Nexo et Oraxen **déjà dans les inventaires** sont remplacés par leur version Vorace à la connexion (ou avec `/vorace maj`).

Chaque version est essayée automatiquement sur un vrai serveur Paper 1.21.4 : modèles animés, couche paquets, et maintenant la conversion d'exemples Nexo, ItemsAdder et ModelEngine, qui doivent se charger sans aucune alerte.

Rien à changer dans tes fichiers : remplace le .jar et redémarre.

> **Code écrit par une IA.** Vorace est développé par Claude (l'IA d'Anthropic), à la demande et sous la direction de ORA, qui teste et décide de ce qui est ajouté. Le code est ouvert pour que chacun puisse le relire, le corriger et l'améliorer.
