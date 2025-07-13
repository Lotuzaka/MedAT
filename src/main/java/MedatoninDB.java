

import org.apache.poi.xwpf.usermodel.*;
import org.locationtech.jts.geom.Geometry;
// import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.plaf.basic.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.List;

// import java.io.BufferedWriter; // No longer needed
// import java.io.OutputStreamWriter; // No longer needed
import java.io.FileOutputStream;

public class MedatoninDB extends JFrame {

    /**
     * Ensures the JVM is running with UTF-8 as the default encoding.
     * Logs a warning if not.
     */
    static {
        String defaultCharset = java.nio.charset.Charset.defaultCharset().name();
        if (!"UTF-8".equalsIgnoreCase(defaultCharset)) {
            System.err.println("[WARNING] JVM default encoding is not UTF-8: " + defaultCharset + ". Please launch with -Dfile.encoding=UTF-8");
        }
    }

    // Debug logger for categorized debug output
    // [REMOVED] Old debugWriter and static block. Logging now handled by debugLog methods.

    // [REMOVED] Old debugLog(String, String) method. Use new debugLog with log levels and context.
    // Utility to create a styled button panel for add/generate buttons
    private JPanel createButtonPanel(JButton... buttons) {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(1, buttons.length, 10, 0));
        panel.setBackground(backgroundColor);
        for (JButton btn : buttons) {
            panel.add(btn);
        }
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        return panel;
    }

    // Utility to create a styled text field (e.g., for question count)
    private JTextField createStyledTextField(String text, int width, Color bgColor, Color fgColor) {
        JTextField field = new JTextField(text);
        field.setPreferredSize(new Dimension(width, 30));
        field.setForeground(fgColor);
        field.setFont(new Font("SansSerif", Font.BOLD, 14));
        field.setBackground(bgColor);
        field.setBorder(BorderFactory.createEmptyBorder());
        field.setHorizontalAlignment(JTextField.CENTER);
        return field;
    }




    /* ------------------------------------------------------------- CONSTANTS */

    private static final Color CLR_BTN_DEFAULT = new Color(221, 221, 221);
    private static final Color CLR_BLUE_MED = new Color(128, 146, 160);

    // Default vertical spacing between buttons (categories and subcategories)
    private static final int BUTTON_SPACING = 5;
    private static final Font FONT_BASE = new Font("SansSerif", Font.PLAIN, 14);
    private static final Font FONT_BOLD = FONT_BASE.deriveFont(Font.BOLD);

    /* ------------------------------------------------------------- FIELDS */

    // DB
    private Connection conn;
    private QuestionDAO questionDAO;
    private OptionDAO optionDAO;
    private testSimulationDAO simulationDAO;

    private String currentUsername; // To store the logged-in username
    // Store sub-databases for each category
    private Map<String, Map<String, DefaultTableModel>> categoryModels = new HashMap<>();;
    private Map<String, List<String>> subcategoryOrder = new HashMap<>();

    private JTable questionTable; // Table to hold questions and checkboxes
    private DefaultTableModel tableModel; // Table model for adding rows
    private JLabel userTextField; // Haupt-Textfeld for real-time editing

    private String currentCategory = "Biologie", currentSubcategory = ""; // Track the current subcategory
    private JButton printCategoryButton; // Button that will dynamically change label
    private JButton bioButton, chemButton, physButton, mathButton, kffButton; // Category buttons
    private JPanel subcategoryPanel; // Panel to hold subcategory buttons
    private JButton selectedSubcategoryButton = null; // Class-level variable to keep track of the currently selected
                                                      // subcategory button

    private Map<JTable, Integer> tablePendingDeleteRowMap = new HashMap<>(); // Track pending delete row per table
    private ImageIcon gearIcon; // Load the gear icon
    private ImageIcon penIcon; // Load the pen icon
    private ImageIcon penEditIcon; // Load the pen edit icon

    private boolean isEditMode = false; // Variable to check if edit mode is enabled
    private Point initialClickPoint; // Initial click point for the drag
    private JButton draggedButton; // Reference to the button being dragged
    private JPanel draggedContainer; // Panel that contains the dragged button
    private int originalIndex = -1; // Original index of the dragged button
    private int dragThreshold = 5; // Threshold in pixels to start dragging
    private boolean isDragging = false; // Indicates if a drag operation is in progress
    private int lastTargetIndex = -1; // **Declaration of lastTargetIndex**
    private Set<QuestionIdentifier> pendingDeleteQuestions = new HashSet<>();
    private boolean isAdjustingFormat = false;

    // Panel to visually separate subcategories
    private JPanel mainContentPanel;
    JButton editToggleButton;
    private JButton addSubcategoryButton; // Declare this as a class member
    private Color backgroundColor = Color.WHITE;
    private int buttonBorderRadius = 15; // Border radius for buttons

    // Dropdown to select test simulations
    private JComboBox<String> simulationComboBox;
    private Map<String, Integer> simulationMap; // Maps simulation names to their IDs

    private Integer selectedSimulationId = null; // ID der ausgewählten Simulation

    /**
     * Utility to create a horizontal container for a subcategory button (with optional delete button).
     * @param subcategory The subcategory name
     * @param category The parent category
     * @param isEditMode Whether edit mode is enabled
     * @param deleteAction Action to perform on delete (can be null if not in edit mode)
     * @return JPanel containing the button(s)
     */
    private JPanel createSubcategoryButtonContainer(String subcategory, String category, boolean isEditMode, Runnable deleteAction) {
        JPanel buttonContainer = new JPanel();
        buttonContainer.setLayout(new BoxLayout(buttonContainer, BoxLayout.X_AXIS));
        buttonContainer.setBackground(backgroundColor);
        buttonContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton subButton = createModernButton(subcategory);
        buttonContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, subButton.getPreferredSize().height));

        if (isEditMode) {
            addDragAndDropFunctionality(subButton, subcategoryPanel);
        }

        subButton.addActionListener(e -> {
            if (!isEditMode) {
                switchSubcategory(category, subcategory);
            }
        });

        // Right-click to edit text (only in edit mode)
        if (isEditMode) {
            subButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        showSubcategoryMenu(e, subButton, category);
                    }
                }
            });
        }

        buttonContainer.add(subButton);

        if (isEditMode) {
            buttonContainer.add(Box.createHorizontalStrut(5));
            JButton deleteButton = createModernButton("-");
            deleteButton.setPreferredSize(new Dimension(30, 25));
            deleteButton.setMaximumSize(new Dimension(30, 25));
            deleteButton.setMinimumSize(new Dimension(30, 25));
            deleteButton.setBackground(new Color(233, 151, 151));
            deleteButton.setForeground(Color.WHITE);
            deleteButton.setFont(new Font("Arial", Font.BOLD, 14));
            deleteButton.setFocusPainted(false);
            deleteButton.setBorderPainted(false);
            deleteButton.setOpaque(true);
            if (deleteAction != null) {
                deleteButton.addActionListener(e -> deleteAction.run());
            }
            buttonContainer.add(deleteButton);
        }
        return buttonContainer;
    }

    public MedatoninDB() throws SQLException {
        // Debug: Print default charset at startup
        debugLog("Startup", "Default charset: " + java.nio.charset.Charset.defaultCharset());

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:mysql://localhost:3306/medatonindb?useUnicode=true&characterEncoding=UTF-8");
        cfg.setUsername("root");
        cfg.setPassword("288369Ma;");
        HikariDataSource ds = new HikariDataSource(cfg); // TODO: Close this resource if not managed elsewhere
        conn = ds.getConnection();
        questionDAO = new QuestionDAO(conn);
        optionDAO = new OptionDAO(conn);
        simulationDAO = new testSimulationDAO(conn);

        // SwingUtilities.invokeLater(() -> createLoginWindow());
        createMainWindow();
    }

    // Method to create the login window
    private void createLoginWindow() {
        JFrame loginFrame = new JFrame("Login to MedatoninDB");
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Color loginFontColor = Color.WHITE;
        loginFrame.getContentPane().setBackground(CLR_BLUE_MED);
        loginFrame.setSize(600, 500);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setLayout(new BorderLayout());

        // Logo panel
        JPanel logoPanel = new JPanel();
        JLabel logoLabel = new JLabel();
        ImageIcon logoIcon = new ImageIcon(getClass().getResource("/images/medatonin_logo.png"));
        logoLabel.setIcon(logoIcon);
        logoPanel.add(logoLabel);
        loginFrame.add(logoPanel, BorderLayout.NORTH);

        // Login form panel
        JPanel loginPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        loginPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JLabel usernameLabel = new JLabel("Username:");
        JTextField usernameField = new JTextField();
        JLabel passwordLabel = new JLabel("Password:");
        JPasswordField passwordField = new JPasswordField();
        JButton loginButton = createModernButton("Login");

        // Set font color for labels
        usernameLabel.setForeground(loginFontColor);
        passwordLabel.setForeground(loginFontColor);

        // set background & borders color
        usernameField.setBackground(new Color(189, 226, 236));
        usernameField.setBorder(BorderFactory.createEmptyBorder()); // Optional: make borderless
        passwordField.setBackground(new Color(189, 226, 236));
        passwordField.setBorder(BorderFactory.createEmptyBorder());
        logoPanel.setBackground(CLR_BLUE_MED);
        loginPanel.setBackground(CLR_BLUE_MED);
        loginButton.setBackground(new Color(243, 211, 135));

        loginPanel.add(usernameLabel);
        loginPanel.add(usernameField);
        loginPanel.add(passwordLabel);
        loginPanel.add(passwordField);
        loginPanel.add(new JLabel()); // Empty cell
        loginPanel.add(loginButton);

        loginFrame.add(loginPanel, BorderLayout.CENTER);

        // ActionListener for the login button
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText();
                String password = new String(passwordField.getPassword());
                new SwingWorker<Boolean, Void>() {
                    @Override
                    protected Boolean doInBackground() throws Exception {
                        return authenticateUser(username, password);
                    }

                    @Override
                    protected void done() {
                        try {
                            if (get()) {
                                currentUsername = username;
                                loginFrame.dispose();
                                SwingUtilities.invokeLater(() -> createMainWindow()); // Create main window on EDT
                            } else {
                                JOptionPane.showMessageDialog(loginFrame, "Invalid username or password.",
                                        "Login Failed", JOptionPane.ERROR_MESSAGE);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }.execute();

            }
        });

        loginFrame.setLocationRelativeTo(null); // Center the login window
        loginFrame.setVisible(true);
    }

    // Method to authenticate the user
    private boolean authenticateUser(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password_hash = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password); // Password should be hashed in real-world applications
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void createMainWindow() {
        questionDAO = new QuestionDAO(conn);
        optionDAO = new OptionDAO(conn);

        // Apply a modern flat look using UIManager
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Load the gear icon
        URL imageUrl = getClass().getResource("/images/gear-icon.png");
        gearIcon = new ImageIcon(imageUrl);
        // Load the pen icon
        imageUrl = getClass().getResource("/images/pen-icon.png");
        penIcon = new ImageIcon(imageUrl);
        imageUrl = getClass().getResource("/images/pen-icon-edit.png");
        penEditIcon = new ImageIcon(imageUrl);
        // Load and set the icon for the frame
        imageUrl = getClass().getResource("/images/window-icon.png"); // Adjust the path as needed
        if (imageUrl != null) {
            ImageIcon icon = new ImageIcon(imageUrl);
            setIconImage(icon.getImage());
        } else {
            debugLog("UI", "Icon not found!");
        }

        // Set up the frame
        setTitle("Medatonin-Datenbank");
        setSize(1200, 600); // Adjusted size to accommodate the left panel
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Apply a modern font and padding to all components
        Font modernFont = new Font("SansSerif", Font.PLAIN, 14);
        UIManager.put("Button.font", modernFont);
        UIManager.put("Table.font", modernFont);
        UIManager.put("Label.font", modernFont);
        UIManager.put("TableHeader.font", new Font("SansSerif", Font.BOLD, 14));
        UIManager.put("Button.background", new Color(221, 221, 221));
        UIManager.put("Button.foreground", Color.WHITE);

        // Custom button UI to make buttons look flat
        printCategoryButton = createModernButton(currentCategory + " Print");
        printCategoryButton.setBackground(new Color(128, 146, 160));
        printCategoryButton.setForeground(Color.WHITE);
        JButton printAllButton = createModernButton("All Print");

        // Set up modern color theme for the frame
        getContentPane().setBackground(backgroundColor);

        // Create the Haupt-Textfeld at the top
        userTextField = new JLabel();
        userTextField.setPreferredSize(new Dimension(800, 50));
        userTextField.setFont(new Font("SansSerif", Font.BOLD, 16));
        userTextField.setText("User: " + currentUsername);

        JButton logoutButton = createModernButton("Logout");
        logoutButton.setBackground(new Color(210, 141, 157));
        logoutButton.addActionListener(e -> logout());

        // In the constructor or initialization method
        simulationComboBox = new ModernComboBox();
        simulationComboBox.setOpaque(false);
        simulationMap = new HashMap<>();
        loadSimulationOptions(); // Load existing simulations from the database

        // Add ActionListener to handle simulation changes
        simulationComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedItem = (String) simulationComboBox.getSelectedItem();

                if (selectedItem != null && !selectedItem.equals("+")) {
                    // Get the simulation ID from the simulationMap
                    Integer simulationId = simulationMap.get(selectedItem);
                    if (simulationId != null) {
                        selectedSimulationId = simulationId; // Speichere die ausgewählte Simulation
                    } else {
                        selectedSimulationId = null;
                        debugLog("Simulation", "No simulation ID found for: " + selectedItem);
                    }
                } else if ("+".equals(selectedItem)) {
                    // Prompt for new simulation name
                    String newSimName = JOptionPane.showInputDialog("Enter new simulation name:");
                    if (newSimName != null && !newSimName.isEmpty()) {
                        try {
                            testSimulationDAO newSimulation = simulationDAO.createSimulation(newSimName);
                            loadSimulationOptions(); // Refresh the simulation list
                            simulationComboBox.setSelectedItem(newSimulation);
                            selectedSimulationId = newSimulation.getId();
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                            JOptionPane.showMessageDialog(MedatoninDB.this, "Error creating new simulation", "Error",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
                loadQuestionsFromDatabase(currentCategory, categoryModels.get(currentCategory), selectedSimulationId);
            }
        });

        // Create the top panel with a BorderLayout
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        topPanel.setBackground(backgroundColor);
        // topPanel.setPreferredSize(new Dimension(topPanel.getWidth(), 30));
        topPanel.add(userTextField, BorderLayout.WEST);
        topPanel.add(Box.createHorizontalStrut(-350), BorderLayout.WEST);
        topPanel.add(simulationComboBox, BorderLayout.CENTER);
        topPanel.add(Box.createHorizontalStrut(350), BorderLayout.EAST);
        topPanel.add(logoutButton, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Initialize category models
        createCategoryModel("Biologie");
        createCategoryModel("Chemie");
        createCategoryModel("Physik");
        createCategoryModel("Mathematik");
        createCategoryModel("KFF");

        // Initialize tableModel to avoid null issues
        tableModel = createTableModel();

        // Create the navigation panel on the left
        JPanel mainCategoryPanel = new JPanel();
        mainCategoryPanel.setLayout(new BoxLayout(mainCategoryPanel, BoxLayout.Y_AXIS)); // 4 categories
        mainCategoryPanel.setBackground(backgroundColor); // Set the background color
        mainCategoryPanel.setAlignmentY(Component.TOP_ALIGNMENT); // Align to top

        // Category buttons
        bioButton = createModernButton("Biologie");
        chemButton = createModernButton("Chemie");
        physButton = createModernButton("Physik");
        mathButton = createModernButton("Mathematik");
        kffButton = createModernButton("KFF");

        // Adjust button sizes to match text height
        bioButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        chemButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        physButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        mathButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        kffButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Set "Biologie" button to orange by default
        bioButton.setBackground(new Color(243, 211, 135));

        // Add action listeners to switch categories
        bioButton.addActionListener(e -> switchCategory("Biologie"));
        chemButton.addActionListener(e -> switchCategory("Chemie"));
        physButton.addActionListener(e -> switchCategory("Physik"));
        mathButton.addActionListener(e -> switchCategory("Mathematik"));
        kffButton.addActionListener(e -> switchCategory("KFF"));

        // Add buttons to the navigation panel
        addButtonWithSpacing(mainCategoryPanel, bioButton);
        addButtonWithSpacing(mainCategoryPanel, chemButton);
        addButtonWithSpacing(mainCategoryPanel, physButton);
        addButtonWithSpacing(mainCategoryPanel, mathButton);
        addButtonWithSpacing(mainCategoryPanel, kffButton);

        // Create the toggle button with the pen icon
        editToggleButton = createModernButton("Arbeitsmodus");
        editToggleButton.setIcon(penIcon);

        // Create a panel for the toggle button
        JPanel toggleButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        toggleButtonPanel.setBackground(backgroundColor); // Match the panel background to existing layout
        toggleButtonPanel.add(editToggleButton);

        editToggleButton.setPreferredSize(new Dimension(300, editToggleButton.getPreferredSize().height)); // Adjust the
                                                                                                           // size as
                                                                                                           // needed
        // Add action listener to toggle button
        editToggleButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isEditMode) {
                    editToggleButton.setIcon(penEditIcon); // Change to the edit icon
                    editToggleButton.setText("Bearbeitungsmodus"); // Display the text next to the icon
                    editToggleButton.setBackground(Color.red);
                    editToggleButton.setForeground(Color.WHITE);
                    setEditMode(true);
                    // Add any specific actions when edit mode is enabled
                } else {
                    editToggleButton.setIcon(penIcon); // Change to the pen icon
                    editToggleButton.setText("Arbeitsmodus"); // Remove text
                    setEditMode(false);
                    editToggleButton.setBackground(new Color(221, 221, 221));
                    editToggleButton.setForeground(Color.BLACK);
                    loadSubcategories(currentCategory);
                    // Add any specific actions when edit mode is disabled
                }
            }
        });

        // Create the main panel to hold the category buttons and the toggle button
        JPanel westPanel = new JPanel(new BorderLayout());
        westPanel.setBackground(backgroundColor); // Match to your existing layout

        // Add some vertical spacing between the toggle button and the navigation
        // buttons
        Box box = Box.createVerticalBox();
        box.add(toggleButtonPanel);
        box.add(Box.createVerticalStrut(10)); // Adds vertical spacing

        // Create the subcategory panel on the right of the main categories
        subcategoryPanel = new JPanel();
        subcategoryPanel.setLayout(new BoxLayout(subcategoryPanel, BoxLayout.Y_AXIS)); // Use BoxLayout with Y_AXIS
        subcategoryPanel.setBackground(backgroundColor);
        subcategoryPanel.setAlignmentY(Component.TOP_ALIGNMENT); // Align to top

        // Add both panels to a single container
        JPanel navigationContainer = new JPanel();
        navigationContainer.setLayout(new BoxLayout(navigationContainer, BoxLayout.X_AXIS));
        navigationContainer.setBackground(backgroundColor);

        navigationContainer.add(mainCategoryPanel);
        navigationContainer.add(Box.createHorizontalStrut(15)); // Optional: Add space between main and subcategory
                                                                // panels
        navigationContainer.add(subcategoryPanel);

        // Add the navigation container and toggle button to the top panel
        westPanel.add(box, BorderLayout.NORTH); // Add the toggle button on the right
        westPanel.add(navigationContainer, BorderLayout.CENTER); // Add the existing navigation container

        // Add the top panel to the main frame at the west side
        add(westPanel, BorderLayout.WEST); // Add to the west of your frame layout

        // Create the main content panel to display the subcategories with separators
        mainContentPanel = new JPanel();
        mainContentPanel.setLayout(new BoxLayout(mainContentPanel, BoxLayout.Y_AXIS));
        mainContentPanel.setBackground(Color.WHITE);
        JScrollPane scrollPane = new JScrollPane(mainContentPanel);
        add(scrollPane, BorderLayout.CENTER);

        // Load the initial category and its subcategories
        loadSubcategories(currentCategory);

        // Create buttons for adding and deleting questions
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(backgroundColor); // Match background color

        // Add ActionListener for printing the current category
        printCategoryButton.addActionListener(e -> {
            printCategory(currentCategory);
            printCategorySolution(currentCategory);
        });

        // Add ActionListener for printing all categories
        printAllButton.addActionListener(e -> {
            printAllCategories();
            printAllCategoriesSolution();
        });

        // Add buttons to the panel and frame
        buttonPanel.add(printCategoryButton);
        buttonPanel.add(printAllButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Setze die Ränder von allen relevanten Panels und ScrollPane auf leer
        mainContentPanel.setBorder(BorderFactory.createEmptyBorder()); // Kein Rand für das Hauptinhalt-Panel
        scrollPane.setBorder(BorderFactory.createEmptyBorder()); // Kein Rand für das ScrollPane
        topPanel.setBorder(BorderFactory.createEmptyBorder()); // Kein Rand für das obere Panel
        mainCategoryPanel.setBorder(BorderFactory.createEmptyBorder()); // Kein Rand für das Kategoriemenü-Panel
        westPanel.setBorder(BorderFactory.createEmptyBorder()); // Kein Rand für das westliche Panel
        buttonPanel.setBorder(BorderFactory.createEmptyBorder()); // Kein Rand für das Button-Panel

        // Set frame visibility
        setVisible(true);
    }

    private void logout() {
        // Close the main window
        this.dispose();

        // Clear the current user
        currentUsername = null;

        // Reset any necessary state
        resetState();

        // Remove all components from the main frame
        getContentPane().removeAll();
        getContentPane().revalidate();
        getContentPane().repaint();

        // Create and show the login window again
        createLoginWindow();
    }

    private void resetState() {
        // Reset any state variables or clear data as needed
        categoryModels.clear();
        currentCategory = null;
        // Add any other state resets here
    }

    // Modify the setEditMode method to handle the spacing change between
    // subcategory buttons
    private void setEditMode(boolean editMode) {
        isEditMode = editMode;
        if (isEditMode) {
            debugLog("EditMode", "Edit mode enabled");
            // Set the spacing between subcategory buttons to zero
            adjustSubcategoryButtonSpacing(0);
        } else {
            debugLog("EditMode", "Normal mode enabled");
            // Restore the normal spacing using the shared spacing constant
            adjustSubcategoryButtonSpacing(BUTTON_SPACING);
            updateSubcategoryOrder(); // Save the order when exiting edit mode
        }
    }

    // Populate ComboBox with simulations
    private void loadSimulationOptions() {
        // Populate the simulation combo box with available simulations
        simulationComboBox.removeAllItems();
        simulationComboBox.addItem("Haupt-Datenbank"); // Add the default option for the general pool
        this.simulationDAO = new testSimulationDAO(conn);
        List<testSimulationDAO> simulations = null;
        try {
            simulations = simulationDAO.getAllSimulations();
        } catch (SQLException e1) {
            e1.printStackTrace();
        }
        for (testSimulationDAO simulation : simulations) {
            simulationComboBox.addItem(simulation.getName());
            simulationMap.put(simulation.getName(), simulation.getId());
        }
        simulationComboBox.addItem("+"); // Add the "Create" option
    }

    // Method to adjust the spacing between subcategory buttons dynamically
    private void adjustSubcategoryButtonSpacing(int spacing) {
        subcategoryPanel.removeAll();
        subcategoryPanel.setLayout(new BoxLayout(subcategoryPanel, BoxLayout.Y_AXIS));
        for (String subcategory : subcategoryOrder.get(currentCategory)) {
            JPanel buttonContainer = createSubcategoryButtonContainer(
                subcategory,
                currentCategory,
                isEditMode,
                isEditMode ? () -> deleteSubcategory(currentCategory, subcategory) : null
            );
            subcategoryPanel.add(buttonContainer);
            if (spacing > 0) {
                subcategoryPanel.add(Box.createVerticalStrut(spacing));
            }
        }
        // "+" Button am Ende hinzufügen (nur im Bearbeitungsmodus)
        if (isEditMode) {
            addSubcategoryButton = createModernButton("+");
            addSubcategoryButton.setBackground(new Color(127, 204, 165));
            addSubcategoryButton.addActionListener(e -> addNewSubcategory(currentCategory));
            subcategoryPanel.add(addSubcategoryButton);
        }
        subcategoryPanel.revalidate();
        subcategoryPanel.repaint();
    }

    private void deleteSubcategory(String category, String subcategoryName) {
        List<String> orderList = subcategoryOrder.get(category);

        if (orderList != null && orderList.size() > 1) {
            int confirmation = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete subcategory: " + subcategoryName + "?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION);

            if (confirmation == JOptionPane.YES_OPTION) {
                // Remove the subcategory and update the order list
                categoryModels.get(category).remove(subcategoryName);
                orderList.remove(subcategoryName);
                debugLog("Subcategory", "Deleted subcategory: " + subcategoryName + " from category: " + category);

                // Remove from database
                if (deleteSubcategoryFromDatabase(category, subcategoryName)) {
                    loadSubcategories(category); // Reload subcategories after deletion
                    JOptionPane.showMessageDialog(this, "Subcategory deleted successfully.",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    debugLog("Subcategory", "Failed to delete subcategory from database: " + subcategoryName);
                    JOptionPane.showMessageDialog(this, "Failed to delete subcategory from database.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } else {
            debugLog("Subcategory", "Attempted to delete last subcategory: " + subcategoryName);
            JOptionPane.showMessageDialog(this, "At least one subcategory must exist.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Function to add drag-and-drop functionality to subcategory buttons
    private void addDragAndDropFunctionality(JButton button, JPanel subcategoryPanel) {
        button.setFocusable(false); // Disable focusability for better user experience

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isEditMode) {
                    initialClickPoint = e.getPoint();
                    isDragging = false; // Not yet dragging
                    originalIndex = getButtonIndex(button);
                    draggedButton = button;
                    draggedContainer = (JPanel) button.getParent();
                    lastTargetIndex = originalIndex; // Initialize lastTargetIndex
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isEditMode && isDragging) {
                    isDragging = false;
                    // Reset visual feedback
                    resetButtonAppearance(draggedButton);

                    draggedContainer = null;

                    // Update subcategoryOrder
                    updateSubcategoryOrder();
                    button.setForeground(Color.BLACK);
                    subcategoryPanel.revalidate();
                    subcategoryPanel.repaint();
                }
            }
        });

        button.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isEditMode) {
                    if (!isDragging) {
                        Point dragPoint = e.getPoint();
                        int distance = Math.abs(dragPoint.y - initialClickPoint.y);
                        if (distance > dragThreshold) {
                            // Start dragging
                            isDragging = true;

                            // Visual feedback: make the button 70% size and semi-transparent
                            // draggedButton.setBorder(BorderFactory.createDashedBorder(Color.BLACK, 1, 5));
                            draggedButton.setPreferredSize(
                                    new Dimension((int) (button.getWidth() * 0.3), (int) (button.getHeight() * 0.3)));
                            draggedButton.setAlignmentX(Component.LEFT_ALIGNMENT);
                            draggedButton.setOpaque(false);
                            draggedButton.setForeground(Color.RED);
                        }
                    }

                    if (isDragging) {
                        subcategoryPanel.validate(); // Ensure component bounds are up to date
                        int targetIndex = getIndexForPoint(e);

                        // Only update if targetIndex has significantly changed
                        if (targetIndex != lastTargetIndex && targetIndex >= 0
                                && targetIndex < subcategoryPanel.getComponentCount()) {
                            // Remove and re-add the button at the new index
                            subcategoryPanel.remove(draggedContainer);
                            subcategoryPanel.add(draggedContainer, targetIndex);

                            lastTargetIndex = targetIndex;

                            subcategoryPanel.revalidate();
                            subcategoryPanel.repaint();
                        }
                    }
                }
            }
        });
    }

    // Helper method to reset button appearance after dragging
    private void resetButtonAppearance(JButton button) {
        button.setPreferredSize(null);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Reset the border to maintain spacing
        button.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10)); // top, left, bottom, right
        button.setOpaque(true);
    }

    // Helper method to get the index of a button in subcategoryPanel
    private int getButtonIndex(JButton button) {
        Component[] components = subcategoryPanel.getComponents();
        for (int index = 0; index < components.length; index++) {
            Component comp = components[index];
            if (comp == button) {
                return index;
            }
            if (comp instanceof JPanel) {
                JPanel panel = (JPanel) comp;
                for (Component inner : panel.getComponents()) {
                    if (inner == button) {
                        return index;
                    }
                }
            }
        }
        return -1; // Should not happen
    }

    // Helper method to get the index for a given mouse event
    private int getIndexForPoint(MouseEvent e) {
        Point panelPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), subcategoryPanel);
        int mouseY = panelPoint.y;

        Component[] components = subcategoryPanel.getComponents();
        int index = 0;
        for (Component comp : components) {
            if (comp == draggedContainer) {
                continue;
            }
            JButton btn = null;
            if (comp instanceof JPanel) {
                Component[] inner = ((JPanel) comp).getComponents();
                if (inner.length > 0 && inner[0] instanceof JButton) {
                    btn = (JButton) inner[0];
                }
            } else if (comp instanceof JButton) {
                btn = (JButton) comp;
            }
            if (btn != null) {
                if ("+".equals(btn.getText())) {
                    break; // Do not place before the '+' button
                }
                Rectangle bounds = comp.getBounds();
                int componentMiddleY = bounds.y + bounds.height / 2;
                if (mouseY < componentMiddleY) {
                    return index;
                }
                index++;
            }
        }
        return index;
    }

    // Method to update the subcategory order after rearrangement
    private void updateSubcategoryOrder() {
        List<String> orderList = new ArrayList<>();
        for (Component comp : subcategoryPanel.getComponents()) {
            if (comp instanceof JPanel) {
                Component[] inner = ((JPanel) comp).getComponents();
                if (inner.length > 0 && inner[0] instanceof JButton) {
                    String text = ((JButton) inner[0]).getText();
                    orderList.add(text);
                }
            } else if (comp instanceof JButton) {
                // in case buttons are added directly (e.g., '+') skip them
                JButton btn = (JButton) comp;
                String text = btn.getText();
                if (!"+".equals(text)) {
                    orderList.add(text);
                }
            }
        }
        subcategoryOrder.put(currentCategory, orderList);

        // Save the new order to the database
        saveSubcategoryOrderToDatabase(currentCategory, orderList);
    }

    private void saveSubcategoryOrderToDatabase(String category, List<String> orderList) {
        String updateSql = "UPDATE subcategories SET order_index = ? WHERE name = ? AND category_id = (SELECT id FROM categories WHERE name = ?)";
        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            for (int i = 0; i < orderList.size(); i++) {
                stmt.setInt(1, i);
                stmt.setString(2, orderList.get(i));
                stmt.setString(3, category);
                stmt.addBatch();
            }
            stmt.executeBatch();
            debugLog("SubcategoryOrder", "Order updated for category: " + category + " -> " + orderList);
        } catch (SQLException e) {
            debugLog("SubcategoryOrder", "Failed to update order for category: " + category + ": " + e.getMessage());
        }
    }

    // Method to add a button to the panel with spacing automatically
    private void addButtonWithSpacing(JPanel panel, JButton button) {
        panel.add(button);
        panel.add(Box.createVerticalStrut(BUTTON_SPACING)); // Automatically add spacing after each button
    }

    // Helper method to create a category with subcategories
    private void createCategoryModel(String category) {
        debugLog("CategoryModel", "Creating category model for: " + category);

        if (category == null) {
            debugLog("CategoryModel", "Error: Category is null");
            return;
        }

        int categoryId = getCategoryID(category);
        if (categoryId == -1) {
            debugLog("CategoryModel", "Error: Invalid category ID for category: " + category);
            return;
        }

        Map<String, DefaultTableModel> subcategories = loadSubcategoriesFromDatabase(categoryId);
        if (subcategories == null) {
            debugLog("CategoryModel", "Error: Failed to load subcategories for category: " + category);
            subcategories = new HashMap<>(); // Create an empty map to avoid null pointer exceptions
        }

        if (categoryModels == null) {
            debugLog("CategoryModel", "Error: categoryModels is null");
            categoryModels = new HashMap<>(); // Initialize if it's null
        }
        categoryModels.put(category, subcategories);

        if (subcategoryOrder == null) {
            debugLog("CategoryModel", "Error: subcategoryOrder is null");
            subcategoryOrder = new HashMap<>(); // Initialize if it's null
        }

        // Store the subcategory names in the order they were loaded from the database
        List<String> orderList = new ArrayList<>(subcategories.keySet());
        subcategoryOrder.put(category, orderList);

        debugLog("CategoryModel", "Category model created for: " + category);
        debugLog("CategoryModel", "subcategories: " + subcategories.keySet());

        // Load questions for all subcategories
        loadQuestionsFromDatabase(category, subcategories, selectedSimulationId);
    }

    // Method to load subcategories and place buttons in the panel
    private void loadSubcategories(String category) {
        subcategoryPanel.removeAll();
        subcategoryPanel.setLayout(new BoxLayout(subcategoryPanel, BoxLayout.Y_AXIS));
        subcategoryPanel.setBackground(backgroundColor);

        List<String> orderList = subcategoryOrder.get(category);
        Color subcategoryBackgroundColor = getCategoryButtonColor(category);

        if (orderList != null) {
            for (String subcategory : orderList) {
                // Container für Button und Minus-Button"-
                JPanel buttonContainer = new JPanel();
                buttonContainer.setLayout(new BoxLayout(buttonContainer, BoxLayout.X_AXIS));
                buttonContainer.setBackground(backgroundColor);
                buttonContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
                JButton subButton = createModernButton(subcategory);

                // Keep the container height consistent with the button height so
                // spacing matches the main category buttons
                buttonContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                        subButton.getPreferredSize().height));

                if (isEditMode) {
                    addDragAndDropFunctionality(subButton, subcategoryPanel);
                }

                subButton.addActionListener(e -> {
                    if (!isEditMode) {
                        switchSubcategory(category, subcategory);
                        subButton.setBackground(subcategoryBackgroundColor);
                    }
                });

                // Right-click to edit text
                subButton.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (SwingUtilities.isRightMouseButton(e) && isEditMode) {
                            showSubcategoryMenu(e, subButton, category);
                        }
                    }
                });

                buttonContainer.add(subButton);

                // Minus-Button nur im Bearbeitungsmodus
                if (isEditMode) {
                    buttonContainer.add(Box.createHorizontalStrut(5));

                    JButton deleteButton = createModernButton("-");
                    deleteButton.setPreferredSize(new Dimension(30, 25));
                    deleteButton.setMaximumSize(new Dimension(30, 25));
                    deleteButton.setMinimumSize(new Dimension(30, 25));
                    deleteButton.setBackground(new Color(233, 151, 151));
                    deleteButton.setForeground(Color.WHITE);
                    deleteButton.setFont(new Font("Arial", Font.BOLD, 14));
                    deleteButton.setFocusPainted(false);
                    deleteButton.setBorderPainted(false);
                    deleteButton.setOpaque(true);

                    deleteButton.addActionListener(e -> deleteSubcategory(category, subcategory));

                    buttonContainer.add(deleteButton);
                }

                subcategoryPanel.add(buttonContainer);
                int spacing = isEditMode ? 0 : BUTTON_SPACING;
                if (spacing > 0) {
                    subcategoryPanel.add(Box.createVerticalStrut(spacing));
                }
            }
        }

        // "+" Button nur im Bearbeitungsmodus
        if (isEditMode) {
            addSubcategoryButton = createModernButton("+");
            addSubcategoryButton.setBackground(new Color(127, 204, 165));
            addSubcategoryButton.addActionListener(e -> addNewSubcategory(category));
            subcategoryPanel.add(addSubcategoryButton);
        }

        subcategoryPanel.revalidate();
        subcategoryPanel.repaint();

        displaySubcategoriesInMainContent(category);
    }

    // Method to get the corresponding background color of the category buttons
    private Color getCategoryButtonColor(String category) {
        if (category == null) {
            return new Color(221, 221, 221); // Default color when category is null
        }
        switch (category) {
            case "Biologie":
                return new Color(243, 211, 135); // Orange background for Biologie
            case "Chemie":
                return new Color(210, 141, 157); // Burgundi background for Chemie
            case "Physik":
                return new Color(189, 226, 236); // Light Blue background for Physik
            case "Mathematik":
                return new Color(128, 146, 160); // Blue background for Mathematik
            case "KFF":
                return Color.CYAN;
            default:
                return new Color(221, 221, 221); // Default dark grey background
        }
    }

    // Method to add a new subcategory to the current category
    private void addNewSubcategory(String category) {
        String subcategoryName = JOptionPane.showInputDialog(this, "Enter the name of the new subcategory:");
        if (subcategoryName != null && !subcategoryName.trim().isEmpty()) {
            // Ensure the subcategory doesn't already exist
            Map<String, DefaultTableModel> subcategories = categoryModels.get(category);
            if (!subcategories.containsKey(subcategoryName)) {
                // Initialize the new subcategory with a DefaultTableModel
                DefaultTableModel newModel = createTableModel();
                subcategories.put(subcategoryName, newModel); // Add to the correct main category

                // Update the subcategory order to include the new subcategory
                List<String> orderList = subcategoryOrder.get(category);
                if (orderList == null) {
                    orderList = new ArrayList<>();
                    subcategoryOrder.put(category, orderList);
                }
                orderList.add(subcategoryName);// Add to the order list
                // Save the new subcategory to the database
                saveSubcategoryToDatabase(category, subcategoryName);
                debugLog("Subcategory", "Added new subcategory: " + subcategoryName + " to category: " + category);
                loadSubcategories(category); // Reload subcategories to reflect changes
            } else {
                debugLog("Subcategory", "Attempted to add duplicate subcategory: " + subcategoryName + " to category: " + category);
                JOptionPane.showMessageDialog(this, "Subcategory already exists.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Method to show the context menu for deleting a subcategory
    private void showSubcategoryMenu(MouseEvent e, JButton button, String category) {
        JPopupMenu subcategoryMenu = new JPopupMenu();
        JMenuItem deleteItem = new JMenuItem("Delete Subcategory");
        JMenuItem editItem = new JMenuItem("Edit Name");

        editItem.addActionListener(ae -> enableButtonTextEdit(button, category));

        deleteItem.addActionListener(ae -> {
            String subcategoryName = button.getText();

            List<String> orderList = subcategoryOrder.get(category);
            if (orderList == null) {
                orderList = new ArrayList<>();
                subcategoryOrder.put(category, orderList);
            }

            if (orderList != null && orderList.size() > 1) {
                int confirmation = JOptionPane.showConfirmDialog(this,
                        "Are you sure you want to delete subcategory: " + subcategoryName + "?", "Confirm Delete",
                        JOptionPane.YES_NO_OPTION);
                if (confirmation == JOptionPane.YES_OPTION) {
                    // Remove the subcategory and update the order list
                    categoryModels.get(category).remove(subcategoryName);
                    orderList.remove(subcategoryName);
                    // Remove from database
                    if (deleteSubcategoryFromDatabase(category, subcategoryName)) {
                        loadSubcategories(category); // Reload subcategories after deletion
                        JOptionPane.showMessageDialog(this, "Subcategory deleted successfully.", "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "Failed to delete subcategory from database.", "Error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, "At least one subcategory must exist.", "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        subcategoryMenu.add(editItem);
        subcategoryMenu.add(deleteItem);
        subcategoryMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    private boolean deleteSubcategoryFromDatabase(String category, String subcategoryName) {
        String sql = "DELETE FROM subcategories WHERE name = ? AND category_id = (SELECT id FROM categories WHERE name = ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, subcategoryName);
            stmt.setString(2, category);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Method to display subcategories in the main content panel with "Add Question"
    // button integrated
    private void displaySubcategoriesInMainContent(String category) {
        mainContentPanel.setLayout(new BoxLayout(mainContentPanel, BoxLayout.Y_AXIS));
        mainContentPanel.removeAll();
        int categoryId = getCategoryID(category);
        Map<String, DefaultTableModel> subcategories = loadSubcategoriesFromDatabase(categoryId);
        loadQuestionsFromDatabase(category, subcategories, selectedSimulationId);

        for (Map.Entry<String, DefaultTableModel> entry : subcategories.entrySet()) {
            String subcategoryName = entry.getKey();
            DefaultTableModel model = entry.getValue();
            if (model == null || model.getRowCount() == 0) {
                continue;
            }

            JPanel combinedPanel = new JPanel(new BorderLayout());
            combinedPanel.setBorder(BorderFactory.createTitledBorder(subcategoryName));
            combinedPanel.setBackground(Color.WHITE);

            JTable subcategoryTable = new JTable(model);
            subcategoryTable.setBorder(BorderFactory.createEmptyBorder());
            subcategoryTable.setShowGrid(false);
            subcategoryTable.setRowHeight(30);
            subcategoryTable.setFont(new Font("SansSerif", Font.PLAIN, 14));
            CustomRenderer renderer = new CustomRenderer(currentSubcategory, pendingDeleteQuestions, gearIcon);
            subcategoryTable.setDefaultRenderer(Object.class, renderer);
            subcategoryTable.setDefaultRenderer(Boolean.class, renderer);
            subcategoryTable.setDefaultRenderer(Integer.class, renderer);
            subcategoryTable.setDefaultRenderer(String.class, renderer);
            subcategoryTable.setDefaultEditor(Object.class, new CustomEditor(subcategoryTable));
            adjustColumnWidths(subcategoryTable);
            model.addTableModelListener(e -> {
                if (e.getColumn() == 3) {
                    SwingUtilities.invokeLater(subcategoryTable::repaint);
                }
            });
            addTableListeners(subcategoryTable);

            JScrollPane tableScrollPane = new JScrollPane(subcategoryTable) {
                @Override
                public Dimension getPreferredSize() {
                    int rowCount = subcategoryTable.getRowCount();
                    if (rowCount == 0) {
                        return new Dimension(0, 0);
                    }
                    int rowHeight = subcategoryTable.getRowHeight();
                    int headerHeight = subcategoryTable.getTableHeader().getPreferredSize().height;
                    int totalHeight = headerHeight + (rowCount * rowHeight);
                    int maxVisibleRows = 10;
                    int maxHeight = headerHeight + (maxVisibleRows * rowHeight);
                    if (totalHeight > maxHeight) {
                        totalHeight = maxHeight;
                    }
                    Dimension size = super.getPreferredSize();
                    size.height = totalHeight;
                    return size;
                }
            };

            JButton addQuestionButton = createModernButton("Add Question");
            addQuestionButton.setBackground(new Color(127, 204, 165));
            addQuestionButton.setForeground(Color.WHITE);
            addQuestionButton.setFont(new Font("SansSerif", Font.BOLD, 14));
            addQuestionButton.setFocusPainted(false);
            addQuestionButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            addQuestionButton.addActionListener(e -> addNewQuestionToSubcategory());

            JPanel buttonPanel;

            if ("KFF".equals(category)) {
                JButton generateButton = createModernButton("Generate");
                generateButton.setBackground(new Color(127, 204, 165));
                generateButton.setForeground(Color.WHITE);
                generateButton.setFont(new Font("SansSerif", Font.BOLD, 14));
                generateButton.setFocusPainted(false);
                generateButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

                JTextField questionCountField = createStyledTextField("0", 40, new Color(127, 204, 165), Color.WHITE);

                JPanel generatePanel = new JPanel(new BorderLayout());
                generatePanel.setBackground(new Color(127, 204, 165));
                generatePanel.add(questionCountField, BorderLayout.EAST);
                generatePanel.add(generateButton, BorderLayout.CENTER);

                generateButton.addActionListener(e -> {
                    try {
                        String input = questionCountField.getText().trim();
                        int questionCount;
                        try {
                            questionCount = Integer.parseInt(input);
                        } catch (NumberFormatException ex) {
                            JOptionPane.showMessageDialog(null, "Please enter a valid number.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        if ("Implikationen".equals(currentSubcategory)) {
                            SyllogismGenerator generator = new SyllogismGenerator(conn, currentCategory, currentSubcategory, selectedSimulationId);
                            generator.execute(questionCount);
                            loadQuestionsFromDatabase(currentCategory, categoryModels.get(currentCategory), selectedSimulationId);
                            switchSubcategory(currentCategory, currentSubcategory);
                        }
                        if ("Zahlenfolgen".equals(currentSubcategory)) {
                            ZahlenfolgenGenerator generator = new ZahlenfolgenGenerator(conn, currentCategory, currentSubcategory, selectedSimulationId);
                            generator.execute(questionCount);
                            loadQuestionsFromDatabase(currentCategory, categoryModels.get(currentCategory), selectedSimulationId);
                            switchSubcategory(currentCategory, currentSubcategory);
                        }
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                });
                buttonPanel = createButtonPanel(addQuestionButton);
                buttonPanel.add(generatePanel);
            } else {
                buttonPanel = createButtonPanel(addQuestionButton);
            }

            combinedPanel.add(tableScrollPane, BorderLayout.CENTER);
            combinedPanel.add(buttonPanel, BorderLayout.SOUTH);
            mainContentPanel.add(combinedPanel);
            mainContentPanel.add(Box.createVerticalStrut(10));
        }

        subcategoryPanel.revalidate();
        subcategoryPanel.repaint();
    }

    // Method to add a new question to the current subcategory
    private void addNewQuestionToSubcategory() {
        if (currentCategory == null || currentSubcategory == null || currentSubcategory.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a category and subcategory first.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            conn.setAutoCommit(false);
            int subcategoryId = getSubcategoryId(currentCategory, currentSubcategory);
            int questionNumber = questionDAO.getNextQuestionNumber(selectedSimulationId, subcategoryId);
            int simulationId = selectedSimulationId != null ? selectedSimulationId : 0; // 0 oder null, wenn keine
                                                                                        // Simulation ausgewählt
            int questionId = questionDAO.insertEmptyQuestion(currentCategory, currentSubcategory, questionNumber,
                    simulationId);

            if (questionId == -1) {
                throw new SQLException("Failed to insert question");
            }

            DefaultTableModel model = categoryModels.get(currentCategory).get(currentSubcategory);
            model.addRow(new Object[] {
                    String.valueOf(questionNumber),
                    "", // Empty question text
                    false, // Checkbox state
                    "Kurz" // Default format
            });

            // Add empty options
            for (char label = 'A'; label <= 'E'; label++) {
                optionDAO.insertEmptyOption(questionId, String.valueOf(label));
                model.addRow(new Object[] {
                        String.valueOf(label) + ")",
                        "", // Empty option text
                        false,
                        ""
                });
            }

            conn.commit();

            // Refresh the table
            if (questionTable != null) {
                questionTable.revalidate();
                questionTable.repaint();
            }

        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            JOptionPane.showMessageDialog(this, "Error adding question and options: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // Enable text editing for the subcategory button
    private void enableButtonTextEdit(JButton button, String category) {
        String originalText = button.getText();
        button.setText(""); // Clear the text to start editing
        JTextField textField = new JTextField(originalText);
        textField.setPreferredSize(button.getPreferredSize());
        button.setLayout(new BorderLayout());
        button.add(textField, BorderLayout.CENTER);
        textField.requestFocusInWindow();

        // Listener to confirm edit when Enter is pressed
        textField.addActionListener(e -> saveEditedSubcategoryName(textField, button, category, originalText));

        // Focus listener to confirm edit when focus is lost
        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                saveEditedSubcategoryName(textField, button, category, originalText);
            }
        });
    }

    // Save the edited subcategory name and maintain the order
    private void saveEditedSubcategoryName(JTextField textField, JButton button, String category, String originalText) {
        String newText = textField.getText().trim();
        if (!newText.isEmpty() && !newText.equals(originalText)) {
            // Update the button text
            button.setText(newText);
            button.setLayout(null);
            button.remove(textField);

            // Update the subcategory order list
            List<String> orderList = subcategoryOrder.get(category);
            int index = orderList.indexOf(originalText);
            if (index != -1) {
                orderList.set(index, newText);
            }

            // Update the categoryModels map
            Map<String, DefaultTableModel> subcategories = categoryModels.get(category);
            if (subcategories != null && subcategories.containsKey(originalText)) {
                DefaultTableModel model = subcategories.remove(originalText);
                subcategories.put(newText, model);
            }

            // Update the database
            updateSubcategoryNameInDatabase(category, originalText, newText);

            // Refresh the UI
            loadSubcategories(category);
        } else {
            // If the name is empty or unchanged, revert to the original text
            button.setText(originalText);
            button.setLayout(null);
            button.remove(textField);
        }
        // Reload subcategories after editing
        // loadSubcategories(category);
        button.revalidate();
        button.repaint();
    }

    private void updateSubcategoryNameInDatabase(String category, String oldName, String newName) {
        String sql = "UPDATE subcategories SET name = ? WHERE name = ? AND category_id = (SELECT id FROM categories WHERE name = ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setString(2, oldName);
            stmt.setString(3, category);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                debugLog("Subcategory", "Subcategory name updated in database: " + oldName + " -> " + newName);
            } else {
                debugLog("Subcategory", "Failed to update subcategory name in database");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Method to create modern-looking buttons with adjusted height
    private JButton createModernButton(String text) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2d.setColor(getBackground().darker());
                } else if (getModel().isRollover()) {
                    g2d.setColor(adjustBrightness(getBackground(), 1.1f));
                } else {
                    g2d.setColor(getBackground());
                }
                g2d.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, buttonBorderRadius, buttonBorderRadius);
                g2d.setColor(getForeground());
                g2d.setFont(getFont());
                FontMetrics metrics = g2d.getFontMetrics(getFont());
                int x = (getWidth() - metrics.stringWidth(getText())) / 2;
                int y = ((getHeight() - metrics.getHeight()) / 2) + metrics.getAscent();
                g2d.drawString(getText(), x, y);
                g2d.dispose();
            }
            @Override
            protected void paintBorder(Graphics g) {}
            @Override
            public boolean isFocusPainted() { return false; }
            @Override
            public boolean isContentAreaFilled() { return false; }
        };
        styleModernButton(button);
        return button;
    }

    // Centralized styling for modern buttons
    private void styleModernButton(JButton button) {
        button.setFocusPainted(false);
        button.setBackground(CLR_BTN_DEFAULT);
        button.setForeground(Color.BLACK);
        button.setFont(FONT_BOLD);
        button.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        button.setMargin(new Insets(5, 10, 5, 10));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension buttonSize = new Dimension(180, button.getPreferredSize().height);
        button.setPreferredSize(buttonSize);
        button.setMaximumSize(buttonSize);
        button.setMinimumSize(buttonSize);
    }

    private Color adjustBrightness(Color color, float factor) {
        // Ensure factor is greater than 0
        factor = Math.max(factor, 0);

        // Get the current RGB values and adjust brightness
        int r = Math.min(255, (int) (color.getRed() * factor));
        int g = Math.min(255, (int) (color.getGreen() * factor));
        int b = Math.min(255, (int) (color.getBlue() * factor));

        // Return the new color with adjusted brightness
        return new Color(r, g, b);
    }

    // Enhanced method to add listeners to cancel pending deletion when clicking
    // elsewhere
    private void addTableListeners(JTable table) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleTableClick(table, e);
            }
        });
        // Defensive: add a TableModelListener to prevent out-of-bounds updates
        if (table.getModel() instanceof DefaultTableModel) {
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            model.addTableModelListener(e -> {
                int row = e.getFirstRow();
                int col = e.getColumn();
                // Defensive: only update if row and col are valid
                if (row >= 0 && row < model.getRowCount() && col >= 0 && col < model.getColumnCount()) {
                    // ...existing code for handling updates...
                }
            });
        }
    }

    // Handle clicks on tables to cancel deletion highlight if clicked outside the
    // pending delete row
    private void handleTableClick(JTable table, MouseEvent e) {
        int clickedRow = table.rowAtPoint(e.getPoint());
        int clickedColumn = table.columnAtPoint(e.getPoint());
        Integer currentPendingDeleteRow = tablePendingDeleteRowMap.getOrDefault(table, -1);
        int frageRow = getFrageRowForRow(clickedRow, table);

        // If there is a pending deletion and the click is outside the pending question
        // or not on the "X" button
        if (currentPendingDeleteRow != -1) {
            if (frageRow != currentPendingDeleteRow || (frageRow == currentPendingDeleteRow && clickedColumn != 2)) {
                tablePendingDeleteRowMap.put(table, -1);
                table.repaint();
            }
        }
    }

    // Method to switch between categories
    private void switchCategory(String category) {
        currentCategory = category;
        Map<String, DefaultTableModel> subcategories = categoryModels.get(category);
        if (subcategories == null) {
            createCategoryModel(category);
            subcategories = categoryModels.get(category);
        }
        loadQuestionsFromDatabase(category, subcategories, selectedSimulationId);
        loadSubcategories(currentCategory);
        resetPendingDeleteState(); // Reset the deletion state when switching categories

        // Update the print button label immediately when switching categories
        updatePrintButtonLabel();

        // Check if we are in edit mode and reapply the zero spacing
        if (isEditMode) {
            adjustSubcategoryButtonSpacing(0);
        }

        // Dynamically show or hide the "Set" column based on the category
        if (isSetColumnNotVisible(category)) {
            hideSetColumn(); // Hide the "Set" column
        } else {
            showSetColumn(); // Show the "Set" column
        }

        // Set the selected category button to blue and reset others
        resetCategoryButtons();
        updateCategoryButtonColors(category);
    }

    // Update the selected category button colors
    private void updateCategoryButtonColors(String category) {
        resetCategoryButtons();
        if (category.equals("Biologie")) {
            bioButton.setBackground(new Color(243, 211, 135)); // Orange background for selected category
        } else if (category.equals("Chemie")) {
            chemButton.setBackground(new Color(210, 141, 157)); // Burgundi background
        } else if (category.equals("Physik")) {
            physButton.setBackground(new Color(189, 226, 236)); // Light Blue background
        } else if (category.equals("Mathematik")) {
            mathButton.setBackground(new Color(128, 146, 160)); // Blue background
        } else if (category.equals("KFF")) {
            kffButton.setBackground(Color.CYAN); // Blue background
        }
    }

    // Adjust the print button width based on text length
    private void adjustPrintButtonWidth(JButton button, String text) {
        FontMetrics metrics = button.getFontMetrics(button.getFont());
        int width = metrics.stringWidth(text) + 20; // Add some padding
        button.setPreferredSize(new Dimension(width, button.getPreferredSize().height));
        button.setMaximumSize(new Dimension(width, button.getPreferredSize().height));
    }

    private void switchSubcategory(String category, String subcategory) {

        currentCategory = category;
        currentSubcategory = subcategory;

        // Remove all components from mainContentPanel
        mainContentPanel.removeAll();
        mainContentPanel.setLayout(new BorderLayout());

        // Create a panel to hold the content
        JPanel subcategoryContentPanel = new JPanel();
        subcategoryContentPanel.setLayout(new BorderLayout());

        // Create a panel for the buttons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setBackground(backgroundColor);

        // Ensure the subcategory exists before setting the model
        Map<String, DefaultTableModel> subcategories = categoryModels.get(category);
        if (subcategories.containsKey(subcategory)) {
            tableModel = subcategories.get(subcategory);
        } else {
            tableModel = createTableModel(); // Create an empty model if subcategory doesn't exist
            subcategories.put(subcategory, tableModel); // Add the new subcategory to the map
        }

        // Create the table for the subcategory
        questionTable = new JTable(tableModel);
        questionTable.setBorder(BorderFactory.createEmptyBorder());
        questionTable.setShowGrid(false);
        questionTable.setRowHeight(30);
        questionTable.setFont(new Font("SansSerif", Font.PLAIN, 14));
        CustomRenderer renderer = new CustomRenderer(currentSubcategory, pendingDeleteQuestions, gearIcon);
        questionTable.setDefaultRenderer(Object.class, renderer);
        questionTable.setDefaultRenderer(Boolean.class, renderer);
        questionTable.setDefaultRenderer(Integer.class, renderer);
        questionTable.setDefaultRenderer(String.class, renderer);
        questionTable.setDefaultEditor(Object.class, new CustomEditor(questionTable));

        // After initializing your questionTable
        TableColumn korrektColumn = questionTable.getColumnModel().getColumn(3);
        korrektColumn.setCellEditor(new CustomEditor(questionTable));

        // Adjust the column widths after creating the table
        adjustColumnWidths(questionTable);

        if ("Figuren".equals(currentSubcategory)) {
            questionTable.setRowHeight(150); // Default row height for question rows
            for (int row = 0; row < questionTable.getRowCount(); row++) {
                Object value = questionTable.getValueAt(row, 1);
                if (value instanceof List<?>) {
                    questionTable.setRowHeight(row, 50); // Adjust height for options row
                }
            }
        } else if ("Implikationen".equals(currentSubcategory)) {
            // Always use bold, multi-line JTextArea for Implikationen question rows (view & edit)
            Font boldFont = questionTable.getFont().deriveFont(Font.BOLD);
            for (int row = 0; row < questionTable.getRowCount(); row++) {
                Object textObj = questionTable.getValueAt(row, 1);
                String text = textObj != null ? textObj.toString() : "";
                // Ensure line breaks between premises
                if (text.contains(";")) {
                    text = text.replace(";", "\n");
                }
                JTextArea area = new JTextArea(text);
                area.setFont(boldFont);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                area.setOpaque(false);
                area.setSize(questionTable.getColumnModel().getColumn(1).getWidth(), Short.MAX_VALUE);
                int prefHeight = area.getPreferredSize().height + 10;
                questionTable.setRowHeight(row, Math.max(prefHeight, 30));
                // If cell is being edited, ensure editor is also a bold JTextArea and preserves line breaks
                if (questionTable.isCellEditable(row, 1) && questionTable.getEditingRow() == row && questionTable.getEditingColumn() == 1) {
                    Component editorComp = questionTable.getEditorComponent();
                    if (editorComp instanceof JTextArea) {
                        ((JTextArea) editorComp).setFont(boldFont);
                        ((JTextArea) editorComp).setLineWrap(true);
                        ((JTextArea) editorComp).setWrapStyleWord(true);
                        String editText = ((JTextArea) editorComp).getText();
                        if (editText.contains(";")) {
                            ((JTextArea) editorComp).setText(editText.replace(";", "\n"));
                        }
                    }
                }
            }
        } else {
            questionTable.setRowHeight(30); // Default row height for other subcategories
        }

        // Add listeners for synchronizing with text fields and other interactions
        addTableListeners(questionTable);

        // Dynamically show or hide the "Set" column based on the category
        if (isSetColumnNotVisible(category)) {
            hideSetColumn(); // Hide the "Set" column
        } else {
            showSetColumn(); // Show the "Set" column
        }

        questionTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = questionTable.rowAtPoint(e.getPoint());
                int col = questionTable.columnAtPoint(e.getPoint());

                if (col != 3 || !isFrageRow(row, (DefaultTableModel) questionTable.getModel())) {
                    pendingDeleteQuestions.clear();
                    questionTable.repaint();
                }
            }
        });

        // Add TableModelListener to repaint the table when checkbox changes
        tableModel.addTableModelListener(e -> {
            if (e.getColumn() == 3) {
                // Force repaint to show green highlight as soon as checkbox changes
                SwingUtilities.invokeLater(questionTable::repaint);
            }
        });

        // Add the table in a scroll pane to the content panel
        JScrollPane subScrollPane = new JScrollPane(questionTable);

        // Add components to subcategoryContentPanel
        subcategoryContentPanel.add(subScrollPane, BorderLayout.CENTER);

        // For other categories and subcategories, only add "Add Question" button
        JButton addQuestionButton = createModernButton("Add Question");
        addQuestionButton.setBackground(new Color(127, 204, 165)); // Green background
        addQuestionButton.setForeground(Color.WHITE);
        addQuestionButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        addQuestionButton.setFocusPainted(false);
        addQuestionButton.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Button padding
        addQuestionButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Adjust button width
        addQuestionButton.setPreferredSize(new Dimension(150, addQuestionButton.getPreferredSize().height));

        // Create buttons according to category and subcategory
        if ("KFF".equals(currentCategory) || "Figuren".equals(currentSubcategory)) {

            // Create the button first to get its height and style
            JButton generateButton = createModernButton("Generate");
            generateButton.setBackground(new Color(127, 204, 165)); // Green background
            generateButton.setForeground(Color.WHITE);
            generateButton.setFont(new Font("SansSerif", Font.BOLD, 14));
            generateButton.setFocusPainted(false);
            generateButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10)); // Padding for aesthetics
            generateButton.setPreferredSize(new Dimension(150, generateButton.getPreferredSize().height));

            // Match the border radius and font size to the button
            int buttonBorderRadius = 16; // Match the button's roundness (see createModernButton)
            int numberBoxHeight = generateButton.getPreferredSize().height;
            // Font numberBoxFont = generateButton.getFont();
            JTextField questionCountField = new RoundedTextField("0", buttonBorderRadius, new Color(127,204,165), Color.WHITE, numberBoxHeight);

            // Fix: Set larger row height for FigurenOptionsData rows in KFF overview
            if (questionTable != null) {
                for (int row = 0; row < questionTable.getRowCount(); row++) {
                    Object value = questionTable.getValueAt(row, 1);
                    if (value != null && value.getClass().getSimpleName().equals("FigurenOptionsData")) {
                        questionTable.setRowHeight(row, 150); // Make Figuren options panel less squished
                    }
                }
            }

            // Set number box width and height to match button
            questionCountField.setPreferredSize(new Dimension(60, numberBoxHeight));

            // Set up action listeners
            if ("Figuren".equals(currentSubcategory)) {
                // Only generateButton is added
                // Add buttons and text field to buttonPanel
                buttonPanel.add(generateButton);
                buttonPanel.add(questionCountField);
                generateButton.addActionListener(e -> {
                    try {
                        // Get the value from the text field
                        String input = questionCountField.getText().trim();
                        int questionCount;

                        // Validate the input as a number
                        try {
                            questionCount = Integer.parseInt(input);
                        } catch (NumberFormatException ex) {
                            JOptionPane.showMessageDialog(null, "Please enter a valid number.", "Invalid Input",
                                    JOptionPane.ERROR_MESSAGE);
                            return; // Exit if input is invalid
                        }

                        // Generate questions
                        for (int i = 0; i < questionCount; i++) {
                            addNewFigurenQuestion();
                        }

                        // Refresh the table
                        tableModel.fireTableDataChanged();

                        // Scroll to the newly added question
                        if (questionTable != null && tableModel != null) {
                            questionTable.scrollRectToVisible(
                                    questionTable.getCellRect(tableModel.getRowCount() - 1, 0, true));
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            } else {
                // For "KFF" category or its subcategories

                // Add button to buttonPanel
                buttonPanel.add(addQuestionButton);
                // Add buttons and text field to buttonPanel
                buttonPanel.add(generateButton);
                buttonPanel.add(questionCountField);

                // Set up action listener for "Add Question" button
                addQuestionButton.addActionListener(e -> {
                    addNewQuestionToSubcategory();
                    // Scroll to the newly added question if needed
                    if (questionTable != null && tableModel != null) {
                        questionTable
                                .scrollRectToVisible(questionTable.getCellRect(tableModel.getRowCount() - 1, 0, true));
                    }
                });

                generateButton.addActionListener(e -> {
                    try {
                        // Get the value from the text field
                        String input = questionCountField.getText().trim();
                        int questionCount;

                        // Validate the input as a number
                        try {
                            questionCount = Integer.parseInt(input);
                        } catch (NumberFormatException ex) {
                            JOptionPane.showMessageDialog(null, "Please enter a valid number.", "Invalid Input",
                                    JOptionPane.ERROR_MESSAGE);
                            return; // Exit if input is invalid
                        }

                        if ("Wortflüssigkeit".equals(currentSubcategory)) {
                            WortfluessigkeitGenerator gen = new WortfluessigkeitGenerator(conn, currentCategory,
                                    currentSubcategory, selectedSimulationId);
                            gen.execute(questionCount);
                            loadQuestionsFromDatabase(currentCategory, categoryModels.get(currentCategory),
                                    selectedSimulationId);
                            switchSubcategory(currentCategory, currentSubcategory);
                        } else if ("Implikationen".equals(currentSubcategory)) {
                            SyllogismGenerator generator = new SyllogismGenerator(conn, currentCategory,
                                    currentSubcategory,
                                    selectedSimulationId);
                            generator.execute(questionCount);

                            // Reload questions and refresh UI
                            loadQuestionsFromDatabase(currentCategory, categoryModels.get(currentCategory),
                                    selectedSimulationId);
                            tableModel.fireTableDataChanged();
                        } else if ("Zahlenfolgen".equals(currentSubcategory)) {
                            ZahlenfolgenGenerator generator = new ZahlenfolgenGenerator(conn, currentCategory,
                                    currentSubcategory, selectedSimulationId);
                            generator.execute(questionCount);

                            // Reload questions and refresh UI
                            loadQuestionsFromDatabase(currentCategory, categoryModels.get(currentCategory),
                                    selectedSimulationId);
                            tableModel.fireTableDataChanged();
                        }

                        if (questionTable != null && tableModel != null) {
                            questionTable.scrollRectToVisible(
                                    questionTable.getCellRect(tableModel.getRowCount() - 1, 0, true));
                        }
                    } catch (SQLException ex) {
                        debugLog("QuestionGen", currentSubcategory + " not generated SQLException: " + ex.getMessage());
                        ex.printStackTrace();
                    } catch (IOException e1) {
                        debugLog("QuestionGen", currentSubcategory + " not generated IOException: " + e1.getMessage());
                        e1.printStackTrace();
                    }
                });
            }


        } else {
            // Add button to buttonPanel
            buttonPanel.add(addQuestionButton);

            // Set up action listener for "Add Question" button
            addQuestionButton.addActionListener(e -> {
                addNewQuestionToSubcategory();
                if (questionTable != null && tableModel != null) {
                    questionTable.scrollRectToVisible(questionTable.getCellRect(tableModel.getRowCount() - 1, 0, true));
                }
            });
        }

        // Buttons for deleting questions
        JButton deleteMarkedButton = createModernButton("Delete Marked");
        deleteMarkedButton.setBackground(new Color(233, 151, 151));
        deleteMarkedButton.setForeground(Color.WHITE);
        deleteMarkedButton.addActionListener(e -> deleteSelectedQuestions());

        JButton deleteAllButton = createModernButton("Delete All");
        deleteAllButton.setBackground(new Color(233, 151, 151));
        deleteAllButton.setForeground(Color.WHITE);
        deleteAllButton.addActionListener(e -> {
            int res = JOptionPane.showConfirmDialog(this,
                    "Delete all questions in this subcategory?", "Confirm Delete",
                    JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) {
                deleteAllQuestions();
            }
        });

        buttonPanel.add(deleteMarkedButton);
        buttonPanel.add(deleteAllButton);

        // Add buttonPanel to subcategoryContentPanel
        subcategoryContentPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Add subcategoryContentPanel to mainContentPanel
        mainContentPanel.add(subcategoryContentPanel, BorderLayout.CENTER);

        // Ensure safe UI updates
        mainContentPanel.revalidate();
        mainContentPanel.repaint();

        // Update the print button label immediately when switching subcategories
        updatePrintButtonLabel();

        // Highlight the selected subcategory button and reset others
        resetSubcategoryButtons();

        // Find the button corresponding to the selected subcategory
        JButton newSelectedButton = getSubcategoryButton(subcategory);
        if (newSelectedButton != null) {
            // Set the background color of the new selected button
            newSelectedButton.setBackground(new Color(128, 146, 160));
            selectedSubcategoryButton = newSelectedButton;
        }

        // Load questions from the database
        loadQuestionsFromDatabase(category, categoryModels.get(category), selectedSimulationId);

        // Add TableModelListener to handle updates
        questionTable.getModel().addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (isAdjustingFormat) {
                    return; // Ignore events during format adjustments
                }
                if ((e.getType() == TableModelEvent.UPDATE || e.getType() == TableModelEvent.INSERT)) {
                    int row = e.getFirstRow();
                    int column = e.getColumn();
                    if (row == -1 || column == -1) {
                        // The event is not related to a specific cell; ignore or handle appropriately
                        return;
                    }
                    TableModel model = (TableModel) e.getSource();

                    if (row >= 0 && row < model.getRowCount() && column >= 0 && column < model.getColumnCount()) {
                        Object data = model.getValueAt(row, column);

                        // Determine if this is a question or option row
                        if (isFrageRow(row, (DefaultTableModel) model)) { // Question row
                            updateQuestion(row, column, data);
                        } else { // Option row
                            updateOption(row, column, data);
                        }
                    }
                }
            }
        });
    }

    private void addNewFigurenQuestion() {
        try {
            // Generate a random shape
            String[] shapes = { "Hexagon", "Octagon", "Heptagon", "Pentagon", "circle", "three-quarter circle",
                    "half circle", "quarter circle" };
            Random random = new Random();
            String selectedShape = shapes[random.nextInt(shapes.length)];
            int numberOfPieces = 5 + random.nextInt(3); // 5 to 7 pieces

            // Create the original shape
            Geometry originalGeometry = FigurenGenerator.createShape(selectedShape, 200, 200, 100);
            FigurenGenerator generator = new FigurenGenerator(originalGeometry, selectedShape);

            // Dissect the shape
            FigurenGenerator.PolygonDissector dissector = generator.new PolygonDissector(selectedShape, "hard");
            FigurenGenerator.DissectedPieces dissectedPieces = dissector.dissect(numberOfPieces);

            // Save the question and options to the database
            saveFigurenQuestionToDatabase(generator, dissectedPieces);

        } catch (SQLException ex) {
            debugLog("QuestionAdd", currentSubcategory + " not added SQLException: " + ex.getMessage());
            ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void addFigurenQuestionToTableModel(int questionNumber, FigurenGenerator generator,
            FigurenGenerator.DissectedPieces dissectedPieces, List<Geometry> optionShapes) throws SQLException {

        // Retrieve options from the database
        int questionId = questionDAO.getQuestionId(currentCategory, currentSubcategory, questionNumber,
                selectedSimulationId);
        List<OptionDAO> options = optionDAO.getOptionsForQuestion(questionId);

        OptionDAO correctOption = null;
        for (OptionDAO o : options) {
            if (o.isCorrect()) {
                correctOption = o;
                break;
            }
        }

        // Add the question to the table model
        DefaultTableModel model = categoryModels.get(currentCategory).get(currentSubcategory);
        model.addRow(new Object[] {
                String.valueOf(questionNumber),
                dissectedPieces, // Use the dissectedPieces as the question content
                correctOption,
                false, // Checkbox state
                "Kurz",
                "MEDIUM"
        });

        FigurenOptionsData figurenOptionsData = new FigurenOptionsData(options, dissectedPieces);

        // Add the options row
        model.addRow(new Object[] {
                "", // No number for options row
                figurenOptionsData, // Store the FigurenOptionsData object
                "",
                false,
                ""
        });

        // Refresh the table
        SwingUtilities.invokeLater(() -> {
            questionTable.revalidate();
            questionTable.repaint();
        });

        // Log rotated pieces for debugging
        for (Geometry geom : dissectedPieces.rotatedPieces) {
            debugLog("Geometry", "Rotated Geometry: " + geom.toText());
        }
    }

    /**
     * Saves a "Figuren" question and its options to the database.
     *
     * @param generator       The FigurenGenerator instance used to generate the
     *                        shape.
     * @param dissectedPieces The dissected pieces of the shape.
     * @throws SQLException If a database access error occurs.
     */
    private void saveFigurenQuestionToDatabase(FigurenGenerator generator,
            FigurenGenerator.DissectedPieces dissectedPieces) throws SQLException {
        // Define the question text and format
        String questionText = "Setzen Sie die Teile zusammen, um die ursprüngliche Figur zu erhalten.";
        // String format = "Lang"; // Removed unused variable

        // Get the shape type (ensure this method exists in FigurenGenerator)
        String shapeType = generator.getShapeType(); // e.g., "Hexagon"

        // Get the original shape geometry
        Geometry originalGeometry = generator.getGeometry();

        // Convert the original geometry to WKT
        WKTWriter wktWriter = new WKTWriter();
        String questionShapeWKT = wktWriter.write(originalGeometry);

        // Convert dissected pieces to WKT, separated by semicolons
        StringBuilder dissectedPiecesWKT = new StringBuilder();
        for (Geometry piece : dissectedPieces.originalPieces) {
            String pieceWKT = wktWriter.write(piece);
            dissectedPiecesWKT.append(pieceWKT).append(";");

            debugLog("Geometry", "Saving dissected piece: " + piece.toText());
        }

        StringBuilder originalPiecesWKT = new StringBuilder();
        for (Geometry piece : dissectedPieces.originalPieces) {
            originalPiecesWKT.append(wktWriter.write(piece)).append(";");
        }

        // Remove the last semicolon
        if (dissectedPiecesWKT.length() > 0) {
            dissectedPiecesWKT.setLength(dissectedPiecesWKT.length() - 1);
        }

        // Geometry fullShape = generator.getGeometry(); // Removed unused variable
        StringBuilder assembledPiecesWKT = new StringBuilder();
        for (Geometry p : dissectedPieces.originalPieces) { // **nicht der Union-Shape!**
            assembledPiecesWKT.append(wktWriter.write(p)).append(";");
        }
        if (assembledPiecesWKT.length() > 0)
            assembledPiecesWKT.setLength(assembledPiecesWKT.length() - 1);
        // Get the current subcategory ID
        int subcategoryId = getSubcategoryId(currentCategory, currentSubcategory);
        if (subcategoryId == -1) {
            throw new SQLException("Subcategory not found for category: " + currentCategory + " and subcategory: "
                    + currentSubcategory);
        }

        // Get the next question number
        int questionNumber = questionDAO.getNextQuestionNumber(selectedSimulationId, subcategoryId);

        // Initialize DAOs
        QuestionDAO questionDAOInstance = new QuestionDAO(conn);
        OptionDAO optionDAOInstance = new OptionDAO(conn);

        // Start transaction
        try {
            conn.setAutoCommit(false);

            // Insert the question into the database
            int questionId = questionDAOInstance.insertQuestionWithShape(
                    currentCategory,
                    currentSubcategory,
                    questionNumber,
                    questionText,
                    selectedSimulationId,
                    questionShapeWKT,
                    shapeType,
                    dissectedPiecesWKT.toString(),
                    assembledPiecesWKT.toString());

            if (questionId == -1) {
                throw new SQLException("Failed to insert Figuren question into the database.");
            }

            // Generate distractor shapes (assuming you have this method)
            List<Geometry> distractorShapes = generator.generateDistractorShapes(3); // Generate 3 distractors

            // Prepare options list: correct option + distractors
            List<Geometry> optionShapes = new ArrayList<>();
            optionShapes.add(originalGeometry); // Correct option
            optionShapes.addAll(distractorShapes); // Distractors

            // Shuffle options to randomize their order
            Collections.shuffle(optionShapes);

            // Insert options A-D with shape data
            char optionLabel = 'A';
            for (Geometry optionShape : optionShapes) {
                String label = String.valueOf(optionLabel);
                String optionShapeWKT = wktWriter.write(optionShape);
                boolean isCorrect = optionShape.equalsExact(originalGeometry);
                optionDAOInstance.addOptionWithShape(questionId, label, optionShapeWKT, isCorrect);
                optionLabel++;
            }

            // Insert option E as "X" without shape data
            optionDAOInstance.addOption(questionId, "E", "X", false);

            // Commit transaction
            conn.commit();

            // Add the question and its options to the table model in the UI
            addFigurenQuestionToTableModel(questionNumber, generator, dissectedPieces, optionShapes);

        } catch (SQLException e) {
            // Rollback transaction on error
            try {
                conn.rollback();
                JOptionPane.showMessageDialog(this, "Transaction rolled back due to an error.", "Error",
                        JOptionPane.ERROR_MESSAGE);
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error during rollback: " + rollbackEx.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving Figuren question: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            // Reset auto-commit to true
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    // Method to reset pending delete state and repaint all relevant tables
    private void resetPendingDeleteState() {
        // Clear pending delete state
        tablePendingDeleteRowMap.clear();
        repaintAllTables();
    }

    // Repaint all tables in the main content panel to clear highlights
    private void repaintAllTables() {
        for (Component comp : mainContentPanel.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel subPanel = (JPanel) comp;
                for (Component subComp : subPanel.getComponents()) {
                    if (subComp instanceof JScrollPane) {
                        JScrollPane scrollPane = (JScrollPane) subComp;
                        if (scrollPane.getViewport().getView() instanceof JTable) {
                            JTable table = (JTable) scrollPane.getViewport().getView();
                            table.repaint();
                        }
                    }
                }
            } else if (comp instanceof JButton) {
                // If you need to do something with buttons, do it here
                debugLog("UI", "Button text: " + ((JButton) comp).getText());
            }
        }
        if (questionTable != null) {
            questionTable.repaint(); // Ensure the main question table is also repainted
        }
    }

    // Delete all questions currently marked for deletion
    private void deleteSelectedQuestions() {
        if (pendingDeleteQuestions.isEmpty()) {
            return;
        }

        List<Integer> toDelete = new ArrayList<>();
        for (QuestionIdentifier id : pendingDeleteQuestions) {
            if (currentSubcategory.equals(id.subcategory)) {
                toDelete.add(id.questionNumber);
            }
        }
        toDelete.sort(Comparator.reverseOrder());

        try {
            conn.setAutoCommit(false);
            for (int num : toDelete) {
                if (questionDAO.deleteQuestion(currentCategory, currentSubcategory, num, selectedSimulationId)) {
                    renumberQuestionsInDatabaseAndUI(num);
                } else {
                    conn.rollback();
                    JOptionPane.showMessageDialog(this, "Failed to delete question", "Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            JOptionPane.showMessageDialog(this, "Error deleting questions: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        pendingDeleteQuestions.clear();
        reloadQuestionsAfterDeletion();
    }

    // Delete every question in the current subcategory
    private void deleteAllQuestions() {
        try {
            int subId = getSubcategoryId(currentCategory, currentSubcategory);
            List<QuestionDAO> qs = questionDAO.getQuestionsBySubcategoryAndSimulation(subId, selectedSimulationId);
            List<Integer> nums = new ArrayList<>();
            for (QuestionDAO q : qs) {
                nums.add(q.getQuestionNumber());
            }
            pendingDeleteQuestions.clear();
            nums.sort(Comparator.reverseOrder());

            conn.setAutoCommit(false);
            for (int num : nums) {
                if (questionDAO.deleteQuestion(currentCategory, currentSubcategory, num, selectedSimulationId)) {
                    renumberQuestionsInDatabaseAndUI(num);
                }
            }
            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            JOptionPane.showMessageDialog(this, "Error deleting all questions: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        reloadQuestionsAfterDeletion();
    }

    // Reset the background color of all subcategory buttons to default
    private void resetSubcategoryButtons() {
        // Check if there is a currently selected button before resetting
        if (selectedSubcategoryButton != null) {
            selectedSubcategoryButton.setBackground(new Color(221, 221, 221)); // Reset to default color
            selectedSubcategoryButton = null; // Clear the reference to ensure no stale selection remains
        }
    }

    // Method to check if the "Set" column should be visible for the current
    // category
    private boolean isSetColumnNotVisible(String category) {
        // Define which categories should have the "Set" column visible
        List<String> categoriesWithSetColumn = Arrays.asList("KFF"); // Add more categories as needed
        return categoriesWithSetColumn.contains(category);
    }

    // Method to hide the "Set" column

    private void hideSetColumn() {
        if (questionTable == null) return;
        TableColumnModel columnModel = questionTable.getColumnModel();
        int columnIndex = getColumnIndexByName("Set");
        if (columnIndex != -1) {
            TableColumn setColumn = columnModel.getColumn(columnIndex);
            setColumn.setMinWidth(0);
            setColumn.setMaxWidth(0);
            setColumn.setPreferredWidth(0);
            setColumn.setHeaderValue("");
        }
    }

    // Method to show the "Set" column
    private void showSetColumn() {
        if (questionTable == null) return;
        TableColumnModel columnModel = questionTable.getColumnModel();
        int columnIndex = getColumnIndexByName("Set");
        if (columnIndex != -1) {
            TableColumn setColumn = columnModel.getColumn(columnIndex);
            setColumn.setMinWidth(35);
            setColumn.setMaxWidth(35);
            setColumn.setPreferredWidth(35);
            setColumn.setHeaderValue("Set");
        }
    }

    // Helper method to get the index of a column by name
    private int getColumnIndexByName(String columnName) {
        TableColumnModel columnModel = questionTable.getColumnModel();
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            if (columnModel.getColumn(i).getHeaderValue().equals(columnName)) {
                return i;
            }
        }
        return -1; // Column not found
    }

    // Update the print button label based on the current category and subcategory
    // names
    private void updatePrintButtonLabel() {
        String printButtonText = currentCategory + " Print";
        printCategoryButton.setText(printButtonText);
        adjustPrintButtonWidth(printCategoryButton, printButtonText);
    }

    // Method to get the subcategory button by its text
    private JButton getSubcategoryButton(String subcategory) {
        for (Component component : subcategoryPanel.getComponents()) {
            if (component instanceof JPanel) {
                JPanel panel = (JPanel) component;
                for (Component inner : panel.getComponents()) {
                    if (inner instanceof JButton) {
                        JButton btn = (JButton) inner;
                        if (subcategory.equals(btn.getText())) return btn;
                    }
                }
            } else if (component instanceof JButton) {
                JButton btn = (JButton) component;
                if (subcategory.equals(btn.getText())) return btn;
            }
        }
        return null;
    }

    // Adjust column widths for a given table
    /**
     * Utility to set preferred/min/max widths for columns by index.
     */
    private void adjustColumnWidths(JTable table) {
        if (table == null || table.getColumnModel().getColumnCount() == 0) return;
        setColumnWidth(table, 0, 40, 40, 40); // Nr.
        setColumnWidth(table, 2, 120, 150, 130); // Solution
        setColumnWidth(table, 3, 40, 50, 50); // ✓
        setColumnWidth(table, 4, 35, 35, 35); // Set
        setColumnWidth(table, 5, 35, 100, 60); // Diff
    }

    /**
     * Helper to set min/max/preferred width for a column.
     */
    private void setColumnWidth(JTable table, int col, int min, int max, int pref) {
        TableColumn column = table.getColumnModel().getColumn(col);
        column.setMinWidth(min);
        column.setMaxWidth(max);
        column.setPreferredWidth(pref);
    }

    // Reset the background color of all category buttons to default
    /**
     * Resets all category buttons to the default background color.
     */
    private void resetCategoryButtons() {
        Color defaultColor = new Color(221, 221, 221);
        for (JButton btn : Arrays.asList(bioButton, chemButton, physButton, mathButton, kffButton)) {
            btn.setBackground(defaultColor);
        }
    }

    // Method to create a new table model with an additional button column
    private DefaultTableModel createTableModel() {
        return new DefaultTableModel(new String[] { "Nr.", "Text", "Solution", "✓", "Set", "Diff" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                if ("Figuren".equals(currentSubcategory)) {
                    Object value = getValueAt(row, 1);
                    if (value instanceof FigurenOptionsData || value instanceof List<?>) {
                        return false; // Options row not editable
                    }
                    // Allow editing of the checkmark and difficulty for question rows
                    if (column == 3 || column == 5) {
                        return true;
                    }
                    return false;
                } else {
                    // For other subcategories, "Text" column is editable
                    if (column == 1) {
                        return true;
                    }
                }
                if (column == 3) {
                    // "✓" column is editable for both question and answer rows
                    return true;
                }
                if (column == 4)
                    return isFrageRow(row, this); // Only allow editing the "Set" column for question rows

                if (column == 5)
                    return isFrageRow(row, this); // Difficulty nur bei Fragen editierbar

                return false; // "Nr." column is not editable
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0)
                    return String.class; // Ensure the first column is always String
                if (columnIndex == 1)
                    return Object.class;
                if (columnIndex == 2)
                    return String.class; // Solution column
                if (columnIndex == 3)
                    return Object.class; // Use Object.class instead of Boolean.class
                if (columnIndex == 4)
                    return String.class; // For the format
                if (columnIndex == 5)
                    return String.class; // Difficulty
                return Object.class;
            }
        };
    }

    // Method to print the current category to a Word document
    private void printCategory(String category) {
        Map<String, DefaultTableModel> subcategories = categoryModels.get(category);
        if (subcategories == null) {
            JOptionPane.showMessageDialog(this, "No data available for category: " + category, "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        XWPFDocument document = new XWPFDocument();

        // Create a heading for the category
        XWPFParagraph categoryHeading = document.createParagraph();
        categoryHeading.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun categoryRun = categoryHeading.createRun();
        categoryRun.setBold(true);
        categoryRun.setFontSize(16);
        categoryRun.setText("Category: " + category);
        categoryRun.addBreak();

        // Iterate over subcategories
        for (String subcategory : subcategoryOrder.get(category)) {
            DefaultTableModel model = subcategories.get(subcategory);
            if (model == null || model.getRowCount() == 0) {
                continue; // Skip empty subcategories
            }

            // Create a heading for the subcategory
            XWPFParagraph subcategoryHeading = document.createParagraph();
            subcategoryHeading.setAlignment(ParagraphAlignment.LEFT);
            XWPFRun subcategoryRun = subcategoryHeading.createRun();
            subcategoryRun.setBold(true);
            subcategoryRun.setFontSize(14);
            subcategoryRun.setText("Subcategory: " + subcategory);
            subcategoryRun.addBreak();

            // Add questions
            addQuestionsToDocument(document, model, false);
        }

        // Save the document
        try (FileOutputStream out = new FileOutputStream(category + ".docx")) {
            document.write(out);
            JOptionPane.showMessageDialog(this, "Document saved: " + category + ".docx");
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving document: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Method to print the solution document for the current category
    private void printCategorySolution(String category) {
        Map<String, DefaultTableModel> subcategories = categoryModels.get(category);
        if (subcategories == null) {
            JOptionPane.showMessageDialog(this, "No data available for category: " + category, "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        XWPFDocument document = new XWPFDocument();

        // Create a heading for the category
        XWPFParagraph categoryHeading = document.createParagraph();
        categoryHeading.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun categoryRun = categoryHeading.createRun();
        categoryRun.setBold(true);
        categoryRun.setFontSize(16);
        categoryRun.setText("Solutions for Category: " + category);
        categoryRun.addBreak();

        // Iterate over subcategories
        for (String subcategory : subcategoryOrder.get(category)) {
            DefaultTableModel model = subcategories.get(subcategory);
            if (model == null || model.getRowCount() == 0) {
                continue; // Skip empty subcategories
            }

            // Create a heading for the subcategory
            XWPFParagraph subcategoryHeading = document.createParagraph();
            subcategoryHeading.setAlignment(ParagraphAlignment.LEFT);
            XWPFRun subcategoryRun = subcategoryHeading.createRun();
            subcategoryRun.setBold(true);
            subcategoryRun.setFontSize(14);
            subcategoryRun.setText("Subcategory: " + subcategory);
            subcategoryRun.addBreak();

            // Add solutions
            addQuestionsToDocument(document, model, true);
        }

        // Save the document
        try (FileOutputStream out = new FileOutputStream(category + "_Solutions.docx")) {
            document.write(out);
            JOptionPane.showMessageDialog(this, "Solution document saved: " + category + "_Solutions.docx");
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving solution document: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Method to print all categories and their subcategories to a single Word
    // document
    private void printAllCategories() {
        XWPFDocument document = new XWPFDocument();

        // Iterate over all categories
        for (String category : categoryModels.keySet()) {
            Map<String, DefaultTableModel> subcategories = categoryModels.get(category);

            // Create a heading for the category
            XWPFParagraph categoryHeading = document.createParagraph();
            categoryHeading.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun categoryRun = categoryHeading.createRun();
            categoryRun.setBold(true);
            categoryRun.setFontSize(16);
            categoryRun.setText("Category: " + category);
            categoryRun.addBreak();

            // Iterate over subcategories
            for (String subcategory : subcategoryOrder.get(category)) {
                DefaultTableModel model = subcategories.get(subcategory);
                if (model == null || model.getRowCount() == 0) {
                    continue; // Skip empty subcategories
                }

                // Create a heading for the subcategory
                XWPFParagraph subcategoryHeading = document.createParagraph();
                subcategoryHeading.setAlignment(ParagraphAlignment.LEFT);
                XWPFRun subcategoryRun = subcategoryHeading.createRun();
                subcategoryRun.setBold(true);
                subcategoryRun.setFontSize(14);
                subcategoryRun.setText("Subcategory: " + subcategory);
                subcategoryRun.addBreak();

                // Add questions
                addQuestionsToDocument(document, model, false);
            }
        }

        // Save the document
        try (FileOutputStream out = new FileOutputStream("All_Categories.docx")) {
            document.write(out);
            JOptionPane.showMessageDialog(this, "Document saved: All_Categories.docx");
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving document: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Method to print all categories and their subcategories to a single solution
    // Word document
    private void printAllCategoriesSolution() {
        XWPFDocument document = new XWPFDocument();

        // Iterate over all categories
        for (String category : categoryModels.keySet()) {
            Map<String, DefaultTableModel> subcategories = categoryModels.get(category);

            // Create a heading for the category
            XWPFParagraph categoryHeading = document.createParagraph();
            categoryHeading.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun categoryRun = categoryHeading.createRun();
            categoryRun.setBold(true);
            categoryRun.setFontSize(16);
            categoryRun.setText("Solutions for Category: " + category);
            categoryRun.addBreak();

            // Iterate over subcategories
            for (String subcategory : subcategoryOrder.get(category)) {
                DefaultTableModel model = subcategories.get(subcategory);
                if (model == null || model.getRowCount() == 0) {
                    continue; // Skip empty subcategories
                }

                // Create a heading for the subcategory
                XWPFParagraph subcategoryHeading = document.createParagraph();
                subcategoryHeading.setAlignment(ParagraphAlignment.LEFT);
                XWPFRun subcategoryRun = subcategoryHeading.createRun();
                subcategoryRun.setBold(true);
                subcategoryRun.setFontSize(14);
                subcategoryRun.setText("Subcategory: " + subcategory);
                subcategoryRun.addBreak();

                // Add solutions
                addQuestionsToDocument(document, model, true);
            }
        }

        // Save the document
        try (FileOutputStream out = new FileOutputStream("All_Categories_Solutions.docx")) {
            document.write(out);
            JOptionPane.showMessageDialog(this, "Solution document saved: All_Categories_Solutions.docx");
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving solution document: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Helper method to add questions and answers to the document
    private void addQuestionsToDocument(XWPFDocument document, DefaultTableModel model, boolean isSolution) {
        int rowCount = model.getRowCount();

        for (int row = 0; row < rowCount; row++) {
            if (isFrageRow(row, model)) {
                // Get question text
                String questionNumber = model.getValueAt(row, 0).toString();
                String questionText = model.getValueAt(row, 1).toString();

                // Add question to document
                XWPFParagraph questionParagraph = document.createParagraph();
                questionParagraph.setAlignment(ParagraphAlignment.LEFT);
                XWPFRun questionRun = questionParagraph.createRun();
                questionRun.setBold(true);
                questionRun.setFontSize(12);
                questionRun.setText(questionNumber + ". " + questionText);
                questionRun.addBreak();

                String format = (String) model.getValueAt(row, 4);

                // Collect answers
                row++;
                List<String> options = new ArrayList<>();
                List<String> answers = new ArrayList<>();
                while (row < rowCount && !isFrageRow(row, model)) {
                    String label = model.getValueAt(row, 0).toString();
                    String text = model.getValueAt(row, 1).toString();
                    Object korrektObj = model.getValueAt(row, 3);

                    if ("Lang".equals(format) && label.matches("\\d\\.")) {
                        // It's an option row
                        options.add(label + " " + text);
                    } else if (label.matches("[A-E]\\)")) {
                        // It's an answer row
                        String answerText = label + " " + text;
                        if (isSolution) {
                            // Append the correct options to the answer
                            if (korrektObj instanceof Boolean && (Boolean) korrektObj) {
                                answerText += " (Correct)";
                            }
                        }
                        answers.add(answerText);
                    }
                    row++;
                }
                row--; // Adjust row index after the loop

                // Add options if any
                if (!options.isEmpty()) {
                    XWPFParagraph optionsParagraph = document.createParagraph();
                    optionsParagraph.setAlignment(ParagraphAlignment.LEFT);
                    for (String option : options) {
                        XWPFRun optionRun = optionsParagraph.createRun();
                        optionRun.setFontSize(12);
                        optionRun.setText(option);
                        optionRun.addBreak();
                    }
                }

                // Add answers
                XWPFParagraph answersParagraph = document.createParagraph();
                answersParagraph.setAlignment(ParagraphAlignment.LEFT);
                for (String answer : answers) {
                    XWPFRun answerRun = answersParagraph.createRun();
                    answerRun.setFontSize(12);
                    answerRun.setText(answer);
                    answerRun.addBreak();
                }
            }
        }
    }


    // =====================
    // Debug Logging Section
    // =====================
    public enum LogLevel { DEBUG, INFO, WARN, ERROR }
    private static final String LOG_FILE = "debug.log";
    private static final Set<String> VERBOSE_CATEGORIES = new HashSet<>(Arrays.asList("Geometry", "Table"));
    private static LogLevel globalLogLevel = LogLevel.INFO;
    private static boolean debugVerbose = false;

    /**
     * Set the global log level for debug output.
     */
    public static void setLogLevel(LogLevel level) {
        globalLogLevel = level;
    }

    /**
     * Enable or disable verbose debug output for certain categories.
     */
    public static void setDebugVerbose(boolean verbose) {
        debugVerbose = verbose;
    }

    /**
     * Centralized debug logger with log levels, categories, and method context.
     * Only logs if the message level is >= globalLogLevel, or if the category is in VERBOSE_CATEGORIES and debugVerbose is true.
     * @param category Category of the log (e.g., "Geometry", "UI", "DB")
     * @param level LogLevel (DEBUG, INFO, WARN, ERROR)
     * @param method Method context (e.g., "saveFigurenQuestionToDatabase")
     * @param message The log message
     */
    public static void debugLog(String category, LogLevel level, String method, String message) {
        if (level.ordinal() < globalLogLevel.ordinal() && !(debugVerbose && VERBOSE_CATEGORIES.contains(category))) {
            return;
        }
        String logEntry = String.format("[%s] [%s] [%s] %s: %s\n",
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()),
                level.name(), category, method, message);
        try (java.io.OutputStreamWriter fw = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(LOG_FILE, true), java.nio.charset.StandardCharsets.UTF_8)) {
            fw.write(logEntry);
        } catch (java.io.IOException e) {
            // Fallback: print to stderr if logging fails
            System.err.println("[LOGGING ERROR] " + logEntry);
        }
    }

    /**
     * Convenience overload for INFO level with automatic method context.
     */
    public static void debugLog(String category, String message) {
        debugLog(category, LogLevel.INFO, inferCallingMethod(), message);
    }

    /**
     * Convenience overload for custom level with automatic method context.
     */
    public static void debugLog(String category, LogLevel level, String message) {
        debugLog(category, level, inferCallingMethod(), message);
    }

    /**
     * Infers the calling method name for logging context.
     */
    private static String inferCallingMethod() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        // stack[0] = getStackTrace, stack[1] = inferCallingMethod, stack[2] = debugLog, stack[3] = caller
        if (stack.length > 4) {
            return stack[4].getMethodName();
        } else if (stack.length > 3) {
            return stack[3].getMethodName();
        }
        return "unknown";
    }

    // =====================
    // End Debug Logging Section
    // =====================


    private int getFrageRowForRow(int row, JTable table) {
        if (table == null) {
            debugLog("Table", "Error: table is null in getFrageRowForRow");
            return -1;
        }
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        while (row >= 0) {
            if (isFrageRow(row, model)) {
                return row;
            }
            row--;
        }
        return -1;
    }

    class CustomEditor extends AbstractCellEditor implements TableCellEditor {
        private JButton deleteButton;
        private JTable questionTable;
        private JCheckBox checkBox;
        private Component editorComponent;
        private TableCellEditor defaultTextEditor;
        private JTable table;
        private int editingRow;
        private int editingColumn;
        private final JButton actionButton; // Button for the new column
        private final JPopupMenu formatMenu; // Popup menu for the format options
        private JComboBox<String> difficultyCombo;

        public CustomEditor(JTable table) {
            this.table = table;
            this.defaultTextEditor = new DefaultCellEditor(new JTextField()); // Keep this for text editing
            MedatoninDB.this.tablePendingDeleteRowMap = new HashMap<>();

            // Initialize the action button with the gear icon
            actionButton = new JButton();
            actionButton.setIcon(gearIcon); // Set the gear icon
            actionButton.setBackground(new Color(216, 232, 255)); // Light blue background);
            actionButton.setFocusPainted(false);
            actionButton.setBorder(BorderFactory.createEmptyBorder());
            actionButton.setContentAreaFilled(false); // Makes button background transparent

            // Initialize the dropdown menu
            formatMenu = new JPopupMenu();
            JMenuItem kurzItem = new JMenuItem("Kurz");
            kurzItem.setFont(new Font("SansSerif", Font.PLAIN, 12));
            kurzItem.addActionListener(e -> updateFormatForRow(editingRow, "Kurz"));
            formatMenu.add(kurzItem);

            JMenuItem langItem = new JMenuItem("Lang");
            langItem.setFont(new Font("SansSerif", Font.PLAIN, 12));
            langItem.addActionListener(e -> updateFormatForRow(editingRow, "Lang"));
            formatMenu.add(langItem);

            actionButton.addActionListener(e -> {
                if (editingRow >= 0 && editingRow < table.getRowCount()) {
                    String currentFormat = (String) table.getModel().getValueAt(editingRow, 3);
                    if (currentFormat == null || currentFormat.isEmpty()) {
                        currentFormat = "Kurz";
                    }
                    updateMenuHighlight(currentFormat, kurzItem, langItem);
                    formatMenu.show(actionButton, 0, actionButton.getHeight());
                } else {
                    debugLog("UI", "Error: Invalid editing row index: " + editingRow);
                }
            });

            // Initialize the delete button
            deleteButton = new JButton("X");
            deleteButton.setForeground(Color.RED);
            deleteButton.setFocusPainted(false);
            deleteButton.setBorderPainted(false);
            deleteButton.setContentAreaFilled(false);
            deleteButton.setHorizontalAlignment(SwingConstants.CENTER);

            // Add action listener to the delete button
            deleteButton.addActionListener(e -> {
                stopCellEditing();
                togglePendingDelete(editingRow);
            });

            // Initialize the checkbox
            checkBox = new JCheckBox();
            checkBox.setHorizontalAlignment(SwingConstants.CENTER);
            checkBox.addActionListener(e -> {
                stopCellEditing();
                table.repaint(); // Force the table to repaint
            });

            // Initialize the default text editor
            defaultTextEditor = new DefaultCellEditor(new JTextField());

            // Difficulty ComboBox initialisieren
            difficultyCombo = new JComboBox<>(new String[] { "EASY", "MEDIUM", "HARD" });
            difficultyCombo.setBackground(Color.WHITE);
            difficultyCombo.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));
            difficultyCombo.setFocusable(false);
            // Custom Renderer: colored rectangle, no text, same size as cell
            difficultyCombo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value,
                        int index, boolean isSelected, boolean cellHasFocus) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(
                            list, value, index, isSelected, cellHasFocus);
                    Color bg = Color.WHITE;
                    if (value != null) {
                        String diffStr = value.toString().toLowerCase();
                        if ("easy".equals(diffStr)) {
                            bg = new Color(127, 204, 165, 75); // green
                        } else if ("medium".equals(diffStr)) {
                            bg = new Color(255, 191, 71, 75); // orange
                        } else if ("hard".equals(diffStr)) {
                            bg = new Color(255, 71, 71, 75); // red
                        }
                    }
                    label.setText("");
                    label.setBackground(bg);
                    label.setOpaque(true);
                    // Set preferred size to match the Diff cell (height 30, width 80)
                    label.setPreferredSize(new Dimension(80, 30));
                    return label;
                }
            });

            difficultyCombo.addActionListener(e -> {
                stopCellEditing(); // End editing immediately so renderer is shown
                // Update the table model with the new difficulty value
                if (editingRow >= 0 && editingRow < table.getRowCount()) {
                    Object selected = difficultyCombo.getSelectedItem();
                    table.setValueAt(selected, editingRow, 5); // Update Diff cell
                    // Force renderer update for Diff cell
                    DefaultTableModel model = (DefaultTableModel) table.getModel();
                    model.fireTableCellUpdated(editingRow, 5);

                    // Persist difficulty to database if this is a question row
                    if (isFrageRow(editingRow, model)) {
                        String questionNumber = String.valueOf(model.getValueAt(editingRow, 0));
                        try {
                            int subcategoryId = getSubcategoryId(currentCategory, currentSubcategory);
                            int qNum = Integer.parseInt(questionNumber);
                            String selectedDifficulty = selected != null ? selected.toString() : "MEDIUM";
                            questionDAO.updateQuestionDifficulty(subcategoryId, qNum, selectedDifficulty, selectedSimulationId);
                            debugLog("DB", LogLevel.INFO, "CustomEditor", "Updated difficulty for subcategoryId " + subcategoryId + ", questionNumber " + qNum + " to " + selectedDifficulty);
                        } catch (Exception ex) {
                            debugLog("DB", LogLevel.ERROR, "CustomEditor", "Failed to update difficulty: " + ex.getMessage());
                        }
                    }
                }
                // Force immediate repaint of the edited cell
                Rectangle cellRect = table.getCellRect(editingRow, 5, true);
                table.repaint(cellRect);
                SwingUtilities.invokeLater(() -> {
                    table.revalidate();
                    table.repaint();
                });
            });

            // Popup-Größe anpassen
            difficultyCombo.setMaximumRowCount(3);
            difficultyCombo.setPreferredSize(new Dimension(80, 30));
        }

        private void updateMenuHighlight(String currentFormat, JMenuItem kurzItem, JMenuItem langItem) {
            kurzItem.setFont(new Font("SansSerif", Font.PLAIN, 12));
            langItem.setFont(new Font("SansSerif", Font.PLAIN, 12));

            // Highlight the currently selected format
            if ("Kurz".equals(currentFormat)) {
                kurzItem.setFont(new Font("SansSerif", Font.BOLD, 12)); // Highlight Kurz
            } else if ("Lang".equals(currentFormat)) {
                langItem.setFont(new Font("SansSerif", Font.BOLD, 12)); // Highlight Lang
            }
        }

        // Update the format for a specific question row and adjust rows if needed
        private void updateFormatForRow(int row, String newFormat) {
            if (table == null || table.getModel() == null) {
                debugLog("UI", "Error: Table or table model is null in updateFormatForRow.");
                return;
            }

            DefaultTableModel model = (DefaultTableModel) table.getModel();

            if (row < 0 || row >= model.getRowCount()) {
                debugLog("UI", "Error: Invalid row index in updateFormatForRow: " + row);
                return;
            }

            String currentFormat = (String) table.getModel().getValueAt(row, 3);
            if (currentFormat == null) {
                currentFormat = "Kurz";
            }

            if (!currentFormat.equals(newFormat)) {
                // Get the question number and text
                String questionNumber = String.valueOf(model.getValueAt(row, 0)); // Treat value as a String

                try {
                    // Update the question format in the database
                    int subcategoryId = getSubcategoryId(currentCategory, currentSubcategory);
                    questionDAO.updateQuestionFormat(subcategoryId, Integer.parseInt(questionNumber), newFormat);
                    int questionId = questionDAO.getQuestionId(currentCategory, currentSubcategory,
                            Integer.parseInt(questionNumber), selectedSimulationId);
                    if (questionId == -1) {
                        throw new SQLException("Question not found.");
                    }
                    MedatoninDB.this.isAdjustingFormat = true; // Suppress listeners
                    // Adjust the options in the database based on the new format
                    if (newFormat.equals("Kurz")) {
                        // Remove options 1-4 if they exist
                        for (int i = 1; i <= 4; i++) {
                            optionDAO.deleteOption(questionId, i + ".");
                        }
                    } else if (newFormat.equals("Lang")) {
                        // Add options 1-4 if they don't exist
                        for (int i = 1; i <= 4; i++) {
                            String optionLabel = i + ".";
                            if (!optionDAO.optionExists(questionId, optionLabel)) {
                                optionDAO.addOption(questionId, optionLabel, "", false);
                            }
                        }
                    }

                    // Adjust the GUI
                    adjustRowsForFormat(row, currentFormat, newFormat);
                    table.getModel().setValueAt(newFormat, row, 3);
                    SwingUtilities.invokeLater(() -> {
                        table.revalidate();
                        table.repaint();
                    });
                } catch (SQLException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Error updating question format in database: " + e.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    MedatoninDB.this.isAdjustingFormat = false; // Re-enable listeners
                }
            } else {
                debugLog("UI", "Format is already " + newFormat + ". No changes needed.");
                return; // Exit the method without making any changes
            }

            stopCellEditing();
            SwingUtilities.invokeLater(() -> {
                table.revalidate();
                table.repaint(); // Ensure the renderer is reapplied
            });

        }

        // Method to adjust rows when format changes between "Kurz" and "Lang"
        private void adjustRowsForFormat(int frageRow, String currentFormat, String newFormat) {
            DefaultTableModel model = (DefaultTableModel) table.getModel();

            // Store existing A)-E) option texts and correctness states
            Map<String, String> answerTexts = new HashMap<>();
            Map<String, Boolean> answerCorrectness = new HashMap<>();
            int rowIndex = frageRow + 1;

            while (rowIndex < model.getRowCount() && !isFrageRow(rowIndex, model)) {
                String label = model.getValueAt(rowIndex, 0).toString();
                if (label.matches("[A-E]\\)")) {
                    answerTexts.put(label, model.getValueAt(rowIndex, 1).toString());
                    Object correctObj = model.getValueAt(rowIndex, 2);
                    answerCorrectness.put(label, correctObj instanceof Boolean && (Boolean) correctObj);
                }
                model.removeRow(rowIndex);
                // Do not increment rowIndex; rows shift up after removal
            }

            // Add new rows based on the new format
            if ("Kurz".equals(newFormat)) {
                String[] answerLabels = { "A)", "B)", "C)", "D)", "E)" };
                for (int i = 0; i < answerLabels.length; i++) {
                    String label = answerLabels[i];
                    String text = answerTexts.getOrDefault(label, "");
                    Boolean isCorrect = answerCorrectness.getOrDefault(label, false);
                    model.insertRow(frageRow + 1 + i, new Object[] { label, text, isCorrect, "" });
                }
            } else if ("Lang".equals(newFormat)) {
                String[] optionLabels = { "1.", "2.", "3.", "4." };
                String[] answerLabels = { "A)", "B)", "C)", "D)", "E)" };
                for (int i = 0; i < optionLabels.length; i++) {
                    model.insertRow(frageRow + 1 + i, new Object[] { optionLabels[i], "", null, "" });
                }
                for (int i = 0; i < answerLabels.length; i++) {
                    String label = answerLabels[i];
                    String text = answerTexts.getOrDefault(label, "");
                    Boolean isCorrect = answerCorrectness.getOrDefault(label, false);
                    model.insertRow(frageRow + 1 + optionLabels.length + i,
                            new Object[] { label, text, isCorrect, "" });
                }
            }

            SwingUtilities.invokeLater(() -> {
                table.revalidate();
                table.repaint(); // Ensure all custom rendering and styles are applied
            });
        }

        private void togglePendingDelete(int row) {
            DefaultTableModel model = (DefaultTableModel) questionTable.getModel();
            String questionNumber = String.valueOf(model.getValueAt(row, 0));
            QuestionIdentifier identifier = new QuestionIdentifier(currentSubcategory,
                    Integer.parseInt(questionNumber));

            if (pendingDeleteQuestions.contains(identifier)) {
                // Second click - delete without confirmation
                deleteQuestionAtRow(questionTable, row);
                pendingDeleteQuestions.remove(identifier);
            } else {
                // First click - highlight the question
                pendingDeleteQuestions.add(identifier);
            }

            questionTable.repaint();
        }

        @Override
        public Object getCellEditorValue() {
            if (editingColumn == 2) {
                if (editorComponent == deleteButton) {
                    return "X";
                } else if (editorComponent == checkBox) {
                    return checkBox.isSelected();
                }
            } else if (editingColumn == 4 && editorComponent == difficultyCombo) {
                return difficultyCombo.getSelectedItem(); // Das ist schon korrekt
            }
            return defaultTextEditor.getCellEditorValue();
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            this.questionTable = table;
            this.editingRow = row;
            this.editingColumn = column;

            // Handle the options row for "Figuren"
            if ("Figuren".equals(currentSubcategory)) {
                Object cellValue = table.getModel().getValueAt(row, column);
                if (cellValue instanceof List<?>) {
                    // Options row in "Figuren" is not editable
                    return null;
                }
            }

            if (column == 3) {
                if (isFrageRow(row, (DefaultTableModel) table.getModel())) {
                    // For question rows, use delete button
                    deleteButton.setForeground(Color.RED);
                    deleteButton.setBackground(table.getBackground());
                    editorComponent = deleteButton;
                    return editorComponent; // Return immediately
                } else {
                    Boolean checked = false;
                    if (value instanceof Boolean) {
                        checked = (Boolean) value;
                    }
                    checkBox.setSelected(checked != null && checked);
                    editorComponent = checkBox;
                    return editorComponent; // Return immediately
                }
            }

            if (column == 4 && isFrageRow(row, (DefaultTableModel) table.getModel())) {
                editorComponent = actionButton;
                return editorComponent;
            } else if (column == 4) {
                // Return an empty label for non-question rows
                return new JLabel("");
            }

            if (column == 5 && isFrageRow(row, (DefaultTableModel) table.getModel())) {
                difficultyCombo.setSelectedItem(value != null ? value.toString() : "MEDIUM");
                editorComponent = difficultyCombo;
                return difficultyCombo;
            }

            // For other cells, use default text editor
            editorComponent = defaultTextEditor.getTableCellEditorComponent(table, value, isSelected, row, column);
            return editorComponent;
        }

        private void deleteQuestionAtRow(JTable table, int row) {
            if (table == null || questionTable == null) {
                JOptionPane.showMessageDialog(MedatoninDB.this, "Error: Table not initialized", "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (row < 0 || row >= table.getRowCount()) {
                return;
            }
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            String questionNumber = String.valueOf(model.getValueAt(row, 0)); // Get the current question number
            int deletedQuestionNumber = Integer.parseInt(questionNumber);

            try {
                conn.setAutoCommit(false); // Start transaction

                // Delete the question from the database using the original question number
                if (questionDAO.deleteQuestion(currentCategory, currentSubcategory, deletedQuestionNumber,
                        selectedSimulationId)) {

                    // Renumber the remaining questions in both the UI and the database
                    renumberQuestionsInDatabaseAndUI(Integer.parseInt(questionNumber));

                    conn.commit(); // Commit transaction

                } else {
                    conn.rollback(); // Rollback if deletion fails
                    JOptionPane.showMessageDialog(MedatoninDB.this, "Failed to delete question", "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            } catch (SQLException e) {
                try {
                    conn.rollback(); // Rollback if an error occurs
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
                e.printStackTrace();
                JOptionPane.showMessageDialog(MedatoninDB.this, "Error deleting question: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            } finally {
                try {
                    conn.setAutoCommit(true); // Reset to auto-commit mode
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }

            // Clear pending delete state
            pendingDeleteQuestions.remove(new QuestionIdentifier(currentSubcategory, Integer.parseInt(questionNumber)));

            // Reload questions to ensure correct synchronization between UI and database
            reloadQuestionsAfterDeletion();
        }
    }

    // Renumber all questions after deletion
    private void renumberQuestionsInDatabaseAndUI(int deletedQuestionNumber) {
        DefaultTableModel model = categoryModels.get(currentCategory).get(currentSubcategory);
        int newQuestionNumber = deletedQuestionNumber; // Start renumbering from the deleted question's position

        for (int i = 0; i < model.getRowCount(); i++) {
            // Check if the row is a question row (Nr. column contains a numeric String)
            Object value = model.getValueAt(i, 0);
            if (value instanceof String && ((String) value).matches("\\d+")) {
                int oldQuestionNumber = Integer.parseInt((String) value);

                if (oldQuestionNumber > deletedQuestionNumber) {
                    // Update the Nr. column in the UI
                    model.setValueAt(String.valueOf(newQuestionNumber), i, 0);

                    // Update the question number in the database
                    try {
                        questionDAO.updateQuestionNumber(currentCategory, currentSubcategory, oldQuestionNumber,
                                newQuestionNumber);
                    } catch (SQLException e) {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(this, "Error renumbering question: " + e.getMessage(), "Error",
                                JOptionPane.ERROR_MESSAGE);
                    }

                    newQuestionNumber++; // Increment the new question number
                }
            }
        }
    }

    private void reloadQuestionsAfterDeletion() {
        // Clear the current table model to prevent duplications
        DefaultTableModel model = categoryModels.get(currentCategory).get(currentSubcategory);
        model.setRowCount(0);

        // Load the questions again from the database to ensure the UI matches the
        // database
        loadQuestionsFromDatabase(currentCategory, categoryModels.get(currentCategory), selectedSimulationId);

        // Refresh the table to show the updated data
        questionTable.revalidate();
        questionTable.repaint();
    }

    // Method to check if the row is a Frage row with boundary checks
    // Method to check if the row is a Frage (question) row
    private boolean isFrageRow(int row, DefaultTableModel model) {
        if (row < 0 || row >= model.getRowCount()) {
            return false;
        }
        Object value = model.getValueAt(row, 0);
        if (value == null) {
            return false;
        }
        String strValue = value.toString();
        return strValue.matches("\\d+"); // If it matches one or more digits, it's a question row
    }

    public static void main(String[] a) {
        try {
            new MedatoninDB();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadQuestionsFromDatabase(String category, Map<String, DefaultTableModel> subcategories,
            Integer simulationId) {
        debugLog("DB", "Loading questions for category: " + category);
        debugLog("DB", "Number of subcategories: " + subcategories.size());

        try {
            QuestionDAO questionDAO = new QuestionDAO(conn);
            OptionDAO optionDAO = new OptionDAO(conn);

            for (Map.Entry<String, DefaultTableModel> entry : subcategories.entrySet()) {
                String subcategoryName = entry.getKey();
                DefaultTableModel model = entry.getValue();
                debugLog("DB", "Processing subcategory: " + subcategoryName);

                int subcategoryId = getSubcategoryId(category, subcategoryName);
                debugLog("DB", "Subcategory ID: " + subcategoryId);

                if (subcategoryId != -1) {
                    List<QuestionDAO> questions;

                    questions = questionDAO.getQuestionsBySubcategoryAndSimulation(subcategoryId, simulationId);
                    debugLog("DB", "Number of questions loaded: " + questions.size());

                    model.setRowCount(0); // Clear existing rows
                    for (QuestionDAO question : questions) {
                        if ("Figuren".equals(subcategoryName)) {
                            // Handle "Figuren" subcategory differently
                            loadFigurenQuestionIntoModel(model, question, optionDAO);
                        } else {
                            // Handle other subcategories
                            loadStandardQuestionIntoModel(model, question, optionDAO);
                        }
                    }
                    debugLog("DB", "Added " + questions.size() + " questions with options to the model for "
                            + subcategoryName);
                } else {
                    debugLog("DB", "Failed to get subcategory ID for: " + subcategoryName);
                }
            }
        } catch (SQLException e) {
            debugLog("DB", "Error loading questions and options: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadStandardQuestionIntoModel(DefaultTableModel model, QuestionDAO question, OptionDAO optionDAO) {
        // Zahlenfolgen: Solution column must always show the correct two numbers from the correct OptionDAO
        String solutionText = question.getShapeData();
        if ("Zahlenfolgen".equals(currentSubcategory)) {
            try {
                List<OptionDAO> options = optionDAO.getOptionsForQuestion(question.getId());
                boolean found = false;
                for (OptionDAO option : options) {
                    if (option.isCorrect() && !"E".equals(option.getLabel())) {
                        solutionText = option.getText();
                        found = true;
                        break;
                    }
                }
                // If no correct option except E, fallback to first option with '/' in text
                if (!found) {
                    for (OptionDAO option : options) {
                        if (option.getText() != null && option.getText().contains("/")) {
                            solutionText = option.getText();
                            break;
                        }
                    }
                }
            } catch (SQLException e) {
                debugLog("DB", "Error loading Zahlenfolgen solution: " + e.getMessage());
            }
        }
        // Defensive: ensure difficulty is not null or empty
        String difficulty = question.getDifficulty();
        if (difficulty == null || difficulty.trim().isEmpty()) {
            difficulty = "MEDIUM";
        }
        // For Implikationen, convert semicolons to line breaks for display
        String questionText = question.getText();
        if ("Implikationen".equals(currentSubcategory) && questionText != null) {
            questionText = questionText.replace(";", "\n");
        }
        // Add question row
        model.addRow(new Object[] {
            String.valueOf(question.getQuestionNumber()),
            questionText,
            solutionText,
            false, // Checkbox state
            question.getFormat(),
            difficulty // Neue Spalte
        });

        // Load and add options
        try {
            List<OptionDAO> options = optionDAO.getOptionsForQuestion(question.getId());
            boolean isLang = "Lang".equals(question.getFormat());
            // Sort options
            Collections.sort(options, Comparator.comparing(OptionDAO::getLabel));
            for (OptionDAO option : options) {
                String label = option.getLabel();
                // Defensive: always use option.getText() for option text, preserving umlauts
                if (isLang && label.matches("\\d+\\.")) {
                    model.addRow(new Object[] { label, option.getText(), "", option.isCorrect(), "" });
                } else if (isLang && label.matches("[A-E]")) {
                    model.addRow(new Object[] { label, option.getText(), "", option.isCorrect(), "" });
                } else if (!isLang && label.matches("[A-E]")) {
                    model.addRow(new Object[] { label + ")", option.getText(), "", option.isCorrect(), "" });
                }
            }
        } catch (SQLException e) {
            debugLog("DB", "Error loading options for question ID " + question.getId() + ": "
                    + e.getMessage());
        }
    }

    private void loadFigurenQuestionIntoModel(DefaultTableModel model, QuestionDAO question, OptionDAO optionDAO) {
        try {
            WKTReader wktReader = new WKTReader();
            List<Geometry> dissectedPiecesList = new ArrayList<>();
            for (String pieceWKT : question.getDissectedPiecesData().split(";")) {
                dissectedPiecesList.add(wktReader.read(pieceWKT));
            }
            List<Geometry> assembledPiecesList = new ArrayList<>();
            for (String wkt : question.getAssembledPiecesData().split(";")) {
                if (!wkt.trim().isEmpty()) assembledPiecesList.add(wktReader.read(wkt));
            }
            FigurenGenerator.DissectedPieces dissectedPieces = new FigurenGenerator.DissectedPieces(
                dissectedPiecesList, dissectedPiecesList, assembledPiecesList);

            // Show dissected pieces as shapes (object) in the 'Text' column
            List<OptionDAO> options = optionDAO.getOptionsForQuestion(question.getId());
            OptionDAO correctOption = null;
            for (OptionDAO option : options) {
                if (option.isCorrect()) {
                    correctOption = option;
                    break;
                }
            }
            // Use the stored difficulty if available
            String difficulty = question.getDifficulty();
            if (difficulty == null || difficulty.trim().isEmpty()) {
                difficulty = "MEDIUM";
            }
            model.addRow(new Object[] {
                String.valueOf(question.getQuestionNumber()),
                dissectedPieces,
                correctOption,
                false,
                "Kurz",
                difficulty
            });

            // Option panel: only show options in grey, no solution
            FigurenOptionsData figurenOptionsData = new FigurenOptionsData(options, dissectedPieces);
            model.addRow(new Object[] { "", figurenOptionsData, "", false, "" });
        } catch (Exception e) {
            debugLog("DB", "Error loading Figuren question ID " + question.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Map<String, DefaultTableModel> loadSubcategoriesFromDatabase(int categoryId) {
        Map<String, DefaultTableModel> subcategories = new LinkedHashMap<>();
        String sql = "SELECT name FROM subcategories WHERE category_id = ? ORDER BY order_index";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, categoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String subcategoryName = rs.getString("name");
                    debugLog("DB", "Loaded subcategory: " + subcategoryName);
                    DefaultTableModel model = createTableModel();
                    subcategories.put(subcategoryName, model);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (subcategories.isEmpty()) {
            debugLog("DB", "Warning: No subcategories found for category ID: " + categoryId);
        }

        return subcategories;
    }

    private int getCategoryID(String categoryName) {
        String sql = "SELECT id FROM categories WHERE name = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, categoryName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        debugLog("DB", "Warning: No ID found for category: " + categoryName);
        return -1; // Return -1 if no ID is found
    }

    private int getSubcategoryId(String category, String subcategoryName) throws SQLException {
        String sql = "SELECT s.id FROM subcategories s JOIN categories c ON s.category_id = c.id WHERE s.name = ? AND c.name = ?";
        debugLog("DB", "Executing SQL: " + sql);
        debugLog("DB", "Category: " + category + ", Subcategory: " + subcategoryName);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, subcategoryName);
            stmt.setString(2, category);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    debugLog("DB", "Found subcategory ID: " + id);
                    return id;
                }
            }
        }
        debugLog("DB", "Subcategory not found");
        return -1; // Return -1 if subcategory not found
    }

    private void saveSubcategoryToDatabase(String category, String subcategoryName) {
        String sql = "INSERT INTO subcategories (name, category_id, order_index) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int categoryId = getCategoryID(category);
            int orderIndex = subcategoryOrder.get(category).size() - 1; // New subcategory is added at the end
            stmt.setString(1, subcategoryName);
            stmt.setInt(2, categoryId);
            stmt.setInt(3, orderIndex);
            stmt.executeUpdate();
            System.out.println("Saved new subcategory to database: " + subcategoryName);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateQuestion(int row, int column, Object data) {
        // Defensive: check row and column bounds before accessing table
        if (row < 0 || row >= questionTable.getRowCount() || column < 0 || column >= questionTable.getColumnCount()) {
            System.out.println("Error: updateQuestion called with invalid row/column: row=" + row + ", col=" + column);
            return;
        }
        Object questionNumberObj = questionTable.getValueAt(row, 0);
        int questionNumber;
        try {
            questionNumber = Integer.parseInt(questionNumberObj.toString());
        } catch (NumberFormatException e) {
            System.out.println("Error parsing question number in updateQuestion.");
            return;
        }
        try {
            switch (column) {
                case 1: // Question text
                    String textToSave = (String) data;
                    // For Implikationen, automatically insert semicolon between premises if user enters multiple lines
                    if ("Implikationen".equals(currentSubcategory) && textToSave != null) {
                        String[] lines = textToSave.split("\r?\n");
                        if (lines.length > 1) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < lines.length; i++) {
                                if (i > 0) sb.append(";");
                                sb.append(lines[i].trim());
                            }
                            textToSave = sb.toString();
                        } else {
                            textToSave = textToSave.replace("\r\n", ";").replace("\n", ";").replace("\r", ";");
                        }
                    }
                    questionDAO.updateQuestionText(currentCategory, currentSubcategory, questionNumber, textToSave,
                            selectedSimulationId);
                    // Do NOT update the table model here to avoid infinite recursion.
                    break;
                case 2: // Checkbox state (if applicable)
                    // Implement if needed
                    break;
                case 4: // Difficulty
                    questionDAO.updateQuestionDifficulty(
                            getSubcategoryId(currentCategory, currentSubcategory),
                            questionNumber,
                            (String) data,
                            selectedSimulationId);
                    break;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error updating question: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateOption(int row, int column, Object data) {
        int frageRow = getFrageRowForRow(row, questionTable);
        if (frageRow == -1) {
            System.out.println("Error: Invalid frageRow detected in updateOption.");
            return; // Exit if there's no valid frageRow
        }
        Object questionNumberObj = questionTable.getValueAt(frageRow, 0);
        int questionNumber;
        try {
            questionNumber = Integer.parseInt(questionNumberObj.toString());
        } catch (NumberFormatException e) {
            System.out.println("Error parsing question number in updateOption.");
            return;
        }
        String label = questionTable.getValueAt(row, 0).toString();
        // Remove any trailing ')' from the label
        if (label.endsWith(")")) {
            label = label.substring(0, label.length() - 1);
        }
        try {
            int questionId = questionDAO.getQuestionId(currentCategory, currentSubcategory, questionNumber,
                    selectedSimulationId);
            if (questionId == -1) {
                throw new SQLException("Question not found.");
            }
            switch (column) {
                case 1: // Option text
                    String optionText = (String) data;
                    optionDAO.updateOptionText(questionId, label, optionText, selectedSimulationId);
                    break;
                case 2: // Is correct
                    boolean isCorrect = (boolean) data;
                    optionDAO.updateOptionCorrectness(questionId, label, isCorrect, selectedSimulationId);
                    break;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error updating option: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    class ModernComboBox extends JComboBox<String> {
        public ModernComboBox() {
            super();
            setUI(new ModernComboBoxUI());
            setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            setBackground(Color.WHITE);
            setForeground(Color.BLACK);
            setFont(new Font("SansSerif", Font.BOLD, 16));
            setAlignmentX(CENTER_ALIGNMENT);
        }

        private class ModernComboBoxUI extends BasicComboBoxUI {

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                // Don't paint the background
            }

            @Override
            protected ComboPopup createPopup() {
                return new BasicComboPopup(comboBox) {
                    @Override
                    protected JScrollPane createScroller() {
                        JScrollPane scroller = new JScrollPane(list,
                                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                        scroller.getVerticalScrollBar().setUI(new ModernScrollBarUI());
                        return scroller;
                    }
                };
            }

            @Override
            protected JButton createArrowButton() {
                JButton arrowButton = new JButton();
                arrowButton.setIcon(new ArrowIcon()); // Set a custom icon for the arrow
                arrowButton.setBorder(BorderFactory.createEmptyBorder());
                arrowButton.setContentAreaFilled(false); // Remove background filling
                return arrowButton;
            }
        }

        private class ModernScrollBarUI extends BasicScrollBarUI {
            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }

            private JButton createZeroButton() {
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(0, 0));
                return button;
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
                if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
                    return;
                }

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new Color(180, 180, 180));
                g2.fillRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height, 10, 10);
                g2.dispose();
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
                // Don't paint the track
            }
        }
    }

    class ArrowIcon implements Icon {
        private int width = 10;
        private int height = 5;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(new Color(0, 0, 0)); // Farbe des Pfeils
            g2d.fillPolygon(new int[] { x, x + width / 2, x + width }, new int[] { y, y + height, y }, 3); // Zeichnet
                                                                                                           // den Pfeil
                                                                                                           // als
                                                                                                           // Dreieck
            g2d.dispose();
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }

    // Add this class inside MedatoninDB.java
    /**
     * Data holder for Figuren options and dissected pieces.
     */


    // Schwierigkeitsgrad-Konstanten
    /**
     * Enum for question difficulty with color and symbol.
     */

}
