#!/bin/bash
# DonutDeck Build Script for macOS
# Run from the project root directory: ./scripts/build.sh

set -e  # Exit on error

echo "=== DonutDeck Build ==="
echo "Target: Minecraft 1.21.11 | Fabric API 0.141.6+"
echo ""

# Check for Java 25 (required by Loom)
JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home"
if [ ! -d "$JAVA_HOME" ]; then
    echo "ERROR: Java 25 not found at $JAVA_HOME"
    echo "Please install JDK 25 or update JAVA_HOME in gradle.properties"
    exit 1
fi

echo "Using Java: $(java -version 2>&1 | head -n1)"
echo ""

# Run Gradle build
cd "$(dirname "$0")/.."
./gradlew clean shadowJar --no-daemon

if [ $? -eq 0 ]; then
    echo ""
    echo "=== Build Successful ==="
    JAR_FILE="build/libs/DonutDeck-1.0.0-all.jar"
    
    if [ -f "$JAR_FILE" ]; then
        SIZE=$(stat -f%z "$JAR_FILE" 2>/dev/null || stat -c%s "$JAR_FILE")
        echo "Output: $JAR_FILE"
        echo "Size: $SIZE bytes"
        
        # Copy to mods folder for quick testing (optional)
        MODS_DIR="$HOME/.minecraft/mods"
        if [ -d "$MODS_DIR" ]; then
            cp "$JAR_FILE" "$MODS_DIR/"
            echo ""
            echo "Also copied to: $MODS_DIR/DonutDeck-1.0.0-all.jar"
            echo "Launch Minecraft with Fabric loader and enjoy!"
        else
            echo ""
            echo "Mods directory not found at $MODS_DIR"
            echo "Manually copy the jar to your mods folder."
        fi
    else
        echo "ERROR: Jar file not found at $JAR_FILE"
        exit 1
    fi
else
    echo "=== Build Failed ==="
    exit 1
fi
