package model;

import java.util.*;
import java.util.List;
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

    /** Squares in each colored home lane, including the final home square. */
    public static final int HOME_LANE_LENGTH = 6;

    private final Game.Level level;
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
     * @param playing     the seats that have a player; the start and arrow squares of the other seats become normal
     *                    squares
     * @throws IllegalStateException if a seat's route is broken
     */
    private Board(Game.Level level, int rows, int cols, List<Square> sharedTrack, List<Square> squares,
                  Map<Square, Square> next, Map<Square, Square> arrowTurns,
                  Map<Seat, GridArea> castles, GridArea center, Set<Seat> playing) {
        this.level = level;
        this.rows = rows;
        this.cols = cols;
        this.castles = Collections.unmodifiableMap(new EnumMap<>(castles));
        this.center = center;
        Map<Seat, List<Square>> routes = new EnumMap<>(Seat.class);
        for (Seat seat : castles.keySet()) {
            routes.put(seat, buildPath(seat, sharedTrack, squares.size(), next, arrowTurns));
        }
        // A corner nobody plays has no start square and no arrow: they are normal squares there.
        Map<Square, Square> plain = new HashMap<>();
        for (Square square : sharedTrack) {
            boolean seatSquare = square.type() == Square.Type.START || square.type() == Square.Type.ARROW;
            if (seatSquare && !playing.contains(square.owner())) {
                plain.put(square, Square.create(square.id(), Square.Type.REGULAR, square.position(), null, null));
            }
        }
        this.sharedTrack = replaceSquares(sharedTrack, plain);
        this.squares = replaceSquares(squares, plain);
        routes.forEach((seat, route) -> paths.put(seat, replaceSquares(route, plain)));
    }

    /**
     * @param list        a list of squares
     * @param replacement squares to swap for others
     * @return an unmodifiable copy of the list with the squares swapped
     */
    private static List<Square> replaceSquares(List<Square> list, Map<Square, Square> replacement) {
        return list.stream().map(s -> replacement.getOrDefault(s, s)).toList();
    }

    /**
     * Walks the route of one seat: from its start square along the track, turning at its own arrow into the next inner
     * track or its home lane (FR-32), until the finish square. Fails loudly if the layout data has a gap or a loop.
     */
    private static List<Square> buildPath(Seat seat, List<Square> sharedTrack, int squareCount,
                                          Map<Square, Square> next, Map<Square, Square> arrowTurns) {
        Square current = sharedTrack.stream()
                .filter(s -> s.type() == Square.Type.START && s.owner() == seat)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No start square for " + seat));
        List<Square> path = new ArrayList<>();
        path.add(current);
        while (current.type() != Square.Type.FINISH) {
            Square following = current.isArrowFor(seat) ? arrowTurns.get(current) : next.get(current);
            if (following == null || path.size() > squareCount) {
                throw new IllegalStateException("Broken route for " + seat + " after " + current);
            }
            path.add(following);
            current = following;
        }
        return path;
    }

    /**
     * @return the level this board belongs to
     */
    public Game.Level level() {
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
    public long countSquaresOfType(Square.Type type) {
        return sharedTrack.stream().filter(s -> s.type() == type).count();
    }

    /**
     * A cell position on the board grid, used by the view to draw a square.
     *
     * @param row zero-based row from the top
     * @param col zero-based column from the left
     */
    public record GridPosition(int row, int col) {
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


    /** Track index on the Easy circuit of the top-left start square. */
    private static final int EASY_START_INDEX = 1;
    /** Track index on the Easy circuit of the top-left arrow into the home lane. */
    private static final int EASY_ARROW_INDEX = 51;
    /** Cells across the inner Easy cross. */
    private static final int EASY_GRID_SIZE = 15;

    /**
     * Builds the board of a level: the fixed geometry of the boards in the customer document plus freshly randomized
     * special squares (FR-14–17).
     * <p>
     * Every level has the same inner 15×15 cross with one 52-square circuit (the Easy board). Medium wraps one more
     * square ring around it (17×17 grid, 64 more squares) and Hard two rings (19×19, 64 + 72 more). On Medium and
     * Hard the start squares sit on the four corners of the outermost ring. A piece goes clockwise around its ring
     * until its own arrow, which turns it one ring inward; the arrow on the inner circuit turns it into its home lane
     * (FR-32). The geometry is described for the top-left seat and turned a quarter clockwise for each next seat.
     *
     * @param level the level to build
     * @param rng   random source for placing the special squares; a seeded generator gives a reproducible board
     * @return a new board with a player in every corner
     */
    public static Board build(Game.Level level, RandomGenerator rng) {
        return build(level, EnumSet.allOf(Seat.class), rng);
    }

    /**
     * Builds the board of a level for the corners that have a player. In a corner nobody plays, the start square and
     * the arrows become normal squares; its castle and home lane stay on the board but nobody uses them.
     *
     * @param level   the level to build
     * @param playing the seats that have a player
     * @param rng     random source for placing the special squares; a seeded generator gives a reproducible board
     * @return a new board
     */
    public static Board build(Game.Level level, Collection<Seat> playing, RandomGenerator rng) {
        int rings = level.ordinal();
        int size = EASY_GRID_SIZE + 2 * rings;
        List<List<GridPosition>> circuits = new ArrayList<>();
        for (int o = 0; o < rings; o++) {
            circuits.add(ringPositions(o, size));
        }
        List<GridPosition> inner = new ArrayList<>();
        for (GridPosition p : easyTrackPositions()) {
            inner.add(new GridPosition(p.row() + rings, p.col() + rings));
        }
        circuits.add(inner);
        Map<GridPosition, Seat> starts = new HashMap<>();
        Map<GridPosition, Seat> arrows = new HashMap<>();
        for (Seat seat : Seat.values()) {
            starts.put(rotateForSeat(startPosition(rings), seat, size), seat);
            for (int o = 0; o <= rings; o++) {
                arrows.put(rotateForSeat(arrowPosition(o, rings), seat, size), seat);
            }
        }
        List<GridPosition> cells = new ArrayList<>();
        circuits.forEach(cells::addAll);
        if (cells.size() != level.sharedTrackSquares()) {
            throw new IllegalStateException(level + " track has " + cells.size() + " cells");
        }
        List<Integer> ordinary = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            if (!starts.containsKey(cells.get(i)) && !arrows.containsKey(cells.get(i))) {
                ordinary.add(i);
            }
        }
        Map<Integer, Game.Level> questionLevels = new HashMap<>();
        Map<Integer, Square.Type> specials = placeSpecialSquares(level, ordinary, rng, questionLevels);
        int[] nextId = {0};
        Map<GridPosition, Square> byCell = new HashMap<>();
        List<Square> track = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            GridPosition pos = cells.get(i);
            Square square;
            if (starts.containsKey(pos)) {
                square = Square.create(nextId[0]++, Square.Type.START, pos, starts.get(pos), null);
            } else if (arrows.containsKey(pos)) {
                square = Square.create(nextId[0]++, Square.Type.ARROW, pos, arrows.get(pos), null);
            } else {
                square = Square.create(nextId[0]++, specials.getOrDefault(i, Square.Type.REGULAR), pos, null,
                        questionLevels.get(i));
            }
            track.add(square);
            byCell.put(pos, square);
        }
        List<Square> all = new ArrayList<>(track);
        Map<Square, Square> next = new HashMap<>();
        Map<Square, Square> arrowTurns = new HashMap<>();
        for (List<GridPosition> circuit : circuits) {
            for (int i = 0; i < circuit.size(); i++) {
                next.put(byCell.get(circuit.get(i)), byCell.get(circuit.get((i + 1) % circuit.size())));
            }
        }
        for (Seat seat : Seat.values()) {
            for (int o = 0; o < rings; o++) {
                arrowTurns.put(byCell.get(rotateForSeat(arrowPosition(o, rings), seat, size)),
                        byCell.get(rotateForSeat(entryPosition(o), seat, size)));
            }
            List<Square> lane = homeLane(seat, rings, size, nextId);
            all.addAll(lane);
            for (int i = 0; i + 1 < lane.size(); i++) {
                next.put(lane.get(i), lane.get(i + 1));
            }
            arrowTurns.put(byCell.get(rotateForSeat(arrowPosition(rings, rings), seat, size)), lane.get(0));
        }
        Map<Seat, GridArea> castles = new EnumMap<>(Seat.class);
        castles.put(Seat.TOP_LEFT, new GridArea(rings, rings, 6, 6));
        castles.put(Seat.TOP_RIGHT, new GridArea(rings, rings + 9, 6, 6));
        castles.put(Seat.BOTTOM_RIGHT, new GridArea(rings + 9, rings + 9, 6, 6));
        castles.put(Seat.BOTTOM_LEFT, new GridArea(rings + 9, rings, 6, 6));
        GridArea center = new GridArea(rings + 6, rings + 6, 3, 3);
        return new Board(level, size, size, track, all, next, arrowTurns, castles, center, EnumSet.copyOf(playing));
    }

    /**
     * Chooses which ordinary squares become stars, question squares and surprise squares (FR-16, FR-17): exact
     * counts, no overlap, neighbours allowed. Question squares are split evenly between easy, medium and hard
     * questions (FR-42).
     *
     * @param level          the level, which knows how many special squares it has
     * @param ordinary       track indices that may become special (starts and arrows excluded)
     * @param rng            random source
     * @param questionLevels filled with the question difficulty of every chosen question square
     * @return the chosen track indices with their special type
     */
    private static Map<Integer, Square.Type> placeSpecialSquares(Game.Level level, List<Integer> ordinary, RandomGenerator rng,
                                                                Map<Integer, Game.Level> questionLevels) {
        Map<Square.Type, Integer> counts = new EnumMap<>(Square.Type.class);
        counts.put(Square.Type.STAR, level.protectionSquares());
        counts.put(Square.Type.QUESTION, level.questionSquares());
        counts.put(Square.Type.SURPRISE, level.surpriseSquares());
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total > ordinary.size()) {
            throw new IllegalArgumentException("Need " + total + " special squares but only "
                    + ordinary.size() + " ordinary squares are available");
        }
        List<Integer> shuffled = new ArrayList<>(ordinary);
        Collections.shuffle(shuffled, rng);
        Map<Integer, Square.Type> specials = new HashMap<>();
        int next = 0;
        for (Map.Entry<Square.Type, Integer> entry : counts.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                specials.put(shuffled.get(next++), entry.getKey());
            }
        }
        List<Integer> questions = new ArrayList<>();
        specials.forEach((index, type) -> {
            if (type == Square.Type.QUESTION) {
                questions.add(index);
            }
        });
        Collections.sort(questions);
        Collections.shuffle(questions, rng);
        for (int i = 0; i < questions.size(); i++) {
            questionLevels.put(questions.get(i), Game.Level.values()[i / level.questionSquaresPerDifficulty()]);
        }
        return specials;
    }

    /**
     * @param rings number of rings around the inner cross
     * @return the top-left start square: the second cell of the inner circuit on Easy, the top-left corner of the
     *         outermost ring on Medium and Hard
     */
    private static GridPosition startPosition(int rings) {
        if (rings == 0) {
            return easyTrackPositions().get(EASY_START_INDEX);
        }
        return new GridPosition(0, 0);
    }

    /**
     * @param o     ring offset from the edge of the grid; {@code o == rings} is the inner circuit
     * @param rings number of rings around the inner cross
     * @return the top-left seat's arrow on that ring
     */
    private static GridPosition arrowPosition(int o, int rings) {
        if (o == rings) {
            GridPosition easy = easyTrackPositions().get(EASY_ARROW_INDEX);
            return new GridPosition(easy.row() + rings, easy.col() + rings);
        }
        return new GridPosition(7 + o, o);
    }

    /**
     * @param o ring offset of an outer ring
     * @return the cell one step inside that ring's top-left arrow, where the piece continues on the next ring
     */
    private static GridPosition entryPosition(int o) {
        return new GridPosition(7 + o, o + 1);
    }

    /**
     * Turns a top-left cell into the same cell of another seat: a quarter turn clockwise per seat after top-left.
     *
     * @param pos  a cell of the top-left seat
     * @param seat the wanted seat
     * @param size cells across the grid
     * @return the matching cell of that seat
     */
    private static GridPosition rotateForSeat(GridPosition pos, Seat seat, int size) {
        GridPosition p = pos;
        for (int i = 0; i < seat.ordinal(); i++) {
            p = new GridPosition(p.col(), size - 1 - p.row());
        }
        return p;
    }

    /**
     * @param o    ring offset from the grid edge
     * @param size cells across the grid
     * @return the cells of that ring, clockwise from its top-left corner
     */
    private static List<GridPosition> ringPositions(int o, int size) {
        int last = size - 1 - o;
        List<GridPosition> cells = new ArrayList<>();
        for (int c = o; c < last; c++) cells.add(new GridPosition(o, c));
        for (int r = o; r < last; r++) cells.add(new GridPosition(r, last));
        for (int c = last; c > o; c--) cells.add(new GridPosition(last, c));
        for (int r = last; r > o; r--) cells.add(new GridPosition(r, o));
        return cells;
    }

    /**
     * @return the 52 cells of the Easy circuit on a 15×15 grid, in clockwise order starting at (6,0)
     */
    private static List<GridPosition> easyTrackPositions() {
        List<GridPosition> cells = new ArrayList<>();
        for (int c = 0; c <= 5; c++) cells.add(new GridPosition(6, c));
        for (int r = 5; r >= 0; r--) cells.add(new GridPosition(r, 6));
        cells.add(new GridPosition(0, 7));
        for (int r = 0; r <= 5; r++) cells.add(new GridPosition(r, 8));
        for (int c = 9; c <= 14; c++) cells.add(new GridPosition(6, c));
        cells.add(new GridPosition(7, 14));
        for (int c = 14; c >= 9; c--) cells.add(new GridPosition(8, c));
        for (int r = 9; r <= 14; r++) cells.add(new GridPosition(r, 8));
        cells.add(new GridPosition(14, 7));
        for (int r = 14; r >= 9; r--) cells.add(new GridPosition(r, 6));
        for (int c = 5; c >= 0; c--) cells.add(new GridPosition(8, c));
        cells.add(new GridPosition(7, 0));
        return cells;
    }

    /**
     * The home lane of a seat (5 lane squares and the final home square), running from the inner arrow toward the
     * center.
     *
     * @param seat   the seat
     * @param rings  number of rings around the inner cross
     * @param size   cells across the grid
     * @param nextId the next free square id, increased for every new square
     * @return the lane squares in walking order
     */
    private static List<Square> homeLane(Seat seat, int rings, int size, int[] nextId) {
        int laneSquares = Board.HOME_LANE_LENGTH - 1;
        List<Square> lane = new ArrayList<>();
        for (int k = 1; k <= Board.HOME_LANE_LENGTH; k++) {
            GridPosition pos = rotateForSeat(new GridPosition(7 + rings, k + rings), seat, size);
            Square.Type type = k <= laneSquares ? Square.Type.HOME_LANE : Square.Type.FINISH;
            lane.add(Square.create(nextId[0]++, type, pos, seat, null));
        }
        return lane;
    }

    /**
     * A fixed corner of the board: its castle, start square, arrow and home lane (FR-15). Declaration order is clockwise,
     * starting top-left, which is also the turn order (FR-13, FR-19). A seat says nothing about a player's color; colors
     * are chosen freely ({@code Player.Color}).
     */
    public enum Seat {
        TOP_LEFT("Top-left"),
        TOP_RIGHT("Top-right"),
        BOTTOM_RIGHT("Bottom-right"),
        BOTTOM_LEFT("Bottom-left");

        private final String displayName;

        /**
         * @param displayName human-readable corner name
         */
        Seat(String displayName) {
            this.displayName = displayName;
        }

        /**
         * @return human-readable corner name
         */
        public String displayName() {
            return displayName;
        }

        /**
         * @return the diagonally opposite corner
         */
        public Seat opposite() {
            Seat[] all = values();
            return all[(ordinal() + all.length / 2) % all.length];
        }

        /**
         * Seats used for a number of players, in clockwise order. Two players always sit in opposite corners (TD-04).
         *
         * @param players number of players, 2–4
         * @return the seat of player 1, player 2, …
         * @throws IllegalArgumentException for an unsupported player count
         */
        public static List<Seat> forPlayers(int players) {
            return switch (players) {
                case 2 -> List.of(TOP_LEFT, BOTTOM_RIGHT);
                case 3 -> List.of(TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT);
                case 4 -> List.of(values());
                default -> throw new IllegalArgumentException("Unsupported player count: " + players);
            };
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
