package view;

import model.*;

import javax.swing.*;
import javax.swing.Timer; // the Swing timer, not java.util.Timer
import java.awt.*;
import java.awt.Shape;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List; // java.util.List, not java.awt.List
import java.util.function.Consumer;

/**
 * The board component (FR-04, FR-18, NFR-02): the flat 2D board seen from above. The board artwork ({@link BoardArt}) is
 * placed and scaled by the {@link Camera} so the whole board fits and never moves. On it stand the pieces ({@link Pieces}), which hop
 * during moves ({@link Pieces.MoveAnimation}); the LED frame lights up in the color of the player whose turn it is;
 * {@link Effects} adds light rings, comic bursts and particles; {@link Banner} shows messages in the middle.
 * <p>
 * It never decides which moves are legal — selectable pieces come from the model (FR-31). The camera and effects are
 * purely visual.
 */
public final class BoardPanel extends JPanel {

    /** Upper bound for a whole move animation, regardless of distance (NFR-13). */
    public static final int MAX_MOVE_ANIMATION_MILLIS = 900;


    /** Pixels per cell of the board artwork image. */
    private static final double ART_CELL = 80;

    private final Camera view = new Camera();
    private final Pieces pieces = new Pieces();
    private final Pieces.MoveAnimation animation = new Pieces.MoveAnimation();
    private final Effects effects = new Effects();
    private Game.State game;
    private Consumer<Piece> onPieceClicked = p -> { };
    /** The message in the middle of the board, or {@code null}. */
    private Banner banner;

    private boolean effectsOn = true;

    private BufferedImage art;
    private Game.State artGame;

    /** The highlighted piece under the mouse: its path is previewed on the board. */
    private Piece hover;
    private long lastFrame;
    /** {@code true} between a mouse press and its release, unless the press closed a message. */
    private boolean pressed;

