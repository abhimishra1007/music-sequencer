package com.abhinavmishra.sequencer.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import com.abhinavmishra.sequencer.model.Note;
import com.abhinavmishra.sequencer.model.Song;

/** Converts a {@link Song} into a one-track MIDI {@link Sequence}. */
public final class MidiSequenceBuilder {

    /** Ticks per quarter note. 24 divides evenly into sixteenth-note steps. */
    public static final int RESOLUTION = 24;
    public static final int TICKS_PER_STEP = RESOLUTION / Song.STEPS_PER_BEAT;
    public static final int CHANNEL = 0;

    private MidiSequenceBuilder() {
    }

    public static Sequence build(Song song) throws InvalidMidiDataException {
        Sequence sequence = new Sequence(Sequence.PPQ, RESOLUTION);
        Track track = sequence.createTrack();

        track.add(event(ShortMessage.PROGRAM_CHANGE, song.instrument(), 0, 0));
        for (Note n : song.notes()) {
            long on = (long) n.start() * TICKS_PER_STEP;
            // End one tick early so a note-off never lands on the same tick as the
            // next note-on of the same pitch (which could cut the new note off).
            long off = (long) n.end() * TICKS_PER_STEP - 1;
            track.add(event(ShortMessage.NOTE_ON, n.pitch(), n.velocity(), on));
            track.add(event(ShortMessage.NOTE_OFF, n.pitch(), 0, off));
        }
        // Pin the track length to the full song so loops include trailing silence.
        track.add(event(ShortMessage.CONTROL_CHANGE, 110, 0, lengthInTicks(song)));
        return sequence;
    }

    public static long lengthInTicks(Song song) {
        return (long) song.steps() * TICKS_PER_STEP;
    }

    private static MidiEvent event(int command, int data1, int data2, long tick) throws InvalidMidiDataException {
        ShortMessage msg = new ShortMessage();
        msg.setMessage(command, CHANNEL, data1, data2);
        return new MidiEvent(msg, tick);
    }
}
