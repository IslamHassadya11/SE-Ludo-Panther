package model;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the saved Settings choices (NFR-39).
 */
class SettingsTest {

    @TempDir
    Path folder;

    /**
     * Without a file every choice answers with its default: switches are on and texts give the fallback.
     */
    @Test
    void missingFileGivesDefaults() {
        Settings settings = Settings.load(folder);
        assertFalse(settings.contains(Settings.LANGUAGE));
        assertEquals("ENGLISH", settings.get(Settings.LANGUAGE, "ENGLISH"));
        assertTrue(settings.isOn(Settings.MUSIC));
    }

    /**
     * Saved choices come back the next time the settings are loaded.
     */
    @Test
    void savedChoicesComeBack() {
        Settings settings = Settings.load(folder);
        settings.set(Settings.LANGUAGE, "HEBREW");
        settings.set(Settings.TURN_TIMER, false);
        assertTrue(settings.save());

        Settings again = Settings.load(folder);
        assertEquals("HEBREW", again.get(Settings.LANGUAGE, "ENGLISH"));
        assertFalse(again.isOn(Settings.TURN_TIMER));
        assertTrue(again.isOn(Settings.SOUND_EFFECTS));
    }

    /**
     * A damaged file is not an error; the defaults are used.
     */
    @Test
    void damagedFileGivesDefaults() throws Exception {
        Files.write(folder.resolve(Settings.FILE_NAME), "music=\\u00zz".getBytes(StandardCharsets.ISO_8859_1));
        Settings settings = Settings.load(folder);
        assertTrue(settings.isOn(Settings.MUSIC));
    }
}
