import java.awt.*;
import java.util.List;
import java.util.Random;

class StaticRectObstacle implements Obstacle {
    protected int x, y, w, h;
    protected final Color color;

    private final int[] polyX, polyY, numPts = new int[1];
    private final int[] crackX1, crackY1, crackX2, crackY2;

    public StaticRectObstacle(int x, int y, int w, int h, Color color) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.color = color != null ? color : new Color(140, 130, 115);

        Random r = new Random((long)x * 31 + y * 17 + w * 7 + h * 3);

        // Irregular stone polygon (8–11 points around the perimeter)
        numPts[0] = 8 + r.nextInt(4);
        polyX = new int[numPts[0]];
        polyY = new int[numPts[0]];
        double cx = x + w / 2.0, cy = y + h / 2.0;
        for (int i = 0; i < numPts[0]; i++) {
            double angle = 2 * Math.PI * i / numPts[0] + r.nextDouble() * 0.35;
            double rx = (w / 2.0) * (0.68 + r.nextDouble() * 0.30);
            double ry = (h / 2.0) * (0.68 + r.nextDouble() * 0.30);
            polyX[i] = (int)(cx + rx * Math.cos(angle));
            polyY[i] = (int)(cy + ry * Math.sin(angle));
        }

        // Pre-compute crack line coordinates
        crackX1 = new int[3]; crackY1 = new int[3];
        crackX2 = new int[3]; crackY2 = new int[3];
        int icx = x + w / 2, icy = y + h / 2;
        for (int i = 0; i < 3; i++) {
            crackX1[i] = icx + r.nextInt(w / 2) - w / 4;
            crackY1[i] = icy + r.nextInt(h / 2) - h / 4;
            crackX2[i] = crackX1[i] + r.nextInt(w / 3) - w / 6;
            crackY2[i] = crackY1[i] + r.nextInt(h / 3) - h / 6;
        }
    }

    @Override
    public void update(int panelWidth, int panelHeight, List<Avatar> avatars) {}

    @Override
    public Rectangle getBounds() { return new Rectangle(x, y, w, h); }

    @Override
    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int n = numPts[0];

        // Shadow / depth beneath
        g2.setColor(new Color(0, 0, 0, 55));
        int[] sx = new int[n], sy = new int[n];
        for (int i = 0; i < n; i++) { sx[i] = polyX[i] + 4; sy[i] = polyY[i] + 5; }
        g2.fillPolygon(sx, sy, n);

        // Stone base fill
        g2.setColor(color);
        g2.fillPolygon(polyX, polyY, n);

        // Highlight (upper-left ambient)
        g2.setColor(new Color(255, 255, 255, 38));
        g2.fillOval(x + w / 6, y + h / 6, w / 3, h / 3);

        // Surface cracks
        g2.setColor(color.darker().darker());
        g2.setStroke(new BasicStroke(0.9f));
        for (int i = 0; i < 3; i++) {
            g2.drawLine(crackX1[i], crackY1[i], crackX2[i], crackY2[i]);
        }

        // Dark border
        g2.setColor(color.darker().darker());
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawPolygon(polyX, polyY, n);
    }
}
