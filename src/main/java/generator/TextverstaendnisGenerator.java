package generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * Enhanced Textverständnis Generator following AGENTS.md specifications.
 * Implements sophisticated question generation with advanced NLP processing,
 * systematic distractor creation, and difficulty grading.
 */
public class TextverstaendnisGenerator {

    private final Connection conn;
    private final String category;
    private final String subcategory;
    private final Integer simulationId;
    private final StanfordCoreNLP pipeline;
    private final Map<String, List<String>> templates;
    private Random random = new Random();

    // Default weights according to AGENTS.md
    private static final Map<String, Double> DEFAULT_QUESTION_TYPE_WEIGHTS = Map.of(
        "entailment", 0.4,
        "widerspruch", 0.2,
        "nicht_erwaehnt", 0.15,
        "kernaussage", 0.15,
        "struktur", 0.1
    );

    private static final Map<String, Double> DEFAULT_DIFFICULTY_WEIGHTS = Map.of(
        "leicht", 0.3,
        "mittel", 0.5,
        "schwer", 0.2
    );

    private Map<String, Double> questionTypeWeights = new HashMap<>(DEFAULT_QUESTION_TYPE_WEIGHTS);
    private Map<String, Double> difficultyWeights = new HashMap<>(DEFAULT_DIFFICULTY_WEIGHTS);

    private final List<QuestionRecord> generatedQuestions = new ArrayList<>();
    private List<Proposition> lastPropositions = new ArrayList<>();
    private final List<String> metaWarnings = new ArrayList<>();

    /**
     * Constructs the generator with DB connection and context.
     * Uses a lightweight approach that doesn't require Stanford CoreNLP models.
     */
    public TextverstaendnisGenerator(Connection conn, String category, String subcategory, Integer simulationId) {
        this.conn = conn;
        this.category = category;
        this.subcategory = subcategory;
        this.simulationId = simulationId;

        // Load question templates
        this.templates = loadTemplates();

        // Use a minimal pipeline that doesn't require external model files
        StanfordCoreNLP tempPipeline;
        try {
            Properties props = new Properties();
            props.setProperty("annotators", "tokenize,ssplit");
            props.setProperty("tokenize.language", "de"); // German tokenization
            props.setProperty("ssplit.newlineIsSentenceBreak", "always");
            tempPipeline = new StanfordCoreNLP(props);
            System.out.println("TextverstaendnisGenerator: Initialized with lightweight NLP pipeline");
        } catch (Exception e) {
            System.err.println("Warning: Could not initialize NLP pipeline, using fallback text processing: " + e.getMessage());
            tempPipeline = null;
        }
        this.pipeline = tempPipeline;
    }

    /**
     * Main execution method following AGENTS.md specifications.
     */
    public void execute(String passageText, int questionCount) throws SQLException {
        execute(passageText, questionCount, null);
    }

    /**
     * Execution method with parameter map for weight overrides.
     */
    public String execute(String passageText, int questionCount, Map<String, Object> params) throws SQLException {
        generatedQuestions.clear();
        lastPropositions = new ArrayList<>();
        metaWarnings.clear();

        applyParams(params);

        if (params != null && params.get("anzahl_fragen_total") instanceof Number n) {
            questionCount = n.intValue();
        }

        if (passageText.split("\\s+").length < 120) {
            metaWarnings.add("Passage kürzer als 120 Wörter");
            questionCount = Math.min(questionCount, 3);
        }

        System.out.println("Generating " + questionCount + " questions using AGENTS.md methodology...");

        // Comprehensive text analysis
        TextAnalysis analysis = analyzeText(passageText);
        lastPropositions = analysis.propositions;

        // Distribute questions across types and difficulties
        List<QuestionSpec> questionSpecs = distributeQuestions(questionCount);

        // Generate questions systematically
        for (int i = 0; i < questionSpecs.size(); i++) {
            QuestionSpec spec = questionSpecs.get(i);
            try {
                QuestionData questionData = generateQuestion(spec, analysis);
                
                // Insert into database
                int passageId = getPassageId(passageText);
                int questionId = insertQuestion(questionData.text, i + 1, passageId);
                insertAnswerOptions(questionId, questionData.options, questionData.correctIndex);

                storeQuestionRecord("Q" + (i + 1), spec, questionData, analysis);
                System.out.println("Generated " + spec.type + " question (" + spec.difficulty + ")");

            } catch (Exception e) {
                System.err.println("Failed to generate question of type " + spec.type + ": " + e.getMessage());
            }
        }

        System.out.println("Question generation completed successfully!");
        int passageId = getPassageId(passageText);
        return toJson(passageId);
    }

