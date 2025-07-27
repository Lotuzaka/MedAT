import dao.AllergyCardDAO;
import model.AllergyCardData;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Envelope;
// import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.awt.ShapeWriter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.plaf.basic.*;
import javax.swing.table.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.math.BigInteger;

// import java.io.BufferedWriter; // No longer needed
// import java.io.OutputStreamWriter; // No longer needed

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
    // [REMOVED] Unused createButtonPanel method

    // Utility to create a styled text field (e.g., for question count)
    private JTextField createStyledTextField(String text, int width, Color bgColor, Color fgColor) {
        JTextField field = new JTextField(text);
        field.setPreferredSize(new Dimension(width, 32)); // Slightly reduced height
        field.setForeground(fgColor);
        field.setFont(FONT_BASE);
        field.setBackground(bgColor);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CLR_BORDER, 1),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        field.setHorizontalAlignment(JTextField.CENTER);
        return field;
    }




    /* ------------------------------------------------------------- CONSTANTS */

    // Modern flat design color palette
    private static final Color CLR_PRIMARY = new Color(64, 64, 64);        // Dark gray for primary text
    private static final Color CLR_ACCENT = new Color(0, 122, 255);        // Modern blue accent
    private static final Color CLR_SURFACE = new Color(248, 249, 250);     // Light surface background
    private static final Color CLR_CARD = Color.WHITE;                     // Pure white for cards
    private static final Color CLR_BORDER = new Color(229, 231, 235);      // Subtle border color
    private static final Color CLR_HOVER = new Color(240, 242, 245);       // Hover state background
    
    // Legacy colors for compatibility
    private static final Color CLR_BLUE_MED = new Color(108, 117, 125);

    // Reduced spacing for slim design
    private static final int BUTTON_SPACING = 2;
    private static final int PANEL_PADDING = 8;
    
    // Modern font family with fallbacks
    private static final String FONT_FAMILY = getModernFontFamily();
    private static final Font FONT_BASE = new Font(FONT_FAMILY, Font.PLAIN, 13);
    private static final Font FONT_BOLD = new Font(FONT_FAMILY, Font.BOLD, 13);
    private static final Font FONT_LARGE = new Font(FONT_FAMILY, Font.BOLD, 15);
    
    // Helper method to determine the best available font
    private static String getModernFontFamily() {
        String[] preferredFonts = {"Inter", "SF Pro Display", "Segoe UI", "Roboto", "Helvetica Neue", "Arial", "SansSerif"};
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] availableFonts = ge.getAvailableFontFamilyNames();
        
        for (String preferred : preferredFonts) {
            for (String available : availableFonts) {
                if (available.equals(preferred)) {
                    return preferred;
                }
            }
        }
        return "SansSerif"; // Fallback
    }

    /* ------------------------------------------------------------- FIELDS */

    // DB
    private Connection conn;
    private QuestionDAO questionDAO;
    private OptionDAO optionDAO;
    private testSimulationDAO simulationDAO;
    private HikariDataSource ds; // Store datasource for proper lifecycle

    private String currentUsername; // To store the logged-in username
    // Store sub-databases for each category
    private Map<String, Map<String, DefaultTableModel>> categoryModels = new HashMap<>();
    private Map<String, List<String>> subcategoryOrder = new HashMap<>();

    // Template-based introduction content system
    private static IntroTemplateEngine templateEngine;
    
    static {
        try {
            templateEngine = new IntroTemplateEngine();
        } catch (Exception e) {
            System.err.println("Error initializing template engine: " + e.getMessage());
            templateEngine = null;
        }
    }
    // Helper method to get introduction content for a subcategory using template engine
    private static String getIntroContent(String name) {
        if (templateEngine == null) {
            debugLog("Template", LogLevel.ERROR, "Template engine not initialized");
            return null;
        }
        
        try {
            return templateEngine.generateIntroContent(name);
        } catch (Exception e) {
            debugLog("Template", LogLevel.ERROR, "Error generating intro content for " + name + ": " + e.getMessage());
            return null;
        }
    }

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
    private Color backgroundColor = CLR_SURFACE;
    private int buttonBorderRadius = 6; // Smaller radius for flatter look


    // Dropdown to select test simulations
    private JComboBox<String> simulationComboBox;
    private Map<String, Integer> simulationMap; // Maps simulation names to their IDs

    private Integer selectedSimulationId = null; // ID der ausgewählten Simulation

    // Notification system components
    private JPanel statusBar;
    private JLabel statusLabel;
    private JProgressBar statusProgressBar;
    private JPanel toastContainer;
    private Timer toastTimer;

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
        // Mark subcategory buttons as navigation buttons for proper hover effects
        subButton.putClientProperty("isNavigationButton", true);
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

    // ================================ NOTIFICATION SYSTEM ================================
    
    /**
     * Modern toast notification class with slide-in animation
     */
    private class ToastNotification extends JPanel {
        private static final int TOAST_WIDTH = 320;
        private static final int TOAST_HEIGHT = 60;
        private Timer slideTimer;
        private int targetY;
        private int currentY;
        private boolean isSliding = false;
        
        public ToastNotification(String message, Color backgroundColor, Color textColor) {
            setLayout(new BorderLayout());
            setBackground(backgroundColor);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 0, 0, 30), 1),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
            ));
            setPreferredSize(new Dimension(TOAST_WIDTH, TOAST_HEIGHT));
            setOpaque(true);
            
            // Create rounded corners effect
            setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            
            JLabel messageLabel = new JLabel(message);
            messageLabel.setForeground(textColor);
            messageLabel.setFont(FONT_BASE);
            add(messageLabel, BorderLayout.CENTER);
            
            // Add close button
            JButton closeBtn = new JButton("×");
            closeBtn.setForeground(textColor);
            closeBtn.setBackground(backgroundColor);
            closeBtn.setBorder(null);
            closeBtn.setFocusPainted(false);
            closeBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
            closeBtn.setPreferredSize(new Dimension(20, 20));
            closeBtn.addActionListener(e -> hideToast());
            add(closeBtn, BorderLayout.EAST);
        }
        
        public void showToast() {
            if (toastContainer == null) return;
            
            // Position toast initially off-screen (to the right)
            currentY = 10;
            targetY = 10;
            setBounds(toastContainer.getWidth(), currentY, TOAST_WIDTH, TOAST_HEIGHT);
            
            toastContainer.add(this);
            toastContainer.revalidate();
            toastContainer.repaint();
            
            // Animate slide in from right
            isSliding = true;
            slideTimer = new Timer(16, e -> {
                int targetX = toastContainer.getWidth() - TOAST_WIDTH - 20;
                int currentX = getX();
                
                if (currentX > targetX) {
                    int newX = Math.max(targetX, currentX - 15);
                    setBounds(newX, currentY, TOAST_WIDTH, TOAST_HEIGHT);
                    toastContainer.repaint();
                } else {
                    isSliding = false;
                    slideTimer.stop();
                    
                    // Auto-hide after 4 seconds
                    Timer hideTimer = new Timer(4000, hideEvent -> hideToast());
                    hideTimer.setRepeats(false);
                    hideTimer.start();
                }
            });
            slideTimer.start();
        }
        
        public void hideToast() {
            if (slideTimer != null && slideTimer.isRunning()) {
                slideTimer.stop();
            }
            
            // Animate slide out to the right
            Timer hideAnimation = new Timer(16, e -> {
                int currentX = getX();
                int targetX = toastContainer.getWidth();
                
                if (currentX < targetX) {
                    int newX = Math.min(targetX, currentX + 15);
                    setBounds(newX, currentY, TOAST_WIDTH, TOAST_HEIGHT);
                    toastContainer.repaint();
                } else {
                    toastContainer.remove(this);
                    toastContainer.revalidate();
                    toastContainer.repaint();
                    ((Timer) e.getSource()).stop();
                }
            });
            hideAnimation.start();
        }
    }
    
    /**
     * Initialize the notification system components
     */
    private void initializeNotificationSystem() {
        // Create status bar
        statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(CLR_CARD);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, CLR_BORDER),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));
        statusBar.setPreferredSize(new Dimension(0, 40));
        
        // Status label
        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(CLR_PRIMARY);
        statusLabel.setFont(FONT_BASE);
        statusBar.add(statusLabel, BorderLayout.WEST);
        
        // Progress bar
        statusProgressBar = new JProgressBar();
        statusProgressBar.setVisible(false);
        statusProgressBar.setPreferredSize(new Dimension(200, 20));
        statusProgressBar.setBackground(CLR_SURFACE);
        statusProgressBar.setForeground(CLR_ACCENT);
        statusProgressBar.setBorder(BorderFactory.createEmptyBorder());
        statusBar.add(statusProgressBar, BorderLayout.EAST);
        
        // Toast container (overlay)
        toastContainer = new JPanel();
        toastContainer.setLayout(null);
        toastContainer.setOpaque(false);
        
        // Add status bar to the main frame
        JPanel southPanel = (JPanel) ((BorderLayout) getContentPane().getLayout()).getLayoutComponent(BorderLayout.SOUTH);
        if (southPanel != null) {
            southPanel.add(statusBar, BorderLayout.SOUTH);
        } else {
            add(statusBar, BorderLayout.SOUTH);
        }
        
        // Add toast container as glass pane overlay
        JPanel glassPane = new JPanel();
        glassPane.setLayout(new BorderLayout());
        glassPane.setOpaque(false);
        glassPane.add(toastContainer, BorderLayout.CENTER);
        setGlassPane(glassPane);
        glassPane.setVisible(true);
    }
    
    /**
     * Show a toast notification
     */
    private void showToast(String message, NotificationType type) {
        Color bgColor, textColor;
        
        switch (type) {
            case SUCCESS:
                bgColor = new Color(34, 197, 94);
                textColor = Color.WHITE;
                break;
            case ERROR:
                bgColor = new Color(239, 68, 68);
                textColor = Color.WHITE;
                break;
            case INFO:
                bgColor = CLR_ACCENT;
                textColor = Color.WHITE;
                break;
            case WARNING:
                bgColor = new Color(245, 158, 11);
                textColor = Color.WHITE;
                break;
            default:
                bgColor = CLR_PRIMARY;
                textColor = Color.WHITE;
        }
        
        SwingUtilities.invokeLater(() -> {
            ToastNotification toast = new ToastNotification(message, bgColor, textColor);
            toast.showToast();
        });
    }
    
    /**
     * Update status bar with progress
     */
    private void updateStatus(String message, int progress) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);
            if (progress >= 0) {
                statusProgressBar.setValue(progress);
                statusProgressBar.setVisible(true);
            } else {
                statusProgressBar.setVisible(false);
            }
            statusBar.revalidate();
            statusBar.repaint();
        });
    }
    
    /**
     * Clear status and hide progress bar
     */
    private void clearStatus() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Ready");
            statusProgressBar.setVisible(false);
            statusProgressBar.setValue(0);
            statusBar.revalidate();
            statusBar.repaint();
        });
    }
    
    /**
     * Notification types for different toast styles
     */
    private enum NotificationType {
        SUCCESS, ERROR, INFO, WARNING
    }
    
    // ================================ END NOTIFICATION SYSTEM ================================

    public MedatoninDB() throws SQLException {
        // Debug: Print default charset at startup
        debugLog("Startup", "Default charset: " + java.nio.charset.Charset.defaultCharset());

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:mysql://localhost:3306/medatonindb?useUnicode=true&characterEncoding=UTF-8");
        cfg.setUsername("root");
        cfg.setPassword("288369Ma;");
        ds = new HikariDataSource(cfg); // Store as a field for later cleanup
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
                                // Instead of showing a blocking dialog, change the password field background to indicate error
                                passwordField.setBackground(new Color(255, 230, 230)); // Light red background
                                passwordField.setText(""); // Clear the password field
                                SwingUtilities.invokeLater(() -> {
                                    Timer timer = new Timer(2000, e -> passwordField.setBackground(Color.WHITE));
                                    timer.setRepeats(false);
                                    timer.start();
                                });
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

        // Apply modern flat design styling to all components
        UIManager.put("Button.font", FONT_BASE);
        UIManager.put("Table.font", FONT_BASE);
        UIManager.put("Label.font", FONT_BASE);
        UIManager.put("TableHeader.font", FONT_BOLD);
        UIManager.put("Panel.background", CLR_SURFACE);
        UIManager.put("ScrollPane.background", CLR_SURFACE);

        // Custom button UI to make buttons look flat
        printCategoryButton = createModernButton(currentCategory + " Print");
        printCategoryButton.setBackground(CLR_ACCENT);
        printCategoryButton.setForeground(Color.WHITE);
        JButton printAllButton = createModernButton("All Print");
        printAllButton.setBackground(new Color(175, 82, 222)); // Modern purple
        printAllButton.setForeground(Color.WHITE);
        // Mark print buttons as navigation buttons for proper hover effects
        printCategoryButton.putClientProperty("isNavigationButton", true);
        printAllButton.putClientProperty("isNavigationButton", true);

        // Set up modern color theme for the frame
        getContentPane().setBackground(backgroundColor);

        // Create the user info label at the top
        userTextField = new JLabel();
        userTextField.setPreferredSize(new Dimension(800, 40)); // Reduced height
        userTextField.setFont(FONT_LARGE);
        userTextField.setForeground(CLR_PRIMARY);
        userTextField.setText("User: " + currentUsername);

        JButton logoutButton = createModernButton("Logout");
        logoutButton.setBackground(new Color(255, 59, 48)); // Modern red
        logoutButton.setForeground(Color.WHITE);
        logoutButton.addActionListener(e -> logout());
        // Mark as navigation button for proper hover effects
        logoutButton.putClientProperty("isNavigationButton", true);

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
                        // Validate simulation exists in database before setting it
                        try {
                            if (simulationDAO.simulationExists(simulationId)) {
                                selectedSimulationId = simulationId;
                                debugLog("Simulation", "Selected simulation: " + selectedItem + " (ID: " + simulationId + ")");
                            } else {
                                debugLog("Simulation", LogLevel.ERROR, "Simulation does not exist in database: " + selectedItem + " (ID: " + simulationId + ")");
                                showToast("Selected simulation no longer exists in database", NotificationType.ERROR);
                                // Reload simulation options to refresh the list
                                loadSimulationOptions();
                                return;
                            }
                        } catch (SQLException ex) {
                            debugLog("Simulation", LogLevel.ERROR, "Error validating simulation: " + ex.getMessage());
                            selectedSimulationId = null;
                        }
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
                            simulationComboBox.setSelectedItem(newSimulation.getName());
                            selectedSimulationId = newSimulation.getId();
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                            showToast("Error creating new simulation", NotificationType.ERROR);
                        }
                    }
                }
                if (selectedSimulationId != null) {
                    loadQuestionsFromDatabase(currentCategory, categoryModels.get(currentCategory), selectedSimulationId);
                }
            }
        });

        // Create the top panel with a horizontal BoxLayout for consistent header layout
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
        topPanel.setBackground(CLR_CARD);
        topPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, CLR_BORDER),
            BorderFactory.createEmptyBorder(PANEL_PADDING, PANEL_PADDING * 2, PANEL_PADDING, PANEL_PADDING * 2)
        ));

        userTextField.setMinimumSize(new Dimension(80, 32));
        userTextField.setPreferredSize(new Dimension(120, 32));
        simulationComboBox.setMinimumSize(new Dimension(120, 32));
        simulationComboBox.setPreferredSize(new Dimension(160, 32));
        logoutButton.setMinimumSize(new Dimension(80, 32));
        logoutButton.setPreferredSize(new Dimension(120, 32));

        // Solution toggle button setup (created below)
        JButton solutionToggleButton = createModernButton("Solution");
        solutionToggleButton.setBackground(new Color(52, 199, 89)); // Modern green
        solutionToggleButton.setForeground(Color.WHITE);
        solutionToggleButton.setFont(FONT_BASE);
        solutionToggleButton.setFocusPainted(false);
        solutionToggleButton.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        solutionToggleButton.setMinimumSize(new Dimension(120, 32));
        solutionToggleButton.setPreferredSize(new Dimension(140, 30));
        // Mark as navigation button for proper hover effects
        solutionToggleButton.putClientProperty("isNavigationButton", true);
        solutionToggleButton.addActionListener(e -> {
            showSolutionColumn = !showSolutionColumn;
            updateSolutionColumnVisibility();
        });

        topPanel.add(Box.createHorizontalStrut(10));
        topPanel.add(userTextField);
        topPanel.add(Box.createHorizontalGlue());
        topPanel.add(simulationComboBox);
        topPanel.add(Box.createHorizontalGlue());
        topPanel.add(logoutButton);
        topPanel.add(Box.createHorizontalGlue());
        topPanel.add(solutionToggleButton);
        topPanel.add(Box.createHorizontalStrut(10));

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
        mainCategoryPanel.setLayout(new BoxLayout(mainCategoryPanel, BoxLayout.Y_AXIS));
        mainCategoryPanel.setBackground(CLR_CARD);
        mainCategoryPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, CLR_BORDER),
            BorderFactory.createEmptyBorder(PANEL_PADDING, PANEL_PADDING, PANEL_PADDING, PANEL_PADDING)
        ));
        mainCategoryPanel.setAlignmentY(Component.TOP_ALIGNMENT);

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

        // Set "Biologie" button to modern green by default
        bioButton.setBackground(new Color(52, 199, 89));
        bioButton.setForeground(Color.WHITE);

        // Mark all category buttons as navigation buttons
        ((JButton) bioButton).putClientProperty("isNavigationButton", true);
        ((JButton) chemButton).putClientProperty("isNavigationButton", true);
        ((JButton) physButton).putClientProperty("isNavigationButton", true);
        ((JButton) mathButton).putClientProperty("isNavigationButton", true);
        ((JButton) kffButton).putClientProperty("isNavigationButton", true);

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
        // Mark as navigation button for proper hover effects
        editToggleButton.putClientProperty("isNavigationButton", true);

        // Create a panel for the toggle buttons
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
                    editToggleButton.setBackground(new Color(255, 59, 48)); // Modern red
                    editToggleButton.setForeground(Color.WHITE);
                    setEditMode(true);
                    // Add any specific actions when edit mode is enabled
                } else {
                    editToggleButton.setIcon(penIcon); // Change to the pen icon
                    editToggleButton.setText("Arbeitsmodus"); // Remove text
                    setEditMode(false);
                    editToggleButton.setBackground(CLR_CARD);
                    editToggleButton.setForeground(CLR_PRIMARY);
                    loadSubcategories(currentCategory);
                    // Add any specific actions when edit mode is disabled
                }
            }
        });

        // Create the main panel to hold the category buttons and the toggle button
        JPanel westPanel = new JPanel(new BorderLayout());
        westPanel.setBackground(CLR_CARD);

        // Add some vertical spacing between the toggle button and the navigation buttons
        Box box = Box.createVerticalBox();
        box.add(toggleButtonPanel);
        box.add(Box.createVerticalStrut(PANEL_PADDING)); // Modern spacing

        // Create the subcategory panel on the right of the main categories
        subcategoryPanel = new JPanel();
        subcategoryPanel.setLayout(new BoxLayout(subcategoryPanel, BoxLayout.Y_AXIS));
        subcategoryPanel.setBackground(CLR_CARD);
        subcategoryPanel.setBorder(BorderFactory.createEmptyBorder(0, PANEL_PADDING, 0, 0));
        subcategoryPanel.setAlignmentY(Component.TOP_ALIGNMENT);

        // Add both panels to a single container
        JPanel navigationContainer = new JPanel();
        navigationContainer.setLayout(new BoxLayout(navigationContainer, BoxLayout.X_AXIS));
        navigationContainer.setBackground(CLR_CARD);

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
        mainContentPanel.setBackground(CLR_SURFACE);
        mainContentPanel.setBorder(BorderFactory.createEmptyBorder(PANEL_PADDING, PANEL_PADDING, PANEL_PADDING, PANEL_PADDING));
        
        JScrollPane scrollPane = new JScrollPane(mainContentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(CLR_SURFACE);
        scrollPane.getViewport().setBackground(CLR_SURFACE);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        // Load the initial category and its subcategories
        loadSubcategories(currentCategory);
        updateSolutionColumnVisibility();

        // Create buttons for adding and deleting questions
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, PANEL_PADDING, PANEL_PADDING));
        buttonPanel.setBackground(CLR_CARD);
        buttonPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, CLR_BORDER),
            BorderFactory.createEmptyBorder(PANEL_PADDING, 0, PANEL_PADDING, 0)
        ));

        // Add ActionListener for printing the current category
        printCategoryButton.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> {
                showToast("Starting document generation for " + currentCategory, NotificationType.INFO);
                updateStatus("Generating " + currentCategory + " document...", 10);
                
                // Run print operations in background thread
                new Thread(() -> {
                    try {
                        updateStatus("Processing questions...", 30);
                        printCategory(currentCategory);
                        
                        updateStatus("Generating solutions...", 60);
                        printCategorySolution(currentCategory);
                        
                        updateStatus("Document completed!", 100);
                        SwingUtilities.invokeLater(() -> {
                            showToast("Document created successfully for " + currentCategory, NotificationType.SUCCESS);
                            clearStatus();
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            showToast("Error creating document: " + ex.getMessage(), NotificationType.ERROR);
                            clearStatus();
                        });
                    }
                }).start();
            });
        });

        // Add ActionListener for printing all categories
        printAllButton.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> {
                showToast("Starting complete document generation for all categories", NotificationType.INFO);
                updateStatus("Generating complete document...", 5);
                
                // Run print operations in background thread
                new Thread(() -> {
                    try {
                        updateStatus("Processing all categories...", 20);
                        printAllCategories();
                        
                        updateStatus("Generating all solutions...", 70);
                        printAllCategoriesSolution();
                        
                        updateStatus("Complete document ready!", 100);
                        SwingUtilities.invokeLater(() -> {
                            showToast("Complete document created successfully", NotificationType.SUCCESS);
                            clearStatus();
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            showToast("Error creating complete document: " + ex.getMessage(), NotificationType.ERROR);
                            clearStatus();
                        });
                    }
                }).start();
            });
        });

        // Add buttons to the panel and frame (bottom panel)
        buttonPanel.add(printCategoryButton);
        buttonPanel.add(printAllButton);
        
        // Create combined south panel for buttons and status bar
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(buttonPanel, BorderLayout.NORTH);
        // Status bar will be added later in initializeNotificationSystem()
        
        add(southPanel, BorderLayout.SOUTH);
        // Setze die Ränder von allen relevanten Panels und ScrollPane auf leer
        mainContentPanel.setBorder(BorderFactory.createEmptyBorder()); // Kein Rand für das Hauptinhalt-Panel
        scrollPane.setBorder(BorderFactory.createEmptyBorder()); // Kein Rand für das ScrollPane
        topPanel.setBorder(BorderFactory.createEmptyBorder()); // Kein Rand für das obere Panel
        mainCategoryPanel.setBorder(BorderFactory.createEmptyBorder()); // Kein Rand für das Kategoriemenü-Panel
        westPanel.setBorder(BorderFactory.createEmptyBorder()); // Kein Rand für das westliche Panel
        buttonPanel.setBorder(BorderFactory.createEmptyBorder()); // Kein Rand für das Button-Panel

        // Initialize notification system
        initializeNotificationSystem();

        // Set frame visibility
        setVisible(true);
    }

    // Flag to control Solution column visibility
    private boolean showSolutionColumn = true;

    /**
     * Show/hide the Solution column in all tables (both overview and subcategory tables) based on showSolutionColumn flag.
     */
    private void updateSolutionColumnVisibility() {
        // Update all tables in the main content panel (overview panels)
        updateSolutionColumnVisibilityInMainContent();
        
        // Update individual subcategory tables (when viewing specific subcategories)
        for (Map<String, DefaultTableModel> subMap : categoryModels.values()) {
            for (String subcat : subMap.keySet()) {
                JTable table = getTableForSubcategory(subcat);
                if (table == null) continue;
                updateSolutionColumnForTable(table);
            }
        }
    }

    /**
     * Update solution column visibility for all tables in the main content panel (overview panels).
     */
    private void updateSolutionColumnVisibilityInMainContent() {
        for (Component comp : mainContentPanel.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel panel = (JPanel) comp;
                for (Component inner : panel.getComponents()) {
                    if (inner instanceof JScrollPane) {
                        JScrollPane scroll = (JScrollPane) inner;
                        JViewport viewport = scroll.getViewport();
                        Component view = viewport.getView();
                        if (view instanceof JTable) {
                            JTable table = (JTable) view;
                            updateSolutionColumnForTable(table);
                        }
                    }
                }
            }
        }
    }

    /**
     * Update solution column visibility for a specific table.
     */
    private void updateSolutionColumnForTable(JTable table) {
        TableColumnModel colModel = table.getColumnModel();

        // "Solution" is at model index 2
        int solutionViewIdx = table.convertColumnIndexToView(2);

        // If the column is not part of the view, add it back when showing
        if (solutionViewIdx == -1 && showSolutionColumn) {
            TableColumn col = new TableColumn(2);
            col.setHeaderValue("Solution");
            colModel.addColumn(col);

            int textViewIdx = table.convertColumnIndexToView(1);
            if (textViewIdx != -1) {
                colModel.moveColumn(colModel.getColumnCount() - 1, textViewIdx + 1);
                solutionViewIdx = textViewIdx + 1;
            } else {
                solutionViewIdx = colModel.getColumnCount() - 1;
            }
        }

        if (solutionViewIdx != -1) {
            TableColumn col = colModel.getColumn(solutionViewIdx);
            if (showSolutionColumn) {
                col.setMinWidth(120);
                col.setMaxWidth(150);
                col.setPreferredWidth(130);
                col.setWidth(130);
                col.setHeaderValue("Solution");

                int textViewIdx = table.convertColumnIndexToView(1);
                if (textViewIdx != -1 && solutionViewIdx != textViewIdx + 1) {
                    colModel.moveColumn(solutionViewIdx, textViewIdx + 1);
                    solutionViewIdx = textViewIdx + 1;
                }
            } else {
                col.setMinWidth(0);
                col.setMaxWidth(0);
                col.setPreferredWidth(0);
                col.setWidth(0);
                col.setHeaderValue("");
            }
        }

        table.revalidate();
        table.repaint();
    }

    /**
     * Helper to get the JTable for a subcategory name (if visible in UI).
     */
    private JTable getTableForSubcategory(String subcategory) {
        for (Component comp : mainContentPanel.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel panel = (JPanel) comp;
                for (Component inner : panel.getComponents()) {
                    if (inner instanceof JScrollPane) {
                        JScrollPane scroll = (JScrollPane) inner;
                        JViewport viewport = scroll.getViewport();
                        Component view = viewport.getView();
                        if (view instanceof JTable) {
                            JTable table = (JTable) view;
                            if (table.getModel() instanceof DefaultTableModel) {
                                DefaultTableModel model = (DefaultTableModel) table.getModel();
                                // Try to match subcategory by model reference
                                if (categoryModels.get(currentCategory) != null &&
                                    categoryModels.get(currentCategory).get(subcategory) == model) {
                                    return table;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private void logout() {
        // Close the main window
        this.dispose();

        // Close DB connection and datasource
        try {
            if (conn != null && !conn.isClosed()) conn.close();
            if (ds != null && !ds.isClosed()) ds.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

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
        
        // Set default selection to first available simulation
        if (!simulations.isEmpty()) {
            String firstSimName = simulations.get(0).getName();
            simulationComboBox.setSelectedItem(firstSimName);
            selectedSimulationId = simulationMap.get(firstSimName);
            debugLog("Simulation", "Auto-selected first simulation: " + firstSimName + " (ID: " + selectedSimulationId + ")");
        } else {
            debugLog("Simulation", "No simulations available, selectedSimulationId remains null");
        }
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
            // Mark as navigation button for proper hover effects
            addSubcategoryButton.putClientProperty("isNavigationButton", true);
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
                    showToast("Subcategory deleted successfully", NotificationType.SUCCESS);
                } else {
                    debugLog("Subcategory", "Failed to delete subcategory from database: " + subcategoryName);
                    showToast("Failed to delete subcategory from database", NotificationType.ERROR);
                }
            }
        } else {
            debugLog("Subcategory", "Attempted to delete last subcategory: " + subcategoryName);
            showToast("At least one subcategory must exist", NotificationType.WARNING);
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
        // Reset the border to modern styling
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CLR_BORDER, 1),
            BorderFactory.createEmptyBorder(7, 15, 7, 15)
        ));
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

    // Method to add a button to the panel with modern spacing
    private void addButtonWithSpacing(JPanel panel, JButton button) {
        panel.add(button);
        panel.add(Box.createVerticalStrut(BUTTON_SPACING)); // Modern minimal spacing
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
            // Mark as navigation button for proper hover effects
            addSubcategoryButton.putClientProperty("isNavigationButton", true);
            addSubcategoryButton.addActionListener(e -> addNewSubcategory(category));
            subcategoryPanel.add(addSubcategoryButton);
        }

        subcategoryPanel.revalidate();
        subcategoryPanel.repaint();

        displaySubcategoriesInMainContent(category);
        updateSolutionColumnVisibility();
    }

    // Method to get the corresponding background color of the category buttons
    private Color getCategoryButtonColor(String category) {
        if (category == null) {
            return CLR_CARD; // Default color when category is null
        }
        switch (category) {
            case "Biologie":
                return new Color(52, 199, 89); // Modern green
            case "Chemie":
                return new Color(255, 45, 85); // Modern red
            case "Physik":
                return new Color(0, 122, 255); // Modern blue
            case "Mathematik":
                return new Color(175, 82, 222); // Modern purple
            case "KFF":
                return new Color(255, 149, 0); // Modern orange
            default:
                return CLR_CARD; // Default white background
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
                showToast("Subcategory already exists", NotificationType.WARNING);
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
                        showToast("Subcategory deleted successfully", NotificationType.SUCCESS);
                    } else {
                        showToast("Failed to delete subcategory from database", NotificationType.ERROR);
                    }
                }
            } else {
                showToast("At least one subcategory must exist", NotificationType.WARNING);
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
            
            // Apply solution column visibility to this table
            updateSolutionColumnForTable(subcategoryTable);
            
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

            combinedPanel.add(tableScrollPane, BorderLayout.CENTER);

            // Do NOT add buttons in the overview - only show tables
            // Buttons should only appear when viewing individual subcategories
            
            mainContentPanel.add(combinedPanel);
            mainContentPanel.add(Box.createVerticalStrut(10));
        }

        subcategoryPanel.revalidate();
        subcategoryPanel.repaint();
    }

    // Method to add a new question to the current subcategory
    private void addNewQuestionToSubcategory() {
        if (currentCategory == null || currentSubcategory == null || currentSubcategory.isEmpty()) {
            showToast("Please select a category and subcategory first", NotificationType.WARNING);
            return;
        }

        // Validate simulation ID
        if (selectedSimulationId == null) {
            showToast("Please select a test simulation first", NotificationType.WARNING);
            return;
        }

        // Verify simulation exists in database
        try {
            if (!simulationDAO.simulationExists(selectedSimulationId)) {
                showToast("Selected simulation does not exist. Please select a valid simulation", NotificationType.ERROR);
                return;
            }
        } catch (SQLException e) {
            debugLog("DB", LogLevel.ERROR, "addNewQuestionToSubcategory", "Error validating simulation: " + e.getMessage());
            showToast("Error validating simulation: " + e.getMessage(), NotificationType.ERROR);
            return;
        }

        try {
            conn.setAutoCommit(false);
            int subcategoryId = getSubcategoryId(currentCategory, currentSubcategory);
            int questionNumber = questionDAO.getNextQuestionNumber(selectedSimulationId, subcategoryId);
            int questionId = questionDAO.insertEmptyQuestion(currentCategory, currentSubcategory, questionNumber,
                    selectedSimulationId);

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
            showToast("Error adding question and options: " + e.getMessage(), NotificationType.ERROR);
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

    // Method to create modern-looking buttons with flat design
    private JButton createModernButton(String text) {
        JButton button = new JButton(text) {
            private Color originalBackground = null;
            
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                
                Color bgColor = getBackground();
                
                // Smart hover effects for navigation buttons
                if (getModel().isPressed()) {
                    g2d.setColor(adjustBrightness(bgColor, 0.9f));
                } else if (getModel().isRollover()) {
                    Boolean isNavButton = (Boolean) getClientProperty("isNavigationButton");
                    if (isNavButton != null && isNavButton && !bgColor.equals(CLR_CARD)) {
                        // For colored category/subcategory buttons, make them lighter/pastel on hover
                        g2d.setColor(createPastelVersion(bgColor));
                    } else {
                        // For uncolored buttons, use standard hover
                        g2d.setColor(CLR_HOVER);
                    }
                } else {
                    g2d.setColor(bgColor);
                }
                
                // Very subtle rounded corners for modern flat look
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), buttonBorderRadius, buttonBorderRadius);
                
                // Clean text rendering
                g2d.setColor(getForeground());
                g2d.setFont(getFont());
                FontMetrics metrics = g2d.getFontMetrics(getFont());
                int x = (getWidth() - metrics.stringWidth(getText())) / 2;
                int y = ((getHeight() - metrics.getHeight()) / 2) + metrics.getAscent();
                g2d.drawString(getText(), x, y);
                g2d.dispose();
            }
            
            @Override
            protected void paintBorder(Graphics g) {
                // No border for flat design
            }
            
            @Override
            public boolean isFocusPainted() { return false; }
            
            @Override
            public boolean isContentAreaFilled() { return false; }
            
            // Override setBackground to track original color
            @Override
            public void setBackground(Color bg) {
                super.setBackground(bg);
                if (originalBackground == null || !bg.equals(CLR_CARD)) {
                    originalBackground = bg;
                }
            }
        };
        styleModernButton(button);
        return button;
    }

    // Centralized styling for modern flat buttons
    private void styleModernButton(JButton button) {
        button.setFocusPainted(false);
        button.setBackground(CLR_CARD);
        button.setForeground(CLR_PRIMARY);
        button.setFont(FONT_BASE);
        button.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16)); // Reduced padding
        button.setMargin(new Insets(8, 16, 8, 16));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Slimmer button dimensions
        Dimension buttonSize = new Dimension(160, 36); // Reduced height and width
        button.setPreferredSize(buttonSize);
        button.setMaximumSize(buttonSize);
        button.setMinimumSize(buttonSize);
        
        // Add subtle shadow effect through border
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CLR_BORDER, 1),
            BorderFactory.createEmptyBorder(7, 15, 7, 15)
        ));
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

    // Helper method to create pastel/lighter versions of colors for hover effects
    private Color createPastelVersion(Color color) {
        // Convert to HSB to adjust saturation and brightness
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        
        // Reduce saturation by 30% and increase brightness by 20% for pastel effect
        float newSaturation = hsb[1] * 0.7f;
        float newBrightness = Math.min(1.0f, hsb[2] * 1.2f);
        
        return Color.getHSBColor(hsb[0], newSaturation, newBrightness);
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
        updateSolutionColumnVisibility();
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

        // Remove legacy solution column logic. Solution column visibility is now only controlled by showSolutionColumn and updateSolutionColumnVisibility().

        // Set the selected category button to blue and reset others
        resetCategoryButtons();
        updateCategoryButtonColors(category);
    }

    // Update the selected category button colors
    private void updateCategoryButtonColors(String category) {
        resetCategoryButtons();
        if (category.equals("Biologie")) {
            bioButton.setBackground(new Color(52, 199, 89)); // Modern green
            bioButton.setForeground(Color.WHITE);
        } else if (category.equals("Chemie")) {
            chemButton.setBackground(new Color(255, 45, 85)); // Modern red
            chemButton.setForeground(Color.WHITE);
        } else if (category.equals("Physik")) {
            physButton.setBackground(new Color(0, 122, 255)); // Modern blue
            physButton.setForeground(Color.WHITE);
        } else if (category.equals("Mathematik")) {
            mathButton.setBackground(new Color(175, 82, 222)); // Modern purple
            mathButton.setForeground(Color.WHITE);
        } else if (category.equals("KFF")) {
            kffButton.setBackground(new Color(255, 149, 0)); // Modern orange
            kffButton.setForeground(Color.WHITE);
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
        
        // Apply solution column visibility to this table
        updateSolutionColumnForTable(questionTable);

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

        // Special handling for "Merkfähigkeiten" subcategory
        if ("Merkfähigkeiten".equals(currentSubcategory)) {
            // Create split pane with table on left and allergy cards on right
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
            splitPane.setLeftComponent(subScrollPane);
            
            // Create the allergy card grid panel on the right
            try {
                Class<?> allergyGridClass = Class.forName("ui.merkfaehigkeit.AllergyCardGridPanel");
                JPanel allergyCardGridPanel = (JPanel) allergyGridClass.getDeclaredConstructor().newInstance();
                JScrollPane allergyScrollPane = new JScrollPane(allergyCardGridPanel);
                allergyScrollPane.setPreferredSize(new Dimension(620, 380)); // Much smaller for compact cards
                allergyScrollPane.getViewport().setBackground(Color.WHITE); // Set viewport background to white
                allergyScrollPane.setBackground(Color.WHITE); // Set scroll pane background to white
                splitPane.setRightComponent(allergyScrollPane);
                
                // Set initial divider location and resize behavior for minimal right panel width
                splitPane.setResizeWeight(1.0); // Give maximum space to left panel initially
                splitPane.setDividerLocation(0.6); // Start with 60% for left, 40% for right
                splitPane.setOneTouchExpandable(true); // Allow quick expansion/collapse
                
                subcategoryContentPanel.add(splitPane, BorderLayout.CENTER);
                
                // Try to restore existing allergy card data from database
                try {
                    if (selectedSimulationId != null) {
                        AllergyCardDAO allergyDAO = new AllergyCardDAO(conn);
                        if (allergyDAO.hasDataForSession(selectedSimulationId)) {
                            List<AllergyCardData> existingData = allergyDAO.getBySessionId(selectedSimulationId);
                            if (!existingData.isEmpty()) {
                                // Load existing data into the allergy card panel
                                allergyCardGridPanel.getClass().getMethod("loadCards", List.class).invoke(allergyCardGridPanel, existingData);
                                debugLog("UI", "Restored " + existingData.size() + " allergy cards from database for simulation " + selectedSimulationId);
                            }
                        }
                    }
                } catch (Exception ex) {
                    debugLog("UI", LogLevel.WARN, "Could not restore allergy card data: " + ex.getMessage());
                }
                
                // Add "Generate ID" button specifically for Merkfähigkeiten
                JButton generateIdButton = createModernButton("Generate ID");
                generateIdButton.setBackground(new Color(255, 165, 0)); // Orange background
                generateIdButton.setForeground(Color.WHITE);
                generateIdButton.setFont(new Font("SansSerif", Font.BOLD, 14));
                generateIdButton.setFocusPainted(false);
                generateIdButton.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                generateIdButton.setPreferredSize(new Dimension(150, generateIdButton.getPreferredSize().height));
                
                generateIdButton.addActionListener(e -> {
                    try {
                        // Call generateRandomData method on the allergy card grid panel
                        allergyCardGridPanel.getClass().getMethod("generateRandomData").invoke(allergyCardGridPanel);
                        debugLog("UI", "Generated random data for all allergy cards");
                        
                        // Save allergy card data to database if we have a selected simulation
                        if (selectedSimulationId != null) {
                            @SuppressWarnings("unchecked")
                            List<AllergyCardData> cardData = (List<AllergyCardData>) allergyCardGridPanel.getClass().getMethod("getAllCards").invoke(allergyCardGridPanel);
                            AllergyCardDAO allergyDAO = new AllergyCardDAO(conn);
                            allergyDAO.insertAll(cardData, selectedSimulationId);
                            debugLog("UI", "Saved " + cardData.size() + " allergy cards to database for simulation " + selectedSimulationId);
                        } else {
                            debugLog("UI", LogLevel.WARN, "No simulation selected - allergy card data not saved to database");
                        }
                    } catch (Exception ex) {
                        debugLog("UI", LogLevel.ERROR, "Failed to generate/save random data: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                });
                
                debugLog("UI", "Added AllergyCardGridPanel for Merkfähigkeiten subcategory");
            } catch (Exception e) {
                debugLog("UI", LogLevel.ERROR, "Failed to load AllergyCardGridPanel: " + e.getMessage());
                // Fallback to normal table view
                subcategoryContentPanel.add(subScrollPane, BorderLayout.CENTER);
            }
        } else {
            // Add components to subcategoryContentPanel for normal subcategories
            subcategoryContentPanel.add(subScrollPane, BorderLayout.CENTER);
        }

        // For other categories and subcategories, only add "Add Question" button
        JButton addQuestionButton = createModernButton("Add Question");
        addQuestionButton.setBackground(new Color(52, 199, 89)); // Modern green background
        addQuestionButton.setForeground(Color.WHITE);
        addQuestionButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        addQuestionButton.setFocusPainted(false);
        addQuestionButton.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Button padding
        addQuestionButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Mark as navigation button for proper hover effects
        addQuestionButton.putClientProperty("isNavigationButton", true);

        // Adjust button width
        addQuestionButton.setPreferredSize(new Dimension(150, addQuestionButton.getPreferredSize().height));

        // Create buttons according to category and subcategory
        // Add "Add Question" button for ALL categories and subcategories
        buttonPanel.add(addQuestionButton);

        // Set up action listener for "Add Question" button
        addQuestionButton.addActionListener(e -> {
            addNewQuestionToSubcategory();
            if (questionTable != null && tableModel != null) {
                questionTable.scrollRectToVisible(questionTable.getCellRect(tableModel.getRowCount() - 1, 0, true));
            }
        });

        // Add Generate button for specific KFF subcategories  
        if ("KFF".equals(currentCategory) && ("Implikationen".equals(currentSubcategory) || "Zahlenfolgen".equals(currentSubcategory))) {
            JButton generateButton = createModernButton("Generate");
            generateButton.setBackground(new Color(127, 204, 165));
            generateButton.setForeground(Color.WHITE);
            generateButton.setFont(new Font("SansSerif", Font.BOLD, 14));
            generateButton.setFocusPainted(false);
            generateButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            generateButton.putClientProperty("isNavigationButton", true);

            JTextField questionCountField = createStyledTextField("0", 40, new Color(127, 204, 165), Color.WHITE);

            generateButton.addActionListener(e -> {
                try {
                    String input = questionCountField.getText().trim();
                    int questionCount;
                    try {
                        questionCount = Integer.parseInt(input);
                    } catch (NumberFormatException ex) {
                        showToast("Please enter a valid number", NotificationType.ERROR);
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

            buttonPanel.add(generateButton);
            buttonPanel.add(questionCountField);
        }

        // Add Generate button and number box for Merkfähigkeiten right after Add Question
        if ("Merkfähigkeiten".equals(currentSubcategory)) {
            try {
                // Find the allergy card grid panel from the split pane
                // JSplitPane splitPane = (JSplitPane) subcategoryContentPanel.getComponent(0); // Unused variable removed
                // JScrollPane allergyScrollPane = (JScrollPane) splitPane.getRightComponent(); // Unused variable removed
                // JPanel allergyCardGridPanel = (JPanel) allergyScrollPane.getViewport().getView(); // Unused variable removed

                // Create Generate button (green) with number box for Merkfähigkeiten
                JButton generateButton = createModernButton("Generate");
                generateButton.setBackground(new Color(52, 199, 89)); // Modern green background
                generateButton.setForeground(Color.WHITE);
                generateButton.setFont(new Font("SansSerif", Font.BOLD, 14));
                generateButton.setFocusPainted(false);
                generateButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                generateButton.setPreferredSize(new Dimension(150, generateButton.getPreferredSize().height));

                int buttonBorderRadius = 16;
                int numberBoxHeight = generateButton.getPreferredSize().height;
                JTextField questionCountField = new RoundedTextField("0", buttonBorderRadius, new Color(127,204,165), Color.WHITE, numberBoxHeight);
                questionCountField.setPreferredSize(new Dimension(60, numberBoxHeight));

                generateButton.addActionListener(e -> {
                    try {
                        String input = questionCountField.getText().trim();
                        int questionCount;
                        try {
                            questionCount = Integer.parseInt(input);
                        } catch (NumberFormatException ex) {
                            showToast("Bitte eine gültige Zahl eingeben", NotificationType.ERROR);
                            return;
                        }
                        debugLog("QuestionGen", "Starting Merkfähigkeiten generation for " + questionCount + " questions");
                        JSplitPane sp = (JSplitPane) subcategoryContentPanel.getComponent(0);
                        JScrollPane asp = (JScrollPane) sp.getRightComponent();
                        ui.merkfaehigkeit.AllergyCardGridPanel grid = (ui.merkfaehigkeit.AllergyCardGridPanel) asp.getViewport().getView();
                        java.util.List<model.AllergyCardData> cards = grid.getAllCards();
                        debugLog("QuestionGen", "Retrieved " + cards.size() + " allergy cards");
                        generator.MerkQuestionGenerator gen = new generator.MerkQuestionGenerator(
                                conn, currentCategory, currentSubcategory, selectedSimulationId, cards);
                        int beforeCount = tableModel.getRowCount();
                        try {
                            gen.execute(questionCount);
                        } catch (Exception genEx) {
                            showToast("Fehler bei der Generierung: " + genEx.getMessage(), NotificationType.ERROR);
                            debugLog("QuestionGen", "Merkfähigkeiten generation failed: " + genEx.getMessage());
                            return;
                        }
                        debugLog("QuestionGen", "Successfully executed MerkQuestionGenerator");
                        loadQuestionsFromDatabase(currentCategory, categoryModels.get(currentCategory),
                                selectedSimulationId);
                        tableModel.fireTableDataChanged();
                        debugLog("QuestionGen", "Refreshed table model");
                        int afterCount = tableModel.getRowCount();
                        if (questionTable != null && tableModel != null && afterCount > beforeCount) {
                            questionTable.scrollRectToVisible(
                                    questionTable.getCellRect(afterCount - 1, 0, true));
                        }
                        // Force UI update
                        questionTable.revalidate();
                        questionTable.repaint();
                    } catch (Exception ex) {
                        showToast("Fehler: " + ex.getMessage(), NotificationType.ERROR);
                        debugLog("QuestionGen", "Merkfähigkeiten generation failed: " + ex.getMessage());
                    }
                });

                buttonPanel.add(generateButton);
                buttonPanel.add(questionCountField);

                // Add Test button for generating all question types and variations
                JButton testButton = createModernButton("Test");
                testButton.setBackground(new Color(255, 193, 7)); // Yellow background 
                testButton.setForeground(Color.BLACK);
                testButton.setFont(new Font("SansSerif", Font.BOLD, 14));
                testButton.setFocusPainted(false);
                testButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                testButton.setPreferredSize(new Dimension(80, testButton.getPreferredSize().height));

                testButton.addActionListener(e -> {
                    try {
                        debugLog("QuestionGen", "Starting comprehensive Merkfähigkeiten test generation");
                        JSplitPane sp = (JSplitPane) subcategoryContentPanel.getComponent(0);
                        JScrollPane asp = (JScrollPane) sp.getRightComponent();
                        ui.merkfaehigkeit.AllergyCardGridPanel grid = (ui.merkfaehigkeit.AllergyCardGridPanel) asp.getViewport().getView();
                        java.util.List<model.AllergyCardData> cards = grid.getAllCards();
                        debugLog("QuestionGen", "Retrieved " + cards.size() + " allergy cards for test");
                        
                        if (cards.isEmpty() || cards.stream().anyMatch(card -> card.name() == null || card.name().trim().isEmpty())) {
                            showToast("Bitte zuerst Allergiekarten generieren (Generate ID drücken)", NotificationType.WARNING);
                            return;
                        }
                        
                        generator.MerkQuestionGenerator gen = new generator.MerkQuestionGenerator(
                                conn, currentCategory, currentSubcategory, selectedSimulationId, cards);
                        int beforeCount = tableModel.getRowCount();
                        try {
                            gen.executeAllTypes(); // Generate comprehensive test set with all variations
                        } catch (Exception genEx) {
                            showToast("Fehler bei der Test-Generierung: " + genEx.getMessage(), NotificationType.ERROR);
                            debugLog("QuestionGen", "Merkfähigkeiten test generation failed: " + genEx.getMessage());
                            return;
                        }
                        debugLog("QuestionGen", "Successfully executed comprehensive MerkQuestionGenerator test");
                        loadQuestionsFromDatabase(currentCategory, categoryModels.get(currentCategory),
                                selectedSimulationId);
                        tableModel.fireTableDataChanged();
                        debugLog("QuestionGen", "Refreshed table model");
                        int afterCount = tableModel.getRowCount();
                        int actualGenerated = (afterCount - beforeCount) / 6; // Each question has 5 option rows + 1 question row = 6 rows total
                        if (questionTable != null && tableModel != null && afterCount > beforeCount) {
                            questionTable.scrollRectToVisible(
                                    questionTable.getCellRect(afterCount - 1, 0, true));
                        }
                        // Force UI update
                        questionTable.revalidate();
                        questionTable.repaint();
                        
                        showToast("Test abgeschlossen! Alle Fragetypen und Varianten wurden generiert (" + actualGenerated + " Fragen total)", NotificationType.SUCCESS);
                    } catch (Exception ex) {
                        showToast("Fehler: " + ex.getMessage(), NotificationType.ERROR);
                        debugLog("QuestionGen", "Merkfähigkeiten test generation failed: " + ex.getMessage());
                    }
                });

                buttonPanel.add(testButton);

            } catch (Exception e) {
                debugLog("UI", LogLevel.ERROR, "Failed to add Generate button: " + e.getMessage());
            }
        }

        // Add Generate button and number box for Figuren subcategory
        if ("Figuren".equals(currentSubcategory)) {
            JButton generateButton = createModernButton("Generate");
            generateButton.setBackground(new Color(52, 199, 89));
            generateButton.setForeground(Color.WHITE);
            generateButton.setFont(new Font("SansSerif", Font.BOLD, 14));
            generateButton.setFocusPainted(false);
            generateButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            generateButton.putClientProperty("isNavigationButton", true);

            JTextField questionCountField = createStyledTextField("0", 40, new Color(52, 199, 89), Color.WHITE);

            generateButton.addActionListener(e -> {
                try {
                    String input = questionCountField.getText().trim();
                    int questionCount;
                    try {
                        questionCount = Integer.parseInt(input);
                    } catch (NumberFormatException ex) {
                        showToast("Please enter a valid number", NotificationType.ERROR);
                        return;
                    }
                    for (int i = 0; i < questionCount; i++) {
                        addNewFigurenQuestion();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showToast("Fehler bei der Generierung: " + ex.getMessage(), NotificationType.ERROR);
                }
            });

            buttonPanel.add(generateButton);
            buttonPanel.add(questionCountField);
        }

        // Add Generate button and number box for Wortflüssigkeit subcategory
        if ("Wortflüssigkeit".equals(currentSubcategory)) {
            JButton generateButton = createModernButton("Generate");
            generateButton.setBackground(new Color(52, 199, 89));
            generateButton.setForeground(Color.WHITE);
            generateButton.setFont(new Font("SansSerif", Font.BOLD, 14));
            generateButton.setFocusPainted(false);
            generateButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            generateButton.putClientProperty("isNavigationButton", true);

            JTextField questionCountField = createStyledTextField("0", 40, new Color(52, 199, 89), Color.WHITE);

            generateButton.addActionListener(e -> {
                try {
                    String input = questionCountField.getText().trim();
                    int questionCount;
                    try {
                        questionCount = Integer.parseInt(input);
                    } catch (NumberFormatException ex) {
                        showToast("Please enter a valid number", NotificationType.ERROR);
                        return;
                    }
                    WortfluessigkeitGenerator generator = new WortfluessigkeitGenerator(conn, currentCategory, currentSubcategory, selectedSimulationId);
                    generator.execute(questionCount);
                    loadQuestionsFromDatabase(currentCategory, categoryModels.get(currentCategory), selectedSimulationId);
                    switchSubcategory(currentCategory, currentSubcategory);
                } catch (SQLException | IOException ex) {
                    ex.printStackTrace();
                    showToast("Fehler bei der Generierung: " + ex.getMessage(), NotificationType.ERROR);
                }
            });

            buttonPanel.add(generateButton);
            buttonPanel.add(questionCountField);
        }

        // Buttons for deleting questions
        JButton deleteMarkedButton = createModernButton("Delete Marked");
        deleteMarkedButton.setBackground(new Color(255, 59, 48)); // Modern red
        deleteMarkedButton.setForeground(Color.WHITE);
        deleteMarkedButton.addActionListener(e -> deleteSelectedQuestions());

        JButton deleteAllButton = createModernButton("Delete All");
        deleteAllButton.setBackground(new Color(255, 59, 48)); // Modern red
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
        
        // Add "Generate ID" button for Merkfähigkeiten at the far right
        if ("Merkfähigkeiten".equals(currentSubcategory)) {
            try {
                // Find the allergy card grid panel from the split pane
                JSplitPane splitPane = (JSplitPane) subcategoryContentPanel.getComponent(0);
                JScrollPane allergyScrollPane = (JScrollPane) splitPane.getRightComponent();
                JPanel allergyCardGridPanel = (JPanel) allergyScrollPane.getViewport().getView();

                JButton generateIdButton = createModernButton("Generate ID");
                generateIdButton.setBackground(new Color(255, 165, 0)); // Orange background
                generateIdButton.setForeground(Color.WHITE);
                generateIdButton.setFont(new Font("SansSerif", Font.BOLD, 14));
                generateIdButton.setFocusPainted(false);
                generateIdButton.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                generateIdButton.setPreferredSize(new Dimension(150, generateIdButton.getPreferredSize().height));

                generateIdButton.addActionListener(e -> {
                    try {
                        // Generate random data for allergy cards
                        allergyCardGridPanel.getClass().getMethod("generateRandomData").invoke(allergyCardGridPanel);
                        debugLog("UI", "Generated random data for all allergy cards");
                        // Save allergy card data to database if we have a selected simulation
                        if (selectedSimulationId != null) {
                            @SuppressWarnings("unchecked")
                            List<AllergyCardData> cardData = (List<AllergyCardData>) allergyCardGridPanel.getClass().getMethod("getAllCards").invoke(allergyCardGridPanel);
                            AllergyCardDAO allergyDAO = new AllergyCardDAO(conn);
                            allergyDAO.deleteBySessionId(selectedSimulationId); // Remove old cards for session
                            allergyDAO.insertAll(cardData, selectedSimulationId);
                            debugLog("UI", "Saved " + cardData.size() + " allergy cards to database for simulation " + selectedSimulationId);
                        } else {
                            debugLog("UI", LogLevel.WARN, "No simulation selected - allergy card data not saved to database");
                        }
                    } catch (Exception ex) {
                        debugLog("UI", LogLevel.ERROR, "Failed to generate/save random data: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                });

                buttonPanel.add(generateIdButton);

                // Always reload allergy cards from DB when switching to Merkfähigkeiten
                if (selectedSimulationId != null) {
                    try {
                        AllergyCardDAO allergyDAO = new AllergyCardDAO(conn);
                        if (allergyDAO.hasDataForSession(selectedSimulationId)) {
                            List<AllergyCardData> existingData = allergyDAO.getBySessionId(selectedSimulationId);
                            if (!existingData.isEmpty()) {
                                allergyCardGridPanel.getClass().getMethod("loadCards", List.class).invoke(allergyCardGridPanel, existingData);
                                debugLog("UI", "Restored " + existingData.size() + " allergy cards from database for simulation " + selectedSimulationId);
                            }
                        }
                    } catch (Exception ex) {
                        debugLog("UI", LogLevel.WARN, "Could not restore allergy card data: " + ex.getMessage());
                    }
                }
            } catch (Exception e) {
                debugLog("UI", LogLevel.ERROR, "Failed to add Generate ID/Generate button: " + e.getMessage());
            }
        }

        // Add buttonPanel to subcategoryContentPanel
        subcategoryContentPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Add subcategoryContentPanel to mainContentPanel
        mainContentPanel.add(subcategoryContentPanel, BorderLayout.CENTER);

        // Ensure safe UI updates
        mainContentPanel.revalidate();
        mainContentPanel.repaint();

        // Apply visibility setting for the Solution column now that the table
        // is part of the component hierarchy
        updateSolutionColumnVisibility();

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
                "",
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
                showToast("Transaction rolled back due to an error", NotificationType.ERROR);
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
                showToast("Error during rollback: " + rollbackEx.getMessage(), NotificationType.ERROR);
            }
            e.printStackTrace();
            showToast("Error saving Figuren question: " + e.getMessage(), NotificationType.ERROR);
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
                    showToast("Failed to delete question", NotificationType.ERROR);
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
            showToast("Error deleting questions: " + e.getMessage(), NotificationType.ERROR);
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
            showToast("Error deleting all questions: " + e.getMessage(), NotificationType.ERROR);
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
            selectedSubcategoryButton.setBackground(CLR_CARD); // Reset to white instead of gray
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
        for (JButton btn : Arrays.asList(bioButton, chemButton, physButton, mathButton, kffButton)) {
            btn.setBackground(CLR_CARD);
            btn.setForeground(CLR_PRIMARY);
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

    // Helper method to configure page margins for Word documents
    private void setupPageMargins(XWPFDocument document) {
        try {
            // Get or create the section properties
            CTSectPr sectPr;
            if (document.getDocument().getBody().getSectPr() != null) {
                sectPr = document.getDocument().getBody().getSectPr();
            } else {
                sectPr = document.getDocument().getBody().addNewSectPr();
            }
            
            // Set page margins
            CTPageMar pageMar;
            if (sectPr.getPgMar() != null) {
                pageMar = sectPr.getPgMar();
            } else {
                pageMar = sectPr.addNewPgMar();
            }
            
            // Set margins in TWIPs (1 inch = 1440 TWIPs, 1 cm ≈ 567 TWIPs)
            // Top: 1.5cm = 1.5 * 567 = 850 TWIPs
            // Bottom: 1.5cm = 1.5 * 567 = 850 TWIPs  
            // Left: 2cm = 2 * 567 = 1134 TWIPs
            // Right: 2cm = 2 * 567 = 1134 TWIPs
            pageMar.setTop(java.math.BigInteger.valueOf(850));
            pageMar.setBottom(java.math.BigInteger.valueOf(850));
            pageMar.setLeft(java.math.BigInteger.valueOf(1134));
            pageMar.setRight(java.math.BigInteger.valueOf(1134));
            
            debugLog("Print", LogLevel.INFO, "Set page margins: Top/Bottom=1.5cm, Left/Right=2cm");
        } catch (Exception e) {
            debugLog("Print", LogLevel.ERROR, "Error setting page margins: " + e.getMessage());
        }
    }

    // Method to print the current category to a Word document
    private void printCategory(String category) {
        Map<String, DefaultTableModel> subcategories = categoryModels.get(category);
        if (subcategories == null) {
            showToast("No data available for category: " + category, NotificationType.ERROR);
            return;
        }

        try {
            // Check if docx4j is available at runtime
            Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage");
            
            docx.Docx4jPrinter printer = new docx.Docx4jPrinter();

            // Create document manually to avoid import issues
            java.lang.reflect.Method createMethod = Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage")
                .getMethod("createPackage");
            Object pkg = createMethod.invoke(null);
            
            // Process each subcategory
            java.util.List<String> subcatList = subcategoryOrder.get(category);
            int totalSubcategories = subcatList.size();
            
            for (int i = 0; i < subcatList.size(); i++) {
                String subcategory = subcatList.get(i);
                DefaultTableModel model = subcategories.get(subcategory);
                if (model == null || model.getRowCount() == 0) {
                    continue; // Skip empty subcategories
                }

                // Update progress based on current subcategory
                int progress = 30 + (i * 30 / totalSubcategories);
                updateStatus("Processing " + subcategory + "...", progress);

                // Insert introduction page for this specific subcategory (Untertest)
                String introContent = getIntroContent(subcategory);
                if (introContent != null) {
                    java.lang.reflect.Method addIntroMethod = printer.getClass()
                        .getMethod("addIntroductionPage", Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage"), String.class);
                    addIntroMethod.invoke(printer, pkg, introContent);
                }

                // Add questions using reflection to avoid import issues
                java.lang.reflect.Method addQuestionsMethod = printer.getClass()
                    .getMethod("addQuestions", Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage"), DefaultTableModel.class);
                addQuestionsMethod.invoke(printer, pkg, model);

                // Add stop sign page using reflection
                java.lang.reflect.Method addStopSignMethod = printer.getClass()
                    .getMethod("addStopSignPage", Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage"));
                addStopSignMethod.invoke(printer, pkg);

                // Remove potential trailing page break from questions
                // (stop sign page will handle page separation)
            }

            // Save the document using reflection with conflict resolution
            String baseFileName = category + ".docx";
            File outputFile = new File(baseFileName);
            
            // If file is in use, try alternative names
            int counter = 1;
            while (!canWriteToFile(outputFile)) {
                String fileName = category + "_" + counter + ".docx";
                outputFile = new File(fileName);
                counter++;
                
                // Prevent infinite loop
                if (counter > 100) {
                    throw new RuntimeException("Unable to find available filename after 100 attempts");
                }
            }
            
            java.lang.reflect.Method saveMethod = pkg.getClass().getMethod("save", java.io.File.class);
            saveMethod.invoke(pkg, outputFile);
            
            String message = "Document saved: " + outputFile.getName();
            if (!outputFile.getName().equals(baseFileName)) {
                message += "\n\n(Original filename was in use, saved with alternative name)";
            }
            showToast(message, NotificationType.SUCCESS);
        } catch (ClassNotFoundException e) {
            // docx4j is not available, show a helpful message
            showToast("docx4j library is not available. Document printing functionality requires docx4j dependencies", NotificationType.WARNING);
            
            // Fallback: log the data to console
            System.out.println("=== CATEGORY: " + category + " ===");
            for (String subcategory : subcategoryOrder.get(category)) {
                DefaultTableModel model = subcategories.get(subcategory);
                if (model == null || model.getRowCount() == 0) {
                    continue;
                }
                System.out.println("--- Subcategory: " + subcategory + " ---");
                for (int i = 0; i < model.getRowCount(); i++) {
                    Object questionText = model.getValueAt(i, 1);
                    if (questionText != null) {
                        System.out.println("Question " + (i + 1) + ": " + questionText);
                    }
                }
            }
            System.out.println("=== END CATEGORY ===");
        } catch (Exception e) {
            e.printStackTrace();
            showToast("Error saving document: " + e.getMessage(), NotificationType.ERROR);
        }
    }

    // Method to print the solution document for the current category
    private void printCategorySolution(String category) {
        Map<String, DefaultTableModel> subcategories = categoryModels.get(category);
        if (subcategories == null) {
            showToast("No data available for category: " + category, NotificationType.ERROR);
            return;
        }

        try {
            // Check if docx4j is available at runtime
            Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage");
            
            docx.Docx4jPrinter printer = new docx.Docx4jPrinter();

            // Create document manually to avoid import issues
            java.lang.reflect.Method createMethod = Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage")
                .getMethod("createPackage");
            Object pkg = createMethod.invoke(null);
            
            // Process each subcategory
            java.util.List<String> subcatList = subcategoryOrder.get(category);
            for (int i = 0; i < subcatList.size(); i++) {
                String subcategory = subcatList.get(i);
                DefaultTableModel model = subcategories.get(subcategory);
                if (model == null || model.getRowCount() == 0) {
                    continue; // Skip empty subcategories
                }

                // Insert introduction page for this specific subcategory (Untertest)
                String introContent = getIntroContent(subcategory);
                if (introContent != null) {
                    java.lang.reflect.Method addIntroMethod = printer.getClass()
                        .getMethod("addIntroductionPage", Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage"), String.class);
                    addIntroMethod.invoke(printer, pkg, introContent);
                }

                // Add solutions using reflection to avoid import issues
                java.lang.reflect.Method addSolutionsMethod = printer.getClass()
                    .getMethod("addQuestionsSolution", Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage"), DefaultTableModel.class);
                addSolutionsMethod.invoke(printer, pkg, model);

                // Add stop sign page using reflection
                java.lang.reflect.Method addStopSignMethod = printer.getClass()
                    .getMethod("addStopSignPage", Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage"));
                addStopSignMethod.invoke(printer, pkg);

                // Stop sign already starts a new page; no extra break needed
            }

            // Save the document using reflection with conflict resolution
            String baseFileName = category + "_Solutions.docx";
            File outputFile = new File(baseFileName);
            
            // If file is in use, try alternative names
            int counter = 1;
            while (!canWriteToFile(outputFile)) {
                String fileName = category + "_Solutions_" + counter + ".docx";
                outputFile = new File(fileName);
                counter++;
                
                // Prevent infinite loop
                if (counter > 100) {
                    throw new RuntimeException("Unable to find available filename after 100 attempts");
                }
            }
            
            java.lang.reflect.Method saveMethod = pkg.getClass().getMethod("save", java.io.File.class);
            saveMethod.invoke(pkg, outputFile);
            
            String message = "Solution document saved: " + outputFile.getName();
            if (!outputFile.getName().equals(baseFileName)) {
                message += "\n\n(Original filename was in use, saved with alternative name)";
            }
            showToast(message, NotificationType.SUCCESS);
        } catch (ClassNotFoundException e) {
            // docx4j is not available, show a helpful message
            showToast("docx4j library is not available. Document printing functionality requires docx4j dependencies", NotificationType.WARNING);
            
            // Fallback: log the solution data to console
            System.out.println("=== SOLUTIONS FOR CATEGORY: " + category + " ===");
            for (String subcategory : subcategoryOrder.get(category)) {
                DefaultTableModel model = subcategories.get(subcategory);
                if (model == null || model.getRowCount() == 0) {
                    continue;
                }
                System.out.println("--- Subcategory: " + subcategory + " ---");
                for (int i = 0; i < model.getRowCount(); i++) {
                    Object questionText = model.getValueAt(i, 1);
                    Object solutionText = model.getValueAt(i, 2);
                    if (questionText != null) {
                        System.out.println("Question " + (i + 1) + ": " + questionText);
                        if (solutionText != null) {
                            System.out.println("Solution " + (i + 1) + ": " + solutionText);
                        }
                    }
                }
            }
            System.out.println("=== END SOLUTIONS ===");
        } catch (Exception e) {
            e.printStackTrace();
            showToast("Error saving solution document: " + e.getMessage(), NotificationType.ERROR);
        }
    }

    // Method to print all categories and their subcategories to a single Word
    // document
    private void printAllCategories() {
        try {
            // Check if docx4j is available at runtime
            Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage");
            
            docx.Docx4jPrinter printer = new docx.Docx4jPrinter();
            
            // Create document manually to avoid import issues
            java.lang.reflect.Method createMethod = Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage")
                .getMethod("createPackage");
            Object pkg = createMethod.invoke(null);

            // Calculate total subcategories for progress tracking
            int totalSubcategories = 0;
            for (String category : categoryModels.keySet()) {
                totalSubcategories += subcategoryOrder.get(category).size();
            }
            
            int processedCount = 0;
            for (String category : categoryModels.keySet()) {
                Map<String, DefaultTableModel> subcats = categoryModels.get(category);
                for (String subcat : subcategoryOrder.get(category)) {
                    // Update progress
                    int progress = 20 + (processedCount * 50 / totalSubcategories);
                    updateStatus("Processing " + subcat + " (" + category + ")...", progress);
                    processedCount++;
                    
                    String introContent = getIntroContent(subcat);
                    if (introContent != null) {
                        java.lang.reflect.Method addIntroMethod = printer.getClass()
                            .getMethod("addIntroductionPage", Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage"), String.class);
                        addIntroMethod.invoke(printer, pkg, introContent);
                    }

                    DefaultTableModel model = subcats.get(subcat);
                    if (model != null && model.getRowCount() > 0) {
                        // Add questions using reflection
                        java.lang.reflect.Method addQuestionsMethod = printer.getClass()
                            .getMethod("addQuestions", Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage"), DefaultTableModel.class);
                        addQuestionsMethod.invoke(printer, pkg, model);
                        
                        // Add page break using reflection
                        java.lang.reflect.Method addPageBreakMethod = printer.getClass()
                            .getMethod("addPageBreak", Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage"));
                        addPageBreakMethod.invoke(printer, pkg);
                    }
                }
            }

            // Save the document using reflection with conflict resolution
            String baseFileName = "All_Categories.docx";
            File outputFile = new File(baseFileName);
            
            // If file is in use, try alternative names
            int counter = 1;
            while (!canWriteToFile(outputFile)) {
                String fileName = "All_Categories_" + counter + ".docx";
                outputFile = new File(fileName);
                counter++;
                
                // Prevent infinite loop
                if (counter > 100) {
                    throw new RuntimeException("Unable to find available filename after 100 attempts");
                }
            }
            
            java.lang.reflect.Method saveMethod = pkg.getClass().getMethod("save", java.io.File.class);
            saveMethod.invoke(pkg, outputFile);

            String message = "Document saved: " + outputFile.getName();
            if (!outputFile.getName().equals(baseFileName)) {
                message += "\n\n(Original filename was in use, saved with alternative name)";
            }
            showToast(message, NotificationType.SUCCESS);
        } catch (ClassNotFoundException e) {
            // docx4j is not available, show a helpful message
            showToast("docx4j library is not available. Document printing functionality requires docx4j dependencies", NotificationType.WARNING);
            
            // Fallback: log all categories to console
            System.out.println("=== ALL CATEGORIES ===");
            for (String category : categoryModels.keySet()) {
                System.out.println("CATEGORY: " + category);
                Map<String, DefaultTableModel> subcats = categoryModels.get(category);
                for (String subcat : subcategoryOrder.get(category)) {
                    DefaultTableModel model = subcats.get(subcat);
                    if (model != null && model.getRowCount() > 0) {
                        System.out.println("  Subcategory: " + subcat);
                        for (int i = 0; i < model.getRowCount(); i++) {
                            Object questionText = model.getValueAt(i, 1);
                            if (questionText != null) {
                                System.out.println("    Question " + (i + 1) + ": " + questionText);
                            }
                        }
                    }
                }
                System.out.println(""); // Empty line between categories
            }
            System.out.println("=== END ALL CATEGORIES ===");
        } catch (Exception e) {
            e.printStackTrace();
            showToast("Error saving document: " + e.getMessage(), NotificationType.ERROR);
        }
    }

    // Method to print all categories and their subcategories to a single solution
    // Word document
    private void printAllCategoriesSolution() {
        try {
            // Check if docx4j is available at runtime
            Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage");
            
            docx.Docx4jPrinter printer = new docx.Docx4jPrinter();

            // Create document manually to avoid import issues
            java.lang.reflect.Method createMethod = Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage")
                .getMethod("createPackage");
            Object pkg = createMethod.invoke(null);

            // Iterate over all categories
            java.util.List<String> categoryList = new java.util.ArrayList<>(categoryModels.keySet());
            for (int c = 0; c < categoryList.size(); c++) {
                String category = categoryList.get(c);
                Map<String, DefaultTableModel> subcategories = categoryModels.get(category);

                // Iterate over subcategories without category/subcategory headings
                java.util.List<String> subcatList = subcategoryOrder.get(category);
                for (int i = 0; i < subcatList.size(); i++) {
                    String subcategory = subcatList.get(i);
                    DefaultTableModel model = subcategories.get(subcategory);
                    if (model == null || model.getRowCount() == 0) {
                        continue; // Skip empty subcategories
                    }

                    // Insert introduction page for this subcategory if available
                    String introContent = getIntroContent(subcategory);
                    if (introContent != null) {
                        java.lang.reflect.Method addIntroMethod = printer.getClass()
                            .getMethod("addIntroductionPage", Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage"), String.class);
                        addIntroMethod.invoke(printer, pkg, introContent);
                    }

                    // Add questions with solutions using reflection
                    java.lang.reflect.Method addQuestionsSolutionMethod = printer.getClass()
                        .getMethod("addQuestionsSolution", Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage"), DefaultTableModel.class);
                    addQuestionsSolutionMethod.invoke(printer, pkg, model);

                    // Add stop sign page using reflection
                    java.lang.reflect.Method addStopSignMethod = printer.getClass()
                        .getMethod("addStopSignPage", Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage"));
                    addStopSignMethod.invoke(printer, pkg);

                    // Next introduction page already begins on a new page
                }
            }

            // Save the document using reflection with conflict resolution
            String baseFileName = "All_Categories_Solutions.docx";
            File outputFile = new File(baseFileName);
            
            // If file is in use, try alternative names
            int counter = 1;
            while (!canWriteToFile(outputFile)) {
                String fileName = "All_Categories_Solutions_" + counter + ".docx";
                outputFile = new File(fileName);
                counter++;
                
                // Prevent infinite loop
                if (counter > 100) {
                    throw new RuntimeException("Unable to find available filename after 100 attempts");
                }
            }
            
            java.lang.reflect.Method saveMethod = pkg.getClass().getMethod("save", java.io.File.class);
            saveMethod.invoke(pkg, outputFile);
            
            String message = "Solution document saved: " + outputFile.getName();
            if (!outputFile.getName().equals(baseFileName)) {
                message += " (Original filename was in use, saved with alternative name)";
            }
            showToast(message, NotificationType.SUCCESS);
        } catch (ClassNotFoundException e) {
            // docx4j is not available, show a helpful message
            showToast("docx4j library is not available. Document printing functionality requires docx4j dependencies. To enable this feature: 1. Add docx4j dependencies to pom.xml 2. Run 'mvn clean install' 3. Restart the application. For now, all solution data has been logged to console.", NotificationType.WARNING);
            
            // Fallback: log all solutions to console
            System.out.println("=== ALL CATEGORY SOLUTIONS ===");
            for (String category : categoryModels.keySet()) {
                System.out.println("CATEGORY: " + category);
                Map<String, DefaultTableModel> subcategories = categoryModels.get(category);
                for (String subcategory : subcategoryOrder.get(category)) {
                    DefaultTableModel model = subcategories.get(subcategory);
                    if (model != null && model.getRowCount() > 0) {
                        System.out.println("  Subcategory: " + subcategory);
                        for (int i = 0; i < model.getRowCount(); i++) {
                            Object questionText = model.getValueAt(i, 1);
                            Object solutionText = model.getValueAt(i, 2);
                            if (questionText != null) {
                                System.out.println("    Question " + (i + 1) + ": " + questionText);
                                if (solutionText != null) {
                                    System.out.println("    Solution " + (i + 1) + ": " + solutionText);
                                }
                            }
                        }
                    }
                }
                System.out.println(""); // Empty line between categories
            }
            System.out.println("=== END ALL SOLUTIONS ===");
        } catch (Exception e) {
            e.printStackTrace();
            showToast("Error saving solution document: " + e.getMessage(), NotificationType.ERROR);
        }
    }

    // Helper method to add questions and answers to the document
    @SuppressWarnings("unused") // Legacy POI method - kept for potential future use
    private int addQuestionsToDocument(XWPFDocument document, DefaultTableModel model, boolean isSolution, int globalQuestionCount, String subcategory) {
        int rowCount = model.getRowCount();
        int questionCount = 0; // Track number of questions for page breaks
        
        // Determine questions per page based on subcategory
        int questionsPerPage = "Zahlenfolgen".equals(subcategory) ? 5 : 3;

        for (int row = 0; row < rowCount; row++) {
            if (isFrageRow(row, model)) {
                questionCount++;
                
                // Get question text
                String questionNumber = model.getValueAt(row, 0).toString();
                Object questionObj = model.getValueAt(row, 1);
                String questionText;
                
                // Handle Figuren questions (DissectedPieces objects)
                boolean isFigurenQuestionTitle = false;
                if (questionObj instanceof FigurenGenerator.DissectedPieces) {
                    questionText = "Welche Figur lässt sich aus den folgenden Bausteinen zusammensetzen?";
                    isFigurenQuestionTitle = true;
                } else {
                    questionText = questionObj.toString();
                }

                // Add question to document with appropriate spacing
                XWPFParagraph questionParagraph = document.createParagraph();
                questionParagraph.setAlignment(ParagraphAlignment.LEFT);
                
                // Set paragraph spacing based on question type and subcategory
                CTPPr pPr = questionParagraph.getCTP().isSetPPr() ? questionParagraph.getCTP().getPPr() : questionParagraph.getCTP().addNewPPr();
                CTSpacing spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
                
                if (isFigurenQuestionTitle) {
                    // Special spacing for Figuren question title: Vor 10pt, Nach 10pt, Zeilenabstand Einfach
                    spacing.setBefore(BigInteger.valueOf(200));  // 10pt = 200 twentieths of a point
                    spacing.setAfter(BigInteger.valueOf(200));   // 10pt = 200 twentieths of a point
                } else if ("Zahlenfolgen".equals(subcategory)) {
                    // Special spacing for Zahlenfolgen questions: Vor 24pt, Nach 18pt, Einfach
                    spacing.setBefore(BigInteger.valueOf(480));  // 24pt = 480 twentieths of a point
                    spacing.setAfter(BigInteger.valueOf(360));   // 18pt = 360 twentieths of a point
                } else {
                    // Minimal spacing for all other questions
                    spacing.setBefore(BigInteger.valueOf(0));    // 0pt before
                    spacing.setAfter(BigInteger.valueOf(0));     // 0pt after
                }
                spacing.setLine(BigInteger.valueOf(240));    // Single line spacing
                spacing.setLineRule(STLineSpacingRule.AUTO);
                
                XWPFRun questionRun = questionParagraph.createRun();
                questionRun.setBold(false); // No bold formatting
                questionRun.setFontFamily("Calibri"); // Set font to Calibri
                questionRun.setFontSize(11); // Set font size to 11pt
                questionRun.setText(questionNumber + ". " + questionText);

                // For Figuren questions, add the dissected pieces image
                if (questionObj instanceof FigurenGenerator.DissectedPieces) {
                    FigurenGenerator.DissectedPieces dissectedPieces = (FigurenGenerator.DissectedPieces) questionObj;
                    addShapeImageToDocument(document, dissectedPieces.rotatedPieces, false);
                }

                String format = (String) model.getValueAt(row, 4);

                // Collect answers
                row++;
                List<String> options = new ArrayList<>();
                List<String> answers = new ArrayList<>();
                boolean isFigurenQuestion = false;
                
                while (row < rowCount && !isFrageRow(row, model)) {
                    String label = model.getValueAt(row, 0).toString();
                    Object textObj = model.getValueAt(row, 1);
                    Object korrektObj = model.getValueAt(row, 3);
                    
                    String text;
                    // Handle FigurenOptionsData objects
                    if (textObj instanceof FigurenOptionsData) {
                        isFigurenQuestion = true;
                        FigurenOptionsData figurenData = (FigurenOptionsData) textObj;
                        
                        // Add the option shapes as images
                        addFigurenOptionsToDocument(document, figurenData, isSolution);
                        
                        break; // Skip further processing for this options row
                    } else {
                        text = textObj.toString();
                    }
                    
                    if (!isFigurenQuestion) {
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
                    }
                    row++;
                }
                row--; // Adjust row index after the loop

                // Add options with minimal spacing if any
                if (!options.isEmpty()) {
                    XWPFParagraph optionsParagraph = document.createParagraph();
                    optionsParagraph.setAlignment(ParagraphAlignment.LEFT);
                    
                    // Set minimal spacing for options
                    CTPPr optPPr = optionsParagraph.getCTP().isSetPPr() ? optionsParagraph.getCTP().getPPr() : optionsParagraph.getCTP().addNewPPr();
                    CTSpacing optSpacing = optPPr.isSetSpacing() ? optPPr.getSpacing() : optPPr.addNewSpacing();
                    optSpacing.setBefore(BigInteger.valueOf(0));    // 0pt before
                    optSpacing.setAfter(BigInteger.valueOf(0));     // 0pt after
                    optSpacing.setLine(BigInteger.valueOf(240));    // Single line spacing
                    optSpacing.setLineRule(STLineSpacingRule.AUTO);
                    
                    for (String option : options) {
                        XWPFRun optionRun = optionsParagraph.createRun();
                        optionRun.setFontFamily("Calibri"); // Set font to Calibri
                        optionRun.setFontSize(11); // Set font size to 11pt
                        optionRun.setText(option);
                        optionRun.addBreak();
                    }
                }

                // Add answers with specific spacing based on subcategory - only for non-Figuren questions
                if (!answers.isEmpty() && !isFigurenQuestion) {
                    for (String answer : answers) {
                        XWPFParagraph answerParagraph = document.createParagraph();
                        answerParagraph.setAlignment(ParagraphAlignment.LEFT);
                        
                        // Set spacing for answers based on subcategory
                        CTPPr ansPPr = answerParagraph.getCTP().isSetPPr() ? answerParagraph.getCTP().getPPr() : answerParagraph.getCTP().addNewPPr();
                        CTSpacing ansSpacing = ansPPr.isSetSpacing() ? ansPPr.getSpacing() : ansPPr.addNewSpacing();
                        
                        if ("Zahlenfolgen".equals(subcategory)) {
                            // Special spacing for Zahlenfolgen options A-E: Vor 0pt, Nach 3pt, Einfach
                            ansSpacing.setBefore(BigInteger.valueOf(0));    // 0pt before
                            ansSpacing.setAfter(BigInteger.valueOf(60));    // 3pt = 60 twentieths of a point
                        } else {
                            // Minimal spacing for other questions
                            ansSpacing.setBefore(BigInteger.valueOf(0));    // 0pt before
                            ansSpacing.setAfter(BigInteger.valueOf(0));     // 0pt after
                        }
                        ansSpacing.setLine(BigInteger.valueOf(240));    // Single line spacing
                        ansSpacing.setLineRule(STLineSpacingRule.AUTO);
                        
                        XWPFRun answerRun = answerParagraph.createRun();
                        answerRun.setFontFamily("Calibri"); // Set font to Calibri
                        answerRun.setFontSize(11); // Set font size to 11pt
                        answerRun.setText(answer);
                    }
                }
                
                // Add page break after specified number of questions (using global counter)
                if ((questionCount + globalQuestionCount) % questionsPerPage == 0) {
                    // Count remaining questions in the document
                    int remainingQuestions = 0;
                    for (int checkRow = row + 1; checkRow < rowCount; checkRow++) {
                        if (isFrageRow(checkRow, model)) {
                            remainingQuestions++;
                        }
                    }
                    
                    debugLog("Print", LogLevel.INFO, "After question " + (questionCount + globalQuestionCount) + ", found " + remainingQuestions + " remaining questions");
                    
                    // Only add page break if there are actually more questions
                    if (remainingQuestions > 0) {
                        // Create page break directly without any paragraph
                        XWPFParagraph pageBreakParagraph = document.createParagraph();
                        XWPFRun pageBreakRun = pageBreakParagraph.createRun();
                        pageBreakRun.addBreak(BreakType.PAGE);
                        debugLog("Print", LogLevel.INFO, "Added page break after question " + (questionCount + globalQuestionCount));
                    }
                    // Note: Stop sign will be added at the very end, not here
                }
                // No spacing between question blocks to avoid layout issues
            }
        }
        
        return globalQuestionCount + questionCount;
    }

    // Helper method to add Figuren option shapes to the document
    private void addFigurenOptionsToDocument(XWPFDocument document, FigurenOptionsData figurenData, boolean isSolution) {
        try {
            // Create a table for horizontal layout of options
            XWPFTable optionsTable = document.createTable(2, figurenData.options.size()); // 2 rows: images and labels
            optionsTable.setWidth("100%");
            
            // Set equal column distribution - "Spaltenbreite gleichmäßig verteilen"
            int totalColumns = figurenData.options.size();
            int columnWidth = 100 / totalColumns; // Equal percentage distribution
            
            // First row: shape images
            XWPFTableRow imageRow = optionsTable.getRow(0);
            // Second row: option labels
            XWPFTableRow labelRow = optionsTable.getRow(1);
            
            for (int i = 0; i < figurenData.options.size(); i++) {
                OptionDAO option = figurenData.options.get(i);
                
                // Configure image cell with equal width and proper spacing
                XWPFTableCell imageCell = imageRow.getCell(i);
                imageCell.setWidth(columnWidth + "%");
                imageCell.removeParagraph(0); // Remove default paragraph
                XWPFParagraph imageParagraph = imageCell.addParagraph();
                imageParagraph.setAlignment(ParagraphAlignment.CENTER);
                // Set cell vertical alignment to center
                imageCell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
                
                // Set minimal spacing for image cell: Vor 0pt, Nach 0pt, Zeilenabstand Einfach
                CTPPr imgCellPPr = imageParagraph.getCTP().isSetPPr() ? imageParagraph.getCTP().getPPr() : imageParagraph.getCTP().addNewPPr();
                CTSpacing imgCellSpacing = imgCellPPr.isSetSpacing() ? imgCellPPr.getSpacing() : imgCellPPr.addNewSpacing();
                imgCellSpacing.setBefore(BigInteger.valueOf(0));    // 0pt
                imgCellSpacing.setAfter(BigInteger.valueOf(0));     // 0pt
                imgCellSpacing.setLine(BigInteger.valueOf(240));    // Single line spacing = 240
                imgCellSpacing.setLineRule(STLineSpacingRule.AUTO);
                
                // Configure label cell with equal width and proper spacing
                XWPFTableCell labelCell = labelRow.getCell(i);
                labelCell.setWidth(columnWidth + "%");
                labelCell.removeParagraph(0); // Remove default paragraph
                XWPFParagraph labelParagraph = labelCell.addParagraph();
                labelParagraph.setAlignment(ParagraphAlignment.CENTER);
                // Set cell vertical alignment to center
                labelCell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
                
                // Set minimal spacing for label cell: Vor 0pt, Nach 0pt, Zeilenabstand Einfach
                CTPPr labelCellPPr = labelParagraph.getCTP().isSetPPr() ? labelParagraph.getCTP().getPPr() : labelParagraph.getCTP().addNewPPr();
                CTSpacing labelCellSpacing = labelCellPPr.isSetSpacing() ? labelCellPPr.getSpacing() : labelCellPPr.addNewSpacing();
                labelCellSpacing.setBefore(BigInteger.valueOf(0));    // 0pt
                labelCellSpacing.setAfter(BigInteger.valueOf(0));     // 0pt
                labelCellSpacing.setLine(BigInteger.valueOf(240));    // Single line spacing = 240
                labelCellSpacing.setLineRule(STLineSpacingRule.AUTO);
                
                XWPFRun labelRun = labelParagraph.createRun();
                labelRun.setFontFamily("Calibri"); // Set font to Calibri
                labelRun.setFontSize(11); // Set font size to 11pt
                labelRun.setBold(false); // No bold formatting
                
                // For option E (X), just add text in both cells
                if ("X".equals(option.getText())) {
                    XWPFRun textRun = imageParagraph.createRun();
                    textRun.setFontFamily("Calibri"); // Set font to Calibri
                    textRun.setFontSize(20);
                    textRun.setBold(true);
                    textRun.setText("X");
                    
                    String labelText = option.getLabel() + ")";
                    if (isSolution && option.isCorrect()) {
                        labelText += " (Correct)";
                    }
                    labelRun.setText(labelText);
                } else {
                    // For shape options, add the shape image (with grey color)
                    String shapeData = option.getShapeData();
                    if (shapeData != null && !shapeData.trim().isEmpty()) {
                        addSingleShapeImageToDocument(document, shapeData, imageParagraph, true); // true for grey color
                    }
                    
                    String labelText = option.getLabel() + ")";
                    if (isSolution && option.isCorrect()) {
                        labelText += " (Correct)";
                    }
                    labelRun.setText(labelText);
                }
            }
            
            // Remove table borders for cleaner look
            optionsTable.getCTTbl().getTblPr().unsetTblBorders();
            
            // REMOVED: No spacing after tables as per requirement #2
            // XWPFParagraph spaceParagraph = document.createParagraph();
            // XWPFRun spaceRun = spaceParagraph.createRun();
            // spaceRun.addBreak();
            
        } catch (Exception e) {
            debugLog("Print", LogLevel.ERROR, "Error adding Figuren options to document: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper method to add a shape image to the document
    private void addShapeImageToDocument(XWPFDocument document, List<? extends Geometry> shapes, boolean assembled) {
        try {
            // Calculate the bounding box of all shapes to determine optimal cropping
            Envelope totalBounds = new Envelope();
            for (Geometry shape : shapes) {
                totalBounds.expandToInclude(shape.getEnvelopeInternal());
            }
            
            // No padding - we want absolute minimal space
            // totalBounds.expandBy(padding); // REMOVED padding
            
            // Create a PolygonPanel to render the shapes with better horizontal spacing
            PolygonPanel panel = new PolygonPanel(shapes);
            panel.setAssembled(assembled);
            panel.setBackground(Color.WHITE); // Ensure white background, not light grey
            
            // Calculate optimal image dimensions with better horizontal distribution
            double contentAspectRatio = totalBounds.getWidth() / totalBounds.getHeight();
            
            // Start with larger width to better distribute pieces horizontally
            int imageWidth = 1400;   // Increased for better horizontal spacing
            int imageHeight = (int) (imageWidth / contentAspectRatio);
            
            // Ensure reasonable height constraints
            imageHeight = Math.min(imageHeight, 400);  // Allow more height for better distribution
            imageHeight = Math.max(imageHeight, 150);
            
            panel.setSize(imageWidth, imageHeight);
            
            // Create a high-quality BufferedImage with white background
            BufferedImage fullImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = fullImage.createGraphics();
            
            // Enhanced anti-aliasing settings for crisp output
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            // Set pure white background (not light grey)
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, imageWidth, imageHeight);
            panel.paint(g2d);
            g2d.dispose();
            
            // Aggressive cropping to remove ALL unnecessary white space
            BufferedImage croppedImage = cropImageToContentOptimized(fullImage);
            
            // Convert cropped image to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(croppedImage, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            baos.close();
            
            // Add image to document with minimal spacing
            XWPFParagraph imageParagraph = document.createParagraph();
            imageParagraph.setAlignment(ParagraphAlignment.CENTER);
            
            // Set minimal paragraph spacing: Vor 0pt, Nach 0pt, Zeilenabstand Einfach
            CTPPr imgPPr = imageParagraph.getCTP().isSetPPr() ? imageParagraph.getCTP().getPPr() : imageParagraph.getCTP().addNewPPr();
            CTSpacing imgSpacing = imgPPr.isSetSpacing() ? imgPPr.getSpacing() : imgPPr.addNewSpacing();
            imgSpacing.setBefore(BigInteger.valueOf(0));    // 0pt
            imgSpacing.setAfter(BigInteger.valueOf(0));     // 0pt
            imgSpacing.setLine(BigInteger.valueOf(240));    // Single line spacing = 240
            imgSpacing.setLineRule(STLineSpacingRule.AUTO);
            
            XWPFRun imageRun = imageParagraph.createRun();
            
            try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
                // Calculate optimal dimensions with max height constraint of 3.8cm
                double maxHeightCm = 3.8;
                double maxWidthCm = 17.5;
                
                double croppedAspectRatio = (double) croppedImage.getWidth() / croppedImage.getHeight();
                
                // Calculate width based on max height constraint
                double heightCm = maxHeightCm;
                double widthCm = heightCm * croppedAspectRatio;
                
                // If width exceeds max, scale down proportionally
                if (widthCm > maxWidthCm) {
                    widthCm = maxWidthCm;
                    heightCm = widthCm / croppedAspectRatio;
                }
                
                // Convert to EMU (1 cm = 360,000 EMU)
                int widthEMU = (int) (widthCm * 360000);
                int heightEMU = (int) (heightCm * 360000);
                
                imageRun.addPicture(bis, XWPFDocument.PICTURE_TYPE_PNG, "shapes.png", widthEMU, heightEMU);
                
                debugLog("Print", LogLevel.INFO, "Image dimensions: " + widthCm + "cm x " + heightCm + "cm");
            }
            
        } catch (Exception e) {
            debugLog("Print", LogLevel.ERROR, "Error adding shape image to document: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Optimized helper method to crop image to content by removing white space
    private BufferedImage cropImageToContentOptimized(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Find the bounds of non-white content using optimized scanning
        int minX = width, minY = height, maxX = 0, maxY = 0;
        boolean hasContent = false;
        
        // Very aggressive white threshold - only pure white is considered background
        int whiteThreshold = 248;
        
        // Optimized scanning: scan by rows and columns for faster detection
        // First, scan rows from top and bottom to find vertical bounds
        for (int y = 0; y < height && minY == height; y++) {
            for (int x = 0; x < width; x++) {
                if (isContentPixel(image.getRGB(x, y), whiteThreshold)) {
                    minY = y;
                    hasContent = true;
                    break;
                }
            }
        }
        
        // Scan from bottom up
        for (int y = height - 1; y >= 0 && maxY == 0; y--) {
            for (int x = 0; x < width; x++) {
                if (isContentPixel(image.getRGB(x, y), whiteThreshold)) {
                    maxY = y;
                    break;
                }
            }
        }
        
        // If no content found, return original image
        if (!hasContent) {
            return image;
        }
        
        // Now scan columns for horizontal bounds within the vertical bounds
        for (int x = 0; x < width && minX == width; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (isContentPixel(image.getRGB(x, y), whiteThreshold)) {
                    minX = x;
                    break;
                }
            }
        }
        
        // Scan from right to left
        for (int x = width - 1; x >= 0 && maxX == 0; x--) {
            for (int y = minY; y <= maxY; y++) {
                if (isContentPixel(image.getRGB(x, y), whiteThreshold)) {
                    maxX = x;
                    break;
                }
            }
        }
        
        // Add absolutely minimal padding (only 2 pixels on each side)
        int paddingX = 2;
        int paddingY = 2;
        
        minX = Math.max(0, minX - paddingX);
        minY = Math.max(0, minY - paddingY);
        maxX = Math.min(width - 1, maxX + paddingX);
        maxY = Math.min(height - 1, maxY + paddingY);
        
        // Calculate cropped dimensions
        int croppedWidth = maxX - minX + 1;
        int croppedHeight = maxY - minY + 1;
        
        // Create cropped image
        BufferedImage croppedImage = new BufferedImage(croppedWidth, croppedHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = croppedImage.createGraphics();
        g2d.drawImage(image, 0, 0, croppedWidth, croppedHeight, minX, minY, maxX + 1, maxY + 1, null);
        g2d.dispose();
        
        debugLog("Print", LogLevel.INFO, "Optimized crop: " + width + "x" + height + 
                " → " + croppedWidth + "x" + croppedHeight + " (removed " + 
                ((height - croppedHeight) * 100 / height) + "% height)");
        
        return croppedImage;
    }
    
    // Helper method to check if a pixel contains content (not white)
    private boolean isContentPixel(int rgb, int whiteThreshold) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return red < whiteThreshold || green < whiteThreshold || blue < whiteThreshold;
    }

    // Helper method to add a single shape image to the document with color option
    private void addSingleShapeImageToDocument(XWPFDocument document, String shapeWKT, XWPFParagraph paragraph, boolean useGreyColor) {
        try {
            // Parse WKT to Geometry
            WKTReader reader = new WKTReader();
            Geometry geometry = reader.read(shapeWKT);
            
            // Larger size for option figures (increased from 200x160)
            int shapeWidth = 300;  // Bigger for better visibility
            int shapeHeight = 240; // Bigger for better visibility
            
            // Create a high-quality BufferedImage
            BufferedImage image = new BufferedImage(shapeWidth, shapeHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            
            // Enhanced anti-aliasing settings for crisp output
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            // White background
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, shapeWidth, shapeHeight);
            
            // Manual rendering with direct color control
            if (useGreyColor) {
                // Manually render the shape with grey colors instead of using PolygonPanel
                // Calculate bounds and scale
                ShapeWriter shapeWriter = new ShapeWriter();
                
                Envelope envelope = geometry.getEnvelopeInternal();
                if (envelope.getWidth() == 0 || envelope.getHeight() == 0) {
                    debugLog("Print", LogLevel.WARN, "Shape has zero width/height");
                    return;
                }
                
                double scaleX = shapeWidth / envelope.getWidth();
                double scaleY = shapeHeight / envelope.getHeight();
                double scale = Math.min(scaleX, scaleY) * 0.9; // Scale down slightly to fit
                
                AffineTransform at = new AffineTransform();
                at.translate(shapeWidth / 2, shapeHeight / 2);
                at.scale(scale, -scale); // Negative scale on y-axis to flip vertically
                at.translate(-envelope.centre().x, -envelope.centre().y);
                
                Shape shape = shapeWriter.toShape(geometry);
                Shape transformedShape = at.createTransformedShape(shape);
                
                // Force grey colors
                g2d.setColor(new Color(160, 160, 160)); // Medium grey for fill
                g2d.fill(transformedShape);
                g2d.setColor(new Color(80, 80, 80));  // Dark grey for outline
                g2d.setStroke(new BasicStroke(2.0f));
                g2d.draw(transformedShape);
                
                debugLog("Print", LogLevel.INFO, "Applied grey colors directly to shape");
            } else {
                // Use PolygonPanel for normal (non-grey) rendering
                List<Geometry> singleShape = Arrays.asList(geometry);
                PolygonPanel panel = new PolygonPanel(singleShape);
                panel.setAssembled(true);
                panel.setSize(shapeWidth, shapeHeight);
                panel.paint(g2d);
            }
            
            g2d.dispose();
            
            // Convert image to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            baos.close();
            
            // Add image to the paragraph - option figures exactly 2.5cm height
            XWPFRun imageRun = paragraph.createRun();
            try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
                // Option figures: maintain aspect ratio with exactly 2.5cm height
                // 2.5cm = 2.5 * 360,000 = 900,000 EMU height
                // Width proportional: if source is 300x240, then width = (300/240) * 2.5cm = 3.125cm
                // 3.125cm = 3.125 * 360,000 = 1,125,000 EMU
                imageRun.addPicture(bis, XWPFDocument.PICTURE_TYPE_PNG, "shape.png", 
                                  1125000, 900000); // 3.125cm x 2.5cm in EMU
            }
            
        } catch (Exception e) {
            debugLog("Print", LogLevel.ERROR, "Error adding single shape image: " + e.getMessage());
            e.printStackTrace();
            
            // Fallback: add text description
            XWPFRun textRun = paragraph.createRun();
            textRun.setText("Figur");
        }
    }

    // Helper method to add a stop sign page at the end of the document
    @SuppressWarnings("unused") // Legacy POI method - kept for potential future use
    private void addStopSignPage(XWPFDocument document) {
        try {
            // Create a new page with page break
            XWPFParagraph pageBreakParagraph = document.createParagraph();
            XWPFRun pageBreakRun = pageBreakParagraph.createRun();
            pageBreakRun.addBreak(BreakType.PAGE);

            // Create a table for perfect vertical and horizontal centering
            XWPFTable table = document.createTable(1, 1);
            table.setWidth("100%");
            
            // Remove table borders for invisible table
            try {
                if (table.getCTTbl().getTblPr() != null) {
                    table.getCTTbl().getTblPr().unsetTblBorders();
                }
            } catch (Exception e) {
                debugLog("Print", LogLevel.WARN, "Could not remove table borders: " + e.getMessage());
            }
            
            // Set table height to fill page (approximately A4 height minus margins)
            table.getRow(0).setHeight(15000); // Large height for vertical centering
            XWPFTableCell cell = table.getRow(0).getCell(0);
            cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            cell.setWidth("100%");
            
            // Remove cell borders with proper null checks
            try {
                if (cell.getCTTc() != null && cell.getCTTc().getTcPr() != null) {
                    cell.getCTTc().getTcPr().unsetTcBorders();
                } else if (cell.getCTTc() != null) {
                    // Initialize TcPr if it doesn't exist
                    cell.getCTTc().addNewTcPr();
                }
            } catch (Exception e) {
                debugLog("Print", LogLevel.WARN, "Could not remove cell borders: " + e.getMessage());
            }
            
            cell.removeParagraph(0);
            XWPFParagraph stopSignParagraph = cell.addParagraph();
            stopSignParagraph.setAlignment(ParagraphAlignment.CENTER);

            // Try to use the provided stop sign image with detailed debugging
            String[] possiblePaths = {
                "stopp_sign.png",
                "src/main/resources/images/stopp_sign.png", 
                "resources/images/stopp_sign.png",
                "images/stopp_sign.png"
            };

            byte[] stopSignBytes = null;
            String imagePath = null;
            
            debugLog("Print", LogLevel.INFO, "Searching for stop sign image in paths:");
            for (String path : possiblePaths) {
                debugLog("Print", LogLevel.INFO, "  Checking: " + path);
                try {
                    File imageFile = new File(path);
                    debugLog("Print", LogLevel.INFO, "    File exists: " + imageFile.exists());
                    debugLog("Print", LogLevel.INFO, "    Absolute path: " + imageFile.getAbsolutePath());
                    if (imageFile.exists()) {
                        imagePath = path;
                        FileInputStream fis = new FileInputStream(imageFile);
                        stopSignBytes = fis.readAllBytes();
                        fis.close();
                        debugLog("Print", LogLevel.INFO, "    SUCCESS: Loaded " + stopSignBytes.length + " bytes from " + path);
                        break;
                    }
                } catch (Exception e) {
                    debugLog("Print", LogLevel.WARN, "    Error loading " + path + ": " + e.getMessage());
                }
            }

            if (stopSignBytes == null) {
                debugLog("Print", LogLevel.INFO, "Trying classpath resource: /images/stopp_sign.png");
                try {
                    InputStream resourceStream = getClass().getResourceAsStream("/images/stopp_sign.png");
                    if (resourceStream != null) {
                        stopSignBytes = resourceStream.readAllBytes();
                        resourceStream.close();
                        imagePath = "classpath:/images/stopp_sign.png";
                        debugLog("Print", LogLevel.INFO, "SUCCESS: Loaded " + stopSignBytes.length + " bytes from classpath");
                    } else {
                        debugLog("Print", LogLevel.WARN, "Classpath resource not found");
                    }
                } catch (Exception e) {
                    debugLog("Print", LogLevel.WARN, "Error loading from classpath: " + e.getMessage());
                }
            }

            XWPFRun stopSignRun = stopSignParagraph.createRun();
            
            // If no image found, create a simple stop sign programmatically
            if (stopSignBytes == null) {
                debugLog("Print", LogLevel.INFO, "Creating programmatic stop sign image");
                stopSignBytes = createStopSignImage();
                imagePath = "programmatic";
            }
            
            if (stopSignBytes != null) {
                try (ByteArrayInputStream bis = new ByteArrayInputStream(stopSignBytes)) {
                    // Stop sign size: 8cm x 8cm (2,880,000 EMU)
                    stopSignRun.addPicture(bis, XWPFDocument.PICTURE_TYPE_PNG, "stop_sign.png", 2880000, 2880000);
                    debugLog("Print", LogLevel.INFO, "Added stop sign image from: " + imagePath);
                }
            } else {
                // Final fallback to text
                stopSignRun.setFontFamily("Arial");
                stopSignRun.setFontSize(72);
                stopSignRun.setBold(true);
                stopSignRun.setColor("FF0000"); // Red color
                stopSignRun.setText("STOP");
                debugLog("Print", LogLevel.WARN, "Using text fallback for stop sign");
            }
            
            debugLog("Print", LogLevel.INFO, "Added stop sign page after subcategory");
        } catch (Exception e) {
            debugLog("Print", LogLevel.ERROR, "Error adding stop sign page: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Helper method to create a programmatic stop sign image
    private byte[] createStopSignImage() {
        try {
            int size = 400;
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            
            // Enable anti-aliasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // White background
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, size, size);
            
            // Create octagonal stop sign
            int centerX = size / 2;
            int centerY = size / 2;
            int radius = size / 2 - 20;
            
            // Calculate octagon points
            int[] xPoints = new int[8];
            int[] yPoints = new int[8];
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0;
                xPoints[i] = centerX + (int)(radius * Math.cos(angle));
                yPoints[i] = centerY + (int)(radius * Math.sin(angle));
            }
            
            // Draw red octagon
            g2d.setColor(Color.RED);
            g2d.fillPolygon(xPoints, yPoints, 8);
            
            // Draw black border
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(8));
            g2d.drawPolygon(xPoints, yPoints, 8);
            
            // Draw "STOP" text
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 60));
            FontMetrics fm = g2d.getFontMetrics();
            String text = "STOP";
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();
            g2d.drawString(text, centerX - textWidth/2, centerY + textHeight/4);
            
            g2d.dispose();
            
            // Convert to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            baos.close();
            
            debugLog("Print", LogLevel.INFO, "Created programmatic stop sign image: " + imageBytes.length + " bytes");
            return imageBytes;
            
        } catch (Exception e) {
            debugLog("Print", LogLevel.ERROR, "Error creating programmatic stop sign: " + e.getMessage());
            return null;
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
                    Object formatObj = table.getModel().getValueAt(editingRow, 3);
                    String currentFormat;
                    if (formatObj instanceof String) {
                        currentFormat = (String) formatObj;
                    } else if (formatObj != null) {
                        currentFormat = formatObj.toString();
                    } else {
                        currentFormat = "Kurz";
                    }
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
                // Update the model with a real Boolean value
                if (editingRow >= 0 && editingColumn == 3) {
                    table.getModel().setValueAt(checkBox.isSelected(), editingRow, 3);
                    ((DefaultTableModel) table.getModel()).fireTableCellUpdated(editingRow, 3);
                    
                    // Persist to database for option rows
                    try {
                        updateOption(editingRow, 3, checkBox.isSelected());
                    } catch (Exception ex) {
                        debugLog("DB", LogLevel.ERROR, "CustomEditor", "Failed to update option correctness: " + ex.getMessage());
                    }
                }
                stopCellEditing();
                table.repaint(); // Force the table to repaint
            });

            // Initialize the default text editor
            defaultTextEditor = new DefaultCellEditor(new JTextField());

            // Difficulty ComboBox initialisieren
            difficultyCombo = new JComboBox<>(new String[] { "EASY", "MEDIUM", "HARD" });
            difficultyCombo.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));
            difficultyCombo.setFocusable(false);
            
            // Custom UI to remove arrow and maintain background color
            difficultyCombo.setUI(new BasicComboBoxUI() {
                @Override
                protected JButton createArrowButton() {
                    // Return a completely empty button with no space
                    JButton button = new JButton();
                    button.setPreferredSize(new Dimension(0, 0));
                    button.setMinimumSize(new Dimension(0, 0));
                    button.setMaximumSize(new Dimension(0, 0));
                    button.setVisible(false);
                    button.setBorder(null);
                    return button;
                }
                
                @Override
                protected Rectangle rectangleForCurrentValue() {
                    // Make the current value area take up the entire component
                    int width = comboBox.getWidth();
                    int height = comboBox.getHeight();
                    Insets insets = getInsets();
                    return new Rectangle(insets.left, insets.top, 
                        width - (insets.left + insets.right), 
                        height - (insets.top + insets.bottom));
                }
                
                @Override
                public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                    // Paint the background with the difficulty color
                    String currentValue = (String) difficultyCombo.getSelectedItem();
                    Color bg = Color.WHITE;
                    if (currentValue != null) {
                        String diffStr = currentValue.toLowerCase();
                        if ("easy".equals(diffStr)) {
                            bg = new Color(127, 204, 165, 75); // green
                        } else if ("medium".equals(diffStr)) {
                            bg = new Color(255, 191, 71, 75); // orange
                        } else if ("hard".equals(diffStr)) {
                            bg = new Color(255, 71, 71, 75); // red
                        }
                    }
                    g.setColor(bg);
                    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                }
            });
            
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

            Object formatObj = table.getModel().getValueAt(row, 3);
            String currentFormat;
            if (formatObj instanceof String) {
                currentFormat = (String) formatObj;
            } else if (formatObj != null) {
                currentFormat = formatObj.toString();
            } else {
                currentFormat = "Kurz";
            }
            if (currentFormat == null) {
                currentFormat = "Kurz";
            }

            if (!currentFormat.equals(newFormat)) {
                // Get the question number and text
                String questionNumber = String.valueOf(model.getValueAt(row, 0)); // Treat value as a String

                // Defensive: check simulationId
                if (selectedSimulationId == null) {
                    showToast("Simulation ID is not set. Please select a simulation before changing the format.", NotificationType.ERROR);
                    return;
                }

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
                    showToast("Error updating question format in database: " + e.getMessage(), NotificationType.ERROR);
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
                    Object correctObj = model.getValueAt(rowIndex, 3); // ✓ column is index 3, not 2
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
                    // Always set Solution column to empty string
                    model.insertRow(frageRow + 1 + i, new Object[] { label, text, "", isCorrect, "", "" });
                }
            } else if ("Lang".equals(newFormat)) {
                String[] optionLabels = { "1.", "2.", "3.", "4." };
                String[] answerLabels = { "A)", "B)", "C)", "D)", "E)" };
                for (int i = 0; i < optionLabels.length; i++) {
                    // Set ✓ column to false (not null) for option rows
                    model.insertRow(frageRow + 1 + i, new Object[] { optionLabels[i], "", "", false, "", "" });
                }
                for (int i = 0; i < answerLabels.length; i++) {
                    String label = answerLabels[i];
                    String text = answerTexts.getOrDefault(label, "");
                    Boolean isCorrect = answerCorrectness.getOrDefault(label, false);
                    // Always set Solution column to empty string
                    model.insertRow(frageRow + 1 + optionLabels.length + i,
                            new Object[] { label, text, "", isCorrect, "", "" });
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
            if (editingColumn == 3) {
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
                    // Defensive: handle Boolean, String, or null
                    Boolean checked = false;
                    if (value instanceof Boolean) {
                        checked = (Boolean) value;
                    } else if (value instanceof String) {
                        // Accept "true"/"false" strings
                        checked = Boolean.parseBoolean((String) value);
                    } else {
                        checked = false;
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
                // Defensive: always use string representation for difficulty
                String diffValue = "MEDIUM";
                if (value != null) {
                    diffValue = value.toString();
                }
                difficultyCombo.setSelectedItem(diffValue);
                editorComponent = difficultyCombo;
                return difficultyCombo;
            }

            // For other cells, use default text editor
            editorComponent = defaultTextEditor.getTableCellEditorComponent(table, value, isSelected, row, column);
            return editorComponent;
        }

        private void deleteQuestionAtRow(JTable table, int row) {
            if (table == null || questionTable == null) {
                showToast("Error: Table not initialized", NotificationType.ERROR);
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
                    showToast("Failed to delete question", NotificationType.ERROR);
                }
            } catch (SQLException e) {
                try {
                    conn.rollback(); // Rollback if an error occurs
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
                e.printStackTrace();
                showToast("Error deleting question: " + e.getMessage(), NotificationType.ERROR);
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
                        showToast("Error renumbering question: " + e.getMessage(), NotificationType.ERROR);
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
                    model.addRow(new Object[] { label, option.getText(), "", option.isCorrect(), "", "" });
                } else if (isLang && label.matches("[A-E]")) {
                    model.addRow(new Object[] { label, option.getText(), "", option.isCorrect(), "", "" });
                } else if (!isLang && label.matches("[A-E]")) {
                    model.addRow(new Object[] { label + ")", option.getText(), "", option.isCorrect(), "", "" });
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
            model.addRow(new Object[] { "", figurenOptionsData, "", false, "", "" });
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
            showToast("Error updating question: " + e.getMessage(), NotificationType.ERROR);
        }
    }

    private void updateOption(int row, int column, Object data) {
        // Validate selectedSimulationId first
        if (selectedSimulationId == null) {
            debugLog("DB", LogLevel.ERROR, "updateOption", "selectedSimulationId is null");
            return;
        }
        
        int frageRow = getFrageRowForRow(row, questionTable);
        if (frageRow == -1) {
            debugLog("DB", LogLevel.ERROR, "updateOption", "Invalid frageRow detected");
            return; // Exit if there's no valid frageRow
        }
        Object questionNumberObj = questionTable.getValueAt(frageRow, 0);
        int questionNumber;
        try {
            questionNumber = Integer.parseInt(questionNumberObj.toString());
        } catch (NumberFormatException e) {
            debugLog("DB", LogLevel.ERROR, "updateOption", "Error parsing question number: " + questionNumberObj);
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
                debugLog("DB", LogLevel.WARN, "updateOption", "Question not found for category=" + currentCategory + 
                    ", subcategory=" + currentSubcategory + ", questionNumber=" + questionNumber + 
                    ", simulationId=" + selectedSimulationId);
                return; // Silently return instead of throwing exception
            }
            switch (column) {
                case 1: // Option text
                    String optionText = (String) data;
                    optionDAO.updateOptionText(questionId, label, optionText, selectedSimulationId);
                    break;
                case 2: // Solution column (not used for options)
                    break;
                case 3: // Is correct (✓ column)
                    boolean isCorrect = (boolean) data;
                    optionDAO.updateOptionCorrectness(questionId, label, isCorrect, selectedSimulationId);
                    debugLog("DB", LogLevel.INFO, "updateOption", "Updated correctness for option " + label + " to " + isCorrect);
                    break;
            }
        } catch (SQLException e) {
            debugLog("DB", LogLevel.ERROR, "updateOption", "Error updating option: " + e.getMessage());
            // Don't show dialog for every error to avoid spam
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
    public static class FigurenOptionsData {
        public final List<OptionDAO> options;
        public final FigurenGenerator.DissectedPieces dissectedPieces;
        
        public FigurenOptionsData(List<OptionDAO> options, FigurenGenerator.DissectedPieces dissectedPieces) {
            this.options = options;
            this.dissectedPieces = dissectedPieces;
        }
    }

    // Schwierigkeitsgrad-Konstanten
    /**
     * Enum for question difficulty with color and symbol.
     */

    /**
     * Helper method to check if a file can be written to (not locked by another process).
     * @param file The file to check
     * @return true if the file can be written to, false otherwise
     */
    private boolean canWriteToFile(File file) {
        // If file doesn't exist, we can write to it
        if (!file.exists()) {
            return true;
        }
        
        // Try to open the file for writing to check if it's locked
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file, true)) {
            return true;
        } catch (java.io.IOException e) {
            // File is locked or cannot be written to
            debugLog("Print", LogLevel.WARN, "File is locked or cannot be written to: " + file.getName() + " - " + e.getMessage());
            return false;
        }
    }

}
