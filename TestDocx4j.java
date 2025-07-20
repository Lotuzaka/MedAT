public class TestDocx4j {
    public static void main(String[] args) {
        try {
            // Test if docx4j classes are available
            Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage");
            System.out.println("SUCCESS: docx4j classes are available!");
            
            // Test creating a package
            java.lang.reflect.Method createMethod = Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage")
                .getMethod("createPackage");
            Object pkg = createMethod.invoke(null);
            System.out.println("SUCCESS: WordprocessingMLPackage created successfully!");
            
            System.out.println("docx4j dependencies are now working correctly.");
            System.out.println("You can now use the print functionality in MedatoninDB.");
            
        } catch (ClassNotFoundException e) {
            System.err.println("ERROR: docx4j classes not found: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
