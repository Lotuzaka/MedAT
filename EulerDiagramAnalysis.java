import util.*;
import java.util.*;

/**
 * Analysis tool to print all possible Euler diagram scenarios for "Implikationen"
 */
public class EulerDiagramAnalysis {
    
    public static void main(String[] args) {
        System.out.println("=== EULER DIAGRAM ANALYSIS FOR IMPLIKATIONEN ===\n");
        
        // Define all possible sentence types
        Type[] types = {Type.A, Type.E, Type.I, Type.O};
        String[] typeNames = {"A (Universal Affirmative)", "E (Universal Negative)", 
                             "I (Particular Affirmative)", "O (Particular Negative)"};
        
        // Print legend
        System.out.println("SENTENCE TYPES:");
        System.out.println("A: Alle X sind Y (Universal Affirmative)");
        System.out.println("E: Keine X sind Y / Alle X sind keine Y (Universal Negative)");
        System.out.println("I: Einige X sind Y (Particular Affirmative)");
        System.out.println("O: Einige X sind keine Y (Particular Negative)");
        System.out.println();
        
        System.out.println("EULER DIAGRAM REGIONS:");
        System.out.println("Region 0: A only (A - B - C)");
        System.out.println("Region 1: A ∩ B only (A ∩ B - C)");
        System.out.println("Region 2: B only (B - A - C)");
        System.out.println("Region 3: A ∩ B ∩ C (all three circles)");
        System.out.println("Region 4: C only (C - A - B)");
        System.out.println("Region 5: A ∩ C only (A ∩ C - B)");
        System.out.println("Region 6: B ∩ C only (B ∩ C - A)");
        System.out.println("Region 7: Empty (outside all circles)");
        System.out.println();
        
        // Example sentences for each type
        Map<Type, String[]> examples = new HashMap<>();
        examples.put(Type.A, new String[]{"Alle Menschen sind Säugetiere", "Alle Hunde sind Tiere"});
        examples.put(Type.E, new String[]{"Keine Vögel sind Säugetiere", "Alle Fische sind keine Landtiere"});
        examples.put(Type.I, new String[]{"Einige Tiere sind Haustiere", "Einige Menschen sind Sportler"});
        examples.put(Type.O, new String[]{"Einige Tiere sind keine Haustiere", "Einige Menschen sind nicht Sportler"});
        
        int scenarioCount = 0;
        
        // Generate all 16 combinations (4x4)
        for (int i = 0; i < types.length; i++) {
            for (int j = 0; j < types.length; j++) {
                scenarioCount++;
                Type majorType = types[i];
                Type minorType = types[j];
                
                System.out.println("=".repeat(60));
                System.out.printf("SCENARIO %d: %s + %s\n", scenarioCount, majorType, minorType);
                System.out.println("=".repeat(60));
                
                // Show example sentences
                System.out.printf("Major Premise (%s): %s\n", majorType, examples.get(majorType)[0]);
                System.out.printf("Minor Premise (%s): %s\n", minorType, examples.get(minorType)[0]);
                System.out.println();
                
                // Get the diagram mask
                int[] mask = SyllogismUtils.deduceDiagramMask(majorType, minorType);
                
                // Show which regions are filled
                System.out.println("FILLED REGIONS (shown in gray):");
                boolean hasFilledRegions = false;
                for (int k = 0; k < mask.length; k++) {
                    if (mask[k] == 1) {
                        System.out.printf("  Region %d: %s\n", k, getRegionDescription(k));
                        hasFilledRegions = true;
                    }
                }
                if (!hasFilledRegions) {
                    System.out.println("  No regions filled (empty diagram)");
                }
                
                System.out.println();
                
                // Show ASCII representation
                System.out.println("ASCII DIAGRAM:");
                printASCIIDiagram(mask);
                
                System.out.println();
                System.out.println("LOGICAL EXPLANATION:");
                explainLogic(majorType, minorType, mask);
                
                System.out.println("\n");
            }
        }
        
        System.out.println("=".repeat(60));
        System.out.printf("TOTAL SCENARIOS: %d\n", scenarioCount);
        System.out.println("=".repeat(60));
    }
    
    private static String getRegionDescription(int region) {
        switch (region) {
            case 0: return "A only (A - B - C)";
            case 1: return "A ∩ B only (A ∩ B - C)";
            case 2: return "B only (B - A - C)";
            case 3: return "A ∩ B ∩ C (all three)";
            case 4: return "C only (C - A - B)";
            case 5: return "A ∩ C only (A ∩ C - B)";
            case 6: return "B ∩ C only (B ∩ C - A)";
            case 7: return "Empty (outside all)";
            default: return "Unknown";
        }
    }
    
    private static void printASCIIDiagram(int[] mask) {
        System.out.println("         C");
        System.out.println("      ┌─────┐");
        System.out.println("     ╱   " + (mask[4] == 1 ? "█" : " ") + "   ╲");
        System.out.println("    ╱  " + (mask[5] == 1 ? "█" : " ") + " " + (mask[3] == 1 ? "█" : " ") + " " + (mask[6] == 1 ? "█" : " ") + "  ╲");
        System.out.println("   A ∩─────────∩ B");
        System.out.println("    ╲  " + (mask[0] == 1 ? "█" : " ") + " " + (mask[1] == 1 ? "█" : " ") + " " + (mask[2] == 1 ? "█" : " ") + "  ╱");
        System.out.println("     ╲       ╱");
        System.out.println("      └─────┘");
        System.out.println();
        System.out.println("Legend: █ = filled region");
    }
    
    private static void explainLogic(Type major, Type minor, int[] mask) {
        System.out.println("Major premise effect:");
        switch (major) {
            case A:
                System.out.println("  Type A: 'All A are B' → Eliminates regions 0 and 5 (A without B)");
                break;
            case E:
                System.out.println("  Type E: 'No A are B' → Eliminates regions 1 and 3 (A with B)");
                break;
            case I:
                System.out.println("  Type I: 'Some A are B' → No regions eliminated (existence claim)");
                break;
            case O:
                System.out.println("  Type O: 'Some A are not B' → No regions eliminated (existence claim)");
                break;
        }
        
        System.out.println("Minor premise effect:");
        switch (minor) {
            case A:
                System.out.println("  Type A: 'All B are C' → Eliminates regions 2 and 1 (B without C)");
                break;
            case E:
                System.out.println("  Type E: 'No B are C' → Eliminates regions 6 and 3 (B with C)");
                break;
            case I:
                System.out.println("  Type I: 'Some B are C' → No regions eliminated (existence claim)");
                break;
            case O:
                System.out.println("  Type O: 'Some B are not C' → No regions eliminated (existence claim)");
                break;
        }
        
        System.out.println("Combined effect:");
        boolean hasEffect = false;
        for (int i = 0; i < mask.length; i++) {
            if (mask[i] == 1) {
                System.out.printf("  Region %d eliminated\n", i);
                hasEffect = true;
            }
        }
        if (!hasEffect) {
            System.out.println("  No regions eliminated (both premises are particular/existence claims)");
        }
    }
}
