package view;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.util.Arrays;

/**
 * The neon look shared by all screens, in one place: {@link Button}, {@link TextField}, {@link PasswordField},
 * {@link Title}, the animated {@link Background} and the content {@link Card}. Each component is its own nested
 * class because each one extends a different Swing class (JButton, JTextField, JPanel, ...); this outer class only
 * groups them and is never created.
 */
public final class Neon {

    /**
     * Only nested component classes; no objects of this class are created.
     */
    private Neon() {
    }

    /**
     * Animated night background shared by the menu screens: dark purple vignette with slowly drifting LED "fireflies".
     * Also offers {@link #paintCard} for the glowing content cards.
     */
    public static class Background extends JPanel {

        private static final int PARTICLES = 42;
        private final double[][] particles = new double[PARTICLES][4];

        /**
         * @param layout layout for the panel's children
         */
        public Background(LayoutManager layout) {
            super(layout);
            setOpaque(true);
            setBackground(UiTheme.background());
            java.util.Random seeds = new java.util.Random(7);
            for (double[] p : particles) {
                p[0] = seeds.nextDouble();
                p[1] = seeds.nextDouble();
                p[2] = 0.01 + seeds.nextDouble() * 0.025;
                p[3] = seeds.nextDouble() * 10;
            }
            Animator.subscribe(this);
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
                    new Color[]{UiTheme.vignette(), UiTheme.background(), UiTheme.bgDeep()},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE));
            g2.fillRect(0, 0, w, h);

