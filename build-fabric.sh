#!/bin/bash
set -e

echo "=== Building internal modules ==="
./gradlew :api:build :core:build :bridge-core:build

echo "=== Building Fabric shell ==="
./gradlew :platforms:fabric:remapJar

echo "=== Building Fabric 1.21.1 ==="
./gradlew -p platforms/fabric-121 build -x test

echo "=== Building Fabric 1.21.4 ==="
./gradlew -p platforms/fabric-1214 build -x test

echo "=== Building Fabric 1.21.11 ==="
./gradlew -p platforms/fabric-12111 build -x test

echo "=== Building Fabric 26.1 ==="
./gradlew -p platforms/fabric-26 build -x test

echo "=== Building distribution JAR ==="
./gradlew :distribution:shadowJar

echo "=== Done ==="
ls -la distribution/build/libs/modl-*.jar
