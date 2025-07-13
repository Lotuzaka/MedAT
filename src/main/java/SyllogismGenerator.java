/**
 * Generator for Syllogism questions used in logical reasoning tests.
 * Creates questions based on predefined syllogism models and a word list.
 */
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

public class SyllogismGenerator {

  // Constants
  private static final String WORDLIST_PATH = "src/main/resources/lists/wortliste.docx";
  private static final String NO_VALID_ANSWER = "Keine Antwort ist richtig.";
  private static final int OPTION_COUNT = 5;
  private static final int WORDS_PER_QUESTION = 3;

  // Instance variables
  private final Connection conn;
  private final QuestionDAO questionDAO;
  private final OptionDAO optionDAO;
  private final String category;
  private final String subcategory;
  private final Integer simulationId;
  private final Random random;
  private final List<SyllogismModel> syllogismModels;

  /**
   * Constructor for SyllogismGenerator.
   * 
   * @param conn Database connection
   * @param category Question category (e.g., "KFF")
   * @param subcategory Question subcategory (e.g., "Implikationen erkennen")
   * @param simulationId Current simulation ID
   */
  public SyllogismGenerator(Connection conn, String category, String subcategory, Integer simulationId) {
    this.conn = conn;
    this.category = category;
    this.subcategory = subcategory;
    this.simulationId = simulationId;
    this.questionDAO = new QuestionDAO(conn);
    this.optionDAO = new OptionDAO(conn);
    this.random = new Random();
    this.syllogismModels = initializeSyllogismModels();
  }

  /**
   * Generates the specified number of syllogism questions and inserts them into the database.
   * 
   * @param numberOfQuestions Number of questions to generate
   * @throws SQLException If database operation fails
   * @throws IOException If word list cannot be read
   */
  public void execute(int numberOfQuestions) throws SQLException, IOException {
    List<String> wordList = readWordList(WORDLIST_PATH);
    Collections.shuffle(wordList);

    int subcategoryId = questionDAO.getSubcategoryId(category, subcategory);
    int nextQuestionNumber = questionDAO.getNextQuestionNumber(simulationId, subcategoryId);
    int wordIndex = 0;

    for (int i = 0; i < numberOfQuestions; i++) {
      wordIndex = ensureSufficientWords(wordList, wordIndex);
      
      String[] words = extractWords(wordList, wordIndex);
      wordIndex += WORDS_PER_QUESTION;
      
      SyllogismModel model = selectRandomModel();
      QuestionData questionData = generateQuestion(model, words[0], words[1], words[2]);
      
      insertQuestionIntoDatabase(questionData, nextQuestionNumber);
      nextQuestionNumber++;
    }
  }

  /**
   * Converts an option index to its corresponding letter label (A, B, C, D, E).
   */
  private String getOptionLabel(int index) {
    return String.valueOf((char) ('A' + index));
  }

  /**
   * Reads the word list from a Word document.
   * 
   * @param filename Path to the Word document
   * @return List of words extracted from the document
   * @throws IOException If the file cannot be read
   */
  private List<String> readWordList(String filename) throws IOException {
    List<String> wordList = new ArrayList<>();
    
    try (InputStream is = new java.io.FileInputStream(filename);
         XWPFDocument doc = new XWPFDocument(is)) {
      
      for (XWPFParagraph para : doc.getParagraphs()) {
        String text = para.getText();
        if (text != null && !text.trim().isEmpty()) {
          String[] words = text.split("\\s+");
          for (String word : words) {
            word = word.trim();
            if (!word.isEmpty()) {
              wordList.add(word);
            }
          }
        }
      }
    }
    
    return wordList;
  }

