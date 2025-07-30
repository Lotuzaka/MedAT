package ui.textverstaendnis;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * HTML-based WYSIWYG editor for rich text editing
 */
public class HTMLTextEditor extends JPanel {
    
    private final JEditorPane editorPane;
    private final HTMLEditorKit editorKit;
    private final HTMLDocument document;
    
    public HTMLTextEditor() {
        setLayout(new BorderLayout());
        
        // Initialize HTML editor
        editorPane = new JEditorPane();
        editorKit = new HTMLEditorKit();
        editorPane.setEditorKit(editorKit);
        document = (HTMLDocument) editorPane.getDocument();
        
        // Create toolbar
        add(createHTMLToolbar(), BorderLayout.NORTH);
        
        // Add editor with scroll
        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setPreferredSize(new Dimension(600, 400));
        add(scrollPane, BorderLayout.CENTER);
        
        // Initialize with basic HTML
        editorPane.setText("<html><head></head><body><p></p></body></html>");
    }
    
    private JToolBar createHTMLToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        
        // Basic formatting
        addHTMLButton(toolbar, "B", "bold", "<b>", "</b>");
        addHTMLButton(toolbar, "I", "italic", "<i>", "</i>");
        addHTMLButton(toolbar, "U", "underline", "<u>", "</u>");
        
        toolbar.addSeparator();
        
        // Headings
        JComboBox<String> headingCombo = new JComboBox<>(new String[]{
            "Normal", "Überschrift 1", "Überschrift 2", "Überschrift 3"
        });
        headingCombo.addActionListener(e -> {
            String selected = (String) headingCombo.getSelectedItem();
            insertHeading(selected);
        });
        toolbar.add(headingCombo);
        
        toolbar.addSeparator();
        
        // Lists
        addHTMLButton(toolbar, "• List", "bullet", "<ul><li>", "</li></ul>");
        addHTMLButton(toolbar, "1. List", "numbered", "<ol><li>", "</li></ol>");
        
        toolbar.addSeparator();
        
        // Links and images
        JButton linkButton = new JButton("Link");
        linkButton.addActionListener(e -> insertLink());
        toolbar.add(linkButton);
        
        JButton imageButton = new JButton("Bild");
        imageButton.addActionListener(e -> insertImage());
        toolbar.add(imageButton);
        
        toolbar.addSeparator();
        
        // Table
        JButton tableButton = new JButton("Tabelle");
        tableButton.addActionListener(e -> insertTable());
        toolbar.add(tableButton);
        
        return toolbar;
    }
    
    private void addHTMLButton(JToolBar toolbar, String text, String actionName, String startTag, String endTag) {
        JButton button = new JButton(text);
        button.addActionListener(e -> insertHTMLTag(startTag, endTag));
        toolbar.add(button);
    }
    
    private void insertHTMLTag(String startTag, String endTag) {
        try {
            String selectedText = editorPane.getSelectedText();
            if (selectedText != null) {
                editorPane.replaceSelection(startTag + selectedText + endTag);
            } else {
                editorPane.replaceSelection(startTag + endTag);
                // Move cursor between tags
                int pos = editorPane.getCaretPosition() - endTag.length();
                editorPane.setCaretPosition(pos);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void insertHeading(String headingType) {
        String tag = switch (headingType) {
            case "Überschrift 1" -> "<h1>";
            case "Überschrift 2" -> "<h2>";
            case "Überschrift 3" -> "<h3>";
            default -> "<p>";
        };
        String endTag = tag.replace("<", "</");
        insertHTMLTag(tag, endTag);
    }
    
    private void insertLink() {
        String url = JOptionPane.showInputDialog(this, "URL eingeben:");
        if (url != null && !url.trim().isEmpty()) {
            String text = JOptionPane.showInputDialog(this, "Link-Text eingeben:", url);
            if (text != null) {
                insertHTMLTag("<a href=\"" + url + "\">", "</a>");
            }
        }
    }
    
    private void insertImage() {
        String src = JOptionPane.showInputDialog(this, "Bild-URL eingeben:");
        if (src != null && !src.trim().isEmpty()) {
            String alt = JOptionPane.showInputDialog(this, "Alt-Text eingeben:", "Bild");
            if (alt != null) {
                editorPane.replaceSelection("<img src=\"" + src + "\" alt=\"" + alt + "\" />");
            }
        }
    }
    
    private void insertTable() {
        String rowsStr = JOptionPane.showInputDialog(this, "Anzahl Zeilen:", "3");
        String colsStr = JOptionPane.showInputDialog(this, "Anzahl Spalten:", "3");
        
        try {
            int rows = Integer.parseInt(rowsStr);
            int cols = Integer.parseInt(colsStr);
            
            StringBuilder table = new StringBuilder("<table border=\"1\">\\n");
            for (int i = 0; i < rows; i++) {
                table.append("  <tr>\\n");
                for (int j = 0; j < cols; j++) {
                    table.append("    <td>Zelle ").append(i + 1).append(",").append(j + 1).append("</td>\\n");
                }
                table.append("  </tr>\\n");
            }
            table.append("</table>");
            
            editorPane.replaceSelection(table.toString());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Ungültige Eingabe für Zeilen/Spalten");
        }
    }
    
    // Public API
    public String getHTML() {
        return editorPane.getText();
    }
    
    public void setHTML(String html) {
        editorPane.setText(html);
    }
    
    public String getPlainText() {
        return editorPane.getText().replaceAll("<[^>]*>", "");
    }
    
    public JEditorPane getEditorPane() {
        return editorPane;
    }
}
