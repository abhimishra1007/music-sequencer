package com.abhinavmishra.sequencer.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoteTest {

    @Test
    void velocityIsClampedToMidiRange() {
        assertEquals(127, new Note(60, 0, 1, 500).velocity());
        assertEquals(1, new Note(60, 0, 1, -20).velocity());
        assertEquals(127, new Note(60, 0, 1, 120).withVelocity(135).velocity());
    }

    @Test
    void rejectsInvalidNotes() {
        assertThrows(IllegalArgumentException.class, () -> new Note(128, 0, 1, 100));
        assertThrows(IllegalArgumentException.class, () -> new Note(60, -1, 1, 100));
        assertThrows(IllegalArgumentException.class, () -> new Note(60, 0, 0, 100));
    }

    @Test
    void overlapRequiresSamePitchAndSharedSteps() {
        Note a = new Note(60, 0, 4, 100);
        assertTrue(a.overlaps(new Note(60, 3, 2, 100)));
        assertFalse(a.overlaps(new Note(60, 4, 2, 100)));  // starts right after a ends
        assertFalse(a.overlaps(new Note(61, 0, 4, 100)));
    }

    @Test
    void pitchNames() {
        assertEquals("C4", Note.pitchName(60));
        assertEquals("A4", Note.pitchName(69));
        assertEquals("C#3", Note.pitchName(49));
        assertTrue(Note.isBlackKey(61));
        assertFalse(Note.isBlackKey(64));
    }
}
