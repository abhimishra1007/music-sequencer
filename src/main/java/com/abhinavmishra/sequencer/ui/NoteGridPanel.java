package com.abhinavmishra.sequencer.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import com.abhinavmishra.sequencer.model.Note;
import com.abhinavmishra.sequencer.model.NoteClipboard;
import com.abhinavmishra.sequencer.model.Song;

/**
 * The editable note grid (a piano roll). Rows are pitches, columns are steps.
 *
 * <p>Mouse controls:
 * <ul>
 *   <li>Click an empty cell to place a note; keep dragging right to make it longer.</li>
 *   <li>Drag a note to move it (all selected notes move together); drag its right edge to resize.</li>
 *   <li>Ctrl+click toggles a note in the selection; Shift+drag draws a selection box.</li>
 *   <li>Mouse wheel over a note changes its volume (velocity).</li>
 *   <li>Right-click a note to delete it.</li>
 * </ul>
 * Keyboard: Ctrl+C / Ctrl+V copy and paste at the mouse position, Delete removes
 * the selection, Ctrl+A selects all, Esc clears the selection.
 */
public class NoteGridPanel extends JPanel {

    public static final int LOW_PITCH = 36;   // C2
    public static final int HIGH_PITCH = 96;  // C7
    public static final int CELL_W = 22;
    public static final int CELL_H = 16;
    private static final int RESIZE_HANDLE_PX = 6;
    private static final int VELOCITY_STEP = 8;

    private static final Color WHITE_ROW = new Color(0xF7F8FA);
    private static final Color BLACK_ROW = new Color(0xE9ECF1);
    private static final Color STEP_LINE = new Color(0xDDE1E7);
    private static final Color BEAT_LINE = new Color(0xB8BEC8);
    private static final Color BAR_LINE = new Color(0x7D8592);
    private static final Color SELECTED_OUTLINE = new Color(0xF59E0B);
    private static final Color PLAYHEAD = new Color(0xE11D48);

    private enum Mode { NONE, RESIZE, MOVE, SELECT }

    private final Song song;
    private final GridListener listener;
    private final NoteClipboard clipboard = new NoteClipboard();
    private final Set<Note> selection = new LinkedHashSet<>();

    private Mode mode = Mode.NONE;
    private Point pressPoint;
    private Note anchor;                          // note under the mouse when the drag started
    private List<Note> dragOriginals = List.of(); // notes being moved/resized, as they were
    private List<Note> dragPreview = List.of();   // where they would land if released now
    private Rectangle selectBox;
    private int hoverStep = -1;
    private double playheadStep = -1;

    public NoteGridPanel(Song song, GridListener listener) {
        this.song = song;
        this.listener = listener;
        setFocusable(true);
        setToolTipText("");

        song.addListener(s -> {
            selection.retainAll(s.notes());
            revalidate();
            repaint();
        });

        Mouse mouse = new Mouse();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        installKeyBindings();
    }

    // ---------------------------------------------------------------- geometry

    public static int rows() {
        return HIGH_PITCH - LOW_PITCH + 1;
    }

    public static int yOf(int pitch) {
        return (HIGH_PITCH - pitch) * CELL_H;
    }

    static int pitchAt(int y) {
        return clamp(HIGH_PITCH - y / CELL_H, LOW_PITCH, HIGH_PITCH);
    }

    private int stepAt(int x) {
        return clamp(x / CELL_W, 0, song.steps() - 1);
    }

    private static Rectangle bounds(Note n) {
        return new Rectangle(n.start() * CELL_W, yOf(n.pitch()), n.length() * CELL_W, CELL_H);
    }

    private Optional<Note> noteAt(Point p) {
        return song.noteAt(pitchAt(p.y), p.x / CELL_W);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(song.steps() * CELL_W + 1, rows() * CELL_H + 1);
    }

    // ---------------------------------------------------------------- public actions

    public void setPlayhead(double step) {
        if (step != playheadStep) {
            playheadStep = step;
            repaint();
        }
    }

    public void copySelection() {
        if (selection.isEmpty()) {
            listener.status("Nothing selected to copy");
            return;
        }
        clipboard.copy(selection);
        listener.status("Copied " + selection.size() + " note(s)");
    }

    public void paste() {
        if (clipboard.isEmpty()) {
            listener.status("Clipboard is empty");
            return;
        }
        int at = hoverStep >= 0 ? hoverStep
                : selection.stream().mapToInt(Note::end).max().orElse(0);
        List<Note> pasted = clipboard.pasteAt(at);
        song.addAll(pasted);
        selection.clear();
        selection.addAll(pasted);
        listener.status("Pasted " + pasted.size() + " note(s) at step " + (at + 1));
        repaint();
    }