            double t = Animator.seconds();
            Color[] particleColors = {UiTheme.neonPurple(), UiTheme.neonCyan(), UiTheme.pantherGold()};
            for (int i = 0; i < PARTICLES; i++) {
                double[] p = particles[i];
                double y = (p[1] - t * p[2]) % 1.0;
                if (y < 0) {
                    y += 1;
                }
                double x = p[0] + 0.015 * Math.sin(t * 0.6 + p[3]);
                double twinkle = 0.25 + 0.55 * (0.5 + 0.5 * Math.sin(t * 1.7 + p[3] * 3));
                Glow.led(g2, x * w, y * h, 2, particleColors[i % particleColors.length], twinkle);
            }
            g2.dispose();
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
            RoundRectangle2D card = new RoundRectangle2D.Double(x, y, width, height, 28, 28);
            g2.setColor(UiTheme.alpha(UiTheme.surface(), 0.92));
            g2.fill(card);
            Glow.stroke(g2, card, UiTheme.neonPurple(), 1.4f, 0.5);
            Glow.ledStrip(g2, new java.awt.geom.Rectangle2D.Double(x + 18, y + 6, width - 36, height - 12), 26, 2,
                    new Color[]{UiTheme.neonPurple(), UiTheme.neonCyan()}, 0.12);
        }

    }

    /**
     * Rounded neon button with an animated hover glow. "Primary" buttons are filled with the accent color; secondary
     * buttons are dark with a glowing outline. Keyboard focus is shown with a bright ring.
     */
    public static class Button extends JButton {

        private Color accent;
        private final boolean primary;
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
            setFont(UiTheme.BUTTON);
            setForeground(UiTheme.text());
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(12, 28, 12, 28));
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
         * @param accent new neon color (e.g. the current player's color)
         */
        public void setAccent(Color accent) {
            this.accent = accent;
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
            double inset = 7;
            double arc = Math.min(getHeight() - 2 * inset, 22);
            RoundRectangle2D shape = new RoundRectangle2D.Double(inset, inset + (pressed ? 1 : 0),
                    getWidth() - 2 * inset, getHeight() - 2 * inset, arc, arc);
            Color base = enabled ? accent : UiTheme.grid();
            double glow = enabled ? Math.min(1, 0.35 + 0.65 * hover + extraGlow()) : 0;

            if (primary && enabled) {
                g2.setPaint(new GradientPaint(0, (float) shape.getY(),
                        UiTheme.mix(base, Color.WHITE, 0.85 + 0.15 * (1 - hover)),
                        0, (float) shape.getMaxY(), UiTheme.mix(base, UiTheme.bgDeep(), 0.78)));
            } else {
                g2.setColor(UiTheme.mix(base, UiTheme.surface(), 0.08 + 0.12 * hover));
            }
            g2.fill(shape);
            if (enabled) {
                Glow.stroke(g2, shape, base, 1.6f, glow);
            } else {
                g2.setColor(UiTheme.grid());
                g2.draw(shape);
            }
            if (isFocusOwner() && enabled) {
                g2.setColor(UiTheme.text());
                g2.draw(new RoundRectangle2D.Double(shape.getX() + 3, shape.getY() + 3, shape.getWidth() - 6,
                        shape.getHeight() - 6, arc - 4, arc - 4));
            }

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String text = getText();
            Color textColor = !enabled ? UiTheme.textMuted() : primary ? UiTheme.textOnNeon() : UiTheme.text();
            g2.setColor(textColor);
            g2.drawString(text, (float) (getWidth() - fm.stringWidth(text)) / 2,
                    (float) (shape.getCenterY() + (fm.getAscent() - fm.getDescent()) / 2.0));
            g2.dispose();
        }
    }

    /**
     * Rounded text field in the neon style. Its outline lights up in the accent color while focused, a grey placeholder
     * hint shows while it is empty, and it can be marked invalid, which draws a red outline (NFR-07).
     */
    public static final class TextField extends JTextField {

        private final String placeholder;
        private Color accent = UiTheme.neonPurple();
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
            field.setFont(UiTheme.BASE);
            field.setForeground(UiTheme.text());
            field.setCaretColor(UiTheme.text());
            field.setSelectionColor(UiTheme.mix(UiTheme.neonPurple(), UiTheme.surface(), 0.5));
            field.setSelectedTextColor(UiTheme.text());
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
                    14, 14);
            g2.setColor(field.isEnabled() ? UiTheme.surfaceHi() : UiTheme.surface());
            g2.fill(box);
            if (invalid) {
                Glow.stroke(g2, box, UiTheme.error(), 1.5f, field.hasFocus() ? 0.7 : 0.4);
            } else if (field.hasFocus()) {
                Glow.stroke(g2, box, accent, 1.5f, 0.7);
            } else {
                g2.setColor(UiTheme.grid());
                g2.draw(box);
            }
            if (field.getDocument().getLength() == 0 && !field.hasFocus() && placeholder != null) {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(field.getFont());
                g2.setColor(UiTheme.textMuted());
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
            TextField.paintFieldBackground(g, this, UiTheme.neonPurple(), false, placeholder);
            super.paintComponent(g);
        }
    }

    /**
     * Large neon heading: gradient-filled text with a softly pulsing purple glow.
     */
    public static final class Title extends JComponent {

        private String text;
        private final Font font;

        /**
         * @param text title text
         * @param size font size in points
         */
        public Title(String text, float size) {
            this.text = text;
            this.font = UiTheme.font(size, true);
            setOpaque(false);
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
            Rectangle2D bounds = new TextLayout(text, font, frc).getBounds();
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
            TextLayout layout = new TextLayout(text, font, g2.getFontRenderContext());
            Rectangle2D bounds = layout.getBounds();
            double x = (getWidth() - bounds.getWidth()) / 2 - bounds.getX();
            double y = (getHeight() - bounds.getHeight()) / 2 - bounds.getY();
            Shape outline = layout.getOutline(AffineTransform.getTranslateInstance(x, y));
            Glow.stroke(g2, outline, UiTheme.neonPurple(), 1.2f, 0.55 + 0.35 * Animator.pulse(0.4));
            g2.setPaint(new GradientPaint((float) x, 0, UiTheme.text(),
                    (float) (x + bounds.getWidth()), 0, UiTheme.mix(UiTheme.neonCyan(), UiTheme.text(), 0.6)));
            g2.fill(outline);
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
}
