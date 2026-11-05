# Journal des changements

## 1.1.0 (novembre 2026, branche `ameliorations-modeles` du fork d'Antoine)

Par rapport à la 1.0.0 (`main`) : modèles animés plus fluides et moins coûteux, et un système de mobs complet
(le rôle de MythicMobs). Rien n'est retiré, aucune commande ni méthode d'API existante ne change. Le détail de
chaque réglage est dans le README (sections « Modèles animés » et « Mobs »).

Ces changements ont été faits pour le serveur Babel d'Antoine (ville d'Orario : 2 185 animaux sur 1 083 points
d'apparition) et testés sur un vrai serveur Paper 1.21.4 avec un faux joueur (voir « Tests » plus bas).

### À savoir avant de mettre à jour

- **Les modèles animés ne s'affichent plus au-delà de 48 blocs** d'un joueur (`modeles.distance-affichage`).
  Mettre `0` pour les afficher toujours, comme en 1.0.
- Le passage d'une animation à l'autre se fait en fondu de 0,2 s (`modeles.fondu-secondes: 0` pour une coupure nette).
- Le pack généré donne aux parties des modèles animés une teinte réglée par le serveur (pour le rouge des dégâts) :
  regénérer le pack après la mise à jour.
- Nouvelles sections `modeles` et `mobs` dans `config.yml`, avec des valeurs par défaut : une ancienne
  configuration marche sans rien changer. Les mobs se coupent avec `mobs.actif: false`.
- Nouveaux dossiers `plugins/Vorace/mobs`, `zones` et `apparitions` (exemples fournis).

### Modèles animés

- Portée d'affichage : les entités d'un modèle n'existent que près des joueurs ; au-delà de la moitié de la
  portée, les images sont envoyées deux fois moins souvent.
- Fondus entre animations.
- Couches : `degat`, `attaque` et `clic` se jouent par-dessus l'animation en cours (un mob attaque en marchant).
- Tête qui suit le regard du mob (os `h_...`, `head` ou `tete`).
- Textures animées de Blockbench (le `.mcmeta` est écrit tout seul).
- Teinte rouge quand le mob est touché (1.21.4 et plus).
- Clés d'effet de Blockbench : sons, particules, scripts transmis par `VoraceModeleEffetEvent`.
- Os spéciaux : `tag_...` (nom du mob), position de n'importe quel os par l'API.
- Taille par modèle, gardée avec le monde.
- Molang dans les clés d'animation (opérations, conditions, `math.*`, `query.anim_time`).

### Mobs (nouveau)

- Cerveau : besoins (faim, soif, fatigue), émotions (peur, colère) ; chaque action reçoit une note et la meilleure
  l'emporte : repos, errer, manger, boire, dormir, rejoindre le troupeau, rentrer, fuir, attaquer.
- Caractères paisible, craintif, territorial, agressif ; horaires diurne, nocturne, toujours.
- Troupeaux, maison et laisse, zones (rectangle, cercle, polygone) avec mangeoire, abreuvoir et abri.
- Points d'apparition : population gardée autour d'un endroit, morts remplacés après un délai, rien n'apparaît sans
  joueur à moins de 96 blocs.
- Volants (oiseaux, papillons) : vrais mobs qu'on peut taper, posés au sol ou en hauteur, envol quand on approche,
  vol de point en point, atterrissage dans la laisse ; les oiseaux qui marchent (pigeons) trottinent et picorent ;
  volée : toute la bande part ensemble ; ils ne se posent pas à côté d'un joueur.
- Décors vivants (`type: decor`) : modèle sans entité (bain d'oiseaux, nid) qui joue `envol` quand on approche et
  `retour` 15 s après le départ du dernier joueur.
- Mob coincé : repart quelques blocs plus loin, ou reposé sur un sol libre s'il n'a de chemin nulle part.
- Un mob monté ou tenu en laisse obéit au joueur.
- Les joueurs en créatif font vivre et effraient les mobs comme les autres, sans jamais être attaqués.
- IA au choix : `vorace` (le cerveau), `jeu` (IA du jeu gardée en laisse : poissons, abeilles) ou `aucune`.
- Performances : un mob à plus de 64 blocs de tout joueur ne réfléchit pas ; les autres deux fois par seconde,
  chacun à son tour.

Corrections trouvées sur la vraie map pendant les essais :
- le sol d'un oiseau se cherche sous lui en sautant les barrières (un plafond de barrières invisibles était pris
  pour le sol : les oiseaux ne marchaient ni ne s'envolaient) ;
- les joueurs en créatif étaient ignorés, donc aucun mob ne « réfléchissait » autour d'eux ;
- un mob qui broutait loin de son troupeau faisait des allers-retours ; il broute maintenant près de lui ;
- une fuite sans chemin prend une autre direction, sinon le mob se calme.

### Commandes et API

- `/vorace mob [liste|invoquer <mob> [nombre]|info [début|*] [joueur]|apparitions|retirer|recharger]` ; `info` donne
  l'action, les besoins, la position, la distance parcourue et, pour un décor, l'animation, les sons et les
  particules joués ; depuis la console, le nom d'un joueur relève les mobs autour de lui.
- `/vorace modele taille <facteur>` et `/vorace modele anim <animation> [boucle] dessus`.
- API : `invoquerMob`, `mobVorace`, `actionMob`, `effrayerMob`, `jouerAnimationParDessus`, `tailleModele`,
  `regardModele`, `positionOs`.
- Événements : `VoraceMobActionEvent` (changement d'action d'un mob), `VoraceModeleEffetEvent` (clé d'effet, annulable).

### Tests

- `gradle tests` : nouveaux tests sans serveur `TestMobs` et `TestModelesAvances`.
- `outils/essai-serveur.sh` (workflow « Compilation ») : vrai serveur Paper avec un faux joueur (mineflayer) et un
  « zoo » de mobs.
- `outils/essai-orario.sh` (workflow « Essai Orario ») : propre au serveur d'Antoine. Il lance un serveur sur un
  extrait de sa map rangé dans la branche `essai-orario-donnees` et suit chaque animal. Sans cette branche, le
  workflow ne fait rien.

### Fichiers principaux

- `src/main/java/fr/vorace/mobs/` (nouveau) : `Mobs` (gestion et pilotage), `Cerveau` (choix des actions), `Vol`
  (volants), `Apparition`, `DefinitionMob`, `Zone`, `Geometrie`, `EcouteurMobs`.
- `src/main/java/fr/vorace/modeles/` : `Modeles`, `Animateur`, `Molang` et `Teinte` (nouveaux), `LecteurBBModel`,
  `GenerateurModeles`.
- `src/main/java/fr/vorace/api/` : nouvelles méthodes et les deux événements.

Les messages de commit décrivent chaque changement ; ceux marqués `[orario]` lançaient l'essai sur la map d'Antoine.
