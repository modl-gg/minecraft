#!/bin/bash
set -e

echo "=== Building internal modules ==="
./gradlew :api:build :core:build :bridge-core:build

echo "=== Building Fabric 1.21.x ==="
./gradlew :platforms:fabric:remapJar

echo "=== Building Fabric 26.1 ==="
if [ -d "platforms/fabric-26" ] && [ -f "platforms/fabric-26/gradlew" ]; then
    (cd platforms/fabric-26 && ./gradlew build)
else
    echo "WARN: platforms/fabric-26 not set up with Gradle wrapper, skipping 26.1 build"
fi

echo "=== Building distribution JAR ==="
./gradlew :distribution:shadowJar

echo "=== Done ==="
ls -la distribution/build/libs/modl-*.jar
