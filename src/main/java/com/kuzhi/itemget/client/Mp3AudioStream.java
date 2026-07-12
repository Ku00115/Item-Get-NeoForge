package com.kuzhi.itemget.client;

import javazoom.jl.decoder.*;
import net.minecraft.client.sounds.AudioStream;
import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Queue;

final class Mp3AudioStream implements AudioStream {
    private final Bitstream bitstream; private final Decoder decoder = new Decoder(); private final Queue<ByteBuffer> pending = new ArrayDeque<>();
    private AudioFormat format; private boolean ended;
    Mp3AudioStream(InputStream input) throws IOException { bitstream = new Bitstream(input); decode(); if (format == null) format = new AudioFormat(44100, 16, 2, true, false); }
    @Override public AudioFormat getFormat() { return format; }
    @Override public ByteBuffer read(int size) throws IOException {
        ByteBuffer out = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN);
        while (out.hasRemaining()) { if (pending.isEmpty()) { if (ended) break; decode(); if (pending.isEmpty() && ended) break; }
            ByteBuffer next = pending.peek(); int count = Math.min(out.remaining(), next.remaining()); int limit = next.limit(); next.limit(next.position() + count); out.put(next); next.limit(limit); if (!next.hasRemaining()) pending.remove(); }
        out.flip(); return out;
    }
    @Override public void close() throws IOException { try { bitstream.close(); } catch (JavaLayerException e) { throw new IOException(e); } pending.clear(); ended = true; }
    private void decode() throws IOException { try { Header header = bitstream.readFrame(); if (header == null) { ended = true; return; } SampleBuffer samples = (SampleBuffer)decoder.decodeFrame(header, bitstream); if (format == null) format = new AudioFormat(samples.getSampleFrequency(), 16, samples.getChannelCount(), true, false); ByteBuffer buffer = ByteBuffer.allocateDirect(samples.getBufferLength() * 2).order(ByteOrder.LITTLE_ENDIAN); for (int i=0;i<samples.getBufferLength();i++) buffer.putShort(samples.getBuffer()[i]); buffer.flip(); pending.add(buffer); } catch (JavaLayerException e) { throw new IOException(e); } finally { bitstream.closeFrame(); } }
}
