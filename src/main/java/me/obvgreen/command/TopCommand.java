package me.obvgreen.command;

import me.obvgreen.database.StatCategory;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code /kotl top [category]} — opens the leaderboard dialog.
 *
 * <p>Open to every player; there is no reason to hide a scoreboard behind a permission.</p>
 */
public final class TopCommand implements KotlSubcommand {

    @Override
    public String name() {
        return "top";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("leaderboards", "lb");
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }

    @Override
    public String description() {
        return "Open the leaderboard";
    }

    @Override
    public String usage() {
        return "/kotl top [rating|kills|deaths|wins]";
    }

    @Override
    public boolean execute(CommandContext context) {
        Player player = context.player();
        if (player == null) {
            return true;
        }

        String requested = context.arg(1);
        StatCategory category = StatCategory.RATING;
        if (requested != null) {
            category = StatCategory.byKey(requested.toLowerCase(Locale.ROOT));
            if (category == null) {
                context.error("Unknown category <white>" + requested
                        + "<red>. Use rating, kills, deaths or wins.");
                return true;
            }
        }

        context.plugin().dialogManager().openLeaderboards(player, category);
        return true;
    }

    @Override
    public List<String> complete(CommandContext context) {
        if (context.args().length != 2) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        Arrays.stream(StatCategory.values()).forEach(category -> options.add(category.column()));
        return options;
    }
}
