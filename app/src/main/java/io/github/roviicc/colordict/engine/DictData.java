package io.github.roviicc.colordict.engine;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Random access to article payloads in a .dict file, transparently handling
 * both plain files and dictzip-compressed .dict.dz files.
 */
public final class DictData implements Closeable {

    /** Refuse to allocate articles larger than this (corrupt index guard). */
    private static final int MAX_ARTICLE_BYTES = 32 * 1024 * 1024;

    private final RandomAccessFile plain; // exactly one of plain/zipped is set
    private final DictZipFile zipped;

    private DictData(RandomAccessFile plain, DictZipFile zipped) {
        this.plain = plain;
        this.zipped = zipped;
    }

    public static DictData open(File file) throws IOException {
        if (file.getName().toLowerCase().endsWith(".dz")) {
            return new DictData(null, new DictZipFile(file));
        }
        return new DictData(new RandomAccessFile(file, "r"), null);
    }

    public long length() throws IOException {
        return zipped != null ? zipped.length() : plain.length();
    }

    public synchronized byte[] read(long offset, int size) throws IOException {
        if (size < 0 || size > MAX_ARTICLE_BYTES) {
            throw new IOException("implausible article size " + size);
        }
        if (zipped != null) {
            return zipped.read(offset, size);
        }
        if (offset < 0 || offset + size > plain.length()) {
            throw new IOException("read beyond .dict payload: " + offset + "+" + size);
        }
        byte[] out = new byte[size];
        plain.seek(offset);
        plain.readFully(out);
        return out;
    }

    @Override
    public void close() throws IOException {
        if (zipped != null) {
            zipped.close();
        }
        if (plain != null) {
            plain.close();
        }
    }
}
