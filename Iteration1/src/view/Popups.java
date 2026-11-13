package view;

import model.*;

import javax.swing.*;
import javax.swing.Timer; // the Swing timer, not java.util.Timer
import java.awt.*;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.geom.*;
import java.util.*;
import java.util.List; // java.util.List, not java.awt.List

/**
 * Every pop-up window of the program, each in its own nested class:
 * <ul>
 *     <li>{@link Question} and {@link Surprise}: the windows that open when a panther stops on a special square;</li>
 *     <li>{@link Rules}: the game rules;</li>
 *     <li>{@link QuestionEditor}: adding or editing a question in question management;</li>
 *     <li>{@link Message}: short messages, errors and yes/no questions.</li>
 * </ul>
 * The windows only display and report the choice; the model decides what an answer or event does. When the computer
 * plays for a player whose time ran out (TD-15), the question and surprise windows show the computer's choice and
 * continue by itself after a short pause.
 */
public final class Popups {

    /** Pause between the actions the computer plays for a player whose time ran out (TD-15). */
    public static final int AUTO_PLAY_DELAY_MILLIS = 1300;

    private static final int DIALOG_WIDTH = 620;
    private static final int BOX_WIDTH = 470;
    private static final int ANSWER_WIDTH = 250;

    /**
     * Only static helpers; no objects of this class are created.
     */
    private Popups() {
    }

    private static final Color NEON_CYAN = new Color(0x2CE5FF);
    private static final Color NEON_PINK = new Color(0xFF4FD8);
    private static final Color NEON_GREEN = new Color(0x39F08F);
    private static final Color NEON_RED = new Color(0xFF4D6D);

