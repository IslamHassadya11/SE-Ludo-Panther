package controller;

import model.*;

import java.io.Console;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;

/**
 * Command-line tool that creates the single admin account (FR-61). The team runs it once, ships the resulting
 * {@code data/admin-account.properties} next to the JAR and gives the password to the course staff separately.
 * The password is read without echo and only its salted hash is saved (NFR-31).
 * <pre>
 * java -cp panther-ludo.jar controller.CreateAdminAccount [data-folder]
 * </pre>
 */
public final class CreateAdminAccount {

    /**
     * Only the static {@code main} method is used; no objects are created.
     */
    private CreateAdminAccount() {
    }

    /**
     * @param args optional data folder; default is the {@code data} folder next to the JAR
     */
    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("Run this tool from a terminal so the password can be typed without showing it.");
            System.exit(1);
            return;
        }
        Path folder = args.length > 0 ? Path.of(args[0]) : GameConfig.dataDirectory();
        FileAdminAccountStore store = new FileAdminAccountStore(folder);
        AdminAuthService auth = new AdminAuthService(store, new PasswordHasher(), Clock.systemUTC());
        if (auth.isSetUp()) {
            String answer = console.readLine("An admin account already exists in %s. Replace it? (yes/no): ", folder);
            if (answer == null || !answer.strip().equalsIgnoreCase("yes")) {
                console.printf("Nothing changed.%n");
                return;
            }
        }
        String username = console.readLine("Admin username: ");
        char[] password = console.readPassword("Password (at least 15 characters): ");
        char[] repeat = console.readPassword("Repeat the password: ");
        try {
            List<String> problems = auth.createAccount(username, password, repeat);
            if (problems.isEmpty()) {
                console.printf("Saved %s%nGive the password to the course staff separately; never commit it.%n",
                        store.accountFile());
            } else {
                problems.forEach(p -> console.printf("- %s%n", p));
                System.exit(2);
            }
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(repeat, '\0');
        }
    }
}
