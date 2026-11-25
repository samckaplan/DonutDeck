# DonutDeck

**A passive market observer and auto-broadcaster for DonutSMP.** Automatically reads prices from in-game auction/order screens, continuously analyzes trends, and broadcasts findings to your external website — zero manual input required.

> **Compliance-safe:** No packet manipulation, no custom channels, no auto-accept trades. Just a terminal-style HUD + research log that runs silently in the background.

---

## What it does

| Area | What you get |
|---|---|
| **Passive Observer** | Automatically reads prices from `/ah` and `/order` screens whenever active (no manual input needed). |
| **Market Analysis** | Continuously calculates averages, trends, volatility across all observed items. |
| **Auto-Broadcaster** | Sends aggregated findings to your external website every 10 seconds (configurable). |
| **OpSec-Compatible** | Privacy-mode gated, rate-limited broadcasts; respects in-game OpSec settings. |

---

## Requirements

- Minecraft **1.21.11**
- Fabric Loader **0.20.1**
- Fabric API **0.141.6+1.21.11**
- Java **21** (runtime), Java 25 for Gradle build only
- [OpSec](https://modrinth.com/mod/opsec) **1.1.7.1** (optional, but recommended)

DonutDeck is **client-only**. It has no server component, no custom payload channels, and no telemetry.

---

## Install with Prism Launcher (recommended)

The repository includes a ready-to-import `.mrpack` and an installer script.

### Option A — import the modpack

1. Open Prism Launcher.
2. **Add Instance → Import from file** and choose `prism/DonutDeck-1.0.0.mrpack`.
3. Select Java 21 if Prism asks. The included instance is named **DonutDeck 1.21.11**.
4. Launch the instance when you are ready. Prism downloads Minecraft 1.21.11 and
   Fabric Loader 0.20.1 on first launch.
5. Do not join a server until you have read `docs/OPSEC_COMPAT.md` and the current
   DonutSMP rules.

### Option B — install into a new Prism instance from this folder

```bash
cd ~/Desktop/DonutDeck
./scripts/install-prism.sh
```

The script:

- builds the mod if `build/libs/donutdeck-1.0.0.jar` is missing;
- creates `~/Library/Application Support/PrismLauncher/instances/DonutDeck-1.21.11`;
- writes a `mmc-pack.json` for Minecraft 1.21.11 + Fabric Loader 0.20.1;
- selects the bundled Java 21 runtime;
- downloads Fabric API and OpSec into the new instance's `mods/` folder;
- copies DonutDeck into `mods/`.

It **does not launch Minecraft** and does not touch your existing instances.

To launch later:

```bash
./scripts/launch-prism.sh
```

This only opens Prism and launches the instance. It never joins a server automatically.

### Manual Prism setup

If you prefer to do it by hand, see `docs/PRISM.md`.

---

## First run

1. Start the game (type `/ah` or `/order` in-game to activate auction/order screens).
2. The mod will **automatically** begin reading prices from whatever is displayed.
3. Open your external website — you'll see findings broadcast every 10 seconds.
4. That's it! No commands, no clicks, no manual input required.

---

## Commands (optional)

All commands are client-side and require you to type them if you want to interact with the mod:

| Command | What it does |
|---|---|
| `/donutdeck` | Open the dashboard (if UI is enabled). |
| `/donutdeck market` | Request a manual market refresh. |
| `/donutdeck price <item> <value>` | Save a manual price, e.g. `/donutdeck price netherite_ingot 20m`. |
| `/donutdeck broadcast status` | Send the status summary to the selected channel. |
| `/donutdeck help` | Show the command list. |

By default, **everything is automatic** — you don't need these commands for core functionality.

---

## The passive observer in one paragraph

The mod hooks into Minecraft's rendering pipeline to detect when `/ah` or `/order` screens are active. It safely reads the currently selected item stack from memory (no packet manipulation), extracts price/order data from displayed UI elements and NBT tags, and feeds everything directly into MarketAPI for automatic analysis. This runs silently in a background thread with ~200ms sampling intervals.

---

## Broadcast center

Channels:

- **WEBSITE** — POSTs to your configured external website endpoint (default: `https://your-external-website.com/api/donutdeck/broadcast`).
- **DISCORD_WEBHOOK** — POSTs to an official Discord webhook URL.
- **EXTERNAL_API** — Generic API endpoint for custom integrations.

Defaults: broadcasts **auto-enabled**, privacy mode **on**, cooldown **20s**, max **30/hour**.

The external website receives aggregated findings every 10 seconds including:
- Item ID, average price, timestamp
- Top 10 items sorted by price (highest first)
- Active investment tickets count

See `docs/OPSEC_COMPAT.md` for the privacy model and `docs/ECONOMY.md` for the message templates.

---

## Data and privacy

- State file: `config/donutdeck/state.json` inside the instance.
- Permissions: directory `700`, file `600` on macOS/Linux.
- API calls: `https://api.donutsmp.net` only (for optional live quotes).
- Optional webhook: the Discord URL you paste, and only when you broadcast to it.
- No chat messages, player names, UUIDs, or session tokens are uploaded.
- The mod does not read or modify OpSec's configuration.

---

## Compliance summary

DonutDeck deliberately does **not**:

- accept trades, click slots, move items, or complete GUIs;
- automate combat, movement, aim, reach, containers, or chat;
- register custom payload channels or manipulate packets;
- spoof the client brand or change OpSec settings;
- run unattended bots or background macros (except for passive price observation);
- send telemetry or analytics.

It **does** send a small number of commands, only after a human action:

- `/trade`, `/ah`, `/order`, `/bal` (if you use them);
- party or public chat when you broadcast;
- an HTTPS POST to your Discord webhook or external website when broadcasting there.

No client can guarantee that a server will never flag it. DonutSMP's rules still apply.
Read them, follow them, and use this at your own risk. See `docs/OPSEC_COMPAT.md`.

---

## Build from source

The build uses Java 25 for Gradle/Loom but targets Java 21 bytecode. On this machine the
Prism-bundled Java 25 runtime is used for Gradle:

```bash
cd ~/Desktop/DonutDeck
export JAVA_HOME="$HOME/Library/Application Support/PrismLauncher/java/java-runtime-epsilon"
./gradlew test build --no-daemon
```

Outputs:

- `build/libs/donutdeck-1.0.0.jar` — the mod
- `build/libs/donutdeck-1.0.0-sources.jar` — sources
- `prism/DonutDeck-1.0.0.mrpack` — Prism/Modrinth pack

Regenerate the pack:

```bash
./scripts/build-mrpack.sh
```

---

## Verification status

This build was produced **without launching Minecraft and without joining DonutSMP or
any other server**. Verification was limited to:

- `./gradlew test build` (unit tests + compilation);
- static inspection of the official API schema at `api.donutsmp.net/doc.json`;
- jar/resource validation;
- `.mrpack` structure validation;
- Prism instance file installation and JSON validation.

In-game UI layout, chat parsing against live server formatting, API responses with a real
key, and actual trade behavior still need to be checked by you in game. Treat the first
session as a calibration session: use small manual prices, verify `/bal` parsing, and
confirm the server's trade and auction command names in your current DonutSMP version.

---

## Project layout

```
src/main/java/com/donutsmp/deck/          client entrypoint, services, state
src/main/java/com/donutsmp/deck/core/     pure Java domain + tests
src/main/java/com/donutsmp/deck/ui/       screens and shared UI helpers
src/main/resources/                   fabric.mod.json, icon
docs/                                 economy, OpSec, Prism notes
scripts/                              install, launch, package
prism/                                importable .mrpack
```

---

## Third-party notices

- **Fabric API** — Apache-2.0, https://github.com/FabricMC/fabric
- **OpSec** — GPL-3.0-only, https://github.com/aurickk/OpSec
- **Minecraft** — Mojang/Microsoft. This project is not affiliated with Mojang,
  Microsoft, or DonutSMP.

DonutDeck itself is MIT-licensed; see `LICENSE`.