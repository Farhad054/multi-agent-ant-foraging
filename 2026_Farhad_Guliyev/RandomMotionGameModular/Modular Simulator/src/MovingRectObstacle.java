import java.awt.*;
import java.util.List;
import java.util.Random;

class MovingRectObstacle implements Obstacle {
    private int x, y, w, h;
    private int vx, vy;
    private final Color color;

    private final int[] polyX, polyY;
    private final int numPts;

    public MovingRectObstacle(int x, int y, int w, int h, int vx, int vy, Color color) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.vx = vx; this.vy = vy;
        this.color = color != null ? color : new Color(180, 140, 100);

        // Irregular boulder polygon — slightly fewer points than static rocks
        Random r = new Random((long)x * 41 + y * 13 + w * 5 + h * 9);
        numPts = 7 + r.nextInt(3);
        polyX = new int[numPts];
        polyY = new int[numPts];
        double cx = w / 2.0, cy = h / 2.0;
        for (int i = 0; i < numPts; i++) {
            double angle = 2 * Math.PI * i / numPts + r.nextDouble() * 0.4;
            double rx = (w / 2.0) * (0.65 + r.nextDouble() * 0.33);
            double ry = (h / 2.0) * (0.65 + r.nextDouble() * 0.33);
            // stored as offsets from (0,0); translated in draw()
            polyX[i] = (int)(cx + rx * Math.cos(angle));
            polyY[i] = (int)(cy + ry * Math.sin(angle));
        }
    }

    @Override
    public void update(int panelWidth, int panelHeight, List<Avatar> avatars) {
        int remainingX = vx, remainingY = vy;
        int guard = 0;
        while ((remainingX != 0 || remainingY != 0) && guard++ < 1000) {
            if (remainingX != 0) {
                int stepX = Integer.signum(remainingX);
                int nx = x + stepX;
                if (nx < 0 || nx + w > panelWidth) {
                    vx = -vx; remainingX = 0;
                } else {
                    Rectangle next = new Rectangle(nx, y, w, h);
                    if (!intersectsAnyAvatar(next, avatars)) {
                        x = nx; remainingX -= stepX;
                    } else {
                        vx = -vx; remainingX = 0;
                    }
                }
            }
            if (remainingY != 0) {
                int stepY = Integer.signum(remainingY);
                int ny = y + stepY;
                if (ny < 0 || ny + h > panelHeight) {
                    vy = -vy; remainingY = 0;
                } else {
                    Rectangle next = new Rectangle(x, ny, w, h);
                    if (!intersectsAnyAvatar(next, avatars)) {
                        y = ny; remainingY -= stepY;
                    } else {
                        vy = -vy; remainingY = 0;
                    }
                }
            }
        }
    }

    private boolean intersectsAnyAvatar(Rectangle r, List<Avatar> avatars) {
        for (Avatar a : avatars) if (r.intersects(a.getBounds())) return true;
        return false;
    }

    @Override
    public Rectangle getBounds() { return new Rectangle(x, y, w, h); }

    @Override
    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Translate polygon offsets to current position
        int[] px = new int[numPts], py = new int[numPts];
        for (int i = 0; i < numPts; i++) { px[i] = polyX[i] + x; py[i] = polyY[i] + y; }

        // Motion-blur shadow (offset in velocity direction)
        int sx = Integer.signum(vx) * 5, sy = Integer.signum(vy) * 5;
        int[] shX = new int[numPts], shY = new int[numPts];
        for (int i = 0; i < numPts; i++) { shX[i] = px[i] + sx; shY[i] = py[i] + sy; }
        g2.setColor(new Color(0, 0, 0, 45));
        g2.fillPolygon(shX, shY, numPts);

        // Boulder fill
        g2.setColor(color);
        g2.fillPolygon(px, py, numPts);

        // Warm highlight (top-left)
        g2.setColor(new Color(255, 240, 200, 50));
        g2.fillOval(x + w / 6, y + h / 6, w / 3, h / 3);

        // Dark border
        g2.setColor(color.darker().darker());
        g2.setStroke(new BasicStroke(1.8f));
        g2.drawPolygon(px, py, numPts);

        // Small movement indicator chevron
        g2.setColor(new Color(255, 200, 100, 140));
        g2.setStroke(new BasicStroke(1.2f));
        int midX = x + w / 2, midY = y + h / 2;
        int arrowLen = Math.min(w, h) / 4;
        g2.drawLine(midX - sx * arrowLen / 5, midY - sy * arrowLen / 5,
                    midX + sx * arrowLen / 5, midY + sy * arrowLen / 5);
    }
}
