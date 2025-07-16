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

    private final JTextField nameField = new JTextField();
    private final JFormattedTextField dobField;
    private final JRadioButton medicationYes = new JRadioButton("Ja");
    private final JRadioButton medicationNo = new JRadioButton("Nein");
    private final ButtonGroup medicationGroup = new ButtonGroup();
    private final JRadioButton bloodA = new JRadioButton("A");
    private final JRadioButton bloodB = new JRadioButton("B");
    private final JRadioButton bloodAB = new JRadioButton("AB");
    private final JRadioButton blood0 = new JRadioButton("0");
    private final ButtonGroup bloodGroup = new ButtonGroup();
    private final JTextField allergiesField = new JTextField();
    private final JTextField numberField = new JTextField();
    private final JTextField countryField = new JTextField();

    public AllergyCardPanel() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(300,100)); // Reduced height due to much shorter image
        
        // Add border to create ID card appearance
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY, 2), // Outer border
            BorderFactory.createEmptyBorder(5, 5, 5, 5)    // Inner padding
        ));
        
        buildImageArea();
        MaskFormatter mf = null;
        try {
            mf = new MaskFormatter("##.##.####");
            mf.setPlaceholderCharacter('_');
        } catch (ParseException e) {
            // should not happen
        }
        dobField = new JFormattedTextField(mf);
        dobField.setColumns(8); // Set width for exactly "TT.MM.YYYY" format
        
        // Set up medication radio buttons
        medicationGroup.add(medicationYes);
        medicationGroup.add(medicationNo);
        medicationNo.setSelected(true); // Default to "Nein"
        
        // Set up blood group radio buttons
        bloodGroup.add(bloodA);
        bloodGroup.add(bloodB);
        bloodGroup.add(bloodAB);
        bloodGroup.add(blood0);
        blood0.setSelected(true); // Default to "0"
        
        ((AbstractDocument) numberField.getDocument()).setDocumentFilter(new DigitFilter());

        initInfoPanel();
    }

    private void buildImageArea() {
        imageLabel.setPreferredSize(new Dimension(60,50)); // Reduced height by more than 1/3 (from 80 to 50)
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
                    Image scaled = img.getScaledInstance(60,50,Image.SCALE_SMOOTH); // Updated to new smaller dimensions
                    BufferedImage bi = new BufferedImage(60,50,BufferedImage.TYPE_INT_ARGB);
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
        
        // Name
        c.gridx = 0; c.gridy = 0; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        infoPanel.add(new JLabel("Name"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        infoPanel.add(nameField, c);
        
        // Date of Birth
        c.gridx = 0; c.gridy++; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        infoPanel.add(new JLabel("Geburtsdatum"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        infoPanel.add(dobField, c);
        
        // Medication - Radio buttons
        c.gridx = 0; c.gridy++; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        infoPanel.add(new JLabel("Medikamente"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        JPanel medicationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        medicationPanel.add(medicationYes);
        medicationPanel.add(medicationNo);
        infoPanel.add(medicationPanel, c);
        
        // Blood group - Radio buttons
        c.gridx = 0; c.gridy++; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        infoPanel.add(new JLabel("Blutgruppe"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        JPanel bloodPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bloodPanel.add(bloodA);
        bloodPanel.add(bloodB);
        bloodPanel.add(bloodAB);
        bloodPanel.add(blood0);
        infoPanel.add(bloodPanel, c);
        
        // Allergies - Single line text field
        c.gridx = 0; c.gridy++; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        infoPanel.add(new JLabel("Allergien"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        infoPanel.add(allergiesField, c);
        
        // ID Number
        c.gridx = 0; c.gridy++; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        infoPanel.add(new JLabel("Ausweisnr."), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        infoPanel.add(numberField, c);
        
        // Country
        c.gridx = 0; c.gridy++; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        infoPanel.add(new JLabel("Land"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        infoPanel.add(countryField, c);

        nameField.setToolTipText("Voller Name");
        dobField.setToolTipText("TT.MM.JJJJ");
        medicationYes.setToolTipText("Medikamenteneinnahme: Ja");
        medicationNo.setToolTipText("Medikamenteneinnahme: Nein");
        bloodA.setToolTipText("Blutgruppe A");
        bloodB.setToolTipText("Blutgruppe B");
        bloodAB.setToolTipText("Blutgruppe AB");
        blood0.setToolTipText("Blutgruppe 0");
        allergiesField.setToolTipText("Bekannte Allergien");
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
        String med = medicationYes.isSelected() ? "Ja" : "Nein";
        String blood = "";
        if (bloodA.isSelected()) blood = "A";
        else if (bloodB.isSelected()) blood = "B";
        else if (bloodAB.isSelected()) blood = "AB";
        else if (blood0.isSelected()) blood = "0";
        String allergies = allergiesField.getText().trim();
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
        // Set medication radio buttons
        if ("Ja".equals(d.medikamenteneinnahme())) {
            medicationYes.setSelected(true);
        } else {
            medicationNo.setSelected(true);
        }
        // Set blood group radio buttons
        switch(d.blutgruppe()) {
            case "A": bloodA.setSelected(true); break;
            case "B": bloodB.setSelected(true); break;
            case "AB": bloodAB.setSelected(true); break;
            case "0": blood0.setSelected(true); break;
            default: blood0.setSelected(true); break; // Default to 0
        }
        allergiesField.setText(d.bekannteAllergien());
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
        medicationNo.setSelected(true); // Default to "Nein"
        blood0.setSelected(true); // Default to "0"
        allergiesField.setText("");
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
