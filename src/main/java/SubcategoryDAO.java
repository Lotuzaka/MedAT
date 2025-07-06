import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SubcategoryDAO {
    private int id;
    private int categoryId;
    private String name;
    private int orderIndex;
    private boolean isNew;
    private boolean isDeleted;
    private boolean isModified;
    
    private Connection conn;

    public SubcategoryDAO(int id, int categoryId, String name, int orderIndex) {
        this.id = id;
        this.categoryId = categoryId;
        this.name = name;
        this.orderIndex = orderIndex;
        this.isNew = false;
        this.isDeleted = false;
        this.isModified = false;
    }

    public int getId() {
        return id;
    }

    public Integer getIdInteger() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
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

    public SubcategoryDAO(Connection conn) {
        this.conn = conn;
    }

    // Insert a new Subcategory
    public void insertSubcategory(SubcategoryDAO subcategory) throws SQLException {
        String sql = "INSERT INTO subcategories (category_id, name, order_index) VALUES (?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, subcategory.getCategoryId());
            stmt.setString(2, subcategory.getName());
            stmt.setInt(3, subcategory.getOrderIndex());

            stmt.executeUpdate(); // Execute the insertion
        }
    }

    // Update an existing Subcategory
    public void updateSubcategory(SubcategoryDAO subcategory) throws SQLException {
        if (subcategory.isModified()) {
            String sql = "UPDATE subcategories SET category_id = ?, name = ?, order_index = ? WHERE id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, subcategory.getCategoryId());
                stmt.setString(2, subcategory.getName());
                stmt.setInt(3, subcategory.getOrderIndex());
                stmt.setInt(4, subcategory.getId());

                stmt.executeUpdate(); // Execute the update
            }
        }
    }

    // Delete a Subcategory
    public void deleteSubcategory(SubcategoryDAO subcategory) throws SQLException {
        if (subcategory.isDeleted()) {
            String sql = "DELETE FROM subcategories WHERE id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, subcategory.getId());

                stmt.executeUpdate(); // Execute the deletion
            }
        }
    }

    // Retrieve a Subcategory by ID
    public SubcategoryDAO getSubcategoryById(int id) throws SQLException {
        String sql = "SELECT * FROM subcategories WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new SubcategoryDAO(
                        rs.getInt("id"),
                        rs.getInt("category_id"),
                        rs.getString("name"),
                        rs.getInt("order_index")
                    );
                }
            }
        }
        return null; // If no subcategory is found
    }
    
}