import util.*;
import java.util.*;

/**
 * Diagnostic tool to test specific Implikationen scenarios and identify issues
 */
public class EulerDiagramDiagnostic {
    
    public static void main(String[] args) {
        System.out.println("=== EULER DIAGRAM DIAGNOSTIC TOOL ===\n");
        
        // Test specific problematic scenarios
        testScenario("Alle Menschen sind Säugetiere", "Alle Säugetiere sind Tiere", "A+A");
        testScenario("Alle Menschen sind Säugetiere", "Keine Säugetiere sind Reptilien", "A+E"); 
        testScenario("Keine Vögel sind Säugetiere", "Alle Säugetiere sind Wirbeltiere", "E+A");
        testScenario("Keine Fische sind Landtiere", "Keine Landtiere sind Wassertiere", "E+E");
        
        // Test empty diagram scenarios (problematic)
        System.out.println("\n=== PROBLEMATIC SCENARIOS (Empty Diagrams) ===");
        testScenario("Einige Tiere sind Haustiere", "Einige Haustiere sind Hunde", "I+I");
        testScenario("Einige Tiere sind keine Haustiere", "Einige Haustiere sind Katzen", "O+I");
        
        // Test parsing edge cases
        System.out.println("\n=== PARSING TESTS ===");
        testParsing("Alle Menschen sind Säugetiere");
        testParsing("Keine Vögel sind Säugetiere");
        testParsing("Alle Fische sind keine Landtiere");
        testParsing("Einige Tiere sind Haustiere");
        testParsing("Einige Tiere sind keine Haustiere");
        testParsing("Einige Tiere sind nicht Haustiere");
        
        // Test problematic sentences
        System.out.println("\n=== PARSING EDGE CASES ===");
        testParsing("Manche Menschen sind klug");  // Should fail - "Manche" not supported
        testParsing("Alle Menschen sind.");        // Should fail - no predicate
        testParsing("");                          // Should fail - empty
    }
    
    private static void testScenario(String major, String minor, String label) {
        System.out.println("=".repeat(50));
        System.out.println("TEST: " + label);
        System.out.println("Major: " + major);
        System.out.println("Minor: " + minor);
        System.out.println("=".repeat(50));
        
        try {
            Sentence s1 = SyllogismUtils.parseSentence(major);
            Sentence s2 = SyllogismUtils.parseSentence(minor);
            
            System.out.printf("Parsed Major: %s(%s, %s)\n", s1.type(), s1.subject(), s1.predicate());
            System.out.printf("Parsed Minor: %s(%s, %s)\n", s2.type(), s2.subject(), s2.predicate());
            
            int[] mask = SyllogismUtils.deduceDiagramMask(s1.type(), s2.type());
            
            System.out.print("Mask: [");
            for (int i = 0; i < mask.length; i++) {
                System.out.print(mask[i] + (i < mask.length-1 ? ", " : ""));
            }
            System.out.println("]");
            
            // Show which regions are filled
            System.out.print("Filled regions: ");
            List<Integer> filled = new ArrayList<>();
            for (int i = 0; i < mask.length; i++) {
                if (mask[i] == 1) filled.add(i);
            }
            if (filled.isEmpty()) {
                System.out.println("NONE (Empty diagram - potential issue!)");
            } else {
                System.out.println(filled);
            }
            
            // Show circle labels
            System.out.printf("Circle A: %s\n", s1.subject());
            System.out.printf("Circle B: %s (shared term)\n", s1.predicate());
            System.out.printf("Circle C: %s\n", s2.predicate());
            
            // Visual representation
            System.out.println("\nVisual check:");
            printDetailedDiagram(mask, s1.subject(), s1.predicate(), s2.predicate());
            
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    private static void testParsing(String sentence) {
        System.out.printf("Testing: '%s' -> ", sentence);
        try {
            Sentence s = SyllogismUtils.parseSentence(sentence);
            System.out.printf("SUCCESS: %s(%s, %s)\n", s.type(), s.subject(), s.predicate());
        } catch (Exception e) {
            System.out.printf("FAILED: %s\n", e.getMessage());
        }
    }
    
    private static void printDetailedDiagram(int[] mask, String labelA, String labelB, String labelC) {
        System.out.println("Detailed region analysis:");
        
        String[] regionDescriptions = {
            labelA + " only (not " + labelB + ", not " + labelC + ")",
            labelA + " ∩ " + labelB + " only (not " + labelC + ")", 
            labelB + " only (not " + labelA + ", not " + labelC + ")",
            labelA + " ∩ " + labelB + " ∩ " + labelC + " (all three)",
            labelC + " only (not " + labelA + ", not " + labelB + ")",
            labelA + " ∩ " + labelC + " only (not " + labelB + ")",
            labelB + " ∩ " + labelC + " only (not " + labelA + ")",
            "Outside all circles"
        };
        
        for (int i = 0; i < mask.length; i++) {
            String status = mask[i] == 1 ? "ELIMINATED (gray)" : "allowed (white)";
            System.out.printf("  Region %d: %s - %s\n", i, regionDescriptions[i], status);
        }
        
        // ASCII diagram with labels
        System.out.println("\nDiagram layout:");
        System.out.printf("         %s\n", labelC);
        System.out.println("      ┌─────┐");
        System.out.printf("     ╱   %s   ╲ (region 4)\n", mask[4] == 1 ? "█" : " ");
        System.out.printf("    ╱  %s %s %s  ╲\n", 
                         mask[5] == 1 ? "█" : "5", 
                         mask[3] == 1 ? "█" : "3", 
                         mask[6] == 1 ? "█" : "6");
        System.out.printf("   %s ∩─────────∩ %s\n", labelA, labelB);
        System.out.printf("    ╲  %s %s %s  ╱\n",
                         mask[0] == 1 ? "█" : "0",
                         mask[1] == 1 ? "█" : "1", 
                         mask[2] == 1 ? "█" : "2");
        System.out.println("     ╲       ╱");
        System.out.println("      └─────┘");
        System.out.println("█ = eliminated region, numbers = region indices");
    }
}
