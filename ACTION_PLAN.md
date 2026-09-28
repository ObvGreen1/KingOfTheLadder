# ACTION_PLAN.md — SRP refactor of KingOfTheLadder

Behaviour-preserving structural refactor. No gameplay change, no config-key change, no SQL-schema
change, no placeholder-name change, no permission change. The only visible surface allowed to change
is internal Java class/method layout plus the two build-verification scripts.

Source of truth for the current state: `STRUCTURE.md` (47 plugin files, 4 178 lines).

---

## 1. Rules this refactor obeys

| Rule | Consequence |
|---|---|
| One class = one reason to change | Every class states its single responsibility in one sentence, or it is split |
| Size budget | Value types (records/enums) exempt. Behaviour classes target ≤ 150 lines; orchestrators ≤ 120 |
| No `static` mutable state, no service-locator getters on the plugin class | Every consumer receives narrow dependencies by constructor |
| Bukkit `Plugin` instance is never held by a service | Services take narrow collaborators (e.g. a scheduler adapter), so logic is unit-testable off-server |
| `me.obvgreen` may not be imported by any package except the bootstrap classes | Removes today's "every package may depend on the god object" escape hatch in `tools/PackageCheck.java` |
| One commit per checklist step, each one compiling and green | No big-bang rewrite |
| Every step that moves or creates a package also updates `tools/PackageCheck.java` `ALLOWED` in the same step | Layout check stays authoritative |

---

## 2. Where the responsibility violations are

Ranked by blast radius. "Fan-in" = how many types depend on it today.

| # | Type | Lines | Problems today | Verdict |
|---|---|---|---|---|
| 1 | `arena.ArenaManager` | 651 | 11 responsibilities, 8 mutable maps, 4 collaborators, 1 Bukkit task, private algorithms (rating, effects, push, config IO) | **Break apart entirely** into a registry + 7 services + 5 state holders |
| 2 | `database.DatabaseManager` | 380 | Connection tuning, DDL/DML, row mapping, in-memory mirror, ranking, mutation API, async writer, presence tracking, shutdown drain | **Break apart entirely** into 5 collaborators behind one facade |
| 3 | `KingOfTheLadder` | 190 | Composition root **plus** 10 service-locator accessors; every package imports it | **Break apart**: bootstrap + registrars; delete all accessors |
| 4 | `command.CommandContext` | 144 | Arg parsing, sender adaptation, permission checks, four message styles, plugin service locator | **Break apart** into args + sender + responder |
| 5 | `config.FileSettings` | 139 | 28 components spanning five unrelated domains plus three parsers plus a clamp helper | **Split by domain** into 5 records |
| 6 | `glicko.GlickoManager` | 293 | Pure algorithm, a config reader, a volatility root-finder, scale conversion, and a decay policy | **Split** into 4 types; keep the package a leaf |
| 7 | `dialog.DialogView` | 207 | Static body builders, static button builders, instance builders, callback registry, scheduler hop, presenter | **Split** into 4 types |
| 8 | `arena.SavedState` | 178 | 17-field constructor, capture, restore, item deep-copy, attribute read, duplicate | **Split** into value groups + a Bukkit-facing vault |
| 9 | `dialog.setup.*` (4 classes) | 305 | UI rendering and command execution fused via `SetupActions`, which reaches back through `plugin.commands()` | **Re-point** at a new admin service; delete the bridge |
| 10 | `placeholder.PlaceholderManager` | 77 | Owns nothing but a registration flag and one delegating getter | **Delete** |
| 11 | `dialog.PlaceholderCache` | 89 | Leaderboard cache living in the `dialog` package (wrong dependency direction: `placeholder` → `dialog` → `database`) | **Move** to a new `leaderboard` package |
| 12 | `dialog.setup.SetupDialogManager` | 30 | Pass-through facade over one class; duplicates the `DialogManager` role on the plugin | **Delete** |
| 13 | `command.ArenaLookup` | 50 | Static helper hiding a dependency on `ArenaManager` | **Turn into** an instance over the arena query service |
| 14 | `listener.ArenaCombatListener` | 82 | PvP boundary, fall-damage suppression, hit attribution | **Split** into 2–3 listeners |
| 15 | `arena.Arena` | 115 | Geometry + config + world resolution + a human-readable description | **Split** geometry into `ArenaBounds`; move description to a presenter |

---

## 3. Target package layout

Existing packages stay; four are added.

