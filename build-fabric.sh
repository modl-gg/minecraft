#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Publishing forked PacketEvents to Maven Local..."
../minecraft-packetevents/gradlew -p ../minecraft-packetevents publishToMavenLocal

echo "Building distribution JARs (universal + Fabric, incl. every per-version module)..."
./gradlew :distribution:shadowJar :distribution:fabricJar

echo "Done!"
ls -la distribution/build/libs/modl-*.jar
