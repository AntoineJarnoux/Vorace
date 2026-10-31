#!/bin/bash
# Essai automatique sur un vrai serveur Paper (CI) : pose le golem d'exemple, lance des animations et vérifie
# que les entités d'affichage changent vraiment, côté serveur (/vorace modele info) et côté joueur (faux client).
# Usage : outils/essai-serveur.sh <version de Paper> <dossier contenant Vorace-*.jar>
set -uo pipefail
VERSION=${1:-1.21.4}
JAR=$(ls "$2"/Vorace-*.jar | head -1)
OUTILS=$(cd "$(dirname "$0")" && pwd)
mkdir -p essai-serveur/plugins && cd essai-serveur
cp "$JAR" plugins/
URL=$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/$VERSION/builds/latest" | python3 -c 'import json,sys; print(json.load(sys.stdin)["downloads"]["server:default"]["url"])')
echo "Paper : $URL"
curl -fsSL -o paper.jar "$URL"
echo "eula=true" > eula.txt
cat > server.properties <<PROPS
online-mode=false
level-type=minecraft\:flat
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
node "$OUTILS/essai-bot.mjs" 30 > bot.txt 2>&1 &
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
wait $BOT
cmd "stop"
for _ in $(seq 1 60); do kill -0 $SERVEUR 2>/dev/null || break; sleep 1; done
kill $SERVEUR 2>/dev/null

echo "===== Faux joueur"; cat bot.txt
echo "===== Console (Vorace)"; grep -aiE 'vorace|modèle|empreinte|animation|exception|error|at fr\.' journal.txt | grep -v 'Loading server plugin' | head -150
echo "===== Fin du journal"; tail -30 journal.txt

# verdict
EMPREINTES=$(grep -ao 'empreinte=[-0-9.]*' journal.txt | sort -u | wc -l)
DISTINCTES=$(grep -ao 'transformations-distinctes=[0-9]*' bot.txt | cut -d= -f2)
echo "empreintes serveur distinctes : $EMPREINTES ; transformations reçues par le joueur : ${DISTINCTES:-0}"
if [ "$EMPREINTES" -lt 3 ] || [ "${DISTINCTES:-0}" -lt 50 ]; then echo "ECHEC : les modèles ne s'animent pas"; exit 1; fi
echo "ESSAI REUSSI"
