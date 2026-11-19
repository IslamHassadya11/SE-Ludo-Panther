package view;

import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;

/**
 * The screen parts shared by all screens, in one place: buttons ({@link Button},
 * {@link GearButton}), input fields ({@link TextField}, {@link PasswordField}), choices ({@link ChoicePill}), containers
 * ({@link Background}, {@link Card}, {@link GlowCard}, {@link PageLayout}) and labels ({@link Title}, {@link Logo},
 * {@link PillLabel}). Each part is its own nested class because each one extends a
 * different Swing class (JButton, JTextField, JPanel, ...); this outer class only groups them and is never created.
 * They take their colors, fonts and glow from {@link Theme}.
 */
public final class Widgets {

    /**
     * Only nested component classes; no objects of this class are created.
     */
    private Widgets() {
    }

    /**
     * Places something on a hand-painted part of the screen in the reading direction: in Hebrew and Arabic the
     * component is mirrored, so a position measured from the left edge is measured from the right edge instead.
     *
     * @param c     the component being painted
     * @param x     left edge of the thing in a left-to-right layout
     * @param width its width (0 for a center point)
     * @return its left edge in the component's own layout
     */
    static double startX(Component c, double x, double width) {
        return c.getComponentOrientation().isLeftToRight() ? x : c.getWidth() - x - width;
    }

    /**
     * Animated background shared by all screens: a vignette, a slowly waving aurora in the colors of the appearance
     * and drifting LED "fireflies". Also offers {@link #paintCard} for the glowing content cards.
     */
    public static class Background extends JPanel {

        private static final int PARTICLES = 42;
        /** The aurora is computed at 1/8 of the screen size and stretched smoothly. */
        private static final int AURORA_STEP = 8;
        private final double[][] particles = new double[PARTICLES][4];
        private java.awt.image.BufferedImage aurora;
        private int[] auroraPixels;

