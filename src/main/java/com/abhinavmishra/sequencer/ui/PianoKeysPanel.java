package com.abhinavmishra.sequencer.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;

import com.abhinavmishra.sequencer.model.Note;

/** Keyboard along the left edge of the grid. Clicking a key plays that pitch. */
public class PianoKeysPanel extends JPanel {

    private static final int WIDTH = 56;

    public PianoKeysPanel(GridListener listener) {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int pitch = NoteGridPanel.pitchAt(e.getY());
                listener.previewNote(new Note(pitch, 0, 1, Note.DEFAULT_VELOCITY));
                listener.status(Note.pitchName(pitch));
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, NoteGridPanel.rows() * NoteGridPanel.CELL_H + 1);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(getFont().deriveFont(Font.PLAIN, 10f));
        for (int pitch = NoteGridPanel.LOW_PITCH; pitch <= NoteGridPanel.HIGH_PITCH; pitch++) {
            int y = NoteGridPanel.yOf(pitch);
            boolean black = Note.isBlackKey(pitch);
            g.setColor(black ? new Color(0x2B2F36) : Color.WHITE);
            g.fillRect(0, y, black ? WIDTH * 2 / 3 : WIDTH, NoteGridPanel.CELL_H);
            g.setColor(new Color(0xB8BEC8));
            g.drawRect(0, y, WIDTH - 1, NoteGridPanel.CELL_H);
            if (pitch % 12 == 0) {
                g.setColor(new Color(0x4B5563));
                g.drawString(Note.pitchName(pitch), WIDTH - 24, y + NoteGridPanel.CELL_H - 4);
            }
        }
    }
}
