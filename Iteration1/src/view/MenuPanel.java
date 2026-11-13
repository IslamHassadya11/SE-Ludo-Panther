package view;

import model.*;
import model.GameHistory;
import model.Player;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List; // java.util.List, not java.awt.List
import java.util.Map;

/**
 * Main menu (FR-01), shown after the welcome screen. At the top who is using the program, with Back (a guest returns
 * to the welcome screen) or Log Out (the administrator). On the left the logo, the title and the menu: Play (New
 * Game), More (Game History), Manage (Question Management and Edit Profile, only for the logged-in administrator,
 * FR-60, TD-09) and Exit. On the right "Panther News", a comic slideshow about the finished games and the game.
 */
public final class MenuPanel extends Widgets.Background {

    /**
     * Receives the choices made on the main menu.
     */
    public interface Listener {

        /**
         * Back (guest) or Log Out (admin) was pressed.
         */
        void logOutChosen();

        /**
         * New Game was pressed.
         */
        void newGameChosen();

        /**
         * Game History was pressed.
         */
        void historyChosen();

        /**
         * Question Management was pressed (admin only).
         */
        void questionsChosen();

        /**
         * Edit Profile was pressed (admin only).
         */
        void profileChosen();

        /**
         * The Exit button was pressed.
         */
        void exitChosen();
    }

    private final Widgets.Button newGame = new Widgets.Button(Assets.Texts.get("menu.newGame"), Theme.neonPurple(), true);
    private final Widgets.Button history = new Widgets.Button(Assets.Texts.get("menu.history"), Theme.neonCyan(), false);
    private final Widgets.Button questions = new Widgets.Button(Assets.Texts.get("menu.questions"), Theme.neonCyan(), false);
    private final Widgets.Button profile = new Widgets.Button(Assets.Texts.get("menu.profile"), Theme.neonCyan(), false);
    private final Widgets.Button exit = new Widgets.Button(Assets.Texts.get("common.exit"), Theme.error(), false);
    private final Widgets.PillLabel userPill = new Widgets.PillLabel(Assets.Texts.get("menu.guest"), Theme.neonCyan());
    private final Widgets.Button leave = new Widgets.Button(Assets.Texts.get("common.back"), Theme.neonCyan(), false);
    private final JLabel manageCaption = caption(Assets.Texts.get("menu.manage"));
    private final NewsSlideshow news = new NewsSlideshow();

    /**
     * Builds the screen: who is signed in and Back or Log Out at the top, the menu and Panther News in the center.
     */
    public MenuPanel() {
        super(new BorderLayout());

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(14, 14, 0, 18));
        JPanel user = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        user.setOpaque(false);
        leave.setFont(Theme.Fonts.BOLD);
        leave.setBorder(BorderFactory.createEmptyBorder(9, 20, 9, 20));
        user.add(leave);
        user.add(userPill);
        topBar.add(user, BorderLayout.LINE_START);
        add(topBar, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        add(center, BorderLayout.CENTER);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        Widgets.Logo logo = new Widgets.Logo(128);
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        Widgets.Title title = new Widgets.Title(Assets.Texts.get("app.title"), 44);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel subtitle = mutedLabel(Assets.Texts.get("app.subtitle"));
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        column.add(logo);
        column.add(title);
        column.add(subtitle);
        column.add(Box.createVerticalStrut(12));
        JPanel menu = buildMenuCard();
        menu.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(menu);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weighty = 1;
        c.insets = new Insets(0, 24, 0, 12);
        center.add(column, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(10, 12, 10, 26);
        center.add(buildNewsSide(), c);

        JLabel footer = mutedLabel(Assets.Texts.get("app.team"));
        footer.setFont(Theme.Fonts.SMALL);
        footer.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 18, 10, 18));
        bottom.add(footer, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    // ------------------------------------------------------------------ Panther News

    /**
     * The right side: "Panther News".
     */
    private JPanel buildNewsSide() {
        // The news card takes about three quarters of the space on the right, centered, so it does not crowd the menu.
        JPanel right = new JPanel(null) {
            @Override
            public void doLayout() {
                int w = (int) Math.min(getWidth() * 0.78, getHeight() * 1.15);
                int h = (int) Math.min(getHeight() * 0.8, w * 0.9);
                news.setBounds((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
            }
        };
        right.setOpaque(false);
        right.add(news);
        return right;
    }

    /**
     * @param games the finished games for "Panther News", newest first
     */
    public void setNews(java.util.List<model.GameHistory.GameRecord> games) {
        news.setGames(games);
    }

    /**
     * Builds the card with the main menu buttons (FR-01); the Manage group is added but only shown to the admin
     * (TD-09).
     */
    private JPanel buildMenuCard() {
        JPanel card = new Widgets.GlowCard(new GridBagLayout(), 70);
        // Every menu button has its own colors, like the Panther Ludo logo.
        newGame.setGradient(new Color(0x8B5CF6), new Color(0xFF4FB8));
        history.setGradient(new Color(0x2E9BFF), new Color(0x2CE5FF));
        questions.setGradient(new Color(0xFF9F1C), new Color(0xFFD23F));
        profile.setGradient(new Color(0x14B8A6), new Color(0x2EE66B));
        exit.setGradient(new Color(0xFF4D6D), new Color(0xFF7AA8));
        leave.setGradient(new Color(0x2CE5FF), new Color(0x8B5CF6));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 0);

        newGame.setIconName("feature_dice_king");
        history.setIconName("icon_history");
        questions.setIconName("feature_quiz_master");
        profile.setIconName("tile_block");
        leave.setSymbol("back");
        c.gridy = 0;
        card.add(caption(Assets.Texts.get("menu.play")), c);
        c.gridy++;
        setButtonHeight(newGame, 58);
        card.add(newGame, c);

        c.gridy++;
        c.insets = new Insets(12, 0, 2, 0);
        card.add(caption(Assets.Texts.get("menu.more")), c);
        c.insets = new Insets(1, 0, 1, 0);
        c.gridy++;
        setButtonHeight(history, 48);
        card.add(history, c);
        c.gridy++;
        c.insets = new Insets(12, 0, 2, 0);
        card.add(manageCaption, c);
        c.insets = new Insets(1, 0, 1, 0);
        for (JButton button : new JButton[]{questions, profile}) {
            c.gridy++;
            setButtonHeight(button, 48);
            card.add(button, c);
        }
        c.gridy++;
        c.insets = new Insets(12, 0, 0, 0);
        setButtonHeight(exit, 46);
        card.add(exit, c);
        return card;
    }

    /**
     * Gives a menu button its full width and the given height.
     */
    private static void setButtonHeight(JButton button, int height) {
        button.setPreferredSize(new Dimension(360, height));
    }

    /**
     * Small grey heading above a group of buttons.
     */
    private static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.Fonts.SMALL);
        label.setForeground(Theme.textMuted());
        return label;
    }

