package com.abhinavmishra.sequencer.ui;

import com.abhinavmishra.sequencer.model.Note;

/** Callbacks from the note grid to the window. */
public interface GridListener {

    /** A note was placed, moved, or had its volume changed and should be heard. */
    void previewNote(Note note);

    /** Short message for the status bar. */
    void status(String message);
}
