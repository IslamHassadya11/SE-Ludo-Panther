package view;

import javax.swing.Timer;
import java.awt.Component;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One shared ~30 fps frame clock for all continuous animations (LED lights, glows, countdown ring). Components read
 * {@link #seconds()} while painting, so animations are time-based and independent of frame rate. Hidden components are
 * not repainted, and the timer stops when nobody is subscribed.
 */
public final class Animator {

    private static final long START = System.nanoTime();
    private static final List<Component> SUBSCRIBERS = new CopyOnWriteArrayList<>();
    private static final Timer TIMER = new Timer(33, e -> frame());

    /**
     * Only static helpers; no objects of this class are created.
     */
    private Animator() {
    }

    /**
     * @return seconds since the application started, for animation phases
     */
    public static double seconds() {
        return (System.nanoTime() - START) / 1e9;
    }

    /**
     * @param component component to repaint every frame while it is showing
     */
    public static void subscribe(Component component) {
        if (!SUBSCRIBERS.contains(component)) {
            SUBSCRIBERS.add(component);
        }
        if (!TIMER.isRunning()) {
            TIMER.start();
        }
    }

    /**
     * @param component component to stop animating
     */
    public static void unsubscribe(Component component) {
        SUBSCRIBERS.remove(component);
        if (SUBSCRIBERS.isEmpty()) {
            TIMER.stop();
        }
    }

    /**
     * Stops animating every subscribed component inside a container (used before a window is rebuilt or closed).
     *
     * @param root a window or panel
     */
    public static void unsubscribeWithin(java.awt.Container root) {
        for (Component component : SUBSCRIBERS) {
            if (component == root || javax.swing.SwingUtilities.isDescendingFrom(component, root)) {
                unsubscribe(component);
            }
        }
    }

    /**
     * One animation frame: repaints every subscribed component that is on screen.
     */
    private static void frame() {
        for (Component component : SUBSCRIBERS) {
            if (component.isShowing()) {
                component.repaint();
            }
        }
    }

    /**
     * @param t progress 0–1
     * @return smooth ease-in-out value 0–1
     */
    public static double ease(double t) {
        double x = Math.max(0, Math.min(1, t));
        return x * x * (3 - 2 * x);
    }

    /**
     * @param speed cycles per second
     * @return a 0–1 pulse that follows a sine wave
     */
    public static double pulse(double speed) {
        return 0.5 + 0.5 * Math.sin(seconds() * speed * 2 * Math.PI);
    }
}
