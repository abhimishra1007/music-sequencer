package com.abhinavmishra.sequencer.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.abhinavmishra.sequencer.io.SongFile;
import com.abhinavmishra.sequencer.midi.MidiPlayer;
import com.abhinavmishra.sequencer.model.Note;
import com.abhinavmishra.sequencer.model.Song;

/** Main window: menu bar, transport controls, the note grid, and a status bar. */
public class SequencerFrame extends JFrame implements GridListener {

    /** A few General MIDI programs to choose from. */
    private record Instrument(int program, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static final Instrument[] INSTRUMENTS = {
        new Instrument(0, "Grand Piano"), new Instrument(4, "Electric Piano"), new Instrument(11, "Vibraphone"),
        new Instrument(13, "Xylophone"), new Instrument(19, "Church Organ"), new Instrument(24, "Nylon Guitar"),
        new Instrument(33, "Electric Bass"), new Instrument(40, "Violin"), new Instrument(48, "Strings"),
        new Instrument(56, "Trumpet"), new Instrument(65, "Alto Sax"), new Instrument(73, "Flute"),
        new Instrument(80, "Square Lead"), new Instrument(81, "Saw Lead"), new Instrument(88, "Warm Pad"),
    };

    private final Song song = new Song();
    private final MidiPlayer player;
    private final NoteGridPanel grid;
    private final JLabel status = new JLabel(" ");
    private final JButton playButton = new JButton("Play");
    private final JSpinner tempo = new JSpinner(new SpinnerNumberModel(120, 20, 300, 1));
    private final JSpinner bars = new JSpinner(new SpinnerNumberModel(4, 1, 64, 1));
    private final JComboBox<Instrument> instrument = new JComboBox<>(INSTRUMENTS);
    private final JSlider volume = new JSlider(0, 127, 100);
    private final Timer playheadTimer;

    private Path file;
    private boolean dirty;
    private boolean syncingControls;

    public SequencerFrame() {
        super("Music Sequencer");
        player = openPlayer();

        grid = new NoteGridPanel(song, this);
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setRowHeaderView(new PianoKeysPanel(this));
        scroll.getVerticalScrollBar().setUnitIncrement(NoteGridPanel.CELL_H);
        scroll.getHorizontalScrollBar().setUnitIncrement(NoteGridPanel.CELL_W);

        song.addListener(s -> {
            dirty = true;
            updateTitle();
            syncControls();
            if (player != null) {
                try {
                    player.update(s); // keep playback in step with edits
                } catch (InvalidMidiDataException e) {
                    status("MIDI error: " + e.getMessage());
                }
            }
        });

        setJMenuBar(buildMenus());
        add(buildToolbar(), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(status, BorderLayout.SOUTH);

        playheadTimer = new Timer(30, e -> grid.setPlayhead(player != null ? player.currentStep() : -1));

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });
        setSize(1100, 700);
        setLocationRelativeTo(null);
        // Start scrolled to the middle of the keyboard (around C2-E5) rather than the top.
        SwingUtilities.invokeLater(() -> grid.scrollRectToVisible(new Rectangle(0, NoteGridPanel.yOf(76), 1,
                NoteGridPanel.yOf(NoteGridPanel.LOW_PITCH) - NoteGridPanel.yOf(76))));
        dirty = false;
        updateTitle();
        status("Click the grid to place notes. Drag to move or lengthen, scroll over a note to change its volume, right-click to delete.");
    }

