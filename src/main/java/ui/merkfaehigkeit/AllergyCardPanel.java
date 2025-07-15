package ui.merkfaehigkeit;

import model.AllergyCardData;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Panel representing a single allergy card. */
public class AllergyCardPanel extends JPanel {
    private final JLabel imageLabel = new JLabel("Klicke zum Auswählen", SwingConstants.CENTER);
    private byte[] imageBytes;

    private final JTextField nameField = new JTextField(15);
    private final JFormattedTextField dobField;
    private final JTextField medicationField = new JTextField(15);
    private final JComboBox<String> bloodBox = new JComboBox<>(new String[]{"0+","0-","A+","A-","B+","B-","AB+","AB-"});
    private final JTextArea allergiesArea = new JTextArea(2,15);
    private final JTextField numberField = new JTextField(5);
    private final JTextField countryField = new JTextField(12);

    public AllergyCardPanel() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(260,160));
        buildImageArea();
        MaskFormatter mf = null;
        try {
            mf = new MaskFormatter("##.##.####");
            mf.setPlaceholderCharacter('_');
        } catch (ParseException e) {
            // should not happen
        }
        dobField = new JFormattedTextField(mf);
        allergiesArea.setLineWrap(true);
        allergiesArea.setWrapStyleWord(true);
        ((AbstractDocument) numberField.getDocument()).setDocumentFilter(new DigitFilter());

        initInfoPanel();
    }

    private void buildImageArea() {
        imageLabel.setPreferredSize(new Dimension(120,150));
        imageLabel.setBorder(new LineBorder(Color.DARK_GRAY));
        imageLabel.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e) {
                chooseImage();
            }
        });
        add(imageLabel, BorderLayout.LINE_START);
    }

    private void chooseImage() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Bilder", "png","jpg","jpeg"));
        if(fc.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                BufferedImage img = ImageIO.read(f);
                if(img!=null) {
                    Image scaled = img.getScaledInstance(120,150,Image.SCALE_SMOOTH);
                    BufferedImage bi = new BufferedImage(120,150,BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2 = bi.createGraphics();
                    g2.drawImage(scaled,0,0,null);
                    g2.dispose();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ImageIO.write(bi, "png", bos);
                    imageBytes = bos.toByteArray();
                    imageLabel.setIcon(new ImageIcon(bi));
                    imageLabel.setText("");
                }
            } catch(IOException ex) {
                // ignore
            }
        }
    }

    private void initInfoPanel() {
        JPanel infoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2,2,2,2);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0; infoPanel.add(new JLabel("Name"), c);
        c.gridx = 1; infoPanel.add(nameField, c);
        c.gridx = 0; c.gridy++; infoPanel.add(new JLabel("Geburtsdatum"), c);
        c.gridx = 1; infoPanel.add(dobField, c);
        c.gridx = 0; c.gridy++; infoPanel.add(new JLabel("Medikamente"), c);
        c.gridx = 1; infoPanel.add(medicationField, c);
        c.gridx = 0; c.gridy++; infoPanel.add(new JLabel("Blutgruppe"), c);
        c.gridx = 1; infoPanel.add(bloodBox, c);
        c.gridx = 0; c.gridy++; infoPanel.add(new JLabel("Allergien"), c);
        c.gridx = 1; infoPanel.add(new JScrollPane(allergiesArea), c);
        c.gridx = 0; c.gridy++; infoPanel.add(new JLabel("Ausweisnr."), c);
        c.gridx = 1; infoPanel.add(numberField, c);
        c.gridx = 0; c.gridy++; infoPanel.add(new JLabel("Land"), c);
        c.gridx = 1; infoPanel.add(countryField, c);

        nameField.setToolTipText("Voller Name");
        dobField.setToolTipText("TT.MM.JJJJ");
        medicationField.setToolTipText("Medikamenteneinnahme");
        bloodBox.setToolTipText("Blutgruppe z.B. A+");
        allergiesArea.setToolTipText("Bekannte Allergien");
        numberField.setToolTipText("Genau 5 Ziffern, z. B. 04127");
        countryField.setToolTipText("Ausstellungsland");

        add(infoPanel, BorderLayout.CENTER);
    }

    public AllergyCardData toModel() {
        String name = nameField.getText().trim();
        LocalDate dob = null;
        try {
            dob = LocalDate.parse(dobField.getText(), DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch(DateTimeParseException ex) {
            // leave null
        }
        String med = medicationField.getText().trim();
        String blood = (String) bloodBox.getSelectedItem();
        String allergies = allergiesArea.getText().trim();
        String num = numberField.getText().trim();
        String country = countryField.getText().trim();
        return new AllergyCardData(name,dob,med,blood,allergies,num,country,imageBytes);
    }

    public void load(AllergyCardData d) {
        if(d==null) return;
        nameField.setText(d.name());
        if(d.geburtsdatum()!=null) {
            dobField.setText(d.geburtsdatum().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        }
        medicationField.setText(d.medikamenteneinnahme());
        bloodBox.setSelectedItem(d.blutgruppe());
        allergiesArea.setText(d.bekannteAllergien());
        numberField.setText(d.ausweisnummer());
        countryField.setText(d.ausstellungsland());
        if(d.bildPng()!=null) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(d.bildPng()));
                imageLabel.setIcon(new ImageIcon(img));
                imageLabel.setText("");
                imageBytes = d.bildPng();
            } catch(IOException e) {
                // ignore
            }
        }
    }

    public void reset() {
        nameField.setText("");
        dobField.setValue(null);
        medicationField.setText("");
        bloodBox.setSelectedIndex(0);
        allergiesArea.setText("");
        numberField.setText("");
        countryField.setText("");
        imageLabel.setIcon(null);
        imageLabel.setText("Klicke zum Auswählen");
        imageBytes = null;
    }

    private static class DigitFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            Document doc = fb.getDocument();
            StringBuilder sb = new StringBuilder(doc.getText(0, doc.getLength()));
            sb.replace(offset, offset+length, text==null?"":text);
            if(sb.length()<=5 && sb.toString().matches("\\d*")) {
                fb.replace(offset, length, text, attrs);
            }
        }
    }
}
