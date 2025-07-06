import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.sql.ResultSet;

public class OptionDAO {
    private int id;
    private int questionId;
    private String label;
    private String text;
    private String shapeData;
    private boolean isCorrect;
    private boolean isNew;
    private boolean isDeleted;
    private boolean isModified;

    private Connection conn;

    public OptionDAO(Connection conn) {
        this.conn = conn;
    }

    public OptionDAO(int id, int questionId, String label, String text, boolean isCorrect) {
        this.id = id;
        this.questionId = questionId;
        this.label = label;
        this.text = text;
        this.isCorrect = isCorrect;
        this.isNew = false;
        this.isDeleted = false;
        this.isModified = false;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getQuestionId() {
        return questionId;
    }

    public void setQuestionId(int questionId) {
        this.questionId = questionId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isCorrect() {
        return isCorrect;
    }

    public void setCorrect(boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public String getShapeData() {
        return shapeData;
    }

    public void setShapeData(String shapeData) {
        this.shapeData = shapeData;
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

    // Insert a new Option
    public void insertOption(int questionId, String label, String optionText, boolean isCorrect) throws SQLException {
        String sql = "INSERT INTO options (question_id, label, text, is_correct) VALUES (?, ?, ?, ?)";
        System.out.println("Executing SQL: " + sql);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, questionId);
            stmt.setString(2, label);
            stmt.setString(3, optionText);
            stmt.setBoolean(4, isCorrect);
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating option failed, no rows affected.");
            }
        }
    }

    // Update an existing Option
    public void updateOption(OptionDAO option) throws SQLException {
        if (option.isModified()) {
            String sql = "UPDATE options SET question_id = ?, label = ?, text = ?, is_correct = ? WHERE id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, option.getQuestionId());
                stmt.setString(2, option.getLabel());
                stmt.setString(3, option.getText());
                stmt.setBoolean(4, option.isCorrect());
                stmt.setInt(5, option.getId());

                stmt.executeUpdate(); // Execute the update
            }
        }
    }

    // Delete an Option
    public void deleteOption(int questionId, String optionLabel) throws SQLException {
        String sql = "DELETE FROM options WHERE question_id = ? AND label = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, questionId);
            pstmt.setString(2, optionLabel);
            pstmt.executeUpdate();
        }
    }

    public void addOption(int questionId, String optionLabel, String optionText, boolean isCorrect)
            throws SQLException {
        String sql = "INSERT INTO options (question_id, label, text, is_correct, shape_data) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, questionId);
            pstmt.setString(2, optionLabel);
            pstmt.setString(3, optionText);
            pstmt.setBoolean(4, isCorrect);
            pstmt.setString(5, null);
            pstmt.executeUpdate();
        }
    }

    // Retrieve an Option by ID
    public OptionDAO getOptionById(int id) throws SQLException {
        String sql = "SELECT * FROM options WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new OptionDAO(
                            rs.getInt("id"),
                            rs.getInt("question_id"),
                            rs.getString("label"),
                            rs.getString("text"),
                            rs.getBoolean("is_correct"));
                }
            }
        }
        return null; // If no option is found
    }

    public List<OptionDAO> getOptionsByQuestionId(int questionId) throws SQLException {
        List<OptionDAO> options = new ArrayList<>();
        String sql = "SELECT * FROM options WHERE question_id = ? ORDER BY id";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, questionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    OptionDAO option = new OptionDAO(
                            rs.getInt("id"),
                            rs.getInt("question_id"),
                            rs.getString("label"),
                            rs.getString("text"),
                            rs.getBoolean("is_correct"));
                    options.add(option);
                }
            }
        }
        return options;
    }

    public List<OptionDAO> getOptionsForQuestion(int questionId) throws SQLException {
        List<OptionDAO> options = new ArrayList<>();
        String sql = "SELECT * FROM options WHERE question_id = ? ORDER BY FIELD(label, '1.', '2.', '3.', '4.', 'A', 'B', 'C', 'D', 'E')";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, questionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    OptionDAO option = new OptionDAO(conn);
                    option.setId(rs.getInt("id"));
                    option.setQuestionId(rs.getInt("question_id"));
                    option.setLabel(rs.getString("label"));
                    option.setText(rs.getString("text"));
                    option.setCorrect(rs.getBoolean("is_correct"));
                    option.setShapeData(rs.getString("shape_data")); // Ensure this is fetched

                    options.add(option);
                }
            }
        }
        return options;
    }

    public void addOptionWithShape(int questionId, String label, String shapeWKT, boolean isCorrect)
            throws SQLException {
        String sql = "INSERT INTO options (question_id, label, text, is_correct, shape_data) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, questionId);
            stmt.setString(2, label);
            stmt.setString(3, ""); // No text for shape options
            stmt.setBoolean(4, isCorrect);
            stmt.setString(5, shapeWKT);
            stmt.executeUpdate();
        }
    }

    public void updateOptionText(int questionId, Object optionLabel, String optionText, Integer simulationId)
            throws SQLException {
        String sql = "UPDATE options o " +
                "JOIN questions q ON o.question_id = q.id " +
                "SET o.text = ? " +
                "WHERE o.label = ? AND o.question_id = ? AND q.test_simulation_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, optionText);
            // Handle both String and Integer types for label
            if (optionLabel instanceof String) {
                pstmt.setString(2, (String) optionLabel);
            } else if (optionLabel instanceof Integer) {
                pstmt.setString(2, String.valueOf(optionLabel));
            } else {
                throw new IllegalArgumentException("Unexpected type for label: " + label.getClass().getName());
            }
            pstmt.setInt(3, questionId);
            pstmt.setObject(4, simulationId);

            System.out.println("Option Text SQL: " + pstmt);
            System.out.println("execute option:" + pstmt.executeUpdate());
            pstmt.executeUpdate();
        }
    }

    public void updateOptionCorrectness(int questionId, Object optionLabel, Boolean isCorrect, Integer simulationId)
            throws SQLException {
        String sql = "UPDATE options o " +
                "JOIN questions q ON o.question_id = q.id " +
                "SET o.is_correct = ? " +
                "WHERE o.label = ? AND o.question_id = ? AND q.test_simulation_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, isCorrect);
            // Handle both String and Integer types for label
            if (optionLabel instanceof String) {
                stmt.setString(2, (String) optionLabel);
            } else if (optionLabel instanceof Integer) {
                stmt.setString(2, String.valueOf(optionLabel));
            } else {
                throw new IllegalArgumentException("Unexpected type for label: " + optionLabel.getClass().getName());
            }

            stmt.setInt(3, questionId);
            stmt.setInt(4, simulationId);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Failed to update option correctness; no rows affected.");
            }
        }
    }

    public boolean optionExists(int questionId, String label) throws SQLException {
        String sql = "SELECT COUNT(*) FROM options WHERE question_id = ? AND label = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, questionId);
            stmt.setString(2, label);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    return count > 0;
                }
            }
        }
        return false;
    }

    public void insertEmptyOption(int questionId, String label) throws SQLException {
        String sql = "INSERT INTO options (question_id, label, text, is_correct) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, questionId);
            stmt.setString(2, label);
            stmt.setString(3, ""); // Empty option text
            stmt.setBoolean(4, false); // Default to not correct
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating option failed, no rows affected.");
            }
        }
    }
}
