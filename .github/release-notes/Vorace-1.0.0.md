## Vorace 1.0.0 : la première version complète

Vorace est un plugin **libre et gratuit** pour Paper (Minecraft **1.21 à 26.x**). Il remplace en un seul plugin ce que font Nexo, ItemsAdder, ModelEngine, ProtocolLib et MythicLib, en plus simple.

**Tape `/vorace` : tout se fait avec des menus**, aux couleurs de Vorace (fond dessiné violet-rose). Chaque bouton dit ce que fait chaque clic. Les commandes restent disponibles (`/vorace aide`).

### Ce que contient la 1.0

- **Packs de ressources** : fusion de tous les packs du serveur (les tiens, ModelEngine, MythicMobs...), hébergement intégré, envoi automatique aux joueurs, reconstruction quand un fichier change.
- **Objets personnalisés** : texture ou modèle 3D, propriétés, actions au clic, recettes, mise à jour des objets déjà donnés.
- **Meubles** : modèles 3D posables avec hitbox, sièges, lumières et coffres.
- **Blocs personnalisés** : minerais et décorations, avec outils, vitesse de minage et butin.
- **Glyphes et emojis** : dans le chat, les panneaux, le Tab, les titres et les menus, plus les fonds de menus.
- **Modèles animés** : les .bbmodel de Blockbench importés directement, posés ou portés par un mob, animations automatiques ou à la demande, visibles par tous ou par un seul joueur.
- **Couche paquets** (rôle de ProtocolLib) et **API** pour les autres plugins (dépendance Maven via JitPack).
- **Conversion** : reprend les configurations et images de Nexo, Oraxen, ItemsAdder et ModelEngine (jamais leur code), et met à jour les objets déjà dans les inventaires.
- **Menus** : objets, meubles, blocs, glyphes, modèles animés, pack et joueurs, conversion et outils avancés. Les joueurs sans droits ont un petit menu (recevoir le pack, emojis).

Chaque version est essayée automatiquement sur un vrai serveur Paper 1.21.4, avec un client 1.21.4 et un client 26.1, et compilée pour 1.21.1, 1.21.4, 1.21.11 et 26.2.

### Depuis la 0.9.1

Rien ne change dans le plugin : la 0.9.1 a été validée telle quelle et devient la 1.0.0.

> **Code écrit par une IA.** Vorace est développé par Claude (l'IA d'Anthropic), à la demande et sous la direction de ORA, qui teste et décide de ce qui est ajouté. Le code est ouvert pour que chacun puisse le relire, le corriger et l'améliorer.
>
> Vorace est écrit de zéro. Il ne contient aucun code d'un autre plugin.
