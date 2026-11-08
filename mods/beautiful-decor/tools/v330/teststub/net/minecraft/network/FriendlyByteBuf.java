package net.minecraft.network;

import net.minecraft.core.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Pieza de mentira de FriendlyByteBuf: mismas operaciones, mismos tamaños de VarInt y de byte con signo que Minecraft, sobre un arreglo de bytes. */
public class FriendlyByteBuf {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private ByteArrayInputStream in;

    public FriendlyByteBuf() {
    }

    /** Pasa de escribir a leer lo que se escribió. */
    public FriendlyByteBuf startReading() {
        this.in = new ByteArrayInputStream(this.out.toByteArray());
        return this;
    }

    public int written() {
        return this.out.size();
    }

    public int unread() {
        return this.in.available();
    }

    private int next() {
        int b = this.in.read();
        if (b < 0) {
            throw new IllegalStateException("se leyó más de lo que se escribió");
        }
        return b;
    }

    public FriendlyByteBuf writeByte(int value) {
        this.out.write(value & 0xFF);
        return this;
    }

    /** Un byte CON signo, como ByteBuf.readByte(). */
    public byte readByte() {
        return (byte) next();
    }

    public FriendlyByteBuf writeVarInt(int value) {
        int v = value;
        while ((v & -128) != 0) {
            this.out.write((v & 127) | 128);
            v >>>= 7;
        }
        this.out.write(v);
        return this;
    }

    public int readVarInt() {
        int value = 0;
        int shift = 0;
        int b;
        do {
            b = next();
            value |= (b & 127) << (shift++ * 7);
            if (shift > 5) {
                throw new IllegalStateException("VarInt demasiado largo");
            }
        } while ((b & 128) == 128);
        return value;
    }

    public FriendlyByteBuf writeFloat(float value) {
        int bits = Float.floatToIntBits(value);
        for (int i = 3; i >= 0; i--) {
            this.out.write((bits >> (i * 8)) & 0xFF);
        }
        return this;
    }

    public float readFloat() {
        int bits = 0;
        for (int i = 0; i < 4; i++) {
            bits = (bits << 8) | next();
        }
        return Float.intBitsToFloat(bits);
    }

    public FriendlyByteBuf writeBoolean(boolean value) {
        this.out.write(value ? 1 : 0);
        return this;
    }

    public boolean readBoolean() {
        return next() != 0;
    }

    public FriendlyByteBuf writeUtf(String text, int maxLength) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (text.length() > maxLength) {
            throw new IllegalArgumentException("texto más largo que " + maxLength);
        }
        writeVarInt(bytes.length);
        try {
            this.out.write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    public String readUtf(int maxLength) {
        int n = readVarInt();
        byte[] bytes = new byte[n];
        for (int i = 0; i < n; i++) {
            bytes[i] = (byte) next();
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.length() > maxLength) {
            throw new IllegalStateException("texto más largo que " + maxLength);
        }
        return text;
    }

    public FriendlyByteBuf writeBlockPos(BlockPos pos) {
        writeVarInt(pos.getX());
        writeVarInt(pos.getY());
        writeVarInt(pos.getZ());
        return this;
    }

    public BlockPos readBlockPos() {
        return new BlockPos(readVarInt(), readVarInt(), readVarInt());
    }
}
