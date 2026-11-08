package com.example.beautifuldecor.megaphone.client;

import com.example.beautifuldecor.megaphone.PaDsp;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * V4.0.0: plan B para capturar el micrófono, con Java Sound (solo si OpenAL no pudo). Esta clase se carga únicamente si hace falta:
 * en algunos entornos (Android) Java Sound ni existe, y ahí se captura la excepción.
 */
final class JavaSoundMicBackend implements MicBackend {
    private TargetDataLine line;
    private byte[] raw = new byte[PaDsp.FRAME * 4 * 2];

    @Override
    public String name() {
        return "JavaSound";
    }

    @Override
    public void open() throws Exception {
        AudioFormat format = new AudioFormat(PaDsp.RATE, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new IllegalStateException("Java Sound no encontró un micrófono");
        }
        TargetDataLine opened = (TargetDataLine) AudioSystem.getLine(info);
        opened.open(format, PaDsp.RATE * 2);
        opened.start();
        this.line = opened;
    }

    @Override
    public int read(short[] out, int max) {
        TargetDataLine current = this.line;
        if (current == null) {
            return 0;
        }
        int availableBytes = current.available();
        int n = Math.min(Math.min(availableBytes / 2, max), this.raw.length / 2);
        if (n <= 0) {
            return 0;
        }
        int got = current.read(this.raw, 0, n * 2) / 2;
        for (int i = 0; i < got; i++) {
            out[i] = (short) ((this.raw[2 * i] & 0xFF) | (this.raw[2 * i + 1] << 8));
        }
        return got;
    }

    @Override
    public void close() {
        TargetDataLine current = this.line;
        this.line = null;
        if (current != null) {
            current.stop();
            current.close();
        }
    }
}
