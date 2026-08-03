import java.awt.Point;
import java.awt.Rectangle;
import java.util.*;

/**
 * Reinforcement-learning foraging strategy — tabular Q-learning shared by the colony.
 *
 * Unlike the other three strategies, nothing here is hand-tuned toward the goal:
 * the ants <em>learn</em> which move is best in each situation from the reward
 * they experience. A single Q-table is shared by every ant (the colony learns as
 * one organism, so discoveries by one forager immediately benefit the rest).
 *
 *   State   – (coarse grid cell of the ant) × (carrying food?).
 *   Action  – one of the 8 compass moves.
 *   Reward  – a large bonus for reaching the current goal (food while searching,
 *             nest while carrying), a small per-step cost that discourages
 *             dithering, plus potential-based shaping proportional to the
 *             distance closed toward the goal (this keeps early learning from
 *             stalling without changing the optimal policy).
 *
 * Learning is off-policy Q-learning:
 *     Q(s,a) ← Q(s,a) + α · [ r + γ · maxₐ' Q(s',a') − Q(s,a) ].
 * Actions are chosen ε-greedily with ε decaying from exploratory to nearly
 * greedy as experience accumulates, so early runs wander and later runs commit
 * to the learned nest↔food route. Watching the paths sharpen over time is the
 * point of this strategy.
 *
 * Trails are still deposited so the shared environment view is consistent with
 * the other strategies; the RL policy itself does not read them.
 */
class RLAntStrategy implements MovementStrategy {

    // ── Tuning constants ──────────────────────────────────────────────────────
    private static final int   STEP               = 5;    // pixels per tick
    private static final int   STATE_CELL         = 30;   // px per Q-table state cell (coarser than render grid)
    private static final int   OBSTACLE_LOOKAHEAD = 14;   // pixels ahead checked for avoidance
    private static final float DEPOSIT_FOOD_TRAIL = 0.10f;
    private static final float DEPOSIT_NEST_TRAIL = 0.04f;

    private static final double ALPHA        = 0.30; // learning rate
    private static final double GAMMA        = 0.92; // discount factor
    private static final double GOAL_REWARD  = 100.0;// reaching the current goal
    private static final double STEP_COST    = -0.20;// per-move cost
    private static final double SHAPING      = 1.20; // reward per pixel of progress toward goal
    private static final double EPS_START    = 0.70; // initial exploration rate
    private static final double EPS_MIN      = 0.05; // floor exploration rate
    // ~7x faster than the original 3e-6: ε reaches its floor in ~2700 ticks
    // (~45 s) instead of ~6 min, so the colony stops random-walking in the nest
    // corner and commits to learned nest↔food paths while a viewer is watching.
    private static final double EPS_DECAY    = 2.0e-5; // per-update decay

    private static final int[][] DIRS = {
        { 1,  0}, {-1,  0}, { 0,  1}, { 0, -1},
        { 1,  1}, { 1, -1}, {-1,  1}, {-1, -1}
    };
    private static final double[] DIR_NORM = new double[DIRS.length];
    static {
        for (int i = 0; i < DIRS.length; i++)
            DIR_NORM[i] = Math.sqrt(DIRS[i][0] * DIRS[i][0] + DIRS[i][1] * DIRS[i][1]);
    }

    // ── Per-avatar state (remembers the previous transition for the Q-update) ──
    private static class AgentState {
        boolean hasFood   = false;
        boolean hasPrev   = false;
        long    prevKey;
        int     prevAction;
        double  prevDist;   // distance to goal from the cell the ant acted in
        int     headingX = 1, headingY = 0;
    }

    private final GamePanel panel;
    private final IdentityHashMap<Avatar, AgentState> states = new IdentityHashMap<>();
    // Colony-shared Q-table: state key → Q-values for the 8 actions.
    private final HashMap<Long, double[]> Q = new HashMap<>();
    private long updates = 0; // total learning updates, drives ε decay

    RLAntStrategy(GamePanel panel) { this.panel = panel; }

    @Override public String name()     { return "RL Ant"; }
    @Override public String toString() { return name(); }

