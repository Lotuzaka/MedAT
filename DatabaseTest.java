import java.sql.*;

public class DatabaseTest {
    public static void main(String[] args) {
        try {
            // Test database connection
            String url = "jdbc:mysql://localhost:3306/medatoninDB?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            String username = "root";
            String password = "123";
            
            Connection conn = DriverManager.getConnection(url, username, password);
            System.out.println("✓ Database connection successful");
            
            // Test if allergy_card table exists
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(null, null, "allergy_card", null);
            if (tables.next()) {
                System.out.println("✓ allergy_card table exists");
                
                // Show table structure
                ResultSet columns = metaData.getColumns(null, null, "allergy_card", null);
                System.out.println("Table structure:");
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    String dataType = columns.getString("TYPE_NAME");
                    System.out.println("  - " + columnName + " (" + dataType + ")");
                }
                columns.close();
            } else {
                System.out.println("✗ allergy_card table does NOT exist");
            }
            tables.close();
            
            conn.close();
            System.out.println("✓ Test completed successfully");
        } catch (Exception e) {
            System.out.println("✗ Database test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
