package com.abhinavmishra.sequencer;

import java.nio.file.Path;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.abhinavmishra.sequencer.ui.SequencerFrame;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // fall back to the default look and feel
            }
            SequencerFrame frame = new SequencerFrame();
            frame.setVisible(true);
            if (args.length > 0) {
                frame.openFile(Path.of(args[0]));
            }
        });
    }
}
