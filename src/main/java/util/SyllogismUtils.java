package util;

import java.util.*;
import java.util.regex.*;

public class SyllogismUtils {
    public static Sentence parseSentence(String input) {
        if (input == null) throw new IllegalArgumentException("null sentence");
        String s = input.trim();
        if (s.endsWith(".")) s = s.substring(0, s.length()-1);
        
        // Patterns for German/English simple forms
        // Type A: "Alle X sind Y" (but not "Alle X sind keine Y")
        Pattern a = Pattern.compile("^(?i)(Alle|All)\\s+(\\w+)\\s+sind\\s+(?!keine|nicht)(\\w+)");
        Matcher m = a.matcher(s);
        if (m.find()) {
            return new Sentence(Type.A, m.group(2), m.group(3));
        }
        
        // Type E: "Keine X sind Y" or "Alle X sind keine Y"
        Pattern e1 = Pattern.compile("^(?i)(Keine|Kein|No)\\s+(\\w+)\\s+sind\\s+(\\w+)");
        m = e1.matcher(s);
        if (m.find()) {
            return new Sentence(Type.E, m.group(2), m.group(3));
        }
        
        // Type E: "Alle X sind keine Y" (alternative form)
        Pattern e2 = Pattern.compile("^(?i)(Alle|All)\\s+(\\w+)\\s+sind\\s+keine\\s+(\\w+)");
        m = e2.matcher(s);
        if (m.find()) {
            return new Sentence(Type.E, m.group(2), m.group(3));
        }
        
        // Type O: "Einige X sind keine Y"
        Pattern o = Pattern.compile("^(?i)(Einige|Some)\\s+(\\w+)\\s+sind\\s+(?:keine|nicht|not)\\s+(\\w+)");
        m = o.matcher(s);
        if (m.find()) {
            return new Sentence(Type.O, m.group(2), m.group(3));
        }
        
        // Type I: "Einige X sind Y" (but not "Einige X sind keine Y")
        Pattern i = Pattern.compile("^(?i)(Einige|Some)\\s+(\\w+)\\s+sind\\s+(?!keine|nicht|not)(\\w+)");
        m = i.matcher(s);
        if (m.find()) {
            return new Sentence(Type.I, m.group(2), m.group(3));
        }
        
        throw new IllegalArgumentException("Unrecognized sentence: " + input);
    }

    public static final Map<Pair<Type, Type>, int[]> DIAGRAM_MASKS = initMasks();

    private static Map<Pair<Type, Type>, int[]> initMasks() {
        Map<Pair<Type, Type>, int[]> map = new HashMap<>();
        for (Type maj : Type.values()) {
            for (Type min : Type.values()) {
                map.put(Pair.of(maj, min), deduceDiagramMask(maj, min));
            }
        }
        return map;
    }

    public static int[] deduceDiagramMask(Type major, Type minor) {
        int[] mask = new int[8];
        // Major premise effects
        switch (major) {
            case A -> { mask[0] = 1; mask[5] = 1; }
            case E -> { mask[1] = 1; mask[3] = 1; }
            default -> {}
        }
        // Minor premise effects
        switch (minor) {
            case A -> { mask[2] = 1; mask[1] = 1; }
            case E -> { mask[6] = 1; mask[3] = 1; }
            default -> {}
        }
        return mask;
    }
}