  private List<SyllogismModel> initializeSyllogismModels() {
    List<SyllogismModel> models = new ArrayList<>();

    // I. Universal Affirmative Premises (Alle-Alle / Alle-Einige / Alle-Kein /
    // Alle-Einige nicht)
    // Major Premise: All A are B.

    // Minor Premise: All B are C.
    models.add(new SyllogismModel(
        "Alle {A} sind {B}.",
        Arrays.asList("Alle {B} sind {C}."),
        Arrays.asList("Alle {A} sind {C}."),
        Arrays.asList("Einige {A} sind {C}."),
        false,
        "All A are B; All B are C"));

    // Minor Premise: All C are B.
    models.add(new SyllogismModel(
        "Alle {A} sind {B}.",
        Arrays.asList("Alle {C} sind {B}."),
        new ArrayList<>(),
        new ArrayList<>(),
        true,
        "All A are B; All C are B"));

    // Minor Premise: Some B are C.
    models.add(new SyllogismModel(
        "Alle {A} sind {B}.",
        Arrays.asList("Einige {B} sind {C}."),
        new ArrayList<>(),
        new ArrayList<>(),
        true,
        "All A are B; Some B are C"));

    // Minor Premise: Some C are B.
    models.add(new SyllogismModel(
        "Alle {A} sind {B}.",
        Arrays.asList("Einige {C} sind {B}."),
        new ArrayList<>(),
        new ArrayList<>(),
        true,
        "All A are B; Some C are B"));

    // Minor Premise: All B are not C.
    models.add(new SyllogismModel(
        "Alle {A} sind {B}.",
        Arrays.asList("Alle {B} sind keine {C}."),
        Arrays.asList("Alle {A} sind keine {C}.", "Alle {C} sind keine {A}."),
        Arrays.asList("Einige {A} sind keine {C}.", "Einige {C} sind keine {A}."),
        false,
        "All A are B; All B are not C"));

    // Minor Premise: All C are not B.
    models.add(new SyllogismModel(
        "Alle {A} sind {B}.",
        Arrays.asList("Alle {C} sind keine {B}."),
        Arrays.asList("Alle {A} sind keine {C}.", "Alle {C} sind keine {A}."),
        Arrays.asList("Einige {A} sind keine {C}.", "Einige {C} sind keine {A}."),
        false,
        "All A are B; All C are not B"));

    // Minor Premise: Some B are not C.
    models.add(new SyllogismModel(
        "Alle {A} sind {B}.",
        Arrays.asList("Einige {B} sind keine {C}."),
        new ArrayList<>(),
        new ArrayList<>(),
        true,
        "All A are B; Some B are not C"));

    // Minor Premise: Some C are not B.
    models.add(new SyllogismModel(
        "Alle {A} sind {B}.",
        Arrays.asList("Einige {C} sind keine {B}."),
        new ArrayList<>(),
        new ArrayList<>(),
        true,
        "All A are B; Some C are not B"));

    // Major Premise: All B are A.

    // Minor Premise: All B are C.
    models.add(new SyllogismModel(
        "Alle {B} sind {A}.",
        Arrays.asList("Alle {B} sind {C}."),
        new ArrayList<>(),
        Arrays.asList("Einige {A} sind {C}.", "Einige {C} sind {A}."),
        false,
        "All B are A; All B are C"));

    // Minor Premise: Some B are C.
    models.add(new SyllogismModel(
        "Alle {B} sind {A}.",
        Arrays.asList("Einige {B} sind {C}."),
        new ArrayList<>(),
        Arrays.asList("Einige {A} sind {C}.", "Einige {C} sind {A}."),
        false,
        "All B are A; Some B are C"));

    // Minor Premise: Some C are B.
    models.add(new SyllogismModel(
        "Alle {B} sind {A}.",
        Arrays.asList("Einige {C} sind {B}."),
        new ArrayList<>(),
        Arrays.asList("Einige {A} sind {C}.", "Einige {C} sind {A}."),
        false,
        "All B are A; Some C are B"));

    // Minor Premise: All B are not C.
    models.add(new SyllogismModel(
        "Alle {B} sind {A}.",
        Arrays.asList("Alle {B} sind keine {C}."),
        new ArrayList<>(),
        Arrays.asList("Einige {A} sind keine {C}."),
        false,
        "All B are A; All B are not C"));

    // Minor Premise: All C are not B.
    models.add(new SyllogismModel(
        "Alle {B} sind {A}.",
        Arrays.asList("Alle {C} sind keine {B}."),
        new ArrayList<>(),
        Arrays.asList("Einige {A} sind keine {C}."),
        false,
        "All B are A; All C are not B"));

    // Minor Premise: Some B are not C.
    models.add(new SyllogismModel(
        "Alle {B} sind {A}.",
        Arrays.asList("Einige {B} sind keine {C}."),
        new ArrayList<>(),
        Arrays.asList("Einige {A} sind keine {C}."),
        false,
        "All B are A; Some B are not C"));

    // Minor Premise: Some C are not B.
    models.add(new SyllogismModel(
        "Alle {B} sind {A}.",
        Arrays.asList("Einige {C} sind keine {B}."),
        new ArrayList<>(),
        new ArrayList<>(),
        true,
        "All B are A; Some C are not B"));

    // Major Premise: All C are B.

    // Minor Premise: All B are A.
    models.add(new SyllogismModel(
        "Alle {C} sind {B}.",
        Arrays.asList("Alle {B} sind {A}."),
        Arrays.asList("Alle {C} sind {A}."),
        Arrays.asList("Einige {C} sind {A}.", "Einige {A} sind {C}."),
        false,
        "All C are B; All B are A"));

    // II. Particular Affirmative and Negative Premises (Einige-Alle / Einige-Kein)
    // Major Premise: Some A are B.

    // Minor Premise: All B are C.
    models.add(new SyllogismModel(
        "Einige {A} sind {B}.",
        Arrays.asList("Alle {B} sind {C}."),
        new ArrayList<>(),
        Arrays.asList("Einige {A} sind {C}.", "Einige {C} sind {A}."),
        false,
        "Some A are B; All B are C"));

    // Minor Premise: All C are B.
    models.add(new SyllogismModel(
        "Einige {A} sind {B}.",
        Arrays.asList("Alle {C} sind {B}."),
        new ArrayList<>(),
        new ArrayList<>(),
        true,
        "Some A are B; All C are B"));

    // Minor Premise: All B are not C.
    models.add(new SyllogismModel(
        "Einige {A} sind {B}.",
        Arrays.asList("Alle {B} sind keine {C}."),
        new ArrayList<>(),
        Arrays.asList("Einige {A} sind keine {C}."),
        false,
        "Some A are B; All B are not C"));

    // Minor Premise: All C are not B.
    models.add(new SyllogismModel(
        "Einige {A} sind {B}.",
        Arrays.asList("Alle {C} sind keine {B}."),
        new ArrayList<>(),
        Arrays.asList("Einige {A} sind keine {C}."),
        false,
        "Some A are B; All C are not B"));

    // Major Premise: Some B are A.

    // Minor Premise: All B are C.
    models.add(new SyllogismModel(
        "Einige {B} sind {A}.",
        Arrays.asList("Alle {B} sind {C}."),
        new ArrayList<>(),
        Arrays.asList("Einige {A} sind {C}.", "Einige {C} sind {A}."),
        false,
        "Some B are A; All B are C"));

    // Minor Premise: All C are B.
    models.add(new SyllogismModel(
        "Einige {B} sind {A}.",
        Arrays.asList("Alle {C} sind {B}."),
        new ArrayList<>(),
        new ArrayList<>(),
        true,
        "Some B are A; All C are B"));

    // Minor Premise: All B are not C.
    models.add(new SyllogismModel(
        "Einige {B} sind {A}.",
        Arrays.asList("Alle {B} sind keine {C}."),
        new ArrayList<>(),
        Arrays.asList("Einige {A} sind keine {C}."),
        false,
        "Some B are A; All B are not C"));

    // Minor Premise: All C are not B.
    models.add(new SyllogismModel(
        "Einige {B} sind {A}.",
        Arrays.asList("Alle {C} sind keine {B}."),
        new ArrayList<>(),
        Arrays.asList("Einige {A} sind keine {C}."),
        false,
        "Some B are A; All C are not B"));

    // III. Universal Negative Premises (Kein-Alle / Kein-Einige)
    // Major Premise: All A are not B.

    // Minor Premise: All B are C.
    models.add(new SyllogismModel(
        "Alle {A} sind keine {B}.",
        Arrays.asList("Alle {B} sind {C}."),
        new ArrayList<>(),
        Arrays.asList("Einige {C} sind keine {A}."),
        false,
        "All A are not B; All B are C"));

    // Minor Premise: All C are B.
    models.add(new SyllogismModel(
        "Alle {A} sind keine {B}.",
        Arrays.asList("Alle {C} sind {B}."),
        Arrays.asList("Alle {A} sind keine {C}.", "Alle {C} sind keine {A}."),
        Arrays.asList("Einige {A} sind keine {C}.", "Einige {C} sind keine {A}."),
        false,
        "All A are not B; All C are B"));

    // Minor Premise: Some B are C.
    models.add(new SyllogismModel(
        "Alle {A} sind keine {B}.",
        Arrays.asList("Einige {B} sind {C}."),
        new ArrayList<>(),
        Arrays.asList("Einige {C} sind keine {A}."),
        false,
        "All A are not B; Some B are C"));

    // Minor Premise: Some C are B.
    models.add(new SyllogismModel(
        "Alle {A} sind keine {B}.",
        Arrays.asList("Einige {C} sind {B}."),
        new ArrayList<>(),
        Arrays.asList("Einige {C} sind keine {A}."),
        false,
        "All A are not B; Some C are B"));

    // Major Premise: All B are not A.

    // Minor Premise: All B are C.
    models.add(new SyllogismModel(
        "Alle {B} sind keine {A}.",
        Arrays.asList("Alle {B} sind {C}."),
        new ArrayList<>(),
        Arrays.asList("Einige {C} sind keine {A}."),
        false,
        "All B are not A; All B are C"));

    // Minor Premise: All C are B.
    models.add(new SyllogismModel(
        "Alle {B} sind keine {A}.",
        Arrays.asList("Alle {C} sind {B}."),
        Arrays.asList("Alle {A} sind keine {C}.", "Alle {C} sind keine {A}."),
        Arrays.asList("Einige {A} sind keine {C}.", "Einige {C} sind keine {A}."),
        false,
        "All B are not A; All C are B"));

    // Minor Premise: Some B are C.
    models.add(new SyllogismModel(
        "Alle {B} sind keine {A}.",
        Arrays.asList("Einige {B} sind {C}."),
        new ArrayList<>(),
        Arrays.asList("Einige {C} sind keine {A}."),
        false,
        "All B are not A; Some B are C"));

    // Minor Premise: Some C are B.
    models.add(new SyllogismModel(
        "Alle {B} sind keine {A}.",
        Arrays.asList("Einige {C} sind {B}."),
        new ArrayList<>(),
        Arrays.asList("Einige {C} sind keine {A}."),
        false,
        "All B are not A; Some C are B"));

    // IV. Particular Negative Premises (EinigeKein-Alle)
    // Major Premise: Some A are not B.

    // Minor Premise: All B are C.
    models.add(new SyllogismModel(
        "Einige {A} sind keine {B}.",
        Arrays.asList("Alle {B} sind {C}."),
        new ArrayList<>(),
        new ArrayList<>(),
        true,
        "Some A are not B; All B are C"));

    // Minor Premise: All C are B.
    models.add(new SyllogismModel(
        "Einige {A} sind keine {B}.",
        Arrays.asList("Alle {C} sind {B}."),
        new ArrayList<>(),
        Arrays.asList("Einige {A} sind keine {C}."),
        false,
        "Some A are not B; All C are B"));

    // Major Premise: Some B are not A.

    // Minor Premise: All B are C.
    models.add(new SyllogismModel(
        "Einige {B} sind keine {A}.",
        Arrays.asList("Alle {B} sind {C}."),
        new ArrayList<>(),
        Arrays.asList("Einige {C} sind keine {A}."),
        false,
        "Some B are not A; All B are C"));

    // Minor Premise: All C are B.
    models.add(new SyllogismModel(
        "Einige {B} sind keine {A}.",
        Arrays.asList("Alle {C} sind {B}."),
        new ArrayList<>(),
        new ArrayList<>(),
        true,
        "Some B are not A; All C are B"));

    return models;
  }

