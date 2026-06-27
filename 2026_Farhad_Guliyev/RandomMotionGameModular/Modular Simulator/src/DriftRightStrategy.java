import java.awt.Point;
import java.util.List;
import java.util.Random;

class DriftRightStrategy implements MovementStrategy {
    @Override public String name() { return "Drift Right"; }

    @Override
    public Point computeDelta(Avatar avatar, int panelWidth, int panelHeight,
                              List<Obstacle> obstacles, Random rng) {
        int dx = 2 + rng.nextInt(3);
        int dy = rng.nextInt(7) - 3;
        return new Point(dx, dy);
    }

    @Override public String toString() { return name(); }
}
