package avuuna.player.controller;

import avuuna.player.exception.PlayerException;
import avuuna.player.model.MusicPlayer;
import avuuna.player.model.PlaylistFile;
import avuuna.player.model.SessionStore;
import avuuna.player.model.SessionStore.Session;
import avuuna.player.model.Song;
import avuuna.player.utils.ModelObserver;
import avuuna.player.utils.Strings;
import avuuna.player.utils.Utils;
import avuuna.player.view.GUIPlayer;
import avuuna.player.view.GUIPlayer.PlaylistItem;
import javazoom.jl.player.basic.BasicPlayerEvent;
import javazoom.jl.player.basic.BasicPlayerException;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * MVC Controller. Wires user actions from the view to the model and reflects
 * model state changes back into the view via the Observer pattern.
 */
public class PlayerController implements Serializable, ModelObserver {
    private static final long serialVersionUID = 232492703123683857L;

    private static final int PROGRESS_REFRESH_MS = 200;
    /** "Previous" restarts the current song when it has played longer than this. */
    private static final long RESTART_THRESHOLD_US = 3_000_000L;
    /** Step for Shift+Left / Shift+Right seeking. */
    private static final long SEEK_STEP_US = 5_000_000L;
    private static final int MAX_FOLDER_DEPTH = 10;

    private static PlayerController controller = null;

    private final MusicPlayer model;
    private final GUIPlayer view;
    private final transient SessionStore sessionStore;

    private boolean running;
    /** True while the user is dragging on the progress bar, so the timer doesn't fight it. */
    private boolean scrubbing;

    public static PlayerController getInstance() {
        if (controller == null) {
            controller = new PlayerController();
        }
        return controller;
    }

    private PlayerController() {
        model = MusicPlayer.getInstance();
        // Audio-library callbacks (e.g. end of song) arrive on background threads;
        // run them on the EDT so model state and Swing are only touched from one thread.
        model.setEventExecutor(SwingUtilities::invokeLater);
        view = new GUIPlayer();
        sessionStore = new SessionStore();
        bindActions();
        bindShortcuts();
    }

