package com.abhinavmishra.sequencer.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class NoteClipboardTest {

    @Test
    void pastesRelativeToEarliestCopiedNote() {
        NoteClipboard clip = new NoteClipboard();
        assertTrue(clip.isEmpty());
        clip.copy(List.of(new Note(60, 8, 2, 90), new Note(64, 10, 1, 70)));

        List<Note> pasted = clip.pasteAt(32);
        assertEquals(List.of(new Note(60, 32, 2, 90), new Note(64, 34, 1, 70)), pasted);
        assertEquals(3, clip.span());
    }

    @Test
    void copyTakesASnapshot() {
        NoteClipboard clip = new NoteClipboard();
        Song song = new Song();
        Note n = new Note(60, 0, 1, 100);
        song.add(n);
        clip.copy(song.notes());
        song.remove(n);
        assertEquals(List.of(n), clip.pasteAt(0));
    }
}
