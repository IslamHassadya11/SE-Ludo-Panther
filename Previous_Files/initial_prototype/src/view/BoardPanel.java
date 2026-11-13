package view;

import model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Consumer;
import java.util.List; // java.util.List, not java.awt.List
import javax.swing.Timer; // the Swing timer, not java.util.Timer

/**
 * The board component (FR-04, FR-18, NFR-02). It wires together the board layers:
 * {@link BoardBackgroundPainter} (static artwork, cached), {@link PieceLayer} (pieces, selection, click areas),
 * {@link PieceLayer.MoveAnimation} (hops) and {@link EffectLayer} (bursts), plus the LED frame and current-player glow.
 * It never decides which moves are legal — selectable pieces come from the model (FR-31).
 */
public final class BoardPanel extends JPanel {

    private final BoardBackgroundPainter background = new BoardBackgroundPainter();
    private final PieceLayer pieces = new PieceLayer();
    private final PieceLayer.MoveAnimation animation = new PieceLayer.MoveAnimation();
    private final EffectLayer effects = new EffectLayer();
    private Game game;
    private Consumer<Piece> onPieceClicked = p -> { };

    /**
     * Creates an empty board view.
     */
    public BoardPanel() {
        setBackground(UiTheme.background());
        setPreferredSize(new Dimension(680, 680));
        setToolTipText("");
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Piece piece = clickablePieceAt(e);
                if (piece != null) {
                    onPieceClicked.accept(piece);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(Cursor.getPredefinedCursor(clickablePieceAt(e) != null
                        ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /**
     * @param game match to display, or {@code null}
     */
    public void setGame(Game game) {
        animation.stop();
        effects.clear();
        pieces.clear();
        background.invalidate();
        this.game = game;
        if (game != null) {
            Animator.subscribe(this);
        } else {
            Animator.unsubscribe(this);
        }
        repaint();
    }

    /**
     * @param movable pieces to highlight and accept clicks for
     */
    public void setSelectablePieces(Set<Piece> movable) {
        pieces.setSelectable(movable);
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
     * Animates an applied move as hops; afterwards shows bursts for captures, releases and finishes.
     *
     * @param move     the applied move
     * @param captured pieces captured on the landing square
     * @param onDone   called when the animation ends
     */
    public void animateMove(Move move, List<Piece> captured, Runnable onDone) {
        List<Square> squares = move.traversedSquares();
        if (squares.isEmpty()) {
            onDone.run();
            return;
        }
        animation.start(move, captured, () -> {
            Square landing = squares.get(squares.size() - 1);
            for (Piece victim : captured) {
                effects.burst(landing, UiTheme.neonOf(victim.owner().color()), true);
            }
            if (move.piece().isFinished() || move.from() == Piece.IN_CASTLE) {
                effects.burst(landing, UiTheme.neonOf(move.piece().owner().color()), move.piece().isFinished());
            }
            onDone.run();
        });
    }

    /**
     * Stops any running animation without calling its completion callback.
     */
    public void stopAnimation() {
        animation.stop();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        Piece piece = pieces.pieceAt(event.getPoint(), false);
        if (piece == null) {
            return null;
        }
        Player owner = piece.owner();
        String where = piece.isInCastle() ? "in the castle" : piece.isFinished() ? "home" : "on the board";
        return owner.name() + "'s panther " + piece.number() + " (" + owner.color().name() + "), " + where;
    }

    /**
     * Finds the highlighted piece under the mouse, if any; clicks elsewhere are ignored.
     */
    private Piece clickablePieceAt(MouseEvent e) {
        return animation.isRunning() ? null : pieces.pieceAt(e.getPoint(), true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(UiTheme.background());
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (game == null) {
            g2.dispose();
            return;
        }
        BoardGeometry geo = BoardGeometry.fit(game.board(), getWidth(), getHeight());
        double scale = g2.getTransform().getScaleX();
        g2.drawImage(background.image(game, geo, getWidth(), getHeight(), scale), 0, 0, getWidth(), getHeight(), null);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paintLedFrame(g2, geo);
        paintCurrentCastle(g2, geo);
        pieces.paint(g2, game, geo, animation);
        effects.paint(g2, geo);
        g2.dispose();
    }

    /**
     * Running LED lights around the board in the players' colors.
     */
    private void paintLedFrame(Graphics2D g2, BoardGeometry geo) {
        double cell = geo.cell();
        Rectangle2D grid = geo.gridBounds();
        Rectangle2D strip = new Rectangle2D.Double(grid.getX() - cell * 0.16, grid.getY() - cell * 0.16,
                grid.getWidth() + cell * 0.32, grid.getHeight() + cell * 0.32);
        Color[] palette = game.players().stream().map(p -> UiTheme.neonOf(p.color())).toArray(Color[]::new);
        Glow.ledStrip(g2, strip, Math.max(14, cell * 0.5), 2, palette, 0.06);
    }

    /**
     * Pulsing glow around the castle of the player whose turn it is.
     */
    private void paintCurrentCastle(Graphics2D g2, BoardGeometry geo) {
        if (game.isOver()) {
            return;
        }
        Player current = game.currentPlayer();
        Board board = game.board();
        Rectangle2D r = geo.rect(board.castleArea(current.seat()));
        double cell = geo.cell();
        double pad = cell * 0.12;
        RoundRectangle2D outer = new RoundRectangle2D.Double(r.getX() + pad, r.getY() + pad,
                r.getWidth() - 2 * pad, r.getHeight() - 2 * pad, cell * 0.7, cell * 0.7);
        Glow.stroke(g2, outer, UiTheme.neonOf(current.color()), 2.4f, 0.4 + 0.6 * Animator.pulse(0.9));
    }

    /**
     * Short light bursts on the board: captures, pieces leaving the castle and pieces reaching home. Purely visual.
     */
    private static final class EffectLayer {

        private static final double EFFECT_MILLIS = 650;

        /**
         * One running flash on a square: where, in which color, when it started and whether it is the big
         * capture/finish
         * burst.
         */
        private record Effect(Square square, Color color, long start, boolean big) {
        }

        private final List<Effect> effects = new ArrayList<>();

        /**
         * @param square where the burst happens
         * @param color  burst color
         * @param big    larger burst (capture or finish)
         */
        void burst(Square square, Color color, boolean big) {
            effects.add(new Effect(square, color, System.nanoTime(), big));
        }

        /**
         * Removes all running bursts.
         */
        void clear() {
            effects.clear();
        }

        /**
         * Paints running bursts and drops finished ones.
         *
         * @param g2  graphics
         * @param geo current geometry
         */
        void paint(Graphics2D g2, BoardGeometry geo) {
            long now = System.nanoTime();
            Iterator<Effect> it = effects.iterator();
            while (it.hasNext()) {
                Effect effect = it.next();
                double p = (now - effect.start()) / 1e6 / EFFECT_MILLIS;
                if (p >= 1) {
                    it.remove();
                    continue;
                }
                Point2D c = geo.center(effect.square().pos());
                double radius = geo.cell() * (0.3 + (effect.big() ? 1.4 : 0.8) * Animator.ease(p));
                Glow.stroke(g2, new Ellipse2D.Double(c.getX() - radius, c.getY() - radius, radius * 2, radius * 2),
                        effect.color(), 2f, 1 - p);
                int sparks = effect.big() ? 12 : 8;
                for (int i = 0; i < sparks; i++) {
                    double angle = i * 2 * Math.PI / sparks + p;
                    double dist = radius * 1.1;
                    Glow.led(g2, c.getX() + Math.cos(angle) * dist, c.getY() + Math.sin(angle) * dist, 2,
                            effect.color(), 1 - p);
                }
            }
        }
    }

    /**
     * Where the board grid sits inside the panel (cell size and origin) and how grid cells convert to pixels.
     * Recomputed whenever the panel is resized, so the whole board always stays visible (NFR-02).
     */
    private static final class BoardGeometry {

        private final double cell;
        private final double originX;
        private final double originY;
        private final int rows;
        private final int cols;

        /**
         * Use the static factory method, which computes the cell size from the panel size.
         */
        private BoardGeometry(double cell, double originX, double originY, int rows, int cols) {
            this.cell = cell;
            this.originX = originX;
            this.originY = originY;
            this.rows = rows;
            this.cols = cols;
        }

        /**
         * Fits a board into a panel, keeping square cells and a small margin for the LED frame.
         *
         * @param board  the board
         * @param width  panel width in pixels
         * @param height panel height in pixels
         * @return the geometry
         */
        public static BoardGeometry fit(Board board, int width, int height) {
            double margin = Math.max(20, Math.min(width, height) * 0.035);
            double cell = Math.max(4,
                    Math.min((width - 2 * margin) / board.cols(), (height - 2 * margin) / board.rows()));
            double originX = (width - cell * board.cols()) / 2;
            double originY = (height - cell * board.rows()) / 2;
            return new BoardGeometry(cell, originX, originY, board.rows(), board.cols());
        }

        /**
         * @return edge length of one grid cell in pixels
         */
        public double cell() {
            return cell;
        }

        /**
         * @return pixel bounds of the whole grid
         */
        public Rectangle2D gridBounds() {
            return new Rectangle2D.Double(originX, originY, cell * cols, cell * rows);
        }

        /**
         * @param pos a grid cell
         * @return its pixel rectangle
         */
        public Rectangle2D rect(Board.GridPos pos) {
            return new Rectangle2D.Double(originX + pos.col() * cell, originY + pos.row() * cell, cell, cell);
        }

        /**
         * @param area a block of cells
         * @return its pixel rectangle
         */
        public Rectangle2D rect(Board.GridArea area) {
            return new Rectangle2D.Double(originX + area.col() * cell, originY + area.row() * cell,
                    area.cols() * cell, area.rows() * cell);
        }

        /**
         * @param pos a grid cell
         * @return the pixel center of that cell
         */
        public Point2D center(Board.GridPos pos) {
            return new Point2D.Double(originX + (pos.col() + 0.5) * cell, originY + (pos.row() + 0.5) * cell);
        }

        /**
         * @param castle a castle area
         * @param index  piece slot 0–3 (2×2 arrangement)
         * @return the pixel center of that slot
         */
        public Point2D castleSlot(Board.GridArea castle, int index) {
            double cx = originX + (castle.col() + castle.cols() / 2.0) * cell;
            double cy = originY + (castle.row() + castle.rows() / 2.0) * cell + cell * 0.32;
            double offset = cell * 1.1;
            return new Point2D.Double(cx + (index % 2 == 0 ? -offset : offset), cy + (index < 2 ? -offset : offset));
        }
    }

    /**
     * Paints the static board artwork — frame, castles, squares with their icons (FR-18) and the center — into an
     * image.
     * The image is reused every frame and only re-rendered when the size, screen scale or match changes.
     */
    private static final class BoardBackgroundPainter {

        private BufferedImage cached;
        private Game cachedGame;
        private int cachedWidth = -1;
        private int cachedHeight = -1;
        private double cachedScale = -1;

        private Game game;
        private BoardGeometry geo;

        /**
         * @param game   the match
         * @param geo    current geometry
         * @param width  panel width
         * @param height panel height
         * @param scale  screen scale factor (HiDPI)
         * @return the board artwork for these parameters
         */
        BufferedImage image(Game game, BoardGeometry geo, int width, int height, double scale) {
            if (cached == null || cachedGame != game || cachedWidth != width || cachedHeight != height
                    || cachedScale != scale) {
                cached = render(game, geo, width, height, scale);
                cachedGame = game;
                cachedWidth = width;
                cachedHeight = height;
                cachedScale = scale;
            }
            return cached;
        }

        /**
         * Forgets the cached artwork, e.g. when a new match starts.
         */
        void invalidate() {
            cached = null;
            cachedGame = null;
        }

        /**
         * Draws the whole static board (frame, castles, squares, center) into a new image at the screen's pixel scale.
         */
        private BufferedImage render(Game game, BoardGeometry geo, int width, int height, double scale) {
            this.game = game;
            this.geo = geo;
            BufferedImage image = new BufferedImage(Math.max(1, (int) Math.ceil(width * scale)),
                    Math.max(1, (int) Math.ceil(height * scale)), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            g2.scale(scale, scale);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Board board = game.board();
            double cell = geo.cell();

            Rectangle2D grid = geo.gridBounds();
            RoundRectangle2D frame = new RoundRectangle2D.Double(grid.getX() - cell * 0.32, grid.getY() - cell * 0.32,
                    grid.getWidth() + cell * 0.64, grid.getHeight() + cell * 0.64, cell * 0.6, cell * 0.6);
            g2.setColor(UiTheme.bgDeep());
            g2.fill(frame);
            g2.setColor(UiTheme.grid());
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(frame);

            for (Seat seat : Seat.values()) {
                paintCastle(g2, seat, board.castleArea(seat));
            }
            for (Square square : board.squares()) {
                if (square.type() != SquareType.FINISH) {
                    paintSquare(g2, square);
                }
            }
            paintCenter(g2, board);
            g2.dispose();
            return image;
        }

        /**
         * Draws one castle in its owner's color with four empty slots and the color name; a corner without a player is
         * grey
         * and says "Free corner".
         */
        private void paintCastle(Graphics2D g2, Seat seat, Board.GridArea area) {
            double cell = geo.cell();
            Rectangle2D r = geo.rect(area);
            double pad = cell * 0.12;
            RoundRectangle2D outer = new RoundRectangle2D.Double(r.getX() + pad, r.getY() + pad,
                    r.getWidth() - 2 * pad, r.getHeight() - 2 * pad, cell * 0.7, cell * 0.7);
            Color color = seatNeon(game, seat);
            g2.setColor(UiTheme.dim(color));
            g2.fill(outer);
            Glow.stroke(g2, outer, color, 1.6f, 0.45);
            double inset = cell * 0.8;
            RoundRectangle2D den = new RoundRectangle2D.Double(r.getX() + inset, r.getY() + inset,
                    r.getWidth() - 2 * inset, r.getHeight() - 2 * inset, cell * 0.6, cell * 0.6);
            g2.setColor(UiTheme.alpha(UiTheme.bgDeep(), 0.75));
            g2.fill(den);
            for (int i = 0; i < GameConfig.PIECES_PER_PLAYER; i++) {
                Point2D slot = geo.castleSlot(area, i);
                double d = cell * 1.05;
                g2.setColor(UiTheme.alpha(color, 0.35));
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new Ellipse2D.Double(slot.getX() - d / 2, slot.getY() - d / 2, d, d));
            }
            // Only the color name is written in the castle; the player's name is already in the side panel (NFR-05).
            Player owner = playerAt(game, seat);
            String label = owner == null ? "Free corner" : owner.color().name();
            g2.setFont(fitFont(g2, label, den.getWidth() - 10, Math.max(12, cell * 0.34)));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(owner == null ? UiTheme.textMuted() : UiTheme.text());
            g2.drawString(label, (float) (den.getCenterX() - fm.stringWidth(label) / 2.0),
                    (float) (den.getY() + fm.getAscent() + cell * 0.12));
        }

        /**
         * Draws one track or home-lane square with the look of its type (FR-18).
         */
        private void paintSquare(Graphics2D g2, Square square) {
            double cell = geo.cell();
            Rectangle2D r = geo.rect(square.pos());
            double inset = Math.max(1, cell * 0.05);
            RoundRectangle2D tile = new RoundRectangle2D.Double(r.getX() + inset, r.getY() + inset,
                    r.getWidth() - 2 * inset, r.getHeight() - 2 * inset, cell * 0.22, cell * 0.22);
            Color seatColor = square.owner() == null ? UiTheme.surfaceHi() : seatNeon(game, square.owner());
            Color fill = switch (square.type()) {
                case START -> UiTheme.mix(seatColor, UiTheme.surfaceHi(), 0.38);
                case ARROW -> UiTheme.mix(seatColor, UiTheme.surfaceHi(), 0.28);
                case HOME_LANE -> UiTheme.lane(seatColor);
                default -> UiTheme.surfaceHi();
            };
            g2.setColor(fill);
            g2.fill(tile);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(UiTheme.alpha(Color.WHITE, 0.06));
            g2.draw(tile);
            switch (square.type()) {
                case STAR -> paintStar(g2, r);
                case START -> {
                    Glow.stroke(g2, tile, seatColor, 1.4f, 0.5);
                    paintCenteredText(g2, r, "#", UiTheme.text());
                }
                case ARROW -> paintArrow(g2, square, r);
                case HOME_LANE -> Glow.stroke(g2, tile, seatColor, 1f, 0.25);
                case QUESTION -> paintCenteredText(g2, r, "?", UiTheme.text());
                case SURPRISE -> paintCenteredText(g2, r, "!", UiTheme.text());
                default -> {
                }
            }
        }

        /**
         * Draws the center: one colored triangle pointing at each home lane.
         */
        private void paintCenter(Graphics2D g2, Board board) {
            Board.GridArea area = board.centerArea();
            Rectangle2D r = geo.rect(area);
            double cx = r.getCenterX();
            double cy = r.getCenterY();
            int midRow = area.row() + area.rows() / 2;
            int midCol = area.col() + area.cols() / 2;
            for (Seat seat : Seat.values()) {
                List<Square> path = board.pathFor(seat);
                Board.GridPos finish = path.get(path.size() - 1).pos();
                int dr = finish.row() - midRow;
                int dc = finish.col() - midCol;
                Polygon triangle = new Polygon();
                int x0 = (int) Math.round(r.getX());
                int y0 = (int) Math.round(r.getY());
                int x1 = (int) Math.round(r.getMaxX());
                int y1 = (int) Math.round(r.getMaxY());
                if (dc < 0) {
                    triangle.addPoint(x0, y0);
                    triangle.addPoint(x0, y1);
                } else if (dc > 0) {
                    triangle.addPoint(x1, y0);
                    triangle.addPoint(x1, y1);
                } else if (dr < 0) {
                    triangle.addPoint(x0, y0);
                    triangle.addPoint(x1, y0);
                } else {
                    triangle.addPoint(x0, y1);
                    triangle.addPoint(x1, y1);
                }
                triangle.addPoint((int) Math.round(cx), (int) Math.round(cy));
                Color color = seatNeon(game, seat);
                g2.setColor(UiTheme.mix(color, UiTheme.bgDeep(), 0.6));
                g2.fill(triangle);
                Glow.stroke(g2, triangle, color, 1.2f, 0.35);
            }
            Glow.halo(g2, cx, cy, geo.cell() * 0.7, UiTheme.pantherGold(), 0.8);
        }

        /**
         * Draws the gold star of a protection square.
         */
        private void paintStar(Graphics2D g2, Rectangle2D r) {
            double cx = r.getCenterX();
            double cy = r.getCenterY();
            double outer = geo.cell() * 0.36;
            double inner = outer * 0.45;
            Polygon star = new Polygon();
            for (int i = 0; i < 10; i++) {
                double radius = i % 2 == 0 ? outer : inner;
                double angle = -Math.PI / 2 + i * Math.PI / 5;
                star.addPoint((int) Math.round(cx + radius * Math.cos(angle)),
                        (int) Math.round(cy + radius * Math.sin(angle)));
            }
            Glow.halo(g2, cx, cy, geo.cell() * 0.55, UiTheme.star(), 0.7);
            g2.setColor(UiTheme.star());
            g2.fill(star);
        }

        /**
         * Draws an arrow square's arrow in its owner's color, pointing to where that owner turns.
         */
        private void paintArrow(Graphics2D g2, Square square, Rectangle2D r) {
            List<Square> path = game.board().pathFor(square.owner());
            Board.GridPos next = path.get(path.indexOf(square) + 1).pos();
            double dx = next.col() - square.pos().col();
            double dy = next.row() - square.pos().row();
            double cx = r.getCenterX();
            double cy = r.getCenterY();
            double s = geo.cell() * 0.3;
            Polygon arrow = new Polygon();
            arrow.addPoint((int) (cx + dx * s), (int) (cy + dy * s));
            arrow.addPoint((int) (cx - dx * s - dy * s), (int) (cy - dy * s + dx * s));
            arrow.addPoint((int) (cx - dx * s + dy * s), (int) (cy - dy * s - dx * s));
            Color color = seatNeon(game, square.owner());
            Glow.halo(g2, cx, cy, geo.cell() * 0.5, color, 0.6);
            g2.setColor(color);
            g2.fill(arrow);
        }

        /**
         * Draws a symbol such as #, ? or ! in the middle of a square.
         */
        private void paintCenteredText(Graphics2D g2, Rectangle2D r, String text, Color color) {
            g2.setFont(UiTheme.font((float) Math.max(12, geo.cell() * 0.48), true));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(color);
            g2.drawString(text, (float) (r.getCenterX() - fm.stringWidth(text) / 2.0),
                    (float) (r.getCenterY() + (fm.getAscent() - fm.getDescent()) / 2.0));
        }

        /**
         * Shrinks the font until the text fits, but never below 12 pt (NFR-03).
         */
        private static Font fitFont(Graphics2D g2, String text, double maxWidth, double preferredSize) {
            float size = (float) preferredSize;
            Font font = UiTheme.font(size, true);
            while (size > 12 && g2.getFontMetrics(font).stringWidth(text) > maxWidth) {
                size -= 1;
                font = UiTheme.font(size, true);
            }
            return font;
        }

        // ------------------------------------------------------------------ colors of the corners

        /**
         * @param game the match
         * @param seat a corner
         * @return the player sitting there, or {@code null} for a free corner
         */
        static Player playerAt(Game game, Seat seat) {
            return game.players().stream().filter(p -> p.seat() == seat).findFirst().orElse(null);
        }

        /**
         * @param game the match
         * @param seat a corner
         * @return LED color of the corner: its player's color, or a neutral tone when nobody sits there (TD-08)
         */
        static Color seatNeon(Game game, Seat seat) {
            Player player = playerAt(game, seat);
            return player == null ? UiTheme.emptySeat() : UiTheme.neonOf(player.color());
        }
    }

    /**
     * Paints every piece (in castles, on squares and while hopping), highlights the pieces the model says may move
     * (FR-31) and remembers where each piece was drawn so clicks can be mapped back to pieces (min. 24 px, NFR-06).
     */
    private static final class PieceLayer {

        private static final int MIN_HIT_SIZE = 24;

        private final Map<Piece, Ellipse2D> hitAreas = new LinkedHashMap<>();
        private Set<Piece> selectable = Set.of();

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
        }

        /**
         * @param point          a mouse position
         * @param onlySelectable {@code true} to ignore pieces that may not move now
         * @return the topmost piece drawn at that point, or {@code null}
         */
        Piece pieceAt(Point point, boolean onlySelectable) {
            Piece found = null;
            for (Map.Entry<Piece, Ellipse2D> entry : hitAreas.entrySet()) {
                if ((!onlySelectable || selectable.contains(entry.getKey())) && entry.getValue().contains(point)) {
                    found = entry.getKey();
                }
            }
            return found;
        }

        /**
         * @param g2        graphics
         * @param game      the match
         * @param geo       current geometry
         * @param animation the running move animation (may be idle)
         */
        void paint(Graphics2D g2, Game game, BoardGeometry geo, MoveAnimation animation) {
            hitAreas.clear();
            boolean highlight = !animation.isRunning();
            Piece moving = animation.movingPiece();
            Map<Square, List<Piece>> bySquare = new LinkedHashMap<>();
            for (Player player : game.players()) {
                for (Piece piece : player.pieces()) {
                    if (piece == moving) {
                        continue;
                    }
                    Square square = animation.isPendingCapture(piece)
                            ? animation.landingSquare() : piece.currentSquare();
                    if (square == null) {
                        Point2D slot = geo.castleSlot(game.board().castleArea(player.seat()), piece.number() - 1);
                        paintPiece(g2, game, geo, piece, slot.getX(), slot.getY(), geo.cell() * 0.98, highlight);
                    } else {
                        bySquare.computeIfAbsent(square, k -> new ArrayList<>()).add(piece);
                    }
                }
            }
            for (Map.Entry<Square, List<Piece>> entry : bySquare.entrySet()) {
                paintStack(g2, game, geo, entry.getKey(), entry.getValue(), highlight);
            }
            if (moving != null) {
                MoveAnimation.Pose pose = animation.pose(geo, game.board());
                Color color = UiTheme.neonOf(moving.owner().color());
                Glow.led(g2, pose.trail().getX(), pose.trail().getY(), 3, color, pose.trailAlpha());
                paintPiece(g2, game, geo, moving, pose.x(), pose.y(), geo.cell() * 0.9 * pose.sizeFactor(), false);
            }
        }

        /**
         * Several pieces on one square share it in a 2×2 or 3×3 arrangement; movable ones are drawn on top.
         */
        private void paintStack(Graphics2D g2, Game game, BoardGeometry geo, Square square, List<Piece> pieces,
                                boolean highlight) {
            pieces.sort((a, b) -> Boolean.compare(selectable.contains(a), selectable.contains(b)));
            Rectangle2D r = geo.rect(square.pos());
            double cell = geo.cell();
            int n = pieces.size();
            int k = n == 1 ? 1 : n <= 4 ? 2 : 3;
            double d = cell * (k == 1 ? 0.9 : k == 2 ? 0.56 : 0.4);
            for (int i = 0; i < n; i++) {
                double x = r.getX() + ((i % k) + 0.5) * cell / k;
                double y = r.getY() + ((i / k % k) + 0.5) * cell / k;
                paintPiece(g2, game, geo, pieces.get(i), x, y, d, highlight);
            }
        }

        /**
         * Draws one panther piece, with a pulsing glow when it can be clicked.
         */
        private void paintPiece(Graphics2D g2, Game game, BoardGeometry geo, Piece piece, double cx, double cy,
                           double d,
                                boolean highlight) {
            Player owner = piece.owner();
            boolean active = highlight && selectable.contains(piece);
            double bob = active ? -geo.cell() * 0.08 * Animator.pulse(1.4) : 0;
            if (active) {
                double w = d * 1.15 + 4 * Animator.pulse(1.4);
                double floorY = cy + d * 0.4;
                Glow.halo(g2, cx, floorY, w * 0.75, UiTheme.neonOf(owner.color()), 0.9);
                Glow.stroke(g2, new Ellipse2D.Double(cx - w / 2, floorY - w * 0.17, w, w * 0.34), UiTheme.text(),
                        2f, 0.9);
            }
            double seed = owner.seat().ordinal() * 4 + piece.number();
            PantherPainter.paintPiece(g2, cx, cy + bob, d, UiTheme.colorOf(owner.color()), PantherPainter.blink(seed));
            double hit = Math.max(d, MIN_HIT_SIZE);
            hitAreas.put(piece, new Ellipse2D.Double(cx - hit / 2, cy + bob - hit / 2, hit, hit));
        }

        /**
         * Hop animation of one move, square by square, finishing within one second regardless of distance (NFR-13).
         * The model has already applied the move; this class only decides where to draw the moving piece meanwhile.
         */
        static final class MoveAnimation {

            /**
             * Where to draw the moving piece in the current frame.
             *
             * @param x           center x
             * @param y           center y
             * @param sizeFactor  size multiplier (the piece grows slightly at the top of a hop)
             * @param trail       the square the piece just left, for a fading light trail
             * @param trailAlpha  trail brightness 0–1
             */
            record Pose(double x, double y, double sizeFactor, Point2D trail, double trailAlpha) {
            }

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
                this.millis = durationMillis(squares);
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
             * @return total animation time: ~130 ms per square, at least 260 ms, at most {@link
             * GameConfig#MAX_MOVE_ANIMATION_MILLIS}
             */
            static double durationMillis(int squares) {
                return Math.min(GameConfig.MAX_MOVE_ANIMATION_MILLIS, Math.max(260, squares * 130));
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
             * @param geo   current geometry
             * @param board the board (for the castle slot a released piece starts from)
             * @return where to draw the moving piece now
             */
            Pose pose(BoardGeometry geo, Board board) {
                List<Square> squares = move.traversedSquares();
                double progress = Math.min(1, (System.nanoTime() - startNanos) / 1e6 / millis);
                int n = squares.size();
                double f = progress * n;
                int segment = Math.min(n - 1, (int) f);
                double u = progress >= 1 ? 1 : f - segment;
                Point2D from = segment == 0 ? origin(geo, board) : geo.center(squares.get(segment - 1).pos());
                Point2D to = geo.center(squares.get(segment).pos());
                double eased = Animator.ease(u);
                double hop = Math.sin(Math.PI * u);
                double x = from.getX() + (to.getX() - from.getX()) * eased;
                double y = from.getY() + (to.getY() - from.getY()) * eased - hop * geo.cell() * 0.45;
                return new Pose(x, y, 1 + 0.18 * hop, from, 0.5 * (1 - u));
            }

            /**
             * Where the animation starts: the piece's castle slot when it is released, otherwise the square it leaves.
             */
            private Point2D origin(BoardGeometry geo, Board board) {
                Piece piece = move.piece();
                if (move.from() == Piece.IN_CASTLE) {
                    return geo.castleSlot(board.castleArea(piece.owner().seat()), piece.number() - 1);
                }
                return geo.center(piece.path().get(move.from()).pos());
            }
        }
    }
}
