package com.abhinavmishra.sequencer.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.abhinavmishra.sequencer.model.Note;
import com.abhinavmishra.sequencer.model.Song;

/**
 * Plain-text song format (.seq), easy to read and diff:
 * <pre>
 * # Music Sequencer song
 * format 1
 * tempo 120
 * instrument 0
 * steps 64
 * note 60 0 2 100      (pitch start length velocity)
 * </pre>
 */
public final class SongFile {

    public static final String EXTENSION = "seq";
    private static final int FORMAT_VERSION = 1;

    private SongFile() {
    }

    public static void save(Song song, Path path) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            write(song, w);
        }
    }

    public static Song load(Path path) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(r);
        }
    }

    public static void write(Song song, Writer out) throws IOException {
        out.write("# Music Sequencer song\n");
        out.write("format " + FORMAT_VERSION + "\n");
        out.write("tempo " + song.tempoBpm() + "\n");
        out.write("instrument " + song.instrument() + "\n");
        out.write("steps " + song.steps() + "\n");
        for (Note n : song.notes()) {
            out.write("note " + n.pitch() + " " + n.start() + " " + n.length() + " " + n.velocity() + "\n");
        }
        out.flush();
    }

    public static Song read(Reader in) throws IOException {
        BufferedReader r = in instanceof BufferedReader b ? b : new BufferedReader(in);
        Song song = new Song();
        List<Note> notes = new ArrayList<>();
        int steps = Song.DEFAULT_STEPS;
        boolean sawFormat = false;

        String line;
        int lineNo = 0;
        while ((line = r.readLine()) != null) {
            lineNo++;
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+");
            try {
                switch (parts[0]) {
                    case "format" -> {
                        int version = Integer.parseInt(parts[1]);
                        if (version != FORMAT_VERSION) {
                            throw new SongFormatException(lineNo, "unsupported format version " + version);
                        }
                        sawFormat = true;
                    }
                    case "tempo" -> song.setTempoBpm(Integer.parseInt(parts[1]));
                    case "instrument" -> song.setInstrument(Integer.parseInt(parts[1]));
                    case "steps" -> steps = Integer.parseInt(parts[1]);
                    case "note" -> {
                        if (parts.length != 5) {
                            throw new SongFormatException(lineNo, "expected: note pitch start length velocity");
                        }
                        notes.add(new Note(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                                Integer.parseInt(parts[3]), Integer.parseInt(parts[4])));
                    }
                    default -> throw new SongFormatException(lineNo, "unknown keyword '" + parts[0] + "'");
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                throw new SongFormatException(lineNo, "bad number in '" + line + "'");
            } catch (IllegalArgumentException e) {
                if (e instanceof SongFormatException sfe) {
                    throw sfe;
                }
                throw new SongFormatException(lineNo, e.getMessage());
            }
        }
        if (!sawFormat) {
            throw new SongFormatException(0, "not a Music Sequencer file (missing 'format' line)");
        }
        song.addAll(notes);
        song.setSteps(steps);
        return song;
    }
}
