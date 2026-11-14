package model;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The Settings choices of the players (appearance, language, turn timer, sound, music and the comic effects),
 * remembered between runs in {@code data/settings.properties} (NFR-39). The model only stores the choices as
 * named values; the controller reads them and applies them to the screens.
 */
public final class Settings {

    /** File in the data folder that remembers the choices. */
    public static final String FILE_NAME = "settings.properties";
    public static final String APPEARANCE = "appearance";
    public static final String LANGUAGE = "language";
    public static final String TURN_TIMER = "turnTimer";
    public static final String SOUND_EFFECTS = "soundEffects";
    public static final String MUSIC = "music";
    public static final String COMIC_EFFECTS = "effects";
    public static final String BACKGROUND = "background";

    private final Path file;
    private final Properties values = new Properties();

    /**
     * Use {@link #load(Path)}.
     *
     * @param file the settings file
     */
    private Settings(Path file) {
        this.file = file;
    }

    /**
     * Reads the saved choices. A missing or damaged file is not an error: the settings are then empty and every
     * question about them answers with its default.
     *
     * @param folder the data folder
     * @return the settings of the last run
     */
    public static Settings load(Path folder) {
        Settings settings = new Settings(folder.resolve(FILE_NAME));
        if (Files.exists(settings.file)) {
            try (Reader reader = Files.newBufferedReader(settings.file, StandardCharsets.UTF_8)) {
                settings.values.load(reader);
            } catch (IOException | IllegalArgumentException ignored) {
                settings.values.clear();
            }
        }
        return settings;
    }

    /**
     * @param key one of the names above
     * @return {@code true} if a choice was saved under this name
     */
    public boolean contains(String key) {
        return values.getProperty(key) != null;
    }

    /**
     * @param key      one of the names above
     * @param fallback the answer if nothing was saved
     * @return the saved choice
     */
    public String get(String key, String fallback) {
        return values.getProperty(key, fallback);
    }

    /**
     * @param key one of the names above
     * @return {@code false} only if the choice was saved as switched off (everything is on by default)
     */
    public boolean isOn(String key) {
        return !"false".equals(values.getProperty(key));
    }

    /**
     * @param key   one of the names above
     * @param value the choice to remember
     */
    public void set(String key, String value) {
        values.setProperty(key, value);
    }

    /**
     * @param key one of the names above
     * @param on  the choice to remember
     */
    public void set(String key, boolean on) {
        values.setProperty(key, String.valueOf(on));
    }

    /**
     * Writes the choices to the data folder.
     *
     * @return {@code false} if they could not be written (they then last until the program is closed)
     */
    public boolean save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                values.store(writer, "Panther Ludo settings");
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Name of the folder next to the program that holds the admin account and the game history. */
    private static final String DATA_FOLDER = "data";
    /** Name of the folder of the ready-to-run game (the JAR with its launchers). */
    private static final String APP_FOLDER = "ludoAPP";

    /**
     * @return the {@code data} folder next to the program (not created here)
     */
    public static Path dataDirectory() {
        return programDirectory().resolve(DATA_FOLDER);
    }

    /**
     * The folder whose {@code data} folder keeps the history, the questions, the settings and the admin account
     * (NFR-39):
     * <ul>
     *     <li>when the game runs from its JAR: the folder of the JAR, so the game and its data travel together;</li>
     *     <li>when it runs from compiled classes (in the IDE or with Maven): the project's {@code ludoAPP} folder, so
     *         every way of starting the game reads and saves the same games;</li>
     *     <li>otherwise the current working directory.</li>
     * </ul>
     *
     * @return the program folder
     */
    public static Path programDirectory() {
        try {
            Path location = Path.of(Settings.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                return location.getParent();
            }
            for (Path folder = location; folder != null; folder = folder.getParent()) {
                Path app = folder.resolve(APP_FOLDER);
                if (Files.isDirectory(app)) {
                    return app;
                }
            }
        } catch (URISyntaxException | SecurityException | NullPointerException | IllegalArgumentException ignored) {
            // Fall back to the working directory.
        }
        Path working = Path.of("").toAbsolutePath();
        return Files.isDirectory(working.resolve(APP_FOLDER)) ? working.resolve(APP_FOLDER) : working;
    }
}
