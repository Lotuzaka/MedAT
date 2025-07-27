import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Template engine for generating introduction content using templates and data files.
 * Supports multiple template types and flexible content substitution.
 */
public class IntroTemplateEngine {
    
    private Map<String, String> templates;
    private Map<String, JsonNode> contentData;
    private static final Pattern HANDLER_PATTERN = Pattern.compile("\\[([A-Z_]+)\\]");
    
    public IntroTemplateEngine() {
        this.templates = new HashMap<>();
        this.contentData = new HashMap<>();
        loadTemplates();
        loadContentData();
    }
    
    /**
     * Load templates from the templates file
     */
    private void loadTemplates() {
        try (InputStream is = getClass().getResourceAsStream("/intro_templates.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            
            parseTemplates(content.toString());
            
        } catch (IOException e) {
            System.err.println("Error loading templates: " + e.getMessage());
            // Fallback to default template
            templates.put("default", createDefaultTemplate());
        }
    }
    
    /**
     * Parse template content and extract individual templates
     */
    private void parseTemplates(String content) {
        String[] sections = content.split("\\+\\+\\+TEMPLATE:");
        
        for (String section : sections) {
            if (section.trim().isEmpty()) continue;
            
            String[] parts = section.split("\\+\\+\\+");
            if (parts.length >= 2) {
                String templateName = parts[0].trim();
                String templateContent = parts[1].replace("TEMPLATE_END", "").trim();
                templates.put(templateName, templateContent);
            }
        }
    }
    
    /**
     * Load content data from JSON file
     */
    private void loadContentData() {
        try (InputStream is = getClass().getResourceAsStream("/intro_content.json")) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(is);
            
            rootNode.fields().forEachRemaining(entry -> {
                contentData.put(entry.getKey(), entry.getValue());
            });
            
        } catch (IOException e) {
            System.err.println("Error loading content data: " + e.getMessage());
        }
    }
    
    /**
     * Generate introduction content for a subcategory
     */
    public String generateIntroContent(String subcategoryName) {
        JsonNode data = getContentDataForSubcategory(subcategoryName);
        if (data == null) {
            return null;
        }
        
        String templateName = getTextValue(data, "template", "default");
        String template = templates.get(templateName);
        
        if (template == null) {
            template = templates.get("default");
        }
        
        if (template == null) {
            return null;
        }
        
        return processTemplate(template, data);
    }
    
