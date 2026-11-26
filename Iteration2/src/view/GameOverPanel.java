package view;

import model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.font.TextLayout;
import java.awt.geom.*;
import java.util.*;
import java.util.List; // java.util.List, not java.awt.List

/**
 * The victory screen (FR-55): a comic "VICTORY!" burst with confetti, the podium with every player's panther, "who ate
 * who" (the food chain of the match) and a table of match statistics, plus the level, game time and whether the game
 * was saved to the history. It follows the appearance (dark or light).
 */
public final class GameOverPanel extends Widgets.Background {

    /**
     * Receives the choices made on the game-over screen.
     */
    public interface Listener {

        /**
         * New Game was pressed.
         */
        void newGameChosen();

        /**
         * Main Menu was pressed.
         */
        void menuChosen();

        /**
         * Game History was pressed.
         */
        void historyChosen();
    }

    /** Colors of the podium steps: gold, silver, bronze and purple. */
    private static final Color[] STEP_COLORS = {new Color(0xF5C542), new Color(0xC9CCD6), new Color(0xC98A52),
            new Color(0x9B7BFF)};

    private final FactTile levelChip = new FactTile("tile_dice", Theme.neonPurple());
    private final FactTile timeChip = new FactTile(null, Theme.neonCyan());
    private final FactTile playersChip = new FactTile("icon_players", new Color(0xFF4FB8));
    private final FactTile gameChip = new FactTile("icon_game_number", Theme.neonCyan());
    private final FactTile savedChip = new FactTile(null, Theme.success());
    private int gameId = -1;
    private final PodiumArea stage = new PodiumArea();
    private final StatsTable stats = new StatsTable();
    private final Widgets.Button newGame = new Widgets.Button(Assets.Texts.get("menu.newGame"), Theme.neonPurple(), true);
    private final Widgets.Button menu = new Widgets.Button(Assets.Texts.get("admin.mainMenu"), Theme.neonCyan(), false);
    private final Widgets.Button history = new Widgets.Button(Assets.Texts.get("menu.history"), Theme.neonCyan(), false);