  /**
   * Ensures there are enough words available for the next question.
   * Reshuffles the word list if necessary.
   */
  private int ensureSufficientWords(List<String> wordList, int currentIndex) {
    if (currentIndex + WORDS_PER_QUESTION > wordList.size()) {
      Collections.shuffle(wordList);
      return 0;
    }
    return currentIndex;
  }

  /**
   * Extracts the required number of words from the word list.
   */
  private String[] extractWords(List<String> wordList, int startIndex) {
    return new String[] {
        wordList.get(startIndex),
        wordList.get(startIndex + 1),
        wordList.get(startIndex + 2)
    };
  }

  /**
   * Selects a random syllogism model.
   */
  private SyllogismModel selectRandomModel() {
    return syllogismModels.get(random.nextInt(syllogismModels.size()));
  }

  /**
   * Generates a complete question with premises, conclusion, and options.
   */
  private QuestionData generateQuestion(SyllogismModel model, String wordA, String wordB, String wordC) {
    String majorPremise = replaceVariables(model.getMajorPremiseTemplate(), wordA, wordB, wordC);
    String minorPremise = generateMinorPremise(model, wordA, wordB, wordC);
    String correctConclusion = generateCorrectConclusion(model, wordA, wordB, wordC);
    
    List<String> options = generateOptions(correctConclusion, wordA, wordB, wordC);
    int correctOptionIndex = findCorrectOptionIndex(options, correctConclusion);
    
    String questionText = majorPremise + "\n" + minorPremise;
    
    return new QuestionData(questionText, options, correctOptionIndex);
  }

