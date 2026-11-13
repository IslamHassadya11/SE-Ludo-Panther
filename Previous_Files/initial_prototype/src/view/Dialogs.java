package view;

import javax.swing.JOptionPane;
import java.awt.Component;

/**
 * Standard dialogs. Error dialogs always say what went wrong and what to do (NFR-07).
 */
public final class Dialogs {

    /**
     * Only static helpers; no objects of this class are created.
     */
    private Dialogs() {
    }

    /**
     * @param parent  owner component
     * @param problem what went wrong
     * @param fix     what the user can do about it
     */
    public static void error(Component parent, String problem, String fix) {
        JOptionPane.showMessageDialog(parent, problem + "\n\n" + fix, "Problem", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Shown for unexpected failures instead of crashing (NFR-24).
     *
     * @param parent owner component
     * @param error  the failure
     */
    public static void unexpected(Component parent, Throwable error) {
        error(parent, "Something unexpected went wrong: " + error.getMessage(),
                "You can keep playing or return to the main menu. If this repeats, please report it to the team.");
    }

    /**
     * @param parent  owner component
     * @param message information to show
     */
    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Panther Ludo", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * @param parent   owner component
     * @param question yes/no question
     * @return {@code true} if the user confirmed
     */
    public static boolean confirm(Component parent, String question) {
        return JOptionPane.showConfirmDialog(parent, question, "Please confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }
}
