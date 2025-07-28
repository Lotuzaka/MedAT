package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Service class for managing passages in the context of test simulations.
 * This class provides higher-level operations for working with passages.
 */
public class PassageService {
    private final PassageDAO passageDAO;
    private final Connection conn;

    public PassageService(Connection conn) {
        this.passageDAO = new PassageDAO(conn);
        this.conn = conn;
    }

    /**
     * Gets the subcategory ID for a category and subcategory name.
     */
    private int getSubcategoryId(String category, String subcategory) throws SQLException {
        String sql = "SELECT s.id FROM subcategories s JOIN categories c ON s.category_id = c.id WHERE c.name = ? AND s.name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category);
            stmt.setString(2, subcategory);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        throw new SQLException("Subcategory not found: " + category + " -> " + subcategory);
    }

    /**
     * Creates a new passage for a specific test simulation and subcategory.
     * 
     * @param testSimulationId The test simulation ID
     * @param category The category name (e.g., "Basiskenntnistest")
     * @param subcategory The subcategory name (e.g., "Textverständnis")
     * @param text The passage text content
     * @param source Optional source information
     * @return The generated passage ID
     */
    public int createPassageForSimulation(int testSimulationId, String category, String subcategory, 
                                        String text, String source) throws SQLException {
        int subcategoryId = getSubcategoryId(category, subcategory);
        return passageDAO.insert(subcategoryId, testSimulationId, text, source);
    }

    /**
     * Gets all passages for a specific test simulation.
     */
    public List<PassageDAO.Passage> getPassagesForSimulation(int testSimulationId) throws SQLException {
        return passageDAO.findByTestSimulation(testSimulationId);
    }

    /**
     * Gets passages for a specific subcategory within a test simulation.
     */
    public List<PassageDAO.Passage> getPassagesForSubcategory(int testSimulationId, String category, 
                                                             String subcategory) throws SQLException {
        int subcategoryId = getSubcategoryId(category, subcategory);
        return passageDAO.findBySubcategoryAndSimulation(subcategoryId, testSimulationId);
    }

    /**
     * Creates sample passages for testing Textverständnis.
     * This method can be called to populate test data.
     */
    public void createSamplePassages(int testSimulationId) throws SQLException {
        String[] sampleTexts = {
            "Moderne Medizin hat in den letzten Jahrzehnten erhebliche Fortschritte gemacht. Neue Behandlungsmethoden und Diagnoseverfahren ermöglichen es Ärzten, Krankheiten früher zu erkennen und effektiver zu behandeln. Dabei spielt die Präventivmedizin eine zunehmend wichtige Rolle. Regelmäßige Vorsorgeuntersuchungen können helfen, Gesundheitsprobleme zu vermeiden oder in einem frühen Stadium zu behandeln.",
            
            "Die Klimaforschung zeigt eindeutig, dass sich das Erdklima wandelt. Temperaturen steigen weltweit an, was zu verschiedenen Auswirkungen führt. Gletscher schmelzen, der Meeresspiegel steigt, und extreme Wetterereignisse nehmen zu. Wissenschaftler sind sich einig, dass menschliche Aktivitäten der Hauptgrund für diese Veränderungen sind.",
            
            "Künstliche Intelligenz revolutioniert viele Bereiche unseres Lebens. Von der Medizin über die Automobilindustrie bis hin zur Bildung finden KI-Systeme Anwendung. Dabei bringen sie sowohl Chancen als auch Risiken mit sich. Während sie Prozesse optimieren und neue Möglichkeiten eröffnen können, entstehen auch Fragen bezüglich Datenschutz und Arbeitsplätzen."
        };

        for (int i = 0; i < sampleTexts.length; i++) {
            createPassageForSimulation(testSimulationId, "Basiskenntnistest", "Textverständnis", 
                                     sampleTexts[i], "Sample passage " + (i + 1));
        }
    }
}
