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
     * The questions the bank starts with on the very first run (FR-59): the 27 questions of questions_Panther.xlsx
     * (5 easy, 10 medium, 12 hard) about architectural patterns. After that first run the questions live only in the
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
            // A1 - Event-driven
            add(list, EASY, SOFTWARE_DESIGN, "What triggers an action in Event-Driven Architecture?",
                    1, "A scheduled cron job",
                    "An event or state change",
                    "A user closing the app",
                    "A database backup",
                    "Components react to events (changes of state) instead of calling each other directly.");
            // A2 - Microservices
            add(list, EASY, SOFTWARE_DESIGN, "Why use Microservices?",
                    0, "Independent deployment and scaling",
                    "Zero network latency",
                    "Simplifies databases",
                    "No DevOps needed",
                    "Each service can be developed, deployed and scaled on its own by its own team.");
            // A3 - Repository
            add(list, EASY, SOFTWARE_DESIGN, "In a Repository pattern, how do components interact?",
                    0, "They share a central data store",
                    "They communicate only via events",
                    "They call each other directly",
                    "They use a microkernel",
                    "All components share one central data store and interact through it, like the tools of an IDE.");
            // A4 - Distributed version control (Git)
            add(list, EASY, SOFTWARE_DESIGN, "In Git's distributed design every clone is a full repository. What can a developer do that a central-server system does not allow?",
                    0, "Commit and look at the history while offline",
                    "Skip writing commit messages",
                    "Change files without saving them",
                    "Share work with others without any network",
                    "Commits are made locally and pushed to shared repositories later, so a developer can work and see the history offline.");
            // A5 - API Gateway (microservices)
            add(list, EASY, SOFTWARE_DESIGN, "In a microservices system, what is the role of an API gateway?",
                    1, "A database that all the services share",
                    "A single entry point that routes client requests to the services",
                    "A tool that compiles each service",
                    "A screen for the system administrator",
                    "Clients call one gateway instead of many services; it routes each request and can combine answers from several services.");
            // ---------------------------------------------------------------- medium
            // A6 - Broker
            add(list, MEDIUM, SOFTWARE_DESIGN, "What is the role of a Broker?",
                    1, "Stores all data centrally",
                    "Routes requests between clients and servers",
                    "Compiles code",
                    "Renders the UI",
                    "The broker is a mediator that routes requests from clients to servers, so they do not need to know each other.");
            // A7 - Master-worker (HDFS)
            add(list, MEDIUM, SOFTWARE_DESIGN, "In HDFS one master (the NameNode) keeps the file system's metadata and many DataNodes store the data. What is a risk of this design?",
                    3, "Files cannot be larger than one disk",
                    "DataNodes cannot be added later",
                    "Every DataNode must store all the files",
                    "If the single master fails, the file system cannot be used",
                    "A single master is simple to design, but it is a single point of failure and must be protected.");
            // A8 - Microkernel (plug-in)
            add(list, MEDIUM, SOFTWARE_DESIGN, "What is a core benefit of Microkernel (Plug-in)?",
                    0, "High extensibility via plug-ins",
                    "Zero startup time",
                    "No APIs needed",
                    "Only runs in browsers",
                    "A small core runs plug-ins, so new features are added as plug-ins, as in Eclipse.");
            // A9 - Peer-to-peer
            add(list, MEDIUM, SOFTWARE_DESIGN, "How does Peer-to-Peer differ from Client-Server?",
                    0, "Nodes act as both clients and servers",
                    "Requires a central server",
                    "Only works locally",
                    "Cannot share files",
                    "In peer-to-peer every node is both a client and a server, as in BitTorrent.");
            // A10 - Pipe and filter
            add(list, MEDIUM, SOFTWARE_DESIGN, "Which tool heavily uses Pipe-and-Filter?",
                    1, "Word processor",
                    "Unix command line",
                    "Web browser",
                    "Calculator",
                    "Unix shells chain programs with '|': the output of each program is the input of the next, the pipes-and-filters idea.");
            // A11 - Serverless
            add(list, MEDIUM, SOFTWARE_DESIGN, "What is a characteristic of Serverless architecture?",
                    1, "You must buy physical servers",
                    "You pay only for compute time used",
                    "Servers run 24/7",
                    "No code is required",
                    "The cloud provider manages the servers, and you pay only for the resources the application actually uses.");
            // A12 - API Gateway (microservices)
            add(list, MEDIUM, SOFTWARE_DESIGN, "What is a drawback of adding an API gateway in front of the services?",
                    2, "Clients must call every service directly",
                    "The services can no longer be deployed separately",
                    "It is one more part to build and run, and it adds a network hop",
                    "It removes the need for any security checks",
                    "The gateway is another moving part that must be developed, deployed and managed, and every request takes one more network hop.");
            // A13 - CQRS (Command Query Responsibility Segregation)
            add(list, MEDIUM, SOFTWARE_DESIGN, "What does the CQRS pattern separate?",
                    1, "The user interface from the database server",
                    "The operations that change data from the operations that read it",
                    "The test code from the production code",
                    "Small services from large services",
                    "Commands (writes) and queries (reads) get separate models, so each side can be optimized and scaled on its own; the cost is keeping them in sync.");
            // A14 - Event Sourcing
            add(list, MEDIUM, SOFTWARE_DESIGN, "In Event Sourcing, how is the current state of the application kept?",
                    1, "Only the latest values are stored and old ones are deleted",
                    "It is rebuilt from a stored sequence of all the changes (events)",
                    "Each user keeps his own copy of the state",
                    "It is read back from the screens",
                    "Every change is stored as an event; the state can be rebuilt by replaying the events, also as it was at any time in the past.");
            // A15 - Three-phase design (LLVM)
            add(list, MEDIUM, SOFTWARE_DESIGN, "A compiler with a front end per language, one shared optimizer and a back end per machine (as in LLVM) supports N languages on M machines. What is the main advantage?",
                    2, "The compiler no longer needs an optimizer",
                    "Each program must be compiled twice",
                    "N front ends and M back ends are enough instead of N×M compilers",
                    "Programs run without being compiled",
                    "With a common code representation in the middle, a new language needs only a new front end and a new machine only a new back end.");
            // ---------------------------------------------------------------- hard
            // A16 - Event-driven
            add(list, HARD, SOFTWARE_DESIGN, "What is a trade-off of Event-Driven Architecture?",
                    1, "Producers know all consumers",
                    "Loose coupling but hard to debug",
                    "Immediate ACID consistency",
                    "No serialization",
                    "Loose coupling and asynchronous events make the flow hard to track and debug.");
            // A17 - Event-driven (asynchronous server)
            add(list, HARD, SOFTWARE_DESIGN, "nginx handles connections with an event-driven design instead of one process or thread per connection. What is the main advantage?",
                    0, "It serves many connections at once with little memory",
                    "It needs no operating system",
                    "Each request gets its own process, so requests are isolated",
                    "It can serve only one connection at a time",
                    "A few worker processes handle many connections without blocking, so memory use stays low under load.");
            // A18 - Hexagonal (ports and adapters)
            add(list, HARD, SOFTWARE_DESIGN, "What is the goal of Hexagonal Architecture?",
                    0, "Isolate the core logic from outside technologies",
                    "Make UI rendering faster",
                    "Combine database and UI code",
                    "Replace HTTP",
                    "The core logic talks to the outside only through ports, so databases and user interfaces are replaceable adapters.");
            // A19 - Hexagonal (ports and adapters)
            add(list, HARD, SOFTWARE_DESIGN, "What is the main idea of the hexagonal (ports and adapters) architecture?",
                    1, "The system is split into exactly six layers",
                    "The core business logic does not depend on the user interface or the database",
                    "Every class is connected to six other classes",
                    "The database is the center of the system",
                    "The core talks to the outside only through ports; adapters connect the UI, database and other systems.");
            // A20 - Hexagonal (ports and adapters)
            add(list, HARD, SOFTWARE_DESIGN, "What is a disadvantage of the hexagonal architecture?",
                    2, "The core logic cannot be tested without a database",
                    "The user interface cannot be replaced later",
                    "It adds abstractions and can be over-engineering for a simple application",
                    "It works only for web applications",
                    "Ports and adapters are extra layers; a small program may be easier to understand without them.");
            // A21 - Microkernel (plug-in)
            add(list, HARD, SOFTWARE_DESIGN, "Eclipse is built as a small runtime with many plug-ins. What is the main advantage of this plug-in architecture?",
                    2, "All the features are compiled into one large class",
                    "The program starts only when every plug-in is loaded",
                    "New features can be added as plug-ins without changing the core",
                    "Plug-ins do not need defined interfaces",
                    "A small core defines extension points, and features are added or replaced as separate plug-ins.");
            // A22 - Microservices
            add(list, HARD, SOFTWARE_DESIGN, "What is a major challenge in Microservices?",
                    1, "Tight code coupling",
                    "Distributed data consistency",
                    "Single language lock-in",
                    "No fault isolation",
                    "Each service has its own database, so operations across services need complex, eventually consistent transactions.");
            // A23 - Pipe and filter
            add(list, HARD, SOFTWARE_DESIGN, "What is a disadvantage of Pipe-and-Filter?",
                    1, "Filters are not reusable",
                    "Poor fit for interactive GUIs",
                    "No data transformation",
                    "Fastest database queries",
                    "Pipe and filter suits step-by-step processing of data; interactive systems with many user events fit it poorly.");
            // A24 - Space-based
            add(list, HARD, SOFTWARE_DESIGN, "What problem does Space-Based Architecture solve?",
                    1, "GUI rendering",
                    "Eliminating the central database bottleneck",
                    "Parsing text",
                    "Formatting XML",
                    "Processing units keep the data in replicated memory, so the central database no longer limits how far the system scales.");
            // A25 - Saga (microservices)
            add(list, HARD, SOFTWARE_DESIGN, "Each service has its own database. In the Saga pattern, how is a business transaction that fails halfway undone?",
                    2, "A two-phase commit locks all the databases",
                    "The databases roll everything back automatically",
                    "Compensating transactions undo the steps that already completed",
                    "The client deletes the data by hand",
                    "A saga is a sequence of local transactions; there is no automatic rollback, so developers must write compensating transactions.");
            // A26 - Brokerless messaging (ZeroMQ)
            add(list, HARD, SOFTWARE_DESIGN, "Why did ZeroMQ avoid a central message broker?",
                    2, "A broker cannot store messages",
                    "A broker works only on one computer",
                    "A broker becomes a bottleneck and every message crosses the network twice",
                    "A broker delivers messages out of order",
                    "With a broker every message passes the network twice and all traffic goes through one server, which at some point becomes the bottleneck.");
            // A27 - Event Sourcing
            add(list, HARD, SOFTWARE_DESIGN, "An Event Sourcing system rebuilds its state by replaying the stored events. What problem must be handled during a replay?",
                    1, "The events are deleted after the replay",
                    "External systems would receive the same updates again",
                    "The replay can run only once",
                    "The current state can no longer be saved",
                    "External systems do not know the difference between real processing and a replay, so calls to them are wrapped in gateways that are switched off during replays.");
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