    /**
     * Creates an empty board view.
     */
    public BoardPanel() {
        // Transparent: the screen's background shows around the board.
        setOpaque(false);
        setPreferredSize(new Dimension(680, 680));
        setToolTipText("");
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressed = true;
                if (banner != null) {
                    // A click closes the message; a message that holds the game lets it continue at once.
                    Banner closed = banner;
                    banner = null;
                    repaint();
                    if (closed.onClose() != null) {
                        pressed = false;
                        closed.onClose().run();
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (pressed) {
                    Piece piece = clickablePieceAt(e);
                    if (piece != null) {
                        // Once chosen, the landing ring goes away; the move itself lights up its squares.
                        hover = null;
                        onPieceClicked.accept(piece);
                    }
                }
                pressed = false;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                hover = clickablePieceAt(e);
                setCursor(Cursor.getPredefinedCursor(hover != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = null;
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    // ------------------------------------------------------------------ match

    /**
     * @param game match to display, or {@code null}
     */
    public void setGame(Game.State game) {
        animation.stop();
        banner = null;
        effects.clear();
        pieces.clear();
        art = null;
        this.game = game;
        if (game != null) {
            view.setBoard(game.board().cols(), game.board().rows(), BoardArt.MARGIN);
            Theme.Motion.subscribe(this);
        } else {
            Theme.Motion.unsubscribe(this);
        }
        repaint();
    }

    /**
     * @param movable pieces to highlight and accept clicks for
     */
    public void setSelectablePieces(Set<Piece> movable) {
        // TODO T1.14 (step 16): choosing a piece with the mouse.
    }

    /**
     * @param handler called when a highlighted piece is clicked
     */
    public void setOnPieceClicked(Consumer<Piece> handler) {
        this.onPieceClicked = handler;
    }

    /**
     * @return {@code true} while a move animation is running
     */
    public boolean isAnimating() {
        return animation.isRunning();
    }

    /**
     * Animates an applied move as hops; afterwards shows light rings and comic bursts for captures, releases and
     * finishes.
     *
     * @param move     the applied move
     * @param captured pieces captured on the landing square
     * @param onDone   called when the animation ends
     */
    public void animateMove(Move move, List<Piece> captured, Runnable onDone) {
        // TODO T1.14 (step 16): the move animation.
        onDone.run();
    }

    /**
     * Pops up a comic burst (a star with big letters, particles) in the middle of the board, e.g. "Let's go!" when a
     * match starts. Does nothing while effects are switched off.
     *
     * @param text    the big word
     * @param caption small line under it, or {@code null}
     * @param color   accent color
     */
    public void comicBurst(String text, String caption, Color color) {
        if (effectsOn) {
            effects.addBurst(text, caption, color, null);
        }
    }

    /**
     * Pops up a comic burst over a player's castle, e.g. "NOPE!" when that player cannot move.
     *
     * @param text    the big word
     * @param caption small line under it, or {@code null}
     * @param player  the player whose castle it pops over
     */
    public void comicBurst(String text, String caption, Player player) {
        if (effectsOn && game != null) {
            Board.GridArea castle = game.board().castleArea(player.seat());
            Point2D at = new Point2D.Double(castle.col() + castle.cols() / 2.0, castle.row() + castle.rows() / 2.0);
            effects.addBurst(text, caption, Theme.neonOf(player.color()), at);
        }
    }

    /**
     * The comic word for where a piece landed: an eaten opponent (KAPOW!, CHOMP!…), a panther home or a finished
     * player's place, leaving the castle (DEPLOY!), a protected square (SHIELD!) or a question square (QUIZ!).
     */
    private void showLandingEffect(Move move, List<Piece> captured, Square landing, Point2D at, Color color) {
        Player mover = move.piece().owner();
        if (!captured.isEmpty()) {
            effects.addBurst(Assets.Texts.get("pop.eat" + (1 + RANDOM.nextInt(5))), Assets.Texts.get("board.burst.ate", mover.name(),
                    captured.get(0).owner().name()), color, at);
        } else if (move.piece().isFinished()) {
            int place = game.ranking().indexOf(mover) + 1;
            String word = mover.hasFinishedAll() && place > 1 ? Assets.Texts.get("pop.place", Assets.Texts.ordinal(place))
                    : Assets.Texts.get("board.burst.home");
            effects.addBurst(word, mover.name(), color, at);
        } else if (move.from() == Piece.IN_CASTLE) {
            effects.addBurst(Assets.Texts.get("pop.deploy"), mover.name(), color, at);
        } else if (landing.type() == Square.Type.STAR || landing.type() == Square.Type.START) {
            effects.addBurst(Assets.Texts.get("pop.shield"), Assets.Texts.get("pop.safe"), color, at);
        } else if (landing.type() == Square.Type.QUESTION) {
            effects.addBurst(Assets.Texts.get("pop.quiz"), Assets.Texts.get("level." + landing.questionLevel().name()), color, at);
        }
    }

    private static final Random RANDOM = new Random();

    /**
     * Pops up a message in the middle of the board for a while (it fades in and out). A new message replaces the
     * old one. A click on the board closes it.
     *
     * @param title   short heading, e.g. "Dana can't move"
     * @param text    explanation; line breaks start a new line
     * @param color   accent color, usually the player's color
     * @param millis  how long the message stays
     * @param onClose run when the player clicks the message away, or {@code null}
     */
    public void showBanner(String title, String text, Color color, int millis, Runnable onClose) {
        showBanner(title, text, color, millis, onClose, null);
    }

    /**
     * Like {@link #showBanner(String, String, Color, int, Runnable)}, with a Panther Ludo icon at the side.
     *
     * @param title   heading
     * @param text    explanation
     * @param color   accent color
     * @param millis  how long the message stays
     * @param onClose run when it is clicked away, or {@code null}
     * @param icon    icon name (see {@link Theme#drawIcon}), or {@code null}
     */
    public void showBanner(String title, String text, Color color, int millis, Runnable onClose, String icon) {
        showBanner(title, text, color, millis, onClose, icon, null);
    }

    /**
     * The comic message with the player's panther "speaking" it.
     *
     * @param title   heading
     * @param text    explanation
     * @param color   accent color
     * @param millis  how long the message stays
     * @param onClose run when it is clicked away, or {@code null}
     * @param icon    icon name (see {@link Theme#drawIcon}), or {@code null}
     * @param speaker the color of the panther avatar that speaks, or {@code null} (then the icon speaks)
     */
    public void showBanner(String title, String text, Color color, int millis, Runnable onClose, String icon,
                           Color speaker) {
        banner = new Banner(title, text, color, System.nanoTime(), millis, onClose, icon, speaker);
        repaint();
    }

    /**
     * Removes the message in the middle of the board, if any.
     */
    public void hideBanner() {
        banner = null;
        repaint();
    }

    /**
     * Stops any running animation without calling its completion callback.
     */
    public void stopAnimation() {
        animation.stop();
    }

    // ------------------------------------------------------------------ look

    /**
     * @param on {@code false} to switch off comic bursts and particles
     */
    public void setEffectsOn(boolean on) {
        this.effectsOn = on;
        if (!on) {
            effects.clearBursts();
        }
    }

    // ------------------------------------------------------------------ mouse helpers

    @Override
    public String getToolTipText(MouseEvent event) {
        Piece piece = pieces.pieceAt(event.getPoint(), false);
        if (piece == null) {
            return null;
        }
        Player owner = piece.owner();
        String where = Assets.Texts.get(piece.isInCastle() ? "board.inCastle"
                : piece.isFinished() ? "board.home" : "board.onBoard");
        return Assets.Texts.get("board.pieceTip", owner.name(), piece.number(), Assets.Texts.color(owner.color()), where);
    }

    /**
     * Finds the highlighted piece under the mouse, if any; clicks elsewhere are ignored.
     */
    private Piece clickablePieceAt(MouseEvent e) {
        // TODO T1.14 (step 16): choosing a piece with the mouse.
        return null;
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics g) {
        if (game == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : (now - lastFrame) / 1e9;
        lastFrame = now;
        view.fitToPanel(getWidth(), getHeight());
        paintSurface(g2);
        paintLedFrame(g2);
        paintCurrentCastle(g2);
        effects.paintRings(g2, view);
        paintPathPreview(g2);
        pieces.paint(g2, game, view, animation);
        effects.paintBursts(g2, view, getWidth(), getHeight(), dt);
        if (banner != null && !banner.paint(g2, new Rectangle2D.Double(0, 0, getWidth(), getHeight()))) {
            banner = null;
        }
        g2.dispose();
    }

    /**
     * The board artwork, placed by the camera.
     */
    private void paintSurface(Graphics2D g2) {
        if (art == null || artGame != game) {
            art = BoardArt.render(game, ART_CELL);
            artGame = game;
        }
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        Board board = game.board();
        double m = BoardArt.MARGIN;
        AffineTransform t = view.planeTransform(0);
        t.translate(-m, -m);
        t.scale(1 / ART_CELL, 1 / ART_CELL);
        g.drawImage(art, t, null);
        g.dispose();
    }

    /**
     * Running LED lights around the board with a glowing frame, in one steady color during the game; after the game
     * they show every player's color.
     */
    private void paintLedFrame(Graphics2D g2) {
        Board board = game.board();
        double inset = 0.2;
        Rectangle2D strip = new Rectangle2D.Double(-inset, -inset, board.cols() + 2 * inset,
                board.rows() + 2 * inset);
        int core = view.scale() > 40 ? 3 : 2;
        if (game.isOver()) {
            Color[] palette = game.players().stream().map(p -> Theme.neonOf(p.color())).toArray(Color[]::new);
            paintLedStrip(g2, strip, 0.5, core, palette, 0.06);
            return;
        }
        // One steady color for the frame and its lights: the board does not change color from turn to turn (the
        // player card and the glowing pieces show whose turn it is).
        Color steady = Theme.neonPurple();
        Shape frame = view.projectShape(new RoundRectangle2D.Double(-0.38, -0.38,
                board.cols() + 0.76, board.rows() + 0.76, 0.6, 0.6), 0);
        Theme.Glow.paintGlowStroke(g2, frame, steady, 2.4f, 0.7);
        paintLedStrip(g2, strip, 0.42, core, new Color[]{steady}, 0.12);
    }

    /**
     * LEDs along a rectangle of the board, each placed by the camera (a chasing light runs around).
     */
    private void paintLedStrip(Graphics2D g2, Rectangle2D r, double spacing, int core, Color[] palette, double speed) {
        double w = r.getWidth();
        double h = r.getHeight();
        double perimeter = 2 * (w + h);
        int count = Math.max(8, (int) (perimeter / spacing));
        double t = Theme.Motion.secondsSinceStart() * speed;
        for (int i = 0; i < count; i++) {
            double d = perimeter * i / count;
            double x;
            double y;
            if (d < w) {
                x = r.getX() + d;
                y = r.getY();
            } else if (d < w + h) {
                x = r.getMaxX();
                y = r.getY() + (d - w);
            } else if (d < 2 * w + h) {
                x = r.getMaxX() - (d - w - h);
                y = r.getMaxY();
            } else {
                x = r.getX();
                y = r.getMaxY() - (d - 2 * w - h);
            }
            double phase = ((double) i / count - t) % 1.0;
            if (phase < 0) {
                phase += 1;
            }
            double wave = Math.pow(Math.max(0, Math.cos(phase * 2 * Math.PI * 3)), 6);
            Point2D p = view.project(x, y, 0);
            Theme.Glow.paintLed(g2, p.getX(), p.getY(), core, palette[(i / 6) % palette.length], 0.22 + 0.78 * wave);
        }
    }

    /**
     * Pulsing glow around the castle of the player whose turn it is.
     */
    private void paintCurrentCastle(Graphics2D g2) {
        if (game.isOver()) {
            return;
        }
        Player current = game.currentPlayer();
        Board.GridArea area = game.board().castleArea(current.seat());
        double pad = 0.12;
        Shape outer = view.projectShape(new RoundRectangle2D.Double(area.col() + pad,
                area.row() + pad, area.cols() - 2 * pad, area.rows() - 2 * pad, 0.7, 0.7), 0);
        Theme.Glow.paintGlowStroke(g2, outer, Theme.neonOf(current.color()), 2.4f, 0.4 + 0.6 * Theme.Motion.pulse(0.9));
    }

    /**
     * While a player chooses a panther: shows where the panther under the mouse would go, a glowing line along its path
     * ending in an arrow head and a ring on the square where it would land. Once a panther is chosen it disappears.
     */
    private void paintPathPreview(Graphics2D g2) {
        // TODO T1.14 (step 16): the arrow to the landing square.
    }

    /**
     * A point on or above the board, in board units.
     *
     * @param x board x
     * @param y board y
     * @param z height
     */
    record Point3(double x, double y, double z) {
    }

    // ------------------------------------------------------------------ banner

    /**
     * A message that pops up in the middle of the board (for example "Dana can't move" with the reason), fades in,
     * stays for a while and fades out.
     *
     * @param title          heading
     * @param text           explanation
     * @param color          accent color
     * @param startMillis    {@link System#nanoTime()} when it appeared
     * @param durationMillis how long it stays
     * @param onClose        run when it is clicked away, or {@code null}
     */
    private record Banner(String title, String text, Color color, long startMillis, int durationMillis, Runnable onClose,
                          String icon, Color speaker) {

        private static final double FADE_IN = 180;
        private static final double FADE_OUT = 320;

        /**
         * Paints the message as a comic panel: a yellow caption strip ("Meanwhile, on the board…"), the player's
         * panther speaking in a speech balloon with the comic title and the explanation, and the icon with "Click to
         * continue" under it. A short message (only a title) is just the panther and its balloon. It pops in.
         *
         * @return {@code false} once its time is over
         */
        boolean paint(Graphics2D g2, Rectangle2D area) {
            double age = (System.nanoTime() - startMillis) / 1e6;
            if (age >= durationMillis) {
                return false;
            }
            double alpha = Math.max(0, Math.min(1, Math.min(age / FADE_IN, (durationMillis - age) / FADE_OUT)));
            double pop = age < 260 ? 1 - Math.pow(2, -9 * age / 260) * Math.cos(age / 260 * 3.2 * Math.PI) * 0.6 : 1;
            boolean full = text != null && !text.isBlank();
            Color ink = new Color(0x1B1640);
            Color paper = Theme.mode() == Theme.Mode.LIGHT ? Color.WHITE : new Color(0xFFFBF0);
            double width = Math.min(area.getWidth() * 0.86, full ? 600 : 470);
            double pad = 18;
            double speakerSize = full ? 100 : 84;
            String heading = Assets.Texts.directional(title.toUpperCase(Locale.ROOT));
            Font titleFont = Theme.Fonts.comicFont(full ? 32f : 34f, heading);
            Font textFont = Theme.Fonts.baseFont(16, false);
            FontMetrics tm = g2.getFontMetrics(titleFont);
            FontMetrics fm = g2.getFontMetrics(textFont);
            boolean rtl = !Assets.Texts.orientation().isLeftToRight();
            double balloonX = pad + speakerSize + 22;
            double balloonW = width - balloonX - pad;
            List<String> lines = full ? wrap(fm, text, balloonW - 30) : List.of();
            while (titleFont.getSize2D() > 24 && tm.stringWidth(heading) > balloonW - 30) {
                titleFont = titleFont.deriveFont(titleFont.getSize2D() - 1);
                tm = g2.getFontMetrics(titleFont);
            }
            // A heading that is still too long goes on two (or more) lines, each in the reading direction.
            List<String> titleLines = wrap(tm, heading, balloonW - 30);
            double titleH = titleLines.size() * tm.getHeight();
            double balloonH = 14 + titleH + (lines.isEmpty() ? 0 : 4 + lines.size() * fm.getHeight()) + 12;
            double strip = full ? 40 : 0;
            boolean bottom = full;
            double body = Math.max(speakerSize, balloonH);
            double height = strip + pad + body + (bottom ? 42 : 0) + pad;
            Graphics2D g = (Graphics2D) g2.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
            if (onClose != null) {
                // A message that holds the game dims the board; the short turn message does not.
                g.setColor(Theme.alpha(Theme.deepBackground(), 0.35));
                g.fill(new RoundRectangle2D.Double(area.getX(), area.getY(), area.getWidth(), area.getHeight(),
                        24, 24));
            }
            g.translate(area.getCenterX(), area.getCenterY());
            g.rotate(-0.012);
            g.scale(pop, pop);
            double x = -width / 2;
            double y = -height / 2;
            RoundRectangle2D box = new RoundRectangle2D.Double(x, y, width, height, 14, 14);
            g.setColor(ink);
            g.fill(AffineTransform.getTranslateInstance(7, 9).createTransformedShape(box));
            g.setColor(Theme.mode() == Theme.Mode.LIGHT ? Theme.mix(color, Color.WHITE, 0.12) : Theme.surface());
            g.fill(box);
            Shape clip = g.getClip();
            g.clip(box);
            // Halftone dots on the panel background, for the comic print look.
            g.setColor(Theme.alpha(color, 0.18));
            for (double dy = y + strip + 4; dy < y + height; dy += 12) {
                for (double dx = x + 4; dx < x + width; dx += 12) {
                    g.fill(new Ellipse2D.Double(dx, dy, 3, 3));
                }
            }
            if (full) {
                g.setColor(new Color(0xFFD23F));
                g.fill(new Rectangle2D.Double(x, y, width, strip));
            }
            g.setClip(clip);
            g.setColor(ink);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (full) {
                g.draw(new Line2D.Double(x, y + strip, x + width, y + strip));
                String caption = Assets.Texts.directional(Assets.Texts.get("banner.caption").toUpperCase(Locale.ROOT));
                g.setFont(Theme.Fonts.comicFont(22f, caption));
                FontMetrics cm = g.getFontMetrics();
                double captionX = rtl ? x + width - pad - cm.stringWidth(caption) : x + pad;
                g.drawString(caption, (float) captionX, (float) (y + (strip + cm.getAscent() - cm.getDescent()) / 2));
            }
            g.draw(box);

            // The speaker: the player's panther, or the icon (e.g. the game bot).
            double sy = y + strip + pad + body / 2;
            double sx = rtl ? x + width - pad - speakerSize / 2 : x + pad + speakerSize / 2;
            if (speaker != null) {
                Theme.Panther.paintAvatar(g, sx, sy, speakerSize, speaker, 0.3);
            } else if (icon != null) {
                Theme.drawIcon(g, icon, sx, sy, speakerSize);
            }

            // The speech balloon with its tail pointing at the speaker.
            double by = y + strip + pad + (body - balloonH) / 2;
            double left = rtl ? x + pad : x + balloonX;
            RoundRectangle2D balloon = new RoundRectangle2D.Double(left, by, balloonW, balloonH, 30, 30);
            double edge = rtl ? left + balloonW - 2 : left + 2;
            double tip = rtl ? left + balloonW + 18 : left - 18;
            Path2D tail = new Path2D.Double();
            tail.moveTo(edge, sy - 12);
            tail.lineTo(tip, sy + 4);
            tail.lineTo(edge, sy + 10);
            tail.closePath();
            Area shape = new Area(balloon);
            shape.add(new Area(tail));
            g.setColor(paper);
            g.fill(shape);
            g.setColor(ink);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(shape);
            double cx = left + balloonW / 2;
            g.setFont(titleFont);
            double baseline = by + 12 + tm.getAscent();
            g.setColor(Theme.mix(color, ink, 0.85));
            for (int i = 0; i < titleLines.size(); i++) {
                String line = titleLines.get(i);
                g.drawString(line, (float) (cx - tm.stringWidth(line) / 2.0), (float) baseline);
                if (i < titleLines.size() - 1) {
                    baseline += tm.getHeight();
                }
            }
            g.setFont(textFont);
            g.setColor(ink);
            baseline += tm.getDescent() + 4 + fm.getAscent();
            for (String line : lines) {
                g.drawString(line, (float) (cx - fm.stringWidth(line) / 2.0), (float) baseline);
                baseline += fm.getHeight();
            }
            if (bottom) {
                double rowY = y + strip + pad + body + 8;
                if (icon != null && speaker != null) {
                    Theme.drawIcon(g, icon, rtl ? x + width - pad - 20 : x + pad + 20, rowY + 17, 40);
                }
                if (onClose != null) {
                    String click = Assets.Texts.get("banner.clickShort");
                    g.setFont(Theme.Fonts.baseFont(15, true));
                    FontMetrics km = g.getFontMetrics();
                    g.setColor(Theme.text());
                    g.drawString(click, (float) (rtl ? x + pad : x + width - pad - km.stringWidth(click)),
                            (float) (rowY + 17 + km.getAscent() / 2.0 - 2));
                }
            }
            g.dispose();
            return true;
        }

        /**
         * Breaks the text into lines that fit the width; line breaks in the text are kept.
         */
        private static List<String> wrap(FontMetrics fm, String text, double width) {
            List<String> lines = new ArrayList<>();
            if (text == null || text.isBlank()) {
                return lines;
            }
            for (String paragraph : text.split("\n")) {
                StringBuilder line = new StringBuilder();
                for (String word : paragraph.split(" ")) {
                    String candidate = line.isEmpty() ? word : line + " " + word;
                    if (fm.stringWidth(candidate) > width && !line.isEmpty()) {
                        lines.add(Assets.Texts.directional(line.toString()));
                        line = new StringBuilder(word);
                    } else {
                        line = new StringBuilder(candidate);
                    }
                }
                lines.add(Assets.Texts.directional(line.toString()));
            }
            return lines;
        }
    }

    // ------------------------------------------------------------------ effects

    /**
     * Short visual effects: light rings on the board (captures, pieces leaving the castle, pieces reaching home),
     * comic bursts with flying particles. Purely visual.
     */
    private static final class Effects {

        private static final double RING_MILLIS = 650;
        private static final double BURST_MILLIS = 1500;
        private static final double GRAVITY = 900;

        /** A light ring on the board. */
        private record Ring(Point2D at, Color color, long start, boolean big) {
        }

        /** A comic burst; {@code at} is a board point, or {@code null} for the middle of the panel. */
        private static final class Burst {
            final String text;
            final String caption;
            final Color color;
            final Point2D at;
            final long start = System.nanoTime();
            boolean spawned;

            /**
             * @param text    the big comic word
             * @param caption the small text under it, or {@code null}
             * @param color   fill color
             * @param at      where on the board it pops up
             */
            Burst(String text, String caption, Color color, Point2D at) {
                this.text = text;
                this.caption = caption;
                this.color = color;
                this.at = at;
            }
        }

        /** One flying particle, in screen pixels. */
        private static final class Particle {
            double x;
            double y;
            double vx;
            double vy;
            double life;
            final double size;
            final Color color;

            /**
             * @param x     start x
             * @param y     start y
             * @param vx    speed to the right
             * @param vy    speed downward
             * @param size  diameter
             * @param color color
             */
            Particle(double x, double y, double vx, double vy, double size, Color color) {
                this.x = x;
                this.y = y;
                this.vx = vx;
                this.vy = vy;
                this.size = size;
                this.color = color;
                this.life = 1;
            }
        }

        private final List<Ring> rings = new ArrayList<>();
        private final List<Burst> bursts = new ArrayList<>();
        private final List<Particle> particles = new ArrayList<>();
        private final Random random = new Random();

        /**
         * @param at    board point
         * @param color ring color
         * @param big   larger ring (capture or finish)
         */
        void addRing(Point2D at, Color color, boolean big) {
            rings.add(new Ring(at, color, System.nanoTime(), big));
        }

        /**
         * @param text    big word
         * @param caption line under it, or {@code null}
         * @param color   accent color
         * @param at      board point, or {@code null} for the middle
         */
        void addBurst(String text, String caption, Color color, Point2D at) {
            bursts.clear();
            bursts.add(new Burst(text, caption, color, at));
        }

        /**
         * Removes everything.
         */
        void clear() {
            rings.clear();
            clearBursts();
        }

        /**
         * Removes bursts and particles.
         */
        void clearBursts() {
            bursts.clear();
            particles.clear();
        }

        /**
         * Paints the light rings on the board and drops finished ones.
         */
        void paintRings(Graphics2D g2, Camera view) {
            long now = System.nanoTime();
            Iterator<Ring> it = rings.iterator();
            while (it.hasNext()) {
                Ring ring = it.next();
                double p = (now - ring.start()) / 1e6 / RING_MILLIS;
                if (p >= 1) {
                    it.remove();
                    continue;
                }
                double radius = 0.3 + (ring.big() ? 1.4 : 0.8) * Theme.Motion.ease(p);
                double x = ring.at().getX();
                double y = ring.at().getY();
                Shape circle = view.projectShape(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2), 0);
                Theme.Glow.paintGlowStroke(g2, circle, ring.color(), 2f, 1 - p);
                int sparks = ring.big() ? 12 : 8;
                for (int i = 0; i < sparks; i++) {
                    double angle = i * 2 * Math.PI / sparks + p;
                    double sx = x + Math.cos(angle) * radius * 1.1;
                    double sy = y + Math.sin(angle) * radius * 1.1;
                    Point2D s = view.project(sx, sy, p * 0.8);
                    Theme.Glow.paintLed(g2, s.getX(), s.getY(), 2, ring.color(), 1 - p);
                }
            }
        }

        /**
         * Paints the comic bursts and particles (in screen space, above the board).
         */
        void paintBursts(Graphics2D g2, Camera view, int width, int height, double dt) {
            long now = System.nanoTime();
            Iterator<Burst> it = bursts.iterator();
            while (it.hasNext()) {
                Burst burst = it.next();
                double age = (now - burst.start) / 1e6;
                if (age >= BURST_MILLIS) {
                    it.remove();
                    continue;
                }
                Point2D c = burst.at == null
                        ? new Point2D.Double(width / 2.0, height / 2.0)
                        : view.project(burst.at.getX(), burst.at.getY(), 1.4);
                double radius = Math.max(70, Math.min(width, height) * 0.15);
                // Keep the whole star inside the panel.
                double cx = Math.max(radius * 1.2, Math.min(width - radius * 1.2, c.getX()));
                double cy = Math.max(radius * 1.1, Math.min(height - radius * 1.1, c.getY()));
                if (!burst.spawned) {
                    burst.spawned = true;
                    spawn(cx, cy, burst.color, radius);
                }
                paintBurst(g2, burst, cx, cy, radius, age);
            }
            Iterator<Particle> pi = particles.iterator();
            while (pi.hasNext()) {
                Particle p = pi.next();
                p.vy += GRAVITY * dt;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.life -= dt / 1.1;
                if (p.life <= 0) {
                    pi.remove();
                    continue;
                }
                g2.setColor(Theme.alpha(p.color, Math.min(1, p.life * 1.5)));
                double s = p.size * (0.5 + 0.5 * p.life);
                g2.fill(new Rectangle2D.Double(p.x - s / 2, p.y - s / 2, s, s * 0.6));
            }
        }

        /**
         * Throws confetti in the burst's color, gold and white.
         */
        private void spawn(double cx, double cy, Color color, double radius) {
            Color[] colors = {color, Theme.pantherGold(), Color.WHITE, Theme.neonCyan(), Theme.neonPurple()};
            for (int i = 0; i < 34; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double speed = 220 + random.nextDouble() * 420;
                particles.add(new Particle(cx + Math.cos(angle) * radius * 0.3, cy + Math.sin(angle) * radius * 0.3,
                        Math.cos(angle) * speed, Math.sin(angle) * speed - 260, 5 + random.nextDouble() * 7,
                        colors[i % colors.length]));
            }
        }

        /**
         * A spiky comic star in the player's color and gold, with outlined comic letters and a caption tag.
         */
        private static void paintBurst(Graphics2D g2, Burst burst, double cx, double cy, double radius, double age) {
            double pop = age < 260 ? elastic(age / 260) : 1;
            double alpha = age > BURST_MILLIS - 300 ? (BURST_MILLIS - age) / 300 : 1;
            Graphics2D g = (Graphics2D) g2.create();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, alpha)));
            g.translate(cx, cy);
            g.rotate(-0.06);
            g.scale(pop, pop);
            Random shape = new Random(burst.text.hashCode());
            // Speed lines behind the star.
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.alpha(Color.BLACK, 0.55));
            for (int i = 0; i < 14; i++) {
                double a = i * Math.PI * 2 / 14 + 0.1;
                double r1 = radius * (1.08 + 0.1 * shape.nextDouble());
                double r2 = radius * (1.35 + 0.25 * shape.nextDouble());
                g.draw(new Line2D.Double(Math.cos(a) * r1, Math.sin(a) * r1, Math.cos(a) * r2, Math.sin(a) * r2));
            }
            Path2D outer = star(radius * 1.08, radius * 0.78, 18, shape);
            Path2D inner = star(radius, radius * 0.72, 18, new Random(burst.text.hashCode() + 1));
            g.setColor(Theme.alpha(Color.BLACK, 0.4));
            g.fill(AffineTransform.getTranslateInstance(6, 8).createTransformedShape(outer));
            g.setColor(Theme.mix(burst.color, Color.WHITE, 0.85));
            g.fill(outer);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(0, -radius * 0.2), (float) radius,
                    new float[]{0f, 0.6f, 1f}, new Color[]{new Color(0xFFF6B0), new Color(0xFFD23F),
                    new Color(0xFF9F1C)}));
            g.fill(inner);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(inner);
            // Halftone dots for the comic look.
            g.setColor(Theme.alpha(new Color(0xFF7A00), 0.25));
            Shape clip = g.getClip();
            g.clip(inner);
            double dot = Math.max(5, radius * 0.07);
            for (double y = -radius; y < radius; y += dot * 1.6) {
                for (double x = -radius; x < radius; x += dot * 1.6) {
                    if (x + y > radius * 0.2) {
                        g.fill(new Ellipse2D.Double(x, y, dot * 0.55, dot * 0.55));
                    }
                }
            }
            g.setClip(clip);
            paintComicText(g, burst.text, radius * 0.44, 0, radius * 0.14, Color.WHITE, radius * 1.6);
            if (burst.caption != null && !burst.caption.isBlank()) {
                String upper = burst.caption.toUpperCase(Locale.ROOT);
                Font font = Theme.Fonts.comicFont((float) Math.max(14, radius * 0.17), upper);
                FontMetrics fm = g.getFontMetrics(font);
                // A long caption goes on two lines instead of running off the tag; each line reads in its direction.
                List<String> captionLines = Banner.wrap(fm, upper, radius * 2.2);
                double w = 0;
                for (String line : captionLines) {
                    w = Math.max(w, fm.stringWidth(line));
                }
                w += 22;
                double h = captionLines.size() * fm.getHeight() + 6;
                double y = radius * 0.62;
                g.rotate(0.05);
                RoundRectangle2D tag = new RoundRectangle2D.Double(-w / 2, y, w, h, 6, 6);
                g.setColor(Theme.alpha(Color.BLACK, 0.4));
                g.fill(AffineTransform.getTranslateInstance(3, 4).createTransformedShape(tag));
                g.setColor(new Color(0xFFFBEA));
                g.fill(tag);
                g.setColor(Color.BLACK);
                g.setStroke(new BasicStroke(2.5f));
                g.draw(tag);
                g.setFont(font);
                double baseline = y + 3 + fm.getAscent();
                for (String line : captionLines) {
                    g.drawString(line, (float) (-fm.stringWidth(line) / 2.0), (float) baseline);
                    baseline += fm.getHeight();
                }
            }
            g.dispose();
        }

