#!/bin/bash
# Essai sur la vraie map d'Orario (CI) : serveur Paper 1.21.4 avec le monde et les animaux d'Orario, un faux joueur en
# créatif emmené sur une place à pigeons, près d'un bain d'oiseaux, dans un pré et dans un jardin ; relevé des mobs
# toutes les 5 s ; redémarrage du serveur puis nouveau relevé (les animaux reprennent leur cerveau).
# Usage : outils/essai-orario.sh <zip du serveur Orario> <dossier contenant Vorace-*.jar>
set -uo pipefail
ZIP=$(cd "$(dirname "$1")" && echo "$PWD/$(basename "$1")")
JAR=$(cd "$2" && ls "$PWD"/Vorace-*.jar | head -1)
OUTILS=$(cd "$(dirname "$0")" && pwd)
mkdir -p essai-orario && cd essai-orario
unzip -q -o "$ZIP"
SRV=$(dirname "$(find . -maxdepth 3 -name 'level.dat' -path '*Orario*' | head -1)")
cd "$SRV/.."
rm -f plugins/Vorace-*.jar && cp "$JAR" plugins/
URL=$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/1.21.4/builds/latest" | python3 -c 'import json,sys; print(json.load(sys.stdin)["downloads"]["server:default"]["url"])')
curl -fsSL -o paper.jar "$URL"
echo "eula=true" > eula.txt
cat > server.properties <<PROPS
online-mode=false
level-name=Orario
spawn-protection=0
view-distance=6
simulation-distance=6
spawn-monsters=false
PROPS
python3 - <<'PY' > lieux.txt
import yaml, glob
ap = {}
for f in glob.glob('plugins/Vorace/apparitions/*.yml'):
    ap.update(yaml.safe_load(open(f)) or {})
