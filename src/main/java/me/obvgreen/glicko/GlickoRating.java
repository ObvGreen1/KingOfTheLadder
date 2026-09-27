package me.obvgreen.glicko;

/**
 * A Glicko-2 rating triple.
 *
 * <p>A pure value type, deliberately free of any dependency on the rest of the plugin: the
 * {@code glicko} package knows about arithmetic and nothing else, so the rating maths can be
 * read, tested and changed without holding a database or a Bukkit type in mind.</p>
 *
 * <p>The three constants are the starting values for a new player. They live here rather than
 * on {@link GlickoManager} because they describe a rating, not a policy — {@code
 * PlayerStats.defaults} needs them to seed a row, and the manager is not the only thing that
 * should know a new player starts at 1500.</p>
 *
 * @param rating      the Glicko scale rating, default {@value #DEFAULT_RATING}
 * @param deviation   the rating deviation (uncertainty), default {@value #DEFAULT_RATING_DEVIATION}
 * @param volatility  the volatility constant, default {@value #DEFAULT_VOLATILITY}
 */
public record GlickoRating(double rating, double deviation, double volatility) {

    public static final double DEFAULT_RATING = 1500.0;
    public static final double DEFAULT_RATING_DEVIATION = 350.0;

    /** The paper's step 1(a) starting volatility for an unrated player. */
    public static final double DEFAULT_VOLATILITY = 0.06;

    public static GlickoRating defaults() {
        return new GlickoRating(DEFAULT_RATING, DEFAULT_RATING_DEVIATION, DEFAULT_VOLATILITY);
    }
}
