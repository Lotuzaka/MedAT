
// Java program to do level order traversal
// of a generic tree
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SyllogismGenerator {

  private Connection conn;
  private QuestionDAO questionDAO;
  private OptionDAO optionDAO;
  private String category; // e.g., "KFF"
  private String subcategory; // e.g., "Implikationen erkennen"
  private Integer simulationId; // Current simulation ID
  private Random random;
  private List<SyllogismModel> syllogismModels;

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

  public void execute(int numberOfQuestions) throws SQLException, IOException {
    // Read word list from file
    List<String> wordList = readWordList(
        "C:\\Users\\mahmo\\Desktop\\medatonin\\medatonin-datenbank\\src\\main\\resources\\lists\\wortliste.txt");
    Collections.shuffle(wordList);

    int subcategoryId = questionDAO.getSubcategoryId(category, subcategory);
    int nextQuestionNumber = questionDAO.getNextQuestionNumber(simulationId, subcategoryId);

    int wordIndex = 0; // To keep track of used words

    for (int i = 0; i < numberOfQuestions; i++) {
      // Select unique words A, B, C
      if (wordIndex + 3 > wordList.size()) {
        // If we've run out of words, reshuffle and reset index
        Collections.shuffle(wordList);
        wordIndex = 0;
      }

      String A = wordList.get(wordIndex++);
      String B = wordList.get(wordIndex++);
      String C = wordList.get(wordIndex++);

      // Select a syllogism model
      SyllogismModel model = syllogismModels.get(random.nextInt(syllogismModels.size()));

      // Generate the major premise
      String majorPremise = model.getMajorPremiseTemplate().replace("{A}", A).replace("{B}", B).replace("{C}", C);

      // Generate the minor premise
      String minorPremiseTemplate = model.getMinorPremiseTemplates()
          .get(random.nextInt(model.getMinorPremiseTemplates().size()));
      String minorPremise = minorPremiseTemplate.replace("{A}", A).replace("{B}", B).replace("{C}", C);

      String correctConclusion = null;
      if (model.isHasNoValidConclusion()) {
        correctConclusion = "Keine Antwort ist richtig.";
      } else {
        // Generate the correct conclusions
        List<String> possibleConclusions = new ArrayList<>();
        possibleConclusions.addAll(model.getStrongConclusionTemplates());
        possibleConclusions.addAll(model.getWeakConclusionTemplates());
        // Randomly select one correct conclusion
        String conclusionTemplate = possibleConclusions.get(random.nextInt(possibleConclusions.size()));
        correctConclusion = conclusionTemplate.replace("{A}", A).replace("{B}", B).replace("{C}", C);
      }

      // Generate distractor conclusions
      List<String> distractors = generateDistractorConclusions(A, B, C, correctConclusion);

      // Prepare options
      List<String> options = new ArrayList<>();
      int correctOptionIndex = -1;

      // If "Keine Antwort ist richtig." is the correct conclusion, place it at option E
      if (correctConclusion.equals("Keine Antwort ist richtig.")) {
        // Add distractors until options.size() == 4
        while (options.size() < 4) {
          String distractor = distractors.get(random.nextInt(distractors.size()));
          if (!options.contains(distractor)) {
            options.add(distractor);
          }
        }
        // Add "Keine Antwort ist richtig." as option E
        options.add("Keine Antwort ist richtig.");
        // Correct option index is 4 (option E)
        correctOptionIndex = 4;
      } else {
        // Add correct conclusion
        options.add(correctConclusion);
        // Add distractors until options.size() == 4
        while (options.size() < 4) {
          String distractor = distractors.get(random.nextInt(distractors.size()));
          if (!options.contains(distractor)) {
            options.add(distractor);
          }
        }
        // Shuffle options A-D
        Collections.shuffle(options);
        // Add "Keine Antwort ist richtig." as option E
        options.add("Keine Antwort ist richtig.");
        // Correct option index is the index of the correct conclusion
        correctOptionIndex = options.indexOf(correctConclusion);
      }

      // Create question text
      String questionText = majorPremise + "\n" + minorPremise;
      // DEBUG: Print question text before DB insert
      MedatoninDB.debugLog("Syllogism", "Question: " + questionText);

      // Insert question into database
      int questionId = questionDAO.insertQuestion(category, subcategory, questionText, nextQuestionNumber,
          simulationId);

      // Insert options into database
      for (int j = 0; j < options.size(); j++) {
        String optionLabel = getOptionLabel(j);
        String optionText = options.get(j);
        boolean isCorrect = (j == correctOptionIndex);
        // DEBUG: Print option text before DB insert
        MedatoninDB.debugLog("Syllogism", "Option: " + optionText);
        optionDAO.insertOption(questionId, optionLabel, optionText, isCorrect);
      }

      nextQuestionNumber++;
    }
  }

  private String getOptionLabel(int index) {
    return String.valueOf((char) ('A' + index));
  }

  private List<String> readWordList(String filename) throws IOException {
    List<String> wordList = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(
        new java.io.InputStreamReader(new java.io.FileInputStream(filename), java.nio.charset.StandardCharsets.UTF_8))) {
      String word;
      int lineNum = 0;
      while ((word = br.readLine()) != null) {
        word = word.trim();
        if (!word.isEmpty()) {
          wordList.add(word);
          // Log first 5 words for encoding check
          if (lineNum < 5) {
            MedatoninDB.debugLog("FileRead", "Line " + (lineNum+1) + ": " + word);
          }
          lineNum++;
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

  private List<String> generateDistractorConclusions(String A, String B, String C, String correctConclusion) {
    // Generate distractor conclusions
    List<String> distractors = new ArrayList<>();
    distractors.add("Alle " + A + " sind " + C + ".");
    distractors.add("Einige " + A + " sind " + C + ".");
    distractors.add("Alle " + A + " sind keine " + C + ".");
    distractors.add("Einige " + A + " sind keine " + C + ".");
    distractors.add("Keine " + A + " sind " + C + ".");
    distractors.add("Alle " + C + " sind " + A + ".");
    distractors.add("Einige " + C + " sind " + A + ".");
    distractors.add("Alle " + C + " sind keine " + A + ".");
    distractors.add("Einige " + C + " sind keine " + A + ".");
    distractors.add("Keine " + C + " sind " + A + ".");

    // Remove the correct conclusion and duplicates
    distractors.remove(correctConclusion);
    distractors = new ArrayList<>(new HashSet<>(distractors)); // Remove duplicates

    return distractors;
  }

  public static class SyllogismModel {
    private String majorPremiseTemplate;
    private List<String> minorPremiseTemplates;
    private List<String> strongConclusionTemplates;
    private List<String> weakConclusionTemplates;
    private boolean hasNoValidConclusion;
    private String description;

    // Constructor
    public SyllogismModel(String majorPremiseTemplate, List<String> minorPremiseTemplates,
        List<String> strongConclusionTemplates, List<String> weakConclusionTemplates,
        boolean hasNoValidConclusion, String description) {
      this.majorPremiseTemplate = majorPremiseTemplate;
      this.minorPremiseTemplates = minorPremiseTemplates;
      this.strongConclusionTemplates = strongConclusionTemplates;
      this.weakConclusionTemplates = weakConclusionTemplates;
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