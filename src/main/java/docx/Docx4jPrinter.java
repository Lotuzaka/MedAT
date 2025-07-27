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

import jakarta.xml.bind.JAXBElement;

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
     * Read the .docx, split on <w:br w:type="page"/>,
     * then for each page extract the first paragraph’s text
     * (e.g. "Basiskenntnistest - Biologie") and use it as the key.
     */
    public Map<String, List<Object>> loadIntroductionPagesMap(File docx) throws Docx4JException {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.load(docx);
        List<Object> content = pkg.getMainDocumentPart().getContent();
        Map<String, List<Object>> map = new LinkedHashMap<>();
        List<Object> current = new ArrayList<>();
        for (Object o : content) {
            current.add(o);
            if (containsPageBreak(o)) {
                String title = extractFirstParagraphText(current).toLowerCase().trim();
                map.put(title, new ArrayList<>(current));
                current.clear();
            }
        }
        if (!current.isEmpty()) {
            String title = extractFirstParagraphText(current).toLowerCase().trim();
            map.put(title, current);
        }
        return map;
    }

    private String extractFirstParagraphText(List<Object> page) {
        for (Object o : page) {
            if (o instanceof P p) {
                for (Object x : p.getContent()) {
                    Object val = x instanceof JAXBElement<?> je ? je.getValue() : x;
                    if (val instanceof Text t) {
                        return t.getValue();
                    }
                }
            }
        }
        return "";
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
 * Build a document by, for each subcategory in `order`:
 *  1. looking up its intro-page in `introPagesMap` (by exact or contains key),
 *  2. inserting that intro page + a page break,
 *  3. adding the questions,
 *  4. adding a page break between sections (but not after the last one).
 */
public WordprocessingMLPackage buildDocument(
        Map<String, DefaultTableModel> subcats,
        List<String> order,
        Map<String,List<Object>> introPagesMap
    ) throws Docx4JException {

    WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();

    for (int i = 0; i < order.size(); i++) {
        String rawSubcat = order.get(i);
        String key = rawSubcat.toLowerCase().trim();

        // 1) find matching intro page
        List<Object> intro = introPagesMap.get(key);
        if (intro == null) {
            // fallback: any map-key that contains our subcat name
            for (String cand : introPagesMap.keySet()) {
                if (cand.contains(key)) {
                    intro = introPagesMap.get(cand);
                    break;
                }
            }
        }

        // 2) insert intro + break
        if (intro != null) {
            for (Object o : intro) {
                pkg.getMainDocumentPart().addObject(o);
            }
            addPageBreak(pkg);
        }

        // 3) render questions for this subcategory
        DefaultTableModel model = subcats.get(rawSubcat);
        if (model != null) {
            addQuestions(pkg, model);
        }

        // 4) break *between* subsections, but not after the last one
        if (i < order.size() - 1) {
            addPageBreak(pkg);
        }
    }

    return pkg;
}

    /**
     * Append the given question table to the document with proper handling for
     * different question types.
     */
    public void addQuestions(WordprocessingMLPackage pkg, DefaultTableModel model) {
        int figurenCounter = 0; // Counter for Figuren questions
        int nonFigCounter = 0; // Counter for non-Figuren questions
        boolean isFirstQuestionOnPage = true; // Track if this is the first question on each page

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

                // Add page break for non-Figuren questions every 5 questions
                if (!isFigurenQuestion) {
                    nonFigCounter++;
                    if (nonFigCounter > 5 && (nonFigCounter - 1) % 5 == 0) {
                        // Remove any existing trailing breaks to avoid blank page
                        removeTrailingPageBreak(pkg);
                        addPageBreak(pkg);
                        isFirstQuestionOnPage = true; // Reset for new page
                    }
                }

                // For Figuren questions: existing page break logic
                if (isFigurenQuestion) {
                    figurenCounter++;
                    if (figurenCounter > 3 && (figurenCounter - 1) % 3 == 0) {
                        // Clean any trailing breaks to avoid blank page before new section
                        removeTrailingPageBreak(pkg);
                        addPageBreak(pkg);
                        isFirstQuestionOnPage = true; // Reset for new page
                    }
                }

                // Add question text with special formatting for Figuren questions
                P questionP = factory.createP();
                // For non-Figuren first question on a page, only reset spacing before (not after)
                if (!isFigurenQuestion && isFirstQuestionOnPage) {
                    PPr noSpacingPr = factory.createPPr();
                    PPrBase.Spacing firstQuestionSpacing = factory.createPPrBaseSpacing();
                    firstQuestionSpacing.setBefore(BigInteger.ZERO); // No space before first question
                    firstQuestionSpacing.setAfter(BigInteger.valueOf(120)); // But keep space after for next question
                    noSpacingPr.setSpacing(firstQuestionSpacing);
                    questionP.setPPr(noSpacingPr);
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
                r = addQuestionOptions(pkg, model, r, isFirstQuestionOnPage);

                // Reset the first question flag after processing the first question
                isFirstQuestionOnPage = false;
            }
        }

        // Ensure no leftover page break after the last question
        removeTrailingPageBreak(pkg);
    }

    /**
     * Add questions with solutions to the document with proper handling for
     * different question types.
     */
    public void addQuestionsSolution(WordprocessingMLPackage pkg, DefaultTableModel model) {
        int figurenCounter = 0; // Counter for Figuren questions
        int nonFigCounter = 0; // Counter for non-Figuren questions
        boolean isFirstQuestionOnPage = true; // Track if first question on a new page

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

                // Page break for non-Figuren questions every 5 questions (before 6th, 11th,
                // etc.)
                if (!isFigurenQuestion) {
                    nonFigCounter++;
                    if (nonFigCounter > 5 && (nonFigCounter - 1) % 5 == 0) {
                        addPageBreak(pkg);
                        isFirstQuestionOnPage = true; // Start new page
                    }
                }

                // For Figuren questions: existing page break logic
                if (isFigurenQuestion) {
                    figurenCounter++;
                    if (figurenCounter > 3 && (figurenCounter - 1) % 3 == 0) {
                        addPageBreak(pkg);
                        isFirstQuestionOnPage = true; // Reset for new page
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
                r = addQuestionOptions(pkg, model, r, isFirstQuestionOnPage);

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

                // Reset the first question flag after processing the first question
                isFirstQuestionOnPage = false;
            }
        }

        // Clean up any trailing page break from the questions
        removeTrailingPageBreak(pkg);
    }

    /**
     * Add a stop sign page to the document with centered image or text.
     */
    public void addStopSignPage(WordprocessingMLPackage pkg) {
        // Ensure no leftover page break from the previous section
        removeTrailingPageBreak(pkg);

        // Start the STOP page on a new sheet
        addPageBreak(pkg);

        // Add a few empty paragraphs for simple vertical centering
        for (int i = 0; i < 5; i++) {
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

        // Minimal bottom spacing so the next page begins flush at the top
        P bottom = factory.createP();
        pkg.getMainDocumentPart().addObject(bottom);

        // Start next section on a new page
        addPageBreak(pkg);
    }

    /**
     * Remove trailing page breaks and empty paragraphs to avoid blank pages
     * before inserting the next section.
     */
    private void removeTrailingPageBreak(WordprocessingMLPackage pkg) {
        java.util.List<Object> content = pkg.getMainDocumentPart().getContent();
        // Remove any empty trailing paragraphs
        while (!content.isEmpty() && isEmptyParagraph(content.get(content.size() - 1))) {
            content.remove(content.size() - 1);
        }

        // Remove a trailing page break paragraph if present
        if (!content.isEmpty() && isPageBreakParagraph(content.get(content.size() - 1))) {
            content.remove(content.size() - 1);

            // Also remove any empty paragraphs that may precede the page break
            while (!content.isEmpty() && isEmptyParagraph(content.get(content.size() - 1))) {
                content.remove(content.size() - 1);
            }
        }
    }

    /** Check if the given object is an empty paragraph with no content. */
    private boolean isEmptyParagraph(Object obj) {
        return obj instanceof P p && p.getContent().isEmpty();
    }

    /**
     * Check if the given object is a paragraph consisting solely of a page break.
     */
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
     * Add an introduction page with the given text content.
     * This creates a formatted introduction page with the provided text.
     */
    public void addIntroductionPage(WordprocessingMLPackage pkg, String introText) {
        if (introText == null || introText.trim().isEmpty()) {
            return; // Don't add empty intro pages
        }

        // Split the intro text by newlines
        String[] lines = introText.split("\\n");
        
        // Find key sections
        String headerLine = "";
        String timingLine = "";
        List<String> instructionLines = new ArrayList<>();
        List<String> exampleLines = new ArrayList<>();
        
        boolean inInstructions = false;
        boolean inExample = false;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (headerLine.isEmpty() && (line.contains("Basiskenntnistest") || 
                line.contains("Figuren") || line.contains("Gedächtnis") || 
                line.contains("Zahlenfolgen") || line.contains("Wortflüssigkeit") ||
                line.contains("Implikationen") || line.contains("Emotionen") || 
                line.contains("Soziales") || line.contains("Textverständnis"))) {
                headerLine = line;
            } else if (line.startsWith("Bearbeitungszeit") || line.startsWith("Lernzeit")) {
                timingLine = line;
            } else if (line.startsWith("Beispielaufgabe:") || line.startsWith("Beispieltext:") || line.startsWith("Beispielausweis:")) {
                inExample = true;
                inInstructions = false;
                exampleLines.add(line);
            } else if (inExample) {
                exampleLines.add(line);
            } else if (line.startsWith("Die folgenden Aufgaben") || 
                       line.startsWith("In diesem Untertest") ||
                       line.startsWith("Die Aufgaben sind im Single-Choice") ||
                       line.startsWith("Bitte markieren Sie") ||
                       line.startsWith("Das Zurückblättern") ||
                       line.startsWith("Sie dürfen mit der Bearbeitung") ||
                       inInstructions) {
                inInstructions = true;
                instructionLines.add(line);
                if (line.contains("freigegeben hat")) {
                    inInstructions = false;
                }
            }
        }
        
        // 1. Add header - Bold, Montserrat 18pt, Vor 0pt, Nach 0pt, Einfach
        if (!headerLine.isEmpty()) {
            addFormattedParagraph(pkg, headerLine, true, 36, 0, 0, false, JcEnumeration.LEFT, "Montserrat");
        }
        
        // 2. Add timing line - Bold, Montserrat, 11pt, Vor 6pt, Nach 18pt, Mehrfach 1.15
        if (!timingLine.isEmpty()) {
            addFormattedParagraph(pkg, timingLine, true, 22, 120, 360, true, JcEnumeration.LEFT, "Montserrat");
        }
        
        // 3. Add instructions in a 1x1 table
        if (!instructionLines.isEmpty()) {
            addInstructionsTable(pkg, instructionLines);
        }
        
        // 4. Add example section with "Beispielaufgabe:" bold
        for (String exampleLine : exampleLines) {
            if (exampleLine.startsWith("Beispielaufgabe:") || exampleLine.startsWith("Beispieltext:") || exampleLine.startsWith("Beispielausweis:")) {
                // Example question header: Bold, Aptos 11pt, Vor 0pt, Nach 10pt, Mehrfach 1.15
                addFormattedParagraph(pkg, exampleLine, true, 22, 0, 200, true, JcEnumeration.LEFT, "Aptos");
            } else if (exampleLine.equals("Welche Figur lässt sich aus den folgenden Bausteinen zusammensetzen?") && 
                      headerLine.contains("Figuren")) {
                // Add the question text
                addFormattedParagraph(pkg, exampleLine, false, 22, 0, 200, true, JcEnumeration.LEFT, "Aptos");
                // Add the figure image for Figuren Zusammensetzen
                addFigurenExampleImage(pkg);
            } else if (exampleLine.matches("^[A-E]\\).*")) {
                // Answer options: Aptos 11pt, Vor 3pt, Nach 3pt, Mehrfach 1.15
                addFormattedParagraph(pkg, exampleLine, false, 22, 60, 60, true, JcEnumeration.LEFT, "Aptos");
            } else {
                // Other example content: Aptos 11pt, default spacing
                addFormattedParagraph(pkg, exampleLine, false, 22, 120, 120, false, JcEnumeration.LEFT, "Aptos");
            }
        }
        
        // Add a page break after the introduction
        addPageBreak(pkg);
    }

    /**
     * Helper method to add a formatted paragraph with specific styling
     */
    private void addFormattedParagraph(WordprocessingMLPackage pkg, String text, boolean bold, 
                                     int fontSize, int spaceBefore, int spaceAfter, 
                                     boolean multipleSpacing, JcEnumeration alignment, String fontName) {
        P paragraph = factory.createP();
        PPr pPr = factory.createPPr();
        
        // Set alignment
        if (alignment != null) {
            Jc jc = factory.createJc();
            jc.setVal(alignment);
            pPr.setJc(jc);
        }
        
        // Set spacing
        PPrBase.Spacing spacing = factory.createPPrBaseSpacing();
        if (spaceBefore > 0) {
            spacing.setBefore(BigInteger.valueOf(spaceBefore));
        }
        if (spaceAfter > 0) {
            spacing.setAfter(BigInteger.valueOf(spaceAfter));
        }
        if (multipleSpacing) {
            spacing.setLine(BigInteger.valueOf(276)); // 1.15 line spacing (240 * 1.15)
            spacing.setLineRule(STLineSpacingRule.AUTO);
        }
        pPr.setSpacing(spacing);
        
        paragraph.setPPr(pPr);
        
        // Create run with text formatting
        R run = factory.createR();
        RPr rPr = factory.createRPr();
        
        // Set font
        if (fontName != null && !fontName.isEmpty()) {
            RFonts fonts = factory.createRFonts();
            fonts.setAscii(fontName);
            fonts.setHAnsi(fontName);
            rPr.setRFonts(fonts);
        }
        
        // Set font size
        if (fontSize > 0) {
            HpsMeasure size = factory.createHpsMeasure();
            size.setVal(BigInteger.valueOf(fontSize));
            rPr.setSz(size);
            rPr.setSzCs(size); // For complex scripts
        }
        
        // Set bold
        if (bold) {
            BooleanDefaultTrue boldProp = factory.createBooleanDefaultTrue();
            boldProp.setVal(true);
            rPr.setB(boldProp);
            rPr.setBCs(boldProp); // For complex scripts
        }
        
        run.setRPr(rPr);
        
        // Add text
        Text textElement = factory.createText();
        textElement.setValue(text);
        run.getContent().add(textElement);
        paragraph.getContent().add(run);
        
        pkg.getMainDocumentPart().addObject(paragraph);
    }
    
    /**
     * Helper method to add instructions in a 1x1 table
     */
    private void addInstructionsTable(WordprocessingMLPackage pkg, List<String> instructionLines) {
        // Create table
        Tbl table = factory.createTbl();
        
        // Table properties
        TblPr tblPr = factory.createTblPr();
        
        // Table width - full page width
        TblWidth tblWidth = factory.createTblWidth();
        tblWidth.setType("pct");
        tblWidth.setW(BigInteger.valueOf(5000)); // 100% width
        tblPr.setTblW(tblWidth);
        
        // Table borders - 1/2 pt black border
        TblBorders tblBorders = factory.createTblBorders();
        CTBorder border = factory.createCTBorder();
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4)); // 1/2 pt = 4 eighths of a point
        border.setColor("000000"); // Black color
        tblBorders.setTop(border);
        tblBorders.setLeft(border);
        tblBorders.setBottom(border);
        tblBorders.setRight(border);
        tblBorders.setInsideH(border);
        tblBorders.setInsideV(border);
        tblPr.setTblBorders(tblBorders);
        
        table.setTblPr(tblPr);
        
        // Create single row
        Tr row = factory.createTr();
        
        // Create single cell
        Tc cell = factory.createTc();
        
        // Cell properties
        TcPr tcPr = factory.createTcPr();
        
        // Cell width
        TblWidth cellWidth = factory.createTblWidth();
        cellWidth.setType("pct");
        cellWidth.setW(BigInteger.valueOf(5000)); // 100% of table width
        tcPr.setTcW(cellWidth);
        
        cell.setTcPr(tcPr);
        
        // Add instruction text to cell
        for (String line : instructionLines) {
            P paragraph = factory.createP();
            PPr pPr = factory.createPPr();
            
            // Standard paragraph spacing
            PPrBase.Spacing spacing = factory.createPPrBaseSpacing();
            spacing.setAfter(BigInteger.valueOf(120)); // 6pt spacing after
            pPr.setSpacing(spacing);
            
            paragraph.setPPr(pPr);
            
            // Create run
            R run = factory.createR();
            RPr rPr = factory.createRPr();
            
            // Set font to Aptos 11pt
            RFonts fonts = factory.createRFonts();
            fonts.setAscii("Aptos");
            fonts.setHAnsi("Aptos");
            rPr.setRFonts(fonts);
            
            // Standard font size
            HpsMeasure size = factory.createHpsMeasure();
            size.setVal(BigInteger.valueOf(22)); // 11pt
            rPr.setSz(size);
            rPr.setSzCs(size);
            
            run.setRPr(rPr);
            
            // Add text
            Text text = factory.createText();
            text.setValue(line);
            run.getContent().add(text);
            paragraph.getContent().add(run);
            
            cell.getContent().add(paragraph);
        }
        
        row.getContent().add(cell);
        table.getContent().add(row);
        
        pkg.getMainDocumentPart().addObject(table);
    }
    
    /**
     * Add the Figuren example image
     */
    private void addFigurenExampleImage(WordprocessingMLPackage pkg) {
        try {
            // Create a simple geometric figure example programmatically
            BufferedImage image = createFigurenExampleImage();
            
            // Convert to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            
            // Add image to document
            BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(pkg, imageBytes);
            
            // Create inline image
            Inline inline = imagePart.createImageInline("Figuren Example", "Figuren pieces example", 0, 1, false);
            
            // Create paragraph with image
            P paragraph = factory.createP();
            PPr pPr = factory.createPPr();
            
            // Center the image
            Jc jc = factory.createJc();
            jc.setVal(JcEnumeration.CENTER);
            pPr.setJc(jc);
            
            // Add spacing
            PPrBase.Spacing spacing = factory.createPPrBaseSpacing();
            spacing.setBefore(BigInteger.valueOf(120));
            spacing.setAfter(BigInteger.valueOf(120));
            pPr.setSpacing(spacing);
            
            paragraph.setPPr(pPr);
            
            // Create run and add image
            R run = factory.createR();
            Drawing drawing = factory.createDrawing();
            drawing.getAnchorOrInline().add(inline);
            run.getContent().add(drawing);
            paragraph.getContent().add(run);
            
            pkg.getMainDocumentPart().addObject(paragraph);
            
        } catch (Exception e) {
            System.err.println("Error adding Figuren example image: " + e.getMessage());
            // Add text fallback
            addFormattedParagraph(pkg, "[Hier würden die Bausteine als Bild angezeigt werden]", 
                                false, 22, 120, 120, false, JcEnumeration.CENTER, "Calibri");
        }
    }
    
    /**
     * Create the exact Figuren example image as shown in the "Pasted Image"
     */
    private BufferedImage createFigurenExampleImage() {
        int width = 500;
        int height = 250;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        // Set rendering hints for better quality
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        // Fill background with white
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        
        // Set drawing color to dark gray
        g2d.setColor(new Color(96, 96, 96));
        g2d.setStroke(new BasicStroke(1.5f));
        
        // TOP ROW: The pieces to be assembled
        
        // Piece 1: Diamond/Rhombus
        int[] x1 = {50, 80, 50, 20};
        int[] y1 = {20, 50, 80, 50};
        g2d.fillPolygon(x1, y1, 4);
        
        // Piece 2: Triangle
        int[] x2 = {130, 160, 145};
        int[] y2 = {20, 50, 80};
        g2d.fillPolygon(x2, y2, 3);
        
        // Piece 3: Parallelogram
        int[] x3 = {200, 250, 230, 180};
        int[] y3 = {20, 20, 80, 80};
        g2d.fillPolygon(x3, y3, 4);
        
        // Piece 4: Pentagon
        int[] x4 = {330, 360, 370, 340, 310};
        int[] y4 = {20, 30, 70, 90, 60};
        g2d.fillPolygon(x4, y4, 5);
        
        // BOTTOM ROW: Answer choices
        
        // A: Pentagon
        int[] xa = {30, 60, 70, 45, 15};
        int[] ya = {110, 120, 160, 180, 150};
        g2d.fillPolygon(xa, ya, 5);
        
        // B: Hexagon
        int[] xb = {110, 140, 150, 140, 110, 100};
        int[] yb = {120, 120, 150, 180, 180, 150};
        g2d.fillPolygon(xb, yb, 6);
        
        // C: Heptagon (7-sided)
        int[] xc = new int[7];
        int[] yc = new int[7];
        int centerX = 220, centerY = 150, radius = 35;
        for (int i = 0; i < 7; i++) {
            double angle = 2 * Math.PI * i / 7 - Math.PI/2;
            xc[i] = centerX + (int)(radius * Math.cos(angle));
            yc[i] = centerY + (int)(radius * Math.sin(angle));
        }
        g2d.fillPolygon(xc, yc, 7);
        
        // D: Octagon (8-sided)
        int[] xd = new int[8];
        int[] yd = new int[8];
        centerX = 320; centerY = 150; radius = 35;
        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8 - Math.PI/2;
            xd[i] = centerX + (int)(radius * Math.cos(angle));
            yd[i] = centerY + (int)(radius * Math.sin(angle));
        }
        g2d.fillPolygon(xd, yd, 8);
        
        // Add labels
        g2d.setColor(Color.BLACK);
        java.awt.Font labelFont = new java.awt.Font("Arial", java.awt.Font.PLAIN, 14);
        g2d.setFont(labelFont);
        
        // Labels below each answer choice
        g2d.drawString("A", 45, 200);
        g2d.drawString("B", 125, 200);
        g2d.drawString("C", 205, 200);
        g2d.drawString("D", 305, 200);
        g2d.drawString("E", 420, 200);
        
        // "Keine der figuren ist richtig" text for option E
        java.awt.Font smallFont = new java.awt.Font("Arial", java.awt.Font.PLAIN, 10);
        g2d.setFont(smallFont);
        g2d.drawString("Keine der figuren", 395, 150);
        g2d.drawString("ist richtig", 410, 165);
        
        g2d.dispose();
        return image;
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
    private int addQuestionOptions(WordprocessingMLPackage pkg, DefaultTableModel model, int startRow,
            boolean isFirstQuestionOnPage) {
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

            // Add spacing after all options only if this is NOT the first question on the
            // page
            if (!isFirstQuestionOnPage) {
                P spacingP = factory.createP();
                pkg.getMainDocumentPart().addObject(spacingP);
            }
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

                    int size = (int) (10 / 2.54 * 96); // ~10cm (increased from 4cm)
                    addImageToRunWithSize(pkg, run, imageBytes, "stopp_sign.png", size, size);
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
                int size = (int) (10 / 2.54 * 96); // ~10cm (increased from 4cm)
                addImageToRunWithSize(pkg, run, imageBytes, "stopp_sign.png", size, size);
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
