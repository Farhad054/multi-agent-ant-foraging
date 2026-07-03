import java.awt.Point;
import java.awt.Rectangle;
import java.util.*;

/**
 * Stochastic Ant Colony Optimisation (ACO) foraging strategy.
 *
 * Phases
 *   SEARCH  – biased random walk from nest toward food.
 *             Follows the food trail (green) probabilistically.
 *   RETURN  – carries food back to nest, depositing the food trail on the way.
 *             Follows the nest trail (blue) it laid down while searching.
 *
 * Sensing model
 *   Ants do NOT know the exact location of the food or nest from the start.
 *   Only within SENSE_RADIUS of the current goal does a strong "smell/sight"
 *   bias kick in and pull the ant toward it. Beyond that range, movement is
 *   driven mostly by pheromone trails, heading inertia, randomness, and a weak
 *   scouting gradient so first food discovery remains observable in a demo.
 *
 * Direction choice uses roulette-wheel (fitness-proportionate) selection over
 * 8 compass directions, weighted by:
 *   pheromone level ahead  (reinforces successful paths)
 *   goal bias              (strong pull within SENSE_RADIUS, weak scout cue outside)
 *   outward bias           (searchers spread away from the nest before finding food)
 *   heading inertia        (momentum, avoids jitter)
 *   exploration floor      (guarantees every direction has non-zero probability)
 * Directions that would walk the ant into an obstacle are excluded entirely,
 * which lets ants steer around rocks rather than just stopping at them.
 */
class StochasticAntStrategy implements MovementStrategy {

    // ── Tuning constants ──────────────────────────────────────────────────────
    private static final int   STEP             = 5;     // pixels per tick
    private static final int   LOOK_AHEAD        = 25;    // pixels ahead to sample pheromone
    private static final int   SENSE_RADIUS      = 450;   // pixels: strong smell/vision range for goal bias
    private static final int   OBSTACLE_LOOKAHEAD = 14;   // pixels ahead checked for obstacle avoidance
    private static final float DEPOSIT_FOOD_TRAIL = 0.10f; // green trail, laid while returning with food
    private static final float DEPOSIT_NEST_TRAIL = 0.04f; // blue trail, laid while searching
    private static final float W_PHERO       = 5.0f;  // weight: pheromone signal
    private static final float W_GOAL        = 4.0f;  // strong pull toward target once inside sense range
    private static final float W_SCOUT       = 0.35f; // weak broad gradient; prevents hour-long wandering
    private static final float W_OUTWARD     = 0.75f; // search bias away from nest before food is found
    private static final float W_INERTIA     = 0.9f;  // heading persistence
    private static final float W_EXPLORE     = 0.7f;  // base weight (exploration floor)
    private static final float W_JITTER      = 0.25f; // per-tick noise to break clusters and loops

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
        // Returning ants lay the food trail (green) so searchers can find the food.
        // Searching ants lay the nest trail (blue) so they (or others) can find the way home.
        int cx = avatar.getX() + avatar.getSize() / 2;
        int cy = avatar.getY() + avatar.getSize() / 2;
        if (s.hasFood) panel.depositFoodTrail(cx, cy, DEPOSIT_FOOD_TRAIL);
        else            panel.depositNestTrail(cx, cy, DEPOSIT_NEST_TRAIL);

        // ── Stochastic direction selection ────────────────────────────────────
        Point target = s.hasFood ? panel.getNestPos() : panel.getFoodPos();

        // Unit vector toward target + raw distance (used to gate the goal bias)
        double gx   = target.x - cx;
        double gy   = target.y - cy;
        double glen = Math.hypot(gx, gy);
        double gNorm = Math.max(1.0, glen);
        double gux  = gx / gNorm;
        double guy  = gy / gNorm;
        boolean withinSenseRange = glen <= SENSE_RADIUS;

        // Searchers that have not found a food trail yet should spread away
        // from the nest instead of orbiting the starting area.
        Point nest = panel.getNestPos();
        double ox = cx - nest.x;
        double oy = cy - nest.y;
        double oNorm = Math.max(1.0, Math.hypot(ox, oy));
        double oux = ox / oNorm;
        double ouy = oy / oNorm;

        // Current heading as unit vector
        double hLen = Math.max(1.0, Math.hypot(s.headingX, s.headingY));
        double hux  = s.headingX / hLen;
        double huy  = s.headingY / hLen;

        int size = avatar.getSize();

        // Score each compass direction
        float[] weights = new float[DIRS.length];
        for (int i = 0; i < DIRS.length; i++) {
            int    dx   = DIRS[i][0];
            int    dy   = DIRS[i][1];
            double norm = DIR_NORM[i];

            // Obstacles block this direction outright — lets ants steer around them.
            Rectangle ahead = new Rectangle(
                avatar.getX() + dx * OBSTACLE_LOOKAHEAD,
                avatar.getY() + dy * OBSTACLE_LOOKAHEAD,
                size, size);
            if (blockedByObstacle(ahead, obstacles)) {
                weights[i] = 0f;
                continue;
            }

            // Pheromone at look-ahead cell: searchers follow the food trail,
            // returners follow the nest trail.
            int   lx    = (int)(cx + dx * LOOK_AHEAD);
            int   ly    = (int)(cy + dy * LOOK_AHEAD);
            float phero = s.hasFood ? panel.getNestTrailAt(lx, ly) : panel.getFoodTrailAt(lx, ly);

            // Cosine similarity with goal direction (only forward half-space),
            // only applied once the ant is close enough to sense the target.
            double dotGoal    = (dx / norm) * gux + (dy / norm) * guy;
            double dotOutward = (dx / norm) * oux + (dy / norm) * ouy;
            double dotInertia = (dx / norm) * hux + (dy / norm) * huy;

            weights[i] = W_EXPLORE
                       + W_PHERO   * phero
                       + (withinSenseRange ? W_GOAL : W_SCOUT) * (float) Math.max(0.0, dotGoal)
                       + (!s.hasFood && !withinSenseRange ? W_OUTWARD * (float) Math.max(0.0, dotOutward) : 0f)
                       + W_INERTIA * (float) Math.max(0.0, dotInertia)
                       + rng.nextFloat() * W_JITTER;
        }

        // Roulette-wheel selection
        float total = 0f;
        for (float w : weights) total += w;
        if (total <= 1e-6f) {
            // Every direction blocked (rare) — fall back to ignoring obstacles
            // for this tick so the ant doesn't freeze in a dead end.
            Arrays.fill(weights, W_EXPLORE);
            total = W_EXPLORE * weights.length;
        }
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

    private boolean blockedByObstacle(Rectangle ahead, List<Obstacle> obstacles) {
        for (Obstacle o : obstacles) {
            if (o.getBounds().intersects(ahead)) return true;
        }
        return false;
    }
}
