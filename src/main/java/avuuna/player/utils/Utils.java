package avuuna.player.utils;

import avuuna.player.exception.LookAndFeelException;

import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Utils {

    public static final String NIMBUS_LOOK_AND_FEEL = "Nimbus";
    public static final String WINDOWS_LOOK_AND_FEEL = "Windows";

    private static final Random RANDOM = new Random();

    public static void display() {
        System.out.println();
    }

    public static void display(Object msg) {
        System.out.println(msg);
    }

    public static void display(String msg) {
        System.out.println(msg);
    }

    public static void display(boolean msg) {
        System.out.println(msg);
    }

    public static void display(char msg) {
        System.out.println(msg);
    }

    public static void display(char[] msg) {
        System.out.println(msg);
    }

    public static void display(double msg) {
        System.out.println(msg);
    }

    public static void display(float msg) {
        System.out.println(msg);
    }

    public static void display(int msg) {
        System.out.println(msg);
    }

    public static void display(long msg) {
        System.out.println(msg);
    }

    public static void log(String name, Exception ex) {
        Logger.getLogger(name).log(Level.SEVERE, null, ex);
    }

    public static void setLookAndFeel(String lookAndFeel) throws LookAndFeelException {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if (lookAndFeel.equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | UnsupportedLookAndFeelException ex) {
            throw new LookAndFeelException(ex.getMessage());
        }
    }

    /**
     * Converts microseconds to {@code mm:ss} format.
     */
    public static String formatTime(long microseconds) {
        int seconds = (int) (microseconds / 1_000_000);
        int minutes = seconds / 60;
        seconds %= 60;
        String result = (minutes < 10 ? "0" : "") + minutes + ":";
        result += (seconds < 10 ? "0" : "") + seconds;
        return result;
    }

    public static String getStackTrace(Exception ex) {
        StringWriter errors = new StringWriter();
        ex.printStackTrace(new PrintWriter(errors));
        return errors.toString();
    }

    /**
     * Returns a random int in {@code [start, end]} that is not in {@code exclude}.
     * Picks from the remaining candidates directly, so it always terminates.
     *
     * @throws IllegalArgumentException if every value in the range is excluded
     */
    public static int getRandomWithExclusion(int start, int end, int... exclude) {
        Set<Integer> excludes = new HashSet<>();
        for (int ex : exclude) {
            excludes.add(ex);
        }

        List<Integer> candidates = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            if (!excludes.contains(i)) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("All values in [" + start + ", " + end + "] are excluded");
        }
        return candidates.get(RANDOM.nextInt(candidates.size()));
    }
}
