package util;

import java.util.*;
import java.util.regex.*;

public class SyllogismUtils {
    public static Sentence parseSentence(String input) {
        if (input == null) throw new IllegalArgumentException("null sentence");
        String s = input.trim();
        if (s.endsWith(".")) s = s.substring(0, s.length()-1);
        
        // Patterns for German/English simple forms with Unicode support
        // Type A: "Alle X sind Y" (but not "Alle X sind keine Y")
        Pattern a = Pattern.compile("^(?iu)(Alle|All)\\s+([\\p{L}\\p{N}]+)\\s+sind\\s+(?!keine|nicht)([\\p{L}\\p{N}]+)");
        Matcher m = a.matcher(s);
        if (m.find()) {
            return new Sentence(Type.A, m.group(2), m.group(3));
        }
        
        // Type E: "Keine X sind Y" or "Alle X sind keine Y"
        Pattern e1 = Pattern.compile("^(?iu)(Keine|Kein|No)\\s+([\\p{L}\\p{N}]+)\\s+sind\\s+([\\p{L}\\p{N}]+)");
        m = e1.matcher(s);
        if (m.find()) {
            return new Sentence(Type.E, m.group(2), m.group(3));
        }
        
        // Type E: "Alle X sind keine Y" (alternative form)
        Pattern e2 = Pattern.compile("^(?iu)(Alle|All)\\s+([\\p{L}\\p{N}]+)\\s+sind\\s+keine\\s+([\\p{L}\\p{N}]+)");
        m = e2.matcher(s);
        if (m.find()) {
            return new Sentence(Type.E, m.group(2), m.group(3));
        }
        
        // Type O: "Einige X sind keine Y"
        Pattern o = Pattern.compile("^(?iu)(Einige|Some|Manche)\\s+([\\p{L}\\p{N}]+)\\s+sind\\s+(?:keine|nicht|not)\\s+([\\p{L}\\p{N}]+)");
        m = o.matcher(s);
        if (m.find()) {
            return new Sentence(Type.O, m.group(2), m.group(3));
        }
        
        // Type I: "Einige X sind Y" (but not "Einige X sind keine Y")
        Pattern i = Pattern.compile("^(?iu)(Einige|Some|Manche)\\s+([\\p{L}\\p{N}]+)\\s+sind\\s+(?!keine|nicht|not)([\\p{L}\\p{N}]+)");
        m = i.matcher(s);
        if (m.find()) {
            return new Sentence(Type.I, m.group(2), m.group(3));
        }
        
        throw new IllegalArgumentException("Unrecognized sentence: " + input);
    }

    public static final Map<Pair<Type, Type>, int[]> DIAGRAM_MASKS = initMasks();
    public static final Map<Pair<Type, Type>, int[]> EXISTENCE_MARKERS = initExistenceMarkers();

    private static Map<Pair<Type, Type>, int[]> initMasks() {
        Map<Pair<Type, Type>, int[]> map = new HashMap<>();
        for (Type maj : Type.values()) {
            for (Type min : Type.values()) {
                map.put(Pair.of(maj, min), deduceDiagramMask(maj, min));
            }
        }
        return map;
    }

    private static Map<Pair<Type, Type>, int[]> initExistenceMarkers() {
        Map<Pair<Type, Type>, int[]> map = new HashMap<>();
        for (Type maj : Type.values()) {
            for (Type min : Type.values()) {
                map.put(Pair.of(maj, min), deduceExistenceMarkers(maj, min));
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

    /**
     * Determines which regions should have existence markers (dots) for particular claims.
     * Returns an array where 1 indicates a region that should show existence.
     */
    public static int[] deduceExistenceMarkers(Type major, Type minor) {
        int[] markers = new int[8];
        
        // Major premise existence claims
        switch (major) {
            case I -> { 
                // "Some A are B" - mark intersection regions
                markers[1] = 1; // A ∩ B only
                markers[3] = 1; // A ∩ B ∩ C  
            }
            case O -> { 
                // "Some A are not B" - mark A-only regions
                markers[0] = 1; // A only
                markers[5] = 1; // A ∩ C only
            }
            default -> {}
        }
        
        // Minor premise existence claims
        switch (minor) {
            case I -> { 
                // "Some B are C" - mark intersection regions
                markers[3] = 1; // A ∩ B ∩ C
                markers[6] = 1; // B ∩ C only
            }
            case O -> { 
                // "Some B are not C" - mark B-only regions  
                markers[1] = 1; // A ∩ B only
                markers[2] = 1; // B only
            }
            default -> {}
        }
        
        return markers;
    }
}
