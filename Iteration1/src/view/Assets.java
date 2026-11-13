package view;


import model.*;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.List; // java.util.List, not java.awt.List
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Everything the game shows or plays besides its drawings: the words on the screens in every language, the music and
 * sound effects, and the pictures. Each kind has its own nested class.
 */
public final class Assets {

    /**
     * Only nested classes; no objects of this class are created.
     */
    private Assets() {
    }

    /**
     * Every text the screens show, in the chosen language (FR-06): English, Hebrew or Arabic. The texts are kept in one
     * file per language next to this class ({@code texts_en.properties}, {@code texts_he.properties}, ...). A text
     * missing in a translation falls back to English, so a screen never shows an empty label.
     * <p>Hebrew and Arabic are written right to left: {@link #orientation()} tells the window to mirror its layout. The
     * board itself and the questions written by the admin are not changed.
     * <p>Values are put into a text with {@code {0}}, {@code {1}}, ... (for example a player's name).
     * <p>It also builds the sentences of the game screen from the state of the model: prompts, roll and move
     * descriptions, notices, questions, surprises and status lines.
     */
    public static final class Texts {

        private static final Properties ENGLISH = load(Language.ENGLISH);
        private static Language language = Language.ENGLISH;
        private static Properties current = ENGLISH;

        /**
         * Only static helpers; no objects of this class are created.
         */
        private Texts() {
        }

        /**
         * Changes the language of every screen built from now on.
         *
         * @param newLanguage the language to use
         */
        public static void setLanguage(Language newLanguage) {
            language = Objects.requireNonNull(newLanguage);
            current = newLanguage == Language.ENGLISH ? ENGLISH : load(newLanguage);
        }

        /**
         * @return the language in use
         */
        public static Language language() {
            return language;
        }

        /**
         * @return right to left for Hebrew and Arabic, left to right otherwise
         */
        public static ComponentOrientation orientation() {
            return language.isRightToLeft() ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT;
        }

        /**
         * Lays out a window or panel in the reading direction of the language: mirrored for Hebrew and Arabic. Swing
         * measures Hebrew and Arabic text a few pixels too narrow, so in those languages every label also gets a little
         * room on both sides; otherwise its first letter could be cut off.
         *
         * @param root the window or panel to lay out
         */
        public static void applyOrientation(Component root) {
            root.applyComponentOrientation(orientation());
            if (language.isRightToLeft()) {
                padLabels(root);
            }
        }

        /**
         * Gives every label without a border 6 pixels of room left and right.
         */
        private static void padLabels(Component component) {
            if (component instanceof JLabel label && label.getBorder() == null) {
                label.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            }
            if (component instanceof Container container) {
                for (Component child : container.getComponents()) {
                    padLabels(child);
                }
            }
            if (component instanceof RootPaneContainer window) {
                padLabels(window.getContentPane());
            }
        }

        /**
         * @param key    the text's key, e.g. {@code menu.newGame}
         * @param values values for {@code {0}}, {@code {1}}, ...
         * @return the text in the current language (English if it has no translation, the key if it does not exist)
         */
        public static String get(String key, Object... values) {
            String text = current.getProperty(key);
            if (text == null) {
                text = ENGLISH.getProperty(key, key);
            }
            for (int i = 0; i < values.length; i++) {
                text = text.replace("{" + i + "}", String.valueOf(values[i]));
            }
            if (language.isRightToLeft()) {
                // Every line starts with an invisible right-to-left mark, so a line that begins with a Latin name or a
                // number is still laid out right to left.
                text = directionMark() + text.replace("\n", "\n" + directionMark());
            }
            return text;
        }

        /**
         * @param level a level
         * @return its name, e.g. "Medium"
         */
        public static String level(Game.Level level) {
            return get("level." + level.name());
        }

        /**
         * @param key a text key
         * @return {@code true} if the current language or English has a text for it
         */
        public static boolean contains(String key) {
            return current.containsKey(key) || ENGLISH.containsKey(key);
        }

        /**
         * @param seat a corner
         * @return its name, e.g. "Top-left"
         */
        public static String seat(Board.Seat seat) {
            return get("seat." + seat.name());
        }

        /**
         * @param color a piece color
         * @return the color's name; a custom color shows its RGB code
         */
        public static String color(Player.Color color) {
            String key = "color." + color.name();
            if (current.containsKey(key) || ENGLISH.containsKey(key)) {
                return get(key);
            }
            return get("color.custom", String.format(Locale.ROOT, "#%06X", color.rgb()));
        }

        /**
         * @param topic a question topic
         * @return its name, e.g. "Design patterns"
         */
        public static String topic(Question.Topic topic) {
            return get("topic." + topic.name());
        }

        /**
         * @param player a player
         * @return "name (color)", so players are never told apart by color alone (NFR-05)
         */
        public static String nameAndColor(Player player) {
            return get("player.nameAndColor", player.name(), color(player.color()));
        }

        /**
         * @param problem a setup problem found by the model
         * @return the message in the current language
         */
        public static String problem(Player.SetupProblem problem) {
            Object[] values = problem.values().stream()
                    .map(v -> v instanceof Player.Color c ? color(c) : v)
                    .toArray();
            return get("setup.problem." + problem.kind().name(), values);
        }

        /**
         * @param n a place number
         * @return the place as an ordinal, e.g. "1st"
         */
        public static String ordinal(int n) {
            String key = "ordinal." + n;
            return current.containsKey(key) || ENGLISH.containsKey(key) ? get(key) : get("ordinal.other", n);
        }

