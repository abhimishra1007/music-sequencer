package com.abhinavmishra.sequencer.model;

/**
 * One note on the grid. Immutable: editing a note replaces it in the {@link Song}.
 *
 * @param pitch    MIDI note number (60 = middle C)
 * @param start    first step the note plays on (a step is one grid column)
 * @param length   number of steps the note lasts, at least 1
 * @param velocity MIDI velocity 1-127, i.e. how loud the note is
 */
public record Note(int pitch, int start, int length, int velocity) {

    public static final int MIN_VELOCITY = 1;
    public static final int MAX_VELOCITY = 127;
    public static final int DEFAULT_VELOCITY = 100;

    public Note {
        if (pitch < 0 || pitch > 127) {
            throw new IllegalArgumentException("pitch out of MIDI range: " + pitch);
        }
        if (start < 0) {
            throw new IllegalArgumentException("start must be >= 0: " + start);
        }
        if (length < 1) {
            throw new IllegalArgumentException("length must be >= 1: " + length);
        }
        velocity = Math.max(MIN_VELOCITY, Math.min(MAX_VELOCITY, velocity));
    }

    /** First step after the note ends. */
    public int end() {
        return start + length;
    }

    public boolean covers(int step) {
        return step >= start && step < end();
    }

    public boolean overlaps(Note other) {
        return pitch == other.pitch && start < other.end() && other.start < end();
    }

    public Note withLength(int newLength) {
        return new Note(pitch, start, newLength, velocity);
    }

    public Note withVelocity(int newVelocity) {
        return new Note(pitch, start, length, newVelocity);
    }

    public Note movedBy(int dSteps, int dPitch) {
        return new Note(pitch + dPitch, start + dSteps, length, velocity);
    }

    public static String pitchName(int pitch) {
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        return names[pitch % 12] + (pitch / 12 - 1);
    }

    public static boolean isBlackKey(int pitch) {
        int pc = pitch % 12;
        return pc == 1 || pc == 3 || pc == 6 || pc == 8 || pc == 10;
    }

    @Override
    public String toString() {
        return pitchName(pitch) + "@" + start + "+" + length + " v" + velocity;
    }
}
