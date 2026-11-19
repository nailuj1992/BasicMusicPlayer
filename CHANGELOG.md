# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.1.0] - 2026-11-18

### Added
- Playlist, current song, volume, repeat and shuffle are saved on exit and restored
  on start (`%APPDATA%\AvuunaMusicPlayer`; override with `-Dmusicplayer.dataDir`).
- Playlist files: **File > Save playlist as...** (Ctrl+Shift+S) writes an M3U8 (UTF-8,
  paths relative to the playlist's folder when the songs are inside it) or M3U file;
  **File > Open playlist...** (Ctrl+L) reads `.m3u8` / `.m3u` files written by this or
  other players and asks whether to replace the current songs or add to them. Missing
  files and non-MP3 entries are skipped and reported.
- Drag and drop MP3 files, whole folders or playlist files onto the window;
  **File > Open folder...**.
- Keyboard shortcuts: Space, Enter, Delete, Ctrl+Left/Right, Shift+Left/Right (±5 s),
  Ctrl+Up/Down, Ctrl+R, Ctrl+S, Ctrl+Shift+O, Ctrl+L, Ctrl+Shift+S; listed under
  **Help > Keyboard shortcuts** (F1).
- Drag on the progress bar to scrub; the time preview follows the mouse and the
  seek happens on release.
- Volume percentage label; mouse wheel adjusts volume.
- Help menu with About; File > Exit.

### Changed
- Playlist rows are numbered, hide the `.mp3` extension and show the full path as a
  tooltip; the current song is bold and marked with ▶. An empty playlist shows a hint.
- Current song title is shown in bold and in the window title.
- Buttons are enabled only when they can act, instead of showing error dialogs.
  Removing songs no longer asks for confirmation (clearing the playlist still does).
- Play with nothing loaded opens the file picker (or plays the selected song).
- Previous restarts the current song when it has played for more than 3 s.
- Next respects shuffle mode.
- Removing the current song while stopped loads the next one without starting playback;
  removing several songs at once no longer opens songs one after another.
- The window is resizable; buttons are keyboard-focusable and have accessible names.
- README: OGG is no longer listed as supported (it can't be added from the UI yet).

### Fixed
- Playlist actions use positions instead of file names, so two songs with the same file
  name in different folders no longer get mixed up.
- Double-clicking the current song now restarts it.
- Duplicate songs no longer report "An error occurred while opening the song".
- Progress-bar busy loop that kept one CPU core at 100%.
- Hang at end of song with one song + shuffle + repeat all.
- Garbled non-ASCII file names (e.g. `Canción.mp3`) from the file picker.
- `.MP3` files (upper case) were ignored.
- Swing is now only touched from the Event Dispatch Thread, and end-of-song handling
  waits for the playback thread to finish.
- A long song name no longer pushes the shuffle button and progress bar out of view.

## [2.0.0] - 2026-05-05

### Added
- Native Windows 11 file picker (PowerShell `System.Windows.Forms.OpenFileDialog`),
  with `java.awt.FileDialog` as a fallback when PowerShell is unavailable.
- Click-to-seek on the progress bar, with a hand cursor over the bar.
- Multi-selection in the playlist (Ctrl-/Shift-click) for batch remove and move
  up/down — contiguous blocks move as a unit, non-contiguous selections move
  independently when not blocked by another selected item.
- 3-state repeat button (no repeat / repeat all / repeat one) and 2-state
  shuffle button, each with a distinct programmatic icon.
- GitHub Actions release workflow: pushing a `v*` tag builds a Windows
  `app-image` via `jpackage`, zips it, and publishes a GitHub Release.

### Changed
- Single-instance guard rewritten as `InstanceLock`, using a loopback
  `ServerSocket` instead of a periodically-refreshed temp file. No more 20-second
  wait to relaunch after a crash, and no background timer.
- All button icons are now generated at runtime with `Graphics2D` (Unicode
  symbols rendered to `BufferedImage`s) — no more PNG files for buttons.
- `Images` icon-key constants renamed from PNG filenames (`Play64.png`, …) to
  `btn-xxx` pattern, matching the new programmatic-icon model.
- Player panel layout: playback buttons centered with flexible gaps, transport
  group (`prev / play / stop / next`) visually separated from mode group
  (`repeat / shuffle`) by a 20 px gap, content vertically centered.
- Playlist panel layout: action buttons centered, with a 20 px separator between
  `add / remove / clear` and `up / down`. Removed an incorrect
  `setPreferredSize(285, 23)` that caused the buttons row to overflow.
- Playback buttons resized from 80×65 to 65×65 (square) — better proportion with
  the 48 px symbol icons.
- Song label centered above the progress bar.
- Progress bar now uses the audio-data byte length (computed by parsing the
  ID3v2 header from the file), keeping max and value in the same byte units so
  the bar reaches 100% at end-of-song and click-to-seek lands accurately.

### Fixed
- `IOException: Resetting to invalid mark` when opening MP3 files with large
  ID3v2 tags (embedded artwork, lyrics): `BufferedBasicPlayer` overrides
  `BasicPlayer.initAudioInputStream(File)` to use a `ByteArrayInputStream`,
  which supports unlimited mark/reset. Seek still works because `m_dataSource`
  remains the original `File`.
- Progress bar capping at &lt; 100% for files with large ID3v2 tags
  (`mp3.length.bytes` reports the file size, but `mp3.position.byte` only counts
  audio frame bytes — fixed by computing audio length from the ID3v2 header).
- Seek click position no longer overshoots the visual click point on files with
  ID3v2 tags.

### Removed
- `Control.java` — replaced by `InstanceLock`.
- 8 unused PNG icon files: `Pause64.png`, `Play64.png`, `Skip-backward64.png`,
  `Skip-forward64.png`, `Stop64.png`, `Plus16.png`, `Less16.png`, `Close16.png`.

[Unreleased]: https://github.com/nailuj1992/ReproductorMusica/compare/v2.1.0...HEAD
[2.1.0]: https://github.com/nailuj1992/ReproductorMusica/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/nailuj1992/ReproductorMusica/releases/tag/v2.0.0
