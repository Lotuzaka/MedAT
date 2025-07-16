package merk;


import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Pattern p = Pattern.compile("\\{\\s*\"id\"\\s*:\\s*\"(.*?)\"\\s*,\\s*\"question\"\\s*:\\s*\"(.*?)\"(\\s*,\\s*\"variants\"\\s*:\\s*\\[(.*?)\\])?\\s*\\}");
                Matcher m = p.matcher(json);
                while (m.find()) {
                    String idStr = m.group(1);
                    String q = unescape(m.group(2));
                    String block = m.group(4);
                    List<String> vars = new ArrayList<>();
                    if (block != null) {
                        Matcher vm = Pattern.compile("\"(.*?)\"").matcher(block);
                        while (vm.find()) {
                            vars.add(unescape(vm.group(1)));
                        }
                    }
                    MerkTemplate id = MerkTemplate.valueOf(idStr);
                    map.put(id, new TemplateData(id, q, vars));
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new TemplateRegistry(map);
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"");
    }

    public TemplateData get(MerkTemplate id) {
        return templates.get(id);
    }

    public Collection<TemplateData> all() {
        return templates.values();
    }

}
