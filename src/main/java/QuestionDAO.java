import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.sql.ResultSet;

public class QuestionDAO {
    private int id;
    private int subcategoryId;
    private int questionNumber;
    private String text;
    private String format;
    private int test_simulation_id;
    private String shapeData;
    private String shapeType;

    private String dissectedPiecesData;
    private String assembledPiecesData;

    private boolean isNew;
    private boolean isDeleted;
    private boolean isModified;
    private List<OptionDAO> options;

    private Connection conn;
    private OptionDAO optionDAO;

    public QuestionDAO(int id, int subcategoryId, int questionNumber, String text, String format,
            int test_simulation_id) {
        this.id = id;
        this.subcategoryId = subcategoryId;
        this.questionNumber = questionNumber;
        this.text = text;
        this.format = format;
        this.test_simulation_id = test_simulation_id;
        this.isNew = false;
        this.isDeleted = false;
        this.isModified = false;
        this.options = new ArrayList<>();
    }

    public QuestionDAO(Connection conn) {
        this.conn = conn;
        this.optionDAO = new OptionDAO(conn);
    }

    public List<OptionDAO> getOptions() {
        return options;
    }

    public String getShapeData() {
        return shapeData;
    }

    public void setShapeData(String shapeData) {
        this.shapeData = shapeData;
    }

    public String getShapeType() {
        return shapeType;
    }

    public void setShapeType(String shapeType) {
        this.shapeType = shapeType;
    }

    public String getDissectedPiecesData() {
        return dissectedPiecesData;
    }

    public String getAssembledPiecesData() {
        return assembledPiecesData;
    }

    public void setDissectedPiecesData(String dissectedPiecesData) {
        this.dissectedPiecesData = dissectedPiecesData;
    }