  /**
   * Generates the minor premise from the model templates.
   */
  private String generateMinorPremise(SyllogismModel model, String wordA, String wordB, String wordC) {
    List<String> templates = model.getMinorPremiseTemplates();
    String template = templates.get(random.nextInt(templates.size()));
    return replaceVariables(template, wordA, wordB, wordC);
  }

  /**
   * Generates the correct conclusion based on the model.
   */
  private String generateCorrectConclusion(SyllogismModel model, String wordA, String wordB, String wordC) {
    if (model.isHasNoValidConclusion()) {
      return NO_VALID_ANSWER;
    }
    
    List<String> possibleConclusions = new ArrayList<>();
    possibleConclusions.addAll(model.getStrongConclusionTemplates());
    possibleConclusions.addAll(model.getWeakConclusionTemplates());
    
    String template = possibleConclusions.get(random.nextInt(possibleConclusions.size()));
    return replaceVariables(template, wordA, wordB, wordC);
  }

  /**
   * Generates all options for the question.
   */
  private List<String> generateOptions(String correctConclusion, String wordA, String wordB, String wordC) {
    List<String> options = new ArrayList<>();
    
    if (NO_VALID_ANSWER.equals(correctConclusion)) {
      addDistractorsToOptions(options, wordA, wordB, wordC, correctConclusion, 4);
      options.add(NO_VALID_ANSWER);
    } else {
      options.add(correctConclusion);
      addDistractorsToOptions(options, wordA, wordB, wordC, correctConclusion, 3);
      Collections.shuffle(options);
      options.add(NO_VALID_ANSWER);
    }
    
    return options;
  }

