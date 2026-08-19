import java.awt.Point;
import java.awt.Rectangle;
import java.util.*;

/**
 * Game-theoretic foraging strategy — foraging framed as a spatial congestion game.
 *
 * Every ant is a self-interested player choosing the move that maximises its own
 * payoff. The payoff combines a <em>benefit</em> (making useful progress) and a
 * <em>congestion cost</em> (crowding is wasteful):
 *
 *   benefit    – follow the recruiting pheromone trail ahead, plus a pull toward
 *                the goal <b>only once the ant is close enough to sense it</b>, plus
 *                an outward push while searching so scouts spread out to find food.
 *   − congestion – sharing a cell with many other ants is costly (finite trail
 *                width, jostling). A route everyone takes stops paying off, so
 *                rational ants disperse onto alternative lanes.
 *
 * Fairness note: this strategy does <b>not</b> know where the food is in advance.
 * It uses exactly the same information model as the stochastic baseline — the goal
 * exerts a strong pull only within {@code SENSE_RADIUS}; beyond that the ant must
 * discover food by exploring outward and following the food trail laid by ants
 * that have already found it. The distinctive game-theoretic ingredient is what it
 * does with that information: each ant plays a <em>best response</em> (argmax
 * payoff over the 8 moves) to the current positions of the others. Because the
 * crowd an ant sees is itself the product of everyone's best responses, the colony
 * settles toward a Nash-like traffic equilibrium — goal-directed, yet spread across
 * parallel lanes instead of piling into one congested line. A small "trembling
 * hand" keeps the population off degenerate fixed points.
 *
 * Congestion is measured directly from the other agents' positions (via
 * {@link GamePanel#getAvatarCenters()}): coordination here is through mutual
 * observation of players, layered on top of the shared pheromone medium.
 */
class GameTheoryAntStrategy implements MovementStrategy {

    // ── Tuning constants ──────────────────────────────────────────────────────
    private static final int    STEP               = 5;    // pixels per tick
    private static final int    OBSTACLE_LOOKAHEAD = 14;    // pixels ahead checked for avoidance
    private static final int    PROBE              = 24;    // how far ahead a candidate cell is evaluated
    private static final int    LOOK_AHEAD         = 25;    // pixels ahead to sample the pheromone trail
    private static final int    SENSE_RADIUS       = 450;   // goal is only "sensed" within this range
    private static final double CROWD_RADIUS       = 42.0;  // neighbours within this range count as congestion
    private static final float  DEPOSIT_FOOD_TRAIL = 0.10f;
    private static final float  DEPOSIT_NEST_TRAIL = 0.04f;
    private static final double TREMBLE            = 0.06;  // probability of a random (exploratory) move

    // Benefit terms — same information model as StochasticAntStrategy.
    private static final float W_PHERO     = 5.0f;  // follow the recruiting trail ahead
    private static final float W_GOAL      = 4.0f;  // strong pull toward goal, only within SENSE_RADIUS
    private static final float W_SCOUT     = 0.35f; // weak goal cue beyond sensing range
    private static final float W_OUTWARD   = 0.75f; // searchers spread away from the nest to find food
    // Game-theoretic terms.
    private static final float W_CONGESTION = 2.2f; // penalty per crowding neighbour (best-response driver)
    private static final float W_INERTIA    = 0.8f; // heading persistence
    private static final float W_JITTER     = 0.20f; // tiny tie-breaking noise

    private static final int[][] DIRS = {
        { 1,  0}, {-1,  0}, { 0,  1}, { 0, -1},
        { 1,  1}, { 1, -1}, {-1,  1}, {-1, -1}
    };
    private static final double[] DIR_NORM = new double[DIRS.length];
    static {
        for (int i = 0; i < DIRS.length; i++)
            DIR_NORM[i] = Math.sqrt(DIRS[i][0] * DIRS[i][0] + DIRS[i][1] * DIRS[i][1]);
    }

    private static class AgentState {
        boolean hasFood  = false;
        int     headingX = 1;
        int     headingY = 0;
    }

    private final GamePanel panel;
    private final IdentityHashMap<Avatar, AgentState> states = new IdentityHashMap<>();

    GameTheoryAntStrategy(GamePanel panel) { this.panel = panel; }

    @Override public String name()     { return "Game Theory Ant"; }
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

