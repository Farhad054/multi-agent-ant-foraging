import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.Timer;

class GamePanel extends JPanel implements ActionListener {
    private final int NUM_AVATARS = 12;
    private final int AVATAR_SIZE = 20;

    // Pheromone grid constants (public so strategies can reference them)
    public static final int  CELL_SIZE        = 10;
    public static final int  NEST_RADIUS      = 25;
    public static final int  FOOD_RADIUS      = 20;
    private static final float EVAPORATION_RATE = 0.005f; // ~2.3 s half-life at 60 fps

    private final Avatar[] avatars;
    private final java.util.List<Obstacle> obstacles;

    private MovementStrategy movementStrategy = new RandomWalkStrategy();
    private final Timer timer;
    private final Random rng = new Random();
    private BufferedImage bgTexture;

    private static final int DEFAULT_W = 900;
    private static final int DEFAULT_H = 600;

    // --- Ant foraging environment ---
    private final Point nestPos;          // pixel center of the nest
    private final Point foodPos;          // pixel center of the food source
    private final float[][] pheromoneGrid; // [gridX][gridY], values in [0, 1]
    private final int gridW;
    private final int gridH;

    public GamePanel() {
        setBackground(new Color(38, 28, 16));

        avatars   = new Avatar[NUM_AVATARS];
        obstacles = new ArrayList<>();

        // Nest in the top-left area, food in the bottom-right area
        nestPos = new Point(80, 80);
        foodPos = new Point(DEFAULT_W - 100, DEFAULT_H - 100);

        gridW         = DEFAULT_W / CELL_SIZE;
        gridH         = DEFAULT_H / CELL_SIZE;
        pheromoneGrid = new float[gridW][gridH];

        // Spawn avatars clustered near the nest
        for (int i = 0; i < NUM_AVATARS; i++) {
            int x = clampInt(nestPos.x + rng.nextInt(60) - 30, 0, DEFAULT_W - AVATAR_SIZE);
            int y = clampInt(nestPos.y + rng.nextInt(60) - 30, 0, DEFAULT_H - AVATAR_SIZE);
            avatars[i] = new Avatar(x, y, AVATAR_SIZE);
        }

        // Default obstacles — cool gray stones + warm sandstone boulder
        obstacles.add(new StaticRectObstacle(200, 150, 100, 200, new Color(148, 140, 128)));
        obstacles.add(new StaticRectObstacle(550, 300, 160, 100, new Color(132, 122, 108)));
        obstacles.add(new MovingRectObstacle(350, 60, 90, 80, 3, 2, new Color(175, 142, 90)));

        // Ensure starting state has no overlaps
        scatterAvatarsToNonOverlapping();

        timer = new Timer(16, this); // ~60 FPS
    }

    // -------------------------------------------------------------------------
    // Pheromone grid API (used by ant strategies)
    // -------------------------------------------------------------------------

    /** Deposit pheromone at a pixel position; clamped to [0, 1]. */
    public void depositPheromone(int px, int py, float amount) {
        int gx = px / CELL_SIZE;
        int gy = py / CELL_SIZE;
        if (gx >= 0 && gx < gridW && gy >= 0 && gy < gridH) {
            pheromoneGrid[gx][gy] = Math.min(1.0f, pheromoneGrid[gx][gy] + amount);
        }
    }

    /** Read pheromone level at a pixel position (0 if out of bounds). */
    public float getPheromoneAt(int px, int py) {
        int gx = px / CELL_SIZE;
        int gy = py / CELL_SIZE;
        if (gx >= 0 && gx < gridW && gy >= 0 && gy < gridH) return pheromoneGrid[gx][gy];
        return 0f;
    }

    public float[][] getPheromoneGrid() { return pheromoneGrid; }
    public int getGridW()               { return gridW; }
    public int getGridH()               { return gridH; }

    // -------------------------------------------------------------------------
    // Nest / food API (used by ant strategies)
    // -------------------------------------------------------------------------

    public Point getNestPos() { return nestPos; }
    public Point getFoodPos() { return foodPos; }

    /** True if the avatar's centre is within (NEST_RADIUS + half avatar size) of the nest centre. */
    public boolean atNest(Avatar a) {
        return distToPoint(a, nestPos) <= NEST_RADIUS + a.getSize() / 2.0;
    }

    /** True if the avatar's centre is within (FOOD_RADIUS + half avatar size) of the food centre. */
    public boolean atFood(Avatar a) {
        return distToPoint(a, foodPos) <= FOOD_RADIUS + a.getSize() / 2.0;
    }

    private double distToPoint(Avatar a, Point p) {
        double ax = a.getX() + a.getSize() / 2.0;
        double ay = a.getY() + a.getSize() / 2.0;
        return Math.hypot(ax - p.x, ay - p.y);
    }