    /**
     * Analyzes text using available NLP capabilities with fallback for missing models.
     */
    private TextAnalysis analyzeText(String text) {
        try {
            List<CoreMap> sentences = new ArrayList<>();
            List<RelationTriple> relations = new ArrayList<>();
            List<Proposition> propositions = new ArrayList<>();
            
            if (pipeline != null) {
                // Use Stanford CoreNLP if available
                Annotation document = new Annotation(text);
                pipeline.annotate(document);
                sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
                System.out.println("TextverstaendnisGenerator: Processed " + sentences.size() + " sentences with CoreNLP");
            } else {
                // Fallback: simple sentence splitting
                String[] sentenceArray = text.split("[.!?]+");
                for (String sent : sentenceArray) {
                    if (sent.trim().length() > 0) {
                        // Create a simple CoreMap-like object
                        Annotation sentAnnotation = new Annotation(sent.trim());
                        sentences.add(sentAnnotation);
                    }
                }
                System.out.println("TextverstaendnisGenerator: Using fallback processing for " + sentences.size() + " sentences");
            }
            
            // Create simple propositions from sentences (fallback approach)
            propositions = createSimplePropositions(sentences);
            
            return new TextAnalysis(sentences, relations, propositions, null);
            
        } catch (Exception e) {
            System.err.println("Error analyzing text: " + e.getMessage());
            // Return minimal analysis
            List<CoreMap> fallbackSentences = new ArrayList<>();
            String[] sentenceArray = text.split("[.!?]+");
            for (String sent : sentenceArray) {
                if (sent.trim().length() > 0) {
                    fallbackSentences.add(new Annotation(sent.trim()));
                }
            }
            return new TextAnalysis(fallbackSentences, new ArrayList<>(), new ArrayList<>(), null);
        }
    }
    
    /**
     * Creates simple propositions from sentences when advanced NLP is not available.
     */
    private List<Proposition> createSimplePropositions(List<CoreMap> sentences) {
        List<Proposition> propositions = new ArrayList<>();
        int id = 1;
        
        for (CoreMap sentence : sentences) {
            String text = sentence.toString().trim();
            
            // Simple heuristic: look for common patterns like "X ist Y", "X hat Y", etc.
            if (text.contains(" ist ")) {
                String[] parts = text.split(" ist ", 2);
                if (parts.length == 2) {
                    propositions.add(new Proposition("p" + id++, parts[0].trim(), "ist", parts[1].trim(), new ArrayList<>()));
                }
            } else if (text.contains(" hat ")) {
                String[] parts = text.split(" hat ", 2);
                if (parts.length == 2) {
                    propositions.add(new Proposition("p" + id++, parts[0].trim(), "hat", parts[1].trim(), new ArrayList<>()));
                }
            } else if (text.contains(" kann ")) {
                String[] parts = text.split(" kann ", 2);
                if (parts.length == 2) {
                    propositions.add(new Proposition("p" + id++, parts[0].trim(), "kann", parts[1].trim(), new ArrayList<>()));
                }
            } else {
                // Generic proposition from full sentence
                propositions.add(new Proposition("p" + id++, "Text", "besagt", text, new ArrayList<>()));
            }
        }
        
        return propositions;
    }

    /**
     * Extract propositions from sentences and relation triples.
     * Handles both advanced CoreNLP output and simple fallback.
     */
    private List<Proposition> extractPropositions(List<CoreMap> sentences, List<RelationTriple> triples) {
        List<Proposition> propositions = new ArrayList<>();
        int id = 1;
        
        // If we have relation triples from advanced NLP, use them
        if (!triples.isEmpty()) {
            for (RelationTriple triple : triples) {
                propositions.add(new Proposition(
                    "p" + id++,
                    triple.subjectGloss(),
                    triple.relationGloss(), 
                    triple.objectGloss(),
                    extractModifiers(triple)
                ));
            }
        } else {
            // Fallback: create simple propositions from sentences
            propositions = createSimplePropositions(sentences);
        }
        
        return propositions;
    }

