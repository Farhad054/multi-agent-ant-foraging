import java.awt.Point;
import java.awt.Rectangle;
import java.util.*;

/**
 * Stigmergy foraging strategy.
 *
 * The defining trait of stigmergy is <em>indirect coordination through the
 * environment</em>: ants never negotiate directly, they only read and write the
 * shared pheromone grid. This implementation makes that the dominant signal —
 * direction choice is driven mostly by the concentration of the relevant trail
 * ahead, with only a weak goal cue used to bootstrap the very first path. That is
 * the deliberate contrast with {@link StochasticAntStrategy}, which "cheats" by
 * locking onto the goal with a strong sensing bias once it is within range; here
 * the trail — not privileged goal knowledge — carries the colony to the food.
 * Because ants deposit onto and then prefer the same cells, a single sharp,
 * self-reinforcing trail emerges: the positive-feedback loop that is stigmergy.
 *
 * Selection is fitness-proportionate (roulette) over the pheromone-weighted
 * directions rather than a hard argmax: keeping every open direction reachable
 * lets scouts flow around obstacles and escape local traps, while the heavy
 * pheromone weight still collapses steady-state traffic onto the strongest trail.
 *
 * Phases
 *   SEARCH – walk from the nest toward the food, following the food trail (green).
 *   RETURN – carry food home, following the nest trail (blue) laid while searching.
 */
class StigmergyAntStrategy implements MovementStrategy {

    // ── Tuning constants ──────────────────────────────────────────────────────
    private static final int   STEP               = 5;    // pixels per tick
    private static final int   LOOK_AHEAD         = 22;    // pixels ahead to sample pheromone
    private static final int   OBSTACLE_LOOKAHEAD = 14;    // pixels ahead checked for avoidance
    private static final float DEPOSIT_FOOD_TRAIL = 0.16f; // strong deposit → vivid, readable trail
    private static final float DEPOSIT_NEST_TRAIL = 0.09f;

    private static final float W_GRADIENT = 6.0f;  // weight: pheromone concentration (the dominant signal)
    private static final float W_GOAL     = 2.2f;  // weak pull toward goal (bootstraps + keeps net progress)
    private static final float W_INERTIA  = 1.1f;  // heading persistence — keeps the line straight
    private static final float W_JITTER   = 0.30f; // noise: breaks symmetric ties / escapes dead loops
    private static final float W_EXPLORE  = 0.6f;  // positive floor — every open direction stays reachable

    // 8 compass directions
    private static final int[][] DIRS = {
        { 1,  0}, {-1,  0}, { 0,  1}, { 0, -1},
        { 1,  1}, { 1, -1}, {-1,  1}, {-1, -1}
    };
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

    StigmergyAntStrategy(GamePanel panel) { this.panel = panel; }

    @Override public String name()     { return "Stigmergy Ant"; }
    @Override public String toString() { return name(); }

    @Override
    public Point computeDelta(Avatar avatar, int panelWidth, int panelHeight,
                              List<Obstacle> obstacles, Random rng) {

        AgentState s = states.computeIfAbsent(avatar, a -> new AgentState());

        // ── Phase transitions ─────────────────────────────────────────────────
        if (!s.hasFood && panel.atFood(avatar)) {
            s.hasFood  = true;
            s.headingX = -s.headingX;
            s.headingY = -s.headingY;
        } else if (s.hasFood && panel.atNest(avatar)) {
            s.hasFood  = false;
            s.headingX = -s.headingX;
            s.headingY = -s.headingY;
        }

        int cx = avatar.getX() + avatar.getSize() / 2;
        int cy = avatar.getY() + avatar.getSize() / 2;

        // ── Deposit onto the shared medium (the whole point of stigmergy) ──────
        if (s.hasFood) panel.depositFoodTrail(cx, cy, DEPOSIT_FOOD_TRAIL);
        else           panel.depositNestTrail(cx, cy, DEPOSIT_NEST_TRAIL);

        // Goal / heading unit vectors (goal is only a weak bootstrap cue).
        Point  target = s.hasFood ? panel.getNestPos() : panel.getFoodPos();
        double gx = target.x - cx, gy = target.y - cy;
        double gNorm = Math.max(1.0, Math.hypot(gx, gy));
        double gux = gx / gNorm, guy = gy / gNorm;

        double hLen = Math.max(1.0, Math.hypot(s.headingX, s.headingY));
        double hux = s.headingX / hLen, huy = s.headingY / hLen;

        int size = avatar.getSize();

        // ── Score each (unobstructed) direction ────────────────────────────────
        // Pheromone concentration dominates; the goal term is a weak bootstrap
        // cue. Weights stay strictly positive (W_EXPLORE floor) for roulette.
        float[]   weights = new float[DIRS.length];
        boolean[] blocked = new boolean[DIRS.length];
        int       allowed = 0;

        for (int i = 0; i < DIRS.length; i++) {
            int dx = DIRS[i][0], dy = DIRS[i][1];
            double norm = DIR_NORM[i];

            Rectangle ahead = new Rectangle(
                avatar.getX() + dx * OBSTACLE_LOOKAHEAD,
                avatar.getY() + dy * OBSTACLE_LOOKAHEAD, size, size);
            if (blockedByObstacle(ahead, obstacles)) { blocked[i] = true; continue; }
            allowed++;

            int   lx = cx + dx * LOOK_AHEAD;
            int   ly = cy + dy * LOOK_AHEAD;
            // Searchers follow the food trail; returners follow the nest trail.
            float phero = s.hasFood ? panel.getNestTrailAt(lx, ly) : panel.getFoodTrailAt(lx, ly);

            double dotGoal    = (dx / norm) * gux + (dy / norm) * guy;
            double dotInertia = (dx / norm) * hux + (dy / norm) * huy;

            weights[i] = W_EXPLORE
                       + W_GRADIENT * phero
                       + W_GOAL     * (float) Math.max(0.0, dotGoal)
                       + W_INERTIA  * (float) Math.max(0.0, dotInertia)
                       + rng.nextFloat() * W_JITTER;
        }

        // Fully boxed-in: ignore obstacles this tick so the ant can't freeze.
        if (allowed == 0) {
            int b = rng.nextInt(DIRS.length);
            s.headingX = DIRS[b][0];
            s.headingY = DIRS[b][1];
            return new Point(DIRS[b][0] * STEP, DIRS[b][1] * STEP);
        }

        // Fitness-proportionate pick: heavy pheromone weight collapses traffic
        // onto the trail, while every open direction keeps a non-zero chance so
        // scouts can round obstacles and escape local traps.
        int best = roulette(weights, blocked, rng);

        s.headingX = DIRS[best][0];
        s.headingY = DIRS[best][1];
        return new Point(DIRS[best][0] * STEP, DIRS[best][1] * STEP);
    }

    /** Fitness-proportionate pick over the unblocked directions. */
    private int roulette(float[] weights, boolean[] blocked, Random rng) {
        float total = 0f;
        for (int i = 0; i < weights.length; i++) if (!blocked[i]) total += weights[i];
        float r = rng.nextFloat() * total;
        int last = -1;
        for (int i = 0; i < weights.length; i++) {
            if (blocked[i]) continue;
            last = i;
            r -= weights[i];
            if (r <= 0f) return i;
        }
        return last; // numerical fallback: last unblocked direction
    }

    private boolean blockedByObstacle(Rectangle ahead, List<Obstacle> obstacles) {
        for (Obstacle o : obstacles) if (o.getBounds().intersects(ahead)) return true;
        return false;
    }
}
