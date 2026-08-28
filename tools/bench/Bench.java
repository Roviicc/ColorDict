import java.io.File;
import java.util.List;

import io.github.roviicc.colordict.engine.IndexEntry;
import io.github.roviicc.colordict.engine.StarDictDictionary;

/** Measures index load cost, heap footprint, and lookup latency. */
public final class Bench {

    public static void main(String[] args) throws Exception {
        File ifo = new File(args[0]);
        Runtime rt = Runtime.getRuntime();

        settle(rt);
        long beforeHeap = used(rt);
        long t0 = System.nanoTime();
        StarDictDictionary dict = StarDictDictionary.load(ifo);
        long loadMs = (System.nanoTime() - t0) / 1_000_000;
        settle(rt);
        long heapMb = (used(rt) - beforeHeap) / (1024 * 1024);

        int n = dict.wordCount();
        // Headwords sampled across the whole index by the generator.
        String[] probes = java.nio.file.Files.readAllLines(
                new File(ifo.getParentFile(), "probes.txt").toPath())
                .toArray(new String[0]);

        // Warm up, then measure lookup + article read.
        for (int i = 0; i < 200; i++) {
            readOne(dict, probes[i % probes.length]);
        }
        t0 = System.nanoTime();
        int reads = 0;
        for (String probe : probes) {
            reads += readOne(dict, probe);
        }
        long totalUs = (System.nanoTime() - t0) / 1000;

        System.out.printf(
                "%-12s words=%,9d  load=%,6d ms  heap=%,4d MB  "
                        + "lookup+read=%.3f ms avg (%d reads)%n",
                ifo.getParentFile().getName(), n, loadMs, heapMb,
                totalUs / 1000.0 / probes.length, reads);
        dict.close();
    }

    private static int readOne(StarDictDictionary dict, String word) throws Exception {
        List<IndexEntry> hits = dict.lookup(word);
        int count = 0;
        for (IndexEntry e : hits) {
            dict.articleHtml(e);
            count++;
        }
        return count;
    }

    private static long used(Runtime rt) {
        return rt.totalMemory() - rt.freeMemory();
    }

    private static void settle(Runtime rt) throws InterruptedException {
        for (int i = 0; i < 4; i++) {
            System.gc();
            Thread.sleep(60);
        }
    }
}
