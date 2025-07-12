import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

public class WortfluessigkeitGeneratorPropertyTest {

    private Connection createDb() throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE categories(id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(255))");
            st.execute(
                    "CREATE TABLE subcategories(id INT PRIMARY KEY AUTO_INCREMENT, category_id INT, name VARCHAR(255), order_index INT)");
            st.execute(
                    "CREATE TABLE questions(id INT PRIMARY KEY AUTO_INCREMENT, subcategory_id INT, question_number INT, text VARCHAR(255), format VARCHAR(10), test_simulation_id INT, difficulty VARCHAR(10), shape_data VARCHAR(255), shape_type VARCHAR(255), dissected_pieces_data VARCHAR(255), assembled_pieces_data VARCHAR(255))");
            st.execute(
                    "CREATE TABLE options(id INT PRIMARY KEY AUTO_INCREMENT, question_id INT, label VARCHAR(5), text VARCHAR(255), is_correct BOOLEAN, shape_data VARCHAR(255))");
            st.execute("INSERT INTO categories(id,name) VALUES(1,'KFF')");
            st.execute("INSERT INTO subcategories(id,category_id,name,order_index) VALUES(1,1,'Wortflüssigkeit',1)");
        }
        return c;
    }

    @Property
    void oneCorrectOptionPerQuestion(@ForAll("ids") int simId) throws Exception {
        try (Connection conn = createDb()) {
            WortfluessigkeitGenerator gen = new WortfluessigkeitGenerator(conn, "KFF", "Wortflüssigkeit", simId);
            gen.execute(1);
            QuestionDAO qDao = new QuestionDAO(conn);
            int subId = qDao.getSubcategoryId("KFF", "Wortflüssigkeit");
            List<QuestionDAO> qs = qDao.getQuestionsBySubcategoryAndSimulation(subId, simId);
            assertEquals(1, qs.size());
            OptionDAO oDao = new OptionDAO(conn);
            List<OptionDAO> opts = oDao.getOptionsByQuestionId(qs.get(0).getId());
            long count = opts.stream().filter(OptionDAO::isCorrect).count();
            assertEquals(1, count);
        }
    }

    @Provide
    Arbitrary<Integer> ids() {
        // statt Integer.between(…) ⇒ Arbitraries.integers().between(…)
        return Arbitraries.integers().between(1, 1000);
    }

}
