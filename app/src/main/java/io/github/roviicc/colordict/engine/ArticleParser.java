package io.github.roviicc.colordict.engine;

import java.util.Arrays;

/**
 * Splits a raw .dict payload into typed blocks.
 *
 * <p>With a {@code sametypesequence} the type characters come from the .ifo
 * and the payload holds just the block bodies: lower-case blocks are
 * NUL-terminated, upper-case blocks carry a u32 BE length — and the final
 * block omits its terminator/length, running to the end of the payload.
 *
 * <p>Without a {@code sametypesequence} every block starts with its own type
 * byte and keeps its terminator/length.
 */
public final class ArticleParser {

    private ArticleParser() {
    }

    public static Article parse(byte[] data, String sametypesequence) {
        Article article = new Article();
        if (sametypesequence != null && !sametypesequence.isEmpty()) {
            parseWithSequence(article, data, sametypesequence);
        } else {
            parseTyped(article, data);
        }
        return article;
    }

    private static void parseWithSequence(Article article, byte[] data, String seq) {
        int pos = 0;
        for (int i = 0; i < seq.length() && pos <= data.length; i++) {
            char type = seq.charAt(i);
            boolean last = i == seq.length() - 1;
            if (Character.isLowerCase(type)) {
                if (last) {
                    article.add(type, Arrays.copyOfRange(data, pos, data.length));
                    pos = data.length;
                } else {
                    int nul = indexOfNul(data, pos);
                    if (nul < 0) { // malformed: treat the rest as this block
                        article.add(type, Arrays.copyOfRange(data, pos, data.length));
                        pos = data.length;
                    } else {
                        article.add(type, Arrays.copyOfRange(data, pos, nul));
                        pos = nul + 1;
                    }
                }
            } else {
                if (last) {
                    article.add(type, Arrays.copyOfRange(data, pos, data.length));
                    pos = data.length;
                } else {
                    if (pos + 4 > data.length) {
                        break;
                    }
                    int size = readU32(data, pos);
                    pos += 4;
                    int end = (int) Math.min((long) pos + size, data.length);
                    article.add(type, Arrays.copyOfRange(data, pos, end));
                    pos = end;
                }
            }
        }
    }

    private static void parseTyped(Article article, byte[] data) {
        int pos = 0;
        while (pos < data.length) {
            char type = (char) (data[pos] & 0xFF);
            if (!Character.isLetter(type)) {
                break; // corrupt stream; stop rather than loop
            }
            pos++;
            if (Character.isLowerCase(type)) {
                int nul = indexOfNul(data, pos);
                int end = nul < 0 ? data.length : nul;
                article.add(type, Arrays.copyOfRange(data, pos, end));
                pos = nul < 0 ? data.length : nul + 1;
            } else {
                if (pos + 4 > data.length) {
                    break;
                }
                int size = readU32(data, pos);
                pos += 4;
                int end = (int) Math.min((long) pos + size, data.length);
                article.add(type, Arrays.copyOfRange(data, pos, end));
                pos = end;
            }
        }
    }

    private static int indexOfNul(byte[] data, int from) {
        for (int i = from; i < data.length; i++) {
            if (data[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    private static int readU32(byte[] b, int pos) {
        return ((b[pos] & 0xFF) << 24) | ((b[pos + 1] & 0xFF) << 16)
                | ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
    }
}
