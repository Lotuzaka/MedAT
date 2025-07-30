package ui.textverstaendnis;

import dao.PassageDAO;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * Professional Word-like editor panel for Textverständnis passages.
 * Includes comprehensive formatting, find/replace, and document management.
 */
public class ProfessionalTextPassagePanel extends JPanel {
    
    private final AdvancedTextEditor textEditor;
    private final JTextField sourceField = new JTextField();
    private final JTextField titleField = new JTextField();
    private final JComboBox<String> difficultyCombo;
    private final JSpinner wordCountSpinner;
    
    private final PassageDAO dao;
    private final int subcategoryId;
    private Integer simulationId;
    private int currentIndex = 1;
    private PassageDAO.Passage currentPassage;
    private Consumer<Integer> onIndexChange;
    
    private final JToggleButton[] passageButtons = new JToggleButton[5];
    private final JLabel statusLabel = new JLabel("Bereit");
    
    public ProfessionalTextPassagePanel(PassageDAO dao, int subcategoryId, Integer simulationId) {
        this.dao = dao;
        this.subcategoryId = subcategoryId;
        this.simulationId = simulationId;
        
        // Initialize difficulty combo
        difficultyCombo = new JComboBox<>(new String[]{"Leicht", "Mittel", "Schwer"});
        difficultyCombo.setSelectedItem("Mittel");
        
        // Initialize word count spinner
        wordCountSpinner = new JSpinner(new SpinnerNumberModel(300, 50, 1000, 50));
        
        setLayout(new BorderLayout());
        
        // Create and add components
        add(createNavigationPanel(), BorderLayout.NORTH);
        
        // Main content with advanced editor
        textEditor = new AdvancedTextEditor();
        add(textEditor, BorderLayout.CENTER);
        
        // Document properties panel
        add(createPropertiesPanel(), BorderLayout.SOUTH);
        
        // Setup auto-save
        setupAutoSave();
    }
    
    private JPanel createNavigationPanel() {
        JPanel navPanel = new JPanel(new BorderLayout());
        navPanel.setBorder(BorderFactory.createTitledBorder("Textpassagen Navigation"));
        
        // Passage selection buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ButtonGroup group = new ButtonGroup();
        
        for (int i = 0; i < passageButtons.length; i++) {
            int idx = i + 1;
            JToggleButton btn = new JToggleButton("Text " + idx);
            btn.setPreferredSize(new Dimension(80, 30));
            btn.addActionListener(e -> switchPassage(idx));
            group.add(btn);
            buttonPanel.add(btn);
            passageButtons[i] = btn;
        }
        
        // Add new passage button
        JButton addBtn = new JButton("+ Neue Passage");
        addBtn.setBackground(new Color(52, 199, 89));
        addBtn.setForeground(Color.WHITE);
        addBtn.setPreferredSize(new Dimension(120, 30));
        addBtn.addActionListener(e -> createNewPassage());
        buttonPanel.add(addBtn);
        
        navPanel.add(buttonPanel, BorderLayout.WEST);
        
        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton loadBtn = new JButton("Laden");
        loadBtn.addActionListener(e -> loadPassage());
        actionPanel.add(loadBtn);
        
        JButton saveBtn = new JButton("Speichern");
        saveBtn.setBackground(new Color(52, 199, 89));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.addActionListener(e -> savePassage());
        actionPanel.add(saveBtn);
        
        JButton exportBtn = new JButton("Export");
        exportBtn.addActionListener(e -> exportPassage());
        actionPanel.add(exportBtn);
        
        JButton importBtn = new JButton("Import");
        importBtn.addActionListener(e -> importPassage());
        actionPanel.add(importBtn);
        
        navPanel.add(actionPanel, BorderLayout.EAST);
        
        return navPanel;
    }
    
    private JPanel createPropertiesPanel() {
        JPanel propsPanel = new JPanel();
        propsPanel.setLayout(new BoxLayout(propsPanel, BoxLayout.Y_AXIS));
        propsPanel.setBorder(BorderFactory.createTitledBorder("Dokumenteigenschaften"));
        
        // Title and source row
        JPanel titleSourcePanel = new JPanel(new GridLayout(1, 4, 5, 5));
        titleSourcePanel.add(new JLabel("Titel:"));
        titleSourcePanel.add(titleField);
        titleSourcePanel.add(new JLabel("Quelle:"));
        titleSourcePanel.add(sourceField);
        propsPanel.add(titleSourcePanel);
        
        // Difficulty and word count row
        JPanel difficultyPanel = new JPanel(new GridLayout(1, 4, 5, 5));
        difficultyPanel.add(new JLabel("Schwierigkeit:"));
        difficultyPanel.add(difficultyCombo);
        difficultyPanel.add(new JLabel("Zielwortanzahl:"));
        difficultyPanel.add(wordCountSpinner);
        propsPanel.add(difficultyPanel);
        
        // Status bar
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createLoweredBevelBorder());
        statusPanel.add(statusLabel, BorderLayout.WEST);
        
        JLabel passageInfoLabel = new JLabel();
        updatePassageInfo(passageInfoLabel);
        statusPanel.add(passageInfoLabel, BorderLayout.EAST);
        
        propsPanel.add(statusPanel);
        
