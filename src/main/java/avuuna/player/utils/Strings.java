package avuuna.player.utils;

public class Strings {

    public static final String APP_TITLE = "Music Player - by Avuuna, la Luz del Alba";

    public static final String FILE_MENU = "File";
    public static final String OPEN_ITEM = "Open...";
    public static final String OPEN_FOLDER_ITEM = "Open folder...";
    public static final String EXIT_ITEM = "Exit";
    public static final String HELP_MENU = "Help";
    public static final String SHORTCUTS_ITEM = "Keyboard shortcuts";
    public static final String ABOUT_ITEM = "About";

    public static final String PLAYER_PANEL_TITLE = "Player";
    public static final String VOLUME_PANEL_TITLE = "Volume";
    public static final String NO_SONG_LOADED = "No song loaded — press Play or drop music here";

    public static final String PLAY = "Play (Space)";
    public static final String PAUSE = "Pause (Space)";
    public static final String STOP = "Stop";
    public static final String NEXT = "Next (Ctrl+Right)";
    public static final String PREV = "Previous / restart (Ctrl+Left)";

    public static final String REPEAT_ALL = "Repeat: all (click for repeat one)";
    public static final String REPEAT_ONE = "Repeat: one (click to turn off)";
    public static final String NO_REPEAT = "Repeat: off (click for repeat all)";

    public static final String SHUFFLE_ON = "Shuffle: on (click to turn off)";
    public static final String SHUFFLE_OFF = "Shuffle: off (click to turn on)";

    public static final String PLAYLIST_TITLE = "Playlist";
    public static final String ADD_SONG_TOOLTIP = "Add songs (Ctrl+O)";
    public static final String REMOVE_SONG_TOOLTIP = "Remove selected (Delete)";
    public static final String CLEAR_PLAYLIST_TOOLTIP = "Clear playlist";
    public static final String MOVE_UP_TOOLTIP = "Move up (Ctrl+Up)";
    public static final String MOVE_DOWN_TOOLTIP = "Move down (Ctrl+Down)";
    public static final String VOLUME_TOOLTIP = "Volume (scroll to adjust)";
    public static final String SEEK_TOOLTIP = "Click or drag to seek (Shift+Left/Right: ±5 s)";

    public static final String EMPTY_PLAYLIST_LINE1 = "Drop MP3 files or folders here";
    public static final String EMPTY_PLAYLIST_LINE2 = "or press + to add songs";

    public static final String OF = " / ";
    public static final String ZERO_TIME = "00:00 / 00:00";
    public static final String ZERO = "00:00";

    public static final String CONFIRM_TITLE = "Confirm";
    public static final String CONFIRM_CLEAR_MSG = "Are you sure you want to clear the entire playlist?";

    public static final String SKIPPED_DUPLICATES = "%d song(s) were already in the playlist and were skipped.";
    public static final String FAILED_TO_OPEN = "%d file(s) could not be opened.";
    public static final String NO_MP3_FOUND = "No MP3 files were found in what you added.";

    public static final String SHORTCUTS_TEXT =
            "<html><table cellpadding='2'>"
            + "<tr><td><b>Space</b></td><td>Play / pause</td></tr>"
            + "<tr><td><b>Enter</b></td><td>Play selected song</td></tr>"
            + "<tr><td><b>Ctrl+Left / Ctrl+Right</b></td><td>Previous / next song</td></tr>"
            + "<tr><td><b>Shift+Left / Shift+Right</b></td><td>Seek back / forward 5 s</td></tr>"
            + "<tr><td><b>Ctrl+Up / Ctrl+Down</b></td><td>Move selected songs</td></tr>"
            + "<tr><td><b>Delete</b></td><td>Remove selected songs</td></tr>"
            + "<tr><td><b>Ctrl+O</b></td><td>Add songs</td></tr>"
            + "<tr><td><b>Ctrl+Shift+O</b></td><td>Add a folder</td></tr>"
            + "<tr><td><b>Ctrl+R / Ctrl+S</b></td><td>Cycle repeat / toggle shuffle</td></tr>"
            + "</table><br>You can also drag files or folders onto the window.</html>";

    public static final String ABOUT_TEXT =
            "<html><b>Music Player</b><br>by Avuuna, la Luz del Alba<br><br>"
            + "MP3 playback with Java Swing.</html>";
}
