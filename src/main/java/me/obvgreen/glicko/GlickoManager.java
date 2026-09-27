package me.obvgreen.glicko;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Glicko-2, implemented as the eight steps of Mark Glickman's
 * <a href="https://www.glicko.net/glicko/glicko2.pdf">Example of the Glicko-2 system</a>
 * (Boston University, March 2022), which is the current revision of the 2013 paper.
 *
 * <h2>Rating periods</h2>
 * <p>All eight steps run for every call, including a period holding a single game. The paper
 * puts no condition on the volatility step for small periods — it only notes that the system
 * "works best" with 10-15 games per period, which is advisory rather than a rule, so inventing
 * a special case here would be a departure from the specification, not an optimisation of it.
 * KotL resolves one knockoff at a time, so in practice every period is a single game.</p>
 *
 * <h2>Where new volatility feeds forward</h2>
 * <p>The easily-missed detail: step 6 uses the <em>new</em> volatility σ′², while the
 * inactive-player rule uses the <em>old</em> σ². Getting these backwards produces a rating
 * deviation that is subtly too tight.</p>
 *
 * <p>All methods are pure: no shared mutable state, safe to call from any thread.</p>
 */
public final class GlickoManager {


    /**
     * The Glicko-2 scale constant: step 2 defines
     * {@code mu = (r - 1500) / 173.7178} and {@code phi = RD / 173.7178}, and step 8 inverts it.
     */
    public static final double SCALE = 173.7178;

    /** The paper's default system constant; step 1 says 0.3-1.2 are all reasonable. */
    public static final double DEFAULT_TAU = 0.5;

    /** The convergence tolerance the paper names in step 5. */
    private static final double EPSILON = 0.000001;

    /** Guards the leftward search in step 5; the paper observes k is "almost always 1". */
    private static final int MAX_BRACKET_SEARCH = 100;

    public static final double WIN = 1.0;
    public static final double LOSS = 0.0;

    /**
     * Bounds kept on stored values. The paper does not clamp; a clamp is here because a rating
     * is player-visible and a runaway value would be permanent, being written to the database.
     */
    private static final double MIN_DEVIATION = 30.0;
    private static final double MAX_DEVIATION = 350.0;
    private static final double MIN_RATING = 100.0;
    private static final double MAX_RATING = 3500.0;

    private final double tau;
    private final double inactivityGrowthPerDay;

    public GlickoManager(FileConfiguration config) {
        this.tau = config == null ? DEFAULT_TAU : config.getDouble("glicko.tau", DEFAULT_TAU);
        this.inactivityGrowthPerDay = config == null
                ? 40.0
                : config.getDouble("glicko.inactivity-growth-per-day", 40.0);
    }