        /**
         * Big letters with a thick black outline and a drop shadow; shrinks to fit {@code maxWidth}.
         */
        private static void paintComicText(Graphics2D g, String text, double size, double x, double y, Color fill,
                                           double maxWidth) {
            String shown = Assets.Texts.directional(text.toUpperCase(Locale.ROOT));
            Font font = Theme.Fonts.comicFont((float) size, shown);
            TextLayout layout = new TextLayout(shown, font, g.getFontRenderContext());
            double w = layout.getBounds().getWidth();
            double scale = w > maxWidth ? maxWidth / w : 1;
            Shape outline = layout.getOutline(null);
            Rectangle2D b = outline.getBounds2D();
            AffineTransform t = AffineTransform.getTranslateInstance(x, y);
            t.scale(scale, scale);
            t.translate(-b.getCenterX(), -b.getCenterY());
            Shape letters = t.createTransformedShape(outline);
            g.setColor(Theme.alpha(Color.BLACK, 0.5));
            g.fill(AffineTransform.getTranslateInstance(4, 5).createTransformedShape(letters));
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke((float) Math.max(4, size * 0.16), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            g.draw(letters);
            g.setPaint(new GradientPaint(0, (float) (y - size * 0.4), fill, 0, (float) (y + size * 0.4),
                    new Color(0xFFE27A)));
            g.fill(letters);
        }

        /**
         * A star outline with slightly random spikes.
         */
        private static Path2D star(double outer, double inner, int spikes, Random random) {
            Path2D p = new Path2D.Double();
            for (int i = 0; i < spikes * 2; i++) {
                double r = i % 2 == 0 ? outer * (0.86 + 0.14 * random.nextDouble()) : inner;
                double a = i * Math.PI / spikes - Math.PI / 2;
                double x = Math.cos(a) * r * 1.25;
                double y = Math.sin(a) * r * 0.9;
                if (i == 0) {
                    p.moveTo(x, y);
                } else {
                    p.lineTo(x, y);
                }
            }
            p.closePath();
            return p;
        }

        /**
         * @param t 0–1
         * @return a pop that overshoots and settles at 1
         */
        private static double elastic(double t) {
            return 1 - Math.pow(2, -9 * t) * Math.cos(t * 3.5 * Math.PI);
        }
    }

