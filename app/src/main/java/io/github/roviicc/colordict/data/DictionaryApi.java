package io.github.roviicc.colordict.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/** Blocking client for the dictionary server's GET /api/define endpoint. */
public final class DictionaryApi {

    /** A failed lookup, with a message that is safe to show to the user. */
    public static final class LookupException extends Exception {
        public final boolean retryable;

        public LookupException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }
    }

    private final String baseUrl;
    private final String appToken;

    public DictionaryApi(String baseUrl, String appToken) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.appToken = appToken == null ? "" : appToken;
    }

    /** Builds the request URL; package-private for tests. */
    static String requestUrl(String baseUrl, String word) {
        try {
            return baseUrl + "/api/define?word=" + URLEncoder.encode(word, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    public Entry define(String word) throws LookupException {
        if (baseUrl.isEmpty()) {
            throw new LookupException(
                    "No dictionary server is set. Add its address in Settings.", false);
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(requestUrl(baseUrl, word)).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(60_000);
            conn.setRequestProperty("Accept", "application/json");
            if (!appToken.isEmpty()) {
                conn.setRequestProperty("X-App-Token", appToken);
            }
            int status = conn.getResponseCode();
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = in == null ? "" : readAll(in);
            if (status == 200) {
                return Entry.fromJson(body);
            }
            throw new LookupException(errorMessage(status, body), status == 429 || status >= 500);
        } catch (UnknownHostException e) {
            throw new LookupException("You're offline. Connect to the internet and try again.", true);
        } catch (SocketTimeoutException e) {
            throw new LookupException("The dictionary took too long to answer.", true);
        } catch (JSONException e) {
            throw new LookupException("The dictionary sent an answer the app can't read.", true);
        } catch (IOException e) {
            throw new LookupException("Couldn't reach the dictionary server.", true);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** Uses the server's own message when it sent one. Package-private for tests. */
    static String errorMessage(int status, String body) {
        try {
            JSONObject err = new JSONObject(body).optJSONObject("error");
            if (err != null && !err.optString("message").isEmpty()) {
                return err.optString("message");
            }
        } catch (JSONException ignored) {
            // fall through to a generic message
        }
        if (status == 401) {
            return "The dictionary server rejected this app. Check the app token.";
        }
        if (status == 404) {
            return "The dictionary server address looks wrong. Check it in Settings.";
        }
        return "The dictionary server had a problem (" + status + ").";
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