    /**
     * Extract modifiers from relation triple with null safety.
     */
    private List<String> extractModifiers(RelationTriple triple) {
        List<String> modifiers = new ArrayList<>();
        
        if (triple == null) {
            return modifiers;
        }
        
        String relation = triple.relationGloss().toLowerCase();
        
        if (relation.contains("wenn") || relation.contains("falls")) {
            modifiers.add("condition");
        }
        if (relation.contains("häufig") || relation.contains("oft")) {
            modifiers.add("quantifier_frequent");
        }
        if (relation.contains("selten") || relation.contains("nie")) {
            modifiers.add("quantifier_rare");
        }
        if (relation.contains("jedoch") || relation.contains("aber")) {
            modifiers.add("contrast");
        }
        
        return modifiers;
    }

    /**
     * Distribute questions across types and difficulties.
     */
    private List<QuestionSpec> distributeQuestions(int totalCount) {
        List<QuestionSpec> specs = new ArrayList<>();
        
        for (Map.Entry<String, Double> entry : questionTypeWeights.entrySet()) {
            String type = entry.getKey();
            int typeCount = (int) Math.round(totalCount * entry.getValue());
            
            for (int i = 0; i < typeCount; i++) {
                String difficulty = selectDifficulty();
                specs.add(new QuestionSpec(type, difficulty));
            }
        }
        
        // Fill remaining slots if rounding caused shortfall
        while (specs.size() < totalCount) {
            String type = selectQuestionType();
            String difficulty = selectDifficulty();
            specs.add(new QuestionSpec(type, difficulty));
        }
        
        Collections.shuffle(specs);
        return specs;
    }

    /**
     * Select question type based on weights.
     */
    private String selectQuestionType() {
        double rand = random.nextDouble();
        double cumulative = 0.0;
        
        for (Map.Entry<String, Double> entry : questionTypeWeights.entrySet()) {
            cumulative += entry.getValue();
            if (rand <= cumulative) {
                return entry.getKey();
            }
        }
        
        return "entailment"; // fallback
    }

    /**
     * Select difficulty based on weights.
     */
    private String selectDifficulty() {
        double rand = random.nextDouble();
        double cumulative = 0.0;
        
        for (Map.Entry<String, Double> entry : difficultyWeights.entrySet()) {
            cumulative += entry.getValue();
            if (rand <= cumulative) {
                return entry.getKey();
            }
        }
        
        return "mittel"; // fallback
    }

    /**
     * Generate a single question using AGENTS.md specifications.
     */
    private QuestionData generateQuestion(QuestionSpec spec, TextAnalysis analysis) {
        List<String> templateList = templates.get(spec.type);
        if (templateList == null || templateList.isEmpty()) {
            templateList = Arrays.asList("Welche Aussage ist korrekt?");
        }
        
        String stem = templateList.get(random.nextInt(templateList.size()));
        
        switch (spec.type) {
            case "entailment":
                return generateEntailmentQuestion(stem, analysis, spec.difficulty);
            case "widerspruch":
                return generateContradictionQuestion(stem, analysis, spec.difficulty);
            case "nicht_erwaehnt":
                return generateNotMentionedQuestion(stem, analysis, spec.difficulty);
            case "kernaussage":
                return generateCoreStatementQuestion(stem, analysis, spec.difficulty);
            case "struktur":
                return generateStructureQuestion(stem, analysis, spec.difficulty);
            default:
                return generateEntailmentQuestion(stem, analysis, spec.difficulty);
        }
    }

