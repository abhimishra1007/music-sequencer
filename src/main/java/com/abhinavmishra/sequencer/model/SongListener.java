package com.abhinavmishra.sequencer.model;

/** Notified whenever a {@link Song} changes, so the view and the MIDI player stay in sync. */
@FunctionalInterface
public interface SongListener {
    void songChanged(Song song);
}
