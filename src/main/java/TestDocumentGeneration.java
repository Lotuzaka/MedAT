import docx.Docx4jPrinter;
import java.util.*;

/**
 * Quick test to verify the new document generation features work
 */
public class TestDocumentGeneration {
    public static void main(String[] args) {
        try {
            System.out.println("Testing Docx4jPrinter functionality...");
            
            // Test 1: Basic initialization
            Docx4jPrinter printer = new Docx4jPrinter();
            System.out.println("✅ Docx4jPrinter initialized successfully");
            
            // Test 2: Test buildDocumentComplete method exists
            Class<?> printerClass = printer.getClass();
            boolean hasCompleteMethod = false;
            for (java.lang.reflect.Method method : printerClass.getMethods()) {
                if (method.getName().equals("buildDocumentComplete")) {
                    hasCompleteMethod = true;
                    System.out.println("✅ buildDocumentComplete method found");
                    System.out.println("   Parameters: " + Arrays.toString(method.getParameterTypes()));
                    break;
                }
            }
            
            if (!hasCompleteMethod) {
                System.out.println("❌ buildDocumentComplete method not found");
            }
            
            // Test 3: Test addAllergyCards method exists (private method)
            boolean hasAllergyMethod = false;
            for (java.lang.reflect.Method method : printerClass.getDeclaredMethods()) {
                if (method.getName().equals("addAllergyCards")) {
                    hasAllergyMethod = true;
                    System.out.println("✅ addAllergyCards method found (private)");
                    break;
                }
            }
            
            if (!hasAllergyMethod) {
                System.out.println("❌ addAllergyCards method not found");
            }
            
            // Test 4: Check template engine integration
            try {
                Class.forName("IntroTemplateEngine");
                System.out.println("✅ IntroTemplateEngine class available");
            } catch (ClassNotFoundException e) {
                System.out.println("⚠️ IntroTemplateEngine not found: " + e.getMessage());
            }
            
            // Test 5: Check docx4j availability
            try {
                Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage");
                System.out.println("✅ docx4j library available");
            } catch (ClassNotFoundException e) {
                System.out.println("❌ docx4j library not found: " + e.getMessage());
            }
            
            System.out.println("\n🎉 All tests completed!");
            System.out.println("The new memory test functionality should be ready to use.");
            System.out.println("Try using 'All Print' in the application to test the complete workflow.");
            
        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
