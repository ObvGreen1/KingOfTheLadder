package me.obvgreen.database;

import java.util.Locale;

/**
 * The four tracked leaderboard categories.
 *
 * <p>{@link #column()} is the literal SQLite column / placeholder key so that callers never
 * concatenate user input into SQL or placeholder identifiers.</p>
 */
public enum StatCategory {

    RATING("rating", true),
    KILLS("kills", false),
    DEATHS("deaths", false),
    WINS("wins", false);

    private final String column;
    private final boolean decimal;

    StatCategory(String column, boolean decimal) {
        this.column = column;
        this.decimal = decimal;
    }

    public String column() {
        return column;
    }

    public boolean decimal() {
        return decimal;
    }

    public String format(double value) {
        return decimal ? String.format(Locale.ROOT, "%.1f", value) : Long.toString(Math.round(value));
    }

    /** Lower-case display name, e.g. {@code Rating}. */
    public String display() {
        String lower = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** @return the category for {@code key}, or {@code null} when the key is unknown. */
    public static StatCategory byKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (StatCategory category : values()) {
            if (category.column.equals(normalized)) {
                return category;
            }
        }
        return null;
    }
}