    private static int clampInt(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private void scatterAvatarsToNonOverlapping() {
        for (int i = 0; i < avatars.length; i++) {
            Avatar a = avatars[i];
            int attempts = 0;
            while (overlapsSomething(a.getBounds(), i)) {
                int x = rng.nextInt(DEFAULT_W - AVATAR_SIZE);
                int y = rng.nextInt(DEFAULT_H - AVATAR_SIZE);
                a.setPosition(x, y);
                attempts++;
                if (attempts > 2000) break;
            }
        }
    }

    private boolean overlapsSomething(Rectangle r, int upToAvatarIndex) {
        for (Obstacle o : obstacles) {
            if (o.getBounds().intersects(r)) return true;
        }
        for (int j = 0; j < upToAvatarIndex; j++) {
            if (avatars[j].getBounds().intersects(r)) return true;
        }
        return false;
    }

    public void setMovementStrategy(MovementStrategy strategy) {
        this.movementStrategy = strategy;
    }

    public void start() { if (!timer.isRunning()) timer.start(); }
    public void stop()  { if (timer.isRunning()) timer.stop(); }

    public void clearObstacles() {
        obstacles.clear();
        repaint();
    }

    public void addRandomStaticObstacle() {
        int w = Math.max(40, rng.nextInt(140));
        int h = Math.max(40, rng.nextInt(140));
        int x = rng.nextInt(Math.max(1, getPlayWidth()  - w));
        int y = rng.nextInt(Math.max(1, getPlayHeight() - h));
        // Stone grays with slight warm/cool variation
        int base = 120 + rng.nextInt(60);
        Color c = new Color(base + rng.nextInt(15), base, base - rng.nextInt(15));
        obstacles.add(new StaticRectObstacle(x, y, w, h, c));
    }

    public void addRandomMovingObstacle() {
        int w = 60 + rng.nextInt(80);
        int h = 40 + rng.nextInt(70);
        int x = rng.nextInt(Math.max(1, getPlayWidth()  - w));
        int y = rng.nextInt(Math.max(1, getPlayHeight() - h));
        int vx = rng.nextBoolean() ? (2 + rng.nextInt(3)) : -(2 + rng.nextInt(3));
        int vy = rng.nextBoolean() ? (2 + rng.nextInt(3)) : -(2 + rng.nextInt(3));
        // Warmer sandstone tone for moving boulders
        Color c = new Color(160 + rng.nextInt(30), 130 + rng.nextInt(25), 80 + rng.nextInt(20));
        obstacles.add(new MovingRectObstacle(x, y, w, h, vx, vy, c));
    }

    private int getPlayWidth()  { return Math.max(1, getWidth()  == 0 ? DEFAULT_W : getWidth()); }
    private int getPlayHeight() { return Math.max(1, getHeight() == 0 ? DEFAULT_H : getHeight()); }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private BufferedImage buildBgTexture(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Random r = new Random(0xDEADBEEFL);

        // Base soil colour
        g.setColor(new Color(38, 28, 16));
        g.fillRect(0, 0, w, h);

        // Coarse soil blobs
        int blobs = w * h / 110;
        for (int i = 0; i < blobs; i++) {
            int bx = r.nextInt(w), by = r.nextInt(h);
            int bw = 4 + r.nextInt(14), bh = 3 + r.nextInt(9);
            int v = r.nextInt(38) - 18;
            g.setColor(new Color(
                clampInt(40 + v, 18, 72),
                clampInt(30 + v / 2, 12, 52),
                clampInt(17 + v / 3, 6, 34)
            ));
            g.fillOval(bx, by, bw, bh);
        }

        // Fine grain speckling
        int grains = w * h / 35;
        for (int i = 0; i < grains; i++) {
            int bx = r.nextInt(w), by = r.nextInt(h);
            int v = r.nextInt(22) - 11;
            g.setColor(new Color(
                clampInt(42 + v, 20, 68),
                clampInt(31 + v / 2, 14, 50),
                clampInt(18 + v / 3, 7, 32)
            ));
            g.fillRect(bx, by, 1, 1);
        }

        g.dispose();
        return img;
    }

    private void evaporatePheromones() {
        float factor = 1f - EVAPORATION_RATE;
        for (int gx = 0; gx < gridW; gx++)
            for (int gy = 0; gy < gridH; gy++)
                pheromoneGrid[gx][gy] *= factor;
    }

    private void drawPheromones(Graphics2D g2) {
        for (int gx = 0; gx < gridW; gx++) {
            for (int gy = 0; gy < gridH; gy++) {
                float v = pheromoneGrid[gx][gy];
                if (v > 0.01f) {
                    int alpha = Math.min(220, (int)(v * 220));
                    g2.setColor(new Color(0, 200, 120, alpha));
                    g2.fillRect(gx * CELL_SIZE, gy * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                }
            }
        }
    }

    private void drawNest(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        int r = NEST_RADIUS;
        int nx = nestPos.x, ny = nestPos.y;

        // Disturbed soil ring around mound
        g2.setColor(new Color(75, 52, 28, 110));
        g2.fillOval(nx - r - 14, ny - r - 10, (r + 14) * 2, (r + 10) * 2);

        // Mound body (slightly flattened dome)
        g2.setColor(new Color(105, 75, 42));
        g2.fillOval(nx - r, ny - r + 4, r * 2, (int)(r * 1.6));

        // Mound highlight (lighter crest)
        g2.setColor(new Color(148, 108, 65, 140));
        g2.fillOval(nx - r / 2, ny - r / 2 + 4, r, r / 2);

        // Tunnel entrance (dark oval hole)
        g2.setColor(new Color(15, 8, 2));
        g2.fillOval(nx - 7, ny - 4, 14, 10);
        g2.setColor(new Color(50, 32, 14, 160));
        g2.setStroke(new BasicStroke(1f));
        g2.drawOval(nx - 7, ny - 4, 14, 10);

        // Outer edge
        g2.setColor(new Color(65, 44, 22));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(nx - r, ny - r + 4, r * 2, (int)(r * 1.6));

        // Label
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 10f));
        g2.setColor(new Color(200, 175, 130));
        g2.drawString("NEST", nx - 12, ny + r + 16);
    }

