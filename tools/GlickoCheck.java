import me.obvgreen.glicko.GlickoManager;
import me.obvgreen.glicko.GlickoRating;

import java.util.List;

/**
 * Verifies the Glicko-2 implementation against Mark Glickman's own worked example.
 *
 * <p>The primary test is the paper's Table example (glicko.net, "Example of the Glicko-2
 * system", March 2022), which publishes every intermediate value. If this passes, the eight
 * steps are right; if it fails, they are not, regardless of what the code looks like.</p>
 *
 * <p>Run against the compiled classes:
 * {@code java -cp "target/tools;target/classes;<deps>" GlickoCheck}</p>
 */
public final class GlickoCheck {

    public static void main(String[] args) {
        paperExample();
        System.out.println();
        headToHead();
        System.out.println();
        edgeCases();
        System.out.println();
        System.out.println("ALL CHECKS PASSED");
    }

    /**
     * The paper's worked example, verbatim.
     *
     * <p>"Suppose a player rated 1500 competes against players rated 1400, 1550 and 1700,
     * winning the first game and losing the next two. Assume the 1500-rated player's rating
     * deviation is 200, and his opponents' are 30, 100 and 300, respectively. Assume the
     * 1500 player has volatility sigma = 0.06, and the system constant tau is 0.5."</p>
     *
     * <p>Published results: r' = 1464.06, RD' = 151.52, sigma' = 0.05999.</p>
     */
    private static void paperExample() {
        System.out.println("== Glickman's worked example ==");
        // tau = 0.5 is the paper's system constant for this example; a null config gives 0.5.
        GlickoManager glicko = new GlickoManager(null);

        GlickoRating player = new GlickoRating(1500.0, 200.0, 0.06);
        List<GlickoRating> opponents = List.of(
                new GlickoRating(1400.0, 30.0, 0.06),
                new GlickoRating(1550.0, 100.0, 0.06),
                new GlickoRating(1700.0, 300.0, 0.06));
        List<Double> scores = List.of(GlickoManager.WIN, GlickoManager.LOSS, GlickoManager.LOSS);

        // The paper's printed table, to 4 decimal places.
        check("g(phi_1)", GlickoManager.g(GlickoManager.deviationToGlickmanScale(30.0)), 0.9955, 5e-5);
        check("g(phi_2)", GlickoManager.g(GlickoManager.deviationToGlickmanScale(100.0)), 0.9531, 5e-5);
        check("g(phi_3)", GlickoManager.g(GlickoManager.deviationToGlickmanScale(300.0)), 0.7242, 5e-5);

        double mu = GlickoManager.toGlickmanScale(1500.0);
        check("E(1)", GlickoManager.expectedScore(mu, GlickoManager.toGlickmanScale(1400.0), 0.9955), 0.639, 5e-4);
        check("E(2)", GlickoManager.expectedScore(mu, GlickoManager.toGlickmanScale(1550.0), 0.9531), 0.432, 5e-4);
        check("E(3)", GlickoManager.expectedScore(mu, GlickoManager.toGlickmanScale(1700.0), 0.7242), 0.303, 5e-4);

        GlickoRating result = glicko.rate(player, opponents, scores);
        System.out.printf("computed: r'=%.4f  RD'=%.4f  sigma'=%.5f%n",
                result.rating(), result.deviation(), result.volatility());

        // Published: "r' = 1464.06", "RD' = 151.52", "The new volatility sigma' = 0.05999".
        // The rating tolerance is looser than the others because the paper computes mu' from E
        // values it prints to only 3 dp (0.639/0.432/0.303); the unrounded ones move r' by
        // ~0.01. Matching to that precision means matching the arithmetic, not the rounding.
        check("r'", result.rating(), 1464.06, 0.05);
        check("RD'", result.deviation(), 151.52, 0.05);
        check("sigma'", result.volatility(), 0.05999, 5e-5);
    }

