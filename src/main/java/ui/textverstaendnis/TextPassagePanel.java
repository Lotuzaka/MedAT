package ui.textverstaendnis;

import dao.PassageDAO;

import javax.swing.*;
import javax.swing.text.StyledEditorKit;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;

/**
 * Simple editor panel for Textverständnis passages.
 */
public class TextPassagePanel extends JPanel {
    private final JTextPane textPane = new JTextPane();
    private final JTextField sourceField = new JTextField();

    private final PassageDAO dao;
    private final int subcategoryId;
    private Integer simulationId;
    private int currentIndex = 1;
    private PassageDAO.Passage currentPassage;

    public TextPassagePanel(PassageDAO dao, int subcategoryId) {
        this(dao, subcategoryId, null);
    }

    public TextPassagePanel(PassageDAO dao, int subcategoryId, Integer simulationId) {
        this.dao = dao;
        this.subcategoryId = subcategoryId;
        this.simulationId = simulationId;
        setLayout(new BorderLayout());
        buildToolbar();
        add(new JScrollPane(textPane), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.add(new JLabel("Quelle:"), BorderLayout.WEST);
        south.add(sourceField, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private final JToggleButton[] passageButtons = new JToggleButton[5];
    private void buildToolbar() {
        JPanel toolbarPanel = new JPanel();
        toolbarPanel.setLayout(new BorderLayout());

        JToolBar navBar = new JToolBar();
        navBar.setFloatable(false);
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < passageButtons.length; i++) {
            int idx = i + 1;
            JToggleButton btn = new JToggleButton("Text " + idx);
            btn.addActionListener(e -> switchPassage(idx));
            group.add(btn);
            navBar.add(btn);
            passageButtons[i] = btn;
        }
        JButton addBtn = new JButton("+");
        addBtn.addActionListener(e -> createNewPassage());
        navBar.add(addBtn);

        toolbarPanel.add(navBar, BorderLayout.WEST);

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        Action bold = new StyledEditorKit.BoldAction();
        bold.putValue(Action.NAME, "B");
        bar.add(bold);

        Action italic = new StyledEditorKit.ItalicAction();
        italic.putValue(Action.NAME, "I");
        bar.add(italic);

        Action bullet = new AbstractAction("\u2022") {
            @Override
            public void actionPerformed(ActionEvent e) {
                textPane.replaceSelection("\u2022 ");
            }
        };
        bar.add(bullet);

        JButton loadBtn = new JButton("Load");
        loadBtn.addActionListener(e -> loadPassage());
        bar.add(loadBtn);

        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> savePassage());
        bar.add(saveBtn);

        toolbarPanel.add(bar, BorderLayout.EAST);
        add(toolbarPanel, BorderLayout.NORTH);
    }

    public void loadPassage() {
        try {
            if (simulationId != null) {
                currentPassage = dao.findBySubcategorySimulationAndIndex(subcategoryId, simulationId, currentIndex);
            } else {
                currentPassage = dao.findBySubcategoryId(subcategoryId);
            }
            if (currentPassage != null) {
                textPane.setText(currentPassage.text());
                sourceField.setText(currentPassage.source() == null ? "" : currentPassage.source());
            } else {
                textPane.setText("");
                sourceField.setText("");
            }
            if (currentIndex >= 1 && currentIndex <= passageButtons.length) {
                passageButtons[currentIndex - 1].setSelected(true);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Fehler beim Laden: " + ex.getMessage(),
                    "Load", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void savePassage() {
        try {
            String text = textPane.getText();
            String src = sourceField.getText().trim();
            if (currentPassage == null) {
                if (!text.trim().isEmpty()) {
                    int id;
                    if (simulationId != null) {
                        id = dao.insert(subcategoryId, simulationId, currentIndex, text, src.isEmpty() ? null : src);
                    } else {
                        id = dao.insert(subcategoryId, text, src.isEmpty() ? null : src);
                    }
                    currentPassage = new PassageDAO.Passage(id, subcategoryId, simulationId, currentIndex, text, src.isEmpty() ? null : src);
                }
            } else {
                if (simulationId != null) {
                    dao.update(currentPassage.id(), simulationId, currentIndex, text, src.isEmpty() ? null : src);
                } else {
                    dao.update(currentPassage.id(), text, src.isEmpty() ? null : src);
                }
                currentPassage = new PassageDAO.Passage(currentPassage.id(), subcategoryId, simulationId, currentIndex, text, src.isEmpty() ? null : src);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Fehler beim Speichern: " + ex.getMessage(),
                    "Save", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void switchPassage(int index) {
        savePassage();
        currentIndex = index;
        loadPassage();
    }

    private void createNewPassage() {
        for (int i = 1; i <= passageButtons.length; i++) {
            try {
                if (simulationId != null && dao.findBySubcategorySimulationAndIndex(subcategoryId, simulationId, i) == null) {
                    passageButtons[i - 1].setSelected(true);
                    currentIndex = i;
                    textPane.setText("");
                    sourceField.setText("");
                    currentPassage = null;
                    return;
                }
            } catch (SQLException ignored) {
            }
        }
    }

    public void setSimulationId(Integer simulationId) {
        savePassage();
        this.simulationId = simulationId;
        currentIndex = 1;
        currentPassage = null;
        loadPassage();
    }
}
