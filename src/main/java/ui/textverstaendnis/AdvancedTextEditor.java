package ui.textverstaendnis;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Arrays;

/**
 * Advanced Word-like text editor with comprehensive formatting options
 */
public class AdvancedTextEditor extends JPanel {
    
    private final JTextPane textPane;
    private final StyledDocument document;
    private JComboBox<String> fontFamilyCombo;
    private JComboBox<Integer> fontSizeCombo;
    private JToggleButton boldButton, italicButton, underlineButton;
    private JButton alignLeftButton, alignCenterButton, alignRightButton, alignJustifyButton;
    private JButton colorButton, backgroundButton;
    private JButton bulletButton, numberedButton;
    private JButton indentButton, outdentButton;
    private JTextField findField;
    
    public AdvancedTextEditor() {
        setLayout(new BorderLayout());
        
        // Initialize text pane with styled document
        textPane = new JTextPane();
        document = textPane.getStyledDocument();
        
        // Create comprehensive toolbar
        add(createToolbarPanel(), BorderLayout.NORTH);
        
        // Add text pane with scroll
        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setPreferredSize(new Dimension(600, 400));
        add(scrollPane, BorderLayout.CENTER);
        
        // Add status bar
        add(createStatusBar(), BorderLayout.SOUTH);
        
        // Setup key bindings
        setupKeyBindings();
        
        // Set default styling
        setupDefaultStyles();
    }
    
    private JPanel createToolbarPanel() {
        JPanel toolbarPanel = new JPanel();
        toolbarPanel.setLayout(new BoxLayout(toolbarPanel, BoxLayout.Y_AXIS));
        
        // Main formatting toolbar
        toolbarPanel.add(createMainToolbar());
        
        // Paragraph and alignment toolbar
        toolbarPanel.add(createAlignmentToolbar());
        
        // Find/Replace toolbar
        toolbarPanel.add(createFindToolbar());
        
        return toolbarPanel;
    }
    
    private JToolBar createMainToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        
        // Font family
        String[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        fontFamilyCombo = new JComboBox<>(Arrays.copyOf(fonts, Math.min(fonts.length, 20))); // Limit for performance
        fontFamilyCombo.setSelectedItem("Arial");
        fontFamilyCombo.setMaximumSize(new Dimension(150, 25));
        fontFamilyCombo.addActionListener(e -> applyFontFamily());
        toolbar.add(fontFamilyCombo);
        
        toolbar.addSeparator();
        
        // Font size
        Integer[] sizes = {8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 26, 28, 36, 48, 72};
        fontSizeCombo = new JComboBox<>(sizes);
        fontSizeCombo.setSelectedItem(12);
        fontSizeCombo.setMaximumSize(new Dimension(60, 25));
        fontSizeCombo.addActionListener(e -> applyFontSize());
        toolbar.add(fontSizeCombo);
        
        toolbar.addSeparator();
        
        // Bold, Italic, Underline
        boldButton = new JToggleButton("<html><b>B</b></html>");
        boldButton.setToolTipText("Fett (Ctrl+B)");
        boldButton.addActionListener(e -> applyBold());
        toolbar.add(boldButton);
        
        italicButton = new JToggleButton("<html><i>I</i></html>");
        italicButton.setToolTipText("Kursiv (Ctrl+I)");
        italicButton.addActionListener(e -> applyItalic());
        toolbar.add(italicButton);
        
        underlineButton = new JToggleButton("<html><u>U</u></html>");
        underlineButton.setToolTipText("Unterstrichen (Ctrl+U)");
        underlineButton.addActionListener(e -> applyUnderline());
        toolbar.add(underlineButton);
        
        toolbar.addSeparator();
        
        // Colors
        colorButton = new JButton("A");
        colorButton.setForeground(Color.BLACK);
        colorButton.setToolTipText("Schriftfarbe");
        colorButton.addActionListener(e -> chooseTextColor());
        toolbar.add(colorButton);
        
        backgroundButton = new JButton("H");
        backgroundButton.setBackground(Color.YELLOW);
        backgroundButton.setToolTipText("Hintergrundfarbe");
        backgroundButton.addActionListener(e -> chooseBackgroundColor());
        toolbar.add(backgroundButton);
        
        return toolbar;
    }
    
    private JToolBar createAlignmentToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        
        // Alignment buttons
        alignLeftButton = new JButton("⬅");
        alignLeftButton.setToolTipText("Linksbündig");
        alignLeftButton.addActionListener(e -> applyAlignment(StyleConstants.ALIGN_LEFT));
        toolbar.add(alignLeftButton);
        
