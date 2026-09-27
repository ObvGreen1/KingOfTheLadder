# King of the Ladder

A lobby minigame for Paper 26.2: players climb a tower, shove each other off the top, and the
last one standing owns the crown. Ratings run on Glicko-2, stats live in a WAL-mode SQLite
database, and the UI uses Paper's native dialog API.

---

## Requirements

| | |
|---|---|
| **Java** | **25** — the plugin is compiled with `--release 25` and will not load on an older JVM |
| **Server** | Paper (or a fork) for Minecraft **26.2**, build 129 or newer |
| **Optional** | [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6248/) 2.12.3, for the `%kotl_*%` placeholders |

Paper 26.2 ships the SQLite JDBC driver and PlaceholderAPI is declared `softdepend`, so neither
is shaded into the jar.

> The build uses a Java **toolchain** pinned to 25, so a JDK 25 must be installed
> somewhere on the machine, but `JAVA_HOME` does not matter — Gradle finds the JDK itself.
> To pin one specific install instead, uncomment the `org.gradle.java.installations.paths`
> line in `gradle.properties`:
> ```properties
> org.gradle.java.installations.paths=C:\\path\\to\\jdk-25
> ```

---

## Building

Gradle Kotlin DSL. The wrapper is checked in, so the only prerequisite is a JDK 25
installed on the machine. The first run downloads Gradle 9.8.0.

```powershell
.\gradlew.bat clean build
```

The jar lands in `build/libs/KingOfTheLadder-1.0.0.jar`.

The build resolves its JDK through a **toolchain** pinned in `gradle.properties`, so
`JAVA_HOME` can point at anything — including an older JDK — and the build still works.
By default Gradle keeps its caches and its distribution in `~/.gradle`; set
`GRADLE_USER_HOME` to keep them inside the project instead:

```powershell
$env:GRADLE_USER_HOME = "$PWD\_gradle\home"
.\gradlew.bat clean build
```

Drop the resulting jar into `plugins/` and restart. On first run you get
`plugins/KingOfTheLadder/config.yml`.

---

## Commands and permissions

Command: `/kotl` (alias `/kingoftheladder`). Every subcommand is a class of its own under
`me.obvgreen.command.sub`, and each one owns its own permission check, usage string and tab
completion.

| Command | Permission | What it does |
|---|---|---|
| `/kotl help` | none | Lists the subcommands you can actually see. |
| `/kotl setup` | `kotl.admin` | Opens the guided setup dialog (aliases `gui`, `admin`, `config`). |
| `/kotl top [category]` | `kotl.use` | Opens the leaderboard dialog. Category is `rating`, `kills`, `deaths` or `wins`; defaults to `rating`. Aliases `leaderboards`, `lb`. |
| `/kotl list` | `kotl.use` | Lists configured arenas in chat. |
| `/kotl wand` | `kotl.admin` | Gives the selection wand. |
| `/kotl create <name>` | `kotl.admin` | Creates an arena from the wand's two selected corners. |
| `/kotl setplate <name>` | `kotl.admin` | Sets the King pressure plate, using the block you are looking at. |
| `/kotl spawn <name>` | `kotl.admin` | Sets the respawn point to your current position. |
| `/kotl toggle <name>` | `kotl.admin` | Enables or disables an arena. Disabling also releases anyone standing inside it. |
| `/kotl delete <name> confirm` | `kotl.admin` | Deletes an arena. The `confirm` argument is required. |

`kotl.admin` defaults to op and has `kotl.use` as a child; `kotl.use` defaults to true.

### Setting up an arena

Everything can be done from the dialog:

1. `/kotl setup` — opens the setup menu.
2. **Give me the wand**, then left-click one corner of your tower and right-click the opposite one.
3. **New arena** — type a name and confirm. The arena is created from your selection.
4. Open the arena's page and use **Set King plate** (look at the plate first), **Set spawn here**,
   and **Enable arena**.

The same steps typed by hand, if you would rather not use the dialog:

1. `/kotl wand`
2. Left-click one corner, right-click the opposite corner.
3. `/kotl create tower`
4. Look at the King pressure plate: `/kotl setplate tower`
5. Stand where players should respawn: `/kotl spawn tower`
6. `/kotl toggle tower` to enable it.

Both routes call the same subcommand classes, so a button and a typed command cannot disagree.

The arena is the box between the two corners, and its `minY` is the floor: **falling below it
puts you back at the spawn point and keeps you in the game**. Leaving the box any other way —
walking out, `/tp`, a portal — returns your real inventory and drops you out of the arena.

---

## Gameplay

- Walking into an arena's region joins it, saves your real inventory, state and game mode, and
  gives you the kit: leather armour and a **Knockback I** stick.
- Standing on the King plate claims the crown: fireworks, a title, a broadcast, and a radial
  shove that pushes everyone else away from you. Re-claiming is rate-limited by
  `king.claim-cooldown-seconds`.
- A knockoff is credited to the **last player who hit you within 5 seconds**
  (`combat.knockback-window-seconds`). Fall on your own and nobody gets the kill.
- PvP only applies inside an arena, and fall damage is disabled while you are in one.
- You respawn at the arena spawn if you drop to zero health *or* fall below the arena floor.

---

## Placeholders

Registered with PlaceholderAPI as the `kotl` expansion. `<category>` is one of
`rating`, `kills`, `deaths`, `wins`.

| Placeholder | Returns |
|---|---|
| `%kotl_top_<category>_<1-10>_name%` | The player name at that position, or empty |
| `%kotl_top_<category>_<1-10>_value%` | Their value, formatted for that category |
| `%kotl_player_<category>%` | Your own total for that category |
| `%kotl_player_rank_<category>%` | Your 1-based position, or `-` if unranked |
| `%kotl_king_<arena>%` | The current King of that arena, or `-` |