    @Override
    public Point computeDelta(Avatar avatar, int panelWidth, int panelHeight,
                              List<Obstacle> obstacles, Random rng) {

        AgentState s = states.computeIfAbsent(avatar, a -> new AgentState());

        int cx = avatar.getX() + avatar.getSize() / 2;
        int cy = avatar.getY() + avatar.getSize() / 2;

        // Goal for the ant's CURRENT carrying state, and whether it just arrived.
        boolean arrived = s.hasFood ? panel.atNest(avatar) : panel.atFood(avatar);
        Point   goal    = s.hasFood ? panel.getNestPos()   : panel.getFoodPos();
        double  curDist = Math.hypot(goal.x - cx, goal.y - cy);

        // ── Learn from the previous action (reward realised this tick) ─────────
        if (s.hasPrev) {
            double reward = STEP_COST + SHAPING * (s.prevDist - curDist);
            if (arrived) reward += GOAL_REWARD;
            long   curKey    = encode(cx, cy, panelWidth, s.hasFood);
            double bootstrap = arrived ? 0.0 : GAMMA * maxQ(curKey); // terminal: no bootstrap
            double[] qPrev   = qRow(s.prevKey);
            qPrev[s.prevAction] += ALPHA * (reward + bootstrap - qPrev[s.prevAction]);
            updates++;
        }

        // ── Phase transition on arrival (state's food bit flips) ───────────────
        if (arrived) {
            s.hasFood = !s.hasFood;
            s.hasPrev = false; // start a fresh episode toward the new goal
            goal      = s.hasFood ? panel.getNestPos() : panel.getFoodPos();
            curDist   = Math.hypot(goal.x - cx, goal.y - cy);
        }

        // Deposit trails so the shared environment view matches other strategies.
        if (s.hasFood) panel.depositFoodTrail(cx, cy, DEPOSIT_FOOD_TRAIL);
        else           panel.depositNestTrail(cx, cy, DEPOSIT_NEST_TRAIL);

        // ── ε-greedy action selection over unobstructed moves ──────────────────
        long     key   = encode(cx, cy, panelWidth, s.hasFood);
        double[] q     = qRow(key);
        int      size  = avatar.getSize();

        boolean[] blocked = new boolean[DIRS.length];
        int allowedCount = 0;
        for (int i = 0; i < DIRS.length; i++) {
            Rectangle ahead = new Rectangle(
                avatar.getX() + DIRS[i][0] * OBSTACLE_LOOKAHEAD,
                avatar.getY() + DIRS[i][1] * OBSTACLE_LOOKAHEAD, size, size);
            blocked[i] = blockedByObstacle(ahead, obstacles);
            if (!blocked[i]) allowedCount++;
        }
        if (allowedCount == 0) Arrays.fill(blocked, false); // boxed in — free every move

        double eps = Math.max(EPS_MIN, EPS_START - EPS_DECAY * updates);
        int action;
        if (rng.nextDouble() < eps) {
            action = randomAllowed(blocked, rng);
        } else {
            action = greedyAllowed(q, blocked, rng);
        }

        // ── Remember this transition for next tick's update ────────────────────
        s.prevKey    = key;
        s.prevAction = action;
        s.prevDist   = curDist;
        s.hasPrev    = true;
        s.headingX   = DIRS[action][0];
        s.headingY   = DIRS[action][1];

        return new Point(DIRS[action][0] * STEP, DIRS[action][1] * STEP);
    }

    // ── Q-table helpers ────────────────────────────────────────────────────────

    private long encode(int px, int py, int panelWidth, boolean hasFood) {
        int cols = Math.max(1, panelWidth / STATE_CELL);
        int gx   = Math.max(0, px / STATE_CELL);
        int gy   = Math.max(0, py / STATE_CELL);
        long cell = (long) gy * cols + gx;
        return (cell << 1) | (hasFood ? 1L : 0L);
    }

    private double[] qRow(long key) {
        return Q.computeIfAbsent(key, k -> new double[DIRS.length]);
    }

    private double maxQ(long key) {
        double[] row = Q.get(key);
        if (row == null) return 0.0;
        double m = row[0];
        for (double v : row) if (v > m) m = v;
        return m;
    }

    private int randomAllowed(boolean[] blocked, Random rng) {
        int idx = rng.nextInt(DIRS.length);
        for (int k = 0; k < DIRS.length; k++) {
            int i = (idx + k) % DIRS.length;
            if (!blocked[i]) return i;
        }
        return idx;
    }

    /** Highest-Q unblocked action, ties broken randomly. */
    private int greedyAllowed(double[] q, boolean[] blocked, Random rng) {
        double best = Double.NEGATIVE_INFINITY;
        int count = 0, chosen = -1;
        for (int i = 0; i < DIRS.length; i++) {
            if (blocked[i]) continue;
            if (q[i] > best) { best = q[i]; chosen = i; count = 1; }
            else if (q[i] == best && rng.nextInt(++count) == 0) chosen = i; // reservoir tie-break
        }
        return chosen < 0 ? randomAllowed(blocked, rng) : chosen;
    }

    private boolean blockedByObstacle(Rectangle ahead, List<Obstacle> obstacles) {
        for (Obstacle o : obstacles) if (o.getBounds().intersects(ahead)) return true;
        return false;
    }
}