        alignCenterButton = new JButton("⬌");
        alignCenterButton.setToolTipText("Zentriert");
        alignCenterButton.addActionListener(e -> applyAlignment(StyleConstants.ALIGN_CENTER));
        toolbar.add(alignCenterButton);
        
        alignRightButton = new JButton("➡");
        alignRightButton.setToolTipText("Rechtsbündig");
        alignRightButton.addActionListener(e -> applyAlignment(StyleConstants.ALIGN_RIGHT));
        toolbar.add(alignRightButton);
        
        alignJustifyButton = new JButton("⬍");
        alignJustifyButton.setToolTipText("Blocksatz");
        alignJustifyButton.addActionListener(e -> applyAlignment(StyleConstants.ALIGN_JUSTIFIED));
        toolbar.add(alignJustifyButton);
        
        toolbar.addSeparator();
        
        // List buttons
        bulletButton = new JButton("• List");
        bulletButton.setToolTipText("Aufzählungsliste");
        bulletButton.addActionListener(e -> insertBulletPoint());
        toolbar.add(bulletButton);
        
        numberedButton = new JButton("1. List");
        numberedButton.setToolTipText("Nummerierte Liste");
        numberedButton.addActionListener(e -> insertNumberedPoint());
        toolbar.add(numberedButton);
        
        toolbar.addSeparator();
        
        // Indent buttons
        indentButton = new JButton("→");
        indentButton.setToolTipText("Einrücken");
        indentButton.addActionListener(e -> adjustIndent(20));
        toolbar.add(indentButton);
        
        outdentButton = new JButton("←");
        outdentButton.setToolTipText("Ausrücken");
        outdentButton.addActionListener(e -> adjustIndent(-20));
        toolbar.add(outdentButton);
        
