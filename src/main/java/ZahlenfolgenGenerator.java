import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
//import java.util.Scanner;

public class ZahlenfolgenGenerator {

    static Random r = new Random();
    private Connection conn;
    private static QuestionDAO questionDAO;
    private static OptionDAO optionDAO;
    private static String category; // e.g., "KFF"
    private static String subcategory; // e.g., "Zahlenfolgen"
    private static Integer simulationId; // Current simulation ID

    public ZahlenfolgenGenerator(Connection conn, String category, String subcategory, Integer simulationId) {
        this.conn = conn;
        this.category = category;
        this.subcategory = subcategory;
        this.simulationId = simulationId;
        this.questionDAO = new QuestionDAO(conn);
        this.optionDAO = new OptionDAO(conn);
    }

    public void execute(int bspAnzahl) throws IOException, SQLException {
        // Get the subcategoryId
        int subcategoryId = questionDAO.getSubcategoryId(category, subcategory);

        // Get the next question number
        int nextQuestionNumber = questionDAO.getNextQuestionNumber(simulationId, subcategoryId);
        List<Zahlenfolge> zahlenfolgenList = new ArrayList<>();
        for (int i = 0; i < bspAnzahl; i++) {
            int auswahl = r.nextInt(15);
            int zahl1 = randomRange(1, 50, false);
            int zahl2 = randomRange(1, 50, false);
            int zahl3 = randomRange(1, 50, false);

            Zahlenfolge zahlenfolge = null;

            switch (auswahl) {
                case 0:
                    zahlenfolge = zahlenfolge1(zahl1);
                    break;
                case 1:
                    zahlenfolge = zahlenfolge2(zahl1);
                    break;
                case 2:
                    zahlenfolge = zahlenfolge3(zahl1);
                    break;
                case 3:
                    zahlenfolge = zahlenfolge4(zahl1);
                    break;
                case 4:
                    zahlenfolge = zahlenfolge5(zahl1);
                    break;
                case 5:
                    zahlenfolge = zahlenfolge6(zahl1, zahl2);
                    break;
                case 6:
                    zahlenfolge = zahlenfolge7(zahl1, zahl2);
                    break;
                case 7:
                    zahlenfolge = zahlenfolge8(zahl1, zahl2, zahl3);
                    break;
                case 8:
                    zahlenfolge = zahlenfolge9(zahl1, zahl2, zahl3);
                    break;
                case 9:
                    zahlenfolge = zahlenfolge10(zahl1);
                    break;
                case 10:
                    zahlenfolge = zahlenfolge11(zahl1, zahl2);
                    break;
                case 11:
                    zahlenfolge = zahlenfolge12(zahl1, zahl2);
                    break;
                case 12:
                    zahlenfolge = zahlenfolge13(zahl1, zahl2, zahl3);
                    break;
                case 13:
                    zahlenfolge = zahlenfolge14(zahl1, zahl2, zahl3);
                    break;
                case 14:
                    zahlenfolge = zahlenfolge15(zahl1, zahl2);
                    break;
            }

            zahlenfolgenList.add(zahlenfolge);

            // Insert the question and options into the database
            insertQuestionAndOptions(zahlenfolge, nextQuestionNumber);
            nextQuestionNumber++;

        }
    }