    /**
     * Grey explanatory text.
     */
    private static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.Fonts.BASE);
        label.setForeground(Theme.textMuted());
        return label;
    }

    /**
     * Connects the screen to its controller.
     *
     * @param listener receives what the user does on this screen
     */
    public void setListener(Listener listener) {
        leave.addActionListener(e -> listener.logOutChosen());
        onNewGame(e -> listener.newGameChosen());
        onHistory(e -> listener.historyChosen());
        onQuestions(e -> listener.questionsChosen());
        profile.addActionListener(e -> listener.profileChosen());
        onExit(e -> listener.exitChosen());
    }

    /**
     * Shows the menu for the signed-in user. Admin-only items are hidden for guests, not just disabled (TD-09).
     *
     * @param admin    {@code true} for the logged-in administrator
     * @param username admin username (ignored for guests)
     */
    public void showSession(boolean admin, String username) {
        questions.setVisible(admin);
        profile.setVisible(admin);
        manageCaption.setVisible(admin);
        userPill.setText(admin ? Assets.Texts.get("menu.adminUser", username) : Assets.Texts.get("menu.guest"));
        if (admin) {
            userPill.setGradient(new Color(0xFF9F1C), new Color(0xFF4FB8));
        } else {
            userPill.setGradient(new Color(0x14B8A6), new Color(0x2E9BFF));
        }
        leave.setText(Assets.Texts.get(admin ? "menu.logOut" : "common.back"));
        // Back has the back arrow like every Back button; Log Out keeps its own sign.
        leave.setSymbol(admin ? null : "back");
        leave.setIconName(admin ? "icon_players" : null);
        revalidate();
        repaint();
    }

    /**
     * @param l handler for New Game
     */
    private void onNewGame(ActionListener l) {
        newGame.addActionListener(l);
    }

    /**
     * @param l handler for Game History
     */
    private void onHistory(ActionListener l) {
        history.addActionListener(l);
    }

    /**
     * @param l handler for Question Management
     */
    private void onQuestions(ActionListener l) {
        questions.addActionListener(l);
    }

    /**
     * @param l handler for Exit
     */
    private void onExit(ActionListener l) {
        exit.addActionListener(l);
    }

    /**
     * "Panther News" on the main menu: a comic panel whose slides change by themselves with a slide-and-fade transition.
     * <ol>
     *     <li><b>Last winner</b>: the roaring winner panther on a sun, a "LAST WINNER!" burst and how the game went.</li>
     *     <li><b>Panther of the day</b>: one of the panthers, changing every day, with a short line about it.</li>
     *     <li><b>Did you know?</b>: the Quiz Master panther with a tip about the rules.</li>
     *     <li><b>Hall of fame</b>: the three players who won the most games.</li>
     *     <li><b>Panther run</b>: the logo while the four panthers race past.</li>
     * </ol>
     * The data comes from the saved game history, handed over by the controller ({@link #setGames}). A click moves to the
     * next slide; the dots at the bottom jump to a slide.
     */
    public static final class NewsSlideshow extends JComponent {

        private static final double SLIDE_SECONDS = 6.5;
        private static final double TRANSITION_SECONDS = 0.6;
        private static final int SLIDES = 5;
        /** The panther chase, drawn by the game. */
        private static final int CHASE = 4;
        private static final int TIPS = 6;
        private static final int TAGLINES = 6;
        private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        private List<GameHistory.GameRecord> games = List.of();
        private int slide;
        private int previous = -1;
        private double slideStart = Theme.Motion.secondsSinceStart();
        private int shown;
        private final List<Ellipse2D> dots = new ArrayList<>();

        /**
         * Builds the panel; it shows the slides as soon as it is on screen.
         */
        public NewsSlideshow() {
            setPreferredSize(new Dimension(560, 520));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    for (int i = 0; i < dots.size(); i++) {
                        if (dots.get(i).getBounds2D().contains(e.getPoint())) {
                            go(i);
                            return;
                        }
                    }
                    go((slide + 1) % SLIDES);
                }
            });
        }

        @Override
        public void addNotify() {
            super.addNotify();
            Theme.Motion.subscribe(this);
        }

        @Override
        public void removeNotify() {
            Theme.Motion.unsubscribe(this);
            super.removeNotify();
        }

        /**
         * @param games the finished games, newest first
         */
        public void setGames(List<GameHistory.GameRecord> games) {
            this.games = List.copyOf(games);
            repaint();
        }

        /**
         * Slides to another news slide.
         *
         * @param next index of the slide
         */
        private void go(int next) {
            if (next == slide) {
                return;
            }
            Assets.Sound.play(Assets.Sound.Effect.CLICK);
            previous = slide;
            slide = next;
            slideStart = Theme.Motion.secondsSinceStart();
            shown++;
        }

        @Override
        protected void paintComponent(Graphics g) {
            double now = Theme.Motion.secondsSinceStart();
            if (now - slideStart > slideSeconds(slide)) {
                previous = slide;
                slide = (slide + 1) % SLIDES;
                slideStart = now;
                shown++;
            }
            double age = now - slideStart;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int w = getWidth();
            int h = getHeight();
            boolean light = Theme.mode() == Theme.Mode.LIGHT;
            RoundRectangle2D card = new RoundRectangle2D.Double(4, 4, w - 18, h - 20, 30, 30);
            Color ink = Theme.Comic.inkColor();
            Shape clip = g2.getClip();
            g2.clip(card);
            paintStage(g2, card, light);
            cardBounds = card.getBounds2D();
            Rectangle2D area = new Rectangle2D.Double(card.getX(), card.getY() + 56, card.getWidth(),
                    card.getHeight() - 56 - 40);
            double k = Math.min(1, age / TRANSITION_SECONDS);
            double e = Theme.Motion.ease(k);
            boolean rtl = !getComponentOrientation().isLeftToRight();
            double dir = rtl ? -1 : 1;
            if (previous >= 0 && k < 1) {
                Graphics2D out = (Graphics2D) g2.create();
                out.translate(-dir * e * area.getWidth() * 0.35, 0);
                out.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (1 - e)));
                paintSlide(out, previous, area, slideSeconds(previous), shown - 1);
                out.dispose();
            }
            Graphics2D in = (Graphics2D) g2.create();
            in.translate(dir * (1 - e) * area.getWidth() * 0.35, 0);
            in.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) e));
            paintSlide(in, slide, area, age, shown);
            in.dispose();
            g2.setClip(clip);
            Theme.Glow.paintGlowStroke(g2, card, Theme.neonPurple(), 1.8f, 0.55);

            // "PANTHER NEWS" tag at the top.
            String tag = Assets.Texts.directional(Assets.Texts.get("news.title").toUpperCase(Locale.ROOT));
            Font tagFont = Theme.Fonts.comicFont(22f, tag);
            FontMetrics tm = g2.getFontMetrics(tagFont);
            double tw = tm.stringWidth(tag) + 34;
            double tx = rtl ? card.getMaxX() - 22 - tw : card.getX() + 22;
            RoundRectangle2D pill = new RoundRectangle2D.Double(tx, card.getY() + 16, tw, 34, 34, 34);
            g2.setColor(new Color(0xFFD23F));
            g2.fill(pill);
            g2.setColor(ink);
            g2.setStroke(new BasicStroke(3f));
            g2.draw(pill);
            g2.setFont(tagFont);
            g2.drawString(tag, (float) (tx + 17), (float) (pill.getCenterY() + (tm.getAscent() - tm.getDescent()) / 2.0));

            // Progress dots.
            dots.clear();
            double dx = rtl ? card.getX() + 26 : card.getMaxX() - 26 - (SLIDES - 1) * 20 - 26;
            double dy = card.getMaxY() - 30;
            for (int i = 0; i < SLIDES; i++) {
                boolean on = i == slide;
                double dw = on ? 26 : 12;
                RoundRectangle2D dot = new RoundRectangle2D.Double(dx, dy, dw, 12, 12, 12);
                dots.add(new Ellipse2D.Double(dx - 4, dy - 4, dw + 8, 20));
                g2.setColor(on ? new Color(0xFF4FB8) : Theme.alpha(Theme.text(), 0.25));
                g2.fill(dot);
                if (on) {
                    Theme.Glow.paintGlowStroke(g2, dot, new Color(0xFF4FB8), 1f, 0.6);
                }
                dx += dw + 8;
            }
            g2.dispose();
        }

        /**
         * The news stage: a sunset-to-night sky, two big soft color lights drifting slowly, light rays from the top and
         * a glowing neon floor grid running toward the horizon, like a TV studio of the game.
         */
        private void paintStage(Graphics2D g2, RoundRectangle2D card, boolean light) {
            double x = card.getX();
            double y = card.getY();
            double w = card.getWidth();
            double h = card.getHeight();
            double t = Theme.Motion.secondsSinceStart();
            Color top = light ? new Color(0xE9E4FF) : new Color(0x0E0B2E);
            Color mid = light ? new Color(0xFFE3F2) : new Color(0x2A1450);
            Color low = light ? new Color(0xFFF1DE) : new Color(0x3B1248);
            g2.setPaint(new LinearGradientPaint(0, (float) y, 0, (float) (y + h), new float[]{0f, 0.62f, 1f},
                    new Color[]{top, mid, low}));
            g2.fill(card);
            // Drifting color lights.
            double[][] orbs = {{0.22 + 0.06 * Math.sin(t * 0.21), 0.3 + 0.05 * Math.cos(t * 0.17), 0.55},
                    {0.8 + 0.05 * Math.cos(t * 0.19), 0.36 + 0.06 * Math.sin(t * 0.23), 0.5}};
            Color[] orbColors = {new Color(0x2CE5FF), new Color(0xFF4FB8)};
            for (int i = 0; i < 2; i++) {
                Point2D c = new Point2D.Double(x + orbs[i][0] * w, y + orbs[i][1] * h);
                float r = (float) (orbs[i][2] * Math.max(w, h));
                g2.setPaint(new RadialGradientPaint(c, r, new float[]{0f, 1f},
                        new Color[]{Theme.alpha(orbColors[i], light ? 0.3 : 0.32), Theme.alpha(orbColors[i], 0)}));
                g2.fill(card);
            }
            // Soft light rays from the top.
            Graphics2D rays = (Graphics2D) g2.create();
            for (int i = 0; i < 6; i++) {
                double cx = x + w * (0.1 + 0.16 * i) + 30 * Math.sin(t * 0.3 + i);
                Path2D ray = new Path2D.Double();
                ray.moveTo(cx - 14, y);
                ray.lineTo(cx + 14, y);
                ray.lineTo(cx + 70, y + h * 0.7);
                ray.lineTo(cx - 10, y + h * 0.7);
                ray.closePath();
                rays.setPaint(new GradientPaint(0, (float) y, new Color(255, 255, 255, light ? 70 : 28), 0,
                        (float) (y + h * 0.7), new Color(255, 255, 255, 0)));
                rays.fill(ray);
            }
            rays.dispose();
            // Neon floor: a horizon glow and a grid running toward it.
            double horizon = y + h * 0.66;
            Color grid = light ? new Color(0xB45CFF) : new Color(0xFF4FD8);
            g2.setPaint(new GradientPaint(0, (float) (horizon - 30), Theme.alpha(grid, 0), 0, (float) horizon,
                    Theme.alpha(grid, light ? 0.28 : 0.4)));
            g2.fill(new Rectangle2D.Double(x, horizon - 30, w, 30));
            g2.setPaint(new GradientPaint(0, (float) horizon, Theme.alpha(grid, light ? 0.1 : 0.16), 0,
                    (float) (y + h), Theme.alpha(grid, light ? 0.04 : 0.08)));
            g2.fill(new Rectangle2D.Double(x, horizon, w, y + h - horizon));
            g2.setStroke(new BasicStroke(1.2f));
            double cx = x + w / 2;
            for (int i = -10; i <= 10; i++) {
                g2.setColor(Theme.alpha(grid, light ? 0.22 : 0.35));
                g2.draw(new Line2D.Double(cx + i * w * 0.04, horizon, cx + i * w * 0.22, y + h));
            }
            double scroll = (t * 0.6) % 1;
            for (int k = 0; k < 7; k++) {
                double d = (k + scroll) / 7;
                double ly = horizon + (y + h - horizon) * d * d;
                g2.setColor(Theme.alpha(grid, (light ? 0.25 : 0.4) * d));
                g2.draw(new Line2D.Double(x, ly, x + w, ly));
            }
        }

        /** The whole card, where the chase is shown. */
        private Rectangle2D cardBounds = new Rectangle2D.Double();

        /**
         * @return how long a slide stays: the chase plays once
         */
        private double slideSeconds(int index) {
            return index == CHASE ? CHASE_SECONDS : SLIDE_SECONDS;
        }

        /**
         * Draws one news slide.
         */
        private void paintSlide(Graphics2D g, int index, Rectangle2D area, double age, int count) {
            switch (index) {
                case 0 -> lastWinner(g, area, age);
                case 1 -> pantherOfTheDay(g, area, age);
                case 2 -> didYouKnow(g, area, age, count);
                case 3 -> hallOfFame(g, area, age);
                default -> chase(g, cardBounds, age);
            }
        }

        // ------------------------------------------------------------------ slides

        /**
         * Draws the "last winner" slide.
         */
        private void lastWinner(Graphics2D g, Rectangle2D a, double age) {
            double sun = Math.min(a.getHeight() * 0.74, a.getWidth() * 0.42);
            double sx = side(a, 0.06) + (isRtl() ? -sun : 0);
            double sy = a.getCenterY() - sun / 2 + 8;
            Color fur = games.isEmpty() ? Theme.colorOf(Player.Color.PRESETS.get(0))
                    : furOf(games.get(0).placeNames().get(0));
            // The winner's panther glows in front of slowly turning light rays in its own color.
            double cx = sx + sun / 2;
            double cy = sy + sun / 2;
            Graphics2D r = (Graphics2D) g.create();
            for (int i = 0; i < 14; i++) {
                double a0 = age * 0.25 + i * Math.PI / 7;
                Path2D ray = new Path2D.Double();
                ray.moveTo(cx, cy);
                ray.lineTo(cx + Math.cos(a0) * sun * 0.75, cy + Math.sin(a0) * sun * 0.75);
                ray.lineTo(cx + Math.cos(a0 + 0.17) * sun * 0.75, cy + Math.sin(a0 + 0.17) * sun * 0.75);
                ray.closePath();
                r.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) (sun * 0.75), new float[]{0f, 1f},
                        new Color[]{Theme.alpha(fur, 0.35), Theme.alpha(fur, 0)}));
                r.fill(ray);
            }
            r.dispose();
            double bob = 4 * Math.sin(age * 2.2);
            Theme.Panther.paintAvatar(g, cx, cy + bob, sun * 0.78, fur, 0.7 + 0.2 * Theme.Motion.pulse(1.1));
            Theme.drawIcon(g, "feature_crown_pawn", cx + sun * 0.27, cy - sun * 0.33 + bob, sun * 0.28);
            double colX = isRtl() ? a.getX() + 22 : sx + sun + 18;
            double colW = isRtl() ? sx - 18 - colX : a.getMaxX() - 22 - colX;
            if (games.isEmpty()) {
                burst(g, colX + colW / 2, a.getY() + a.getHeight() * 0.22, colW, Assets.Texts.get("news.noChamp"),
                        new Color(0xFF4FB8), age);
                caption(g, colX, a.getY() + a.getHeight() * 0.46, colW, null, null, Assets.Texts.get("news.noGames"), age);
                return;
            }
            GameHistory.GameRecord last = games.get(0);
            burst(g, colX + colW / 2, a.getY() + a.getHeight() * 0.22, colW, Assets.Texts.get("news.lastWinner"),
                    new Color(0xFF4FB8), age);
            String text = Assets.Texts.get("news.lastWinnerText", last.gameId(), Assets.Texts.level(last.level()),
                    time(last.duration()), last.finishedAt().format(DATE));
            caption(g, colX, a.getY() + a.getHeight() * 0.46, colW, last.placeNames().get(0),
                    Theme.Comic.candyColor(furOf(last.placeNames().get(0))), text, age);
        }

        /**
         * Draws the "panther of the day" slide.
         */
        private void pantherOfTheDay(Graphics2D g, Rectangle2D a, double age) {
            int day = LocalDate.now().getDayOfYear();
            Player.Color color = Player.Color.PRESETS.get(day % Player.Color.PRESETS.size());
            double size = Math.min(a.getHeight() * 0.7, a.getWidth() * 0.4);
            double cx = isRtl() ? a.getMaxX() - a.getWidth() * 0.06 - size / 2 : a.getX() + a.getWidth() * 0.06 + size / 2;
            double cy = a.getCenterY() + 10 + 4 * Math.sin(age * 1.8);
            Theme.Panther.paintAvatar(g, cx, cy, size, Theme.colorOf(color), 0.5 + 0.3 * Theme.Motion.pulse(0.8));
            double colX = isRtl() ? a.getX() + 22 : cx + size / 2 + 22;
            double colW = isRtl() ? cx - size / 2 - 22 - colX : a.getMaxX() - 22 - colX;
            burst(g, colX + colW / 2, a.getY() + a.getHeight() * 0.22, colW, Assets.Texts.get("news.ofTheDay"),
                    new Color(0xFFD23F), age);
            caption(g, colX, a.getY() + a.getHeight() * 0.46, colW, Assets.Texts.color(color),
                    Theme.Comic.candyColor(Theme.colorOf(color)), Assets.Texts.get("news.tagline" + (1 + day % TAGLINES)), age);
        }

        /**
         * Draws a "did you know?" slide with a rules tip.
         */
        private void didYouKnow(Graphics2D g, Rectangle2D a, double age, int count) {
            double size = Math.min(a.getHeight() * 0.72, a.getWidth() * 0.4);
            double cx = isRtl() ? a.getMaxX() - a.getWidth() * 0.05 - size / 2 : a.getX() + a.getWidth() * 0.05 + size / 2;
            Graphics2D r = (Graphics2D) g.create();
            r.rotate(0.06 * Math.sin(age * 1.5), cx, a.getCenterY() + size / 2);
            Theme.drawIcon(r, "quiz_master", cx, a.getCenterY() + 14, size);
            r.dispose();
            double colX = isRtl() ? a.getX() + 22 : cx + size / 2 + 18;
            double colW = isRtl() ? cx - size / 2 - 18 - colX : a.getMaxX() - 22 - colX;
            burst(g, colX + colW / 2, a.getY() + a.getHeight() * 0.22, colW, Assets.Texts.get("news.didYouKnow"),
                    new Color(0x2F8BFF), age);
            int tip = 1 + Math.floorMod(count / SLIDES, TIPS);
            caption(g, colX, a.getY() + a.getHeight() * 0.46, colW, null, null, Assets.Texts.get("news.fact" + tip), age);
        }

        /**
         * Draws the "hall of fame" slide: the players with the most wins.
         */
        private void hallOfFame(Graphics2D g, Rectangle2D a, double age) {
            burst(g, a.getCenterX(), a.getY() + a.getHeight() * 0.13, Math.min(a.getWidth() * 0.7, 420),
                    Assets.Texts.get("news.hallOfFame"), new Color(0x12C48B), age);
            Map<String, Integer> wins = new HashMap<>();
            for (GameHistory.GameRecord record : games) {
                wins.merge(record.placeNames().get(0), 1, Integer::sum);
            }
            List<Map.Entry<String, Integer>> top = new ArrayList<>(wins.entrySet());
            top.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
            if (top.isEmpty()) {
                caption(g, a.getX() + a.getWidth() * 0.15, a.getY() + a.getHeight() * 0.42, a.getWidth() * 0.7, null,
                        null, Assets.Texts.get("news.noGames"), age);
                return;
            }
            int places = Math.min(3, top.size());
            int[] order = places == 3 ? new int[]{1, 0, 2} : places == 2 ? new int[]{1, 0} : new int[]{0};
            double colW = Math.min(150, a.getWidth() / 3.4);
            double total = colW * order.length + 16 * (order.length - 1);
            double x = a.getCenterX() - total / 2;
            double floor = a.getMaxY() - 8;
            double[] heights = {0.30, 0.20, 0.13};
            Color[] steps = {new Color(0xFFD23F), new Color(0xD9DEEA), new Color(0xF0A86A)};
            for (int slot = 0; slot < order.length; slot++) {
                int place = isRtl() ? order[order.length - 1 - slot] : order[slot];
                Map.Entry<String, Integer> entry = top.get(place);
                double rise = Theme.Motion.ease(Math.min(1, Math.max(0, (age - 0.25 - 0.15 * (2 - place)) / 0.5)));
                double stepH = a.getHeight() * heights[place] * rise;
                RoundRectangle2D step = new RoundRectangle2D.Double(x, floor - stepH, colW, stepH + 12, 14, 14);
                g.setColor(steps[place]);
                g.fill(step);
                g.setColor(Theme.Comic.inkColor());
                g.setStroke(new BasicStroke(3f));
                g.draw(step);
                String number = Integer.toString(place + 1);
                Font big = Theme.Fonts.comicFont(34f);
                FontMetrics bm = g.getFontMetrics(big);
                if (stepH > bm.getAscent()) {
                    Theme.Comic.outlinedText(g, number, big, x + colW / 2, floor - stepH + 6 + bm.getAscent(), Color.WHITE, 3f);
                }
                double avatar = Math.min(colW * 0.78, a.getHeight() * 0.3);
                double ay = floor - stepH - avatar / 2 - 52;
                Theme.Panther.paintAvatar(g, x + colW / 2, ay, avatar, furOf(entry.getKey()), place == 0 ? 0.8 : 0.3);
                if (place == 0) {
                    Theme.drawIcon(g, "icon_win", x + colW / 2 + avatar * 0.42, ay - avatar * 0.42, avatar * 0.42);
                }
                String name = Assets.Texts.directional(entry.getKey());
                Font nameFont = Theme.Fonts.comicFont(22f, name);
                FontMetrics nm = g.getFontMetrics(nameFont);
                while (nameFont.getSize2D() > 12 && nm.stringWidth(name) > colW + 10) {
                    nameFont = nameFont.deriveFont(nameFont.getSize2D() - 1);
                    nm = g.getFontMetrics(nameFont);
                }
                g.setFont(nameFont);
                g.setColor(Theme.text());
                g.drawString(name, (float) (x + colW / 2 - nm.stringWidth(name) / 2.0), (float) (ay + avatar / 2 + 4
                        + nm.getAscent() * 0.8));
                String count = Assets.Texts.get("news.wins", entry.getValue());
                g.setFont(Theme.Fonts.baseFont(13, true));
                FontMetrics cm = g.getFontMetrics();
                g.setColor(Theme.textMuted());
                g.drawString(count, (float) (x + colW / 2 - cm.stringWidth(count) / 2.0),
                        (float) (ay + avatar / 2 + 6 + nm.getHeight() + cm.getAscent() * 0.6));
                x += colW + 16;
            }
        }

        private static final double CHASE_SECONDS = 7.0;

        /**
         * The chase: four big neon panthers (blue, pink, green, gold) bound after a tumbling die across a glowing neon
         * floor, closing in until the leader pounces and catches it; then the logo and a crown for the winner.
         */
        private void chase(Graphics2D g, Rectangle2D card, double age) {
            double t = Math.min(age, CHASE_SECONDS);
            double w = card.getWidth();
            double h = card.getHeight();
            boolean rtl = isRtl();
            double dir = rtl ? -1 : 1;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.clip(card);
            g2.setPaint(new GradientPaint(0, (float) card.getY(), new Color(0x0A0C2E), 0, (float) card.getMaxY(),
                    new Color(0x1B0F45)));
            g2.fill(card);
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(card.getCenterX(), card.getY() + h * 0.45),
                    (float) (w * 0.6), new float[]{0f, 1f}, new Color[]{new Color(139, 92, 246, 120),
                            new Color(139, 92, 246, 0)}));
            g2.fill(card);
            double floor = card.getY() + h * 0.74;
            g2.setPaint(new GradientPaint(0, (float) floor, new Color(90, 70, 220, 110), 0, (float) card.getMaxY(),
                    new Color(20, 10, 60, 40)));
            g2.fill(new Rectangle2D.Double(card.getX(), floor, w, card.getMaxY() - floor));
            double catchAt = 4.6;
            double run = Math.min(t, catchAt);
            g2.setStroke(new BasicStroke(1.4f));
            for (int i = 0; i < 9; i++) {
                double d = ((i + run * 1.4) % 9) / 9.0;
                double ly = floor + (card.getMaxY() - floor) * d * d;
                g2.setColor(new Color(150, 120, 255, (int) (110 * d)));
                g2.draw(new Line2D.Double(card.getX(), ly, card.getMaxX(), ly));
            }
            for (int i = -8; i <= 8; i++) {
                double off = (run * 160 * dir) % (w * 0.12);
                double x0 = card.getCenterX() + i * w * 0.12 - off;
                g2.setColor(new Color(150, 120, 255, 70));
                g2.draw(new Line2D.Double(card.getCenterX() + (x0 - card.getCenterX()) * 0.25, floor, x0, card.getMaxY()));
            }
            for (int i = 0; i < 14; i++) {
                double sy = card.getY() + h * (0.15 + 0.55 * ((i * 0.37) % 1));
                double sx = card.getX() + w * (((i * 0.61) - run * (0.55 + 0.1 * (i % 3)) * dir) % 1 + 1) % 1;
                g2.setColor(new Color(180, 160, 255, t < catchAt ? 70 : 0));
                g2.draw(new Line2D.Double(sx, sy, sx - dir * 40, sy));
            }
            double size = Math.min(h * 0.3, w * 0.2);
            Color[] colors = {new Color(0x3D9BFF), new Color(0xFF5FC8), new Color(0x2EE66B), new Color(0xFFD23F)};
            double dieX = card.getCenterX() + dir * w * (0.3 + 0.03 * Math.sin(t * 2.1));
            double dieHop = Math.abs(Math.sin(t * 6.3)) * size * 0.45;
            boolean caught = t >= catchAt;
            if (caught) {
                dieX = card.getCenterX() + dir * w * 0.3;
                dieHop = 0;
            }
            double close = Theme.Motion.ease(Math.min(1, run / catchAt));
            double[] xs = new double[4];
            double[] ys = new double[4];
            for (int i = 0; i < 4; i++) {
                double gap = size * (0.95 + 0.95 * i) * (1.35 - 0.55 * close);
                xs[i] = dieX - dir * gap - dir * size * 0.12 * Math.sin(t * 1.7 + i * 2.1);
                double bound = Math.abs(Math.sin(t * 5.2 + i * 1.3));
                ys[i] = floor - size * 0.5 - bound * size * 0.38;
            }
            double pounce = t < catchAt - 0.55 ? 0 : Math.min(1, (t - (catchAt - 0.55)) / 0.55);
            if (pounce > 0) {
                double sx = xs[0];
                xs[0] = sx + (dieX - sx) * Theme.Motion.ease(pounce);
                ys[0] = floor - size * 0.5 - Math.sin(pounce * Math.PI) * size * 0.9;
            }
            for (int i = 3; i >= 0; i--) {
                double s = size * (1 - 0.08 * i);
                g2.setColor(new Color(colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), 60));
                g2.fill(new Ellipse2D.Double(xs[i] - s * 0.4, floor - s * 0.06, s * 0.8, s * 0.12));
                if (t < catchAt) {
                    for (int k = 3; k >= 1; k--) {
                        Composite old = g2.getComposite();
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f * (4 - k)));
                        Theme.Panther.paintAvatar(g2, xs[i] - dir * k * s * 0.22, ys[i] + k * 3, s, colors[i], 0);
                        g2.setComposite(old);
                    }
                }
                Graphics2D lean = (Graphics2D) g2.create();
                lean.rotate(dir * (t < catchAt ? 0.12 : 0) * Math.sin(t * 5.2 + i * 1.3), xs[i], ys[i]);
                Theme.Panther.paintAvatar(lean, xs[i], ys[i], s, colors[i], 0.55);
                lean.dispose();
            }
            if (!caught || t < catchAt + 0.15) {
                paintDie(g2, dieX, floor - dieHop, size * 0.42, caught ? -1 : t * 1.4, 6);
            }
            if (caught) {
                double c = Math.min(1, (t - catchAt) / 0.5);
                g2.setColor(new Color(255, 255, 255, (int) (130 * Math.max(0, 1 - c * 2))));
                g2.fill(card);
                Theme.drawIcon(g2, "feature_crown_pawn", xs[0], ys[0] - size * 0.62 - 6 * Math.sin(t * 4),
                        size * 0.45 * Theme.Motion.ease(c));
                BufferedImage logo = Theme.logo();
                if (logo != null) {
                    double lw = Math.min(w * 0.27, 170) * (0.7 + 0.3 * Theme.Motion.ease(c));
                    double lh = lw * logo.getHeight() / logo.getWidth();
                    Composite old = g2.getComposite();
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) c));
                    g2.drawImage(logo, (int) (card.getCenterX() - lw / 2), (int) (card.getY() + h * 0.05), (int) lw,
                            (int) lh, null);
                    g2.setComposite(old);
                    String line = Assets.Texts.directional(Assets.Texts.get("news.run").toUpperCase(Locale.ROOT));
                    Theme.Comic.outlinedText(g2, line, Theme.Fonts.comicFont(28f, line), card.getCenterX(), card.getMaxY() - 26,
                            new Color(0xFF4FB8), 3.5f);
                }
            }
            g2.dispose();
        }

        /**
         * A white die: tumbling and bouncing while it rolls (time 0 or more), or lying still showing a face (time below
         * zero).
         */
        private static void paintDie(Graphics2D g, double x, double ground, double size, double time, int shown) {
            boolean still = time < 0;
            double bounce = still ? 0 : Math.abs(Math.sin(time * 5.2)) * size * 0.6;
            double angle = still ? 0 : time * 7.5;
            int face = still ? shown : 1 + (int) (Math.floor(time * 5.2 / Math.PI) % 6);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, (int) Math.max(10, 60 - bounce / size * 25)));
            double sw = size * (1.1 - bounce / size * 0.35);
            g2.fill(new Ellipse2D.Double(x - sw / 2, ground - size * 0.08, sw, size * 0.16));
            double cy = ground - size / 2 - bounce;
            g2.rotate(angle, x, cy);
            RoundRectangle2D cube = new RoundRectangle2D.Double(x - size / 2, cy - size / 2, size, size, size * 0.28,
                    size * 0.28);
            g2.setPaint(new GradientPaint((float) x, (float) (cy - size / 2), Color.WHITE, (float) x,
                    (float) (cy + size / 2), new Color(0xD9DCEB)));
            g2.fill(cube);
            g2.setColor(new Color(0x9AA0BF));
            g2.setStroke(new BasicStroke((float) Math.max(1, size * 0.04)));
            g2.draw(cube);
            g2.setColor(new Color(0x1B1640));
            double r = size * 0.09;
            double o = size * 0.26;
            int[][] pips = switch (face) {
                case 1 -> new int[][]{{0, 0}};
                case 2 -> new int[][]{{-1, -1}, {1, 1}};
                case 3 -> new int[][]{{-1, -1}, {0, 0}, {1, 1}};
                case 4 -> new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
                case 5 -> new int[][]{{-1, -1}, {1, -1}, {0, 0}, {-1, 1}, {1, 1}};
                default -> new int[][]{{-1, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {1, 1}};
            };
            for (int[] pip : pips) {
                g2.fill(new Ellipse2D.Double(x + pip[0] * o - r, cy + pip[1] * o - r, 2 * r, 2 * r));
            }
            g2.dispose();
        }

        // ------------------------------------------------------------------ helpers

        /**
         * @return {@code true} while the language is written right to left
         */
        private boolean isRtl() {
            return !getComponentOrientation().isLeftToRight();
        }

        /**
         * @return the x of the leading edge of the area moved in by the given share of its width
         */
        private double side(Rectangle2D a, double share) {
            return isRtl() ? a.getMaxX() - a.getWidth() * share : a.getX() + a.getWidth() * share;
        }

        /**
         * A popping comic burst with the slide's headline.
         */
        private static void burst(Graphics2D g, double cx, double cy, double width, String text, Color color,
                                  double age) {
            double pop = age < 0.45 ? elastic(age / 0.45) : 1;
            if (pop <= 0.01) {
                return;
            }
            double w = Math.min(width, 340);
            Graphics2D b = (Graphics2D) g.create();
            b.translate(cx, cy);
            b.scale(pop, pop);
            Theme.Comic.paintBurst(b, 0, 0, w, w * 0.46, color, Assets.Texts.directional(text.toUpperCase(Locale.ROOT)), 40f, -0.07);
            b.dispose();
        }

        /**
         * A white comic caption card: an optional name in a comic font and color, then the text, wrapped.
         */
        private void caption(Graphics2D g, double x, double y, double width, String name, Color nameColor, String text,
                             double age) {
            double show = Theme.Motion.ease(Math.min(1, Math.max(0, (age - 0.2) / 0.4)));
            if (show <= 0) {
                return;
            }
            Graphics2D c = (Graphics2D) g.create();
            c.translate(0, (1 - show) * 24);
            Font body = Theme.Fonts.baseFont(17, false);
            FontMetrics bm = c.getFontMetrics(body);
            List<String> lines = wrap(bm, text, width - 36);
            Font nameFont = name == null ? null : Theme.Fonts.comicFont(32f, name);
            FontMetrics nm = nameFont == null ? null : c.getFontMetrics(nameFont);
            double height = 28 + (nm == null ? 0 : nm.getHeight()) + lines.size() * bm.getHeight();
            RoundRectangle2D box = new RoundRectangle2D.Double(x, y, width, height, 22, 22);
            c.setColor(Theme.Comic.inkColor());
            c.fill(new RoundRectangle2D.Double(x + 5, y + 6, width, height, 22, 22));
            c.setColor(Theme.Comic.comicPaper());
            c.fill(box);
            c.setColor(Theme.Comic.inkColor());
            c.setStroke(new BasicStroke(3f));
            c.draw(box);
            double baseline = y + 14;
            if (nm != null) {
                baseline += nm.getAscent();
                c.setFont(nameFont);
                c.setColor(Theme.mix(nameColor, Theme.Comic.inkColor(), 0.85));
                String shownName = Assets.Texts.directional(name);
                c.drawString(shownName, (float) (isRtl() ? x + width - 18 - nm.stringWidth(shownName) : x + 18),
                        (float) baseline);
                baseline += nm.getDescent();
            }
            c.setFont(body);
            c.setColor(Theme.Comic.inkColor());
            for (String line : lines) {
                baseline += bm.getAscent();
                c.drawString(line, (float) (isRtl() ? x + width - 18 - bm.stringWidth(line) : x + 18), (float) baseline);
                baseline += bm.getDescent() + bm.getLeading();
            }
            c.dispose();
        }

        /**
         * @return the text broken into lines that fit the width
         */
        private static List<String> wrap(FontMetrics fm, String text, double width) {
            List<String> lines = new ArrayList<>();
            for (String paragraph : text.split("\n")) {
                StringBuilder line = new StringBuilder();
                for (String word : paragraph.split(" ")) {
                    String next = line.isEmpty() ? word : line + " " + word;
                    if (fm.stringWidth(next) > width && !line.isEmpty()) {
                        lines.add(Assets.Texts.directional(line.toString()));
                        line = new StringBuilder(word);
                    } else {
                        line = new StringBuilder(next);
                    }
                }
                lines.add(Assets.Texts.directional(line.toString()));
            }
            return lines;
        }

        /**
         * @return an ease that overshoots and settles, for {@code t} from 0 to 1
         */
        private static double elastic(double t) {
            return t >= 1 ? 1 : 1 - Math.pow(2, -9 * t) * Math.cos(t * 3.2 * Math.PI) * 0.9;
        }

        /**
         * @return a panther color for a player name from the history (the history keeps names, not colors)
         */
        private static Color furOf(String name) {
            return Theme.colorOf(Player.Color.PRESETS.get(Math.floorMod(name.hashCode(), Player.Color.PRESETS.size())));
        }

        /**
         * @return the duration as {@code M:SS} or {@code H:MM:SS}
         */
        private static String time(Duration duration) {
            long s = duration.toSeconds();
            return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                    : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
        }
    }
}
