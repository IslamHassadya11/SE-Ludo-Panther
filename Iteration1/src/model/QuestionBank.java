package model;

import javax.crypto.SecretKey;
import java.io.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.random.RandomGenerator;

import static model.Game.Level.*;
import static model.Question.Topic.*;

/**
 * Singleton pattern (NFR-21): the one question bank of the program (FR-59–68). Every screen and every new game uses
 * the same instance, so an admin change is seen everywhere and the file is never written by two objects.
 * <ul>
 *     <li>On the first run it is filled with the {@link DefaultQuestions} and saved encrypted (NFR-34).</li>
 *     <li>Every add, edit and delete is saved at once (FR-68). If saving fails, the change is undone.</li>
 *     <li>A delete, or an edit that moves a question to another difficulty, is refused if fewer than 30 questions, or
 *         fewer than 10 of a difficulty, would remain (FR-67).</li>
 *     <li>If the file cannot be read the bank is "unavailable": games are Easy only (NFR-27) and nothing is saved, so
 *         the damaged file is not overwritten.</li>
 * </ul>
 * The bank is kept in one encrypted file (NFR-34), {@code data/questions.dat}, encrypted with AES-256-GCM, which hides
 * the questions and answers from anyone opening the file and detects any change to it. The 256-bit key is created
 * randomly the first time and kept in {@code data/questions.key} (S-03, TD-16), so no key is written in the program
 * code. Without that key, or after the file was changed by hand, the bank cannot be read. File layout: the bytes
 * {@code PQB1}, a fresh 12-byte nonce, then the encrypted question list with its 16-byte authentication tag; the
 * question list itself is a {@link Properties} text.
 */
public final class QuestionBank {

    /** Fewest questions the bank may have (FR-59, FR-67). */
    public static final int MIN_TOTAL = 30;
    /** Fewest questions each difficulty may have (FR-59, FR-67). */
    public static final int MIN_PER_LEVEL = 10;

    /** File name of the encrypted question bank inside the data folder. */
    private static final String BANK_FILE = "questions.dat";
    /** File name of the encryption key inside the data folder. */
    private static final String KEY_FILE = "questions.key";

    /** The first bytes of the bank file; the encryption itself is done by {@link Security} (NFR-34). */
    private static final byte[] FILE_HEADER = "PQB1".getBytes(StandardCharsets.US_ASCII);

    private final Path bankFile;
    private final Path keyFile;
    private final SecureRandom random = new SecureRandom();

    private static QuestionBank uniqueInstance;

    private final List<Question> questions = new ArrayList<>();
    /** Why the bank cannot be used, or {@code null} when it works. */
    private UnreadableException.Reason loadProblem;

    /**
     * Loads the bank from its file, or creates the default bank on the first run.
     *
     * @param dataDirectory folder that holds the bank file and its key (created when saving)
     */
    QuestionBank(Path dataDirectory) {
        this.bankFile = dataDirectory.resolve(BANK_FILE);
        this.keyFile = dataDirectory.resolve(KEY_FILE);
        reload();
    }

    /**
     * @return the question bank of the program, loaded from the {@code data} folder the first time it is needed
     */
    public static synchronized QuestionBank instance() {
        if (uniqueInstance == null) {
            uniqueInstance = new QuestionBank(Settings.dataDirectory());
        }
        return uniqueInstance;
    }

    /**
     * Reads the file again (or creates the default bank if there is none yet).
     */
    public synchronized void reload() {
        questions.clear();
        loadProblem = null;
        try {
            if (fileExists()) {
                questions.addAll(readFile());
            } else {
                List<Question> defaults = DefaultQuestions.all();
                writeFile(defaults);
                questions.addAll(defaults);
            }
        } catch (UnreadableException e) {
            loadProblem = e.reason();
        } catch (IOException e) {
            loadProblem = UnreadableException.Reason.DAMAGED;
        }
        if (loadProblem == null && Arrays.stream(Game.Level.values()).anyMatch(l -> count(l) == 0)) {
            loadProblem = UnreadableException.Reason.DAMAGED;
        }
        if (loadProblem != null) {
            questions.clear();
        }
    }

    /**
     * @return {@code true} if the bank was read and Medium and Hard games can be played (NFR-27)
     */
    public synchronized boolean isAvailable() {
        return loadProblem == null;
    }

    /**
     * @return why the bank cannot be used, or empty if it works
     */
    public synchronized Optional<UnreadableException.Reason> loadProblem() {
        return Optional.ofNullable(loadProblem);
    }

    /**
     * @param level a game level
     * @return {@code true} if that level can be played: Easy always, Medium and Hard only with a readable bank
     */
    public boolean canPlay(Game.Level level) {
        return level == Game.Level.EASY || isAvailable();
    }