        /**
         * @param layout layout for the panel's children
         */
        public Background(LayoutManager layout) {
            super(layout);
            setOpaque(true);
            setBackground(Theme.background());
            java.util.Random seeds = new java.util.Random(7);
            for (double[] p : particles) {
                p[0] = seeds.nextDouble();
                p[1] = seeds.nextDouble();
                p[2] = 0.01 + seeds.nextDouble() * 0.025;
                p[3] = seeds.nextDouble() * 10;
            }
            Theme.Motion.subscribe(this);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            float radius = (float) Math.max(w, h) * 0.75f;
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.5, h * 0.35), radius,
                    new float[]{0f, 0.55f, 1f},
                    new Color[]{Theme.vignette(), Theme.background(), Theme.deepBackground()},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE));
            g2.fillRect(0, 0, w, h);
            Theme.Backdrop backdrop = Theme.backdrop();
            if (backdrop != Theme.Backdrop.CALM) {
                paintSwooshes(g2, w, h);
                paintAurora(g2, w, h);
            }

            double t = Theme.Motion.secondsSinceStart();
            Color[] particleColors = {Theme.neonPurple(), Theme.neonCyan(), Theme.pantherGold()};
            for (int i = 0; i < PARTICLES; i++) {
                double[] p = particles[i];
                double y = (p[1] - t * p[2]) % 1.0;
                if (y < 0) {
                    y += 1;
                }
                double x = p[0] + 0.015 * Math.sin(t * 0.6 + p[3]);
                double twinkle = 0.25 + 0.55 * (0.5 + 0.5 * Math.sin(t * 1.7 + p[3] * 3));
                Theme.Glow.paintLed(g2, x * w, y * h, 2, particleColors[i % particleColors.length], twinkle);
            }
            g2.dispose();
        }

        /**
         * Two big soft color swooshes in the corners, like the Panther Ludo artwork: electric blue from the top
         * left and hot pink from the bottom right; they breathe slowly.
         */
        private void paintSwooshes(Graphics2D g2, int w, int h) {
            Color[] rims = Theme.rimLights();
            double t = Theme.Motion.secondsSinceStart();
            double light = Theme.mode() == Theme.Mode.LIGHT ? 0.3 : 0.32;
            Graphics2D g = (Graphics2D) g2.create();
            for (int i = 0; i < 2; i++) {
                double breathe = 0.85 + 0.15 * Math.sin(t * 0.35 + i * 2);
                double cx = i == 0 ? -w * 0.05 : w * 1.05;
                double cy = i == 0 ? -h * 0.05 : h * 1.05;
                float radius = (float) (Math.max(w, h) * 0.42 * breathe);
                g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), radius, new float[]{0f, 0.5f, 1f},
                        new Color[]{Theme.alpha(rims[i], light), Theme.alpha(rims[i], light * 0.3),
                                Theme.alpha(rims[i], 0)}));
                g.fill(new java.awt.geom.Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
                // A thin diagonal light streak through the swoosh.
                g.rotate(-0.5, cx, cy);
                double streak = radius * 0.9;
                g.setPaint(new GradientPaint((float) (cx - streak), (float) cy, Theme.alpha(rims[i], 0),
                        (float) cx, (float) cy, Theme.alpha(Color.WHITE, light * 0.35)));
                g.fill(new Rectangle2D.Double(cx - streak, cy - 1.5 + (i == 0 ? radius * 0.45 : -radius * 0.45),
                        streak * 2, 3));
                g.rotate(0.5, cx, cy);
            }
            g.dispose();
        }

        /**
         * Three soft curtains of light that wave slowly across the upper part of the screen. Each curtain has a
         * sharp lower edge and fades upward, with faint vertical rays, like a real aurora.
         */
        private void paintAurora(Graphics2D g2, int w, int h) {
            int aw = Math.max(16, w / AURORA_STEP);
            int ah = Math.max(9, h / AURORA_STEP);
            if (aurora == null || aurora.getWidth() != aw || aurora.getHeight() != ah) {
                aurora = new java.awt.image.BufferedImage(aw, ah, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                auroraPixels = new int[aw * ah];
            }
            Color[] colors = Theme.aurora();
            double strength = Theme.mode() == Theme.Mode.LIGHT ? 0.5 : 0.72;
            double t = Theme.Motion.secondsSinceStart();
            int bands = colors.length;
            double[][] center = new double[bands][aw];
            double[][] rays = new double[bands][aw];
            for (int b = 0; b < bands; b++) {
                for (int x = 0; x < aw; x++) {
                    double u = (double) x / aw;
                    center[b][x] = 0.2 + b * 0.16 + 0.07 * Math.sin(u * (3.1 + b) + t * (0.17 + 0.05 * b) + b * 2)
                            + 0.04 * Math.sin(u * (7.3 - b) - t * (0.23 + 0.04 * b));
                    // Fine vertical rays that shimmer, like the curtains of a real aurora.
                    double fine = 0.5 + 0.5 * Math.sin(u * (90 + 17 * b) + t * (1.3 + 0.2 * b) + Math.sin(u * 13 + t));
                    rays[b][x] = (0.62 + 0.18 * Math.sin(u * (21 + 5 * b) + t * 0.5 + b) + 0.2 * fine)
                            * (0.5 + 0.5 * Math.sin(u * Math.PI * (1.4 + 0.3 * b) + t * 0.07 + b * 1.7));
                }
            }
            for (int y = 0; y < ah; y++) {
                double v = (double) y / ah;
                for (int x = 0; x < aw; x++) {
                    double r = 0;
                    double gr = 0;
                    double bl = 0;
                    double sum = 0;
                    for (int b = 0; b < bands; b++) {
                        double d = (v - center[b][x]) / 0.09;
                        double glow = (d < 0 ? Math.exp(-d * d * 0.18) : Math.exp(-d * d * 2.2)) * rays[b][x];
                        r += colors[b].getRed() * glow;
                        gr += colors[b].getGreen() * glow;
                        bl += colors[b].getBlue() * glow;
                        sum += glow;
                    }
                    int alpha = (int) Math.round(Math.min(1, sum) * strength * 255);
                    if (sum <= 0.001 || alpha <= 0) {
                        auroraPixels[y * aw + x] = 0;
                    } else {
                        auroraPixels[y * aw + x] = alpha << 24 | (int) Math.min(255, r / sum) << 16
                                | (int) Math.min(255, gr / sum) << 8 | (int) Math.min(255, bl / sum);
                    }
                }
            }
            aurora.setRGB(0, 0, aw, ah, auroraPixels, 0, aw);
            Graphics2D g = (Graphics2D) g2.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(aurora, 0, 0, w, h, null);
            g.dispose();
        }

        /**
         * Paints a glowing content card with an animated LED border.
         *
         * @param g2     graphics
         * @param x      left
         * @param y      top
         * @param width  width
         * @param height height
         */
        public static void paintCard(Graphics2D g2, double x, double y, double width, double height) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // A bubbly comic card: paper fill, ink outline and a hard shadow.
            RoundRectangle2D card = new RoundRectangle2D.Double(x, y, width - 8, height - 10, 28, 28);
            Theme.Comic.paintCard(g2, card, Theme.mode() == Theme.Mode.LIGHT ? Color.WHITE
                    : Theme.alpha(Theme.surface(), 0.96), Theme.Comic.cardShadow());
        }

    }

    /**
     * Wrapped text drawn directly with Java 2D: plain text (line breaks with {@code \n}) or simple markup
     * ({@code <b>}, {@code <br>} and {@code &bull;}). Hebrew and Arabic lines run right to left and start at the right
     * edge, even when a line begins with a Latin name. It is used instead of Swing's HTML labels, whose right-to-left
     * measuring fails on some computers ("TextHitInfo is out of range").
     */
    public static final class Paragraph extends JComponent {

        private final java.util.List<java.text.AttributedString> lines = new java.util.ArrayList<>();
        private final int wrapWidth;
        private boolean centered;
        private String text = "";

        /**
         * @param markup the text
         * @param font   the normal font (bold parts use its bold style)
         * @param color  the text color
         * @param width  the width at which lines wrap, in pixels
         */
        public Paragraph(String markup, Font font, Color color, int width) {
            this.wrapWidth = width;
            setFont(font);
            setForeground(color);
            setOpaque(false);
            setText(markup);
        }

        /**
         * @param centered {@code true} to center every line instead of starting it at the reading side
         * @return this paragraph
         */
        public Paragraph setCentered(boolean centered) {
            this.centered = centered;
            repaint();
            return this;
        }

        /**
         * @param markup the new text
         */
        public void setText(String markup) {
            text = markup == null ? "" : markup;
            lines.clear();
            Font font = getFont();
            String clean = text.replace("\u200f", "").replace("&bull;", "\u2022").replace("&amp;", "&")
                    .replace("\n", "<br>");
            for (String line : clean.split("<br>", -1)) {
                StringBuilder plain = new StringBuilder();
                java.util.List<int[]> bold = new java.util.ArrayList<>();
                int at = 0;
                int start = -1;
                while (at < line.length()) {
                    if (line.startsWith("<b>", at)) {
                        start = plain.length();
                        at += 3;
                    } else if (line.startsWith("</b>", at)) {
                        bold.add(new int[]{Math.max(0, start), plain.length()});
                        at += 4;
                    } else {
                        plain.append(line.charAt(at++));
                    }
                }
                String value = plain.toString().strip();
                java.text.AttributedString styled = new java.text.AttributedString(value.isEmpty() ? " " : value);
                styled.addAttribute(java.awt.font.TextAttribute.FONT, font);
                for (int[] range : bold) {
                    int from = Math.min(range[0], value.length());
                    int to = Math.min(range[1], value.length());
                    if (to > from) {
                        styled.addAttribute(java.awt.font.TextAttribute.FONT, font.deriveFont(Font.BOLD), from, to);
                    }
                }
                if (Assets.Texts.language().isRightToLeft()) {
                    styled.addAttribute(java.awt.font.TextAttribute.RUN_DIRECTION,
                            java.awt.font.TextAttribute.RUN_DIRECTION_RTL);
                }
                lines.add(styled);
            }
            revalidate();
            repaint();
        }

        /**
         * @return the text as it was set
         */
        public String getText() {
            return text;
        }

        @Override
        public void setFont(Font font) {
            super.setFont(font);
            if (text != null && lines != null) {
                setText(text);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            double[] size = layout(null, wrapWidth);
            return new Dimension((int) Math.ceil(size[0]) + 2, (int) Math.ceil(size[1]) + 4);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2.setColor(getForeground());
            layout(g2, Math.min(getWidth(), wrapWidth));
            g2.dispose();
        }

        /**
         * Lays the lines out at the given width and draws them if a graphics context is given.
         *
         * @return the widest line and the total height
         */
        private double[] layout(Graphics2D g2, int width) {
            java.awt.font.FontRenderContext frc = g2 != null ? g2.getFontRenderContext()
                    : new java.awt.font.FontRenderContext(null, true, true);
            boolean rtl = Assets.Texts.language().isRightToLeft();
            double y = 0;
            double widest = 0;
            for (java.text.AttributedString line : lines) {
                java.text.AttributedCharacterIterator it = line.getIterator();
                java.awt.font.LineBreakMeasurer measurer = new java.awt.font.LineBreakMeasurer(it, frc);
                while (measurer.getPosition() < it.getEndIndex()) {
                    java.awt.font.TextLayout part = measurer.nextLayout(Math.max(40, width));
                    double advance = part.getAdvance();
                    widest = Math.max(widest, advance);
                    y += part.getAscent();
                    if (g2 != null) {
                        double x = centered ? (getWidth() - advance) / 2 : rtl ? getWidth() - advance : 0;
                        part.draw(g2, (float) x, (float) y);
                    }
                    y += part.getDescent() + part.getLeading() + 2;
                }
            }
            return new double[]{widest, y};
        }
    }

    /**
     * Puts a screen into a scroll pane that only scrolls when the window is smaller than the screen needs: in a big
     * window the screen simply fills it; in a small or half-screen window nothing is cut off, it can be scrolled.
     *
     * @param screen a screen whose content has a natural size (for example a centered card)
     * @return the scroll pane to show instead of the screen
     */
    public static JScrollPane scrollWhenSmall(JComponent screen) {
        final class Holder extends JPanel implements Scrollable {

            Holder() {
                super(new BorderLayout());
                add(screen, BorderLayout.CENTER);
            }

            @Override
            public Dimension getPreferredScrollableViewportSize() {
                return getPreferredSize();
            }

            @Override
            public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
                return 24;
            }

            @Override
            public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
                return 120;
            }

            @Override
            public boolean getScrollableTracksViewportWidth() {
                return getParent() == null || getParent().getWidth() >= screen.getPreferredSize().width;
            }

            @Override
            public boolean getScrollableTracksViewportHeight() {
                return getParent() == null || getParent().getHeight() >= screen.getPreferredSize().height;
            }
        }
        JScrollPane scroll = new JScrollPane(new Holder());
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.background());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getHorizontalScrollBar().setUnitIncrement(24);
        return scroll;
    }

    /**
     * Keeps every window of the program on the part of the screen the user can really see: never behind the Windows
     * taskbar and never past an edge. It works for the screen the window is on, whatever its size or scaling: a window
     * that is too big is made smaller (its content then scrolls), and one that sticks out is moved back in. A
     * maximized main window already uses the whole free screen and is left alone.
     */
    public static final class ScreenFit {

        private static boolean installed;

        /**
         * Only static helpers; no objects of this class are created.
         */
        private ScreenFit() {
        }

        /**
         * Watches every window of the program from now on: whenever one opens, is shown or changes its size (for
         * example a question window that grows to show the explanation), it is kept on the screen.
         */
        public static synchronized void watchAllWindows() {
            if (installed) {
                return;
            }
            installed = true;
            Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                if (event.getSource() instanceof Window window
                        && (event.getID() == WindowEvent.WINDOW_OPENED || event.getID() == ComponentEvent.COMPONENT_SHOWN
                        || event.getID() == ComponentEvent.COMPONENT_RESIZED)) {
                    SwingUtilities.invokeLater(() -> keepOnScreen(window));
                }
            }, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK);
        }

        /**
         * @param component any component (or {@code null} for the main screen)
         * @return the part of its screen that is not covered by the taskbar
         */
        public static Rectangle usableArea(Component component) {
            GraphicsConfiguration screen = component == null ? null : component.getGraphicsConfiguration();
            if (screen == null) {
                screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                        .getDefaultConfiguration();
            }
            Rectangle bounds = screen.getBounds();
            Insets taskbar = Toolkit.getDefaultToolkit().getScreenInsets(screen);
            return new Rectangle(bounds.x + taskbar.left, bounds.y + taskbar.top,
                    bounds.width - taskbar.left - taskbar.right, bounds.height - taskbar.top - taskbar.bottom);
        }

        /**
         * Makes a window fit on the usable part of its screen: smaller if needed, then moved fully inside.
         *
         * @param window the window
         */
        public static void keepOnScreen(Window window) {
            if (!window.isDisplayable() || window instanceof Frame frame
                    && (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                return;
            }
            Rectangle usable = usableArea(window);
            Dimension minimum = window.getMinimumSize();
            if (minimum.width > usable.width || minimum.height > usable.height) {
                window.setMinimumSize(new Dimension(Math.min(minimum.width, usable.width),
                        Math.min(minimum.height, usable.height)));
            }
            Rectangle now = window.getBounds();
            int width = Math.min(now.width, usable.width);
            int height = Math.min(now.height, usable.height);
            int x = Math.max(usable.x, Math.min(now.x, usable.x + usable.width - width));
            int y = Math.max(usable.y, Math.min(now.y, usable.y + usable.height - height));
            if (x != now.x || y != now.y || width != now.width || height != now.height) {
                window.setBounds(x, y, width, height);
                window.validate();
            }
        }

        /**
         * Sizes a pop-up to its content, centers it over its parent and keeps it on the screen.
         *
         * @param window the pop-up
         * @param parent what it belongs to (or {@code null})
         */
        public static void packAndPlace(Window window, Component parent) {
            window.pack();
            window.setLocationRelativeTo(parent);
            keepOnScreen(window);
        }

        /**
         * Puts a pop-up's content in a scroll pane without borders: it looks the same, but if the screen is too small
         * for it, the content scrolls instead of disappearing under the taskbar.
         *
         * @param content    the pop-up's content
         * @param background color shown around the content
         * @return the scroll pane to use as the pop-up's content pane
         */
        public static JScrollPane wrapInScroll(JComponent content, Color background) {
            JScrollPane scroll = new JScrollPane(content);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.setViewportBorder(null);
            scroll.getViewport().setBackground(background);
            scroll.setBackground(background);
            scroll.getVerticalScrollBar().setUnitIncrement(24);
            scroll.getHorizontalScrollBar().setUnitIncrement(24);
            return scroll;
        }
    }

    /**
     * Space kept free at the end of a top bar for the Settings gear that floats in the top corner of every screen.
     */
    public static final int CORNER_ROOM = 66;

    /**
     * Building blocks of the full-window list pages (Game history, Question wizard): a big title with a subtitle and
     * buttons on the right, labeled drop-down filters, and a large, airy table; and the Back button every screen
     * shares.
     */
    public static final class PageLayout {

        /**
         * Only static helpers; no objects of this class are created.
         */
        private PageLayout() {
        }

        /**
         * @param title    big title, e.g. "Game history"
         * @param subtitle grey line under it
         * @param buttons  buttons at the right
         * @return the header row
         */
        public static JComponent header(String title, String subtitle, JComponent... buttons) {
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JPanel texts = new JPanel();
            texts.setOpaque(false);
            texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
            JLabel big = new JLabel(title);
            big.setFont(Theme.Fonts.baseFont(38, true));
            big.setForeground(Theme.text());
            JLabel small = new JLabel(subtitle);
            small.setFont(Theme.Fonts.baseFont(18, false));
            small.setForeground(Theme.textMuted());
            texts.add(big);
            texts.add(small);
            header.add(texts, BorderLayout.LINE_START);
            JPanel right = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
            right.setOpaque(false);
            for (JComponent button : buttons) {
                if (button instanceof Button b) {
                    b.setFont(Theme.Fonts.baseFont(19, true));
                    b.setBorder(BorderFactory.createEmptyBorder(14, 30, 14, 30));
                }
                right.add(button);
            }
            // Room for the Settings gear that floats in this corner.
            right.add(Box.createHorizontalStrut(CORNER_ROOM));
            header.add(right, BorderLayout.LINE_END);
            return header;
        }

        /**
         * The Back button of every screen: the same color, size and arrow everywhere.
         *
         * @return a new Back button
         */
        public static Button backButton() {
            Button back = new Button(Assets.Texts.get("common.back"), Theme.neonPurple(), true);
            // The same comic gradient style as New Game on the main menu.
            back.setGradient(new Color(0x2CE5FF), new Color(0x8B5CF6));
            back.setSymbol("back");
            back.setFont(Theme.Fonts.baseFont(19, true));
            back.setBorder(BorderFactory.createEmptyBorder(12, 26, 12, 26));
            back.setMinimumSize(back.getPreferredSize());
            return back;
        }

        /**
         * Puts the Back button in the top corner where reading starts (left, or right in Hebrew and Arabic), the same
         * place on every screen; the Settings gear sits in the other top corner.
         *
         * @param back the Back button
         * @return the row to add at the top of the screen
         */
        public static JComponent backRow(Button back) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
            row.setOpaque(false);
            row.add(back);
            return row;
        }

        /**
         * Lays out a screen whose content is one centered card: the card in the middle and the Back button in the top
         * corner where reading starts, like on the screens with a header.
         *
         * @param screen a screen with a {@link GridBagLayout}
         * @param back   the Back button
         * @param card   the centered content
         */
        public static void centerWithBack(JComponent screen, Button back, JComponent card) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 0;
            c.weightx = 1;
            c.weighty = 1;
            c.anchor = GridBagConstraints.FIRST_LINE_START;
            c.insets = new Insets(26, 26, 0, 26);
            screen.add(back, c);
            c.anchor = GridBagConstraints.CENTER;
            c.insets = new Insets(0, 0, 0, 0);
            screen.add(card, c);
        }

        /**
         * @param text label in front of a filter, e.g. "Level"
         * @return the label
         */
        public static JLabel createCaption(String text) {
            JLabel label = new JLabel(text);
            label.setFont(Theme.Fonts.baseFont(18, true));
            label.setForeground(Theme.text());
            return label;
        }

        /**
         * @param combo a drop-down to style
         */
        public static void styleComboBox(JComboBox<?> combo) {
            combo.setFont(Theme.Fonts.baseFont(18, false));
            combo.setBackground(Theme.surfaceHighlight());
            combo.setForeground(Theme.text());
            combo.setFocusable(false);
            combo.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
                @Override
                protected JButton createArrowButton() {
                    JButton arrow = new JButton() {
                        @Override
                        protected void paintComponent(Graphics g) {
                            Graphics2D g2 = (Graphics2D) g.create();
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            g2.setColor(Theme.surfaceHighlight());
                            g2.fillRect(0, 0, getWidth(), getHeight());
                            g2.setColor(Theme.gridColor());
                            g2.drawLine(0, 4, 0, getHeight() - 5);
                            double cx = getWidth() / 2.0;
                            double cy = getHeight() / 2.0;
                            Path2D p = new Path2D.Double();
                            p.moveTo(cx - 6, cy - 3);
                            p.lineTo(cx + 6, cy - 3);
                            p.lineTo(cx, cy + 4);
                            p.closePath();
                            g2.setColor(Theme.text());
                            g2.fill(p);
                            g2.dispose();
                        }
                    };
                    arrow.setBorder(BorderFactory.createEmptyBorder());
                    arrow.setPreferredSize(new Dimension(32, 32));
                    arrow.setFocusable(false);
                    return arrow;
                }
            });
            combo.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.gridColor(), 2, true),
                    BorderFactory.createEmptyBorder(2, 6, 2, 0)));
            Dimension size = combo.getPreferredSize();
            combo.setPreferredSize(new Dimension(size.width + 24, 42));
        }

        /**
         * Large rows, bold centered column titles with the sort arrow, horizontal lines only.
         *
         * @param table the table
         */
        public static void styleTable(JTable table) {
            table.setFont(Theme.Fonts.baseFont(18, false));
            table.setRowHeight(48);
            table.setBackground(Theme.surfaceHighlight());
            table.setForeground(Theme.text());
            table.setGridColor(Theme.gridColor());
            table.setShowVerticalLines(false);
            table.setShowHorizontalLines(true);
            table.setIntercellSpacing(new Dimension(0, 1));
            table.setSelectionBackground(Theme.mix(Theme.neonPurple(), Theme.surfaceHighlight(), 0.3));
            table.setSelectionForeground(Theme.text());
            table.setFillsViewportHeight(true);
            table.getTableHeader().setReorderingAllowed(false);
            table.getTableHeader().setPreferredSize(new Dimension(10, 38));
            table.getTableHeader().setDefaultRenderer(new javax.swing.table.DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                               boolean focus, int row, int column) {
                    super.getTableCellRendererComponent(t, value, false, false, row, column);
                    setHorizontalAlignment(CENTER);
                    setFont(Theme.Fonts.baseFont(18, true));
                    setBackground(Theme.surface());
                    setForeground(Theme.text());
                    setBorder(BorderFactory.createMatteBorder(0, 0, 2, 1, Theme.gridColor()));
                    setIcon(null);
                    if (t.getRowSorter() != null) {
                        for (RowSorter.SortKey key : t.getRowSorter().getSortKeys()) {
                            if (key.getColumn() == t.convertColumnIndexToModel(column)
                                    && key.getSortOrder() != SortOrder.UNSORTED) {
                                setIcon(new SortArrow(key.getSortOrder() == SortOrder.ASCENDING));
                                setHorizontalTextPosition(LEADING);
                            }
                        }
                    }
                    return this;
                }
            });
            javax.swing.table.DefaultTableCellRenderer cells = new javax.swing.table.DefaultTableCellRenderer();
            cells.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            table.setDefaultRenderer(Object.class, cells);
        }

        /**
         * @param table the table
         * @return the table in a scroll pane with a thin frame
         */
        public static JScrollPane scrollPane(JTable table) {
            JScrollPane scroll = new JScrollPane(table);
            scroll.setBorder(BorderFactory.createLineBorder(Theme.gridColor()));
            scroll.getViewport().setBackground(Theme.surfaceHighlight());
            return scroll;
        }

        /** The small triangle next to the title of the sorted column. */
        private record SortArrow(boolean up) implements Icon {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.text());
                Path2D p = new Path2D.Double();
                if (up) {
                    p.moveTo(x, y + 8);
                    p.lineTo(x + 10, y + 8);
                    p.lineTo(x + 5, y + 2);
                } else {
                    p.moveTo(x, y + 2);
                    p.lineTo(x + 10, y + 2);
                    p.lineTo(x + 5, y + 8);
                }
                p.closePath();
                g2.fill(p);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 10;
            }

            @Override
            public int getIconHeight() {
                return 10;
            }
        }
    }

    /**
     * The Panther Ludo logo (the panther in the ring) for the current appearance, floating gently with a blue and pink
     * glow behind it. Falls back to a drawn panther head if the image is missing.
     */
    public static final class Logo extends JComponent {

        private int size;
        private java.awt.image.BufferedImage scaled;
        private int scaledPixels;

        /**
         * @param size width and height in pixels
         */
        public Logo(int size) {
            setOpaque(false);
            setLogoSize(size);
            Theme.Motion.subscribe(this);
        }

        /**
         * @param size new width and height in pixels (the logo is re-scaled sharply for it)
         */
        public void setLogoSize(int size) {
            if (size == this.size) {
                return;
            }
            this.size = size;
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double side = Math.min(getWidth(), getHeight());
            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0 + Math.sin(Theme.Motion.secondsSinceStart() * 1.3) * side * 0.012;
            Color[] rims = Theme.rimLights();
            double breathe = 0.35 + 0.25 * Theme.Motion.pulse(0.35);
            Theme.Glow.paintHalo(g2, cx - side * 0.12, cy, side * 0.5, rims[0], breathe);
            Theme.Glow.paintHalo(g2, cx + side * 0.12, cy, side * 0.5, rims[1], breathe);
            // Scaled for the real screen pixels (Windows display scaling), so it stays sharp when enlarged.
            double screen = Math.max(1, g2.getTransform().getScaleX());
            java.awt.image.BufferedImage image = image((int) Math.round(side * screen));
            if (image == null) {
                Theme.Panther.paintHead(g2, cx, cy, side * 0.7, new Color(0x2B2B35), rims[1], 0.6, 1);
            } else {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.drawImage(image, (int) Math.round(cx - side / 2), (int) Math.round(cy - side / 2),
                        (int) Math.round(side), (int) Math.round(side), null);
            }
            g2.dispose();
        }

        /**
         * The logo scaled smoothly to the given pixel size, in a few halving steps for a sharp result.
         */
        private java.awt.image.BufferedImage image(int pixels) {
            if (scaled == null || scaledPixels != pixels) {
                java.awt.image.BufferedImage source = Theme.logo();
                if (source == null) {
                    return null;
                }
                java.awt.image.BufferedImage current = source;
                int w = source.getWidth();
                while (w / 2 >= pixels) {
                    w /= 2;
                    current = resize(current, w);
                }
                scaled = pixels >= current.getWidth() ? current : resize(current, pixels);
                scaledPixels = pixels;
            }
            return scaled;
        }

        /**
         * @return the picture smoothly scaled to the width
         */
        private static java.awt.image.BufferedImage resize(java.awt.image.BufferedImage source, int width) {
            int height = Math.max(1, source.getHeight() * width / source.getWidth());
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(width, height,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
            g.dispose();
            return out;
        }
    }

    /**
     * Rounded neon button with an animated hover glow. "Primary" buttons are filled with the accent color; secondary
     * buttons are dark with a glowing outline. Keyboard focus is shown with a bright ring.
     */
    public static class Button extends JButton {

        private Color accent;
        /** Second color of a two-color gradient button, or {@code null}. */
        private Color accent2;
        private boolean primary;
        /** A Panther Ludo icon shown before the label, or {@code null}. */
        private String iconName;
        /** A drawn symbol before the label ("play" or "stop"), or {@code null}; drawn, so it shows on every computer. */
        private String symbol;
        private double hover;
        private double hoverTarget;
        private final Timer hoverTimer = new Timer(16, e -> stepHover());

        /**
         * @param text    label
         * @param accent  neon color
         * @param primary filled (primary action) or outlined
         */
        public Button(String text, Color accent, boolean primary) {
            super(text);
            this.accent = accent;
            this.primary = primary;
            setFont(Theme.Fonts.BUTTON);
            setForeground(Theme.text());
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(12, 28, 12, 28));
            addActionListener(e -> Assets.Sound.play(Assets.Sound.Effect.CLICK));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    animateHover(1);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    animateHover(0);
                }
            });
        }

        /**
         * @param name a Panther Ludo icon (see {@link Theme#drawIcon}) to show before the label, or {@code null}
         */
        public void setIconName(String name) {
            this.iconName = name;
            revalidate();
            repaint();
        }

        /**
         * @param symbol "play" (a triangle), "stop" (a square) or "back" (an arrow pointing back) drawn before the label,
         *               or {@code null}
         */
        public void setSymbol(String symbol) {
            this.symbol = symbol;
            revalidate();
            repaint();
        }

        /**
         * @param accent new neon color (e.g. the current player's color)
         */
        public void setAccent(Color accent) {
            this.accent = accent;
            repaint();
        }

        /**
         * Makes this a colorful gradient button (white letters), e.g. on the main menu.
         *
         * @param from left color
         * @param to   right color
         */
        public void setGradient(Color from, Color to) {
            this.accent = from;
            this.accent2 = to;
            // Gradient buttons are main buttons: the same comic letters as New Game.
            this.primary = true;
            revalidate();
            repaint();
        }

        /**
         * @param filled {@code true} for the candy-filled look (e.g. to mark an answer as right or wrong)
         */
        public void setFilled(boolean filled) {
            this.primary = filled;
            setForeground(filled ? Theme.Comic.textColorOn(accent) : Theme.text());
            repaint();
        }

        /**
         * Starts fading the hover glow in (1) or out (0).
         */
        private void animateHover(double target) {
            hoverTarget = target;
            if (!hoverTimer.isRunning()) {
                hoverTimer.start();
            }
        }

        /**
         * One step of the hover fade; stops the timer when the target is reached.
         */
        private void stepHover() {
            hover += (hoverTarget - hover) * 0.25;
            if (Math.abs(hoverTarget - hover) < 0.02) {
                hover = hoverTarget;
                hoverTimer.stop();
            }
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            if (isPreferredSizeSet() || getClientProperty(BasicHTML.propertyKey) != null || getText() == null) {
                return size;
            }
            // Room for the icon before the label, for the bigger comic letters of main buttons, and for the shadow.
            int icon = iconName == null && symbol == null ? 0 : (int) (size.height * 0.62) + 8;
            int extra = 0;
            if (primary && Theme.Fonts.comicFont(12f).canDisplayUpTo(getText()) == -1) {
                String upper = getText().toUpperCase(java.util.Locale.ROOT);
                Font comic = Theme.Fonts.comicFont(getFont().getSize2D() * 1.3f);
                extra = Math.max(0, getFontMetrics(comic).stringWidth(upper) - getFontMetrics(getFont())
                        .stringWidth(getText()));
            }
            return new Dimension(size.width + icon + extra, size.height + (int) Theme.Comic.DROP);
        }

        /**
         * A glowing two-color gradient pill with a glossy top and a bright neon edge.
         */
        private void paintGradient(Graphics2D g2, RoundRectangle2D shape, double hover, boolean pressed) {
            double dip = pressed ? 1 : 0;
            double arc = Math.min(shape.getHeight() * 0.62, 26);
            RoundRectangle2D body = new RoundRectangle2D.Double(shape.getX() + 1, shape.getY() + 1 + dip,
                    shape.getWidth() - 2, shape.getHeight() - 2, arc, arc);
            Theme.Glow.paintHalo(g2, body.getCenterX(), body.getCenterY(), body.getWidth() * 0.55,
                    Theme.mix(accent, accent2, 0.5), 0.2 + 0.25 * hover);
            Color a = Theme.mix(accent, Color.WHITE, 0.92 - 0.08 * hover);
            Color b = Theme.mix(accent2, Color.WHITE, 0.92 - 0.08 * hover);
            g2.setPaint(new GradientPaint((float) body.getX(), 0, a, (float) body.getMaxX(), 0, b));
            g2.fill(body);
            Shape clip = g2.getClip();
            g2.clip(body);
            g2.setPaint(new GradientPaint(0, (float) body.getY(), new Color(255, 255, 255, 90), 0,
                    (float) (body.getY() + body.getHeight() * 0.55), new Color(255, 255, 255, 0)));
            g2.fill(new Rectangle2D.Double(body.getX(), body.getY(), body.getWidth(), body.getHeight() * 0.55));
            g2.setPaint(new GradientPaint(0, (float) (body.getMaxY() - body.getHeight() * 0.35), new Color(0, 0, 0, 0),
                    0, (float) body.getMaxY(), new Color(0, 0, 0, 50)));
            g2.fill(body);
            g2.setClip(clip);
            g2.setPaint(new GradientPaint((float) body.getX(), 0, Theme.mix(accent, Color.WHITE, 0.6),
                    (float) body.getMaxX(), 0, Theme.mix(accent2, Color.WHITE, 0.6)));
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(body);
        }

        /**
         * @return extra glow 0–1 for subclasses that pulse
         */
        protected double extraGlow() {
            return 0;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean enabled = isEnabled();
            boolean pressed = getModel().isArmed() && getModel().isPressed();
            // Bubbly comic button: candy fill, ink outline, glossy shine and a hard shadow it sinks onto.
            double inset = 4;
            double h = getHeight() - 2 * inset - Theme.Comic.DROP;
            double arc = Math.min(h * 0.62, 26);
            RoundRectangle2D shape = new RoundRectangle2D.Double(inset, inset, getWidth() - 2 * inset, h, arc, arc);
            double pulse = enabled ? Math.min(1, hover + extraGlow() * 0.5) : 0;
            if (enabled && extraGlow() > 0) {
                Theme.Glow.paintHalo(g2, shape.getCenterX(), shape.getCenterY(), shape.getWidth() * 0.62, Theme.Comic.candyColor(accent),
                        0.25 + 0.35 * extraGlow());
            }
            if (accent2 != null && enabled) {
                paintGradient(g2, shape, pulse, pressed);
            } else {
                Theme.Comic.paintButton(g2, shape, accent, primary, enabled, pulse, pressed);
            }
            if (pressed) {
                shape = new RoundRectangle2D.Double(shape.getX(), shape.getY() + Theme.Comic.DROP - 1, shape.getWidth(),
                        shape.getHeight(), arc, arc);
            }
            String text = getText();
            boolean plain = getClientProperty(BasicHTML.propertyKey) != null;
            // Main actions speak comic (NEW GAME, ROLL DICE, LOG IN...); the others use the normal bold font. The
            // comic font has no Hebrew or Arabic letters: those words use the clear bold font at its normal size.
            boolean comic = primary && !plain && Theme.Fonts.comicFont(12f).canDisplayUpTo(text) == -1;
            g2.setFont(comic ? Theme.Fonts.comicFont(getFont().getSize2D() * 1.3f) : getFont().deriveFont(Font.BOLD));
            if (comic) {
                text = text.toUpperCase(java.util.Locale.ROOT);
            }
            FontMetrics fm = g2.getFontMetrics();
            double room = getWidth() - 2 * inset - 18 - (iconName == null && symbol == null ? 0
                    : Math.min(h * 0.82, 44) + 8);
            while (!plain && fm.stringWidth(text) > room && g2.getFont().getSize2D() > 9) {
                g2.setFont(g2.getFont().deriveFont(g2.getFont().getSize2D() - 1));
                fm = g2.getFontMetrics();
            }
            Color textColor = !enabled ? Theme.textMuted() : accent2 != null ? Color.WHITE
                    : primary ? Theme.Comic.textColorOn(accent) : Theme.text();
            // The letters are centered on what is really drawn, so tall Arabic and Hebrew letters are never cut.
            double textY = text.isEmpty() ? shape.getCenterY() : shape.getCenterY()
                    - new java.awt.font.TextLayout(text, g2.getFont(), g2.getFontRenderContext()).getBounds().getCenterY();
            double shift = 0;
            if (symbol != null) {
                double size = Math.min(h * 0.42, 16);
                double total = size + 8 + fm.stringWidth(text);
                double left = (getWidth() - total) / 2;
                double cy = shape.getCenterY();
                g2.setColor(textColor);
                if ("back".equals(symbol)) {
                    // An arrow pointing back: to the left, or to the right in Hebrew and Arabic, where it also
                    // stands on the right of the word.
                    boolean rtl = !getComponentOrientation().isLeftToRight();
                    double ax = rtl ? left + fm.stringWidth(text) + 8 : left;
                    g2.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    double tip = rtl ? ax + size : ax;
                    double tail = rtl ? ax : ax + size;
                    double head = size * 0.45 * (rtl ? -1 : 1);
                    g2.draw(new Line2D.Double(tip, cy, tail, cy));
                    Path2D arrow = new Path2D.Double();
                    arrow.moveTo(tip + head, cy - size * 0.45);
                    arrow.lineTo(tip, cy);
                    arrow.lineTo(tip + head, cy + size * 0.45);
                    g2.draw(arrow);
                    shift = (rtl ? -1 : 1) * (size + 8) / 2;
                } else if ("play".equals(symbol)) {
                    Path2D tri = new Path2D.Double();
                    tri.moveTo(left, cy - size / 2);
                    tri.lineTo(left + size * 0.9, cy);
                    tri.lineTo(left, cy + size / 2);
                    tri.closePath();
                    g2.fill(tri);
                } else {
                    g2.fill(new RoundRectangle2D.Double(left, cy - size * 0.42, size * 0.84, size * 0.84, 3, 3));
                }
                if (!"back".equals(symbol)) {
                    shift = (size + 8) / 2;
                }
            } else if (iconName != null) {
                double iconSize = Math.min(h * 0.82, 44);
                double total = iconSize + 8 + fm.stringWidth(text);
                double left = (getWidth() - total) / 2;
                Composite old = g2.getComposite();
                if (!enabled) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
                }
                Theme.drawIcon(g2, iconName, left + iconSize / 2, shape.getCenterY(), iconSize);
                g2.setComposite(old);
                shift = (iconSize + 8) / 2;
            }
            if (enabled && textColor.equals(Color.WHITE)) {
                // Comic letters get a soft dark drop shadow so they pop off the color.
                g2.setColor(new Color(0, 0, 0, 110));
                g2.drawString(text, (float) ((getWidth() - fm.stringWidth(text)) / 2 + shift + 1.5), (float) (textY + 2));
            }
            g2.setColor(textColor);
            View html = (View) getClientProperty(BasicHTML.propertyKey);
            if (html != null) {
                // Long texts (e.g. question answers) are HTML so they can wrap; Swing lays them out.
                Insets in = getInsets();
                float height = html.getPreferredSpan(View.Y_AXIS);
                html.paint(g2, new Rectangle(in.left, (int) ((getHeight() - height) / 2),
                        getWidth() - in.left - in.right, (int) height));
            } else {
                g2.drawString(text, (float) ((getWidth() - fm.stringWidth(text)) / 2 + shift), (float) textY);
            }
            g2.dispose();
        }
    }

    /**
     * Rounded text field in the neon style. Its outline lights up in the accent color while focused, a grey placeholder
     * hint shows while it is empty, and it can be marked invalid, which draws a red outline (NFR-07).
     */
    public static final class TextField extends JTextField {

        private final String placeholder;
        private Color accent = Theme.neonPurple();
        private boolean invalid;

        /**
         * @param columns     preferred width in columns
         * @param placeholder hint shown while empty
         */
        public TextField(int columns, String placeholder) {
            super(columns);
            this.placeholder = placeholder;
            TextField.applyStyle(this);
        }

        /**
         * @param accent outline color while focused, e.g. the player's color on the setup screen
         */
        public void setAccent(Color accent) {
            this.accent = accent;
            repaint();
        }

        /**
         * @param invalid {@code true} to outline the field in red because its content is wrong
         */
        public void setInvalid(boolean invalid) {
            if (this.invalid != invalid) {
                this.invalid = invalid;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            TextField.paintFieldBackground(g, this, accent, invalid, placeholder);
            super.paintComponent(g);
        }

        // ------------------------------------------------------------------ shared look of the neon input fields

        /**
         * Applies colors, font, padding and focus repainting.
         *
         * @param field the field to style
         */
        static void applyStyle(JTextComponent field) {
            field.setOpaque(false);
            field.setFont(Theme.Fonts.BASE);
            field.setForeground(Theme.text());
            field.setCaretColor(Theme.text());
            field.setSelectionColor(Theme.mix(Theme.neonPurple(), Theme.surface(), 0.5));
            field.setSelectedTextColor(Theme.text());
            field.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            field.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    field.repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    field.repaint();
                }
            });
        }

        /**
         * Paints the box, outline and placeholder; call before the field paints its text.
         *
         * @param g           graphics
         * @param field       the field
         * @param accent      outline color while focused
         * @param invalid     {@code true} to show a red outline even without focus, because the input is wrong
         * @param placeholder hint shown while empty, or {@code null}
         */
        static void paintFieldBackground(Graphics g, JTextComponent field, Color accent, boolean invalid,
                                                 String placeholder) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D box = new RoundRectangle2D.Double(2, 2, field.getWidth() - 4, field.getHeight() - 4,
                    16, 16);
            g2.setColor(field.isEnabled() ? Theme.Comic.paperColor() : Theme.surface());
            g2.fill(box);
            if (field.hasFocus() || invalid) {
                Theme.Glow.paintGlowStroke(g2, box, invalid ? Theme.error() : accent, 1.5f, 0.6);
            }
            g2.setColor(invalid ? Theme.error() : field.hasFocus() ? Theme.Comic.candyColor(accent) : Theme.Comic.inkColor());
            g2.setStroke(new BasicStroke(2.5f));
            g2.draw(box);
            if (field.getDocument().getLength() == 0 && !field.hasFocus() && placeholder != null) {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(field.getFont());
                g2.setColor(Theme.textMuted());
                g2.drawString(placeholder, field.getInsets().left,
                        (field.getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2);
            }
            g2.dispose();
        }
    }

    /**
     * Password field in the neon style. The typed characters are hidden; {@link #takePassword()} hands them over and
     * clears the field so no copy of the password stays in the UI (NFR-31).
     */
    public static final class PasswordField extends JPasswordField {

        private final String placeholder;

        /**
         * @param columns     preferred width in columns
         * @param placeholder hint shown while empty
         */
        public PasswordField(int columns, String placeholder) {
            super(columns);
            this.placeholder = placeholder;
            TextField.applyStyle(this);
            setEchoChar('•');
        }

        /**
         * Shows or hides the typed characters, so a password typed only once can be checked before it is used.
         *
         * @param revealed {@code true} to show the characters
         */
        public void setRevealed(boolean revealed) {
            setEchoChar(revealed ? (char) 0 : '•');
        }

        /**
         * @return the typed password; the field is cleared. The caller should wipe the array after use.
         */
        public char[] takePassword() {
            char[] password = getPassword();
            setText("");
            return password;
        }

        /**
         * Clears the field.
         */
        public void clear() {
            char[] old = getPassword();
            Arrays.fill(old, '\0');
            setText("");
        }

        @Override
        protected void paintComponent(Graphics g) {
            TextField.paintFieldBackground(g, this, Theme.neonPurple(), false, placeholder);
            super.paintComponent(g);
        }
    }

    /**
     * Large neon heading: gradient-filled text with a softly pulsing purple glow.
     */
    public static final class Title extends JComponent {

        private String text;
        private final float size;

        /**
         * @param text title text
         * @param size font size in points (the comic font is drawn a little larger)
         */
        public Title(String text, float size) {
            this.text = text;
            this.size = size;
            setOpaque(false);
        }

        /**
         * @return the comic title font, or the bold font for scripts it does not have (Hebrew, Arabic)
         */
        private Font font() {
            return Theme.Fonts.comicFont(size * 1.18f, text);
        }

        /**
         * @param text new title text
         */
        public void setText(String text) {
            this.text = text;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontRenderContext frc = new FontRenderContext(null, true, true);
            Rectangle2D bounds = new TextLayout(text, font(), frc).getBounds();
            return new Dimension((int) bounds.getWidth() + 40, (int) bounds.getHeight() + 30);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            TextLayout layout = new TextLayout(text, font(), g2.getFontRenderContext());
            Rectangle2D bounds = layout.getBounds();
            double x = (getWidth() - bounds.getWidth()) / 2 - bounds.getX();
            double y = (getHeight() - bounds.getHeight()) / 2 - bounds.getY();
            Shape outline = layout.getOutline(AffineTransform.getTranslateInstance(x, y));
            // Comic look: a soft drop shadow, the neon glow, a gradient fill and a thin dark outline.
            g2.setColor(Theme.alpha(Theme.deepBackground(), 0.55));
            g2.fill(AffineTransform.getTranslateInstance(2.5, 3.5).createTransformedShape(outline));
            Theme.Glow.paintGlowStroke(g2, outline, Theme.neonPurple(), 1.2f, 0.55 + 0.35 * Theme.Motion.pulse(0.4));
            g2.setPaint(new GradientPaint((float) x, (float) (y + bounds.getY()), Theme.text(),
                    (float) x, (float) (y + bounds.getMaxY()), Theme.mix(Theme.neonCyan(), Theme.text(), 0.55)));
            g2.fill(outline);
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(Theme.alpha(Theme.deepBackground(), 0.6));
            g2.draw(outline);
            g2.dispose();
        }
    }

    /**
     * Translucent rounded card with a neon outline and LED border, used to group screen content.
     */
    public static final class Card extends JPanel {

        /**
         * @param layout layout for the card's children
         */
        public Card(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(30, 44, 30, 44));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Background.paintCard(g2, 4, 4, getWidth() - 8, getHeight() - 8);
            g2.dispose();
        }
    }

    /**
     * Pill-shaped toggle used for choices (player count, level, colors). A color chip shows a color dot next to the
     * color's name, so color is never the only cue (NFR-05).
     */
    public static final class ChoicePill extends JToggleButton {

        private final Color accent;
        private final Color dot;

        /**
         * @param text   label
         * @param accent highlight color when selected
         * @param dot    optional color dot shown before the label, or {@code null}
         */
        public ChoicePill(String text, Color accent, Color dot) {
            super(text);
            this.accent = accent;
            this.dot = dot;
            setFont(Theme.Fonts.BOLD);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            int left = dot == null ? 18 : 34;
            setBorder(BorderFactory.createEmptyBorder(9, left, 10, 18));
            addActionListener(e -> Assets.Sound.play(Assets.Sound.Effect.CLICK));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean on = isSelected();
            boolean enabled = isEnabled();
            double h = getHeight() - 6 - 3;
            RoundRectangle2D pill = new RoundRectangle2D.Double(3, 3, getWidth() - 6, h, h, h);
            // Neon chip: the chosen one glows filled, the others are outlined in the accent color.
            Theme.Comic.paintButton(g2, pill, accent, on, enabled, enabled && getModel().isRollover() ? 1 : 0, false);
            if (dot != null) {
                double r = 7;
                double cx = 18;
                double cy = 3 + h / 2;
                if (on) {
                    Theme.Glow.paintHalo(g2, cx, cy, r * 2.4, dot, 0.9);
                }
                g2.setColor(enabled ? dot : Theme.mix(dot, Theme.surface(), 0.35));
                g2.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            }
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(!enabled ? Theme.textMuted() : on ? Theme.Comic.textColorOn(accent) : Theme.text());
            // Centered on the letters really drawn, so tall Hebrew and Arabic letters are never cut.
            String text = getText();
            double y = 3 + h / 2;
            if (!text.isEmpty()) {
                y -= new java.awt.font.TextLayout(text, getFont(), g2.getFontRenderContext()).getBounds().getCenterY();
            }
            int x = getComponentOrientation().isLeftToRight() || dot != null ? getInsets().left
                    : getWidth() - getInsets().right - fm.stringWidth(text);
            g2.drawString(text, x, (float) y);
            g2.dispose();
        }
    }

    /**
     * A colorful card (admin login, main menu): a blue-purple-pink glow band at the top, soft color clouds and a frame
     * that glows from cyan through purple to pink, like the Panther Ludo logo.
     */
    public static final class GlowCard extends JPanel {

        private final double band;

        /**
         * @param layout layout for the card's children
         * @param band   height of the colored band at the top in pixels
         */
        public GlowCard(LayoutManager layout, double band) {
            super(layout);
            this.band = band;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(26, 44, 28, 44));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean light = Theme.mode() == Theme.Mode.LIGHT;
            int w = getWidth();
            int h = getHeight();
            java.awt.geom.RoundRectangle2D card = new java.awt.geom.RoundRectangle2D.Double(6, 6, w - 12, h - 12,
                    30, 30);
            Color cyan = new Color(0x2CE5FF);
            Color purple = new Color(0x8B5CF6);
            Color pink = new Color(0xFF4FB8);
            g2.setColor(light ? new Color(255, 255, 255, 235) : new Color(16, 18, 46, 235));
            g2.fill(card);
            Shape clip = g2.getClip();
            g2.clip(card);
            // The colored band at the top, with a soft wavy lower edge.
            java.awt.geom.Path2D wave = new java.awt.geom.Path2D.Double();
            wave.moveTo(0, 0);
            wave.lineTo(w, 0);
            wave.lineTo(w, band);
            wave.curveTo(w * 0.7, band * 1.27, w * 0.35, band * 0.8, 0, band * 1.1);
            wave.closePath();
            g2.setPaint(new LinearGradientPaint(0, 0, w, 0, new float[]{0f, 0.5f, 1f},
                    new Color[]{Theme.alpha(cyan, light ? 0.45 : 0.4), Theme.alpha(purple, light ? 0.4 : 0.38),
                            Theme.alpha(pink, light ? 0.45 : 0.4)}));
            g2.fill(wave);
            // Soft color clouds in the corners below.
            g2.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Double(0, h), w * 0.6f,
                    new float[]{0f, 1f}, new Color[]{Theme.alpha(cyan, 0.2), Theme.alpha(cyan, 0)}));
            g2.fillRect(0, 0, w, h);
            g2.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Double(w, h), w * 0.6f,
                    new float[]{0f, 1f}, new Color[]{Theme.alpha(pink, 0.2), Theme.alpha(pink, 0)}));
            g2.fillRect(0, 0, w, h);
            g2.setClip(clip);
            LinearGradientPaint edge = new LinearGradientPaint(0, 0, w, h, new float[]{0f, 0.5f, 1f},
                    new Color[]{cyan, purple, pink});
            for (int i = 4; i >= 1; i--) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f));
                g2.setPaint(edge);
                g2.setStroke(new BasicStroke(2f + i * 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(card);
            }
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setPaint(edge);
            g2.setStroke(new BasicStroke(2.4f));
            g2.draw(card);
            g2.dispose();
        }
    }

    /**
     * Compact rounded label with a thin neon outline, used for the top-bar clock, round counter and level.
     */
    public static final class PillLabel extends JLabel {

        private Color accent;
        /** Second color of a gradient tag (e.g. the Guest / Admin tag), or {@code null}. */
        private Color accent2;

        /**
         * @param text   initial text
         * @param accent outline color
         */
        public PillLabel(String text, Color accent) {
            super(text);
            this.accent = accent;
            setFont(Theme.Fonts.BOLD);
            setForeground(Theme.text());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        }

        @Override
        public void setText(String text) {
            super.setText(text);
        }

        /**
         * @param accent new outline color
         */
        public void setAccent(Color accent) {
            this.accent = accent;
            repaint();
        }

        /**
         * Makes this a glowing gradient tag with white letters.
         *
         * @param from left color
         * @param to   right color
         */
        public void setGradient(Color from, Color to) {
            this.accent = from;
            this.accent2 = to;
            setForeground(Color.WHITE);
            // The same comic letters as the main buttons.
            setFont(Theme.Fonts.comicFont(17f, getText()));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double h = getHeight() - 4;
            RoundRectangle2D pill = new RoundRectangle2D.Double(2, 2, getWidth() - 4, h, h, h);
            if (accent2 != null) {
                Theme.Glow.paintHalo(g2, pill.getCenterX(), pill.getCenterY(), pill.getWidth() * 0.6,
                        Theme.mix(accent, accent2, 0.5), 0.3);
                g2.setPaint(new GradientPaint(2, 0, accent, getWidth() - 2, 0, accent2));
                g2.fill(pill);
                g2.setPaint(new GradientPaint(0, 2, new Color(255, 255, 255, 80), 0, (float) (h * 0.55),
                        new Color(255, 255, 255, 0)));
                g2.fill(new RoundRectangle2D.Double(4, 3, getWidth() - 8, h * 0.5, h * 0.5, h * 0.5));
                g2.setColor(new Color(255, 255, 255, 170));
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(pill);
                g2.dispose();
                super.paintComponent(g);
                return;
            }
            // A bubbly pastel pill with an ink outline.
            g2.setColor(Theme.mode() == Theme.Mode.LIGHT ? Theme.mix(Theme.Comic.candyColor(accent), Color.WHITE, 0.22)
                    : Theme.mix(Theme.Comic.candyColor(accent), Theme.surface(), 0.22));
            g2.fill(pill);
            g2.setColor(Theme.Comic.inkColor());
            g2.setStroke(new BasicStroke(2.5f));
            g2.draw(pill);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Round icon button with the classic settings gear. The gear turns and glows while hovered.
     */
    public static final class GearButton extends JButton {

        private final Timer animation = new Timer(16, e -> step());
        private double hover;
        private double hoverTarget;

        /**
         * @param size diameter in pixels (at least 24 px, NFR-06)
         */
        public GearButton(int size) {
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(getPreferredSize());
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(Assets.Texts.get("menu.settings"));
            getAccessibleContext().setAccessibleName(Assets.Texts.get("menu.settings"));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hoverTarget = 1;
                    animation.start();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverTarget = 0;
                    animation.start();
                }
            });
        }

        /**
         * One animation step: the glow fades in while hovered and out afterwards; the gear itself never turns.
         */
        private void step() {
            hover += (hoverTarget - hover) * 0.2;
            if (hoverTarget == 0 && hover < 0.02) {
                hover = 0;
                animation.stop();
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double s = Math.min(getWidth(), getHeight()) - 6;
            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0;
            Ellipse2D disc = new Ellipse2D.Double(cx - s / 2, cy - s / 2, s, s);
            g2.setColor(Theme.alpha(Theme.surface(), 0.9));
            g2.fill(disc);
            Theme.Glow.paintGlowStroke(g2, disc, Theme.neonCyan(), 1.4f, 0.35 + 0.65 * hover);
            Theme.Glow.paintHalo(g2, cx, cy, s * 0.5, Theme.neonCyan(), 0.25 + 0.5 * hover);
            Theme.drawIcon(g2, "icon_settings", cx, cy, s * 0.72);
            g2.dispose();
        }
    }
}