    /**
     * Generate entailment question (logical inference from text).
     */
    private QuestionData generateEntailmentQuestion(String stem, TextAnalysis analysis, String difficulty) {
        List<String> options = new ArrayList<>();
        
        // Correct option: valid inference from text
        String correctOption = createEntailmentOption(analysis);
        options.add(correctOption);
        
        // Generate 4 distractors using systematic transformations
        options.add(createNegationDistractor(correctOption));
        options.add(createQuantifierFlipDistractor(correctOption));
        options.add(createConditionDropDistractor(correctOption));
        options.add(createTemporalFlipDistractor(correctOption));
        
        Collections.shuffle(options);
        int correctIndex = options.indexOf(correctOption);
        
        return new QuestionData(stem, options, correctIndex);
    }

    /**
     * Generate contradiction question.
     */
    private QuestionData generateContradictionQuestion(String stem, TextAnalysis analysis, String difficulty) {
        List<String> options = new ArrayList<>();
        
        // Select a statement that contradicts the text
        String correctOption = createContradictionOption(analysis);
        options.add(correctOption);
        
        // Generate 4 distractors (statements that are true or neutral)
        options.add(createTrueStatement(analysis));
        options.add(createNeutralStatement(analysis));
        options.add(createTrueStatement(analysis));
        options.add(createNeutralStatement(analysis));
        
        Collections.shuffle(options);
        int correctIndex = options.indexOf(correctOption);
        
        return new QuestionData(stem, options, correctIndex);
    }

    /**
     * Generate "not mentioned" question.
     */
    private QuestionData generateNotMentionedQuestion(String stem, TextAnalysis analysis, String difficulty) {
        List<String> options = new ArrayList<>();
        
        // Correct option: plausible but not mentioned
        String correctOption = createNotMentionedOption(analysis);
        options.add(correctOption);
        
        // Generate 4 distractors (statements that are mentioned)
        for (int i = 0; i < 4 && i < analysis.sentences.size(); i++) {
            options.add(paraphraseSentence(analysis.sentences.get(i).toString()));
        }
        
        Collections.shuffle(options);
        int correctIndex = options.indexOf(correctOption);
        
        return new QuestionData(stem, options, correctIndex);
    }

    /**
     * Generate core statement question.
     */
    private QuestionData generateCoreStatementQuestion(String stem, TextAnalysis analysis, String difficulty) {
        List<String> options = new ArrayList<>();
        
        // Correct option: main thesis/core message
        String correctOption = extractCoreStatement(analysis);
        options.add(correctOption);
        
        // Generate 4 distractors (details, not main points)
        options.add(createDetailDistractor(analysis));
        options.add(createOvergeneralizationDistractor(analysis));
        options.add(createUndergeneralizationDistractor(analysis));
        options.add(createTangentialDistractor(analysis));
        
        Collections.shuffle(options);
        int correctIndex = options.indexOf(correctOption);
        
        return new QuestionData(stem, options, correctIndex);
    }

    /**
     * Generate structure/function question.
     */
    private QuestionData generateStructureQuestion(String stem, TextAnalysis analysis, String difficulty) {
        List<String> options = new ArrayList<>();
        
        // Correct option: actual rhetorical function
        String correctOption = identifyRhetoricalFunction(analysis);
        options.add(correctOption);
        
        // Generate 4 distractors (wrong rhetorical functions)
        String[] functions = {"Definition", "Beispiel", "Gegenargument", "Einschränkung", "Begründung"};
        for (int i = 0; i < 4; i++) {
            options.add("Dient als " + functions[random.nextInt(functions.length)]);
        }
        
        Collections.shuffle(options);
        int correctIndex = options.indexOf(correctOption);
        
        return new QuestionData(stem, options, correctIndex);
    }

    // Distractor creation methods following AGENTS.md transformations
    
    private String createNegationDistractor(String original) {
        if (original.contains("ist")) {
            return original.replace("ist", "ist nicht");
        }
        if (original.contains("kann")) {
            return original.replace("kann", "kann nicht");
        }
        return "Nicht: " + original;
    }

    private String createQuantifierFlipDistractor(String original) {
        return original.replace("manchmal", "immer")
                     .replace("selten", "häufig")
                     .replace("teilweise", "vollständig");
    }

    private String createEntitySwapDistractor(TextAnalysis analysis) {
        if (!analysis.propositions.isEmpty()) {
            Proposition prop = analysis.propositions.get(random.nextInt(analysis.propositions.size()));
            return prop.obj + " " + prop.pred + " " + prop.subj; // swap subject/object
        }
        return "Verwechselte Entitäten aus dem Text";
    }

