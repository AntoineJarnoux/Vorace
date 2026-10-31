# Vorace

Plugin **libre et gratuit** pour serveurs Paper (Minecraft **1.21 à 26.x**) : packs de ressources,
objets, meubles, blocs et glyphes personnalisés. Le but est de pouvoir tout faire sans plugin payant,
et de corriger ou améliorer le plugin au fur et à mesure des besoins.

> Vorace est écrit de zéro. Il ne contient aucun code d'un autre plugin.

## Ce que fait la version 0.1 (étape 1 : les packs)

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
| `/vorace pack` | (tous les joueurs) recevoir à nouveau le pack |

Permissions : `vorace.admin` (op par défaut), `vorace.pack` (tout le monde).

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

## Compiler

```
gradle build          # le .jar est dans build/libs/
gradle tests          # tests sans serveur (fusion des packs, hébergement HTTP)
```

Par défaut le plugin est compilé contre l'API Paper 1.21.1 (la plus ancienne prise en charge).
La compilation automatique sur GitHub vérifie aussi 1.21.4, 1.21.11 et 26.2.

## Feuille de route

1. ✅ Packs : fusion, hébergement, envoi
2. Objets personnalisés (fichiers YAML : texture ou modèle 3D, nom, description, enchantements, commandes `/vorace give`)
3. Meubles (modèles 3D posables, avec hitbox, sièges, lumières)
4. Blocs personnalisés (minerais, décorations, avec outils et drops)
5. Glyphes et emojis (chat, menus, scoreboard, titres), fonds de menus personnalisés
6. Conversion automatique des configurations Nexo vers Vorace

## Licence

MIT : tu peux utiliser, modifier et partager Vorace librement.
