import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CategoryDAO {
    private int id;
    private String name;
    private int orderIndex;
    private boolean isNew;
    private boolean isDeleted;
    private boolean isModified;
    
    private Connection conn;

    public CategoryDAO(int id, String name, int orderIndex) {
        this.id = id;
        this.name = name;
        this.orderIndex = orderIndex;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
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

    
    public CategoryDAO(Connection conn) {
        this.conn = conn;
    }

    // Insert a new Category
    public void insertCategory(CategoryDAO category) throws SQLException {
        String sql = "INSERT INTO categories (name, order_index) VALUES (?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category.getName());
            stmt.setInt(2, category.getOrderIndex());

            stmt.executeUpdate(); // Execute the insertion
        }
    }

    // Update an existing Category
    public void updateCategory(CategoryDAO category) throws SQLException {
        if (category.isModified()) {
            String sql = "UPDATE categories SET name = ?, order_index = ? WHERE id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, category.getName());
                stmt.setInt(2, category.getOrderIndex());
                stmt.setInt(3, category.getId());

                stmt.executeUpdate(); // Execute the update
            }
        }
    }

    // Delete a Category
    public void deleteCategory(CategoryDAO category) throws SQLException {
        if (category.isDeleted()) {
            String sql = "DELETE FROM categories WHERE id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, category.getId());

                stmt.executeUpdate(); // Execute the deletion
            }
        }
    }

    // Retrieve a Category by ID
    public CategoryDAO getCategoryById(int id) throws SQLException {
        String sql = "SELECT * FROM categories WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new CategoryDAO(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("order_index")
                    );
                }
            }
        }
        return null; // If no category is found
    }

    // Retrieve a Category by ID
    public int getIdByCategory(String categoryName) throws SQLException {
        int categoryId = 0;
        String sql = "SELECT id FROM categories WHERE name = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, categoryName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    categoryId = rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return categoryId; // If no category is found
    }
}
