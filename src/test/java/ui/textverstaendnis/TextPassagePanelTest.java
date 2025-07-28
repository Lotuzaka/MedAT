package ui.textverstaendnis;

import dao.PassageDAO;
import org.junit.jupiter.api.*;

import javax.swing.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI test for TextPassagePanel to verify basic editing and save/load functionality.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TextPassagePanelTest {
    
    private static Connection conn;
    private static PassageDAO passageDAO;
    private static int testSubcategoryId = 1;
    private TextPassagePanel panel;
    
    @BeforeAll
    static void setUpDatabase() throws SQLException {
        // Use H2 in-memory database for testing
        conn = DriverManager.getConnection("jdbc:h2:mem:testui;DB_CLOSE_DELAY=-1", "sa", "");
        
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
            
            // Insert test subcategory
            stmt.execute("INSERT INTO subcategories (category, name) VALUES ('Biologie', 'Textverständnis')");
        }
        
        passageDAO = new PassageDAO(conn);
    }
    
    @BeforeEach
    void setUp() throws SQLException {
        // Clean up passages table before each test
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM passages WHERE subcategory_id = " + testSubcategoryId);
        }
        
        // Create panel for each test
        panel = new TextPassagePanel(passageDAO, testSubcategoryId);
    }
    
    @AfterAll
    static void tearDown() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
    
    @Test
    @Order(1)
    void testPanelInitialization() {
        assertNotNull(panel, "Panel should be created successfully");
        
        // Check that text components exist
        JTextPane textPane = findTextPane(panel);
        JTextField sourceField = findSourceField(panel);
        
        assertNotNull(textPane, "Text pane should exist");
        assertNotNull(sourceField, "Source field should exist");
        
        // Check initial state
        assertEquals("", textPane.getText(), "Text pane should be initially empty");
        assertEquals("", sourceField.getText(), "Source field should be initially empty");
    }
    
    @Test
    @Order(2)
    void testBasicTextEditing() {
        JTextPane textPane = findTextPane(panel);
        JTextField sourceField = findSourceField(panel);
        
        // Set some text
        String testText = "This is a test passage about photosynthesis.";
        String testSource = "Test Biology Book";
        
        textPane.setText(testText);
        sourceField.setText(testSource);
        
        // Verify text was set
        assertEquals(testText, textPane.getText(), "Text should be set correctly");
        assertEquals(testSource, sourceField.getText(), "Source should be set correctly");
    }
    
    @Test
    @Order(3)
    void testSavePassage() throws Exception {
        JTextPane textPane = findTextPane(panel);
        JTextField sourceField = findSourceField(panel);
        
        // Set test data
        String testText = "Photosynthesis is the process by which plants convert light energy into chemical energy.";
        String testSource = "Biology Textbook Chapter 5";
        
        textPane.setText(testText);
        sourceField.setText(testSource);
        
        // Save the passage
        panel.savePassage();
        
        // Verify passage was saved to database
        PassageDAO.Passage savedPassage = passageDAO.findBySubcategoryId(testSubcategoryId);
        assertNotNull(savedPassage, "Passage should be saved to database");
        assertEquals(testText, savedPassage.text(), "Saved text should match input");
        assertEquals(testSource, savedPassage.source(), "Saved source should match input");
    }
    
    @Test
    @Order(4)
    void testLoadPassage() throws Exception {
        // First, save a passage directly to the database
        String testText = "Cellular respiration is the process of breaking down glucose to release energy.";
        String testSource = "Advanced Biology, Chapter 7";
        
        passageDAO.insert(testSubcategoryId, testText, testSource);
        
        // Now load it in the panel
        panel.loadPassage();
        
        // Verify the text was loaded
        JTextPane textPane = findTextPane(panel);
        JTextField sourceField = findSourceField(panel);
        
        assertEquals(testText, textPane.getText(), "Loaded text should match database");
        assertEquals(testSource, sourceField.getText(), "Loaded source should match database");
    }
    
    @Test
    @Order(5)
    void testSaveAndLoadCycle() throws Exception {
        JTextPane textPane = findTextPane(panel);
        JTextField sourceField = findSourceField(panel);
        
        // Set original data
        String originalText = "DNA replication occurs during the S phase of the cell cycle.";
        String originalSource = "Molecular Biology Handbook";
        
        textPane.setText(originalText);
        sourceField.setText(originalSource);
        
        // Save
        panel.savePassage();
        
        // Clear the panel
        textPane.setText("");
        sourceField.setText("");
        
        // Load again
        panel.loadPassage();
        
        // Verify data was preserved
        assertEquals(originalText, textPane.getText(), "Text should be preserved through save/load cycle");
        assertEquals(originalSource, sourceField.getText(), "Source should be preserved through save/load cycle");
    }
    
    @Test
    @Order(6)
    void testUpdateExistingPassage() throws Exception {
        JTextPane textPane = findTextPane(panel);
        JTextField sourceField = findSourceField(panel);
        
        // Save initial passage
        String initialText = "Initial passage text";
        String initialSource = "Initial source";
        
        textPane.setText(initialText);
        sourceField.setText(initialSource);
        panel.savePassage();
        
        // Update the passage
        String updatedText = "Updated passage text with more information";
        String updatedSource = "Updated source reference";
        
        textPane.setText(updatedText);
        sourceField.setText(updatedSource);
        panel.savePassage();
        
        // Verify only one passage exists with updated content
        PassageDAO.Passage passage = passageDAO.findBySubcategoryId(testSubcategoryId);
        assertNotNull(passage, "Passage should exist");
        assertEquals(updatedText, passage.text(), "Text should be updated");
        assertEquals(updatedSource, passage.source(), "Source should be updated");
    }
    
    @Test
    @Order(7)
    void testHandleEmptySource() throws Exception {
        JTextPane textPane = findTextPane(panel);
        JTextField sourceField = findSourceField(panel);
        
        // Set text with empty source
        String testText = "Test passage without source";
        textPane.setText(testText);
        sourceField.setText("   "); // Whitespace only
        
        panel.savePassage();
        
        // Verify passage was saved with null source
        PassageDAO.Passage passage = passageDAO.findBySubcategoryId(testSubcategoryId);
        assertNotNull(passage, "Passage should be saved");
        assertEquals(testText, passage.text(), "Text should be saved");
        assertNull(passage.source(), "Empty source should be saved as null");
        
        // Load and verify empty source is displayed as empty string
        panel.loadPassage();
        assertEquals("", sourceField.getText(), "Empty source should display as empty string");
    }
    
    @Test
    @Order(8)
    void testToolbarButtonsExist() {
        // Find toolbar
        JToolBar toolbar = findToolBar(panel);
        assertNotNull(toolbar, "Toolbar should exist");
        
        // Check that toolbar has buttons (Bold, Italic, Bullet, Load, Save)
        int buttonCount = 0;
        for (int i = 0; i < toolbar.getComponentCount(); i++) {
            if (toolbar.getComponent(i) instanceof AbstractButton) {
                buttonCount++;
            }
        }
        assertTrue(buttonCount >= 5, "Toolbar should have at least 5 buttons (B, I, Bullet, Load, Save)");
    }
    
    // Helper methods to find UI components
    private JTextPane findTextPane(JPanel panel) {
        return findComponent(panel, JTextPane.class);
    }
    
    private JTextField findSourceField(JPanel panel) {
        return findComponent(panel, JTextField.class);
    }
    
    private JToolBar findToolBar(JPanel panel) {
        return findComponent(panel, JToolBar.class);
    }
    
    @SuppressWarnings("unchecked")
    private <T> T findComponent(JPanel panel, Class<T> componentClass) {
        for (int i = 0; i < panel.getComponentCount(); i++) {
            if (componentClass.isInstance(panel.getComponent(i))) {
                return (T) panel.getComponent(i);
            }
            if (panel.getComponent(i) instanceof JScrollPane scrollPane) {
                if (componentClass.isInstance(scrollPane.getViewport().getView())) {
                    return (T) scrollPane.getViewport().getView();
                }
            }
            if (panel.getComponent(i) instanceof JPanel subPanel) {
                T found = findComponent(subPanel, componentClass);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
