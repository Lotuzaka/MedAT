package merk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

public final class TemplateRegistry {
    private final Map<MerkTemplate, TemplateData> templates;

    private TemplateRegistry(Map<MerkTemplate, TemplateData> templates) {
        this.templates = templates;
    }

    public static TemplateRegistry load() {
        Map<MerkTemplate, TemplateData> map = new EnumMap<>(MerkTemplate.class);
        for (MerkTemplate t : MerkTemplate.values()) {
            map.put(t, new TemplateData(t, null, List.of()));
        }
        try (InputStream in = TemplateRegistry.class.getResourceAsStream("/merk/templates.json")) {
            if (in != null) {
                ObjectMapper om = new ObjectMapper();
                List<JsonTemplate> list = om.readValue(in, new TypeReference<>(){});
                for (JsonTemplate jt : list) {
                    MerkTemplate id = MerkTemplate.valueOf(jt.id);
                    List<String> vars = jt.variants == null ? List.of() : List.copyOf(jt.variants);
                    map.put(id, new TemplateData(id, jt.question, vars));
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new TemplateRegistry(map);
    }

    public TemplateData get(MerkTemplate id) {
        return templates.get(id);
    }

    public Collection<TemplateData> all() {
        return templates.values();
    }

    private static class JsonTemplate {
        public String id;
        public String question;
        public List<String> variants;
    }
}
