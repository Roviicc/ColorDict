package io.github.roviicc.colordict.data;

import android.content.Context;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Words a reader reported as missing a connotation note.
 *
 * <p>This is the demand signal the whole selection strategy now rests on: the
 * charge gate was a proxy for "does this word carry connotation", and it chose
 * taxonomy five times in eight. A person who looked a word up and found nothing
 * is not a proxy. They are the demand.
 *
 * <p>Three properties are deliberate and not negotiable:
 *
 * <ul>
 *   <li><b>Local.</b> The log lives in the app's private files directory. It is
 *       never uploaded, never synced, and there is no network code in this
 *       class. The app's promise is that it works on a plane and tells nobody
 *       what you looked up; a report path that phoned home would break it.
 *   <li><b>Append-only.</b> Reporting appends one line. Nothing rewrites the
 *       file except an explicit deletion by the person who owns it, before they
 *       decide to send it.
 *   <li><b>Sent by hand.</b> Export writes a copy to the cache and hands it to
 *       the share sheet. Leaving the device is always a decision somebody made.
 * </ul>
 *
 * <p>One line of JSON per report, so a partly-written file still parses up to
 * the last complete line, and so ingest is a plain read.
 */
public final class ReportLog {

    /** The entry exists and this sense has no connotation note. */
    public static final String REASON_UNANNOTATED = "unannotated";
    /** Nothing was found for the search term at all. */
    public static final String REASON_NOT_FOUND = "not-found";

    private static final String FILE_NAME = "reports.jsonl";
    private static final String EXPORT_NAME = "colordict-reports.jsonl";

    private final Context context;

    public ReportLog(Context context) {
        this.context = context.getApplicationContext();
    }

    private File file() {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    /** One report. Immutable; {@code line} is its position for deletion. */
    public static final class Report {
        public final String sense;
        public final String lemma;
        public final String gloss;
        public final String reason;
        public final long ts;

        Report(String sense, String lemma, String gloss, String reason, long ts) {
            this.sense = sense;
            this.lemma = lemma;
            this.gloss = gloss;
            this.reason = reason;
            this.ts = ts;
        }
    }

    /**
     * Appends one report. Returns false if it could not be written - the caller
     * should say so rather than pretend the report was taken.
     */
    public boolean add(String sense, String lemma, String gloss, String reason) {
        if (lemma == null || lemma.trim().isEmpty()) {
            return false;
        }
        JSONObject o = new JSONObject();
        try {
            o.put("sense", sense == null ? "" : sense);
            o.put("lemma", lemma.trim());
            o.put("gloss", gloss == null ? "" : gloss);
            o.put("reason", reason == null ? REASON_UNANNOTATED : reason);
            o.put("ts", System.currentTimeMillis());
        } catch (JSONException e) {
            return false;
        }
        // Append mode: an existing log is never truncated or rewritten here.
        try (Writer w = new OutputStreamWriter(
                new FileOutputStream(file(), true), StandardCharsets.UTF_8)) {
            w.write(o.toString());
            w.write("\n");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Every report, newest last. A malformed line is skipped, never fatal. */
    public List<Report> all() {
        List<Report> out = new ArrayList<>();
        File f = file();
        if (!f.exists()) {
            return out;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new java.io.FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    JSONObject o = new JSONObject(line);
                    out.add(new Report(o.optString("sense"), o.optString("lemma"),
                            o.optString("gloss"),
                            o.optString("reason", REASON_UNANNOTATED),
                            o.optLong("ts")));
                } catch (JSONException ignored) {
                    // A truncated last line is expected after a hard kill.
                }
            }
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    public int count() {
        return all().size();
    }

    /** True when this exact sense has already been reported. */
    public boolean contains(String sense, String lemma) {
        for (Report r : all()) {
            if (sense != null && !sense.isEmpty() ? sense.equals(r.sense)
                    : r.lemma.equalsIgnoreCase(lemma)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes one report, by index into {@link #all()}. This is the only thing
     * that rewrites the file, and it exists so somebody can take a word back out
     * before they send the log to anyone.
     */
    public boolean removeAt(int index) {
        List<Report> reports = all();
        if (index < 0 || index >= reports.size()) {
            return false;
        }
        reports.remove(index);
        return rewrite(reports);
    }

    public boolean clear() {
        return rewrite(Collections.<Report>emptyList());
    }

    private boolean rewrite(List<Report> reports) {
        File tmp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        try (Writer w = new OutputStreamWriter(
                new FileOutputStream(tmp, false), StandardCharsets.UTF_8)) {
            for (Report r : reports) {
                JSONObject o = new JSONObject();
                o.put("sense", r.sense);
                o.put("lemma", r.lemma);
                o.put("gloss", r.gloss);
                o.put("reason", r.reason);
                o.put("ts", r.ts);
                w.write(o.toString());
                w.write("\n");
            }
        } catch (IOException | JSONException e) {
            return false;
        }
        File dest = file();
        if (dest.exists() && !dest.delete()) {
            return false;
        }
        return tmp.renameTo(dest);
    }

    /**
     * Copies the log into the shareable cache directory and returns a content
     * Uri for it, or null when there is nothing to send. Nothing here sends
     * anything: it produces a file the person can choose to hand to something
     * else.
     */
    public Uri exportForSharing() {
        List<Report> reports = all();
        if (reports.isEmpty()) {
            return null;
        }
        File dir = new File(context.getCacheDir(), "reports");
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        File out = new File(dir, EXPORT_NAME);
        try (Writer w = new OutputStreamWriter(
                new FileOutputStream(out, false), StandardCharsets.UTF_8)) {
            for (Report r : reports) {
                JSONObject o = new JSONObject();
                o.put("sense", r.sense);
                o.put("lemma", r.lemma);
                o.put("gloss", r.gloss);
                o.put("reason", r.reason);
                o.put("ts", r.ts);
                w.write(o.toString());
                w.write("\n");
            }
        } catch (IOException | JSONException e) {
            return null;
        }
        return androidx.core.content.FileProvider.getUriForFile(
                context, context.getPackageName() + ".reports", out);
    }
}
