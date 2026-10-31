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
    private Game game;

    /**
     * Creates an empty board view.
     */
    public BoardPanel() {
        setBackground(UiTheme.background());
        setPreferredSize(new Dimension(680, 680));
    }

    /**
     * @param game match to display, or {@code null}
     */
    public void setGame(Game game) {
        background.invalidate();
        this.game = game;
        if (game != null) {
            Animator.subscribe(this);
        } else {
            Animator.unsubscribe(this);
        }
        repaint();
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

}
