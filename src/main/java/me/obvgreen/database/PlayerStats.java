package me.obvgreen.database;

import me.obvgreen.glicko.GlickoRating;

import java.util.UUID;

/**
 * Immutable snapshot of one player's persisted career.
 *
 * <p>Instances are only ever produced by {@link DatabaseManager}; mutation happens on the
 * database side and the record is re-read, so listeners can safely hold on to a value.</p>
 */
public record PlayerStats(
        UUID uuid,
        String name,
        int kills,
        int deaths,
        int wins,
        double rating,
        double ratingDeviation,
        double volatility) {

    public static PlayerStats defaults(UUID uuid, String name) {
        return new PlayerStats(uuid, name, 0, 0, 0,
                GlickoRating.DEFAULT_RATING,
                GlickoRating.DEFAULT_RATING_DEVIATION,
                GlickoRating.DEFAULT_VOLATILITY);
    }

    public PlayerStats withName(String newName) {
        return new PlayerStats(uuid, newName, kills, deaths, wins, rating, ratingDeviation, volatility);
    }

    public PlayerStats withCounters(int newKills, int newDeaths, int newWins) {
        return new PlayerStats(uuid, name, newKills, newDeaths, newWins, rating, ratingDeviation, volatility);
    }

    public PlayerStats withRating(double newRating, double newDeviation, double newVolatility) {
        return new PlayerStats(uuid, name, kills, deaths, wins, newRating, newDeviation, newVolatility);
    }

    public double valueOf(StatCategory category) {
        return switch (category) {
            case RATING -> rating;
            case KILLS -> kills;
            case DEATHS -> deaths;
            case WINS -> wins;
        };
    }
}
