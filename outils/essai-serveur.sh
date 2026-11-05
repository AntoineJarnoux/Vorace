#!/bin/bash
# Essai automatique sur un vrai serveur Paper (CI) : pose le golem d'exemple, lance des animations et vérifie
# que les entités d'affichage changent vraiment, côté serveur (/vorace modele info) et côté joueur (faux client).
# Usage : outils/essai-serveur.sh <version de Paper> <dossier contenant Vorace-*.jar> [version du faux joueur]
# Avec une version de joueur plus récente que le serveur, ViaVersion est installé (comme sur beaucoup de serveurs).
set -uo pipefail
VERSION=${1:-1.21.4}
JAR=$(cd "$2" && ls "$PWD"/Vorace-*.jar | head -1)
CLIENT=${3:-}
OUTILS=$(cd "$(dirname "$0")" && pwd)
mkdir -p essai-serveur/plugins && cd essai-serveur
cp "$JAR" plugins/
URL=$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/$VERSION/builds/latest" | python3 -c 'import json,sys; print(json.load(sys.stdin)["downloads"]["server:default"]["url"])')
echo "Paper : $URL"
curl -fsSL -o paper.jar "$URL"
if [ -n "$CLIENT" ]; then
  VIA=$(curl -fsSL https://api.github.com/repos/ViaVersion/ViaVersion/releases/latest | python3 -c 'import json,sys; print([a["browser_download_url"] for a in json.load(sys.stdin)["assets"] if a["name"].endswith(".jar")][0])')
  echo "ViaVersion : $VIA"
  curl -fsSL -o plugins/ViaVersion.jar "$VIA"
fi
# contenu d'exemple d'autres plugins, pour essayer /vorace convertir (leurs fichiers seulement, pas les plugins)
cp -r "$OUTILS/exemples-conversion/." plugins/
mkdir -p plugins/ModelEngine/blueprints/monstres
python3 - "$OUTILS/../src/main/resources/exemples/modeles/golem_rubis.bbmodel" plugins/ModelEngine/blueprints/monstres/golem_me.bbmodel <<'PY'
import json, sys
g = json.load(open(sys.argv[1], encoding='utf-8'))
g["elements"].append({"name": "hb", "type": "cube", "uuid": "hitbox-cube-0001", "from": [-8, 0, -8], "to": [8, 40, 8], "origin": [0, 0, 0], "faces": {}})
g["outliner"].append({"name": "hitbox", "origin": [0, 0, 0], "uuid": "hitbox-groupe-0001", "children": ["hitbox-cube-0001"]})
json.dump(g, open(sys.argv[2], 'w', encoding='utf-8'))
PY
# mobs (1.1) : un troupeau de golems paisibles (modèle animé + cerveau + troupeau) et un lapin craintif
mkdir -p plugins/Vorace/mobs
cat > plugins/Vorace/mobs/essai.yml <<'MOBS'
golem_paisible:
  type: sheep
  modele: golem_rubis
  taille: [0.5, 0.6]
  caractere: paisible
  troupeau: { cohesion: 5 }
  laisse: 10
  nourriture: [grass_block, short_grass]
lapin_essai:
  type: rabbit
  caractere: craintif
  detection: 40
  laisse: 10
oiseau_essai:
  type: bat
  modele: golem_rubis
  taille: [0.3, 0.3]
  vol: oiseau
  envol: 40
  laisse: 14
  silencieux: true
cochon_essai:
  type: pig
  troupeau: { cohesion: 5 }
  laisse: 10
  nourriture: [grass_block, short_grass]
poule_essai:
  type: chicken
  caractere: craintif
  detection: 40
  laisse: 10
chat_essai:
  type: cat
  caractere: craintif
  detection: 40
  laisse: 10
chevre_essai:
  type: goat
  ia: jeu
  laisse: 8
pigeon_essai:
  type: bat
  modele: golem_rubis
  taille: [0.3, 0.3]
  vol: oiseau
  marche: 0.9
  envol: 1
  laisse: 10
  silencieux: true
lapin_createf:
  type: rabbit
  caractere: craintif
  detection: 40
  laisse: 10
MOBS
# zoo : un mob de chaque sorte réglé comme les animaux d'Orario (sans leurs modèles), pour vérifier qu'ils bougent tout seuls
cat > plugins/Vorace/mobs/zoo.yml <<'ZOO'
zoo_mouton: { type: sheep, caractere: paisible, troupeau: { cohesion: 6 }, laisse: 18, nourriture: [grass_block, short_grass] }
zoo_vache: { type: cow, caractere: paisible, troupeau: { cohesion: 7 }, laisse: 20, nourriture: [grass_block, short_grass] }
zoo_cochon: { type: pig, caractere: paisible, troupeau: { cohesion: 6 }, laisse: 12, nourriture: [grass_block, short_grass] }
zoo_cheval: { type: horse, caractere: paisible, troupeau: { cohesion: 8 }, laisse: 14, nourriture: [grass_block, short_grass] }
zoo_poule: { type: chicken, caractere: craintif, detection: 5, laisse: 10, nourriture: [grass_block, short_grass] }
zoo_chat: { type: cat, caractere: craintif, activite: toujours, detection: 5, laisse: 18, boit: false }
zoo_lapin: { type: rabbit, caractere: craintif, detection: 10, laisse: 16, vitesse: 1.2, nourriture: [short_grass, grass_block] }
zoo_renard: { type: fox, caractere: craintif, activite: toujours, detection: 12, laisse: 24, nourriture: [grass_block] }
zoo_cerf: { type: cow, caractere: craintif, detection: 16, laisse: 28, vitesse: 1.3, silencieux: true, nourriture: [grass_block, short_grass] }
zoo_sanglier: { type: pig, caractere: territorial, activite: toujours, laisse: 24, degats: 4, vie: 20, nourriture: [grass_block] }
zoo_ecureuil: { type: rabbit, caractere: craintif, detection: 8, laisse: 12, vitesse: 1.3, silencieux: true, boit: false, nourriture: [grass_block] }
zoo_chevre: { type: goat, caractere: territorial, ia: jeu, laisse: 24 }
zoo_pigeon: { type: bat, vol: oiseau, marche: 0.9, envol: 1, laisse: 20, silencieux: true }
zoo_papillon: { type: bat, vol: papillon, envol: 1, laisse: 10, silencieux: true }
ZOO
echo "eula=true" > eula.txt
cat > server.properties <<PROPS
online-mode=false
level-type=minecraft\:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=false
spawn-protection=0
spawn-monsters=false
spawn-animals=false
view-distance=4
simulation-distance=4
PROPS
rm -f console && mkfifo console
exec 3<>console
java -Xmx2G -jar paper.jar --nogui < console > journal.txt 2>&1 &
SERVEUR=$!
cmd() { echo ">>> $1" >> commandes.txt; echo "$1" >&3; }
attendre() { # attendre <motif> <fichier> <secondes>
  for _ in $(seq 1 "$3"); do grep -q "$1" "$2" 2>/dev/null && return 0; sleep 1; done; return 1
}
if ! attendre 'Done (' journal.txt 400; then echo "Le serveur n'a pas démarré"; tail -80 journal.txt; exit 1; fi
cmd "vorace modele"
node "$OUTILS/essai-bot.mjs" 100 $CLIENT > bot.txt 2>&1 &
BOT=$!
attendre 'BOT CONNECTE' bot.txt 60 || { echo "Le faux joueur ne s'est pas connecté"; cat bot.txt; }
sleep 2
cmd "vorace modele poser golem_rubis"
sleep 3
cmd "vorace modele info"; sleep 1; cmd "vorace modele info"
sleep 1
cmd "vorace modele anim marche boucle"
sleep 2
cmd "vorace modele info"; sleep 1; cmd "vorace modele info"; sleep 1; cmd "vorace modele info"
cmd "vorace modele anim attaque"
sleep 0.3
cmd "vorace modele info"
sleep 2
cmd "vorace modele stop"
sleep 1
cmd "vorace modele info"
# améliorations : animation par-dessus, taille du modèle
cmd "vorace modele anim marche boucle"
sleep 0.5
cmd "vorace modele anim salut dessus"
sleep 0.3
cmd "vorace modele info"
cmd "vorace modele taille 1.5"
sleep 1
cmd "vorace modele info"
cmd "vorace modele stop"
# mobs : troupeau avec modèle et lapin craintif, à 8 blocs du faux joueur
cmd "vorace mob invoquer golem_paisible 3"
cmd "vorace mob invoquer lapin_essai"
for m in oiseau_essai poule_essai chat_essai chevre_essai; do cmd "vorace mob invoquer $m"; done
cmd "vorace mob invoquer cochon_essai 2"
sleep 5
cmd "vorace mob info"
sleep 3
cmd "vorace mob info"
# joueur en créatif : les animaux vivent quand même autour de lui (marche du pigeon, fuite du lapin)
cmd "gamemode creative Testeur"
cmd "vorace mob invoquer pigeon_essai 2"
cmd "vorace mob invoquer lapin_createf"
sleep 4
cmd "vorace mob info"
sleep 3
cmd "vorace mob info"
# zoo, joueur toujours en créatif et immobile : 45 s pour voir chaque sorte bouger toute seule
for m in mouton vache cochon cheval poule chat lapin renard cerf sanglier ecureuil chevre pigeon papillon; do cmd "vorace mob invoquer zoo_$m"; done
cmd "time set 1000"
sleep 22
cmd "vorace mob info zoo_"
sleep 23
cmd "vorace mob info zoo_"
# couche paquets : heure remplacée par midi, un message bloqué, un autre qui passe
cmd "gamerule doDaylightCycle false"
cmd "time set 18000"
cmd "vorace paquets essai"
wait $BOT
# conversion du contenu d'exemple de Nexo, ItemsAdder et ModelEngine
AVANT_CONVERSION=$(wc -l < journal.txt)
cmd "vorace convertir"
cmd "vorace convertir tout"
attendre 'Les objets sont dans' journal.txt 90 || echo "La conversion n'a pas fini"
sleep 2
cmd "vorace modele poser modelengine/golem_me"
sleep 2
cmd "vorace modele info"
cmd "vorace meuble liste"
cmd "vorace bloc liste"
cmd "vorace glyphe liste"
sleep 2
# menus (1.0) : un faux joueur admin ouvre /vorace et clique
cmd "op Menus"
node "$OUTILS/essai-menus.mjs" $CLIENT > menus.txt 2>&1
sleep 1
cmd "stop"
for _ in $(seq 1 60); do kill -0 $SERVEUR 2>/dev/null || break; sleep 1; done
kill $SERVEUR 2>/dev/null

echo "===== Faux joueur"; cat bot.txt
echo "===== Console (Vorace)"; grep -aiE 'vorace|modèle|empreinte|animation|paquets|exception|error|at fr\.|game mode' journal.txt | grep -v 'Loading server plugin' | head -150
echo "===== Commandes envoyées, dans l'ordre"; cat commandes.txt
echo "===== Mobs du zoo (joueur en créatif)"; grep -a 'mob=zoo_' journal.txt
echo "===== Fin du journal"; tail -30 journal.txt

# verdict
EMPREINTES=$(grep -ao 'empreinte=[-0-9.]*' journal.txt | sort -u | wc -l)
DISTINCTES=$(grep -ao 'transformations-distinctes=[0-9]*' bot.txt | cut -d= -f2)
echo "empreintes serveur distinctes : $EMPREINTES ; transformations reçues par le joueur : ${DISTINCTES:-0}"
echo "===== Conversion"
tail -n +"$AVANT_CONVERSION" journal.txt | grep -av 'Loading server plugin' | head -80
for f in essai-serveur/plugins/Vorace/conversion/rapport-*.txt plugins/Vorace/conversion/rapport-*.txt; do [ -f "$f" ] && { echo "--- $f"; cat "$f"; }; done
ALERTES_CONVERSION=$(tail -n +"$AVANT_CONVERSION" journal.txt | grep -acE 'WARN\]: \[Vorace\]')
echo "alertes de Vorace après la conversion : $ALERTES_CONVERSION"
for s in Nexo ItemsAdder ModelEngine; do grep -aqE "$s : [0-9]+ objet" journal.txt || { echo "ECHEC : conversion de $s"; exit 1; }; done
if [ "$ALERTES_CONVERSION" -gt 0 ]; then echo "ECHEC : les fichiers convertis donnent des alertes au chargement"; exit 1; fi
echo "===== Menus"; cat menus.txt
grep -aq 'MENUS OK' menus.txt || { echo "ECHEC : menus"; exit 1; }
ERREURS=$(grep -acE 'ERROR\]: \[Vorace\]|at fr\.vorace\.' journal.txt)
echo "erreurs de Vorace dans la console : $ERREURS"
if [ "$ERREURS" -gt 0 ]; then echo "ECHEC : erreurs de Vorace dans la console"; exit 1; fi
if [ "$EMPREINTES" -lt 3 ] || [ "${DISTINCTES:-0}" -lt 50 ]; then echo "ECHEC : les modèles ne s'animent pas"; exit 1; fi
grep -aq 'par-dessus' journal.txt || { echo "ECHEC : animation par-dessus"; exit 1; }
grep -aq '3 golem_paisible invoqué(s) en troupeau' journal.txt || { echo "ECHEC : invocation des mobs"; exit 1; }
MOBS_VUS=$(grep -ao 'mob=[a-z_]* action=[a-z]*' journal.txt | sort -u)
echo "mobs vus : $MOBS_VUS"
echo "$MOBS_VUS" | grep -q 'mob=golem_paisible' || { echo "ECHEC : les golems de l'essai ne pensent pas"; exit 1; }
echo "$MOBS_VUS" | grep -q 'mob=lapin_essai action=fuir' || { echo "ECHEC : le lapin craintif ne fuit pas le joueur"; exit 1; }
echo "$MOBS_VUS" | grep -q 'mob=oiseau_essai action=vole' || { echo "ECHEC : l'oiseau ne s'envole pas"; exit 1; }
echo "$MOBS_VUS" | grep -q 'mob=poule_essai action=fuir' || { echo "ECHEC : la poule ne fuit pas"; exit 1; }
echo "$MOBS_VUS" | grep -q 'mob=chat_essai action=fuir' || { echo "ECHEC : le chat ne fuit pas"; exit 1; }
for m in cochon_essai chevre_essai; do echo "$MOBS_VUS" | grep -q "mob=$m" || { echo "ECHEC : $m absent"; exit 1; }; done
echo "$MOBS_VUS" | grep -q 'mob=lapin_createf action=fuir' || { echo "ECHEC : en créatif, le lapin ne fuit pas (les mobs ne pensent pas)"; exit 1; }
PIED=$(grep -ao 'mob=pigeon_essai .*a-pied=[0-9.]*' journal.txt | grep -ao 'a-pied=[0-9.]*' | cut -d= -f2 | sort -g | tail -1)
echo "pigeon : ${PIED:-0} blocs à pied"
awk -v p="${PIED:-0}" 'BEGIN {exit !(p > 0.5)}' || { echo "ECHEC : en créatif, le pigeon ne marche pas"; exit 1; }
echo "===== Zoo (joueur en créatif, 45 s) : distance parcourue par chaque mob"
ZOO_KO=0
for m in mouton vache cochon cheval poule chat lapin renard cerf sanglier ecureuil chevre pigeon papillon; do
  D=$(grep -ao "mob=zoo_$m .*parcouru=[0-9.]*" journal.txt | grep -ao 'parcouru=[0-9.]*' | cut -d= -f2 | sort -g | tail -1)
  ACTIONS=$(grep -ao "mob=zoo_$m action=[a-z]*" journal.txt | cut -d= -f3 | sort -u | tr '\n' ' ')
  printf 'zoo_%-10s %6s blocs   actions : %s\n' "$m" "${D:-0}" "$ACTIONS"
  awk -v p="${D:-0}" 'BEGIN {exit !(p > 1.0)}' || ZOO_KO=1
done
[ $ZOO_KO = 0 ] || { echo "ECHEC : une sorte de mob ne bouge pas toute seule en 45 s"; exit 1; }
if grep -ao 'maison=[0-9.]*/[0-9]*' journal.txt | awk -F'[=/]' '$2 > $3 + 2 {bad=1} END {exit !bad}'; then echo "ECHEC : un mob est sorti de sa laisse"; exit 1; fi
grep -aq 'taille=1.50 vue=proche' journal.txt || { echo "ECHEC : taille du modèle ou portée d'affichage"; exit 1; }
grep -aq 'ESSAI PAQUETS heures-modifiees=[1-9][0-9]* messages-bloques=[1-9][0-9]* paquets-recus=[1-9][0-9]* erreurs=0' journal.txt || { echo "ECHEC : couche paquets (côté serveur)"; exit 1; }
grep -aq 'VORACE-LIBRE' bot.txt || { echo "ECHEC : le message libre n'est pas arrivé au joueur"; exit 1; }
if grep -aq 'VORACE-BLOQUE' bot.txt; then echo "ECHEC : le message bloqué est arrivé au joueur"; exit 1; fi
grep -aqE 'BOT HEURES.* 6000( |$)|BOT HEURES 6000( |$)' bot.txt || { echo "ECHEC : l'heure reçue par le joueur n'a pas été remplacée par midi"; exit 1; }
echo "ESSAI REUSSI"
