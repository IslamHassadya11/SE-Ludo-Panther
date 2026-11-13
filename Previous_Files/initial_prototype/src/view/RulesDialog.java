package view;

import model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.*;

/**
 * The Rules window (FR-07): a modal window with the scrollable {@link RulesPanel} and a Close button. It is opened
 * from the game screen and from Settings. Because it is modal, {@link #show(Component)} returns only after the
 * window is closed, which lets the game controller pause the turn timer meanwhile (FR-30).
 * <p>The window is 900×620 pixels, so it fits the smallest supported screen of 1280×720 (NFR-01).
 */
public final class RulesDialog extends JDialog {

    /**
     * Builds the window: title, scrollable rules and a Close button (Esc also closes).
     */
    private RulesDialog(Window owner) {
        super(owner, "Panther Ludo - Rules", ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiTheme.background());

        JPanel header = new JPanel(new FlowLayout(FlowLayout.CENTER));
        header.setOpaque(false);
        header.add(new Neon.Title("GAME RULES", 30));
        root.add(header, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(new RulesPanel());
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UiTheme.background());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        root.add(scroll, BorderLayout.CENTER);

        Neon.Button close = new Neon.Button("Close", UiTheme.neonPurple(), true);
        close.addActionListener(e -> dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 6));
        footer.setOpaque(false);
        footer.add(close);
        root.add(footer, BorderLayout.SOUTH);

