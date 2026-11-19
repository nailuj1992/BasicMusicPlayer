package avuuna.player.model;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Saves and restores the player session between runs: the playlist (as an
 * extended M3U file, so it can also be opened by other players) and a few
 * settings (current song, volume, repeat and shuffle modes).
 */
public class SessionStore {

    private static final String PLAYLIST_FILE = "playlist.m3u8";
    private static final String SETTINGS_FILE = "settings.properties";

    private static final String KEY_CURRENT = "currentIndex";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_REPEAT = "repeat";
    private static final String KEY_SHUFFLE = "shuffle";

    private static final String REPEAT_NONE = "none";
    private static final String REPEAT_ALL = "all";
    private static final String REPEAT_ONE = "one";

    /**
     * Snapshot of a session. {@code repeatMode} follows {@link MusicPlayer}:
     * {@code true} = all, {@code false} = one, {@code null} = none.
     */
    public record Session(List<Path> songs, int currentIndex, int volume, Boolean repeatMode, boolean shuffle) {
        public static Session empty() {
            return new Session(List.of(), -1, 50, null, false);
        }
    }

    private final Path dir;

    public SessionStore() {
        this(defaultDirectory());
    }

    public SessionStore(Path dir) {
        this.dir = dir;
    }

    /**
     * {@code -Dmusicplayer.dataDir=...} overrides the location (handy for testing);
     * otherwise {@code %APPDATA%\AvuunaMusicPlayer}, or the home folder if APPDATA is unset.
     */
    public static Path defaultDirectory() {
        String override = System.getProperty("musicplayer.dataDir");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        String appData = System.getenv("APPDATA");
        Path base = appData != null ? Paths.get(appData) : Paths.get(System.getProperty("user.home"));
        return base.resolve("AvuunaMusicPlayer");
    }

    public void save(Session session) throws IOException {
        Files.createDirectories(dir);
        // Absolute paths: the session file lives in %APPDATA%, far from the music.
        PlaylistFile.write(dir.resolve(PLAYLIST_FILE), session.songs(), false);

        Properties props = new Properties();
        props.setProperty(KEY_CURRENT, Integer.toString(session.currentIndex()));
        props.setProperty(KEY_VOLUME, Integer.toString(session.volume()));
        props.setProperty(KEY_REPEAT, encodeRepeat(session.repeatMode()));
        props.setProperty(KEY_SHUFFLE, Boolean.toString(session.shuffle()));
        try (Writer w = Files.newBufferedWriter(dir.resolve(SETTINGS_FILE), StandardCharsets.UTF_8)) {
            props.store(w, "Music Player settings");
        }
    }

    /**
     * Loads the last session. Songs whose files no longer exist are skipped and the
     * current index is adjusted accordingly. Returns an empty session if nothing was saved.
     */
    public Session load() {
        Properties props = new Properties();
        Path settings = dir.resolve(SETTINGS_FILE);
        if (Files.isRegularFile(settings)) {
            try (Reader r = Files.newBufferedReader(settings, StandardCharsets.UTF_8)) {
                props.load(r);
            } catch (IOException ignored) {
                // fall back to defaults
            }
        }

        List<Path> saved = new ArrayList<>();
        Path playlist = dir.resolve(PLAYLIST_FILE);
        if (Files.isRegularFile(playlist)) {
            try {
                saved.addAll(PlaylistFile.read(playlist));
            } catch (IOException | RuntimeException ignored) {
                saved.clear();
            }
        }

        int savedCurrent = parseInt(props.getProperty(KEY_CURRENT), -1);
        List<Path> songs = new ArrayList<>();
        int current = -1;
        for (int i = 0; i < saved.size(); i++) {
            if (Files.isRegularFile(saved.get(i))) {
                if (i == savedCurrent) {
                    current = songs.size();
                }
                songs.add(saved.get(i));
            }
        }
        if (current < 0 && !songs.isEmpty() && savedCurrent >= 0) {
            current = 0;
        }

        int volume = Math.max(0, Math.min(100, parseInt(props.getProperty(KEY_VOLUME), 50)));
        Boolean repeat = decodeRepeat(props.getProperty(KEY_REPEAT, REPEAT_NONE));
        boolean shuffle = Boolean.parseBoolean(props.getProperty(KEY_SHUFFLE, "false"));
        return new Session(songs, current, volume, repeat, shuffle);
    }

    private static String encodeRepeat(Boolean mode) {
        if (mode == null) return REPEAT_NONE;
        return mode ? REPEAT_ALL : REPEAT_ONE;
    }

    private static Boolean decodeRepeat(String value) {
        if (REPEAT_ALL.equals(value)) return Boolean.TRUE;
        if (REPEAT_ONE.equals(value)) return Boolean.FALSE;
        return null;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
