import java.awt.*;
import java.util.List;

interface Obstacle {
    void update(int panelWidth, int panelHeight, List<Avatar> avatars);
    Rectangle getBounds();
    void draw(Graphics g);
}
