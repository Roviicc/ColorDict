package io.github.roviicc.colordict.engine;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

/** Locates the committed StarDict fixture files on the test classpath. */
final class TestFixtures {

    private TestFixtures() {
    }

    static File file(String relativePath) {
        URL url = TestFixtures.class.getResource("/fixtures/" + relativePath);
        if (url == null) {
            throw new IllegalStateException("missing test fixture: " + relativePath
                    + " (run tools/gen_fixtures.py)");
        }
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
