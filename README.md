# Vorace

Plugin **libre et gratuit** pour serveurs Paper (Minecraft **1.21 à 26.x**) : packs de ressources,
objets, meubles, blocs et glyphes personnalisés. Le but est de pouvoir tout faire sans plugin payant,
et de corriger ou améliorer le plugin au fur et à mesure des besoins.

> **Code écrit par une IA.** Vorace est développé par Claude (l'IA d'Anthropic), à la demande et sous la direction de ORA, qui teste et décide de ce qui est ajouté. Le code est ouvert pour que chacun puisse le relire, le corriger et l'améliorer.
>
> Vorace est écrit de zéro. Il ne contient aucun code d'un autre plugin.

## Ce que fait Vorace

### Packs de ressources (0.1)

- **Fusionne tous les packs** du serveur en un seul : tes packs (dossiers ou .zip), celui de
  **ModelEngine**, celui de **MythicMobs**... Les fichiers partagés sont fusionnés intelligemment :
  `sounds.json`, langues, polices (`font`), atlas, modèles avec `overrides` (avant 1.21.4), définitions
  d'objets `items/*.json` (1.21.4+), overlays du `pack.mcmeta`.
- **Héberge le pack sur le port du jeu** : aucun port à ouvrir, aucun site à payer. Le joueur télécharge
  le pack à la même adresse que celle qu'il utilise pour se connecter.
  (Autres modes possibles : un port dédié, ou une adresse externe.)
- **Envoie le pack** aux joueurs à leur arrivée, obligatoire ou non, et suit leur réponse
  (accepté, chargé, refusé, échec).
- **Se met à jour tout seul** : quand un pack source change (par exemple après `/meg reload`),
  le pack est reconstruit automatiquement.
- Écrit le bon `pack.mcmeta` pour la version du serveur (ancien format `pack_format` ou nouveau
  `min_format` / `max_format` depuis 1.21.9).
- Écrit un **rapport** de chaque fusion : `plugins/Vorace/pack/genere/rapport.txt`.

### Objets personnalisés (0.2)

