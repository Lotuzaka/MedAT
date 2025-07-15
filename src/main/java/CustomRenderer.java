import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.awt.ShapeWriter;
import svg.SvgBuilder;
import util.*;



// Enum for question difficulty (copied from MedatoninDB)
enum Difficulty {
    EASY(new Color(150, 190, 152), "●"),
    MEDIUM(new Color(247, 181, 127), "●●"),
    HARD(new Color(233, 151, 151), "●●●");
    public final Color color;
    public final String symbol;
    Difficulty(Color color, String symbol) {
        this.color = color;
        this.symbol = symbol;
    }
}

public class CustomRenderer extends DefaultTableCellRenderer {
    // Helper for difficulty color
    private Color getDifficultyColor(String difficulty) {
        if (difficulty == null) return Color.WHITE;
        switch (difficulty.toLowerCase()) {
            case "easy":
            case "leicht":
                return new Color(150, 190, 152); // green
            case "medium":
            case "mittel":
                return new Color(247, 181, 127); // orange
            case "hard":
            case "schwer":
                return new Color(233, 151, 151); // red
            default:
                return Color.WHITE;
        }
    }
    private final String currentSubcategory;
    private final Set<QuestionIdentifier> pendingDeleteQuestions;
    private final Icon gearIcon;

    public CustomRenderer(String currentSubcategory, Set<QuestionIdentifier> pendingDeleteQuestions, Icon gearIcon) {
        this.currentSubcategory = currentSubcategory;
        this.pendingDeleteQuestions = pendingDeleteQuestions;
        this.gearIcon = gearIcon;
    }

    private boolean isFrageRow(int row, DefaultTableModel model) {
        // A row is a question row if the value in column 0 is a string of digits
        if (row < 0 || row >= model.getRowCount()) return false;
        Object value = model.getValueAt(row, 0);
        if (value == null) return false;
        String strValue = value.toString();
        return strValue.matches("\\d+");
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        boolean isQuestionRow = isFrageRow(row, model);

        // Defensive: always render FigurenOptionsData panel if value is MedatoninDB.FigurenOptionsData
        if (value instanceof MedatoninDB.FigurenOptionsData) {
            MedatoninDB.FigurenOptionsData data = (MedatoninDB.FigurenOptionsData) value;
            List<OptionDAO> options = data.options;

            JPanel optionsPanel = new JPanel();
            optionsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 10));
            optionsPanel.setBackground(Color.WHITE);

            for (OptionDAO option : options) {
                JPanel optionPanel = new JPanel(new BorderLayout());
                optionPanel.setBackground(Color.WHITE);
                optionPanel.setOpaque(true);

                PolygonPanel shapePanel;

                if ("E".equalsIgnoreCase(option.getLabel())) {
                    JLabel optionELabel = new JLabel("Option E: X");
                    optionELabel.setFont(optionELabel.getFont().deriveFont(Font.BOLD));
                    optionsPanel.add(optionELabel);
                    continue;
                }

                try {
                    shapePanel = new PolygonPanel(
                            Collections.singletonList(new WKTReader().read(option.getShapeData())));
                    shapePanel.setAssembled(false);
                } catch (ParseException e) {
                    e.printStackTrace();
                    continue;
                }

                if (option.isCorrect()) {
                    optionPanel.setBackground(new Color(127, 204, 165, 75));
                }

                shapePanel.setPreferredSize(new java.awt.Dimension(100, 100));
                optionPanel.add(shapePanel, BorderLayout.CENTER);
                optionPanel.add(new JLabel("Option " + option.getLabel(), SwingConstants.CENTER),
                        BorderLayout.SOUTH);

                optionsPanel.add(optionPanel);
            }

