import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Dimension;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.swing.JPanel;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.locationtech.jts.awt.ShapeWriter;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import java.awt.*;
import java.awt.geom.*;

import javax.imageio.ImageIO;
import javax.swing.*;

public class PolygonPanel extends JPanel {
    private List<Geometry> shapes;
    private Color outlineColor = Color.BLACK;
    private Color fillColor = new Color(200, 200, 200);
    private boolean assembled = false; // Add assembled flag
    private ShapeWriter shapeWriter = new ShapeWriter();

    private String shapeType;
    private Random random = new Random();

    public PolygonPanel(List<? extends Geometry> shapes) {
        if (shapes == null) {
            throw new IllegalArgumentException("Shapes list cannot be null");
        }
        this.shapes = new ArrayList<>(shapes);
        setPreferredSize(new Dimension(300, 300));
        setOpaque(true);
    }

    // Setters for outline and fill colors
    public void setOutlineColor(Color color) {
        this.outlineColor = color;
    }

    public void setFillColor(Color color) {
        this.fillColor = color;
    }

    public void setAssembled(boolean assembled) {
        this.assembled = assembled;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        // Set rendering hints for better graphics quality
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        if (shapes.isEmpty()) {
            System.out.println("No shapes to render.");
            return;
        }

        // System.out.println("Rendering " + shapes.size() + " shapes.");
        int shapeIndexNonAssembled = 0;

        if (assembled) {
            // Assembled case: render the shapes in their original positions, scaled to fit
            // Calculate combined envelope
            Envelope envelope = null;
            int shapeIndexAssembled = 0;
            for (Geometry geometry : shapes) {
                // System.out.println("Rendering shape index: " + shapeIndexAssembled);
                Envelope geomEnvelope = geometry.getEnvelopeInternal();
                System.out.println("Shape " + shapeIndexAssembled + " bounds: " + geomEnvelope);

                if (envelope == null) {
                    envelope = new Envelope(geomEnvelope);
                } else {
                    envelope.expandToInclude(geomEnvelope);
                }
                shapeIndexAssembled++;

            }

            System.out.println("Combined envelope: " + envelope);
            if (envelope == null)
                return;

            double scaleX = getWidth() / envelope.getWidth();
            double scaleY = getHeight() / envelope.getHeight();
            double scale = Math.min(scaleX, scaleY) * 0.9; // Scale down slightly to fit

            AffineTransform at = new AffineTransform();

            // Adjust for inverted y-axis
            at.translate(getWidth() / 2, getHeight() / 2);
            at.scale(scale, -scale); // Negative scale on y-axis to flip vertically
            at.translate(-envelope.centre().x, -envelope.centre().y);

            // Sort shapes by area size ascending
            shapes.sort(Comparator.comparingDouble(Geometry::getArea));

            // Rendering loop
            for (int i = 0; i < shapes.size(); i++) {
                Geometry geometry = shapes.get(i);
                Shape shape = shapeWriter.toShape(geometry);
                Shape transformedShape = at.createTransformedShape(shape);

                // Use alpha composite to handle overlapping
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

                // Generate a distinct color
                float hue = 0.25f + (0.25f * i / shapes.size());
                g2.setColor(Color.getHSBColor(hue, 0.8f, 0.8f));
                g2.fill(transformedShape);
                g2.setColor(outlineColor);
                g2.draw(transformedShape);
            }
        } else {
            // Non-assembled case: render shapes separately with spacing
            int numPieces = shapes.size();
            int spacing = 10;
            int totalWidth = getWidth();
            int totalHeight = getHeight();
            int pieceWidth = (totalWidth - (numPieces + 1) * spacing) / numPieces;
            int xOffset = spacing;

            for (Geometry geometry : shapes) {
                // System.out.println("Rendering shape index: " + shapeIndexNonAssembled);
                Envelope geomEnvelope = geometry.getEnvelopeInternal();
                double scaleX = (double) pieceWidth / geomEnvelope.getWidth();
                double scaleY = (double) (totalHeight - 2 * spacing) / geomEnvelope.getHeight();
                double scale = Math.min(scaleX, scaleY);

                AffineTransform at = new AffineTransform();
                at.translate(xOffset - geomEnvelope.getMinX() * scale, spacing - geomEnvelope.getMinY() * scale);
                at.scale(scale, scale);

                Shape shape = shapeWriter.toShape(geometry);
                Shape transformedShape = at.createTransformedShape(shape);
                g2.setColor(fillColor);
                g2.fill(transformedShape);
                g2.setColor(outlineColor);
                g2.draw(transformedShape);

                xOffset += pieceWidth + spacing;
                shapeIndexNonAssembled++;
            }
        }
        System.out.println("Assembled flag: " + assembled);
        System.out.println("Shapes count: " + shapes.size());

    }

