#!/bin/bash
# Compilation sans Gradle (pour les environnements sans accès à repo.papermc.io).
# Usage : ./construire-local.sh <classpath contenant paper-api (ou les copies de compilation), adventure, netty, gson>
set -e
CP="$1"
V=$(grep '^version=' gradle.properties | cut -d= -f2)
rm -rf build/local && mkdir -p build/local/classes build/local/stubs build/libs
javac -nowarn -encoding UTF-8 --release 21 -cp "$CP" -d build/local/stubs $(find src/stubs/java -name "*.java")
javac -Xlint:all,-processing,-classfile -encoding UTF-8 --release 21 -cp "$CP:build/local/stubs" -d build/local/classes $(find src/main/java -name "*.java")
cp src/main/resources/config.yml build/local/classes/
sed "s/\${version}/$V/" src/main/resources/plugin.yml > build/local/classes/plugin.yml
cp LICENSE build/local/classes/LICENSE-Vorace.txt
(cd build/local/classes && jar --create --file ../../libs/Vorace-$V.jar .)
ls -la build/libs/Vorace-$V.jar
