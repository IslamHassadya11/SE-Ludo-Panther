package model;

import java.util.*;
import java.util.random.RandomGenerator;

/**
 * A complete board: all squares, their grid cells, castles, and the route each seat follows.
 * <p>
 * The route is given as a graph ({@code next} links plus own-seat arrow turns) so that boards with one,
 * two or three circuits use the same code (FR-14, FR-32). The graph is walked once per seat to produce that
 * seat's path: own start, clockwise along the shared track(s), through its own arrow(s), along its home lane
 * to its final home square.
 */
public final class Board {

    private final Level level;
    private final int rows;
    private final int cols;
    private final List<Square> sharedTrack;
    private final List<Square> squares;
    private final Map<Seat, List<Square>> paths = new EnumMap<>(Seat.class);
    private final Map<Seat, GridArea> castles;
    private final GridArea center;

    /**
     * @param level       level this board was built for
     * @param rows        grid height
     * @param cols        grid width
     * @param sharedTrack all shared-track squares (every circuit)
     * @param squares     every square on the board (shared track, home lanes and finishes)
     * @param next        default next square for each square that has one
     * @param arrowTurns  for each arrow square, the square its own seat turns into
     * @param castles     castle area per seat
     * @param center      center finish area
     * @throws IllegalStateException if a seat's route is broken
     */
    public Board(Level level, int rows, int cols, List<Square> sharedTrack, List<Square> squares,
                 Map<Square, Square> next, Map<Square, Square> arrowTurns,
                 Map<Seat, GridArea> castles, GridArea center) {
        this.level = level;
        this.rows = rows;
        this.cols = cols;
        this.sharedTrack = List.copyOf(sharedTrack);
        this.squares = List.copyOf(squares);
        this.castles = Collections.unmodifiableMap(new EnumMap<>(castles));
        this.center = center;
        for (Seat seat : castles.keySet()) {
            paths.put(seat, buildPath(seat, next, arrowTurns));
        }
    }

    /**
     * Walks the route of one seat: from its start square along the track, turning at its own arrow into the next inner
     * track or its home lane (FR-32), until the finish square. Fails loudly if the layout data has a gap or a loop.
     */
    private List<Square> buildPath(Seat seat, Map<Square, Square> next, Map<Square, Square> arrowTurns) {
        Square current = sharedTrack.stream()
                .filter(s -> s.type() == SquareType.START && s.owner() == seat)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No start square for " + seat));
        List<Square> path = new ArrayList<>();
        path.add(current);
        while (current.type() != SquareType.FINISH) {
            Square following = current.isArrowFor(seat) ? arrowTurns.get(current) : next.get(current);
            if (following == null || path.size() > squares.size()) {
                throw new IllegalStateException("Broken route for " + seat + " after " + current);
            }
            path.add(following);
            current = following;
        }
        return Collections.unmodifiableList(path);
    }

    /**
     * @return the level this board belongs to
     */
    public Level level() {
        return level;
    }

    /**
     * @return grid height in cells
     */
    public int rows() {
        return rows;
    }

    /**
     * @return grid width in cells
     */
    public int cols() {
        return cols;
    }

    /**
     * @return shared-track squares (excluding home lanes)
     */
    public List<Square> sharedTrack() {
        return sharedTrack;
    }

    /**
     * @return every square on the board
     */
    public List<Square> squares() {
        return squares;
    }

    /**
     * @param seat a seat
     * @return that seat's ordered path from its start square (index 0) to its final home square (last index)
     */
    public List<Square> pathFor(Seat seat) {
        return paths.get(seat);
    }

    /**
     * @param seat a seat
     * @return that seat's start square
     */
    public Square startSquare(Seat seat) {
        return paths.get(seat).get(0);
    }

    /**
     * @param seat a seat
     * @return grid area of that seat's castle
     */
    public GridArea castleArea(Seat seat) {
        return castles.get(seat);
    }

    /**
     * @return grid area containing the final home squares
     */
    public GridArea centerArea() {
        return center;
    }

    /**
     * @param type a square type
     * @return how many shared-track squares have that type
     */
    public long countOnTrack(SquareType type) {
        return sharedTrack.stream().filter(s -> s.type() == type).count();
    }

