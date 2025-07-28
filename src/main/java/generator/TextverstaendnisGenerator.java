import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * Generator for Textverständnis questions using a passage based approach.
 * The implementation mirrors the structure of {@link SyllogismGenerator}.
 */
public class TextverstaendnisGenerator {

    private final Connection conn;
    private final QuestionDAO questionDAO;
    private final OptionDAO optionDAO;
    private final String category;
    private final String subcategory;
    private final Integer simulationId;

    private final StanfordCoreNLP pipeline;
    private final Map<String, List<String>> templates;
    private final Random random = new Random();

    /**
     * Constructs the generator with DB connection and context.
     */
    public TextverstaendnisGenerator(Connection conn, String category, String subcategory, Integer simulationId) throws IOException {
        this.conn = conn;
        this.category = category;
        this.subcategory = subcategory;
        this.simulationId = simulationId;
        this.questionDAO = new QuestionDAO(conn);
        this.optionDAO = new OptionDAO(conn);

        // Load question stems
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("textverstaendnis/templates.json")) {
            if (is == null) {
                throw new IOException("templates.json not found");
            }
            this.templates = mapper.readValue(is, new TypeReference<>() {});
        }

        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse,coref,openie");
        props.setProperty("coref.algorithm", "neural");
        this.pipeline = new StanfordCoreNLP(props);
    }

    /** Reads the passage text from the database. */
    private String loadPassage(int passageId) throws SQLException {
        String sql = "SELECT text FROM passages WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, passageId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        throw new SQLException("Passage not found: " + passageId);
    }

    /**
     * Generate questions for the provided passage.
     * @param count number of questions
     * @param passageId passage identifier from passages table
     */
    public void execute(int count, int passageId) throws SQLException, IOException {
        String passage = loadPassage(passageId);

        Annotation doc = new Annotation(passage);
        pipeline.annotate(doc);

        List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
        List<RelationTriple> triples = new ArrayList<>();
        for (CoreMap s : sentences) {
            Collection<RelationTriple> rels = s.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
            if (rels != null) {
                triples.addAll(rels);
            }
        }

        int subId = questionDAO.getSubcategoryId(category, subcategory);
        int qNum = questionDAO.getNextQuestionNumber(simulationId, subId);

        for (int i = 0; i < count; i++) {
            String type = new ArrayList<>(templates.keySet()).get(random.nextInt(templates.size()));
            String stem = templates.get(type).get(random.nextInt(templates.get(type).size()));
            QuestionData q = buildQuestion(stem, type, sentences, triples);

            int qId = insertQuestion(q.text, qNum, passageId);
            for (int j = 0; j < q.options.size(); j++) {
                String label = String.valueOf((char)('A' + j));
                boolean isCorrect = (j == q.correctIndex);
                optionDAO.insertOption(qId, label, q.options.get(j), isCorrect);
            }
            qNum++;
        }
    }

    /**
     * Creates a single question using simple heuristics.
     */
    private QuestionData buildQuestion(String stem, String type, List<CoreMap> sentences, List<RelationTriple> triples) {
        String question;
        String correct;
        List<String> distractors = new ArrayList<>();

        if ("Nicht erwähnt".equals(type)) {
            question = stem;
            correct = "Keine der anderen Aussagen";
            for (int i = 0; i < Math.min(4, sentences.size()); i++) {
                distractors.add(sentences.get(i).toString());
            }
        } else if ("Kernaussage".equals(type)) {
            question = stem;
            correct = sentences.isEmpty() ? "" : sentences.get(0).toString();
            for (int i = 1; i < sentences.size() && distractors.size() < 4; i++) {
                distractors.add(sentences.get(i).toString());
            }
        } else if ("Ursache-Wirkung".equals(type) && !triples.isEmpty()) {
            RelationTriple t = triples.get(random.nextInt(triples.size()));
            String sent = t.toString();
            question = stem.replace("{SENTENCE}", sent);
            correct = t.subjectGloss() + " " + t.relationGloss() + " " + t.objectGloss();
            for (RelationTriple other : triples) {
                String cand = other.subjectGloss() + " " + other.relationGloss() + " " + other.objectGloss();
                if (!cand.equals(correct) && distractors.size() < 4) {
                    distractors.add(cand);
                }
            }
        } else {
            String sent = sentences.isEmpty() ? "" : sentences.get(random.nextInt(sentences.size())).toString();
            question = stem.replace("{SENTENCE}", sent);
            correct = sent;
            for (CoreMap s : sentences) {
                String txt = s.toString();
                if (!txt.equals(correct) && distractors.size() < 4) {
                    distractors.add(txt);
                }
            }
        }

        while (distractors.size() < 4) {
            distractors.add("...");
        }

        List<String> options = new ArrayList<>();
        options.add(correct);
        options.addAll(distractors.subList(0, 4));
        Collections.shuffle(options);

        int correctIndex = options.indexOf(correct);
        return new QuestionData(question, options, correctIndex);
    }

    /** Inserts the question into the DB including the passage_id column. */
    private int insertQuestion(String text, int number, int passageId) throws SQLException {
        int subId = questionDAO.getSubcategoryId(category, subcategory);
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

    private static class QuestionData {
        final String text;
        final List<String> options;
        final int correctIndex;
        QuestionData(String t, List<String> o, int i) { this.text = t; this.options = o; this.correctIndex = i; }
    }
}