    private MidiPlayer openPlayer() {
        try {
            return new MidiPlayer();
        } catch (MidiUnavailableException e) {
            // Shown once the window is up, so a missing sound device doesn't block startup.
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "No MIDI synthesizer is available, so sound is disabled.\nYou can still edit and save songs.\n\n"
                            + e.getMessage(), "Sound unavailable", JOptionPane.WARNING_MESSAGE));
            return null;
        }
    }

    // ---------------------------------------------------------------- layout

    private JMenuBar buildMenus() {
        JMenuBar bar = new JMenuBar();
        int menu = NoteGridPanel.menuMask();

        JMenu fileMenu = new JMenu("File");
        fileMenu.add(action("New", KeyStroke.getKeyStroke(KeyEvent.VK_N, menu), this::newSong));
        fileMenu.add(action("Open...", KeyStroke.getKeyStroke(KeyEvent.VK_O, menu), this::open));
        fileMenu.add(action("Save", KeyStroke.getKeyStroke(KeyEvent.VK_S, menu), this::save));
        fileMenu.add(action("Save As...", KeyStroke.getKeyStroke(KeyEvent.VK_S, menu | KeyEvent.SHIFT_DOWN_MASK), this::saveAs));
        fileMenu.addSeparator();
        fileMenu.add(action("Exit", null, this::exit));
        bar.add(fileMenu);

        JMenu edit = new JMenu("Edit");
        edit.add(action("Copy", KeyStroke.getKeyStroke(KeyEvent.VK_C, menu), grid::copySelection));
        edit.add(action("Paste", KeyStroke.getKeyStroke(KeyEvent.VK_V, menu), grid::paste));
        edit.add(action("Delete", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), grid::deleteSelection));
        edit.add(action("Select All", KeyStroke.getKeyStroke(KeyEvent.VK_A, menu), grid::selectAll));
        bar.add(edit);

        JMenu help = new JMenu("Help");
        help.add(action("Controls", null, this::showControls));
        bar.add(help);
        return bar;
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));

        playButton.addActionListener(e -> togglePlay());
        playButton.setEnabled(player != null);
        bar.add(playButton);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "togglePlay");
        getRootPane().getActionMap().put("togglePlay", action("Play/Stop", null, this::togglePlay));

        bar.add(new JLabel("Tempo"));
        tempo.addChangeListener(e -> {
            if (!syncingControls) {
                song.setTempoBpm((Integer) tempo.getValue());
            }
        });
        bar.add(tempo);

        bar.add(new JLabel("Bars"));
        bars.addChangeListener(e -> {
            if (!syncingControls) {
                song.setSteps((Integer) bars.getValue() * Song.STEPS_PER_BEAT * 4);
            }
        });
        bar.add(bars);

        bar.add(new JLabel("Instrument"));
        instrument.addActionListener(e -> {
            if (!syncingControls) {
                song.setInstrument(((Instrument) instrument.getSelectedItem()).program());
            }
        });
        bar.add(instrument);

        bar.add(new JLabel("Volume"));
        volume.setPreferredSize(new Dimension(140, volume.getPreferredSize().height));
        volume.addChangeListener(e -> {
            if (player != null) {
                player.setVolume(volume.getValue());
            }
        });
        bar.add(volume);
        return bar;
    }

    private static Action action(String name, KeyStroke key, Runnable run) {
        AbstractAction a = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
        if (key != null) {
            a.putValue(Action.ACCELERATOR_KEY, key);
        }
        return a;
    }

    /** Pushes song settings into the toolbar without triggering their listeners. */
    private void syncControls() {
        syncingControls = true;
        tempo.setValue(song.tempoBpm());
        bars.setValue(song.steps() / (Song.STEPS_PER_BEAT * 4));
        for (Instrument i : INSTRUMENTS) {
            if (i.program() == song.instrument()) {
                instrument.setSelectedItem(i);
            }
        }
        syncingControls = false;
    }

    // ---------------------------------------------------------------- playback

    private void togglePlay() {
        if (player == null) {
            return;
        }
        if (player.isPlaying()) {
            player.stop();
            playheadTimer.stop();
            grid.setPlayhead(-1);
            playButton.setText("Play");
        } else {
            try {
                player.setVolume(volume.getValue());
                player.play(song);
                playheadTimer.start();
                playButton.setText("Stop");
            } catch (InvalidMidiDataException e) {
                status("MIDI error: " + e.getMessage());
            }
        }
    }

    @Override
    public void previewNote(Note note) {
        if (player != null) {
            player.preview(note.pitch(), note.velocity(), song.instrument());
        }
    }

    @Override
    public void status(String message) {
        status.setText(message);
    }

    // ---------------------------------------------------------------- files

    private JFileChooser chooser() {
        JFileChooser fc = new JFileChooser(file != null ? file.getParent().toFile() : null);
        fc.setFileFilter(new FileNameExtensionFilter("Sequencer songs (*.seq)", SongFile.EXTENSION));
        return fc;
    }

    private void newSong() {
        if (confirmDiscard()) {
            stopIfPlaying();
            song.load(new Song());
            file = null;
            dirty = false;
            updateTitle();
        }
    }

    private void open() {
        if (!confirmDiscard()) {
            return;
        }
        JFileChooser fc = chooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openFile(fc.getSelectedFile().toPath());
        }
    }

    public void openFile(Path path) {
        try {
            Song loaded = SongFile.load(path);
            stopIfPlaying();
            song.load(loaded);
            file = path;
            dirty = false;
            updateTitle();
            status("Opened " + path.getFileName() + " (" + song.notes().size() + " notes)");
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "Could not open " + path.getFileName() + ":\n" + e.getMessage(),
                    "Open failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean save() {
        return file == null ? saveAs() : writeTo(file);
    }

    private boolean saveAs() {
        JFileChooser fc = chooser();
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return false;
        }
        Path path = fc.getSelectedFile().toPath();
        if (!path.getFileName().toString().endsWith("." + SongFile.EXTENSION)) {
            path = path.resolveSibling(path.getFileName() + "." + SongFile.EXTENSION);
        }
        return writeTo(path);
    }

    private boolean writeTo(Path path) {
        try {
            SongFile.save(song, path);
            file = path;
            dirty = false;
            updateTitle();
            status("Saved " + path.getFileName());
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save:\n" + e.getMessage(), "Save failed",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private boolean confirmDiscard() {
        if (!dirty) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(this, "Save changes to the current song?", "Unsaved changes",
                JOptionPane.YES_NO_CANCEL_OPTION);
        return choice == JOptionPane.NO_OPTION || (choice == JOptionPane.YES_OPTION && save());
    }

    private void exit() {
        if (confirmDiscard()) {
            if (player != null) {
                player.close();
            }
            dispose();
            System.exit(0);
        }
    }

    private void stopIfPlaying() {
        if (player != null && player.isPlaying()) {
            togglePlay();
        }
    }

    private void updateTitle() {
        String name = file == null ? "Untitled" : file.getFileName().toString();
        setTitle((dirty ? "*" : "") + name + " - Music Sequencer");
    }

    private void showControls() {
        String[][] rows = {
            {"Click an empty cell", "place a note (drag right to lengthen)"},
            {"Drag a note", "move it (selected notes move together)"},
            {"Drag a note's right edge", "change its length"},
            {"Mouse wheel over a note", "change its volume"},
            {"Right-click a note", "delete it"},
            {"Ctrl+click", "add or remove a note from the selection"},
            {"Shift+drag", "select notes in a box"},
            {"Ctrl+C / Ctrl+V", "copy / paste at the mouse position"},
            {"Delete", "delete selected notes"},
            {"Space", "play / stop"},
        };
        StringBuilder html = new StringBuilder("<html><table cellpadding=3>");
        for (String[] row : rows) {
            html.append("<tr><td><b>").append(row[0]).append("</b></td><td>").append(row[1]).append("</td></tr>");
        }
        html.append("</table></html>");
        JOptionPane.showMessageDialog(this, html.toString(), "Controls", JOptionPane.INFORMATION_MESSAGE);
    }
}
