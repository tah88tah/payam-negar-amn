#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mkdir -p build/pure-test
javac -encoding UTF-8 -d build/pure-test \
  app/src/main/java/ir/tahmuresi/payamnegar/CryptoEngine.java \
  app/src/main/java/ir/tahmuresi/payamnegar/SmsPacketProtocol.java \
  app/src/main/java/ir/tahmuresi/payamnegar/PhoneNumbers.java \
  tools/PureJavaSelfTest.java
java -cp build/pure-test ir.tahmuresi.payamnegar.PureJavaSelfTest > build/java-packet.json
node tools/crosscheck-webcrypto.mjs
javac -encoding UTF-8 -cp build/pure-test -d build/pure-test tools/DecryptPacket.java
java -cp build/pure-test ir.tahmuresi.payamnegar.DecryptPacket build/webcrypto-packet.json
