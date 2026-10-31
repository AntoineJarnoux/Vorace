Étape 2 de **Vorace** : les objets personnalisés.

> **Code écrit par une IA.** Vorace est développé par Claude (l'IA d'Anthropic), à la demande et sous la direction de Jules (ORA-KUN), qui teste et décide de ce qui est ajouté. Le code est ouvert pour que chacun puisse le relire, le corriger et l'améliorer.

Cette version a été compilée et testée automatiquement (génération du pack des objets pour 1.21, 1.21.2 et 1.21.4+), mais pas encore essayée en jeu : les retours sont bienvenus dans les Issues.

## Nouveautés
- **Objets personnalisés** décrits dans `plugins/Vorace/objets/*.yml` (exemple créé au premier démarrage : un rubis et une épée de rubis).
- **Apparence** : image (modèle plat généré) ou **modèle 3D Blockbench** (export « Java Block/Item »), textures retrouvées et reliées automatiquement. Le bon système selon la version : `item_model` (1.21.2+) ou `custom_model_data` (1.21 / 1.21.1).
- **Propriétés** : nom et description en MiniMessage, enchantements, brillance, incassable, attributs (dégâts, vitesse, vie...), couleur, taille de pile, durabilité, infobulle allégée.
- **Actions au clic** droit ou gauche : commandes console ou joueur, délai, objet consommé.
- **Recettes** avec forme, sans forme ou au four, avec des objets Vorace comme ingrédients.
- **Mise à jour automatique** des objets déjà dans les inventaires quand tu modifies leur fichier.
- Commandes `/vorace give`, `/vorace objets` (menu), `/vorace maj`. API `VoraceAPI` pour les autres plugins.
- Les objets et leurs images sont surveillés : une modification reconstruit le pack toute seule.

## Mise à jour depuis la 0.1.0
Remplace le .jar dans `plugins/` et redémarre. Ta config est gardée ; ajoute si tu veux la section `objets:` (voir le README).
