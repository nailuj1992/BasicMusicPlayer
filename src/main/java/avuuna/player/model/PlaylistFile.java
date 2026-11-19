package avuuna.player.model;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads and writes M3U / M3U8 playlist files.
 * <ul>
 *   <li>Writing always uses UTF-8, an {@code #EXTM3U} header and an {@code #EXTINF}
 *       line per song. Paths can be written relative to the playlist's folder (portable
 *       when the folder is moved together with the music) or absolute.</li>
 *   <li>Reading accepts what other players write: comments and {@code #EXT...} lines are
 *       ignored, relative paths are resolved against the playlist's folder, {@code file:}
 *       URIs are supported, other URLs (http, ...) are skipped, and a UTF-8 BOM is dropped.
 *       {@code .m3u} files that are not valid UTF-8 are read as Windows-1252, the
 *       traditional encoding for that extension.</li>
 * </ul>
 */
public final class PlaylistFile {

    public static final String M3U8 = ".m3u8";
    public static final String M3U = ".m3u";

    /** "scheme://" with a scheme of 2+ characters, so "C:/..." is not mistaken for a URL. */
    private static final Pattern URL = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]+://.*");
    private static final Charset LEGACY_M3U = Charset.forName("windows-1252");

    private PlaylistFile() {
    }

    public static boolean isPlaylist(File f) {
        String name = f.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(M3U8) || name.endsWith(M3U);
    }

    /**
     * Returns the entries of a playlist as normalized absolute paths, in order.
     * Entries are not checked for existence: callers decide what to do with missing files.
     */
    public static List<Path> read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        boolean utf8Only = file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(M3U8);
        String text = decode(bytes, utf8Only);
        Path base = file.toAbsolutePath().getParent();

        List<Path> result = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Path entry = toPath(line, base);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Writes {@code songs} to {@code file} (UTF-8). With {@code relativePaths}, songs inside
     * the playlist's folder (or its subfolders) are written relative to it; others stay absolute.
     * The file is written to a temporary sibling first and then moved into place.
     */
    public static void write(Path file, List<Path> songs, boolean relativePaths) throws IOException {
        Path target = file.toAbsolutePath();
        Path base = target.getParent();

        List<String> lines = new ArrayList<>();
        lines.add("#EXTM3U");
        for (Path song : songs) {
            Path abs = song.toAbsolutePath().normalize();
            lines.add("#EXTINF:-1," + titleOf(abs));
            boolean inside = relativePaths && base != null && abs.startsWith(base);
            lines.add(inside ? base.relativize(abs).toString() : abs.toString());
        }

        if (base != null) {
            Files.createDirectories(base);
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** File name without its extension, as shown in the playlist. */
    static String titleOf(Path song) {
        String name = song.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String decode(byte[] bytes, boolean utf8Only) {
        int offset = bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF ? 3 : 0;
        CharsetDecoder strict = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return strict.decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
        } catch (CharacterCodingException e) {
            Charset fallback = utf8Only ? StandardCharsets.UTF_8 : LEGACY_M3U;
            return new String(bytes, offset, bytes.length - offset, fallback);
        }
    }

    private static Path toPath(String line, Path base) {
        try {
            if (line.regionMatches(true, 0, "file:", 0, 5)) {
                return Paths.get(URI.create(line)).normalize();
            }
            if (URL.matcher(line).matches()) {
                return null; // streams and web URLs are not supported
            }
            Path p = Paths.get(line);
            if (!p.isAbsolute() && base != null) {
                p = base.resolve(p);
            }
            return p.toAbsolutePath().normalize();
        } catch (IllegalArgumentException | FileSystemNotFoundException e) {
            // InvalidPathException is an IllegalArgumentException
            return null;
        }
    }
}
