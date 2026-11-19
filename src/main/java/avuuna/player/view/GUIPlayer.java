package avuuna.player.view;

import avuuna.player.utils.Images;
import avuuna.player.utils.Strings;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Main application window. Declares and lays out all UI components.
 * Exposes a clean API for the controller to wire listeners and push state updates.
 */
public class GUIPlayer extends View {
    private static final long serialVersionUID = 422073346876789713L;

    private static final Color ACCENT = new Color(30, 120, 215);
    private static final Color MUTED_TEXT = new Color(130, 130, 130);

    /** One row of the playlist as shown to the user. */
    public record PlaylistItem(String title, String tooltip) {}

    private JMenuItem openItem;
    private JMenuItem openFolderItem;
    private JMenuItem exitItem;
    private JMenuItem shortcutsItem;
    private JMenuItem aboutItem;

    private JPanel playerPanel;

    private JPanel detailsPanel;
    private JLabel currentSongLabel;
    private JButton playButton;
    private JButton stopButton;
    private JButton prevButton;
    private JButton nextButton;
    private JButton repeatButton;
    private JButton shuffleButton;
    private JProgressBar progressBar;

    private JPanel volumePanel;
    private JSlider volumeSlider;
    private JLabel volumeLabel;

    private JPanel playlistPanel;

    private JScrollPane listScroll;
    private JList<PlaylistItem> songList;
    private DefaultListModel<PlaylistItem> listModel;
    private int currentPlaylistIndex = -1;

    private JButton addButton;
    private JButton removeButton;
    private JButton clearButton;
    private JButton moveUpButton;
    private JButton moveDownButton;

    public GUIPlayer() {
        super(Strings.APP_TITLE);
        buildComponents();
        buildMenuComponents();
    }

    // -------------------------------------------------------------------------
    // Listener registration
    // -------------------------------------------------------------------------

    public void addOpenMenuListener(ActionListener l) {
        openItem.addActionListener(l);
    }

    public void setOpenMenuAccelerator(KeyStroke ks) {
        openItem.setAccelerator(ks);
    }

    public void addOpenFolderMenuListener(ActionListener l) {
        openFolderItem.addActionListener(l);
    }

    public void setOpenFolderMenuAccelerator(KeyStroke ks) {
        openFolderItem.setAccelerator(ks);
    }

    public void addExitMenuListener(ActionListener l) {
        exitItem.addActionListener(l);
    }

    public void addPlayListener(ActionListener l) {
        playButton.addActionListener(l);
    }

    public void addStopListener(ActionListener l) {
        stopButton.addActionListener(l);
    }

    public void addNextListener(ActionListener l) {
        nextButton.addActionListener(l);
    }

    public void addPrevListener(ActionListener l) {
        prevButton.addActionListener(l);
    }

    public void addRepeatListener(ActionListener l) {
        repeatButton.addActionListener(l);
    }

    public void addShuffleListener(ActionListener l) {
        shuffleButton.addActionListener(l);
    }

    public void addVolumeChangeListener(ChangeListener l) {
        volumeSlider.addChangeListener(l);
    }

    public void addSongListMouseListener(MouseListener l) {
        songList.addMouseListener(l);
    }

    public void addSongListSelectionListener(ListSelectionListener l) {
        songList.addListSelectionListener(l);
    }

    public void addAddSongListener(ActionListener l) {
        addButton.addActionListener(l);
    }

    public void addRemoveSongListener(ActionListener l) {
        removeButton.addActionListener(l);
    }

    public void addClearPlaylistListener(ActionListener l) {
        clearButton.addActionListener(l);
    }

    public void addMoveUpListener(ActionListener l) {
        moveUpButton.addActionListener(l);
    }

    public void addMoveDownListener(ActionListener l) {
        moveDownButton.addActionListener(l);
    }

