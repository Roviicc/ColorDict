package io.github.roviicc.colordict.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;

import io.github.roviicc.colordict.engine.DefinitionRenderer;

/**
 * A desktop harness for the ColorDict engine: the same StarDict parsing,
 * lookup and color-coded rendering the Android app uses, wrapped in a Swing
 * window so it can be tried on a PC with nothing but a JDK installed.
 *
 * <pre>
 *   ./run-desktop.sh                     open the window (sample glossary)
 *   ./run-desktop.sh --dict ~/dicts      also load dictionaries from a folder
 *   ./run-desktop.sh --lookup serene     print a definition to the terminal
 *   ./run-desktop.sh --list              list the dictionaries that loaded
 * </pre>
 */
public final class DesktopApp {

    private static final int MAX_SUGGESTIONS = 60;
    private static final String SAMPLE_DIR = "app/src/main/assets/dicts";

    private final DictionarySet dictionaries;
    private JFrame frame;
    private JTextField searchField;
    private JList<DictionarySet.Suggestion> suggestionList;
    private DefaultListModel<DictionarySet.Suggestion> suggestionModel;
    private JEditorPane definitionPane;
    private JPanel dictionaryPanel;

    private DesktopApp(DictionarySet dictionaries) {
        this.dictionaries = dictionaries;
    }

    // ------------------------------------------------------------ entry point