  /**
   * Adds distractor options to the options list.
   */
  private void addDistractorsToOptions(List<String> options, String wordA, String wordB, String wordC, 
                                      String correctConclusion, int count) {
    List<String> distractors = generateDistractorConclusions(wordA, wordB, wordC, correctConclusion);
    
    while (options.size() < count && !distractors.isEmpty()) {
      String distractor = distractors.get(random.nextInt(distractors.size()));
      if (!options.contains(distractor)) {
        options.add(distractor);
      }
      distractors.remove(distractor);
    }
  }

  /**
   * Finds the index of the correct option in the options list.
   */
  private int findCorrectOptionIndex(List<String> options, String correctConclusion) {
    if (NO_VALID_ANSWER.equals(correctConclusion)) {
      return options.size() - 1; // Last option (E)
    }
    return options.indexOf(correctConclusion);
  }

  /**
   * Replaces variable placeholders in templates with actual words.
   */
  private String replaceVariables(String template, String wordA, String wordB, String wordC) {
    return template.replace("{A}", wordA)
                  .replace("{B}", wordB)
                  .replace("{C}", wordC);
  }

  /**
   * Inserts the generated question and options into the database.
   */
  private void insertQuestionIntoDatabase(QuestionData questionData, int questionNumber) throws SQLException {
    MedatoninDB.debugLog("Syllogism", "Question: " + questionData.questionText);
    
    int questionId = questionDAO.insertQuestion(category, subcategory, questionData.questionText, 
                                               questionNumber, simulationId);
    
    for (int i = 0; i < questionData.options.size(); i++) {
      String optionLabel = getOptionLabel(i);
      String optionText = questionData.options.get(i);
      boolean isCorrect = (i == questionData.correctOptionIndex);
      
      MedatoninDB.debugLog("Syllogism", "Option: " + optionText);
      optionDAO.insertOption(questionId, optionLabel, optionText, isCorrect);
    }
  }

