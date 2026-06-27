import java.awt.Point;
import java.util.List;
import java.util.Random;

interface MovementStrategy {
    String name();

    Point computeDelta(Avatar avatar,
                       int panelWidth,
                       int panelHeight,
                       List<Obstacle> obstacles,
                       Random rng);
}