    /**
     * Draws a rounded frame glowing from cyan (top left) to pink (bottom right).
     */
    static void neonFrame(Graphics2D g2, Shape frame, float width) {
        java.awt.geom.Rectangle2D b = frame.getBounds2D();
        GradientPaint paint = new GradientPaint((float) b.getX(), (float) b.getY(), NEON_CYAN, (float) b.getMaxX(),
                (float) b.getMaxY(), NEON_PINK);
        Composite old = g2.getComposite();
        for (int i = 4; i >= 1; i--) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.09f));
            g2.setPaint(paint);
            g2.setStroke(new BasicStroke(width + i * 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(frame);
        }
        g2.setComposite(old);
        g2.setPaint(paint);
        g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(frame);
    }

    /**
     * A modal pop-up over the game screen: only its own card, without the system title bar and its close button; every
     * pop-up closes with its own buttons.
     */
    private static JDialog dialog(Component parent, String title) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setResizable(false);
        return dialog;
    }

    /**
     * Shows the window centered over the game and returns once it is closed. It always stays on the usable part of the
     * screen: on a small screen its content scrolls instead of disappearing under the taskbar.
     */
    private static void open(JDialog dialog, JPanel content, Component parent) {
        dialog.setContentPane(Widgets.ScreenFit.wrapInScroll(content, Theme.surface()));
        Assets.Texts.applyOrientation(dialog);
        Widgets.ScreenFit.packAndPlace(dialog, parent);
        dialog.setVisible(true);
    }

    /**
     * The window background with a neon frame.
     */
    private static JPanel buildContent() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(Theme.surface());
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.neonPurple(), 2),
                BorderFactory.createEmptyBorder(18, 22, 16, 22)));
        return content;
    }

    /**
     * Layout settings for one column of rows.
     */
    private static GridBagConstraints constraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(3, 0, 3, 0);
        return c;
    }

    /**
     * Runs an action once after a pause (used when the computer plays).
     */
    private static void runLater(int millis, Runnable action) {
        Timer timer = new Timer(millis, e -> action.run());
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * A label in the given font and color.
     */
    private static JLabel createLabel(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    /**
     * The question window (FR-42–44): the question with its level and four answers, visible to everyone at the
     * computer; only the current player answers and there is no time limit. After the answer the window shows whether
     * it was right, the correct answer and the explanation, and waits for Continue.
     */
    public static final class Question {

        /**
         * Receives what happens in the question window.
         */
        public interface Listener {

            /**
             * The player picked an answer.
             *
             * @param index index of the chosen answer
             * @return the game's verdict on the answer
             */
            Game.Answer answerChosen(int index);

            /**
             * The player pressed Continue and the window has closed.
             */
            void questionClosed();
        }

        /**
         * Only static methods and helper parts; no objects of this class are created.
         */
        private Question() {
        }

        /**
         * Shows a question and waits until the player has answered and pressed Continue.
         *
         * @param parent     a component of the game screen
         * @param player     the player who answers
         * @param question   the question
         * @param autoChoice the answer the computer picks for an absent player, or -1 for a human answer
         * @param listener   gets the chosen answer and is told when the window has closed
         */
        public static void show(Component parent, Player player, model.Question question, int autoChoice,
                                Listener listener) {
            JDialog dialog = dialog(parent, Assets.Texts.get("question.title"));
            Color level = Theme.questionColor(question.level());
            NeonBox content = new NeonBox(new BorderLayout(16, 0));

            // The Quiz Master panther on the left; the question box speaks for it.
            JLabel master = new JLabel(Theme.iconOf("quiz_master", 190));
            master.setVerticalAlignment(SwingConstants.BOTTOM);
            content.add(master, BorderLayout.LINE_START);

            SpeechBox box = new SpeechBox();
            box.setLayout(new GridBagLayout());
            GridBagConstraints c = constraints();
            JPanel head = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 0));
            head.setOpaque(false);
            head.add(createLabel(Assets.Texts.get("question.label"), Theme.Fonts.baseFont(19, true), NEON_CYAN));
            head.add(new LevelTag(Assets.Texts.get("question.level", Assets.Texts.level(question.level()),
                    Assets.Texts.topic(question.topic())), level));
            box.add(head, c);
            c.gridy++;
            c.insets = new Insets(6, 0, 4, 0);
            box.add(new Widgets.Paragraph(question.text(), Theme.Fonts.baseFont(19, true), Color.WHITE, BOX_WIDTH), c);
            c.gridy++;
            c.insets = new Insets(0, 0, 10, 0);
            box.add(new Widgets.Paragraph(Assets.Texts.get("question.who", player.name(), question.steps()),
                    Theme.Fonts.baseFont(14, false), new Color(0xA9B3E0), BOX_WIDTH), c);

            // The four answers: dark neon pills in a 2 × 2 grid.
            List<AnswerButton> buttons = new ArrayList<>();
            JPanel answers = new JPanel(new GridLayout(0, 2, 12, 12));
            answers.setOpaque(false);
            Explanation explanation = new Explanation();
            Widgets.Button next = new Widgets.Button(Assets.Texts.get("common.continue"), Theme.neonPurple(), true);
            next.setVisible(false);
            for (int i = 0; i < question.answers().size(); i++) {
                int choice = i;
                AnswerButton button = new AnswerButton((char) ('A' + i) + ".  " + question.answers().get(i));
                button.addActionListener(e -> {
                    if (next.isVisible()) {
                        return;
                    }
                    Game.Answer answer = listener.answerChosen(choice);
                    for (int j = 0; j < buttons.size(); j++) {
                        buttons.get(j).showResult(j == question.correctIndex() ? AnswerButton.RIGHT
                                : j == choice ? AnswerButton.WRONG : AnswerButton.OTHER);
                    }
                    String pop = Assets.Texts.get((answer.wasCorrect() ? "pop.right" : "pop.wrong") + (1 + new Random().nextInt(3)));
                    explanation.show(pop, answer.wasCorrect(), Assets.Texts.answered(answer) + "\n"
                            + Assets.Texts.get("question.correctAnswer", question.correctAnswer()) + "\n"
                            + Assets.Texts.get("question.explanation", question.explanation()));
                    next.setVisible(true);
                    dialog.getRootPane().setDefaultButton(next);
                    // The explanation makes the window taller: it grows, then is moved (or made scrollable) so its
                    // bottom with Continue never goes under the taskbar.
                    dialog.pack();
                    Widgets.ScreenFit.keepOnScreen(dialog);
                    next.requestFocusInWindow();
                    if (autoChoice >= 0) {
                        runLater(2 * Popups.AUTO_PLAY_DELAY_MILLIS, next::doClick);
                    }
                });
                buttons.add(button);
                answers.add(button);
            }
            c.gridy++;
            c.insets = new Insets(2, 0, 2, 0);
            box.add(answers, c);
            if (autoChoice >= 0) {
                c.gridy++;
                box.add(new Widgets.Paragraph(Assets.Texts.get("question.computer"), Theme.Fonts.SMALL, Theme.warning(),
                        BOX_WIDTH), c);
                runLater(Popups.AUTO_PLAY_DELAY_MILLIS, () -> buttons.get(autoChoice).doClick());
            }
            c.gridy++;
            c.insets = new Insets(12, 0, 0, 0);
            box.add(explanation, c);
            c.gridy++;
            c.insets = new Insets(10, 0, 0, 0);
            c.fill = GridBagConstraints.NONE;
            c.anchor = GridBagConstraints.LINE_END;
            box.add(next, c);
            content.add(box, BorderLayout.CENTER);
            next.addActionListener(e -> dialog.dispose());
            // No time limit and no way around the question: the window closes only through Continue (FR-42, FR-44).
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            open(dialog, content, parent);
            listener.questionClosed();
        }

        /**
         * The dark neon window of the question: deep navy with a glowing cyan-to-pink frame.
         */
        static final class NeonBox extends JPanel {

            /**
             * @param layout layout of the content
             */
            NeonBox(LayoutManager layout) {
                super(layout);
                setOpaque(true);
                setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 24));
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, new Color(0x121A45), 0, h, new Color(0x070A22)));
                g2.fillRect(0, 0, w, h);
                g2.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Double(w * 0.2, h * 0.75), w * 0.5f,
                        new float[]{0f, 1f}, new Color[]{new Color(255, 79, 216, 40), new Color(255, 79, 216, 0)}));
                g2.fillRect(0, 0, w, h);
                java.awt.geom.RoundRectangle2D frame = new java.awt.geom.RoundRectangle2D.Double(6, 6, w - 12, h - 12,
                        26, 26);
                neonFrame(g2, frame, 2.2f);
                g2.dispose();
            }
        }
        /**
         * The question box: a dark panel with a neon frame and a small tail pointing at the Quiz Master.
         */
        static final class SpeechBox extends JPanel {

            /**
             * Creates an empty speech box.
             */
            SpeechBox() {
                setOpaque(false);
                setBorder(BorderFactory.createEmptyBorder(18, 34, 18, 22));
            }

            @Override
            public void doLayout() {
                boolean rtl = !getComponentOrientation().isLeftToRight();
                setBorder(BorderFactory.createEmptyBorder(18, rtl ? 22 : 34, 18, rtl ? 34 : 22));
                super.doLayout();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean rtl = !getComponentOrientation().isLeftToRight();
                double x = rtl ? 4 : 16;
                double w = getWidth() - 20;
                java.awt.geom.RoundRectangle2D body = new java.awt.geom.RoundRectangle2D.Double(x, 4, w, getHeight() - 8,
                        24, 24);
                double ty = Math.min(150, getHeight() * 0.35);
                java.awt.geom.Path2D tail = new java.awt.geom.Path2D.Double();
                double edge = rtl ? x + w : x;
                double tip = rtl ? x + w + 14 : x - 14;
                tail.moveTo(edge, ty - 12);
                tail.lineTo(tip, ty);
                tail.lineTo(edge, ty + 12);
                tail.closePath();
                java.awt.geom.Area shape = new java.awt.geom.Area(body);
                shape.add(new java.awt.geom.Area(tail));
                g2.setColor(new Color(13, 19, 54, 235));
                g2.fill(shape);
                neonFrame(g2, shape, 1.8f);
                g2.dispose();
            }
        }
        /**
         * The small colored tag with the question's level and topic.
         */
        static final class LevelTag extends JComponent {

            private final String text;
            private final Color color;

            /**
             * @param text  the tag text
             * @param color the difficulty color
             */
            LevelTag(String text, Color color) {
                this.text = text;
                this.color = color;
                FontMetrics fm = getFontMetrics(Theme.Fonts.baseFont(13, true));
                setPreferredSize(new Dimension(fm.stringWidth(text) + 22, 26));
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                java.awt.geom.RoundRectangle2D pill = new java.awt.geom.RoundRectangle2D.Double(1, 2, getWidth() - 2,
                        getHeight() - 4, getHeight() - 4, getHeight() - 4);
                g2.setColor(Theme.alpha(color, 0.18));
                g2.fill(pill);
                Theme.Glow.paintGlowStroke(g2, pill, color, 1.2f, 0.6);
                g2.setFont(Theme.Fonts.baseFont(13, true));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(Theme.mix(color, Color.WHITE, 0.6));
                g2.drawString(text, 11f, (float) (getHeight() / 2.0 + (fm.getAscent() - fm.getDescent()) / 2.0));
                g2.dispose();
            }
        }
        /**
         * One answer: a dark pill with a cyan neon edge. After answering, the right answer turns neon green and a
         * wrong choice neon red, both with black text so it reads well; the other answers fade.
         */
        static final class AnswerButton extends JButton {

            static final int NONE = 0;
            static final int RIGHT = 1;
            static final int WRONG = 2;
            static final int OTHER = 3;
            private int state = NONE;
            /** The answer's text, drawn by itself so Hebrew and Arabic wrap correctly. */
            private final Widgets.Paragraph label;

            /**
             * @param text the answer
             */
            AnswerButton(String text) {
                super();
                label = new Widgets.Paragraph(text, Theme.Fonts.baseFont(16, true), Color.WHITE, ANSWER_WIDTH).setCentered(true);
                setLayout(new BorderLayout());
                add(label, BorderLayout.CENTER);
                setForeground(Color.WHITE);
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setOpaque(false);
                setRolloverEnabled(true);
                setHorizontalAlignment(SwingConstants.CENTER);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
                addActionListener(e -> Assets.Sound.play(Assets.Sound.Effect.CLICK));
            }

            /**
             * Colors the button after the answer.
             *
             * @param state {@link #RIGHT}, {@link #WRONG} or {@link #OTHER}
             */
            void showResult(int state) {
                this.state = state;
                setForeground(state == RIGHT || state == WRONG ? Color.BLACK : new Color(0x8A93C0));
                label.setForeground(getForeground());
                setCursor(Cursor.getDefaultCursor());
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                java.awt.geom.RoundRectangle2D pill = new java.awt.geom.RoundRectangle2D.Double(4, 4, getWidth() - 8,
                        getHeight() - 8, 22, 22);
                boolean hover = state == NONE && getModel().isRollover();
                switch (state) {
                    case RIGHT, WRONG -> {
                        Color neon = state == RIGHT ? NEON_GREEN : NEON_RED;
                        for (int i = 3; i >= 1; i--) {
                            g2.setColor(Theme.alpha(neon, 0.16));
                            g2.setStroke(new BasicStroke(2f + i * 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            g2.draw(pill);
                        }
                        g2.setPaint(new GradientPaint(0, 4, Theme.mix(neon, Color.WHITE, 0.75), 0, getHeight() - 4,
                                neon));
                        g2.fill(pill);
                        g2.setColor(Theme.mix(neon, Color.WHITE, 0.5));
                        g2.setStroke(new BasicStroke(2f));
                        g2.draw(pill);
                    }
                    case OTHER -> {
                        g2.setColor(new Color(20, 26, 66, 200));
                        g2.fill(pill);
                        g2.setColor(new Color(0x39406E));
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.draw(pill);
                    }
                    default -> {
                        g2.setColor(hover ? new Color(28, 40, 96) : new Color(20, 28, 74));
                        g2.fill(pill);
                        Theme.Glow.paintGlowStroke(g2, pill, NEON_CYAN, 1.6f, hover ? 0.9 : 0.5);
                    }
                }
                g2.dispose();
                super.paintComponent(g);
            }
        }
        /**
         * The explanation that pops up under the answers: a comic shout (green or red) and why the answer is right.
         * It fades and slides in.
         */
        static final class Explanation extends JPanel {

            private final JLabel shout = new JLabel(" ");
            private final Widgets.Paragraph text = new Widgets.Paragraph(" ", Theme.Fonts.baseFont(15, false),
                    new Color(0xE6EAFF), BOX_WIDTH - 40);
            private Color color = NEON_GREEN;
            private float alpha;
            private final Timer fade = new Timer(16, e -> step());

            /**
             * Creates the hidden explanation.
             */
            Explanation() {
                super(new BorderLayout(0, 4));
                setOpaque(false);
                setVisible(false);
                setBorder(BorderFactory.createEmptyBorder(12, 16, 14, 16));
                add(shout, BorderLayout.NORTH);
                add(text, BorderLayout.CENTER);
            }

            /**
             * Fades the explanation in.
             *
             * @param pop   the comic shout
             * @param right {@code true} for a right answer
             * @param explanation the explanation text, one line per {@code \n}
             */
            void show(String pop, boolean right, String explanation) {
                color = right ? NEON_GREEN : NEON_RED;
                shout.setFont(Theme.Fonts.comicFont(30f, pop));
                shout.setForeground(color);
                shout.setText(Assets.Texts.directional(pop));
                shout.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
                text.setText(explanation);
                alpha = 0;
                setVisible(true);
                fade.start();
            }

            /**
             * Moves the fade-in one frame on.
             */
            private void step() {
                alpha = Math.min(1, alpha + 0.08f);
                if (alpha >= 1) {
                    fade.stop();
                }
                repaint();
            }

            @Override
            protected void paintChildren(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.translate(0, (1 - alpha) * 14);
                super.paintChildren(g2);
                g2.dispose();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.translate(0, (1 - alpha) * 14);
                java.awt.geom.RoundRectangle2D card = new java.awt.geom.RoundRectangle2D.Double(2, 2, getWidth() - 4,
                        getHeight() - 4, 18, 18);
                g2.setColor(new Color(9, 13, 40, 230));
                g2.fill(card);
                g2.setColor(Theme.alpha(color, 0.1));
                g2.fill(card);
                Theme.Glow.paintGlowStroke(g2, card, color, 1.6f, 0.7);
                g2.dispose();
            }
        }
    }

    /**
     * The surprise window (FR-47): the event and what it does; it is applied when OK is pressed.
     */
    public static final class Surprise {

        /**
         * Receives what happens in the surprise window.
         */
        public interface Listener {

            /**
             * OK was pressed (or the window was closed) and the window has closed.
             */
            void surpriseClosed();
        }

        /**
         * Only static methods and helper parts; no objects of this class are created.
         */
        private Surprise() {
        }

        /**
         * Shows a surprise event and waits until OK is pressed (FR-47).
         *
         * @param parent   a component of the game screen
         * @param event    the drawn event
         * @param auto     {@code true} if the computer plays for an absent player: the window closes by itself
         * @param listener is told when the window has closed
         */
        public static void show(Component parent, Game.SurpriseEvent event, boolean auto, Listener listener) {
            JDialog dialog = dialog(parent, Assets.Texts.get("surprise.heading"));
            JPanel content = new ComicPanel(new GridBagLayout(), Theme.pantherGold());
            GridBagConstraints c = constraints();
            c.anchor = GridBagConstraints.CENTER;
            c.fill = GridBagConstraints.NONE;
            content.add(new BurstLabel(Assets.Texts.get("surprise.heading"), new Color(0xFF4FB8), 340, 170, 50f), c);
            c.gridy++;
            JLabel chest = new JLabel(Theme.iconOf("tile_surprise", 120));
            ((ComicPanel) content).addRaysBehind(chest);
            content.add(chest, c);
            c.gridy++;
            c.insets = new Insets(8, 0, 4, 0);
            String name = event.surprise().name();
            String caption = Assets.Texts.get("pop." + name) + " " + Assets.Texts.surpriseTitle(event.surprise());
            content.add(new CaptionBox(caption), c);
            c.gridy++;
            c.insets = new Insets(12, 0, 14, 0);
            content.add(new Widgets.Paragraph(Assets.Texts.surprise(event), Theme.Fonts.baseFont(17, false), Theme.text(),
                    DIALOG_WIDTH - 80).setCentered(true), c);
            Widgets.Button ok = new Widgets.Button(Assets.Texts.get("common.ok"), Theme.neonPurple(), true);
            ok.addActionListener(e -> dialog.dispose());
            c.gridy++;
            c.insets = new Insets(4, 0, 0, 0);
            content.add(ok, c);
            dialog.getRootPane().setDefaultButton(ok);
            // Closing the window counts as OK: the event happens in any case.
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            if (auto) {
                runLater(2 * Popups.AUTO_PLAY_DELAY_MILLIS, ok::doClick);
            }
            open(dialog, content, parent);
            listener.surpriseClosed();
        }

        /**
         * The window background in the Panther Ludo style: the game's own aurora background with the blue and pink
         * swooshes and floating icons, comic sunburst rays turning slowly behind the art (the Quiz Master, the chest) and
         * a thick ink frame.
         */
        static final class ComicPanel extends Widgets.Background {

            private final Color accent;
            private JComponent star;

            /**
             * @param layout layout of the content
             * @param accent color of the rays
             */
            ComicPanel(LayoutManager layout, Color accent) {
                super(layout);
                this.accent = accent;
                setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));
            }

            /**
             * @param component the art the sunburst rays shine from
             */
            void addRaysBehind(JComponent component) {
                this.star = component;
            }

            @Override
            public void removeNotify() {
                Theme.Motion.unsubscribe(this);
                super.removeNotify();
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (star != null && star.isShowing()) {
                    Point p = SwingUtilities.convertPoint(star.getParent(), star.getX() + star.getWidth() / 2,
                            star.getY() + star.getHeight() / 2, this);
                    double radius = Math.max(getWidth(), getHeight());
                    double turn = Theme.Motion.secondsSinceStart() * 0.12;
                    int rays = 18;
                    boolean light = Theme.mode() == Theme.Mode.LIGHT;
                    Color[] colors = {Theme.alpha(Theme.Comic.candyColor(accent), light ? 0.22 : 0.18),
                            Theme.alpha(new Color(0xFF4FB8), light ? 0.16 : 0.14)};
                    for (int i = 0; i < rays; i++) {
                        double a0 = turn + i * 2 * Math.PI / rays;
                        double a1 = a0 + Math.PI / rays;
                        java.awt.geom.Path2D ray = new java.awt.geom.Path2D.Double();
                        ray.moveTo(p.x, p.y);
                        ray.lineTo(p.x + Math.cos(a0) * radius, p.y + Math.sin(a0) * radius);
                        ray.lineTo(p.x + Math.cos(a1) * radius, p.y + Math.sin(a1) * radius);
                        ray.closePath();
                        g2.setPaint(new RadialGradientPaint(p, (float) radius * 0.7f, new float[]{0f, 1f},
                                new Color[]{colors[i % 2], Theme.alpha(colors[i % 2], 0)}));
                        g2.fill(ray);
                    }
                }
                g2.setColor(Theme.Comic.inkColor());
                g2.setStroke(new BasicStroke(4f));
                g2.drawRect(2, 2, getWidth() - 4, getHeight() - 4);
                g2.dispose();
            }
        }
        /**
         * A white speech balloon whose tail points to the speaker at its leading side.
         */
        static final class SpeechBalloon extends JPanel {

            /**
             * Creates an empty balloon.
             */
            SpeechBalloon() {
                setOpaque(false);
                setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
                setBorder(BorderFactory.createEmptyBorder(14, 40, 16, 20));
            }

            @Override
            public Component add(Component comp) {
                if (comp instanceof JComponent j) {
                    j.setAlignmentX(Component.LEFT_ALIGNMENT);
                }
                return super.add(comp);
            }

            @Override
            public void doLayout() {
                boolean rtl = !getComponentOrientation().isLeftToRight();
                setBorder(BorderFactory.createEmptyBorder(14, rtl ? 20 : 40, 16, rtl ? 40 : 20));
                super.doLayout();
            }

            @Override
            protected void paintComponent(Graphics g) {
                boolean rtl = !getComponentOrientation().isLeftToRight();
                double x = rtl ? 2 : 22;
                java.awt.geom.RoundRectangle2D body = new java.awt.geom.RoundRectangle2D.Double(x, 2, getWidth() - 26,
                        getHeight() - 4, 34, 34);
                Theme.Comic.balloon((Graphics2D) g, body, rtl ? getWidth() - 2 : 2, getHeight() * 0.62, Theme.Comic.comicPaper());
            }
        }
        /**
         * A comic burst with a word in it.
         */
        static final class BurstLabel extends JComponent {

            private final String text;
            private final Color color;
            private final float size;

            /**
             * @param text   the word
             * @param color  fill color
             * @param width  width in pixels
             * @param height height in pixels
             * @param size   font size
             */
            BurstLabel(String text, Color color, int width, int height, float size) {
                this.text = Assets.Texts.directional(text);
                this.color = color;
                this.size = size;
                setPreferredSize(new Dimension(width, height));
            }

            @Override
            protected void paintComponent(Graphics g) {
                Theme.Comic.paintBurst((Graphics2D) g, getWidth() / 2.0 - 3, getHeight() / 2.0 - 4, getWidth() - 14,
                        getHeight() - 16, color, text, size, -0.06);
            }
        }
        /**
         * A white, slightly tilted comic caption box.
         */
        static final class CaptionBox extends JComponent {

            private final String text;
            private final Font font;

            /**
             * @param text the caption
             */
            CaptionBox(String text) {
                this.text = Assets.Texts.directional(text.toUpperCase(Locale.ROOT));
                this.font = Theme.Fonts.comicFont(28f, this.text);
                FontMetrics fm = getFontMetrics(font);
                setPreferredSize(new Dimension(fm.stringWidth(this.text) + 60, fm.getHeight() + 28));
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.rotate(-0.025, getWidth() / 2.0, getHeight() / 2.0);
                FontMetrics fm = g2.getFontMetrics(font);
                double w = fm.stringWidth(text) + 36;
                double h = fm.getHeight() + 10;
                double x = (getWidth() - w) / 2;
                double y = (getHeight() - h) / 2;
                g2.setColor(Theme.Comic.inkColor());
                g2.fill(new Rectangle.Double(x + 5, y + 6, w, h));
                g2.setColor(Theme.Comic.comicPaper());
                g2.fill(new Rectangle.Double(x, y, w, h));
                g2.setColor(Theme.Comic.inkColor());
                g2.setStroke(new BasicStroke(3f));
                g2.draw(new Rectangle.Double(x, y, w, h));
                g2.setFont(font);
                g2.drawString(text, (float) (x + 18), (float) (y + 5 + fm.getAscent()));
                g2.dispose();
            }
        }
    }

    /**
     * The Rules window (FR-07): a modal window with the scrollable {@link RulesPanel} and a Close button. It is opened
     * from the game screen and from Settings. Because it is modal, {@link #show(Component)} returns only after the
     * window is closed, which lets the game controller pause the turn timer meanwhile (FR-30).
     * <p>The window is 900×620 pixels, so it fits the smallest supported screen of 1280×720 (NFR-01).
     */
    public static final class Rules extends JDialog {

        /**
         * Builds the window: title, scrollable rules and a Close button (Esc also closes).
         */
        private Rules(Window owner, Game.Level level) {
            super(owner, Assets.Texts.get("rules.window"), ModalityType.APPLICATION_MODAL);
            // A pop-up card without the system title bar; Close and Esc close it.
            setUndecorated(true);
            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(Theme.background());
            root.setBorder(BorderFactory.createLineBorder(Theme.neonPurple(), 2));

            JPanel header = new JPanel();
            header.setOpaque(false);
            header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
            Widgets.Title title = new Widgets.Title(Assets.Texts.get("rules.title"), 30);
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            header.add(title);
            if (level != null) {
                // During a match only the rules of its level are shown, so the window says which level that is.
                JLabel forLevel = new JLabel(Assets.Texts.get("rules.forLevel", Assets.Texts.level(level)));
                forLevel.setFont(Theme.Fonts.BOLD);
                forLevel.setForeground(Theme.questionColor(level));
                forLevel.setAlignmentX(Component.CENTER_ALIGNMENT);
                forLevel.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
                header.add(forLevel);
            }
            root.add(header, BorderLayout.NORTH);

            JScrollPane scroll = new JScrollPane(new RulesPanel(level));
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getViewport().setBackground(Theme.background());
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            root.add(scroll, BorderLayout.CENTER);

            Widgets.Button close = new Widgets.Button(Assets.Texts.get("common.close"), Theme.neonPurple(), true);
            close.addActionListener(e -> dispose());
            JPanel footer = new JPanel(new FlowLayout(FlowLayout.TRAILING, 14, 6));
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
            Assets.Texts.applyOrientation(this);
            setLocationRelativeTo(owner);
            // On a small screen the window becomes smaller; its rules already scroll.
            Widgets.ScreenFit.keepOnScreen(this);
            getRootPane().setDefaultButton(close);
        }

        /**
         * Opens the Rules window over the given screen and waits until the player closes it.
         *
         * @param parent any component of the window the rules belong to
         */
        public static void show(Component parent) {
            show(parent, null);
        }

        /**
         * Opens the Rules window for one level and waits until the player closes it. Only the rules that apply on that
         * level are shown: on Easy there are no question or surprise squares and no multiplier die, so they are not
         * explained.
         *
         * @param parent any component of the window the rules belong to
         * @param level  the level of the running match, or {@code null} for the rules of every level
         */
        public static void show(Component parent, Game.Level level) {
            Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
            new Rules(owner, level).setVisible(true);
        }

        /**
         * The content of the Rules window (FR-07): every rule as a short text with a picture beside it, plus a legend of
         * all square types (FR-18). The texts follow the SRS; rules that only apply on Medium and Hard (questions,
         * surprises, the multiplier die) say so.
         * <p>To change a rule text, edit its {@code rules.*} entries in the language files; the constructor only lists
         * the rules in order with the picture to show.
         */
        private static final class RulesPanel extends JPanel implements Scrollable {

            /** Width of the text column, in pixels; HTML labels wrap at this width. */
            private static final int TEXT_WIDTH = 520;
            /**
             * @return where rows start: at the left, or at the right in Hebrew and Arabic
             */
            private static float start() {
                return Assets.Texts.language().isRightToLeft() ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT;
            }

            /** The level whose rules are shown, or {@code null} for every level. */
            private final Game.Level level;

            /**
             * Builds the whole rules page.
             *
             * @param level the level of the running match, or {@code null} to explain every level
             */
            RulesPanel(Game.Level level) {
                this.level = level;
                setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
                setBackground(Theme.background());
                setBorder(BorderFactory.createEmptyBorder(8, 22, 18, 22));
                // Easy has no question or surprise squares and no multiplier die: those rules are left out.
                boolean specialSquares = level == null || level != Game.Level.EASY;

                section("goal", RulePicture.Kind.GOAL);
                section("turns", RulePicture.Kind.TURN_ORDER);
                section("dice", RulePicture.Kind.DICE, diceText());
                section("six", RulePicture.Kind.SIX);
                section("moving", RulePicture.Kind.MOVE);
                section("capture", RulePicture.Kind.CAPTURE);
                section("block", RulePicture.Kind.BLOCK);
                section("home", RulePicture.Kind.HOME, level == Game.Level.EASY
                        ? Assets.Texts.get("rules.home.easy") + "<br><br>" + Assets.Texts.get("rules.home.p2") : null);

                add(heading(Assets.Texts.get("rules.squares")));
                add(legend(specialSquares));
                add(Box.createVerticalStrut(18));

                if (specialSquares) {
                    section("question", RulePicture.Kind.QUESTION);
                    section("surprise", RulePicture.Kind.SURPRISE);
                }
                section("timer", RulePicture.Kind.TIMER);
            }

            /**
             * @return the dice rule of the shown level, or {@code null} for the text that explains every level
             */
            private String diceText() {
                if (level == null) {
                    return null;
                }
                return switch (level) {
                    case EASY -> Assets.Texts.get("rules.dice.p1");
                    case MEDIUM -> Assets.Texts.get("rules.dice.medium");
                    case HARD -> Assets.Texts.get("rules.dice.hard");
                };
            }

            /**
             * Adds one rule with its usual texts {@code rules.<key>.p1} and {@code rules.<key>.p2}.
             */
            private void section(String key, RulePicture.Kind picture) {
                section(key, picture, null);
            }

            /**
             * Adds one rule: heading on top, picture on the side, text next to it. The texts are
             * {@code rules.<key>.title}, {@code rules.<key>.p1} and {@code rules.<key>.p2} in the language files; simple
             * HTML such as {@code <b>} and {@code <br>} is allowed in them. While a match runs, the heading of a rule
             * that only some levels have leaves out the "(Medium and Hard)" note ({@code rules.<key>.titleShort}).
             *
             * @param key     name of the rule in the language files
             * @param picture drawing that illustrates it
             * @param body    the text to show instead of the usual two paragraphs, or {@code null}
             */
            private void section(String key, RulePicture.Kind picture, String body) {
                String shortTitle = "rules." + key + ".titleShort";
                add(heading(Assets.Texts.get(level != null && Assets.Texts.contains(shortTitle) ? shortTitle
                        : "rules." + key + ".title")));
                JPanel row = new JPanel(new BorderLayout(18, 0));
                row.setOpaque(false);
                row.setAlignmentX(start());
                JPanel pictureHolder = new JPanel(new BorderLayout());
                pictureHolder.setOpaque(false);
                pictureHolder.add(new RulePicture(picture, level), BorderLayout.NORTH);
                row.add(pictureHolder, BorderLayout.LINE_START);
                row.add(text(body != null ? body : Assets.Texts.get("rules." + key + ".p1") + "<br><br>"
                                + Assets.Texts.get("rules." + key + ".p2")), BorderLayout.CENTER);
                add(row);
                add(Box.createVerticalStrut(18));
            }

            /**
             * Legend of the square types with their icons (FR-18), in two columns.
             *
             * @param specialSquares {@code false} on Easy, whose board has no question or surprise squares
             */
            private JComponent legend(boolean specialSquares) {
                JPanel grid = new JPanel(new GridLayout(0, 2, 18, 8));
                grid.setOpaque(false);
                grid.setAlignmentX(start());
                grid.add(legendRow(Square.Type.REGULAR));
                grid.add(legendRow(Square.Type.START));
                grid.add(legendRow(Square.Type.STAR));
                grid.add(legendRow(Square.Type.ARROW));
                grid.add(legendRow(Square.Type.HOME_LANE));
                grid.add(legendRow(Square.Type.FINISH));
                if (specialSquares) {
                    grid.add(legendRow(Square.Type.QUESTION));
                    grid.add(legendRow(Square.Type.SURPRISE));
                }
                grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
                return grid;
            }

            /**
             * One legend entry: square icon, name and meaning, from {@code rules.legend.<TYPE>} in the language files.
             */
            private static JComponent legendRow(Square.Type type) {
                String name = Assets.Texts.get("rules.legend." + type.name());
                String meaning = Assets.Texts.get("rules.legend." + type.name() + ".meaning");
                JPanel row = new JPanel(new BorderLayout(12, 0));
                row.setOpaque(false);
                row.add(new JLabel(new RulePicture.SquareIcon(type)), BorderLayout.LINE_START);
                row.add(new Widgets.Paragraph("<b>" + name + "</b><br>" + meaning, Theme.Fonts.BASE, Theme.text(), 300),
                        BorderLayout.CENTER);
                return row;
            }

            /**
             * A section heading.
             */
            private static JLabel heading(String value) {
                JLabel label = new JLabel(value);
                label.setFont(Theme.Fonts.HEADING);
                label.setForeground(Theme.neonCyan());
                label.setAlignmentX(start());
                // A little room on the right: bold text can be a pixel wider than measured, which would clip the last
                // letter.
                label.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 6));
                return label;
            }

            /**
             * A paragraph that wraps at the text-column width.
             */
            private static JComponent text(String html) {
                return new Widgets.Paragraph(html, Theme.Fonts.BASE, Theme.text(), TEXT_WIDTH);
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
            private static final Player.Color GREEN = Player.Color.PRESETS.get(0);
            private static final Player.Color YELLOW = Player.Color.PRESETS.get(1);
            private static final Player.Color BLUE = Player.Color.PRESETS.get(2);
            private static final Player.Color RED = Player.Color.PRESETS.get(3);

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
            /** The level whose rules are shown, or {@code null} for every level. */
            private final Game.Level level;

            /**
             * @param kind  which rule to draw
             * @param level the level whose rules are shown, or {@code null} for every level
             */
            RulePicture(Kind kind, Game.Level level) {
                this.kind = kind;
                this.level = level;
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
                g2.setColor(Theme.deepBackground());
                g2.fill(card);
                g2.setColor(Theme.gridColor());
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
                    tile(g2, 86 + i * TILE, 50, Theme.surfaceHighlight());
                }
                // The center of the board: four colored triangles meeting in the middle.
                double cx = 206;
                double cy = 65;
                double r = 26;
                Player.Color[] corners = {GREEN, YELLOW, BLUE, RED};
                for (int i = 0; i < 4; i++) {
                    Path2D triangle = new Path2D.Double();
                    triangle.moveTo(cx, cy);
                    triangle.lineTo(cx + r * Math.cos(Math.PI / 4 + i * Math.PI / 2 + Math.PI),
                            cy + r * Math.sin(Math.PI / 4 + i * Math.PI / 2 + Math.PI));
                    triangle.lineTo(cx + r * Math.cos(Math.PI / 4 + (i + 1) * Math.PI / 2 + Math.PI),
                            cy + r * Math.sin(Math.PI / 4 + (i + 1) * Math.PI / 2 + Math.PI));
                    triangle.closePath();
                    g2.setColor(Theme.mix(Theme.neonOf(corners[i]), Theme.deepBackground(), 0.6));
                    g2.fill(triangle);
                }
                Theme.Glow.paintHalo(g2, cx, cy, 20, Theme.pantherGold(), 0.8);
                piece(g2, cx, cy - 2, 24, GREEN);
                curvedArrow(g2, 46, 26, cx, 34, -26, Theme.pantherGold());
            }

            /**
             * One panther in each corner, numbered in clockwise order.
             */
            private void paintTurnOrder(Graphics2D g2) {
                double cx = WIDTH / 2.0;
                double cy = HEIGHT / 2.0;
                // A ring of arrows going clockwise.
                g2.setColor(Theme.alpha(Theme.neonCyan(), 0.8));
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 4; i++) {
                    double start = 90 - i * 90 - 18;
                    g2.draw(new Arc2D.Double(cx - 34, cy - 34, 68, 68, start, -54, Arc2D.OPEN));
                    double end = Math.toRadians(start - 54);
                    arrowHead(g2, cx + 34 * Math.cos(end), cy - 34 * Math.sin(end), end - Math.PI / 2,
                            Theme.neonCyan());
                }
                // One panther in each corner, in clockwise seat order starting top-left.
                piece(g2, cx - 76, cy - 34, 34, GREEN);
                piece(g2, cx + 76, cy - 34, 34, YELLOW);
                piece(g2, cx + 76, cy + 32, 34, BLUE);
                piece(g2, cx - 76, cy + 32, 34, RED);
                label(g2, "1", cx - 100, cy - 44, Theme.text());
                label(g2, "2", cx + 100, cy - 44, Theme.text());
                label(g2, "3", cx + 100, cy + 22, Theme.text());
                label(g2, "4", cx - 100, cy + 22, Theme.text());
            }

            /**
             * Step die × multiplier die with the worked formula.
             */
            private void paintDice(Graphics2D g2) {
                if (level == Game.Level.EASY) {
                    // Easy: only the step die, its number is the number of steps.
                    die(g2, 16, 24, 44, 4, Theme.neonPurple());
                    label(g2, Assets.Texts.get("rules.pic.fourSteps"), 76, 52, Theme.pantherGold());
                    smallText(g2, Assets.Texts.get("rules.pic.easyDice"), 16, 100);
                    return;
                }
                die(g2, 16, 24, 44, 4, Theme.neonPurple());
                label(g2, "×", 68, 52, Theme.text());
                numberDie(g2, 84, 24, 44, "3");
                label(g2, "4 × (3 − 1)", 140, 42, Theme.text());
                label(g2, Assets.Texts.get("rules.pic.eightSteps"), 140, 66, Theme.pantherGold());
                if (level == null) {
                    smallText(g2, Assets.Texts.get("rules.pic.easyDice"), 16, 100);
                }
                smallText(g2, Assets.Texts.get("rules.pic.otherDice"), 16, 118);
            }

            /**
             * A 6 brings a panther from the castle onto its start square.
             */
            private void paintSix(Graphics2D g2) {
                die(g2, 14, 14, 40, 6, Theme.neonPurple());
                castle(g2, 14, 62, 56, 56, GREEN, 1);
                tile(g2, 92, 74, Theme.mix(Theme.neonOf(GREEN), Theme.surfaceHighlight(), 0.38));
                centeredText(g2, "#", 92 + TILE / 2, 74 + TILE / 2);
                piece(g2, 92 + TILE / 2, 74 + TILE / 2 - 4, 26, GREEN);
                curvedArrow(g2, 52, 74, 96, 76, -22, Theme.pantherGold());
                label(g2, Assets.Texts.get("rules.pic.plusRoll"), 150, 40, Theme.pantherGold());
                smallText(g2, Assets.Texts.get("rules.pic.thirdSix"), 136, 100);
                smallText(g2, Assets.Texts.get("rules.pic.cancelled"), 136, 116);
            }

            /**
             * A panther moves four squares for a roll of 4.
             */
            private void paintMove(Graphics2D g2) {
                double y = 70;
                for (int i = 0; i < 6; i++) {
                    tile(g2, 20 + i * (TILE + 4), y, Theme.surfaceHighlight());
                }
                die(g2, 96, 12, 34, 4, Theme.neonPurple());
                // Ghost of the start position, real panther after four steps.
                ghost(g2, 20 + TILE / 2, y + TILE / 2 - 4, 26, BLUE);
                piece(g2, 20 + 4 * (TILE + 4) + TILE / 2, y + TILE / 2 - 4, 26, BLUE);
                curvedArrow(g2, 20 + TILE / 2, y - 2, 20 + 4 * (TILE + 4) + TILE / 2, y - 2, -24, Theme.neonCyan());
                smallText(g2, "1   2   3   4", 62, 120);
            }

            /**
             * A green panther lands on a red one, which flies back to its castle.
             */
            private void paintCapture(Graphics2D g2) {
                double y = 78;
                for (int i = 0; i < 4; i++) {
                    tile(g2, 14 + i * (TILE + 4), y, Theme.surfaceHighlight());
                }
                castle(g2, 176, 14, 50, 50, RED, 1);
                ghost(g2, 14 + TILE / 2, y + TILE / 2 - 4, 26, GREEN);
                piece(g2, 14 + 3 * (TILE + 4) + TILE / 2, y + TILE / 2 - 4, 26, GREEN);
                curvedArrow(g2, 14 + TILE / 2, y - 2, 14 + 3 * (TILE + 4) + TILE / 2 - 6, y - 4, -22, Theme.neonCyan());
                // The captured red panther flies back to its castle.
                curvedArrow(g2, 14 + 3 * (TILE + 4) + TILE / 2 + 10, y + 4, 182, 54, -20, Theme.error());
            }

            /**
             * Two blue panthers form a block; the red panther must stop before it.
             */
            private void paintBlock(Graphics2D g2) {
                double y = 74;
                for (int i = 0; i < 5; i++) {
                    tile(g2, 18 + i * (TILE + 12), y, Theme.surfaceHighlight());
                }
                double blockX = 18 + 3 * (TILE + 12) + TILE / 2;
                piece(g2, blockX - 5, y + TILE / 2 - 8, 24, BLUE);
                piece(g2, blockX + 5, y + TILE / 2 - 2, 24, BLUE);
                piece(g2, 18 + TILE / 2, y + TILE / 2 - 4, 26, RED);
                curvedArrow(g2, 18 + TILE / 2, y - 2, blockX - 26, y - 2, -24, Theme.neonCyan());
                cross(g2, blockX, y - 24, 9);
                smallText(g2, Assets.Texts.get("rules.pic.block"), 18, 122);
            }

            /**
             * A panther in its home lane needs exactly 2 to reach the center.
             */
            private void paintHome(Graphics2D g2) {
                double y = 50;
                Color lane = Theme.laneColor(Theme.colorOf(GREEN));
                tile(g2, 12, y, Theme.mix(Theme.neonOf(GREEN), Theme.surfaceHighlight(), 0.28));
                arrowHead(g2, 12 + TILE / 2 + 7, y + TILE / 2, 0, Theme.neonOf(GREEN));
                for (int i = 0; i < 5; i++) {
                    tile(g2, 12 + (i + 1) * (TILE + 2), y, lane);
                }
                double homeX = 12 + 6 * (TILE + 2) + TILE / 2;
                Theme.Glow.paintHalo(g2, homeX, y + TILE / 2, 22, Theme.pantherGold(), 0.8);
                g2.setColor(Theme.pantherGold());
                g2.fill(new Ellipse2D.Double(homeX - 6, y + TILE / 2 - 6, 12, 12));
                double pieceX = 12 + 4 * (TILE + 2) + TILE / 2;
                piece(g2, pieceX, y + TILE / 2 - 4, 26, GREEN);
                curvedArrow(g2, pieceX, y - 2, homeX, y - 2, -18, Theme.pantherGold());
                die(g2, pieceX + 4, 92, 26, 2, Theme.pantherGold());
                smallText(g2, Assets.Texts.get("rules.pic.exactly"), 64, 110);
            }

            /**
             * A question square with a four-answer card.
             */
            private void paintQuestion(Graphics2D g2) {
                // The same question tile as on the board (here in the medium-question color).
                BoardPanel.BoardArt.NeonTiles.paint(g2, BoardPanel.BoardArt.NeonTiles.Kind.QUESTION, 18 + TILE / 2,
                        50 + TILE / 2, TILE + 4, Theme.questionColor(Game.Level.MEDIUM));
                RoundRectangle2D card = new RoundRectangle2D.Double(66, 10, 160, 86, 14, 14);
                g2.setColor(Theme.surface());
                g2.fill(card);
                Theme.Glow.paintGlowStroke(g2, card, Theme.neonCyan(), 1.4f, 0.4);
                smallText(g2, Assets.Texts.get("rules.pic.question"), 78, 30);
                String[] answers = {"A", "B", "C", "D"};
                for (int i = 0; i < 4; i++) {
                    double x = 78 + (i % 2) * 72;
                    double y = 40 + (i / 2) * 26;
                    RoundRectangle2D option = new RoundRectangle2D.Double(x, y, 64, 20, 10, 10);
                    g2.setColor(i == 1 ? Theme.alpha(Theme.neonOf(GREEN), 0.35) : Theme.surfaceHighlight());
                    g2.fill(option);
                    smallText(g2, answers[i], x + 28, y + 15);
                }
                smallText(g2, Assets.Texts.get("rules.pic.rightWrong"), 66, 116);
            }

            /**
             * A surprise square bursting with light.
             */
            private void paintSurprise(Graphics2D g2) {
                double cx = WIDTH / 2.0;
                double cy = HEIGHT / 2.0 - 4;
                // Rays bursting out of the "!" square.
                g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Color[] rays = {Theme.pantherGold(), Theme.neonPurple(), Theme.neonCyan()};
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    g2.setColor(rays[i % rays.length]);
                    g2.draw(new Line2D.Double(cx + 26 * Math.cos(a), cy + 26 * Math.sin(a),
                            cx + (i % 2 == 0 ? 46 : 38) * Math.cos(a), cy + (i % 2 == 0 ? 46 : 38) * Math.sin(a)));
                }
                // The same gift tile as on the board.
                BoardPanel.BoardArt.NeonTiles.paint(g2, BoardPanel.BoardArt.NeonTiles.Kind.SURPRISE, cx, cy, TILE + 6, null);
                smallText(g2, Assets.Texts.get("rules.pic.events"), cx - 70, 122);
            }

            /**
             * The countdown ring and the two timer limits.
             */
            private void paintTimer(Graphics2D g2) {
                double cx = 70;
                double cy = 62;
                double r = 38;
                g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(Theme.alpha(Theme.warning(), 0.2));
                g2.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                Theme.Glow.paintGlowStroke(g2, new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 90, 360 * 0.6, Arc2D.OPEN),
                        Theme.warning(), 6f, 0.6);
                Theme.Panther.paintAvatar(g2, cx, cy, 48, Theme.colorOf(BLUE), 0.2);
                smallText(g2, "0:18", cx - 14, cy + r + 20);
                label(g2, Assets.Texts.get("rules.pic.idle30"), 130, 48, Theme.warning());
                smallText(g2, Assets.Texts.get("rules.pic.countdown"), 130, 66);
                label(g2, Assets.Texts.get("rules.pic.idle60"), 130, 92, Theme.error());
                smallText(g2, Assets.Texts.get("rules.pic.computer"), 130, 110);
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
                g2.setColor(Theme.gridColor());
                g2.draw(square);
            }

            /**
             * A castle in a player's color with some panthers still inside.
             */
            private static void castle(Graphics2D g2, double x, double y, double w, double h, Player.Color color,
                                           int pieces) {
                RoundRectangle2D box = new RoundRectangle2D.Double(x, y, w, h, 14, 14);
                Color neon = Theme.neonOf(color);
                g2.setColor(Theme.dim(neon));
                g2.fill(box);
                Theme.Glow.paintGlowStroke(g2, box, neon, 1.4f, 0.45);
                for (int i = 0; i < pieces; i++) {
                    double px = x + w / 2 + (pieces == 1 ? 0 : (i % 2 == 0 ? -w / 4.5 : w / 4.5));
                    double py = y + h / 2 + (pieces <= 2 ? 0 : (i < 2 ? -h / 5 : h / 5));
                    piece(g2, px, py - 2, Math.min(w, h) * (pieces == 1 ? 0.5 : 0.36), color);
                }
            }

            /**
             * The same 3D panther piece as on the board.
             */
            private static void piece(Graphics2D g2, double cx, double cy, double size, Player.Color color) {
                Theme.Panther.paintFigure(g2, cx, cy + size * 0.42, size * 0.85, Theme.colorOf(color), 1);
            }

            /**
             * A faded panther showing where a piece was before it moved.
             */
            private static void ghost(Graphics2D g2, double cx, double cy, double size, Player.Color color) {
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
                g2.setColor(Theme.surfaceHighlight());
                g2.fill(face);
                Theme.Glow.paintGlowStroke(g2, face, accent, 1.6f, 0.5);
                int[][] pips = {{}, {4}, {0, 8}, {0, 4, 8}, {0, 2, 6, 8}, {0, 2, 4, 6, 8}, {0, 2, 3, 5, 6, 8}};
                double pip = size / 6.5;
                g2.setColor(Theme.text());
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
                g2.setColor(Theme.surfaceHighlight());
                g2.fill(face);
                Theme.Glow.paintGlowStroke(g2, face, Theme.pantherGold(), 1.6f, 0.5);
                g2.setFont(Theme.Fonts.baseFont((float) (size * 0.45), true));
                g2.setColor(Theme.pantherGold());
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
                g2.setColor(Theme.error());
                g2.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Double(cx - r, cy - r, cx + r, cy + r));
                g2.draw(new Line2D.Double(cx - r, cy + r, cx + r, cy - r));
            }

            /**
             * Bold text with its baseline at (x, y).
             */
            private static void label(Graphics2D g2, String text, double x, double y, Color color) {
                g2.setFont(Theme.Fonts.baseFont(16, true));
                g2.setColor(color);
                g2.drawString(text, (float) x, (float) y);
            }

            /**
             * Small caption text (12 pt, NFR-03) with its baseline at (x, y).
             */
            private static void smallText(Graphics2D g2, String text, double x, double y) {
                g2.setFont(Theme.Fonts.baseFont(12, true));
                g2.setColor(Theme.textMuted());
                g2.drawString(text, (float) x, (float) y);
            }

            /**
             * Bold square symbol such as "#", "?" or "!" centered on (cx, cy).
             */
            private static void centeredText(Graphics2D g2, String text, double cx, double cy) {
                g2.setFont(Theme.Fonts.baseFont(16, true));
                g2.setColor(Theme.text());
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
                private static final Player.Color SAMPLE = Player.Color.PRESETS.get(0);

                private final Square.Type type;

                /**
                 * @param type the kind of square to draw
                 */
                SquareIcon(Square.Type type) {
                    this.type = type;
                }

                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.translate(x, y);
                    Color seat = Theme.neonOf(SAMPLE);
                    if (type == Square.Type.SURPRISE || type == Square.Type.QUESTION || type == Square.Type.START) {
                        // Exactly the tiles of the board: the gift, the "?" and the "#" in their neon colors.
                        BoardPanel.BoardArt.NeonTiles.Kind kind = type == Square.Type.SURPRISE
                                ? BoardPanel.BoardArt.NeonTiles.Kind.SURPRISE : type == Square.Type.QUESTION
                                ? BoardPanel.BoardArt.NeonTiles.Kind.QUESTION : BoardPanel.BoardArt.NeonTiles.Kind.START;
                        Color accent = type == Square.Type.QUESTION ? Theme.questionColor(Game.Level.MEDIUM) : seat;
                        BoardPanel.BoardArt.NeonTiles.paint(g2, kind, SIZE / 2.0, SIZE / 2.0, SIZE - 4, accent);
                        g2.dispose();
                        return;
                    }
                    RoundRectangle2D tile = new RoundRectangle2D.Double(1, 1, SIZE - 2, SIZE - 2, 8, 8);
                    Color fill = switch (type) {
                        case START -> Theme.mix(seat, Theme.surfaceHighlight(), 0.38);
                        case ARROW -> Theme.mix(seat, Theme.surfaceHighlight(), 0.28);
                        case HOME_LANE -> Theme.laneColor(seat);
                        case FINISH -> Theme.deepBackground();
                        default -> Theme.surfaceHighlight();
                    };
                    g2.setColor(fill);
                    g2.fill(tile);
                    g2.setStroke(new BasicStroke(1f));
                    g2.setColor(Theme.gridColor());
                    g2.draw(tile);
                    double cx = SIZE / 2.0;
                    double cy = SIZE / 2.0;
                    switch (type) {
                        case START -> {
                            Theme.Glow.paintGlowStroke(g2, tile, seat, 1.4f, 0.5);
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
                        case HOME_LANE -> Theme.Glow.paintGlowStroke(g2, tile, seat, 1f, 0.25);
                        case FINISH -> {
                            Theme.Glow.paintHalo(g2, cx, cy, 14, Theme.pantherGold(), 0.9);
                            g2.setColor(Theme.pantherGold());
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
                    g2.setFont(Theme.Fonts.baseFont(17, true));
                    g2.setColor(Theme.text());
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
                    g2.setColor(Theme.starColor());
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

    /**
     * The window in which the admin writes a new question or changes an existing one (FR-65, FR-66): question text, four
     * answers with the correct one marked, explanation, difficulty and topic. Save is accepted only when the model finds
     * no problem ({@link model.Question#problems}); otherwise every problem is listed in the window (NFR-07, NFR-25).
     */
    public static final class QuestionEditor extends JDialog {

        private final JTextArea text = area(3);
        private final List<Widgets.TextField> answers = new ArrayList<>();
        private final List<JRadioButton> correct = new ArrayList<>();
        private final JTextArea explanation = area(3);
        private final JComboBox<Game.Level> level = new JComboBox<>(Game.Level.values());
        private final JComboBox<model.Question.Topic> topic = new JComboBox<>(model.Question.Topic.values());
        private final Widgets.Paragraph problems = new Widgets.Paragraph(" ", Theme.Fonts.BASE, Theme.error(), 560);
        private final int id;
        private model.Question result;

        /**
         * @param owner    the admin screen
         * @param existing the question to edit, or {@code null} for a new one
         */
        private QuestionEditor(Window owner, model.Question existing) {
            super(owner, Assets.Texts.get(existing == null ? "editor.addTitle" : "editor.editTitle"),
                    ModalityType.APPLICATION_MODAL);
            this.id = existing == null ? 0 : existing.id();
            JPanel content = new JPanel(new GridBagLayout());
            content.setBackground(Theme.surface());
            content.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 0;
            c.gridwidth = 2;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            c.insets = new Insets(2, 0, 2, 0);

            content.add(caption(Assets.Texts.get("editor.text", model.Question.TEXT_MIN, model.Question.TEXT_MAX)), c);
            c.gridy++;
            content.add(scroll(text), c);

            c.gridy++;
            c.insets = new Insets(10, 0, 2, 0);
            content.add(caption(Assets.Texts.get("editor.answers", model.Question.ANSWER_MAX)), c);
            c.insets = new Insets(2, 0, 2, 0);
            ButtonGroup group = new ButtonGroup();
            for (int i = 0; i < model.Question.ANSWER_COUNT; i++) {
                JRadioButton mark = new JRadioButton(Assets.Texts.get("editor.correct"));
                mark.setOpaque(false);
                mark.setForeground(Theme.text());
                mark.setFont(Theme.Fonts.BASE);
                group.add(mark);
                correct.add(mark);
                Widgets.TextField answer = new Widgets.TextField(36, Assets.Texts.get("editor.answer", i + 1));
                answers.add(answer);
                c.gridy++;
                c.gridwidth = 1;
                c.gridx = 0;
                c.weightx = 1;
                content.add(answer, c);
                c.gridx = 1;
                c.weightx = 0;
                c.insets = new Insets(2, 8, 2, 0);
                content.add(mark, c);
                c.insets = new Insets(2, 0, 2, 0);
            }
            c.gridx = 0;
            c.gridwidth = 2;
            c.weightx = 1;

            c.gridy++;
            c.insets = new Insets(10, 0, 2, 0);
            content.add(caption(Assets.Texts.get("editor.explanation", model.Question.EXPLANATION_MAX)), c);
            c.insets = new Insets(2, 0, 2, 0);
            c.gridy++;
            content.add(scroll(explanation), c);

            c.gridy++;
            c.insets = new Insets(10, 0, 2, 0);
            JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
            choices.setOpaque(false);
            choices.add(caption(Assets.Texts.get("editor.level")));
            level.setRenderer(renderer());
            topic.setRenderer(renderer());
            level.setFont(Theme.Fonts.BASE);
            topic.setFont(Theme.Fonts.BASE);
            choices.add(level);
            choices.add(Box.createHorizontalStrut(12));
            choices.add(caption(Assets.Texts.get("editor.topic")));
            choices.add(topic);
            content.add(choices, c);

            c.gridy++;
            problems.setFont(Theme.Fonts.BASE);
            problems.setForeground(Theme.error());
            content.add(problems, c);

            Widgets.Button save = new Widgets.Button(Assets.Texts.get("common.save"), Theme.neonPurple(), true);
            Widgets.Button cancel = new Widgets.Button(Assets.Texts.get("common.cancel"), Theme.neonCyan(), false);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 6, 0));
            buttons.setOpaque(false);
            buttons.add(cancel);
            buttons.add(save);
            c.gridy++;
            c.insets = new Insets(10, 0, 0, 0);
            content.add(buttons, c);

            if (existing != null) {
                text.setText(existing.text());
                for (int i = 0; i < model.Question.ANSWER_COUNT; i++) {
                    answers.get(i).setText(existing.answers().get(i));
                }
                correct.get(existing.correctIndex()).setSelected(true);
                explanation.setText(existing.explanation());
                level.setSelectedItem(existing.level());
                topic.setSelectedItem(existing.topic());
            }

            save.addActionListener(e -> save());
            cancel.addActionListener(e -> dispose());
            getRootPane().setDefaultButton(null);
            setContentPane(Widgets.ScreenFit.wrapInScroll(content, Theme.surface()));
            Assets.Texts.applyOrientation(this);
            pack();
            setMinimumSize(new Dimension(640, getHeight()));
            setLocationRelativeTo(owner);
            Widgets.ScreenFit.keepOnScreen(this);
        }

        /**
         * Opens the editor and waits until it is closed.
         *
         * @param parent   a component of the admin screen
         * @param existing the question to edit, or {@code null} to write a new one
         * @return the saved question (id 0 for a new one; the bank gives it its id), or empty if cancelled
         */
        public static Optional<model.Question> show(Component parent, model.Question existing) {
            QuestionEditor dialog = new QuestionEditor(SwingUtilities.getWindowAncestor(parent), existing);
            dialog.setVisible(true);
            return Optional.ofNullable(dialog.result);
        }

        /**
         * Checks the input with the model's rules; closes the window only if there is no problem.
         */
        private void save() {
            List<String> typed = answers.stream().map(JTextField::getText).toList();
            int chosen = -1;
            for (int i = 0; i < correct.size(); i++) {
                if (correct.get(i).isSelected()) {
                    chosen = i;
                }
            }
            Game.Level l = (Game.Level) level.getSelectedItem();
            model.Question.Topic t = (model.Question.Topic) topic.getSelectedItem();
            List<model.Question.Problem> found = model.Question.problems(text.getText(), typed, chosen, explanation.getText(), l, t);
            if (!found.isEmpty()) {
                StringBuilder message = new StringBuilder();
                found.forEach(p -> message.append("• ").append(Assets.Texts.get("editor.problem." + p.name(),
                        model.Question.TEXT_MIN, model.Question.TEXT_MAX, model.Question.ANSWER_MAX, model.Question.EXPLANATION_MAX)).append('\n'));
                problems.setText(message.toString().strip());
                pack();
                Widgets.ScreenFit.keepOnScreen(this);
                return;
            }
            result = new model.Question(id, text.getText(), typed, chosen, explanation.getText(), l, t);
            dispose();
        }

        /**
         * Shows levels and topics by their translated names.
         */
        private static ListCellRenderer<Object> renderer() {
            return new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                              boolean focus) {
                    Object shown = value instanceof Game.Level lv ? Assets.Texts.level(lv)
                            : value instanceof model.Question.Topic tp ? Assets.Texts.topic(tp) : value;
                    return super.getListCellRendererComponent(list, shown, index, selected, focus);
                }
            };
        }

        /**
         * A multi-line text field that wraps words.
         */
        private static JTextArea area(int rows) {
            JTextArea area = new JTextArea(rows, 40);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setFont(Theme.Fonts.BASE);
            area.setBackground(Theme.surfaceHighlight());
            area.setForeground(Theme.text());
            area.setCaretColor(Theme.text());
            area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            return area;
        }

        /**
         * Puts a text area in a thin frame.
         */
        private static JScrollPane scroll(JTextArea area) {
            JScrollPane pane = new JScrollPane(area);
            pane.setBorder(BorderFactory.createLineBorder(Theme.gridColor()));
            return pane;
        }

        /**
         * Small grey heading.
         */
        private static JLabel caption(String value) {
            JLabel label = new JLabel(value);
            label.setFont(Theme.Fonts.SMALL);
            label.setForeground(Theme.textMuted());
            return label;
        }
    }

    /**
     * Standard dialogs in the chosen language. Error dialogs always say what went wrong and what to do (NFR-07).
     * Long texts are wrapped, Hebrew and Arabic dialogs are laid out right to left, and the buttons (Yes, No, OK) are the
     * game's own neon buttons, so they look like the rest of the app.
     */
    public static final class Message {

        /** Width at which dialog texts wrap. */
        private static final int WIDTH = 440;

        /**
         * Only static helpers; no objects of this class are created.
         */
        private Message() {
        }

        /**
         * @param parent  owner component
         * @param problem what went wrong
         * @param fix     what the user can do about it
         */
        public static void error(Component parent, String problem, String fix) {
            show(parent, problem + "\n\n" + fix, Assets.Texts.get("common.problem"), JOptionPane.ERROR_MESSAGE,
                    new Object[]{Assets.Texts.get("common.ok")});
        }

        /**
         * Shown for unexpected failures instead of crashing (NFR-24).
         *
         * @param parent owner component
         * @param error  the failure
         */
        public static void unexpectedError(Component parent, Throwable error) {
            error(parent, Assets.Texts.get("common.unexpected", error.getMessage()), Assets.Texts.get("common.unexpectedFix"));
        }

        /**
         * @param parent  owner component
         * @param message information to show
         */
        public static void info(Component parent, String message) {
            show(parent, message, Assets.Texts.get("app.window"), JOptionPane.INFORMATION_MESSAGE,
                    new Object[]{Assets.Texts.get("common.ok")});
        }

        /**
         * @param parent   owner component
         * @param question yes/no question
         * @return {@code true} if the user confirmed
         */
        public static boolean confirm(Component parent, String question) {
            Object[] options = {Assets.Texts.get("common.yes"), Assets.Texts.get("common.no")};
            return show(parent, question, Assets.Texts.get("common.confirm"), JOptionPane.WARNING_MESSAGE, options) == 0;
        }

        /**
         * Shows a modern pop-up card: the dark neon card of the question window, a round badge (! for a problem, ? for a
         * question, i for information), the first paragraph as a big heading, the rest as text, and gradient buttons
         * like the main menu. It has no system title bar; it closes with its buttons (Esc counts as the last one).
         *
         * @return the index of the pressed button, or -1 if the dialog was closed
         */
        private static int show(Component parent, String text, String title, int type, Object[] options) {
            String heading = text;
            String body = "";
            // The first paragraph is the heading (Hebrew and Arabic lines may start with an invisible direction mark).
            String[] parts = text.split("\n\u200f?\n", 2);
            if (parts.length == 2) {
                heading = parts[0];
                body = parts[1].replace("\u200f", "").strip();
            }
            Color accent = type == JOptionPane.ERROR_MESSAGE ? new Color(0xFF4D6D)
                    : type == JOptionPane.WARNING_MESSAGE ? new Color(0xFFC83D) : NEON_CYAN;
            String symbol = type == JOptionPane.ERROR_MESSAGE ? "!" : type == JOptionPane.WARNING_MESSAGE ? "?" : "i";

            Window owner = parent == null ? null : parent instanceof Window w ? w
                    : SwingUtilities.getWindowAncestor(parent);
            JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setUndecorated(true);
            Question.NeonBox card = new Question.NeonBox(new BorderLayout(18, 0));
            card.setBorder(BorderFactory.createEmptyBorder(28, 30, 26, 30));
            card.add(new Badge(symbol, accent), BorderLayout.LINE_START);

            JPanel texts = new JPanel();
            texts.setOpaque(false);
            texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
            float start = Assets.Texts.language().isRightToLeft() ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT;
            Widgets.Paragraph big = new Widgets.Paragraph(heading, Theme.Fonts.baseFont(21, true), Color.WHITE, WIDTH);
            big.setAlignmentX(start);
            texts.add(big);
            if (!body.isEmpty()) {
                texts.add(Box.createVerticalStrut(8));
                Widgets.Paragraph small = new Widgets.Paragraph(body, Theme.Fonts.baseFont(16, false), new Color(0xC9D0F5),
                        WIDTH);
                small.setAlignmentX(start);
                texts.add(small);
            }
            texts.add(Box.createVerticalStrut(20));
            int[] chosen = {-1};
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 10, 0));
            buttons.setOpaque(false);
            buttons.setAlignmentX(start);
            Color[][] colors = {{new Color(0x8B5CF6), new Color(0xFF4FB8)}, {new Color(0x2E9BFF), new Color(0x2CE5FF)}};
            Widgets.Button first = null;
            for (int i = 0; i < options.length; i++) {
                int index = i;
                Widgets.Button button = new Widgets.Button(String.valueOf(options[i]), colors[Math.min(i, 1)][0], true);
                button.setGradient(colors[Math.min(i, 1)][0], colors[Math.min(i, 1)][1]);
                button.setFont(Theme.Fonts.baseFont(17, true));
                button.setBorder(BorderFactory.createEmptyBorder(10, 34, 10, 34));
                button.addActionListener(e -> {
                    chosen[0] = index;
                    dialog.dispose();
                });
                buttons.add(button);
                if (first == null) {
                    first = button;
                }
            }
            texts.add(buttons);
            card.add(texts, BorderLayout.CENTER);
            // Esc counts as the last button (No / OK).
            card.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close");
            card.getActionMap().put("close", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    chosen[0] = options.length == 1 ? 0 : options.length - 1;
                    dialog.dispose();
                }
            });
            dialog.setContentPane(Widgets.ScreenFit.wrapInScroll(card, Theme.surface()));
            Assets.Texts.applyOrientation(dialog);
            dialog.getRootPane().setDefaultButton(first);
            Widgets.ScreenFit.packAndPlace(dialog, parent);
            dialog.setVisible(true);
            return chosen[0];
        }

        /**
         * The round badge of a pop-up message: a glowing colored disc with a big symbol.
         */
        private static final class Badge extends JComponent {

            private static final int SIZE = 64;
            private final String symbol;
            private final Color color;

            /**
             * @param symbol "!", "?" or "i"
             * @param color  the disc color
             */
            Badge(String symbol, Color color) {
                this.symbol = symbol;
                this.color = color;
                setPreferredSize(new Dimension(SIZE + 8, SIZE + 8));
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                double cx = getWidth() / 2.0;
                double cy = SIZE / 2.0 + 4;
                Theme.Glow.paintHalo(g2, cx, cy, SIZE * 0.85, color, 0.55);
                java.awt.geom.Ellipse2D disc = new java.awt.geom.Ellipse2D.Double(cx - SIZE / 2.0, cy - SIZE / 2.0,
                        SIZE, SIZE);
                g2.setPaint(new GradientPaint(0, (float) (cy - SIZE / 2.0), Theme.mix(color, Color.WHITE, 0.7), 0,
                        (float) (cy + SIZE / 2.0), color));
                g2.fill(disc);
                g2.setColor(Theme.mix(color, Color.WHITE, 0.5));
                g2.setStroke(new BasicStroke(2f));
                g2.draw(disc);
                Font font = Theme.Fonts.comicFont(40f);
                java.awt.font.TextLayout layout = new java.awt.font.TextLayout(symbol, font, g2.getFontRenderContext());
                java.awt.geom.Rectangle2D b = layout.getBounds();
                g2.setColor(new Color(0x0B1033));
                layout.draw(g2, (float) (cx - b.getCenterX()), (float) (cy - b.getCenterY()));
                g2.dispose();
            }
        }
    }
}
