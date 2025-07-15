package ui.merkfaehigkeit;

import org.junit.jupiter.api.Test;
import model.AllergyCardData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class AllergyCardPanelTest {
    @Test
    void toModelMapsFieldsCorrectly() throws Exception {
        AllergyCardPanel panel = new AllergyCardPanel();
        BufferedImage img = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        byte[] bytes = bos.toByteArray();

        AllergyCardData data = new AllergyCardData(
                "Max Mustermann",
                LocalDate.of(1990,1,1),
                "Ibuprofen",
                "A+",
                "Pollen",
                "12345",
                "Austria",
                bytes
        );

        panel.load(data);
        AllergyCardData out = panel.toModel();

        assertEquals(data.name(), out.name());
        assertEquals(data.geburtsdatum(), out.geburtsdatum());
        assertEquals(data.medikamenteneinnahme(), out.medikamenteneinnahme());
        assertEquals(data.blutgruppe(), out.blutgruppe());
        assertEquals(data.bekannteAllergien(), out.bekannteAllergien());
        assertEquals(data.ausweisnummer(), out.ausweisnummer());
        assertEquals(data.ausstellungsland(), out.ausstellungsland());
        assertNotNull(out.bildPng());
    }
}
