package merk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TemplateRegistryTest {
    @Test
    void loadProvidesAllTemplates() {
        TemplateRegistry reg = TemplateRegistry.load();
        assertEquals(MerkTemplate.values().length, reg.all().size());
        TemplateData d = reg.get(MerkTemplate.S1);
        assertNotNull(d);
        assertEquals("Die Person mit der Ausweisnummer {ID} hat welche Blutgruppe?", d.question());
    }
}
