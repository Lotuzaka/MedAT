package ui.textverstaendnis;

import javax.swing.*;
import java.awt.*;

/**
 * Test class to demonstrate the new professional text editor
 */
public class TextEditorDemo {
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Professional Text Editor Demo");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1000, 700);
            frame.setLocationRelativeTo(null);
            
            // Create tabs to show different editor options
            JTabbedPane tabbedPane = new JTabbedPane();
            
            // Advanced Text Editor
            AdvancedTextEditor advancedEditor = new AdvancedTextEditor();
            tabbedPane.addTab("Word-ähnlicher Editor", advancedEditor);
            
            // HTML Editor
            HTMLTextEditor htmlEditor = new HTMLTextEditor();
            tabbedPane.addTab("HTML Editor", htmlEditor);
            
            frame.add(tabbedPane);
            frame.setVisible(true);
            
            // Set some demo content
            advancedEditor.setText("Willkommen zum professionellen Texteditor!\n\n" +
                "Dieser Editor bietet Word-ähnliche Funktionen:\n" +
                "• Vollständige Formatierung (Fett, Kursiv, Unterstreichen)\n" +
                "• Schriftarten und Größen\n" +
                "• Farben für Text und Hintergrund\n" +
                "• Ausrichtung (Links, Mitte, Rechts, Blocksatz)\n" +
                "• Listen und Einrückungen\n" +
                "• Suchen und Ersetzen\n" +
                "• Live-Wortanzahl\n\n" +
                "Probieren Sie die Tastenkürzel:\n" +
                "• Ctrl+B = Fett\n" +
                "• Ctrl+I = Kursiv\n" +
                "• Ctrl+U = Unterstreichen\n" +
                "• Ctrl+F = Suchen");
                
            htmlEditor.setHTML("<html><head><title>HTML Demo</title></head><body>" +
                "<h1>HTML Editor Demo</h1>" +
                "<p>Dieser Editor unterstützt <b>HTML-Formatierung</b> und " +
                "<i>WYSIWYG-Bearbeitung</i>.</p>" +
                "<ul><li>Listen</li><li>Links</li><li>Bilder</li><li>Tabellen</li></ul>" +
                "</body></html>");
        });
    }
}
