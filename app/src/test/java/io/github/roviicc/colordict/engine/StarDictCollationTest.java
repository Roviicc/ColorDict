package io.github.roviicc.colordict.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class StarDictCollationTest {

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void foldIgnoresAsciiCaseOnly() {
        assertEquals(0, StarDictCollation.compareFold(b("Apple"), b("aPPle")));
        assertTrue(StarDictCollation.compareFold(b("apple"), b("banana")) < 0);
        // Non-ASCII bytes are NOT folded: 'É' != 'é' under the fold compare.
        assertTrue(StarDictCollation.compareFold(b("É"), b("é")) != 0);
    }

    @Test
    public void fullCompareBreaksTiesByBytes() {
        assertTrue(StarDictCollation.compare(b("APPLE"), b("Apple")) < 0);
        assertTrue(StarDictCollation.compare(b("Apple"), b("apple")) < 0);
        assertEquals(0, StarDictCollation.compare(b("apple"), b("apple")));
    }

    @Test
    public void shorterStringSortsFirstOnCommonPrefix() {
        assertTrue(StarDictCollation.compare(b("cat"), b("catalog")) < 0);
        assertTrue(StarDictCollation.compareFold(b("CAT"), b("catalog")) < 0);
    }

    @Test
    public void multiByteUtf8ComparesUnsigned() {
        // 0xC3... (ü) must sort after plain ASCII as an unsigned byte.
        assertTrue(StarDictCollation.compare(b("über"), b("zebra")) > 0);
    }

    @Test
    public void foldStartsWith() {
        assertTrue(StarDictCollation.foldStartsWith(b("Catalog"), b("cat")));
        assertTrue(StarDictCollation.foldStartsWith(b("cat"), b("CAT")));
        assertFalse(StarDictCollation.foldStartsWith(b("ca"), b("cat")));
        assertFalse(StarDictCollation.foldStartsWith(b("dog"), b("cat")));
    }
}
