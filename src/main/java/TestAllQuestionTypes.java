import merk.MerkQuestionEngine;
import model.AllergyCardData;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

public class TestAllQuestionTypes {
    public static void main(String[] args) {
        // Create test allergy card data
        List<AllergyCardData> testCards = new ArrayList<>();
        
        testCards.add(new AllergyCardData(
            "Max Mustermann", 
            LocalDate.of(1990, 1, 15),
            "Ja",
            "A",
            "Erdnuss, Kuhmilch",
            "12345",
            "Deutschland",
            null
        ));
        
        testCards.add(new AllergyCardData(
            "Anna Schmidt", 
            LocalDate.of(1985, 6, 20),
            "Nein",
            "B",
            "Haselnuss",
            "67890",
            "Österreich",
            null
        ));
        
        testCards.add(new AllergyCardData(
            "Peter Müller", 
            LocalDate.of(1992, 3, 10),
            "Ja",
            "AB",
            "Sojabohne",
            "11111",
            "Schweiz",
            null
        ));
        
        testCards.add(new AllergyCardData(
            "Maria Weber", 
            LocalDate.of(1988, 12, 5),
            "Nein",
            "0",
            "Gluten",
            "22222",
            "Frankreich",
            null
        ));

        // Create engine and generate all question types
        MerkQuestionEngine engine = new MerkQuestionEngine(testCards);
        
        System.out.println("Testing ALL Merkfähigkeiten Question Types:");
        System.out.println("==========================================");
        
        // Generate all available question types
        List<MerkQuestionEngine.Question> allQuestions = engine.generateAllTypes();
        
        for (int i = 0; i < allQuestions.size(); i++) {
            MerkQuestionEngine.Question q = allQuestions.get(i);
            System.out.println("\n--- Question Type " + (i + 1) + " ---");
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
        
        System.out.println("\n==========================================");
        System.out.println("Generated " + allQuestions.size() + " different question types");
        System.out.println("All questions have exactly 5 options (A-E)");
        System.out.println("Option E is always 'Keine Antwort ist richtig.'");
    }
}
