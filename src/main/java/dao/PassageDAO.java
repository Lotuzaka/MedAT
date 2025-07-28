package dao;

import java.sql.*;

/** DAO for CRUD operations on passages. */
public class PassageDAO {
    private final Connection conn;

    public PassageDAO(Connection conn) {
        this.conn = conn;
    }

    /**
     * Inserts a passage and returns the generated id.
     */
    public int insert(int subcategoryId, String text, String source) throws SQLException {
        String sql = "INSERT INTO passages (subcategory_id, text, source) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, subcategoryId);
            ps.setString(2, text);
            if (source != null) {
                ps.setString(3, source);
            } else {
                ps.setNull(3, Types.VARCHAR);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to insert passage");
    }

    /**
     * Updates an existing passage.
     */
    public void update(int id, String text, String source) throws SQLException {
        String sql = "UPDATE passages SET text = ?, source = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, text);
            if (source != null) {
                ps.setString(2, source);
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    /**
     * Loads a passage by its identifier.
     */
    public Passage findById(int id) throws SQLException {
        String sql = "SELECT id, subcategory_id, text, source FROM passages WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Passage(
                            rs.getInt("id"),
                            rs.getInt("subcategory_id"),
                            rs.getString("text"),
                            rs.getString("source"));
                }
            }
        }
        return null;
    }

    /**
     * Deletes a passage.
     */
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM passages WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /** Simple record representing a passage. */
    public record Passage(int id, int subcategoryId, String text, String source) {}
}
