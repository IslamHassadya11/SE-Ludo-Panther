package controller;

import view.*;

import javax.swing.SwingUtilities;

/**
 * The program's starting point. It shows nothing itself: it prepares Swing, creates the {@link AppController} that
 * connects the screens (View) to the game (Model), and starts it. That makes it part of the Controller in MVC.
 */
public final class LudoApp {

    /**
     * Only the static {@code main} method is used; no objects are created.
     */
    private LudoApp() {
    }

    /**
     * Starts the program on the Swing event thread. Unexpected errors are shown in a dialog instead of crashing
     * (NFR-24).
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                SwingUtilities.invokeLater(() -> Dialogs.unexpected(null, error)));
        SwingUtilities.invokeLater(() -> {
            UiTheme.setUp();
            new AppController().start();
        });
    }
}