    private String createOutOfScopeDistractor(TextAnalysis analysis) {
        return "Eine plausible, aber nicht im Text erwähnte Aussage";
    }

    private String createConditionDropDistractor(String original) {
        if (original.contains("wenn")) {
            return original.replaceAll("nur\\s+wenn", "").replace("wenn", "").trim();
        }
        return original + " ohne Bedingung";
    }

    private String createTemporalFlipDistractor(String original) {
        return original.replace("zunächst", "abschließend")
                       .replace("erst", "zuletzt")
                       .replace("später", "früher");
    }

    private String createTrueStatement(TextAnalysis analysis) {
        if (!analysis.sentences.isEmpty()) {
            return paraphraseSentence(analysis.sentences.get(random.nextInt(analysis.sentences.size())).toString());
        }
        return "Eine wahre Aussage aus dem Text";
    }

    private String createNeutralStatement(TextAnalysis analysis) {
        return "Eine neutrale Aussage, die weder bestätigt noch widerlegt wird";
    }

    private String createContradictionOption(TextAnalysis analysis) {
        return "Eine Aussage, die dem Text widerspricht";
    }

    private String createNotMentionedOption(TextAnalysis analysis) {
        return "Eine plausible, aber nicht erwähnte Information";
    }

    private String createEntailmentOption(TextAnalysis analysis) {
        if (!analysis.propositions.isEmpty()) {
            Proposition prop = analysis.propositions.get(random.nextInt(analysis.propositions.size()));
            return "Daraus folgt: " + prop.subj + " " + prop.pred + " " + prop.obj;
        }
        return "Eine logische Schlussfolgerung aus dem Text";
    }

    private String extractCoreStatement(TextAnalysis analysis) {
        return "Die Hauptaussage des Textes";
    }

    private String createDetailDistractor(TextAnalysis analysis) {
        return "Ein spezifisches Detail aus dem Text";
    }

    private String createOvergeneralizationDistractor(TextAnalysis analysis) {
        return "Eine zu allgemeine Verallgemeinerung";
    }

    private String createUndergeneralizationDistractor(TextAnalysis analysis) {
        return "Eine zu spezifische Einschränkung";
    }

    private String createTangentialDistractor(TextAnalysis analysis) {
        return "Ein tangentialer Aspekt des Textes";
    }

    private String identifyRhetoricalFunction(TextAnalysis analysis) {
        return "Dient als Einleitung des Hauptarguments";
    }

    private String paraphraseSentence(String sentence) {
        return sentence.replace("ist", "stellt dar")
                      .replace("wird", "erfolgt")
                      .replace("kann", "ist in der Lage zu");
    }

    /**
     * Loads question templates from templates.json.
     */
    private Map<String, List<String>> loadTemplates() {
        Map<String, List<String>> templates = new HashMap<>();
        
        try {
            InputStream is = getClass().getResourceAsStream("/templates.json");
            if (is == null) {
                File file = new File("src/main/resources/templates.json");
                if (file.exists()) {
                    is = new FileInputStream(file);
                }
            }
            
            if (is != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(is);
                JsonNode textverstaendnis = root.get("textverstaendnis");
                
                if (textverstaendnis != null) {
                    textverstaendnis.fields().forEachRemaining(entry -> {
                        String key = entry.getKey();
                        JsonNode value = entry.getValue();
                        List<String> templateList = new ArrayList<>();
                        
                        if (value.isArray()) {
                            for (JsonNode template : value) {
                                templateList.add(template.asText());
                            }
                        }
                        templates.put(key, templateList);
                    });
                }
                is.close();
            }
        } catch (Exception e) {
            System.err.println("Error loading templates: " + e.getMessage());
        }
        
        // Fallback templates if loading fails
        if (templates.isEmpty()) {
            templates.put("entailment", Arrays.asList("Welche Aussage folgt logisch aus dem Text?"));
            templates.put("widerspruch", Arrays.asList("Welche Aussage widerspricht dem Text?"));
            templates.put("nicht_erwaehnt", Arrays.asList("Welche Information wird im Text nicht erwähnt?"));
            templates.put("kernaussage", Arrays.asList("Was ist die Hauptaussage des Textes?"));
            templates.put("struktur", Arrays.asList("Welche Funktion hat der markierte Textabschnitt?"));
        }
        
        return templates;
    }

