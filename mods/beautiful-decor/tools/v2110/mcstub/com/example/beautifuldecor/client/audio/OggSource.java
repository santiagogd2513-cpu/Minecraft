package com.example.beautifuldecor.client.audio;
import com.example.beautifuldecor.browser.audio.PcmSource;
import java.io.IOException;
public class OggSource implements PcmSource {
    public OggSource(byte[] data) throws IOException { }
    @Override public int channels() { return 2; }
    @Override public int sampleRate() { return 44100; }
    @Override public int read(byte[] buf) { return -1; }
    @Override public void close() { }
}