        /**
         * Makes a line start in the reading direction of the language. Without it, a Hebrew or Arabic sentence that begins
         * with a Latin name (for example "Islam, are you still there?") would be laid out left to right and its
         * punctuation would end up on the wrong side.
         *
         * @param text one line of text
         * @return the text, starting with an invisible right-to-left mark in Hebrew and Arabic
         */
        public static String directional(String text) {
            return directionMark() + text;
        }

        /**
         * @return the invisible right-to-left mark (U+200F) in Hebrew and Arabic, otherwise nothing
         */
        private static String directionMark() {
            return language.isRightToLeft() ? "‏" : "";
        }

        /**
         * Reads the text file of one language.
         */
        private static Properties load(Language language) {
            Properties texts = new Properties();
            String file = "texts_" + language.code() + ".properties";
            try (InputStream in = Texts.class.getResourceAsStream(file)) {
                if (in != null) {
                    texts.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // Missing translation: English is used for every text.
            }
            return texts;
        }

        /**
         * The languages of the program (FR-06).
         */
        public enum Language {
            ENGLISH("en", "English", false),
            HEBREW("he", "עברית", true),
            ARABIC("ar", "العربية", true);

            private final String code;
            private final String nativeName;
            private final boolean rightToLeft;

            /**
             * @param code        language code of the text file
             * @param nativeName  the language's name in that language
             * @param rightToLeft {@code true} for Hebrew and Arabic
             */
            Language(String code, String nativeName, boolean rightToLeft) {
                this.code = code;
                this.nativeName = nativeName;
                this.rightToLeft = rightToLeft;
            }

            /**
             * @return the ISO 639 code, e.g. {@code he}
             */
            public String code() {
                return code;
            }

            /**
             * @return the language's name in that language, as shown on the Settings screen
             */
            public String nativeName() {
                return nativeName;
            }

            /**
             * @return {@code true} for Hebrew and Arabic
             */
            public boolean isRightToLeft() {
                return rightToLeft;
            }
        }



        /**
         * @param first the randomly chosen first player (FR-13)
         * @return opening prompt
         */
        public static String firstPlayer(Player first) {
            return get("game.firstPlayer", first.name());
        }

        /**
         * @return prompt while the dice tumble
         */
        public static String rolling() {
            return get("game.rolling");
        }

        /**
         * @param roll           the roll
         * @param multiplierName name of the multiplier die (empty on Easy)
         * @return the roll and the resulting steps (FR-22, FR-23)
         */
        public static String roll(Dice.Roll roll, String multiplierName) {
            if (!roll.hasMultiplier()) {
                return get("game.rolled", roll.step());
            }
            return get("game.rolledMultiplier", roll.step(), multiplierName, roll.multiplier(), roll.trackSteps(),
                    roll.homeLaneSteps());
        }

        /**
         * @param moves the legal moves
         * @return what the player should do now (FR-31, FR-36); with one possible move, that it plays by itself
         *         (TD-05)
         */
        public static String choices(List<Move> moves) {
            if (moves.size() == 1) {
                return get("game.forced");
            }
            StringBuilder text = new StringBuilder(get("game.clickPanther"));
            if (moves.stream().anyMatch(m -> m.kind() == Move.Kind.RELEASE)) {
                text.append(' ').append(get("game.sixReleases"));
            }
            if (moves.stream().anyMatch(m -> m.kind() == Move.Kind.STOP_BEFORE_BLOCK)) {
                text.append(' ').append(get("game.stopBeforeBlock"));
            }
            return text.toString();
        }

        /**
         * @param result  the applied move
         * @param ranking current ranking (for the place of a player who just finished)
         * @return what happened: release, steps, effects, captures, finishing (FR-33, FR-41, FR-43, FR-48–51)
         */
        public static String move(Move.Result result, List<Player> ranking) {
            Move move = result.move();
            String name = move.piece().owner().name();
            int distance = Math.abs(move.to() - move.from());
            StringBuilder text = new StringBuilder(switch (move.kind()) {
                case RELEASE -> get("move.release", name);
                case STOP_BEFORE_BLOCK -> get("move.stopBeforeBlock", name);
                case FULL -> distance == 1 ? get("move.full1", name) : get("move.full", name, distance);
                case QUESTION_FORWARD -> move.changesPosition() ? get("move.questionForward", name, distance)
                        : get("move.cannotMove", name);
                case QUESTION_BACK -> move.changesPosition() ? get("move.questionBack", name, distance)
                        : get("move.cannotMove", name);
                case SURPRISE_BACK -> move.changesPosition() ? get("move.surpriseBack", name, distance)
                        : get("move.cannotMove", name);
                case JUMP_TO_FINISH -> get("move.jump", name);
                case TO_CASTLE -> get("move.toCastle", name);
            });
            for (Piece captured : result.captured()) {
                text.append(' ').append(get("move.captured", captured.owner().name()));
            }
            if (result.playerFinished()) {
                text.append(' ').append(get("move.playerFinished", name,
                        ordinal(ranking.indexOf(move.piece().owner()) + 1)));
            } else if (result.pieceFinished()) {
                text.append(' ').append(get("move.pieceFinished"));
            }
            return text.toString();
        }

        /**
         * @param notice why a roll could not be used
         * @return the notice text (FR-28, FR-29, FR-52), with the exact reason and who plays next
         */
        public static String notice(Game.TurnNotice notice) {
            String name = notice.player().name();
            String text = switch (notice.kind()) {
                case THIRD_SIX -> get("notice.thirdSix", name);
                case LOST_TURN -> get("notice.lostTurn", name);
                case NO_LEGAL_MOVE -> get("notice.noMove", name) + " " + reasons(notice.reasons());
            };
            String next = notice.rollsAgain() ? get("notice.rollsAgain", name)
                    : get("notice.next", notice.nextPlayer().name());
            return text + " " + next;
        }

        /**
         * A short, plain line for the game log, e.g. "Dana rolled 3 and can't move. Panthers in the castle need a 6 to
         * come out."
         *
         * @param notice a notice
         * @param step   the step die of the roll that ended the turn
         * @return the log line
         */
        public static String noticeLog(Game.TurnNotice notice, int step) {
            String name = notice.player().name();
            return switch (notice.kind()) {
                case THIRD_SIX -> get("log.text.thirdSix", name);
                case LOST_TURN -> get("log.text.lostTurn", name);
                case NO_LEGAL_MOVE -> get("log.text.noMove", name, step, reasons(notice.reasons()));
            };
        }

        /**
         * @param notice a notice
         * @return its tag in the game log: NO MOVE, 3 SIXES or SKIPPED
         */
        public static String noticeTag(Game.TurnNotice notice) {
            return switch (notice.kind()) {
                case THIRD_SIX -> "SIXES";
                case LOST_TURN -> "SKIP";
                case NO_LEGAL_MOVE -> "NOMOVE";
            };
        }

        /**
         * @param notice a notice
         * @return its heading for the message in the middle of the board, e.g. "Dana can't move"
         */
        public static String noticeTitle(Game.TurnNotice notice) {
            return get("notice.title." + notice.kind().name(), notice.player().name());
        }

        /**
         * @param notice a notice
         * @return the explanation under the heading: each reason on its own line (FR-29), then who plays next
         */
        public static String noticeBody(Game.TurnNotice notice) {
            String why = switch (notice.kind()) {
                case NO_LEGAL_MOVE -> reasons(notice.reasons(), "\n");
                case THIRD_SIX -> get("notice.body.THIRD_SIX", notice.player().name());
                case LOST_TURN -> get("notice.body.LOST_TURN");
            };
            String next = notice.rollsAgain() ? get("notice.rollsAgain", notice.player().name())
                    : get("notice.next", notice.nextPlayer().name());
            return why + "\n" + next;
        }

        /**
         * Explains in one or two sentences why no piece can move (FR-29).
         */
        private static String reasons(Set<Move.NoMoveReason> reasons) {
            return reasons(reasons, " ");
        }

        /**
         * The reasons why no piece can move, separated by the given text.
         */
        private static String reasons(Set<Move.NoMoveReason> reasons, String separator) {
            StringJoiner joiner = new StringJoiner(separator);
            for (Move.NoMoveReason reason : Move.NoMoveReason.values()) {
                if (reasons.contains(reason)) {
                    joiner.add(get("notice.reason." + reason.name()));
                }
            }
            return joiner.toString();
        }

        /**
         * @param player    the player to roll
         * @param extraRoll {@code true} after a 6 or a surprise (FR-27)
         * @param bonus     {@code true} if it is the surprise extra roll with the UltraBoost die (FR-53)
         * @return roll prompt
         */
        public static String turnStarted(Player player, boolean extraRoll, boolean bonus) {
            if (bonus) {
                return get("game.bonusRoll", player.name());
            }
            return get(extraRoll ? "game.rollAgain" : "game.yourTurn", player.name());
        }

        /**
         * @param player the player who must act
         * @return the warning shown after 30 idle seconds (FR-30)
         */
        public static String timeWarning(Player player) {
            return get("game.timeWarning", player.name(), Game.TURN_WARNING_SECONDS);
        }

        /**
         * @param player the player whose time ran out
         * @return the message shown while the computer plays for them (TD-15)
         */
        public static String autoPlay(Player player) {
            return get("game.autoPlay", player.name());
        }

        /**
         * @param player   the player who must answer
         * @param question the question
         * @return the prompt while the question is shown (FR-42)
         */
        public static String questionAsked(Player player, Question question) {
            return get("game.question", player.name(), level(question.level()), question.steps());
        }

        /**
         * @param answer the given answer
         * @return the result line (FR-44)
         */
        public static String answered(Game.Answer answer) {
            int steps = answer.question().steps();
            return get(answer.wasCorrect() ? "question.right" : "question.wrong", answer.player().name(), steps);
        }

        /**
         * @param event a surprise event
         * @return its description (FR-47–53)
         */
        public static String surprise(Game.SurpriseEvent event) {
            String key = "surprise." + event.surprise().name();
            if (event.cancelled()) {
                key = "surprise.OPPONENT_BACK.cancelled";
            }
            return get(key, event.player().name(), Surprise.BACK_STEPS);
        }

        /**
         * @param surprise a surprise event
         * @return its short title
         */
        public static String surpriseTitle(Surprise surprise) {
            return get("surprise.title." + surprise.name());
        }

        /**
         * @param player the player who chooses
         * @return prompt for the "move an opponent back" surprise (FR-49)
         */
        public static String chooseVictim(Player player) {
            return get("game.chooseVictim", player.name());
        }

        /**
         * @param victim   the chosen opponent piece
         * @param stepRoll the step die
         * @param steps    squares back
         * @return what the step die decided (FR-49)
         */
        public static String victimRolled(Piece victim, int stepRoll, int steps) {
            return get("game.victimRolled", stepRoll, victim.owner().name(), steps);
        }

        /**
         * @return prompt when the match has ended
         */
        public static String gameOver() {
            return get("game.over");
        }

        /**
         * @param game the match
         * @param idle {@code false} while dice or pieces animate
         * @return short status line under the current player's name
         */
        public static String statusLine(Game.State game, boolean idle) {
            if (game.isOver()) {
                return get("status.over");
            }
            if (!idle) {
                return "...";
            }
            return switch (game.phase()) {
                case AWAITING_ROLL -> get(game.nextRollIsBonus() ? "status.bonusRoll" : "status.roll");
                case AWAITING_MOVE -> get(game.legalMoves().size() == 1 ? "status.forced" : "status.pick");
                case AWAITING_ANSWER, SHOWING_ANSWER -> get("status.question");
                case SHOWING_SURPRISE -> get("status.surprise");
                case CHOOSING_VICTIM -> get("status.victim");
                case SHOWING_NOTICE -> get("status.ending");
                case GAME_OVER -> get("status.over");
            };
        }


        // ------------------------------------------------------------------ time formats (FR-04)

        /**
         * @param duration a duration
         * @return {@code HH:MM:SS} (FR-04)
         */
        public static String formatDuration(Duration duration) {
            long seconds = Math.max(0, duration.getSeconds());
            return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
        }

        /**
         * @param seconds whole seconds
         * @return {@code M:SS}, used for the turn countdown
         */
        public static String minutesSeconds(int seconds) {
            int s = Math.max(0, seconds);
            return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
        }
    }