        return toolbar;
    }
    
    private JToolBar createFindToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        
        toolbar.add(new JLabel("Suchen: "));
        
        findField = new JTextField(15);
        findField.addActionListener(e -> findText());
        toolbar.add(findField);
        
        JButton findButton = new JButton("Suchen");
        findButton.addActionListener(e -> findText());
        toolbar.add(findButton);
        
        JButton replaceButton = new JButton("Ersetzen");
        replaceButton.addActionListener(e -> showReplaceDialog());
        toolbar.add(replaceButton);
        
        toolbar.addSeparator();
        
        // Word count
        JLabel wordCountLabel = new JLabel("Wörter: 0");
        toolbar.add(wordCountLabel);
        
        // Update word count on text change
        textPane.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateWordCount(wordCountLabel); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateWordCount(wordCountLabel); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateWordCount(wordCountLabel); }
        });
        
        return toolbar;
    }
    
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusBar.setBorder(BorderFactory.createLoweredBevelBorder());
        statusBar.add(new JLabel("Bereit"));
        return statusBar;
    }
    
    private void setupKeyBindings() {
        InputMap inputMap = textPane.getInputMap();
        ActionMap actionMap = textPane.getActionMap();
        
        // Ctrl+B for Bold
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.CTRL_DOWN_MASK), "bold");
        actionMap.put("bold", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { applyBold(); }
        });
        
        // Ctrl+I for Italic
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, KeyEvent.CTRL_DOWN_MASK), "italic");
        actionMap.put("italic", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { applyItalic(); }
        });
        
        // Ctrl+U for Underline
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.CTRL_DOWN_MASK), "underline");
        actionMap.put("underline", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { applyUnderline(); }
        });
        
        // Ctrl+F for Find
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), "find");
        actionMap.put("find", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { findField.requestFocus(); }
        });
    }
    
    private void setupDefaultStyles() {
        Style defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        StyleConstants.setFontFamily(defaultStyle, "Arial");
        StyleConstants.setFontSize(defaultStyle, 12);
    }
    
    // Formatting methods
    private void applyFontFamily() {
        String fontFamily = (String) fontFamilyCombo.getSelectedItem();
        applyStyleToSelection(attr -> StyleConstants.setFontFamily(attr, fontFamily));
    }
    
    private void applyFontSize() {
        Integer fontSize = (Integer) fontSizeCombo.getSelectedItem();
        if (fontSize != null) {
            applyStyleToSelection(attr -> StyleConstants.setFontSize(attr, fontSize));
        }
    }
    
    private void applyBold() {
        boolean isBold = boldButton.isSelected();
        applyStyleToSelection(attr -> StyleConstants.setBold(attr, isBold));
    }
    
    private void applyItalic() {
        boolean isItalic = italicButton.isSelected();
        applyStyleToSelection(attr -> StyleConstants.setItalic(attr, isItalic));
    }
    
    private void applyUnderline() {
        boolean isUnderline = underlineButton.isSelected();
        applyStyleToSelection(attr -> StyleConstants.setUnderline(attr, isUnderline));
    }
    
    private void applyStyleToSelection(StyleModifier modifier) {
        int start = textPane.getSelectionStart();
        int end = textPane.getSelectionEnd();
        
        if (start == end) {
            // No selection, apply to input attributes
            MutableAttributeSet inputAttributes = textPane.getInputAttributes();
            modifier.apply(inputAttributes);
        } else {
            // Apply to selection
            SimpleAttributeSet attr = new SimpleAttributeSet();
            modifier.apply(attr);
            document.setCharacterAttributes(start, end - start, attr, false);
        }
        textPane.requestFocus();
    }
    
    private void applyAlignment(int alignment) {
        int pos = textPane.getCaretPosition();
        Element paragraph = document.getParagraphElement(pos);
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setAlignment(attr, alignment);
        document.setParagraphAttributes(paragraph.getStartOffset(), 
                                      paragraph.getEndOffset() - paragraph.getStartOffset(), 
                                      attr, false);
    }
    
    private void chooseTextColor() {
        Color color = JColorChooser.showDialog(this, "Schriftfarbe wählen", Color.BLACK);
        if (color != null) {
            colorButton.setForeground(color);
            applyStyleToSelection(attr -> StyleConstants.setForeground(attr, color));
        }
    }
    
    private void chooseBackgroundColor() {
        Color color = JColorChooser.showDialog(this, "Hintergrundfarbe wählen", Color.YELLOW);
        if (color != null) {
            backgroundButton.setBackground(color);
            applyStyleToSelection(attr -> StyleConstants.setBackground(attr, color));
        }
    }
    
    private void insertBulletPoint() {
        try {
            int pos = textPane.getCaretPosition();
            String bullet = "\n• ";
            document.insertString(pos, bullet, null);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    private void insertNumberedPoint() {
        try {
            int pos = textPane.getCaretPosition();
            String text = textPane.getText();
            int lineStart = text.lastIndexOf('\n', pos - 1) + 1;
            String lineText = text.substring(lineStart, pos);
            
            // Find next number
            int nextNum = 1;
            String[] lines = text.split("\n");
            for (String line : lines) {
                if (line.trim().matches("^\\d+\\..*")) {
                    try {
                        int num = Integer.parseInt(line.trim().split("\\.")[0]);
                        nextNum = Math.max(nextNum, num + 1);
                    } catch (NumberFormatException ignored) {}
                }
            }
            
            String numbered = "\n" + nextNum + ". ";
            document.insertString(pos, numbered, null);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    private void adjustIndent(int amount) {
        int pos = textPane.getCaretPosition();
        Element paragraph = document.getParagraphElement(pos);
        AttributeSet attr = paragraph.getAttributes();
        float currentIndent = StyleConstants.getLeftIndent(attr);
        
        SimpleAttributeSet newAttr = new SimpleAttributeSet();
        StyleConstants.setLeftIndent(newAttr, Math.max(0, currentIndent + amount));
        document.setParagraphAttributes(paragraph.getStartOffset(), 
                                      paragraph.getEndOffset() - paragraph.getStartOffset(), 
                                      newAttr, false);
    }
    
    private void findText() {
        String searchText = findField.getText();
        if (searchText.isEmpty()) return;
        
        String content = textPane.getText().toLowerCase();
        String search = searchText.toLowerCase();
        int index = content.indexOf(search, textPane.getCaretPosition());
        
        if (index == -1) {
            index = content.indexOf(search, 0); // Search from beginning
        }
        
        if (index != -1) {
            textPane.setSelectionStart(index);
            textPane.setSelectionEnd(index + search.length());
        } else {
            JOptionPane.showMessageDialog(this, "Text nicht gefunden: " + searchText);
        }
    }
    
    private void showReplaceDialog() {
        String find = JOptionPane.showInputDialog(this, "Suchen nach:");
        if (find == null || find.isEmpty()) return;
        
        String replace = JOptionPane.showInputDialog(this, "Ersetzen durch:");
        if (replace == null) return;
        
        String content = textPane.getText();
        String newContent = content.replaceAll(find, replace);
        textPane.setText(newContent);
    }
    
    private void updateWordCount(JLabel label) {
        String text = textPane.getText().trim();
        if (text.isEmpty()) {
            label.setText("Wörter: 0");
        } else {
            int wordCount = text.split("\\s+").length;
            label.setText("Wörter: " + wordCount);
        }
    }
    
    // Public API methods
    public String getText() {
        return textPane.getText();
    }
    
    public void setText(String text) {
        textPane.setText(text);
    }
    
    public JTextPane getTextPane() {
        return textPane;
    }
    
    @FunctionalInterface
    private interface StyleModifier {
        void apply(MutableAttributeSet attr);
    }
}
