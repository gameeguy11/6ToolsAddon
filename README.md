# 6Tools Addon

A [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) addon for Fabric
**1.21.11**, built for the 6b6t anarchy server.

[![Minecraft](https://img.shields.io/badge/minecraft-1.21.11-green)](https://www.minecraft.net/)
[![Release](https://img.shields.io/github/v/release/gameeguy11/6ToolsAddon-)](https://github.com/gameeguy11/6ToolsAddon-/releases)
[![Downloads](https://img.shields.io/github/downloads/gameeguy11/6ToolsAddon-/total)](https://github.com/gameeguy11/6ToolsAddon-/releases)
[![Stars](https://img.shields.io/github/stars/gameeguy11/6ToolsAddon-)](https://github.com/gameeguy11/6ToolsAddon-/stargazers)
[![Code Size](https://img.shields.io/github/languages/code-size/gameeguy11/6ToolsAddon-)](https://github.com/gameeguy11/6ToolsAddon-)
[![Issues](https://img.shields.io/github/issues/gameeguy11/6ToolsAddon-)](https://github.com/gameeguy11/6ToolsAddon-/issues)

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

| Component     | Version                    |
|----------------|------------------------------|
| Fabric Loader | 0.18.3+                     |
| Meteor Client | current 1.21.11 snapshot, check [maven.meteordev.org/snapshots](https://maven.meteordev.org/snapshots/meteordevelopment/meteor-client/) |
| JDK           | 21                           |

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

- **General**: `browser-keybind` opens the container browser GUI.
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
- `username-pattern`, the regex used to find the sender's name at the start of a chat line
  (capture group 1). Default matches `Name » message` formatting (6b6t/Meteor style),
  adjust it if your server's chat format differs.
- `debug`, prints each chat line's exact characters (as unicode escapes) plus match/color
  info to help you tune `username-pattern` for your server.

If a name matches more than one category, priority is self > friend > enemy.
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
  covers vanilla's common death messages; adjust it if your server rewords them.

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
  if you're not on 6b6t.

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

---

## Enemies List
A global enemies list, separate from Player Tracker's and Auto TP Accept's own
independent `enemy-names` settings (each of those predates this and still keeps its own
list). This one is shared by **Chat Highlighter**, **Sound Editor**'s `enemy_spotted`
sound, and the new `.onlineplayers enemys` command, and persists to
`.minecraft/config/sixtoolsaddon-enemies.txt`. `.enemy add`/`.enemy remove` tab-complete
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
- **Chest related modules** are adapted from [BepHexAddon](https://github.com/dekrom/BepHaxAddon)
- **Meteor Fix** One of the meteor bug fixes used Fractal420 [Meteor-GUI-Position-Fix](https://github.com/Fractal420/Meteor-GUI-Position-Fix).
## License

MIT, see [LICENSE](LICENSE). You're free to use, modify, and redistribute this addon as
long as the original copyright notice and the credits above are kept.

[discord]: https://discord.gg/fdHkyVYc8
[vidget-discord]: https://invidget.switchblade.xyz/fdHkyVYc8