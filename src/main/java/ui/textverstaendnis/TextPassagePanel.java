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
    private PassageDAO.Passage currentPassage;

    public TextPassagePanel(PassageDAO dao, int subcategoryId) {
        this.dao = dao;
        this.subcategoryId = subcategoryId;
        setLayout(new BorderLayout());
        buildToolbar();
        add(new JScrollPane(textPane), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.add(new JLabel("Quelle:"), BorderLayout.WEST);
        south.add(sourceField, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private void buildToolbar() {
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

        add(bar, BorderLayout.NORTH);
    }

    public void loadPassage() {
        try {
            currentPassage = dao.findBySubcategoryId(subcategoryId);
            if (currentPassage != null) {
                textPane.setText(currentPassage.text());
                sourceField.setText(currentPassage.source() == null ? "" : currentPassage.source());
            } else {
                textPane.setText("");
                sourceField.setText("");
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
                    int id = dao.insert(subcategoryId, text, src.isEmpty() ? null : src);
                    currentPassage = new PassageDAO.Passage(id, subcategoryId, null, text, src.isEmpty() ? null : src);
                }
            } else {
                dao.update(currentPassage.id(), text, src.isEmpty() ? null : src);
                currentPassage = new PassageDAO.Passage(currentPassage.id(), subcategoryId, currentPassage.testSimulationId(), text, src.isEmpty() ? null : src);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Fehler beim Speichern: " + ex.getMessage(),
                    "Save", JOptionPane.ERROR_MESSAGE);
        }
    }
}
