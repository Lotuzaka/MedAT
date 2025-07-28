import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.Map;

public class test_template_engine {
    public static void main(String[] args) {
        try {
            // Load the intro_content.json file
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> introContent = mapper.readValue(
                new File("src/main/resources/intro_content.json"), 
                Map.class
            );
            
            // Get the memory recall data
            Map<String, Object> memoryRecallData = (Map<String, Object>) introContent.get("Gedächtnis und Merkfähigkeit (Abrufphase)");
            
            // Create template engine
            IntroTemplateEngine engine = new IntroTemplateEngine();
            
            // Process the memory recall template
            String template = "memory_recall";
            String processedContent = engine.processTemplate(template, memoryRecallData);
            
            System.out.println("=== PROCESSED MEMORY RECALL TEMPLATE ===");
            System.out.println(processedContent);
            System.out.println("==========================================");
            
            // Check if example question and answers are included
            boolean hasExampleQuestion = processedContent.contains("Welche Blutgruppe hat die Person");
            boolean hasAnswerOptions = processedContent.contains("A)") && processedContent.contains("B)");
            boolean hasAnswerKey = processedContent.contains("Lösung:");
            
            System.out.println("Template processing verification:");
            System.out.println("✓ Has example question: " + hasExampleQuestion);
            System.out.println("✓ Has answer options: " + hasAnswerOptions);
            System.out.println("✓ Has answer key: " + hasAnswerKey);
            
            if (hasExampleQuestion && hasAnswerOptions && hasAnswerKey) {
                System.out.println("\n🎉 SUCCESS: Memory recall template now properly includes example questions and answers!");
            } else {
                System.out.println("\n❌ ISSUE: Some elements are still missing from the template processing.");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
