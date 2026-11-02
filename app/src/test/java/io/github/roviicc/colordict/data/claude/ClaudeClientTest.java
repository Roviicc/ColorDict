package io.github.roviicc.colordict.data.claude;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


import io.github.roviicc.colordict.data.Entry;
import io.github.roviicc.colordict.data.EntryTest;
import io.github.roviicc.colordict.data.LookupError;

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
public class ClaudeClientTest {

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

    private static final String CONNOTATION =
            "{\"tone\":\"positive\",\"summary\":\"Gentle and admiring.\",\"feels_like\":[\"calm\"],\"caution\":\"\"}";

    @Test
    public void asksForConnotationWithTheUsersKeyAndTheMeanings() throws Exception {
        reply = message(CONNOTATION, "end_turn");
        Entry serene = Entry.fromJson(EntryTest.SERENE);
        JSONObject c = new ClaudeClient("sk-ant-user-key", "claude-opus-5-5", baseUrl)
                .connotation("serene", serene);

        assertEquals("positive", c.getString("tone"));
        assertEquals("sk-ant-user-key", lastKey);

        JSONObject body = new JSONObject(lastBody);
        assertEquals("claude-opus-5-5", body.getString("model"));
        String user = body.getJSONArray("messages").getJSONObject(0).getString("content");
        assertTrue(user.startsWith("<headword>serene</headword>"));
        assertTrue("meanings give Claude context", user.contains("(adjective) Calm, peaceful, and untroubled."));
        JSONObject format = body.getJSONObject("output_config").getJSONObject("format");
        assertEquals("json_schema", format.getString("type"));
        assertEquals(new JSONArray("[\"positive\",\"negative\",\"neutral\",\"mixed\"]").toString(),
                format.getJSONObject("schema").getJSONObject("properties").getJSONObject("tone")
                        .getJSONArray("enum").toString());
        assertEquals("low", body.getJSONObject("output_config").getString("effort"));
        assertEquals("default", body.getString("fallbacks"));
        assertTrue(lastBeta.contains("server-side-fallback-2026-07-01"));
    }

    @Test
    public void looksUpWordsTheDictionaryLacksWithMeaningAndConnotation() throws Exception {
        reply = message("{\"word\":\"rizz\",\"found\":true,\"phonetic\":\"/rɪz/\","
                + "\"senses\":[{\"part_of_speech\":\"noun\",\"definition\":\"Charm or the ability to attract someone.\","
                + "\"examples\":[\"He has serious rizz.\"],\"synonyms\":[\"charisma\"],\"antonyms\":[],\"register\":\"slang\"}],"
                + "\"connotation\":{\"tone\":\"positive\",\"summary\":\"Playful praise.\",\"feels_like\":[\"playful\"],\"caution\":\"\"},"
                + "\"suggestions\":[]}", "end_turn");
        JSONObject json = new ClaudeClient("k", "claude-opus-5-5", baseUrl).define("rizz");

        Entry e = Entry.fromJson(json.toString());
        assertTrue(e.fromClaude);
        assertEquals("rizz", e.word);
        assertEquals("slang", e.senses.get(0).register);
        assertEquals(Entry.Tone.POSITIVE, e.connotation.tone);

        JSONObject schema = new JSONObject(lastBody).getJSONObject("output_config")
                .getJSONObject("format").getJSONObject("schema").getJSONObject("properties");
        assertTrue(schema.has("senses"));
        assertTrue("one request gives meaning and connotation", schema.has("connotation"));
        String user = new JSONObject(lastBody).getJSONArray("messages").getJSONObject(0).getString("content");
        assertEquals("<headword>rizz</headword>", user);
    }

    @Test
    public void haikuRequestsSkipServerFallbacks() throws Exception {
        reply = message(CONNOTATION, "end_turn");
        new ClaudeClient("k", "claude-haiku-5-5", baseUrl).connotation("serene", null);
        JSONObject body = new JSONObject(lastBody);
        assertEquals("claude-haiku-5-5", body.getString("model"));
        assertFalse(body.has("fallbacks"));
    }

    @Test
    public void rejectedKeyPointsToSettings() throws Exception {
        status = 401;
        reply = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
        try {
            new ClaudeClient("bad", "claude-opus-5-5", baseUrl).connotation("serene", null);
            fail("expected LookupError.Exception");
        } catch (LookupError.Exception e) {
            assertEquals(LookupError.Fix.OPEN_SETTINGS, e.error.fix);
        }
    }

    @Test
    public void refusalIsReportedNotParsed() throws Exception {
        reply = message("", "refusal");
        try {
            new ClaudeClient("k", "claude-opus-5-5", baseUrl).connotation("x", null);
            fail("expected LookupError.Exception");
        } catch (LookupError.Exception e) {
            assertEquals(LookupError.Fix.NONE, e.error.fix);
        }
    }

    @Test
    public void everySchemaPropertyIsRequired() throws Exception {
        assertAllRequired(new JSONObject(ClaudeClient.connotationSchema()));
        assertAllRequired(new JSONObject(ClaudeClient.defineSchema()));
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
