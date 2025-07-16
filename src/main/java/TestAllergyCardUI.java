import javax.swing.*;
import java.awt.*;

public class TestAllergyCardUI {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Test Allergy Card UI");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);
            
            // Create allergy card grid panel
            try {
                Class<?> allergyGridClass = Class.forName("ui.merkfaehigkeit.AllergyCardGridPanel");
                JPanel allergyCardGridPanel = (JPanel) allergyGridClass.getDeclaredConstructor().newInstance();
                
                // Call generateRandomData to populate the cards
                allergyCardGridPanel.getClass().getMethod("generateRandomData").invoke(allergyCardGridPanel);
                
                JScrollPane scrollPane = new JScrollPane(allergyCardGridPanel);
                scrollPane.getViewport().setBackground(Color.WHITE);
                scrollPane.setBackground(Color.WHITE);
                
                frame.add(scrollPane);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                
                System.out.println("Test UI started with white background and rounded card edges");
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Error creating test UI: " + e.getMessage());
            }
        });
    }
}
