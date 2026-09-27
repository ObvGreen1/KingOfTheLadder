You are an expert Minecraft plugin developer specializing in the modern Paper API and Java. Your task is to write a production-ready "King of the Ladder" (KotL) lobby minigame plugin. Do not skip over complex logic—provide complete, implementable Java classes.

### 1. Architecture & Tech Stack
*   **Language:** Java (Targeting JDK 25 language features and modern standards)
*   **API:** Paper API (Targeting Minecraft 26.2, utilizing Paper 1.21.7+ Native Dialog API)
*   **Build Tool:** Gradle (Kotlin DSL `build.gradle.kts`) or Maven (`pom.xml`). Include dependencies for paper-api and PlaceholderAPI.
*   **Database:** SQLite. Configure the connection to use Write-Ahead Logging (WAL) mode and asynchronous memory flushing for high-performance player stat tracking.
*   **Design Pattern:** Separate concerns into Managers (`ArenaManager`, `DatabaseManager`, `GlickoManager`, `DialogManager`), Listeners, and Commands.

### 2. Stats & Glicko-2 Rating System
*   Track the following stats in the SQLite database:
    *   `kills` (Count of players knocked off to their death/respawn)
    *   `deaths` (Count of times knocked off/respawned)
    *   `wins` (Count of times stepping on the King pressure plate)
    *   `rating` (Glicko-2 rating, default 1500.0)
    *   `rating_deviation` (Default 350.0)
    *   `volatility` (Default 0.06)
*   **Glicko-2 Combat Calculations:**
    *   Track the last player to hit someone. If Player B falls off or respawns within 5 seconds of being damaged by Player A, attribute a kill to Player A and a death to Player B.
    *   Calculate and apply immediate Glicko-2 rating updates for both players using the standard Step 1 through Step 8 Glicko-2 algorithm in `GlickoManager`.

### 3. PlaceholderAPI Integration (Leaderboards & Self Stats)
Register a robust PlaceholderExpansion for PlaceholderAPI (`%kotl_...%`) with async caching (refresh top 10 cache every 60 seconds):
*   **Top 10 Leaderboards (Categories: `rating`, `kills`, `deaths`, `wins`):**
    *   `%kotl_top_<category>_<1-10>_name%` - Returns the player name at position N (e.g. `%kotl_top_rating_1_name%`).
    *   `%kotl_top_<category>_<1-10>_value%` - Returns formatted value at position N (e.g. `%kotl_top_kills_1_value%`).
*   **Self Stats & Server Rank:**
    *   `%kotl_player_<category>%` - Returns the viewing player's current total (e.g. `%kotl_player_rating%`, `%kotl_player_kills%`).
    *   `%kotl_player_rank_<category>%` - Returns the viewing player's numerical leaderboard position for that category (e.g. `%kotl_player_rank_rating%`).
*   **Arena Stats:**
    *   `%kotl_king_<arena>%` - Returns the current King's name in a given arena.

### 4. Menus via Paper 1.21.7+ Dialog API
Implement in-game menus using Paper's native `Audience#showDialog(...)` and `Dialog.create(...)` API instead of traditional inventory GUIs:
*   **Leaderboard Dialog (`/kotl top` or `/kotl leaderboards`):**
    *   A dynamic `Dialog` showing top players across categories (`rating`, `kills`, `wins`), along with the viewing player's current personal rank and stats.
    *   Use action buttons or multi-choice options to toggle between leaderboard views.
*   **Admin Management Dialog (`/kotl gui`):**
    *   A `Dialog` for admins (`kotl.admin`) listing all active arenas.
    *   Includes interactive action buttons to create, toggle, or edit arena region boundaries and set the King pressure plate block.

### 5. Core Gameplay Loop
*   **Seamless Region Entry/Exit:** Arenas are defined by a 3D cuboid bounding box. Listen to `PlayerMoveEvent`. Entering the region automatically joins the arena; exiting restores state.
*   **State Management:** On join, save inventory, armor, and XP to memory. Clear player state, give full health/saturation, and equip the "KotL Kit" (Leather armor and Knockback I stick). On exit or leave, restore original inventory and state.
*   **The King Plate:** Physical pressure plate at the top of the tower.
    *   Stepping on it (`PlayerInteractEvent`) makes the player the active "King".
    *   Spawns fireworks, broadcasts title/messages, increments player `wins` count in database, pushes away nearby non-kings with radial velocity, and applies a 3-second claim cooldown.
*   **Combat Rules:**
    *   PvP enabled inside the region (prevent arena players from attacking external players).
    *   Disable fall damage within the arena boundaries.
    *   On health hitting 0 or hitting the arena floor after knockoff, trigger respawn: heal to max and teleport back to the arena spawn location.

### 6. Setup Commands (`kotl.admin`)
*   `/kotl wand` - Gives the admin a selection tool.
*   `/kotl create <name>` - Creates an arena from current selection.
*   `/kotl setplate <name>` - Sets the targeted pressure plate as the King plate.
*   `/kotl toggle <name>` - Toggles active status.
*   `/kotl gui` - Opens the Admin Dialog menu.
*   `/kotl top` - Opens the Leaderboard Dialog menu.

Please provide the complete `build.gradle.kts`, main plugin class, `DatabaseManager` (SQLite WAL setup), `GlickoManager`, `PlaceholderManager`, `DialogManager` (Paper Dialog API implementation), `ArenaManager`, and gameplay listeners.