    // ------------------------------------------------------------------ pieces

    /**
     * Places and paints every piece (in castles, on squares and while hopping) as standing figures sorted from far to
     * near, highlights the pieces the model says may move (FR-31) and remembers where each piece was drawn so clicks
     * can be mapped back to pieces (min. 24 px, NFR-06).
     */
    private static final class Pieces {

        private static final int MIN_HIT_SIZE = 24;

        private static final double FLY_MILLIS = 800;
        /** How long a square the piece passed keeps glowing (it fades slowly over this time). */
        private static final double GLOW_MILLIS = 2600;

        private final Map<Piece, Ellipse2D> hitAreas = new LinkedHashMap<>();
        private Set<Piece> selectable = Set.of();
        /** Eaten panthers flying back to their castle: where they started and when. */
        private final Map<Piece, Object[]> flights = new HashMap<>();
        /** The move whose squares are being lit, and how many of its squares are lit already. */
        private Move litMove;
        private int litCount;
        /** Squares the moving piece passed (column, row, time in ms): they light up and fade slowly. */
        private final List<double[]> litSquares = new ArrayList<>();
        private Color trailColor = Color.WHITE;
        /** When each piece last landed, for its bounce. */
        private final Map<Piece, Long> landings = new HashMap<>();

        /**
         * @param piece the piece that just landed (it bounces)
         */
        void pieceLanded(Piece piece) {
            landings.put(piece, System.nanoTime());
        }

        /**
         * Starts the flight of an eaten piece from where it was eaten back to its castle.
         *
         * @param piece the eaten piece (already back in its castle in the model)
         * @param from  where it was eaten, in board units
         */
        void flyBackToCastle(Piece piece, Point2D from) {
            flights.put(piece, new Object[]{from, System.nanoTime()});
        }

        /** One figure to draw this frame. */
        private record Figure(Piece piece, double x, double y, double z, double size, boolean active, double grow,
                              boolean moving, double dirX, double dirY) {

            /**
             * A figure that is not walking.
             *
             * @param piece  the piece
             * @param x      board x
             * @param y      board y
             * @param z      height above the board
             * @param size   figure size
             * @param active {@code true} if the piece belongs to the current player
             * @param grow   extra size of a highlighted piece
             */
            Figure(Piece piece, double x, double y, double z, double size, boolean active, double grow) {
                this(piece, x, y, z, size, active, grow, false, 0, 0);
            }
        }

        /**
         * @param pieces pieces to highlight and accept clicks for
         */
        void setSelectable(Set<Piece> pieces) {
            this.selectable = Set.copyOf(pieces);
        }

        /**
         * Forgets selection and hit areas, e.g. when a new match starts.
         */
        void clear() {
            selectable = Set.of();
            hitAreas.clear();
            flights.clear();
            litMove = null;
        }

        /**
         * @param game  the match
         * @param piece a piece
         * @return the direction (board units) this piece walks next; from the castle, toward its start square
         */
        static Point2D walkDirection(Game.State game, Piece piece) {
            List<Square> path = piece.path();
            Point2D from;
            Point2D to;
            if (piece.isInCastle()) {
                Board.GridArea castle = game.board().castleArea(piece.owner().seat());
                from = new Point2D.Double(castle.col() + castle.cols() / 2.0, castle.row() + castle.rows() / 2.0);
                to = centerOf(path.get(0));
            } else {
                int i = Math.max(0, Math.min(piece.progress(), path.size() - 2));
                from = centerOf(path.get(i));
                to = centerOf(path.get(i + 1));
            }
            double dx = to.getX() - from.getX();
            double dy = to.getY() - from.getY();
            return dx == 0 && dy == 0 ? new Point2D.Double(0, -1) : new Point2D.Double(dx, dy);
        }

        /**
         * @param square a square
         * @return its center in board units
         */
        static Point2D centerOf(Square square) {
            return new Point2D.Double(square.position().col() + 0.5, square.position().row() + 0.5);
        }

        /**
         * @param game  the match
         * @param piece a piece
         * @return where the piece stands when nothing moves (its castle slot or its square's center)
         */
        static Point3 restingPlace(Game.State game, Piece piece) {
            Square square = piece.currentSquare();
            if (square == null) {
                Point2D slot = BoardArt.castleSlot(game.board().castleArea(piece.owner().seat()), piece.number() - 1);
                return new Point3(slot.getX(), slot.getY(), 0);
            }
            Point2D c = centerOf(square);
            return new Point3(c.getX(), c.getY(), 0);
        }

        /**
         * @param point          a mouse position
         * @param onlySelectable {@code true} to ignore pieces that may not move now
         * @return the piece under that point whose middle is closest to it (so two panthers sharing a square can each be
         *         picked by clicking on it), or {@code null}
         */
        Piece pieceAt(Point point, boolean onlySelectable) {
            Piece found = null;
            double best = Double.MAX_VALUE;
            for (Map.Entry<Piece, Ellipse2D> entry : hitAreas.entrySet()) {
                Ellipse2D area = entry.getValue();
                if ((!onlySelectable || selectable.contains(entry.getKey())) && area.contains(point)) {
                    double distance = point.distance(area.getCenterX(), area.getCenterY());
                    if (distance < best) {
                        best = distance;
                        found = entry.getKey();
                    }
                }
            }
            return found;
        }