    public void deleteSelection() {
        if (!selection.isEmpty()) {
            int n = selection.size();
            song.removeAll(new ArrayList<>(selection));
            selection.clear();
            listener.status("Deleted " + n + " note(s)");
        }
    }

    public void selectAll() {
        selection.clear();
        selection.addAll(song.notes());
        repaint();
    }

    public void clearSelection() {
        selection.clear();
        repaint();
    }

    private void installKeyBindings() {
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask()), "copy", this::copySelection);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask()), "paste", this::paste);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_A, menuMask()), "selectAll", this::selectAll);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete", this::deleteSelection);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete", this::deleteSelection);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSelection", this::clearSelection);
    }

    private void bind(KeyStroke key, String name, Runnable action) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    static int menuMask() {
        return java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    }

    // ---------------------------------------------------------------- mouse

    private final class Mouse extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            pressPoint = e.getPoint();
            Optional<Note> hit = noteAt(e.getPoint());

            if (SwingUtilities.isRightMouseButton(e)) {
                hit.ifPresent(n -> {
                    List<Note> doomed = selection.contains(n) ? new ArrayList<>(selection) : List.of(n);
                    song.removeAll(doomed);
                    listener.status("Deleted " + doomed.size() + " note(s)");
                });
                return;
            }
            if (!SwingUtilities.isLeftMouseButton(e)) {
                return;
            }

            if (e.isShiftDown()) {
                mode = Mode.SELECT;
                selectBox = new Rectangle(e.getPoint());
                return;
            }

            if (hit.isEmpty()) {
                // Place a new note and let the user drag out its length.
                Note n = new Note(pitchAt(e.getY()), stepAt(e.getX()), 1, Note.DEFAULT_VELOCITY);
                song.add(n);
                listener.previewNote(n);
                selection.clear();
                selection.add(n);
                startDrag(Mode.RESIZE, n, List.of(n));
                return;
            }

            Note n = hit.get();
            if ((e.getModifiersEx() & menuMask()) != 0) {
                if (!selection.remove(n)) {
                    selection.add(n);
                }
                repaint();
                return;
            }
            if (!selection.contains(n)) {
                selection.clear();
                selection.add(n);
            }
            listener.previewNote(n);
            Rectangle r = bounds(n);
            if (e.getX() >= r.x + r.width - RESIZE_HANDLE_PX) {
                startDrag(Mode.RESIZE, n, List.of(n));
            } else {
                startDrag(Mode.MOVE, n, new ArrayList<>(selection));
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            hoverStep = stepAt(e.getX());
            switch (mode) {
                case RESIZE -> {
                    int newLength = Math.max(1, stepAt(e.getX()) - anchor.start() + 1);
                    dragPreview = List.of(anchor.withLength(newLength));
                    listener.status("Length " + newLength + " step(s)");
                }
                case MOVE -> {
                    int dSteps = Math.floorDiv(e.getX() - pressPoint.x + CELL_W / 2, CELL_W);
                    int dPitch = pitchAt(e.getY()) - pitchAt(pressPoint.y);
                    // Keep every moved note inside the grid.
                    for (Note n : dragOriginals) {
                        dSteps = Math.max(dSteps, -n.start());
                        dPitch = clamp(dPitch, LOW_PITCH - n.pitch(), HIGH_PITCH - n.pitch());
                    }
                    int oldPitch = dragPreview.isEmpty() ? anchor.pitch() : previewOfAnchor().pitch();
                    final int ds = dSteps;
                    final int dp = dPitch;
                    dragPreview = dragOriginals.stream().map(n -> n.movedBy(ds, dp)).toList();
                    Note moved = previewOfAnchor();
                    if (moved.pitch() != oldPitch) {
                        listener.previewNote(moved); // hear the new pitch while dragging
                    }
                    listener.status(Note.pitchName(moved.pitch()) + ", step " + (moved.start() + 1));
                }
                case SELECT -> {
                    selectBox = rectBetween(pressPoint, e.getPoint());
                }
                default -> {
                }
            }
            repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            switch (mode) {
                case RESIZE, MOVE -> {
                    if (!dragPreview.isEmpty() && !dragPreview.equals(dragOriginals)) {
                        song.replace(dragOriginals, dragPreview);
                        selection.clear();
                        selection.addAll(dragPreview);
                    }
                }
                case SELECT -> {
                    Rectangle box = selectBox;
                    selection.clear();
                    selection.addAll(song.notesInRange(
                            pitchAt(box.y + box.height), pitchAt(box.y),
                            box.x / CELL_W, (box.x + box.width) / CELL_W + 1));
                    listener.status(selection.size() + " note(s) selected");
                }
                default -> {
                }
            }
            mode = Mode.NONE;
            dragOriginals = List.of();
            dragPreview = List.of();
            selectBox = null;
            repaint();
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            hoverStep = stepAt(e.getX());
            Optional<Note> hit = noteAt(e.getPoint());
            boolean onEdge = hit.map(n -> {
                Rectangle r = bounds(n);
                return e.getX() >= r.x + r.width - RESIZE_HANDLE_PX;
            }).orElse(false);
            setCursor(Cursor.getPredefinedCursor(onEdge ? Cursor.E_RESIZE_CURSOR
                    : hit.isPresent() ? Cursor.MOVE_CURSOR : Cursor.CROSSHAIR_CURSOR));
        }

        @Override
        public void mouseExited(MouseEvent e) {
            hoverStep = -1;
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            Optional<Note> hit = noteAt(e.getPoint());
            if (hit.isEmpty()) {
                // Not over a note: pass the wheel on so the scroll pane scrolls.
                Component scroller = SwingUtilities.getAncestorOfClass(JScrollPane.class, NoteGridPanel.this);
                if (scroller != null) {
                    scroller.dispatchEvent(SwingUtilities.convertMouseEvent(NoteGridPanel.this, e, scroller));
                }
                return;
            }
            Note n = hit.get();
            List<Note> targets = selection.contains(n) ? new ArrayList<>(selection) : List.of(n);
            int delta = -e.getWheelRotation() * VELOCITY_STEP;
            List<Note> louder = targets.stream().map(t -> t.withVelocity(t.velocity() + delta)).toList();
            song.replace(targets, louder);
            selection.clear();
            selection.addAll(louder);
            Note changed = louder.get(targets.indexOf(n));
            listener.previewNote(changed);
            listener.status("Volume " + changed.velocity() + " / 127");
        }
    }

    private void startDrag(Mode m, Note anchorNote, List<Note> originals) {
        mode = m;
        anchor = anchorNote;
        dragOriginals = originals;
        dragPreview = List.of();
    }

    private Note previewOfAnchor() {
        return dragPreview.get(dragOriginals.indexOf(anchor));
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        return noteAt(e.getPoint())
                .map(n -> Note.pitchName(n.pitch()) + "  length " + n.length() + "  volume " + n.velocity())
                .orElse(null);
    }

    // ---------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = song.steps() * CELL_W;
        int height = rows() * CELL_H;

        for (int pitch = LOW_PITCH; pitch <= HIGH_PITCH; pitch++) {
            g.setColor(Note.isBlackKey(pitch) ? BLACK_ROW : WHITE_ROW);
            g.fillRect(0, yOf(pitch), width, CELL_H);
            if (pitch % 12 == 0) {
                g.setColor(BEAT_LINE);
                g.drawLine(0, yOf(pitch) + CELL_H, width, yOf(pitch) + CELL_H);
            }
        }
        for (int step = 0; step <= song.steps(); step++) {
            boolean bar = step % (Song.STEPS_PER_BEAT * 4) == 0;
            boolean beat = step % Song.STEPS_PER_BEAT == 0;
            g.setColor(bar ? BAR_LINE : beat ? BEAT_LINE : STEP_LINE);
            g.drawLine(step * CELL_W, 0, step * CELL_W, height);
        }

        for (Note n : song.notes()) {
            if (!dragPreview.isEmpty() && dragOriginals.contains(n)) {
                continue; // drawn at its preview position below
            }
            paintNote(g, n, selection.contains(n), false);
        }
        for (Note n : dragPreview) {
            paintNote(g, n, true, true);
        }

        if (selectBox != null) {
            g.setColor(new Color(0x3B82F6));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {4f, 3f}, 0f));
            g.draw(selectBox);
        }
        if (playheadStep >= 0) {
            int x = (int) Math.round(playheadStep * CELL_W);
            g.setColor(PLAYHEAD);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(x, 0, x, height);
        }
        g.dispose();
    }

    private static void paintNote(Graphics2D g, Note n, boolean selected, boolean dragging) {
        Rectangle r = bounds(n);
        // Louder notes are drawn darker.
        float t = n.velocity() / 127f;
        Color fill = blend(new Color(0xBFDBFE), new Color(0x1D4ED8), t);
        g.setColor(dragging ? new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 190) : fill);
        g.fillRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 5, 5);
        g.setStroke(new BasicStroke(selected ? 2f : 1f));
        g.setColor(selected ? SELECTED_OUTLINE : fill.darker());
        g.drawRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 5, 5);
        // resize grip
        g.setColor(new Color(255, 255, 255, 140));
        g.fillRect(r.x + r.width - 4, r.y + 4, 2, r.height - 8);
    }

    private static Color blend(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static Rectangle rectBetween(Point a, Point b) {
        return new Rectangle(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
