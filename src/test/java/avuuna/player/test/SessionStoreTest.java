package avuuna.player.test;

import avuuna.player.model.SessionStore;
import avuuna.player.model.SessionStore.Session;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SessionStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void roundTripKeepsPlaylistAndSettings() throws Exception {
        Path a = Files.createFile(tmp.getRoot().toPath().resolve("Canción a.mp3"));
        Path b = Files.createFile(tmp.getRoot().toPath().resolve("b.mp3"));
        SessionStore store = new SessionStore(tmp.newFolder("data").toPath());

        store.save(new Session(List.of(a, b), 1, 73, Boolean.FALSE, true));
        Session loaded = store.load();

        assertEquals(List.of(a.toAbsolutePath(), b.toAbsolutePath()), loaded.songs());
        assertEquals(1, loaded.currentIndex());
        assertEquals(73, loaded.volume());
        assertEquals(Boolean.FALSE, loaded.repeatMode());
        assertTrue(loaded.shuffle());
    }

    @Test
    public void missingFilesAreSkippedAndCurrentIndexFollows() throws Exception {
        Path gone = tmp.getRoot().toPath().resolve("deleted.mp3");
        Path kept = Files.createFile(tmp.getRoot().toPath().resolve("kept.mp3"));
        SessionStore store = new SessionStore(tmp.newFolder("data").toPath());

        store.save(new Session(List.of(gone, kept), 1, 50, null, false));
        Session loaded = store.load();

        assertEquals(List.of(kept.toAbsolutePath()), loaded.songs());
        assertEquals(0, loaded.currentIndex());
    }

    @Test
    public void emptyDirectoryGivesDefaults() throws Exception {
        Session loaded = new SessionStore(tmp.newFolder("empty").toPath()).load();

        assertTrue(loaded.songs().isEmpty());
        assertEquals(-1, loaded.currentIndex());
        assertEquals(50, loaded.volume());
        assertNull(loaded.repeatMode());
        assertFalse(loaded.shuffle());
    }
}