    public void setAssembledPiecesData(String assembledPiecesData) {
        this.assembledPiecesData = assembledPiecesData;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSubcategoryId() {
        return subcategoryId;
    }

    public void setSubcategoryId(int subcategoryId) {
        this.subcategoryId = subcategoryId;
    }

    public int getSubcategoryId(String category, String subcategory) throws SQLException {
        if (subcategory == null || subcategory.isEmpty()) {
            MedatoninDB.debugLog("QuestionDAO", MedatoninDB.LogLevel.WARN, "getSubcategoryId", "Subcategory is null or empty");
            return -1;
        }

        String sql = "SELECT s.id FROM subcategories s JOIN categories c ON s.category_id = c.id WHERE s.name = ? AND c.name = ?";
        MedatoninDB.debugLog("QuestionDAO", MedatoninDB.LogLevel.DEBUG, "getSubcategoryId", "Executing SQL: " + sql);
        MedatoninDB.debugLog("QuestionDAO", MedatoninDB.LogLevel.INFO, "getSubcategoryId", "Category: " + category + ", Subcategory: " + subcategory);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, subcategory);
            stmt.setString(2, category);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    MedatoninDB.debugLog("QuestionDAO", MedatoninDB.LogLevel.DEBUG, "getSubcategoryId", "Found subcategory ID: " + id);
                    return id;
                }
            }
        }
        MedatoninDB.debugLog("QuestionDAO", MedatoninDB.LogLevel.WARN, "getSubcategoryId", "Subcategory not found");
        return -1;
    }

    public int getQuestionId(String category, String subcategory, int questionNumber, Integer simulationId)
            throws SQLException {
        String sql = "SELECT q.id FROM questions q " +
                "JOIN subcategories s ON q.subcategory_id = s.id " +
                "JOIN categories c ON s.category_id = c.id " +
                "WHERE q.question_number = ? AND s.name = ? AND c.name = ? AND q.test_simulation_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, questionNumber);
            stmt.setString(2, subcategory);
            stmt.setString(3, category);
            stmt.setInt(4, simulationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return -1; // Not found
    }

    public int getQuestionNumber() {
        return questionNumber;
    }

    public void setQuestionNumber(int questionNumber) {
        this.questionNumber = questionNumber;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    // Methods to set status
    public void markAsNew() {
        this.isNew = true;
        this.isModified = false;
        this.isDeleted = false;
    }

    public void markAsDeleted() {
        this.isDeleted = true;
        this.isModified = false;
        this.isNew = false;
    }

    public void markAsModified() {
        if (!isNew && !isDeleted) { // Only mark as modified if it's not new or deleted
            this.isModified = true;
        }
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public boolean isModified() {
        return isModified;
    }

    public void setModified(boolean isModified) {
        this.isModified = isModified;
    }

    public void addOption(OptionDAO option) {
        options.add(option);
    }

    public int insertEmptyQuestion(String category, String subcategory, int questionNumber, int simulationId)
            throws SQLException {
        int subcategoryId = getSubcategoryId(category, subcategory);
        if (subcategoryId == -1) {
            throw new SQLException("Subcategory not found.");
        }
        String sql = "INSERT INTO questions (subcategory_id, question_number, text, format, test_simulation_id, difficulty) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, subcategoryId);
            stmt.setInt(2, questionNumber);
            stmt.setString(3, "");
            stmt.setString(4, "Kurz"); // Default format
            stmt.setInt(5, simulationId);
            stmt.setString(6, "MEDIUM");
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating question failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating question failed, no ID obtained.");
                }
            }
        }
    }

    // Insert a new Question
    public int insertQuestion(String category, String subcategory, String questionText, int questionNumber,
            Integer simulationId, Integer passageId) throws SQLException {
        // First, let's get the subcategory_id
        int subcategoryId = getSubcategoryId(category, subcategory);
        if (subcategoryId == -1) {
            throw new SQLException("Subcategory not found for category: " + category + ", subcategory: " + subcategory);
        }

        String sql = "INSERT INTO questions (subcategory_id, question_number, text, format, test_simulation_id, passage_id) VALUES (?, ?, ?, ?, ?, ?)";
        MedatoninDB.debugLog("QuestionDAO", MedatoninDB.LogLevel.DEBUG, "insertQuestion", "Executing SQL: " + sql);
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, subcategoryId);
            stmt.setInt(2, questionNumber);
            stmt.setString(3, questionText);
            stmt.setString(4, "Kurz");
            if (simulationId != null) {
                stmt.setInt(5, simulationId);
            } else {
                stmt.setNull(5, java.sql.Types.INTEGER);
            }
            if (passageId != null) {
                stmt.setInt(6, passageId);
            } else {
                stmt.setNull(6, java.sql.Types.INTEGER);
            }
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating question failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating question failed, no ID obtained.");
                }
            }
        }
    }

    public int insertQuestionWithShape(String category, String subcategory, int questionNumber, String text,
            Integer simulationId, String shapeData, String shapeType, String dissectedPiecesData,
            String assembledPiecesData) throws SQLException {
        String sql = "INSERT INTO questions (subcategory_id, question_number, text, format, test_simulation_id, shape_data, shape_type, dissected_pieces_data, assembled_pieces_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int subcategoryId = getSubcategoryId(category, subcategory);
            stmt.setInt(1, subcategoryId);
            stmt.setInt(2, questionNumber);
            stmt.setString(3, text);
            stmt.setString(4, "Kurz");
            stmt.setInt(5, simulationId != null ? simulationId : 0);
            stmt.setString(6, shapeData);
            stmt.setString(7, shapeType); // Ensure 'shapeType' is available in the method
            stmt.setString(8, dissectedPiecesData);
            stmt.setString(9, assembledPiecesData);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public void updateQuestion(QuestionDAO question) throws SQLException {
        if (question.isModified()) {
            String sql = "UPDATE questions SET subcategory_id = ?, question_number = ?, text = ?, format = ? WHERE id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, question.getSubcategoryId());
                stmt.setInt(2, question.getQuestionNumber());
                stmt.setString(3, question.getText());
                stmt.setString(4, question.getFormat());
                stmt.setInt(5, question.getId());

                stmt.executeUpdate(); // Execute the update
            }
        }
    }

    public void updateQuestionText(String category, String subcategory, int questionNumber, String text,
            Integer simulationId)
            throws SQLException {
        // String sql = "UPDATE questions SET text = ? WHERE question_number = ? AND
        // subcategory_id = (SELECT id FROM subcategories WHERE name = ? AND category_id
        // = (SELECT id FROM categories WHERE name = ?))";
        String sql = "UPDATE questions q " +
                "JOIN subcategories s ON q.subcategory_id = s.id " +
                "JOIN categories c ON s.category_id = c.id " +
                "SET q.text = ? " +
                "WHERE q.question_number = ? AND s.name = ? AND c.name = ? AND q.test_simulation_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, text);
            stmt.setInt(2, questionNumber);
            stmt.setString(3, subcategory);
            stmt.setString(4, category);
            stmt.setInt(5, simulationId); // Scope by test_simulation_id
            stmt.executeUpdate();
        }
    }

    public void updateQuestionFormat(int subcategoryId, int questionNumber, String newFormat) throws SQLException {
        String sql = "UPDATE questions SET format = ? WHERE subcategory_id = ? AND question_number = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newFormat);
            stmt.setInt(2, subcategoryId); // subcategory_id from the correct structure
            stmt.setInt(3, questionNumber); // question_number
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Failed to update question format; no rows affected.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SQLException("Error updating question format", e);
        }
    }

    // In QuestionDAO.java
    public void updateQuestionDifficulty(int subcategoryId, int questionNumber, String difficulty, Integer simulationId)
            throws SQLException {
        String sql = "UPDATE questions SET difficulty = ? WHERE subcategory_id = ? AND question_number = ? AND test_simulation_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, difficulty);
            stmt.setInt(2, subcategoryId);
            stmt.setInt(3, questionNumber);
            stmt.setInt(4, simulationId != null ? simulationId : 0);
            stmt.executeUpdate();
        }
    }

    // getDifficulty Methode hinzufügen
    private String difficulty = "MEDIUM"; // Default

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public boolean deleteQuestion(String category, String subcategory, int questionNumber, Integer simulationId)
            throws SQLException {
        String sql = "DELETE q FROM questions q " +
                "JOIN subcategories s ON q.subcategory_id = s.id " +
                "JOIN categories c ON s.category_id = c.id " +
                "WHERE q.question_number = ? AND s.name = ? AND c.name = ? AND q.test_simulation_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, questionNumber);
            stmt.setString(2, subcategory);
            stmt.setString(3, category);
            stmt.setInt(4, simulationId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }

    // Retrieve questions by subcategory ID
    public List<QuestionDAO> getQuestionsBySubcategory(int subcategoryId) throws SQLException {
        List<QuestionDAO> questions = new ArrayList<>();
        String sql = "SELECT id, subcategory_id, question_number, text, format, test_simulation_id " +
                "FROM questions " +
                "WHERE subcategory_id = ? " +
                "ORDER BY question_number";
        MedatoninDB.debugLog("QuestionDAO", MedatoninDB.LogLevel.DEBUG, "getQuestionsBySubcategory", "Executing SQL: " + sql);
        MedatoninDB.debugLog("QuestionDAO", MedatoninDB.LogLevel.INFO, "getQuestionsBySubcategory", "Subcategory ID: " + subcategoryId);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, subcategoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    QuestionDAO question = new QuestionDAO(
                            rs.getInt("id"),
                            rs.getInt("subcategory_id"),
                            rs.getInt("question_number"),
                            rs.getString("text"),
                            rs.getString("format"),
                            rs.getInt("test_simulation_id"));
                    questions.add(question);
                    MedatoninDB.debugLog("QuestionDAO", MedatoninDB.LogLevel.DEBUG, "getQuestionsBySubcategory", "Loaded question: ID=" + question.getId() + ", Number="
                            + question.getQuestionNumber() + ", Text=" + question.getText());
                }
            }
        }
        MedatoninDB.debugLog("QuestionDAO", MedatoninDB.LogLevel.INFO, "getQuestionsBySubcategory", "Loaded " + questions.size() + " questions for subcategory ID: " + subcategoryId);
        return questions;
    }

    public int getNextQuestionNumber(Integer simulationId, int subcategoryId) throws SQLException {
        String query;
        if (simulationId == null) {
            // For main pool questions
            query = "SELECT MAX(question_number) FROM questions WHERE test_simulation_id IS NULL AND subcategory_id = ?";
        } else {
            // For test simulation questions
            query = "SELECT MAX(question_number) FROM questions WHERE test_simulation_id = ? AND subcategory_id = ?";
        }

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            if (simulationId != null) {
                stmt.setInt(1, simulationId);
                stmt.setInt(2, subcategoryId);
            } else {
                stmt.setInt(1, subcategoryId);
            }

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) + 1; // Increment the maximum question number by 1
            } else {
                return 1; // Start numbering from 1 if no questions are found
            }
        }
    }

    public void updateQuestionNumber(String category, String subcategory, int oldQuestionNumber, int newQuestionNumber)
            throws SQLException {
        String sql = "UPDATE questions q JOIN subcategories s ON q.subcategory_id = s.id JOIN categories c ON s.category_id = c.id "
                +
                "SET q.question_number = ? " +
                "WHERE q.question_number = ? AND s.name = ? AND c.name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, newQuestionNumber);
            stmt.setInt(2, oldQuestionNumber);
            stmt.setString(3, subcategory);
            stmt.setString(4, category);
            stmt.executeUpdate();
        }
    }

    // Load questions by test simulation
    public List<QuestionDAO> getQuestionsBySubcategoryAndSimulation(int subcategoryId, Integer simulationId)
            throws SQLException {
        List<QuestionDAO> questions = new ArrayList<>();

        String sql;

        String BASE_COLS = "q.id, q.subcategory_id, q.question_number, q.text, q.format, " +
                "q.test_simulation_id, q.difficulty, " + // für spätere Auswertungen
                "q.shape_data, q.shape_type, " + // Figuren-spezifisch
                "q.dissected_pieces_data, q.assembled_pieces_data ";

        if (simulationId == null) {
            // For the "Haupt-Datenbank", get all questions and apply ROW_NUMBER()
            sql = "SELECT " + BASE_COLS + ", " +
                    "ROW_NUMBER() OVER (PARTITION BY q.subcategory_id ORDER BY q.id) AS row_num " +
                    "FROM questions q WHERE q.subcategory_id = ? " +
                    "ORDER BY row_num";
        } else {
            sql = "SELECT " + BASE_COLS
                    + "FROM questions q WHERE q.subcategory_id = ? AND q.test_simulation_id = ? ORDER BY question_number";
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, subcategoryId);
            if (simulationId != null) {
                stmt.setInt(2, simulationId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    QuestionDAO question = new QuestionDAO(conn);
                    question.setId(rs.getInt("id"));
                    question.setSubcategoryId(subcategoryId);
                    question.setText(rs.getString("text"));
                    question.setFormat(rs.getString("format"));
                    question.setDifficulty(rs.getString("difficulty"));

                    // Handle question_number differently for Haupt-Datenbank and simulations
                    if (simulationId == null) {
                        question.setQuestionNumber(rs.getInt("row_num")); // Use row_num for Haupt-Datenbank
                    } else {
                        question.setQuestionNumber(rs.getInt("question_number")); // Use question_number for simulations
                    }
                    question.setShapeData(rs.getString("shape_data"));
                    question.setShapeType(rs.getString("shape_type"));
                    question.setDissectedPiecesData(rs.getString("dissected_pieces_data"));
                    question.setAssembledPiecesData(rs.getString("assembled_pieces_data"));
                    questions.add(question);
                }
            }
        }
        return questions;
    }

    // Insert new question for a specific simulation
    public void addQuestionForSimulation(String questionText, int simulationId, int subcategoryId) throws SQLException {
        String query = "INSERT INTO questions (question_text, test_simulation_id, subcategory_id) VALUES (?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, questionText);
        stmt.setInt(2, simulationId);
        stmt.setInt(3, subcategoryId);
        stmt.executeUpdate();
    }

}
