import util.*;

public class ExistenceMarkerTest {
    public static void main(String[] args) {
        System.out.println("=== EXISTENCE MARKER TEST ===");
        
        // Test the problematic scenarios that were empty
        Type[] majors = {Type.I, Type.I, Type.O, Type.O};
        Type[] minors = {Type.I, Type.O, Type.I, Type.O};
        String[] descriptions = {
            "I+I (Some A are B + Some B are C)",
            "I+O (Some A are B + Some B are not C)", 
            "O+I (Some A are not B + Some B are C)",
            "O+O (Some A are not B + Some B are not C)"
        };
        
        for (int i = 0; i < majors.length; i++) {
            System.out.println("\n" + descriptions[i]);
            
            // Get elimination mask (should be all zeros)
            int[] mask = SyllogismUtils.deduceDiagramMask(majors[i], minors[i]);
            System.out.print("Elimination mask: [");
            for (int j = 0; j < mask.length; j++) {
                System.out.print(mask[j] + (j < mask.length-1 ? ", " : ""));
            }
            System.out.println("]");
            
            // Get existence markers (should have some ones)
            int[] existence = SyllogismUtils.deduceExistenceMarkers(majors[i], minors[i]);
            System.out.print("Existence markers: [");
            for (int j = 0; j < existence.length; j++) {
                System.out.print(existence[j] + (j < existence.length-1 ? ", " : ""));
            }
            System.out.println("]");
            
            // Count marked regions
            int markedRegions = 0;
            for (int mark : existence) {
                markedRegions += mark;
            }
            System.out.println("Total marked regions: " + markedRegions);
            
            if (markedRegions > 0) {
                System.out.println("✓ Visual feedback will be provided!");
            } else {
                System.out.println("✗ Still no visual feedback");
            }
        }
        
        // Test a mixed scenario for comparison
        System.out.println("\n=== COMPARISON: A+E (Strong visual feedback) ===");
        int[] strongMask = SyllogismUtils.deduceDiagramMask(Type.A, Type.E);
        int[] strongExistence = SyllogismUtils.deduceExistenceMarkers(Type.A, Type.E);
        
        System.out.print("Elimination mask: [");
        for (int j = 0; j < strongMask.length; j++) {
            System.out.print(strongMask[j] + (j < strongMask.length-1 ? ", " : ""));
        }
        System.out.println("]");
        
        System.out.print("Existence markers: [");
        for (int j = 0; j < strongExistence.length; j++) {
            System.out.print(strongExistence[j] + (j < strongExistence.length-1 ? ", " : ""));
        }
        System.out.println("]");
    }
}
