# OpSec compatibility and compliance

DonutDeck is built to coexist with **OpSec** without hooking it, reconfiguring it, or
depending on it. This page states exactly what the mod does, what it does not do, and what
still depends on the server and on you.

> **No client can guarantee that a server will never flag it.** DonutSMP's rules and
> enforcement are outside this project's control. This page is a factual description of
> the mod's behavior, not a promise of safety.

---

## 1. What "OpSec-compatible" means here

- DonutDeck detects OpSec through Fabric Loader (`opsec` mod id) and shows the detected
  version in the Compliance Center and onboarding card.
- DonutDeck does **not** read, write, or modify OpSec's config.
- DonutDeck does **not** register a custom payload channel, a packet listener, a packet
  sender, a brand changer, or a mixin into OpSec.
- DonutDeck does **not** require OpSec to function. OpSec is optional.
- The included Prism setup installs both mods because that is the configuration you asked
  for. You can remove OpSec and DonutDeck will still run (the Compliance Center will show
  "OpSec not detected").

OpSec itself contains features such as client-brand spoofing. Those are OpSec's features,
not DonutDeck's. DonutDeck does not enable, disable, or rely on them. Follow OpSec's own
documentation and the server's rules when configuring it.

---

## 2. Network behavior

DonutDeck makes exactly these outbound connections:

| Destination | When | Contents |
|---|---|---|
| `https://api.donutsmp.net` | Market refresh (manual or scheduled) | Bearer API key, item search text, page number |
| Your Discord webhook | Only when you broadcast to `DISCORD` | The broadcast message, `username: DonutDeck`, mentions disabled |
| Nothing else | — | No telemetry, analytics, crash reporting, cloud sync, or update pings |

The API key is stored locally in `config/donutdeck/state.json` with owner-only
permissions. It is not placed in chat, item lore, the HUD, the broadcast log, or a Discord
message.

The mod does not send chat or commands on a timer. It does not run a background bot. The
only scheduled network work is the market refresh, and it only runs while the client is
open and the API is enabled.

---

## 3. Outbound game actions

Every outbound command is triggered by a human action:

| Action | Trigger | Guard |
|---|---|---|
| `/trade <name>` | Trade desk **SEND** + **CONFIRM** | 6-second arm window, 1.2-second command cooldown, duplicate-command block |
| `/ah <item>` | Trade desk **SEND** + **CONFIRM** | Same |
| `/order <item>` | Trade desk **SEND** + **CONFIRM** | Same |
| `/bal` | `/donutdeck balance` | Command cooldown |
| Party chat | Broadcast Center (or explicit command) | Privacy mode, broadcasts-enabled, cooldown, hourly cap |
| Public chat | Broadcast Center (or explicit command) | The above plus the explicit public-chat permission |
| Discord webhook | Broadcast Center (or explicit command) | The above plus official-host validation |

DonutDeck never:

- accepts a trade request;
- clicks a trade/auction/container slot;
- moves an item;
- closes a GUI;
- sends a chat reply to a trade request;
- repeats a command because a timer elapsed;
- runs while you are AFK as an unattended bot.

Incoming trade requests are read from chat and shown for manual review. The mod may show a
local notification, but it does not respond to the requester.

---

## 4. Two-step trade confirmation

`TradeService.send(id)` works like this:

1. First call: if the intent is not armed, arm it for 6 seconds and tell you to click
   again. Nothing is sent.
2. Second call inside the window: check the command cooldown and duplicate-command guard,
   clear the arm, send exactly one command, and mark the intent `SENT`.
3. If the window expires, you must start over.
4. The server's trade GUI is completed by you. DonutDeck does not interact with it.
5. **FILL** is a separate two-click action that records the completed trade. **CANCEL**
   records a cancellation.

`/donutdeck broadcast ...` is an explicit typed command and sends in one step, subject to
the same privacy/channel/rate gates. If you want the second confirmation click, use the
Broadcast Center buttons.

---

## 5. Rate limits and anti-spam

- Command cooldown: **1.2 seconds**.
- Duplicate command guard: the same command string within **8 seconds** is blocked.
- Broadcast cooldown: **20 seconds** by default (minimum 10).
- Broadcast hourly cap: **12 per hour** by default (maximum 30).
- Market refresh: **90 seconds** by default (minimum 30); manual refreshes have an
  **8-second** cooldown.
- API budget: up to 18 item searches + 5 transaction pages per refresh, far below the
  documented 250 requests/minute/key limit at the default interval.

These limits are deliberate. They make the mod unsuitable as a chat spammer or a macro.

---

## 6. DonutSMP rules

DonutSMP's rules ban, among other things:

- macros, scripts, auto-clickers, and unattended bots;
- off-platform trading;
- packet manipulation and exploit abuse;
- chat spam.

DonutDeck is a manual UI, calculator, and order desk. It does not automate gameplay. It
does not move items or accept trades. It does not bypass the server's trade GUI. It does
not facilitate off-platform trading; the Discord webhook is a broadcast destination, not a
trade settlement channel.

Read the current rules before using any client mod. Rules can change. If a rule conflicts
with this mod, follow the rule.

---

## 7. Privacy modes

| Setting | Default | Effect |
|---|---|---|
| Privacy mode | **on** | Forces `LOCAL`; blocks party, public, and Discord broadcasts. |
| Broadcasts enabled | **off** | Master switch for external channels. |
| Public chat permission | **off** | Extra gate before `PUBLIC` can be selected. |
| Auto-broadcast marked fills | **on** | One rate-limited message per fill, but only if broadcasts are enabled. |
| Auto-broadcast invest plans | **off** | One rate-limited message when you click **Run plan**, if enabled. |
| Full portfolio details | **off** | Three holdings instead of six in portfolio broadcasts. |

The webhook URL is stored locally. Treat it as a secret: anyone with the URL can post to
that channel. Clear the field if you no longer use it.

---

## 8. What is not protected

- **Server-side detection is out of scope.** The mod does not attempt to evade
  anti-cheat. It avoids automation and packet manipulation; it does not promise that a
  server will never flag a client for any reason.
- **OpSec's own behavior is out of scope.** DonutDeck does not validate or change OpSec's
  settings.
- **Your broadcasts are public to their audience.** Party chat, public chat, and Discord
  messages can be screenshotted, logged, or shared by others.
- **Your manual prices can be wrong.** The mod trusts what you enter. Verify before you
  queue a ticket.
- **The API can change.** Endpoints, fields, and limits are controlled by DonutSMP. A
  future change may require a mod update.

---

## 9. Verification status

This build was **not tested in game and not tested on DonutSMP or any other server**.
Compatibility with OpSec was checked statically:

- no shared mixins or payload channels;
- no references to OpSec classes;
- only Fabric Loader's public `isModLoaded` / metadata API;
- no custom networking, brand, or packet code in the mod.

You should still do a first-run check in a safe context: confirm the mod loads, the
Compliance Center detects OpSec, the API key works, and the server's command names match
your current DonutSMP version. Use small manual prices and a small trade first.