```
me.obvgreen
├── KingOfTheLadder                 thin lifecycle shim: onEnable/onDisable only
├── bootstrap/                       NEW  wiring, registrars, ordered start/stop
├── config/                          settings records + parsers (leaf)
│   ├── FileSettings                 root: from(config) -> 5 domain records
│   ├── KitSettings                  armour, stick, colour, slot, max health, game mode
│   ├── CombatSettings               knockback window
│   ├── KingSettings                 cooldown, fireworks, push, no-king text
│   ├── LeaderboardSettings          size, cache seconds
│   ├── MessageBundle                every messages.* string
│   └── ConfigValues                 clamp + material/gamemode/colour parsers
├── platform/                        NEW  Bukkit seams, so no service holds Plugin
│   ├── TaskScheduler                runNow / runLater / runRepeatingAsync
│   └── WorldResolver                Optional<World> by name
├── glicko/                          (leaf) GlickoSettings, GlickoEngine, VolatilitySolver, GlickoScale, GlickoRating
├── text/                            (leaf) Text, MessageSender
├── database/                        PlayerStats, RankEntry, StatCategory, StatFormatter,
│                                    StatsService, StatsCache, StatsTable, RowMapper,
│                                    SqliteConnectionFactory, AsyncStatsWriter, Leaderboard
├── item/                            SelectionWand
├── arena/
│   ├── Arena, ArenaBounds, BlockPos, ArenaSpawnResolver
│   ├── store/ArenaRepository        config.yml read/write/delete
│   ├── state/                       MembershipRegistry, PlayerStateVault, KingRegistry,
│   │                                SelectionRegistry, Selection, KnockbackAttribution,
│   │                                ClaimCooldown, PlayerSnapshot (record groups)
│   ├── gameplay/                    ArenaQueryService, ArenaEntryService, ArenaExitService,
│   │                                KitService, KnockoffService, CrownService,
│   │                                ArenaMotionService, ArenaAdminService, ArenaDiagnostics,
│   │                                ArenaHousekeeping
│   └── ArenaManager                 facade: wiring + delegation only (≤ 120 lines)
├── leaderboard/                     NEW  LeaderboardCache, LeaderboardDialog, PlaceholderRouter, RankColours
├── command/                         KotLCommand, SubcommandDispatcher, SubcommandCompleter,
│                                    KotlSubcommand, CommandContext, CommandArgs, CommandSenderAdapter,
│                                    Permissions, CommandRegistry, ArenaNameResolver,
│                                    ArenaNameValidator, ArenaListPresenter, HelpPrinter,
│                                    + the 10 subcommands
├── dialog/                          DialogFactory, DialogPresenter, DialogBodies, DialogButtons, DialogCallbacks
├── dialog.setup/                    SetupMenu, ArenaSetupMenu, CreateArenaMenu, DeleteConfirmationDialog,
│                                    BoundsFormatter, SelectionFormatter
├── listener/                        9 thin listeners
└── placeholder/                     KotLExpansion only
```

---

## 4. Method → target class

### 4.1 `me.obvgreen.KingOfTheLadder`

| Method | Target type | Note |
|---|---|---|
| `onEnable`, `onDisable` | `bootstrap.KotlBootstrap.up()` / `.down()` | Plugin class keeps only the two Bukkit callbacks |
| `registerListeners`, `register` | `bootstrap.ListenerRegistrar` | Owns the listener list in priority order |
| `registerCommand` | `bootstrap.CommandRegistrar` | |
| `registerPlaceholders` | `bootstrap.PlaceholderRegistrar` | Sole place allowed to touch PlaceholderAPI presence |
| `settings()`, `arenas()`, `database()`, `glicko()`, `dialogManager()`, `setupDialogs()`, `placeholders()`, `wand()`, `commands()` | **deleted** | Replaced by constructor injection; this is the change that makes the other waves possible |
| new | `bootstrap.KotlBootstrap.startedServices()` | Ordered `AutoCloseable` list so teardown stops mirroring construction by hand |

### 4.2 `arena.Arena`

| Method | Target type | Note |
|---|---|---|
| `of` (min/max swap, world check) | `ArenaBounds.of(a, b)`; `Arena.of` becomes a thin record constructor | |
| `contains(Location)`, `contains(BlockPos)`, `isKnockedOff` | `ArenaBounds` | Geometry, not identity |
| `respawnOrCentre` | `arena.ArenaSpawnResolver` | One policy, one place |
| `resolveWorld` | `platform.WorldResolver` | Removes `Bukkit.getWorld` statics from the model |
| `hasKingPlate` | stays on `Arena` | |
| `withActive`, `withKingPlate`, `withRespawn` | stay on `Arena` | Wither methods are part of the value type |
| `describe` | `command.ArenaListPresenter` (chat) — reused by `dialog.setup.ArenaSetupMenu` | Presentation must not live in the model |

### 4.3 `arena.ArenaManager` — the 11 responsibilities

