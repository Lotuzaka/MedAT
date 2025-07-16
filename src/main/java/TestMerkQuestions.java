import merk.MerkQuestionEngine;
import model.AllergyCardData;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

public class TestMerkQuestions {
    public static void main(String[] args) {
        // Create test allergy card data
        List<AllergyCardData> testCards = new ArrayList<>();
        
        testCards.add(new AllergyCardData(
            "Test Person A", 
            LocalDate.of(1990, 1, 15),
            "Ja",
            "A",
            "Erdnuss, Kuhmilch",
            "12345",
            "Deutschland",
            null
        ));
        
        testCards.add(new AllergyCardData(
            "Test Person B", 
            LocalDate.of(1985, 6, 20),
            "Nein",
            "B",
            "Haselnuss",
            "67890",
            "Österreich",
            null
        ));
        
        testCards.add(new AllergyCardData(
            "Test Person AB", 
            LocalDate.of(1992, 3, 10),
            "Ja",
            "AB",
            "Sojabohne",
            "11111",
            "Schweiz",
            null
        ));
        
        testCards.add(new AllergyCardData(
            "Test Person O", 
            LocalDate.of(1988, 12, 5),
            "Nein",
            "0",
            "Gluten",
            "22222",
            "Frankreich",
            null
        ));

        // Create engine and generate questions
        MerkQuestionEngine engine = new MerkQuestionEngine(testCards);
        
        System.out.println("Testing Merkfähigkeiten Question Generation:");
        System.out.println("===========================================");
        
        // Generate 10 test questions
        for (int i = 0; i < 10; i++) {
            MerkQuestionEngine.Question q = engine.generate();
            System.out.println("\n--- Question " + (i + 1) + " ---");
            System.out.println("Text: " + q.text());
            System.out.println("Options:");
            for (int j = 0; j < q.options().size(); j++) {
                char label = (char)('A' + j);
                boolean isCorrect = (j == q.correctIndex());
                System.out.println("  " + label + ") " + q.options().get(j) + 
                                 (isCorrect ? " *** CORRECT ***" : ""));
            }
            System.out.println("Correct Answer: " + q.correctAnswer());
            System.out.println("Correct Index: " + q.correctIndex());
        }
    }
}
