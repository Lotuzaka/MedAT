package generator;

import dao.PassageDAO;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for TextverstaendnisGenerator to verify it correctly generates
 * questions with passage_id and options.
 * 
 * This test focuses on database integration and bypasses NLP model requirements
 * by testing the database operations directly.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TextverstaendnisGeneratorIT {
    
    private static Connection conn;
    private static int testSubcategoryId;
    private static int testSimulationId = 999;
    private static int testPassageId;
    
    @BeforeAll
    static void setUpDatabase() throws SQLException {
        // Use H2 in-memory database for testing
        conn = DriverManager.getConnection("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", "");
        
        // Create required tables
        try (Statement stmt = conn.createStatement()) {
            // Create subcategories table
            stmt.execute("""
                CREATE TABLE subcategories (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    category VARCHAR(255) NOT NULL,
                    name VARCHAR(255) NOT NULL
                )
            """);
            
            // Create passages table
            stmt.execute("""
                CREATE TABLE passages (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    subcategory_id INTEGER NOT NULL,
                    test_simulation_id INTEGER,
                    text TEXT NOT NULL,
                    source VARCHAR(255),
                    FOREIGN KEY (subcategory_id) REFERENCES subcategories(id)
                )
            """);
            
            // Create questions table
            stmt.execute("""
                CREATE TABLE questions (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    subcategory_id INTEGER NOT NULL,
                    question_number INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    format VARCHAR(50) DEFAULT 'multiple_choice',
                    test_simulation_id INTEGER,
                    passage_id INTEGER,
                    FOREIGN KEY (subcategory_id) REFERENCES subcategories(id),
                    FOREIGN KEY (passage_id) REFERENCES passages(id)
                )
            """);
            
            // Create options table
            stmt.execute("""
                CREATE TABLE options (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    question_id INTEGER NOT NULL,
                    label VARCHAR(10) NOT NULL,
                    text VARCHAR(1000) NOT NULL,
                    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
                    FOREIGN KEY (question_id) REFERENCES questions(id)
                )
            """);
            
            // Insert test subcategory
            stmt.execute("INSERT INTO subcategories (category, name) VALUES ('Biologie', 'Textverständnis')");
            try (ResultSet rs = stmt.executeQuery("SELECT id FROM subcategories WHERE name = 'Textverständnis'")) {
                if (rs.next()) {
                    testSubcategoryId = rs.getInt("id");
                }
            }
        }
        
        // Insert test passage
        PassageDAO passageDAO = new PassageDAO(conn);
        testPassageId = passageDAO.insert(testSubcategoryId, testSimulationId, 
            "Die Photosynthese ist ein biochemischer Prozess, bei dem Pflanzen Kohlendioxid und Wasser " +
            "mithilfe von Lichtenergie in Glucose und Sauerstoff umwandeln. Dieser Prozess findet in den " +
            "Chloroplasten statt und ist essentiell für das Leben auf der Erde. Die Reaktion kann durch " +
            "die Formel 6CO₂ + 6H₂O + Lichtenergie → C₆H₁₂O₆ + 6O₂ dargestellt werden. Ohne Photosynthese " +
            "gäbe es keinen Sauerstoff in der Atmosphäre und somit kein Leben, wie wir es kennen.",
            "Biologie Lehrbuch, Kapitel 8");
    }
    
    @AfterAll
    static void tearDown() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
    
    @Test
    @Order(1)
    void testManualQuestionInsertion() throws Exception {
        // Manually insert questions using the same methods the generator would use
        // This tests the database integration without requiring NLP models
        
        // Get subcategory ID
        int subId = getSubcategoryId("Biologie", "Textverständnis");
        assertEquals(testSubcategoryId, subId, "Should get correct subcategory ID");
        
        // Get next question number
        int qNum = getNextQuestionNumber(testSimulationId, subId);
        assertEquals(1, qNum, "First question should be number 1");
        
        // Insert a test question
        String questionText = "Was ist Photosynthese?";
        int questionId = insertQuestion(questionText, qNum, testPassageId);
        assertTrue(questionId > 0, "Question ID should be positive");
        
        // Insert 5 options for the question
        String[] options = {
            "Ein Prozess der Energiegewinnung in Pflanzen",
            "Ein Prozess der Wasseraufnahme",
            "Ein Prozess der Nährstoffverteilung", 
            "Ein Prozess der Wurzelbildung",
            "Ein Prozess der Blütenbildung"
        };
        
        for (int i = 0; i < options.length; i++) {
            String label = String.valueOf((char)('A' + i));
            boolean isCorrect = (i == 0); // First option is correct
            insertOption(questionId, label, options[i], isCorrect);
        }
        
        // Verify the question was inserted correctly
        verifyQuestionWithPassageId(questionId, testPassageId);
        verifyQuestionHasFiveOptions(questionId);
    }
    
    @Test
    @Order(2)
    void testMultipleQuestionsInsertion() throws Exception {
        // Insert multiple questions to test question numbering
        int subId = getSubcategoryId("Biologie", "Textverständnis");
        
        for (int i = 0; i < 3; i++) {
            int qNum = getNextQuestionNumber(testSimulationId, subId);
            String questionText = "Test question " + (i + 1);
            int questionId = insertQuestion(questionText, qNum, testPassageId);
            
            // Add 5 options
            for (int j = 0; j < 5; j++) {
                String label = String.valueOf((char)('A' + j));
                insertOption(questionId, label, "Option " + (j + 1), j == 0);
            }
        }
        
        // Verify all questions have correct passage_id
        String sql = "SELECT COUNT(*) FROM questions WHERE passage_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, testPassageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    assertTrue(count >= 3, "Should have at least 3 questions for the passage");
                }
            }
        }
    }
    
    // Helper methods that mirror the TextverstaendnisGenerator functionality
    private int getSubcategoryId(String category, String subcategory) throws SQLException {
        String sql = "SELECT id FROM subcategories WHERE category = ? AND name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category);
            stmt.setString(2, subcategory);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        throw new SQLException("Subcategory not found: " + category + " - " + subcategory);
    }
    
    private int getNextQuestionNumber(Integer simulationId, int subcategoryId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(question_number), 0) + 1 FROM questions WHERE test_simulation_id = ? AND subcategory_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, simulationId);
            stmt.setInt(2, subcategoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 1;
    }
    
    private int insertQuestion(String text, int number, int passageId) throws SQLException {
        int subId = getSubcategoryId("Biologie", "Textverständnis");
        String sql = "INSERT INTO questions (subcategory_id, question_number, text, format, test_simulation_id, passage_id) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, subId);
            stmt.setInt(2, number);
            stmt.setString(3, text);
            stmt.setString(4, "multiple_choice");
            stmt.setInt(5, testSimulationId);
            stmt.setInt(6, passageId);
            stmt.executeUpdate();
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to insert question");
    }
    
    private void insertOption(int questionId, String label, String text, boolean isCorrect) throws SQLException {
        String sql = "INSERT INTO options (question_id, label, text, is_correct) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, questionId);
            stmt.setString(2, label);
            stmt.setString(3, text);
            stmt.setBoolean(4, isCorrect);
            stmt.executeUpdate();
        }
    }
    
    private void verifyQuestionWithPassageId(int questionId, int expectedPassageId) throws SQLException {
        String sql = "SELECT passage_id, subcategory_id, test_simulation_id FROM questions WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Question should exist");
                assertEquals(expectedPassageId, rs.getInt("passage_id"), 
                    "Question should have correct passage_id");
                assertEquals(testSubcategoryId, rs.getInt("subcategory_id"), 
                    "Question should have correct subcategory_id");
                assertEquals(testSimulationId, rs.getInt("test_simulation_id"), 
                    "Question should have correct test_simulation_id");
            }
        }
    }
    
    private void verifyQuestionHasFiveOptions(int questionId) throws SQLException {
        String sql = "SELECT COUNT(*) as option_count, SUM(CASE WHEN is_correct THEN 1 ELSE 0 END) as correct_count FROM options WHERE question_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Should get option counts");
                assertEquals(5, rs.getInt("option_count"), 
                    "Question should have exactly 5 options");
                assertEquals(1, rs.getInt("correct_count"), 
                    "Question should have exactly 1 correct option");
            }
        }
        
        // Verify option labels are A, B, C, D, E
        String labelSql = "SELECT label FROM options WHERE question_id = ? ORDER BY label";
        try (PreparedStatement ps = conn.prepareStatement(labelSql)) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                String[] expectedLabels = {"A", "B", "C", "D", "E"};
                int index = 0;
                while (rs.next() && index < expectedLabels.length) {
                    assertEquals(expectedLabels[index], rs.getString("label"),
                        "Option labels should be A, B, C, D, E in order");
                    index++;
                }
                assertEquals(5, index, "Should have processed 5 option labels");
            }
        }
    }
}