        // Searchers lay the blue nest trail; returners lay the green food trail.
        if (s.hasFood) panel.depositFoodTrail(cx, cy, DEPOSIT_FOOD_TRAIL);
        else           panel.depositNestTrail(cx, cy, DEPOSIT_NEST_TRAIL);

        // Goal direction + whether the ant is close enough to actually sense it.
        Point  goal = s.hasFood ? panel.getNestPos() : panel.getFoodPos();
        double gx = goal.x - cx, gy = goal.y - cy;
        double glen = Math.hypot(gx, gy);
        double gNorm = Math.max(1.0, glen);
        double gux = gx / gNorm, guy = gy / gNorm;
        boolean withinSense = glen <= SENSE_RADIUS;

        // Outward direction (away from the nest) — searchers use it to spread out
        // and discover food when the goal is still out of sensing range.
        Point  nest = panel.getNestPos();
        double ox = cx - nest.x, oy = cy - nest.y;
        double oNorm = Math.max(1.0, Math.hypot(ox, oy));
        double oux = ox / oNorm, ouy = oy / oNorm;

        // Snapshot of the other players' positions (the "crowd" this ant responds to).
        List<Point> others = panel.getAvatarCenters();

        double hLen = Math.max(1.0, Math.hypot(s.headingX, s.headingY));
        double hux = s.headingX / hLen, huy = s.headingY / hLen;
        int size = avatar.getSize();

        // ── Best response: argmax payoff over the 8 moves ──────────────────────
        float bestPayoff = Float.NEGATIVE_INFINITY;
        int   best       = -1;
        boolean tremble  = rng.nextDouble() < TREMBLE;

        for (int i = 0; i < DIRS.length; i++) {
            int dx = DIRS[i][0], dy = DIRS[i][1];
            double norm = DIR_NORM[i];

            Rectangle ahead = new Rectangle(
                avatar.getX() + dx * OBSTACLE_LOOKAHEAD,
                avatar.getY() + dy * OBSTACLE_LOOKAHEAD, size, size);
            if (blockedByObstacle(ahead, obstacles)) continue;

            // Recruiting pheromone ahead: searchers read the food trail, returners
            // the nest trail — this is how an ant that cannot yet sense the goal
            // still gets pulled toward it, exactly as in the stochastic baseline.
            int   lx = cx + dx * LOOK_AHEAD;
            int   ly = cy + dy * LOOK_AHEAD;
            float phero = s.hasFood ? panel.getNestTrailAt(lx, ly) : panel.getFoodTrailAt(lx, ly);

            double dotGoal    = (dx / norm) * gux + (dy / norm) * guy;
            double dotOutward = (dx / norm) * oux + (dy / norm) * ouy;
            double dotInertia = (dx / norm) * hux + (dy / norm) * huy;

            // Congestion at the probed cell (best-response driver).
            int px = (int) (cx + (dx / norm) * PROBE);
            int py = (int) (cy + (dy / norm) * PROBE);
            float crowd = countNeighbours(others, px, py, cx, cy);

            float payoff = W_PHERO   * phero
                         + (withinSense ? W_GOAL : W_SCOUT) * (float) Math.max(0.0, dotGoal)
                         + (!s.hasFood && !withinSense ? W_OUTWARD * (float) Math.max(0.0, dotOutward) : 0f)
                         - W_CONGESTION * crowd
                         + W_INERTIA * (float) Math.max(0.0, dotInertia)
                         + (tremble ? rng.nextFloat() * 10f : rng.nextFloat() * W_JITTER);

            if (payoff > bestPayoff) { bestPayoff = payoff; best = i; }
        }

        if (best < 0) best = rng.nextInt(DIRS.length); // boxed in — move anyway

        s.headingX = DIRS[best][0];
        s.headingY = DIRS[best][1];
        return new Point(DIRS[best][0] * STEP, DIRS[best][1] * STEP);
    }

    /** Count other players inside CROWD_RADIUS of a probed cell (self excluded). */
    private float countNeighbours(List<Point> others, int px, int py, int selfX, int selfY) {
        float count = 0f;
        for (Point p : others) {
            if (p.x == selfX && p.y == selfY) continue; // skip self
            if (Math.hypot(p.x - px, p.y - py) <= CROWD_RADIUS) count += 1f;
        }
        return count;
    }

    private boolean blockedByObstacle(Rectangle ahead, List<Obstacle> obstacles) {
        for (Obstacle o : obstacles) if (o.getBounds().intersects(ahead)) return true;
        return false;
    }
}
