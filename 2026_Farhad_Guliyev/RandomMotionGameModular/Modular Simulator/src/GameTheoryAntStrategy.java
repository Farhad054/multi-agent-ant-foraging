import java.awt.Point;
import java.awt.Rectangle;
import java.util.*;

/**
 * Game-theoretic foraging strategy — foraging framed as a spatial congestion game.
 *
 * Every ant is a self-interested player choosing a move that maximises its own
 * payoff. Its payoff has two opposing terms:
 *
 *   + progress   – getting closer to its current goal (food when searching,
 *                  nest when carrying) is rewarded.
 *   − congestion – sharing a cell with many other ants is costly (finite trail
 *                  width, jostling, wasted effort). A route everyone takes stops
 *                  paying off, so rational ants disperse onto alternative lanes.
 *
 * Each ant plays a <em>best response</em> to the current positions of the others
 * (argmax payoff over the 8 compass moves). Because the crowd an ant sees is
 * itself the product of everyone's best responses, the colony settles toward a
 * Nash-like traffic equilibrium: goal-directed, yet spread across several
 * parallel paths instead of piling into one congested line. A small amount of
 * "trembling-hand" exploration keeps the population off degenerate fixed points.
 *
 * Congestion is measured directly from the other agents' positions (via
 * {@link GamePanel#getAvatarCenters()}), so coordination here is through mutual
 * observation of players — not through pheromones, which is what distinguishes
 * this strategy from the stochastic and stigmergy variants. Trails are still
 * deposited so the shared environment view stays consistent across strategies.
 */
class GameTheoryAntStrategy implements MovementStrategy {

    // ── Tuning constants ──────────────────────────────────────────────────────
    private static final int    STEP               = 5;    // pixels per tick
    private static final int    OBSTACLE_LOOKAHEAD = 14;    // pixels ahead checked for avoidance
    private static final int    PROBE              = 24;    // how far ahead a candidate cell is evaluated
    private static final double CROWD_RADIUS       = 42.0;  // neighbours within this range count as congestion
    private static final float  DEPOSIT_FOOD_TRAIL = 0.10f;
    private static final float  DEPOSIT_NEST_TRAIL = 0.04f;
    private static final double TREMBLE            = 0.06;  // probability of a random (exploratory) move

    private static final float W_PROGRESS   = 4.0f;  // payoff: closing distance to goal
    private static final float W_CONGESTION = 2.2f;  // penalty per crowding neighbour (best response driver)
    private static final float W_INERTIA    = 0.8f;  // heading persistence
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

        if (s.hasFood) panel.depositFoodTrail(cx, cy, DEPOSIT_FOOD_TRAIL);
        else           panel.depositNestTrail(cx, cy, DEPOSIT_NEST_TRAIL);

        Point  goal = s.hasFood ? panel.getNestPos() : panel.getFoodPos();
        double curDist = Math.hypot(goal.x - cx, goal.y - cy);

        // Snapshot of the other players' positions (the "crowd" this ant responds to).
        List<Point> others = panel.getAvatarCenters();

        double hLen = Math.max(1.0, Math.hypot(s.headingX, s.headingY));
        double hux = s.headingX / hLen, huy = s.headingY / hLen;
        int size = avatar.getSize();

        // ── Best response: argmax payoff over the 8 moves ──────────────────────
        float bestPayoff = Float.NEGATIVE_INFINITY;
        int   best       = -1;

        // Trembling hand: occasionally ignore the payoff and explore.
        boolean tremble = rng.nextDouble() < TREMBLE;

        for (int i = 0; i < DIRS.length; i++) {
            int dx = DIRS[i][0], dy = DIRS[i][1];
            double norm = DIR_NORM[i];

            Rectangle ahead = new Rectangle(
                avatar.getX() + dx * OBSTACLE_LOOKAHEAD,
                avatar.getY() + dy * OBSTACLE_LOOKAHEAD, size, size);
            if (blockedByObstacle(ahead, obstacles)) continue;

            int px = (int) (cx + (dx / norm) * PROBE);
            int py = (int) (cy + (dy / norm) * PROBE);

            // + progress: normalised distance closed toward the goal (in [-1, 1]).
            double newDist  = Math.hypot(goal.x - px, goal.y - py);
            float  progress = (float) ((curDist - newDist) / PROBE);

            // − congestion: how many other players already occupy the target cell.
            float crowd = countNeighbours(others, px, py, cx, cy);

            double dotInertia = (dx / norm) * hux + (dy / norm) * huy;

            float payoff = W_PROGRESS   * progress
                         - W_CONGESTION * crowd
                         + W_INERTIA    * (float) Math.max(0.0, dotInertia)
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
