import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.Random;

class Avatar {
    private int x, y, size;
    private final Color color;
    private double heading = 0.0; // radians, 0 = facing right

    public Avatar(int x, int y, int size) {
        this.x = x;
        this.y = y;
        this.size = size;
        // Bright orange-red so ants contrast against dark soil background
        int r = 210 + (int)(Math.random() * 40);
        int g = 60  + (int)(Math.random() * 40);
        int b = 10  + (int)(Math.random() * 20);
        this.color = new Color(Math.min(255, r), Math.min(255, g), Math.min(255, b));
    }

    public int getX()    { return x; }
    public int getY()    { return y; }
    public int getSize() { return size; }

    public void setPosition(int nx, int ny) { this.x = nx; this.y = ny; }

    public Rectangle getBounds() { return new Rectangle(x, y, size, size); }

    public void step(MovementStrategy strategy,
                     int panelWidth,
                     int panelHeight,
                     List<Obstacle> obstacles,
                     Avatar[] allAvatars,
                     int selfIndex,
                     Random rng) {
        if (strategy == null) return;

        int prevX = x, prevY = y;

        Point d = strategy.computeDelta(this, panelWidth, panelHeight, obstacles, rng);
        int dx = d.x, dy = d.y;

        int remainingX = dx;
        int remainingY = dy;

        while (remainingX != 0 || remainingY != 0) {
            int stepX = Integer.signum(remainingX);
            int stepY = Integer.signum(remainingY);

            if (remainingX != 0) {
                int nx = clamp(x + stepX, 0, panelWidth - size);
                Rectangle r = new Rectangle(nx, y, size, size);
                if (!collides(r, obstacles) && !collidesAvatars(r, allAvatars, selfIndex)) {
                    x = nx;
                    remainingX -= stepX;
                } else {
                    remainingX = 0;
                }
            }

            if (remainingY != 0) {
                int ny = clamp(y + stepY, 0, panelHeight - size);
                Rectangle r = new Rectangle(x, ny, size, size);
                if (!collides(r, obstacles) && !collidesAvatars(r, allAvatars, selfIndex)) {
                    y = ny;
                    remainingY -= stepY;
                } else {
                    remainingY = 0;
                }
            }

            if (Math.abs(remainingX) + Math.abs(remainingY) > 1000) break;
        }

        int movedDx = x - prevX, movedDy = y - prevY;
        if (movedDx != 0 || movedDy != 0) {
            double target = Math.atan2(movedDy, movedDx);
            double diff = target - heading;
            // Normalize diff to [-π, π] so we always take the short arc
            while (diff >  Math.PI) diff -= 2 * Math.PI;
            while (diff < -Math.PI) diff += 2 * Math.PI;
            heading += diff * 0.18; // gradual rotation — eliminates axis-spin
        }
    }

    private boolean collides(Rectangle r, List<Obstacle> obstacles) {
        for (Obstacle o : obstacles) {
            if (o.getBounds().intersects(r)) return true;
        }
        return false;
    }

    private boolean collidesAvatars(Rectangle r, Avatar[] all, int selfIndex) {
        for (int i = 0; i < all.length; i++) {
            if (i == selfIndex) continue;
            if (all[i].getBounds().intersects(r)) return true;
        }
        return false;
    }

    private int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cx = x + size / 2;
        int cy = y + size / 2;

        AffineTransform saved = g2.getTransform();
        g2.translate(cx, cy);
        g2.rotate(heading);

        Color dark    = color.darker();
        Color darker  = dark.darker();
        Color hilite  = new Color(
            Math.min(255, color.getRed()   + 45),
            Math.min(255, color.getGreen() + 28),
            Math.min(255, color.getBlue()  + 12)
        );

        // ── Legs (drawn first so body segments render on top) ──────────────────
        g2.setStroke(new BasicStroke(0.9f));
        g2.setColor(darker);
        // Front pair (attached near thorax front, x≈4)
        g2.drawLine(4, -2, 8, -6);  g2.drawLine(8, -6,  5, -9);
        g2.drawLine(4,  2, 8,  6);  g2.drawLine(8,  6,  5,  9);
        // Middle pair (attached at thorax centre, x≈1)
        g2.drawLine(1, -2, 0, -7);  g2.drawLine(0, -7, -2, -9);
        g2.drawLine(1,  2, 0,  7);  g2.drawLine(0,  7, -2,  9);
        // Rear pair (attached near thorax rear, x≈-3)
        g2.drawLine(-3, -2, -6, -6); g2.drawLine(-6, -6, -5, -8);
        g2.drawLine(-3,  2, -6,  6); g2.drawLine(-6,  6, -5,  8);

        // ── Abdomen (rear, largest oval) ───────────────────────────────────────
        g2.setColor(color);
        g2.fillOval(-10, -4, 10, 8);
        g2.setColor(hilite);
        g2.fillOval(-9, -3, 4, 3);   // specular highlight
        g2.setColor(darker);
        g2.setStroke(new BasicStroke(0.7f));
        g2.drawOval(-10, -4, 10, 8);

        // ── Thorax (middle, small oval) ────────────────────────────────────────
        g2.setColor(color);
        g2.fillOval(-2, -3, 7, 6);
        g2.setColor(darker);
        g2.drawOval(-2, -3, 7, 6);

        // ── Head (front, round) ────────────────────────────────────────────────
        g2.setColor(color);
        g2.fillOval(4, -3, 6, 6);
        g2.setColor(darker);
        g2.drawOval(4, -3, 6, 6);

        // ── Eyes ───────────────────────────────────────────────────────────────
        g2.setColor(new Color(255, 140, 0, 220));
        g2.fillOval(7, -2, 2, 2);
        g2.fillOval(7,  1, 2, 2);

        // ── Antennae ───────────────────────────────────────────────────────────
        g2.setStroke(new BasicStroke(0.8f));
        g2.setColor(dark);
        g2.drawLine(8, -2, 13, -7); g2.drawLine(13, -7, 11, -9);
        g2.drawLine(8,  1, 13,  5); g2.drawLine(13,  5, 11,  7);

        g2.setTransform(saved);
    }
}
