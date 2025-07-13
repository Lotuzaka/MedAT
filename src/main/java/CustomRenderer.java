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
                }

                try {
                    shapePanel = new PolygonPanel(
                            Collections.singletonList(new WKTReader().read(option.getShapeData())));
                    shapePanel.setAssembled(false);
                } catch (ParseException e) {
                    e.printStackTrace();
                    continue; // Skip this option if parsing fails
                }

                if (option.isCorrect()) {
                    optionPanel.setBackground(new Color(127, 204, 165, 75));
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
            int lines = area.getLineCount();
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
        return c;
    }
}
