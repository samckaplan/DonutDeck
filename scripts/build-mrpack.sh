#!/usr/bin/env bash
# Build prism/DonutDeck-1.0.0.mrpack from the current jar and verified dependencies.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MOD_VERSION="1.0.0"
MOD_JAR="$ROOT/build/libs/donutdeck-$MOD_VERSION.jar"
DEPS_DIR="$ROOT/build/deps"
OUT_DIR="$ROOT/prism"
OUT="$OUT_DIR/DonutDeck-$MOD_VERSION.mrpack"

FABRIC_API_FILE="fabric-api-0.141.6+1.21.11.jar"
OPSEC_FILE="opsec-1.21.11+v1.1.7.1.jar"
FABRIC_API_URL="https://cdn.modrinth.com/data/P7dR8mSH/versions/6qAuTtLR/fabric-api-0.141.6%2B1.21.11.jar"
OPSEC_URL="https://github.com/aurickk/OpSec/releases/download/V1.1.7.1/opsec-1.21.11%2Bv1.1.7.1.jar"
FABRIC_API_SHA1="c98467cbbaf4d197377266795ae015f4130d65b6"
OPSEC_SHA1="7507cc56893785b964d737eed6ae3135a1b9dccc"
JAVA25="${JAVA25:-$HOME/Library/Application Support/PrismLauncher/java/java-runtime-epsilon/bin/java}"

fail() { printf 'error: %s\n' "$*" >&2; exit 1; }
sha1_of() { shasum -a 1 "$1" | awk '{print $1}'; }

mkdir -p "$DEPS_DIR" "$OUT_DIR"

if [[ ! -f "$MOD_JAR" ]]; then
	printf 'Building DonutDeck...\n'
	[[ -x "$JAVA25" ]] || fail "Java 25 runtime not found: $JAVA25"
	(cd "$ROOT" && JAVA_HOME="$JAVA25" ./gradlew build --no-daemon)
fi
[[ -f "$MOD_JAR" ]] || fail "Mod jar not found: $MOD_JAR"

download_verified() {
	local url="$1" dest="$2" expected="$3" label="$4"
	if [[ -f "$dest" ]] && [[ "$(sha1_of "$dest")" == "$expected" ]]; then
		return
	fi
	printf 'Downloading %s...\n' "$label"
	rm -f "$dest"
	curl -fL --retry 3 --connect-timeout 15 --max-time 180 -o "$dest" "$url"
	local actual
	actual="$(sha1_of "$dest")"
	[[ "$actual" == "$expected" ]] || fail "$label checksum mismatch"
}

download_verified "$FABRIC_API_URL" "$DEPS_DIR/$FABRIC_API_FILE" "$FABRIC_API_SHA1" "Fabric API"
download_verified "$OPSEC_URL" "$DEPS_DIR/$OPSEC_FILE" "$OPSEC_SHA1" "OpSec"

python3 - "$ROOT" "$MOD_JAR" "$DEPS_DIR" "$OUT" "$MOD_VERSION" \
	"$FABRIC_API_FILE" "$FABRIC_API_URL" "$FABRIC_API_SHA1" \
	"$OPSEC_FILE" "$OPSEC_URL" "$OPSEC_SHA1" <<'PY'
import hashlib
import json
import os
import sys
import zipfile
from pathlib import Path

(root, mod_jar, deps_dir, out, version,
 fabric_file, fabric_url, fabric_sha1,
 opsec_file, opsec_url, opsec_sha1) = sys.argv[1:]
root = Path(root)
mod_jar = Path(mod_jar)
deps_dir = Path(deps_dir)
out = Path(out)

def sha1(path):
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def entry(path, url, digest):
    return {
        "path": f"mods/{path.name}",
        "hashes": {"sha1": digest},
        "downloads": [url],
        "fileSize": path.stat().st_size,
        "env": {"client": "required", "server": "unsupported"},
    }

fabric_path = deps_dir / fabric_file
opsec_path = deps_dir / opsec_file
index = {
    "formatVersion": 1,
    "game": "minecraft",
    "versionId": version,
    "name": "DonutDeck",
    "summary": "Client-side DonutSMP economy terminal: market intelligence, manual-confirm trading, advisory auto-invest, and opt-in broadcasts. No automation, no auto-accept, no telemetry.",
    "files": [
        entry(fabric_path, fabric_url, fabric_sha1),
        entry(opsec_path, opsec_url, opsec_sha1),
    ],
    "dependencies": {
        "minecraft": "1.21.11",
        "fabric-loader": "0.20.1",
    },
}

out.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    z.writestr("modrinth.index.json", json.dumps(index, indent=2) + "\n")
    z.write(mod_jar, f"overrides/mods/donutdeck-{version}.jar")
    for name in ("README.md", "LICENSE"):
        p = root / name
        if p.exists():
            z.write(p, f"overrides/{name}")
    docs = root / "docs"
    if docs.is_dir():
        for p in sorted(docs.glob("*.md")):
            z.write(p, f"overrides/docs/{p.name}")

print(f"Wrote {out}")
print(f"  size: {out.stat().st_size:,} bytes")
print(f"  sha1: {sha1(out)}")
with zipfile.ZipFile(out) as z:
    names = z.namelist()
    required = {"modrinth.index.json", f"overrides/mods/donutdeck-{version}.jar"}
    missing = required - set(names)
    if missing:
        raise SystemExit(f"pack validation failed, missing: {sorted(missing)}")
    json.loads(z.read("modrinth.index.json"))
    print(f"  entries: {len(names)} (validated)")
PY
