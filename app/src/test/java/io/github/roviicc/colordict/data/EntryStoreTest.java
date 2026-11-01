package io.github.roviicc.colordict.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EntryStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void storesAndReadsEntriesCaseInsensitively() throws Exception {
        EntryStore store = new EntryStore(tmp.newFolder("entries"));
        assertNull(store.get("serene"));
        store.put("Serene", Entry.fromJson(EntryTest.SERENE));
        Entry e = store.get("  SERENE ");
        assertNotNull(e);
        assertEquals("serene", e.word);
        assertEquals(1, store.clear());
        assertNull(store.get("serene"));
    }

    @Test
    public void keyNormalizesCaseAndSpacing() {
        assertEquals("in lieu of", EntryStore.key("  In   LIEU of "));
    }
}
