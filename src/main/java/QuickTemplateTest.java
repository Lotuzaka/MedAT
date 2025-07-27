/**
 * Quick test to see what content the template engine generates
 */
public class QuickTemplateTest {
    public static void main(String[] args) {
        try {
            IntroTemplateEngine templateEngine = new IntroTemplateEngine();
            
            String[] testCases = {"Zahlenfolgen", "Wortflüssigkeit", "Implikationen erkennen"};
            
            for (String subcategory : testCases) {
                System.out.println("=== " + subcategory + " ===");
                String content = templateEngine.generateIntroContent(subcategory);
                if (content != null) {
                    System.out.println(content);
                    System.out.println("\n--- Checking for box content indicators ---");
                    String[] lines = content.split("\n");
                    boolean hasBoxContent = false;
                    for (String line : lines) {
                        line = line.trim();
                        if (line.startsWith("Die folgenden Aufgaben") || 
                            line.startsWith("In diesem Untertest") ||
                            line.startsWith("Die Aufgaben sind im Single-Choice") ||
                            line.startsWith("Bitte markieren Sie") ||
                            line.startsWith("Das Zurückblättern") ||
                            line.startsWith("Sie dürfen mit der Bearbeitung")) {
                            System.out.println("✓ Found box content: " + line.substring(0, Math.min(50, line.length())) + "...");
                            hasBoxContent = true;
                        }
                    }
                    if (!hasBoxContent) {
                        System.out.println("✗ No box content found!");
                    }
                } else {
                    System.out.println("✗ No content generated!");
                }
                System.out.println("\n" + "=".repeat(50) + "\n");
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