    private void drawFood(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        int r = FOOD_RADIUS;
        int fx = foodPos.x, fy = foodPos.y;

        // Soft glow
        g2.setColor(new Color(60, 200, 60, 55));
        g2.fillOval(fx - r - 12, fy - r - 12, (r + 12) * 2, (r + 12) * 2);

        // Dark base (shadow under pile)
        g2.setColor(new Color(20, 70, 20, 130));
        g2.fillOval(fx - r + 3, fy - r + 5, r * 2, r * 2);

        // Main food pile
        g2.setColor(new Color(38, 145, 38));
        g2.fillOval(fx - r, fy - r, r * 2, r * 2);

        // Lighter centre cluster
        g2.setColor(new Color(72, 200, 72, 180));
        g2.fillOval(fx - r / 2, fy - r / 2, r, r / 2 + 3);

        // Bright specular spot
        g2.setColor(new Color(140, 240, 110, 160));
        g2.fillOval(fx - 5, fy - 7, 9, 7);

        // Outline
        g2.setColor(new Color(18, 90, 18));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(fx - r, fy - r, r * 2, r * 2);

        // Label
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 10f));
        g2.setColor(new Color(140, 220, 140));
        g2.drawString("FOOD", fx - 13, fy + r + 16);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Lazy-build / rebuild texture if panel was resized
        int pw = getPlayWidth(), ph = getPlayHeight();
        if (bgTexture == null || bgTexture.getWidth() != pw || bgTexture.getHeight() != ph) {
            bgTexture = buildBgTexture(pw, ph);
        }
        g2.drawImage(bgTexture, 0, 0, null);

        // Layer order: pheromones → obstacles → nest/food → avatars → HUD
        drawPheromones(g2);
        for (Obstacle obs : obstacles) obs.draw(g);
        drawNest(g);
        drawFood(g);
        for (Avatar avatar : avatars) avatar.draw(g);
        drawHud(g2);
    }

    private void drawHud(Graphics2D g2) {
        String stratName  = movementStrategy != null ? movementStrategy.name() : "None";
        boolean running   = timer.isRunning();
        String statusText = running ? "● RUNNING" : "◼ PAUSED";
        Color  statusCol  = running ? new Color(72, 199, 142) : new Color(200, 90, 80);
        String obsText    = "obstacles: " + obstacles.size();

        Font bold  = new Font("SansSerif", Font.BOLD,  11);
        Font plain = new Font("SansSerif", Font.PLAIN, 11);
        g2.setFont(bold);
        FontMetrics fm = g2.getFontMetrics();

        int pad  = 10;
        int lineH = fm.getHeight() + 3;
        int textW = Math.max(fm.stringWidth(stratName),
                    Math.max(fm.stringWidth(statusText), fm.stringWidth(obsText)));
        int boxW  = textW + pad * 2;
        int boxH  = lineH * 3 + pad * 2;
        int bx    = getPlayWidth() - boxW - 12;
        int by    = 12;

        // Background pill
        g2.setColor(new Color(14, 14, 20, 200));
        g2.fill(new RoundRectangle2D.Float(bx, by, boxW, boxH, 12, 12));
        g2.setColor(new Color(60, 60, 80, 180));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(bx + 0.5f, by + 0.5f, boxW - 1, boxH - 1, 12, 12));

        int tx = bx + pad;
        int ty = by + pad + fm.getAscent();

        // Strategy name (bold, white)
        g2.setFont(bold);
        g2.setColor(new Color(215, 215, 225));
        g2.drawString(stratName, tx, ty);

        // Status (coloured)
        g2.setFont(plain);
        g2.setColor(statusCol);
        g2.drawString(statusText, tx, ty + lineH);

        // Obstacle count (dim)
        g2.setColor(new Color(100, 110, 132));
        g2.drawString(obsText, tx, ty + lineH * 2);
    }

    // -------------------------------------------------------------------------
    // Game loop
    // -------------------------------------------------------------------------

    @Override
    public void actionPerformed(ActionEvent e) {
        final int W = getPlayWidth();
        final int H = getPlayHeight();

        for (Obstacle obs : obstacles)
            obs.update(W, H, java.util.Arrays.asList(avatars));

        evaporatePheromones();

        for (int i = 0; i < avatars.length; i++)
            avatars[i].step(movementStrategy, W, H, obstacles, avatars, i, new Random(rng.nextLong()));

        repaint();
    }
}
