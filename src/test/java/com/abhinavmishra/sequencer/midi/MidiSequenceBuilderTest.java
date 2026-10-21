package com.abhinavmishra.sequencer.midi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import com.abhinavmishra.sequencer.model.Note;
import com.abhinavmishra.sequencer.model.Song;
import org.junit.jupiter.api.Test;

class MidiSequenceBuilderTest {

    private static List<ShortMessage> messages(Track track, int command) {
        List<ShortMessage> out = new ArrayList<>();
        for (int i = 0; i < track.size(); i++) {
            if (track.get(i).getMessage() instanceof ShortMessage sm && sm.getCommand() == command) {
                out.add(sm);
            }
        }
        return out;
    }

    private static List<Long> ticks(Track track, int command) {
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < track.size(); i++) {
            MidiEvent e = track.get(i);
            if (e.getMessage() instanceof ShortMessage sm && sm.getCommand() == command) {
                out.add(e.getTick());
            }
        }
        return out;
    }

    @Test
    void notesBecomeTimedNoteOnAndOffEvents() throws Exception {
        Song song = new Song();
        song.setInstrument(40);
        song.add(new Note(60, 0, 2, 100));
        song.add(new Note(67, 4, 1, 50));

        Sequence seq = MidiSequenceBuilder.build(song);
        assertEquals(Sequence.PPQ, seq.getDivisionType());
        assertEquals(24, seq.getResolution());
        Track track = seq.getTracks()[0];

        List<ShortMessage> program = messages(track, ShortMessage.PROGRAM_CHANGE);
        assertEquals(40, program.get(0).getData1());

        List<ShortMessage> ons = messages(track, ShortMessage.NOTE_ON);
        assertEquals(List.of(60, 67), ons.stream().map(ShortMessage::getData1).toList());
        assertEquals(List.of(100, 50), ons.stream().map(ShortMessage::getData2).toList());

        int t = MidiSequenceBuilder.TICKS_PER_STEP;
        assertEquals(List.of(0L, 4L * t), ticks(track, ShortMessage.NOTE_ON));
        assertEquals(List.of(2L * t - 1, 5L * t - 1), ticks(track, ShortMessage.NOTE_OFF));
    }

    @Test
    void sequenceLastsTheWholeSongEvenWhenTheEndIsSilent() throws Exception {
        Song song = new Song(); // 64 steps
        song.add(new Note(60, 0, 1, 100));
        Sequence seq = MidiSequenceBuilder.build(song);
        assertEquals(64L * MidiSequenceBuilder.TICKS_PER_STEP, seq.getTickLength());
    }
}
