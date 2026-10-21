package com.abhinavmishra.sequencer.midi;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;

import com.abhinavmishra.sequencer.model.Song;

/**
 * Sound output through the Java MIDI API: instant note previews while editing,
 * and looped playback of the whole song on a {@link Sequencer}. When the song
 * changes during playback the sequence is rebuilt and playback continues from
 * the same position, so edits are heard immediately.
 */
public class MidiPlayer implements AutoCloseable {

    private static final long PREVIEW_MS = 300;

    private final Synthesizer synth;
    private final Sequencer sequencer;
    private final MidiChannel channel;
    private final ScheduledExecutorService noteOffs = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "note-off");
        t.setDaemon(true);
        return t;
    });

    public MidiPlayer() throws MidiUnavailableException {
        synth = MidiSystem.getSynthesizer();
        synth.open();
        sequencer = MidiSystem.getSequencer(false); // not connected to the default device
        sequencer.open();
        sequencer.getTransmitter().setReceiver(synth.getReceiver());
        channel = synth.getChannels()[MidiSequenceBuilder.CHANNEL];
    }

    /** Plays one note right away (used when placing, moving, or changing the volume of a note). */
    public void preview(int pitch, int velocity, int instrument) {
        if (!sequencer.isRunning()) {
            channel.programChange(instrument);
        }
        channel.noteOn(pitch, velocity);
        noteOffs.schedule(() -> channel.noteOff(pitch), PREVIEW_MS, TimeUnit.MILLISECONDS);
    }

    public void play(Song song) throws InvalidMidiDataException {
        load(song, 0);
        sequencer.start();
    }

    /** Rebuilds the sequence after an edit without restarting playback. */
    public void update(Song song) throws InvalidMidiDataException {
        if (!sequencer.isRunning()) {
            return;
        }
        long tick = sequencer.getTickPosition();
        load(song, tick % Math.max(1, MidiSequenceBuilder.lengthInTicks(song)));
    }

    private void load(Song song, long startTick) throws InvalidMidiDataException {
        sequencer.setSequence(MidiSequenceBuilder.build(song));
        sequencer.setTempoInBPM(song.tempoBpm());
        sequencer.setLoopStartPoint(0);
        sequencer.setLoopEndPoint(MidiSequenceBuilder.lengthInTicks(song));
        sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
        sequencer.setTickPosition(startTick);
    }

    public void stop() {
        sequencer.stop();
        channel.allNotesOff();
    }

    public boolean isPlaying() {
        return sequencer.isRunning();
    }

    /** Current playback position in grid steps, or -1 when stopped. */
    public double currentStep() {
        return sequencer.isRunning() ? (double) sequencer.getTickPosition() / MidiSequenceBuilder.TICKS_PER_STEP : -1;
    }

    /** Master volume, 0-127 (MIDI controller 7). */
    public void setVolume(int volume) {
        channel.controlChange(7, Math.max(0, Math.min(127, volume)));
    }

    @Override
    public void close() {
        noteOffs.shutdownNow();
        sequencer.close();
        synth.close();
    }
}
