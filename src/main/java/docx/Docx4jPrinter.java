package docx;

import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.*;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;

import javax.swing.table.DefaultTableModel;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import java.math.BigInteger;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.Color;
import org.locationtech.jts.io.WKTReader;

// Import geometry classes for Figuren questions
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.awt.ShapeWriter;

/**
 * Utility class using docx4j to generate print documents. This is a very
 * small proof of concept and does not cover the entire functionality of
 * the original Apache POI implementation.
 */
public class Docx4jPrinter {

    private final ObjectFactory factory = new ObjectFactory();

    /**
     * Load the introduction pages and split them by page breaks.
     */
    public java.util.List<java.util.List<Object>> loadIntroductionPages(File docx) throws Docx4JException {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.load(docx);
        java.util.List<Object> content = pkg.getMainDocumentPart().getContent();
        java.util.List<java.util.List<Object>> pages = new ArrayList<>();
        java.util.List<Object> current = new ArrayList<>();
        for (Object o : content) {
            current.add(o);
            if (containsPageBreak(o)) {
                pages.add(new ArrayList<>(current));
                current.clear();
            }
        }
        if (!current.isEmpty()) {
            pages.add(new ArrayList<>(current));
        }
        return pages;
    }

    private boolean containsPageBreak(Object o) {
        if (o instanceof P p) {
            for (Object c : p.getContent()) {
                if (c instanceof Br br && br.getType() == STBrType.PAGE) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Create a document containing the introduction pages followed by question
     * text. Images and advanced formatting are not handled here.
     */
    public WordprocessingMLPackage buildDocument(Map<String, DefaultTableModel> subcats,
            java.util.List<String> order,
            java.util.List<java.util.List<Object>> introPages) throws Docx4JException {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        int pageIndex = 0;
        for (String subcat : order) {
            if (pageIndex < introPages.size()) {
                for (Object o : introPages.get(pageIndex)) {
                    pkg.getMainDocumentPart().addObject(o);
                }
                pageIndex++;
            }
            DefaultTableModel model = subcats.get(subcat);
            if (model != null) {
                addQuestions(pkg, model);
            }
            addPageBreak(pkg);
        }
        return pkg;
    }

    /**
     * Append the given question table to the document with proper handling for
     * different question types.
     */
    public void addQuestions(WordprocessingMLPackage pkg, DefaultTableModel model) {
        int figurenCounter = 0; // Counter for Figuren questions

        for (int r = 0; r < model.getRowCount(); r++) {
            String number = Objects.toString(model.getValueAt(r, 0), "");
            Object questionObj = model.getValueAt(r, 1);

            if (!number.isEmpty() && questionObj != null) {
                String questionText;
                boolean isFigurenQuestion = false;

                // Handle Figuren questions (DissectedPieces objects)
                if (questionObj.getClass().getSimpleName().equals("DissectedPieces")) {
                    questionText = "Welche Figur lässt sich aus den folgenden Bausteinen zusammensetzen?";
                    isFigurenQuestion = true;
                } else {
                    questionText = questionObj.toString();
                }

                // For Figuren questions: Add page break BEFORE every 4th, 7th, 10th, etc.
                // question
                if (isFigurenQuestion) {

                    figurenCounter++;
                    // Add page break before the 4th, 7th, 10th, etc. question (every 3rd question
                    // starting from 4th)
                    if (figurenCounter > 3 && (figurenCounter - 1) % 3 == 0) {
                        addPageBreak(pkg);
                    }
                }

                // Add question text with special formatting for Figuren questions
                P questionP = factory.createP();

                // For Figuren questions, add special spacing: Vor 6pt, Nach 6pt, Zeilenabstand
                // Mehrfach 1.15
                if (isFigurenQuestion) {
                    PPr questionPPr = factory.createPPr();
                    PPrBase.Spacing questionSpacing = factory.createPPrBaseSpacing();
                    questionSpacing.setBefore(BigInteger.valueOf(120)); // 6pt = 120 twips
                    questionSpacing.setAfter(BigInteger.valueOf(120)); // 6pt = 120 twips
                    questionSpacing.setLine(BigInteger.valueOf(276)); // 1.15 * 240 = 276 twips
                    questionSpacing.setLineRule(STLineSpacingRule.AUTO);
                    questionPPr.setSpacing(questionSpacing);
                    questionP.setPPr(questionPPr);
                }

                R questionR = factory.createR();
                Text questionT = factory.createText();
                questionT.setValue(number + ". " + questionText);
                questionR.getContent().add(questionT);
                questionP.getContent().add(questionR);
                pkg.getMainDocumentPart().addObject(questionP);

                // For Figuren questions, add the dissected pieces image
                if (isFigurenQuestion) {
                    try {
                        addFigurenShapeImage(pkg, questionObj);
                    } catch (Exception e) {
                        // If image generation fails, add a text representation
                        P pieceP = factory.createP();
                        R pieceR = factory.createR();
                        Text pieceT = factory.createText();
                        pieceT.setValue("Bausteine: " + questionObj.toString());
                        pieceR.getContent().add(pieceT);
                        pieceP.getContent().add(pieceR);
                        pkg.getMainDocumentPart().addObject(pieceP);
                    }
                }

                // Add options/answers if they exist in the model
                // Also skip option rows to avoid re-processing them
                r = addQuestionOptions(pkg, model, r);

                // Add spacing after non-Figuren questions only
                if (!isFigurenQuestion) {
                    addSpacing(pkg);
                }
            }
        }
    }

    /**
     * Add questions with solutions to the document with proper handling for
     * different question types.
     */
    public void addQuestionsSolution(WordprocessingMLPackage pkg, DefaultTableModel model) {
        int figurenCounter = 0; // Counter for Figuren questions

        for (int r = 0; r < model.getRowCount(); r++) {
            String number = Objects.toString(model.getValueAt(r, 0), "");
            Object questionObj = model.getValueAt(r, 1);
            String solution = Objects.toString(model.getValueAt(r, 2), "");

            if (!number.isEmpty() && questionObj != null) {
                String questionText;
                boolean isFigurenQuestion = false;

                // Handle Figuren questions (DissectedPieces objects)
                if (questionObj.getClass().getSimpleName().equals("DissectedPieces")) {
                    questionText = "Welche Figur lässt sich aus den folgenden Bausteinen zusammensetzen?";
                    isFigurenQuestion = true;
                } else {
                    questionText = questionObj.toString();
                }

                // For Figuren questions: Add page break BEFORE every 4th, 7th, 10th, etc.
                // question
                if (isFigurenQuestion) {

                    figurenCounter++;
                    // Add page break before the 4th, 7th, 10th, etc. question (every 3rd question
                    // starting from 4th)
                    if (figurenCounter > 3 && (figurenCounter - 1) % 3 == 0) {
                        addPageBreak(pkg);
                    }
                }

                // Add question with special formatting for Figuren questions
                P questionP = factory.createP();

                // For Figuren questions, add special spacing: Vor 6pt, Nach 6pt, Zeilenabstand
                // Mehrfach 1.15
                if (isFigurenQuestion) {
                    PPr questionPPr = factory.createPPr();
                    PPrBase.Spacing questionSpacing = factory.createPPrBaseSpacing();
                    questionSpacing.setBefore(BigInteger.valueOf(120)); // 6pt = 120 twips
                    questionSpacing.setAfter(BigInteger.valueOf(120)); // 6pt = 120 twips
                    questionSpacing.setLine(BigInteger.valueOf(276)); // 1.15 * 240 = 276 twips
                    questionSpacing.setLineRule(STLineSpacingRule.AUTO);
                    questionPPr.setSpacing(questionSpacing);
                    questionP.setPPr(questionPPr);
                }

                R questionR = factory.createR();
                Text questionT = factory.createText();
                questionT.setValue(number + ". " + questionText);
                questionR.getContent().add(questionT);
                questionP.getContent().add(questionR);
                pkg.getMainDocumentPart().addObject(questionP);

                // For Figuren questions, add the dissected pieces image
                if (isFigurenQuestion) {
                    try {
                        addFigurenShapeImage(pkg, questionObj);
                    } catch (Exception e) {
                        // If image generation fails, add a text representation
                        P pieceP = factory.createP();
                        R pieceR = factory.createR();
                        Text pieceT = factory.createText();
                        pieceT.setValue("Bausteine: " + questionObj.toString());
                        pieceR.getContent().add(pieceT);
                        pieceP.getContent().add(pieceR);
                        pkg.getMainDocumentPart().addObject(pieceP);
                    }
                }

                // Add options/answers if they exist in the model
                // Also skip option rows to avoid re-processing them
                r = addQuestionOptions(pkg, model, r);

                // Add solution if available
                if (!solution.isEmpty()) {
                    P solutionP = factory.createP();
                    R solutionR = factory.createR();
                    Text solutionT = factory.createText();
                    solutionT.setValue("Lösung: " + solution);
                    solutionR.getContent().add(solutionT);
                    solutionP.getContent().add(solutionR);
                    pkg.getMainDocumentPart().addObject(solutionP);
                }

                // Add spacing after non-Figuren questions only
                if (!isFigurenQuestion) {
                    addSpacing(pkg);
                }
            }
        }
    }

    /**
     * Add a stop sign page to the document with centered image or text.
     */
    public void addStopSignPage(WordprocessingMLPackage pkg) {
        // Ensure no blank page was added by previous operations
        removeTrailingPageBreak(pkg);

        // Create a new page
        addPageBreak(pkg);

        // Add multiple empty paragraphs for vertical centering
        for (int i = 0; i < 8; i++) {
            P emptyP = factory.createP();
            pkg.getMainDocumentPart().addObject(emptyP);
        }

        // Create centered paragraph for stop sign
        P centerP = factory.createP();
        PPr pPr = factory.createPPr();
        Jc jc = factory.createJc();
        jc.setVal(JcEnumeration.CENTER);
        pPr.setJc(jc);
        centerP.setPPr(pPr);

        R stopR = factory.createR();

        // Try to add stop sign image, fallback to styled text
        boolean imageAdded = false;
        try {
            imageAdded = addStopSignImage(pkg, stopR);
        } catch (Exception e) {
            System.out.println("Could not add stop sign image: " + e.getMessage());
        }

        if (!imageAdded) {
            // Fallback: Add large styled "STOP" text
            RPr rPr = factory.createRPr();
            HpsMeasure fontSize = factory.createHpsMeasure();
            fontSize.setVal(java.math.BigInteger.valueOf(72)); // 36pt font
            rPr.setSz(fontSize);
            rPr.setSzCs(fontSize);

            // Make it bold
            BooleanDefaultTrue bold = factory.createBooleanDefaultTrue();
            rPr.setB(bold);
            rPr.setBCs(bold);

            stopR.setRPr(rPr);

            Text stopText = factory.createText();
            stopText.setValue("STOP");
            stopR.getContent().add(stopText);
        }

        centerP.getContent().add(stopR);
        pkg.getMainDocumentPart().addObject(centerP);

        // Add more empty paragraphs for bottom spacing
        for (int i = 0; i < 8; i++) {
            P emptyP = factory.createP();
            pkg.getMainDocumentPart().addObject(emptyP);
        }
    }

    /** Remove trailing page break if the last paragraph only contains a page break. */
    private void removeTrailingPageBreak(WordprocessingMLPackage pkg) {
        java.util.List<Object> content = pkg.getMainDocumentPart().getContent();
        if (content.isEmpty()) {
            return;
        }
        Object last = content.get(content.size() - 1);
        if (isPageBreakParagraph(last)) {
            content.remove(content.size() - 1);
        }
    }

    /** Check if the given object is a paragraph consisting solely of a page break. */
    private boolean isPageBreakParagraph(Object obj) {
        if (obj instanceof P p) {
            for (Object c : p.getContent()) {
                Object val = c;
                if (c instanceof javax.xml.bind.JAXBElement) {
                    val = ((javax.xml.bind.JAXBElement<?>) c).getValue();
                }
                if (val instanceof Br br && br.getType() == STBrType.PAGE) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Add a page break to the document. */
    public void addPageBreak(WordprocessingMLPackage pkg) {
        // Create a new paragraph
        P p = factory.createP();
        // Create a run inside it
        R r = factory.createR();
        // Create the page-break element
        Br br = factory.createBr();
        br.setType(STBrType.PAGE);
        // Put the break into the run, then the run into the paragraph
        r.getContent().add(br);
        p.getContent().add(r);
        // Add that paragraph to the document
        pkg.getMainDocumentPart().addObject(p);
    }

    /**
     * Append the provided introduction page objects to the document.
     */
    public void appendPage(WordprocessingMLPackage pkg, java.util.List<Object> page) {
        for (Object o : page) {
            pkg.getMainDocumentPart().addObject(o);
        }
    }

    /**
     * Add spacing between questions.
     */
    private void addSpacing(WordprocessingMLPackage pkg) {
        P spacingP = factory.createP();
        pkg.getMainDocumentPart().addObject(spacingP);
    }

    /**
     * Add Figuren shape image to the document using reflection to avoid
     * compile-time dependencies.
     */
    private void addFigurenShapeImage(WordprocessingMLPackage pkg, Object dissectedPieces) throws Exception {
        try {
            // Use reflection to access the rotatedPieces field
            java.lang.reflect.Field rotatedPiecesField = dissectedPieces.getClass().getField("rotatedPieces");
            @SuppressWarnings("unchecked")
            java.util.List<Geometry> shapes = (java.util.List<Geometry>) rotatedPiecesField.get(dissectedPieces);

            if (shapes != null && !shapes.isEmpty()) {
                // Generate image from shapes
                BufferedImage shapeImage = createShapeImage(shapes);

                // Scale to a maximum height of 4cm (~151px at 96dpi)
                int width = shapeImage.getWidth();
                int height = shapeImage.getHeight();
                final int MAX_HEIGHT_PX = (int) (4 / 2.54 * 96);
                if (height > MAX_HEIGHT_PX) {
                    double scale = MAX_HEIGHT_PX / (double) height;
                    width = (int) Math.round(width * scale);
                    height = MAX_HEIGHT_PX;
                }

                // Convert to byte array
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(shapeImage, "PNG", baos);
                byte[] imageBytes = baos.toByteArray();

                // Add image to document with explicit size and center alignment
                P imgP = factory.createP();

                // Center the dissected pieces image
                PPr imgPPr = factory.createPPr();
                Jc imgJc = factory.createJc();
                imgJc.setVal(JcEnumeration.CENTER);
                imgPPr.setJc(imgJc);
                imgP.setPPr(imgPPr);

                R imgR = factory.createR();
                addImageToRunWithSize(pkg, imgR, imageBytes, "Figuren_Pieces.png", width, height);
                imgP.getContent().add(imgR);
                pkg.getMainDocumentPart().addObject(imgP);
            }
        } catch (Exception e) {
            System.out.println("Could not generate Figuren image: " + e.getMessage());
            // Add fallback text
            P fallbackP = factory.createP();
            R fallbackR = factory.createR();
            Text fallbackT = factory.createText();
            fallbackT.setValue("Bausteine: " + dissectedPieces.toString());
            fallbackR.getContent().add(fallbackT);
            fallbackP.getContent().add(fallbackR);
            pkg.getMainDocumentPart().addObject(fallbackP);
        }
    }

    /**
     * Create a BufferedImage from geometry shapes with grey background, hand-drawn
     * appearance, and optimally cropped height.
     */
    private BufferedImage createShapeImage(java.util.List<Geometry> shapes) {
        // Calculate bounding box
        Envelope totalBounds = new Envelope();
        for (Geometry shape : shapes) {
            totalBounds.expandToInclude(shape.getEnvelopeInternal());
        }

        // Create initial large image for drawing
        int initialWidth = 600;
        int initialHeight = 400; // Start with larger height to capture all content

        BufferedImage tempImage = new BufferedImage(initialWidth, initialHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = tempImage.createGraphics();

        // Set white background (transparent in document)
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, initialWidth, initialHeight);

        // Enable anti-aliasing for smoother hand-drawn appearance
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Create hand-drawn style stroke with slight variations
        g2d.setColor(java.awt.Color.BLACK);
        float[] dashPattern = { 3.0f, 1.0f, 2.0f, 1.0f }; // Slightly irregular pattern for hand-drawn look
        BasicStroke handDrawnStroke = new BasicStroke(
                1.8f, // Slightly thinner than sharp stroke
                BasicStroke.CAP_ROUND, // Rounded caps for softer appearance
                BasicStroke.JOIN_ROUND, // Rounded joins for softer appearance
                1.0f, // Miter limit
                dashPattern, // Dash pattern for hand-drawn effect
                0.0f // Dash phase
        );
        g2d.setStroke(handDrawnStroke);

        ShapeWriter shapeWriter = new ShapeWriter();

        // Track actual drawn bounds for cropping
        int minY = initialHeight;
        int maxY = 0;

        // Calculate spacing for horizontal distribution
        double shapeSpacing = initialWidth / (double) shapes.size();
        double currentX = shapeSpacing / 2;

        for (Geometry shape : shapes) {
            try {
                // Create transform for positioning
                AffineTransform transform = new AffineTransform();

                // Scale to fit with better proportions
                Envelope shapeBounds = shape.getEnvelopeInternal();
                double scaleX = (shapeSpacing * 0.7) / shapeBounds.getWidth(); // More compact horizontally
                double scaleY = (initialHeight * 0.8) / shapeBounds.getHeight();
                double scale = Math.min(scaleX, scaleY);

                // Center the shape in its allocated space
                double centerX = (shapeBounds.getMinX() + shapeBounds.getMaxX()) / 2.0;
                double centerY = (shapeBounds.getMinY() + shapeBounds.getMaxY()) / 2.0;

                transform.translate(currentX - centerX * scale,
                        initialHeight / 2.0 - centerY * scale);
                transform.scale(scale, scale);

                // Add slight randomness for hand-drawn effect
                java.util.Random random = new java.util.Random(shape.hashCode()); // Consistent randomness per shape
                double offsetX = (random.nextDouble() - 0.5) * 2.0; // Small random offset
                double offsetY = (random.nextDouble() - 0.5) * 2.0;
                transform.translate(offsetX, offsetY);

                // Draw the shape with grey fill and black outline
                Shape awtShape = shapeWriter.toShape(shape);
                Shape transformedShape = transform.createTransformedShape(awtShape);

                // Track the actual bounds of the drawn shape for cropping
                java.awt.Rectangle shapeBounds2D = transformedShape.getBounds();
                minY = Math.min(minY, shapeBounds2D.y);
                maxY = Math.max(maxY, shapeBounds2D.y + shapeBounds2D.height);

                g2d.setColor(new java.awt.Color(180, 180, 180)); // Grey fill for shapes
                g2d.fill(transformedShape);
                g2d.setColor(java.awt.Color.BLACK);
                g2d.draw(transformedShape);

                currentX += shapeSpacing;
            } catch (Exception e) {
                System.out.println("Error drawing shape: " + e.getMessage());
            }
        }

        g2d.dispose();

        // Crop the image to the exact vertical bounds of the drawn shapes
        int padding = 2; // minimal padding to avoid cutting off stroke edges
        int cropY = Math.max(0, minY - padding);
        int cropHeight = Math.min(initialHeight - cropY, (maxY + padding) - cropY);

        BufferedImage croppedImage = new BufferedImage(initialWidth, cropHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D cropG2d = croppedImage.createGraphics();
        cropG2d.drawImage(tempImage, 0, 0, initialWidth, cropHeight, 0, cropY, initialWidth, cropY + cropHeight, null);
        cropG2d.dispose();

        return croppedImage;
    }

    /**
     * Create an image for a single option shape using grey fill and black outline -
     * optimized for table cells with tight cropping.
     */
    private BufferedImage createOptionShapeImage(String wkt) throws Exception {
        Geometry geometry = new WKTReader().read(wkt);

        // Start with larger initial dimensions for better rendering quality, then crop
        // tightly
        int initialWidth = 200; // Larger initial size for better quality
        int initialHeight = 160; // Larger initial size for better quality
        BufferedImage tempImage = new BufferedImage(initialWidth, initialHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = tempImage.createGraphics();

        // Enhanced anti-aliasing settings for crisp output
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Set white background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, initialWidth, initialHeight);

        ShapeWriter writer = new ShapeWriter();
        Envelope env = geometry.getEnvelopeInternal();
        double scaleX = initialWidth / env.getWidth();
        double scaleY = initialHeight / env.getHeight();
        double scale = Math.min(scaleX, scaleY) * 0.85; // Scale to fit with some margin for crisp rendering

        AffineTransform at = new AffineTransform();
        at.translate(initialWidth / 2.0, initialHeight / 2.0);
        at.scale(scale, -scale);
        at.translate(-env.centre().x, -env.centre().y);

        Shape shape = writer.toShape(geometry);
        Shape transformedShape = at.createTransformedShape(shape);

        // Draw with grey fill and black outline to match dissected pieces
        g2d.setColor(new java.awt.Color(180, 180, 180)); // Grey fill to match dissected pieces
        g2d.fill(transformedShape);
        g2d.setColor(java.awt.Color.BLACK);
        g2d.setStroke(new BasicStroke(1.5f)); // Appropriate stroke for option figures
        g2d.draw(transformedShape);

        g2d.dispose();

        // Apply tight cropping similar to dissected pieces
        BufferedImage croppedImage = cropOptionImageToContent(tempImage);

        // Scale to fixed width of 2.5cm (approximately 95 pixels at 96 DPI)
        int targetWidth = (int) (2.5 * 96 / 2.54); // 2.5cm = ~95 pixels at 96 DPI
        int targetHeight = (int) (croppedImage.getHeight() * ((double) targetWidth / croppedImage.getWidth()));

        BufferedImage finalImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D finalG2d = finalImage.createGraphics();
        finalG2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        finalG2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        finalG2d.drawImage(croppedImage, 0, 0, targetWidth, targetHeight, null);
        finalG2d.dispose();

        return finalImage;
    }

    /**
     * Optimized cropping method for option figures - removes all white space around
     * drawn shapes.
     */
    private BufferedImage cropOptionImageToContent(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Find the bounds of non-white content using aggressive scanning
        int minX = width, minY = height, maxX = 0, maxY = 0;
        boolean hasContent = false;

        // Very aggressive white threshold - only pure white is considered background
        int whiteThreshold = 248;

        // Scan from top to find first content row
        for (int y = 0; y < height && minY == height; y++) {
            for (int x = 0; x < width; x++) {
                if (isContentPixel(image.getRGB(x, y), whiteThreshold)) {
                    minY = y;
                    hasContent = true;
                    break;
                }
            }
        }

        // Scan from bottom to find last content row
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

        // Scan from left to find first content column within vertical bounds
        for (int x = 0; x < width && minX == width; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (isContentPixel(image.getRGB(x, y), whiteThreshold)) {
                    minX = x;
                    break;
                }
            }
        }

        // Scan from right to find last content column within vertical bounds
        for (int x = width - 1; x >= 0 && maxX == 0; x--) {
            for (int y = minY; y <= maxY; y++) {
                if (isContentPixel(image.getRGB(x, y), whiteThreshold)) {
                    maxX = x;
                    break;
                }
            }
        }

        // Add minimal padding to avoid cutting off stroke edges
        int paddingX = 2;
        int paddingY = 2;

        minX = Math.max(0, minX - paddingX);
        minY = Math.max(0, minY - paddingY);
        maxX = Math.min(width - 1, maxX + paddingX);
        maxY = Math.min(height - 1, maxY + paddingY);

        // Calculate cropped dimensions
        int croppedWidth = maxX - minX + 1;
        int croppedHeight = maxY - minY + 1;

        // Create tightly cropped image
        BufferedImage croppedImage = new BufferedImage(croppedWidth, croppedHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D cropG2d = croppedImage.createGraphics();
        cropG2d.drawImage(image, 0, 0, croppedWidth, croppedHeight, minX, minY, maxX + 1, maxY + 1, null);
        cropG2d.dispose();

        return croppedImage;
    }

    /**
     * Helper method to check if a pixel contains content (not white).
     */
    private boolean isContentPixel(int rgb, int whiteThreshold) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return red < whiteThreshold || green < whiteThreshold || blue < whiteThreshold;
    }

    /**
     * Add Figuren option images in a two-row table (images above labels) with
     * proper sizing.
     */
    private void addFigurenOptionsImages(WordprocessingMLPackage pkg, Object figurenOptionsData) {
        try {
            java.lang.reflect.Field optionsField = figurenOptionsData.getClass().getDeclaredField("options");
            optionsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Object> options = (java.util.List<Object>) optionsField.get(figurenOptionsData);
            if (options == null || options.isEmpty()) {
                return;
            }

            Tbl table = factory.createTbl();

            // Set table properties for better layout with center alignment
            TblPr tblPr = factory.createTblPr();
            TblWidth tblW = factory.createTblWidth();
            tblW.setType("auto");
            tblPr.setTblW(tblW);

            // Center the table
            Jc tblJc = factory.createJc();
            tblJc.setVal(JcEnumeration.CENTER);
            tblPr.setJc(tblJc);

            table.setTblPr(tblPr);

            Tr imageRow = factory.createTr();
            Tr labelRow = factory.createTr();

            for (Object opt : options) {
                String label = String.valueOf(opt.getClass().getMethod("getLabel").invoke(opt));
                String text = String.valueOf(opt.getClass().getMethod("getText").invoke(opt));
                String shapeData = (String) opt.getClass().getMethod("getShapeData").invoke(opt);

                // Image cell with proper sizing
                Tc imgCell = factory.createTc();

                // Set cell width to accommodate smaller images
                TcPr imgCellPr = factory.createTcPr();
                TblWidth imgCellWidth = factory.createTblWidth();
                imgCellWidth.setType("dxa");
                imgCellWidth.setW(BigInteger.valueOf(1800)); // Reduced width for smaller images
                imgCellPr.setTcW(imgCellWidth);

                // Center content vertically
                CTVerticalJc vAlign = factory.createCTVerticalJc();
                vAlign.setVal(STVerticalJc.CENTER);
                imgCellPr.setVAlign(vAlign);

                imgCell.setTcPr(imgCellPr);

                P imgP = factory.createP();
                PPr imgPPr = factory.createPPr();

                // Center alignment
                Jc jc = factory.createJc();
                jc.setVal(JcEnumeration.CENTER);
                imgPPr.setJc(jc);

                // Spacing: Vor 0pt, Nach 3pt, Einfach
                PPrBase.Spacing imgSpacing = factory.createPPrBaseSpacing();
                imgSpacing.setBefore(BigInteger.valueOf(0)); // 0pt
                imgSpacing.setAfter(BigInteger.valueOf(60)); // 3pt = 60 twips
                imgSpacing.setLine(BigInteger.valueOf(240)); // Single line spacing = 240 twips
                imgSpacing.setLineRule(STLineSpacingRule.AUTO);
                imgPPr.setSpacing(imgSpacing);

                imgP.setPPr(imgPPr);

                if ("X".equals(text) || "E".equalsIgnoreCase(label)) {
                    R r = factory.createR();

                    // Make X bold and 20pt
                    RPr rPr = factory.createRPr();
                    BooleanDefaultTrue bold = factory.createBooleanDefaultTrue();
                    rPr.setB(bold);
                    HpsMeasure fontSize = factory.createHpsMeasure();
                    fontSize.setVal(BigInteger.valueOf(40)); // 20pt = 40 half-points
                    rPr.setSz(fontSize);
                    rPr.setSzCs(fontSize); // Complex script font size
                    r.setRPr(rPr);

                    Text t = factory.createText();
                    t.setValue("X");
                    r.getContent().add(t);
                    imgP.getContent().add(r);
                } else if (shapeData != null && !shapeData.isBlank()) {
                    BufferedImage img = createOptionShapeImage(shapeData);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(img, "PNG", baos);
                    R r = factory.createR();
                    // Use actual image dimensions from the optimized cropping
                    addImageToRunWithSize(pkg, r, baos.toByteArray(), "opt_" + label + ".png", img.getWidth(),
                            img.getHeight());
                    imgP.getContent().add(r);
                }

                imgCell.getContent().add(imgP);
                imageRow.getContent().add(imgCell);

                // Label cell with matching width
                Tc labelCell = factory.createTc();

                // Set matching cell width
                TcPr labelCellPr = factory.createTcPr();
                TblWidth labelCellWidth = factory.createTblWidth();
                labelCellWidth.setType("dxa");
                labelCellWidth.setW(BigInteger.valueOf(1800)); // Same width as image cell
                labelCellPr.setTcW(labelCellWidth);

                // Center vertically for better alignment
                CTVerticalJc labelVAlign = factory.createCTVerticalJc();
                labelVAlign.setVal(STVerticalJc.CENTER);
                labelCellPr.setVAlign(labelVAlign);

                labelCell.setTcPr(labelCellPr);

                P labelP = factory.createP();
                PPr labelPPr = factory.createPPr();

                // Center alignment
                Jc labelJc = factory.createJc();
                labelJc.setVal(JcEnumeration.CENTER);
                labelPPr.setJc(labelJc);

                // Spacing: Vor 0pt, Nach 3pt, Einfach
                PPrBase.Spacing labelSpacing = factory.createPPrBaseSpacing();
                labelSpacing.setBefore(BigInteger.valueOf(0)); // 0pt
                labelSpacing.setAfter(BigInteger.valueOf(60)); // 3pt = 60 twips
                labelSpacing.setLine(BigInteger.valueOf(240)); // Single line spacing = 240 twips
                labelSpacing.setLineRule(STLineSpacingRule.AUTO);
                labelPPr.setSpacing(labelSpacing);

                labelP.setPPr(labelPPr);

                R lr = factory.createR();

                // Make labels bold
                RPr labelRPr = factory.createRPr();
                BooleanDefaultTrue labelBold = factory.createBooleanDefaultTrue();
                labelRPr.setB(labelBold);
                lr.setRPr(labelRPr);

                Text lt = factory.createText();
                lt.setValue(label); // Remove ")" from labels
                lr.getContent().add(lt);
                labelP.getContent().add(lr);
                labelCell.getContent().add(labelP);
                labelRow.getContent().add(labelCell);
            }

            table.getContent().add(imageRow);
            table.getContent().add(labelRow);
            pkg.getMainDocumentPart().addObject(table);
        } catch (Exception e) {
            System.out.println("Could not add Figuren option images: " + e.getMessage());
        }
    }

    /**
     * Add question options from the table model with vertical layout for KFF
     * subtests and proper A-E labels.
     */
    /**
     * Add question options from the table model with vertical layout for KFF
     * subtests
     * and proper A-E labels. Returns the last row index that was processed so the
     * caller can skip option rows.
     */
    private int addQuestionOptions(WordprocessingMLPackage pkg, DefaultTableModel model, int startRow) {
        // Look for options in subsequent rows
        int currentRow = startRow + 1;

        // First check if this is a Figuren question with options data
        if (currentRow < model.getRowCount()) {
            Object optObj = model.getValueAt(currentRow, 1);
            if (optObj != null && optObj.getClass().getSimpleName().equals("FigurenOptionsData")) {
                addFigurenOptionsImages(pkg, optObj);
                return currentRow; // Skip the FigurenOptionsData row
            }
        }

        java.util.List<String> optionTexts = new ArrayList<>();

        // Collect all options that follow this question - but ONLY process each option
        // row ONCE
        while (currentRow < model.getRowCount()) {
            Object rowIdentifier = model.getValueAt(currentRow, 0);

            // Stop if we encounter another question (numeric identifier)
            if (rowIdentifier != null && rowIdentifier.toString().matches("\\d+")) {
                break; // Next question encountered
            }

            // Stop if we encounter an empty identifier (end of options)
            if (rowIdentifier == null || rowIdentifier.toString().trim().isEmpty()) {
                currentRow++;
                continue;
            }

            // Check if this is an option row (should have A) through E))
            String identifier = rowIdentifier.toString().trim();
            if (identifier.matches("[A-E]\\)")) {
                Object optionObj = model.getValueAt(currentRow, 1);
                if (optionObj != null) {
                    String optionText = optionObj.toString();
                    optionTexts.add(optionText);
                }
            }
            currentRow++;
        }

        // Display options vertically (untereinander) with labels A-E
        if (!optionTexts.isEmpty()) {
            for (int i = 0; i < optionTexts.size() && i < 5; i++) { // Limit to 5 options (A-E)
                P optionP = factory.createP();

                // Set paragraph spacing for better layout
                PPr pPr = factory.createPPr();
                PPrBase.Spacing spacing = factory.createPPrBaseSpacing();
                spacing.setBefore(BigInteger.valueOf(60)); // Reduced spacing before each option
                spacing.setAfter(BigInteger.valueOf(60)); // Reduced spacing after each option
                pPr.setSpacing(spacing);
                optionP.setPPr(pPr);

                R optionR = factory.createR();

                // Remove bold formatting for option labels (nicht bold)
                // No RPr settings for bold formatting

                Text optionT = factory.createText();

                // Create proper option label (A, B, C, D, E)
                char optionLabel = (char) ('A' + i);

                // Format: "A) OptionText"
                String formattedOption = optionLabel + ") " + optionTexts.get(i);
                optionT.setValue(formattedOption);
                optionR.getContent().add(optionT);
                optionP.getContent().add(optionR);

                pkg.getMainDocumentPart().addObject(optionP);
            }

            // Add spacing after all options
            P spacingP = factory.createP();
            pkg.getMainDocumentPart().addObject(spacingP);
        }

        return currentRow - 1;
    }

    /**
     * Try to add stop sign image to the document.
     */
    private boolean addStopSignImage(WordprocessingMLPackage pkg, R run) {
        String[] possiblePaths = {
                "stopp_sign.png",
                "src/main/resources/images/stopp_sign.png",
                "resources/images/stopp_sign.png",
                "images/stopp_sign.png"
        };

        for (String path : possiblePaths) {
            try {
                File imageFile = new File(path);
                if (imageFile.exists()) {
                    FileInputStream fis = new FileInputStream(imageFile);
                    byte[] imageBytes = fis.readAllBytes();
                    fis.close();

                    addImageToRun(pkg, run, imageBytes, "stopp_sign.png");
                    return true;
                }
            } catch (Exception e) {
                // Continue to next path
            }
        }

        // Try from resources
        try {
            InputStream is = getClass().getResourceAsStream("/images/stopp_sign.png");
            if (is != null) {
                byte[] imageBytes = is.readAllBytes();
                is.close();
                addImageToRun(pkg, run, imageBytes, "stopp_sign.png");
                return true;
            }
        } catch (Exception e) {
            // Fallback to text
        }

        return false;
    }

    /**
     * Add image to a specific run.
     */
    private void addImageToRun(WordprocessingMLPackage pkg, R run, byte[] imageBytes, String filename)
            throws Exception {
        BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(pkg, imageBytes);
        Inline inline = imagePart.createImageInline(filename, "Image", 1, 2, false);

        // Create drawing object
        org.docx4j.wml.Drawing drawing = factory.createDrawing();
        drawing.getAnchorOrInline().add(inline);
        run.getContent().add(drawing);
    }

    /**
     * Add image to a specific run with explicit size control.
     */
    private void addImageToRunWithSize(WordprocessingMLPackage pkg, R run, byte[] imageBytes, String filename,
            int widthPixels, int heightPixels)
            throws Exception {
        BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(pkg, imageBytes);

        // Convert pixels to EMUs (English Metric Units) - 1 pixel = 9525 EMUs
        // approximately
        long widthEMU = widthPixels * 9525L;
        long heightEMU = heightPixels * 9525L;

        Inline inline = imagePart.createImageInline(filename, "Image", 1, 2, false);

        // Set explicit dimensions
        if (inline.getExtent() != null) {
            inline.getExtent().setCx(widthEMU);
            inline.getExtent().setCy(heightEMU);
        }

        // Create drawing object
        org.docx4j.wml.Drawing drawing = factory.createDrawing();
        drawing.getAnchorOrInline().add(inline);
        run.getContent().add(drawing);
    }
}
