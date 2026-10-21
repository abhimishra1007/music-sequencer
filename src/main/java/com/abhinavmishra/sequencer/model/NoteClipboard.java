package com.abhinavmishra.sequencer.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Copy/paste buffer for notes. Notes are stored relative to the earliest
 * copied note so they can be pasted at any step, keeping their pitches,
 * spacing, lengths, and volumes.
 */
public class NoteClipboard {

    private final List<Note> relative = new ArrayList<>();

    public void copy(Collection<Note> notes) {
        relative.clear();
        int first = notes.stream().mapToInt(Note::start).min().orElse(0);
        for (Note n : notes) {
            relative.add(n.movedBy(-first, 0));
        }
    }

    public boolean isEmpty() {
        return relative.isEmpty();
    }

    /** Returns the copied notes shifted so the earliest one starts at {@code step}. */
    public List<Note> pasteAt(int step) {
        return relative.stream().map(n -> n.movedBy(step, 0)).toList();
    }

    /** Length in steps from the first copied note's start to the last one's end. */
    public int span() {
        return relative.stream().mapToInt(Note::end).max().orElse(0);
    }
}