import os
def dedans(c):  # loin du bord des fichiers de région fournis (le reste du monde n'est pas dans le zip d'essai)
    rx, rz = int(c[0] // 512), int(c[2] // 512)
    return os.path.exists(f'Orario/region/r.{rx}.{rz}.mca') and 96 <= c[0] % 512 <= 416 and 96 <= c[2] % 512 <= 416
def premier(prefixe, mini=1):
    for k, v in sorted(ap.items()):
        if k.startswith(prefixe) and v.get('nombre', 1) >= mini and dedans(v['centre']):
            c = v['centre']; return k, v['mob'], c, v.get('nombre', 1)
for nom, (pre, mini) in {'PIGEONS': ('pigeons_place_', 4), 'BAIN': ('bain_oiseaux_', 1), 'PRE': ('mouton_', 2), 'VACHES': ('vache_', 3),
                         'JARDIN': ('chat_jardin_', 1), 'FORET': ('cerf_', 2)}.items():
    r = premier(pre, mini)
    if r:
        k, mob, c, n = r
        print(f'{nom} {k} {mob} {c[0]:.1f} {c[1]:.1f} {c[2]:.1f} {n}')
PY
cat lieux.txt
lieu() { grep "^$1 " lieux.txt | head -1; }
rm -f console && mkfifo console
exec 3<>console
demarrer() {
  local avant
  avant=$(grep -c 'Done (' journal.txt 2>/dev/null || true)
  java -Xmx3G -jar paper.jar --nogui < console >> journal.txt 2>&1 &
  SERVEUR=$!
  for _ in $(seq 1 400); do [ "$(grep -c 'Done (' journal.txt 2>/dev/null || true)" -gt "${avant:-0}" ] && break; sleep 1; done
}
cmd() { echo ">>> $1" >> commandes.txt; echo "$1" >&3; }
releve() { # releve <début du nom> <fois> <écart>
  for _ in $(seq 1 "$2"); do cmd "vorace mob info $1 Testeur"; sleep "$3"; done
}
aller() { # aller <x> <y> <z> : téléporte le faux joueur
  cmd "tp Testeur $1 $2 $3"
}
: > journal.txt
demarrer
node "$OUTILS/essai-orario-bot.mjs" 330 > bot.txt 2>&1 &
BOT=$!
for _ in $(seq 1 60); do grep -q 'BOT CONNECTE' bot.txt && break; sleep 1; done
cmd "gamemode creative Testeur"
cmd "time set 1000"
cmd "gamerule doDaylightCycle false"
# 1. place à pigeons : vus de 12 blocs (marche, picore), puis le joueur arrive au milieu (envol de la volée)
read -r _ ID MOB X Y Z N <<< "$(lieu PIGEONS)"
echo "== PIGEONS $ID ($N pigeons) à $X $Z" >> commandes.txt
aller "$(python3 -c "print($X + 12)")" "$(python3 -c "print($Y + 2)")" "$Z"
sleep 25
releve pigeon 5 5
aller "$X" "$(python3 -c "print($Y + 1)")" "$Z"
sleep 1
releve pigeon 2 2
aller "$(python3 -c "print($X + 40)")" "$(python3 -c "print($Y + 2)")" "$Z"
sleep 25
releve pigeon 2 4
# 2. bain d'oiseaux : présents vus de loin, partis quand on s'approche, revenus 15 s après le départ du joueur
read -r _ ID MOB X Y Z N <<< "$(lieu BAIN)"
echo "== BAIN $ID à $X $Z" >> commandes.txt
aller "$(python3 -c "print($X + 9)")" "$(python3 -c "print($Y + 2)")" "$Z"
sleep 15
releve bain 2 3
aller "$(python3 -c "print($X + 1.5)")" "$(python3 -c "print($Y + 1)")" "$Z"
sleep 2
releve bain 2 2
aller "$(python3 -c "print($X + 12)")" "$(python3 -c "print($Y + 2)")" "$Z"
sleep 24
releve bain 3 3
# 3. pré (moutons), vaches, jardin (chat), forêt (cerfs) : 40 s chacun, relevé toutes les 5 s
for L in PRE VACHES JARDIN FORET; do
  read -r _ ID MOB X Y Z N <<< "$(lieu $L)" || continue
  [ -n "${ID:-}" ] || continue
  echo "== $L $ID ($N) à $X $Z" >> commandes.txt
  aller "$(python3 -c "print($X + 14)")" "$(python3 -c "print($Y + 3)")" "$Z"
  sleep 15
  releve "$MOB" 6 5
done
wait $BOT
# 4. redémarrage : les animaux enregistrés dans le monde reprennent leur cerveau
cmd "stop"
for _ in $(seq 1 90); do kill -0 $SERVEUR 2>/dev/null || break; sleep 1; done
echo "===== REDEMARRAGE" >> journal.txt
demarrer
node "$OUTILS/essai-orario-bot.mjs" 70 > bot2.txt 2>&1 &
BOT=$!
for _ in $(seq 1 60); do grep -q 'BOT CONNECTE' bot2.txt && break; sleep 1; done
cmd "gamemode creative Testeur"
read -r _ ID MOB X Y Z N <<< "$(lieu PRE)"
echo "== APRES REDEMARRAGE $ID à $X $Z" >> commandes.txt
aller "$(python3 -c "print($X + 14)")" "$(python3 -c "print($Y + 3)")" "$Z"
sleep 15
releve "$MOB" 6 5
wait $BOT
cmd "stop"
for _ in $(seq 1 90); do kill -0 $SERVEUR 2>/dev/null || break; sleep 1; done

echo "===== Faux joueur"; cat bot.txt bot2.txt
echo "===== Commandes"; cat commandes.txt
echo "===== Mobs (console)"; grep -aE 'mob=|[0-9]+ mob\(s\)|REDEMARRAGE|Aucun mob' journal.txt
echo "===== Mobs (messages reçus par le joueur)"; grep -ahE 'mob=|[0-9]+ mob\(s\)|Aucun mob' bot.txt bot2.txt
echo "===== Erreurs"; grep -aiE 'exception|at fr\.vorace|Mobs : erreur' journal.txt | head -20
echo "===== Bilan"
python3 - <<'PY'
import re, collections
j = open('journal.txt', errors='replace').read().split('===== REDEMARRAGE')
b1 = open('bot.txt', errors='replace').read(); b2 = open('bot2.txt', errors='replace').read() if __import__('os').path.exists('bot2.txt') else ''
lignes = [j[0] + b1, (j[1] if len(j) > 1 else '') + b2]
if not lignes[1].strip(): lignes = lignes[:1]
def lire(t):
    res = collections.defaultdict(list)
    for m in re.finditer(r'mob=(\S+) action=(\S+) .*?parcouru=([0-9.]+)', t):
        res[m.group(1)].append((m.group(2), float(m.group(3))))
    return res
avant = lire(lignes[0]); apres = lire(lignes[1]) if len(lignes) > 1 else {}
print('mob : actions vues ; distance la plus longue parcourue par un individu')
for k, v in sorted(avant.items()):
    print(f'  {k:14s} {", ".join(sorted(set(a for a, _ in v))):40s} {max(d for _, d in v):6.1f} blocs  ({len(v)} relevés)')
print('après redémarrage :')
for k, v in sorted(apres.items()):
    print(f'  {k:14s} {", ".join(sorted(set(a for a, _ in v))):40s} {max(d for _, d in v):6.1f} blocs  ({len(v)} relevés)')
ok = True
pig = avant.get('pigeon', [])
if not any(a in ('marche', 'picore') for a, _ in pig): print('ECHEC : pigeons ni en marche ni en train de picorer'); ok = False
if sum(1 for a, _ in pig if a == 'vole') < 3: print('ECHEC : la volée ne part pas quand le joueur arrive'); ok = False
bain = [a for a, _ in avant.get('bain_oiseaux', [])]
if 'partis' not in bain or bain[-1] != 'presents': print('ECHEC : les oiseaux du bain ne partent pas ou ne reviennent pas'); ok = False
for k in ('mouton', 'vache'):
    if k in avant and max(d for _, d in avant[k]) < 2: print(f'ECHEC : {k} immobile'); ok = False
if apres and max(d for v in apres.values() for _, d in v) < 2: print('ECHEC : immobiles après redémarrage'); ok = False
if not apres: print('ECHEC : aucun mob relevé après le redémarrage'); ok = False
print('ESSAI ORARIO OK' if ok else 'ESSAI ORARIO EN ECHEC')
raise SystemExit(0 if ok else 1)
PY