Top-N is served from an in-memory snapshot rebuilt on a background thread every 60 seconds
(`leaderboard.cache-seconds`), so a scoreboard polling these every tick never touches the disk.

---

## Ratings

Glicko-2, implemented as the eight steps of Mark Glickman's
[*Example of the Glicko-2 system*](https://www.glicko.net/glicko/glicko2.pdf) (March 2022).
A new player starts at rating **1500**, rating deviation **350**, volatility **0.06**.

The implementation is verified against the paper's own worked example, which publishes every
intermediate value — see `tools/GlickoCheck.java`:

| | Paper | This plugin |
|---|---|---|
| r′ | 1464.06 | 1464.05 |
| RD′ | 151.52 | 151.52 |
| σ′ | 0.05999 | 0.06000 |

The check needs the compiled plugin classes plus `paper-api` and `adventure-api` on the
classpath, so it is wired in as a Gradle task:

```powershell
.\gradlew.bat checkGlicko
```

It prints each assertion and ends with `ALL CHECKS PASSED`, or throws with the expected and
actual value if the arithmetic drifts.

Two behaviours are deliberate departures from the paper, both because KotL updates per knockoff
rather than per rating period:

- **Ratings move on knockoff kills only.** Claiming the crown increments `wins` but does not
  touch the rating, because a crown is a position rather than a result.
- **Inactivity widens the deviation on a schedule** (`glicko.inactivity-growth-per-day`) instead
  of the paper's per-period rule. The paper leaves period length "at the discretion of the
  administrator", so that length is `config.yml`'s to set.

---

## Storage

`plugins/KingOfTheLadder/kotl.db`, SQLite in **WAL** mode so the leaderboard cache can read
while a kill is being written:

```
journal_mode=WAL   synchronous=NORMAL   temp_store=MEMORY
cache_size=-32000  busy_timeout=5000   wal_autocheckpoint=8000
```

Writes go through a single dedicated daemon thread, never the main thread. A complete
`ConcurrentHashMap` mirror of the table is kept in memory and flushed asynchronously, so
placeholder lookups and rank queries are memory reads.

---

## Project layout

```
src/main/java/me/obvgreen/
  KingOfTheLadder.java            plugin entry point, wires the graph, registers listeners
  config/FileSettings.java        config.yml parsed once at enable into an immutable record
  text/Text.java                  the one place a MiniMessage string becomes a Component
  arena/                          Arena, bounds, saved state, and the ArenaManager that owns the game
  command/                        dispatcher, permission and context types, the registry, and one
                                  class per subcommand (Setup, Help, Top, ListArenas, Wand, Create,
                                  SetPlate, SetSpawn, Toggle, Delete)
  database/                       SQLite (WAL), PlayerStats, StatCategory, RankEntry
  dialog/
    DialogView.java               shared registry-dialog plumbing
    DialogManager.java            leaderboard dialog
    PlaceholderCache.java         async top-N cache
    setup/                        guided setup wizard (SetupDialogs, ArenaSetupPage, CreateArenaPage)
  glicko/                         Glicko-2, steps 1-8; GlickoRating is a pure value type
  item/SelectionWand.java         the selection wand item stack
  listener/                       one class per event type: ArenaMoveListener,
                                  ArenaCombatListener, ArenaDeathListener, ArenaRespawnListener,
                                  ArenaQuitListener, KingPlateListener, SelectionWandListener
  placeholder/                    the %kotl_*% expansion
tools/
  GlickoCheck.java                standalone check of the rating maths
  PackageCheck.java               standalone check of the package layout
```

### Package rules

`config`, `glicko` and `text` are leaves: they import nothing from the plugin at all. Everything
else may only depend on what is listed in `ALLOWED` in `tools/PackageCheck.java`, and apart from
the composition root the package graph must stay acyclic. A shared helper belongs in a package
both callers may already use — `Text.of` rather than a `mini` method parked on a manager.

```powershell
.\gradlew.bat checkPackages
```

`build` runs it, so a class in the wrong package, a fully-qualified cross-package call or an
undeclared dependency fails the build instead of waiting to be noticed in review.

The managers are built in dependency order — ratings, then storage, then arenas, then the
placeholder cache, then dialogs — and the plugin enables the expansion only if PlaceholderAPI
is actually present.

---

## Troubleshooting

**`UnsupportedClassVersionError` / `release version 25 not supported`** — the JVM is older than
25. Check `java -version`.

**`PlaceholderAPI not found; %kotl_*% placeholders are unavailable.`** — expected, and only a
warning, when PlaceholderAPI is absent. Everything else works.

**Placeholders return empty** — top-N is rebuilt every 60 s, so a brand-new server has no rows
until the first refresh and the first result. Check the expansion registered:
`/papi list` or `/papi ecloud` should list `kotl`.

**A Player reports `PlaceholderAPI 2.12.x will not load`** — the jars published for 2.12.x on
repo.extendedclip.com are the plain jar rather than the shadow jar, so they are missing their
bundled bStats and Adventure dependencies and fail with
`NoClassDefFoundError: org/bstats/charts/CustomChart`. This is an upstream packaging issue,
not a KotL one. Use a bundled build, or repack the plain jar with its declared dependencies.

---

## Further reading

- [`docs/PROMPT.md`](docs/PROMPT.md) — the original specification this plugin was built from.
- [`docs/DIALOG_CROSSHAIR_FIX.md`](docs/DIALOG_CROSSHAIR_FIX.md) — why every `DialogBase` uses
  `pause(false)` and `afterAction(NONE)`, so opening a dialog does not snap the crosshair back to
  the centre of the screen.

---

## License

MIT — see [`LICENSE`](LICENSE).

