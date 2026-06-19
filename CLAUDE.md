# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

ServerSelectorX (SSX) is a Bukkit/Spigot Minecraft plugin that gives players a clickable GUI menu for jumping between servers on a BungeeCord network (or worlds on a single server). This is the **free** branch, targeting Minecraft 1.13+. Other variants live on sibling branches (`free-legacy`, `premium`, `premium-legacy`) — see `README.md`.

## Build

```bash
mvn clean package        # produces shaded jar in target/
```

There is no test suite, no linter config, and no `run` script. Verification means building the jar and dropping it on a Spigot test server. Java source/target is **11** (`pom.xml`); do not use newer language features.

The Maven shade plugin relocates and minimizes bundled deps (`bstats`, `commons-lang3`, `Derkutils`, `gson`) under `xyz.derkades.serverselectorx.lib.*`. Spigot API and PlaceholderAPI are `provided` (not bundled). The plugin version (`pom.xml` `<version>`) is filtered into `plugin.yml` via `@project.version@`, so resources must go through Maven filtering — don't read `plugin.yml` version expecting a literal.

## Architecture

The whole plugin is driven by **per-menu YAML config files**, not hardcoded behavior. Understanding the config-to-runtime flow is the key to this codebase.

- **`Main`** — plugin entry point. On enable it builds the `ConfigurationManager`, starts the `PingManager`, registers listeners, registers the `BungeeCord` outgoing plugin channel, and **dynamically registers a Bukkit command for every menu file** that declares a `command:` (via reflection into the server's `commandMap`, namespace `ssx-custom`). Also holds the two cross-cutting helpers: `getItemFromMaterialString` (parses `item:` strings incl. `head:<uuid|auto>`) and `teleportPlayerToServer` (sends a BungeeCord `Connect` plugin message).

- **`ConfigurationManager`** — loads `config.yml` (global) plus every `*.yml` in the plugin's `menu/` data folder into a `Map<filename-without-ext, FileConfiguration>`. `default.yml` is copied out of the jar on first run. `reload()` rebuilds this map and is invoked by `/ssx reload`.

- **Opening a menu** happens three ways, all converging on `new SelectorMenu(player, config)`:
  1. `SelectorOpenListener` — right-clicking while holding the menu's configured `item:` material.
  2. The dynamically-registered custom command (in `Main.registerCommands`).
  3. A `sel:<menuname>` action from inside another menu.
  `OnJoinListener` separately gives players the selector item on join (`on-join: true`).

- **`SelectorMenu`** (extends Derkutils `IconMenu`) — renders one menu. `fillMenu` walks the `menu.<slot>` config sections, building each `ItemStack`. `chooseSection` decides whether to show the `online`/`offline`/`dynamic.<motd>` variant of a slot based on live ping data. `onOptionClick` dispatches the slot's `action:` string by prefix: `url:`, `cmd:`, `consolecmd:`, `sel:`, `world:`, `srv:`, `msg:`, plus `close`/`none`. Slot `-1` is the wildcard slot. `checkPermission` gates menus behind `require-permission`/`permission-node`.

- **Server pinging** (`utils/` package) keeps the `{online}`/`{max}`/`{motd}` placeholders and online/offline item states current:
  - `PingManager` (a `PluginMessageListener`) reads which servers to ping from all menu configs. A slot's `ping-server` may be a **boolean** (legacy: ping the explicit `ip:`/`port:`) or a **string** (the BungeeCord server name — IP/port is resolved asynchronously by sending a `ServerIP` plugin message and caching the reply).
  - `PingTask` runs async on a repeating Bukkit timer, pinging one server per tick round-robin, writing results into the static `PingTask.SERVER_INFO` map that `SelectorMenu.chooseSection` reads. Set `ping-debug: true` in config for verbose ping logging.
  - `ServerPinger` / `MinetoolsPinger` / `HolographicPinger` are the ping implementations; the `holographicdisplays_serverpinger/` package is the low-level protocol/packet code.

- **`Stats`** initializes bStats metrics. `ItemMoveDropCancelListener` / `OffHandMoveCancel` optionally lock the selector item in the inventory (`cancel-item-drop` / `cancel-item-move`, toggled into static fields during `ConfigurationManager.reload()`).

## Conventions

- Item materials, actions, and placeholders are documented on the upstream wiki (links are embedded in user-facing error messages in `Main` and `SelectorMenu`) — keep those error messages and their wiki links accurate when changing parsing behavior.
- Config keys are read defensively with defaults and produce in-game `player.sendMessage` warnings rather than throwing — follow that pattern for new config options instead of failing hard.
- Dependency bumps are automated via Renovate/Dependabot (most recent commits are bot PRs); the `free` branch is the working/main branch for this variant.
