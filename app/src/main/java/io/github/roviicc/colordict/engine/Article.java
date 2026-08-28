package io.github.roviicc.colordict.engine;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** A parsed .dict article: an ordered list of typed data blocks. */
public final class Article {

    /** One typed block of article data. */
    public static final class Block {
        /**
         * StarDict type identifier. Lower-case types hold UTF-8 text
         * ('m' plain, 'h' html, 'x' xdxf, 'g' pango, 't' phonetic, 'l' locale
         * text, 'y'/'k' Chinese/KingSoft, 'w' MediaWiki, 'n' WordNet,
         * 'r' resource list); upper-case types hold sized binary data
         * ('W' audio, 'P' picture, 'X' experimental).
         */
        public final char type;
        public final byte[] data;

        Block(char type, byte[] data) {
            this.type = type;
            this.data = data;
        }

        public String text() {
            int end = data.length;
            while (end > 0 && data[end - 1] == 0) {
                end--; // defensively drop stray trailing NULs from sloppy dictionaries
            }
            return new String(data, 0, end, StandardCharsets.UTF_8);
        }
    }

    public final List<Block> blocks = new ArrayList<>();

    void add(char type, byte[] data) {
        blocks.add(new Block(type, data));
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }
}
