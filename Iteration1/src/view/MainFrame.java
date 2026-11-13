package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * The single application window; screens are swapped with a {@link CardLayout}. A change of screen is a quick fade:
 * the new screen is there (and usable) at once while the old one fades away over it.
 * <p>The Settings gear floats above every screen in the top corner (on the right, or on the left in Hebrew and
 * Arabic). The Settings screen hides it.
 */
public final class MainFrame extends JFrame {

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private Runnable onCloseRequested = this::dispose;
    private final FadeTransition transition = new FadeTransition();
    private String current;
    private final Widgets.GearButton settingsButton = new Widgets.GearButton(50);

    /**
     * Creates the window sized for 1280×720 screens (NFR-01).
     */
    public MainFrame() {
        super(Assets.Texts.get("app.window"));
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onCloseRequested.run();
            }
        });
        content.setBackground(Theme.background());
        setContentPane(content);
        setGlassPane(transition);
        getLayeredPane().add(settingsButton, JLayeredPane.PALETTE_LAYER);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                placeCornerButtons();
            }
        });
        // Never bigger than the free part of the user's screen, even on a small or highly scaled screen.
        Rectangle usable = Widgets.ScreenFit.usableArea(null);
        setMinimumSize(new Dimension(Math.min(1024, usable.width), Math.min(640, usable.height)));
        setSize(new Dimension(Math.min(1280, usable.width), Math.min(720, usable.height)));
        setLocationRelativeTo(null);
    }

    /**
     * @param name   screen key
     * @param screen screen component (it may be wrapped, e.g. in a scroll pane)
     */
    public void addScreen(String name, JComponent screen) {
        content.add(screen, name);
    }

    /**
     * Shows a screen; the old one slides away.
     *
     * @param name screen key to display
     */
    public void showScreen(String name) {
        showScreen(name, true);
    }

    /**
     * Shows a screen.
     *
     * @param name    screen key to display
     * @param animate {@code false} to switch at once, without the slide (e.g. right after a login)
     */
    public void showScreen(String name, boolean animate) {
        animate = animate && isShowing() && current != null && !current.equals(name)
                && content.getWidth() > 0 && content.getHeight() > 0;
        java.awt.image.BufferedImage before = animate ? snapshot() : null;
        cards.show(content, name);
        current = name;
        if (before != null) {
            transition.play(before);
        }
    }

    /**
     * A picture of the screen as it is now. It is taken at the window's own size (not the screen's extra HiDPI pixels):
     * it only fades out for a fraction of a second, so the cheaper picture makes the switch start at once.
     */
    private java.awt.image.BufferedImage snapshot() {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(Math.max(1, content.getWidth()),
                Math.max(1, content.getHeight()), java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        content.paint(g);
        g.dispose();
        return image;
    }

    /**
     * Paints the old screen over the new one while it fades out quickly (under a fifth of a second). The new screen
     * can be used straight away: the overlay never catches clicks or keys.
     */
    private static final class FadeTransition extends JComponent {

        private static final double MILLIS = 180;
        private java.awt.image.BufferedImage image;
        private long start;
        private final Timer timer = new Timer(15, e -> step());

        /**
         * Creates the hidden overlay. It has no mouse listener, so clicks go straight to the new screen under it.
         */
        FadeTransition() {
            setOpaque(false);
        }

        /**
         * Fades out a picture of the screen that is leaving.
         *
         * @param before a picture of the old screen
         */
        void play(java.awt.image.BufferedImage before) {
            image = before;
            start = System.nanoTime();
            setVisible(true);
            timer.start();
            repaint();
        }

        /**
         * Moves the fade one frame on and hides the overlay when it is done.
         */
        private void step() {
            if ((System.nanoTime() - start) / 1e6 >= MILLIS) {
                timer.stop();
                image = null;
                setVisible(false);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (image == null) {
                return;
            }
            double t = Math.min(1, (System.nanoTime() - start) / 1e6 / MILLIS);
            // Fast at first, so the new screen shows up almost at once.
            double e = 1 - (1 - t) * (1 - t);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (1 - e)));
            // A small slide to the side, drawn at its own size (no scaling), which keeps every frame cheap.
            g2.drawImage(image, (int) Math.round(-getWidth() * 0.04 * e), 0, null);
            g2.dispose();
        }
    }

    /**
     * Puts the gear in the top corner on the side where lines end.
     */
    private void placeCornerButtons() {
        Rectangle area = getContentPane().getBounds();
        boolean ltr = getComponentOrientation().isLeftToRight();
        Dimension g = settingsButton.getPreferredSize();
        settingsButton.setBounds(ltr ? area.x + area.width - g.width - 16 : area.x + 16, area.y + 12, g.width, g.height);
    }

    /**
     * @param handler called when the Settings gear is pressed
     */
    public void setOnSettings(Runnable handler) {
        settingsButton.addActionListener(e -> handler.run());
    }

    /**
     * @param visible {@code false} to hide the gear (on the Settings screen)
     */
    public void setCornerButtonsVisible(boolean visible) {
        settingsButton.setVisible(visible);
    }

    @Override
    public void applyComponentOrientation(ComponentOrientation orientation) {
        super.applyComponentOrientation(orientation);
        placeCornerButtons();
    }

    /**
     * @param handler called when the user closes the window
     */
    public void setOnCloseRequested(Runnable handler) {
        this.onCloseRequested = handler;
    }
}
