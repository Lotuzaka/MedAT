package merk;

import model.AllergyCardData;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Engine for generating Merkfähigkeit questions based on allergy card data
 */
public class MerkQuestionEngine {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final List<AllergyCardData> cards;
    private final Random rnd;
    private final TemplateRegistry registry;

    public MerkQuestionEngine(List<AllergyCardData> cards) {
        this.cards = cards;
        this.rnd = new Random();
        this.registry = TemplateRegistry.load();
    }

    public Question generate() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("No allergy cards available");
        }

        // Pick a random template from supported types (excluding S5)
        MerkTemplate[] supportedTypes = {
            MerkTemplate.S1, MerkTemplate.S2, MerkTemplate.S3, MerkTemplate.S4,
            MerkTemplate.S6, MerkTemplate.S7, MerkTemplate.S8, MerkTemplate.S9
        };
        MerkTemplate templateType = supportedTypes[rnd.nextInt(supportedTypes.length)];
        
        TemplateData template = registry.get(templateType);
        if (template == null) {
            throw new IllegalStateException("Template not found: " + templateType);
        }

        return switch (templateType) {
            case S1 -> generateS1(template);
            case S2 -> generateS2(template);
            case S3 -> generateS3(template);
            case S4 -> generateS4(template);
            case S6 -> generateS6(template);
            case S7 -> generateS7(template);
            case S8 -> generateS8(template);
            case S9 -> generateS9(template);
            default -> throw new IllegalStateException("Unsupported template: " + templateType);
        };
    }

    private Question generateS1(TemplateData template) {
        AllergyCardData card = randomCard();
        String questionText = fill(template.randomVariant(rnd), Map.of("ID", card.ausweisnummer()));
        List<String> options = bloodOptions(card.blutgruppe());
        return new Question(questionText, options, card.blutgruppe());
    }

    private Question generateS2(TemplateData template) {
        AllergyCardData card = randomCard();
        String questionText = fill(template.randomVariant(rnd), Map.of("COUNTRY", card.ausstellungsland()));
        List<String> options = otherValues(Alley::id, card.ausweisnummer());
        return new Question(questionText, options, card.ausweisnummer());
    }

    private Question generateS3(TemplateData template) {
        AllergyCardData card = randomCard();
        String questionText = fill(template.randomVariant(rnd), Map.of(
            "COUNTRY", card.ausstellungsland(),
            "BLOOD", card.blutgruppe()
        ));
        List<String> options = otherValues(Alley::name, card.name());
        return new Question(questionText, options, card.name());
    }

    private Question generateS4(TemplateData template) {
        AllergyCardData card = randomCard();
        String questionText = fill(template.randomVariant(rnd), Map.of("NAME", card.name()));
        List<String> options = otherValues(d -> formatDate(d.geburtsdatum()), formatDate(card.geburtsdatum()));
        return new Question(questionText, options, formatDate(card.geburtsdatum()));
    }

    private Question generateS6(TemplateData template) {
        AllergyCardData card = randomCard();
        String questionText = fill(template.randomVariant(rnd), Map.of("NAME", card.name()));
        List<String> options = otherValues(Alley::country, card.ausstellungsland());
        return new Question(questionText, options, card.ausstellungsland());
    }

    private Question generateS7(TemplateData template) {
        AllergyCardData card = randomCard();
        String questionText = fill(template.randomVariant(rnd), Map.of("NAME", card.name()));
        List<String> options = bloodOptions(card.blutgruppe());
        return new Question(questionText, options, card.blutgruppe());
    }

    private Question generateS8(TemplateData template) {
        AllergyCardData card = randomCard();
        String questionText = fill(template.randomVariant(rnd), Map.of("NAME", card.name()));
        
        // Generate proper A-E options for medication question
        String correctAnswer = "Ja".equals(card.medikamenteneinnahme()) ? "Ja" : "Nein";
        List<String> options = new ArrayList<>();
        options.add("Ja");
        options.add("Nein");
        // Add placeholder options to fill A-D
        options.add("Unbekannt");
        options.add("Keine Angabe");
        // Shuffle the first 4 options (A-D)
        Collections.shuffle(options, rnd);
        // Add option E
        options.add("Keine Antwort ist richtig.");
        
        return new Question(questionText, options, correctAnswer);
    }

    private Question generateS9(TemplateData template) {
        AllergyCardData card = randomCard();
        String questionText = fill(template.randomVariant(rnd), Map.of("NAME", card.name()));
        List<String> options = otherValues(Alley::id, card.ausweisnummer());
        return new Question(questionText, options, card.ausweisnummer());
    }

    private AllergyCardData randomCard() {
        return cards.get(rnd.nextInt(cards.size()));
    }

    private List<String> bloodOptions(String correct) {
        // Always include all 4 blood groups A, B, AB, 0 in options A-D
        List<String> all = new ArrayList<>(List.of("A", "B", "AB", "0"));
        Collections.shuffle(all, rnd);
        List<String> opts = new ArrayList<>(all);
        // Add option E: "Keine Antwort ist richtig"
        opts.add("Keine Antwort ist richtig.");
        return opts;
    }

    private List<String> otherValues(Function<AllergyCardData, String> fn, String exclude) {
        List<String> allValues = cards.stream()
                .map(fn)
                .distinct()
                .filter(s -> !s.equals(exclude))
                .collect(Collectors.toCollection(ArrayList::new));
        
        // Ensure we have enough options for A-D (4 options) plus E
        Collections.shuffle(allValues, rnd);
        List<String> opts = new ArrayList<>();
        
        // Add the correct answer first
        opts.add(exclude);
        
        // Add up to 3 other options to make 4 total (A-D)
        for (int i = 0; i < Math.min(3, allValues.size()); i++) {
            opts.add(allValues.get(i));
        }
        
        // If we don't have enough unique values, add some placeholder options
        while (opts.size() < 4) {
            opts.add("Option " + (opts.size()));
        }
        
        // Shuffle the first 4 options (A-D)
        Collections.shuffle(opts, rnd);
        
        // Add option E: "Keine Antwort ist richtig"
        opts.add("Keine Antwort ist richtig.");
        
        return opts;
    }

    private String fill(String template, Map<String,String> map) {
        String t = template;
        for (Map.Entry<String,String> e : map.entrySet()) {
            t = t.replace("{" + e.getKey() + "}", e.getValue());
        }
        return t;
    }

    private String formatDate(LocalDate d) {
        if (d == null) return "";
        return d.format(DATE_FMT);
    }

    // alias helpers for lambda references
    private static class Alley {
        static String name(AllergyCardData d) { return d.name(); }
        static String id(AllergyCardData d) { return d.ausweisnummer(); }
        static String country(AllergyCardData d) { return d.ausstellungsland(); }
    }

    public record Question(String text, List<String> options, String correctAnswer) {
        public int correctIndex() {
            return options.indexOf(correctAnswer);
        }
    }

    /**
     * Generate a question for a specific template type
     */
    public Question generateSpecific(MerkTemplate templateType) {
        if (cards.isEmpty()) {
            throw new IllegalStateException("No allergy cards available");
        }

        TemplateData template = registry.get(templateType);
        if (template == null) {
            throw new IllegalStateException("Template not found: " + templateType);
        }

        return switch (templateType) {
            case S1 -> generateS1(template);
            case S2 -> generateS2(template);
            case S3 -> generateS3(template);
            case S4 -> generateS4(template);
            case S6 -> generateS6(template);
            case S7 -> generateS7(template);
            case S8 -> generateS8(template);
            case S9 -> generateS9(template);
            default -> throw new IllegalStateException("Unsupported template: " + templateType);
        };
    }

    /**
     * Generate all available question types for testing (excluding S5 which is not implemented)
     * Creates multiple questions for each template type to show all possible variations
     */
    public List<Question> generateAllTypes() {
        MerkTemplate[] allTypes = {
            MerkTemplate.S1, MerkTemplate.S2, MerkTemplate.S3, MerkTemplate.S4,
            MerkTemplate.S6, MerkTemplate.S7, MerkTemplate.S8, MerkTemplate.S9
        };
        
        List<Question> questions = new ArrayList<>();
        for (MerkTemplate type : allTypes) {
            try {
                TemplateData template = registry.get(type);
                if (template != null) {
                    // Generate multiple questions to showcase different variations
                    // Main question (using randomVariant which can pick any variant)
                    questions.add(generateSpecific(type));
                    
                    // Generate additional questions to show variety (2-3 more per template)
                    int additionalQuestions = Math.min(3, cards.size());
                    for (int i = 0; i < additionalQuestions; i++) {
                        questions.add(generateSpecific(type));
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to generate question for template " + type + ": " + e.getMessage());
            }
        }
        return questions;
    }
}
