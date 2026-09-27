package me.obvgreen.command;

import me.obvgreen.KingOfTheLadder;
import me.obvgreen.arena.ArenaManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Locale;

/**
 * One invocation of {@code /kotl}: who ran it, what they typed, and the plugin they ran it
 * against.
 *
 * <p>This is the whole contract between the dispatcher and a subcommand, and it is also what the
 * setup dialog constructs when it wants to run a subcommand's logic without a player having to
 * type it. That is deliberate: a dialog button and the matching typed command go through
 * identical code, so they cannot drift apart.</p>
 */
public final class CommandContext {

    private final KingOfTheLadder plugin;
    private final CommandSender sender;
    private final String label;
    private final String[] args;

    public CommandContext(KingOfTheLadder plugin, CommandSender sender, String label, String[] args) {
        this.plugin = plugin;
        this.sender = sender;
        this.label = label;
        this.args = args;
    }

    /** Convenience for dialog code: same sender, but a different argument list. */
    public CommandContext withArgs(String... replacement) {
        return new CommandContext(plugin, sender, label, replacement);
    }

    public KingOfTheLadder plugin() {
        return plugin;
    }

    public CommandSender sender() {
        return sender;
    }

    public String label() {
        return label;
    }

    /** The full argument array, including the subcommand name at index 0. */
    public String[] args() {
        return args;
    }

    public ArenaManager arenas() {
        return plugin.arenas();
    }

    /**
     * The sender as a player.
     *
     * @return the player, or {@code null} when a console or another non-player ran the command
     */
    public Player player() {
        return sender instanceof Player player ? player : null;
    }

    public boolean isPlayer() {
        return sender instanceof Player;
    }

    /**
     * The argument at {@code index}, or {@code null} when it was not supplied.
     *
     * <p>Lower-cased, because every subcommand in this plugin is matched case-insensitively and
     * arena names are the only free-text values — keeping one rule avoids a class of bugs where
     * {@code /kotl toggle Tower} and {@code /kotl toggle tower} disagree.</p>
     */
    public String arg(int index) {
        if (index < 0 || index >= args.length) {
            return null;
        }
        return args[index].toLowerCase(Locale.ROOT);
    }

    /** The argument at {@code index} verbatim, preserving case. Use for names a user typed. */
    public String rawArg(int index) {
        return index < 0 || index >= args.length ? null : args[index];
    }

    public boolean has(String permission) {
        return sender.hasPermission(permission);
    }

    /** Sends a MiniMessage-formatted line to the sender. */
    public void reply(String miniMessage) {
        sender.sendMessage(ArenaManager.mini(miniMessage));
    }

    public void success(String miniMessage) {
        reply("<green>" + miniMessage);
    }

    public void error(String miniMessage) {
        reply("<red>" + miniMessage);
    }

    public void info(String miniMessage) {
        reply("<gray>" + miniMessage);
    }

    /**
     * Enforces {@code requiresPlayer} for a subcommand that needs a body to act on.
     *
     * @return the player, or {@code null} after explaining the refusal to the sender
     */
    public Player requirePlayer(String subcommand) {
        Player player = player();
        if (player == null) {
            error("<white>" + subcommand + " <red>must be run in-game — it needs a player to act on.");
        }
        return player;
    }

    /**
     * Enforces the subcommand's permission node.
     *
     * @return {@code true} when the sender may proceed
     */
    public boolean requirePermission(String permission) {
        if (has(permission)) {
            return true;
        }
        error("You need <white>" + permission + "<red> to do that.");
        return false;
    }

    @Override
    public String toString() {
        return "CommandContext[" + sender.getName() + " " + Arrays.toString(args) + "]";
    }
}