    /**
     * Starts the player. {@code onClose} is called when the user closes the window,
     * so the entry point can manage the application lifecycle.
     */
    public final void start(Runnable onClose) {
        running = false;
        model.addObserver(this);

        Runnable exit = () -> {
            saveSession();
            onClose.run();
        };
        view.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit.run();
            }
        });
        view.addExitMenuListener(e -> exit.run());

        restoreSession();
        update();
        startProgressTimer();
        view.initView();
    }

    // -------------------------------------------------------------------------
    // Wiring
    // -------------------------------------------------------------------------

    private void bindActions() {
        view.setOpenMenuAccelerator(KeyStroke.getKeyStroke("ctrl O"));
        view.setOpenFolderMenuAccelerator(KeyStroke.getKeyStroke("ctrl shift O"));
        view.addOpenMenuListener(e -> openSongDialog());
        view.addOpenFolderMenuListener(e -> openFolderDialog());
        view.setOpenPlaylistMenuAccelerator(KeyStroke.getKeyStroke("ctrl L"));
        view.setSavePlaylistMenuAccelerator(KeyStroke.getKeyStroke("ctrl shift S"));
        view.addOpenPlaylistMenuListener(e -> openPlaylistDialog());
        view.addSavePlaylistMenuListener(e -> savePlaylistDialog());
        view.addShortcutsMenuListener(e -> view.showShortcutsDialog());
        view.addAboutMenuListener(e -> view.showAboutDialog());
        view.addAddSongListener(e -> openSongDialog());

        view.addPlayListener(e -> togglePlayPause());
        view.addStopListener(e -> stopSong());
        view.addNextListener(e -> nextSong());
        view.addPrevListener(e -> previousSong());
        view.addRepeatListener(e -> cycleRepeat());
        view.addShuffleListener(e -> toggleShuffle());
        view.addVolumeChangeListener(e -> setVolume());

        view.addSeekListener(this::previewSeek, this::commitSeek);
        view.setFileDropHandler(this::addFiles);

        view.addSongListMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(evt)) {
                    int index = view.getPlaylistIndexAt(evt.getPoint());
                    if (index >= 0) {
                        playSongAt(index);
                    }
                }
            }
        });
        view.addSongListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshControlState();
            }
        });

        view.addRemoveSongListener(e -> removeSelected());
        view.addClearPlaylistListener(e -> clearPlaylist());
        view.addMoveUpListener(e -> moveSelection(-1));
        view.addMoveDownListener(e -> moveSelection(+1));
    }

    private void bindShortcuts() {
        view.bindShortcut(KeyStroke.getKeyStroke("SPACE"), "playPause", this::togglePlayPause);
        view.bindShortcut(KeyStroke.getKeyStroke("ENTER"), "playSelected", this::playSelected);
        view.bindShortcut(KeyStroke.getKeyStroke("DELETE"), "removeSelected", this::removeSelected);
        view.bindShortcut(KeyStroke.getKeyStroke("ctrl LEFT"), "previous", this::previousSong);
        view.bindShortcut(KeyStroke.getKeyStroke("ctrl RIGHT"), "next", this::nextSong);
        view.bindShortcut(KeyStroke.getKeyStroke("shift LEFT"), "seekBack", () -> seekBy(-SEEK_STEP_US));
        view.bindShortcut(KeyStroke.getKeyStroke("shift RIGHT"), "seekForward", () -> seekBy(SEEK_STEP_US));
        view.bindShortcut(KeyStroke.getKeyStroke("ctrl UP"), "moveUp", () -> moveSelection(-1));
        view.bindShortcut(KeyStroke.getKeyStroke("ctrl DOWN"), "moveDown", () -> moveSelection(+1));
        view.bindShortcut(KeyStroke.getKeyStroke("ctrl R"), "repeat", this::cycleRepeat);
        view.bindShortcut(KeyStroke.getKeyStroke("ctrl S"), "shuffle", this::toggleShuffle);
    }

    /**
     * Refreshes the progress bar periodically on the EDT. A Swing Timer replaces the
     * former busy-wait thread, which pinned a CPU core and touched Swing off the EDT.
     */
    private void startProgressTimer() {
        new Timer(PROGRESS_REFRESH_MS, e -> refreshProgress()).start();
    }

    private void refreshProgress() {
        if (scrubbing) {
            return;
        }
        Song current = model.getCurrentSong();
        if (current != null) {
            view.setProgressValue((int) model.getProgressBytes());
            view.setProgressText(Utils.formatTime(model.getProgressTime())
                    + Strings.OF + Utils.formatTime(current.getDuration()));
        } else {
            view.setProgressValue(0);
            view.setProgressText(Strings.ZERO_TIME);
        }
    }

    // -------------------------------------------------------------------------
    // Observer — drives view from model state
    // -------------------------------------------------------------------------

    @Override
    public void update() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::update);
            return;
        }
        Song current = model.getCurrentSong();
        if (current != null) {
            switch (model.getCurrentEvent()) {
                case BasicPlayerEvent.RESUMED:
                    view.setPlayingState(true);
                    break;
                case BasicPlayerEvent.PAUSED:
                    view.setPlayingState(false);
                    break;
                case BasicPlayerEvent.PLAYING:
                    view.setPlayingState(true);
                    running = true;
                    break;
                case BasicPlayerEvent.STOPPED:
                    view.setPlayingState(false);
                    running = false;
                    break;
                case BasicPlayerEvent.OPENED:
                    view.setSongLabel(displayTitle(current));
                    view.setProgressMax((int) current.getBytesLength());
                    view.setProgressText(Strings.ZERO + Strings.OF + Utils.formatTime(current.getDuration()));
                    if (!model.shouldAutoPlay()) {
                        view.setPlayingState(false);
                    } else if (!running) {
                        playSong();
                    }
                    setVolume();
                    break;
                default:
                    break;
            }
        } else {
            running = false;
            view.setSongLabel(null);
            view.setProgressMax(0);
            view.setPlayingState(false);
        }

        view.setRepeatButtonState(model.getRepeatMode());
        view.setShuffleButtonState(model.isRandomMode());
        updatePlaylistDisplay();
        refreshControlState();
    }

    private void updatePlaylistDisplay() {
        List<PlaylistItem> items = new ArrayList<>();
        for (Song song : model.getSongs()) {
            items.add(new PlaylistItem(displayTitle(song), song.getAbsolutePath()));
        }
        view.refreshPlaylist(items, model.getCurrentIndex());
    }

    private void refreshControlState() {
        view.updateControlState(model.getSongCount() > 0,
                view.getSelectedIndices().length > 0,
                model.getCurrentSong() != null);
    }

    /** File name without the extension, e.g. "Song.mp3" -> "Song". */
    private static String displayTitle(Song song) {
        String name = song.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // -------------------------------------------------------------------------
    // Session
    // -------------------------------------------------------------------------

    private void restoreSession() {
        Session session = sessionStore.load();
        for (Path path : session.songs()) {
            try {
                model.addSong(new Song(path.toString()));
            } catch (PlayerException ignored) {
                // duplicate entry in the saved file
            }
        }
        view.setVolumeValue(session.volume());
        model.setRepeatMode(session.repeatMode());

        int index = session.currentIndex();
        if (index >= 0 && index < model.getSongCount()) {
            try {
                model.load(model.getSong(index));
                view.setSelectedIndex(index);
            } catch (BasicPlayerException ex) {
                Utils.log(PlayerController.class.getName(), ex);
                model.setCurrentSong(null);
            }
        }
        model.setRandomMode(session.shuffle());
    }

    private void saveSession() {
        List<Path> songs = model.getSongs().stream().map(File::toPath).toList();
        try {
            sessionStore.save(new Session(songs, model.getCurrentIndex(), view.getVolumeValue(),
                    model.getRepeatMode(), model.isRandomMode()));
        } catch (IOException ex) {
            Utils.log(PlayerController.class.getName(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // Adding songs
    // -------------------------------------------------------------------------

    private void openSongDialog() {
        File[] files = view.showOpenDialog();
        if (files != null && files.length > 0) {
            addFiles(Arrays.asList(files));
        }
    }

    private void openFolderDialog() {
        File dir = view.showOpenFolderDialog();
        if (dir != null) {
            addFiles(List.of(dir));
        }
    }

    // -------------------------------------------------------------------------
    // Playlist files
    // -------------------------------------------------------------------------

    /** File > Open playlist: asks Replace / Add when songs are already loaded. */
    private void openPlaylistDialog() {
        File file = view.showOpenPlaylistDialog();
        if (file == null) {
            return;
        }
        Expansion songs = new Expansion();
        try {
            songs.addPlaylist(PlaylistFile.read(file.toPath()));
        } catch (IOException | RuntimeException ex) {
            Utils.log(PlayerController.class.getName(), new IOException(ex));
            view.showError(PlayerException.ERROR, String.format(Strings.FAILED_TO_READ_PLAYLIST, file.getName()));
            return;
        }

        // Nothing usable in the file: report it without touching the current playlist.
        if (!songs.mp3s.isEmpty() && model.getSongCount() > 0) {
            GUIPlayer.PlaylistOpenMode mode = view.askReplaceOrAdd(file.getName());
            if (mode == null) {
                return;
            }
            if (mode == GUIPlayer.PlaylistOpenMode.REPLACE) {
                try {
                    model.clearPlaylist();
                } catch (BasicPlayerException ex) {
                    fail(PlayerException.ERROR_CLEAR_LIST, ex);
                    return;
                }
            }
        }
        addExpanded(songs);
    }

    /** File > Save playlist as: writes the current songs as .m3u8 (or .m3u). */
    private void savePlaylistDialog() {
        if (model.getSongCount() == 0) {
            return;
        }
        File file = view.showSavePlaylistDialog();
        if (file == null) {
            return;
        }
        if (!PlaylistFile.isPlaylist(file)) {
            file = new File(file.getPath() + PlaylistFile.M3U8);
        }
        List<Path> songs = model.getSongs().stream().map(File::toPath).toList();
        try {
            // Relative paths keep the playlist working if its folder is moved with the music.
            PlaylistFile.write(file.toPath(), songs, true);
        } catch (IOException | RuntimeException ex) {
            Utils.log(PlayerController.class.getName(), new IOException(ex));
            view.showError(PlayerException.ERROR, String.format(Strings.FAILED_TO_SAVE_PLAYLIST, file.getName()));
        }
    }

    /**
     * Adds files, the MP3s inside folders (recursively) and the songs listed in dropped
     * playlist files. Duplicates are skipped. If nothing was loaded yet, the first added
     * song starts playing.
     */
    private void addFiles(List<File> files) {
        addExpanded(expand(files));
    }

    private void addExpanded(Expansion found) {
        List<String> notes = new ArrayList<>();
        if (found.skipped > 0) {
            notes.add(String.format(Strings.SKIPPED_PLAYLIST_ENTRIES, found.skipped,
                    found.skipped == 1 ? "y was" : "ies were"));
        }
        if (found.unreadable > 0) {
            notes.add(String.format(Strings.UNREADABLE_PLAYLISTS, found.unreadable));
        }
        if (found.mp3s.isEmpty()) {
            notes.add(0, Strings.NO_MP3_FOUND);
            view.showInfo(String.join("\n", notes));
            return;
        }

        int duplicates = 0;
        int firstAdded = -1;
        for (File f : found.mp3s) {
            try {
                model.addSong(new Song(f.getAbsolutePath()));
                if (firstAdded < 0) {
                    firstAdded = model.getSongCount() - 1;
                }
            } catch (PlayerException ex) {
                duplicates++;
            }
        }

        if (firstAdded < 0) {
            notes.add(0, String.format(Strings.SKIPPED_DUPLICATES, duplicates));
        }
        if (!notes.isEmpty()) {
            view.showInfo(String.join("\n", notes));
        }
        if (firstAdded < 0) {
            return;
        }
        if (model.getCurrentSong() == null) {
            Song first = model.getSong(firstAdded);
            try {
                model.setCurrentSong(first);
                model.open(first);
            } catch (BasicPlayerException ex) {
                Utils.log(PlayerException.ERROR_OPENING_SONG, ex);
                model.setCurrentSong(null);
                update();
                view.showError(PlayerException.ERROR, String.format(Strings.FAILED_TO_OPEN, 1));
            }
        }
        view.setSelectedIndex(firstAdded);
    }

    /** What a set of dropped/selected files expands to. */
    private static final class Expansion {
        final List<File> mp3s = new ArrayList<>();
        /** Playlist entries that point to a missing file or a non-MP3. */
        int skipped;
        /** Playlist files that could not be read. */
        int unreadable;

        void addPlaylist(List<Path> entries) {
            for (Path entry : entries) {
                File f = entry.toFile();
                if (f.isFile() && isMp3(f)) {
                    mp3s.add(f);
                } else {
                    skipped++;
                }
            }
        }
    }

    private static Expansion expand(List<File> files) {
        Expansion result = new Expansion();
        for (File f : files) {
            if (f.isDirectory()) {
                try (Stream<Path> walk = Files.walk(f.toPath(), MAX_FOLDER_DEPTH)) {
                    walk.filter(Files::isRegularFile)
                        .map(Path::toFile)
                        .filter(PlayerController::isMp3)
                        .sorted()
                        .forEach(result.mp3s::add);
                } catch (IOException | RuntimeException ex) {
                    Utils.log(PlayerController.class.getName(), new IOException(ex));
                }
            } else if (PlaylistFile.isPlaylist(f)) {
                try {
                    result.addPlaylist(PlaylistFile.read(f.toPath()));
                } catch (IOException | RuntimeException ex) {
                    Utils.log(PlayerController.class.getName(), new IOException(ex));
                    result.unreadable++;
                }
            } else if (isMp3(f)) {
                result.mp3s.add(f);
            }
        }
        return result;
    }

    private static boolean isMp3(File f) {
        return f.getName().toLowerCase(Locale.ROOT).endsWith(".mp3");
    }

    // -------------------------------------------------------------------------
    // Playback actions
    // -------------------------------------------------------------------------

    private void togglePlayPause() {
        if (model.getCurrentSong() == null) {
            if (model.getSongCount() == 0) {
                openSongDialog();
            } else {
                int selected = view.getSelectedIndex();
                playSongAt(selected >= 0 ? selected : 0);
            }
        } else if (model.isPlaying()) {
            pauseSong();
        } else if (model.isPaused()) {
            resumeSong();
        } else {
            playSong();
        }
    }

    private void playSelected() {
        int selected = view.getSelectedIndex();
        if (selected >= 0) {
            playSongAt(selected);
        }
    }

    /** Plays the song at {@code index} from the start (also restarts the current one). */
    private void playSongAt(int index) {
        if (index < 0 || index >= model.getSongCount()) {
            return;
        }
        try {
            Song song = model.getSong(index);
            model.setCurrentSong(song);
            model.stop();
            model.clearShuffleHistory();
            model.open(song);
        } catch (BasicPlayerException ex) {
            fail(PlayerException.ERROR_OPENING_SONG, ex);
        }
    }

    private void playSong() {
        try {
            model.play();
        } catch (BasicPlayerException ex) {
            fail(PlayerException.ERROR_PLAYING_SONG, ex);
        }
    }

    private void pauseSong() {
        try {
            model.pause();
        } catch (BasicPlayerException ex) {
            fail(PlayerException.ERROR_PAUSING_SONG, ex);
        }
    }

    private void resumeSong() {
        try {
            model.resume();
        } catch (BasicPlayerException ex) {
            fail(PlayerException.ERROR_RESUMING_SONG, ex);
        }
    }

    private void stopSong() {
        if (model.getCurrentSong() == null) {
            return;
        }
        try {
            model.stop();
        } catch (BasicPlayerException ex) {
            fail(PlayerException.ERROR_STOPPING_SONG, ex);
        }
    }

    private void nextSong() {
        if (model.getCurrentSong() == null) {
            return;
        }
        try {
            model.skipForward();
        } catch (BasicPlayerException ex) {
            fail(PlayerException.ERROR_NEXT_SONG, ex);
        }
    }

    /** Restarts the current song if it has been playing a while, otherwise goes back one. */
    private void previousSong() {
        if (model.getCurrentSong() == null) {
            return;
        }
        try {
            if (model.getProgressTime() > RESTART_THRESHOLD_US) {
                model.replayCurrent();
            } else {
                model.previous(true);
            }
        } catch (BasicPlayerException ex) {
            fail(PlayerException.ERROR_PREV_SONG, ex);
        }
    }

    private void cycleRepeat() {
        // none -> all -> one -> none
        Boolean current = model.getRepeatMode();
        if (current == null) {
            model.setRepeatMode(Boolean.TRUE);
        } else if (current) {
            model.setRepeatMode(Boolean.FALSE);
        } else {
            model.setRepeatMode(null);
        }
    }

    private void toggleShuffle() {
        model.setRandomMode(!model.isRandomMode());
    }

    private void setVolume() {
        try {
            model.setVolume((double) view.getVolumeValue() / 100);
        } catch (BasicPlayerException ex) {
            // Silent — volume errors occur frequently when no song is loaded
        }
    }

    // -------------------------------------------------------------------------
    // Seeking
    // -------------------------------------------------------------------------

    private void previewSeek(double ratio) {
        Song current = model.getCurrentSong();
        if (current == null) {
            return;
        }
        scrubbing = true;
        view.setProgressValue((int) (ratio * view.getProgressMax()));
        view.setProgressText(Utils.formatTime((long) (ratio * current.getDuration()))
                + Strings.OF + Utils.formatTime(current.getDuration()));
    }

    private void commitSeek(double ratio) {
        scrubbing = false;
        Song current = model.getCurrentSong();
        if (current == null) {
            return;
        }
        try {
            model.seek((long) (ratio * current.getBytesLength()));
        } catch (BasicPlayerException ex) {
            Utils.display("Seek error: " + ex.getMessage());
        }
        refreshProgress();
    }

    private void seekBy(long deltaMicros) {
        Song current = model.getCurrentSong();
        if (current == null || current.getDuration() <= 0 || current.getBytesLength() <= 0) {
            return;
        }
        double ratio = model.getProgressBytes() / current.getBytesLength()
                + (double) deltaMicros / current.getDuration();
        commitSeek(Math.max(0, Math.min(1, ratio)));
    }

    // -------------------------------------------------------------------------
    // Playlist editing
    // -------------------------------------------------------------------------

    private void removeSelected() {
        int[] indices = view.getSelectedIndices();
        if (indices.length == 0) {
            return;
        }
        List<Song> songs = new ArrayList<>();
        for (int i : indices) {
            songs.add(model.getSong(i));
        }
        int first = Arrays.stream(indices).min().getAsInt();
        try {
            model.removeSongs(songs);
        } catch (BasicPlayerException ex) {
            fail(PlayerException.ERROR_REMOVE_SONG, ex);
        }
        int remaining = model.getSongCount();
        if (remaining > 0) {
            view.setSelectedIndex(Math.min(first, remaining - 1));
        }
    }

    private void clearPlaylist() {
        if (model.getSongCount() == 0 || !view.confirm(Strings.CONFIRM_CLEAR_MSG)) {
            return;
        }
        try {
            model.clearPlaylist();
        } catch (BasicPlayerException ex) {
            fail(PlayerException.ERROR_CLEAR_LIST, ex);
        }
    }

    private void moveSelection(int direction) {
        int[] indices = view.getSelectedIndices();
        if (indices.length == 0) {
            return;
        }
        Arrays.sort(indices);
        Set<Integer> selected = new HashSet<>();
        for (int idx : indices) selected.add(idx);

        int size = model.getSongCount();
        int[] result = new int[indices.length];
        int start = direction < 0 ? 0 : indices.length - 1;
        int end   = direction < 0 ? indices.length : -1;
        int step  = direction < 0 ? 1 : -1;

        for (int i = start; i != end; i += step) {
            int idx = indices[i];
            int target = idx + direction;
            boolean inBounds = target >= 0 && target < size;
            if (inBounds && !selected.contains(target)) {
                model.swapSongs(idx, target);
                result[i] = target;
                selected.remove(idx);
                selected.add(target);
            } else {
                result[i] = idx;
            }
        }
        view.setSelectedIndices(result);
    }

    private void fail(String message, Exception ex) {
        Utils.log(message, ex);
        view.showError(PlayerException.ERROR, message);
    }
}
