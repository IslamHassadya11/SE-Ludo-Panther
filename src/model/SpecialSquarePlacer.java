package model;

import java.util.*;
import java.util.random.RandomGenerator;

/**
 * Randomly assigns special square types (star, question, surprise) to ordinary shared-track positions
 * at the start of every new game (FR-16, FR-17): exact counts, no overlap, adjacency allowed.
 */
public final class SpecialSquarePlacer {

    /**
     * Only static helpers; no objects of this class are created.
     */
    private SpecialSquarePlacer() {
    }

    /**
     * @param candidates track indices that may receive a special type (fixed squares already excluded)
     * @param counts     how many squares of each type to place; iterated in map order for determinism
     * @param rng        random source; a seeded generator gives a reproducible board
     * @return map from chosen track index to its special type
     * @throws IllegalArgumentException if there are not enough candidates
     */
    public static Map<Integer, SquareType> place(List<Integer> candidates, Map<SquareType, Integer> counts,
                                                 RandomGenerator rng) {
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total > candidates.size()) {
            throw new IllegalArgumentException("Need " + total + " special squares but only "
                    + candidates.size() + " ordinary squares are available");
        }
        List<Integer> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, rng);
        Map<Integer, SquareType> placed = new HashMap<>();
        int next = 0;
        for (Map.Entry<SquareType, Integer> entry : counts.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                placed.put(shuffled.get(next++), entry.getKey());
            }
        }
        return placed;
    }
}
