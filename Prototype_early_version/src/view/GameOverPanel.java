package view;

import model.Player;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.util.List;

/**
 * Game-over screen: the winner's crowned panther, the final ranking and the game time (FR-55).
 */
public final class GameOverPanel extends Neon.Background {

    private final PantherAvatar winnerAvatar = new PantherAvatar(null, 120);
    private final Neon.Title winnerTitle = new Neon.Title(" ", 34);
    private final JPanel rankingList = new JPanel();
    private final PillLabel durationPill = new PillLabel(" ", UiTheme.neonCyan());
    private final Neon.Button newGame = new Neon.Button("New Game", UiTheme.neonPurple(), true);
    private final Neon.Button menu = new Neon.Button("Main Menu", UiTheme.neonCyan(), false);

    /**
     * Builds the screen.
     */
    public GameOverPanel() {
        super(new GridBagLayout());
        Neon.Card card = new Neon.Card(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel caption = new JLabel("GAME OVER");
        caption.setFont(UiTheme.SMALL);
        caption.setForeground(UiTheme.textMuted());
        center(caption);
        card.add(caption);
        Crown crown = new Crown();
        center(crown);
        card.add(crown);
        winnerAvatar.setGlow(0.9);
        center(winnerAvatar);
        card.add(winnerAvatar);
        center(winnerTitle);
        card.add(winnerTitle);
        card.add(Box.createVerticalStrut(10));

        rankingList.setOpaque(false);
        rankingList.setLayout(new BoxLayout(rankingList, BoxLayout.Y_AXIS));
        center(rankingList);
        card.add(rankingList);
        card.add(Box.createVerticalStrut(14));
        durationPill.setFont(UiTheme.CLOCK);
        center(durationPill);
        card.add(durationPill);
        card.add(Box.createVerticalStrut(18));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttons.setOpaque(false);
        buttons.add(menu);
        buttons.add(newGame);
        center(buttons);
        card.add(buttons);
        add(card);
    }

    /**
     * @param ranking  players from first to last place
     * @param duration game duration
     */
    public void showResult(List<Player> ranking, Duration duration) {
        Player winner = ranking.get(0);
        winnerAvatar.setColor(UiTheme.colorOf(winner.color()));
        winnerTitle.setText(winner.name() + " wins!");
        rankingList.removeAll();
        for (int i = 0; i < ranking.size(); i++) {
            rankingList.add(rankRow(i + 1, ranking.get(i)));
            rankingList.add(Box.createVerticalStrut(4));
        }
        durationPill.setText("Game time  " + GameMessages.hms(duration));
        rankingList.revalidate();
        revalidate();
        repaint();
    }

    /**
     * One line of the ranking: place, panther and name with color (FR-55).
     */
    private JComponent rankRow(int place, Player player) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        row.setOpaque(false);
        JLabel placeLabel = new JLabel(GameMessages.ordinal(place));
        placeLabel.setFont(UiTheme.HEADING);
        placeLabel.setForeground(place == 1 ? UiTheme.pantherGold() : UiTheme.textMuted());
        placeLabel.setPreferredSize(new Dimension(54, 30));
        PantherAvatar avatar = new PantherAvatar(UiTheme.colorOf(player.color()), 38);
        avatar.setGlow(place == 1 ? 0.7 : 0.2);
        String suffix = player.hasFinishedAll() ? "" : "  - did not finish";
        JLabel name = new JLabel(player.name() + " (" + player.color().name() + ")" + suffix);
        name.setFont(place == 1 ? UiTheme.font(18, true) : UiTheme.BASE);
        name.setForeground(UiTheme.text());
        row.add(placeLabel);
        row.add(avatar);
        row.add(name);
        row.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        row.setMaximumSize(new Dimension(520, 46));
        row.setPreferredSize(new Dimension(460, 46));
        return row;
    }

    /**
     * Centers a component in a vertical box.
     */
    private static void center(JComponent component) {
        component.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    /**
     * @param handler called when New Game is pressed
     */
    public void setOnNewGame(Runnable handler) {
        newGame.addActionListener(e -> handler.run());
    }

    /**
     * @param handler called when Main Menu is pressed
     */
    public void setOnMenu(Runnable handler) {
        menu.addActionListener(e -> handler.run());
    }

    /** Glowing golden crown above the winner. */
    private static final class Crown extends JComponent {
        Crown() {
            setPreferredSize(new Dimension(90, 40));
            setMaximumSize(getPreferredSize());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int cx = w / 2;
            Polygon crown = new Polygon(
                    new int[]{cx - 30, cx - 30, cx - 15, cx, cx + 15, cx + 30, cx + 30},
                    new int[]{36, 12, 24, 6, 24, 12, 36}, 7);
            Glow.halo(g2, cx, 24, 46, UiTheme.pantherGold(), 0.5 + 0.4 * Animator.pulse(0.8));
            g2.setColor(UiTheme.pantherGold());
            g2.fill(crown);
            g2.setColor(new Color(0x7A5A00));
            g2.draw(crown);
            for (int x : new int[]{cx - 30, cx, cx + 30}) {
                Glow.led(g2, x, x == cx ? 6 : 12, 2, Color.WHITE, 0.9);
            }
            g2.dispose();
        }
    }
}
