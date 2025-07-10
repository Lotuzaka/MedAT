import javax.swing.*;
import java.awt.*;

public class RoundedTextField extends JTextField {
    private final int arc;
    private final Color bgColor;
    private final Color fgColor;
    private final int fixedHeight;

    public RoundedTextField(String text, int arc, Color bgColor, Color fgColor, int fixedHeight) {
        super(text);
        this.arc = arc;
        this.bgColor = bgColor;
        this.fgColor = fgColor;
        this.fixedHeight = fixedHeight;
        setOpaque(false);
        setForeground(fgColor);
        setCaretColor(fgColor);
        // Match the button font exactly (see styleModernButton in MedatoninDB)
        setFont(new Font("SansSerif", Font.BOLD, 14));
        setHorizontalAlignment(JTextField.CENTER);
        setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(bgColor);
        // Match the button's border radius (see buttonBorderRadius in MedatoninDB)
        int radius = Math.max(arc, 7); // Never more round than the button
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, fixedHeight);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension ps = super.getPreferredSize();
        return new Dimension(ps.width, fixedHeight);
    }

    @Override
    public Insets getInsets() {
        return new Insets(5, 10, 5, 10);
    }
}