    /**
     * Runs the full Glicko-2 rating period — steps 1 to 8.
     *
     * @param player    the rating being updated
     * @param opponents the opponents played this period
     * @param scores    {@code scores.get(i)} is {@code player}'s result against
     *                  {@code opponents.get(i)}: {@link #WIN} or {@link #LOSS}
     * @return the updated rating
     * @throws IllegalArgumentException if the period is empty, mismatched, or self-referential
     */
    public GlickoRating rate(GlickoRating player, List<GlickoRating> opponents, List<Double> scores) {
        if (opponents.isEmpty()) {
            throw new IllegalArgumentException("a rating period needs at least one game");
        }
        if (opponents.size() != scores.size()) {
            throw new IllegalArgumentException("one score per opponent is required");
        }
        for (GlickoRating opponent : opponents) {
            // Identity, not equality: two brand-new players are both exactly (1500, 350, 0.06),
            // so a value comparison here would reject a perfectly ordinary match.
            if (opponent == player) {
                throw new IllegalArgumentException("a player cannot be rated against themselves");
            }
        }

        // Step 2: onto the Glicko-2 scale. The volatility is deliberately not scaled, and the
        // deviation divides by the scale rather than being offset — the r-1500 offset applies
        // to the rating alone.
        double mu = toGlickmanScale(player.rating());
        double phi = deviationToGlickmanScale(player.deviation());
        // Steps 3 and 4: the variance of the rating implied by outcomes alone, and the
        // performance delta against the pre-period rating.
        double sumEvidence = 0.0D;
        double sumDelta = 0.0D;
        for (int index = 0; index < opponents.size(); index++) {
            GlickoRating opponent = opponents.get(index);
            double gOpponent = g(deviationToGlickmanScale(opponent.deviation()));
            double expected = expectedScore(mu, toGlickmanScale(opponent.rating()), gOpponent);
            sumEvidence += gOpponent * gOpponent * expected * (1.0D - expected);
            sumDelta += gOpponent * (scores.get(index) - expected);
        }

        if (sumEvidence <= 0.0D) {
            // A period against opponents certain enough to leave no information: keep the rating
            // rather than divide by zero.
            return player;
        }
        double variance = 1.0D / sumEvidence;
        double delta = variance * sumDelta;

        // Step 5: the new volatility, by iteration.
        double newVolatility = Math.exp(solveVolatility(phi, variance, delta, player.volatility(), tau) / 2.0D);

        // Step 6: pre-period rating deviation. Uses the NEW volatility, unlike the
        // inactive-player rule in decay(), which uses the old one.
        double preDeviation = Math.sqrt(square(phi) + square(newVolatility));

        // Step 7: the final deviation, and the rating shifted by it. The paper multiplies the
        // *sum* of g(phi_j)(s_j - E_j), not delta -- delta is that sum already scaled by v, and
        // step 7 is the one place the unscaled form appears.
        double newPhi = 1.0D / Math.sqrt(1.0D / square(preDeviation) + 1.0D / variance);
        double newMu = mu + square(newPhi) * sumDelta;

        // Step 8: back to the original scale.
        return clamp(newVolatility, newMu, newPhi);
    }

    /**
     * Step 5: the new volatility as {@code ln(sigma'^2)}, found by the Illinois variant of
     * regula falsi.
     *
     * <p>Solves {@code f(x) = 0} for {@code x = ln(sigma'^2)}, where the paper defines</p>
     * <pre>
     * f(x) = e^x (delta^2 - phi^2 - v - e^x) / (2 (phi^2 + v + e^x)^2) - (x - a) / tau^2
     * a   = ln(sigma^2)
     * </pre>
     *
     * <p>This replaced the original Newton-Raphson iteration, which the paper reports was
     * "occasionally" non-convergent from a poor starting value. The bracketing matters: when
     * {@code delta^2 <= phi^2 + v} there is no closed-form upper bracket, so the paper steps
     * left from {@code a} in multiples of {@code tau} until the sign flips.</p>
     */
    private static double solveVolatility(double phi, double variance, double delta, double sigma, double tau) {
        double a = Math.log(square(sigma));

        double lower = a;
        double upper;
        if (square(delta) > square(phi) + variance) {
            upper = Math.log(square(delta) - square(phi) - variance);
        } else {
            int k = 1;
            while (k < MAX_BRACKET_SEARCH && volatilityF(a - k * tau, phi, variance, delta, tau, a) < 0.0D) {
                k++;
            }
            upper = a - k * tau;
        }

        double fLower = volatilityF(lower, phi, variance, delta, tau, a);
        double fUpper = volatilityF(upper, phi, variance, delta, tau, a);

        while (Math.abs(upper - lower) > EPSILON) {
            double probe = lower + (lower - upper) * fLower / (fUpper - fLower);
            double fProbe = volatilityF(probe, phi, variance, delta, tau, a);
            if (fProbe * fUpper <= 0.0D) {
                lower = upper;
                fLower = fUpper;
            } else {
                // The Illinois damping: halve the retained value so the same root is not
                // approached so steeply on the next secant step.
                fLower /= 2.0D;
            }
            upper = probe;
            fUpper = fProbe;
        }
        return lower;
    }

    /** {@code f(x)} from step 5: the secular equation whose root is {@code ln(sigma'^2)}. */
    private static double volatilityF(double x, double phi, double variance, double delta, double tau, double a) {
        double expX = Math.exp(x);
        double denominator = square(phi) + variance + expX;
        return (expX * (square(delta) - square(phi) - variance - expX)) / (2.0D * square(denominator))
                - (x - a) / square(tau);
    }

