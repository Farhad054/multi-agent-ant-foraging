import java.awt.Point;
import java.util.List;
import java.util.Random;

class RandomWalkStrategy implements MovementStrategy {
    @Override public String name() { return "Random Walk"; }

    @Override
    public Point computeDelta(Avatar avatar, int panelWidth, int panelHeight,
                              List<Obstacle> obstacles, Random rng) {
        // Range ±3 instead of ±5 — keeps random mode from looking frantic
        int dx = rng.nextInt(7) - 3;
        int dy = rng.nextInt(7) - 3;
        return new Point(dx, dy);
    }

    @Override public String toString() { return name(); }
}