        /**
         * Paints all pieces.
         */
        void paint(Graphics2D g2, Game.State game, Camera view, MoveAnimation animation) {
            hitAreas.clear();
            boolean highlight = !animation.isRunning();
            Piece moving = animation.movingPiece();
            List<Figure> figures = new ArrayList<>();
            Map<Square, List<Piece>> bySquare = new LinkedHashMap<>();
            for (Player player : game.players()) {
                for (Piece piece : player.pieces()) {
                    if (piece == moving) {
                        continue;
                    }
                    Object[] flight = flights.get(piece);
                    if (flight != null) {
                        double t = (System.nanoTime() - (long) flight[1]) / 1e6 / FLY_MILLIS;
                        if (t < 1 && piece.isInCastle()) {
                            Point2D from = (Point2D) flight[0];
                            Point3 to = restingPlace(game, piece);
                            double e = Theme.Motion.ease(t);
                            figures.add(new Figure(piece, from.getX() + (to.x() - from.getX()) * e,
                                    from.getY() + (to.y() - from.getY()) * e, Math.sin(Math.PI * t) * 3.2, 0.9,
                                    false, 1 + 0.3 * Math.sin(Math.PI * t)));
                            continue;
                        }
                        flights.remove(piece);
                    }
                    Square square = animation.isPendingCapture(piece)
                            ? animation.landingSquare() : piece.currentSquare();
                    if (square == null) {
                        Point3 slot = restingPlace(game, piece);
                        figures.add(new Figure(piece, slot.x(), slot.y(), 0, 0.98,
                                highlight && selectable.contains(piece), 1));
                    } else {
                        bySquare.computeIfAbsent(square, k -> new ArrayList<>()).add(piece);
                    }
                }
            }
            for (Map.Entry<Square, List<Piece>> entry : bySquare.entrySet()) {
                List<Piece> stack = entry.getValue();
                Board.GridPosition pos = entry.getKey().position();
                stack.sort((a, b) -> Boolean.compare(selectable.contains(a), selectable.contains(b)));
                int n = stack.size();
                int k = n == 1 ? 1 : n <= 4 ? 2 : 3;
                double d = k == 1 ? 0.9 : n == 2 ? 0.64 : k == 2 ? 0.56 : 0.4;
                for (int i = 0; i < n; i++) {
                    double x = pos.col() + ((i % k) + 0.5) / k;
                    double y = pos.row() + ((i / k % k) + 0.5) / k;
                    if (n == 2) {
                        // Two panthers side by side, a little apart, so each one is easy to click.
                        x = pos.col() + 0.5 + (i == 0 ? -0.28 : 0.28);
                        y = pos.row() + 0.55;
                    }
                    figures.add(new Figure(stack.get(i), x, y, 0, d, highlight && selectable.contains(stack.get(i)),
                            1));
                }
            }
            if (moving != null) {
                Point3 pose = animation.currentPosition(game.board());
                double nowMs = System.nanoTime() / 1e6;
                trailColor = Theme.neonOf(moving.owner().color());
                Point2D next = animation.nextSquareCenter(game.board());
                // Every square of the path lights up as the panther reaches it, even when a long move passes several
                // squares in one frame; castle squares never light up.
                if (animation.move() != litMove) {
                    litMove = animation.move();
                    litCount = 0;
                }
                List<Square> path = animation.squares();
                int reached = animation.isRelease() ? 0 : animation.squaresReached();
                for (; litCount < reached; litCount++) {
                    Board.GridPosition square = path.get(litCount).position();
                    litSquares.add(new double[]{square.col(), square.row(), nowMs});
                }
                if (animation.isTeleport()) {
                    // Leaving the castle or reaching the end: the panther vanishes in a puff of its color and appears
                    // where it lands.
                    double grow = animation.teleportScale();
                    Point2D c = view.project(pose.x(), pose.y(), 0);
                    Theme.Glow.paintHalo(g2, c.getX(), c.getY(), view.scale() * (1.1 - 0.5 * grow), trailColor,
                            0.9 * (1 - grow * 0.6));
                    if (grow > 0.01) {
                        figures.add(new Figure(moving, pose.x(), pose.y(), 0, 0.9, false, grow, true,
                                next.getX() - pose.x(), next.getY() - pose.y() + 0.001));
                    }
                } else {
                    Point2D from = animation.trailSquare(game.board());
                    figures.add(new Figure(moving, pose.x(), pose.y(), pose.z(), 0.9, false,
                            1 + 0.18 * pose.z() / 0.9, true, next.getX() - from.getX(), next.getY() - from.getY()));
                }
            }
            paintTrail(g2, view);
            // Floor marks first (shadows of hopping pieces, selection), then figures far to near.
            for (Figure f : figures) {
                paintFloorMark(g2, view, f);
            }
            figures.sort(Comparator.comparingDouble((Figure f) -> view.depth(f.x(), f.y()))
                    .thenComparingDouble(Figure::z));
            for (Figure f : figures) {
                paintFigure(g2, view, f, game);
            }
        }

        /**
         * The light the moving piece leaves behind: the path squares it stepped on glow and fade slowly.
         */
        private void paintTrail(Graphics2D g2, Camera view) {
            double now = System.nanoTime() / 1e6;
            // Every square the piece passed glows in its color and fades slowly, like in the prototype; the newest
            // one also sends up a soft beam of light.
            litSquares.removeIf(p -> now - p[2] > GLOW_MILLIS);
            for (int i = 0; i < litSquares.size(); i++) {
                double[] sq = litSquares.get(i);
                double life = 1 - (now - sq[2]) / GLOW_MILLIS;
                life = life * life * (3 - 2 * life);
                double pad = 0.05;
                Shape tile = view.projectShape(new RoundRectangle2D.Double(sq[0] + pad, sq[1] + pad, 1 - 2 * pad, 1 - 2 * pad,
                        0.25, 0.25), 0.02);
                g2.setColor(Theme.alpha(trailColor, 0.42 * life));
                g2.fill(tile);
                Theme.Glow.paintGlowStroke(g2, tile, Theme.mix(trailColor, Color.WHITE, 0.5), 1.6f, 0.85 * life);
                if (i == litSquares.size() - 1) {
                    Point2D foot = view.project(sq[0] + 0.5, sq[1] + 0.5, 0);
                    Point2D top = view.project(sq[0] + 0.5, sq[1] + 0.5, 0.9);
                    double bw = Math.max(6, view.scale() * 0.34);
                    g2.setPaint(new GradientPaint((float) foot.getX(), (float) foot.getY(),
                            Theme.alpha(trailColor, 0.45 * life), (float) top.getX(), (float) top.getY(),
                            Theme.alpha(trailColor, 0)));
                    g2.fill(new Rectangle2D.Double(foot.getX() - bw / 2, Math.min(top.getY(), foot.getY()), bw,
                            Math.abs(foot.getY() - top.getY())));
                }
            }
        }

        /**
         * The light under a piece: a pulsing ring when it can be clicked, a shadow under a hopping piece.
         */
        private void paintFloorMark(Graphics2D g2, Camera view, Figure f) {
            Color color = Theme.neonOf(f.piece().owner().color());
            if (f.z() > 0.01) {
                double r = f.size() * 0.4;
                g2.setColor(Theme.alpha(Color.BLACK, 0.3));
                g2.fill(view.projectShape(new Ellipse2D.Double(f.x() - r, f.y() - r, r * 2, r * 2), 0));
                return;
            }
            if (f.active()) {
                double r = f.size() * 0.55 + 0.05 * Theme.Motion.pulse(1.4);
                Point2D c = view.project(f.x(), f.y(), 0);
                Theme.Glow.paintHalo(g2, c.getX(), c.getY(), r * view.scale() * 1.2, color, 0.9);
                Theme.Glow.paintGlowStroke(g2, view.projectShape(new Ellipse2D.Double(f.x() - r, f.y() - r, r * 2, r * 2), 0),
                        Theme.text(), 2f, 0.9);
            }
        }

        /**
         * Draws one standing figure and remembers its click area.
         */
        private void paintFigure(Graphics2D g2, Camera view, Figure f, Game.State game) {
            Piece piece = f.piece();
            Player owner = piece.owner();
            double lift = f.active() ? 0.14 * Theme.Motion.pulse(1.4) : 0;
            Point2D base = view.project(f.x(), f.y(), f.z() + lift);
            double scale = view.scale();
            double height = f.size() * 1.45 * scale * f.grow();
            // Seen from straight above, the figure stands centered on its square.
            double baseY = base.getY() + height * 0.42 - (f.active() ? lift * scale * 0.6 : 0);
            Color color = Theme.colorOf(owner.color());
            height = Math.min(height, 900);
            // Idle pieces breathe gently; a piece that just landed bounces (squash, then settle).
            double seed = owner.seat().ordinal() * 4 + piece.number();
            double squash = -0.025 * (0.5 + 0.5 * Math.sin(Theme.Motion.secondsSinceStart() * 2.2 + seed));
            Long landedAt = landings.get(piece);
            if (landedAt != null) {
                double p = (System.nanoTime() - landedAt) / 1e6 / 420.0;
                if (p < 1) {
                    squash = 0.22 * Math.exp(-5 * p) * Math.cos(p * 11);
                } else {
                    landings.remove(piece);
                }
            }
            if (f.moving()) {
                squash = 0;
            }
            // The panther looks the way it walks: the direction on the screen decides the mirror.
            Point2D dir = f.moving() ? new Point2D.Double(f.dirX(), f.dirY()) : walkDirection(game, piece);
            Point2D ahead = view.project(f.x() + dir.getX() * 0.3, f.y() + dir.getY() * 0.3, f.z());
            boolean left = ahead.getX() < base.getX() - 0.5;
            // The panther statue hops along its path, leaning the way it goes.
            double hop = f.moving() ? Math.abs(Math.sin(Theme.Motion.secondsSinceStart() * 10)) : 0;
            double tilt = f.moving() ? (left ? -0.1 : 0.1) * hop : 0;
            Theme.Panther.paintStatue(g2, base.getX(), baseY - hop * height * 0.12, height,
                    color, f.moving() ? -0.06 * hop : squash, tilt);
            if (f.active() && owner == game.currentPlayer()) {
                // The number badge: the panther can also be chosen with that key.
                paintBadge(g2, base.getX(), baseY - height - 4, piece.number(), Theme.neonOf(owner.color()));
            }
            double hit = Math.max(height * 0.85, MIN_HIT_SIZE);
            double cy = baseY - height * 0.45;
            hitAreas.remove(piece);
            hitAreas.put(piece, new Ellipse2D.Double(base.getX() - hit / 2, cy - hit / 2, hit, hit));
        }

        /**
         * A round comic badge with the piece's number above a piece that may move.
         */
        private static void paintBadge(Graphics2D g2, double cx, double bottom, int number, Color color) {
            double r = 11;
            double cy = bottom - r;
            Ellipse2D circle = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
            g2.setColor(new Color(0, 0, 0, 90));
            g2.fill(new Ellipse2D.Double(cx - r + 2, cy - r + 3, 2 * r, 2 * r));
            g2.setColor(color);
            g2.fill(circle);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(2.2f));
            g2.draw(circle);
            String text = String.valueOf(number);
            g2.setFont(Theme.Fonts.comicFont(18f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) (cy + fm.getAscent() / 2.0 - 2));
        }

