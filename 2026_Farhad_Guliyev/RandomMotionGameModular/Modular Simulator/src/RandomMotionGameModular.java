import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

public class RandomMotionGameModular extends JFrame {

    // ── Palette ──────────────────────────────────────────────────────
    static final Color BG_DEEP  = new Color(16,  16,  22);
    static final Color BG_BTN   = new Color(38,  38,  50);
    static final Color BG_HOV   = new Color(54,  54,  70);
    static final Color FG_TEXT  = new Color(215, 215, 225);
    static final Color FG_DIM   = new Color(100, 110, 132);
    static final Color SEP      = new Color(52,  52,  68);
    static final Color C_GREEN  = new Color(72,  199, 142);
    static final Color C_RED    = new Color(240,  85,  65);
    static final Color C_BLUE   = new Color(82,  156, 222);
    static final Color C_ORANGE = new Color(250, 160,  50);

    public RandomMotionGameModular() {
        setTitle("Ant Foraging Simulator — Multi-Agent Strategies");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 710);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DEEP);

        GamePanel gamePanel = new GamePanel();

        MovementStrategy[] strategies = {
            new RandomWalkStrategy(),
            new SeekCenterStrategy(),
            new DriftRightStrategy(),
            new StochasticAntStrategy(gamePanel),
            new StigmergyAntStrategy(gamePanel),
            new GameTheoryAntStrategy(gamePanel),
            new RLAntStrategy(gamePanel),
        };

        // ── Control bar ──────────────────────────────────────────────
        JPanel bar = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(BG_DEEP);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(SEP);
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        bar.setOpaque(false);
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBorder(BorderFactory.createEmptyBorder(8, 14, 10, 14));

        DarkBtn startBtn     = new DarkBtn("▶  Start",   C_GREEN);
        DarkBtn stopBtn      = new DarkBtn("■  Stop",    C_RED);
        DarkBtn addStaticBtn = new DarkBtn("＋ Static",   C_BLUE);
        DarkBtn addMovingBtn = new DarkBtn("＋ Moving",   C_BLUE);
        DarkBtn clearObsBtn  = new DarkBtn("✕  Clear",   C_ORANGE);

        ButtonGroup stratGroup  = new ButtonGroup();
        StratToggle[] toggles   = new StratToggle[strategies.length];
        for (int i = 0; i < strategies.length; i++) {
            toggles[i] = new StratToggle(strategies[i].name(), i == 0);
            stratGroup.add(toggles[i]);
        }

        bar.add(startBtn);
        bar.add(gap(6));
        bar.add(stopBtn);
        bar.add(vsep());
        bar.add(dimLabel("STRATEGY"));
        bar.add(gap(8));
        for (int i = 0; i < toggles.length; i++) {
            bar.add(toggles[i]);
            if (i < toggles.length - 1) bar.add(gap(4));
        }
        bar.add(vsep());
        bar.add(addStaticBtn);
        bar.add(gap(6));
        bar.add(addMovingBtn);
        bar.add(gap(6));
        bar.add(clearObsBtn);
        bar.add(Box.createHorizontalGlue());

        // ── Wiring ───────────────────────────────────────────────────
        startBtn.addActionListener(e -> gamePanel.start());
        stopBtn.addActionListener(e -> gamePanel.stop());
        for (int i = 0; i < strategies.length; i++) {
            final int idx = i;
            toggles[i].addActionListener(e -> gamePanel.setMovementStrategy(strategies[idx]));
        }
        addStaticBtn.addActionListener(e -> gamePanel.addRandomStaticObstacle());
        addMovingBtn.addActionListener(e -> gamePanel.addRandomMovingObstacle());
        clearObsBtn.addActionListener(e -> gamePanel.clearObstacles());

        setLayout(new BorderLayout());
        add(gamePanel, BorderLayout.CENTER);
        add(bar,       BorderLayout.SOUTH);

        gamePanel.setMovementStrategy(strategies[0]);
        setVisible(true);
    }

    // ── Layout helpers ───────────────────────────────────────────────

    static Component gap(int px) {
        return Box.createRigidArea(new Dimension(px, 0));
    }

    static JLabel dimLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(FG_DIM);
        l.setFont(new Font("SansSerif", Font.BOLD, 10));
        return l;
    }

    static Component vsep() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(SEP);
                g.fillRect(0, 5, 1, getHeight() - 10);
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(18, 34));
        p.setMaximumSize(new Dimension(18, 34));
        return p;
    }

    // ── Dark action button ────────────────────────────────────────────

    static class DarkBtn extends JButton {
        private final Color accent;
        private boolean hov;

        DarkBtn(String text, Color accent) {
            super(text);
            this.accent = accent;
            setFont(new Font("SansSerif", Font.PLAIN, 12));
            setForeground(FG_TEXT);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(0, 14, 2, 14));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hov = true;  repaint(); }
                public void mouseExited(MouseEvent e)  { hov = false; repaint(); }
            });
        }

        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, 32);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(hov ? BG_HOV : BG_BTN);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 8, 8));
            g2.setColor(accent);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(6, h - 2, w - 6, h - 2);
            g2.setColor(SEP);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 8, 8));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ── Strategy toggle button ────────────────────────────────────────

    static class StratToggle extends JToggleButton {
        private boolean hov;

        StratToggle(String text, boolean selected) {
            super(text, selected);
            setFont(new Font("SansSerif", Font.PLAIN, 12));
            setForeground(selected ? Color.WHITE : new Color(155, 160, 180));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(0, 13, 2, 13));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hov = true;  repaint(); }
                public void mouseExited(MouseEvent e)  { hov = false; repaint(); }
            });
            addItemListener(e ->
                setForeground(isSelected() ? Color.WHITE : new Color(155, 160, 180)));
        }

        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, 32);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            boolean sel = isSelected();
            if (sel) {
                g2.setColor(new Color(35, 68, 55));
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 8, 8));
                g2.setColor(C_GREEN);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 8, 8));
            } else {
                g2.setColor(hov ? BG_HOV : BG_BTN);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 8, 8));
                g2.setColor(SEP);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 8, 8));
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(RandomMotionGameModular::new);
    }
}