  /**
   * Generates distractor conclusions that are logically incorrect.
   * 
   * @param wordA First word variable
   * @param wordB Second word variable  
   * @param wordC Third word variable
   * @param correctConclusion The correct conclusion to exclude
   * @return List of distractor conclusions
   */
  private List<String> generateDistractorConclusions(String wordA, String wordB, String wordC, String correctConclusion) {
    Set<String> distractorSet = new LinkedHashSet<>(); // Preserve order and avoid duplicates
    
    // Generate all possible conclusion patterns
    String[] patterns = {
        "Alle {A} sind {C}.",
        "Einige {A} sind {C}.", 
        "Alle {A} sind keine {C}.",
        "Einige {A} sind keine {C}.",
        "Keine {A} sind {C}.",
        "Alle {C} sind {A}.",
        "Einige {C} sind {A}.",
        "Alle {C} sind keine {A}.",
        "Einige {C} sind keine {A}.",
        "Keine {C} sind {A}."
    };
    
    for (String pattern : patterns) {
      String distractor = replaceVariables(pattern, wordA, wordB, wordC);
      if (!distractor.equals(correctConclusion)) {
        distractorSet.add(distractor);
      }
    }
    
    return new ArrayList<>(distractorSet);
  }

  /**
   * Inner class to hold question data.
   */
  private static class QuestionData {
    final String questionText;
    final List<String> options;
    final int correctOptionIndex;
    
    QuestionData(String questionText, List<String> options, int correctOptionIndex) {
      this.questionText = questionText;
      this.options = options;
      this.correctOptionIndex = correctOptionIndex;
    }
  }

  /**
   * Model class representing a syllogism template with premises and conclusions.
   */
  public static class SyllogismModel {
    private final String majorPremiseTemplate;
    private final List<String> minorPremiseTemplates;
    private final List<String> strongConclusionTemplates;
    private final List<String> weakConclusionTemplates;
    private final boolean hasNoValidConclusion;
    private final String description;

    /**
     * Constructor for SyllogismModel.
     * 
     * @param majorPremiseTemplate Template for the major premise
     * @param minorPremiseTemplates List of templates for minor premises
     * @param strongConclusionTemplates List of strong conclusion templates
     * @param weakConclusionTemplates List of weak conclusion templates  
     * @param hasNoValidConclusion Whether this model has no valid logical conclusion
     * @param description Human-readable description of the model
     */
    public SyllogismModel(String majorPremiseTemplate, List<String> minorPremiseTemplates,
        List<String> strongConclusionTemplates, List<String> weakConclusionTemplates,
        boolean hasNoValidConclusion, String description) {
      this.majorPremiseTemplate = majorPremiseTemplate;
      this.minorPremiseTemplates = new ArrayList<>(minorPremiseTemplates);
      this.strongConclusionTemplates = new ArrayList<>(strongConclusionTemplates);
      this.weakConclusionTemplates = new ArrayList<>(weakConclusionTemplates);
      this.hasNoValidConclusion = hasNoValidConclusion;
      this.description = description;
    }

    // Getters
    public String getMajorPremiseTemplate() {
      return majorPremiseTemplate;
    }

    public List<String> getMinorPremiseTemplates() {
      return minorPremiseTemplates;
    }

    public List<String> getStrongConclusionTemplates() {
      return strongConclusionTemplates;
    }

    public List<String> getWeakConclusionTemplates() {
      return weakConclusionTemplates;
    }

    public boolean isHasNoValidConclusion() {
      return hasNoValidConclusion;
    }

    public String getDescription() {
      return description;
    }
  }
}