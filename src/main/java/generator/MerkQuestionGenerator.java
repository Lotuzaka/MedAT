package generator;

import model.AllergyCardData;
import merk.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * Generates Merkfaehigkeit questions based on the current allergy cards
 * and inserts them into the database.
 */
public class MerkQuestionGenerator {
    private final Connection conn;
    private final String category;
    private final String subcategory;
    private final Integer simulationId;
    private final List<AllergyCardData> cards;
    private final MerkQuestionEngine engine;

    public MerkQuestionGenerator(Connection conn, String category, String subcategory,
                                 Integer simulationId, List<AllergyCardData> cards) {
        this.conn = conn;
        this.category = category;
        this.subcategory = subcategory;
        this.simulationId = simulationId;
        this.cards = cards;
        this.engine = new MerkQuestionEngine(cards);
    }

    public void execute(int count) throws SQLException {
        try {
            // Note: Since QuestionDAO and OptionDAO are in the default package,
            // we need to use reflection to access them
            Class<?> questionDAOClass = Class.forName("QuestionDAO");
            Class<?> optionDAOClass = Class.forName("OptionDAO");
            
            Object questionDAO = questionDAOClass.getDeclaredConstructor(java.sql.Connection.class).newInstance(conn);
            Object optionDAO = optionDAOClass.getDeclaredConstructor(java.sql.Connection.class).newInstance(conn);
            
            // Get subcategory ID
            var getSubcategoryIdMethod = questionDAOClass.getMethod("getSubcategoryId", String.class, String.class);
            int subId = (Integer) getSubcategoryIdMethod.invoke(questionDAO, category, subcategory);
            
            // Get next question number  
            var getNextQuestionNumberMethod = questionDAOClass.getMethod("getNextQuestionNumber", Integer.class, int.class);
            int qNum = (Integer) getNextQuestionNumberMethod.invoke(questionDAO, simulationId, subId);
            
            // Generate questions
            for (int i = 0; i < count; i++) {
                System.out.println("Generating question " + (i + 1) + " of " + count);
                MerkQuestionEngine.Question q = engine.generate();
                System.out.println("Generated question: " + q.text());
                System.out.println("Options: " + q.options());
                System.out.println("Correct answer: " + q.correctAnswer());
                
                // Insert question
                var insertQuestionMethod = questionDAOClass.getMethod("insertQuestion", String.class, String.class, String.class, int.class, Integer.class);
                int qId = (Integer) insertQuestionMethod.invoke(questionDAO, category, subcategory, q.text(), qNum, simulationId);
                System.out.println("Inserted question with ID: " + qId);
                
                // Insert options
                var insertOptionMethod = optionDAOClass.getMethod("insertOption", int.class, String.class, String.class, boolean.class);
                for (int j = 0; j < q.options().size(); j++) {
                    String label = String.valueOf((char)('A' + j));
                    boolean isCorrect = (j == q.correctIndex());
                    insertOptionMethod.invoke(optionDAO, qId, label, q.options().get(j), isCorrect);
                    System.out.println("Inserted option " + label + ": " + q.options().get(j) + " (correct: " + isCorrect + ")");
                }
                
                qNum++;
            }
            
            System.out.println("Successfully generated " + count + " Merkfähigkeiten questions");
            
        } catch (Exception e) {
            throw new SQLException("Failed to generate questions: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate all available question types for testing - creates comprehensive test set
     */
    public void executeAllTypes() throws SQLException {
        try {
            // Note: Since QuestionDAO and OptionDAO are in the default package,
            // we need to use reflection to access them
            Class<?> questionDAOClass = Class.forName("QuestionDAO");
            Class<?> optionDAOClass = Class.forName("OptionDAO");
            
            Object questionDAO = questionDAOClass.getDeclaredConstructor(java.sql.Connection.class).newInstance(conn);
            Object optionDAO = optionDAOClass.getDeclaredConstructor(java.sql.Connection.class).newInstance(conn);
            
            // Get subcategory ID
            var getSubcategoryIdMethod = questionDAOClass.getMethod("getSubcategoryId", String.class, String.class);
            int subId = (Integer) getSubcategoryIdMethod.invoke(questionDAO, category, subcategory);
            
            // Get next question number  
            var getNextQuestionNumberMethod = questionDAOClass.getMethod("getNextQuestionNumber", Integer.class, int.class);
            int qNum = (Integer) getNextQuestionNumberMethod.invoke(questionDAO, simulationId, subId);
            
            // Generate comprehensive test set using the engine's generateAllTypes method
            var insertQuestionMethod = questionDAOClass.getMethod("insertQuestion", String.class, String.class, String.class, int.class, Integer.class);
            var insertOptionMethod = optionDAOClass.getMethod("insertOption", int.class, String.class, String.class, boolean.class);
            
            System.out.println("Generating comprehensive test set with all question variations...");
            List<MerkQuestionEngine.Question> questions = engine.generateAllTypes();
            
            int totalGenerated = 0;
            for (MerkQuestionEngine.Question q : questions) {
                try {
                    System.out.println("Generating question " + (totalGenerated + 1) + ": " + q.text());
                    
                    // Insert question
                    int qId = (Integer) insertQuestionMethod.invoke(questionDAO, category, subcategory, q.text(), qNum, simulationId);
                    System.out.println("Inserted question with ID: " + qId);
                    
                    // Insert options
                    for (int j = 0; j < q.options().size(); j++) {
                        String label = String.valueOf((char)('A' + j));
                        boolean isCorrect = (j == q.correctIndex());
                        insertOptionMethod.invoke(optionDAO, qId, label, q.options().get(j), isCorrect);
                    }
                    
                    qNum++;
                    totalGenerated++;
                } catch (Exception e) {
                    System.err.println("Failed to insert question: " + e.getMessage());
                }
            }
            
            System.out.println("Successfully generated " + totalGenerated + " comprehensive test questions covering all templates and variations");
            
        } catch (Exception e) {
            throw new SQLException("Failed to generate test questions: " + e.getMessage(), e);
        }
    }
}