        /**
         * Hop animation of one move, square by square, finishing within one second regardless of distance (NFR-13).
         * The model has already applied the move; this class only decides where to draw the moving piece meanwhile.
         */
        static final class MoveAnimation {

            /** A panther leaving its castle or reaching its end vanishes and appears within this time. */
            static final double RELEASE_MILLIS = 560;

            private Move move;
            private List<Piece> captured = List.of();
            private long startNanos;
            private double millis;
            private Timer end;

            /**
             * @param move     the applied move
             * @param captured pieces captured on the landing square; they stay visible until the mover lands
             * @param onLanded called once the piece has landed
             */
            void start(Move move, List<Piece> captured, Runnable onLanded) {
                stop();
                int squares = move.traversedSquares().size();
                this.move = move;
                this.captured = List.copyOf(captured);
                this.millis = move.from() == Piece.IN_CASTLE || move.to() == move.piece().lastIndex() ? RELEASE_MILLIS
                        : durationMillis(squares);
                this.startNanos = System.nanoTime();
                end = new Timer((int) millis + 15, e -> {
                    stop();
                    onLanded.run();
                });
                end.setRepeats(false);
                end.start();
            }

            /**
             * @param squares number of squares to hop over
             * @return total animation time: ~130 ms per square, at least 260 ms, at most
             *         {@link BoardPanel#MAX_MOVE_ANIMATION_MILLIS}
             */
            static double durationMillis(int squares) {
                return Math.min(BoardPanel.MAX_MOVE_ANIMATION_MILLIS, Math.max(260, squares * 130));
            }

            /**
             * Cancels the animation without calling its completion callback.
             */
            void stop() {
                if (end != null) {
                    end.stop();
                }
                end = null;
                move = null;
                captured = List.of();
            }

            /**
             * @return {@code true} while a piece is hopping
             */
            boolean isRunning() {
                return move != null;
            }

            /**
             * @return the hopping piece, or {@code null}
             */
            Piece movingPiece() {
                return move == null ? null : move.piece();
            }

            /**
             * @param piece any piece
             * @return {@code true} if it was captured by this move and must still be drawn on the landing square
             */
            boolean isPendingCapture(Piece piece) {
                return captured.contains(piece);
            }

            /**
             * @return the square the moving piece lands on
             */
            Square landingSquare() {
                List<Square> squares = move.traversedSquares();
                return squares.get(squares.size() - 1);
            }

            /**
             * @return how far the animation is, from 0 to 1
             */
            private double progress() {
                return Math.min(1, (System.nanoTime() - startNanos) / 1e6 / millis);
            }

            /**
             * @param board the board (for the castle slot a released piece starts from)
             * @return where the moving piece is now, in board units, with the height of its hop
             */
            Point3 currentPosition(Board board) {
                List<Square> squares = move.traversedSquares();
                double progress = progress();
                if (isTeleport()) {
                    Point2D at = progress < 0.5 ? origin(board) : centerOf(squares.get(squares.size() - 1));
                    return new Point3(at.getX(), at.getY(), 0);
                }
                int n = squares.size();
                double f = progress * n;
                int segment = Math.min(n - 1, (int) f);
                double u = progress >= 1 ? 1 : f - segment;
                Point2D from = segment == 0 ? origin(board) : centerOf(squares.get(segment - 1));
                Point2D to = centerOf(squares.get(segment));
                double eased = Theme.Motion.ease(u);
                double hop = Math.sin(Math.PI * u);
                return new Point3(from.getX() + (to.getX() - from.getX()) * eased,
                        from.getY() + (to.getY() - from.getY()) * eased, hop * 0.9);
            }

            /**
             * @param board the board
             * @return the center of the square the piece is hopping to now
             */
            Point2D nextSquareCenter(Board board) {
                List<Square> squares = move.traversedSquares();
                int segment = Math.min(squares.size() - 1, (int) (progress() * squares.size()));
                return centerOf(squares.get(segment));
            }

            /**
             * @param board the board
             * @return the square the piece just left, for a fading light trail
             */
            Point2D trailSquare(Board board) {
                List<Square> squares = move.traversedSquares();
                int segment = Math.min(squares.size() - 1, (int) (progress() * squares.size()));
                return segment == 0 ? origin(board) : centerOf(squares.get(segment - 1));
            }

            /**
             * @return {@code true} if the moving piece is leaving its castle: it does not walk over the castle but
             *         vanishes there and appears on its start square
             */
            boolean isRelease() {
                return move != null && move.from() == Piece.IN_CASTLE;
            }

            /**
             * @return {@code true} if the moving piece does not walk but vanishes and appears: when it leaves its castle
             *         and when it reaches the end of its path
             */
            boolean isTeleport() {
                return isRelease() || move != null && move.to() == move.piece().lastIndex();
            }

            /**
             * @return the move being animated, or {@code null}
             */
            Move move() {
                return move;
            }

            /**
             * @return the squares the moving piece passes, in order, ending with the one it lands on
             */
            List<Square> squares() {
                return move.traversedSquares();
            }

            /**
             * @return how many squares of the path the piece has reached so far
             */
            int squaresReached() {
                int n = move.traversedSquares().size();
                return Math.min(n, (int) Math.floor(progress() * n) + 1);
            }

            /**
             * @return the size of a piece that vanishes and appears: it shrinks away where it was (1 to 0), stays hidden
             *         for a moment, then grows where it lands (0 to 1)
             */
            double teleportScale() {
                double p = progress();
                if (p < 0.4) {
                    return 1 - Theme.Motion.ease(p / 0.4);
                }
                return p < 0.55 ? 0 : Theme.Motion.ease((p - 0.55) / 0.45);
            }

