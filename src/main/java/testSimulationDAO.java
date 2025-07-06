import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class testSimulationDAO {
    private int id;
    private String name;
    private Timestamp createdAt;
    private Connection conn;

    public testSimulationDAO() {

    }

    public testSimulationDAO(Connection conn) {
        this.conn = conn;
    }

    public testSimulationDAO(int id, String name, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public testSimulationDAO createSimulation(String name) throws SQLException {
        String sql = "INSERT INTO test_simulations (name) VALUES (?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Creating simulation failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    return new testSimulationDAO(id, name, createdAt);
                } else {
                    throw new SQLException("Creating simulation failed, no ID obtained.");
                }
            }
        }
    }

    // Get all simulations
    public List<testSimulationDAO> getAllSimulations() throws SQLException {
        String query = "SELECT * FROM test_simulations";
        PreparedStatement stmt = conn.prepareStatement(query);
        ResultSet rs = stmt.executeQuery();
        
        List<testSimulationDAO> simulations = new ArrayList<>();
        while (rs.next()) {
            testSimulationDAO sim = new testSimulationDAO();
            sim.setId(rs.getInt("id"));
            sim.setName(rs.getString("name"));
            sim.setCreatedAt(rs.getTimestamp("created_at"));
            simulations.add(sim);
        }
        return simulations;
    }
}