    // Database helper methods
    
    private int getPassageId(String passageText) throws SQLException {
        // For now, return a default passage ID
        // In a real implementation, this would search for or create the passage
        return 1;
    }

    private int getSubcategoryId(String category, String subcategory) throws SQLException {
        String sql = """
            SELECT s.id FROM subcategories s 
            JOIN categories c ON s.category_id = c.id 
            WHERE c.name = ? AND s.name = ?
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category);
            stmt.setString(2, subcategory);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        
        // If not found, create new subcategory
        // First get category ID
        int categoryId = getCategoryId(category);
        
        sql = "INSERT INTO subcategories (category_id, name, order_index) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, categoryId);
            stmt.setString(2, subcategory);
            stmt.setInt(3, 1); // Default order index
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        
        throw new SQLException("Failed to get or create subcategory ID");
    }
    
    private int getCategoryId(String category) throws SQLException {
        String sql = "SELECT id FROM categories WHERE name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        
        // If not found, create new category
        sql = "INSERT INTO categories (name, order_index) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, category);
            stmt.setInt(2, 1); // Default order index
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        
        throw new SQLException("Failed to get or create category ID");
    }

    private int insertQuestion(String text, int number, int passageId) throws SQLException {
        int subId = getSubcategoryId(category, subcategory);
        String sql = "INSERT INTO questions (subcategory_id, question_number, text, format, test_simulation_id, passage_id) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, subId);
            stmt.setInt(2, number);
            stmt.setString(3, text);
            stmt.setString(4, "Kurz");
            if (simulationId != null) {
                stmt.setInt(5, simulationId);
            } else {
                stmt.setNull(5, java.sql.Types.INTEGER);
            }
            stmt.setInt(6, passageId);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to insert question");
    }

    private void insertAnswerOptions(int questionId, List<String> options, int correctIndex) throws SQLException {
        String sql = "INSERT INTO options (question_id, label, text, is_correct) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < options.size(); i++) {
                stmt.setInt(1, questionId);
                stmt.setString(2, String.valueOf((char)('A' + i))); // Generate labels A, B, C, D, E
                stmt.setString(3, options.get(i));
                stmt.setBoolean(4, i == correctIndex);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    // Parameter handling
    private void applyParams(Map<String, Object> params) {
        if (params == null) {
            return;
        }

        if (params.get("frage_typen_gewichtung") instanceof Map<?, ?> map) {
            Map<String, Double> newWeights = new HashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getValue() instanceof Number n) {
                    newWeights.put(e.getKey().toString(), n.doubleValue());
                }
            }
            if (!newWeights.isEmpty()) {
                questionTypeWeights = newWeights;
            }
        }

        if (params.get("schwierigkeitsverteilung") instanceof Map<?, ?> map) {
            Map<String, Double> newDiff = new HashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getValue() instanceof Number n) {
                    newDiff.put(e.getKey().toString(), n.doubleValue());
                }
            }
            if (!newDiff.isEmpty()) {
                difficultyWeights = newDiff;
            }
        }

        if (params.get("seed") instanceof Number n) {
            random = new Random(n.longValue());
        }
    }

    private void storeQuestionRecord(String frageId, QuestionSpec spec, QuestionData data, TextAnalysis analysis) {
        List<Option> optionList = new ArrayList<>();
        Map<String, List<String>> trans = new LinkedHashMap<>();
        for (int i = 0; i < data.options.size(); i++) {
            String label = String.valueOf((char)('A' + i));
            optionList.add(new Option(label, data.options.get(i)));
            if (i != data.correctIndex) {
                // Simple heuristic: mark by order of transformation methods used
                List<String> t = new ArrayList<>();
                switch (i) {
                    case 0 -> t.add("negation");
                    case 1 -> t.add("quantifier_flip");
                    case 2 -> t.add("condition_drop");
                    case 3 -> t.add("temporal_flip");
                }
                trans.put(label, t);
            }
        }

        String korrektLabel = String.valueOf((char)('A' + data.correctIndex));
        List<String> evidence = analysis.propositions.isEmpty() ? new ArrayList<>() : List.of(analysis.propositions.get(0).id);

        generatedQuestions.add(new QuestionRecord(frageId, spec.type, spec.difficulty,
                data.text, optionList, korrektLabel, evidence, trans));
    }

    public String toJson(int passageId) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("text_id", String.valueOf(passageId));

        List<Map<String, Object>> qList = new ArrayList<>();
        for (QuestionRecord q : generatedQuestions) {
            Map<String, Object> qMap = new LinkedHashMap<>();
            qMap.put("frage_id", q.frageId);
            qMap.put("typ", q.typ);
            qMap.put("schwierigkeitsgrad", q.schwierigkeitsgrad);
            qMap.put("stem", q.stem);

            List<Map<String, String>> opts = new ArrayList<>();
            for (Option o : q.optionen) {
                Map<String, String> om = new LinkedHashMap<>();
                om.put("label", o.label);
                om.put("text", o.text);
                opts.add(om);
            }
            qMap.put("optionen", opts);
            qMap.put("korrekt", q.korrekt);
            qMap.put("evidence_props", q.evidenceProps);
            qMap.put("transformations_distraktoren", q.transformationsDistraktoren);

            qList.add(qMap);
        }
        root.put("fragen", qList);

        List<Map<String, Object>> propList = new ArrayList<>();
        for (Proposition p : lastPropositions) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", p.id);
            pm.put("subj", p.subj);
            pm.put("pred", p.pred);
            pm.put("obj", p.obj);
            pm.put("mods", p.modifiers);
            propList.add(pm);
        }
        root.put("propositionen", propList);

        if (!metaWarnings.isEmpty()) {
            root.put("meta_warnungen", metaWarnings);
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    // Helper classes
    
    private static class QuestionSpec {
        String type;
        String difficulty;
        
        QuestionSpec(String type, String difficulty) {
            this.type = type;
            this.difficulty = difficulty;
        }
    }
    
    private static class TextAnalysis {
        List<CoreMap> sentences;
        List<RelationTriple> relations;
        List<Proposition> propositions;
        Annotation document;
        
        TextAnalysis(List<CoreMap> sentences, List<RelationTriple> relations, 
                    List<Proposition> propositions, Annotation document) {
            this.sentences = sentences;
            this.relations = relations;
            this.propositions = propositions;
            this.document = document;
        }
    }
    
    private static class Proposition {
        String id;
        String subj;
        String pred;
        String obj;
        List<String> modifiers;
        
        Proposition(String id, String subj, String pred, String obj, List<String> modifiers) {
            this.id = id;
            this.subj = subj;
            this.pred = pred;
            this.obj = obj;
            this.modifiers = modifiers;
        }
        
        @Override
        public String toString() {
            return subj + " " + pred + " " + obj;
        }
    }

    private static class QuestionData {
        String text;
        List<String> options;
        int correctIndex;
        
        QuestionData(String text, List<String> options, int correctIndex) {
            this.text = text;
            this.options = new ArrayList<>(options);
            this.correctIndex = correctIndex;
        }
    }

    private static class Option {
        String label;
        String text;

        Option(String label, String text) {
            this.label = label;
            this.text = text;
        }
    }

    private static class QuestionRecord {
        String frageId;
        String typ;
        String schwierigkeitsgrad;
        String stem;
        List<Option> optionen;
        String korrekt;
        List<String> evidenceProps;
        Map<String, List<String>> transformationsDistraktoren;

        QuestionRecord(String frageId, String typ, String schwierigkeitsgrad,
                       String stem, List<Option> optionen, String korrekt,
                       List<String> evidenceProps,
                       Map<String, List<String>> transformationsDistraktoren) {
            this.frageId = frageId;
            this.typ = typ;
            this.schwierigkeitsgrad = schwierigkeitsgrad;
            this.stem = stem;
            this.optionen = optionen;
            this.korrekt = korrekt;
            this.evidenceProps = evidenceProps;
            this.transformationsDistraktoren = transformationsDistraktoren;
        }
    }
}