    /**
     * Builds the screen.
     */
    public GameOverPanel() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 14, 16));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        // The facts of the game as a row of neon chips: level, time, players, game number and "saved".
        JPanel infos = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 4));
        infos.setOpaque(false);
        for (FactTile chip : new FactTile[]{levelChip, timeChip, playersChip, gameChip, savedChip}) {
            infos.add(chip);
        }
        savedChip.setVisible(false);
        top.add(infos, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        buttons.setOpaque(false);
        history.setIconName("icon_history");
        menu.setIconName("square_home");
        // The same comic gradient style as New Game.
        menu.setGradient(new Color(0x2CE5FF), new Color(0x8B5CF6));
        history.setGradient(new Color(0x2E9BFF), new Color(0x2CE5FF));
        newGame.setGradient(new Color(0x8B5CF6), new Color(0xFF4FB8));
        newGame.setIconName("feature_dice_king");
        for (Widgets.Button button : new Widgets.Button[]{menu, history, newGame}) {
            button.setFont(Theme.Fonts.BOLD);
            button.setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
            buttons.add(button);
        }
        // Room for the Settings gear that floats in this corner.
        buttons.add(Box.createHorizontalStrut(Widgets.CORNER_ROOM - 8));
        top.add(buttons, BorderLayout.LINE_END);
        add(top, BorderLayout.NORTH);
        add(stage, BorderLayout.CENTER);
        add(stats, BorderLayout.SOUTH);
        Theme.Motion.subscribe(stage);
    }


    /**
     * Says whether the finished game was saved to the game history.
     *
     * @param gameId the game's number in the history
     * @param saved  {@code false} if the history file could not be written
     */
    public void showSaved(int gameId, boolean saved) {
        this.gameId = gameId;
        gameChip.setVisible(true);
        savedChip.setFact(null, Assets.Texts.get(saved ? "over.savedShort" : "over.notSaved", gameId),
                saved ? Theme.success() : Theme.error(), saved ? FactTile.CHECK : FactTile.CROSS);
        savedChip.setVisible(true);
        gameChip.setFact(Assets.Texts.get("over.chip.game"), "#" + gameId, null, 0);
    }

    /**
     * Says that the match was quit before it was decided, so it was not saved to the game history (FR-56).
     */
    public void showQuit() {
        gameId = -1;
        savedChip.setFact(null, Assets.Texts.get("over.quitNotSaved"), Theme.error(), FactTile.CROSS);
        savedChip.setVisible(true);
        gameChip.setVisible(false);
    }

    /**
     * Shows the result of a finished match: burst, podium, who ate who, statistics and game time. A match that was
     * quit before anybody finished shows "No winner" and every panther side by side, without places.
     *
     * @param game the finished match
     */
    public void showResult(Game.State game) {
        String level = Assets.Texts.level(game.level()).toUpperCase(Locale.ROOT);
        levelChip.setFact(Assets.Texts.get("over.chip.level"), Assets.Texts.level(game.level()), null, 0);
        timeChip.setFact(Assets.Texts.get("over.chip.time"), Assets.Texts.formatDuration(game.elapsed()), null, FactTile.CLOCK);
        playersChip.setFact(Assets.Texts.get("over.chip.players"), String.valueOf(game.players().size()), null, 0);
        gameChip.setVisible(gameId > 0);
        gameChip.setFact(Assets.Texts.get("over.chip.game"), gameId < 0 ? "-" : "#" + gameId, null, 0);
        String caption = game.winner()
                .map(winner -> Assets.Texts.get(game.wasQuitEarly() ? "over.winsQuit" : "over.winsLevel", winner.name(),
                        level))
                .orElse(Assets.Texts.get("over.nobodyFinished", level));
        stage.show(game, caption);
        stats.show(game);
        revalidate();
        repaint();
    }

    /**
     * Connects the screen to its controller.
     *
     * @param listener receives what the user does on this screen
     */
    public void setListener(Listener listener) {
        setOnNewGame(listener::newGameChosen);
        setOnMenu(listener::menuChosen);
        setOnHistory(listener::historyChosen);
    }

    /**
     * @param handler called when Game History is pressed
     */
    private void setOnHistory(Runnable handler) {
        history.addActionListener(e -> handler.run());
    }

    /**
     * @param handler called when New Game is pressed
     */
    private void setOnNewGame(Runnable handler) {
        newGame.addActionListener(e -> handler.run());
    }

    /**
     * @param handler called when Main Menu is pressed
     */
    private void setOnMenu(Runnable handler) {
        menu.addActionListener(e -> handler.run());
    }

    // ------------------------------------------------------------------ comic drawing helpers

    /**
     * Comic letters with a thick black outline, a hard shadow and a fill, centered on (cx, cy) and shrunk to fit.
     */
    private static void comicText(Graphics2D g2, String text, double cx, double cy, double size, Paint fill,
                                  double maxWidth) {
        String shown = Assets.Texts.directional(text.toUpperCase(Locale.ROOT));
        TextLayout layout = new TextLayout(shown, Theme.Fonts.comicFont((float) size, shown), g2.getFontRenderContext());
        Shape outline = layout.getOutline(null);
        Rectangle2D b = outline.getBounds2D();
        double scale = Math.min(1, maxWidth / Math.max(1, b.getWidth()));
        AffineTransform t = AffineTransform.getTranslateInstance(cx, cy);
        t.scale(scale, scale);
        t.translate(-b.getCenterX(), -b.getCenterY());
        Shape letters = t.createTransformedShape(outline);
        g2.setColor(new Color(0, 0, 0, 120));
        g2.fill(AffineTransform.getTranslateInstance(size * 0.06, size * 0.08).createTransformedShape(letters));
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke((float) Math.max(3, size * 0.12), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g2.draw(letters);
        g2.setPaint(fill);
        g2.fill(letters);
    }

    /**
     * A spiky star.
     */
    private static Path2D drawStar(double cx, double cy, double rx, double ry, double inner, int spikes, long seed) {
        Random random = new Random(seed);
        Path2D p = new Path2D.Double();
        for (int i = 0; i < spikes * 2; i++) {
            double a = i * Math.PI / spikes - Math.PI / 2;
            double r = i % 2 == 0 ? 0.85 + 0.25 * random.nextDouble() : inner;
            double x = cx + Math.cos(a) * rx * r;
            double y = cy + Math.sin(a) * ry * r;
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.closePath();
        return p;
    }

    // ------------------------------------------------------------------ the stage

    /**
     * The upper part: confetti, the VICTORY! burst, the podium on the left and the food chain on the right.
     */
    private static final class PodiumArea extends JComponent {

        private static final int CONFETTI = 90;
        private final double[][] confetti = new double[CONFETTI][5];
        private List<Player> ranking = List.of();
        private List<Player> players = List.of();
        private Game.Stats stats;
        private String caption = "";
        private long shownAt;

        /**
         * Creates the podium stage and scatters its confetti.
         */
        PodiumArea() {
            setPreferredSize(new Dimension(1000, 470));
            Random random = new Random(11);
            for (double[] c : confetti) {
                c[0] = random.nextDouble();
                c[1] = random.nextDouble();
                c[2] = 0.05 + random.nextDouble() * 0.09;
                c[3] = random.nextDouble() * Math.PI;
                c[4] = random.nextInt(5);
            }
        }

        /**
         * @param game    the finished match
         * @param caption the headline under "Victory"
         */
        void show(Game.State game, String caption) {
            this.ranking = List.copyOf(game.ranking());
            this.players = List.copyOf(game.players());
            this.stats = game.stats();
            this.caption = caption;
            this.shownAt = System.nanoTime();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (players.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            double split = w * 0.56;
            // The podium (or, without a winner, the line-up) comes first in the reading direction: on the left, or on
            // the right in Hebrew and Arabic.
            if (ranking.isEmpty()) {
                paintLineUp(g2, Widgets.startX(this, 0, split), h * 0.34, split, h * 0.66);
            } else {
                paintConfetti(g2, w, h);
                paintPodium(g2, Widgets.startX(this, 0, split), h * 0.34, split, h * 0.66);
            }
            paintFoodChain(g2, Widgets.startX(this, split + 16, w - split - 18), h * 0.4, w - split - 18, h * 0.6 - 4);
            paintBurst(g2, w * 0.5, h * 0.14, Math.min(w * 0.22, 300), Math.min(h * 0.13, 70));
            g2.dispose();
        }

        /**
         * Draws the falling confetti.
         */
        private void paintConfetti(Graphics2D g2, int w, int h) {
            Color[] colors = {Theme.neonPurple(), Theme.neonCyan(), Theme.pantherGold(), Theme.error(),
                    Theme.success()};
            double t = Theme.Motion.secondsSinceStart();
            for (double[] c : confetti) {
                double y = (c[1] + t * c[2]) % 1.0;
                double x = c[0] + 0.01 * Math.sin(t * 1.3 + c[3] * 5);
                Graphics2D g = (Graphics2D) g2.create();
                g.translate(x * w, y * h);
                g.rotate(c[3] + t * (0.8 + c[2] * 6));
                g.setColor(Theme.alpha(colors[(int) c[4]], 0.85));
                g.fill(new Rectangle2D.Double(-5, -3, 10, 6));
                g.dispose();
            }
        }

        /**
         * The big VICTORY! burst (or a cool blue NO WINNER! burst) with black rays behind it and the caption box under
         * it; it pops in when shown.
         */
        private void paintBurst(Graphics2D g2, double cx, double cy, double rx, double ry) {
            double age = (System.nanoTime() - shownAt) / 1e9;
            double pop = age < 0.45 ? 1 - Math.pow(2, -10 * age / 0.45) * Math.cos(age / 0.45 * 3.5 * Math.PI) : 1;
            Graphics2D g = (Graphics2D) g2.create();
            g.translate(cx, cy);
            g.rotate(-0.03);
            g.scale(pop, pop);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.alpha(Theme.text(), 0.55));
            Random random = new Random(5);
            for (int i = 0; i < 28; i++) {
                double a = i * Math.PI * 2 / 28;
                double r1 = 1.05 + 0.1 * random.nextDouble();
                double r2 = 1.5 + 0.5 * random.nextDouble();
                g.draw(new Line2D.Double(Math.cos(a) * rx * r1, Math.sin(a) * ry * r1, Math.cos(a) * rx * r2,
                        Math.sin(a) * ry * r2));
            }
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D front = drawStar(0, 0, rx, ry, 0.74, 16, 8);
            boolean noWinner = ranking.isEmpty();
            g.setPaint(noWinner
                    ? new GradientPaint(0, (float) -ry, new Color(0xCFE8FF), 0, (float) ry, new Color(0x5B8CFF))
                    : new GradientPaint(0, (float) -ry, new Color(0xFFF2A0), 0, (float) ry, new Color(0xFFB627)));
            g.fill(front);
            Shape clip = g.getClip();
            g.clip(front);
            g.setColor(noWinner ? new Color(30, 60, 160, 60) : new Color(255, 140, 0, 70));
            for (double y = -ry; y < ry; y += 9) {
                for (double x = -rx; x < rx; x += 9) {
                    g.fill(new Ellipse2D.Double(x, y, 3.5, 3.5));
                }
            }
            g.setClip(clip);
            g.setColor(Color.BLACK);
            g.draw(front);
            comicText(g, Assets.Texts.get(noWinner ? "over.noWinner" : "over.victory"), 0, -ry * 0.08, ry * 1.25,
                    new GradientPaint(0, (float) -ry, Color.WHITE, 0, (float) ry,
                            noWinner ? new Color(0xBFD9FF) : new Color(0xFFE27A)), rx * 1.5);
            String shown = Assets.Texts.directional(caption.toUpperCase(Locale.ROOT));
            Font font = Theme.Fonts.comicFont((float) Math.max(16, ry * 0.42), shown);
            FontMetrics fm = g.getFontMetrics(font);
            double bw = Math.min(fm.stringWidth(shown) + 34, rx * 2.1);
            double bh = fm.getHeight() + 8;
            g.rotate(0.02);
            Rectangle2D box = new Rectangle2D.Double(-bw / 2, ry * 0.72, bw, bh);
            g.setColor(new Color(0, 0, 0, 110));
            g.fill(AffineTransform.getTranslateInstance(5, 6).createTransformedShape(box));
            g.setColor(new Color(0xFFFBEA));
            g.fill(box);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(3f));
            g.draw(box);
            g.setFont(font);
            double scale = Math.min(1, (bw - 20) / Math.max(1, fm.stringWidth(shown)));
            Graphics2D gt = (Graphics2D) g.create();
            gt.translate(0, box.getY() + 4 + fm.getAscent());
            gt.scale(scale, 1);
            gt.drawString(shown, (float) (-fm.stringWidth(shown) / 2.0), 0f);
            gt.dispose();
            g.dispose();
        }

        /**
         * 3D podium boxes (4th, 2nd, 1st, 3rd from the left) with each player's panther, name bubble and the crown.
         */
        private void paintPodium(Graphics2D g2, double x, double y, double width, double height) {
            int[] order = {4, 2, 1, 3};
            // Lower steps and figures leave room under the VICTORY! burst, so the winner's name stays visible.
            double[] heights = {0, 0.31, 0.24, 0.18, 0.13};
            int shown = 0;
            for (int place : order) {
                if (place <= ranking.size()) {
                    shown++;
                }
            }
            double depth = Math.min(22, width * 0.02);
            double gap = width * 0.03;
            double stepW = Math.min(190, (width - gap * (shown + 1) - depth * shown) / shown);
            double left = x + (width - shown * (stepW + depth) - (shown - 1) * gap) / 2;
            double floor = y + height - 4;
            for (int place : order) {
                if (place > ranking.size()) {
                    continue;
                }
                Player player = ranking.get(place - 1);
                Color color = STEP_COLORS[place - 1];
                double h = height * heights[place];
                double top = floor - h;
                Path2D side = new Path2D.Double();
                side.moveTo(left + stepW, top);
                side.lineTo(left + stepW + depth, top - depth);
                side.lineTo(left + stepW + depth, floor - depth);
                side.lineTo(left + stepW, floor);
                side.closePath();
                Path2D cap = new Path2D.Double();
                cap.moveTo(left, top);
                cap.lineTo(left + depth, top - depth);
                cap.lineTo(left + stepW + depth, top - depth);
                cap.lineTo(left + stepW, top);
                cap.closePath();
                Rectangle2D front = new Rectangle2D.Double(left, top, stepW, h);
                g2.setColor(Theme.mix(color, Color.BLACK, 0.62));
                g2.fill(side);
                g2.setColor(Theme.mix(color, Color.WHITE, 0.72));
                g2.fill(cap);
                g2.setPaint(new GradientPaint(0, (float) top, color, 0, (float) floor,
                        Theme.mix(color, Color.BLACK, 0.82)));
                g2.fill(front);
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(side);
                g2.draw(cap);
                g2.draw(front);
                comicText(g2, String.valueOf(place), left + stepW / 2, top + h * 0.52, Math.min(h * 0.62, 70),
                        Color.WHITE, stepW);

                double cx = left + stepW / 2 + depth / 2;
                double figure = Math.min(stepW * 0.6, height * (place == 1 ? 0.3 : 0.25));
                double feet = top - depth / 2;
                if (place == 1) {
                    Theme.Glow.paintHalo(g2, cx, feet - figure * 0.5, figure, Theme.pantherGold(), 0.5);
                }
                // Every panther sits; the winner is a little bigger and the others look toward it in the middle.
                Theme.Panther.paintPanther(g2, cx, feet, place == 1 ? figure * 1.08 : figure,
                        Theme.colorOf(player.color()), Theme.Panther.Pose.SIT,
                        place == 3 || place == 4 && order[0] != 4, 0);
                double bubbleY = feet - figure - 14;
                if (place == 1) {
                    paintCrown(g2, cx, feet - figure - 4);
                    bubbleY -= 28;
                }
                paintNameBubble(g2, player, cx, bubbleY);
                left += stepW + depth + gap;
            }
        }

        /**
         * Without a winner there is no podium: every player's panther sits on the same floor, in turn order, with its
         * name and how many panthers it brought home.
         */
        private void paintLineUp(Graphics2D g2, double x, double y, double width, double height) {
            int n = players.size();
            double slot = width / n;
            double floor = y + height - 34;
            g2.setColor(Theme.alpha(Theme.text(), 0.25));
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(x + slot * 0.2, floor + 2, x + width - slot * 0.2, floor + 2));
            boolean rtl = !getComponentOrientation().isLeftToRight();
            for (int i = 0; i < n; i++) {
                Player player = players.get(i);
                double cx = x + slot * ((rtl ? n - 1 - i : i) + 0.5);
                double figure = Math.min(slot * 0.55, height * 0.42);
                Theme.Panther.paintPanther(g2, cx, floor, figure, Theme.colorOf(player.color()),
                        Theme.Panther.Pose.SIT, cx > x + width / 2,
                        0);
                paintNameBubble(g2, player, cx, floor - figure - 10);
                String home = Assets.Texts.get("over.homeCount", player.finishedCount(), player.pieces().size());
                Font font = Theme.Fonts.baseFont(14, true);
                FontMetrics fm = g2.getFontMetrics(font);
                g2.setFont(font);
                g2.setColor(Theme.textMuted());
                g2.drawString(home, (float) (cx - fm.stringWidth(home) / 2.0), (float) (floor + 24));
            }
        }

        /**
         * Draws a player's name in a comic bubble above their panther.
         */
        private static void paintNameBubble(Graphics2D g2, Player player, double cx, double bottom) {
            String name = Assets.Texts.directional(player.name().toUpperCase(Locale.ROOT));
            Font font = Theme.Fonts.comicFont(20f, name);
            FontMetrics fm = g2.getFontMetrics(font);
            double w = Math.max(fm.getHeight() + 8, fm.stringWidth(name) + 22);
            double h = fm.getHeight() + 6;
            RoundRectangle2D bubble = new RoundRectangle2D.Double(cx - w / 2, bottom - h, w, h, h, h);
            g2.setColor(Theme.surface());
            g2.fill(bubble);
            g2.setColor(Theme.neonOf(player.color()));
            g2.setStroke(new BasicStroke(3f));
            g2.draw(bubble);
            g2.setFont(font);
            g2.setColor(Theme.text());
            g2.drawString(name, (float) (cx - fm.stringWidth(name) / 2.0), (float) (bottom - h + 3 + fm.getAscent()));
        }

        /**
         * Draws the glowing crown above the winner.
         */
        private static void paintCrown(Graphics2D g2, double cx, double bottom) {
            Theme.Glow.paintHalo(g2, cx, bottom - 14, 34, Theme.pantherGold(), 0.4 + 0.3 * Theme.Motion.pulse(0.8));
            Theme.drawIcon(g2, "icon_ranking", cx, bottom - 15, 40);
        }

        /**
         * The food chain: every player's avatar around the middle, an arrow from eater to victim with how often,
         * or "PEACEFUL GAME!" when nobody was eaten.
         */
        private void paintFoodChain(Graphics2D g2, double x, double y, double width, double height) {
            RoundRectangle2D card = new RoundRectangle2D.Double(x, y, width, height, 22, 22);
            g2.setColor(Theme.alpha(Theme.surface(), 0.94));
            g2.fill(card);
            g2.setColor(Theme.gridColor());
            g2.setStroke(new BasicStroke(2f));
            g2.draw(card);
            boolean rtl = !getComponentOrientation().isLeftToRight();
            comicText(g2, Assets.Texts.get("over.whoAteWho"), rtl ? x + width - 120 : x + 120, y + 30, 32,
                    Theme.pantherGold(), 210);
            g2.setFont(Theme.Fonts.BASE);
            g2.setColor(Theme.textMuted());
            String foodChain = Assets.Texts.get("over.foodChain");
            int chainWidth = g2.getFontMetrics().stringWidth(foodChain);
            g2.drawString(foodChain, (float) (rtl ? x + width - 22 - chainWidth : x + 22), (float) (y + 64));

            int n = players.size();
            double cx = x + width / 2;
            double cy = y + height * 0.58;
            double radius = Math.min(width * 0.3, height * 0.3);
            double avatar = Math.min(60, height * 0.22);
            Point2D[] at = new Point2D[n];
            for (int i = 0; i < n; i++) {
                double a = -Math.PI / 2 + i * 2 * Math.PI / n;
                at[i] = new Point2D.Double(cx + Math.cos(a) * radius * 1.25, cy + Math.sin(a) * radius);
            }
            int eats = 0;
            Player top = null;
            int best = 0;
            // First every arrow, then every "×n" label on top, so no arrow is ever drawn over a number.
            List<Runnable> labels = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Player eater = players.get(i);
                if (stats.captures(eater) > best) {
                    best = stats.captures(eater);
                    top = eater;
                }
                for (int j = 0; j < n; j++) {
                    int times = stats.captures(eater, players.get(j));
                    if (times > 0) {
                        eats += times;
                        labels.add(paintArrow(g2, at[i], at[j], avatar / 2 + 6, Theme.neonOf(eater.color()), times));
                    }
                }
            }
            labels.forEach(Runnable::run);
            for (int i = 0; i < n; i++) {
                Player player = players.get(i);
                Theme.Panther.paintAvatar(g2, at[i].getX(), at[i].getY(), avatar, Theme.colorOf(player.color()),
                        0.25);
                boolean rightSide = at[i].getX() >= cx - 1;
                String name = Assets.Texts.directional(player.name().toUpperCase(Locale.ROOT));
                String line = Assets.Texts.get("over.ateEaten", stats.captures(player), stats.timesCaptured(player));
                g2.setFont(Theme.Fonts.comicFont(20f, name));
                FontMetrics nf = g2.getFontMetrics();
                g2.setColor(Theme.text());
                g2.setFont(Theme.Fonts.SMALL);
                FontMetrics lf = g2.getFontMetrics();
                double tx = rightSide ? at[i].getX() + avatar / 2 + 10
                        : at[i].getX() - avatar / 2 - 10 - Math.max(nf.stringWidth(name), lf.stringWidth(line));
                g2.setFont(Theme.Fonts.comicFont(20f, name));
                g2.drawString(name, (float) tx, (float) (at[i].getY() - 2));
                g2.setFont(Theme.Fonts.SMALL);
                g2.setColor(Theme.textMuted());
                g2.drawString(line, (float) tx, (float) (at[i].getY() + lf.getAscent() + 2));
            }
            if (eats == 0) {
                // Nobody was eaten: no arrows, so the middle is free.
                Graphics2D g = (Graphics2D) g2.create();
                g.rotate(-0.06, cx, cy);
                comicText(g, Assets.Texts.get("over.peaceful"), cx, cy, 30, Theme.success(), width * 0.4);
                g.dispose();
            } else if (top != null) {
                // The top eater goes in the top corner across from the title, away from the arrows and their numbers
                // in the middle.
                String text = Assets.Texts.get("over.topEater", top.name());
                double maxWidth = width * 0.42;
                double textWidth = comicWidth(g2, text, 24, maxWidth);
                double icon = 30;
                double edge = 22;
                double textCx = rtl ? x + edge + textWidth / 2 : x + width - edge - textWidth / 2;
                double iconCx = rtl ? textCx + textWidth / 2 + 6 + icon / 2 : textCx - textWidth / 2 - 6 - icon / 2;
                double ty = y + 32;
                Graphics2D g = (Graphics2D) g2.create();
                g.rotate(-0.04, textCx, ty);
                Theme.drawIcon(g, "feature_star_player", iconCx, ty, icon);
                comicText(g, text, textCx, ty, 24, Theme.error(), maxWidth);
                g.dispose();
            }
        }

        /**
         * @return the width {@link #comicText} gives the text at that size, at most {@code maxWidth}
         */
        private static double comicWidth(Graphics2D g2, String text, double size, double maxWidth) {
            String shown = Assets.Texts.directional(text.toUpperCase(Locale.ROOT));
            TextLayout layout = new TextLayout(shown, Theme.Fonts.comicFont((float) size, shown), g2.getFontRenderContext());
            return Math.min(maxWidth, layout.getOutline(null).getBounds2D().getWidth());
        }

        /**
         * An arrow between two avatars, from the eater to the victim. The "×n" label is not drawn yet: it sits on the
         * eater's part of the arrow (not in the middle, where arrows between opposite players cross) and the caller
         * draws all labels after all arrows.
         *
         * @return draws the arrow's "×n" label
         */
        private static Runnable paintArrow(Graphics2D g2, Point2D from, Point2D to, double inset, Color color,
                                           int times) {
            double dx = to.getX() - from.getX();
            double dy = to.getY() - from.getY();
            double length = Math.hypot(dx, dy);
            if (length < 1) {
                return () -> { };
            }
            double ux = dx / length;
            double uy = dy / length;
            // Shift a little sideways so arrows in both directions do not overlap.
            double ox = -uy * 7;
            double oy = ux * 7;
            Point2D a = new Point2D.Double(from.getX() + ux * inset + ox, from.getY() + uy * inset + oy);
            Point2D b = new Point2D.Double(to.getX() - ux * inset + ox, to.getY() - uy * inset + oy);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(a, b));
            Path2D head = new Path2D.Double();
            head.moveTo(b.getX(), b.getY());
            head.lineTo(b.getX() - ux * 12 - uy * 6, b.getY() - uy * 12 + ux * 6);
            head.lineTo(b.getX() - ux * 12 + uy * 6, b.getY() - uy * 12 - ux * 6);
            head.closePath();
            g2.fill(head);
            return () -> {
                String label = "×" + times;
                g2.setFont(Theme.Fonts.comicFont(18f));
                g2.setStroke(new BasicStroke(2f));
                FontMetrics fm = g2.getFontMetrics();
                // A third of the way from the eater: arrows in both directions and arrows that cross in the middle
                // keep their numbers apart.
                double along = 0.34;
                double mx = a.getX() + (b.getX() - a.getX()) * along;
                double my = a.getY() + (b.getY() - a.getY()) * along;
                RoundRectangle2D pill = new RoundRectangle2D.Double(mx - fm.stringWidth(label) / 2.0 - 6,
                        my - fm.getHeight() / 2.0, fm.stringWidth(label) + 12, fm.getHeight(), 10, 10);
                g2.setColor(Theme.surface());
                g2.fill(pill);
                g2.setColor(color);
                g2.draw(pill);
                g2.setColor(Theme.text());
                g2.drawString(label, (float) (mx - fm.stringWidth(label) / 2.0),
                        (float) (my + fm.getAscent() / 2.0 - 1));
            };
        }
    }

    // ------------------------------------------------------------------ the statistics table

    /**
     * The match statistics: one tinted row per player, in ranking order.
     */
    private static final class StatsTable extends JComponent {

        private static final String[] COLUMNS = {"player", "place", "home", "chomps", "gotEaten", "quizRight",
                "quizWrong", "sixes", "squares", "surprises"};
        private static final double[] WIDTHS = {0.22, 0.08, 0.08, 0.09, 0.09, 0.09, 0.09, 0.08, 0.09, 0.09};
        private static final int ROW = 34;
        /** All players: those with a place first, in ranking order. */
        private List<Player> ranking = List.of();
        /** How many of the rows have a place. */
        private int places;
        private Game.Stats stats;

        /**
         * Creates an empty table.
         */
        StatsTable() {
            setPreferredSize(new Dimension(1000, 44 + 4 * ROW + 16));
        }

        /**
         * @param game the finished match whose statistics are shown
         */
        void show(Game.State game) {
            // Players with a place first, best first; in a match that was quit, the others follow without a place.
            List<Player> rows = new java.util.ArrayList<>(game.ranking());
            places = rows.size();
            game.players().stream().filter(p -> !rows.contains(p)).forEach(rows::add);
            ranking = List.copyOf(rows);
            stats = game.stats();
            setPreferredSize(new Dimension(1000, 44 + ranking.size() * ROW + 16));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            RoundRectangle2D card = new RoundRectangle2D.Double(1, 1, w - 2, getHeight() - 2, 20, 20);
            g2.setColor(Theme.alpha(Theme.surface(), 0.94));
            g2.fill(card);
            g2.setColor(Theme.gridColor());
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(card);
            double inner = w - 40;
            g2.setFont(Theme.Fonts.SMALL);
            g2.setColor(Theme.textMuted());
            // In Hebrew and Arabic the columns run from the right.
            double x = 24;
            FontMetrics hm = g2.getFontMetrics();
            for (int i = 0; i < COLUMNS.length; i++) {
                String heading = Assets.Texts.get("over.col." + COLUMNS[i]).toUpperCase(Locale.ROOT);
                g2.drawString(heading, (float) Widgets.startX(this, x, hm.stringWidth(heading)), 30f);
                x += WIDTHS[i] * inner;
            }
            for (int r = 0; r < ranking.size(); r++) {
                Player p = ranking.get(r);
                Color color = Theme.neonOf(p.color());
                double y = 42 + r * ROW;
                g2.setColor(Theme.alpha(color, 0.13));
                g2.fill(new RoundRectangle2D.Double(14, y, w - 28, ROW - 4, 10, 10));
                g2.setColor(color);
                g2.fill(new RoundRectangle2D.Double(Widgets.startX(this, 14, 5), y, 5, ROW - 4, 4, 4));
                double baseline = y + (ROW - 4) / 2.0 + 6;
                Object[] values = {Assets.Texts.nameAndColor(p), r < places ? Assets.Texts.ordinal(r + 1) : "—",
                        p.finishedCount() + " / " + p.pieces().size(), stats.captures(p), stats.timesCaptured(p),
                        stats.questionsRight(p), stats.questionsWrong(p), stats.sixes(p), stats.squaresWalked(p),
                        stats.surprises(p)};
                x = 24;
                for (int i = 0; i < values.length; i++) {
                    String text = String.valueOf(values[i]);
                    if (i == 1) {
                        g2.setFont(Theme.Fonts.comicFont(19f, text));
                        g2.setColor(STEP_COLORS[Math.min(r, 3)].darker());
                        if (Theme.mode() != Theme.Mode.LIGHT) {
                            g2.setColor(STEP_COLORS[Math.min(r, 3)]);
                        }
                    } else {
                        g2.setFont(i == 0 ? Theme.Fonts.BOLD : Theme.Fonts.BASE);
                        g2.setColor(Theme.text());
                    }
                    g2.drawString(text, (float) Widgets.startX(this, x, g2.getFontMetrics().stringWidth(text)),
                            (float) baseline);
                    x += WIDTHS[i] * inner;
                }
            }
            g2.dispose();
        }
    }

    /**
     * One fact of the finished game as a small neon glass chip: an icon, a caption and the value.
     */
    private static final class FactTile extends JComponent {

        static final int CLOCK = 1;
        static final int CHECK = 2;
        static final int CROSS = 3;
        private final String icon;
        private Color accent;
        private String caption = "";
        private String value = " ";
        private int symbol;

        /**
         * @param icon   icon name
         * @param accent outline color
         */
        FactTile(String icon, Color accent) {
            this.icon = icon;
            this.accent = accent;
        }

        /**
         * @param caption small text above the value
         * @param value   the value
         * @param color   value color, or {@code null} for the normal text color
         * @param symbol  which small symbol to draw, or 0 for none
         */
        void setFact(String caption, String value, Color color, int symbol) {
            this.caption = caption == null ? "" : caption.toUpperCase(Locale.ROOT);
            this.value = value;
            this.symbol = symbol;
            if (color != null) {
                this.accent = color;
            }
            revalidate();
            repaint();
        }

        /**
         * @return the font of the caption
         */
        private Font captionFont() {
            return chipFont(14);
        }

        /**
         * The same letters as the buttons next to the chips: the comic font where it has the letters (English), the
         * bold font in Hebrew and Arabic.
         *
         * @param size the size of the bold font
         */
        private Font chipFont(float size) {
            String all = caption + value;
            Font comic = Theme.Fonts.comicFont(size * 1.25f);
            return comic.canDisplayUpTo(all) == -1 ? comic : Theme.Fonts.baseFont(size, true);
        }

        /**
         * @return the font of the value
         */
        private Font valueFont() {
            return chipFont(caption.isEmpty() ? 15 : 17);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics cm = getFontMetrics(captionFont());
            FontMetrics vm = getFontMetrics(valueFont());
            int lead = icon != null || symbol != 0 ? 36 : 14;
            int text = caption.isEmpty() ? vm.stringWidth(value) : cm.stringWidth(caption) + 8 + vm.stringWidth(value);
            return new Dimension(lead + text + 16, 40);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            RoundRectangle2D pill = new RoundRectangle2D.Double(2, 3, getWidth() - 4, getHeight() - 6,
                    getHeight() - 6, getHeight() - 6);
            // The same glossy gradient pill as the buttons next to it, with white letters.
            Color from = Theme.mix(accent, Color.WHITE, 0.92);
            Color to = Theme.mix(partner(accent), Color.WHITE, 0.92);
            Theme.Glow.paintHalo(g2, pill.getCenterX(), pill.getCenterY(), pill.getWidth() * 0.55, accent, 0.2);
            g2.setPaint(new GradientPaint((float) pill.getX(), 0, from, (float) pill.getMaxX(), 0, to));
            g2.fill(pill);
            Shape clip = g2.getClip();
            g2.clip(pill);
            g2.setPaint(new GradientPaint(0, (float) pill.getY(), new Color(255, 255, 255, 90), 0,
                    (float) (pill.getY() + pill.getHeight() * 0.55), new Color(255, 255, 255, 0)));
            g2.fill(new Rectangle2D.Double(pill.getX(), pill.getY(), pill.getWidth(), pill.getHeight() * 0.55));
            g2.setClip(clip);
            g2.setPaint(new GradientPaint((float) pill.getX(), 0, Theme.mix(accent, Color.WHITE, 0.6),
                    (float) pill.getMaxX(), 0, Theme.mix(partner(accent), Color.WHITE, 0.6)));
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(pill);
            double cy = getHeight() / 2.0;
            double x = 14;
            // In Hebrew and Arabic the icon is on the right and the text runs to the left of it.
            double iconX = Widgets.startX(this, 24, 0);
            if (icon != null) {
                Theme.drawIcon(g2, icon, iconX, cy, 24);
                x = 40;
            } else if (symbol != 0) {
                paintSymbol(g2, iconX, cy);
                x = 40;
            }
            FontMetrics cm = g2.getFontMetrics(captionFont());
            FontMetrics vm = g2.getFontMetrics(valueFont());
            if (!caption.isEmpty()) {
                g2.setFont(captionFont());
                shadowed(g2, caption, Widgets.startX(this, x, cm.stringWidth(caption)),
                        cy + (cm.getAscent() - cm.getDescent()) / 2.0, new Color(255, 255, 255, 225));
                x += cm.stringWidth(caption) + 8;
            }
            g2.setFont(valueFont());
            shadowed(g2, value, Widgets.startX(this, x, vm.stringWidth(value)),
                    cy + (vm.getAscent() - vm.getDescent()) / 2.0, Color.WHITE);
            g2.dispose();
        }

        /**
         * White letters with a soft dark drop shadow, like on the gradient buttons.
         */
        private static void shadowed(Graphics2D g2, String text, double x, double y, Color color) {
            g2.setColor(new Color(0, 0, 0, 110));
            g2.drawString(text, (float) (x + 1.5), (float) (y + 2));
            g2.setColor(color);
            g2.drawString(text, (float) x, (float) y);
        }

        /**
         * @return the second color of the chip's gradient, matching the button colors: purple to pink, cyan to blue,
         *         pink to purple, green to teal, red to pink
         */
        private static Color partner(Color accent) {
            float[] hsb = Color.RGBtoHSB(accent.getRed(), accent.getGreen(), accent.getBlue(), null);
            float hue = (hsb[0] + 0.1f) % 1f;
            return Color.getHSBColor(hue, Math.min(1f, hsb[1]), Math.min(1f, hsb[2] + 0.1f));
        }

        /**
         * Draws the chip's small symbol.
         */
        private void paintSymbol(Graphics2D g2, double cx, double cy) {
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (symbol) {
                case CLOCK -> {
                    g2.draw(new Ellipse2D.Double(cx - 9, cy - 9, 18, 18));
                    g2.draw(new Line2D.Double(cx, cy, cx, cy - 6));
                    g2.draw(new Line2D.Double(cx, cy, cx + 4.5, cy + 2));
                }
                case CHECK -> {
                    g2.fill(new Ellipse2D.Double(cx - 10, cy - 10, 20, 20));
                    g2.setColor(accent);
                    Path2D tick = new Path2D.Double();
                    tick.moveTo(cx - 5, cy);
                    tick.lineTo(cx - 1.5, cy + 4);
                    tick.lineTo(cx + 5.5, cy - 4);
                    g2.draw(tick);
                }
                default -> {
                    g2.draw(new Line2D.Double(cx - 6, cy - 6, cx + 6, cy + 6));
                    g2.draw(new Line2D.Double(cx - 6, cy + 6, cx + 6, cy - 6));
                }
            }
        }
    }
}