    private static void insertQuestionAndOptions(Zahlenfolge zahlenfolge, int questionNumber) throws SQLException {
        // Generate question text: first 7 numbers with "? ?"
        StringBuilder questionTextBuilder = new StringBuilder();
        for (int num : zahlenfolge.getSequence()) {
            questionTextBuilder.append(num).append("    ");
        }
        questionTextBuilder.append("?    ?");

        String questionText = questionTextBuilder.toString();

        // Insert the question into the database
        int questionId = questionDAO.insertQuestion(category, subcategory, questionText, questionNumber, simulationId);

        // Generate options
        int correctOptionIndex = r.nextInt(5); // Random index between 0 and 4

        for (int j = 0; j < 5; j++) {
            String optionLabel;
            String optionText;
            boolean isCorrect;

            optionLabel = (j == 4) ? "E" : String.valueOf((char) ('A' + j));

            if (j == correctOptionIndex) {
                if (j == 4) {
                    optionText = "Keine Antwort ist richtig.";
                    isCorrect = true;
                } else {
                    optionText = zahlenfolge.getSolution()[0] + "/" + zahlenfolge.getSolution()[1];
                    isCorrect = true;
                }
            } else {
                if (j == 4) {
                    optionText = "Keine Antwort ist richtig.";
                    isCorrect = false;
                } else {
                    int changeAntwort1 = randomRange(-30, 30, false);
                    int changeAntwort2 = randomRange(-30, 30, false);
                    optionText = (zahlenfolge.getSolution()[0] + changeAntwort1) + "/"
                            + (zahlenfolge.getSolution()[1] + changeAntwort2);
                    isCorrect = false;
                }
            }

            // Insert the option into the database
            optionDAO.insertOption(questionId, optionLabel, optionText, isCorrect);
        }

        // Optionally, handle solution information if needed
    }

    public static int randomRange(int min, int max, boolean even) {
        if (min > max) {
            throw new IllegalArgumentException("Min cannot be greater than max.");
        }
        int random;

        max += 1;

        if (even) {
            // Adjust min and max to the next even numbers if they are odd
            if (min % 2 != 0)
                min++;
            if (max % 2 != 0)
                max--;
            if (min > max) {
                throw new IllegalArgumentException("No even numbers in the given range.");
            }
            int numEvens = ((max - min) / 2) + 1;
            random = min + 2 * r.nextInt(numEvens);
        } else {
            random = min + r.nextInt(max - min + 1);
        }

        return random;
    }

    private static Zahlenfolge zahlenfolge1(int zahl) {
        int[] output = new int[9];
        output[0] = zahl;

        int Op1 = randomRange(-20, 10, false); // range [-20 ... 10]
        int Op2 = randomRange(1, 6, false); // range [1 ... 6]
        String lsgInformation = "ZF 1  |  Zahl: " + zahl + ", Op1: " + Op1 + ", Op2: " + Op2;
        for (int i = 0; i < output.length - 1; i++) {
            if (i % 2 == 0)
                output[i + 1] = output[i] + Op1;
            else {
                output[i + 1] = output[i] * Op2;
            }
        }

        int[] sequence = Arrays.copyOf(output, output.length - 2);
        int[] solution = { output[7], output[8] };
        return new Zahlenfolge(sequence, solution, lsgInformation);
    }

    private static Zahlenfolge zahlenfolge2(int zahl) {
        int[] output = new int[9];
        output[0] = zahl;

        int Op1 = randomRange(-10, 10, false); // range [-10 ... 10]
        int Op2 = randomRange(-10, 10, false); // range [-10 ... 10]
        int Op1Step = randomRange(-6, 6, false); // range [-6 ... 6];
        int Op2Step = randomRange(-6, 6, false); // range [-6 ... 6];
        String lsgInformation = "ZF 2  |  Zahl: " + zahl + ", Op1: " + Op1 + ", Op1Step: " + Op1Step + ",  Op2: " + Op2
                + ", Op2Step: " + Op2Step;
        for (int i = 0; i < output.length - 1; i++) {
            if (i % 2 == 0) {
                output[i + 1] = output[i] + Op1;
                Op1 += Op1Step;
            } else {
                output[i + 1] = output[i] + Op2;
                Op2 += Op2Step;
            }
        }

        int[] sequence = Arrays.copyOf(output, output.length - 2);
        int[] solution = { output[7], output[8] };
        return new Zahlenfolge(sequence, solution, lsgInformation);
    }