            return optionsPanel;
        }
        // Fange den Fall ab, dass value ein String ist, der wie ein Objekt aussieht
        if (value instanceof String && ((String)value).startsWith("MedatoninDB$FigurenOptionsData@")) {
            JPanel errorPanel = new JPanel();
            errorPanel.setBackground(Color.WHITE);
            errorPanel.add(new JLabel("[Fehler] FigurenOptionsData als String!"));
            return errorPanel;
        }

        // Render figures for question rows (Figuren)
        if (isQuestionRow) {
            PolygonPanel panel = null;

            if (column == 1 && value instanceof FigurenGenerator.DissectedPieces) {
                FigurenGenerator.DissectedPieces dissectedPieces = (FigurenGenerator.DissectedPieces) value;
                panel = new PolygonPanel(dissectedPieces.rotatedPieces);
                panel.setAssembled(false); // Display dissected pieces

            } else if (column == 2) {
                FigurenGenerator.DissectedPieces pieces = null;
                // Column 1 holds the DissectedPieces for this row
                Object questionValue = model.getValueAt(row, 1);
                if (questionValue instanceof FigurenGenerator.DissectedPieces) {
                    pieces = (FigurenGenerator.DissectedPieces) questionValue;
                } else if (value instanceof FigurenGenerator.DissectedPieces) {
                    pieces = (FigurenGenerator.DissectedPieces) value;
                }

                if (pieces != null) {
                    panel = new PolygonPanel(pieces.originalPieces);
                    panel.setAssembled(true); // Show assembled figure from pieces
                } else if (value instanceof OptionDAO) {
                    // Fallback: render option shape directly
                    try {
                        Geometry shape = new WKTReader().read(((OptionDAO) value).getShapeData());
                        panel = new PolygonPanel(Collections.singletonList(shape));
                        panel.setAssembled(false);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (panel != null) {
                panel.setPreferredSize(new java.awt.Dimension(200, 200));
                return panel;
            }
        }

        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        // Handle multiline text for Syllogism premises
        // Implikationen: always use bold, multi-line JTextArea for question cell (column 1)
        if (isQuestionRow && column == 1 && value instanceof String) {
            JTextArea area = new JTextArea((String) value);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setOpaque(true);
            area.setBackground(c.getBackground());
            area.setForeground(Color.BLACK);
            area.setFont(c.getFont().deriveFont(Font.BOLD));
            area.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            // Dynamically set row height to fit text
            int preferredHeight = area.getPreferredSize().height;
            if (table.getRowHeight(row) != preferredHeight) {
                table.setRowHeight(row, preferredHeight);
            }
            return area;
        }

        c.setForeground(Color.BLACK);

        boolean isPendingDeletion = false;
        if (isQuestionRow) {
            String questionNumber = String.valueOf(model.getValueAt(row, 0));
            if (questionNumber != null && questionNumber.matches("\\d+")) {
                int questionNum = Integer.parseInt(questionNumber);
                QuestionIdentifier identifier = new QuestionIdentifier(currentSubcategory, questionNum);
                isPendingDeletion = pendingDeleteQuestions.contains(identifier);
            }
            if (isPendingDeletion) {
                c.setBackground(Color.RED);
                c.setForeground(Color.WHITE);
            } else {
                c.setBackground(new Color(221, 221, 221));
            }
        } else {
            c.setBackground(Color.WHITE);
            Boolean isChecked = false;
            Object cellValue = model.getValueAt(row, 3);
            if (cellValue instanceof Boolean) {
                isChecked = (Boolean) cellValue;
            } else if (cellValue instanceof String) {
                // Defensive: treat "true" or "1" as checked
                String str = (String) cellValue;
                isChecked = str.equalsIgnoreCase("true") || str.equals("1");
            }
            if (isChecked) {
                c.setBackground(new Color(127, 204, 165, 75));
            }
        }

        if (column == 3) {
            if (isQuestionRow) {
                JLabel deleteLabel = new JLabel("X", SwingConstants.CENTER);
                deleteLabel.setForeground(isPendingDeletion ? Color.WHITE : Color.RED);
                deleteLabel.setBackground(c.getBackground());
                deleteLabel.setOpaque(true);
                return deleteLabel;
            } else {
                // Defensive: handle Boolean, String, null, and any other type
                boolean checked = false;
                if (value instanceof Boolean) {
                    checked = (Boolean) value;
                } else if (value instanceof String) {
                    String str = (String) value;
                    checked = str.equalsIgnoreCase("true") || str.equals("1");
                } else if (value != null) {
                    // Fallback: try toString and check for "true" or "1"
                    String str = value.toString();
                    checked = str.equalsIgnoreCase("true") || str.equals("1");
                }
                JCheckBox checkBox = new JCheckBox();
                checkBox.setSelected(checked);
                checkBox.setHorizontalAlignment(SwingConstants.CENTER);
                checkBox.setEnabled(false);
                checkBox.setBackground(c.getBackground());
                checkBox.setForeground(c.getForeground());
                return checkBox;
            }
        }

        if (column == 4) {
            if (isQuestionRow) {
                // Only show gear icon on question rows
                JLabel gearLabel = new JLabel(gearIcon);
                gearLabel.setHorizontalAlignment(SwingConstants.CENTER);
                gearLabel.setBackground(c.getBackground());
                gearLabel.setOpaque(true);
                return gearLabel;
            } else {
                // Option row: highlight cell green if correct
                boolean isChecked = false;
                Object cellValue = model.getValueAt(row, 3);
                if (cellValue instanceof Boolean) {
                    isChecked = (Boolean) cellValue;
                } else if (cellValue instanceof String) {
                    String str = (String) cellValue;
                    isChecked = str.equalsIgnoreCase("true") || str.equals("1");
                } else if (cellValue != null) {
                    String str = cellValue.toString();
                    isChecked = str.equalsIgnoreCase("true") || str.equals("1");
                }
                JLabel empty = new JLabel();
                empty.setOpaque(true);
                if (isChecked) {
                    empty.setBackground(new Color(127, 204, 165, 75));
                } else {
                    empty.setBackground(c.getBackground());
                }
                return empty;
            }
        }

        if (column == 4 && !isQuestionRow) {
            return new JLabel("");
        }

        if (isQuestionRow) {
            c.setFont(c.getFont().deriveFont(Font.BOLD));
        } else if (!isPendingDeletion) {
            // Defensive: handle Boolean, String, null, and any other type for option rows
            boolean isChecked = false;
            Object cellValue = model.getValueAt(row, 3);
            if (cellValue instanceof Boolean) {
                isChecked = (Boolean) cellValue;
            } else if (cellValue instanceof String) {
                String str = (String) cellValue;
                isChecked = str.equalsIgnoreCase("true") || str.equals("1");
            } else if (cellValue != null) {
                String str = cellValue.toString();
                isChecked = str.equalsIgnoreCase("true") || str.equals("1");
            }
            if (isChecked) {
                c.setBackground(new Color(127, 204, 165, 75));
            }
        }

        // Diff cell: editable dropdown, colored background only (no text/symbols)
        if (column == 5 && isFrageRow(row, (DefaultTableModel) table.getModel())) {
            String difficulty = (value != null) ? value.toString().toLowerCase() : "";
            c.setBackground(getDifficultyColor(difficulty));
            if (c instanceof JLabel) {
                // Remove any text/symbols, just show colored rectangle
                ((JLabel) c).setText("");
            }
            return c;
        }

        // Show Euler diagram for Implikationen solution column
        // Implikationen erkennen: Lösung als breite Zelle, andere Zellen leer
        if ("Implikationen".equals(currentSubcategory) && isQuestionRow && column == 2) {
            Object qTextObj = model.getValueAt(row, 1);
            if (qTextObj instanceof String text && text.contains("\n")) {
                String[] lines = text.split("\n");
                if (lines.length >= 2) {
                    JPanel panel = createEulerPanel(lines[0].trim(), lines[1].trim());
                    // Make the diagram panel even smaller
                    int preferredHeight = 120;
                    int preferredWidth = 120;
                    panel.setPreferredSize(new java.awt.Dimension(preferredWidth, preferredHeight));
                    if (table.getRowHeight(row) != preferredHeight) {
                        table.setRowHeight(row, preferredHeight);
                    }
                    return panel;
                }
            }
        }
        return c;
    }

    /**
     * Renders the Euler diagram for two premises as an SVG file.
     *
     * @param major first premise (e.g. "Alle A sind B.")
     * @param minor second premise (e.g. "Einige B sind nicht C.")
     * @param terms optional labels for the circles; if null labels are taken from the premises
     * @return path to the generated SVG file
     * @throws IOException if file creation fails
     * @throws IllegalArgumentException if sentences cannot be parsed
     */
    public static Path renderEuler(String major, String minor, List<String> terms) throws IOException {
        try {
            Sentence s1 = SyllogismUtils.parseSentence(major);
            Sentence s2 = SyllogismUtils.parseSentence(minor);

            int[] mask = Objects.requireNonNull(
                    SyllogismUtils.DIAGRAM_MASKS.get(Pair.of(s1.type(), s2.type())),
                    "unknown combination");
            int[] existenceMarkers = Objects.requireNonNull(
                    SyllogismUtils.EXISTENCE_MARKERS.get(Pair.of(s1.type(), s2.type())),
                    "unknown existence markers");

            List<String> labels;
            if (terms != null && terms.size() >= 3) {
                labels = terms;
            } else {
                labels = List.of(s1.subject(), s1.predicate(), s2.predicate());
            }

            // Make the SVG canvas and diagram elements larger to fill the cell
            SvgBuilder svg = new SvgBuilder(110, 110);
            svg.setupCircles(44, 55, 32,
                            66, 55, 32,
                            55, 28, 32);

            // Fill eliminated regions
            for (int i = 0; i < mask.length; i++) {
                if (mask[i] == 1) {
                    svg.fillRegion(i, Color.LIGHT_GRAY);
                }
            }

            // Add existence markers (green dots) for particular claims
            for (int i = 0; i < existenceMarkers.length; i++) {
                if (existenceMarkers[i] == 1 && mask[i] == 0) { // Only if region not eliminated
                    svg.addExistenceMarker(i);
                }
            }

            svg.addText("labelA", labels.get(0), 28, 100);
            svg.addText("labelB", labels.get(1), 82, 100);
            svg.addText("labelC", labels.get(2), 55, 18);

            Path dir = Paths.get(System.getProperty("java.io.tmpdir"));
            String fileName = "diagram_" + UUID.randomUUID() + ".svg";
            return svg.saveSvg(dir, fileName);
        } catch (IllegalArgumentException e) {
            // If sentence parsing fails, throw IOException with meaningful message
            throw new IOException("Cannot parse sentences for Euler diagram: " + e.getMessage(), e);
        }
    }

    private static JPanel createEulerPanel(String major, String minor) {
        try {
            Sentence s1 = SyllogismUtils.parseSentence(major);
            Sentence s2 = SyllogismUtils.parseSentence(minor);
            int[] mask = Objects.requireNonNull(
                    SyllogismUtils.DIAGRAM_MASKS.get(Pair.of(s1.type(), s2.type())),
                    "unknown combination");
            int[] existenceMarkers = Objects.requireNonNull(
                    SyllogismUtils.EXISTENCE_MARKERS.get(Pair.of(s1.type(), s2.type())),
                    "unknown existence markers");
            List<String> lbl = List.of(s1.subject(), s1.predicate(), s2.predicate());
            return new EulerPanel(mask, existenceMarkers, lbl);
        } catch (IllegalArgumentException e) {
            // If sentence parsing fails, return a simple error panel
            JPanel errorPanel = new JPanel();
            errorPanel.setBackground(Color.WHITE);
            errorPanel.add(new JLabel("Cannot parse: " + e.getMessage()));
            return errorPanel;
        }
    }

    private static class EulerPanel extends JPanel {
        private final List<Geometry> regions = new ArrayList<>();
        private final int[] mask;
        private final int[] existenceMarkers;
        private final List<String> labels;
        private final GeometryFactory gf = new GeometryFactory();
        private final ShapeWriter sw = new ShapeWriter();

        EulerPanel(int[] mask, int[] existenceMarkers, List<String> labels) {
            this.mask = mask;
            this.existenceMarkers = existenceMarkers;
            this.labels = labels;
            setupRegions();
            // Make default preferred size even smaller for Implikationen
            setPreferredSize(new java.awt.Dimension(120, 120));
            setOpaque(true);
        }

        private void setupRegions() {
            // Make the drawn circles larger to fill the cell
            Geometry A = circle(44, 55, 32);
            Geometry B = circle(66, 55, 32);
            Geometry C = circle(55, 28, 32);
            regions.add(A.difference(B.union(C)));                //0
            regions.add(A.intersection(B).difference(C));         //1
            regions.add(B.difference(A.union(C)));                //2
            regions.add(A.intersection(B).intersection(C));       //3
            regions.add(C.difference(A.union(B)));                //4
            regions.add(A.intersection(C).difference(B));         //5
            regions.add(B.intersection(C).difference(A));         //6
            regions.add(gf.createPolygon());                      //7
        }

        private org.locationtech.jts.geom.Polygon circle(double x, double y, double r) {
            return (org.locationtech.jts.geom.Polygon) gf.createPoint(new Coordinate(x, y)).buffer(r);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, getWidth(), getHeight());

            // Fill eliminated regions with light gray
            g2.setColor(Color.LIGHT_GRAY);
            for (int i = 0; i < mask.length && i < regions.size(); i++) {
                if (mask[i] == 1) {
                    Shape s = sw.toShape(regions.get(i));
                    g2.fill(s);
                }
            }

            // Draw larger circles to fill the cell
            g2.setColor(Color.BLACK);
            g2.draw(new Ellipse2D.Double(12, 23, 64, 64)); // Circle A
            g2.draw(new Ellipse2D.Double(36, 23, 64, 64)); // Circle B
            g2.draw(new Ellipse2D.Double(24, 0, 64, 64));  // Circle C

            // Draw existence markers (green dots) for particular claims
            g2.setColor(new Color(0, 150, 0)); // Dark green
            for (int i = 0; i < existenceMarkers.length && i < regions.size(); i++) {
                if (existenceMarkers[i] == 1 && mask[i] == 0) { // Only if region not eliminated
                    drawExistenceMarker(g2, i);
                }
            }

            // Draw larger labels
            g2.setColor(Color.BLACK);
            g2.drawString(labels.get(0), 32, 98);
            g2.drawString(labels.get(1), 88, 98);
            g2.drawString(labels.get(2), 55, 28);
        }

        /**
         * Draws a small green dot in the specified region to indicate existence claims.
         */
        private void drawExistenceMarker(Graphics2D g2, int regionIndex) {
            // Define approximate center points for each region
            int[] regionCenters = {
                28, 45,   // Region 0: A only
                44, 45,   // Region 1: A ∩ B only  
                60, 45,   // Region 2: B only
                50, 38,   // Region 3: A ∩ B ∩ C
                50, 18,   // Region 4: C only
                40, 30,   // Region 5: A ∩ C only
                60, 30,   // Region 6: B ∩ C only
                80, 80    // Region 7: Outside (unused)
            };
            
            if (regionIndex < regionCenters.length / 2) {
                int x = regionCenters[regionIndex * 2];
                int y = regionCenters[regionIndex * 2 + 1];
                
                // Draw a small filled circle (dot)
                g2.fillOval(x - 3, y - 3, 6, 6);
                
                // Add a white border for better visibility
                g2.setColor(Color.WHITE);
                g2.drawOval(x - 3, y - 3, 6, 6);
                g2.setColor(new Color(0, 150, 0)); // Restore green color
            }
        }
    }
}
