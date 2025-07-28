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
    public int insert(int subcategoryId, int testSimulationId, int passageIndex, String text, String source) throws SQLException {
        String sql = "INSERT INTO passages (subcategory_id, test_simulation_id, passage_index, text, source) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, subcategoryId);
            ps.setInt(2, testSimulationId);
            ps.setInt(3, passageIndex);
            ps.setString(4, text);
            if (source != null) {
                ps.setString(5, source);
            } else {
                ps.setNull(5, Types.VARCHAR);
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
        return insert(subcategoryId, 0, 1, text, source);
    }

    /**
     * Updates an existing passage including test_simulation_id.
     */
    public void update(int id, int testSimulationId, int passageIndex, String text, String source) throws SQLException {
        String sql = "UPDATE passages SET test_simulation_id = ?, passage_index = ?, text = ?, source = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, testSimulationId);
            ps.setInt(2, passageIndex);
            ps.setString(3, text);
            if (source != null) {
                ps.setString(4, source);
            } else {
                ps.setNull(4, Types.VARCHAR);
            }
            ps.setInt(5, id);
            ps.executeUpdate();
        }
    }

    /**
     * Updates an existing passage (backward compatibility).
     */
    public void update(int id, String text, String source) throws SQLException {
        update(id, 0, 1, text, source);
    }

    /**
     * Loads a passage by its identifier.
     */
    public Passage findById(int id) throws SQLException {
        String sql = "SELECT id, subcategory_id, test_simulation_id, passage_index, text, source FROM passages WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Passage(
                            rs.getInt("id"),
                            rs.getInt("subcategory_id"),
                            rs.getObject("test_simulation_id", Integer.class), // Can be null
                            rs.getInt("passage_index"),
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
        String sql = "SELECT id, subcategory_id, test_simulation_id, passage_index, text, source FROM passages WHERE subcategory_id = ? AND passage_index = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subcategoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Passage(
                            rs.getInt("id"),
                            rs.getInt("subcategory_id"),
                            rs.getObject("test_simulation_id", Integer.class), // Can be null
                            rs.getInt("passage_index"),
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
        String sql = "SELECT id, subcategory_id, test_simulation_id, passage_index, text, source FROM passages WHERE test_simulation_id = ? ORDER BY passage_index";
        List<Passage> passages = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, testSimulationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    passages.add(new Passage(
                            rs.getInt("id"),
                            rs.getInt("subcategory_id"),
                            rs.getObject("test_simulation_id", Integer.class),
                            rs.getInt("passage_index"),
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
        String sql = "SELECT id, subcategory_id, test_simulation_id, passage_index, text, source FROM passages WHERE subcategory_id = ? AND test_simulation_id = ? ORDER BY passage_index";
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
                            rs.getInt("passage_index"),
                            rs.getString("text"),
                            rs.getString("source")));
                }
            }
        }
        return passages;
    }

    /**
     * Finds a single passage by subcategory, simulation and index.
     */
    public Passage findBySubcategorySimulationAndIndex(int subcategoryId, int simulationId, int passageIndex) throws SQLException {
        String sql = "SELECT id, subcategory_id, test_simulation_id, passage_index, text, source FROM passages WHERE subcategory_id = ? AND test_simulation_id = ? AND passage_index = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subcategoryId);
            ps.setInt(2, simulationId);
            ps.setInt(3, passageIndex);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Passage(
                            rs.getInt("id"),
                            rs.getInt("subcategory_id"),
                            rs.getObject("test_simulation_id", Integer.class),
                            rs.getInt("passage_index"),
                            rs.getString("text"),
                            rs.getString("source"));
                }
            }
        }
        return null;
    }

    /**
     * Finds a single passage by subcategory and index (main question pool).
     */
    public Passage findBySubcategoryAndIndex(int subcategoryId, int passageIndex) throws SQLException {
        String sql = "SELECT id, subcategory_id, test_simulation_id, passage_index, text, source FROM passages WHERE subcategory_id = ? AND test_simulation_id IS NULL AND passage_index = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subcategoryId);
            ps.setInt(2, passageIndex);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Passage(
                            rs.getInt("id"),
                            rs.getInt("subcategory_id"),
                            rs.getObject("test_simulation_id", Integer.class),
                            rs.getInt("passage_index"),
                            rs.getString("text"),
                            rs.getString("source"));
                }
            }
        }
        return null;
    }

    /** Simple record representing a passage. */
    public record Passage(int id, int subcategoryId, Integer testSimulationId, int passageIndex, String text, String source) {}
}