    private static Zahlenfolge zahlenfolge3(int zahl) {
        int[] output = new int[9];
        output[0] = zahl;

        int Op1 = randomRange(-10, 10, false); // range [-10 ... 10]
        int Op1Step = randomRange(2, 3, false); // range [2 ... 3]
        int Op1StepSub = randomRange(1, 3, false); // range [1 ... 3];
        String lsgInformation = "ZF 3  |  Zahl: " + zahl + ", Op1: " + Op1 + ", Op1Step: " + Op1Step + ", Op1StepSub: "
                + Op1StepSub;
        for (int i = 0; i < output.length - 1; i++) {
            output[i + 1] = output[i] + Op1;
            if (i == 0)
                Op1 += Op1Step;
            else {
                Op1 += Op1Step * Op1StepSub;
                Op1Step *= Op1StepSub;
            }
        }

        int[] sequence = Arrays.copyOf(output, output.length - 2);
        int[] solution = { output[7], output[8] };
        return new Zahlenfolge(sequence, solution, lsgInformation);
    }

    private static Zahlenfolge zahlenfolge4(int zahl) {
        int[] output = new int[9];
        if (zahl % 2 != 0)
            zahl += 1;
        output[0] = zahl;

        int Op1 = randomRange(2, 4, true); // range even [2, 4]
        int Op2 = randomRange(2, 4, true); // range even [2, 4]
        int Op3 = randomRange(2, 22, true); // range even[2...20];
        String lsgInformation = "ZF 4  |  Zahl: " + zahl + ", Op1: " + Op1 + ", Op2: " + Op2 + ", Op3: " + Op3;
        for (int i = 0; i < output.length - 1; i++) {
            if (i % 3 == 0)
                output[i + 1] = output[i] / Op1;
            else if (i % 3 == 1)
                output[i + 1] = output[i] * Op2;
            else
                output[i + 1] = output[i] + Op3;
        }

        int[] sequence = Arrays.copyOf(output, output.length - 2);
        int[] solution = { output[7], output[8] };
        return new Zahlenfolge(sequence, solution, lsgInformation);
    }

    /******* SCHWER *******/
    private static Zahlenfolge zahlenfolge5(int zahl) {
        int[] output = new int[9];
        output[0] = zahl;

        int Op1 = randomRange(1, 15, false); // range [1 ... 15]
        int Op2 = randomRange(-15, -1, false); // range [-15 ... -1]
        int Op3 = randomRange(2, 4, true); // range [2, 4]
        int OpStep = randomRange(1, 3, false); // range even[1...3];
        String lsgInformation = "ZF 5  |  Zahl: " + zahl + ", Op1: " + Op1 + ", Op2: " + Op2 + ", Op3: " + Op3
                + ", OpStep: "
                + OpStep;
        for (int i = 0; i < output.length - 1; i++) {
            if (i % 3 == 0) {
                output[i + 1] = output[i] + Op1;
                Op1 += OpStep;
            } else if (i % 3 == 1) {
                output[i + 1] = output[i] + Op2;
                Op2 += OpStep;
            } else {
                output[i + 1] = output[i] * Op3;
                Op3 += OpStep;
            }
        }

        int[] sequence = Arrays.copyOf(output, output.length - 2);
        int[] solution = { output[7], output[8] };
        return new Zahlenfolge(sequence, solution, lsgInformation);
    }

    private static Zahlenfolge zahlenfolge6(int zahl1, int zahl2) {
        int[] output = new int[9];
        output[0] = zahl1;
        output[1] = zahl2;

        String lsgInformation = "ZF 6  |  Zahl: " + zahl1 + ", Zahl2: " + zahl2;
        for (int i = 0; i < output.length - 2; i++) {
            output[i + 2] = output[i + 1] + output[i];
        }

        int[] sequence = Arrays.copyOf(output, output.length - 2);
        int[] solution = { output[7], output[8] };
        return new Zahlenfolge(sequence, solution, lsgInformation);
    }

