package view;

import model.PieceColor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.function.Consumer;

/**
 * Pill button showing a player's color (dot + name, NFR-05). Clicking opens a palette of named colors plus
 * "Custom color…", which accepts any color at all (TD-08).
 */
public final class ColorPickerButton extends JButton {

    private PieceColor color;
    private Consumer<PieceColor> onPick = c -> { };

    /**
     * @param color initial color
     */
    public ColorPickerButton(PieceColor color) {
        setFont(UiTheme.BOLD);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(10, 38, 10, 32));
        setToolTipText("Choose any color");
        addActionListener(e -> showPalette());
        setColor(color);
    }

    /**
     * @param color color to show
     */
    public void setColor(PieceColor color) {
        this.color = color;
        setText(color.name());
        revalidate();
        repaint();
    }

    /**
     * @return the shown color
     */
    public PieceColor color() {
        return color;
    }

    /**
     * @param handler called with the color the user picked
     */
    public void setOnPick(Consumer<PieceColor> handler) {
        this.onPick = handler;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(170, d.width), Math.max(44, d.height));
    }

    /**
     * Opens the pop-up with the ready-made colors and a "More colors" option for any custom color (TD-08).
     */
    private void showPalette() {
        JPopupMenu popup = new JPopupMenu();
        popup.setBackground(UiTheme.surface());
        popup.setBorder(BorderFactory.createLineBorder(UiTheme.neonPurple(), 1));
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(UiTheme.surface());
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel title = new JLabel("Pick a color");
        title.setFont(UiTheme.SMALL);
        title.setForeground(UiTheme.textMuted());
        content.add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 4, 6, 6));
        grid.setOpaque(false);
        for (PieceColor preset : PieceColor.PRESETS) {
            grid.add(new SwatchTile(preset, preset.rgb() == color.rgb(), () -> {
                popup.setVisible(false);
                pick(preset);
            }));
        }
        content.add(grid, BorderLayout.CENTER);

        Neon.Button custom = new Neon.Button("Custom color...", UiTheme.neonCyan(), false);
        custom.setFont(UiTheme.SMALL);
        custom.addActionListener(e -> {
            popup.setVisible(false);
            Color chosen = JColorChooser.showDialog(this, "Pick any color", UiTheme.colorOf(color));
            if (chosen != null) {
                pick(PieceColor.of(chosen.getRGB()));
            }
        });
        content.add(custom, BorderLayout.SOUTH);
        popup.add(content);
        Point below = new Point(0, getHeight());
        popup.show(this, below.x, below.y);
    }

    /**
     * Shows the chosen color and tells the setup screen.
     */
    private void pick(PieceColor picked) {
        setColor(picked);
        onPick.accept(picked);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color swing = UiTheme.colorOf(color);
        Color neon = UiTheme.neon(swing);
        double h = getHeight() - 6;
        RoundRectangle2D pill = new RoundRectangle2D.Double(3, 3, getWidth() - 6, h, h, h);
        g2.setColor(UiTheme.mix(neon, UiTheme.surfaceHi(), 0.14));
        g2.fill(pill);
        Glow.stroke(g2, pill, neon, 1.5f, getModel().isRollover() ? 0.9 : 0.55);
        if (isFocusOwner()) {
            g2.setColor(UiTheme.text());
            g2.draw(new RoundRectangle2D.Double(6, 6, getWidth() - 12, h - 6, h - 6, h - 6));
        }
        paintDot(g2, 20, 3 + h / 2, 9, swing);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(UiTheme.text());
        g2.drawString(getText(), getInsets().left, (float) (3 + h / 2 + (fm.getAscent() - fm.getDescent()) / 2.0));
        double ax = getWidth() - 20;
        double ay = 3 + h / 2;
        Path2D chevron = new Path2D.Double();
        chevron.moveTo(ax - 5, ay - 2);
        chevron.lineTo(ax, ay + 3);
        chevron.lineTo(ax + 5, ay - 2);
        g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g2.setColor(UiTheme.textMuted());
        g2.draw(chevron);
        g2.dispose();
    }

    /**
     * Glossy color ball.
     */
    static void paintDot(Graphics2D g2, double cx, double cy, double r, Color color) {
        Glow.halo(g2, cx, cy, r * 2.2, UiTheme.neon(color), 0.6);
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(cx - r * 0.35, cy - r * 0.4), (float) (r * 1.5),
                new float[]{0f, 0.6f, 1f},
                new Color[]{UiTheme.mix(color, Color.WHITE, 0.5), color, UiTheme.mix(color, Color.BLACK, 0.6)}));
        g2.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g2.setColor(UiTheme.alpha(UiTheme.neon(color), 0.8));
        g2.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    /** One palette entry: glossy dot with the color name below. */
    private static final class SwatchTile extends JComponent {
        private final PieceColor color;
        private final boolean selected;
        private boolean hover;
        SwatchTile(PieceColor color, boolean selected, Runnable onClick) {
            this.color = color;
            this.selected = selected;
            setPreferredSize(new Dimension(70, 62));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(color.name());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onClick.run();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color swing = UiTheme.colorOf(color);
            RoundRectangle2D tile = new RoundRectangle2D.Double(1, 1, getWidth() - 2, getHeight() - 2, 12, 12);
            if (hover || selected) {
                g2.setColor(UiTheme.surfaceHi());
                g2.fill(tile);
                Glow.stroke(g2, tile, UiTheme.neon(swing), 1.2f, selected ? 0.9 : 0.5);
            }
            paintDot(g2, getWidth() / 2.0, 22, 13, swing);
            g2.setFont(UiTheme.SMALL);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(UiTheme.text());
            g2.drawString(color.name(), (getWidth() - fm.stringWidth(color.name())) / 2f, getHeight() - 8f);
            g2.dispose();
        }
    }
}