    /**
     * Get content data for a subcategory with fallback lookup
     */
    private JsonNode getContentDataForSubcategory(String subcategoryName) {
        // Direct lookup
        JsonNode data = contentData.get(subcategoryName);
        if (data != null) {
            return data;
        }
        
        // Normalized lookup for variations
        String normalized = normalizeKey(subcategoryName);
        for (Map.Entry<String, JsonNode> entry : contentData.entrySet()) {
            if (normalizeKey(entry.getKey()).equals(normalized)) {
                return entry.getValue();
            }
        }
        
        // Partial match lookup
        for (Map.Entry<String, JsonNode> entry : contentData.entrySet()) {
            String entryNormalized = normalizeKey(entry.getKey());
            if (entryNormalized.contains(normalized) || normalized.contains(entryNormalized)) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * Process template by replacing handlers with content
     */
    private String processTemplate(String template, JsonNode data) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = HANDLER_PATTERN.matcher(template);
        int lastEnd = 0;
        
        while (matcher.find()) {
            // Add text before the handler
            result.append(template, lastEnd, matcher.start());
            
            // Process the handler
            String handlerName = matcher.group(1);
            String handlerContent = processHandler(handlerName, data);
            result.append(handlerContent);
            
            lastEnd = matcher.end();
        }
        
        // Add remaining text
        result.append(template.substring(lastEnd));
        
        return result.toString().trim();
    }
    
    /**
     * Process individual handlers
     */
    private String processHandler(String handlerName, JsonNode data) {
        switch (handlerName) {
            case "HEADER":
                return getTextValue(data, "header", "");
                
            case "TIME":
                String taskCount = getTextValue(data, "task_count", "");
                String timeLimit = getTextValue(data, "time_limit", "");
                return String.format("Bearbeitungszeit für %s: %s\n\n", taskCount, timeLimit);
                
            case "KNOWLEDGE_HEADER":
                String subject = getTextValue(data, "subject", "");
                return String.format("Basiskenntnistest - %s\n\n", subject);
                
            case "DESCRIPTION":
                return getTextValue(data, "description", "") + "\n";
                
            case "KNOWLEDGE_DESCRIPTION":
                String subj = getTextValue(data, "subject", "");
                String desc = getTextValue(data, "description", "");
                return String.format("Die folgenden Aufgaben überprüfen Ihre Kenntnisse im Themenbereich %s.\n%s\n", subj, desc);
                
            case "SPATIAL_DESCRIPTION":
                return getTextValue(data, "spatial_description", "") + "\n";
                
            case "TASK_EXPLANATION":
                return getTextValue(data, "task_explanation", "") + "\n";
                
            case "IMPLICATION_DESCRIPTION":
                return getTextValue(data, "description", "") + "\n";
                
            case "EMOTION_DESCRIPTION":
                return getTextValue(data, "description", "") + "\n";
                
            case "FORMAT_INFO":
                return "Die Aufgaben sind im Single-Choice Format gestellt und jeweils nur eine der gegebenen Antwortmöglichkeiten A) bis E) ist korrekt.\n";
                
            case "ANSWER_SHEET_INFO":
                return "Bitte markieren Sie für jede Aufgabe die korrekte Antwort in Ihrem Antwortbogen, da ausschließlich dieser für die Auswertung Ihrer Ergebnisse herangezogen wird. Markierungen im Testheft werden nicht beurteilt.\n";
                
            case "NAVIGATION_WARNING":
                return "Das Zurückblättern zum vorherigen Untertest, sowie das selbstständige Weiterblättern zum nächsten Untertest ist nicht erlaubt und führt zum sofortigen Ausschluss von der Prüfung.\n";
                
            case "START_PERMISSION":
                return "Sie dürfen mit der Bearbeitung der Aufgaben erst beginnen, wenn der Testleiter den Untertest freigegeben hat!\n";
                
            case "EXAMPLE_SECTION":
                return processExampleSection(data);
                
            case "EXAMPLE_VISUAL":
                return processExampleVisual(data);
                
            case "EXAMPLE_TEXT":
                return processExampleText(data);
                
            case "IMPLICATION_EXAMPLE":
                return processImplicationExample(data);
                
            case "EMOTION_EXAMPLE":
                return processEmotionExample(data);
                
            case "ANSWER_KEY":
                return processAnswerKey(data);
                
            case "MEMORY_HEADER":
                String header = getTextValue(data, "header", "");
                String subheader = getTextValue(data, "subheader", "");
                return String.format("%s\n%s\n\n", header, subheader);
                
            case "LEARNING_TIME":
                return getTextValue(data, "learning_time", "") + "\n\n";
                
            case "RECALL_TIME":
                String recallTaskCount = getTextValue(data, "task_count", "");
                String recallTimeLimit = getTextValue(data, "time_limit", "");
                return String.format("Bearbeitungszeit für %s: %s\n\n", recallTaskCount, recallTimeLimit);
                
            case "MEMORY_DESCRIPTION":
                return getTextValue(data, "description", "") + "\n\n";
                
            case "RECALL_DESCRIPTION":
                return getTextValue(data, "description", "") + "\n\n";
                
            case "LEARNING_RULES":
                return getTextValue(data, "learning_rules", "") + "\n\n";
                
            case "EXAMPLE_CARD":
                return "Beispielausweis:\n\n";
                
            case "CUSTOM_ADDITIONAL_TEXT":
                return getTextValue(data, "additional_text", "") + "\n\n";
                
            default:
                return "";
        }
    }
    
    /**
     * Process example section for standard questions
     */
    private String processExampleSection(JsonNode data) {
        StringBuilder example = new StringBuilder("Beispielaufgabe:\n");
        
        String question = getTextValue(data, "example_question", "");
        if (!question.isEmpty()) {
            example.append(question).append("\n");
        }
        
        JsonNode options = data.get("answer_options");
        if (options != null && options.isArray()) {
            for (int i = 0; i < options.size(); i++) {
                char letter = (char) ('A' + i);
                example.append(letter).append(")\t").append(options.get(i).asText()).append("\n");
            }
        }
        
        return example.append("\n").toString();
    }
    
    /**
     * Process example visual for figuren questions
     */
    private String processExampleVisual(JsonNode data) {
        StringBuilder example = new StringBuilder("Beispielaufgabe:\n");
        String visualExample = getTextValue(data, "visual_example", "");
        if (!visualExample.isEmpty()) {
            example.append(visualExample).append("\n\n");
        }
        return example.toString();
    }
    
    /**
     * Process example text for comprehension questions
     */
    private String processExampleText(JsonNode data) {
        StringBuilder example = new StringBuilder("Beispieltext:\n");
        String exampleText = getTextValue(data, "example_text", "");
        if (!exampleText.isEmpty()) {
            example.append(exampleText).append("\n\n");
        }
        return example.toString();
    }
    
    /**
     * Process implication examples
     */
    private String processImplicationExample(JsonNode data) {
        StringBuilder example = new StringBuilder("Beispielaufgabe:\n");
        
        // Handle premise-based examples (logical syllogisms)
        JsonNode premises = data.get("example_premises");
        if (premises != null && premises.isArray()) {
            for (JsonNode premise : premises) {
                example.append(premise.asText()).append("\n");
            }
        } else {
            // Handle statement-based examples
            String statement = getTextValue(data, "example_statement", "");
            if (!statement.isEmpty()) {
                example.append("Ausgangsaussage: ").append(statement).append("\n\n");
            }
        }
        
        String question = getTextValue(data, "example_question", "");
        if (!question.isEmpty()) {
            example.append(question).append("\n");
        }
        
        JsonNode options = data.get("answer_options");
        if (options != null && options.isArray()) {
            for (int i = 0; i < options.size(); i++) {
                char letter = (char) ('A' + i);
                example.append(letter).append(")\t").append(options.get(i).asText()).append(".\n");
            }
        }
        
        return example.append("\n").toString();
    }
    
    /**
     * Process emotion examples (situations and reactions)
     */
    private String processEmotionExample(JsonNode data) {
        StringBuilder example = new StringBuilder("Beispielaufgabe:\n");
        
        String situation = getTextValue(data, "example_situation", "");
        if (!situation.isEmpty()) {
            example.append("Situation: ").append(situation).append("\n\n");
        }
        
        String visual = getTextValue(data, "example_visual", "");
        if (!visual.isEmpty()) {
            example.append(visual).append("\n\n");
        }
        
        String question = getTextValue(data, "example_question", "");
        if (!question.isEmpty()) {
            example.append(question).append("\n");
        }
        
        JsonNode options = data.get("answer_options");
        if (options != null && options.isArray()) {
            for (int i = 0; i < options.size(); i++) {
                char letter = (char) ('A' + i);
                example.append(letter).append(") ").append(options.get(i).asText()).append(".\n");
            }
        }
        
        return example.append("\n").toString();
    }
    
    /**
     * Process answer key
     */
    private String processAnswerKey(JsonNode data) {
        String correctAnswer = getTextValue(data, "correct_answer", "");
        String correctAnswerText = getTextValue(data, "correct_answer_text", "");
        
        if (!correctAnswer.isEmpty()) {
            if (!correctAnswerText.isEmpty()) {
                return String.format("Die korrekte Antwort der Beispielaufgabe wäre Antwortmöglichkeit %s) %s.", 
                    correctAnswer, correctAnswerText);
            } else {
                return String.format("Die korrekte Antwort der Beispielaufgabe wäre Antwortmöglichkeit %s).", 
                    correctAnswer);
            }
        }
        
        return "";
    }
    
    /**
     * Create a fallback default template
     */
    private String createDefaultTemplate() {
        return "[HEADER]\n\n[TIME][DESCRIPTION]\n[FORMAT_INFO]\n[ANSWER_SHEET_INFO]\n[NAVIGATION_WARNING]\n[START_PERMISSION]\n[EXAMPLE_SECTION][ANSWER_KEY]";
    }
    
    /**
     * Safely get text value from JSON node
     */
    private String getTextValue(JsonNode node, String fieldName, String defaultValue) {
        JsonNode field = node.get(fieldName);
        return field != null ? field.asText() : defaultValue;
    }
    
    /**
     * Normalize key for matching
     */
    private String normalizeKey(String key) {
        return java.text.Normalizer.normalize(key, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-zA-Z0-9]", "")
            .toLowerCase();
    }
    
    /**
     * Get all available subcategory names
     */
    public Set<String> getAvailableSubcategories() {
        return contentData.keySet();
    }
    
    /**
     * Check if content exists for a subcategory
     */
    public boolean hasContentFor(String subcategoryName) {
        return getContentDataForSubcategory(subcategoryName) != null;
    }
}