    /******* SCHWER *******/
    private static Zahlenfolge zahlenfolge7(int zahl1, int zahl2) {
        int[] output = new int[9];
        output[0] = zahl1;
        output[1] = zahl2;

        String lsgInformation = "ZF 7  |  Zahl: " + zahl1 + ", Zahl2: " + zahl2;
        for (int i = 0; i < output.length - 2; i++) {
            if (i % 2 == 0)
                output[i + 2] = output[i + 1] + output[i];
            else
                output[i + 2] = output[i + 1] - output[i];
        }

        int[] sequence = Arrays.copyOf(output, output.length - 2);
        int[] solution = { output[7], output[8] };
        return new Zahlenfolge(sequence, solution, lsgInformation);
    }

    private static Zahlenfolge zahlenfolge8(int zahl1, int zahl2, int zahl3) {
        int[] output = new int[9];
        output[0] = zahl1;
        output[1] = zahl2;
        output[2] = zahl3;

        String lsgInformation = "ZF 8  |  Zahl1: " + zahl1 + ", Zahl2: " + zahl2 + ", Zahl3: " + zahl3;
        for (int i = 0; i < output.length - 3; i++) {
            output[i + 3] = output[i + 2] + output[i + 1] + output[i];
        }

        int[] sequence = Arrays.copyOf(output, output.length - 2);
        int[] solution = { output[7], output[8] };
        return new Zahlenfolge(sequence, solution, lsgInformation);
    }

    /******* SCHWER *******/
    private static Zahlenfolge zahlenfolge9(int zahl1, int zahl2, int zahl3) {
        int[] output = new int[9];
        output[0] = zahl1;
        output[1] = zahl2;
        output[2] = zahl3;

        String lsgInformation = "ZF 9  |  Zahl: " + zahl1 + ", Zahl2: " + zahl2 + ", Zahl3: " + zahl3;
        for (int i = 0; i < output.length - 3; i++) {
            output[i + 3] = output[i + 2] + output[i];
        }

        int[] sequence = Arrays.copyOf(output, output.length - 2);
        int[] solution = { output[7], output[8] };
        return new Zahlenfolge(sequence, solution, lsgInformation);
    }

    private static Zahlenfolge zahlenfolge10(int zahl1) {
        int[] output = new int[9];
        output[0] = zahl1;
        int Op1 = randomRange(1, 10, false); // range [1 ... 10]
        int Op2 = randomRange(1, 10, false); // range [1 ... 10]

        String lsgInformation = "ZF 10  |  Zahl: " + zahl1 + ", Op1: " + Op1 + ", Op2: " + Op2;
        for (int i = 0; i < output.length - 2; i = i + 2) {
            output[i + 1] = output[i] + Op1;
            output[i + 2] = output[i + 1] + Op2;
            Op1 += Op2;
            Op2 += Op1;
        }

        int[] sequence = Arrays.copyOf(output, output.length - 2);
        int[] solution = { output[7], output[8] };
        return new Zahlenfolge(sequence, solution, lsgInformation);
    }

    private static Zahlenfolge zahlenfolge11(int zahl1, int zahl2) {
        int[] output = new int[9];
        output[0] = zahl1;
        output[1] = zahl2;
        int Op1 = randomRange(1, 15, false); // range [1 ... 15]
        int Op2 = randomRange(1, 15, false); // range [1 ... 15]

        String lsgInformation = "ZF 11 |  Zahl1: " + zahl1 + ", Zahl2: " + zahl2 + ", Op1: " + Op1 + ", Op2: " + Op2;
        for (int i = 0; i < output.length - 2; i++) {
            if (i % 2 == 0)
                output[i + 2] = output[i] + Op1;
            else {
                output[i + 2] = output[i] + Op2;
                Op2 *= 2;
            }
        }

        int[] sequence = Arrays.copyOf(output, output.length - 2);
        int[] solution = { output[7], output[8] };
        return new Zahlenfolge(sequence, solution, lsgInformation);
    }

