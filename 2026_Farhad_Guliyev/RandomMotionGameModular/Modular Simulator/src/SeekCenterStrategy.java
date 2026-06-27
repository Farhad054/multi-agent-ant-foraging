import java.awt.Point;
import java.util.List;
import java.util.Random;

class SeekCenterStrategy implements MovementStrategy {
    @Override public String name() { return "Seek Center"; }

    @Override
    public Point computeDelta(Avatar avatar, int panelWidth, int panelHeight,
                              List<Obstacle> obstacles, Random rng) {
        int cx = panelWidth / 2;
        int cy = panelHeight / 2;

        double vx = cx - (avatar.getX() + avatar.getSize() / 2.0);
        double vy = cy - (avatar.getY() + avatar.getSize() / 2.0);

        double len = Math.max(1e-6, Math.hypot(vx, vy));
        double maxStep = 4.0;
        int dx = (int)Math.round((vx / len) * maxStep);
        int dy = (int)Math.round((vy / len) * maxStep);

        dx += rng.nextInt(3) - 1;
        dy += rng.nextInt(3) - 1;

        return new Point(dx, dy);
    }

    @Override public String toString() { return name(); }
}
