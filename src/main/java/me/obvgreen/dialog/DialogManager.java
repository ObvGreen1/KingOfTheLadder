package me.obvgreen.dialog;

import me.obvgreen.KingOfTheLadder;
import me.obvgreen.database.DatabaseManager;
import me.obvgreen.database.RankEntry;
import me.obvgreen.database.StatCategory;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The leaderboard dialog, opened by {@code /kotl top}.
 *
 * <p>Only the leaderboard lives here. The setup screens are in
 * {@link me.obvgreen.dialog.setup.SetupDialogManager}, and the shared building blocks — assembling a
 * dialog, registering a callback, building a button — are in {@link DialogView}, so neither has to
 * reimplement the other.</p>
 *
 * <p>The dialog is rebuilt on every open rather than cached, because a leaderboard is stale the
 * moment a knockoff lands; what is cached is the underlying data ({@link PlaceholderCache}), not
 * the rendered layout.</p>
 */
public final class DialogManager {

    private final DatabaseManager database;
    private final PlaceholderCache cache;
    private final DialogView view;

    public DialogManager(KingOfTheLadder plugin,
                         DatabaseManager database, PlaceholderCache cache) {
        this.database = database;
        this.cache = cache;
        this.view = new DialogView(plugin);
    }

    /**
     * Opens the leaderboard for {@code player} on {@code category}.
     *
     * <p>The body is the cached rank list; the buttons switch category and re-open in place, so
     * a player never has to close the dialog to compare boards.</p>
     */
    public void openLeaderboards(Player player, StatCategory category) {
        List<RankEntry> entries = cache.top(category);
        List<DialogBody> body = new ArrayList<>();

        body.add(DialogView.line("<gray>Top <white>" + cache.size() + " <gray>by <aqua>" + category.display()));
        body.add(DialogView.gap());

        if (entries.isEmpty()) {
            body.add(DialogView.line("<yellow>No results yet. Be the first to fall off."));
        } else {
            for (RankEntry entry : entries) {
                body.add(DialogView.line(rankColour(entry.position())
                        + "#" + entry.position()
                        + " <white>" + entry.name()
                        + " <dark_gray>- <gray>" + entry.formatted(category)));
            }
        }

        body.add(DialogView.gap());
        body.add(DialogView.line(selfSummary(player, category)));

        List<ActionButton> actions = new ArrayList<>();
        for (StatCategory option : StatCategory.values()) {
            if (option == category) {
                continue;
            }
            actions.add(view.button(
                    DialogView.text("<gray>" + option.display()),
                    DialogView.text("<gray>Show the <white>" + option.display() + " <gray>board"),
                    140,
                    view.onClick(player2 -> openLeaderboards(player2, option))));
        }
        actions.add(view.closeButton());

        view.show(player, view.build(
                DialogView.text("<gold><bold>KotL Top - " + category.display()),
                body,
                view.actions(actions, 3)));
    }

    /** The viewer's own line: name, position, and the value they actually hold. */
    private Component selfSummary(Player player, StatCategory category) {
        int rank = database.rankOf(player.getUniqueId(), category);
        String rankText = rank < 0 ? "unranked" : "#" + rank;
        double value = database.get(player).valueOf(category);
        return DialogView.text("<gray>You: <white>" + player.getName()
                + " <dark_gray>(" + rankText + ")"
                + " <gray>— <aqua>" + category.format(value));
    }

    private static String rankColour(int position) {
        return switch (position) {
            case 1 -> "<gradient:#ffd700:#ff8c00><bold>";
            case 2 -> "<gray>";
            case 3 -> "<#cd7f32>";
            default -> "<dark_gray>";
        };
    }
}
