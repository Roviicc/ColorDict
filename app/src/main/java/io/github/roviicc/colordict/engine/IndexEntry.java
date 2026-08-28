package io.github.roviicc.colordict.engine;

/** One headword record from a StarDict .idx file. */
public final class IndexEntry {

    /** Position of this entry within the index (0-based). */
    public final int position;
    public final String word;
    /** Byte offset of the article in the (uncompressed) .dict payload. */
    public final long offset;
    /** Byte length of the article. */
    public final int size;

    IndexEntry(int position, String word, long offset, int size) {
        this.position = position;
        this.word = word;
        this.offset = offset;
        this.size = size;
    }

    @Override
    public String toString() {
        return word + "@" + offset + "+" + size;
    }
}
