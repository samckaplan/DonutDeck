#!/usr/bin/env bash
# Open Prism Launcher and launch the DonutDeck instance.
# This only launches Minecraft; it never adds a server address or joins a world.
set -euo pipefail

PRISM_APP="${PRISM_APP:-/Applications/Prism Launcher.app}"
INSTANCE_ID="${INSTANCE_ID:-DonutDeck-1.21.11}"

if [[ ! -d "$PRISM_APP" ]]; then
	printf 'error: Prism Launcher not found at %s\n' "$PRISM_APP" >&2
	exit 1
fi

printf 'Launching Prism instance: %s\n' "$INSTANCE_ID"
printf 'No server address is passed. Join a server yourself after the game opens.\n'
open -a "$PRISM_APP" --args --launch "$INSTANCE_ID"