| Responsibility | Methods | Target type |
|---|---|---|
| Config persistence | `ARENA_ROOT`, `loadArenas`, `readArena`, `parsePos`, `parseRespawn`, `saveArena`, `deleteArena` | `arena.store.ArenaRepository` |
| Read queries | `arenas`, `byName`, `arenaAtBlockPos`, `activeAt` | `arena.gameplay.ArenaQueryService` |
| Membership | `membership`, `isInArena`, `arenaOf`, `occupants` | `arena.state.MembershipRegistry` |
| Player snapshot storage | `savedStates` | `arena.state.PlayerStateVault` |
| Kit grant | `giveKit` | `arena.gameplay.KitService` |
| Enter / exit | `join`, `leave` | `arena.gameplay.ArenaEntryService`, `ArenaExitService` |
| Position rules | `handleMove`, `teleportToRespawn`, `healFully`, `handleDeath` | `arena.gameplay.ArenaMotionService` |
| Scoring + rating | `handleKnockoff`, `scoreKnockoff`, `applyCombatRating`, `formatSigned`, `ratingOf` | `arena.gameplay.KnockoffService` |
| Crown | `claimCrown`, `king`, `kingName`, `pushBystanders`, `spawnFireworks` | `arena.gameplay.CrownService` |
| Crown cooldown | `claimCooldown` | `arena.state.ClaimCooldown` |
| Knockback attribution | `pendingHits`, `registerHit`, `consumeAttacker`, `PendingHit` | `arena.state.KnockbackAttribution` |
| Wand selections | `selections`, `selection`, `setSelectionFirst`, `setSelectionSecond`, `clearSelection`, `Selection` | `arena.state.SelectionRegistry` + top-level `Selection` record |
| Expiry timer | `startHousekeeping`, `shutdown`, `expirePendingHits`, `housekeepingTask` | `arena.gameplay.ArenaHousekeeping` (takes `TaskScheduler`) |
| Admin mutations | implicit, today reached through commands and dialogs | `arena.gameplay.ArenaAdminService` (new: create, setPlate, setSpawn, toggle, delete-with-eject, validateName) |
| Diagnostics | `problems`, `reportProblems` | `arena.gameplay.ArenaDiagnostics` |
| Everything else | facade delegation + construction | `ArenaManager` ≤ 120 lines, zero private algorithms |

### 4.4 `arena.SavedState`

| Method | Target type | Note |
|---|---|---|
| `capture`, `restore`, `maxHealthOf` | `arena.state.PlayerStateVault` | Bukkit interaction, not value data |
| `copy`, `copyItem` | `arena.state.PlayerSnapshot.copiedItems` (or package-private helper) | Deep-copy is a property of the snapshot record |
| `duplicate`, `effects`, `location` | stay on the value | |
| 17-field canonical constructor | replaced by 4 grouped records: `InventorySnapshot`, `VitalsSnapshot`, `MovementSnapshot`, `EffectSnapshot` composed into `PlayerSnapshot` | Constructor arguments stop being positional trivia |

### 4.5 `database.DatabaseManager`

| Method | Target type | Note |
|---|---|---|
| `initialize`, `shutdown`, `closeQuietly` | `database.StatsService` (lifecycle only) | |
| `openTunedConnection` + pragma list | `database.SqliteConnectionFactory` | Tuned-connection construction is one concern |
| `CREATE_TABLE`, `UPSERT`, `SELECT_ALL`, `read`, `loadIntoCache` | `database.StatsTable`, `database.RowMapper` | SQL text stays in one file |
| `cache`, `get`, `peek`, `snapshot`, `mutate`, `save` (dispatch part) | `database.StatsCache` | In-memory mirror, thread-safe, no JDBC knowledge |
| `io` executor, `save` (queue part), shutdown drain | `database.AsyncStatsWriter` | Owns `KotL-DB` thread and the 5 s drain |
| `top`, `rankOf` | `database.Leaderboard` (pure, works on any collection) | Enables leaderboard unit tests without SQLite |
| `addCounter`, `applyRating` | `database.StatsService` (domain API) | |
| `trackOnlinePlayers`, `nameOf` | `database.PlayerPresenceTracker` | |
| `onlinePlayerCount` | renamed `knownPlayerCount` in `StatsService` | Fixes the misleading name called out in `STRUCTURE.md` §8; log line updated in the same step |
| `StatCategory.format`, `RankEntry.formatted` | `database.StatFormatter.format(category, value)` | Formatting is presentation, not enum behaviour |

### 4.6 `glicko.GlickoManager`

| Method | Target type | Note |
|---|---|---|
| constructor (reads `glicko.tau`, `inactivity-growth-per-day`) | `GlickoSettings.from(FileConfiguration)`; `GlickoEngine` receives the record | Engine becomes pure |
| `rate` (steps 1–8), `rateSingle`, `rateMatch` | `GlickoEngine` | |
| `solveVolatility`, `volatilityF`, `MAX_BRACKET_SEARCH` | `VolatilitySolver` | Numerical root-finding isolated and independently testable |
| `g`, `expectedScore`, `toGlickmanScale`, `deviationToGlickmanScale`, `deviationFromGlickmanScale`, `fromGlickmanScale`, `clamp`, `square` | `GlickoScale` (public) / package-private in `GlickoEngine` | |
| `decay` | `RatingDecayPolicy` | Currently dead code — either wire it into housekeeping or delete it and its config key; decision recorded in the step |
| `MIN_DEVIATION`, `MIN_RATING`, `MAX_RATING`, `WIN`, `LOSS` | used by `GlickoEngine.clamp`, else deleted | Dead constants must not survive a "clean code" pass |
| `GlickoRating`, `defaults`, constants | unchanged | Already correct |

