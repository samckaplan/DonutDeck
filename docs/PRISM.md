# Prism Launcher setup

This project was prepared for **Prism Launcher** on macOS. The installer creates a new
instance named **DonutDeck 1.21.11** and leaves your existing instances alone.

> The installer does **not** launch Minecraft. Prism downloads Minecraft 1.21.11 and
> Fabric Loader 0.20.1 the first time you launch the instance. DonutDeck never joins a
> server automatically.

---

## 1. Paths used on this machine

| Thing | Path |
|---|---|
| Prism data | `~/Library/Application Support/PrismLauncher` |
| New instance | `~/Library/Application Support/PrismLauncher/instances/DonutDeck-1.21.11` |
| Instance mods | `.../instances/DonutDeck-1.21.11/minecraft/mods` |
| Java 21 runtime | `.../java/java-runtime-delta/bin/java` |
| Java 25 runtime (Gradle only) | `.../java/java-runtime-epsilon/bin/java` |
| Prism app | `/Applications/Prism Launcher.app` |

The instance folder is `DonutDeck-1.21.11`; the display name is `DonutDeck 1.21.11`.
Prism's `--launch` flag uses the folder id.

---

## 2. One-command install

```bash
cd ~/Desktop/DonutDeck
./scripts/install-prism.sh
```

What it does:

1. Checks for `build/libs/donutdeck-1.0.0.jar`. If missing, runs the Gradle build with the
   Java 25 runtime.
2. Creates the instance directory and `minecraft/mods`.
3. Writes `instance.cfg` and `mmc-pack.json` if they do not already exist.
4. Downloads Fabric API and OpSec into the instance's `mods/` folder.
5. Copies the DonutDeck jar into `mods/`.
6. Prints the next steps.

Overrides:

```bash
PRISM_DIR="/path/to/PrismLauncher" \
INSTANCE_ID="MyDeck" \
INSTANCE_NAME="My Deck" \
./scripts/install-prism.sh
```

The script refuses to overwrite an existing `mmc-pack.json` or `instance.cfg`; it will
still copy the mods. Delete the instance folder yourself if you want a clean reinstall.

---

## 3. Manual install

If you prefer Prism's UI:

1. **Add Instance → Vanilla → 1.21.11**.
2. After it is created, open **Edit → Version** and add **Fabric Loader 0.20.1**.
3. Set **Java → Override → Java 21** (the `java-runtime-delta` path above).
4. Open the instance's **Mods** folder and add:
   - `donutdeck-1.0.0.jar`
   - `fabric-api-0.141.6+1.21.11.jar`
   - `opsec-1.21.11+v1.1.7.1.jar`
5. Launch the instance when ready.

The exact files are in:

- `build/libs/donutdeck-1.0.0.jar`
- `build/deps/fabric-api-0.141.6+1.21.11.jar`
- `build/deps/opsec-1.21.11+v1.1.7.1.jar`

or the script will download the two dependencies for you.

---

## 4. Import the modpack instead

The packaged file is:

```
prism/DonutDeck-1.0.0.mrpack
```

In Prism: **Add Instance → Import from file**, select the `.mrpack`, and confirm. The pack
declares:

- Minecraft `1.21.11`
- Fabric Loader `0.20.1`
- Fabric API `0.141.6+1.21.11` (remote download)
- OpSec `1.1.7.1` (remote download)
- DonutDeck `1.0.0` (bundled in `overrides/mods`)

Prism will download the two remote mods on import/first launch. If you are offline, use
the script or copy the jars manually.

---

## 5. What should be in the instance

```
DonutDeck-1.21.11/
├── instance.cfg
├── mmc-pack.json
├── icon.png
└── minecraft/
    ├── config/                  (created on first run)
    │   └── donutdeck/
    │       └── state.json       (API key, settings, portfolio)
    └── mods/
        ├── donutdeck-1.0.0.jar
        ├── fabric-api-0.141.6+1.21.11.jar
        └── opsec-1.21.11+v1.1.7.1.jar
```

`mmc-pack.json` components:

```json
{
  "formatVersion": 1,
  "components": [
    { "uid": "org.lwjgl3", "version": "3.3.3", "dependencyOnly": true },
    { "uid": "net.minecraft", "version": "1.21.11", "important": true },
    { "uid": "net.fabricmc.intermediary", "version": "1.21.11", "dependencyOnly": true },
    { "uid": "net.fabricmc.fabric-loader", "version": "0.20.1" }
  ]
}
```

Prism may rewrite this file with cached metadata after the first launch. That is normal.

---

## 6. Launching later

```bash
./scripts/launch-prism.sh
```

or use Prism's UI and click **Launch**. The script only opens Prism and passes
`--launch DonutDeck-1.21.11`; it does not add a server address.

If you want to launch with a specific account, use Prism's normal account picker. Do not
put credentials in a script.

---

## 7. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Prism says Fabric Loader is missing | The instance was created as vanilla only | Edit the instance version and add Fabric Loader 0.20.1. |
| Game crashes on startup with a Java error | Wrong Java version | Set the instance Java to 21 (`java-runtime-delta`). |
| `fabric-api` dependency error | Fabric API jar missing or wrong version | Install `fabric-api-0.141.6+1.21.11.jar`. |
| DonutDeck not in the mod list | Jar not in `minecraft/mods` | Copy `build/libs/donutdeck-1.0.0.jar` there. |
| Compliance Center says "OpSec not detected" | OpSec jar missing or disabled | Install `opsec-1.21.11+v1.1.7.1.jar`; check the mod list. |
| Market status stays `NO DATA` | No API key, API disabled, or 401 | Run `/api` in game, paste the key, enable the API, click Refresh. |
| Market status `ERROR` | Key rejected, rate limited, or API changed | Read the status message; wait, re-check the key, or use manual prices. |
| No prices at all | Sample placeholders are hidden by design | Add a manual price or a valid API key. |
| HUD overlaps other mods | HUD position is fixed bottom-right | Turn it off in Settings or `/donutdeck hud`. |
| Buttons are off-screen | Very small GUI scale/window | Increase the window size or GUI scale. |

---

## 8. Safety notes

- Do not join a server until you have read `docs/OPSEC_COMPAT.md` and the current
  DonutSMP rules.
- The first session should be a calibration session: check `/bal`, `/trade`, `/ah`, and
  `/order` behavior on your current server version before using the desk for a real trade.
- Keep your API key and Discord webhook private. They are stored in
  `config/donutdeck/state.json`; the file is owner-only on macOS, but backups and
  screenshots can still leak it.
- DonutDeck does not launch, join, or play for you.
