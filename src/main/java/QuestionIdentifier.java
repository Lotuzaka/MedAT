import java.util.Objects;

class QuestionIdentifier {
    String subcategory;
    int questionNumber;

    public QuestionIdentifier(String subcategory, int questionNumber) {
        this.subcategory = subcategory;
        this.questionNumber = questionNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QuestionIdentifier that = (QuestionIdentifier) o;
        return questionNumber == that.questionNumber && Objects.equals(subcategory, that.subcategory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subcategory, questionNumber);
    }
}