### 4.7 `config.FileSettings`

| Method | Target type | Note |
|---|---|---|
| `from` | `FileSettings.from` → assembles 5 domain records | |
| `readArmour` | `KitSettings` | |
| `parseMaterial`, `parseGameMode`, `parseColour`, `clamp` | `ConfigValues` | Reusable value coercion with a fallback policy |
| 28 components | `KitSettings`, `CombatSettings`, `KingSettings`, `LeaderboardSettings`, `MessageBundle` | Consumers depend on the narrow record they actually read |

### 4.8 `command.CommandContext`

| Method | Target type | Note |
|---|---|---|
| `args`, `arg`, `rawArg`, `withArgs`, `label` | `command.CommandArgs` | Pure parsing, zero Bukkit |
| `sender`, `player`, `isPlayer`, `requirePlayer` | `command.CommandSenderAdapter` | |
| `has`, `requirePermission` | `command.Permissions` (constants + guard) | Guard lives in the dispatcher; constants stay a leaf holder |
| `reply`, `success`, `error`, `info` | `text.MessageSender` | Prefix policy is one place, shared by listeners and dialogs |
| `plugin()`, `arenas()` | **deleted** | Subcommands take what they need |
| `toString` | stays with `CommandContext` | |

### 4.9 `command.KotLCommand`, registry, subcommands

| Method | Target type | Note |
|---|---|---|
| `onCommand` | `command.SubcommandDispatcher` | Resolution, permission guard, player guard |
| `onTabComplete`, `filter` | `command.SubcommandCompleter` | Visibility filtering needs the registry only |
| class itself | thin adapter implementing `CommandExecutor` + `TabCompleter` | |
| `ArenaLookup.from`, `.names` | `command.ArenaNameResolver` (instance, over `ArenaQueryService`) | No static back-door to the arena manager |
| `CreateCommand.validate` | `command.ArenaNameValidator` | Shared with `CreateArenaMenu`, so the dialog cannot accept what the command rejects |
| `ListArenasCommand.execute` body | `command.ArenaListPresenter` | Chat text shared with the setup menu |
| `HelpCommand.sendUsage` (static) | `command.HelpPrinter` (instance) | Called by the no-args path of the dispatcher |
| `CommandRegistry.find/all/names/visibleTo` | unchanged, but constructed from an injected `List<KotlSubcommand>` | Removes the hard-coded `new` list of 10 classes |

### 4.10 `dialog.DialogView` and friends

| Method | Target type | Note |
|---|---|---|
| `build` (both overloads) | `dialog.DialogFactory` | |
| `show` | `dialog.DialogPresenter` | |
| `text`, `line`, `gap`, `BODY_WIDTH` | `dialog.DialogBodies` | Static value → UI conversion, no plugin reference |
| `actions`, `button` (3 overloads), `closeButton` | `dialog.DialogButtons` | |
| `onClick`, `onSubmit`, `closeAction`, `clickOptions`, `RegisteredAction` | `dialog.DialogCallbacks` (takes `TaskScheduler`) | The only place that hops to the main thread |
| `DialogView` itself | deleted; `DialogFactory` becomes the shared supertype | |
| `DialogManager.openLeaderboards`, `selfSummary` | `leaderboard.LeaderboardDialog` | New package: dialog UI and leaderboard data belong together, and `placeholder` no longer needs `dialog` |
| `rankColour` | `leaderboard.RankColours` | |
| `PlaceholderCache.*` | `leaderboard.LeaderboardCache` (package move) | Breaks the `placeholder → dialog → database` chain |
| `SetupDialogs.open` | `dialog.setup.SetupMenu` | |
| `SetupDialogManager` | **deleted** — one screen does not need a facade | |
| `ArenaSetupPage.open` | `dialog.setup.ArenaSetupMenu` | Buttons call `ArenaAdminService`, not commands |
| `ArenaSetupPage.confirmDelete` | `dialog.setup.DeleteConfirmationDialog` | Safety UI separated from the menu |
| `ArenaSetupPage.size` | `dialog.setup.BoundsFormatter` (or `ArenaBounds.describe()`) | |
| `CreateArenaPage.open`, `NAME_KEY` | `dialog.setup.CreateArenaMenu` | |
| `CreateArenaPage.describe` | `dialog.setup.SelectionFormatter` | |
| `SetupActions.run` | **deleted** — dialogs are no longer a remote control for `KotlSubcommand` | |

### 4.11 `placeholder`

