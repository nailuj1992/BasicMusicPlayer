package avuuna.player.test;

import avuuna.player.exception.*;
import avuuna.player.model.*;
import javazoom.jl.player.basic.*;
import org.junit.*;
import static org.junit.Assert.*;

import java.net.URL;
import java.nio.file.Paths;

public class PlayerTest {

    private MusicPlayer player;
    private Song song1, song2, song3;
    private int sleep;

    @Before
    public void setUp() {
        player = MusicPlayer.getInstance();

        song1 = new Song(musicPath("14 - Vale Healing.mp3"));
        song2 = new Song(musicPath("06 - Nemesis.mp3"));
        song3 = new Song(musicPath("03 - Times Change.mp3"));

        sleep = 500;
    }

    private static String musicPath(String filename) {
        try {
            URL url = PlayerTest.class.getClassLoader().getResource("music/" + filename);
            return Paths.get(url.toURI()).toString();
        } catch (Exception e) {
            throw new RuntimeException("Test resource not found: music/" + filename, e);
        }
    }

    @After
    public void tearDown() {
        try {
            player.clearPlaylist();
            song1 = null;
            song2 = null;
            song3 = null;
        } catch (BasicPlayerException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void playSingleSong() {
        try {
            player.setCurrentSong(song1);
            player.open(player.getCurrentSong());
            player.play();
            Thread.sleep(sleep);
        } catch (BasicPlayerException | InterruptedException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void playTwoSongs() {
        try {
            player.addSong(song1);
            player.addSong(song2);
            player.setCurrentSong(player.getSong(0));
            player.open(player.getCurrentSong());
            player.play();
        } catch (PlayerException | BasicPlayerException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void noAddRepeatedSong() {
        try {
            player.addSong(song1);
            player.addSong(song2);
            player.addSong(song1);
        } catch (PlayerException ex) {
            assertTrue(ex.getMessage().equals(PlayerException.REPEATED_SONG));
        }
    }

    @Test
    public void playNextSong() {
        try {
            player.addSong(song1);
            player.addSong(song2);
            player.addSong(song3);
            player.setCurrentSong(player.getSong(0));
            player.open(player.getCurrentSong());
            player.play();
            Thread.sleep(sleep);
            player.next(true);
            Thread.sleep(sleep);
            player.next(true);
            Thread.sleep(sleep);
            player.next(true);
            Thread.sleep(sleep);
        } catch (PlayerException | BasicPlayerException | InterruptedException ex) {
            fail(ex.getMessage());
        }
    }

    /**
     * Regression: one song + shuffle + repeat all used to hang forever at end of song.
     */
    @Test(timeout = 5000)
    public void endOfSongWithSingleSongShuffleAndRepeatAllDoesNotHang() throws Exception {
        try {
            player.addSong(song1);
            player.setCurrentSong(song1);
            player.open(song1);
            player.setRandomMode(true);
            player.setRepeatMode(Boolean.TRUE);

            player.stateUpdated(new BasicPlayerEvent(this, BasicPlayerEvent.EOM, -1, -1, null));

            assertEquals(song1, player.getCurrentSong());
        } finally {
            player.setRandomMode(false);
            player.setRepeatMode(null);
        }
    }

    @Test
    public void removingCurrentWhileStoppedLoadsNextWithoutPlaying() throws Exception {
        player.addSong(song1);
        player.addSong(song2);
        player.addSong(song3);
        player.load(song1);

        player.removeSongs(java.util.List.of(song1, song2));

        assertEquals(song3, player.getCurrentSong());
        assertEquals(1, player.getSongCount());
        assertFalse(player.shouldAutoPlay());
        assertFalse(player.isPlaying());
    }

    @Test
    public void removingEverySongClearsCurrent() throws Exception {
        player.addSong(song1);
        player.addSong(song2);
        player.load(song2);

        player.removeSongs(java.util.List.of(song1, song2));

        assertNull(player.getCurrentSong());
        assertEquals(-1, player.getCurrentIndex());
    }

    @Test
    public void shuffleNextNeverRepeatsCurrentSong() throws Exception {
        try {
            player.addSong(song1);
            player.addSong(song2);
            player.addSong(song3);
            player.load(song1);
            player.setRandomMode(true);
            for (int i = 0; i < 10; i++) {
                Song before = player.getCurrentSong();
                player.skipForward();
                assertNotEquals(before, player.getCurrentSong());
                player.stop();
            }
        } finally {
            player.setRandomMode(false);
        }
    }

    @Test
    public void playPreviousSong() {
        try {
            player.addSong(song1);
            player.addSong(song2);
            player.addSong(song3);
            player.setCurrentSong(player.getSong(0));
            player.open(player.getCurrentSong());
            player.play();
            Thread.sleep(sleep);
            player.previous(true);
            Thread.sleep(sleep);
            player.previous(true);
            Thread.sleep(sleep);
            player.previous(true);
            Thread.sleep(sleep);
        } catch (PlayerException | BasicPlayerException | InterruptedException ex) {
            fail(ex.getMessage());
        }
    }
}
