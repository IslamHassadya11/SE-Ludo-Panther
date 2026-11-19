package model;

import java.util.*;
import java.util.random.RandomGenerator;

/**
 * One multiple-choice question of the question bank (FR-42, FR-59, FR-65): text, four different answers, the index of
 * the correct one, an explanation, a difficulty and a topic. The question text is shown exactly as the admin wrote it,
 * in every language of the program.
 *
 * @param id           unique number given by the bank (FR-65)
 * @param text         the question, 10–300 characters
 * @param answers      exactly four different answers, 1–100 characters each
 * @param correctIndex index (0–3) of the correct answer
 * @param explanation  why the correct answer is right, 1–300 characters (FR-44)
 * @param level        question difficulty: decides the reward or penalty of 1, 2 or 3 steps (FR-43)
 * @param topic        curriculum topic (FR-59)
 */
public record Question(int id, String text, List<String> answers, int correctIndex, String explanation, Game.Level level,
                       Topic topic) {

    /** Shortest allowed question text (FR-65). */
    public static final int TEXT_MIN = 10;
    /** Longest allowed question text (FR-65). */
    public static final int TEXT_MAX = 300;
    /** Number of answers of every question (FR-42). */
    public static final int ANSWER_COUNT = 4;
    /** Longest allowed answer (FR-65). */
    public static final int ANSWER_MAX = 100;
    /** Longest allowed explanation (FR-65). */
    public static final int EXPLANATION_MAX = 300;

    /**
     * Trims the texts and checks every FR-65 rule.
     *
     * @throws IllegalArgumentException with a list of the broken rules
     */
    public Question {
        text = text == null ? "" : text.strip();
        explanation = explanation == null ? "" : explanation.strip();
        answers = answers == null ? List.of() : answers.stream().map(a -> a == null ? "" : a.strip()).toList();
        List<Problem> problems = problems(text, answers, correctIndex, explanation, level, topic);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Invalid question: " + problems);
        }
    }

    /**
     * @return the same question with another id (used when the bank gives a new question its number)
     */
    public Question withId(int newId) {
        return new Question(newId, text, answers, correctIndex, explanation, level, topic);
    }

    /**
     * @return the text of the correct answer
     */
    public String correctAnswer() {
        return answers.get(correctIndex);
    }

    /**
     * @return how many steps a right answer moves forward and a wrong answer moves back: 1, 2 or 3 (FR-43)
     */
    public int steps() {
        return level.ordinal() + 1;
    }

    /**
     * Checks the FR-65 rules without creating a question, so the editor can show every problem at once.
     *
     * @param text        question text
     * @param answers     the answers (should be four)
     * @param correct     index of the correct answer
     * @param explanation explanation text
     * @param level       difficulty, must not be {@code null}
     * @param topic       topic, must not be {@code null}
     * @return the broken rules, empty if the question is valid
     */
    public static List<Problem> problems(String text, List<String> answers, int correct, String explanation,
                                         Game.Level level, Topic topic) {
        List<Problem> problems = new ArrayList<>();
        int length = text == null ? 0 : text.strip().length();
        if (length < TEXT_MIN || length > TEXT_MAX) {
            problems.add(Problem.TEXT_LENGTH);
        }
        if (answers == null || answers.size() != ANSWER_COUNT) {
            problems.add(Problem.ANSWER_LENGTH);
        } else {
            Set<String> distinct = new HashSet<>();
            for (String answer : answers) {
                String a = answer == null ? "" : answer.strip();
                if (a.isEmpty() || a.length() > ANSWER_MAX) {
                    problems.add(Problem.ANSWER_LENGTH);
                    break;
                }
            }
            for (String answer : answers) {
                distinct.add(answer == null ? "" : answer.strip().toLowerCase(Locale.ROOT));
            }
            if (distinct.size() != ANSWER_COUNT) {
                problems.add(Problem.ANSWERS_NOT_DIFFERENT);
            }
        }
        if (correct < 0 || correct >= ANSWER_COUNT) {
            problems.add(Problem.NO_CORRECT_ANSWER);
        }
        int explained = explanation == null ? 0 : explanation.strip().length();
        if (explained < 1 || explained > EXPLANATION_MAX) {
            problems.add(Problem.EXPLANATION_LENGTH);
        }
        if (level == null) {
            problems.add(Problem.NO_LEVEL);
        }
        if (topic == null) {
            problems.add(Problem.NO_TOPIC);
        }
        return problems;
    }

    /**
     * The rules a question can break (FR-65). The screens turn each one into a translated message.
     */
    public enum Problem {
        /** The question text is not 10–300 characters long. */
        TEXT_LENGTH,
        /** An answer is empty or longer than 100 characters. */
        ANSWER_LENGTH,
        /** Two answers are the same. */
        ANSWERS_NOT_DIFFERENT,
        /** No correct answer is marked. */
        NO_CORRECT_ANSWER,
        /** The explanation is empty or longer than 300 characters. */
        EXPLANATION_LENGTH,
        /** No difficulty was chosen. */
        NO_LEVEL,
        /** No topic was chosen. */
        NO_TOPIC
    }

    /**
     * The curriculum topics of the course (FR-59).
     */
    public enum Topic {
        AGILE,
        MVC,
        DESIGN_PATTERNS,
        QUALITY_ASSURANCE,
        SOFTWARE_DESIGN
    }

    /**
     * Where a game gets its questions from. The real game uses a {@link Deck} of the bank; tests pass fixed
     * questions.
     */
    @FunctionalInterface
    public interface Source {

        /**
         * @param level the difficulty of the question square (FR-42)
         * @return a question of that difficulty
         */
        Question next(Game.Level level);
    }

    /**
     * The questions of one game (FR-45, FR-68): a copy of the bank taken when the game starts, so admin changes only
     * count from the next game. Each difficulty is shuffled and dealt without repeats; when all questions of a
     * difficulty were asked, that difficulty is shuffled again.
     */
    public static final class Deck implements Source {

        private final Map<Game.Level, List<Question>> all = new EnumMap<>(Game.Level.class);
        private final Map<Game.Level, Deque<Question>> remaining = new EnumMap<>(Game.Level.class);
        private final RandomGenerator rng;

        /**
         * @param questions the questions to deal from
         * @param rng       random source for shuffling
         */
        public Deck(Collection<Question> questions, RandomGenerator rng) {
            this.rng = Objects.requireNonNull(rng);
            for (Game.Level level : Game.Level.values()) {
                all.put(level, questions.stream().filter(q -> q.level() == level).toList());
                remaining.put(level, new ArrayDeque<>());
            }
        }

        @Override
        public Question next(Game.Level level) {
            Deque<Question> pile = remaining.get(level);
            if (pile.isEmpty()) {
                List<Question> shuffled = new ArrayList<>(all.get(level));
                if (shuffled.isEmpty()) {
                    throw new IllegalStateException("No " + level + " questions in the bank");
                }
                Collections.shuffle(shuffled, rng);
                pile.addAll(shuffled);
            }
            return pile.poll();
        }
    }

    /**
     * The computer does not know the answer: it picks one of the four at random.
     *
     * @param question the question
     * @param rng      random source
     * @return the chosen answer index
     */
    public static int computerAnswer(Question question, RandomGenerator rng) {
        return rng.nextInt(question.answers().size());
    }
}
