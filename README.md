<div align="center">

# 6Tools Addon

**A feature-packed [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) addon for Fabric 1.21.11, built for the 6b6t anarchy server.**

[![Minecraft](https://img.shields.io/badge/minecraft-1.21.11-green)](https://www.minecraft.net/)
[![Release](https://img.shields.io/github/v/release/gameeguy11/6ToolsAddon)](https://github.com/gameeguy11/6ToolsAddon/releases)
[![Downloads](https://img.shields.io/github/downloads/gameeguy11/6ToolsAddon/total)](https://github.com/gameeguy11/6ToolsAddon/releases)
[![Stars](https://img.shields.io/github/stars/gameeguy11/6ToolsAddon)](https://github.com/gameeguy11/6ToolsAddon/stargazers)
[![Issues](https://img.shields.io/github/issues/gameeguy11/6ToolsAddon)](https://github.com/gameeguy11/6ToolsAddon/issues)

[![Discord](https://invidget.switchblade.xyz/fdHkyVYc8)](https://discord.gg/fdHkyVYc8)

[Install](#installation) · [Build](#building) · [Credits](#credits)

</div>

---

## What it adds

| Type | Count |
|---|-------|
| Modules | 50    |
| Commands | 16    |
| HUD elements | 9     |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and the matching [Meteor Client](https://meteorclient.com/archive).
2. Download the latest jar from the [releases page](https://github.com/gameeguy11/6ToolsAddon/releases).
3. Drop it into your `mods` folder and launch the game.

### Requirements

| Component | Version |
|---|---|
| Fabric Loader | 0.18.3+ |
| Meteor Client | current 1.21.11 snapshot, see [maven.meteordev.org/snapshots](https://maven.meteordev.org/snapshots/meteordevelopment/meteor-client/) |
| Baritone | optional, a build matching your [Meteor Client](https://meteorclient.com/archive) version, see [Baritone](https://github.com/cabaletta/baritone) |
| Baritone (upgrade) | optional, the updated fork [dekrom/baritone v1.3.0-1.21.11](https://github.com/dekrom/baritone/releases/tag/v1.3.0-1.21.11), supported alongside the older build |
| JDK | 21 (only for building) |


## Chat

A built-in chat between 6Tools users, separate from the server's chat. Open it from the
**Chat** tab in Meteor's GUI (next to Enemies) or with `.chat` (alias `.6c`).

- **General**: one public channel shared by everyone connected to the relay. Messages are
  plain text.
- **Friend DMs**: only with players on your Meteor friends list, and only from them: DMs
  from non-friends are dropped. They are end-to-end encrypted (a fresh RSA-2048 key per
  session wraps an AES-GCM key per message), so the relay only forwards ciphertext. DMs
  need the other person to be online; nothing is stored.
- **Relay**: a small Cloudflare Worker (`worker/`) that forwards messages. It connects
  automatically when you join a world (`autoConnect`). Names are verified through Mojang's
  session server, so nobody can pose as a premium account; cracked accounts can still
  connect as unverified names.
- **Filters**: you never see general messages from muted players or from anyone on your
  Enemies list. `.chat mute` is local to your client, nothing is sent to the relay.
- **Notifications**: while the chat screen is closed, new messages are echoed in the main
  chat and unread counts show on the conversation tabs. `.chat echo` toggles the echo.
- **Rate limits**: 5 message burst, then about 1 per second; the same message can't be
  repeated within 5 seconds; max 256 characters.
- **Self-hosting**: deploy `worker/` with Wrangler, set `ADMINS` in `worker/wrangler.toml`
  to your own Minecraft UUID(s), then point clients at it with `.chat url wss://...`.
  `ADMINS` only matters on the relay you host: the server checks the admin's Mojang-verified
  UUID, so editing the client or the open source code can't grant admin on someone else's
  relay.

Settings live in `.minecraft/config/sixtoolsaddon/chat.json` (`relayUrl`, `autoConnect`,
`echo`, `muted`).

## Discord Webhook Setup

Discord Notifier needs a webhook URL before it can send anything. A webhook is a link tied
to one specific Discord channel, anything posted to it shows up as a message in that
channel, it isn't tied to a Discord account or bot.

1. In Discord, open the server/channel you want chat forwarded to.
2. Go to that channel's settings → **Integrations** → **Webhooks** → **New Webhook** (or
   **Create Webhook**).
3. Give it a name/avatar if you want, then click **Copy Webhook URL**. It looks like
   `https://discord.com/api/webhooks/123456789012345678/AbCdEf...`.
4. In Minecraft, run `.setdiscord set <paste the URL here>`.
5. Enable the **Discord Notifier** module and turn on `send-chat` and/or `send-coords`,
   whichever you want forwarded.

The URL is only ever stored locally in your Meteor config, it's never shown back in chat
or logged, and `.setdiscord set` validates that what you paste actually looks like a
Discord webhook URL before accepting it. Run `.setdiscord clear` any time to remove it
(the module just stops sending, no need to disable it first).

Treat the webhook URL like a password, anyone who has it can post messages into that
Discord channel. If you ever want to revoke it, delete the webhook from that channel's
Integrations settings in Discord and create a new one.

---

## Building

```
./gradlew build
```

The output jar lands in `build/libs/`, versioned as `6ToolsAddon-<major>.<minor>.<patch>.jar`
(the version auto-increments on every build, see the top of `build.gradle.kts` if you want
to reset or change that). Drop the built jar into your `mods` folder alongside a matching
Meteor Client build.

`gradle/libs.versions.toml` is the single place to bump Minecraft/Yarn/Loader/Loom/Meteor
versions if any of them move.

## Credits

- **Homes** and the underlying base system are adapted from
  [Powie69](https://github.com/Powie69)'s **6Bees** addon:
  <https://github.com/Powie69/6Bees>. Full credit for the original design goes to Powie69.
- **Efly**, the elytra-flight logic, is [Volizray](https://github.com/Volizray)'s own
  **VolytraFly**: <https://github.com/Volizray/VolytraFly-Addon>. 
- **Whisper Logger** is adapted from [Plumbiller](https://github.com/Plumbiller)'s
  [PlumbillerPublic](https://github.com/Plumbiller/PlumbillerPublic) addon.
- **Chest related modules** are adapted from [BepHaxAddon](https://github.com/dekrom/BepHaxAddon).
- **Meteor Fix**, one of the Meteor bug fixes used Fractal420's
  [Meteor-GUI-Position-Fix](https://github.com/Fractal420/Meteor-GUI-Position-Fix).
- **Lucky1821**
- **Wither Print** is based on [ComicSquid74273](https://github.com/ComicSquid74273)'s
  Wither printer: <https://github.com/ComicSquid74273/Wither-printer>.

## License

MIT, see [LICENSE](LICENSE). You're free to use, modify, and redistribute this addon as
long as the original copyright notice and the credits above are kept.

[discord]: https://discord.gg/HX6rSFg3k
[vidget-discord]: https://invidget.switchblade.xyz/fdHkyVYc8
