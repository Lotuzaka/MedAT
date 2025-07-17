package ui.merkfaehigkeit;

import model.AllergyCardData;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;

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
        setPreferredSize(new Dimension(320, 85)); // Slightly wider for bigger image area
        setOpaque(false); // Make transparent so custom painting shows
        
        // Add inner padding
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        buildImageArea();
        // No formatter needed - we'll handle the TT.Monat format manually
        dobField = new JFormattedTextField();
        dobField.setColumns(10); // Set width for "TT.Monat" format (e.g., "15.Januar")
        
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
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw rounded rectangle background
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
        
        // Draw rounded border
        g2.setColor(Color.GRAY);
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 15, 15);
        
        g2.dispose();
    }

    private void buildImageArea() {
        imageLabel.setPreferredSize(new Dimension(80, 75)); // Larger image area for better proportion
        imageLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        imageLabel.setBackground(new Color(248, 248, 248));
        imageLabel.setOpaque(true);
        imageLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        imageLabel.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e) {
                chooseImage();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                imageLabel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(100, 150, 200), 2),
                    BorderFactory.createEmptyBorder(1, 1, 1, 1)
                ));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                imageLabel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
                    BorderFactory.createEmptyBorder(2, 2, 2, 2)
                ));
            }
        });
        add(imageLabel, BorderLayout.LINE_START);
    }

    private void chooseImage() {
        // Use native file dialog for better user experience
        try {
            // Try to use the native file dialog through AWT FileDialog
            Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
            if (parentFrame == null) {
                parentFrame = new Frame();
            }
            
            FileDialog fd = new FileDialog(parentFrame, "Bild auswählen", FileDialog.LOAD);
            fd.setFile("*.jpg;*.jpeg;*.png;*.gif;*.bmp"); // Set file filter hint
            fd.setMultipleMode(false);
            fd.setVisible(true);
            
            String filename = fd.getFile();
            String directory = fd.getDirectory();
            
            if (filename != null && directory != null) {
                File f = new File(directory, filename);
                loadImageFile(f);
            }
        } catch (Exception e) {
            // Fallback to Swing FileChooser if native dialog fails
            useSwingFileChooser();
        }
    }
    
    private void useSwingFileChooser() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Bild auswählen");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Bilddateien (*.jpg, *.jpeg, *.png, *.gif, *.bmp)", 
            "jpg", "jpeg", "png", "gif", "bmp"));
        fc.setAcceptAllFileFilterUsed(false);
        fc.setMultiSelectionEnabled(false);
        
        // Set to pictures directory if available
        String userHome = System.getProperty("user.home");
        File picturesDir = new File(userHome, "Pictures");
        if (picturesDir.exists()) {
            fc.setCurrentDirectory(picturesDir);
        }
        
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            loadImageFile(f);
        }
    }
    
    private void loadImageFile(File f) {
        try {
            BufferedImage img = ImageIO.read(f);
            if (img != null) {
                Image scaled = img.getScaledInstance(80, 75, Image.SCALE_SMOOTH);
                BufferedImage bi = new BufferedImage(80, 75, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = bi.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.drawImage(scaled, 0, 0, null);
                g2.dispose();
                
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(bi, "png", bos);
                imageBytes = bos.toByteArray();
                imageLabel.setIcon(new ImageIcon(bi));
                imageLabel.setText("");
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, 
                "Fehler beim Laden des Bildes: " + ex.getMessage(), 
                "Bildfehler", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void initInfoPanel() {
        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setOpaque(false); // Make transparent for rounded corners
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 8, 2, 4); // More left margin to shift content right
        c.anchor = GridBagConstraints.WEST;
        
        // Style text fields for modern look
        styleTextField(nameField);
        styleTextField(dobField);
        styleTextField(allergiesField);
        styleTextField(numberField);
        styleTextField(countryField);
        
        // Name
        c.gridx = 0; c.gridy = 0; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        JLabel nameLabel = createStyledLabel("Name:");
        infoPanel.add(nameLabel, c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        infoPanel.add(nameField, c);
        
        // Date of Birth
        c.gridx = 0; c.gridy++; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        JLabel dobLabel = createStyledLabel("Geburtsdatum:");
        infoPanel.add(dobLabel, c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 0.6; // Slightly shorter
        infoPanel.add(dobField, c);
        
        // Medication - Radio buttons
        c.gridx = 0; c.gridy++; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        JLabel medicationLabel = createStyledLabel("Medikamente:");
        infoPanel.add(medicationLabel, c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        JPanel medicationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        medicationPanel.setOpaque(false);
        styleRadioButton(medicationYes);
        styleRadioButton(medicationNo);
        medicationPanel.add(medicationYes);
        medicationPanel.add(medicationNo);
        infoPanel.add(medicationPanel, c);
        
        // Blood group - Radio buttons
        c.gridx = 0; c.gridy++; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        JLabel bloodLabel = createStyledLabel("Blutgruppe:");
        infoPanel.add(bloodLabel, c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        JPanel bloodPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        bloodPanel.setOpaque(false);
        styleRadioButton(bloodA);
        styleRadioButton(bloodB);
        styleRadioButton(bloodAB);
        styleRadioButton(blood0);
        bloodPanel.add(bloodA);
        bloodPanel.add(bloodB);
        bloodPanel.add(bloodAB);
        bloodPanel.add(blood0);
        infoPanel.add(bloodPanel, c);
        
        // Allergies - Single line text field
        c.gridx = 0; c.gridy++; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        JLabel allergiesLabel = createStyledLabel("Allergien:");
        infoPanel.add(allergiesLabel, c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        infoPanel.add(allergiesField, c);
        
        // ID Number
        c.gridx = 0; c.gridy++; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        JLabel numberLabel = createStyledLabel("Ausweisnr.:");
        infoPanel.add(numberLabel, c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 0.5; // Shorter field
        infoPanel.add(numberField, c);
        
        // Country
        c.gridx = 0; c.gridy++; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        JLabel countryLabel = createStyledLabel("Land:");
        infoPanel.add(countryLabel, c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        infoPanel.add(countryField, c);

        nameField.setToolTipText("Voller Name");
        dobField.setToolTipText("TT.Monat (z.B. 15.Januar)");
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
    
    /**
     * Parse date in "TT.Monat" format (e.g., "15.Januar") to LocalDate.
     * Uses current year as default.
     */
    private LocalDate parseDDMMMFormat(String dateText) {
        if (dateText == null || dateText.trim().isEmpty()) {
            return null;
        }
        
        String[] parts = dateText.trim().split("\\.");
        if (parts.length != 2) {
            return null;
        }
        
        try {
            int day = Integer.parseInt(parts[0]);
            String monthName = parts[1].toLowerCase();
            
            // Map German month names to month numbers
            int month = switch (monthName) {
                case "januar" -> 1;
                case "februar" -> 2;
                case "märz" -> 3;
                case "april" -> 4;
                case "mai" -> 5;
                case "juni" -> 6;
                case "juli" -> 7;
                case "august" -> 8;
                case "september" -> 9;
                case "oktober" -> 10;
                case "november" -> 11;
                case "dezember" -> 12;
                default -> throw new IllegalArgumentException("Invalid month: " + monthName);
            };
            
            // Use current year as default
            int year = LocalDate.now().getYear();
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Format LocalDate to "TT.Monat" format (e.g., "15.Januar").
     */
    private String formatToDDMMM(LocalDate date) {
        if (date == null) {
            return "";
        }
        
        String[] monthNames = {
            "Januar", "Februar", "März", "April", "Mai", "Juni",
            "Juli", "August", "September", "Oktober", "November", "Dezember"
        };
        
        int day = date.getDayOfMonth();
        String monthName = monthNames[date.getMonthValue() - 1];
        
        return String.format("%02d.%s", day, monthName);
    }

    public AllergyCardData toModel() {
        String name = nameField.getText().trim();
        LocalDate dob = null;
        try {
            // Parse "TT.Monat" format (e.g., "15.Januar")
            String dobText = dobField.getText().trim();
            if (!dobText.isEmpty()) {
                dob = parseDDMMMFormat(dobText);
            }
        } catch(Exception ex) {
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
            // Format as "TT.Monat" (e.g., "15.Januar")
            dobField.setText(formatToDDMMM(d.geburtsdatum()));
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
    
    private JLabel createStyledLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 11));
        label.setForeground(new Color(60, 60, 60));
        return label;
    }
    
    private void styleTextField(JTextField field) {
        field.setFont(new Font("SansSerif", Font.PLAIN, 11));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
            BorderFactory.createEmptyBorder(2, 4, 2, 4)
        ));
        field.setBackground(Color.WHITE);
        
        // Add focus effects
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(100, 150, 200), 2),
                    BorderFactory.createEmptyBorder(1, 3, 1, 3)
                ));
            }
            public void focusLost(java.awt.event.FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                    BorderFactory.createEmptyBorder(2, 4, 2, 4)
                ));
            }
        });
    }
    
    private void styleRadioButton(JRadioButton button) {
        button.setFont(new Font("SansSerif", Font.PLAIN, 10));
        button.setOpaque(false);
        button.setForeground(new Color(60, 60, 60));
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
