import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import util.Type;
import util.SyllogismUtils;
import util.Pair;

public class RenderEulerTest {
    private String sentence(Type t, String s, String p) {
        return switch (t) {
            case A -> "Alle " + s + " sind " + p + ".";
            case E -> "Keine " + s + " sind " + p + ".";
            case I -> "Einige " + s + " sind " + p + ".";
            case O -> "Einige " + s + " sind keine " + p + ".";
        };
    }

    @Test
    public void rendersAllCombinations() throws Exception {
        for (Type major : Type.values()) {
            for (Type minor : Type.values()) {
                String maj = sentence(major, "A", "B");
                String min = sentence(minor, "B", "C");
                Path p = CustomRenderer.renderEuler(maj, min, null);
                assertTrue(Files.exists(p), major+"-"+minor+" file");
                assertTrue(Files.size(p) > 0, "non empty");
            }
        }
    }

    @Test
    public void maskMatchesKnownExample() {
        int[] mask = SyllogismUtils.DIAGRAM_MASKS.get(Pair.of(Type.A, Type.A));
        assertNotNull(mask);
        int[] expected = SyllogismUtils.deduceDiagramMask(Type.A, Type.A);
        assertArrayEquals(expected, mask);
    }
}