    /**
     * Applies the paper's inactive-player rule: a player who does not compete in the period
     * keeps their rating and volatility, while their deviation widens.
     *
     * <p>The paper writes this as {@code phi' = phi* = sqrt(phi^2 + sigma^2)} — note the
     * <em>old</em> volatility, the opposite of step 6, because the volatility is not updated.
     * KotL rates on knockoff rather than on a period, so a knockoff-free stretch is several
     * such periods; {@code daysInactive} scales that repetition.</p>
     *
     * @param daysInactive how long the player has been away; negatives are treated as zero
     */
    public GlickoRating decay(GlickoRating player, double daysInactive) {
        if (daysInactive <= 0.0D) {
            return player;
        }
        // Widening by a fixed amount per day is a deliberate KotL choice, not the paper's
        // formula: the paper's is per rating period, and KotL's period length is "at the
        // discretion of the administrator" — so the administrator (config.yml) sets it here.
        return new GlickoRating(player.rating(),
                Math.min(MAX_DEVIATION, player.deviation() + inactivityGrowthPerDay * daysInactive),
                player.volatility());
    }

    // ------------------------------------------------------------------ KotL's actual case

    /**
     * One knockoff against one opponent, treated as a complete rating period.
     *
     * @param score {@link #WIN} or {@link #LOSS} from {@code player}'s point of view
     */
    public GlickoRating rateSingle(GlickoRating player, GlickoRating opponent, double score) {
        return rate(player, List.of(opponent), List.of(score));
    }

    /**
     * A head-to-head: the winner scores {@link #WIN} and the loser {@link #LOSS}, each rated
     * against the other's pre-game rating.
     *
     * @return {@code [newWinner, newLoser]}
     */
    public GlickoRating[] rateMatch(GlickoRating winner, GlickoRating loser) {
        return new GlickoRating[]{
                rateSingle(winner, loser, WIN),
                rateSingle(loser, winner, LOSS)
        };
    }

    // ------------------------------------------------------------------ the paper's own functions, exposed

    /**
     * Step 3: {@code g(phi) = 1 / sqrt(1 + 3*phi^2/pi^2)}.
     * It scales how much a game against a given opponent actually tells us.
     */
    public static double g(double phi) {
        return 1.0D / Math.sqrt(1.0D + (3.0D * square(phi)) / square(Math.PI));
    }

    /**
     * Step 3: the expected score against an opponent, via the logistic form
     * {@code 1 / (1 + exp(-g(phi_j) * (mu - mu_j)))}.
     */
    public static double expectedScore(double mu, double muOpponent, double gOpponent) {
        return 1.0D / (1.0D + Math.exp(-gOpponent * (mu - muOpponent)));
    }

    public static double toGlickmanScale(double rating) {
        return (rating - GlickoRating.DEFAULT_RATING) / SCALE;
    }

    /**
     * Step 2: {@code phi = RD / 173.7178}.
     *
     * <p>Not the same transform as {@link #toGlickmanScale(double)}: a rating deviation is
     * already measured from zero, so the {@code r - 1500} offset must not be applied to it.
     * Substituting one for the other silently produces a wildly wrong {@code phi} — an RD of
     * 30 becomes -8.46 instead of 0.17.</p>
     */
    public static double deviationToGlickmanScale(double deviation) {
        return deviation / SCALE;
    }

    /** The inverse of {@link #deviationToGlickmanScale(double)}; used by step 8. */
    public static double deviationFromGlickmanScale(double scaled) {
        return SCALE * scaled;
    }

    public static double fromGlickmanScale(double scaled) {
        return SCALE * scaled + GlickoRating.DEFAULT_RATING;
    }

    // ------------------------------------------------------------------ helpers

    private static GlickoRating clamp(double volatility, double mu, double phi) {
        return new GlickoRating(
                Math.min(MAX_RATING, Math.max(MIN_RATING, fromGlickmanScale(mu))),
                Math.min(MAX_DEVIATION, Math.max(MIN_DEVIATION, deviationFromGlickmanScale(phi))),
                Math.min(1.0D, Math.max(1e-6D, volatility)));
    }

    private static double square(double value) {
        return value * value;
    }
}
