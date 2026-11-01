package io.github.roviicc.colordict.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.junit.Test;

public class DictionaryApiTest {

    @Test
    public void encodesWordsInTheRequestUrl() {
        assertEquals("https://d.example/api/define?word=in%20lieu%20of",
                DictionaryApi.requestUrl("https://d.example", "in lieu of"));
        assertEquals("https://d.example/api/define?word=caf%C3%A9",
                DictionaryApi.requestUrl("https://d.example", "café"));
    }

    @Test
    public void prefersTheServersErrorMessage() {
        assertEquals("Too many lookups right now.", DictionaryApi.errorMessage(429,
                "{\"error\":{\"code\":\"busy\",\"message\":\"Too many lookups right now.\"}}"));
        assertEquals("The dictionary server had a problem (502).",
                DictionaryApi.errorMessage(502, "<html>Bad gateway</html>"));
        assertEquals("The dictionary server address looks wrong. Check it in Settings.",
                DictionaryApi.errorMessage(404, ""));
    }

    @Test
    public void missingServerIsANonRetryableError() {
        try {
            new DictionaryApi("  ", "").define("serene");
            fail("expected LookupException");
        } catch (DictionaryApi.LookupException e) {
            assertFalse(e.retryable);
        }
    }
}
