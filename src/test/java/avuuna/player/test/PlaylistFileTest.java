package avuuna.player.test;

import avuuna.player.model.PlaylistFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaylistFileTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path root() {
        return tmp.getRoot().toPath().toAbsolutePath();
    }

    @Test
    public void writeThenReadKeepsOrderAndAccents() throws Exception {
        Path music = Files.createDirectories(root().resolve("Música"));
        Path a = Files.createFile(music.resolve("Canción ñ.mp3"));
        Path b = Files.createFile(music.resolve("b.mp3"));
        Path list = root().resolve("mix.m3u8");

        PlaylistFile.write(list, List.of(b, a), true);

        assertEquals(List.of(b, a), PlaylistFile.read(list));
    }

    @Test
    public void relativePathsInsideThePlaylistFolderAbsoluteOutside() throws Exception {
        Path inside = Files.createFile(Files.createDirectories(root().resolve("lists/sub")).resolve("in.mp3"));
        Path outside = Files.createFile(root().resolve("out.mp3"));
        Path list = root().resolve("lists/mix.m3u8");

        PlaylistFile.write(list, List.of(inside, outside), true);
        List<String> lines = Files.readAllLines(list, StandardCharsets.UTF_8);

        assertEquals("#EXTM3U", lines.get(0));
        assertEquals("#EXTINF:-1,in", lines.get(1));
        assertEquals("sub" + File.separator + "in.mp3", lines.get(2));
        assertEquals(outside.toString(), lines.get(4));
        assertFalse("no temp file left behind", Files.exists(root().resolve("lists/mix.m3u8.tmp")));
    }

    @Test
    public void absoluteModeWritesFullPaths() throws Exception {
        Path song = Files.createFile(root().resolve("s.mp3"));
        Path list = root().resolve("session.m3u8");

        PlaylistFile.write(list, List.of(song), false);

        assertEquals(song.toString(), Files.readAllLines(list).get(2));
    }

    @Test
    public void readsWhatOtherPlayersWrite() throws Exception {
        Path list = root().resolve("other.m3u8");
        String content = "﻿#EXTM3U\r\n"
                + "#EXTINF:215,Artist - Song\r\n"
                + "songs/one.mp3\r\n"
                + "\r\n"
                + "# a comment\r\n"
                + "http://radio.example/stream\r\n"
                + root().resolve("two.mp3").toUri() + "\r\n"
                + "../three.mp3\r\n";
        Files.writeString(list, content, StandardCharsets.UTF_8);

        List<Path> entries = PlaylistFile.read(list);

        assertEquals(List.of(
                root().resolve("songs/one.mp3"),
                root().resolve("two.mp3"),
                root().getParent().resolve("three.mp3")), entries);
    }

    @Test
    public void legacyM3uInWindows1252IsDecoded() throws Exception {
        Path list = root().resolve("old.m3u");
        Files.write(list, "Canción.mp3\n".getBytes(Charset.forName("windows-1252")));

        assertEquals(List.of(root().resolve("Canción.mp3")), PlaylistFile.read(list));
    }

    @Test
    public void recognisesPlaylistExtensions() {
        assertTrue(PlaylistFile.isPlaylist(new File("a.M3U8")));
        assertTrue(PlaylistFile.isPlaylist(new File("a.m3u")));
        assertFalse(PlaylistFile.isPlaylist(new File("a.mp3")));
    }
}
