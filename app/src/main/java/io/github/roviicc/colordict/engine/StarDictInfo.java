package io.github.roviicc.colordict.engine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Metadata parsed from a StarDict .ifo file. The file is plain UTF-8 text:
 * a magic first line followed by {@code key=value} pairs.
 */
public final class StarDictInfo {

    public static final String MAGIC = "StarDict's dict ifo file";

    public String version = "";
    public String bookname = "";
    public int wordcount;
    public int synwordcount;
    public long idxfilesize;
    public int idxoffsetbits = 32;
    public String sametypesequence = "";
    public String author = "";
    public String email = "";
    public String website = "";
    public String description = "";
    public String date = "";

    private StarDictInfo() {
    }

    public static StarDictInfo parse(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return parse(in, file.getName());
        }
    }

    public static StarDictInfo parse(InputStream in, String name) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
            if (buf.size() > 1 << 20) {
                throw new IOException(name + ": .ifo file implausibly large");
            }
        }
        String text = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }

        StarDictInfo info = new StarDictInfo();
        boolean sawMagic = false;
        for (String rawLine : text.split("\n", -1)) {
            String line = rawLine.endsWith("\r")
                    ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            if (line.trim().isEmpty()) {
                continue;
            }
            if (!sawMagic) {
                if (!line.trim().equals(MAGIC)) {
                    throw new IOException(name + ": not a StarDict .ifo file");
                }
                sawMagic = true;
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            switch (key) {
                case "version": info.version = value; break;
                case "bookname": info.bookname = value; break;
                case "wordcount": info.wordcount = parseInt(value, 0); break;
                case "synwordcount": info.synwordcount = parseInt(value, 0); break;
                case "idxfilesize": info.idxfilesize = parseLong(value, 0); break;
                case "idxoffsetbits": info.idxoffsetbits = parseInt(value, 32); break;
                case "sametypesequence": info.sametypesequence = value; break;
                case "author": info.author = value; break;
                case "email": info.email = value; break;
                case "website": info.website = value; break;
                case "description": info.description = value; break;
                case "date": info.date = value; break;
                default: break; // unknown keys are ignored
            }
        }
        if (!sawMagic) {
            throw new IOException(name + ": empty .ifo file");
        }
        if (info.idxoffsetbits != 32 && info.idxoffsetbits != 64) {
            throw new IOException(name + ": unsupported idxoffsetbits=" + info.idxoffsetbits);
        }
        if (info.bookname.isEmpty()) {
            info.bookname = stripExtension(name);
        }
        return info;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String s, long fallback) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
