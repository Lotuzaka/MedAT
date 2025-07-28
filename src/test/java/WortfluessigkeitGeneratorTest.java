import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for the WortfluessigkeitGenerator. */
public class WortfluessigkeitGeneratorTest {

    private Connection conn;

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection("jdbc:h2:mem:test_wf_unit;DB_CLOSE_DELAY=-1");
        try (Statement st = conn.createStatement()) {
            st.execute("DROP ALL OBJECTS");
            st.execute("CREATE TABLE categories(id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(255))");
            st.execute("CREATE TABLE subcategories(id INT PRIMARY KEY AUTO_INCREMENT, category_id INT, name VARCHAR(255), order_index INT)");
            st.execute("CREATE TABLE questions(id INT PRIMARY KEY AUTO_INCREMENT, subcategory_id INT, question_number INT, text VARCHAR(255), format VARCHAR(10), test_simulation_id INT, passage_id INT, difficulty VARCHAR(10), shape_data VARCHAR(255), shape_type VARCHAR(255), dissected_pieces_data VARCHAR(255), assembled_pieces_data VARCHAR(255))");
            st.execute("CREATE TABLE options(id INT PRIMARY KEY AUTO_INCREMENT, question_id INT, label VARCHAR(5), text VARCHAR(255), is_correct BOOLEAN, shape_data VARCHAR(255))");
            st.execute("INSERT INTO categories(id,name) VALUES(1,'KFF')");
            st.execute("INSERT INTO subcategories(id,category_id,name,order_index) VALUES(1,1,'Wortflüssigkeit',1)");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        conn.close();
    }

    @Test
    void scrambleNeverReturnsOriginal() {
        WortfluessigkeitGenerator gen = new WortfluessigkeitGenerator(null, "KFF", "Wortflüssigkeit", 1);
        String word = "ABCDEFG";
        for (int i = 0; i < 10; i++) {
            String scrambled = gen.scramble(word);
            assertNotEquals(word, scrambled);
        }
    }

    @Test
    void executeCreatesQuestionsAndOptions() throws Exception {
        WortfluessigkeitGenerator gen = new WortfluessigkeitGenerator(conn, "KFF", "Wortflüssigkeit", 1);
        gen.execute(15);
        try (Statement st = conn.createStatement()) {
            var rs = st.executeQuery("SELECT COUNT(*) FROM questions WHERE test_simulation_id=1");
            assertTrue(rs.next());
            assertEquals(15, rs.getInt(1));
            rs = st.executeQuery("SELECT COUNT(*) FROM options o JOIN questions q ON o.question_id=q.id WHERE q.test_simulation_id=1");
            assertTrue(rs.next());
            assertEquals(75, rs.getInt(1));
        }
    }
}