| Method | Target type | Note |
|---|---|---|
| `PlaceholderManager.register`, `unregister`, `active` | `bootstrap.PlaceholderRegistrar` | The manager holds no state worth its own type |
| `PlaceholderManager.arenaOf` | **deleted** | Pure delegation |
| `KotLExpansion.onRequest`, `getIdentifier`, `persist`, `getAuthor`, `getVersion` | `placeholder.KotLExpansion` | Adapter stays thin |
| `top`, `playerStat`, `king`, params splitting | `leaderboard.PlaceholderRouter` | Placeholder grammar is a separate, testable concern |

### 4.12 `listener`

| Method | Target type | Note |
|---|---|---|
| `ArenaCombatListener.onDamageByEntity`, `resolveAttacker` | `listener.PvpBoundaryListener` | |
| `ArenaCombatListener.onDamage` (FALL cancel) | `listener.FallDamageListener` | |
| `ArenaCombatListener.onDamage` (registerHit) | `listener.KnockbackAttributionListener` | |
| `ArenaRespawnListener.onRespawn` | `listener.ArenaRespawnListener` taking `TaskScheduler` instead of `KingOfTheLadder` | Removes the last non-bootstrap `Plugin` field |
| `ArenaMoveListener`, `ArenaDeathListener`, `ArenaQuitListener`, `KingPlateListener`, `SelectionWandListener` | unchanged bodies, dependencies narrowed to the one service each uses | |

### 4.13 `item.SelectionWand`

| Method | Target type | Note |
|---|---|---|
| `key`, `isWand` | stays | |
| `create` | stays | |
| `give` | `platform.ItemGiver.give(player, stack)` shared with the kit service | Inventory mutation, sound, drop-leftovers is not wand-specific |

---

## 5. Classes to be broken apart entirely

Full replacements. Each old class name disappears; no back-compat shims.

1. **`ArenaManager` (651 → ~110 + 17 new types).** The single worst offender: registry, persistence, membership, snapshots, crown, combat attribution, rating, kit, selections, timer, diagnostics, and four private algorithms in one main-thread class. Replaced by `store/ArenaRepository`, 5 `state/` holders, 10 `gameplay/` services, and a delegation-only `ArenaManager`.
2. **`DatabaseManager` (380 → ~95 + 5 new types).** Connection tuning, SQL text, row mapping, cache, ranking, async writing, presence tracking. Replaced by `SqliteConnectionFactory`, `StatsTable`, `RowMapper`, `StatsCache`, `AsyncStatsWriter`, `Leaderboard`, `PlayerPresenceTracker` behind `StatsService`.
3. **`KingOfTheLadder` (190 → ~40 + 4 new types).** Composition plus 10 accessors. Replaced by `bootstrap/KotlBootstrap` + `ListenerRegistrar` + `CommandRegistrar` + `PlaceholderRegistrar`; the plugin class becomes a two-method shim.
4. **`CommandContext` (144 → ~35 + 3 new types).** Replaced by `CommandContext` (sender + args + responder), `CommandArgs`, `CommandSenderAdapter`, `text.MessageSender`.
5. **`DialogView` (207 → 4 types).** Replaced by `DialogFactory`, `DialogPresenter`, `DialogBodies`, `DialogButtons`, `DialogCallbacks`.
6. **`GlickoManager` (293 → 4 types).** Replaced by `GlickoEngine`, `VolatilitySolver`, `GlickoScale`, `GlickoSettings` (+ optional `RatingDecayPolicy`).
7. **`SavedState` (178 → value + vault).** Replaced by `PlayerSnapshot` composed of 4 grouped records, plus `PlayerStateVault` for capture/restore.
8. **`FileSettings` (139 → 6 types).** One god record replaced by 5 domain records + `ConfigValues`.
9. **`SetupDialogManager` + `SetupActions` (~73).** Both deleted outright; the wizard talks to `ArenaAdminService`.
10. **`PlaceholderManager` (77).** Deleted; lifecycle folds into `PlaceholderRegistrar`.

## 6. Delete / fix list

| Item | Reason |
|---|---|
| `PlaceholderManager` | No responsibility beyond registration flag + a delegating getter |
| `SetupDialogManager` | Pass-through facade over one class |
| `SetupActions.run` | UI reaching into the command layer through `plugin.commands()` |
| All 9 plugin accessors | Service-locator smell; replaced by injection |
| `ArenaManager.problems`/`reportProblems` from the facade | Diagnostics get their own type |
| `GlickoManager.decay` + `inactivity-growth-per-day` | No caller. Either wire into housekeeping or delete both |
| `GlickoManager.MIN_DEVIATION/MIN_RATING/MAX_RATING/WIN/LOSS` | Referenced by nothing; use or delete |
| `DatabaseManager.onlinePlayerCount` | Name lies: returns players ever seen. Rename `knownPlayerCount`, fix the enable log |
| README `me.obvgreen.command.sub` claim | No `.sub` package exists; fix during the package-rule wave |
| `checkGlicko` not wired into `check` | Ratings can drift silently; wire it in the safety-rail wave |

