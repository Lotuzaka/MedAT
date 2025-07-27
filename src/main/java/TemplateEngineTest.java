/**
 * Simple test to verify the IntroTemplateEngine works correctly
 */
public class TemplateEngineTest {
    public static void main(String[] args) {
        try {
            IntroTemplateEngine templateEngine = new IntroTemplateEngine();
            
            // Test the three subcategories mentioned by the user
            String[] testSubcategories = {
                "Zahlenfolgen",
                "Wortflüssigkeit", 
                "Implikationen erkennen"
            };
            
            System.out.println("=== IntroTemplateEngine Test Results ===");
            System.out.println();
            
            for (String subcategory : testSubcategories) {
                System.out.println("Testing subcategory: " + subcategory);
                System.out.println("Has content: " + templateEngine.hasContentFor(subcategory));
                
                String content = templateEngine.generateIntroContent(subcategory);
                if (content != null && !content.trim().isEmpty()) {
                    System.out.println("Content generated successfully!");
                    System.out.println("Content length: " + content.length() + " characters");
                    System.out.println("Content preview (first 100 chars): " + 
                        content.substring(0, Math.min(100, content.length())) + "...");
                } else {
                    System.out.println("ERROR: No content generated!");
                }
                System.out.println("---");
            }
            
            // Show all available subcategories
            System.out.println("Available subcategories in template engine:");
            for (String subcategory : templateEngine.getAvailableSubcategories()) {
                System.out.println("- " + subcategory);
            }
            
        } catch (Exception e) {
            System.err.println("Test failed with error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