    /**
     * Plays the sound effects and the background music of the game. Both can be switched on and off in Settings; the
     * controller remembers the choice.
     * <ul>
     *     <li>Sound effects are made by {@link Synth}. They are prepared once in the background when the program starts;
     *         a few copies of each are kept, so the same effect can sound twice at the same time.</li>
     *     <li>The music is the prototype's own: "Night Panther", a calm theme on the menus, and "Panther Run", a lively
     *         theme during a match. Both are made by {@link Synth} too, so no recording of anyone else is used. They loop
     *         without end; the game-over screen has no music, so the fanfare is heard.</li>
     *     <li>A computer without a sound device simply stays silent: sound never stops the game.</li>
     * </ul>
     * Only called from the Swing event thread; the preparing happens on one background thread.
     */
    public static final class Sound {

        /** Loudness of the sound effects, 0–1. */
        private static final double EFFECTS_VOLUME = 0.7;
        /** The music, made once when it is first needed. */
        private static final Map<Music, byte[]> SONGS = new ConcurrentHashMap<>();
        /** Loudness of the music, quieter so the sound effects stay clear. */
        private static final double MUSIC_VOLUME = 0.55;
        /** Copies of each effect that can play at the same time. */
        private static final int COPIES = 3;

        private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "sound-loader");
            thread.setDaemon(true);
            return thread;
        });
        private static final Map<Effect, Clip[]> EFFECTS = new ConcurrentHashMap<>();
        private static final Map<Effect, Integer> NEXT_COPY = new EnumMap<>(Effect.class);

        private static volatile boolean effectsOn = true;
        private static volatile boolean musicOn = true;
        /** The music of the current screen, or {@code null} for silence. */
        private static volatile Music wanted;
        /** The music that is playing, or {@code null}. */
        private static Streamer playing;
        /** {@code false} once it is clear that this computer cannot play sound. */
        private static volatile boolean available = true;

        /**
         * Only static methods; no objects of this class are created.
         */
        private Sound() {
        }

        /**
         * The two themes of the game.
         */
        public enum Music {
            /** "Night Panther": the calm theme of the menus. */
            MENU,
            /** "Panther Run": the lively theme of a match. */
            GAME
        }

        /**
         * The short sounds of the game.
         */
        public enum Effect {
            /** A button or choice is pressed. */
            CLICK,
            /** The dice are rolled. */
            DICE,
            /** A panther moves. */
            MOVE,
            /** A panther leaves its castle. */
            RELEASE,
            /** A panther is captured or sent back to its castle. */
            CAPTURE,
            /** A panther reaches home. */
            HOME,
            /** A right answer. */
            RIGHT,
            /** A wrong answer. */
            WRONG,
            /** A surprise square. */
            SURPRISE,
            /** A turn ends without a move (can't move, third 6, lost turn). */
            NOTICE,
            /** A new turn or roll begins. */
            TURN,
            /** 30 seconds without a roll or move. */
            WARNING,
            /** Each of the last 10 seconds of the turn timer. */
            TICK,
            /** The game is over. */
            WIN
        }

        /**
         * Starts preparing the sound effects in the background, so the first one plays without delay.
         */
        public static void preload() {
            LOADER.execute(() -> {
                for (Effect effect : Effect.values()) {
                    if (!available) {
                        return;
                    }
                    Clip[] copies = new Clip[COPIES];
                    byte[] data = Synth.toPcmBytes(Synth.renderEffect(effect), EFFECTS_VOLUME);
                    for (int i = 0; i < COPIES; i++) {
                        copies[i] = open(data);
                    }
                    if (copies[0] != null) {
                        EFFECTS.put(effect, copies);
                    }
                }
            });
        }

        /**
         * Plays a sound effect if sound effects are on.
         *
         * @param effect the effect
         */
        public static void play(Effect effect) {
            if (!effectsOn) {
                return;
            }
            Clip[] copies = EFFECTS.get(effect);
            if (copies == null) {
                return;
            }
            int index = NEXT_COPY.merge(effect, 1, Integer::sum) % COPIES;
            Clip clip = copies[index];
            if (clip == null) {
                return;
            }
            try {
                clip.stop();
                clip.setFramePosition(0);
                clip.start();
            } catch (RuntimeException ignored) {
                // A sound that cannot play is simply not heard.
            }
        }

        /**
         * Sets the music of the current screen: it starts (or keeps playing) if music is on.
         *
         * @param music the theme of this screen, or {@code null} for silence
         */
        public static void playMusic(Music music) {
            wanted = music;
            LOADER.execute(Sound::updateMusic);
        }

        /**
         * @param on {@code true} to hear sound effects
         */
        public static void setEffectsOn(boolean on) {
            effectsOn = on;
        }

        /**
         * @return {@code true} if sound effects are on
         */
        public static boolean isEffectsOn() {
            return effectsOn;
        }

        /**
         * Switches the music on (the song starts if the current screen has music) or off (it stops at once).
         *
         * @param on {@code true} to hear music
         */
        public static void setMusicOn(boolean on) {
            musicOn = on;
            LOADER.execute(Sound::updateMusic);
        }

        /**
         * Switches the music on and plays it right now, e.g. from the music button.
         */
        public static void startMusicNow() {
            musicOn = true;
            if (wanted == null) {
                wanted = Music.MENU;
            }
            LOADER.execute(Sound::updateMusic);
        }

        /**
         * @return {@code true} if music is on
         */
        public static boolean isMusicOn() {
            return musicOn;
        }

        /**
         * Runs on the loader thread: plays the theme of the current screen when music is on, and stops it otherwise.
         */
        private static void updateMusic() {
            Music target = musicOn && available ? wanted : null;
            if (playing != null && playing.music == target && playing.isAlive()) {
                return;
            }
            if (playing != null) {
                playing.stopPlaying();
                playing = null;
            }
            if (target != null) {
                byte[] data = SONGS.computeIfAbsent(target, m -> Synth.toPcmBytes(Synth.renderSong(m), MUSIC_VOLUME));
                playing = new Streamer(target, data);
                playing.start();
            }
        }

        /**
         * Opens sound data as a clip, or returns {@code null} if this computer cannot play sound.
         */
        private static Clip open(byte[] data) {
            if (!available) {
                return null;
            }
            try {
                AudioFormat format = new AudioFormat(Synth.RATE, 16, 1, true, false);
                AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(data), format,
                        data.length / format.getFrameSize());
                Clip clip = AudioSystem.getClip();
                clip.open(stream);
                return clip;
            } catch (LineUnavailableException | IllegalArgumentException | SecurityException
                     | java.io.IOException e) {
                available = false;
                return null;
            }
        }

        /**
         * Plays one theme again and again on its own thread, a small piece at a time, so it can stop at once.
         */
        private static final class Streamer extends Thread {

            private final Music music;
            private final byte[] data;
            private volatile boolean running = true;
            private volatile SourceDataLine line;

            /**
             * @param music the theme
             * @param data  its sound data
             */
            Streamer(Music music, byte[] data) {
                super("music");
                setDaemon(true);
                this.music = music;
                this.data = data;
            }

            /**
             * Stops the music at once.
             */
            void stopPlaying() {
                running = false;
                SourceDataLine l = line;
                if (l != null) {
                    l.stop();
                    l.flush();
                }
            }

            @Override
            public void run() {
                AudioFormat format = new AudioFormat(Synth.RATE, 16, 1, true, false);
                try (SourceDataLine out = AudioSystem.getSourceDataLine(format)) {
                    out.open(format, 8820);
                    line = out;
                    out.start();
                    while (running) {
                        for (int at = 0; at < data.length && running; at += 4410) {
                            out.write(data, at, Math.min(4410, data.length - at));
                        }
                    }
                } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
                    // No sound card: the game stays silent.
                }
            }
        }

        /**
         * Makes every sound effect of the game from scratch: each one is computed note by note from sine, triangle and
         * soft square waves plus a little noise, so no recording of anyone else is used.
         * <p>Everything here is a pure function from a sound to an array of samples between -1 and 1, so it can be tested
         * without a sound card. {@link Sound} plays the result.
         */
        static final class Synth {

            /** Samples per second; enough for these simple sounds. */
            static final float RATE = 22_050f;

            /**
             * Only static helpers; no objects of this class are created.
             */
            private Synth() {
            }

            /** The shape of a sound wave. */
            private enum Wave {
                /** Pure and soft (bells, chimes). */
                SINE,
                /** Mellow, a little hollow (bass, plucks). */
                TRIANGLE,
                /** Bright, game-like (melody), made of only a few harmonics so it is not harsh. */
                SQUARE
            }

            // ------------------------------------------------------------------ sound effects

            /**
             * @param effect a sound effect
             * @return its samples, between -1 and 1
             */
            static float[] renderEffect(Sound.Effect effect) {
                return switch (effect) {
                    case CLICK -> {
                        float[] b = buffer(0.06);
                        note(b, 0, 0.05, 1250, 0.5, Wave.SINE, 0.002, 0.03, 40, 0, 0);
                        yield b;
                    }
                    case DICE -> {
                        // Small wooden knocks that get quieter, like dice tumbling on a table.
                        float[] b = buffer(0.7);
                        Random random = new Random(7);
                        double t = 0;
                        for (int i = 0; i < 7; i++) {
                            double amp = 0.55 * Math.pow(0.82, i);
                            noise(b, t, 0.025, amp, 120, random);
                            note(b, t, 0.05, 260 + random.nextInt(220), amp * 0.8, Wave.TRIANGLE, 0.001, 0.02, 60, 0, 0);
                            t += 0.04 + random.nextDouble() * 0.05;
                        }
                        yield b;
                    }
                    case MOVE -> {
                        float[] b = buffer(0.22);
                        note(b, 0, 0.09, 520, 0.45, Wave.TRIANGLE, 0.003, 0.04, 20, 0, 640);
                        note(b, 0.09, 0.11, 640, 0.4, Wave.TRIANGLE, 0.003, 0.05, 20, 0, 780);
                        yield b;
                    }
                    case RELEASE -> arpeggio(new int[]{72, 76, 79, 84}, 0.07, 0.25, Wave.TRIANGLE, 0.45);
                    case CAPTURE -> {
                        float[] b = buffer(0.45);
                        note(b, 0, 0.38, 760, 0.42, Wave.SQUARE, 0.003, 0.1, 4, 0, 110);
                        noise(b, 0, 0.12, 0.35, 30, new Random(3));
                        yield b;
                    }
                    case HOME -> {
                        float[] b = arpeggio(new int[]{84, 88, 91, 96}, 0.08, 0.45, Wave.SINE, 0.45);
                        float[] echo = Arrays.copyOf(b, b.length + (int) (0.18 * RATE));
                        int delay = (int) (0.18 * RATE);
                        for (int i = 0; i < b.length; i++) {
                            echo[i + delay] += b[i] * 0.35f;
                        }
                        yield echo;
                    }
                    case RIGHT -> {
                        float[] b = buffer(0.7);
                        note(b, 0, 0.5, frequencyOf(88), 0.45, Wave.SINE, 0.003, 0.3, 5, 0, 0);
                        note(b, 0.12, 0.55, frequencyOf(93), 0.45, Wave.SINE, 0.003, 0.35, 4, 0, 0);
                        yield b;
                    }
                    case WRONG -> {
                        float[] b = buffer(0.55);
                        note(b, 0, 0.2, 220, 0.35, Wave.SQUARE, 0.005, 0.05, 3, 0, 0);
                        note(b, 0.22, 0.3, 185, 0.35, Wave.SQUARE, 0.005, 0.12, 3, 0, 0);
                        yield b;
                    }
                    case SURPRISE -> arpeggio(new int[]{72, 74, 76, 78, 80, 82, 84, 86}, 0.05, 0.3, Wave.SINE, 0.4);
                    case NOTICE -> {
                        float[] b = buffer(0.35);
                        note(b, 0, 0.3, 330, 0.5, Wave.TRIANGLE, 0.004, 0.12, 8, 0, 240);
                        yield b;
                    }
                    case TURN -> {
                        float[] b = buffer(0.5);
                        note(b, 0, 0.45, frequencyOf(79), 0.3, Wave.SINE, 0.004, 0.3, 6, 0, 0);
                        note(b, 0.06, 0.4, frequencyOf(86), 0.25, Wave.SINE, 0.004, 0.3, 6, 0, 0);
                        yield b;
                    }
                    case WARNING -> {
                        float[] b = buffer(0.36);
                        note(b, 0, 0.12, 880, 0.4, Wave.SQUARE, 0.003, 0.03, 0, 0, 0);
                        note(b, 0.2, 0.12, 880, 0.4, Wave.SQUARE, 0.003, 0.03, 0, 0, 0);
                        yield b;
                    }
                    case TICK -> {
                        float[] b = buffer(0.05);
                        note(b, 0, 0.04, 1500, 0.35, Wave.TRIANGLE, 0.001, 0.02, 80, 0, 0);
                        yield b;
                    }
                    case WIN -> {
                        // A short fanfare: a rising C major arpeggio, then the full chord.
                        float[] b = buffer(1.9);
                        int[] run = {60, 64, 67, 72};
                        for (int i = 0; i < run.length; i++) {
                            note(b, i * 0.15, 0.16, frequencyOf(run[i] + 12), 0.35, Wave.SQUARE, 0.004, 0.05, 2, 0, 0);
                        }
                        for (int n : new int[]{72, 76, 79, 84}) {
                            note(b, 0.6, 1.25, frequencyOf(n), 0.22, Wave.TRIANGLE, 0.01, 0.6, 1.2, 0.004, 0);
                        }
                        note(b, 0.6, 1.25, frequencyOf(48), 0.3, Wave.TRIANGLE, 0.01, 0.6, 1.2, 0, 0);
                        yield b;
                    }
                };
            }

            /**
             * Notes played quickly one after another, each ringing on.
             */
            private static float[] arpeggio(int[] notes, double step, double ring, Wave wave, double amp) {
                float[] b = buffer(step * notes.length + ring);
                for (int i = 0; i < notes.length; i++) {
                    note(b, i * step, ring, frequencyOf(notes[i]), amp, wave, 0.003, ring * 0.6, 6, 0, 0);
                }
                return b;
            }

            // ------------------------------------------------------------------ building blocks

            // ------------------------------------------------------------------ music

            /** Rest in a melody. */
            private static final int REST = -1;
            /** The previous melody note goes on. */
            private static final int HOLD = 0;

            /**
             * @param music a theme
             * @return its samples, between -1 and 1; the end joins the start without a click
             */
            static float[] renderSong(Sound.Music music) {
                return switch (music) {
                    case MENU -> nightPanther();
                    case GAME -> pantherRun();
                };
            }

            /**
             * "Night Panther": calm A-minor theme for the menus, 84 beats per minute, 16 bars. Soft chords, a rippling
             * arpeggio, a bass and a dreamy melody.
             */
            private static float[] nightPanther() {
                double bar = 4 * 60.0 / 84;
                int[][] chords = {{57, 60, 64}, {57, 60, 65}, {55, 60, 64}, {55, 59, 62},
                        {57, 60, 64}, {57, 60, 65}, {57, 62, 65}, {56, 59, 64}};
                int[] bass = {45, 41, 48, 43, 45, 41, 38, 40};
                int[][] melody = {
                        {76, HOLD, 74, 72, 69, HOLD, 72, 74},
                        {72, HOLD, HOLD, 69, 65, HOLD, 69, 72},
                        {76, HOLD, 79, 76, 72, HOLD, 74, 76},
                        {74, HOLD, HOLD, 71, 67, HOLD, REST, REST},
                        {69, 72, 76, HOLD, 81, HOLD, 79, 76},
                        {77, HOLD, 76, 74, 72, HOLD, 69, HOLD},
                        {74, HOLD, 77, HOLD, 76, 74, 72, HOLD},
                        {71, HOLD, 68, HOLD, 64, HOLD, REST, REST}};
                int bars = 16;
                float[] b = buffer(bar * bars);
                double eighth = bar / 8;
                for (int i = 0; i < bars; i++) {
                    int c = i % 8;
                    double start = i * bar;
                    for (int n : chords[c]) {
                        note(b, start, bar, frequencyOf(n), 0.07, Wave.SINE, 0.4, 0.5, 0, 0, 0);
                    }
                    note(b, start, bar * 0.48, frequencyOf(bass[c]), 0.22, Wave.TRIANGLE, 0.01, 0.2, 1.5, 0, 0);
                    note(b, start + bar / 2, bar * 0.48, frequencyOf(bass[c]), 0.18, Wave.TRIANGLE, 0.01, 0.2, 1.5, 0, 0);
                    for (int k = 0; k < 8; k++) {
                        int n = chords[c][k % 3] + (k >= 3 && k < 6 ? 12 : 0);
                        note(b, start + k * eighth, eighth * 1.6, frequencyOf(n + 12), 0.06, Wave.TRIANGLE, 0.005, 0.15, 5, 0, 0);
                    }
                    // The melody rests in bars 9-12, so the song breathes before it comes back.
                    if (i < 8 || i >= 12) {
                        melody(b, melody[c], start, eighth, 0, 0.13, Wave.SQUARE, 0.006);
                    }
                }
                return normalize(b, 0.9);
            }

            /**
             * "Panther Run": lively C-major theme for the game screen, 116 beats per minute, 16 bars, with drums, a
             * pulsing bass, chords and a bouncy melody (an octave lower the second time).
             */
            private static float[] pantherRun() {
                double bar = 4 * 60.0 / 116;
                int[][] chords = {{60, 64, 67}, {59, 62, 67}, {60, 64, 69}, {60, 65, 69},
                        {60, 64, 67}, {59, 62, 67}, {60, 65, 69}, {59, 62, 67}};
                int[] bass = {36, 43, 45, 41, 36, 43, 41, 43};
                int[][] melody = {
                        {72, 76, 79, 76, 84, HOLD, 79, HOLD},
                        {79, HOLD, 74, 71, 74, HOLD, 79, HOLD},
                        {81, HOLD, 76, 72, 76, HOLD, 81, 79},
                        {77, HOLD, 72, 69, 72, 74, 76, 77},
                        {79, HOLD, 76, HOLD, 72, 76, 79, 84},
                        {83, HOLD, 79, HOLD, 74, HOLD, 79, 81},
                        {81, 79, 77, 76, 77, HOLD, 81, HOLD},
                        {79, HOLD, HOLD, HOLD, 74, HOLD, 71, HOLD}};
                int bars = 16;
                float[] b = buffer(bar * bars);
                double eighth = bar / 8;
                Random random = new Random(11);
                for (int i = 0; i < bars; i++) {
                    int c = i % 8;
                    double start = i * bar;
                    for (int beat = 0; beat < 4; beat++) {
                        double t = start + beat * bar / 4;
                        if (beat % 2 == 0) {
                            note(b, t, 0.18, 120, 0.5, Wave.SINE, 0.001, 0.08, 18, 0, 45);
                        } else {
                            noise(b, t, 0.14, 0.22, 28, random);
                            note(b, t, 0.08, 190, 0.15, Wave.TRIANGLE, 0.001, 0.05, 30, 0, 0);
                        }
                        for (int h = 0; h < 2; h++) {
                            noise(b, t + h * eighth, 0.03, 0.06, 140, random);
                        }
                        for (int n : chords[c]) {
                            note(b, t + eighth, eighth * 0.9, frequencyOf(n), 0.045, Wave.TRIANGLE, 0.004, 0.05, 6, 0, 0);
                        }
                    }
                    for (int k = 0; k < 8; k++) {
                        int n = bass[c] + (k % 4 == 3 ? 7 : 0);
                        note(b, start + k * eighth, eighth * 0.85, frequencyOf(n), 0.22, Wave.TRIANGLE, 0.004, 0.04, 3, 0, 0);
                    }
                    melody(b, melody[c], start, eighth, i < 8 ? 0 : -12, 0.12, Wave.SQUARE, 0.004);
                }
                return normalize(b, 0.9);
            }

            /**
             * Plays one bar of a melody: each value is a MIDI note, {@link #HOLD} (the note before goes on) or {@link #REST}.
             */
            private static void melody(float[] b, int[] notes, double start, double eighth, int transpose, double amp,
                                       Wave wave, double vibrato) {
                for (int k = 0; k < notes.length; k++) {
                    if (notes[k] <= HOLD) {
                        continue;
                    }
                    int length = 1;
                    while (k + length < notes.length && notes[k + length] == HOLD) {
                        length++;
                    }
                    note(b, start + k * eighth, eighth * length, frequencyOf(notes[k] + transpose), amp, wave, 0.01, 0.06, 1.2,
                            vibrato, 0);
                }
            }

            // ------------------------------------------------------------------ building blocks

            /**
             * Adds one note to the buffer. Samples past the end continue at the start, so loops stay seamless.
             *
             * @param start   start time in seconds
             * @param length  length in seconds
             * @param hz      pitch
             * @param amp     loudness 0–1
             * @param attack  seconds to fade in
             * @param release seconds to fade out at the end
             * @param decay   how fast the note dies away (0 = it holds)
             * @param vibrato depth of a gentle pitch wobble (0 = none)
             * @param endHz   pitch at the end for a slide, or 0 for a steady pitch
             */
            private static void note(float[] b, double start, double length, double hz, double amp, Wave wave,
                                     double attack, double release, double decay, double vibrato, double endHz) {
                int first = (int) (start * RATE);
                int count = (int) (length * RATE);
                double phase = 0;
                for (int i = 0; i < count; i++) {
                    double t = i / RATE;
                    double f = endHz > 0 ? hz + (endHz - hz) * t / length : hz;
                    if (vibrato > 0) {
                        f *= 1 + vibrato * Math.sin(2 * Math.PI * 5.5 * t);
                    }
                    phase += 2 * Math.PI * f / RATE;
                    double env = Math.min(1, t / attack) * Math.min(1, (length - t) / release) * Math.exp(-decay * t);
                    b[(first + i) % b.length] += (float) (amp * env * wave(wave, phase));
                }
            }

            /**
             * Adds a burst of noise (drums, dice knocks).
             */
            private static void noise(float[] b, double start, double length, double amp, double decay, Random random) {
                int first = (int) (start * RATE);
                int count = (int) (length * RATE);
                for (int i = 0; i < count; i++) {
                    double t = i / RATE;
                    b[(first + i) % b.length] += (float) (amp * Math.exp(-decay * t) * (random.nextDouble() * 2 - 1));
                }
            }

            /**
             * One value of a wave at the given phase.
             */
            private static double wave(Wave wave, double x) {
                return switch (wave) {
                    case SINE -> Math.sin(x);
                    case TRIANGLE -> 0.81 * (Math.sin(x) - Math.sin(3 * x) / 9 + Math.sin(5 * x) / 25);
                    case SQUARE -> 0.8 * (Math.sin(x) + Math.sin(3 * x) / 3 + Math.sin(5 * x) / 5 + Math.sin(7 * x) / 7);
                };
            }

            /**
             * @param midi a MIDI note number (60 = middle C)
             * @return its pitch in Hz
             */
            static double frequencyOf(int midi) {
                return 440 * Math.pow(2, (midi - 69) / 12.0);
            }

            /**
             * An empty buffer of the given length.
             */
            private static float[] buffer(double seconds) {
                return new float[Math.max(1, (int) (seconds * RATE))];
            }

            /**
             * Scales the samples so the loudest one reaches the given peak.
             */
            private static float[] normalize(float[] b, double peak) {
                float max = 0;
                for (float v : b) {
                    max = Math.max(max, Math.abs(v));
                }
                if (max > 0) {
                    float scale = (float) (peak / max);
                    for (int i = 0; i < b.length; i++) {
                        b[i] *= scale;
                    }
                }
                return b;
            }

            /**
             * Turns samples into 16-bit little-endian sound data at the given volume; values are clipped to -1..1.
             *
             * @param samples samples between -1 and 1
             * @param volume  0–1
             * @return the sound data
             */
            static byte[] toPcmBytes(float[] samples, double volume) {
                byte[] out = new byte[samples.length * 2];
                for (int i = 0; i < samples.length; i++) {
                    double v = Math.max(-1, Math.min(1, samples[i] * volume));
                    short s = (short) Math.round(v * Short.MAX_VALUE);
                    out[2 * i] = (byte) s;
                    out[2 * i + 1] = (byte) (s >> 8);
                }
                return out;
            }
        }
    }

    /**
     * The pictures that ship inside the program (logo, icons and panther pictures), all read from the {@code images}
     * folder next to this class.
     */
    public static final class Images {

        private static final java.util.Map<String, java.awt.image.BufferedImage> CACHE = new java.util.HashMap<>();

        /**
         * Only static methods; no objects of this class are created.
         */
        private Images() {
        }

        /**
         * Reads a picture from the program.
         *
         * @param path path next to this class
         * @return the picture, or {@code null} if it is missing or cannot be read
         */
        private static java.awt.image.BufferedImage read(String path) {
            try (java.io.InputStream in = Assets.class.getResourceAsStream(path)) {
                return in == null ? null : javax.imageio.ImageIO.read(in);
            } catch (java.io.IOException e) {
                return null;
            }
        }

        /**
         * Reads a picture the first time it is asked for and keeps it for the next times.
         *
         * @param path path next to this class
         * @return the picture, or {@code null} if it is missing or cannot be read
         */
        public static synchronized java.awt.image.BufferedImage get(String path) {
            return CACHE.computeIfAbsent(path, Images::read);
        }
    }
}
