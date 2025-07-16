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
        List<String> options = List.of("Ja", "Nein", "Keine der Antwortmöglichkeiten ist richtig.");
        return new Question(questionText, options, "Ja".equals(card.medikamenteneinnahme()) ? "Ja" : "Nein");
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
        List<String> all = new ArrayList<>(List.of("A", "B", "AB", "0"));
        all.remove(correct);
        Collections.shuffle(all, rnd);
        List<String> opts = new ArrayList<>();
        opts.add(correct);
        opts.addAll(all.subList(0, Math.min(2, all.size())));
        Collections.shuffle(opts, rnd);
        opts.add("Keine der Antwortmöglichkeiten ist richtig.");
        return opts;
    }

    private List<String> otherValues(Function<AllergyCardData, String> fn, String exclude) {
        return cards.stream()
                .map(fn)
                .distinct()
                .filter(s -> !s.equals(exclude))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<String> pick(List<String> pool, int n) {
        Collections.shuffle(pool, rnd);
        if (pool.size() > n) {
            pool = new ArrayList<>(pool.subList(0, n));
        }
        return new ArrayList<>(pool);
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
}
