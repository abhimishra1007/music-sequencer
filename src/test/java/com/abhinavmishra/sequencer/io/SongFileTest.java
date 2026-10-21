package com.abhinavmishra.sequencer.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.abhinavmishra.sequencer.model.Note;
import com.abhinavmishra.sequencer.model.Song;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SongFileTest {

    private static Song sample() {
        Song song = new Song();
        song.setTempoBpm(96);
        song.setInstrument(24);
        song.addAll(List.of(new Note(60, 0, 2, 100), new Note(64, 2, 2, 80), new Note(67, 4, 4, 64)));
        song.setSteps(32);
        return song;
    }

    @Test
    void roundTripsThroughAFile(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("song.seq");
        Song original = sample();
        SongFile.save(original, path);
        Song loaded = SongFile.load(path);

        assertEquals(original.notes(), loaded.notes());
        assertEquals(96, loaded.tempoBpm());
        assertEquals(24, loaded.instrument());
        assertEquals(32, loaded.steps());
        assertTrue(Files.readString(path).contains("note 64 2 2 80"));
    }

    @Test
    void ignoresCommentsAndBlankLines() throws Exception {
        String text = """
                # hand-written
                format 1

                tempo 140
                note 72 0 1 90   
                """;
        Song song = SongFile.read(new StringReader(text));
        assertEquals(140, song.tempoBpm());
        assertEquals(List.of(new Note(72, 0, 1, 90)), song.notes());
    }

    @Test
    void reportsLineOfBadInput() {
        String text = "format 1\ntempo 120\nnote 60 0 zero 100\n";
        SongFormatException e = assertThrows(SongFormatException.class, () -> SongFile.read(new StringReader(text)));
        assertTrue(e.getMessage().startsWith("line 3"), e.getMessage());
    }

    @Test
    void rejectsInvalidNoteValues() {
        String text = "format 1\nnote 60 0 0 100\n";
        SongFormatException e = assertThrows(SongFormatException.class, () -> SongFile.read(new StringReader(text)));
        assertTrue(e.getMessage().contains("length"), e.getMessage());
    }

    @Test
    void rejectsFilesWithoutFormatLine() {
        assertThrows(SongFormatException.class, () -> SongFile.read(new StringReader("tempo 120\n")));
    }

    @Test
    void writeProducesStableText() throws Exception {
        StringWriter out = new StringWriter();
        SongFile.write(sample(), out);
        assertEquals("""
                # Music Sequencer song
                format 1
                tempo 96
                instrument 24
                steps 32
                note 60 0 2 100
                note 64 2 2 80
                note 67 4 4 64
                """, out.toString());
    }
}
