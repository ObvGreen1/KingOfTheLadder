package me.obvgreen.glicko;

import me.obvgreen.database.PlayerStats;

/**
 * A Glicko-2 rating triple.
 *
 * @param rating      the Glicko scale rating, default {@value GlickoManager#DEFAULT_RATING}
 * @param deviation   the rating deviation (uncertainty), default {@value GlickoManager#DEFAULT_RATING_DEVIATION}
 * @param volatility  the volatility constant, default {@value GlickoManager#DEFAULT_VOLATILITY}
 */
public record GlickoRating(double rating, double deviation, double volatility) {

    public static GlickoRating defaults() {
        return new GlickoRating(GlickoManager.DEFAULT_RATING,
                GlickoManager.DEFAULT_RATING_DEVIATION,
                GlickoManager.DEFAULT_VOLATILITY);
    }

    public static GlickoRating of(PlayerStats stats) {
        return new GlickoRating(stats.rating(), stats.ratingDeviation(), stats.volatility());
    }
}
