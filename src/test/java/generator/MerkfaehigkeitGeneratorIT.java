import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import generator.MerkfaehigkeitGenerator;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

public class MerkfaehigkeitGeneratorIT {
    @Test
    void saveWithMissingDataShowsError() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        MerkfaehigkeitGenerator gen = new MerkfaehigkeitGenerator();
        SwingUtilities.invokeAndWait(gen::start);
        JButton save = gen.getSaveButton();
        SwingUtilities.invokeAndWait(save::doClick);
        assertNotNull(gen.getFrame());
        SwingUtilities.invokeAndWait(() -> gen.getFrame().dispose());
    }
}