        // Esc closes the window, like the Close button.
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close");
        root.getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        setContentPane(root);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(new Dimension(900, 620));
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(close);
    }

    /**
     * Opens the Rules window over the given screen and waits until the player closes it.
     *
     * @param parent any component of the window the rules belong to
     */
    public static void show(Component parent) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        new RulesDialog(owner).setVisible(true);
    }

    /**
     * The content of the Rules window (FR-07): every rule as a short text with a picture beside it, plus a legend of
     * all square types (FR-18). The texts follow the SRS; rules that only apply on Medium and Hard (questions,
     * surprises, the multiplier die) say so.
     * <p>To change a rule text, edit the {@code section(...)} calls in the constructor; each one is a heading, the
     * paragraph(s) and the picture to show.
     */
    private static final class RulesPanel extends JPanel implements Scrollable {

        /** Width of the text column, in pixels; HTML labels wrap at this width. */
        private static final int TEXT_WIDTH = 520;

        /**
         * Builds the whole rules page.
         */
        public RulesPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(UiTheme.background());
            setBorder(BorderFactory.createEmptyBorder(8, 22, 18, 22));

            section("Goal of the game", RulePicture.Kind.GOAL,
                    "Every player has four panthers that start in their castle. Move all four around the board "
                            + "and into "
                            + "the golden center before the others.",
                    "When a player brings their fourth panther home, they get the next place in the ranking. The game "
                            + "ends when only one player is left; that player comes last.");

            section("Taking turns", RulePicture.Kind.TURN_ORDER,
                    "The first player is picked at random. After that, the turn passes clockwise around the board.",
                    "Press <b>Roll Dice</b> (or the Space key) to roll. The top bar shows the game time and the round "
                            + "number; the list on the right shows the turn order and how many panthers each player "
                            + "has brought home.");

            section("The dice", RulePicture.Kind.DICE,
                    "<b>Easy:</b> one step die (1–6). The number is how many squares you move.",
                    "<b>Medium:</b> step die + Boost die (1–3). <b>Hard:</b> step die + UltraBoost die (1–6). "
                            + "Steps = step die × (other die − 1), so a 1 on the other die means no move. Inside your "
                            + "home lane only the step die counts.");

            section("Rolling a 6", RulePicture.Kind.SIX,
                    "A 6 on the step die lets you bring a panther out of the castle onto your start square (#), "
                            + "or move a panther that is already on the board.",
                    "After a 6 you roll again, even if no panther could move. A third 6 in a row is cancelled and the "
                            + "turn passes to the next player.");

            section("Moving", RulePicture.Kind.MOVE,
                    "After rolling, the panthers that can move glow. Click one of them. You must use the whole roll "
                            + "with one panther; you cannot skip or split it.",
                    "If there is only one possible move, the panther moves by itself. If no panther can move, a short "
                            + "message appears and the turn passes.");

            section("Capturing", RulePicture.Kind.CAPTURE,
                    "Land on a square where one opponent panther stands and it goes back to its castle. It needs a "
                            + "6 to come out again.",
                    "Nobody can be captured on a protected square (start #, star, surprise, home lane). Capturing does "
                            + "not give an extra roll.");

            section("Double Block", RulePicture.Kind.BLOCK,
                    "Two or more of your panthers on the same square form a Double Block. Opponents cannot land on it "
                            + "or move past it, forwards or backwards. Your own panthers can.",
                    "If a block is in the way and no other panther can use the whole roll, the panther stops on the "
                            + "square right before the block.");

            section("Going home", RulePicture.Kind.HOME,
                    "When a panther reaches the arrow square in its own color, it turns into its colored home lane. "
                            + "Arrows of other colors are normal squares for it.",
                    "To reach the center you need the exact number. A roll that is too big cannot be used by that "
                            + "panther; move another one instead.");

            add(heading("Squares on the board"));
            add(legend());
            add(Box.createVerticalStrut(18));

            section("Question squares  (Medium and Hard)", RulePicture.Kind.QUESTION,
                    "Stop on a ? square and a question with four answers appears for everyone to see. Only the player "
                            + "whose turn it is answers; there is no time limit.",
                    "Right answer: move forward 1, 2 or 3 squares (easy, medium or hard question). Wrong answer: move "
                            + "back the same number. The normal capture and block rules apply.");

            section("Surprise squares  (Medium and Hard)", RulePicture.Kind.SURPRISE,
                    "Stop on a ! square and a random surprise happens:",
                    "• 10% jump straight to the center<br>"
                            + "• 10% move an opponent's panther back (half a step-die roll, rounded up)<br>"
                            + "• 10% back to your castle<br>"
                            + "• 30% move back 3 squares<br>"
                            + "• 30% lose your next turn<br>"
                            + "• 10% an extra roll with the UltraBoost die");

            section("Turn timer", RulePicture.Kind.TIMER,
                    "If you do not roll or move for 30 seconds, a countdown appears under your panther.",
                    "After 60 seconds without a roll or move, your turn passes and any unused roll is lost. The timer "
                            + "stops while this Rules window is open.");
        }

        /**
         * Adds one rule: heading on top, picture on the left, text on the right.
         *
         * @param title      heading of the rule
         * @param picture    drawing that illustrates it
         * @param paragraphs one or more paragraphs; simple HTML such as {@code <b>} is allowed
         */
        private void section(String title, RulePicture.Kind picture, String... paragraphs) {
            add(heading(title));
            JPanel row = new JPanel(new BorderLayout(18, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel pictureHolder = new JPanel(new BorderLayout());
            pictureHolder.setOpaque(false);
            pictureHolder.add(new RulePicture(picture), BorderLayout.NORTH);
            row.add(pictureHolder, BorderLayout.WEST);
            row.add(text(String.join("<br><br>", paragraphs)), BorderLayout.CENTER);
            add(row);
            add(Box.createVerticalStrut(18));
        }

        /**
         * Legend of every square type with its icon (FR-18), in two columns.
         */
        private JComponent legend() {
            JPanel grid = new JPanel(new GridLayout(0, 2, 18, 8));
            grid.setOpaque(false);
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            grid.add(legendRow(SquareType.REGULAR, "Normal square", "Panthers can be captured here."));
            grid.add(legendRow(SquareType.START, "Start (#)", "Where your panthers enter. Protected."));
            grid.add(legendRow(SquareType.STAR, "Star", "Protected: no capturing here."));
            grid.add(legendRow(SquareType.ARROW, "Arrow", "Your color: turn into your home lane."));
            grid.add(legendRow(SquareType.HOME_LANE, "Home lane", "Only for its own color. Protected."));
            grid.add(legendRow(SquareType.FINISH, "Center", "The goal. Needs the exact number."));
            grid.add(legendRow(SquareType.QUESTION, "Question (?)", "Answer to move forward. Medium/Hard."));
            grid.add(legendRow(SquareType.SURPRISE, "Surprise (!)", "Random event. Protected. Medium/Hard."));
            grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
            return grid;
        }

        /**
         * One legend entry: square icon, name and meaning.
         */
        private static JLabel legendRow(SquareType type, String name, String meaning) {
            JLabel label = new JLabel("<html><b>" + name + "</b><br>" + meaning + "</html>",
                    new RulePicture.SquareIcon(type),
                    JLabel.LEFT);
            label.setIconTextGap(12);
            label.setFont(UiTheme.BASE);
            label.setForeground(UiTheme.text());
            return label;
        }

        /**
         * A section heading.
         */
        private static JLabel heading(String value) {
            JLabel label = new JLabel(value);
            label.setFont(UiTheme.HEADING);
            label.setForeground(UiTheme.neonCyan());
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            // A little room on the right: bold text can be a pixel wider than measured, which would clip the last
            // letter.
            label.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 6));
            return label;
        }

        /**
         * A paragraph that wraps at the text-column width.
         */
        private static JLabel text(String html) {
            // The width attribute is in real pixels; CSS "px" would be scaled and overflow the column.
            JLabel label = new JLabel("<html><body width='" + TEXT_WIDTH + "'>" + html + "</body></html>");
            label.setFont(UiTheme.BASE);
            label.setForeground(UiTheme.text());
            label.setVerticalAlignment(JLabel.TOP);
            return label;
        }

        // ------------------------------------------------------------------ Scrollable: scroll vertically only
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(24, visibleRect.height - 48);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /**
     * A small drawing that explains one rule in the Rules window (FR-07). Every picture is painted with Java 2D from
     * the
     * same panther pieces and square styles as the real board, so it always matches the current dark or light theme and
     * needs no image files.
     * <p>Each {@link Kind} has its own {@code paint...} method below. Coordinates are in pixels inside a
     * {@value #WIDTH}×{@value #HEIGHT} box.
     */
    private static final class RulePicture extends JComponent {

        /** Width of every picture in pixels. */
        static final int WIDTH = 240;
        /** Height of every picture in pixels. */
        static final int HEIGHT = 130;

        /** Sample player colors used in the pictures. */
        private static final PieceColor GREEN = PieceColor.PRESETS.get(0);
        private static final PieceColor YELLOW = PieceColor.PRESETS.get(1);
        private static final PieceColor BLUE = PieceColor.PRESETS.get(2);
        private static final PieceColor RED = PieceColor.PRESETS.get(3);

        /** Size of one board square in the pictures. */
        private static final double TILE = 30;

        /**
         * The rule a picture explains.
         */
        enum Kind {
            /** Castle → track → center: bring all four panthers home. */
            GOAL,
            /** Four corners with a clockwise arrow. */
            TURN_ORDER,
            /** Step die and multiplier die with the step formula. */
            DICE,
            /** A 6 lets a panther leave the castle onto its start square. */
            SIX,
            /** A panther moves forward by the roll. */
            MOVE,
            /** Landing on a lone opponent sends it back to its castle. */
            CAPTURE,
            /** Two panthers on one square stop an opponent. */
            BLOCK,
            /** Own arrow → home lane → center, exact roll needed. */
            HOME,
            /** A question card with four answers. */
            QUESTION,
            /** A surprise square bursting. */
            SURPRISE,
            /** The countdown ring of the inactivity timer. */
            TIMER
        }

        private final Kind kind;

        /**
         * @param kind which rule to draw
         */
        RulePicture(Kind kind) {
            this.kind = kind;
            setOpaque(false);
            Dimension size = new Dimension(WIDTH, HEIGHT);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // A soft card behind every picture so the drawings stand out from the page.
            RoundRectangle2D card = new RoundRectangle2D.Double(1, 1, WIDTH - 2, HEIGHT - 2, 18, 18);
            g2.setColor(UiTheme.bgDeep());
            g2.fill(card);
            g2.setColor(UiTheme.grid());
            g2.setStroke(new BasicStroke(1f));
            g2.draw(card);
            switch (kind) {
                case GOAL -> paintGoal(g2);
                case TURN_ORDER -> paintTurnOrder(g2);
                case DICE -> paintDice(g2);
                case SIX -> paintSix(g2);
                case MOVE -> paintMove(g2);
                case CAPTURE -> paintCapture(g2);
                case BLOCK -> paintBlock(g2);
                case HOME -> paintHome(g2);
                case QUESTION -> paintQuestion(g2);
                case SURPRISE -> paintSurprise(g2);
                case TIMER -> paintTimer(g2);
            }
            g2.dispose();
        }

        // ------------------------------------------------------------------ one method per picture

        /**
         * Castle with panthers, a few squares and the golden center.
         */
        private void paintGoal(Graphics2D g2) {
            castle(g2, 14, 30, 62, 62, GREEN, 3);
            for (int i = 0; i < 3; i++) {
                tile(g2, 86 + i * TILE, 50, UiTheme.surfaceHi());
            }
            // The center of the board: four colored triangles meeting in the middle.
            double cx = 206;
            double cy = 65;
            double r = 26;
            PieceColor[] corners = {GREEN, YELLOW, BLUE, RED};
            for (int i = 0; i < 4; i++) {
                Path2D triangle = new Path2D.Double();
                triangle.moveTo(cx, cy);
                triangle.lineTo(cx + r * Math.cos(Math.PI / 4 + i * Math.PI / 2 + Math.PI),
                        cy + r * Math.sin(Math.PI / 4 + i * Math.PI / 2 + Math.PI));
                triangle.lineTo(cx + r * Math.cos(Math.PI / 4 + (i + 1) * Math.PI / 2 + Math.PI),
                        cy + r * Math.sin(Math.PI / 4 + (i + 1) * Math.PI / 2 + Math.PI));
                triangle.closePath();
                g2.setColor(UiTheme.mix(UiTheme.neonOf(corners[i]), UiTheme.bgDeep(), 0.6));
                g2.fill(triangle);
            }
            Glow.halo(g2, cx, cy, 20, UiTheme.pantherGold(), 0.8);
            piece(g2, cx, cy - 2, 24, GREEN);
            curvedArrow(g2, 46, 26, cx, 34, -26, UiTheme.pantherGold());
        }

        /**
         * One panther in each corner, numbered in clockwise order.
         */
        private void paintTurnOrder(Graphics2D g2) {
            double cx = WIDTH / 2.0;
            double cy = HEIGHT / 2.0;
            // A ring of arrows going clockwise.
            g2.setColor(UiTheme.alpha(UiTheme.neonCyan(), 0.8));
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 4; i++) {
                double start = 90 - i * 90 - 18;
                g2.draw(new Arc2D.Double(cx - 34, cy - 34, 68, 68, start, -54, Arc2D.OPEN));
                double end = Math.toRadians(start - 54);
                arrowHead(g2, cx + 34 * Math.cos(end), cy - 34 * Math.sin(end), end - Math.PI / 2,
                        UiTheme.neonCyan());
            }
            // One panther in each corner, in clockwise seat order starting top-left.
            piece(g2, cx - 76, cy - 34, 34, GREEN);
            piece(g2, cx + 76, cy - 34, 34, YELLOW);
            piece(g2, cx + 76, cy + 32, 34, BLUE);
            piece(g2, cx - 76, cy + 32, 34, RED);
            label(g2, "1", cx - 100, cy - 44, UiTheme.text());
            label(g2, "2", cx + 100, cy - 44, UiTheme.text());
            label(g2, "3", cx + 100, cy + 22, UiTheme.text());
            label(g2, "4", cx - 100, cy + 22, UiTheme.text());
        }

        /**
         * Step die × multiplier die with the worked formula.
         */
        private void paintDice(Graphics2D g2) {
            die(g2, 16, 24, 44, 4, UiTheme.neonPurple());
            label(g2, "×", 68, 52, UiTheme.text());
            numberDie(g2, 84, 24, 44, "3");
            label(g2, "4 × (3 − 1)", 140, 42, UiTheme.text());
            label(g2, "= 8 steps", 140, 66, UiTheme.pantherGold());
            smallText(g2, "Easy: step die only", 16, 100);
            smallText(g2, "Medium/Hard: step × (other − 1)", 16, 118);
        }

        /**
         * A 6 brings a panther from the castle onto its start square.
         */
        private void paintSix(Graphics2D g2) {
            die(g2, 14, 14, 40, 6, UiTheme.neonPurple());
            castle(g2, 14, 62, 56, 56, GREEN, 1);
            tile(g2, 92, 74, UiTheme.mix(UiTheme.neonOf(GREEN), UiTheme.surfaceHi(), 0.38));
            centeredText(g2, "#", 92 + TILE / 2, 74 + TILE / 2);
            piece(g2, 92 + TILE / 2, 74 + TILE / 2 - 4, 26, GREEN);
            curvedArrow(g2, 52, 74, 96, 76, -22, UiTheme.pantherGold());
            label(g2, "+1 roll", 150, 40, UiTheme.pantherGold());
            smallText(g2, "3rd 6 in a row:", 136, 100);
            smallText(g2, "cancelled", 136, 116);
        }

        /**
         * A panther moves four squares for a roll of 4.
         */
        private void paintMove(Graphics2D g2) {
            double y = 70;
            for (int i = 0; i < 6; i++) {
                tile(g2, 20 + i * (TILE + 4), y, UiTheme.surfaceHi());
            }
            die(g2, 96, 12, 34, 4, UiTheme.neonPurple());
            // Ghost of the start position, real panther after four steps.
            ghost(g2, 20 + TILE / 2, y + TILE / 2 - 4, 26, BLUE);
            piece(g2, 20 + 4 * (TILE + 4) + TILE / 2, y + TILE / 2 - 4, 26, BLUE);
            curvedArrow(g2, 20 + TILE / 2, y - 2, 20 + 4 * (TILE + 4) + TILE / 2, y - 2, -24, UiTheme.neonCyan());
            smallText(g2, "1   2   3   4", 62, 120);
        }

        /**
         * A green panther lands on a red one, which flies back to its castle.
         */
        private void paintCapture(Graphics2D g2) {
            double y = 78;
            for (int i = 0; i < 4; i++) {
                tile(g2, 14 + i * (TILE + 4), y, UiTheme.surfaceHi());
            }
            castle(g2, 176, 14, 50, 50, RED, 1);
            ghost(g2, 14 + TILE / 2, y + TILE / 2 - 4, 26, GREEN);
            piece(g2, 14 + 3 * (TILE + 4) + TILE / 2, y + TILE / 2 - 4, 26, GREEN);
            curvedArrow(g2, 14 + TILE / 2, y - 2, 14 + 3 * (TILE + 4) + TILE / 2 - 6, y - 4, -22, UiTheme.neonCyan());
            // The captured red panther flies back to its castle.
            curvedArrow(g2, 14 + 3 * (TILE + 4) + TILE / 2 + 10, y + 4, 182, 54, -20, UiTheme.error());
        }

        /**
         * Two blue panthers form a block; the red panther must stop before it.
         */
        private void paintBlock(Graphics2D g2) {
            double y = 74;
            for (int i = 0; i < 5; i++) {
                tile(g2, 18 + i * (TILE + 12), y, UiTheme.surfaceHi());
            }
            double blockX = 18 + 3 * (TILE + 12) + TILE / 2;
            piece(g2, blockX - 5, y + TILE / 2 - 8, 24, BLUE);
            piece(g2, blockX + 5, y + TILE / 2 - 2, 24, BLUE);
            piece(g2, 18 + TILE / 2, y + TILE / 2 - 4, 26, RED);
            curvedArrow(g2, 18 + TILE / 2, y - 2, blockX - 26, y - 2, -24, UiTheme.neonCyan());
            cross(g2, blockX, y - 24, 9);
            smallText(g2, "Double Block: no landing, no passing", 18, 122);
        }

        /**
         * A panther in its home lane needs exactly 2 to reach the center.
         */
        private void paintHome(Graphics2D g2) {
            double y = 50;
            Color lane = UiTheme.lane(UiTheme.colorOf(GREEN));
            tile(g2, 12, y, UiTheme.mix(UiTheme.neonOf(GREEN), UiTheme.surfaceHi(), 0.28));
            arrowHead(g2, 12 + TILE / 2 + 7, y + TILE / 2, 0, UiTheme.neonOf(GREEN));
            for (int i = 0; i < 5; i++) {
                tile(g2, 12 + (i + 1) * (TILE + 2), y, lane);
            }
            double homeX = 12 + 6 * (TILE + 2) + TILE / 2;
            Glow.halo(g2, homeX, y + TILE / 2, 22, UiTheme.pantherGold(), 0.8);
            g2.setColor(UiTheme.pantherGold());
            g2.fill(new Ellipse2D.Double(homeX - 6, y + TILE / 2 - 6, 12, 12));
            double pieceX = 12 + 4 * (TILE + 2) + TILE / 2;
            piece(g2, pieceX, y + TILE / 2 - 4, 26, GREEN);
            curvedArrow(g2, pieceX, y - 2, homeX, y - 2, -18, UiTheme.pantherGold());
            die(g2, pieceX + 4, 92, 26, 2, UiTheme.pantherGold());
            smallText(g2, "exactly 2 →", 64, 110);
        }

        /**
         * A question square with a four-answer card.
         */
        private void paintQuestion(Graphics2D g2) {
            tile(g2, 18, 50, UiTheme.surfaceHi());
            centeredText(g2, "?", 18 + TILE / 2, 50 + TILE / 2);
            RoundRectangle2D card = new RoundRectangle2D.Double(66, 10, 160, 86, 14, 14);
            g2.setColor(UiTheme.surface());
            g2.fill(card);
            Glow.stroke(g2, card, UiTheme.neonCyan(), 1.4f, 0.4);
            smallText(g2, "Which pattern...?", 78, 30);
            String[] answers = {"A", "B", "C", "D"};
            for (int i = 0; i < 4; i++) {
                double x = 78 + (i % 2) * 72;
                double y = 40 + (i / 2) * 26;
                RoundRectangle2D option = new RoundRectangle2D.Double(x, y, 64, 20, 10, 10);
                g2.setColor(i == 1 ? UiTheme.alpha(UiTheme.neonOf(GREEN), 0.35) : UiTheme.surfaceHi());
                g2.fill(option);
                smallText(g2, answers[i], x + 28, y + 15);
            }
            smallText(g2, "right: +1/2/3   wrong: −1/2/3", 66, 116);
        }

        /**
         * A surprise square bursting with light.
         */
        private void paintSurprise(Graphics2D g2) {
            double cx = WIDTH / 2.0;
            double cy = HEIGHT / 2.0 - 4;
            // Rays bursting out of the "!" square.
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Color[] rays = {UiTheme.pantherGold(), UiTheme.neonPurple(), UiTheme.neonCyan()};
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                g2.setColor(rays[i % rays.length]);
                g2.draw(new Line2D.Double(cx + 26 * Math.cos(a), cy + 26 * Math.sin(a),
                        cx + (i % 2 == 0 ? 46 : 38) * Math.cos(a), cy + (i % 2 == 0 ? 46 : 38) * Math.sin(a)));
            }
            tile(g2, cx - TILE / 2, cy - TILE / 2, UiTheme.surfaceHi());
            centeredText(g2, "!", cx, cy);
            smallText(g2, "one of 6 random events", cx - 70, 122);
        }

        /**
         * The countdown ring and the two timer limits.
         */
        private void paintTimer(Graphics2D g2) {
            double cx = 70;
            double cy = 62;
            double r = 38;
            g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(UiTheme.alpha(UiTheme.warning(), 0.2));
            g2.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            Glow.stroke(g2, new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 90, 360 * 0.6, Arc2D.OPEN),
                    UiTheme.warning(), 6f, 0.6);
            PantherPainter.paintHead(g2, cx, cy + 2, 46, UiTheme.colorOf(BLUE), UiTheme.neonOf(BLUE), 0.2, 1);
            smallText(g2, "0:18", cx - 14, cy + r + 20);
            label(g2, "30 s idle", 130, 48, UiTheme.warning());
            smallText(g2, "→ countdown", 130, 66);
            label(g2, "60 s idle", 130, 92, UiTheme.error());
            smallText(g2, "→ turn passes", 130, 110);
        }

        // ------------------------------------------------------------------ drawing helpers

        /**
         * One board square with its top-left corner at (x, y).
         */
        private static void tile(Graphics2D g2, double x, double y, Color fill) {
            RoundRectangle2D square = new RoundRectangle2D.Double(x, y, TILE, TILE, 7, 7);
            g2.setColor(fill);
            g2.fill(square);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(UiTheme.grid());
            g2.draw(square);
        }

        /**
         * A castle in a player's color with some panthers still inside.
         */
        private static void castle(Graphics2D g2, double x, double y, double w, double h, PieceColor color,
                                       int pieces) {
            RoundRectangle2D box = new RoundRectangle2D.Double(x, y, w, h, 14, 14);
            Color neon = UiTheme.neonOf(color);
            g2.setColor(UiTheme.dim(neon));
            g2.fill(box);
            Glow.stroke(g2, box, neon, 1.4f, 0.45);
            for (int i = 0; i < pieces; i++) {
                double px = x + w / 2 + (pieces == 1 ? 0 : (i % 2 == 0 ? -w / 4.5 : w / 4.5));
                double py = y + h / 2 + (pieces <= 2 ? 0 : (i < 2 ? -h / 5 : h / 5));
                piece(g2, px, py - 2, Math.min(w, h) * (pieces == 1 ? 0.5 : 0.36), color);
            }
        }

        /**
         * The same 3D panther piece as on the board.
         */
        private static void piece(Graphics2D g2, double cx, double cy, double size, PieceColor color) {
            PantherPainter.paintPiece(g2, cx, cy, size, UiTheme.colorOf(color), 1);
        }

        /**
         * A faded panther showing where a piece was before it moved.
         */
        private static void ghost(Graphics2D g2, double cx, double cy, double size, PieceColor color) {
            Graphics2D faded = (Graphics2D) g2.create();
            faded.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.35f));
            piece(faded, cx, cy, size, color);
            faded.dispose();
        }

        /**
         * A step die showing {@code value} pips.
         */
        private static void die(Graphics2D g2, double x, double y, double size, int value, Color accent) {
            RoundRectangle2D face = new RoundRectangle2D.Double(x, y, size, size, size * 0.27, size * 0.27);
            g2.setColor(UiTheme.surfaceHi());
            g2.fill(face);
            Glow.stroke(g2, face, accent, 1.6f, 0.5);
            int[][] pips = {{}, {4}, {0, 8}, {0, 4, 8}, {0, 2, 6, 8}, {0, 2, 4, 6, 8}, {0, 2, 3, 5, 6, 8}};
            double pip = size / 6.5;
            g2.setColor(UiTheme.text());
            for (int cell : pips[value]) {
                double px = x + size / 6.0 + (cell % 3) * (size / 3.0);
                double py = y + size / 6.0 + (cell / 3) * (size / 3.0);
                g2.fill(new Ellipse2D.Double(px - pip / 2, py - pip / 2, pip, pip));
            }
        }

        /**
         * The Boost / UltraBoost die, which shows a number instead of pips.
         */
        private static void numberDie(Graphics2D g2, double x, double y, double size, String value) {
            RoundRectangle2D face = new RoundRectangle2D.Double(x, y, size, size, size * 0.27, size * 0.27);
            g2.setColor(UiTheme.surfaceHi());
            g2.fill(face);
            Glow.stroke(g2, face, UiTheme.pantherGold(), 1.6f, 0.5);
            g2.setFont(UiTheme.font((float) (size * 0.45), true));
            g2.setColor(UiTheme.pantherGold());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(value, (float) (x + (size - fm.stringWidth(value)) / 2),
                    (float) (y + (size + fm.getAscent() - fm.getDescent()) / 2));
        }

        /**
         * A dashed curved arrow from (x1, y1) to (x2, y2); a negative {@code bend} bows it upwards.
         */
        private static void curvedArrow(Graphics2D g2, double x1, double y1, double x2, double y2, double bend,
                                        Color color) {
            double mx = (x1 + x2) / 2;
            double my = (y1 + y2) / 2 + bend;
            Path2D curve = new Path2D.Double();
            curve.moveTo(x1, y1);
            curve.quadTo(mx, my, x2, y2);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                    new float[]{6f, 5f}, 0f));
            g2.draw(curve);
            // The tip points along the last part of the curve.
            arrowHead(g2, x2, y2, Math.atan2(y2 - my, x2 - mx), color);
        }

        /**
         * A filled triangular arrow tip at (x, y) pointing in direction {@code angle} (radians, 0 = right).
         */
        private static void arrowHead(Graphics2D g2, double x, double y, double angle, Color color) {
            double size = 8;
            Polygon tip = new Polygon();
            tip.addPoint((int) Math.round(x + size * Math.cos(angle)), (int) Math.round(y + size * Math.sin(angle)));
            tip.addPoint((int) Math.round(x + size * Math.cos(angle + 2.4)),
                    (int) Math.round(y + size * Math.sin(angle + 2.4)));
            tip.addPoint((int) Math.round(x + size * Math.cos(angle - 2.4)),
                    (int) Math.round(y + size * Math.sin(angle - 2.4)));
            g2.setColor(color);
            g2.fill(tip);
        }

        /**
         * A red "not allowed" cross.
         */
        private static void cross(Graphics2D g2, double cx, double cy, double r) {
            g2.setColor(UiTheme.error());
            g2.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(cx - r, cy - r, cx + r, cy + r));
            g2.draw(new Line2D.Double(cx - r, cy + r, cx + r, cy - r));
        }

        /**
         * Bold text with its baseline at (x, y).
         */
        private static void label(Graphics2D g2, String text, double x, double y, Color color) {
            g2.setFont(UiTheme.font(16, true));
            g2.setColor(color);
            g2.drawString(text, (float) x, (float) y);
        }

        /**
         * Small caption text (12 pt, NFR-03) with its baseline at (x, y).
         */
        private static void smallText(Graphics2D g2, String text, double x, double y) {
            g2.setFont(UiTheme.font(12, true));
            g2.setColor(UiTheme.textMuted());
            g2.drawString(text, (float) x, (float) y);
        }

        /**
         * Bold square symbol such as "#", "?" or "!" centered on (cx, cy).
         */
        private static void centeredText(Graphics2D g2, String text, double cx, double cy) {
            g2.setFont(UiTheme.font(16, true));
            g2.setColor(UiTheme.text());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0),
                    (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
        }

        /**
         * One square of the board as a small icon, for the legend in the Rules window (FR-18). It copies the look of
         * {@code BoardPanel.BoardBackgroundPainter} so players recognize the squares: "#" for start, a gold star, a
         * colored arrow, the
         * colored home lane, the golden center, "?" for questions and "!" for surprises.
         */
        static final class SquareIcon implements Icon {

            private static final int SIZE = 34;
            /** Color used for the colored squares (start, arrow, home lane) in the legend. */
            private static final PieceColor SAMPLE = PieceColor.PRESETS.get(0);

            private final SquareType type;

            /**
             * @param type the kind of square to draw
             */
            SquareIcon(SquareType type) {
                this.type = type;
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.translate(x, y);
                Color seat = UiTheme.neonOf(SAMPLE);
                RoundRectangle2D tile = new RoundRectangle2D.Double(1, 1, SIZE - 2, SIZE - 2, 8, 8);
                Color fill = switch (type) {
                    case START -> UiTheme.mix(seat, UiTheme.surfaceHi(), 0.38);
                    case ARROW -> UiTheme.mix(seat, UiTheme.surfaceHi(), 0.28);
                    case HOME_LANE -> UiTheme.lane(seat);
                    case FINISH -> UiTheme.bgDeep();
                    default -> UiTheme.surfaceHi();
                };
                g2.setColor(fill);
                g2.fill(tile);
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(UiTheme.grid());
                g2.draw(tile);
                double cx = SIZE / 2.0;
                double cy = SIZE / 2.0;
                switch (type) {
                    case START -> {
                        Glow.stroke(g2, tile, seat, 1.4f, 0.5);
                        symbol(g2, "#");
                    }
                    case STAR -> star(g2, cx, cy);
                    case ARROW -> {
                        Polygon arrow = new Polygon();
                        arrow.addPoint((int) (cx + 9), (int) cy);
                        arrow.addPoint((int) (cx - 7), (int) (cy - 9));
                        arrow.addPoint((int) (cx - 7), (int) (cy + 9));
                        g2.setColor(seat);
                        g2.fill(arrow);
                    }
                    case HOME_LANE -> Glow.stroke(g2, tile, seat, 1f, 0.25);
                    case FINISH -> {
                        Glow.halo(g2, cx, cy, 14, UiTheme.pantherGold(), 0.9);
                        g2.setColor(UiTheme.pantherGold());
                        g2.fill(new Ellipse2D.Double(cx - 5, cy - 5, 10, 10));
                    }
                    case QUESTION -> symbol(g2, "?");
                    case SURPRISE -> symbol(g2, "!");
                    default -> {
                    }
                }
                g2.dispose();
            }

            /**
             * Draws #, ? or ! in the middle of the icon.
             */
            private static void symbol(Graphics2D g2, String text) {
                g2.setFont(UiTheme.font(17, true));
                g2.setColor(UiTheme.text());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text, (SIZE - fm.stringWidth(text)) / 2f, (SIZE + fm.getAscent() - fm.getDescent()) / 2f);
            }

            /**
             * Five-pointed gold star, as on the protected star squares.
             */
            private static void star(Graphics2D g2, double cx, double cy) {
                Polygon star = new Polygon();
                for (int i = 0; i < 10; i++) {
                    double radius = i % 2 == 0 ? 11 : 5;
                    double angle = -Math.PI / 2 + i * Math.PI / 5;
                    star.addPoint((int) Math.round(cx + radius * Math.cos(angle)),
                            (int) Math.round(cy + radius * Math.sin(angle)));
                }
                g2.setColor(UiTheme.star());
                g2.fill(star);
            }

            @Override
            public int getIconWidth() {
                return SIZE;
            }

            @Override
            public int getIconHeight() {
                return SIZE;
            }
        }
    }
}
