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
        try (InputStream in = TemplateRegistry.class.getResourceAsStream("/merk/templates.json")) {
            if (in != null) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return fromJson(json);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        // Fallback to built-in JSON if resource missing (e.g. running from old jar)
        return fromJson(BUILTIN_JSON);
    }

    private static TemplateRegistry fromJson(String json) {
        Map<MerkTemplate, TemplateData> map = new EnumMap<>(MerkTemplate.class);
        for (MerkTemplate t : MerkTemplate.values()) {
            map.put(t, new TemplateData(t, null, List.of()));
        }
        Pattern p = Pattern.compile("\\{\\s*\"id\"\\s*:\\s*\"(.*?)\"\\s*,\\s*\"question\"\\s*:\\s*\"(.*?)\"(\\s*,\\s*\"variants\"\\s*:\\s*\\[(.*?)\\])?\\s*\\}", Pattern.DOTALL);
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
        return new TemplateRegistry(map);
    }

    private static final String BUILTIN_JSON = """
[
  {
    "id": "S1",
    "question": "Die Person mit der Ausweisnummer {ID} hat welche Blutgruppe?",
    "variants": [
      "Blutgruppe der Person Nr. {ID}?",
      "Welche Blutgruppe besitzt die Person mit der Ausweisnummer {ID}?",
      "Welche Blutgruppe hat die Person, deren Ausweisnummer {ID} lautet?"
    ]
  },
  {
    "id": "S2",
    "question": "Welche Ausweisnummer hat die Person aus {COUNTRY}?",
    "variants": [
      "Nummer des Ausweises aus {COUNTRY}?",
      "Welche Identifikationsnummer gehört zur Person aus {COUNTRY}?",
      "Der Ausweis aus {COUNTRY} trägt welche Nummer?"
    ]
  },
  {
    "id": "S3",
    "question": "Wie heißt die Person aus {COUNTRY} mit der Blutgruppe {BLOOD}?",
    "variants": [
      "Wer stammt aus {COUNTRY} und hat Blutgruppe {BLOOD}?",
      "Name der Person aus {COUNTRY} mit Blutgruppe {BLOOD}?",
      "Welche Person aus {COUNTRY} besitzt die Blutgruppe {BLOOD}?"
    ]
  },
  {
    "id": "S4",
    "question": "Wann ist {NAME} geboren?",
    "variants": [
      "Geburtsdatum von {NAME}?",
      "Wann feiert {NAME} Geburtstag?",
      "An welchem Datum hat {NAME} Geburtstag?"
    ]
  },
  {
    "id": "S5",
    "question": "Welche Allergien hat die Person mit der Blutgruppe {BLOOD} und dem Geburtsdatum {DOB}?",
    "variants": [
      "Allergien der Person (Blut {BLOOD}, geb. {DOB})?",
      "Welche Allergien weist die am {DOB} geborene Person mit Blutgruppe {BLOOD} auf?"
    ]
  },
  {
    "id": "S6",
    "question": "In welchem Land wurde der Ausweis von {NAME} ausgestellt?",
    "variants": [
      "Land des Ausweises von {NAME}?",
      "Woher stammt {NAME}?",
      "Ausstellungsland für {NAME}?"
    ]
  },
  {
    "id": "S7",
    "question": "Welche Blutgruppe hat {NAME}?",
    "variants": [
      "Blutgruppe von {NAME}?",
      "Welche Blutgruppe besitzt {NAME}?",
      "Welche Blutgruppe wurde für {NAME} vermerkt?"
    ]
  },
  {
    "id": "S8",
    "question": "Nimmt {NAME} Medikamente ein?",
    "variants": [
      "Medikamentenanamnese {NAME}?",
      "Gibt {NAME} eine Medikamenteneinnahme an?",
      "MedStatus von {NAME}: Ja oder Nein?"
    ]
  },
  {
    "id": "S9",
    "question": "Welche Ausweisnummer gehört zu {NAME}?",
    "variants": [
      "Ausweisnummer von {NAME}?",
      "Welche Nummer hat {NAME}?",
      "Identifikationsnummer {NAME}?"
    ]
  }
]
""";

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