---

## 7. Ordered checklist — bottom-up

Waves run in order. Within a wave, steps run in listed order. Each step compiles, passes
`.\gradlew.bat build`, `checkPackages`, `checkGlicko`, and the manual smoke list in §8 before the next
begins. **Nothing that depends on a type in a later wave is touched in an earlier wave.**

### Wave 0 — Safety rails (no production code changes)

- [ ] 0.1 Wire `checkGlicko` into the `check` task in `build.gradle.kts`, so every build verifies the rating maths.
- [ ] 0.2 Add a characterisation harness (same shape as `tools/`: standalone `main` programs, no JUnit dependency) covering: `Arena.of` bounds swapping, `ArenaBounds.contains` edges, `StatCategory.format`, `StatsService.top/rankOf` ordering, `GlickoEngine.rate` known vectors, and the config parser's defaults for every key.
- [ ] 0.3 Record today's chat strings, permission outcomes, and placeholder outputs (`%kotl_top_rating_1_name%` etc.) in a golden-file fixture; the harness compares against it.
- [ ] 0.4 Add `tools/ArchitectureCheck.java` (wired into `check`) asserting: no non-bootstrap class imports `me.obvgreen.KingOfTheLadder`; no class named `*Manager` outside an allow list; no class over the line budget.

**Exit:** build runs all three checks and passes on unmodified sources.

### Wave 1 — Pure leaves (zero plugin dependencies)

- [ ] 1.1 `glicko` split: introduce `GlickoSettings`, `GlickoEngine`, `VolatilitySolver`, `GlickoScale`; delete `GlickoManager`. Keep `GlickoRating` untouched. Resolve the unused constants and `decay` (use or delete). `tools/GlickoCheck.java` must still pass unchanged against the new types — update only its import lines.
- [ ] 1.2 `config` split: add `KitSettings`, `CombatSettings`, `KingSettings`, `LeaderboardSettings`, `MessageBundle`, `ConfigValues`; reduce `FileSettings` to an aggregating `from`. Same parsed values, same defaults, same clamps — verified by the 0.2 fixture.
- [ ] 1.3 `text`: add `MessageSender` (reply/success/error/info over an `Audience`/`CommandSender`). `Text` unchanged.
- [ ] 1.4 `database` value types: add `StatFormatter`; move formatting out of `StatCategory`/`RankEntry`. `PlayerStats`, `RankEntry`, `StatCategory` otherwise unchanged.

**Exit:** `config`, `glicko`, `text` still import nothing from the plugin; `checkGlicko` green.

### Wave 2 — Persistence

- [ ] 2.1 `SqliteConnectionFactory` — pragmas and connection opening extracted verbatim.
- [ ] 2.2 `StatsTable` + `RowMapper` — `CREATE_TABLE`, indexes, `UPSERT`, `SELECT_ALL`, row mapping. SQL text byte-identical.
- [ ] 2.3 `StatsCache` — the `ConcurrentHashMap` mirror, `get`/`peek`/`snapshot`/`mutate` semantics unchanged.
- [ ] 2.4 `AsyncStatsWriter` — the `KotL-DB` daemon executor, statement queueing, and the 5 s drain on shutdown.
- [ ] 2.5 `Leaderboard` — pure ordering and rank computation over a collection, no JDBC, no Bukkit.
- [ ] 2.6 `PlayerPresenceTracker` — `trackOnlinePlayers`, `nameOf`.
- [ ] 2.7 Collapse `DatabaseManager` into `StatsService` (lifecycle + domain API + delegation), rename `onlinePlayerCount` → `knownPlayerCount`, update the enable log line.

**Exit:** db file, schema, indexes, pragma set and thread name unchanged; existing DB opens and reads.

### Wave 3 — World/scheduler seams and item

- [ ] 3.1 New `platform` package: `WorldResolver` (replaces `Bukkit.getWorld` in the model) and `TaskScheduler` (run now/later/async-repeat).
- [ ] 3.2 New `platform.ItemGiver` — `give` with leftover drop and pickup sound, taken from `SelectionWand`.
- [ ] 3.3 `SelectionWand` keeps `key`, `isWand`, `create`; delegates giving.

**Exit:** no behaviour change; `platform` imports only Bukkit.

### Wave 4 — Arena value types

- [ ] 4.1 `ArenaBounds` — absorb `of`'s min/max swap, both `contains` overloads, `isKnockedOff`.
- [ ] 4.2 `Arena` reduced to identity + withers + `hasKingPlate`; `describe` moved out; `resolveWorld` moved to `WorldResolver`; `respawnOrCentre` to `ArenaSpawnResolver`.
- [ ] 4.3 Promote `Selection` and `PendingHit` to top-level types (`Selection` in `arena.state`).
- [ ] 4.4 Split `SavedState` into `PlayerSnapshot` (composed of inventory/vitals/movement/effects records) + `PlayerStateVault` for capture/restore.

