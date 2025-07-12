import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.Random;
import java.util.Set;

/**
 * Generator for the "Wortflüssigkeit" subtest. It creates questions
 * consisting of scrambled words and multiple choice options. The class follows
 * the same DAO based architecture as the other generators.
 */
public class WortfluessigkeitGenerator {
    private static final String WORDLIST_PATH = "src/main/resources/wortliste.txt";
    private static final char[] FILLER = {'S', 'T', 'N', 'R', 'L'};

    private final Connection conn;
    private final QuestionDAO questionDAO;
    private final OptionDAO optionDAO;
    private final String category;
    private final String subcategory;
    private final Integer simulationId;
    private final Random random = new Random();

    public WortfluessigkeitGenerator(Connection conn,
                                     String category,
                                     String subcategory,
                                     Integer simulationId) {
        this.conn = conn;
        this.category = category;
        this.subcategory = subcategory;
        this.simulationId = simulationId;
        this.questionDAO = new QuestionDAO(conn);
        this.optionDAO = new OptionDAO(conn);
    }

    /**
     * Generates {@code numQuestions} items and persists them to the database.
     */
    public void execute(int numQuestions) throws IOException, SQLException {
        List<String> words = readWordList(WORDLIST_PATH);
        Collections.shuffle(words, random);

        int subId = questionDAO.getSubcategoryId(category, subcategory);
        int nextNr = questionDAO.getNextQuestionNumber(simulationId, subId);

        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (int i = 0; i < numQuestions && i < words.size(); i++) {
                String word = words.get(i).toUpperCase(Locale.GERMAN);
                String scrambled = scramble(word);
                char[] optionLetters = new char[4];
                int correctIndex = fillOptions(word, optionLetters);
                String questionText = toSpacedString(scrambled);

                if (i < 5) {
                    MedatoninDB.debugLog("Wortfluessigkeit",
                            "ID " + nextNr + " | Word = \"" + word +
                                    "\" | Scramble = \"" + questionText +
                                    "\" | Correct = \"" + optionLetters[correctIndex] +
                                    "\" | Distraktoren = \"" + distractorString(optionLetters, correctIndex) + "\"");
                }

                int qId = questionDAO.insertQuestionWithShape(
                        category,
                        subcategory,
                        nextNr++,
                        questionText,
                        simulationId,
                        word,
                        "WORD",
                        null,
                        null);

                for (int j = 0; j < 5; j++) {
                    String label = (j == 4) ? "E" : String.valueOf((char) ('A' + j));
                    String text = (j == 4) ? "Keine Antwort ist richtig." :
                            "Anfangsbuchstabe: " + optionLetters[j];
                    boolean correct = j == correctIndex;
                    optionDAO.insertOption(qId, label, text, correct);
                }
            }
            conn.commit();
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    /**
     * Reads all valid words from the given list file.
     *
     * <p>The word list distributed with the project is encoded in
     * ISO-8859-1. When reading it using UTF-8 the umlaut bytes become
     * replacement characters, which bypasses the {@link #INVALID_CHARS}
     * check. To ensure words containing umlauts are filtered out, we
     * read the file using the correct encoding.</p>
     *
     * <p>Only words with a length between 7 and 9 characters are
     * considered.</p>
     */
    List<String> readWordList(String path) throws IOException {
        List<String> raw = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new java.io.FileInputStream(path), StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = br.readLine()) != null) {
                raw.add(line.trim());
            }
        }

        Set<String> rawUpper = new HashSet<>();
        for (String r : raw) {
            rawUpper.add(r.toUpperCase(Locale.GERMAN));
        }

        List<String> out = new ArrayList<>();
        for (String w : raw) {
            String upper = w.toUpperCase(Locale.GERMAN);
            int len = upper.codePointCount(0, upper.length());
            if (len >= 7 && len <= 9 && isValidWord(upper, rawUpper)) {
                out.add(upper);
            }
        }
        return out;
    }

    private static final Pattern INVALID_CHARS = Pattern.compile(".*[ÄÖÜäöüß].*");
    private static final Pattern DIMINUTIVE = Pattern.compile(".*(CHEN|LEIN)$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> PROPER_NAMES = Set.of("ALDI", "DM", "BUNDESLIGA", "BUNDESREPUBLIK");

    private static boolean isValidWord(String word, Set<String> words) {
        if (INVALID_CHARS.matcher(word).matches()) {
            return false;
        }
        if (DIMINUTIVE.matcher(word).matches()) {
            return false;
        }
        if (PROPER_NAMES.contains(word)) {
            return false;
        }
        // simple plural detection: if removing a typical plural ending yields another word
        if (word.endsWith("EN") && words.contains(word.substring(0, word.length() - 2))) {
            return false;
        }
        if (word.endsWith("ER") && words.contains(word.substring(0, word.length() - 2))) {
            return false;
        }
        if (word.endsWith("E") && words.contains(word.substring(0, word.length() - 1))) {
            return false;
        }
        if (word.endsWith("S") && words.contains(word.substring(0, word.length() - 1))) {
            return false;
        }
        if (word.endsWith("N") && words.contains(word.substring(0, word.length() - 1))) {
            return false;
        }
        return true;
    }

    /**
     * Scrambles the characters of {@code word} using Fisher–Yates.
     * The result is guaranteed not to equal the input.
     */
    String scramble(String word) {
        char[] arr = word.toCharArray();
        for (int attempt = 0; attempt < 20; attempt++) {
            for (int i = arr.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                char tmp = arr[i];
                arr[i] = arr[j];
                arr[j] = tmp;
            }
            String result = new String(arr);
            if (!result.equals(word)) {
                return result;
            }
        }
        return new StringBuilder(word).reverse().toString();
    }

    /**
     * Checks whether the two strings consist of the same multiset of
     * code points.
     */
    boolean isPermutation(String a, String b) {
        if (a.codePointCount(0, a.length()) != b.codePointCount(0, b.length())) {
            return false;
        }
        int[] counts = new int[Character.MAX_CODE_POINT + 1];
        a.codePoints().forEach(cp -> counts[cp]++);
        b.codePoints().forEach(cp -> counts[cp]--);
        for (int c : counts) {
            if (c != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Populates {@code target} with four option letters and returns the index of
     * the correct one.
     */
    private int fillOptions(String word, char[] target) {
        char correct = Character.toUpperCase(word.charAt(0));
        Set<Character> unique = new HashSet<>();
        for (char c : word.toCharArray()) {
            char u = Character.toUpperCase(c);
            if (u != correct) {
                unique.add(u);
            }
        }
        List<Character> distractors = new ArrayList<>(unique);
        Collections.shuffle(distractors, random);
        List<Character> letters = new ArrayList<>();
        letters.add(correct);
        for (int i = 0; i < distractors.size() && letters.size() < 4; i++) {
            letters.add(distractors.get(i));
        }
        for (char filler : FILLER) {
            if (letters.size() >= 4) break;
            if (!letters.contains(filler)) {
                letters.add(filler);
            }
        }
        Collections.shuffle(letters, random);
        for (int i = 0; i < 4; i++) {
            target[i] = letters.get(i);
        }
        return letters.indexOf(correct);
    }

    private static String toSpacedString(String word) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(word.charAt(i));
        }
        return sb.toString();
    }

    private static String distractorString(char[] options, int correctIndex) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < 4; i++) {
            if (i == correctIndex) continue;
            if (!first) sb.append(", ");
            first = false;
            sb.append(options[i]);
        }
        return sb.toString();
    }
}
