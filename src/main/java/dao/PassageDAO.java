package dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** DAO for CRUD operations on passages. */
public class PassageDAO {
    private final Connection conn;

    public PassageDAO(Connection conn) {
        this.conn = conn;
    }

    /**
     * Inserts a passage and returns the generated id.
     */
    public int insert(int subcategoryId, int testSimulationId, String text, String source) throws SQLException {
        String sql = "INSERT INTO passages (subcategory_id, test_simulation_id, text, source) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, subcategoryId);
            ps.setInt(2, testSimulationId);
            ps.setString(3, text);
            if (source != null) {
                ps.setString(4, source);
            } else {
                ps.setNull(4, Types.VARCHAR);
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
     * Inserts a passage without test_simulation_id (for backward compatibility)
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
     * Updates an existing passage including test_simulation_id.
     */
    public void update(int id, int testSimulationId, String text, String source) throws SQLException {
        String sql = "UPDATE passages SET test_simulation_id = ?, text = ?, source = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, testSimulationId);
            ps.setString(2, text);
            if (source != null) {
                ps.setString(3, source);
            } else {
                ps.setNull(3, Types.VARCHAR);
            }
            ps.setInt(4, id);
            ps.executeUpdate();
        }
    }

    /**
     * Updates an existing passage (backward compatibility).
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
        String sql = "SELECT id, subcategory_id, test_simulation_id, text, source FROM passages WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Passage(
                            rs.getInt("id"),
                            rs.getInt("subcategory_id"),
                            rs.getObject("test_simulation_id", Integer.class), // Can be null
                            rs.getString("text"),
                            rs.getString("source"));
                }
            }
        }
        return null;
    }

    /**
     * Loads the passage for a given subcategory. Returns {@code null} if none exists.
     */
    public Passage findBySubcategoryId(int subcategoryId) throws SQLException {
        String sql = "SELECT id, subcategory_id, test_simulation_id, text, source FROM passages WHERE subcategory_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subcategoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Passage(
                            rs.getInt("id"),
                            rs.getInt("subcategory_id"),
                            rs.getObject("test_simulation_id", Integer.class), // Can be null
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

    /**
     * Finds all passages for a specific test simulation.
     */
    public List<Passage> findByTestSimulation(int testSimulationId) throws SQLException {
        String sql = "SELECT id, subcategory_id, test_simulation_id, text, source FROM passages WHERE test_simulation_id = ?";
        List<Passage> passages = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, testSimulationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    passages.add(new Passage(
                            rs.getInt("id"),
                            rs.getInt("subcategory_id"),
                            rs.getObject("test_simulation_id", Integer.class),
                            rs.getString("text"),
                            rs.getString("source")));
                }
            }
        }
        return passages;
    }

    /**
     * Finds passages for a specific subcategory and test simulation.
     */
    public List<Passage> findBySubcategoryAndSimulation(int subcategoryId, int testSimulationId) throws SQLException {
        String sql = "SELECT id, subcategory_id, test_simulation_id, text, source FROM passages WHERE subcategory_id = ? AND test_simulation_id = ?";
        List<Passage> passages = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subcategoryId);
            ps.setInt(2, testSimulationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    passages.add(new Passage(
                            rs.getInt("id"),
                            rs.getInt("subcategory_id"),
                            rs.getObject("test_simulation_id", Integer.class),
                            rs.getString("text"),
                            rs.getString("source")));
                }
            }
        }
        return passages;
    }

    /** Simple record representing a passage. */
    public record Passage(int id, int subcategoryId, Integer testSimulationId, String text, String source) {}
}