    /**
     * A cell position on the board grid, used by the view to draw a square.
     *
     * @param row zero-based row from the top
     * @param col zero-based column from the left
     */
    public record GridPos(int row, int col) {
    }

    /**
     * A rectangular block of grid cells, e.g. a castle or the center finish area.
     *
     * @param row  top row
     * @param col  left column
     * @param rows height in cells
     * @param cols width in cells
     */
    public record GridArea(int row, int col, int rows, int cols) {
    }

    /**
     * Builds the board of one level: fixed geometry plus freshly randomized special squares (FR-14–17).
     * <p>{@link #forLevel(Level)} is a factory method (Factory pattern, NFR-21): the game asks for "the layout of this
     * level" and does not need to know which layout class builds it.
     */
    public interface Layout {

        /**
         * @param rng random source for special-square placement
         * @return a new board
         */
        Board build(RandomGenerator rng);

        /**
         * @param level a difficulty level
         * @return {@code true} if a board layout exists for that level
         */
        public static boolean isAvailable(Level level) {
            return level == Level.EASY;
        }

        /**
         * @param level a difficulty level
         * @return the layout that builds boards for that level
         * @throws UnsupportedOperationException if the level's layout is not implemented yet
         */
        public static Layout forLevel(Level level) {
            return switch (level) {
                case EASY -> new EasyLayout();
                case MEDIUM, HARD -> throw new UnsupportedOperationException(
                        level + " board layout is pending decision S-04 (board route map)");
            };
        }
    }

    /**
     * The Easy board: a 15×15 cross with one 52-square shared circuit, as drawn in the customer document
     * ({@code Ludo Project.pdf} p. 2). Green castle top-left, Yellow top-right, Blue bottom-right, Red bottom-left.
     * Shared-track index 0 is cell (6,0); indices increase clockwise.
     */
    public static final class EasyLayout implements Layout {

        private static final int SIZE = 15;

        /** Track index of each seat's start square ({@code #}). */
        private static final Map<Seat, Integer> START_INDEX = Map.of(
                Seat.TOP_LEFT, 1, Seat.TOP_RIGHT, 14, Seat.BOTTOM_RIGHT, 27, Seat.BOTTOM_LEFT, 40);

        /** Track index of each seat's arrow leading into its home lane. */
        private static final Map<Seat, Integer> ARROW_INDEX = Map.of(
                Seat.TOP_LEFT, 51, Seat.TOP_RIGHT, 12, Seat.BOTTOM_RIGHT, 25, Seat.BOTTOM_LEFT, 38);
        @Override
        public Board build(RandomGenerator rng) {
            GameConfig.LevelSettings settings = GameConfig.settingsFor(Level.EASY);
            List<Board.GridPos> trackCells = trackCells();
            if (trackCells.size() != settings.sharedTrackSquares()) {
                throw new IllegalStateException("Easy track has " + trackCells.size() + " cells");
            }

            Map<Integer, Seat> starts = invert(START_INDEX);
            Map<Integer, Seat> arrows = invert(ARROW_INDEX);
            List<Integer> ordinary = new ArrayList<>();
            for (int i = 0; i < trackCells.size(); i++) {
                if (!starts.containsKey(i) && !arrows.containsKey(i)) {
                    ordinary.add(i);
                }
            }
            Map<SquareType, Integer> counts = new EnumMap<>(SquareType.class);
            counts.put(SquareType.STAR, settings.protectionSquares());
            counts.put(SquareType.QUESTION, settings.questionSquares());
            counts.put(SquareType.SURPRISE, settings.surpriseSquares());
            Map<Integer, SquareType> specials = SpecialSquarePlacer.place(ordinary, counts, rng);

            SquareFactory factory = new SquareFactory();
            List<Square> track = new ArrayList<>();
            for (int i = 0; i < trackCells.size(); i++) {
                Board.GridPos pos = trackCells.get(i);
                if (starts.containsKey(i)) {
                    track.add(factory.create(SquareType.START, pos, starts.get(i)));
                } else if (arrows.containsKey(i)) {
                    track.add(factory.create(SquareType.ARROW, pos, arrows.get(i)));
                } else {
                    track.add(factory.createShared(specials.getOrDefault(i, SquareType.REGULAR), pos));
                }
            }

            List<Square> all = new ArrayList<>(track);
            Map<Square, Square> next = new HashMap<>();
            Map<Square, Square> arrowTurns = new HashMap<>();
            for (int i = 0; i < track.size(); i++) {
                next.put(track.get(i), track.get((i + 1) % track.size()));
            }
            for (Seat seat : Seat.values()) {
                List<Square> lane = buildHomeLane(seat, factory);
                all.addAll(lane);
                for (int i = 0; i + 1 < lane.size(); i++) {
                    next.put(lane.get(i), lane.get(i + 1));
                }
                arrowTurns.put(track.get(ARROW_INDEX.get(seat)), lane.get(0));
            }

            Map<Seat, Board.GridArea> castles = new EnumMap<>(Seat.class);
            castles.put(Seat.TOP_LEFT, new Board.GridArea(0, 0, 6, 6));
            castles.put(Seat.TOP_RIGHT, new Board.GridArea(0, 9, 6, 6));
            castles.put(Seat.BOTTOM_RIGHT, new Board.GridArea(9, 9, 6, 6));
            castles.put(Seat.BOTTOM_LEFT, new Board.GridArea(9, 0, 6, 6));

            return new Board(Level.EASY, SIZE, SIZE, track, all, next, arrowTurns, castles, new GridArea(6, 6, 3, 3));
        }