    /**
     * Registers seeking on the progress bar. While the user presses or drags,
     * {@code onPreview} receives the position as a ratio in [0, 1]; on release,
     * {@code onCommit} receives the final ratio.
     */
    public void addSeekListener(DoubleConsumer onPreview, DoubleConsumer onCommit) {
        MouseAdapter seek = new MouseAdapter() {
            private boolean dragging;

            private double ratio(MouseEvent e) {
                double r = (double) e.getX() / Math.max(1, progressBar.getWidth());
                return Math.max(0, Math.min(1, r));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && progressBar.isEnabled()) {
                    dragging = true;
                    onPreview.accept(ratio(e));
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging) {
                    onPreview.accept(ratio(e));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragging) {
                    dragging = false;
                    onCommit.accept(ratio(e));
                }
            }
        };
        progressBar.addMouseListener(seek);
        progressBar.addMouseMotionListener(seek);
    }

    /**
     * Accepts files and folders dropped anywhere on the window.
     */
    public void setFileDropHandler(Consumer<List<File>> handler) {
        TransferHandler th = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    // Let the drop finish before doing slow work / showing dialogs.
                    SwingUtilities.invokeLater(() -> handler.accept(new ArrayList<>(files)));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
        setTransferHandler(th);
        songList.setTransferHandler(th);
        listScroll.setTransferHandler(th);
    }

    /**
     * Binds a keyboard shortcut that works anywhere in the window.
     */
    public void bindShortcut(KeyStroke ks, String name, Runnable action) {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        root.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    // -------------------------------------------------------------------------
    // State update methods
    // -------------------------------------------------------------------------

    public void setPlayingState(boolean playing) {
        if (playing) {
            setButtonTip(playButton, Strings.PAUSE);
            playButton.setIcon(Images.getImageIcon(Images.BTN_PAUSE));
        } else {
            setButtonTip(playButton, Strings.PLAY);
            playButton.setIcon(Images.getImageIcon(Images.BTN_PLAY));
        }
    }

    /**
     * Shows the current song title, or a hint when nothing is loaded.
     * Also reflects it in the window title so it is visible in the taskbar.
     */
    public void setSongLabel(String name) {
        if (name != null) {
            currentSongLabel.setText(name);
            currentSongLabel.setForeground(UIManager.getColor("Label.foreground"));
            currentSongLabel.setToolTipText(name);
            setTitle(name + " — " + Strings.APP_TITLE);
        } else {
            currentSongLabel.setText(Strings.NO_SONG_LOADED);
            currentSongLabel.setForeground(MUTED_TEXT);
            currentSongLabel.setToolTipText(null);
            setTitle(Strings.APP_TITLE);
        }
    }

    public void setProgressMax(int max) {
        progressBar.setMaximum(max);
    }

    public int getProgressMax() {
        return progressBar.getMaximum();
    }

    public void setProgressValue(int value) {
        progressBar.setValue(value);
    }

    public void setProgressText(String text) {
        progressBar.setString(text);
    }

    /**
     * Replaces the playlist rows. When only the current-song marker changed, the
     * rows are kept and just repainted, so the user's selection and scroll stay put.
     */
    public void refreshPlaylist(List<PlaylistItem> items, int currentIndex) {
        boolean sameRows = items.size() == listModel.size();
        for (int i = 0; sameRows && i < items.size(); i++) {
            sameRows = items.get(i).equals(listModel.get(i));
        }
        currentPlaylistIndex = currentIndex;
        if (sameRows) {
            songList.repaint();
            return;
        }
        int[] selected = songList.getSelectedIndices();
        listModel.clear();
        for (PlaylistItem item : items) {
            listModel.addElement(item);
        }
        int[] stillValid = Arrays.stream(selected).filter(i -> i < items.size()).toArray();
        songList.setSelectedIndices(stillValid);
    }

    public void setRepeatButtonState(Boolean repeatMode) {
        if (repeatMode == null) {
            setButtonTip(repeatButton, Strings.NO_REPEAT);
            repeatButton.setIcon(Images.getImageIcon(Images.BTN_REPEAT_NONE));
        } else if (repeatMode) {
            setButtonTip(repeatButton, Strings.REPEAT_ALL);
            repeatButton.setIcon(Images.getImageIcon(Images.BTN_REPEAT_ALL));
        } else {
            setButtonTip(repeatButton, Strings.REPEAT_ONE);
            repeatButton.setIcon(Images.getImageIcon(Images.BTN_REPEAT_ONE));
        }
    }

    public void setShuffleButtonState(boolean shuffle) {
        if (shuffle) {
            setButtonTip(shuffleButton, Strings.SHUFFLE_ON);
            shuffleButton.setIcon(Images.getImageIcon(Images.BTN_SHUFFLE_ON));
        } else {
            setButtonTip(shuffleButton, Strings.SHUFFLE_OFF);
            shuffleButton.setIcon(Images.getImageIcon(Images.BTN_SHUFFLE_OFF));
        }
    }

    /**
     * Enables only the controls that can do something in the current state,
     * instead of showing error dialogs after the fact.
     */
    public void updateControlState(boolean hasSongs, boolean hasSelection, boolean hasCurrent) {
        stopButton.setEnabled(hasCurrent);
        prevButton.setEnabled(hasCurrent);
        nextButton.setEnabled(hasCurrent);
        progressBar.setEnabled(hasCurrent);
        removeButton.setEnabled(hasSelection);
        moveUpButton.setEnabled(hasSelection);
        moveDownButton.setEnabled(hasSelection);
        clearButton.setEnabled(hasSongs);
    }

    // -------------------------------------------------------------------------
    // Query methods
    // -------------------------------------------------------------------------

    public int getSelectedIndex() {
        return songList.getSelectedIndex();
    }

    public void setSelectedIndex(int index) {
        songList.setSelectedIndex(index);
        songList.ensureIndexIsVisible(index);
    }

    public int[] getSelectedIndices() {
        return songList.getSelectedIndices();
    }

    public void setSelectedIndices(int[] indices) {
        songList.setSelectedIndices(indices);
        if (indices.length > 0) {
            songList.ensureIndexIsVisible(indices[0]);
        }
    }

    /** Returns the playlist row under the mouse, or -1 if the click was below the last row. */
    public int getPlaylistIndexAt(Point p) {
        int index = songList.locationToIndex(p);
        if (index >= 0 && songList.getCellBounds(index, index).contains(p)) {
            return index;
        }
        return -1;
    }

    public int getVolumeValue() {
        return volumeSlider.getValue();
    }

    public void setVolumeValue(int value) {
        volumeSlider.setValue(value);
    }

    // -------------------------------------------------------------------------
    // Dialogs
    // -------------------------------------------------------------------------

    public boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, Strings.CONFIRM_TITLE,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, Strings.PLAYLIST_TITLE, JOptionPane.INFORMATION_MESSAGE);
    }

    public void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public void addShortcutsMenuListener(ActionListener l) {
        shortcutsItem.addActionListener(l);
    }

    public void addAboutMenuListener(ActionListener l) {
        aboutItem.addActionListener(l);
    }

    public void showShortcutsDialog() {
        JOptionPane.showMessageDialog(this, Strings.SHORTCUTS_TEXT, Strings.SHORTCUTS_ITEM,
                JOptionPane.PLAIN_MESSAGE);
    }

    public void showAboutDialog() {
        JOptionPane.showMessageDialog(this, Strings.ABOUT_TEXT, Strings.ABOUT_ITEM,
                JOptionPane.INFORMATION_MESSAGE, Images.getImageIcon(Images.IMG_LOGO));
    }

    public File showOpenFolderDialog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Strings.OPEN_FOLDER_ITEM);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    public File[] showOpenDialog() {
        try {
            // Force UTF-8 output: PowerShell defaults to the OEM codepage, which
            // garbles non-ASCII paths (e.g. "Canción.mp3") when decoded by Java.
            String script =
                "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; " +
                "Add-Type -AssemblyName System.Windows.Forms; " +
                "$d = New-Object System.Windows.Forms.OpenFileDialog; " +
                "$d.Filter = 'MP3 Files (*.mp3)|*.mp3'; " +
                "$d.Multiselect = $true; " +
                "$d.Title = 'Open'; " +
                "if ($d.ShowDialog() -eq 'OK') { $d.FileNames -join [System.Environment]::NewLine }";

            Process process = new ProcessBuilder("powershell", "-NoProfile", "-STA", "-Command", script)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                return showLegacyOpenDialog();
            }
            if (!output.isEmpty()) {
                return Arrays.stream(output.split("\\r?\\n"))
                    .map(File::new)
                    .toArray(File[]::new);
            }
            return null; // user cancelled
        } catch (Exception ignored) {
            return showLegacyOpenDialog();
        }
    }

    private File[] showLegacyOpenDialog() {
        FileDialog dialog = new FileDialog(this, Strings.OPEN_ITEM, FileDialog.LOAD);
        dialog.setMultipleMode(true);
        dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".mp3"));
        dialog.setVisible(true);
        File[] files = dialog.getFiles();
        return files.length > 0 ? files : null;
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private static void setButtonTip(JButton button, String tip) {
        button.setToolTipText(tip);
        button.getAccessibleContext().setAccessibleName(tip);
    }

    private static JButton iconButton(String iconKey, String tip) {
        JButton button = new JButton(Images.getImageIcon(iconKey));
        setButtonTip(button, tip);
        return button;
    }

    private void buildMenuComponents() {
        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);

        JMenu fileMenu = new JMenu(Strings.FILE_MENU);
        fileMenu.setMnemonic('F');
        menuBar.add(fileMenu);

        openItem = new JMenuItem(Strings.OPEN_ITEM, Images.getImageIcon(Images.IMG_OPEN));
        fileMenu.add(openItem);
        openFolderItem = new JMenuItem(Strings.OPEN_FOLDER_ITEM);
        fileMenu.add(openFolderItem);
        fileMenu.addSeparator();
        exitItem = new JMenuItem(Strings.EXIT_ITEM);
        fileMenu.add(exitItem);

        JMenu helpMenu = new JMenu(Strings.HELP_MENU);
        helpMenu.setMnemonic('H');
        menuBar.add(helpMenu);
        shortcutsItem = new JMenuItem(Strings.SHORTCUTS_ITEM);
        shortcutsItem.setAccelerator(KeyStroke.getKeyStroke("F1"));
        helpMenu.add(shortcutsItem);
        aboutItem = new JMenuItem(Strings.ABOUT_ITEM);
        helpMenu.add(aboutItem);
    }

    private void buildComponents() {

        playerPanel = new JPanel();
        playlistPanel = new JPanel();

        buildPlayerComponents();
        layoutPlayerPanel();

        buildPlaylistComponents();
        layoutPlaylistPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, playerPanel, playlistPanel);
        splitPane.setContinuousLayout(true);
        splitPane.setResizeWeight(0.4); // extra width goes mostly to the playlist
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        this.add(splitPane, BorderLayout.CENTER);
    }

    private void buildPlayerComponents() {
        currentSongLabel = new JLabel();
        currentSongLabel.setHorizontalAlignment(SwingConstants.CENTER);
        currentSongLabel.setFont(currentSongLabel.getFont().deriveFont(Font.BOLD, 14f));
        setSongLabel(null);

        progressBar = new JProgressBar();
        progressBar.setMinimum(0);
        progressBar.setMaximum(0);
        progressBar.setStringPainted(true);
        progressBar.setString(Strings.ZERO_TIME);
        progressBar.setToolTipText(Strings.SEEK_TOOLTIP);
        progressBar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        prevButton = iconButton(Images.BTN_PREV, Strings.PREV);
        playButton = iconButton(Images.BTN_PLAY, Strings.PLAY);
        stopButton = iconButton(Images.BTN_STOP, Strings.STOP);
        nextButton = iconButton(Images.BTN_NEXT, Strings.NEXT);
        repeatButton = iconButton(Images.BTN_REPEAT_NONE, Strings.NO_REPEAT);
        shuffleButton = iconButton(Images.BTN_SHUFFLE_OFF, Strings.SHUFFLE_OFF);

        volumeSlider = new JSlider(0, 100, 50);
        volumeSlider.setToolTipText(Strings.VOLUME_TOOLTIP);
        volumeSlider.getAccessibleContext().setAccessibleName(Strings.VOLUME_PANEL_TITLE);
        volumeLabel = new JLabel("50%", SwingConstants.RIGHT);
        volumeSlider.addChangeListener(e -> volumeLabel.setText(volumeSlider.getValue() + "%"));
        volumeSlider.addMouseWheelListener(e ->
                volumeSlider.setValue(volumeSlider.getValue() - e.getWheelRotation() * 5));
    }

    private void layoutPlayerPanel() {
        playerPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        detailsPanel = new JPanel();
        detailsPanel.setBorder(BorderFactory.createTitledBorder(Strings.PLAYER_PANEL_TITLE));

        GroupLayout dl = new GroupLayout(detailsPanel);
        detailsPanel.setLayout(dl);

        dl.setHorizontalGroup(dl.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(dl.createSequentialGroup()
                        .addContainerGap()
                        // min/pref 0: a long song name must not widen the panel; JLabel truncates with "..."
                        .addComponent(currentSongLabel, 0, 0, Short.MAX_VALUE)
                        .addContainerGap())
                .addGroup(dl.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(progressBar, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())
                .addGroup(dl.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(prevButton, GroupLayout.PREFERRED_SIZE, 65, GroupLayout.PREFERRED_SIZE)
                        .addComponent(playButton, GroupLayout.PREFERRED_SIZE, 65, GroupLayout.PREFERRED_SIZE)
                        .addComponent(stopButton, GroupLayout.PREFERRED_SIZE, 65, GroupLayout.PREFERRED_SIZE)
                        .addComponent(nextButton, GroupLayout.PREFERRED_SIZE, 65, GroupLayout.PREFERRED_SIZE)
                        .addGap(20, 20, 20)
                        .addComponent(repeatButton, GroupLayout.PREFERRED_SIZE, 65, GroupLayout.PREFERRED_SIZE)
                        .addComponent(shuffleButton, GroupLayout.PREFERRED_SIZE, 65, GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)));

        dl.setVerticalGroup(dl.createSequentialGroup()
                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(currentSongLabel)
                .addGap(8, 8, 8)
                .addComponent(progressBar, GroupLayout.PREFERRED_SIZE, 24, GroupLayout.PREFERRED_SIZE)
                .addGap(10, 10, 10)
                .addGroup(dl.createParallelGroup(GroupLayout.Alignment.BASELINE)
                        .addComponent(prevButton, GroupLayout.PREFERRED_SIZE, 65, GroupLayout.PREFERRED_SIZE)
                        .addComponent(playButton, GroupLayout.PREFERRED_SIZE, 65, GroupLayout.PREFERRED_SIZE)
                        .addComponent(stopButton, GroupLayout.PREFERRED_SIZE, 65, GroupLayout.PREFERRED_SIZE)
                        .addComponent(nextButton, GroupLayout.PREFERRED_SIZE, 65, GroupLayout.PREFERRED_SIZE)
                        .addComponent(repeatButton, GroupLayout.PREFERRED_SIZE, 65, GroupLayout.PREFERRED_SIZE)
                        .addComponent(shuffleButton, GroupLayout.PREFERRED_SIZE, 65, GroupLayout.PREFERRED_SIZE))
                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));

        volumePanel = new JPanel();
        volumePanel.setBorder(BorderFactory.createTitledBorder(Strings.VOLUME_PANEL_TITLE));

        GroupLayout vl = new GroupLayout(volumePanel);
        volumePanel.setLayout(vl);
        vl.setHorizontalGroup(vl.createSequentialGroup()
                .addContainerGap()
                .addComponent(volumeSlider, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(volumeLabel, GroupLayout.PREFERRED_SIZE, 40, GroupLayout.PREFERRED_SIZE)
                .addContainerGap());
        vl.setVerticalGroup(vl.createParallelGroup(GroupLayout.Alignment.CENTER)
                .addComponent(volumeSlider, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(volumeLabel));

        GroupLayout layout = new GroupLayout(playerPanel);
        playerPanel.setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(detailsPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(volumePanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addComponent(detailsPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(volumePanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));
    }

    private void buildPlaylistComponents() {
        listModel = new DefaultListModel<>();

        songList = new JList<>(listModel) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (listModel.isEmpty()) {
                    paintEmptyHint((Graphics2D) g);
                }
            }

            private void paintEmptyHint(Graphics2D g) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(MUTED_TEXT);
                FontMetrics fm = g.getFontMetrics();
                int lineHeight = fm.getHeight();
                Rectangle view = getVisibleRect();
                int y = view.y + (view.height - 2 * lineHeight) / 2 + fm.getAscent();
                for (String line : new String[]{Strings.EMPTY_PLAYLIST_LINE1, Strings.EMPTY_PLAYLIST_LINE2}) {
                    g.drawString(line, view.x + (view.width - fm.stringWidth(line)) / 2, y);
                    y += lineHeight;
                }
            }
        };
        songList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        songList.setCellRenderer(new PlaylistRenderer());
        songList.getAccessibleContext().setAccessibleName(Strings.PLAYLIST_TITLE);
        ToolTipManager.sharedInstance().registerComponent(songList);

        // Let window-level shortcuts reach the controller even when the list has focus.
        InputMap listKeys = songList.getInputMap(JComponent.WHEN_FOCUSED);
        for (String key : new String[]{"SPACE", "ctrl UP", "ctrl DOWN", "ctrl LEFT", "ctrl RIGHT",
                "shift LEFT", "shift RIGHT", "ENTER", "DELETE"}) {
            listKeys.put(KeyStroke.getKeyStroke(key), "none");
        }

        listScroll = new JScrollPane(songList);

        addButton = iconButton(Images.BTN_PLUS, Strings.ADD_SONG_TOOLTIP);
        removeButton = iconButton(Images.BTN_LESS, Strings.REMOVE_SONG_TOOLTIP);
        clearButton = iconButton(Images.BTN_CLOSE, Strings.CLEAR_PLAYLIST_TOOLTIP);
        moveUpButton = iconButton(Images.BTN_MOVE_UP, Strings.MOVE_UP_TOOLTIP);
        moveDownButton = iconButton(Images.BTN_MOVE_DOWN, Strings.MOVE_DOWN_TOOLTIP);
    }

    private void layoutPlaylistPanel() {
        playlistPanel.setBorder(BorderFactory.createTitledBorder(Strings.PLAYLIST_TITLE));
        playlistPanel.setMinimumSize(new Dimension(200, 100));

        GroupLayout layout = new GroupLayout(playlistPanel);
        playlistPanel.setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(listScroll, GroupLayout.DEFAULT_SIZE, 280, Short.MAX_VALUE)
                .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(addButton)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(removeButton)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(clearButton)
                        .addGap(20, 20, 20)
                        .addComponent(moveUpButton)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(moveDownButton)
                        .addGap(0, 0, Short.MAX_VALUE)));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addComponent(listScroll, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                        .addComponent(addButton)
                        .addComponent(removeButton)
                        .addComponent(clearButton)
                        .addComponent(moveUpButton)
                        .addComponent(moveDownButton))
                .addContainerGap());
    }

    /**
     * Renders "N. Title" rows; the current song is bold, accented and marked with a play glyph.
     */
    private class PlaylistRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            PlaylistItem item = (PlaylistItem) value;
            boolean current = index == currentPlaylistIndex;
            String text = (current ? "▶  " : "") + (index + 1) + ".  " + item.title();
            super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            setToolTipText(item.tooltip());
            setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            if (current) {
                setFont(getFont().deriveFont(Font.BOLD));
                if (!isSelected) {
                    setForeground(ACCENT);
                }
            }
            return this;
        }
    }
}
