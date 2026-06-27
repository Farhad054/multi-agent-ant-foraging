import java.awt.Point;
import java.util.*;

/**
 * Stochastic Ant Colony Optimisation (ACO) foraging strategy.
 *
 * Phases
 *   SEARCH  – biased random walk from nest toward food.
 *             Follows existing pheromone trails probabilistically.
 *   RETURN  – carries food back to nest, depositing pheromones on the way.
 *             Deposited trails attract future search passes.
 *
 * Direction choice uses roulette-wheel (fitness-proportionate) selection over
 * 8 compass directions, weighted by:
 *   pheromone level ahead  (reinforces successful paths)
 *   goal bias              (soft pull toward current target)
 *   heading inertia        (momentum, avoids jitter)
 *   exploration floor      (guarantees every direction has non-zero probability)
 */
class StochasticAntStrategy implements MovementStrategy {

    // ── Tuning constants ──────────────────────────────────────────────────────
    private static final int   STEP          = 5;     // pixels per tick
    private static final int   LOOK_AHEAD    = 25;    // pixels ahead to sample pheromone
    private static final float DEPOSIT_HOME  = 0.10f; // pheromone deposited per tick when returning
    private static final float DEPOSIT_SEEK  = 0.01f; // faint trail left while searching
    private static final float W_PHERO       = 5.0f;  // weight: pheromone signal
    private static final float W_GOAL        = 2.5f;  // weight: pull toward target
    private static final float W_INERTIA     = 1.5f;  // weight: heading persistence
    private static final float W_EXPLORE     = 0.4f;  // base weight (exploration floor)

    // 8 compass directions
    private static final int[][] DIRS = {
        { 1,  0}, {-1,  0}, { 0,  1}, { 0, -1},
        { 1,  1}, { 1, -1}, {-1,  1}, {-1, -1}
    };

    // Pre-computed direction norms (axis = 1.0, diagonal = √2)
    private static final double[] DIR_NORM = new double[DIRS.length];
    static {
        for (int i = 0; i < DIRS.length; i++)
            DIR_NORM[i] = Math.sqrt(DIRS[i][0] * DIRS[i][0] + DIRS[i][1] * DIRS[i][1]);
    }

    // ── Per-avatar state ──────────────────────────────────────────────────────
    private static class AgentState {
        boolean hasFood  = false;
        int     headingX = 1;
        int     headingY = 0;
    }

    private final GamePanel panel;
    private final IdentityHashMap<Avatar, AgentState> states = new IdentityHashMap<>();

    StochasticAntStrategy(GamePanel panel) {
        this.panel = panel;
    }

    @Override public String name() { return "Stochastic Ant"; }
    @Override public String toString() { return name(); }

    // ── Main logic ────────────────────────────────────────────────────────────
    @Override
    public Point computeDelta(Avatar avatar, int panelWidth, int panelHeight,
                              List<Obstacle> obstacles, Random rng) {

        AgentState s = states.computeIfAbsent(avatar, a -> new AgentState());

        // ── Phase transitions ─────────────────────────────────────────────────
        if (!s.hasFood && panel.atFood(avatar)) {
            s.hasFood  = true;
            // Flip heading so the ant starts turning back
            s.headingX = -s.headingX;
            s.headingY = -s.headingY;
        } else if (s.hasFood && panel.atNest(avatar)) {
            s.hasFood  = false;
            s.headingX = -s.headingX;
            s.headingY = -s.headingY;
        }

        // ── Pheromone deposit ─────────────────────────────────────────────────
        int cx = avatar.getX() + avatar.getSize() / 2;
        int cy = avatar.getY() + avatar.getSize() / 2;
        panel.depositPheromone(cx, cy, s.hasFood ? DEPOSIT_HOME : DEPOSIT_SEEK);

        // ── Stochastic direction selection ────────────────────────────────────
        Point target = s.hasFood ? panel.getNestPos() : panel.getFoodPos();

        // Unit vector toward target
        double gx   = target.x - cx;
        double gy   = target.y - cy;
        double glen = Math.max(1.0, Math.hypot(gx, gy));
        double gux  = gx / glen;
        double guy  = gy / glen;

        // Current heading as unit vector
        double hLen = Math.max(1.0, Math.hypot(s.headingX, s.headingY));
        double hux  = s.headingX / hLen;
        double huy  = s.headingY / hLen;

        // Score each compass direction
        float[] weights = new float[DIRS.length];
        for (int i = 0; i < DIRS.length; i++) {
            int    dx   = DIRS[i][0];
            int    dy   = DIRS[i][1];
            double norm = DIR_NORM[i];

            // Pheromone at look-ahead cell
            int   lx    = (int)(cx + dx * LOOK_AHEAD);
            int   ly    = (int)(cy + dy * LOOK_AHEAD);
            float phero = panel.getPheromoneAt(lx, ly);

            // Cosine similarity with goal direction (only forward half-space)
            double dotGoal    = (dx / norm) * gux + (dy / norm) * guy;
            double dotInertia = (dx / norm) * hux + (dy / norm) * huy;

            weights[i] = W_EXPLORE
                       + W_PHERO   * phero
                       + W_GOAL    * (float) Math.max(0.0, dotGoal)
                       + W_INERTIA * (float) Math.max(0.0, dotInertia);
        }

        // Roulette-wheel selection
        float total = 0f;
        for (float w : weights) total += w;
        float r = rng.nextFloat() * total;
        int chosen = DIRS.length - 1;
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r <= 0f) { chosen = i; break; }
        }

        // Persist heading (used as inertia next tick)
        s.headingX = DIRS[chosen][0];
        s.headingY = DIRS[chosen][1];

        return new Point(DIRS[chosen][0] * STEP, DIRS[chosen][1] * STEP);
    }
}
