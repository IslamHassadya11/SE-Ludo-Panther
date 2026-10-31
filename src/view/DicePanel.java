package view;

import model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Neon dice. A roll tumbles through random faces for {@link GameConfig#DICE_ANIMATION_MILLIS}, then lands on the
 * model's result with a glow pop, well within the 1 s limit of NFR-12. The tumbling faces are cosmetic only.
 */
public final class DicePanel extends JPanel {

    private static final int DIE = 66;
    private static final int[][] PIPS = {
            {}, {4}, {0, 8}, {0, 4, 8}, {0, 2, 6, 8}, {0, 2, 4, 6, 8}, {0, 2, 3, 5, 6, 8}
    };

    private DiceRoll roll;
    private String multiplierName = "";
    private Color accent = UiTheme.neonPurple();
    private long rollStart;
    private boolean rolling;
    private Timer settle;

    /**
     * Creates an empty dice display.
     */
    public DicePanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(2 * DIE + 60, DIE + 20));
        setMaximumSize(getPreferredSize());
    }

    /**
     * Shows a roll without animation.
     *
     * @param roll           roll, or {@code null} for blank dice
     * @param multiplierName name of the multiplier die (empty on Easy)
     * @param accent         glow color
     */
    public void showStatic(DiceRoll roll, String multiplierName, Color accent) {
        stop();
        this.roll = roll;
        this.multiplierName = multiplierName;
        this.accent = accent;
        repaint();
    }

    /**
     * Plays the tumbling animation and lands on the given roll.
     *
     * @param roll           the rolled result
     * @param multiplierName name of the multiplier die (empty on Easy)
     * @param accent         glow color
     * @param onDone         called when the dice have settled
     */
    public void animateRoll(DiceRoll roll, String multiplierName, Color accent, Runnable onDone) {
        stop();
        this.roll = roll;
        this.multiplierName = multiplierName;
        this.accent = accent;
        rolling = true;
        rollStart = System.nanoTime();
        Animator.subscribe(this);
        settle = new Timer(GameConfig.DICE_ANIMATION_MILLIS, e -> {
            rolling = false;
            settle = null;
            rollStart = System.nanoTime();
            onDone.run();
        });
        settle.setRepeats(false);
        settle.start();
    }

    /**
     * @return {@code true} while the dice are tumbling
     */
    public boolean isAnimating() {
        return rolling;
    }

    /**
     * Cancels a running roll animation.
     */
    private void stop() {
        if (settle != null) {
            settle.stop();
            settle = null;
        }
        rolling = false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double sinceStart = (System.nanoTime() - rollStart) / 1e6;
        double pop = rolling ? 0 : Math.max(0, 1 - sinceStart / 350.0);
        if (!rolling && pop == 0) {
            Animator.unsubscribe(this);
        }
        int step = roll == null ? 0 : roll.step();
        double angle = 0;
        if (rolling) {
            int frame = (int) (sinceStart / 55);
            step = 1 + Math.floorMod(frame * 7 + 3, 6);
            angle = Math.sin(sinceStart / 40.0) * 0.28;
        }
        drawStepDie(g2, 10, 10, step, angle, pop);
        if (!multiplierName.isEmpty()) {
            String text = roll == null || !roll.hasMultiplier() ? "" : "x" + roll.multiplier();
            if (rolling) {
                text = "x" + (1 + Math.floorMod((int) (sinceStart / 55) * 5 + 1, 6));
            }
            drawNumberDie(g2, DIE + 40, 10, text, -angle, pop);
        }
        g2.dispose();
    }

    /**
     * Draws the step die with pips for the given value.
     */
    private void drawStepDie(Graphics2D g2, int x, int y, int value, double angle, double pop) {
        Graphics2D g = transform(g2, x, y, angle, pop);
        RoundRectangle2D face = new RoundRectangle2D.Double(0, 0, DIE, DIE, 18, 18);
        g.setColor(UiTheme.surfaceHi());
        g.fill(face);
        Glow.stroke(g, face, accent, 2f, 0.6 + 0.4 * pop);
        double pip = DIE / 6.5;
        for (int cell : PIPS[value]) {
            double cx = DIE / 6.0 + (cell % 3) * (DIE / 3.0);
            double cy = DIE / 6.0 + (cell / 3) * (DIE / 3.0);
            Glow.halo(g, cx, cy, pip * 1.3, accent, 0.6);
            g.setColor(UiTheme.text());
            g.fill(new Ellipse2D.Double(cx - pip / 2, cy - pip / 2, pip, pip));
        }
        g.dispose();
    }

    /**
     * Draws the Boost / UltraBoost die, which shows a number.
     */
    private void drawNumberDie(Graphics2D g2, int x, int y, String text, double angle, double pop) {
        Graphics2D g = transform(g2, x, y, angle, pop);
        RoundRectangle2D face = new RoundRectangle2D.Double(0, 0, DIE, DIE, 18, 18);
        g.setColor(UiTheme.surfaceHi());
        g.fill(face);
        Glow.stroke(g, face, UiTheme.pantherGold(), 2f, 0.6 + 0.4 * pop);
        g.setFont(UiTheme.font(26, true));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(UiTheme.pantherGold());
        g.drawString(text, (DIE - fm.stringWidth(text)) / 2f, (DIE + fm.getAscent() - fm.getDescent()) / 2f);
        g.dispose();
    }

    /**
     * Rotates and scales a die around its center for the tumbling and pop effects.
     */
    private Graphics2D transform(Graphics2D g2, int x, int y, double angle, double pop) {
        Graphics2D g = (Graphics2D) g2.create();
        double scale = 1 + 0.12 * pop;
        g.translate(x + DIE / 2.0, y + DIE / 2.0);
        g.rotate(angle);
        g.scale(scale, scale);
        g.translate(-DIE / 2.0, -DIE / 2.0);
        return g;
    }
}
