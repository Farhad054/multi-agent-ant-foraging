import java.awt.Point;
import java.util.*;

/**
 * Behavioural regression test for the four ant-foraging strategies.
 *
 * There is no JUnit on the classpath, so this is a plain runnable checker: it
 * drives the real {@link GamePanel} game loop head-less (real evaporation,
 * collision, and all 12 avatars) and asserts properties that would break if a
 * strategy regressed — ants getting stuck, never returning, trail channels
 * getting crossed, or the RL agent failing to learn.
 *
 * The simulator seeds its RNG from the system clock, so runs are not
 * deterministic; thresholds are set well below observed means to stay robust.
 *
 * Build & run (from the "Modular Simulator" directory):
 *   javac -d out src/*.java test/*.java
 *   java  -ea -Djava.awt.headless=true -cp out StrategyBehaviorTest
 * Exits non-zero if any check fails.
 */
public class StrategyBehaviorTest {

    private static int failures = 0;

    public static void main(String[] args) {
        trailChannelsAreIndependent();
        for (String name : new String[]{"Stochastic Ant", "Stigmergy Ant",
                                        "Game Theory Ant", "RL Ant"}) {
            foragingIsHealthy(name);
        }
        rlLearnsOverTime();

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL CHECKS PASSED");
        } else {
            System.out.println(failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    // ── Individual checks ───────────────────────────────────────────────────────

    /** The nest (blue) and food (green) trail channels must not bleed into each other. */
    private static void trailChannelsAreIndependent() {
        GamePanel panel = new GamePanel();
        panel.depositNestTrail(300, 300, 0.5f);
        check("trail-channels: nest deposit raises nest trail",
              panel.getNestTrailAt(300, 300) > 0f);
        check("trail-channels: nest deposit leaves food trail at zero",
              panel.getFoodTrailAt(300, 300) == 0f);

        panel.depositFoodTrail(500, 400, 0.5f);
        check("trail-channels: food deposit raises food trail",
              panel.getFoodTrailAt(500, 400) > 0f);
        check("trail-channels: food deposit leaves nest trail at zero",
              panel.getNestTrailAt(500, 400) == 0f);
    }

    /** No strategy should freeze ants, and most of the colony should complete a round trip. */
    private static void foragingIsHealthy(String name) {
        GamePanel panel = strategyPanel(name);
        Point food = panel.getFoodPos(), nest = panel.getNestPos();
        List<Point> prev = panel.getAvatarCenters();
        int n = prev.size();
        boolean[] carrying = new boolean[n];
        boolean[] completedTrip = new boolean[n];
        double moveSum = 0;

        final int TICKS = 8000;
        for (int t = 0; t < TICKS; t++) {
            panel.actionPerformed(null);
            List<Point> cur = panel.getAvatarCenters();
            for (int i = 0; i < n; i++) {
                Point p = cur.get(i), q = prev.get(i);
                moveSum += Math.hypot(p.x - q.x, p.y - q.y);
                if (!carrying[i] && near(p, food, 30)) carrying[i] = true;
                else if (carrying[i] && near(p, nest, 35)) { carrying[i] = false; completedTrip[i] = true; }
            }
            prev = cur;
        }

        int trips = 0;
        for (boolean c : completedTrip) if (c) trips++;
        double avgMove = moveSum / ((double) TICKS * n);

        check(name + ": >=10/12 ants complete a full round trip (got " + trips + ")", trips >= 10);
        check(name + ": ants keep moving, not frozen (avgMove " + fmt(avgMove) + "px > 3)", avgMove > 3.0);
    }

    /** RL must actually learn: converged throughput clearly beats the cold-start window. */
    private static void rlLearnsOverTime() {
        GamePanel panel = strategyPanel("RL Ant");
        Point food = panel.getFoodPos(), nest = panel.getNestPos();
        int n = panel.getAvatarCenters().size();
        boolean[] carrying = new boolean[n];

        // Equal-length windows: cold start (high ε, ants leaving the nest corner)
        // vs. converged (ε at its floor, learned nest↔food route being exploited).
        int cold = countTrips(panel, food, nest, carrying, n, 2000); // exploratory
        countTrips(panel, food, nest, carrying, n, 1000);            // let ε decay to floor
        int warm = countTrips(panel, food, nest, carrying, n, 2000); // exploiting learned policy

        check("RL: learns — converged throughput >= 1.3x cold start (cold=" + cold + ", warm=" + warm + ")",
              warm >= cold * 1.3);
        check("RL: converges to efficient foraging (warm trips " + warm + " >= 40)", warm >= 40);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static int countTrips(GamePanel panel, Point food, Point nest,
                                  boolean[] carrying, int n, int ticks) {
        int trips = 0;
        for (int t = 0; t < ticks; t++) {
            panel.actionPerformed(null);
            List<Point> cur = panel.getAvatarCenters();
            for (int i = 0; i < n; i++) {
                Point p = cur.get(i);
                if (!carrying[i] && near(p, food, 30)) carrying[i] = true;
                else if (carrying[i] && near(p, nest, 35)) { carrying[i] = false; trips++; }
            }
        }
        return trips;
    }

    private static GamePanel strategyPanel(String name) {
        GamePanel panel = new GamePanel();
        MovementStrategy[] strats = {
            new StochasticAntStrategy(panel), new StigmergyAntStrategy(panel),
            new GameTheoryAntStrategy(panel), new RLAntStrategy(panel),
        };
        for (MovementStrategy s : strats) if (s.name().equals(name)) panel.setMovementStrategy(s);
        return panel;
    }

    private static boolean near(Point p, Point c, double r) {
        return Math.hypot(p.x - c.x, p.y - c.y) <= r;
    }

    private static String fmt(double v) { return String.format("%.2f", v); }

    private static void check(String label, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + label);
        if (!ok) failures++;
    }
}
