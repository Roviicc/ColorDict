package io.github.roviicc.colordict.engine;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Random access into a dictzip (.dz) file.
 *
 * <p>dictzip is ordinary gzip whose deflate stream was flushed (Z_FULL_FLUSH)
 * at fixed uncompressed intervals, with the compressed length of every chunk
 * recorded in a gzip FEXTRA subfield ('R','A'). Because a full flush resets
 * the compressor state on a byte boundary, each chunk can be inflated on its
 * own with a raw inflater, giving O(1) seeks into the uncompressed payload.
 */
public final class DictZipFile implements Closeable {

    private static final int FTEXT = 1;
    private static final int FHCRC = 2;
    private static final int FEXTRA = 4;
    private static final int FNAME = 8;
    private static final int FCOMMENT = 16;
    private static final int CACHED_CHUNKS = 16;

    private final RandomAccessFile raf;
    private final int chunkLen;
    private final long[] chunkOffsets;   // absolute file offset of each compressed chunk
    private final int[] chunkSizes;      // compressed size of each chunk
    private final long uncompressedLength;

    private final LinkedHashMap<Integer, byte[]> cache =
            new LinkedHashMap<Integer, byte[]>(CACHED_CHUNKS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> eldest) {
                    return size() > CACHED_CHUNKS;
                }
            };

    public DictZipFile(File file) throws IOException {
        raf = new RandomAccessFile(file, "r");
        try {
            if (raf.length() < 20) {
                throw new IOException(file.getName() + ": too short for a dictzip file");
            }
            raf.seek(0);
            if (raf.read() != 0x1F || raf.read() != 0x8B) {
                throw new IOException(file.getName() + ": not a gzip file");
            }
            if (raf.read() != 8) {
                throw new IOException(file.getName() + ": unsupported gzip compression method");
            }
            int flg = raf.read();
            skip(6); // MTIME, XFL, OS

            int foundChunkLen = -1;
            int[] sizes = null;
            if ((flg & FEXTRA) != 0) {
                int xlen = readU16le();
                byte[] extra = new byte[xlen];
                raf.readFully(extra);
                int pos = 0;
                while (pos + 4 <= extra.length) {
                    int si1 = extra[pos] & 0xFF;
                    int si2 = extra[pos + 1] & 0xFF;
                    int slen = (extra[pos + 2] & 0xFF) | ((extra[pos + 3] & 0xFF) << 8);
                    int dataAt = pos + 4;
                    if (si1 == 'R' && si2 == 'A' && dataAt + slen <= extra.length && slen >= 6) {
                        int ver = (extra[dataAt] & 0xFF) | ((extra[dataAt + 1] & 0xFF) << 8);
                        if (ver != 1) {
                            throw new IOException(file.getName() + ": dictzip RA version " + ver);
                        }
                        foundChunkLen = (extra[dataAt + 2] & 0xFF) | ((extra[dataAt + 3] & 0xFF) << 8);
                        int chcnt = (extra[dataAt + 4] & 0xFF) | ((extra[dataAt + 5] & 0xFF) << 8);
                        if (slen < 6 + 2 * chcnt) {
                            throw new IOException(file.getName() + ": truncated dictzip RA field");
                        }
                        sizes = new int[chcnt];
                        for (int i = 0; i < chcnt; i++) {
                            sizes[i] = (extra[dataAt + 6 + 2 * i] & 0xFF)
                                    | ((extra[dataAt + 7 + 2 * i] & 0xFF) << 8);
                        }
                    }
                    pos = dataAt + slen;
                }
            }
            if (sizes == null || foundChunkLen <= 0) {
                throw new IOException(file.getName()
                        + ": no dictzip random-access data (plain gzip? re-compress with dictzip)");
            }
            if ((flg & FNAME) != 0) {
                skipZeroTerminated();
            }
            if ((flg & FCOMMENT) != 0) {
                skipZeroTerminated();
            }
            if ((flg & FHCRC) != 0) {
                skip(2);
            }

            chunkLen = foundChunkLen;
            chunkSizes = sizes;
            chunkOffsets = new long[sizes.length];
            long at = raf.getFilePointer();
            for (int i = 0; i < sizes.length; i++) {
                chunkOffsets[i] = at;
                at += sizes[i];
            }
            if (at > raf.length() - 8) {
                throw new IOException(file.getName() + ": dictzip chunk table exceeds file size");
            }

            raf.seek(raf.length() - 4); // gzip ISIZE trailer (uncompressed length mod 2^32)
            long isize = readU16le() | ((long) readU16le() << 16);
            long maxByTable = (long) chunkLen * sizes.length;
            uncompressedLength = (isize <= maxByTable) ? isize : maxByTable;
        } catch (IOException e) {
            raf.close();
            throw e;
        }
    }

    private void skip(int n) throws IOException {
        raf.seek(raf.getFilePointer() + n);
    }

    private void skipZeroTerminated() throws IOException {
        int b;
        do {
            b = raf.read();
            if (b < 0) {
                throw new EOFException("unterminated gzip header field");
            }
        } while (b != 0);
    }

    private int readU16le() throws IOException {
        int lo = raf.read();
        int hi = raf.read();
        if (lo < 0 || hi < 0) {
            throw new EOFException("truncated gzip header");
        }
        return lo | (hi << 8);
    }

    public long length() {
        return uncompressedLength;
    }

    /** Reads {@code size} uncompressed bytes starting at {@code offset}. */
    public synchronized byte[] read(long offset, int size) throws IOException {
        if (offset < 0 || size < 0 || offset + size > uncompressedLength) {
            throw new IOException("read beyond dictzip payload: " + offset + "+" + size
                    + " of " + uncompressedLength);
        }
        byte[] out = new byte[size];
        int copied = 0;
        while (copied < size) {
            long at = offset + copied;
            int chunkIndex = (int) (at / chunkLen);
            int within = (int) (at % chunkLen);
            byte[] chunk = chunk(chunkIndex);
            int n = Math.min(size - copied, chunk.length - within);
            if (n <= 0) {
                throw new IOException("dictzip chunk " + chunkIndex + " shorter than expected");
            }
            System.arraycopy(chunk, within, out, copied, n);
            copied += n;
        }
        return out;
    }

    private byte[] chunk(int index) throws IOException {
        byte[] cached = cache.get(index);
        if (cached != null) {
            return cached;
        }
        byte[] comp = new byte[chunkSizes[index]];
        raf.seek(chunkOffsets[index]);
        raf.readFully(comp);

        int expected = (int) Math.min(chunkLen,
                uncompressedLength - (long) index * chunkLen);
        byte[] out = new byte[expected];
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(comp);
            int have = 0;
            while (have < expected && !inflater.finished()) {
                int n = inflater.inflate(out, have, expected - have);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                have += n;
            }
            if (have < expected) {
                throw new IOException("dictzip chunk " + index + " inflated to "
                        + have + " of " + expected + " bytes");
            }
        } catch (DataFormatException e) {
            throw new IOException("corrupt dictzip chunk " + index, e);
        } finally {
            inflater.end();
        }
        cache.put(index, out);
        return out;
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}
