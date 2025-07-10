import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class WortfluessigkeitGenerator {
    private static final Random RANDOM = new Random();

    public static void execute(String filename) {
        String todayAsString = new SimpleDateFormat("ddMMyy").format(new Date());

        List<String> words = readWordsFromFile(filename);
        if (words.isEmpty()) {
            System.out.println("No valid words found in the file.");
            return;
        }

        Collections.shuffle(words);

        try (Writer questionWriter = new OutputStreamWriter(new FileOutputStream("WF_" + todayAsString + ".txt"), java.nio.charset.StandardCharsets.UTF_8);
             Writer answerWriter = new OutputStreamWriter(new FileOutputStream("WF_lsg_" + todayAsString + ".txt"), java.nio.charset.StandardCharsets.UTF_8)) {

            for (int i = 0; i < words.size(); i++) {
                String word = words.get(i);
                String scrambledWord = scrambleString(word);
                char[] answerChoices = generateAnswerChoices(word);

                // Write to question file
                questionWriter.write((i + 1) + ".  " + scrambledWord + "\n\n");
                for (int j = 0; j < answerChoices.length - 1; j++) {
                    questionWriter.write("\t" + (char) ('A' + j) + ". Anfangsbuchstabe: " + answerChoices[j] + "\n");
                }
                questionWriter.write("\tE. Keine Antwort ist richtig.\n\n");

                // Write to answer file
                char correctOption = (char) ('A' + findCorrectOptionIndex(answerChoices, word.charAt(0)));
                answerWriter.write((i + 1) + ".  " + correctOption + "  |  " + word + "\n");
            }

            System.out.println("Questions and solutions successfully written.");
        } catch (IOException e) {
            System.err.println("An error occurred while writing files.");
            e.printStackTrace();
        }
    }

    private static List<String> readWordsFromFile(String filename) {
        List<String> words = new ArrayList<>();

        try (Scanner scanner = new Scanner(new File(filename), "UTF-8")) {
            while (scanner.hasNextLine()) {
                String word = scanner.nextLine().trim().toUpperCase();
                if (isValidWord(word)) {
                    words.add(word);
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + filename);
            e.printStackTrace();
        }

        return words;
    }

    private static boolean isValidWord(String word) {
        return word.length() > 4 && word.length() < 12
                && word.chars().noneMatch(Character::isDigit)
                && !word.matches(".*[ÄÖÜß].*")
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
