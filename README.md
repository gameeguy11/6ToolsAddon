# 6Tools Addon

A [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) addon for Fabric
**1.21.11**, built for the 6b6t anarchy server.

[![Minecraft](https://img.shields.io/badge/minecraft-1.21.11-green)](https://www.minecraft.net/)
[![Release](https://img.shields.io/github/v/release/gameeguy11/6ToolsAddon)](https://github.com/gameeguy11/6ToolsAddon/releases)
[![Downloads](https://img.shields.io/github/downloads/gameeguy11/6ToolsAddon/total)](https://github.com/gameeguy11/6ToolsAddon/releases)
[![Stars](https://img.shields.io/github/stars/gameeguy11/6ToolsAddon)](https://github.com/gameeguy11/6ToolsAddon/stargazers)
[![Code Size](https://img.shields.io/github/languages/code-size/gameeguy11/6ToolsAddon)](https://github.com/gameeguy11/6ToolsAddon)
[![Issues](https://img.shields.io/github/issues/gameeguy11/6ToolsAddon)](https://github.com/gameeguy11/6ToolsAddon/issues)

[![Discord](https://invidget.switchblade.xyz/fdHkyVYc8)](https://discord.gg/fdHkyVYc8)
<!--
[![Discord](https://img.shields.io/discord/1504623086769537207?logo=discord&logoColor=white&label=Discord)](https://discord.gg/fdHkyVYc8)

<iframe src="https://discord.com/widget?id=1504623086769537207&theme=dark" width="350" height="500" allowtransparency="true" frameborder="0" sandbox="allow-popups allow-popups-to-escape-sandbox allow-same-origin allow-scripts"></iframe>
-->
#### Showcase: https://medal.tv/games/minecraft/clips/nz5q0SlHc9e2K63a6?invite=cr-MSwxcHEsMzIxOTEyMjI1

## Using this addon might give you 2 **FREE** homes

## Unblock Servers
Prevents known anarchy servers from being flagged as blocked by Mojang's server
blocklist, so you can actually connect to them.

## Requirements

| Component     | Version                                                                                                                                      |
|---------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| Fabric Loader | 0.18.3+                                                                                                                                      |
| Meteor Client | current 1.21.11 snapshot, check [maven.meteordev.org/snapshots](https://maven.meteordev.org/snapshots/meteordevelopment/meteor-client/)      |
| Baritone      | a build matching your [Meteor Client](https://meteorclient.com/archive) version when using [Baritone](https://github.com/cabaletta/baritone) |
| JDK           | 21                                                                                                                                           |
| AnarchyMod    | The mod is option but if you are using it make sure it's [1.4.3](https://6b6t.kianbrose.com/) |
---

## Modules

<details>
<summary> stash-mover</summary>

Automatically empties a whole "input" stash into an "output" stash: walks container to
container in the input area, pulls items out (optionally shulkers only), and repeats the
round trip until the input area has nothing left in it or you turn the module off.

- **General**: `container-reach`, max distance to open a container from. `pause-on-lag`
  pauses actions when the server is lagging. `max-retries`, retry cap for failed actions.
  `output-pickup-pos` / `output-throw-pos`, the two coordinates used for pearl pickup/throw
  at the output side (kept here so you can see and set them without digging into the
  pearl-loading group).
- **Input**: `only-shulkers`, only take shulker boxes out of input chests. `break-empty`,
  break a container once it's fully emptied. `fill-enderchest`, top off your ender chest
  first to squeeze in more items before making the trip.
- **Pearl Loading**: how the module travels from input to output. `output-travel-method`
  picks between `Pearl Loading`, `/home Command`, and `/tpa Command`. Pearl loading messages
  `pearl-player` with `pearl-command` (plus a random suffix) and waits up to `pearl-timeout`
  seconds, retrying every `pearl-retry-delay` ticks. The `/home`/`/tpa` methods instead send
  `/home <home-name-output>` or `/tpa <tpa-target-output>` and skip the pearl state machine
  entirely.
- **Go Back**: the return trip, output back to input. `go-back-method` adds `Kill` on top of
  the three above (yes, actually killing yourself to respawn back near the input area).
  Pearl uses `go-back-player`/`go-back-command`; `/home`/`/tpa` use `home-name-input`/
  `tpa-target-input`.
- **Reset Pearl**: fine control over the pearl-throwing itself when using Pearl Loading —
  `output-throw-pitch`/`output-throw-yaw`, `input-pickup-pos`/`input-throw-pos`,
  `input-throw-pitch`/`input-throw-yaw`, `pearl-wait-time` (how long to wait after
  throwing), `position-tolerance` (how close you need to stand before throwing), and
  `trapdoor-edge-distance`.
- **Delays**: `open-delay`, `transfer-delay`, `close-delay`, `move-delay`, all in ticks,
  tune these if items get skipped or the server can't keep up.
- **Rendering**: `render-selection` toggles outlines for the input/output areas and every
  container inside them, with `outline-width` and separate colors for the area outlines
  (`input-area-outline`/`output-area-outline`) and container states (`input-container-color`
  for containers with items left, `output-container-color` for containers with space,
  `active-container-color` for whichever one is currently open, `empty-container-color`,
  `full-container-color`).

Set the two areas first with `.setinput`/`.setoutput` (see Commands), then enable the
module. `.stashstatus` reports both areas and the current configuration; `.setclear` wipes
both selections if you need to start over.
</details>

<details>
<summary> chest-tracker</summary>

Remembers what's inside every chest, barrel, shulker box, ender chest, hopper, dispenser/
dropper (and copper chest, on modded servers) you've ever opened, so you can search for an
item across everything you've seen without re-opening a single container. Comes with an
in-game browser GUI (`.chesttracker` or the `browser-keybind`, default `Y`) that lists
every tracked container and its contents.

- **General**: `browser-keybind` opens the container browser GUI. `remove-destroyed`
  (on by default) forgets a tracked container, its outline and its items once the block is
  broken or replaced by another kind of container, or when a double chest loses or gains its
  other half (reopen it to track it again). It only checks chunks you currently have loaded,
  so containers far away are never removed by mistake.
- **Auto-Open**: `auto-open` automatically opens any untracked container within
  `auto-open-range`, waiting `auto-open-delay` ticks between each and `auto-close-delay`
  ticks after opening (so the server has time to send the full contents before it closes
  again). `use-baritone` lets it use Baritone to walk toward untracked containers that are
  outside interaction range, searching out to `baritone-search-radius` blocks; it keeps
  walking and opening until nothing untracked is left in range, or you disable auto-open.
- **Render**: `render-tracked` outlines every tracked container, `render-search-results`
  outlines only containers matching your current search, both within `render-distance`,
  drawn with `shape-mode` and the `tracked-color`/`search-color`/`search-line-color` colors.
- **Filter**: which container types get tracked at all — `track-chests`, `track-barrels`,
  `track-shulkers`, `track-ender-chests`, `track-hoppers`, `track-dispensers`,
  `track-copper-chests`.
- **Advanced**: `debug`, prints internal state transitions to chat.

Search with `.chesttracker search hand` (whatever's in your main hand) or
`.chesttracker search <item>`, browse everything nearby with `.chesttracker nearby
<radius>`, and manage the saved data with `.chesttracker clear all` / `.chesttracker clear
dimension` or export it with `.chesttracker export`. Data is saved locally and survives
restarts.
</details>

<details>
<summary> ItemSearchBar</summary>

Adds a live search field over your inventory and every container screen, highlighting
matching items as you type instead of hunting through slots by eye.

- **General**: `search-query`, `case-sensitive`, `split-queries` (treat commas as separate
  search terms instead of literal text), and where it searches — `search-item-name`,
  `search-item-type`, `search-lore`. `highlight-color` for matches. `inventory-highlight`
  highlights matches in your own inventory too, not just open containers.
  `chest-tracker-integration` feeds the same search into Chest Tracker automatically.
  `click-to-search-key`, hold this and click an item to instantly search for it.
- **Field**: `show-search-field` toggles the on-screen field itself; `field-width`/
  `field-height` and `offset-x`/`offset-y` (offset-y negative puts it above the container)
  position and size it.
- **Item Frames**: `highlight-item-frames` highlights item frames holding a matching item
  out in the world, with `fill-color`/`outline-color`, `render-fill`/`render-outline`
  toggles, and `tracers`/`tracer-color` to draw a line to each one.
</details>

<details>
<summary> auto-stash-sorter</summary>

Scans nearby chests and barrels once, then sorts your inventory by dropping each item into
whichever scanned container already holds the most of that item — a one-shot "put this
away where it belongs" rather than Stash Mover's input→output loop.

- `scan-radius`, how far to look for containers when the module starts.
  `include-barrels`/`include-trapped-chests` widen the scan beyond plain chests.
  `max-containers`, a safety cap on how many it scans in one run.
- `keep-hotbar`, leave your hotbar (slots 1-9) alone and only sort the main inventory.
  `keep-items`, item IDs (e.g. `minecraft:ender_pearl`) that never get put away.
  `fallback-to-emptiest-chest`, if nothing scanned already has the item, dump it in
  whichever scanned chest has the most free space instead of skipping it.
- `interact-delay`/`close-delay`, ticks to wait after opening/closing a container.
  `goto-timeout`, max ticks to spend walking to one container before giving up on it.
- `chat-feedback`, prints progress. `render-containers`/`scanned-color`, highlights every
  scanned container.
</details>

<details>
<summary> inventory-sorter</summary>

Saves a named snapshot of your inventory layout and auto-sorts items back into that exact
layout whenever it drifts, slot by slot rather than by container contents (see
`auto-stash-sorter` above for the one-shot "put it away" version). Controlled entirely
through the `.invsorter` commands (see Commands).

- **General**: `chat-notify`, chat message when an inventory is saved or finishes sorting.
  `tick-rate`, ticks between each slot move (higher is slower but less likely to trip
  anti-cheat). `auto-disable`, turn the module off by itself once sorting finishes instead
  of continuing to watch for drift.
- **Auto-Loot**: `auto-loot` — `Off` does nothing extra; `Refill` tops up whatever you
  already carry whenever you open storage while the module is active; `Rekit` pulls items
  belonging to a chosen saved inventory out of any storage you open, then arranges your
  inventory into that layout once you close it. `rekit-inventory`, which saved inventory
  Rekit targets.

Save and load layouts with `.invsorter save <name>` / `.invsorter load <name>`; manage them
with `.invsorter delete <name>`, `.invsorter clear`, and `.invsorter list`. Saved layouts
persist to `.minecraft/config/inventory-sorter/inventories.json`.
</details>

<details>
<summary> efly</summary>

Elytra-flight and movement module tuned for 6b6t. This is Volizray's VolytraFly,
repackaged (see Credits below).

To use it, enable the module and start gliding on an elytra. Key setting groups:
- **Mapping mode** controls how the module reads terrain to steer around it, plus a
  `render-radius` for how far ahead it looks.
- **Building mode** slows the module down near unloaded or newly placed blocks so it
  doesn't fly into something that just appeared.
- **Player/hazard avoidance** steers around nearby players, wither skulls, arrows, and
  blocks within a configurable radius, with an `ignore-friends` toggle so it won't dodge
  people on your friends list.
- **Anti-slam** slows the module down before hitting terrain at speed.
- **Auto-pilot** automatically fires fireworks to maintain speed, with configurable delay
  and a slot to pull fireworks from.
- **Elytra/firework management**: `elytra-replace` and `chest-swap` swap in a fresh elytra
  from your inventory or a nearby chest when durability runs low; `replenish-fireworks`
  keeps a hotbar slot topped up.

Every one of these settings has its own in-game description in the module's settings
panel, so if a specific value's behavior isn't obvious, check there first.
</details>

<details>
<summary> forever-forward</summary>

Holds the forward-movement key down for you, so you don't have to keep a finger on `W`.
One setting: `sprint`, also holds the sprint key down while active.
</details>

<details>
<summary> stripper</summary>

Strips and breaks logs automatically after you place the first one, then keeps placing
and stripping the next log along the same line.

- `axe-slot`, the hotbar slot (1-9) holding your axe.
- `strip-delay`/`break-delay`/`place-delay`, ticks to wait before each respective action.
- `rotation-time`, ticks to hold your look rotation before acting.
- `auto-mine`, also mine the stripped log instead of leaving it.
</details>

<details>
<summary> ez</summary>

Sends a message you write yourself when a nearby player dies or pops a totem. Ships with
no built-in messages, both the Kill Messages and Pop Messages lists start empty, so
nothing is sent until you fill them in yourself.

To use it: enable the module, open its settings, and add lines to `kill-messages` and/or
`pop-messages`. Use `<n>` in a message to have it replaced with the other player's name.

- **Kill attribution**: only fires when the game's own death message credits you
  specifically (it matches your exact name after "by" in the vanilla death message, e.g.
  "SomePlayer was slain by YourName"). This means your own deaths are correctly ignored.
  If your server uses non-English or custom death-message phrasing that doesn't follow
  the "by killer" pattern, attribution won't catch it.
- **Pop** (totem) is separate and not kill-attributed, it fires for any nearby non-friend
  player who pops a totem, regardless of who caused it. Off by default (`enabled` under
  Pop Messages).
- `range` sets how far away a death or pop is still detected; `delay` throttles repeated
  triggers.
</details>

<details>
<summary> csgo-spin</summary>

Rotates your view for you automatically, in either of two styles picked by `spin-mode`.

- **CSGO mode** (default): spins yaw and/or pitch in a ping-pong sweep. `yaw`/`yaw-speed`
  sweep left-right between -180° and 180°; `pitch`/`rotation-speed` sweep up-down. `anti-
  desync` pauses spinning while you're using things that need a steady look direction —
  `All` covers bows, ender pearls, XP bottles, elytra, EXPThrower and BedAura; `Except
  Elytra` covers everything except elytra; `None` never pauses.
- **CS2 mode**: continuously spins yaw at a constant `rotation-speed` (0-50), pausing only
  while EXPThrower or Quiver are active.
</details>

<details>
<summary> dub-counter</summary>

Counts how many double chests are nearby (see the `.dub` command below), driving the
Dub Counter HUD element. Also runnable as a standalone module if you just want the
counting logic active without displaying the HUD element.
</details>

<details>
<summary> Map Copier</summary>

Automatically duplicates all maps in your inventory using inventory crafting.

- `show-status`, prints status messages in chat.
- `silent-crafting`, tries to craft without opening your inventory (may not work on every
  server).
- `crafting-loops`, how many times to duplicate each map stack.
- `max-clicks-per-second`/`click-delay`, rate limiting to avoid getting kicked for
  clicking too fast.
</details>

<details>
<summary> Anti-Drop</summary>

Stops you from dropping certain items.

To use it, enable the module and, if you want to allow-list rather than block-list, add
items to `items`.

- `all-items`, blocks dropping completely.
- `items`, the specific item list to block (ignored if `all-items` is on).
- `check-shulkers`, also checks the contents of a shulker box you're trying to drop.
</details>

<details>
<summary> suicide</summary>

Kills yourself. Recommended.

- `disable-on-death`, turns the module off once you actually die.
- **Auto-Crystal**: `enabled`, places and pops an end crystal on yourself when triggered
  (requires standing on obsidian or bedrock); `delay-ticks` between each place/attack
  attempt. `enable-crystal-aura` uses Meteor's own Crystal Aura module instead of this
  built-in logic.
- **Trigger Conditions**: `wait-for-conditions`, only starts killing you once the
  conditions below are met instead of immediately on activation. `require-mode`, whether
  both conditions must be true or just one. `min-totems`, triggers once you have this many
  totems of undying or fewer (0 = none left). `min-health`, triggers once your health
  (including absorption) drops to this or lower.
</details>

<details>
<summary> respawn-point-blocker</summary>

Prevents setting respawn points by blocking bed and respawn anchor interactions.

- `block-beds`/`block-respawn-anchors`, which interactions to block.
- `chat-feedback`/`sound-feedback`, notify you when an interaction is blocked, with
  `feedback-sound` (only the first sound in the list plays) and `sound-volume`.
</details>

<details>
<summary> homes</summary>

Save areas as homes and protect them from teleport requests. Open the module's settings
and press `Manage Homes`.

- A home is a circle in X/Z only, all heights count. The radius is measured from the
  center, so radius 50 is a circle 100 blocks across.
- `default-radius`, radius used for new homes. `Apply radius to all existing homes` sets
  every saved home to it.
- Each home has its own name, coordinates, radius, and dimension. Nether and Overworld
  homes convert at 8:1, End homes only count in the End.
- `protect`, Auto TP Accept won't accept requests while you're inside this home.
- `deny-requests`, answer with `/tpn` instead of silently ignoring.
- `allow-friends`, friends can still teleport to you inside this home.

Homes are saved in `meteor-client/sixtoolsaddon-homes.json`. The home system is adapted
from Powie69's 6Bees addon.
</details>

<details>
<summary> auto-return-home</summary>

Auto-runs `/home` again after a cooldown if you get teleported into one of your saved
homes, so pearling/tpa'ing into a home resets your position cleanly instead of leaving you
wherever you landed.

- `default-cooldown`, default seconds to wait after landing in a home before auto-running
  `/home <name>` again. Individual homes can override this.
- `protected-homes-only`, only trigger for homes marked `protect` (see Homes above). Turn
  off to trigger for any saved home.
- `chat-feedback`, shows a countdown message when the timer starts and when it fires.
- `home-command`, the command sent to return home, `%name%` is replaced with the home's
  name.
</details>

<details>
<summary> auto-tpy</summary>

Automatically runs `/tpy <player>` when a teleport request comes in, since 6b6t requires
that manually.

To use it, enable the module and turn on who you want to accept.

- `accept-friends` (on by default), `accept-enemies`, `accept-everyone`, who gets
  auto-accepted. Friends come from Meteor's friends list, enemies from the Enemies tab
  and the `enemy-names` list.
- `deny-enemies`, `deny-friends`, answer requests from that group with `/tpn` instead.
- `enemy-names`, extra player names treated as enemies. Not case sensitive.
- `request-pattern`, the regex used to detect a teleport request and pull the requester's
  name out of it (capture group 1). Default matches 6b6t's actual `/tpa` notification
  ("&lt;name&gt; wants to teleport to you."), only needs changing on a different server.
- `chat-feedback`, prints who got auto-accepted, denied, or skipped in chat.

While you are standing inside a protected home (see Homes), requests are never accepted.
</details>

<details>
<summary> chat-highlight</summary>

Colors player names in chat: yourself, anyone on your Meteor friends list, and anyone on
a custom enemy list, each independently toggleable with its own color.

To use it, just enable the module, no other setup needed. Defaults already highlight all
three.

- `highlight-self`/`self-color`, colors your own name.
- `highlight-friends`/`friend-color`, colors names on your real Meteor friends list.
- `highlight-enemies`/`enemy-color`, colors names on the `enemy-names` list below.
- `enemy-names`, player names to treat as enemies. Not case sensitive, and separate from
  Auto TP Accept's and Player Tracker's own enemy lists (Meteor has no built-in enemy
  list, so each module that needs one keeps its own).
- `username-pattern`, the regex used to find player names in the part of a chat line before
  the message (capture group 1). Rank prefixes like `[Prime] Name » message` are skipped
  automatically: every name-like word before the `»`, `:` or `>` is checked, starting from
  the one closest to the message, and the first that matches one of your highlight rules wins.
  Adjust it if your server's chat format differs.
- `debug`, prints each chat line's exact characters (as unicode escapes) plus match/color
  info to help you tune `username-pattern` for your server.

If a name matches more than one category, priority is self > friend > enemy.
</details>

<details>
<summary> social-sync</summary>

Keeps your friends and enemies identical across **Meteor**, **Mio**, **RusherHack** and
**Lambda**, so adding or removing someone in one client shows up in the others. Meteor's
friends and this addon's enemies list (see Enemies List below) count as the Meteor side.

Enable the module and it works on its own: it never needs to be told which client you used
last. A client that isn't installed is simply skipped, so you can leave any of them out.
The module's settings window also has a **Sync now** button, and the module reports in chat
which clients it found when you turn it on.

**How each client is synced**

| Client | How | Notes |
|--------|-----|-------|
| Meteor | live, through the addon | friends and enemies |
| RusherHack | live, through RusherHack's plugin API | friends and enemies. Falls back to `.minecraft/rusherhack/config/relations.json` if the API isn't available |
| Lambda | live, in memory | friends only (Lambda has no enemies). Names are turned into UUIDs through Mojang |
| Mio | `.minecraft/mio-fabric/socials.json` plus a sync on exit | Mio keeps its list in memory and rewrites the file when it closes, so it can't be updated live |

Mio's file looks like `{"socials": [{"name", "role"}]}`, where role is `friend` or `enemy`.

**How it works**

Every `interval-ticks` (default 40, about 2 seconds) the module checks whether anything
changed in any client. If so, it works out what changed on each side and applies it everywhere.

To tell "you deleted Bob" apart from "this client never had Bob", the module saves a snapshot
of the last synced state in `meteor-client/sixtoolsaddon-social-sync.json`. Each client is
compared against that snapshot:

- A name in a client but not in the snapshot was **added** there, so it's added everywhere.
- A name in the snapshot but gone from a client was **removed** there, so it's removed everywhere.
- A name whose role differs from the snapshot (friend to enemy, or back) was **changed**
  there, so everyone follows.
- If two clients change the same name in different ways at once, `priority` decides. With
  priority `Off`, the order is Mio, then RusherHack, then Meteor.

Names are matched case-insensitively, and a name is only ever a friend or an enemy, never both.

**Settings**

- `priority`, `Off` (default), `Meteor`, `Mio`, `RusherHack` or `Lambda`. The chosen client wins
  when the same name is changed in several places at once. A change made in that client always
  goes through everywhere.
- `overwrite-others`, shown when a priority is set. Makes the priority client the source of
  truth: the other lists are overwritten to match it exactly, including removing entries only
  they have. Because Mio only saves on exit, avoid this with priority `Mio` unless you manage
  your friends in Mio.
- `interval-ticks`, how often to check for changes.
- `sync-on-exit` (on by default), waits for Mio's own write when the game closes, then merges
  everything back into Mio's file so the next launch has the right list. It only merges and
  never mirrors, even when a priority is set. A crash or force-kill skips it.
- `mio`, `rusherhack`, `lambda`, turn syncing with each client on or off.
- `mio-file`, `rusherhack-file`, full paths to Mio's `socials.json` and RusherHack's
  `relations.json`. Leave empty to auto-detect inside the game folder (useful for launchers
  with separate instances).
- **Sync now** button, runs a full sync immediately. With a priority set, it mirrors everything
  to that client.

**Safety**

- A client that is new to the sync, or whose list is missing, never counts as "deleted
  everything". A new client's names are added, and a missing file is skipped.
- On the first check after startup, if a list is empty while the snapshot isn't, that check
  is skipped instead of wiping the other lists.
- Before the first write each session the module copies Mio's and RusherHack's files to
  `.bak` files next to them.
- File writes go to a temporary file first and are then moved into place, so a client never
  reads a half-written file. Fields the module doesn't manage (like RusherHack's `alias`) are
  kept as they are.

**Limitations**

- Lambda needs a real account name to find a UUID. Cracked names that Mojang doesn't know stay
  pending and never reach Lambda.
- Mio can't be updated while it's running. A friend added in another client shows up in Mio
  after you close the game and start it again.
- If you upgraded from the old `mio-sync` module, your sync history is picked up automatically
  from `sixtoolsaddon-mio-sync.json`.
</details>

<details>
<summary> death-logger</summary>

Logs the coordinates and dimension of every death to a local text file, and gives you a
`View Death Log` button right in the module's settings panel to browse past entries
without leaving the game.
</details>

<details>
<summary> discord-notifier</summary>

Forwards chat, and optionally death/kill info, to a Discord webhook, so you can keep an
eye on things without the game open. Independent toggles, mix and match:
- **Chat itself** (`send-chat`), every chat message, optionally excluding your own.
- **Coordinates** (`send-coords`), messages from other players that contain coordinates,
  tagged `[Coords]`.
- **Your death coordinates** (`send-death-coords`), where you died, tagged `[Death]`.
- **Players you killed** (`send-kills`), tagged `[Kill]`.
- **Who killed you** (`send-killed-by`), tagged `[Killed By]`.

These can overlap (e.g. a coordinate message with `send-chat` also on gets sent twice,
once plain and once tagged), since these are independent toggles.

Setup: see the **Discord Webhook Setup** section below.

- `send-chat`, forwards every chat message.
- `send-coords`, forwards messages containing coordinates from other players.
- `ignore-own-messages`, skips your own lines (matched by the vanilla `<YourName>` chat
  prefix, so a server with a custom chat format may not catch them, turn this off if so).
- `coord-pattern`, the regex used to detect coordinates in a message, adjust it if your
  server shares coordinates in an unusual format.
- `send-death-coords`/`send-kills`/`send-killed-by`, the death/kill toggles above.
- `death-pattern`, the regex used to detect death messages and pull out who died
  (`<victim>`) and who killed them (`<killer>`, if the death had an attacker). The default
  covers vanilla's common death messages and skips rank prefixes; adjust it if your server
  rewords them. If you changed this setting in an older version, reset it to pick up the
  fixed default.

Messages are queued and sent in batches every 2 seconds rather than instantly, so a busy
chat doesn't spam or rate-limit your webhook.
</details>

<details>
<summary> whisper-logger</summary>

Keeps a running, Discord-styled HTML archive of your whisper conversations, one file per
person you've messaged. Adapted from Plumbiller's addon, see Credits.

Enable the module and it logs automatically, no further setup needed unless your server's
whisper wording differs from 6b6t's.

- Every whisper you send or receive gets appended to a local `.html` file, styled to look
  like a Discord DM thread rather than plain chat text.
- Files live under `.minecraft/config/sixtoolsaddon/WhisperLogs/`, named after the other
  person in the conversation.
- The two format settings (`receive-format`/`send-format`) tell it how to recognize a
  whisper in chat, so you can adjust them to match your server's `/msg` or `/tell` wording
  if you're not on 6b6t. Rank tags like `[Prime]` are ignored, and a line is only logged
  when the `{player}` part is a single valid username, so a format that is too loose can't
  turn ordinary chat into log files.

- `receive-format`, pattern for incoming whispers (`{player}`/`{message}` placeholders).
- `send-format`, pattern for whispers you send.
- `time-format`, timestamp style shown next to each logged message.
</details>

<details>
<summary> shulker-view</summary>

Shows shulker box contents in a live preview overlay while your inventory is open, no need
to actually open each shulker box. Ported from cattyngmd/shulker-view, see Credits.

Just enable the module and open your inventory, previews appear automatically next to any
shulker box shown in your inventory.

**General**
- `compact`, merges stacks of the same item and hides empty slots.
- `both-sides`, once previews fill one side of the screen, continues them on the other.
- `tooltips`, shows the normal item tooltip when hovering an item in a preview.
- `scale`, preview size, in tenths (10 = normal size).

**Background** (customizable)
- `background-color`, full RGBA color picker for the preview background. Set alpha to 0
  for no background at all.

**Position** (customizable)
- `anchor-right`, starts drawing previews from the right edge of the screen instead of the
  left. With `both-sides` on, overflow spills to whichever edge you didn't anchor to.
- `offset-x`, extra horizontal offset in pixels, measured inward from whichever edge
  previews are anchored to.
- `offset-y`, extra vertical offset in pixels, measured down from the top of the screen.

Click a preview to pick up that shulker box (same as vanilla slot-click behavior); scroll
to pan through previews that overflow the screen height.

Shulker View is a plain module, not a HUD element, so it can't be dragged around in
Meteor's HUD editor, `offset-x`/`offset-y`/`anchor-right` are how you reposition it
instead.
</details>

<details>
<summary> sound-editor</summary>

Plays **your own** sound files for addon events - nothing is bundled, so a sound type stays
silent until you put a file in its folder. Folders are created automatically in
`.minecraft/config/sixtoolsaddon/sounds/`:

| Folder | Plays when |
|--------|------------|
| `gui_hover/` | the mouse moves onto a module button in Meteor's click GUI |
| `gui_click_left/` / `gui_click_right/` | you left / right click a module button |
| `module_on/` / `module_off/` | you switch any module on / off |
| `typing/` | you press a key with the chat box open |
| `chat_keyword/` | a chat message contains one of your `keywords` |
| `enemy_spotted/` | a player on your enemies list renders in |
| `death/` | you die |

Supports `.ogg` and `.wav`. Put as many files as you like in one folder, then per sound type
choose **Random**, **Sequential** or **Specific** (with a file name). The module's window has a
file list for every folder with **Play** and **Use** buttons, plus *Open sounds folder* and
*Reload files*. Each type also has volume, pitch, pitch-variation and cooldown settings.
The module must be enabled for any sound to play. `enemy_spotted/` only reacts to players on
your enemies list (`.enemy add <name>`); `death/` needs nothing extra.
</details>

<details>
<summary> MusicTweaks</summary>

Lets you mess with the background music: change what plays, adjust the pitch, and control
how often a new song starts.

- `start-on-enable`/`stop-on-disable`, start/stop music with the module's toggle.
  `display-now-playing`/`fade-out-display`, an on-screen "now playing" text.
- `display-mode`, `use-exact-delay`, `song-delay-seconds`, `minimum-delay-seconds`/
  `maximum-delay-seconds`, control the cooldown between songs (either one exact value or a
  random range).
- `random-pitch`/`trippy-pitch`, `song-pitch-adjustment`, `random-pitch-adjustment-range`,
  pitch playback of the music.
- `intensity`, `weighted-chance-%`, `volume-%-boost`, weighting/volume controls.
- A full per-track list lets you individually enable/disable every vanilla and Minecraft
  Live music track (C418, Lena Raine, Kumi Tanioka, and Aaron Cherof tracks are all
  listed individually).
</details>

<details>
<summary> parkinsons</summary>

A blunt freecam: the camera flies away from your body while the real player stays put.
**Warnings:** your player can still be attacked/killed while you look around, the camera is
fast enough to outrun loaded chunks, and it's a fake client-side entity so visual glitches are
possible. Don't combine it with Meteor's Freecam. Turns itself off when you leave the world.
Adapted from the Genyo addon's module of the same name (wuritz, Barnika18, Awakeyv).
</details>

<details>
<summary> swing-speed</summary>

Slows down the outward part of your arm swing animation. Purely visual, client-side only.
One setting: `out-speed`, multiplier for how fast the outward swing plays (1 = vanilla
speed, lower = slower; the return to rest always plays at vanilla speed).
</details>

---

## HUD elements

<details>
<summary> Player Tracker</summary>

Lists every player currently loaded (within render/simulation distance), color-coded as
friend, enemy, or everyone else, with distance in meters. Fully draggable and configurable
like a built-in HUD element.

Drag it onto your screen from Meteor's HUD editor to use it.

- **General**: `limit` (max players shown), `show-distance`, `shadow`, `alignment`,
  `border`.
- **Colors**: separate colors for `friend-color`, `enemy-color`, `other-color`, and
  `distance-color`; `enemy-names` is a manually maintained list (Meteor has no built-in
  enemy list, so this addon keeps its own).
- **Grid Snapping**: `snap-to-grid` and `grid-size`, snaps the element to a pixel grid
  while dragging it in the HUD editor, instead of free placement.
- **Scale**: optional `custom-scale` independent of the global HUD text scale.
- **Background**: toggleable background with its own color.
</details>

<details>
<summary> Armor Hud</summary>

Shows your currently equipped armor (and durability) as a small HUD element.

- `orientation`, how to lay out the armor pieces; `flip-order`, flips their order.
  `show-empty`, renders barrier icons for empty armor slots.
- `durability`, how to display durability; `durability-color`/`durability-color-use-theme`
  and `durability-shadow`, text appearance for it.
- **Scale**: optional `custom-scale`. **Background**: toggleable background with its own
  color, or `background-color-use-theme` to match Meteor's current GUI theme instead.
</details>

<details>
<summary> Inventory Hud</summary>

Mirrors your full inventory (and optionally hotbar) as an always-visible HUD element, so
you can see what you're carrying without opening your inventory.

- `inventory-only`, only shows the main inventory grid; when off, the hotbar is shown
  above it as well.
- `show-empty`, renders barrier icons for empty slots. `show-count`, shows the stack count
  on top of each item.
- **Scale**: optional `custom-scale`. **Background**: toggleable background with its own
  color, or theme-matched via `background-color-use-theme`.
</details>

<details>
<summary> Dimension Coords</summary>

Shows your coordinates for all three dimensions at once (converting Nether/Overworld at
8:1), so you always know where a Nether portal will drop you.

- `show-title`, display the HUD element's title. `show-current-dimension`, show which
  dimension you're actually in right now.
- `text-scale`/`text-shadow`, text appearance. `title-color` plus separate
  `overworld-color`/`nether-color`/`end-color` for each dimension's line.
- `show-labels`, show dimension name labels next to each set of coordinates.
  `remove-commas`, strip the thousands-separator commas from large coordinates.
  `horizontal-layout`, lay the three dimensions out side by side instead of stacked.
</details>

<details>
<summary> PvP Necessities Hud</summary>

A compact row of item counts for whatever PvP essentials you care about (totems, gapples,
ender pearls, etc.), so you can glance at your stock without opening your inventory.

- `items`, the list of items to track and display.
- `text-color`/`text-use-theme`, count text color (or match the current Meteor theme).
  `margin`, spacing between items.
- **Scale**: optional `custom-scale`. **Background**: toggleable background with its own
  color, or theme-matched via `background-color-use-theme`.
</details>

<details>
<summary> Dub Counter HUD</summary>

Small text element mirroring the `.dub` command's last result (reads the command's stored
state directly rather than re-scanning). Drag it onto your screen from the HUD editor,
then run `.dub` to populate it.

`show-mode` toggles whether it also shows Loaded vs. Rendered; `shadow` and `color`
control text appearance.
</details>

<details>
<summary> Now Playing HUD</summary>

Shows the song currently playing on your PC as `Artist - Title`. Drag it onto your screen
from the HUD editor, then play something.

- `show-artist`, show the artist before the track title; turn off to show only the title.
- `hide-when-paused`, hides the element when nothing is playing (on by default).
- `max-length`, longest text to show before it is cut off with `...`.
- `shadow`, `color` and `color-use-theme`, text appearance.
- **Scale**: optional `custom-scale` (with a `scale` slider, 0.5-3) independent of the global
  HUD text scale.

**Windows**: reads whatever Windows reports as the active media session (Spotify, YouTube
in a browser, VLC, and most other players) through a hidden PowerShell process. Nothing to
install.

**Linux**: reads the MPRIS media info that Spotify, Firefox, Chromium, VLC and most other
players publish, using [`playerctl`](https://github.com/altdesktop/playerctl). Install it
first, e.g. `sudo apt install playerctl` (Debian/Ubuntu), `sudo pacman -S playerctl` (Arch)
or `sudo dnf install playerctl` (Fedora). Without it the HUD editor shows "Install
playerctl".

macOS isn't supported. It only shows your own PC's music, not what other players are
listening to, and the background process only runs while the element is on screen. If
several players are open, the one that is actually playing is shown; if none is, it falls
back to the one the OS considers current.
</details>

<details>
<summary> Stats HUD</summary>

Displays your Minecraft statistics (play time, distance traveled, blocks broken, mobs
killed, and more) as a HUD element, dragged and positioned like any other HUD element.

Drag it onto your screen from the HUD editor. It works out of the box with sane defaults,
turn individual stats on or off in the **Stats** setting group.

- **General**: `shadow`, `alignment`, `text-color`, `border`.
- **Scale**: optional `custom-scale` independent of the global HUD text scale.
- **Background**: toggleable background with its own color.
- **Grid Snapping**: `snap-to-grid` and `grid-size`, same as Player Tracker's.
- **Sync**: `auto-sync` periodically requests fresh stats from the server (`sync-delay`
  controls how often); `update-interval` controls how often the displayed text
  recalculates from the last-known stats.
- **Order and Formatting**: `stat-order` controls which stats show and in what order;
  `hourly-rates` appends a per-hour rate next to applicable stats.
- **Stats**: toggle each stat individually (play time, distance traveled/walked/
  sprinted/flown/swum, blocks broken, mobs killed, players killed, items crafted/used/
  picked up, deaths, time since death, time since sleep). Blocks, mobs, and crafted/used/
  picked-up items can each be narrowed to a specific list via their own count-mode and
  list setting.
</details>

<details>
<summary> Watermark</summary>

Shows your 6Tools version on screen as text, an image, or both. Drag it onto your screen
from the HUD editor, where it's listed as **Watermark** under the 6Tools Addon group. By
default it shows the 6Tools icon in front of the text `6Tools <version>`.

- `display`, `Image and Text` (default) puts the image in front of the text, `Text` shows
  only the text, `Image` shows only the image.
- `text`, what the text says. `{version}` is replaced with your current 6Tools version
  (it updates by itself every build) and `{name}` with `6Tools`. The default is
  `{name} {version}`. You can write anything around them, e.g. `6Tools v{version} | 6b6t`.
- `color`, the text color. `color-use-theme` uses your current Meteor theme accent color
  instead. `shadow` toggles the shadow behind the text.
- **Image**: `default-icon`, uses the 6Tools icon (the addon's own `icon.png`) whenever no
  image file is set. Turn it off to show no image until you set one, or set `display` to
  `Text` to drop the image entirely.
  `image`, the file name of an image in
  `.minecraft/config/sixtoolsaddon/watermark/` (the folder is created for you), or a full
  path like `C:\Users\you\Pictures\logo.png`. Leave it empty to use the 6Tools icon.
  PNG works best, JPG, GIF and BMP also load. Big images are shrunk automatically.
  `image-height`, height in pixels, the width follows the picture's proportions.
  `gap`, space between the image and the text. `tint-image`, multiplies the image by the
  text color, handy for one-color logos.
- **Scale**: optional `custom-scale` independent of the global HUD text scale.

Changing the image file (replacing it or editing it) is picked up within about a second, no
restart needed. If the image can't be found or loaded, the element shows just the text (or,
in `Image` mode, nothing outside the HUD editor, where it says "No image").
</details>

---

## Enemies List
A global enemies list, separate from Player Tracker's and Auto TP Accept's own
independent `enemy-names` settings (each of those predates this and still keeps its own
list). This one is shared by **Chat Highlighter**, **Sound Editor**'s `enemy_spotted`
sound, and the new `.onlineplayers enemys` command, and persists to
`.minecraft/config/sixtoolsaddon-enemies.txt`. The **social-sync** module can keep it matched with Mio and RusherHack. `.enemy add`/`.enemy remove` tab-complete
(add suggests currently loaded player names, remove suggests names already on the list).

## Commands

Use whatever command prefix your Meteor build is set to, not the dot:

| Command                       | Effect                                            |
|--------------------------------|------------------------------------------------------|
| `.invsorter save <n>`         | Snapshot your current inventory as `<n>`             |
| `.invsorter load <n>`         | Enable the sorter and start sorting to `<n>`         |
| `.invsorter delete <n>`       | Remove a saved inventory                              |
| `.invsorter clear`            | Remove every saved inventory                          |
| `.invsorter list`             | List saved inventory names                            |
| `.dub`                        | Count double chests across every loaded chunk         |
| `.dub rendered`                | Count double chests within an 8-chunk radius          |
| `.dub rendered <radius>`       | Count double chests within a custom chunk radius      |
| `.setdiscord set <url>`        | Set the Discord webhook URL used by Discord Notifier  |
| `.setdiscord clear`            | Clear the saved webhook URL                            |
| `.enemy add <name>`            | Add a name to the shared enemies list (tab-completes online players) |
| `.enemy remove <name>`         | Remove a name from the list (tab-completes existing entries) |
| `.enemy list`                  | List everyone currently on the enemies list            |
| `.onlineplayers friends`       | List which of your friends are currently online       |
| `.onlineplayers enemys`        | List which of your enemies are currently online       |
| `.coords`                      | Copy your current coordinates to the clipboard         |
| `.coords raw`                  | Copy just the raw numbers, no formatting               |
| `.chesttracker search hand`    | Search Chest Tracker for whatever's in your main hand |
| `.chesttracker search <item>`  | Search Chest Tracker for a specific item               |
| `.chesttracker clear all`      | Wipe all tracked container data                        |
| `.chesttracker clear dimension`| Wipe tracked data for your current dimension only      |
| `.chesttracker export`         | Export tracked container data                          |
| `.chesttracker nearby <radius>`| List tracked containers within a radius                |
| `.setinput`                    | Start input area selection for Stash Mover              |
| `.setoutput`                   | Start output area selection for Stash Mover              |
| `.setclear`                    | Clear all Stash Mover area selections                    |
| `.stashstatus`                 | Check Stash Mover areas and current configuration        |

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
  **VolytraFly**: <https://github.com/Volizray/VolytraFly-Addon>. It's repackaged and
  renamed only for this addon (package, class name, module id `efly`, and category
  changed); the actual flight logic is untouched. Full credit for Efly's design and
  implementation goes to Volizray.
- **Shulker View** is ported from [cattyngmd/shulker-view](https://github.com/cattyngmd/shulker-view)
  (MIT licensed). Adapted to run as a normal Meteor module (own settings, own category)
  instead of a separate mod with its own config screen, and extended in this addon with
  fully customizable background color and on-screen position.
- **Whisper Logger** is adapted from [Plumbiller](https://github.com/Plumbiller)'s
  [PlumbillerPublic](https://github.com/Plumbiller/PlumbillerPublic) addon. Repackaged into
  this addon's structure (package, category, config folder) and simplified to target only
  this project's supported Minecraft version, with the rest of the logic unchanged. Full
  credit for the original module and its Discord-style HTML log design goes to Plumbiller.
- **Chest related modules** are adapted from [BepHaxAddon](https://github.com/dekrom/BepHaxAddon).
- **Meteor Fix** — one of the Meteor bug fixes used Fractal420's
  [Meteor-GUI-Position-Fix](https://github.com/Fractal420/Meteor-GUI-Position-Fix).
- **Lucky1821**

## License

MIT, see [LICENSE](LICENSE). You're free to use, modify, and redistribute this addon as
long as the original copyright notice and the credits above are kept.

[discord]: https://discord.gg/HX6rSFg3k
[vidget-discord]: https://invidget.switchblade.xyz/fdHkyVYc8