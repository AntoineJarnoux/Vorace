## Vorace 0.7.0 : couche paquets et API commune

**Couche paquets (le rôle de ProtocolLib), sans plugin en plus.** Vorace se glisse dans la connexion de chaque joueur : tes plugins peuvent lire, modifier, annuler ou envoyer n'importe quel paquet du jeu. Rien n'est fait tant que personne n'écoute.

```java
VoraceAPI.ecouterPaquets(this, Sens.SORTANT, p -> p.ecrire("dayTime", 6000L), "ClientboundSetTimePacket");
```

- `Paquet` : `lire`, `texte`, `ecrire` (les paquets « record » du jeu sont recréés tout seuls), `remplacer`, `annuler`, `champs`.
- Les lots de paquets passent un par un dans les écoutes. Une écoute qui plante ne bloque jamais le paquet. Les écoutes d'un plugin s'arrêtent avec lui.
- `VoraceAPI.envoyerPaquet` et `recevoirPaquet`.
- `/vorace paquets journal <joueur> [filtre]` : les paquets en direct avec tous leurs champs (pour trouver les noms). `/vorace paquets stats [joueur]` : les plus fréquents. `/vorace paquets ecoutes` : qui écoute quoi.
- `paquets.actif: false` dans la config pour que Vorace ne touche pas aux connexions.

**API commune.**
- Vorace s'utilise comme dépendance avec JitPack : `compileOnly("com.github.ORA-KUN:Vorace:Vorace-0.7.0")` (dépôt `https://jitpack.io`).
- Nouveaux événements : `VoraceBlocPoseEvent`, `VoraceObjetUtiliseEvent` (commandes modifiables), `VoraceModeleAnimationEvent` (animation modifiable). Tous annulables, en plus de ceux des meubles, des blocs cassés et du pack.

**Modèles visibles par un seul joueur** : `/vorace modele prive [joueur]`, `/vorace modele public`, `VoraceAPI.modeleVisiblePour(base, joueurs...)`.

Chaque version est essayée automatiquement sur un vrai serveur Paper 1.21.4 avec un faux joueur (client 1.21.4, et 26.1 via ViaVersion) : le golem doit s'animer, l'heure reçue doit être modifiée par la couche paquets, un message doit être bloqué et un autre passer.

Rien à changer dans tes fichiers : remplace le .jar et redémarre.

> **Code écrit par une IA.** Vorace est développé par Claude (l'IA d'Anthropic), à la demande et sous la direction de ORA, qui teste et décide de ce qui est ajouté. Le code est ouvert pour que chacun puisse le relire, le corriger et l'améliorer.