        return propsPanel;
    }
    
    private void setupAutoSave() {
        // Auto-save every 30 seconds
        Timer autoSaveTimer = new Timer(30000, e -> {
            if (hasUnsavedChanges()) {
                savePassage();
                statusLabel.setText("Automatisch gespeichert um " + java.time.LocalTime.now().toString().substring(0, 8));
            }
        });
        autoSaveTimer.start();
    }
    
    private boolean hasUnsavedChanges() {
        if (currentPassage == null) {
            return !textEditor.getText().trim().isEmpty();
        }
        return !textEditor.getText().equals(currentPassage.text()) ||
               !sourceField.getText().equals(currentPassage.source() == null ? "" : currentPassage.source());
    }
    
    public void loadPassage() {
        try {
            if (simulationId != null) {
                currentPassage = dao.findBySubcategorySimulationAndIndex(subcategoryId, simulationId, currentIndex);
            } else {
                currentPassage = dao.findBySubcategoryId(subcategoryId);
            }
            
            if (currentPassage != null) {
                textEditor.setText(currentPassage.text());
                sourceField.setText(currentPassage.source() == null ? "" : currentPassage.source());
                statusLabel.setText("Passage geladen");
            } else {
                textEditor.setText("");
                sourceField.setText("");
                titleField.setText("");
                statusLabel.setText("Neue Passage");
            }
            
            // Update selected button
            if (currentIndex >= 1 && currentIndex <= passageButtons.length) {
                passageButtons[currentIndex - 1].setSelected(true);
            }
            
            // Notify listeners
            if (onIndexChange != null) {
                onIndexChange.accept(currentIndex);
            }
            
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, 
                "Fehler beim Laden der Passage: " + ex.getMessage(),
                "Ladefehler", JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("Fehler beim Laden");
        }
    }
    
    public void savePassage() {
        try {
            String text = textEditor.getText();
            String source = sourceField.getText().trim();
            String title = titleField.getText().trim();
            
            if (text.trim().isEmpty()) {
                statusLabel.setText("Kein Text zum Speichern");
                return;
            }
            
            if (currentPassage == null) {
                // Create new passage
                int id;
                if (simulationId != null) {
                    id = dao.insert(subcategoryId, simulationId, currentIndex, text, 
                                  source.isEmpty() ? null : source);
                } else {
                    id = dao.insert(subcategoryId, text, source.isEmpty() ? null : source);
                }
                currentPassage = new PassageDAO.Passage(id, subcategoryId, simulationId, 
                                                       currentIndex, text, source.isEmpty() ? null : source);
                statusLabel.setText("Neue Passage gespeichert");
            } else {
                // Update existing passage
                if (simulationId != null) {
                    dao.update(currentPassage.id(), simulationId, currentIndex, text, 
                             source.isEmpty() ? null : source);
                } else {
                    dao.update(currentPassage.id(), text, source.isEmpty() ? null : source);
                }
                currentPassage = new PassageDAO.Passage(currentPassage.id(), subcategoryId, 
                                                       simulationId, currentIndex, text, 
                                                       source.isEmpty() ? null : source);
                statusLabel.setText("Passage aktualisiert");
            }
            
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, 
                "Fehler beim Speichern der Passage: " + ex.getMessage(),
                "Speicherfehler", JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("Fehler beim Speichern");
        }
    }
    
    private void switchPassage(int index) {
        // Save current passage if modified
        if (hasUnsavedChanges()) {
            int result = JOptionPane.showConfirmDialog(this,
                "Änderungen speichern?", "Ungespeicherte Änderungen",
                JOptionPane.YES_NO_CANCEL_OPTION);
            
            if (result == JOptionPane.YES_OPTION) {
                savePassage();
            } else if (result == JOptionPane.CANCEL_OPTION) {
                return; // Don't switch
            }
        }
        
        currentIndex = index;
        loadPassage();
    }
    
    private void createNewPassage() {
        // Find next available index
        int newIndex = 1;
        for (int i = 1; i <= 5; i++) {
            try {
                PassageDAO.Passage existing = dao.findBySubcategorySimulationAndIndex(
                    subcategoryId, simulationId, i);
                if (existing == null) {
                    newIndex = i;
                    break;
                }
            } catch (SQLException ex) {
                // Assume this index is available
                newIndex = i;
                break;
            }
        }
        
        currentIndex = newIndex;
        currentPassage = null;
        textEditor.setText("");
        sourceField.setText("");
        titleField.setText("");
        passageButtons[currentIndex - 1].setSelected(true);
        statusLabel.setText("Neue Passage erstellt");
    }
    
    private void exportPassage() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Text Dateien (*.txt)", "txt"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                java.nio.file.Files.write(fileChooser.getSelectedFile().toPath(),
                    textEditor.getText().getBytes());
                statusLabel.setText("Passage exportiert");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Fehler beim Export: " + ex.getMessage());
            }
        }
    }
    
    private void importPassage() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Text Dateien (*.txt)", "txt"));
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(
                    fileChooser.getSelectedFile().toPath()));
                textEditor.setText(content);
                statusLabel.setText("Passage importiert");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Fehler beim Import: " + ex.getMessage());
            }
        }
    }
    
    private void updatePassageInfo(JLabel label) {
        Timer timer = new Timer(1000, e -> {
            String text = textEditor.getText();
            int wordCount = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
            int charCount = text.length();
            label.setText(String.format("Wörter: %d | Zeichen: %d", wordCount, charCount));
        });
        timer.start();
    }
    
    // Public API methods
    public PassageDAO.Passage getCurrentPassage() {
        return currentPassage;
    }
    
    public int getCurrentIndex() {
        return currentIndex;
    }
    
    public String getPassageText() {
        return textEditor.getText();
    }
    
    public void setOnIndexChange(Consumer<Integer> callback) {
        this.onIndexChange = callback;
    }
    
    public void setSimulationId(Integer simulationId) {
        this.simulationId = simulationId;
    }
    
    public AdvancedTextEditor getTextEditor() {
        return textEditor;
    }
}
