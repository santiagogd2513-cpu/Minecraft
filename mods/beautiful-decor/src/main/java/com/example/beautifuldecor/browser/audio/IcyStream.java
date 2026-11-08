package com.example.beautifuldecor.browser.audio;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * V3.5.0: quita los "metadatos en la transmisión" de una radio en vivo (Icecast/Shoutcast) y avisa del título que suena.
 * <p>
 * Si al pedir la emisora se manda la cabecera {@code Icy-MetaData: 1}, el servidor contesta con {@code icy-metaint: N} y, cada N
 * bytes de sonido, mete un bloque de texto: un byte con su largo (en grupos de 16 bytes) y luego algo como
 * {@code StreamTitle='Artista - Canción';}. Este filtro deja pasar solo el sonido (el decodificador de MP3 no se entera de nada) y
 * le pasa el título a quien lo pida. Es Java puro (se prueba fuera del juego: tools/v350/java/RadioTest.java).
 */
public final class IcyStream extends FilterInputStream {
    /** Un bloque de metadatos mide como mucho 255 × 16 bytes. */
    public static final int MAX_META = 255 * 16;

    private final int metaint;
    private final Consumer<String> titles;
    private int untilMeta;
    private String last = "";

    /**
     * @param in      la transmisión tal cual llega
     * @param metaint cada cuántos bytes de sonido viene un bloque de metadatos (la cabecera icy-metaint), mayor que 0
     * @param titles  a quién avisar cuando cambia el título (puede ser null)
     */
    public IcyStream(InputStream in, int metaint, Consumer<String> titles) {
        super(in);
        if (metaint <= 0) {
            throw new IllegalArgumentException("icy-metaint debe ser mayor que 0");
        }
        this.metaint = metaint;
        this.titles = titles;
        this.untilMeta = metaint;
    }

    /** El título que está sonando ("" si la emisora no mandó ninguno todavía). */
    public String lastTitle() {
        return this.last;
    }

    @Override
    public int read() throws IOException {
        if (this.untilMeta == 0 && !skipMetadata()) {
            return -1;
        }
        int b = this.in.read();
        if (b >= 0) {
            this.untilMeta--;
        }
        return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (this.untilMeta == 0 && !skipMetadata()) {
            return -1;
        }
        int n = this.in.read(buf, off, Math.min(len, this.untilMeta));
        if (n > 0) {
            this.untilMeta -= n;
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        long done = 0;
        byte[] tmp = new byte[4096];
        while (done < n) {
            int got = read(tmp, 0, (int) Math.min(tmp.length, n - done));
            if (got <= 0) {
                break;
            }
            done += got;
        }
        return done;
    }

    @Override
    public int available() throws IOException {
        return Math.min(this.in.available(), this.untilMeta);
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public synchronized void mark(int readlimit) {
        // no se puede: el filtro va contando bytes
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("mark/reset no se puede en una transmisión con metadatos");
    }

    /** Lee (y quita) el bloque de metadatos que toca ahora. Falso si la transmisión se terminó. */
    private boolean skipMetadata() throws IOException {
        int size = this.in.read();
        if (size < 0) {
            return false;
        }
        int bytes = size * 16;
        if (bytes > 0) {
            byte[] meta = new byte[bytes];
            int got = 0;
            while (got < bytes) {
                int n = this.in.read(meta, got, bytes - got);
                if (n < 0) {
                    return false;
                }
                got += n;
            }
            String title = streamTitle(meta);
            if (title != null && !title.equals(this.last)) {
                this.last = title;
                if (this.titles != null) {
                    this.titles.accept(title);
                }
            }
        }
        this.untilMeta = this.metaint;
        return true;
    }

    /**
     * El título de un bloque de metadatos ({@code StreamTitle='...';}), sin espacios a los lados; null si el bloque no trae título.
     * Acepta comillas dentro del título ("Guns N' Roses"): termina en la primera {@code ';} que cierra. Se lee como UTF-8 y, si no lo
     * es, como Latin-1 (las emisoras viejas).
     */
    public static String streamTitle(byte[] meta) {
        int end = meta.length;
        while (end > 0 && meta[end - 1] == 0) {
            end--;                                          // el bloque se rellena con ceros hasta múltiplo de 16
        }
        String text = decode(meta, end);
        String key = "StreamTitle='";
        int start = text.indexOf(key);
        if (start < 0) {
            return null;
        }
        start += key.length();
        int stop = text.indexOf("';", start);
        if (stop < 0) {
            stop = text.lastIndexOf('\'');
            if (stop < start) {
                stop = text.length();
            }
        }
        StringBuilder clean = new StringBuilder();
        for (char c : text.substring(start, stop).toCharArray()) {
            clean.append(Character.isISOControl(c) ? ' ' : c);
        }
        String title = clean.toString().trim();
        return title.length() > 120 ? title.substring(0, 120) : title;
    }

    private static String decode(byte[] data, int length) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data, 0, length))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(data, 0, length, StandardCharsets.ISO_8859_1);
        }
    }
}
