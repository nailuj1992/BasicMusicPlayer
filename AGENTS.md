# AGENTS.md — Reproductor de Música

Guidance for AI coding agents working in this repository. Read it before proposing changes.

## Git workflow — mandatory

- **NEVER push directly to `master`.** Not even a one-line fix, a doc tweak or a revert.
- **Every change goes on its own branch cut from `master`** (`git checkout master && git pull && git checkout -b <type>/<short-description>`), with `<type>` one of `feature/`, `fix/`, `chore/`, `docs/`, `refactor/`, `test/`. Push that branch and integrate it into `master` through a pull request.
- Never force-push a shared branch, never rewrite pushed history, never bypass hooks (`--no-verify`).
- Commit only what you were asked to commit, staging files **by name** (not `git add -A`): run `git status` first and keep stray files out (`.idea/`, `target/`, `.claude/`, logs, local session data).
- Commit messages are in **English**, Conventional Commits style (`feat:`, `fix:`, `docs:`, `test:`, `chore:`, `refactor:`): a short imperative subject, then a body explaining the *why* for non-trivial changes. (Older history mixes Spanish and free-form messages; don't copy that.)
- **Never push git tags.** Pushing a `v*` tag triggers `.github/workflows/release.yml`, which builds and **publishes a public GitHub Release**. Releasing is the maintainer's decision.
- Do not merge a PR yourself. Don't change `.github/workflows/` unless asked.
- Remote: `origin` = `github.com/nailuj1992/ReproductorMusica`. Maintainer: **nailuj1992** (local git user `pegasus1992`).

## Project

Lightweight **Windows desktop music player** in Java / Swing: MP3 playback, playlist with multi-selection, repeat (none / all / one), shuffle, click-or-drag seeking, drag and drop, keyboard shortcuts, and a playlist/settings session that survives restarts.

- **Platform:** Windows 10/11 only (Windows Look and Feel, PowerShell file picker, `Segoe UI Symbol` icons, `%APPDATA%`). Don't add cross-platform abstractions unless asked, but don't break the fallbacks that exist (`FileDialog` when PowerShell fails, home folder when `APPDATA` is unset).
- **Languages:** UI text is **English** (no i18n yet; all user-facing strings live in `Strings` and `PlayerException`). Code, comments, commits and docs are in English. Keep source files UTF-8 (filenames with `ñ`/accents must keep working).
- **End users** get a self-contained `jpackage` app image (bundled runtime, no Java install).

### Stack

- **Java 21** (`maven.compiler.source/target`), **Maven 3.6+**, plain Swing (`GroupLayout`), no framework.
- Audio: `mp3spi` 1.9.5.4 (+ `jlayer`, `tritonus-share`) and `vorbisspi` 1.0.3.3 from Maven; the **jlGui `BasicPlayer` sources are vendored** under `src/main/java/javazoom/jl/player/basic/`.
- Logging: `slf4j-api` + `slf4j-simple` 1.7.36 (BasicPlayer logs through it) and `java.util.logging` via `Utils.log`.
- Tests: JUnit 4.13.2.
- **Do not add a dependency without asking** (FlatLaf, a tag library, etc. are welcome ideas, but the maintainer decides). Dependencies go in `pom.xml` only — `libs/*.jar` is a legacy leftover from the pre-Maven build: never add to it or reference it.

## Layout

| Path | Purpose |
|---|---|
| `avuuna.player.MainPlayer` | Entry point: single-instance check, then builds the UI on the EDT (`SwingUtilities.invokeLater`). |
| `avuuna.player.InstanceLock` | Single-instance guard: loopback `ServerSocket` on port **49152** (the OS frees it on crash; no lock file). `release()` also calls `System.exit(0)`. |
| `controller/PlayerController` | The only class that knows both model and view. Wires listeners and shortcuts, turns user actions into model calls, renders model state in `update()`, refreshes progress with a 200 ms Swing `Timer`, saves/restores the session. |
| `model/MusicPlayer` | Playlist, current song, playback state, repeat/shuffle logic, end-of-song handling. Observer **subject**. Singleton. No Swing imports. |
| `model/Song` | `extends File`, plus duration (µs) and audio byte length. Equality is `File` equality (path, case-insensitive on Windows). |
| `model/BufferedBasicPlayer` | `BasicPlayer` subclass that reads the whole file into a `ByteArrayInputStream` (fixes "Resetting to invalid mark" with big ID3v2 tags). |
| `model/PlaylistFile` | The **only** M3U/M3U8 reader/writer. Writes UTF-8 with `#EXTM3U`/`#EXTINF`, relative paths optional (atomic temp-file + move). Reads other players' files: skips comments and URLs, resolves relative paths and `file:` URIs, drops the BOM, falls back to Windows-1252 for non-UTF-8 `.m3u`. |
| `model/SessionStore` | Saves/restores `playlist.m3u8` (through `PlaylistFile`, absolute paths) + `settings.properties` in `%APPDATA%\AvuunaMusicPlayer` (override: `-Dmusicplayer.dataDir=<dir>`). |
| `view/View`, `view/GUIPlayer` | `JFrame` base and the main window. Exposes `addXListener(...)`, `bindShortcut(...)`, `setFileDropHandler(...)`, `addSeekListener(...)` and semantic setters (`setPlayingState`, `refreshPlaylist`, `updateControlState`, …). Imports nothing from `model`. |
| `utils/Images` | Icons generated at runtime from Unicode glyphs (`Graphics2D`). Only `logo.png` and `Open16.png` are real image resources. |
| `utils/Strings`, `exception/PlayerException` | All user-facing text (labels, tooltips, dialogs, error messages). |
| `utils/Utils` | Time formatting, logging, Look and Feel, `getRandomWithExclusion`. |
| `utils/ModelObserver`, `utils/ModelSubject` | The project's own Observer interfaces. |
| `src/test/java/.../PlayerTest`, `UtilsTest`, `SessionStoreTest`, `PlaylistFileTest` | Tests (see *Testing*). `src/test/resources/music/` holds the sample MP3s. |
| `.github/workflows/release.yml` | Tag-triggered Windows build: `mvn package` (tests skipped), `jpackage --type app-image`, zip, GitHub Release with notes taken from `CHANGELOG.md`. |

`utils/NetUtils` and several `Utils.display(...)` overloads are unused leftovers; remove them only as a deliberate cleanup, not in passing.

## Architecture rules — do not violate

- **Strict MVC.** The controller never touches view component fields; it goes through `GUIPlayer`'s public methods. The view never imports `avuuna.player.model`; it receives plain data (`PlaylistItem`, strings, ints). The model never imports Swing/AWT UI classes.
- **Model state drives the UI.** Play/pause, repeat, shuffle and the playlist are read from the model in `update()`, never from button tooltips, icons or list text.
- **Playlist operations use indices**, never display names (two files can share a name in different folders). The view's rows are display-only.
- **Threading:**
  - Swing is touched **only on the EDT**. `MainPlayer` starts on it, `update()` re-dispatches itself if called from another thread, and progress uses `javax.swing.Timer` (never a `while(true)` thread — that once pinned a CPU core).
  - `BasicPlayer` fires events from its own threads, and `EOM` arrives on a separate launcher thread **while the playback thread is still closing its stream**. `MusicPlayer.stateUpdated` joins that thread (≤ 2 s) and then runs `handleEndOfMedia` through `eventExecutor` (the controller sets it to `SwingUtilities::invokeLater`; tests keep the direct default). Any new audio callback that changes model state must follow the same path.
  - `progressTime`/`progressBytes` are written by the audio thread and read by the timer: keep them `volatile`.
- **`open(song)` vs `load(song)`:** `open` means "play it" (the controller auto-plays on `OPENED`); `load` makes it current and ready without playing (`shouldAutoPlay() == false`). Use `load` for session restore, removing the current song while stopped, and reaching the end of the list with repeat off.
- **Repeat mode is a tri-state `Boolean`:** `null` = none, `TRUE` = all, `FALSE` = one. Keep `SessionStore`'s `none/all/one` encoding in sync.
- **Progress is in audio bytes, not file bytes.** mp3spi reports `mp3.length.bytes` as the whole file but `mp3.position.byte` counts frame bytes only, so `MusicPlayer.audioDataLength` strips the ID3v2 tag. Bar max/value and seek targets all use that unit; don't switch to file size or to time.
- **Vendored `javazoom` code:** fix behaviour in the subclass (`BufferedBasicPlayer`) or in `MusicPlayer`, not by editing `BasicPlayer` — unless there is no other way, and then say so explicitly.
- **Shuffle:** at least two songs; `getRandomWithExclusion` throws when everything is excluded (it used to loop forever). Next respects shuffle via `skipForward()`.
- **User feedback:** prefer disabling controls (`updateControlState`) over error dialogs for "nothing selected / empty playlist". Confirm only destructive bulk actions (clearing the playlist). New user-facing text goes in `Strings`/`PlayerException`, and new shortcuts go in `bindShortcuts()`, in the tooltip text and in `Strings.SHORTCUTS_TEXT`.
- **Layout gotcha:** a `JLabel`'s minimum width is its full text width. Labels holding dynamic text (song names) must be added to `GroupLayout` with min/pref `0`, or a long name pushes controls out of the window.
- **File pickers:** every native dialog goes through `GUIPlayer.runPowerShellDialog`, whose prelude sets `[Console]::OutputEncoding` to UTF-8 (Java decodes UTF-8), or non-ASCII paths break. Each one needs a Swing/AWT fallback when PowerShell fails. Extension checks are case-insensitive (`toLowerCase(Locale.ROOT)`).
- **Playlist files:** saved playlists use relative paths (portable with their music); the session file uses absolute paths. Opening never clears the current songs before the file was read successfully and contains at least one usable MP3. Entries that are missing or not MP3 are skipped and reported in one message, not one dialog each.

## Build, run & test

```bash
mvn clean package                                    # compile + tests + jar
mvn test                                             # tests only (needs a sound device)
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency
java -cp "target/classes;target/dependency/*" avuuna.player.MainPlayer
```

- Packaging with `jpackage` is described in `README.md`. The pom version (`1.0-SNAPSHOT`) is **not** the app version: the release workflow takes it from the tag. Don't "fix" either without asking.
- **Tests need an audio output device** (`PlayerTest` opens real lines), which is why CI skips them. Run them locally before saying a change works.
- `MusicPlayer` is a **singleton shared by every test**: `tearDown` clears the playlist, and a test that changes repeat/shuffle must restore them in `finally`. Prefer `load()` over `play()` in tests when playback isn't the point.
- `SessionStore` tests use `TemporaryFolder`, never the real `%APPDATA%`.
- **Don't add more MP3s** to `src/test/resources/music/` (already ~99 MB of third-party music); reuse the existing ones.

### Running the app to check a change

- Launch it with `-Dmusicplayer.dataDir=<scratch folder>` so you never read or overwrite the maintainer's real playlist in `%APPDATA%\AvuunaMusicPlayer`. Seed that folder with a `playlist.m3u8` if you need songs.
- Only one instance can run (port 49152). A second launch only shows "Application is already running."
- `java` from `Oracle\Java\javapath` is a launcher: the window belongs to a **child** `java.exe`. Find the process by its command line, and **stop only the PID you started** — never all `java` processes (the maintainer may have the player or an IDE running).
- UI changes can't be fully verified headlessly. Say what you checked (tests, screenshots, measurements) and what still needs a manual try (shortcuts, drag and drop, audio).

## Restrictions

- Don't touch the maintainer's real data (`%APPDATA%\AvuunaMusicPlayer`) or music folders unless explicitly asked; back files up before overwriting them.
- Don't publish anything (tags, releases, GitHub pages) and don't commit secrets (none exist today; keep it that way).
- Don't commit `target/`, `.idea/`, `.claude/` or generated session files. Note: `.gitignore` ignores `*.jar`, yet `libs/*.jar` is tracked from older history; leave it until a cleanup is requested.
- Don't claim OGG support works: `vorbisspi` is bundled, but the file picker, drop handler, playlist import and progress keys are MP3-only today.
- Windows shell: both PowerShell and Git Bash are available; working-tree files are LF with `core.autocrlf=true`. Don't mass-convert line endings.

## Documentation & releases

- **Every user-visible change gets an entry under `## [Unreleased]` in `CHANGELOG.md`** ([Keep a Changelog](https://keepachangelog.com/en/1.1.0/): Added / Changed / Fixed / Removed).
- Releasing (maintainer only): rename `[Unreleased]` to `## [x.y.z] - YYYY-MM-DD` — the workflow's `awk` extracts the notes by that exact `## [x.y.z]` heading — then tag `vx.y.z`. The stray `## [Released]` heading and the compare links at the bottom (still pointing to `pegasus1992/...` and only `1.0.0`) need tidying at the next release.
- Update `README.md` when features, requirements or build steps change, and **update this file** when a convention changes or you learn a gotcha the hard way.

## Known gaps (not bugs to fix in passing)

- OGG Vorbis is not really supported (see above).
- `mp3.position.microseconds` after a seek may be relative to the new stream start; the time label could drift after seeking.
- No song metadata (ID3 title/artist/duration) in the playlist: rows show file names.
- The PowerShell file picker blocks the EDT while open and starts PowerShell each time (~1 s).
- No CI workflow builds or tests pull requests; tests can't run on GitHub runners (no sound device).
