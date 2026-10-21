package com.abhinavmishra.sequencer.io;

public class SongFormatException extends IllegalArgumentException {

    public SongFormatException(int line, String message) {
        super(line > 0 ? "line " + line + ": " + message : message);
    }
}
