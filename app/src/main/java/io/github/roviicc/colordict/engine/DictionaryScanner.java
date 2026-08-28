package io.github.roviicc.colordict.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Finds StarDict dictionaries (.ifo files) under a directory tree. */
public final class DictionaryScanner {

    private static final int MAX_DEPTH = 8;

    private DictionaryScanner() {
    }

    public static List<File> findIfoFiles(File root) {
        List<File> out = new ArrayList<>();
        if (root != null && root.isDirectory()) {
            walk(root, 0, out);
        }
        out.sort(Comparator.comparing(File::getPath));
        return out;
    }

    private static void walk(File dir, int depth, List<File> out) {
        if (depth > MAX_DEPTH) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            String name = f.getName();
            if (name.startsWith(".")) {
                continue;
            }
            if (f.isDirectory()) {
                walk(f, depth + 1, out);
            } else if (name.toLowerCase().endsWith(".ifo")) {
                out.add(f);
            }
        }
    }
}