**Exit:** records and containment semantics verified by the 0.2 fixture.

### Wave 5 — Arena persistence

- [ ] 5.1 `arena.store.ArenaRepository` — `loadArenas`, `readArena`, `parsePos`, `saveArena`, `deleteArena`, `ARENA_ROOT`. Same keys, same malformed-entry warnings, same log counts.

**Exit:** a config file written by the old code loads unchanged and round-trips.

### Wave 6 — Arena state holders

- [ ] 6.1 `MembershipRegistry`
- [ ] 6.2 `PlayerStateVault` (snapshot store)
- [ ] 6.3 `KingRegistry`
- [ ] 6.4 `SelectionRegistry` + top-level `Selection`
- [ ] 6.5 `KnockbackAttribution` (with `PendingHit`)
- [ ] 6.6 `ClaimCooldown`

**Exit:** each holder has no Bukkit-event knowledge and no cross-holder calls.

### Wave 7 — Gameplay services

- [ ] 7.1 `KitService.giveKit`
- [ ] 7.2 `ArenaEntryService.join` and `ArenaExitService.leave`
- [ ] 7.3 `ArenaMotionService` — `handleMove`, `teleportToRespawn`, `healFully`, death routing
- [ ] 7.4 `KnockoffService` — `handleKnockoff`, `scoreKnockoff`, `applyCombatRating`, `formatSigned`, `ratingOf`
- [ ] 7.5 `CrownService` — `claimCrown`, `king`, `kingName`, `pushBystanders`, `spawnFireworks`
- [ ] 7.6 `ArenaQueryService` — `arenas`, `byName`, `arenaAtBlockPos`, `activeAt`, `occupants`
- [ ] 7.7 `ArenaDiagnostics` — `problems`, `reportProblems`
- [ ] 7.8 `ArenaHousekeeping` — expiry task on `TaskScheduler`; `expirePendingHits` now coordinates 6.5 and 6.6
- [ ] 7.9 Collapse `ArenaManager` to a construction-and-delegation facade ≤ 120 lines

**Exit:** no `ArenaManager` method contains an algorithm; every listener-facing call has a named home.

### Wave 8 — Admin application service (the seam commands and dialogs will share)

- [ ] 8.1 `ArenaAdminService` — create / setPlate / setRespawn / toggle / delete-with-eject / name validation, plus `ArenaNameValidator`.
- [ ] 8.2 Re-point `CreateCommand`, `SetPlateCommand`, `RespawnCommand`, `ToggleCommand`, `DeleteCommand` at the service; `ArenaLookup` becomes `ArenaNameResolver`; `ListArenasCommand` uses `ArenaListPresenter`.
- [ ] 8.3 Give `CommandContext` its split: `CommandArgs`, `CommandSenderAdapter`, `text.MessageSender`, `command.Permissions`. Remove `plugin()` and `arenas()`; every subcommand takes constructor dependencies.

**Exit:** no subcommand reads a manager off the plugin class; `SetupActions` becomes a compile error in the next wave (expected, do it in the same commit as 9.1).

### Wave 9 — Leaderboard and placeholder packages

- [ ] 9.1 Move `PlaceholderCache` to `leaderboard.LeaderboardCache`; update `tools/PackageCheck.java` `ALLOWED` (drop `placeholder → dialog`).
- [ ] 9.2 Move `DialogManager` to `leaderboard.LeaderboardDialog`, extract `RankColours`.
- [ ] 9.3 `PlaceholderRouter` — parse `top` / `player_<category>` / `player_rank_<category>` / `king_<arena>`; `KotLExpansion` becomes an adapter over it.
- [ ] 9.4 Delete `PlaceholderManager`; move registration/unregistration into `bootstrap.PlaceholderRegistrar`.

**Exit:** the golden placeholder fixture from 0.3 matches byte-for-byte.

### Wave 10 — Dialog layer

- [ ] 10.1 Split `DialogView` into `DialogFactory`, `DialogPresenter`, `DialogBodies`, `DialogButtons`, `DialogCallbacks`; `DialogCallbacks` takes `TaskScheduler`.
- [ ] 10.2 `SetupDialogManager` deleted.
- [ ] 10.3 `SetupDialogs` → `SetupMenu`; `ArenaSetupPage` → `ArenaSetupMenu` + `DeleteConfirmationDialog` + `BoundsFormatter`; `CreateArenaPage` → `CreateArenaMenu` + `SelectionFormatter`.
- [ ] 10.4 Delete `SetupActions`; wire every setup button to `ArenaAdminService` directly. Dialog and command now share one implementation — the invariant the README already claims.

**Exit:** `/kotl setup` and the equivalent typed commands produce identical state changes (verify with the smoke list).