Les objets se décrivent dans `plugins/Vorace/objets/*.yml` (un fichier d'exemple est créé au premier démarrage) :

```yaml
epee_rubis:
  materiau: DIAMOND_SWORD
  nom: "<red><bold>Épée de rubis"
  description: ["<gray>Forgée avec des rubis."]
  texture: exemple/epee_rubis      # plugins/Vorace/ressources/textures/exemple/epee_rubis.png
  # modele: couronne               # ou un modèle 3D Blockbench : ressources/modeles/couronne.json
  enchantements: { sharpness: 3 }
  attributs: { degats: 9, vitesse-attaque: -2.4 }
  incassable: true
  commandes:
    clic-droit: ["console: effect give %joueur% speed 3 2"]
    delai-ticks: 100
  recette:
    type: forme
    forme: [" R ", " R ", " B "]
    ingredients: { R: "vorace:rubis", B: STICK }
```

- **Apparence** : une image (modèle plat généré, tenu comme une épée pour les armes et outils) ou un
  modèle 3D exporté de Blockbench (« Java Block/Item ») ; ses textures sont retrouvées et reliées toutes seules.
  Vorace écrit le pack selon la version : `item_model` (1.21.2+) ou `custom_model_data` (1.21 / 1.21.1).
- **Propriétés** : nom et description en MiniMessage, enchantements, brillance, incassable, attributs,
  couleur (cuir, potions), taille de pile, durabilité, éléments cachés de l'infobulle.
- **Actions** : commandes au clic droit ou gauche (console ou joueur), délai entre deux utilisations, objet consommé.
- **Recettes** : avec forme, sans forme, ou au four ; les ingrédients peuvent être d'autres objets Vorace.
- **Mise à jour** : quand tu modifies un objet, ceux déjà dans les inventaires sont mis à jour à la connexion.
- `/vorace give <joueur> <objet> [quantité]`, `/vorace objets` (menu de tous les objets), `/vorace maj`.
- API : `VoraceAPI.objet("epee_rubis", 1)`, `VoraceAPI.id(itemStack)`.

### Meubles (0.3)

Un meuble est un objet Vorace avec une section `meuble:` (exemples créés au premier démarrage :
chaise, lampe, caisse de rangement, grande table) :

```yaml
chaise:
  materiau: PAPER
  nom: "<#c8935a>Chaise en bois"
  modele: exemple/chaise          # modèle Blockbench, l'avant est la face « north »
  meuble:
    sieges: ["0, 0.6, 0"]         # x, hauteur de l'assise, z
    rotation: 8                   # 4, 8, 16 ou libre
```

- **Poser** : clic droit sur un bloc avec l'objet, le meuble se tourne vers toi. Au sol, contre un mur ou au plafond (`pose: [sol, mur, plafond]`).
- **Casser** : clic gauche, l'objet est rendu (sauf en créatif). Les protections (WorldGuard, claims...) sont respectées.
- **Hitbox** : des blocs solides sur plusieurs cases (`hitbox: ["0,0,0", "1,0,0"]`, dans le repère du modèle Blockbench),
  ou une hitbox non solide de la taille voulue (`hitbox: { largeur: 1, hauteur: 0.2 }`) pour les tapis et plantes.
- **Sièges**, **lumière** (`lumiere: 15`, `luminosite: 15` pour un modèle toujours éclairé), **coffre** intégré
  (`stockage: 3` lignes, le contenu reste dans le meuble), **commandes** au clic droit, sons, taille, décalage.
- Rien à configurer côté pack : le meuble utilise le modèle de son objet. Quand tu modifies un meuble,
  ceux déjà posés se mettent à jour.
- `/vorace meuble` (liste), `/vorace meuble poser <meuble>` (là où tu regardes, même dans une zone où on ne peut pas construire, comme un hub), `/vorace meuble info|retirer [rayon]` (le plus proche), `/vorace meuble rafraichir`.
- API : `VoraceAPI.poserMeuble(id, bloc, yaw)`, `VoraceAPI.meuble(entite)`, `VoraceAPI.retirerMeuble(entite)`,
  événements `VoraceMeublePoseEvent` et `VoraceMeubleCasseEvent` (annulables).

### Blocs (0.4)

Un bloc est un objet Vorace avec une section `bloc:` (exemples créés au premier démarrage :
minerai de rubis et bloc de rubis) :

```yaml
minerai_rubis:
  materiau: PAPER
  nom: "<#e8284a>Minerai de rubis"
  bloc:
    textures: exemple/minerai_rubis     # ou { haut: a, bas: b, cotes: c }, ou modele: <Blockbench>
    durete: 3                           # comme dans le jeu (pierre 1.5, obsidienne 50)
    outil: pioche
    niveau: fer                         # outil minimum pour avoir le butin
    butin: ["vorace:rubis 1-2"]         # objet, quantité, chance en %
    experience: "3-7"
    generation: { mondes: [world], hauteur: "-64, 32", filons: 4, taille: 5 }
```

- **Vrais blocs pleins** : 550 blocs possibles, sans entité, aussi légers qu'un bloc du jeu. L'objet en main montre le bloc en 3D.
- **Minage calculé par le serveur** : vitesse selon l'outil, Efficacité, Célérité et Fatigue, fissures visibles par tous.
  Butin avec Fortune et Toucher de soie, expérience, usure de l'outil. Les protections sont respectées.
- Un signal de redstone, un piston, une explosion ou un bloc posé au-dessus ne change jamais son apparence.
  Vorace active tout seul le réglage Paper `block-updates.disable-noteblock-updates` (prend effet au redémarrage suivant).
- **Génération** de filons dans les nouveaux chunks.
- `/vorace bloc` (liste), `/vorace bloc poser <bloc>` et `/vorace bloc casser` (là où tu regardes, même en zone protégée), `/vorace bloc info`.
- API : `VoraceAPI.bloc(bloc)`, `VoraceAPI.poserBloc(id, bloc)`, événement `VoraceBlocCasseEvent` (butin modifiable, annulable).

Limite connue (la même qu'avec les autres plugins de blocs) : les blocs Vorace utilisent les états du bloc à note.
Avec le réglage Paper activé, les blocs à note normaux ne changent plus d'instrument selon le bloc posé dessous.

### Glyphes, emojis et fonds de menus (0.5)

Un glyphe est une image qui s'écrit comme une lettre. Ils se créent dans `plugins/Vorace/glyphes/`
(exemples créés au premier démarrage : cœur, étoile, sourire, feu et un fond de coffre) :

```yaml
coeur:
  texture: exemple/glyphes/coeur     # image dans ressources/textures/
  raccourcis: ["<3"]                 # « :coeur: » est ajouté tout seul
  # permission: vip.emojis

fond_coffre:
  texture: exemple/glyphes/fond_coffre   # 176 pixels de large
  fond-menu: true                        # hauteur et position calculées toutes seules
```

- **Emojis dans le chat et sur les panneaux** : `:coeur:` devient l'image. Les raccourcis apparaissent dans les
  suggestions du chat (touche Tab), `/emojis` les montre à tous les joueurs. Permission par emoji possible.
- **Partout** : les glyphes utilisent la police normale du jeu, donc ils marchent dans les noms d'objets, le lore,
  le Tab, le tableau de scores, les titres, les bossbars et les menus, même avec des plugins qui n'écrivent que du texte simple.
- Balises MiniMessage dans les objets et messages Vorace : `<glyphe:coeur>`, `<decalage:-8>` (décalage en pixels).
- **Fonds de menus** : `VoraceAPI.titreMenu("fond_coffre", titre)` place l'image en haut à gauche de la fenêtre
  (la largeur réelle de l'image est calculée pour que le titre reste à sa place). Aperçu : `/vorace glyphe menu fond_coffre`.
- PlaceholderAPI (facultatif) : `%vorace_glyphe_coeur%`, `%vorace_decalage_-8%`.
- `/vorace glyphe liste` (clic sur un glyphe : copie son caractère), `/vorace glyphe menu <fond> [lignes]`.
- API : `VoraceAPI.glyphe(id)`, `glypheComposant(id)`, `decalage(n)`, `titreMenu(fond, titre)`, `emojis(joueur, texte)`.

Chaque glyphe garde son caractère d'un redémarrage à l'autre (`donnees/glyphes.txt`), de U+EA00 à U+F6FF.

### Modèles animés (0.6)

Mets tes projets Blockbench (`.bbmodel`, format « Modèle générique », l'avant du modèle tourné vers le nord)
dans `plugins/Vorace/modeles-animes/`. Rien d'autre à faire : Vorace lit les os, les cubes, les textures
intégrées et les animations, et fabrique le pack tout seul. Exemple créé au premier démarrage : `golem_rubis`
(animations repos, marche, attaque, salut, degat, mort).

- **Modèles posés** : `/vorace modele poser golem_rubis [animation]`, sauvegardés avec le monde, avec une hitbox
  à la taille du modèle. Clic droit dessus : animation `clic` (ou `interact`) si elle existe.
- **Modèles sur des mobs** : `/vorace modele sur <modèle>` (le mob regardé) ou `/vorace modele invoquer <modèle> [mob]`.
  Le mob devient invisible et le modèle suit ses mouvements. Animations jouées toutes seules d'après leur nom :
  `idle`/`repos`, `walk`/`marche`, `run`/`course`, `attack`/`attaque`, `hurt`/`degat`, `death`/`mort`.
- Animations Blockbench : rotation, position, échelle ; interpolation linéaire, Catmull-Rom et en escalier ;
  boucle, une fois ou figée à la fin. Les os imbriqués, les pivots et les cubes tournés dans tous les sens sont gérés
  (un cube tourné sur plusieurs axes devient une partie à lui seul), et les grands modèles sont réduits puis
  agrandis à l'affichage pour dépasser la limite de taille des modèles d'objets.
- Chaque os est une entité d'affichage (pas de mob caché ni d'armor stand) : animations fluides grâce à
  l'interpolation du jeu, réglable avec `modeles.intervalle-tics`.
- `/vorace modele [liste|poser|anim <animation> [boucle]|stop|sur|invoquer|retirer]`.
- API : `VoraceAPI.poserModele`, `attacherModele`, `jouerAnimation`, `modeleAnime`, `retirerModele`.

Pas encore pris en charge : expressions Molang dans les animations (seulement des nombres), maillages (meshes),
teinte rouge des dégâts.

## Installation

1. Mets `Vorace-x.y.z.jar` dans `plugins/` et redémarre le serveur.
2. Dépose tes packs (dossiers ou .zip) dans `plugins/Vorace/pack/sources/`.
   Ceux de ModelEngine et MythicMobs sont trouvés tout seuls.
3. `/vorace reload`, puis reconnecte-toi : le pack est proposé à l'arrivée.

Si tu viens d'un autre plugin qui envoie déjà un pack (Nexo, ItemsAdder, Oraxen...), retire-le ou
désactive son envoi, sinon les joueurs recevront deux packs.

## Commandes

| Commande | Rôle |
|---|---|
| `/vorace reload` | recharge `config.yml` et reconstruit le pack |
| `/vorace envoyer [joueur\|tous]` | envoie le pack |
| `/vorace info` | état du pack, de l'hébergement, et ton adresse de téléchargement |
| `/vorace sources` | packs trouvés, dans l'ordre de fusion |
| `/vorace give <joueur> <objet> [quantité]` | donne un objet Vorace |
| `/vorace objets` | menu de tous les objets (clic = 1, Maj + clic = une pile) |
| `/vorace maj` | met à jour les objets Vorace dans les inventaires des joueurs connectés |
| `/vorace meuble [liste\|poser\|info\|retirer\|rafraichir]` | gestion des meubles posés |
| `/vorace bloc [liste\|poser\|casser\|info]` | blocs personnalisés |
| `/vorace glyphe [liste\|menu <fond>]` | glyphes et aperçu des fonds de menus |
| `/vorace modele [liste\|poser\|anim\|stop\|sur\|invoquer\|retirer]` | modèles animés |
| `/emojis` | (tous les joueurs) liste des emojis du chat |
| `/vorace pack` | (tous les joueurs) recevoir à nouveau le pack |

Permissions : `vorace.admin` (op par défaut), `vorace.pack` et `vorace.emojis` (tout le monde).

## Ordre de fusion

Les sources de `config.yml > pack.sources` vont de la **moins** prioritaire à la **plus** prioritaire.
Dans un dossier `.../*`, l'ordre est alphabétique : préfixe les noms pour le choisir
(`10-base.zip`, `20-smashduels.zip`, `90-retouches/`).

| Fichier présent dans deux packs | Résultat |
|---|---|
| `sounds.json`, `lang/*.json` | clés réunies, la source prioritaire gagne sur une même clé |
| `font/*.json` | `providers` réunis, ceux de la source prioritaire d'abord |
| `atlases/*.json` | `sources` réunies |
| `models/**.json` avec `overrides` | overrides réunis et triés par `custom_model_data` |
| `items/*.json` (1.21.4+) | `range_dispatch` / `select` réunis ; un modèle simple devient le repli |
| `pack.mcmeta` | réécrit par Vorace ; overlays, langues et filtres conservés |
| tout le reste (textures, sons...) | la source prioritaire gagne, noté dans le rapport |

## Hébergement : dépannage

Au démarrage, Vorace se télécharge lui-même le pack pour vérifier : la console affiche
`Test de l'hébergement : OK`. Si un joueur voit « échec du téléchargement » :

- `/vorace info` montre l'adresse donnée au joueur : elle doit être celle du serveur.
- Si le serveur est derrière un proxy (Velocity, BungeeCord, TCPShield...), le port du jeu ne laisse
  pas passer le web : mets `hebergement.mode: port` avec un port ouvert, ou `externe`.
- Si l'adresse tapée par les joueurs n'est pas la bonne pour télécharger, remplis
  `hebergement.adresse-publique` (et `port-public`).

## Pour les autres plugins

- `fr.vorace.api.VoracePackPretEvent` : le pack vient d'être (re)construit.
- `fr.vorace.api.VoracePackChargeEvent` : un joueur a fini de charger le pack.
- `fr.vorace.api.VoraceMeublePoseEvent`, `VoraceMeubleCasseEvent` : un meuble va être posé ou cassé (annulables).

## Compiler

```
gradle build          # le .jar est dans build/libs/
gradle tests          # tests sans serveur (fusion des packs, hébergement HTTP)
```

Par défaut le plugin est compilé contre l'API Paper 1.21.1 (la plus ancienne prise en charge).
La compilation automatique sur GitHub vérifie aussi 1.21.4, 1.21.11 et 26.2.

## Feuille de route

Objectif : un Vorace complet qui fait tout ce que font Nexo, ItemsAdder, ModelEngine, ProtocolLib et MythicLib,
en plus simple. Ensuite viendra VoraceMMO (mobs et compétences, objets RPG, progression).

1. ✅ Packs : fusion, hébergement, envoi
2. ✅ Objets personnalisés (texture ou modèle 3D, propriétés, actions au clic, recettes)
3. ✅ Meubles (modèles 3D posables, hitbox, sièges, lumières, coffres)
4. ✅ Blocs personnalisés (minerais, décorations, avec outils, vitesse de minage et drops)
5. ✅ Glyphes et emojis (chat, menus, Tab, titres), fonds de menus personnalisés
6. ✅ Modèles animés : import direct des .bbmodel, animations, sur des entités
7. Paquets et bibliothèque commune (entités visibles par un seul joueur, API pour VoraceMMO)
8. Conversion automatique des configurations Nexo, ItemsAdder et ModelEngine

## Licence

MIT : tu peux utiliser, modifier et partager Vorace librement.
