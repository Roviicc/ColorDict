package io.github.roviicc.colordict.data.claude;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JsonStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void savesConnotationsPerWordCaseInsensitively() throws Exception {
        JsonStore store = new JsonStore(tmp.newFolder("connotations"));
        assertNull(store.get("serene"));
        store.put("Serene", new JSONObject("{\"tone\":\"positive\",\"summary\":\"Calm.\"}"));
        JSONObject c = store.get("  SERENE ");
        assertNotNull(c);
        assertEquals("positive", c.getString("tone"));
        assertEquals(1, store.clear());
        assertNull(store.get("serene"));
    }
}