### Wave 11 — Listeners

- [ ] 11.1 Split `ArenaCombatListener` into `PvpBoundaryListener`, `FallDamageListener`, `KnockbackAttributionListener`.
- [ ] 11.2 `ArenaRespawnListener` takes `TaskScheduler` instead of `KingOfTheLadder`; the next-tick heal moves into `ArenaMotionService`.
- [ ] 11.3 Narrow every listener's constructor to the single service it uses.

**Exit:** no class outside `bootstrap` holds a `Plugin` reference.

### Wave 12 — Dispatcher

- [ ] 12.1 Extract `SubcommandDispatcher` and `SubcommandCompleter` from `KotLCommand`; the class becomes a two-method adapter.
- [ ] 12.2 `CommandRegistry` accepts an injected `List<KotlSubcommand>` instead of `new`-ing ten classes.
- [ ] 12.3 `HelpCommand.sendUsage` becomes `HelpPrinter`, called by the dispatcher's no-args path.

### Wave 13 — Composition root

- [ ] 13.1 `bootstrap.KotlBootstrap` — construction in dependency order, `start()`/`down()` with an ordered close list.
- [ ] 13.2 `ListenerRegistrar`, `CommandRegistrar`, `PlaceholderRegistrar`.
- [ ] 13.3 `KingOfTheLadder` reduced to `onEnable` → `bootstrap.up()`, `onDisable` → `bootstrap.down()`.
- [ ] 13.4 **Enforce** the new rule in `tools/PackageCheck.java`: only `me.obvgreen` and `me.obvgreen.bootstrap` may import the plugin class. Then delete any remaining violations.

**Exit:** `tools/ArchitectureCheck.java` (step 0.4) passes with zero exceptions.

### Wave 14 — Rules, docs, dead code

- [ ] 14.1 Update `ALLOWED` in `tools/PackageCheck.java` to the final graph, including `platform`, `leaderboard`, `bootstrap`.
- [ ] 14.2 Confirm no package cycle after the moves; `dialog.setup → command` must be gone.
- [ ] 14.3 Update `README.md` project layout (remove the `command.sub` claim, add `platform`/`leaderboard`/`bootstrap`).
- [ ] 14.4 Regenerate `STRUCTURE.md` from the new sources.
- [ ] 14.5 Final dead-code sweep: unused constants, unused methods, unused config keys, unused permissions.

---

## 8. Verification gates

Run after **every** step, not just every wave.

| Gate | Command / action |
|---|---|
| Compile + package rules | `.\gradlew.bat build` |
| Rating arithmetic | `.\gradlew.bat checkGlicko` (still standalone until 0.1, then part of `build`) |
| Characterisation fixtures | new harness from 0.2 / 0.3 |
| Architecture rules | `tools/ArchitectureCheck.java` |
| Manual smoke — arena | `/kotl create` → `setplate` → `spawn` → `toggle`; `/kotl list`; `/kotl delete tower confirm` |
| Manual smoke — gameplay | walk in → kit applied; hit → attribution; fall → respawn at arena spawn; press plate → crown + fireworks + push; `/kotl toggle` while occupied → everyone ejected |
| Manual smoke — UI | `/kotl setup` full wizard, including delete confirmation, side by side with the typed commands |
| Manual smoke — placeholders | `/papi list`, `/papi ecloud`, and the six `%kotl_*%` forms from the README table |
| Persistence | boot on an existing `kotl.db`; confirm the file, schema and row contents are untouched; confirm WAL pragmas still applied |

## 9. Risks and rollback

| Risk | Mitigation |
|---|---|
| Wave 7 is the risky one: 11 behaviours move out of a single main-thread class at once | Land 7.1–7.6 one service per commit; each keeps its current `ArenaManager` entry point as a delegating wrapper so listeners never break mid-wave; delete the wrappers in 7.9 |
| Config drift in `FileSettings` (Wave 1) | Same keys, same defaults, same clamps; the 0.2 fixture diffs the parsed record field by field |
| SQL drift (Wave 2) | Statement text moved verbatim; existing db file must open and read unchanged |
| Threading regression when `StatsCache`/`AsyncStatsWriter` split (Wave 2) | The single-writer guarantee is the whole design; `AsyncStatsWriter` owns the executor and nothing else may submit to it |
| Main-thread affinity (Waves 7, 9, 10) | Anything that touches Bukkit entities goes through `TaskScheduler`; services never call `Bukkit` statics after Wave 3 |
| Two entry points drifting apart (dialog vs command, Wave 10) | Both must call `ArenaAdminService`; `SetupActions` is deleted so a second path cannot be reintroduced silently |
| Package-check churn | Every step that moves a package updates `ALLOWED` in the same commit — a wave never leaves `build` red |

Rollback is per-step: each step is one commit that compiles and passes all gates, so reverting a single
commit restores a working state. No step depends on a later step.
