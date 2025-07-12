import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class WortfluessigkeitGenerator {
    private static final Random RANDOM = new Random();

    public static void execute(String filename) {
        String todayAsString = new SimpleDateFormat("ddMMyy").format(new Date());
        List<String> words = readWordsFromFile(filename);
        if (words.isEmpty()) {
            MedatoninDB.debugLog("Wortfluessigkeit", "No valid words found in the file.");
            return;
        }

        Collections.shuffle(words);

        try (Writer questionWriter = new OutputStreamWriter(new FileOutputStream("WF_" + todayAsString + ".txt"), java.nio.charset.StandardCharsets.UTF_8);
             Writer answerWriter = new OutputStreamWriter(new FileOutputStream("WF_lsg_" + todayAsString + ".txt"), java.nio.charset.StandardCharsets.UTF_8)) {

            for (int i = 0; i < words.size(); i++) {
                String word = words.get(i);
                // Debug: Log word before scrambling
                if (i < 5) {
                    MedatoninDB.debugLog("Pipeline", "Word before scramble (index " + i + "): " + word);
                }
                String scrambledWord = scrambleString(word);
                char[] answerChoices = generateAnswerChoices(word);
                // Debug: Log scrambled word and answer choices
                if (i < 5) {
                    MedatoninDB.debugLog("Pipeline", "Scrambled word (index " + i + "): " + scrambledWord);
                    MedatoninDB.debugLog("Pipeline", "Answer choices (index " + i + "): " + Arrays.toString(answerChoices));
                }

                // Write to question file
                questionWriter.write((i + 1) + ".  " + scrambledWord + "\n\n");
                for (int j = 0; j < answerChoices.length - 1; j++) {
                    questionWriter.write("\t" + (char) ('A' + j) + ". Anfangsbuchstabe: " + answerChoices[j] + "\n");
                }
                questionWriter.write("\tE. Keine Antwort ist richtig.\n\n");

                // Write to answer file
                // Debug: Log word before DB/output file write
                if (i < 5) {
                    MedatoninDB.debugLog("Pipeline", "Word before output (index " + i + "): " + word);
                }
                char correctOption = (char) ('A' + findCorrectOptionIndex(answerChoices, word.charAt(0)));
                answerWriter.write((i + 1) + ".  " + correctOption + "  |  " + word + "\n");
            }

            MedatoninDB.debugLog("Wortfluessigkeit", "Questions and solutions successfully written.");
        } catch (IOException e) {
            System.err.println("An error occurred while writing files.");
            e.printStackTrace();
        }
    }

    private static List<String> readWordsFromFile(String filename) {
        List<String> words = new ArrayList<>();
        try (Scanner scanner = new Scanner(new File(filename), java.nio.charset.StandardCharsets.UTF_8)) {
            int lineNum = 0;
            while (scanner.hasNextLine()) {
                String originalWord = scanner.nextLine().trim();
                // Debug: Log raw word as read from file
                if (lineNum < 5) {
                    MedatoninDB.debugLog("FileRead", "Raw word (line " + (lineNum+1) + "): " + originalWord);
                }
                String word = originalWord.toUpperCase(Locale.GERMAN);
                // Debug: Log word after uppercase conversion
                if (lineNum < 5) {
                    MedatoninDB.debugLog("FileRead", "Uppercase word (line " + (lineNum+1) + "): " + word);
                }
                if (isValidWord(word)) {
                    words.add(word);
                    // Log first 5 words for encoding check
                    if (lineNum < 5) {
                        MedatoninDB.debugLog("FileRead", "Accepted word (line " + (lineNum+1) + "): " + word);
                    }
                }
                lineNum++;
            }
        } catch (IOException e) {
            MedatoninDB.debugLog("FileRead", "Error reading file: " + filename + " - " + e.getMessage());
        }

        return words;
    }

    private static boolean isValidWord(String word) {
        return word.length() > 4 && word.length() < 12
                && word.chars().noneMatch(Character::isDigit)
                // Allow umlauts and ß
                && hasAtLeastFiveUniqueLetters(word);
    }

    private static boolean hasAtLeastFiveUniqueLetters(String word) {
        return word.chars().distinct().count() >= 5;
    }

    private static String scrambleString(String input) {
        char[] chars = input.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            // Swap letters
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }

    private static char[] generateAnswerChoices(String word) {
        char[] answerChoices = new char[5];
        Arrays.fill(answerChoices, '\0');
        int correctIndex = RANDOM.nextInt(5);
        answerChoices[correctIndex] = word.charAt(0);

        int index = 0;
        for (char c : word.toCharArray()) {
            if (!containsChar(answerChoices, c) && index < answerChoices.length - 1) {
                if (answerChoices[index] == '\0') {
                    answerChoices[index] = c;
                    index++;
                }
            }
        }

        return answerChoices;
    }

    private static boolean containsChar(char[] array, char c) {
        for (char x : array) {
            if (x == c) {
                return true;
            }
        }
        return false;
    }

    private static int findCorrectOptionIndex(char[] answerChoices, char correctChar) {
        for (int i = 0; i < answerChoices.length; i++) {
            if (answerChoices[i] == correctChar) {
                return i;
            }
        }
        return -1; // Should not happen
    }
}