    public static void main(String[] args) throws Exception {
        List<File> folders = new ArrayList<>();
        String lookup = null;
        String screenshot = null;
        boolean list = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dict":
                case "--dicts":
                    folders.add(new File(requireValue(args, ++i, "--dict")));
                    break;
                case "--lookup":
                    lookup = requireValue(args, ++i, "--lookup");
                    break;
                case "--screenshot":
                    screenshot = requireValue(args, ++i, "--screenshot");
                    break;
                case "--list":
                    list = true;
                    break;
                case "--help":
                case "-h":
                    printUsage(System.out);
                    return;
                default:
                    System.err.println("unknown option: " + args[i]);
                    printUsage(System.err);
                    System.exit(2);
            }
        }

        if (folders.isEmpty()) {
            File sample = defaultDictionaryFolder();
            if (sample != null) {
                folders.add(sample);
            }
        }

        DictionarySet set = new DictionarySet();
        for (File folder : folders) {
            if (!folder.isDirectory()) {
                System.err.println("not a folder: " + folder);
                continue;
            }
            set.addFolder(folder);
        }
        set.sortByName();
        for (String failure : set.failures()) {
            System.err.println("skipped " + failure);
        }

        if (list) {
            printDictionaries(set, System.out);
            return;
        }
        if (lookup != null && screenshot == null) {
            if (set.dictionaries().isEmpty()) {
                System.err.println("no dictionaries loaded; pass --dict FOLDER");
                System.exit(1);
            }
            System.out.print(DictionarySet.toPlainText(set.define(lookup)));
            return;
        }

        DesktopApp app = new DesktopApp(set);
        final String initialWord = lookup;
        SwingUtilities.invokeAndWait(() -> {
            app.buildUi();
            if (initialWord != null) {
                app.searchField.setText(initialWord);
                app.define(initialWord);
            }
        });
        if (screenshot != null) {
            app.writeScreenshot(new File(screenshot));
            System.exit(0);
        }
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            System.err.println(option + " needs a value");
            System.exit(2);
        }
        return args[index];
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage: run-desktop.sh [--dict FOLDER]... [--lookup WORD] [--list]");
        out.println();
        out.println("  --dict FOLDER   load every StarDict dictionary under FOLDER");
        out.println("                  (repeatable; defaults to the bundled sample glossary)");
        out.println("  --lookup WORD   print the definition to the terminal and exit");
        out.println("  --list          list the dictionaries that loaded and exit");
        out.println();
        out.println("With no --lookup or --list, the graphical window opens.");
    }

    /** The repo's bundled sample glossary, or the user's own folder if present. */
    private static File defaultDictionaryFolder() {
        File repoSample = new File(SAMPLE_DIR);
        if (repoSample.isDirectory()) {
            return repoSample;
        }
        File home = new File(System.getProperty("user.home"), ".colordict/dictionaries");
        return home.isDirectory() ? home : null;
    }

    private static void printDictionaries(DictionarySet set, PrintStream out) {
        if (set.dictionaries().isEmpty()) {
            out.println("No dictionaries loaded.");
            return;
        }
        for (DictionarySet.Loaded d : set.dictionaries()) {
            out.printf("%-40s %7d words  %s%n", d.name(), d.dictionary.wordCount(),
                    DefinitionRenderer.hexColor(d.color));
        }
    }

    // ------------------------------------------------------------ ui

    private void buildUi() {
        frame = new JFrame("ColorDict — desktop harness");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 680);
        frame.setLocationRelativeTo(null);

        searchField = new JTextField();
        searchField.setToolTipText("Type to search every loaded dictionary");
        searchField.setBorder(BorderFactory.createCompoundBorder(
                searchField.getBorder(), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshSuggestions();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshSuggestions();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshSuggestions();
            }
        });
        searchField.addActionListener(e -> define(searchField.getText().trim()));

        suggestionModel = new DefaultListModel<>();
        suggestionList = new JList<>(suggestionModel);
        suggestionList.setCellRenderer(new SuggestionRenderer());
        suggestionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                DictionarySet.Suggestion s = suggestionList.getSelectedValue();
                if (s != null) {
                    define(s.word);
                }
            }
        });

        definitionPane = new JEditorPane();
        definitionPane.setContentType("text/html");
        definitionPane.setEditable(false);
        definitionPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String href = e.getDescription();
                if (href != null && href.startsWith("bword://")) {
                    String word = URLDecoder.decode(
                            href.substring("bword://".length()), StandardCharsets.UTF_8);
                    searchField.setText(word);
                    define(word);
                }
            }
        });

        dictionaryPanel = new JPanel();
        dictionaryPanel.setLayout(new BoxLayout(dictionaryPanel, BoxLayout.Y_AXIS));
        dictionaryPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        rebuildDictionaryPanel();

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 6));
        left.add(searchField, BorderLayout.NORTH);
        left.add(new JScrollPane(suggestionList), BorderLayout.CENTER);

        JScrollPane dictionaryScroll = new JScrollPane(dictionaryPanel);
        dictionaryScroll.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JSplitPane words = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                left, dictionaryScroll);
        words.setDividerLocation(430);
        words.setResizeWeight(0.75);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                words, new JScrollPane(definitionPane));
        split.setDividerLocation(320);

        frame.setJMenuBar(buildMenuBar());
        frame.add(split, BorderLayout.CENTER);
        frame.setVisible(true);

        showWelcome();
        refreshSuggestions();
        searchField.requestFocusInWindow();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("Dictionaries");
        JMenuItem open = new JMenuItem("Add folder…");
        open.addActionListener(e -> chooseFolder());
        JMenuItem quit = new JMenuItem("Quit");
        quit.addActionListener(e -> System.exit(0));
        file.add(open);
        file.addSeparator();
        file.add(quit);
        bar.add(file);
        return bar;
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose a folder containing StarDict dictionaries");
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            int added = dictionaries.addFolder(chooser.getSelectedFile());
            dictionaries.sortByName();
            rebuildDictionaryPanel();
            refreshSuggestions();
            if (added == 0) {
                definitionPane.setText("<html><body style=\"font-family:sans-serif\">"
                        + "<p>No StarDict dictionaries (.ifo files) found in that folder.</p>"
                        + "</body></html>");
            }
        }
    }

    private void rebuildDictionaryPanel() {
        dictionaryPanel.removeAll();
        JLabel heading = new JLabel("Loaded dictionaries");
        heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        dictionaryPanel.add(heading);
        if (dictionaries.dictionaries().isEmpty()) {
            dictionaryPanel.add(new JLabel("<html><i>none — use Dictionaries → Add folder…</i></html>"));
        }
        for (DictionarySet.Loaded d : dictionaries.dictionaries()) {
            JCheckBox box = new JCheckBox(String.format("<html><font color='%s'>●</font> "
                            + "%s <font color='gray'>(%d words)</font></html>",
                    DefinitionRenderer.hexColor(d.color), escapeHtml(d.name()),
                    d.dictionary.wordCount()), d.enabled);
            box.addActionListener(e -> {
                d.enabled = box.isSelected();
                refreshSuggestions();
                String word = searchField.getText().trim();
                if (!word.isEmpty()) {
                    define(word);
                }
            });
            dictionaryPanel.add(box);
        }
        dictionaryPanel.revalidate();
        dictionaryPanel.repaint();
    }

    private void showWelcome() {
        int words = 0;
        for (DictionarySet.Loaded d : dictionaries.dictionaries()) {
            words += d.dictionary.wordCount();
        }
        definitionPane.setText("<html><body style=\"font-family:sans-serif;margin:14px\">"
                + "<h2>ColorDict desktop harness</h2>"
                + "<p>" + dictionaries.dictionaries().size() + " dictionary/dictionaries loaded, "
                + words + " words total.</p>"
                + "<p>Type in the search box to see suggestions, then pick one to view the "
                + "color-coded definitions. Every dictionary that knows the word contributes "
                + "its own card.</p>"
                + "<p>Add your own StarDict dictionaries with "
                + "<b>Dictionaries → Add folder…</b></p></body></html>");
        definitionPane.setCaretPosition(0);
    }

    private void refreshSuggestions() {
        String prefix = searchField.getText().trim();
        suggestionModel.clear();
        if (prefix.isEmpty()) {
            for (DictionarySet.Suggestion s : dictionaries.suggest("", MAX_SUGGESTIONS)) {
                suggestionModel.addElement(s);
            }
            return;
        }
        for (DictionarySet.Suggestion s : dictionaries.suggest(prefix, MAX_SUGGESTIONS)) {
            suggestionModel.addElement(s);
        }
    }

    private void define(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        DictionarySet.Result result = dictionaries.define(word);
        DefinitionRenderer.Labels labels = new DefinitionRenderer.Labels(
                "No results for “" + word + "”.", "Similar words:");
        definitionPane.setText(DefinitionRenderer.page(
                swingCss(), result.sections, result.similar, labels));
        definitionPane.setCaretPosition(0);
    }

    /**
     * A stylesheet trimmed to what Swing's HTML renderer understands
     * (no rounded corners or flexible box models, but colors and spacing work).
     */
    private static String swingCss() {
        return "body{font-family:sans-serif;margin:10px;color:#1F2328}"
                + "a{color:#1565C0}"
                + ".card{margin:0 0 14px 0;padding:6px 10px;background:#FFFFFF}"
                + ".dictname{font-size:small;font-weight:bold}"
                + ".hw{font-size:large;font-weight:bold;margin:4px 0}"
                + ".phon{color:#6A6F75}"
                + ".xex{color:#2E7D32}"
                + ".xabr{color:#6A6F75}"
                + ".xk{font-weight:bold}"
                + ".res{color:#6A6F75;font-size:small}"
                + ".note{color:#6A6F75;margin:12px 0}"
                + ".simword{margin-right:8px}"
                + ".fld{margin:2px 0}"
                + ".flk{font-weight:bold}"
                + ".pos{color:#6A6F75;font-style:italic}"
                + ".cn{font-size:small;font-weight:bold}"
                + ".cnp{color:#1B5E20}"
                + ".cnn{color:#B71C1C}"
                + ".ul{color:#6A6F75;font-size:small}"
                + ".sn{font-weight:bold}"
                + ".cx{color:#6A6F75}"
                + ".rl{font-size:small}"
                + ".rlk{color:#6A6F75;font-weight:bold}"
                + ".wfm{color:#6A6F75;font-style:italic}"
                + ".tier{color:#9E9E9E;font-size:small}"
                // Swing's HTML renderer has no <details>; the summary shows as
                // a plain line and the extra senses stay visible below it.
                + "summary{color:#6A6F75;font-size:small}";
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Renders the current window to a PNG — used to verify the UI headlessly. */
    private void writeScreenshot(File target) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            frame.paint(g);
            g.dispose();
            try {
                ImageIO.write(image, "png", target);
                System.out.println("screenshot written to " + target);
            } catch (IOException e) {
                System.err.println("screenshot failed: " + e.getMessage());
            }
        });
    }

    /** Draws a word plus one colored dot per dictionary that contains it. */
    private static final class SuggestionRenderer
            implements ListCellRenderer<DictionarySet.Suggestion> {

        private final JPanel panel = new JPanel(new BorderLayout(8, 0));
        private final JLabel word = new JLabel();
        private final JLabel dots = new JLabel();

        SuggestionRenderer() {
            panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            panel.add(word, BorderLayout.CENTER);
            panel.add(dots, BorderLayout.EAST);
            panel.setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends DictionarySet.Suggestion> list,
                DictionarySet.Suggestion value, int index, boolean selected, boolean focused) {
            word.setText(value.word);
            StringBuilder sb = new StringBuilder("<html>");
            for (int color : value.colors) {
                sb.append("<font color='").append(DefinitionRenderer.hexColor(color))
                        .append("'>●</font>");
            }
            dots.setText(sb.append("</html>").toString());
            Color bg = selected ? list.getSelectionBackground() : list.getBackground();
            panel.setBackground(bg);
            word.setForeground(selected
                    ? list.getSelectionForeground() : list.getForeground());
            panel.setPreferredSize(new Dimension(0, 26));
            return panel;
        }
    }
}