            /**
             * Where the animation starts: the piece's castle slot when it is released, otherwise the square it leaves.
             */
            private Point2D origin(Board board) {
                Piece piece = move.piece();
                if (move.from() == Piece.IN_CASTLE) {
                    return BoardArt.castleSlot(board.castleArea(piece.owner().seat()), piece.number() - 1);
                }
                return centerOf(piece.path().get(move.from()));
            }
        }
    }

    /**
     * The camera that shows the flat board from above. Positions on the board are given in board units: one unit is one
     * grid cell, (0, 0) is the top-left corner of the grid and the height {@code z} is measured upward from the board
     * surface (seen from above it does not move a point). The whole board always fits the panel, centered; the board
     * never moves. Purely visual: the camera never changes the match.
     */
    public static final class Camera {

        /** Board size in units, including the frame around the grid. */
        private double boardWidth = 15;
        private double boardHeight = 15;
        private double margin;

        // The current projection, recomputed by fit().
        private double scale = 1;
        private double centerX;
        private double centerY;

        /**
         * @param cols   grid columns
         * @param rows   grid rows
         * @param margin frame width around the grid, in units
         */
        void setBoard(int cols, int rows, double margin) {
            this.boardWidth = cols;
            this.boardHeight = rows;
            this.margin = margin;
        }

        /**
         * Computes the scale so the whole board fits the panel, centered.
         *
         * @param width  panel width
         * @param height panel height
         */
        void fitToPanel(int width, int height) {
            double halfW = boardWidth / 2 + margin;
            double halfH = boardHeight / 2 + margin;
            double fitX = width / 2.0 / (halfW + 0.4);
            double fitY = height / 2.0 / (halfH + 0.4);
            scale = Math.max(1, Math.min(fitX, fitY));
            centerX = width / 2.0;
            centerY = height / 2.0;
        }

        /**
         * @return screen pixels per board unit
         */
        double scale() {
            return scale;
        }

        /**
         * @param x board x
         * @param y board y
         * @param z height above the board (seen from above it does not move the point)
         * @return the screen point
         */
        Point2D project(double x, double y, double z) {
            return new Point2D.Double(centerX + (x - boardWidth / 2) * scale, centerY + (y - boardHeight / 2) * scale);
        }

        /**
         * @param x board x
         * @param y board y
         * @return how near the point is to the bottom of the screen (larger is nearer); used to draw far things first
         */
        double depth(double x, double y) {
            return y - boardHeight / 2;
        }

        /**
         * Projects a flat shape lying on the plane at height {@code z}.
         *
         * @param shape shape in board units
         * @param z     height of the plane
         * @return the shape on the screen
         */
        Shape projectShape(Shape shape, double z) {
            return planeTransform(z).createTransformedShape(shape);
        }

        /**
         * @param z height of the plane
         * @return the transform from board units on the plane at height {@code z} to the screen
         */
        AffineTransform planeTransform(double z) {
            AffineTransform t = AffineTransform.getTranslateInstance(centerX, centerY);
            t.scale(scale, scale);
            t.translate(-boardWidth / 2, -boardHeight / 2);
            return t;
        }
    }

    /**
     * Paints the flat artwork of the board surface — frame, castles, squares with their icons (FR-18) and the center —
     * into an image: the panther board, with tiles in the players' colors and neon icons on the special squares.
     * {@link BoardPanel} lays the image on the board.
     */
    public static final class BoardArt {

        /** Width of the frame around the grid, in cells. */
        static final double MARGIN = 0.6;

        private final Game.State game;
        /** Pixels per cell in the image. */
        private final double cell;

        /**
         * Use {@link #render}.
         *
         * @param game the match whose board is drawn
         * @param cell size of one grid cell in pixels
         */
        private BoardArt(Game.State game, double cell) {
            this.game = game;
            this.cell = cell;
        }

        /**
         * @param game the match
         * @param cell pixels per grid cell
         * @return the artwork; the grid's top-left corner is at ({@link #MARGIN}·cell, {@link #MARGIN}·cell)
         */
        static BufferedImage render(Game.State game, double cell) {
            Board board = game.board();
            int w = (int) Math.ceil((board.cols() + 2 * MARGIN) * cell);
            int h = (int) Math.ceil((board.rows() + 2 * MARGIN) * cell);
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            new BoardArt(game, cell).paint(g2, w, h);
            g2.dispose();
            return image;
        }

        /**
         * @param castle a castle area
         * @param index  piece slot 0–3 (2×2 arrangement)
         * @return the center of that slot, in board units
         */
        static Point2D castleSlot(Board.GridArea castle, int index) {
            double cx = castle.col() + castle.cols() / 2.0;
            double cy = castle.row() + castle.rows() / 2.0 + 0.32;
            double offset = 1.1;
            return new Point2D.Double(cx + (index % 2 == 0 ? -offset : offset), cy + (index < 2 ? -offset : offset));
        }

        /**
         * @param game the match
         * @param seat a corner
         * @return the player sitting there, or {@code null} for a free corner
         */
        static Player playerAt(Game.State game, Board.Seat seat) {
            return game.players().stream().filter(p -> p.seat() == seat).findFirst().orElse(null);
        }

        /**
         * @param game the match
         * @param seat a corner
         * @return LED color of the corner: its player's color, or a neutral tone when nobody sits there (TD-08)
         */
        static Color cornerColor(Game.State game, Board.Seat seat) {
            Player player = playerAt(game, seat);
            return player == null ? Theme.emptySeat() : Theme.neonOf(player.color());
        }

        // ------------------------------------------------------------------ geometry inside the image

        /**
         * @return the pixel area of a grid cell
         */
        private Rectangle2D rect(Board.GridPosition pos) {
            return new Rectangle2D.Double((MARGIN + pos.col()) * cell, (MARGIN + pos.row()) * cell, cell, cell);
        }

        /**
         * @return the pixel area of a block of grid cells
         */
        private Rectangle2D rect(Board.GridArea area) {
            return new Rectangle2D.Double((MARGIN + area.col()) * cell, (MARGIN + area.row()) * cell,
                    area.cols() * cell, area.rows() * cell);
        }

        /**
         * @return the pixel position of a piece's place in a castle
         */
        private Point2D slot(Board.GridArea area, int index) {
            Point2D p = castleSlot(area, index);
            return new Point2D.Double((MARGIN + p.getX()) * cell, (MARGIN + p.getY()) * cell);
        }

        // ------------------------------------------------------------------ painting

        /**
         * Draws the whole board picture: frame, squares, castles, home lanes and the center.
         */
        private void paint(Graphics2D g2, int w, int h) {
            Board board = game.board();
            RoundRectangle2D frame = new RoundRectangle2D.Double(0, 0, w, h, cell * 0.9, cell * 0.9);
            g2.setColor(Theme.deepBackground());
            g2.fill(frame);
            g2.setColor(Theme.gridColor());
            g2.setStroke(new BasicStroke((float) Math.max(1.2, cell * 0.04)));
            g2.draw(frame);

            for (Board.Seat seat : Board.Seat.values()) {
                paintCastle(g2, seat, board.castleArea(seat));
            }
            for (Square square : board.squares()) {
                if (square.type() != Square.Type.FINISH) {
                    paintSquare(g2, square);
                }
            }
            paintCenter(g2, board);
        }

        /**
         * Draws one castle in its owner's color with four empty slots and the color name; a corner without a player is
         * grey and says "Free corner".
         */
        private void paintCastle(Graphics2D g2, Board.Seat seat, Board.GridArea area) {
            Rectangle2D r = rect(area);
            double pad = cell * 0.12;
            RoundRectangle2D outer = new RoundRectangle2D.Double(r.getX() + pad, r.getY() + pad,
                    r.getWidth() - 2 * pad, r.getHeight() - 2 * pad, cell * 0.7, cell * 0.7);
            Color color = cornerColor(game, seat);
            g2.setColor(Theme.dim(color));
            g2.fill(outer);
            Theme.Glow.paintGlowStroke(g2, outer, color, 1.6f, 0.45);
            double inset = cell * 0.8;
            RoundRectangle2D den = new RoundRectangle2D.Double(r.getX() + inset, r.getY() + inset,
                    r.getWidth() - 2 * inset, r.getHeight() - 2 * inset, cell * 0.6, cell * 0.6);
            g2.setColor(Theme.alpha(Theme.deepBackground(), 0.75));
            g2.fill(den);
            for (int i = 0; i < Player.PIECES_PER_PLAYER; i++) {
                Point2D slot = slot(area, i);
                double d = cell * 1.05;
                g2.setColor(Theme.alpha(color, 0.35));
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new Ellipse2D.Double(slot.getX() - d / 2, slot.getY() - d / 2, d, d));
            }
            // The castle shows whose it is: the player's name (its color is already shown by the castle itself).
            Player owner = playerAt(game, seat);
            String label = owner == null ? Assets.Texts.get("board.freeCorner") : owner.name();
            g2.setFont(fitFont(g2, label, den.getWidth() - 10, Math.max(12, cell * 0.34)));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(owner == null ? Theme.textMuted() : Theme.text());
            g2.drawString(label, (float) (den.getCenterX() - fm.stringWidth(label) / 2.0),
                    (float) (den.getY() + fm.getAscent() + cell * 0.12));
        }

        /**
         * Draws one track or home-lane square with the look of its type (FR-18).
         */
        private void paintSquare(Graphics2D g2, Square square) {
            Rectangle2D r = rect(square.position());
            double inset = Math.max(1, cell * 0.05);
            RoundRectangle2D tile = new RoundRectangle2D.Double(r.getX() + inset, r.getY() + inset,
                    r.getWidth() - 2 * inset, r.getHeight() - 2 * inset, cell * 0.22, cell * 0.22);
            Color seatColor = square.owner() == null ? Theme.surfaceHighlight() : cornerColor(game, square.owner());
            Color fill = switch (square.type()) {
                case START -> Theme.mix(seatColor, Theme.surfaceHighlight(), 0.38);
                case ARROW -> Theme.mix(seatColor, Theme.surfaceHighlight(), 0.28);
                case HOME_LANE -> Theme.laneColor(seatColor);
                case QUESTION -> Theme.mix(Theme.questionColor(square.questionLevel()), Theme.surfaceHighlight(), 0.3);
                case SURPRISE -> Theme.mix(Theme.neonPurple(), Theme.surfaceHighlight(), 0.35);
                default -> Theme.surfaceHighlight();
            };
            g2.setColor(fill);
            g2.fill(tile);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(Theme.alpha(Color.WHITE, 0.06));
            g2.draw(tile);
            if (paintNeonIcon(g2, square, r, seatColor)) {
                return;
            }
            switch (square.type()) {
                case STAR -> paintStar(g2, r);
                case ARROW -> paintArrow(g2, square, r);
                case HOME_LANE -> {
                    Theme.Glow.paintGlowStroke(g2, tile, seatColor, 1f, 0.25);
                    paintChevron(g2, square, r, seatColor);
                }
                default -> {
                }
            }
        }

        /**
         * The neon square icons: question (in the color of its difficulty, FR-42), surprise gift and "#" start in the
         * player's color.
         *
         * @return {@code false} if this square has no icon (the caller draws its plain look)
         */
        private boolean paintNeonIcon(Graphics2D g2, Square square, Rectangle2D r, Color seatColor) {
            double size = cell * 0.84;
            NeonTiles.Kind kind = switch (square.type()) {
                case QUESTION -> NeonTiles.Kind.QUESTION;
                case SURPRISE -> NeonTiles.Kind.SURPRISE;
                case START -> NeonTiles.Kind.START;
                default -> null;
            };
            if (kind == null) {
                return false;
            }
            Color accent = kind == NeonTiles.Kind.QUESTION ? Theme.questionColor(square.questionLevel())
                    : kind == NeonTiles.Kind.START ? seatColor : null;
            NeonTiles.paint(g2, kind, r.getCenterX(), r.getCenterY(), size, accent);
            return true;
        }

        /**
         * Two chevrons on a home-lane square, pointing to where the piece goes next.
         */
        private void paintChevron(Graphics2D g2, Square square, Rectangle2D r, Color color) {
            List<Square> path = game.board().pathFor(square.owner());
            int index = path.indexOf(square);
            if (index < 0 || index + 1 >= path.size()) {
                return;
            }
            Board.GridPosition next = path.get(index + 1).position();
            double dx = next.col() - square.position().col();
            double dy = next.row() - square.position().row();
            double cx = r.getCenterX();
            double cy = r.getCenterY();
            double s = cell * 0.2;
            Path2D chevrons = new Path2D.Double();
            for (int k = 0; k < 2; k++) {
                double off = (k - 0.5) * s * 1.1;
                double tipX = cx + dx * (s + off);
                double tipY = cy + dy * (s + off);
                chevrons.moveTo(tipX - dx * s - dy * s, tipY - dy * s + dx * s);
                chevrons.lineTo(tipX, tipY);
                chevrons.lineTo(tipX - dx * s + dy * s, tipY - dy * s - dx * s);
            }
            Theme.Glow.paintGlowStroke(g2, chevrons, color, (float) Math.max(1.4, cell * 0.05), 0.5);
        }

        // ------------------------------------------------------------------ shared pieces of artwork

        /**
         * Draws the center: one colored triangle pointing at each home lane.
         */
        private void paintCenter(Graphics2D g2, Board board) {
            Board.GridArea area = board.centerArea();
            Rectangle2D r = rect(area);
            double cx = r.getCenterX();
            double cy = r.getCenterY();
            int midRow = area.row() + area.rows() / 2;
            int midCol = area.col() + area.cols() / 2;
            for (Board.Seat seat : Board.Seat.values()) {
                List<Square> path = board.pathFor(seat);
                Board.GridPosition finish = path.get(path.size() - 1).position();
                int dr = finish.row() - midRow;
                int dc = finish.col() - midCol;
                Path2D triangle = new Path2D.Double();
                if (dc < 0) {
                    triangle.moveTo(r.getX(), r.getY());
                    triangle.lineTo(r.getX(), r.getMaxY());
                } else if (dc > 0) {
                    triangle.moveTo(r.getMaxX(), r.getY());
                    triangle.lineTo(r.getMaxX(), r.getMaxY());
                } else if (dr < 0) {
                    triangle.moveTo(r.getX(), r.getY());
                    triangle.lineTo(r.getMaxX(), r.getY());
                } else {
                    triangle.moveTo(r.getX(), r.getMaxY());
                    triangle.lineTo(r.getMaxX(), r.getMaxY());
                }
                triangle.lineTo(cx, cy);
                triangle.closePath();
                Color color = cornerColor(game, seat);
                g2.setColor(Theme.mix(color, Theme.deepBackground(), 0.6));
                g2.fill(triangle);
                Theme.Glow.paintGlowStroke(g2, triangle, color, 1.2f, 0.35);
            }
            Theme.Glow.paintHalo(g2, cx, cy, cell * 0.7, Theme.pantherGold(), 0.8);
        }

        /**
         * Draws the gold star of a protection square.
         */
        private void paintStar(Graphics2D g2, Rectangle2D r) {
            double cx = r.getCenterX();
            double cy = r.getCenterY();
            double outer = cell * 0.36;
            double inner = outer * 0.45;
            Path2D star = new Path2D.Double();
            for (int i = 0; i < 10; i++) {
                double radius = i % 2 == 0 ? outer : inner;
                double angle = -Math.PI / 2 + i * Math.PI / 5;
                double x = cx + radius * Math.cos(angle);
                double y = cy + radius * Math.sin(angle);
                if (i == 0) {
                    star.moveTo(x, y);
                } else {
                    star.lineTo(x, y);
                }
            }
            star.closePath();
            Theme.Glow.paintHalo(g2, cx, cy, cell * 0.55, Theme.starColor(), 0.7);
            g2.setPaint(new GradientPaint((float) cx, (float) (cy - outer), Theme.mix(Theme.starColor(), Color.WHITE, 0.55),
                    (float) cx, (float) (cy + outer), Theme.starColor()));
            g2.fill(star);
        }

        /**
         * Draws an arrow square's arrow in its owner's color, pointing to where that owner turns.
         */
        private void paintArrow(Graphics2D g2, Square square, Rectangle2D r) {
            List<Square> path = game.board().pathFor(square.owner());
            Board.GridPosition next = path.get(path.indexOf(square) + 1).position();
            double dx = next.col() - square.position().col();
            double dy = next.row() - square.position().row();
            double cx = r.getCenterX();
            double cy = r.getCenterY();
            double s = cell * 0.3;
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(cx + dx * s, cy + dy * s);
            arrow.lineTo(cx - dx * s - dy * s, cy - dy * s + dx * s);
            arrow.lineTo(cx - dx * s + dy * s, cy - dy * s - dx * s);
            arrow.closePath();
            Color color = cornerColor(game, square.owner());
            Theme.Glow.paintHalo(g2, cx, cy, cell * 0.5, color, 0.6);
            g2.setColor(color);
            g2.fill(arrow);
        }

        /**
         * Shrinks the font until the text fits, but never below 12 pt (NFR-03).
         */
        private static Font fitFont(Graphics2D g2, String text, double maxWidth, double preferredSize) {
            float size = (float) preferredSize;
            Font font = Theme.Fonts.baseFont(size, true);
            while (size > 12 && g2.getFontMetrics(font).stringWidth(text) > maxWidth) {
                size -= 1;
                font = Theme.Fonts.baseFont(size, true);
            }
            return font;
        }

        /**
         * The neon board square icons of the Panther Ludo art: a dark rounded tile with a glowing neon frame and a glowing
         * symbol — "?" (question), a gift (surprise), a checkered flag (finish) and "#"
         * (start, as in the game rules). They are drawn as shapes, so they stay sharp at any size and take any color.
         */
        public static final class NeonTiles {

            /** The icon kinds. */
            public enum Kind { QUESTION, SURPRISE, FINISH, START }

            private static final Color PINK = new Color(0xFF4FD8);
            private static final Color FLAG = new Color(0xA45CFF);

            /**
             * Only static helpers; no objects of this class are created.
             */
            private NeonTiles() {
            }

            /**
             * @param kind the icon
             * @return its own neon color (question and start squares take the color given to {@link #paint})
             */
            public static Color colorOf(Kind kind) {
                return switch (kind) {
                    case SURPRISE -> PINK;
                    case FINISH -> FLAG;
                    default -> new Color(0x2CE5FF);
                };
            }

            /**
             * Paints one icon.
             *
             * @param g      graphics
             * @param kind   which icon
             * @param cx     center x
             * @param cy     center y
             * @param size   width and height of the tile
             * @param accent the neon color of a question (its level) or start (its player) tile; {@code null} for the own
             *               color
             */
            public static void paint(Graphics2D g, Kind kind, double cx, double cy, double size, Color accent) {
                Color neon = accent != null && (kind == Kind.QUESTION || kind == Kind.START) ? accent : colorOf(kind);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                double s = size;
                double x = cx - s / 2;
                double y = cy - s / 2;
                RoundRectangle2D tile = new RoundRectangle2D.Double(x, y, s, s, s * 0.3, s * 0.3);
                // Outer glow, dark tile, neon frame.
                for (int i = 3; i >= 1; i--) {
                    g2.setColor(Theme.alpha(neon, 0.07 * (4 - i)));
                    g2.setStroke(new BasicStroke((float) (s * 0.05 * i), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(tile);
                }
                // Dark mode: a dark tile that the neon lights up. Light mode: a white tile with the symbol in its color.
                boolean light = Theme.mode() == Theme.Mode.LIGHT;
                g2.setPaint(light ? new GradientPaint((float) cx, (float) y, Color.WHITE, (float) cx, (float) (y + s),
                        new Color(0xF2F2F8)) : new GradientPaint((float) cx, (float) y, new Color(0x1B2350), (float) cx,
                        (float) (y + s), new Color(0x080B22)));
                g2.fill(tile);
                g2.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) (s * 0.6), new float[]{0f, 1f},
                        new Color[]{Theme.alpha(neon, 0.22), Theme.alpha(neon, 0)}));
                g2.fill(tile);
                g2.setColor(neon);
                g2.setStroke(new BasicStroke((float) Math.max(1, s * 0.045), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(tile);
                g2.setColor(Theme.mix(neon, Color.WHITE, 0.4));
                g2.setStroke(new BasicStroke((float) Math.max(0.6, s * 0.015)));
                g2.draw(tile);
                Shape clip = g2.getClip();
                g2.clip(tile);
                switch (kind) {
                    case QUESTION -> glyph(g2, "?", cx, cy, s, neon);
                    case START -> glyph(g2, "#", cx, cy, s, neon);
                    case SURPRISE -> gift(g2, cx, cy, s);
                    case FINISH -> flag(g2, cx, cy, s);
                    default -> {
                    }
                }
                g2.setClip(clip);
                g2.dispose();
            }

            /**
             * A big glowing character.
             */
            private static void glyph(Graphics2D g2, String text, double cx, double cy, double s, Color neon) {
                Font font = Theme.Fonts.baseFont((float) Math.max(6, s * 0.66), true);
                java.awt.font.TextLayout layout = new java.awt.font.TextLayout(text, font, g2.getFontRenderContext());
                Rectangle2D b = layout.getBounds();
                Shape shape = layout.getOutline(AffineTransform.getTranslateInstance(cx - b.getCenterX(), cy - b.getCenterY()));
                glow(g2, shape, neon, s);
                // On the white tile of light mode the symbol keeps its full color, a little deeper so it reads well.
                g2.setColor(Theme.mode() == Theme.Mode.LIGHT ? Theme.mix(neon, Color.BLACK, 0.85) : Theme.mix(neon,
                        Color.WHITE, 0.55));
                g2.fill(shape);
            }

            /**
             * Draws a shape's outline with a soft neon glow around it.
             */
            private static void glow(Graphics2D g2, Shape shape, Color neon, double s) {
                for (int i = 3; i >= 1; i--) {
                    g2.setColor(Theme.alpha(neon, 0.16));
                    g2.setStroke(new BasicStroke((float) (s * 0.035 * i), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(shape);
                }
            }

            /**
             * A pink gift box with a ribbon and a bow.
             */
            private static void gift(Graphics2D g2, double cx, double cy, double s) {
                Color body = new Color(0xFF8BE0);
                Color ribbon = new Color(0xD61E9C);
                double bw = s * 0.56;
                double bh = s * 0.32;
                double top = cy - s * 0.02;
                RoundRectangle2D box = new RoundRectangle2D.Double(cx - bw / 2, top, bw, bh, s * 0.05, s * 0.05);
                RoundRectangle2D lid = new RoundRectangle2D.Double(cx - bw * 0.56, top - s * 0.12, bw * 1.12, s * 0.13,
                        s * 0.05, s * 0.05);
                Area all = new Area(box);
                all.add(new Area(lid));
                glow(g2, all, PINK, s);
                g2.setPaint(new GradientPaint((float) cx, (float) top, body, (float) cx, (float) (top + bh),
                        Theme.mix(body, ribbon, 0.6)));
                g2.fill(box);
                g2.setColor(Theme.mix(body, Color.WHITE, 0.8));
                g2.fill(lid);
                g2.setColor(ribbon);
                g2.fill(new Rectangle2D.Double(cx - s * 0.045, lid.getY(), s * 0.09, bh + lid.getHeight()));
                // The bow: two loops.
                g2.setStroke(new BasicStroke((float) (s * 0.045), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double by = lid.getY();
                g2.setColor(Theme.mix(body, ribbon, 0.5));
                g2.draw(new Ellipse2D.Double(cx - s * 0.2, by - s * 0.13, s * 0.18, s * 0.13));
                g2.draw(new Ellipse2D.Double(cx + s * 0.02, by - s * 0.13, s * 0.18, s * 0.13));
            }

            /**
             * A waving checkered flag on a pole.
             */
            private static void flag(Graphics2D g2, double cx, double cy, double s) {
                double poleX = cx - s * 0.26;
                double top = cy - s * 0.3;
                g2.setColor(new Color(0xC9CFE8));
                g2.setStroke(new BasicStroke((float) (s * 0.04), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Double(poleX, top, poleX + s * 0.08, cy + s * 0.32));
                int cols = 5;
                int rows = 4;
                double fw = s * 0.52;
                double fh = s * 0.36;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        double u0 = (double) c / cols;
                        double u1 = (double) (c + 1) / cols;
                        double v0 = (double) r / rows;
                        double v1 = (double) (r + 1) / rows;
                        Path2D cell = new Path2D.Double();
                        cell.moveTo(fx(poleX, fw, u0, s), fy(top, fh, u0, v0, s));
                        cell.lineTo(fx(poleX, fw, u1, s), fy(top, fh, u1, v0, s));
                        cell.lineTo(fx(poleX, fw, u1, s), fy(top, fh, u1, v1, s));
                        cell.lineTo(fx(poleX, fw, u0, s), fy(top, fh, u0, v1, s));
                        cell.closePath();
                        g2.setColor((r + c) % 2 == 0 ? new Color(0xF4F6FF) : new Color(0x1D2A80));
                        g2.fill(cell);
                    }
                }
                g2.setColor(Theme.alpha(FLAG, 0.9));
                g2.fill(new Ellipse2D.Double(poleX - s * 0.04, top - s * 0.05, s * 0.08, s * 0.08));
            }

            /**
             * @return the x position of a point on the waving flag
             */
            private static double fx(double poleX, double fw, double u, double s) {
                return poleX + s * 0.02 + u * fw;
            }

            /**
             * @return the y position of a point on the waving flag
             */
            private static double fy(double top, double fh, double u, double v, double s) {
                return top + v * fh + Math.sin(u * Math.PI * 1.6) * s * 0.06 + u * s * 0.04;
            }
        }
    }
}
