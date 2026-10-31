package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * The single application window; screens are swapped with a {@link CardLayout}.
 */
public final class MainFrame extends JFrame {

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private Runnable onCloseRequested = this::dispose;

    /**
     * Creates the window sized for 1280×720 screens (NFR-01).
     */
    public MainFrame() {
        super("Panther Ludo");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onCloseRequested.run();
            }
        });
        content.setBackground(UiTheme.background());
        setContentPane(content);
        setMinimumSize(new Dimension(1024, 640));
        setSize(new Dimension(1280, 720));
        setLocationRelativeTo(null);
    }

    /**
     * @param name   screen key
     * @param screen screen component
     */
    public void addScreen(String name, JPanel screen) {
        content.add(screen, name);
    }

    /**
     * @param name screen key to display
     */
    public void showScreen(String name) {
        cards.show(content, name);
    }

    /**
     * @param handler called when the user closes the window
     */
    public void setOnCloseRequested(Runnable handler) {
        this.onCloseRequested = handler;
    }
}
