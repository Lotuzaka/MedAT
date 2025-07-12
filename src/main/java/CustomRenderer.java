import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

// Data holder for Figuren options and dissected pieces (copied from MedatoninDB)
class FigurenOptionsData {
    public final List<OptionDAO> options;
    public final FigurenGenerator.DissectedPieces dissectedPieces;
    public FigurenOptionsData(List<OptionDAO> options, FigurenGenerator.DissectedPieces dissectedPieces) {
        this.options = options;
        this.dissectedPieces = dissectedPieces;
    }
}

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

        // Render figures for question rows (Figuren)
        if (isQuestionRow && value instanceof FigurenGenerator.DissectedPieces) {
            FigurenGenerator.DissectedPieces dissectedPieces = (FigurenGenerator.DissectedPieces) value;
            PolygonPanel panel;
            if (column == 1) {
                panel = new PolygonPanel(dissectedPieces.rotatedPieces);
                panel.setAssembled(false); // Display dissected pieces
            } else if (column == 2) {
                panel = new PolygonPanel(dissectedPieces.originalPieces);
                panel.setAssembled(true); // Display assembled figure as solution
            } else {
                panel = null;
            }
            if (panel != null) {
                panel.setPreferredSize(new Dimension(200, 200));
                return panel;
            }
        }

        // Always render options panel for FigurenOptionsData, regardless of subcategory
        if (!isQuestionRow && value instanceof FigurenOptionsData) {
            FigurenOptionsData data = (FigurenOptionsData) value;
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
                    // Handle Option E separately as a label "X"
                    JLabel optionELabel = new JLabel("Option E: X");
                    optionELabel.setFont(optionELabel.getFont().deriveFont(Font.BOLD));
                    optionsPanel.add(optionELabel);
                    continue;
                } else if (option.isCorrect()) {
                    List<Geometry> assembled = data.dissectedPieces.originalPieces;
                    shapePanel = new PolygonPanel(assembled);
                    shapePanel.setAssembled(true);
                    optionPanel.setBackground(new Color(127, 204, 165, 75));
                } else {
                    // Distractor option
                    try {
                        shapePanel = new PolygonPanel(
                                Collections.singletonList(new WKTReader().read(option.getShapeData())));
                        shapePanel.setAssembled(false);
                    } catch (ParseException e) {
                        e.printStackTrace();
                        continue; // Skip this option if parsing fails
                    }
                }

                shapePanel.setPreferredSize(new Dimension(100, 100));
                optionPanel.add(shapePanel, BorderLayout.CENTER);
                optionPanel.add(new JLabel("Option " + option.getLabel(), SwingConstants.CENTER),
                        BorderLayout.SOUTH);

                optionsPanel.add(optionPanel);
            }

            return optionsPanel;
        }

        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        // Handle multiline text for Syllogism premises
        if (isQuestionRow && column == 1 && value instanceof String && ((String) value).contains("\n")) {
            JTextArea area = new JTextArea((String) value);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setOpaque(true);
            area.setBackground(c.getBackground());
            area.setForeground(Color.BLACK);
            area.setFont(c.getFont());
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
            Boolean isChecked = (Boolean) model.getValueAt(row, 3);
            if (isChecked != null && isChecked) {
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
                Boolean checked = false;
                if (value instanceof Boolean) {
                    checked = (Boolean) value;
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
                Boolean isChecked = (Boolean) model.getValueAt(row, 3);
                JLabel empty = new JLabel();
                empty.setOpaque(true);
                if (isChecked != null && isChecked) {
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
            Boolean isChecked = (Boolean) model.getValueAt(row, 3);
            if (isChecked != null && isChecked) {
                c.setBackground(new Color(127, 204, 165, 75));
            }
        }

        if (column == 5 && isFrageRow(row, (DefaultTableModel) table.getModel())) {
            String difficulty = (String) value;
            if (difficulty != null && !difficulty.isEmpty()) {
                try {
                    Difficulty diff = Difficulty.valueOf(difficulty.toUpperCase());

                    JPanel panel = new JPanel(new GridBagLayout());
                    panel.setOpaque(true);
                    panel.setBackground(c.getBackground());

                    JLabel badge = new JLabel(diff.symbol);
                    badge.setForeground(diff.color);
                    badge.setFont(new Font("Arial", Font.BOLD, 18));

                    GridBagConstraints gbc = new GridBagConstraints();
                    gbc.gridx = 0;
                    gbc.gridy = 0;
                    gbc.anchor = GridBagConstraints.CENTER;

                    panel.add(badge, gbc);
                    return panel;
                } catch (IllegalArgumentException e) {
                    return c;
                }
            }
        }
        return c;
    }
}
