package com.example.beautifuldecor.browser.audio;

import java.io.Closeable;
import java.io.IOException;

/** Fuente de audio ya decodificado: PCM de 16 bits, little-endian, canales entrelazados (1 o 2). */
public interface PcmSource extends Closeable {
    int channels();

    int sampleRate();

    /** Lee hasta buf.length bytes de PCM. Devuelve cuántos leyó (siempre más de 0) o -1 al terminar. */
    int read(byte[] buf) throws IOException;
}
