package com.abhinavmishra.sequencer.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The song being edited: a set of notes plus tempo, instrument, and length.
 * Two notes of the same pitch never overlap; adding one trims the conflict by
 * removing the notes it would overlap.
 */
public class Song {

    public static final int STEPS_PER_BEAT = 4; // each step is a sixteenth note
    public static final int DEFAULT_STEPS = 64;  // four bars of 4/4

    private final List<Note> notes = new ArrayList<>();
    private final List<SongListener> listeners = new CopyOnWriteArrayList<>();
    private int tempoBpm = 120;
    private int instrument = 0; // General MIDI program, 0 = Acoustic Grand Piano
    private int steps = DEFAULT_STEPS;

    public void addListener(SongListener l) {
        listeners.add(l);
    }

    private void fireChanged() {
        for (SongListener l : listeners) {
            l.songChanged(this);
        }
    }

    public List<Note> notes() {
        return Collections.unmodifiableList(notes);
    }

    public Optional<Note> noteAt(int pitch, int step) {
        return notes.stream().filter(n -> n.pitch() == pitch && n.covers(step)).findFirst();
    }

    public List<Note> notesInRange(int lowPitch, int highPitch, int fromStep, int toStepExclusive) {
        return notes.stream()
                .filter(n -> n.pitch() >= lowPitch && n.pitch() <= highPitch)
                .filter(n -> n.start() < toStepExclusive && n.end() > fromStep)
                .toList();
    }

    public void add(Note note) {
        addAll(List.of(note));
    }

    /** Adds notes in one change, removing any existing notes they overlap and growing the song if needed. */
    public void addAll(Collection<Note> added) {
        if (added.isEmpty()) {
            return;
        }
        for (Note n : added) {
            notes.removeIf(existing -> existing.overlaps(n));
            notes.add(n);
            steps = Math.max(steps, roundUpToBar(n.end()));
        }
        sort();
        fireChanged();
    }

    public void remove(Note note) {
        removeAll(List.of(note));
    }

    public void removeAll(Collection<Note> removed) {
        if (notes.removeAll(removed)) {
            fireChanged();
        }
    }

    /** Swaps old notes for new ones as a single change (used for moves, resizes, and volume edits). */
    public void replace(Collection<Note> oldNotes, Collection<Note> newNotes) {
        notes.removeAll(oldNotes);
        for (Note n : newNotes) {
            notes.removeIf(existing -> existing.overlaps(n));
            notes.add(n);
            steps = Math.max(steps, roundUpToBar(n.end()));
        }
        sort();
        fireChanged();
    }

    public void clear() {
        notes.clear();
        steps = DEFAULT_STEPS;
        fireChanged();
    }

    /** Replaces everything at once, e.g. after loading a file. */
    public void load(Song other) {
        notes.clear();
        notes.addAll(other.notes);
        tempoBpm = other.tempoBpm;
        instrument = other.instrument;
        steps = other.steps;
        fireChanged();
    }

    public int tempoBpm() {
        return tempoBpm;
    }

    public void setTempoBpm(int bpm) {
        if (bpm < 20 || bpm > 300) {
            throw new IllegalArgumentException("tempo must be 20-300 BPM");
        }
        tempoBpm = bpm;
        fireChanged();
    }

    public int instrument() {
        return instrument;
    }

    public void setInstrument(int program) {
        if (program < 0 || program > 127) {
            throw new IllegalArgumentException("instrument must be a General MIDI program 0-127");
        }
        instrument = program;
        fireChanged();
    }

    public int steps() {
        return steps;
    }

    public void setSteps(int newSteps) {
        int lastEnd = notes.stream().mapToInt(Note::end).max().orElse(0);
        steps = Math.max(roundUpToBar(newSteps), roundUpToBar(lastEnd));
        fireChanged();
    }

    private static int roundUpToBar(int s) {
        int bar = STEPS_PER_BEAT * 4;
        return Math.max(bar, ((s + bar - 1) / bar) * bar);
    }

    private void sort() {
        notes.sort(Comparator.comparingInt(Note::start).thenComparingInt(Note::pitch));
    }
}