    // Generate distractor shapes
    public List<Geometry> generateDistractorShapes(int numberOfDistractors) {
        List<Geometry> distractors = new ArrayList<>();
        Set<String> usedShapeTypes = new HashSet<>();

        String[] polygonShapes = { "hexagon", "octagon", "heptagon", "pentagon" };
        String[] circleShapes = { "circle", "three-quarter circle", "half circle", "quarter circle" };

        List<String> shapeTypesList;
        if (Arrays.asList(polygonShapes).contains(shapeType)) {
            shapeTypesList = new ArrayList<>(Arrays.asList(polygonShapes));
        } else if (Arrays.asList(circleShapes).contains(shapeType)) {
            shapeTypesList = new ArrayList<>(Arrays.asList(circleShapes));
        } else {
            throw new IllegalArgumentException("Unsupported shape type");
        }

        shapeTypesList.remove(shapeType);

        for (int i = 0; i < numberOfDistractors; i++) {
            String randomShape;
            do {
                randomShape = shapeTypesList.get(random.nextInt(shapeTypesList.size()));
            } while (usedShapeTypes.contains(randomShape));

            usedShapeTypes.add(randomShape);
            Geometry distractor = FigurenGenerator.createShape(randomShape, 200, 200, 100);
            distractors.add(distractor);
        }

        Collections.shuffle(distractors);

        return distractors;
    }

    // Method to capture JPanel as BufferedImage
    public static BufferedImage getPanelImage(JPanel panel) {
        int width = panel.getWidth();
        int height = panel.getHeight();

        // If the panel size is 0, use preferred size or default dimensions
        if (width <= 0 || height <= 0) {
            Dimension preferredSize = panel.getPreferredSize();
            width = preferredSize.width > 0 ? preferredSize.width : 400;
            height = preferredSize.height > 0 ? preferredSize.height : 400;

            // Set the size of the panel
            panel.setSize(width, height);
        }

        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Set a white background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        // If the panel is not visible, just return the white image
        if (!panel.isVisible()) {
            g2d.dispose();
            return image;
        }

        panel.printAll(g2d);
        g2d.dispose();
        return image;
    }

    // Method to export question and solution to Word document
    public void exportToWord(BufferedImage questionImage, List<BufferedImage> optionImages,
            BufferedImage solutionImage) throws IOException, InvalidFormatException {
        XWPFDocument document = new XWPFDocument();

        // Add question image
        XWPFParagraph questionParagraph = document.createParagraph();
        XWPFRun questionRun = questionParagraph.createRun();
        questionRun.setText("Question:");
        addImageToRun(questionRun, questionImage, "question.png");

        // Add options
        for (int i = 0; i < optionImages.size(); i++) {
            XWPFParagraph optionParagraph = document.createParagraph();
            XWPFRun optionRun = optionParagraph.createRun();
            optionRun.setText("Option " + (char) ('A' + i) + ":");
            addImageToRun(optionRun, optionImages.get(i), "option" + i + ".png");
        }

        // Add option E: "X"
        XWPFParagraph optionEParagraph = document.createParagraph();
        XWPFRun optionERun = optionEParagraph.createRun();
        optionERun.setText("Option E: X");

        // Add solution
        XWPFParagraph solutionParagraph = document.createParagraph();
        XWPFRun solutionRun = solutionParagraph.createRun();
        solutionRun.setText("Solution:");
        addImageToRun(solutionRun, solutionImage, "solution.png");

        // Save the document
        try (FileOutputStream out = new FileOutputStream("FigurenQuestion.docx")) {
            document.write(out);
        }
    }

    private void addImageToRun(XWPFRun run, BufferedImage image, String fileName)
            throws IOException, InvalidFormatException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(image, "png", os);
        InputStream is = new ByteArrayInputStream(os.toByteArray());
        run.addPicture(is, Document.PICTURE_TYPE_PNG, fileName, Units.toEMU(200), Units.toEMU(200));
        is.close();
    }

}