    /**
     * @return all questions, ordered by id (FR-63)
     */
    public synchronized List<Question> questions() {
        return questions.stream().sorted(Comparator.comparingInt(Question::id)).toList();
    }

    /**
     * @param level a difficulty
     * @return how many questions have that difficulty
     */
    public synchronized int count(Game.Level level) {
        return (int) questions.stream().filter(q -> q.level() == level).count();
    }

    /**
     * A copy of the bank for one game (FR-45, FR-68): admin changes made during the game are used from the next game.
     *
     * @param rng random source for the shuffling
     * @return a new deck
     */
    public synchronized Question.Deck newDeck(RandomGenerator rng) {
        return new Question.Deck(List.copyOf(questions), rng);
    }

    /**
     * Adds a question with the next free id and saves the bank (FR-65, FR-68).
     *
     * @param draft the new question; its id is ignored
     * @return the saved question with its id
     * @throws IOException if the bank cannot be saved (the question is then not added)
     */
    public synchronized Question add(Question draft) throws IOException {
        requireAvailable();
        int nextId = questions.stream().mapToInt(Question::id).max().orElse(0) + 1;
        Question added = draft.withId(nextId);
        questions.add(added);
        saveOrUndo(() -> questions.remove(added));
        return added;
    }

    /**
     * Replaces the question with the same id and saves the bank (FR-66, FR-68).
     *
     * @param changed the edited question
     * @throws IOException              if the bank cannot be saved (the old question is then kept)
     * @throws IllegalArgumentException if no question has that id
     * @throws IllegalStateException    if the new difficulty would leave the old one with fewer than 10 questions
     */
    public synchronized void update(Question changed) throws IOException {
        requireAvailable();
        int index = indexOf(changed.id());
        Question old = questions.get(index);
        if (old.level() != changed.level() && count(old.level()) - 1 < MIN_PER_LEVEL) {
            throw new IllegalStateException(DeleteCheck.TOO_FEW_AT_LEVEL.name());
        }
        questions.set(index, changed);
        saveOrUndo(() -> questions.set(index, old));
    }

    /**
     * @param id a question id
     * @return whether that question may be deleted (FR-67)
     */
    public synchronized DeleteCheck deleteProblem(int id) {
        Question q = questions.get(indexOf(id));
        if (questions.size() - 1 < MIN_TOTAL) {
            return DeleteCheck.TOO_FEW_TOTAL;
        }
        if (count(q.level()) - 1 < MIN_PER_LEVEL) {
            return DeleteCheck.TOO_FEW_AT_LEVEL;
        }
        return DeleteCheck.ALLOWED;
    }

    /**
     * Deletes a question and saves the bank (FR-67, FR-68). The screen asks for confirmation first.
     *
     * @param id the question to delete
     * @throws IOException           if the bank cannot be saved (the question is then kept)
     * @throws IllegalStateException if the delete is not allowed (see {@link #deleteProblem(int)})
     */
    public synchronized void delete(int id) throws IOException {
        requireAvailable();
        DeleteCheck check = deleteProblem(id);
        if (check != DeleteCheck.ALLOWED) {
            throw new IllegalStateException(check.name());
        }
        int index = indexOf(id);
        Question removed = questions.remove(index);
        saveOrUndo(() -> questions.add(index, removed));
    }

    /**
     * Saves the bank; if that fails, undoes the change that was just made in memory.
     */
    private void saveOrUndo(Runnable undo) throws IOException {
        try {
            writeFile(questions);
        } catch (IOException e) {
            undo.run();
            throw e;
        }
    }

