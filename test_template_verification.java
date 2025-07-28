import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class test_template_verification {
    public static void main(String[] args) {
        try {
            // Read the intro_templates.txt file
            BufferedReader reader = new BufferedReader(new FileReader("src/main/resources/intro_templates.txt"));
            StringBuilder content = new StringBuilder();
            String line;
            boolean inMemoryRecall = false;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains("+++TEMPLATE:memory_recall+++")) {
                    inMemoryRecall = true;
                }
                if (inMemoryRecall) {
                    content.append(line).append("\n");
                }
                if (line.contains("+++TEMPLATE_END+++") && inMemoryRecall) {
                    break;
                }
            }
            reader.close();
            
            String memoryRecallTemplate = content.toString();
            System.out.println("=== MEMORY RECALL TEMPLATE CONTENT ===");
            System.out.println(memoryRecallTemplate);
            System.out.println("======================================");
            
            // Verify that required placeholders are present
            boolean hasExampleSection = memoryRecallTemplate.contains("[EXAMPLE_SECTION]");
            boolean hasAnswerKey = memoryRecallTemplate.contains("[ANSWER_KEY]");
            
            System.out.println("Template verification:");
            System.out.println("✓ Has [EXAMPLE_SECTION]: " + hasExampleSection);
            System.out.println("✓ Has [ANSWER_KEY]: " + hasAnswerKey);
            
            if (hasExampleSection && hasAnswerKey) {
                System.out.println("\n🎉 SUCCESS: Memory recall template now includes the necessary placeholders for example questions and answers!");
            } else {
                System.out.println("\n❌ ISSUE: Template is still missing required placeholders.");
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
