package generator;

import dao.AllergyCardDAO;
import model.AllergyCardData;
import ui.merkfaehigkeit.AllergyCardGridPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.Connection;
import java.util.List;

/** Generator frame for the allergy card memory test. */
public class MerkfaehigkeitGenerator {
    private JFrame frame;
    private AllergyCardGridPanel gridPanel;
    private JButton saveButton;
    private JButton resetButton;
    private Connection conn;
    private Integer sessionId;

    public MerkfaehigkeitGenerator() {}

    public MerkfaehigkeitGenerator(Connection conn, Integer sessionId) {
        this.conn = conn;
        this.sessionId = sessionId;
    }

    public void start() {
        SwingUtilities.invokeLater(this::buildUI);
    }

    private void buildUI() {
        frame = new JFrame("Merkfähigkeit – Allergieausweise");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        gridPanel = new AllergyCardGridPanel();
        JScrollPane sp = new JScrollPane(gridPanel);
        frame.add(new JPanel(), BorderLayout.CENTER); // placeholder
        frame.add(sp, BorderLayout.EAST);

        saveButton = new JButton("Speichern");
        resetButton = new JButton("Zurücksetzen");
        JPanel south = new JPanel();
        south.add(saveButton);
        south.add(resetButton);
        frame.add(south, BorderLayout.SOUTH);

        initListeners();
        frame.pack();
        frame.setVisible(true);
    }

    private void initListeners() {
        saveButton.addActionListener(this::saveAction);
        resetButton.addActionListener(e -> gridPanel.reset());
    }

    private void saveAction(ActionEvent e) {
        if(validateAndPersist()) {
            JOptionPane.showMessageDialog(frame, "Gespeichert");
        }
    }

    protected void showErrorDialog(String msg) {
        JOptionPane.showMessageDialog(frame, msg, "Fehler", JOptionPane.ERROR_MESSAGE);
    }

    protected boolean validateAndPersist() {
        List<AllergyCardData> cards = gridPanel.getAllCards();
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<cards.size();i++) {
            AllergyCardData c = cards.get(i);
            if(c.name()==null || c.name().isBlank()) {
                sb.append("Karte ").append(i+1).append(": Name fehlt\n");
            }
            if(c.geburtsdatum()==null) {
                sb.append("Karte ").append(i+1).append(": Geburtsdatum fehlt oder Format falsch\n");
            }
            if(c.ausweisnummer()==null || !c.ausweisnummer().matches("\\d{5}")) {
                sb.append("Karte ").append(i+1).append(": Ausweisnummer ungültig\n");
            }
        }
        if(sb.length()>0) {
            showErrorDialog(sb.toString());
            return false;
        }
        if(conn!=null && sessionId!=null) {
            try {
                new AllergyCardDAO(conn).insertAll(cards, sessionId);
            } catch(Exception ex) {
                showErrorDialog("Fehler beim Speichern: "+ex.getMessage());
                return false;
            }
        }
        return true;
    }

    public JFrame getFrame() { return frame; }
    public JButton getSaveButton() { return saveButton; }
}
