package me.obvgreen.database;

import java.util.UUID;

/** One row of a leaderboard snapshot: a 1-based position plus the value it holds. */
public record RankEntry(int position, UUID uuid, String name, double value) {

    public String formatted(StatCategory category) {
        return category.format(value);
    }
}