        /**
         * @return the 52 shared-track cells in clockwise order starting at (6,0)
         */
        static List<Board.GridPos> trackCells() {
            List<Board.GridPos> cells = new ArrayList<>();
            for (int c = 0; c <= 5; c++) cells.add(new Board.GridPos(6, c));
            for (int r = 5; r >= 0; r--) cells.add(new Board.GridPos(r, 6));
            cells.add(new Board.GridPos(0, 7));
            for (int r = 0; r <= 5; r++) cells.add(new Board.GridPos(r, 8));
            for (int c = 9; c <= 14; c++) cells.add(new Board.GridPos(6, c));
            cells.add(new Board.GridPos(7, 14));
            for (int c = 14; c >= 9; c--) cells.add(new Board.GridPos(8, c));
            for (int r = 9; r <= 14; r++) cells.add(new Board.GridPos(r, 8));
            cells.add(new Board.GridPos(14, 7));
            for (int r = 14; r >= 9; r--) cells.add(new Board.GridPos(r, 6));
            for (int c = 5; c >= 0; c--) cells.add(new Board.GridPos(8, c));
            cells.add(new Board.GridPos(7, 0));
            return cells;
        }

        /**
         * Home lane (5 lane squares + final home) running from the arrow toward the center.
         */
        private static List<Square> buildHomeLane(Seat seat, SquareFactory factory) {
            int laneSquares = GameConfig.HOME_LANE_LENGTH - 1;
            List<Square> lane = new ArrayList<>();
            for (int k = 1; k <= GameConfig.HOME_LANE_LENGTH; k++) {
                Board.GridPos pos = switch (seat) {
                    case TOP_LEFT -> new Board.GridPos(7, k);
                    case TOP_RIGHT -> new Board.GridPos(k, 7);
                    case BOTTOM_RIGHT -> new Board.GridPos(7, SIZE - 1 - k);
                    case BOTTOM_LEFT -> new Board.GridPos(SIZE - 1 - k, 7);
                };
                SquareType type = k <= laneSquares ? SquareType.HOME_LANE : SquareType.FINISH;
                lane.add(factory.create(type, pos, seat));
            }
            return lane;
        }

        /**
         * Turns a seat-to-index table into an index-to-seat table, so the builder can look up "whose square is this?"
         * by
         * track index.
         */
        private static Map<Integer, Seat> invert(Map<Seat, Integer> bySeat) {
            Map<Integer, Seat> byIndex = new HashMap<>();
            bySeat.forEach((seat, index) -> byIndex.put(index, seat));
            return byIndex;
        }
    }
}
