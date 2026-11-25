#!/usr/bin/env bash
# Install DonutDeck into a new Prism Launcher instance.
# This script never launches Minecraft and never touches existing instances.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PRISM_DIR="${PRISM_DIR:-$HOME/Library/Application Support/PrismLauncher}"
INSTANCE_ID="${INSTANCE_ID:-DonutDeck-1.21.11}"
INSTANCE_NAME="${INSTANCE_NAME:-DonutDeck 1.21.11}"
JAVA21="${JAVA21:-$PRISM_DIR/java/java-runtime-delta/bin/java}"
JAVA25="${JAVA25:-$PRISM_DIR/java/java-runtime-epsilon/bin/java}"

MOD_VERSION="1.0.0"
MOD_JAR="$ROOT/build/libs/donutdeck-$MOD_VERSION.jar"
DEPS_DIR="$ROOT/build/deps"
FABRIC_API_FILE="fabric-api-0.141.6+1.21.11.jar"
OPSEC_FILE="opsec-1.21.11+v1.1.7.1.jar"
FABRIC_API_URL="https://cdn.modrinth.com/data/P7dR8mSH/versions/6qAuTtLR/fabric-api-0.141.6%2B1.21.11.jar"
OPSEC_URL="https://github.com/aurickk/OpSec/releases/download/V1.1.7.1/opsec-1.21.11%2Bv1.1.7.1.jar"
FABRIC_API_SHA1="c98467cbbaf4d197377266795ae015f4130d65b6"
OPSEC_SHA1="7507cc56893785b964d737eed6ae3135a1b9dccc"

INSTANCE_DIR="$PRISM_DIR/instances/$INSTANCE_ID"
MODS_DIR="$INSTANCE_DIR/minecraft/mods"

say() { printf '%s\n' "$*"; }
fail() { printf 'error: %s\n' "$*" >&2; exit 1; }

sha1_of() {
	shasum -a 1 "$1" | awk '{print $1}'
}

download_verified() {
	local url="$1" dest="$2" expected="$3" label="$4"
	if [[ -f "$dest" ]] && [[ "$(sha1_of "$dest")" == "$expected" ]]; then
		say "  $label already present and verified"
		return
	fi
	rm -f "$dest"
	say "  downloading $label"
	curl -fL --retry 3 --connect-timeout 15 --max-time 180 -o "$dest" "$url"
	local actual
	actual="$(sha1_of "$dest")"
	if [[ "$actual" != "$expected" ]]; then
		rm -f "$dest"
		fail "$label checksum mismatch (expected $expected, got $actual)"
	fi
}

[[ -d "$PRISM_DIR" ]] || fail "Prism data directory not found: $PRISM_DIR"
[[ -x "$JAVA21" ]] || fail "Java 21 runtime not found: $JAVA21"
mkdir -p "$DEPS_DIR" "$MODS_DIR"

say "DonutDeck Prism installer"
say "  Prism:    $PRISM_DIR"
say "  Instance: $INSTANCE_NAME ($INSTANCE_ID)"
say "  Java 21:  $JAVA21"

if [[ ! -f "$MOD_JAR" ]]; then
	say "Building DonutDeck (Java 25 for Gradle, Java 21 target)..."
	[[ -x "$JAVA25" ]] || fail "Java 25 runtime not found: $JAVA25"
	(cd "$ROOT" && JAVA_HOME="$JAVA25" ./gradlew build --no-daemon)
fi
[[ -f "$MOD_JAR" ]] || fail "Mod jar not found after build: $MOD_JAR"

say "Resolving dependencies..."
download_verified "$FABRIC_API_URL" "$DEPS_DIR/$FABRIC_API_FILE" "$FABRIC_API_SHA1" "Fabric API"
download_verified "$OPSEC_URL" "$DEPS_DIR/$OPSEC_FILE" "$OPSEC_SHA1" "OpSec"

if [[ ! -f "$INSTANCE_DIR/instance.cfg" ]]; then
	UUID="$(uuidgen | tr -d '-' | tr '[:upper:]' '[:lower:]')"
	cat > "$INSTANCE_DIR/instance.cfg" <<EOF
[General]
AutomaticJava=false
ConfigVersion=1.3
InstanceType=OneSix
JavaArchitecture=64
JavaPath=$JAVA21
JavaRealArchitecture=aarch64
JavaVendor=Microsoft
JavaVersion=21.0.7
OverrideJavaLocation=true
name=$INSTANCE_NAME
totalTimePlayed=0
uuid=$UUID
EOF
	say "  wrote instance.cfg"
else
	say "  instance.cfg exists; leaving it unchanged"
fi

if [[ ! -f "$INSTANCE_DIR/mmc-pack.json" ]]; then
	cat > "$INSTANCE_DIR/mmc-pack.json" <<'EOF'
{
  "formatVersion": 1,
  "components": [
    {
      "cachedName": "LWJGL 3",
      "cachedVersion": "3.3.3",
      "dependencyOnly": true,
      "uid": "org.lwjgl3",
      "version": "3.3.3"
    },
    {
      "cachedName": "Minecraft",
      "cachedVersion": "1.21.11",
      "important": true,
      "uid": "net.minecraft",
      "version": "1.21.11"
    },
    {
      "cachedName": "Intermediary Mappings",
      "cachedVersion": "1.21.11",
      "dependencyOnly": true,
      "uid": "net.fabricmc.intermediary",
      "version": "1.21.11"
    },
    {
      "cachedName": "Fabric Loader",
      "cachedVersion": "0.20.1",
      "uid": "net.fabricmc.fabric-loader",
      "version": "0.20.1"
    }
  ]
}
EOF
	say "  wrote mmc-pack.json"
else
	say "  mmc-pack.json exists; leaving it unchanged"
fi

install -m 0644 "$MOD_JAR" "$MODS_DIR/donutdeck-$MOD_VERSION.jar"
install -m 0644 "$DEPS_DIR/$FABRIC_API_FILE" "$MODS_DIR/$FABRIC_API_FILE"
install -m 0644 "$DEPS_DIR/$OPSEC_FILE" "$MODS_DIR/$OPSEC_FILE"

if [[ -f "$ROOT/src/main/resources/assets/donutdeck/icon.png" ]]; then
	install -m 0644 "$ROOT/src/main/resources/assets/donutdeck/icon.png" "$INSTANCE_DIR/icon.png"
fi

say ""
say "Installed:"
say "  $MODS_DIR/donutdeck-$MOD_VERSION.jar"
say "  $MODS_DIR/$FABRIC_API_FILE"
say "  $MODS_DIR/$OPSEC_FILE"
say ""
say "Next: open Prism Launcher, select \"$INSTANCE_NAME\", and launch it when ready."
say "The first launch downloads Minecraft 1.21.11 and Fabric Loader 0.20.1."
say "This installer did not launch the game and did not join any server."
