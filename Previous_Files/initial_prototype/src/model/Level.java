package model;

/**
 * Difficulty level chosen before a match starts; fixed for the whole match (FR-09).
 */
public enum Level {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard");

    private final String displayName;
    Level(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return human-readable level name
     */
    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