    /** KotL's real call shape: one knockoff, one victim, each rated against the other. */
    private static void headToHead() {
        System.out.println("== head-to-head (KotL's actual path) ==");
        GlickoManager glicko = new GlickoManager(null);

        GlickoRating[] match = glicko.rateMatch(GlickoRating.defaults(), GlickoRating.defaults());
        System.out.printf("winner: r=%.2f RD=%.2f sigma=%.5f%n", match[0].rating(), match[0].deviation(), match[0].volatility());
        System.out.printf("loser : r=%.2f RD=%.2f sigma=%.5f%n", match[1].rating(), match[1].deviation(), match[1].volatility());

        check("winner above 1500", sign(match[0].rating() - 1500.0), 1.0);
        check("loser below 1500", sign(match[1].rating() - 1500.0), -1.0);
        // Both start at RD 350, so the exchange is exactly zero-sum.
        check("zero-sum", match[0].rating() + match[1].rating(), 3000.0, 1e-6);
        check("RD tightened", sign(350.0 - match[0].deviation()), 1.0);
        // The default players are mirror images, so they must end symmetric.
        check("symmetric RD", match[0].deviation(), match[1].deviation(), 1e-9);

        // A favourite should gain less than an underdog in the same matchup.
        GlickoRating strong = new GlickoRating(2200.0, 80.0, 0.06);
        GlickoRating weak = new GlickoRating(1200.0, 80.0, 0.06);
        double favouriteGain = glicko.rateSingle(strong, weak, GlickoManager.WIN).rating() - 2200.0;
        double underdogGain = glicko.rateSingle(weak, strong, GlickoManager.WIN).rating() - 1200.0;
        System.out.printf("favourite gain %.3f vs underdog gain %.3f%n", favouriteGain, underdogGain);
        check("underdog gains more", sign(underdogGain - favouriteGain), 1.0);
    }

    private static void edgeCases() {
        System.out.println("== edge cases ==");
        GlickoManager glicko = new GlickoManager(null);

        check("scale round-trip 1500", GlickoManager.fromGlickmanScale(GlickoManager.toGlickmanScale(1500.0)), 1500.0);
        check("scale round-trip 1900", GlickoManager.fromGlickmanScale(GlickoManager.toGlickmanScale(1900.0)), 1900.0);
        check("even matchup E = 0.5", GlickoManager.expectedScore(0.0, 0.0, 0.0583), 0.5, 1e-12);

        GlickoRating stale = glicko.decay(GlickoRating.defaults(), 365.0D);
        System.out.printf("after 365 idle days: RD=%.3f%n", stale.deviation());
        check("decay clamps at 350", stale.deviation(), 350.0, 1e-9);
        check("decay leaves rating", stale.rating(), 1500.0, 1e-9);
        check("zero decay is a no-op", glicko.decay(GlickoRating.defaults(), 0.0D).deviation(), 350.0, 1e-9);

        expectThrows("empty period", () -> glicko.rate(GlickoRating.defaults(), List.of(), List.of()));
        expectThrows("mismatched scores", () -> glicko.rate(
                GlickoRating.defaults(), List.of(GlickoRating.defaults()), List.of()));
        // Self-play is rejected by identity, so the test must pass the very same instance --
        // two equal-but-distinct ratings are two legitimate brand-new players, as shown above.
        GlickoRating solo = GlickoRating.defaults();
        expectThrows("self-play", () -> glicko.rate(solo, List.of(solo), List.of(GlickoManager.WIN)));
    }

    private static void expectThrows(String label, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            System.out.println("ok  rejects " + label + ": " + expected.getMessage());
            return;
        }
        throw new AssertionError("expected " + label + " to be rejected");
    }

    private static double sign(double value) {
        return Double.compare(value, 0.0) > 0 ? 1.0 : (Double.compare(value, 0.0) < 0 ? -1.0 : 0.0);
    }

    private static void check(String label, double actual, double expected) {
        check(label, actual, expected, 1e-9);
    }

    private static void check(String label, double actual, double expected, double tolerance) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
        System.out.println("ok  " + label + " = " + actual);
    }
}