    /******* SCHWER *******/
    private static Zahlenfolge zahlenfolge12(int zahl1, int zahl2) {
        int[] output = new int[9];
        output[0] = zahl1;
        output[1] = zahl2;
        int Op1 = randomRange(1, 15, false); // range [1 ... 15]
        int Op1Step = randomRange(1, 10, false); // range [1 ... 10]
        int Op2 = randomRange(-15, -1, false); // range [-15 ... -1]

        String lsgInformation = "ZF 12 |  Zahl1: " + zahl1 + ", Zahl2: " + zahl2 + ", Op1: " + Op1 + ", Op1Step: "
                + Op1Step
                + ", Op2: " + Op2;
        for (int i = 0; i < output.length - 2; i++) {
            if (i % 2 == 0) {
                output[i + 2] = output[i] + Op1;
                Op1 += Op1Step;
            } else {
                output[i + 2] = output[i] + Op2;
            }
        }

        int[] sequence = Arrays.copyOf(output, output.length - 2);
        int[] solution = { output[7], output[8] };
        return new Zahlenfolge(sequence, solution, lsgInformation);
    }

    private static Zahlenfolge zahlenfolge13(int zahl1, int zahl2, int zahl3) {
        int[] output = new int[9];
        output[0] = zahl1;
        output[1] = zahl2;
        output[2] = zahl3;
        int Op1 = randomRange(1, 8, false); // range [1 ... 8]

        String lsgInformation = "ZF 13 |  Zahl1: " + zahl1 + ", Zahl2: " + zahl2 + ", Op1: " + Op1;
        for (int i = 0; i < output.length - 3; i++) {
            if (i % 2 == 0)
                output[i + 3] = output[i] + Op1;
            else
                output[i + 3] = output[i] * Op1;
        }

        int[] sequence = Arrays.copyOf(output, output.length - 2);
        int[] solution = { output[7], output[8] };
        return new Zahlenfolge(sequence, solution, lsgInformation);
    }

    /******* SCHWER *******/
    private static Zahlenfolge zahlenfolge14(int zahl1, int zahl2, int zahl3) {
        int[] output = new int[9];
        output[0] = zahl1;
        output[1] = zahl2;
        output[2] = zahl3;
        int Op1 = randomRange(1, 4, false); // range [1 ... 4]

        String lsgInformation = "ZF 14 |  Zahl1: " + zahl1 + ", Zahl2: " + zahl2 + ", Op1: " + Op1;
        for (int i = 0; i < output.length - 3; i++) {
            output[i + 3] = output[i] * (Op1 + i);
        }

        int[] sequence = Arrays.copyOf(output, output.length - 2);
        int[] solution = { output[7], output[8] };
        return new Zahlenfolge(sequence, solution, lsgInformation);
    }

    private static Zahlenfolge zahlenfolge15(int zahl1, int zahl2) {
        int[] output = new int[9];
        output[0] = zahl1;
        output[1] = zahl2;
        int Op1 = randomRange(1, 18, false); // range [1 ... 18]
        int Op2 = randomRange(-15, -1, false); // range [-15 ... -1]
        int OpStep = randomRange(1, 5, false); // range [1 ... 5]

        String lsgInformation = "ZF 15 |  Zahl1: " + zahl1 + ", Zahl2: " + zahl2 + ", Op1: " + Op1 + ", Op2: " + Op2
                + ", Op1Step: " + OpStep;
        for (int i = 0; i < output.length - 2; i++) {
            if (i % 2 == 0) {
                output[i + 2] = output[i] + Op1;
                Op1 *= OpStep;
            } else {
                output[i + 2] = output[i] + Op2;
                Op2 *= OpStep;
            }
        }

        int[] sequence = Arrays.copyOf(output, output.length - 2);
        int[] solution = { output[7], output[8] };
        return new Zahlenfolge(sequence, solution, lsgInformation);
    }

    public static class Zahlenfolge {
        private int[] sequence;
        private int[] solution; // The last two numbers
        private String lsgInformation;

        public Zahlenfolge(int[] sequence, int[] solution, String lsgInformation) {
            this.sequence = sequence;
            this.solution = solution;
            this.lsgInformation = lsgInformation;
        }

        public int[] getSequence() {
            return sequence;
        }

        public int[] getSolution() {
            return solution;
        }

        public String getLsgInformation() {
            return lsgInformation;
        }
    }
}