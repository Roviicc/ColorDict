package io.github.roviicc.colordict.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/** Runs the real SDK client against a local fake of the Messages API. */
public class ClaudeDictionaryTest {

    private MockWebServer server;
    private String baseUrl;
    private String lastBody;
    private String lastBeta;
    private String lastKey;
    private int status = 200;
    private String reply;

    @Before
    public void start() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                lastBody = request.getBody().readUtf8();
                lastBeta = request.getHeader("anthropic-beta");
                // The SDK sends the key as x-api-key or as a Bearer token depending on API version.
                String auth = request.getHeader("Authorization");
                lastKey = request.getHeader("x-api-key") != null ? request.getHeader("x-api-key")
                        : auth != null ? auth.replaceFirst("^Bearer ", "") : null;
                return new MockResponse().setResponseCode(status)
                        .setHeader("Content-Type", "application/json")
                        .setBody(reply);
            }
        });
        server.start();
        baseUrl = server.url("/").toString().replaceAll("/$", "");
    }

    @After
    public void stop() throws Exception {
        server.shutdown();
    }

    private static String message(String text, String stopReason) throws Exception {
        return new JSONObject()
                .put("id", "msg_test").put("type", "message").put("role", "assistant")
                .put("model", "claude-opus-5-5")
                .put("content", new JSONArray().put(new JSONObject().put("type", "text").put("text", text)))
                .put("stop_reason", stopReason).put("stop_sequence", JSONObject.NULL)
                .put("usage", new JSONObject().put("input_tokens", 10).put("output_tokens", 20))
                .toString();
    }

    @Test
    public void definesAWordWithTheUsersKeyAndASchema() throws Exception {
        reply = message(EntryTest.SERENE, "end_turn");
        Entry e = new ClaudeDictionary("sk-ant-user-key", "claude-opus-5-5", baseUrl).define("serene");

        assertEquals("serene", e.word);
        assertEquals(2, e.senses.size());
        assertEquals("sk-ant-user-key", lastKey);

        JSONObject body = new JSONObject(lastBody);
        assertEquals("claude-opus-5-5", body.getString("model"));
        assertEquals("<headword>serene</headword>",
                body.getJSONArray("messages").getJSONObject(0).getString("content"));
        JSONObject format = body.getJSONObject("output_config").getJSONObject("format");
        assertEquals("json_schema", format.getString("type"));
        JSONObject props = format.getJSONObject("schema").getJSONObject("properties");
        assertTrue(props.has("senses"));
        assertFalse("definitions must not include connotation", props.has("connotation"));
        assertEquals("low", body.getJSONObject("output_config").getString("effort"));
        assertEquals("default", body.getString("fallbacks"));
        assertTrue(lastBeta.contains("server-side-fallback-2026-07-01"));
    }

    @Test
    public void haikuRequestsSkipServerFallbacks() throws Exception {
        reply = message(EntryTest.SERENE, "end_turn");
        new ClaudeDictionary("k", "claude-haiku-5-5", baseUrl).define("serene");
        JSONObject body = new JSONObject(lastBody);
        assertEquals("claude-haiku-5-5", body.getString("model"));
        assertFalse(body.has("fallbacks"));
    }

    @Test
    public void fetchesConnotationSeparately() throws Exception {
        reply = message("{\"tone\":\"negative\",\"summary\":\"Sounds dismissive.\","
                + "\"feels_like\":[\"curt\"],\"caution\":\"\"}", "end_turn");
        JSONObject c = new ClaudeDictionary("k", "claude-opus-5-5", baseUrl).connotation("curt");
        assertEquals("negative", c.getString("tone"));
        JSONObject schema = new JSONObject(lastBody).getJSONObject("output_config")
                .getJSONObject("format").getJSONObject("schema");
        assertEquals(new JSONArray("[\"positive\",\"negative\",\"neutral\",\"mixed\"]").toString(),
                schema.getJSONObject("properties").getJSONObject("tone").getJSONArray("enum").toString());
    }

    @Test
    public void rejectedKeyPointsToSettings() throws Exception {
        status = 401;
        reply = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
        try {
            new ClaudeDictionary("bad", "claude-opus-5-5", baseUrl).define("serene");
            fail("expected LookupException");
        } catch (ClaudeDictionary.LookupException e) {
            assertTrue(e.needsSettings);
            assertFalse(e.retryable);
        }
    }

    @Test
    public void refusalIsReportedNotParsed() throws Exception {
        reply = message("", "refusal");
        try {
            new ClaudeDictionary("k", "claude-opus-5-5", baseUrl).define("x");
            fail("expected LookupException");
        } catch (ClaudeDictionary.LookupException e) {
            assertFalse(e.retryable);
        }
    }

    @Test
    public void everySchemaPropertyIsRequired() throws Exception {
        assertAllRequired(new JSONObject(ClaudeDictionary.entrySchema()));
        assertAllRequired(new JSONObject(ClaudeDictionary.connotationSchema()));
    }

    private static void assertAllRequired(JSONObject schema) throws Exception {
        if (schema.has("properties")) {
            JSONObject props = schema.getJSONObject("properties");
            assertEquals(props.length(), schema.getJSONArray("required").length());
            assertFalse(schema.getBoolean("additionalProperties"));
            for (Iterator<String> it = props.keys(); it.hasNext(); ) {
                assertAllRequired(props.getJSONObject(it.next()));
            }
        }
        if (schema.has("items")) {
            assertAllRequired(schema.getJSONObject("items"));
        }
    }
}
