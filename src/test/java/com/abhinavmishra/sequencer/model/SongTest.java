package com.abhinavmishra.sequencer.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class SongTest {

    @Test
    void addingOverlappingNoteOnSamePitchReplacesIt() {
        Song song = new Song();
        song.add(new Note(60, 0, 4, 100));
        song.add(new Note(60, 2, 1, 80));
        assertEquals(List.of(new Note(60, 2, 1, 80)), song.notes());
    }

    @Test
    void notesOnDifferentPitchesCanStack() {
        Song song = new Song();
        song.addAll(List.of(new Note(60, 0, 4, 100), new Note(64, 0, 4, 100), new Note(67, 0, 4, 100)));
        assertEquals(3, song.notes().size());
    }

    @Test
    void noteAtFindsNoteCoveringStep() {
        Song song = new Song();
        Note n = new Note(62, 4, 3, 100);
        song.add(n);
        assertEquals(n, song.noteAt(62, 6).orElseThrow());
        assertTrue(song.noteAt(62, 7).isEmpty());
        assertTrue(song.noteAt(61, 5).isEmpty());
    }

    @Test
    void replaceIsOneChange() {
        Song song = new Song();
        Note a = new Note(60, 0, 1, 100);
        song.add(a);
        AtomicInteger changes = new AtomicInteger();
        song.addListener(s -> changes.incrementAndGet());
        song.replace(List.of(a), List.of(a.movedBy(4, 2)));
        assertEquals(1, changes.get());
        assertEquals(List.of(new Note(62, 4, 1, 100)), song.notes());
    }

    @Test
    void songGrowsByWholeBarsWhenNotesPassTheEnd() {
        Song song = new Song();
        song.add(new Note(60, 70, 2, 100));
        assertEquals(80, song.steps());
    }

    @Test
    void cannotShrinkSongBelowLastNote() {
        Song song = new Song();
        song.add(new Note(60, 40, 2, 100));
        song.setSteps(16);
        assertEquals(48, song.steps());
    }

    @Test
    void notesInRangeUsesInclusivePitchesAndOverlappingSteps() {
        Song song = new Song();
        Note inside = new Note(60, 2, 4, 100);
        Note startsBefore = new Note(62, 0, 3, 100);
        Note outsidePitch = new Note(70, 2, 1, 100);
        Note outsideTime = new Note(61, 10, 1, 100);
        song.addAll(List.of(inside, startsBefore, outsidePitch, outsideTime));
        List<Note> found = song.notesInRange(60, 62, 2, 6);
        assertEquals(2, found.size());
        assertTrue(found.containsAll(List.of(inside, startsBefore)));
    }

    @Test
    void validatesSettings() {
        Song song = new Song();
        assertThrows(IllegalArgumentException.class, () -> song.setTempoBpm(5));
        assertThrows(IllegalArgumentException.class, () -> song.setInstrument(200));
    }
}