    /**
     * Position of a question in the list.
     */
    private int indexOf(int id) {
        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).id() == id) {
                return i;
            }
        }
        throw new IllegalArgumentException("No question with id " + id);
    }

    /**
     * Changes are refused while the bank file cannot be read, so it is not overwritten.
     */
    private void requireAvailable() {
        if (loadProblem != null) {
            throw new IllegalStateException("The question bank cannot be read: " + loadProblem);
        }
    }

    /**
     * @return {@code true} if a bank file exists (on the very first start it does not)
     */
    private boolean fileExists() {
        return Files.exists(bankFile);
    }

    /**
     * Decrypts and reads all questions.
     *
     * @return the questions in the file
     * @throws UnreadableException if the key is missing, the file was changed or its content is not a question list
     * @throws IOException         if the file cannot be read at all
     */
    private List<Question> readFile() throws IOException {
        byte[] data = Files.readAllBytes(bankFile);
        Optional<SecretKey> key = Security.readKey(keyFile);
        if (key.isEmpty()) {
            throw new UnreadableException(UnreadableException.Reason.KEY_MISSING);
        }
        if (!Security.hasHeader(data, FILE_HEADER)) {
            throw new UnreadableException(UnreadableException.Reason.DAMAGED);
        }
        byte[] plain = Security.decrypt(data, key.get(), FILE_HEADER)
                .orElseThrow(() -> new UnreadableException(UnreadableException.Reason.CHANGED));
        try {
            return fromFileText(new String(plain, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new UnreadableException(UnreadableException.Reason.DAMAGED);
        }
    }

    /**
     * Encrypts and writes all questions, replacing the old file in one step so a crash cannot leave half a file.
     * Creates the data folder and the key on first use.
     *
     * @param questions the whole bank
     * @throws IOException if the file cannot be written
     */
    private void writeFile(Collection<Question> questions) throws IOException {
        Files.createDirectories(bankFile.toAbsolutePath().getParent());
        SecretKey key = Security.readKey(keyFile).orElse(null);
        if (key == null) {
            if (Files.exists(keyFile)) {
                throw new UnreadableException(UnreadableException.Reason.KEY_MISSING);
            }
            key = Security.createKey(keyFile, random);
        }
        Security.writeBytes(bankFile, Security.encrypt(toFileText(questions).getBytes(StandardCharsets.UTF_8), key, FILE_HEADER,
                random));
    }

    /**
     * Writes the questions as {@link Properties} text: {@code q.<n>.<field>} for every field.
     */
    static String toFileText(Collection<Question> questions) {
        Properties p = new Properties();
        int n = 0;
        for (Question q : questions) {
            String k = "q." + n + ".";
            p.setProperty(k + "id", String.valueOf(q.id()));
            p.setProperty(k + "text", q.text());
            for (int i = 0; i < Question.ANSWER_COUNT; i++) {
                p.setProperty(k + "answer" + i, q.answers().get(i));
            }
            p.setProperty(k + "correct", String.valueOf(q.correctIndex()));
            p.setProperty(k + "explanation", q.explanation());
            p.setProperty(k + "level", q.level().name());
            p.setProperty(k + "topic", q.topic().name());
            n++;
        }
        p.setProperty("count", String.valueOf(n));
        StringWriter writer = new StringWriter();
        try {
            p.store(writer, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return writer.toString();
    }

    /**
     * Reads the text written by {@link #toFileText(Collection)}.
     *
     * @throws IllegalArgumentException if a field is missing or invalid
     */
    static List<Question> fromFileText(String text) {
        Properties p = new Properties();
        try {
            p.load(new StringReader(text));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        int count = Integer.parseInt(requiredProperty(p, "count"));
        List<Question> questions = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            String k = "q." + n + ".";
            List<String> answers = new ArrayList<>();
            for (int i = 0; i < Question.ANSWER_COUNT; i++) {
                answers.add(requiredProperty(p, k + "answer" + i));
            }
            questions.add(new Question(Integer.parseInt(requiredProperty(p, k + "id")), requiredProperty(p, k + "text"), answers,
                    Integer.parseInt(requiredProperty(p, k + "correct")), requiredProperty(p, k + "explanation"),
                    Game.Level.valueOf(requiredProperty(p, k + "level")), Question.Topic.valueOf(requiredProperty(p, k + "topic"))));
        }
        return questions;
    }

    /**
     * A property that must be present.
     */
    private static String requiredProperty(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return value;
    }

    /**
     * The bank file exists but cannot be used: missing key, changed or damaged file (NFR-27, NFR-34).
     */
    public static final class UnreadableException extends IOException {

        private static final long serialVersionUID = 1L;

        private final Reason reason;

        /**
         * @param reason why the bank cannot be used; the screens show it in the player's language
         */
        public UnreadableException(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }

        /**
         * @return why the bank cannot be used
         */
        public Reason reason() {
            return reason;
        }

        /**
         * Why a question bank cannot be used.
         */
        public enum Reason {
            /** The key file is missing or damaged, so the bank cannot be decrypted. */
            KEY_MISSING,
            /** The bank was changed outside the program: the AES-GCM check failed (NFR-34). */
            CHANGED,
            /** The file is not a question bank or its content is broken. */
            DAMAGED
        }
    }

    /**
     * Whether a question may be removed from its difficulty (FR-67).
     */
    public enum DeleteCheck {
        /** The question may be deleted. */
        ALLOWED,
        /** Fewer than 30 questions would remain. */
        TOO_FEW_TOTAL,
        /** Fewer than 10 questions of that difficulty would remain. */
        TOO_FEW_AT_LEVEL
    }

    /**
     * The questions the bank starts with on the very first run (FR-59): 36 questions, 12 per difficulty, about Agile,
     * MVC, design patterns, quality assurance and software design. After that first run the questions live only in the
     * encrypted bank file and the admin can change them.
     */
    static final class DefaultQuestions {

        /**
         * Only a static list; no objects of this class are created.
         */
        private DefaultQuestions() {
        }

        /**
         * @return the starting questions with ids 1, 2, 3, ...
         */
        static List<Question> all() {
            List<Question> list = new ArrayList<>();
            // ---------------------------------------------------------------- easy
            add(list, EASY, AGILE, "In Scrum, what is a Sprint?",
                    0, "A short, fixed-length period in which the team builds a usable increment",
                    "A meeting where managers assign tasks", "The final test phase before release",
                    "A document that lists all requirements",
                    "A Sprint is a time-box (usually 1-4 weeks) that ends with a potentially releasable increment.");
            add(list, EASY, AGILE, "Who is responsible for ordering the Product Backlog in Scrum?",
                    1, "The Scrum Master", "The Product Owner", "The development team", "The customer's lawyer",
                    "The Product Owner owns the Product Backlog and decides the order of its items.");
            add(list, EASY, AGILE, "What is the main purpose of the Daily Scrum?",
                    2, "To report hours to the manager", "To demo the product to the customer",
                    "To inspect progress toward the Sprint Goal and plan the next day",
                    "To write the requirements document",
                    "The Daily Scrum is a short daily meeting where developers synchronize and adapt their plan.");
            add(list, EASY, MVC, "In the MVC pattern, which part holds the data and the rules of the application?",
                    0, "The Model", "The View", "The Controller", "The Compiler",
                    "The Model contains the state and the business logic, independent of the user interface.");
            add(list, EASY, MVC, "In MVC, what is the job of the View?",
                    1, "To store data in files", "To display the model's data to the user",
                    "To decide the business rules", "To compile the program",
                    "The View only presents information; it does not decide the rules.");
            add(list, EASY, DESIGN_PATTERNS, "Which design pattern makes sure a class has only one instance?",
                    3, "Observer", "Factory", "Strategy", "Singleton",
                    "Singleton gives a class exactly one instance and a global point of access to it.");
            add(list, EASY, DESIGN_PATTERNS, "Which design pattern lets objects be notified when another object changes?",
                    0, "Observer", "Singleton", "Adapter", "Builder",
                    "In Observer, subscribers register with a subject and are notified of its changes.");
            add(list, EASY, QUALITY_ASSURANCE, "What is a unit test?",
                    2, "A test done by the customer at the end", "A test of the whole system under heavy load",
                    "A test of one small piece of code, such as a single method or class",
                    "A review of the user manual",
                    "Unit tests check the smallest testable parts of the code in isolation.");
            add(list, EASY, QUALITY_ASSURANCE, "What is a bug (defect) in software?",
                    1, "A new feature request", "A flaw that makes the software behave differently than expected",
                    "A comment in the code", "A type of variable",
                    "A defect is any deviation between the actual and the expected behavior.");
            add(list, EASY, SOFTWARE_DESIGN, "What does a UML class diagram show?",
                    3, "The order of messages over time", "The screens of the program",
                    "The hardware the program runs on", "Classes, their attributes, methods and relationships",
                    "Class diagrams describe the static structure of a system.");
            add(list, EASY, SOFTWARE_DESIGN, "What does 'encapsulation' mean in object-oriented design?",
                    0, "Hiding an object's internal data behind methods", "Copying code between classes",
                    "Running code in parallel", "Writing all code in one class",
                    "Encapsulation keeps fields private and exposes behavior through a public interface.");
            add(list, EASY, AGILE, "What does the Agile Manifesto value more than comprehensive documentation?",
                    2, "Following a plan", "Contract negotiation", "Working software", "Processes and tools",
                    "The Manifesto values working software over comprehensive documentation.");
            // ---------------------------------------------------------------- medium
            add(list, MEDIUM, AGILE, "What is the 'Definition of Done' in Scrum?",
                    1, "The date when the project ends", "A shared checklist an increment must meet to be complete",
                    "The list of all bugs", "The customer's signature on the contract",
                    "The Definition of Done is a formal description of the quality required for an increment.");
            add(list, MEDIUM, AGILE, "What is a user story usually written like?",
                    0, "As a <role>, I want <goal> so that <benefit>", "IF condition THEN action ELSE error",
                    "Class name, attributes, methods", "Input, process, output, storage",
                    "User stories describe a need from the user's point of view in this short template.");
            add(list, MEDIUM, AGILE, "What happens in a Sprint Retrospective?",
                    3, "The customer accepts the product", "The Product Backlog is created for the first time",
                    "The code is deployed to production", "The team reflects on how it worked and plans improvements",
                    "The Retrospective looks at people, processes and tools to improve the next Sprint.");
            add(list, MEDIUM, MVC, "In MVC, what does the Controller do?",
                    2, "It draws the buttons", "It saves the database schema",
                    "It receives user input and translates it into actions on the model",
                    "It replaces the model when needed",
                    "The Controller handles input and tells the Model what to do; the View then shows the result.");
            add(list, MEDIUM, MVC, "Why should game rules be in the Model and not in the View?",
                    1, "Because Views cannot contain code",
                    "So the rules can be tested and reused without the user interface",
                    "Because Java requires it", "To make the program run faster",
                    "Separating rules from the UI makes them testable and lets the UI change without changing rules.");
            add(list, MEDIUM, DESIGN_PATTERNS,
                    "Which pattern defines a family of algorithms and makes them interchangeable?",
                    0, "Strategy", "Singleton", "Facade", "Observer",
                    "Strategy puts each algorithm in its own class behind a common interface.");
            add(list, MEDIUM, DESIGN_PATTERNS, "What is the main benefit of the Factory pattern?",
                    2, "It makes every class a singleton", "It removes the need for interfaces",
                    "Code creates objects without depending on their concrete classes",
                    "It speeds up garbage collection",
                    "A Factory hides which concrete class is created, so callers depend only on an abstraction.");
            add(list, MEDIUM, QUALITY_ASSURANCE, "What is the difference between verification and validation?",
                    1, "There is no difference",
                    "Verification: are we building it right? Validation: are we building the right product?",
                    "Verification is done by users, validation by developers", "Validation happens only before coding",
                    "Verification checks conformance to the specification; validation checks it meets the user's needs.");
            add(list, MEDIUM, QUALITY_ASSURANCE, "What is regression testing?",
                    3, "Testing only new features", "Testing the program on old computers",
                    "Testing performance under load", "Re-running tests to check that changes did not break old behavior",
                    "Regression tests catch defects introduced into features that used to work.");
            add(list, MEDIUM, SOFTWARE_DESIGN, "What does 'high cohesion' mean for a class?",
                    0, "Its methods and data are closely related to one purpose", "It has many public fields",
                    "It depends on many other classes", "It has more than 1000 lines",
                    "A cohesive class has one clear responsibility, which makes it easier to understand and change.");
            add(list, MEDIUM, SOFTWARE_DESIGN, "Why is 'low coupling' desirable between modules?",
                    2, "It makes the code longer", "It forces all modules to share global variables",
                    "A change in one module affects others less", "It removes the need for testing",
                    "Loosely coupled modules can be changed, tested and reused independently.");
            add(list, MEDIUM, QUALITY_ASSURANCE, "In black-box testing, what does the tester rely on?",
                    1, "The source code", "The specification of inputs and expected outputs",
                    "The compiler's warnings", "The number of lines of code",
                    "Black-box testing designs tests from requirements without looking at the internal code.");
            // ---------------------------------------------------------------- hard
            add(list, HARD, AGILE, "What does 'velocity' measure in Scrum?",
                    2, "How fast the program runs", "The number of bugs found per day",
                    "The amount of work a team completes in a Sprint, used to forecast",
                    "The time of a single Daily Scrum",
                    "Velocity is the sum of estimates of the items finished per Sprint; it helps plan future Sprints.");
            add(list, HARD, AGILE, "In Scrum, who may cancel a Sprint?",
                    3, "Any developer", "The Scrum Master alone", "The customer's manager", "Only the Product Owner",
                    "Only the Product Owner has the authority to cancel a Sprint, when its goal becomes obsolete.");
            add(list, HARD, AGILE, "What is the purpose of the INVEST criteria for user stories?",
                    0, "To check that stories are Independent, Negotiable, Valuable, Estimable, Small and Testable",
                    "To calculate the project budget", "To choose the programming language",
                    "To decide who joins the team",
                    "INVEST is a checklist for writing good, plannable user stories.");
            add(list, HARD, MVC, "In MVC with the Observer pattern, how does the View learn that the Model changed?",
                    1, "The View checks the database every second", "The Model notifies its registered listeners",
                    "The Controller redraws every pixel", "The user presses Refresh",
                    "The Model publishes events to observers, so it never depends on concrete View classes.");
            add(list, HARD, MVC, "Which statement about MVC dependencies is correct?",
                    2, "The Model must import the Swing View classes", "The View must never read the Model",
                    "The Model should not depend on the View or the Controller",
                    "The Controller must contain all game rules",
                    "Keeping the Model independent lets it be tested and reused with another user interface.");
            add(list, HARD, DESIGN_PATTERNS,
                    "Which pattern converts the interface of a class into another interface clients expect?",
                    3, "Decorator", "Composite", "Observer", "Adapter",
                    "An Adapter wraps an existing class so it can be used through a different interface.");
            add(list, HARD, DESIGN_PATTERNS, "Which pattern adds responsibilities to an object dynamically by wrapping it?",
                    0, "Decorator", "Singleton", "Factory Method", "Template Method",
                    "A Decorator implements the same interface and adds behavior before or after delegating.");
            add(list, HARD, DESIGN_PATTERNS, "What is a common drawback of the Singleton pattern?",
                    1, "It cannot have methods", "It hides dependencies and makes unit testing harder",
                    "It creates too many objects", "It only works in C++",
                    "Global access hides who uses the singleton, and shared state makes tests depend on each other.");
            add(list, HARD, QUALITY_ASSURANCE, "What does 100% statement coverage guarantee?",
                    2, "That the program has no bugs", "That every path through the code was tested",
                    "That every statement was executed at least once by the tests",
                    "That every requirement was validated",
                    "Statement coverage says nothing about all branch combinations or missing requirements.");
            add(list, HARD, QUALITY_ASSURANCE, "What is boundary value analysis?",
                    0, "Testing at and around the edges of valid input ranges", "Measuring the size of the code",
                    "Checking the limits of the hardware", "Reviewing the project budget",
                    "Many defects occur at the edges, e.g. lengths 0, 1, 15 and 16 for a 1-15 character name.");
            add(list, HARD, SOFTWARE_DESIGN, "What does the Open/Closed Principle state?",
                    3, "Classes must be public", "Files must be closed after reading",
                    "A class should have only one reason to change",
                    "Software entities should be open for extension but closed for modification",
                    "New behavior should be added by extension (e.g. new classes), not by editing tested code.");
            add(list, HARD, SOFTWARE_DESIGN, "What does the Liskov Substitution Principle require?",
                    1, "Every class must have a parent class",
                    "Objects of a subclass must be usable wherever the base class is expected",
                    "Interfaces must have one method only", "Subclasses must override every method",
                    "A subtype must keep the base type's promises, so substituting it does not break clients.");
            // Architectural patterns: advantages and disadvantages (sources in questions_Panther.xlsx).
            add(list, EASY, MVC,
                    "What is a main advantage of the MVC architecture?",
                    1, "The program needs fewer classes",
                    "The screens can be changed without changing the rules of the application",
                    "The program runs without a user interface only",
                    "The Model can draw itself on the screen",
                    "MVC separates the data and rules (Model) from the screens (View), so one can change without the other.");
            add(list, MEDIUM, MVC,
                    "What is a disadvantage of the MVC architecture for a very small program?",
                    2, "The rules cannot be tested without the screens",
                    "Only one screen can be shown at a time",
                    "It adds classes and indirection that a small program may not need",
                    "The Model must know every View class",
                    "MVC splits even simple features over three parts, which is extra work when the program is tiny.");
            add(list, MEDIUM, SOFTWARE_DESIGN,
                    "What is an advantage of a layered architecture?",
                    0, "One layer can be updated or replaced without affecting the other layers",
                    "Every class may call every other class directly",
                    "It always runs faster than a design without layers",
                    "It removes the need for testing",
                    "Each layer has one concern and talks only to the layer below it, so a change stays inside its layer.");
            add(list, MEDIUM, SOFTWARE_DESIGN,
                    "What is a trade-off of a layered architecture?",
                    3, "The user interface cannot be replaced",
                    "Layers cannot be reused in other systems",
                    "The layers must run on different computers",
                    "A request passes through several layers, which can cost performance",
                    "Every call travels through the layers in order, so the layers add overhead and must be designed with care.");
            add(list, HARD, SOFTWARE_DESIGN,
                    "What is the main idea of the hexagonal (ports and adapters) architecture?",
                    1, "The system is split into exactly six layers",
                    "The core business logic does not depend on the user interface or the database",
                    "Every class is connected to six other classes",
                    "The database is the center of the system",
                    "The core talks to the outside only through ports; adapters connect the UI, database and other systems.");
            add(list, HARD, SOFTWARE_DESIGN,
                    "What is a disadvantage of the hexagonal architecture?",
                    2, "The core logic cannot be tested without a database",
                    "The user interface cannot be replaced later",
                    "It adds abstractions and can be over-engineering for a simple application",
                    "It works only for web applications",
                    "Ports and adapters are extra layers; a small program may be easier to understand without them.");
            add(list, MEDIUM, SOFTWARE_DESIGN,
                    "What is an advantage of an event-driven architecture?",
                    0, "New consumers of an event can be added with little change to the producers",
                    "The order of events never matters",
                    "It needs no messaging infrastructure",
                    "Every component calls the others directly",
                    "Producers only publish events and do not know the consumers, so the parts are loosely coupled.");
            add(list, HARD, SOFTWARE_DESIGN,
                    "What is a trade-off of an event-driven architecture?",
                    3, "It cannot handle changing loads",
                    "New event types cannot be added later",
                    "Producers must know every consumer",
                    "The flow is asynchronous, so it is harder to trace and debug",
                    "With loose coupling and asynchronous events it is difficult to follow what happened and in which order.");
            add(list, MEDIUM, MVC,
                    "How does Model-View-Presenter (MVP) differ from MVC?",
                    1, "MVP has no Model",
                    "A Presenter takes over the Controller's role and holds the UI logic",
                    "In MVP the View holds the business rules",
                    "MVP can be used only in web pages",
                    "In MVP the Presenter handles the input and updates the View, so the UI logic can be tested without the View.");
            add(list, HARD, SOFTWARE_DESIGN,
                    "Eclipse is built as a small runtime with many plug-ins. What is the main advantage of this plug-in architecture?",
                    2, "All the features are compiled into one large class",
                    "The program starts only when every plug-in is loaded",
                    "New features can be added as plug-ins without changing the core",
                    "Plug-ins do not need defined interfaces",
                    "A small core defines extension points, and features are added or replaced as separate plug-ins.");
            add(list, HARD, SOFTWARE_DESIGN,
                    "nginx handles connections with an event-driven design instead of one process or thread per connection. What is the main advantage?",
                    0, "It serves many connections at once with little memory",
                    "It needs no operating system",
                    "Each request gets its own process, so requests are isolated",
                    "It can serve only one connection at a time",
                    "A few worker processes handle many connections without blocking, so memory use stays low under load.");
            add(list, MEDIUM, SOFTWARE_DESIGN,
                    "In HDFS one master (the NameNode) keeps the file system's metadata and many DataNodes store the data. What is a risk of this design?",
                    3, "Files cannot be larger than one disk",
                    "DataNodes cannot be added later",
                    "Every DataNode must store all the files",
                    "If the single master fails, the file system cannot be used",
                    "A single master is simple to design, but it is a single point of failure and must be protected.");
            add(list, EASY, SOFTWARE_DESIGN,
                    "What triggers an action in Event-Driven Architecture?",
                    1, "A scheduled cron job",
                    "An event or state change",
                    "A user closing the app",
                    "A database backup",
                    "Components react to events (changes of state) instead of calling each other directly.");
            add(list, EASY, SOFTWARE_DESIGN,
                    "Why use Microservices?",
                    0, "Independent deployment and scaling",
                    "Zero network latency",
                    "Simplifies databases",
                    "No DevOps needed",
                    "Each service can be developed, deployed and scaled on its own by its own team.");
            add(list, EASY, MVC,
                    "What is a primary advantage of MVC?",
                    1, "Mixes data and UI",
                    "Separates presentation from data",
                    "Eliminates network latency",
                    "Best for simple scripts",
                    "MVC lets the data change independently of how it is shown, and the same data can be shown in several ways.");
            add(list, EASY, MVC,
                    "Which MVC component manages user interactions?",
                    2, "Model",
                    "View",
                    "Controller",
                    "Database",
                    "The Controller handles user interaction (clicks, keys) and passes it to the View and the Model.");
            add(list, EASY, SOFTWARE_DESIGN,
                    "In a Repository pattern, how do components interact?",
                    0, "They share a central data store",
                    "They communicate only via events",
                    "They call each other directly",
                    "They use a microkernel",
                    "All components share one central data store and interact through it, like the tools of an IDE.");
            add(list, MEDIUM, SOFTWARE_DESIGN,
                    "What is the role of a Broker?",
                    1, "Stores all data centrally",
                    "Routes requests between clients and servers",
                    "Compiles code",
                    "Renders the UI",
                    "The broker is a mediator that routes requests from clients to servers, so they do not need to know each other.");
            add(list, MEDIUM, SOFTWARE_DESIGN,
                    "What is a disadvantage of Client-Server architecture?",
                    2, "Clients must print locally",
                    "Cannot distribute servers",
                    "Server is a single point of failure",
                    "No shared database",
                    "Each service is a single point of failure, open to server failure or denial-of-service attacks.");
            add(list, MEDIUM, SOFTWARE_DESIGN,
                    "What is a primary advantage of Client-Server?",
                    1, "No network needed",
                    "Servers can be distributed across a network",
                    "Clients store all data",
                    "Eliminates security risks",
                    "Servers can be distributed across a network, and general services are available to all clients.");
            add(list, MEDIUM, SOFTWARE_DESIGN,
                    "What is a disadvantage of Layered Architecture?",
                    2, "Layers cannot be replaced",
                    "Prevents teamwork",
                    "Degrades performance due to multiple levels",
                    "Impossible to secure",
                    "A request is interpreted again at each layer it passes through, which can hurt performance.");
            add(list, MEDIUM, SOFTWARE_DESIGN,
                    "When is Layered Architecture typically used?",
                    1, "Building single-page scripts",
                    "Building on top of existing systems (like OS)",
                    "When speed is the only priority",
                    "For real-time gaming",
                    "Layers are used when building new facilities on top of existing systems, when each team owns a layer, or for multilevel security.");
            add(list, MEDIUM, SOFTWARE_DESIGN,
                    "What is a core benefit of Microkernel (Plug-in)?",
                    0, "High extensibility via plug-ins",
                    "Zero startup time",
                    "No APIs needed",
                    "Only runs in browsers",
                    "A small core runs plug-ins, so new features are added as plug-ins, as in Eclipse.");
            add(list, MEDIUM, SOFTWARE_DESIGN,
                    "How does Peer-to-Peer differ from Client-Server?",
                    0, "Nodes act as both clients and servers",
                    "Requires a central server",
                    "Only works locally",
                    "Cannot share files",
                    "In peer-to-peer every node is both a client and a server, as in BitTorrent.");
            add(list, MEDIUM, SOFTWARE_DESIGN,
                    "Which tool heavily uses Pipe-and-Filter?",
                    1, "Word processor",
                    "Unix command line",
                    "Web browser",
                    "Calculator",
                    "Unix shells chain programs with '|': the output of each program is the input of the next, the pipes-and-filters idea.");
            add(list, MEDIUM, SOFTWARE_DESIGN,
                    "What is a characteristic of Serverless architecture?",
                    1, "You must buy physical servers",
                    "You pay only for compute time used",
                    "Servers run 24/7",
                    "No code is required",
                    "The cloud provider manages the servers, and you pay only for the resources the application actually uses.");
            add(list, HARD, SOFTWARE_DESIGN,
                    "Which scenario best fits a Blackboard architecture?",
                    1, "Simple CRUD app",
                    "Independent components working on a shared complex problem",
                    "Strict sequential processing",
                    "Static webpage",
                    "Independent specialist components update a shared blackboard until the problem is solved.");
            add(list, HARD, SOFTWARE_DESIGN,
                    "What is a trade-off of Event-Driven Architecture?",
                    1, "Producers know all consumers",
                    "Loose coupling but hard to debug",
                    "Immediate ACID consistency",
                    "No serialization",
                    "Loose coupling and asynchronous events make the flow hard to track and debug.");
            add(list, HARD, SOFTWARE_DESIGN,
                    "What is the goal of Hexagonal Architecture?",
                    0, "Isolate the core logic from outside technologies",
                    "Make UI rendering faster",
                    "Combine database and UI code",
                    "Replace HTTP",
                    "The core logic talks to the outside only through ports, so databases and user interfaces are replaceable adapters.");
            add(list, HARD, SOFTWARE_DESIGN,
                    "What is a major challenge in Microservices?",
                    1, "Tight code coupling",
                    "Distributed data consistency",
                    "Single language lock-in",
                    "No fault isolation",
                    "Each service has its own database, so operations across services need complex, eventually consistent transactions.");
            add(list, HARD, SOFTWARE_DESIGN,
                    "What is a disadvantage of Pipe-and-Filter?",
                    1, "Filters are not reusable",
                    "Poor fit for interactive GUIs",
                    "No data transformation",
                    "Fastest database queries",
                    "Pipe and filter suits step-by-step processing of data; interactive systems with many user events fit it poorly.");
            add(list, HARD, SOFTWARE_DESIGN,
                    "What problem does Space-Based Architecture solve?",
                    1, "GUI rendering",
                    "Eliminating the central database bottleneck",
                    "Parsing text",
                    "Formatting XML",
                    "Processing units keep the data in replicated memory, so the central database no longer limits how far the system scales.");
            int id = 1;
            List<Question> numbered = new ArrayList<>();
            for (Question q : list) {
                numbered.add(q.withId(id++));
            }
            return numbered;
        }

        /**
         * Adds one question; the answers are listed in display order and {@code correct} points into them.
         */
        private static void add(List<Question> list, Game.Level level, Question.Topic topic, String text, int correct,
                                String a, String b, String c, String d, String explanation) {
            list.add(new Question(0, text, List.of(a, b, c, d), correct, explanation, level, topic));
        }
    }
}
