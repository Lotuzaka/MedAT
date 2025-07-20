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
     * Append the given question table to the document with proper handling for different question types.
     */
    public void addQuestions(WordprocessingMLPackage pkg, DefaultTableModel model) {
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
                
                // Add question text
                P questionP = factory.createP();
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
                addQuestionOptions(pkg, model, r);
                
                // Add spacing after each question
                addSpacing(pkg);
            }
        }
    }

    /**
     * Add questions with solutions to the document with proper handling for different question types.
     */
    public void addQuestionsSolution(WordprocessingMLPackage pkg, DefaultTableModel model) {
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
                
                // Add question
                P questionP = factory.createP();
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
                addQuestionOptions(pkg, model, r);
                
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
                
                // Add spacing after each question
                addSpacing(pkg);
            }
        }
    }

    /**
     * Add a stop sign page to the document with centered image or text.
     */
    public void addStopSignPage(WordprocessingMLPackage pkg) {
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
        
        addPageBreak(pkg);
    }

    /** Add a page break to the document. */
    public void addPageBreak(WordprocessingMLPackage pkg) {
        P p = factory.createP();
        Br br = factory.createBr();
        br.setType(STBrType.PAGE);
        p.getContent().add(br);
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
     * Add Figuren shape image to the document using reflection to avoid compile-time dependencies.
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
                
                // Convert to byte array
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(shapeImage, "PNG", baos);
                byte[] imageBytes = baos.toByteArray();
                
                // Add image to document
                addImageToDocument(pkg, imageBytes, "Figuren_Pieces.png");
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
     * Create a BufferedImage from geometry shapes with grey background and hand-drawn appearance.
     */
    private BufferedImage createShapeImage(java.util.List<Geometry> shapes) {
        // Calculate bounding box
        Envelope totalBounds = new Envelope();
        for (Geometry shape : shapes) {
            totalBounds.expandToInclude(shape.getEnvelopeInternal());
        }
        
        // Create image with appropriate dimensions for better horizontal spacing
        int imageWidth = 600;
        int imageHeight = 200;
        
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        // Set grey background (like in UI)
        g2d.setColor(new java.awt.Color(240, 240, 240)); // Light grey background
        g2d.fillRect(0, 0, imageWidth, imageHeight);
        
        // Enable anti-aliasing for smoother hand-drawn appearance
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        
        // Create hand-drawn style stroke with slight variations
        g2d.setColor(java.awt.Color.BLACK);
        float[] dashPattern = {3.0f, 1.0f, 2.0f, 1.0f}; // Slightly irregular pattern for hand-drawn look
        BasicStroke handDrawnStroke = new BasicStroke(
            1.8f,                           // Slightly thinner than sharp stroke
            BasicStroke.CAP_ROUND,          // Rounded caps for softer appearance
            BasicStroke.JOIN_ROUND,         // Rounded joins for softer appearance
            1.0f,                           // Miter limit
            dashPattern,                    // Dash pattern for hand-drawn effect
            0.0f                            // Dash phase
        );
        g2d.setStroke(handDrawnStroke);
        
        ShapeWriter shapeWriter = new ShapeWriter();
        
        // Calculate spacing for horizontal distribution (improved spacing logic)
        double shapeSpacing = imageWidth / (double) shapes.size();
        double currentX = shapeSpacing / 2;
        
        for (Geometry shape : shapes) {
            try {
                // Create transform for positioning
                AffineTransform transform = new AffineTransform();
                
                // Scale to fit with better proportions
                Envelope shapeBounds = shape.getEnvelopeInternal();
                double scaleX = (shapeSpacing * 0.7) / shapeBounds.getWidth(); // More compact horizontally
                double scaleY = (imageHeight * 0.8) / shapeBounds.getHeight();
                double scale = Math.min(scaleX, scaleY);
                
                // Center the shape in its allocated space
                double centerX = (shapeBounds.getMinX() + shapeBounds.getMaxX()) / 2.0;
                double centerY = (shapeBounds.getMinY() + shapeBounds.getMaxY()) / 2.0;
                
                transform.translate(currentX - centerX * scale, 
                                   imageHeight / 2.0 - centerY * scale);
                transform.scale(scale, scale);
                
                // Add slight randomness for hand-drawn effect
                java.util.Random random = new java.util.Random(shape.hashCode()); // Consistent randomness per shape
                double offsetX = (random.nextDouble() - 0.5) * 2.0; // Small random offset
                double offsetY = (random.nextDouble() - 0.5) * 2.0;
                transform.translate(offsetX, offsetY);
                
                // Draw the shape with hand-drawn appearance
                Shape awtShape = shapeWriter.toShape(shape);
                Shape transformedShape = transform.createTransformedShape(awtShape);
                g2d.setColor(new Color(200, 200, 200));
                g2d.fill(transformedShape);
                g2d.setColor(Color.BLACK);
                g2d.draw(transformedShape);
                
                currentX += shapeSpacing;
            } catch (Exception e) {
                System.out.println("Error drawing shape: " + e.getMessage());
            }
        }
        
        g2d.dispose();
        return image;
    }

    /**
     * Create an image for a single option shape using grey fill and black outline.
     */
    private BufferedImage createOptionShapeImage(String wkt) throws Exception {
        Geometry geometry = new WKTReader().read(wkt);

        int width = 300;
        int height = 240;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        ShapeWriter writer = new ShapeWriter();
        Envelope env = geometry.getEnvelopeInternal();
        double scaleX = width / env.getWidth();
        double scaleY = height / env.getHeight();
        double scale = Math.min(scaleX, scaleY) * 0.9;

        AffineTransform at = new AffineTransform();
        at.translate(width / 2.0, height / 2.0);
        at.scale(scale, -scale);
        at.translate(-env.centre().x, -env.centre().y);

        Shape shape = writer.toShape(geometry);
        Shape ts = at.createTransformedShape(shape);
        g2d.setColor(new Color(200, 200, 200));
        g2d.fill(ts);
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.draw(ts);

        g2d.dispose();
        return image;
    }

    /**
     * Add Figuren option images in a two-row table (images above labels).
     */
    private void addFigurenOptionsImages(WordprocessingMLPackage pkg, Object figurenOptionsData) {
        try {
            java.lang.reflect.Field optionsField = figurenOptionsData.getClass().getField("options");
            @SuppressWarnings("unchecked")
            java.util.List<Object> options = (java.util.List<Object>) optionsField.get(figurenOptionsData);
            if (options == null || options.isEmpty()) {
                return;
            }

            Tbl table = factory.createTbl();
            Tr imageRow = factory.createTr();
            Tr labelRow = factory.createTr();

            for (Object opt : options) {
                String label = String.valueOf(opt.getClass().getMethod("getLabel").invoke(opt));
                String text = String.valueOf(opt.getClass().getMethod("getText").invoke(opt));
                String shapeData = (String) opt.getClass().getMethod("getShapeData").invoke(opt);

                // Image cell
                Tc imgCell = factory.createTc();
                P imgP = factory.createP();

                if ("X".equals(text) || "E".equalsIgnoreCase(label)) {
                    R r = factory.createR();
                    Text t = factory.createText();
                    t.setValue("X");
                    r.getContent().add(t);
                    imgP.getContent().add(r);
                } else if (shapeData != null && !shapeData.isBlank()) {
                    BufferedImage img = createOptionShapeImage(shapeData);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(img, "PNG", baos);
                    R r = factory.createR();
                    addImageToRun(pkg, r, baos.toByteArray(), "opt_" + label + ".png");
                    imgP.getContent().add(r);
                }

                imgCell.getContent().add(imgP);
                imageRow.getContent().add(imgCell);

                // Label cell
                Tc labelCell = factory.createTc();
                P labelP = factory.createP();
                R lr = factory.createR();
                Text lt = factory.createText();
                lt.setValue(label + ")");
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
     * Add question options from the table model with horizontal layout and proper A-E labels.
     */
    private void addQuestionOptions(WordprocessingMLPackage pkg, DefaultTableModel model, int startRow) {
        // Look for options in subsequent rows
        int currentRow = startRow + 1;

        if (currentRow < model.getRowCount()) {
            Object optObj = model.getValueAt(currentRow, 1);
            if (optObj != null && optObj.getClass().getSimpleName().equals("FigurenOptionsData")) {
                addFigurenOptionsImages(pkg, optObj);
                return;
            }
        }

        java.util.List<String> optionTexts = new ArrayList<>();
        
        // Collect all options first
        while (currentRow < model.getRowCount()) {
            Object rowIdentifier = model.getValueAt(currentRow, 0);
            if (rowIdentifier != null && !rowIdentifier.toString().trim().isEmpty() && 
                !rowIdentifier.toString().matches("\\d+")) {
                // This looks like an option
                Object optionObj = model.getValueAt(currentRow, 1);
                if (optionObj != null) {
                    String optionText;
                    if (optionObj.getClass().getSimpleName().equals("FigurenOptionsData")) {
                        try {
                            // Use reflection to access option data
                            java.lang.reflect.Field optionsField = optionObj.getClass().getField("options");
                            @SuppressWarnings("unchecked")
                            java.util.List<Object> options = (java.util.List<Object>) optionsField.get(optionObj);
                            if (options != null && !options.isEmpty()) {
                                optionText = "[Figur Option]";
                            } else {
                                optionText = optionObj.toString();
                            }
                        } catch (Exception e) {
                            optionText = optionObj.toString();
                        }
                    } else {
                        optionText = optionObj.toString();
                    }
                    optionTexts.add(optionText);
                }
                currentRow++;
            } else {
                break; // No more options
            }
        }
        
        // Now display options horizontally with proper labels (A, B, C, D, E where E is "X")
        if (!optionTexts.isEmpty()) {
            // Create a paragraph for horizontal option layout
            P optionsP = factory.createP();
            
            // Set paragraph spacing for better layout
            PPr pPr = factory.createPPr();
            PPrBase.Spacing spacing = factory.createPPrBaseSpacing();
            spacing.setBefore(BigInteger.valueOf(120)); // Space before options
            spacing.setAfter(BigInteger.valueOf(120));  // Space after options
            pPr.setSpacing(spacing);
            optionsP.setPPr(pPr);
            
            for (int i = 0; i < optionTexts.size() && i < 5; i++) { // Limit to 5 options (A-E)
                if (i > 0) {
                    // Add spacing between options
                    R spacingR = factory.createR();
                    Text spacingT = factory.createText();
                    spacingT.setValue("        "); // Multiple spaces for horizontal separation
                    spacingR.getContent().add(spacingT);
                    optionsP.getContent().add(spacingR);
                }
                
                R optionR = factory.createR();
                
                // Set bold formatting for option labels
                RPr rPr = factory.createRPr();
                BooleanDefaultTrue bold = factory.createBooleanDefaultTrue();
                rPr.setB(bold);
                optionR.setRPr(rPr);
                
                Text optionT = factory.createText();
                
                // Create proper option label (A, B, C, D, X for the 5th option)
                char optionLabel;
                if (i == 4) { // 5th option (index 4) should be "X"
                    optionLabel = 'X';
                } else {
                    optionLabel = (char) ('A' + i);
                }
                
                // Format: "A) OptionText"
                String formattedOption = optionLabel + ") " + optionTexts.get(i);
                optionT.setValue(formattedOption);
                optionR.getContent().add(optionT);
                optionsP.getContent().add(optionR);
            }
            
            pkg.getMainDocumentPart().addObject(optionsP);
            
            // Add extra spacing after options
            P spacingP = factory.createP();
            R spacingR = factory.createR();
            Text spacingT = factory.createText();
            spacingT.setValue(" "); // Empty line for spacing
            spacingR.getContent().add(spacingT);
            spacingP.getContent().add(spacingR);
            pkg.getMainDocumentPart().addObject(spacingP);
        }
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
     * Add image to document as a new paragraph.
     */
    private void addImageToDocument(WordprocessingMLPackage pkg, byte[] imageBytes, String filename) throws Exception {
        P imageP = factory.createP();
        R imageR = factory.createR();
        addImageToRun(pkg, imageR, imageBytes, filename);
        imageP.getContent().add(imageR);
        pkg.getMainDocumentPart().addObject(imageP);
    }
    
    /**
     * Add image to a specific run.
     */
    private void addImageToRun(WordprocessingMLPackage pkg, R run, byte[] imageBytes, String filename) throws Exception {
        BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(pkg, imageBytes);
        Inline inline = imagePart.createImageInline(filename, "Image", 1, 2, false);
        
        // Create drawing object
        org.docx4j.wml.Drawing drawing = factory.createDrawing();
        drawing.getAnchorOrInline().add(inline);
        run.getContent().add(drawing);
    }
}
