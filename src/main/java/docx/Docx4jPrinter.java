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
 * Build a document with proper ordering including memory test phases:
 * 1. Figuren
 * 2. Gedächtnis- und Merkfähigkeit (Lernphase) - displays allergy cards
 * 3. Zahlenfolgen
 * 4. Wortflüssigkeit
 * 5. Gedächtnis- und Merkfähigkeit (Abrufphase) - questions about the cards
 * 6. Other subcategories...
 * 
 * Note: This method redirects to buildDocumentComplete with null connection/sessionId
 * to maintain the same ordering for both normal and complete document builds.
 */
public WordprocessingMLPackage buildDocument(
        Map<String, DefaultTableModel> subcats,
        List<String> order,
        Map<String,List<Object>> introPagesMap
    ) throws Docx4JException {

    // Redirect to buildDocumentComplete with null connection and sessionId
    // This ensures the same ordering is used for both normal and complete builds
    return buildDocumentComplete(subcats, order, introPagesMap, null, null);
}

/**
 * Build a complete document with proper ordering including memory test phases:
 * 1. Figuren
 * 2. Gedächtnis- und Merkfähigkeit (Lernphase) - displays allergy cards
 * 3. Zahlenfolgen
 * 4. Wortflüssigkeit
 * 5. Gedächtnis- und Merkfähigkeit (Abrufphase) - questions about the cards
 * 6. Other subcategories...
 */
public WordprocessingMLPackage buildDocumentComplete(
        Map<String, DefaultTableModel> subcats,
        List<String> order,
        Map<String,List<Object>> introPagesMap,
        java.sql.Connection conn,
        Integer sessionId
    ) throws Docx4JException {

    WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
    
    // Define the proper order for memory test
    List<String> reorderedSubcats = new ArrayList<>();
    
    // 1. First add Figuren if it exists
    if (order.contains("Figuren")) {
        reorderedSubcats.add("Figuren");
    }
    
    // 2. Add Gedächtnis- und Merkfähigkeit (Lernphase) - after Figuren, before Zahlenfolgen
    reorderedSubcats.add("Gedächtnis und Merkfähigkeit (Lernphase)");
    
    // 3. Add Zahlenfolgen if it exists
    if (order.contains("Zahlenfolgen")) {
        reorderedSubcats.add("Zahlenfolgen");
    }
    
    // 4. Add Wortflüssigkeit if it exists  
    if (order.contains("Wortflüssigkeit") || order.contains("Wortfluessigkeit")) {
        reorderedSubcats.add(order.contains("Wortflüssigkeit") ? "Wortflüssigkeit" : "Wortfluessigkeit");
    }
    
    // 5. Add Gedächtnis- und Merkfähigkeit (Abrufphase) - after Wortflüssigkeit, before other subcategories
    if (order.contains("Merkfähigkeiten")) {
        reorderedSubcats.add("Gedächtnis und Merkfähigkeit (Abrufphase)");
    }
    
    // 6. Add other subcategories (except already processed ones)
    for (String subcat : order) {
        if (!subcat.equals("Figuren") && !subcat.equals("Zahlenfolgen") && 
            !subcat.equals("Wortflüssigkeit") && !subcat.equals("Wortfluessigkeit") && 
            !subcat.equals("Merkfähigkeiten")) {
            reorderedSubcats.add(subcat);
        }
    }

    for (int i = 0; i < reorderedSubcats.size(); i++) {
        String rawSubcat = reorderedSubcats.get(i);
        String key = rawSubcat.toLowerCase().trim();

        // Handle Lernphase
        if (rawSubcat.equals("Gedächtnis und Merkfähigkeit (Lernphase)")) {
            // Add introduction page for Lernphase
            List<Object> intro = introPagesMap.get(key);
            if (intro == null) {
                // Fallback search
                for (String cand : introPagesMap.keySet()) {
                    if (cand.contains("lernphase")) {
                        intro = introPagesMap.get(cand);
                        break;
                    }
                }
            }
            
            if (intro != null) {
                for (Object o : intro) {
                    pkg.getMainDocumentPart().addObject(o);
                }
                addPageBreak(pkg);
            }
            
            // Add allergy cards only if connection and sessionId are provided (2 per page, total 8 cards = 4 pages)
            if (conn != null && sessionId != null) {
                addAllergyCards(pkg, conn, sessionId);
            }
            
        } else if (rawSubcat.equals("Gedächtnis und Merkfähigkeit (Abrufphase)")) {
            // Handle Abrufphase - use Merkfähigkeiten data but different intro
            List<Object> intro = introPagesMap.get(key);
            if (intro == null) {
                // Fallback search
                for (String cand : introPagesMap.keySet()) {
                    if (cand.contains("abrufphase")) {
                        intro = introPagesMap.get(cand);
                        break;
                    }
                }
            }
            
            if (intro != null) {
                for (Object o : intro) {
                    pkg.getMainDocumentPart().addObject(o);
                }
                addPageBreak(pkg);
            }
            
            // Add questions from Merkfähigkeiten
            DefaultTableModel model = subcats.get("Merkfähigkeiten");
            if (model != null) {
                addQuestions(pkg, model);
            }
            
        } else {
            // Regular subcategory processing
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
        }

        // 4) break *between* subsections, but not after the last one
        if (i < reorderedSubcats.size() - 1) {
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
            String number = Objects.toString(model.getValueAt(r, 0), "").trim();
            Object questionObj = model.getValueAt(r, 1);

            // Handle passage rows (non-numeric identifier and not option row)
            if ((number.isEmpty() || !number.matches("\\d+")) && !number.matches("[A-E]\\)")
                    && questionObj != null) {
                String passage = questionObj.toString();
                if (!passage.trim().isEmpty()) {
                    P passageP = factory.createP();
                    R passageR = factory.createR();
                    Text passageT = factory.createText();
                    passageT.setValue(passage);
                    passageR.getContent().add(passageT);
                    passageP.getContent().add(passageR);
                    pkg.getMainDocumentPart().addObject(passageP);
                }
                continue;
            }

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

                // Check if we need a page break BEFORE adding options (to prevent extra spacing before page break)
                boolean needsPageBreak = !isFigurenQuestion && nonFigCounter % 5 == 0 && nonFigCounter > 0 && r < model.getRowCount() - 1;
                
                // Add options/answers if they exist in the model, but control spacing for page breaks
                // Also skip option rows to avoid re-processing them
                r = addQuestionOptions(pkg, model, r, isFirstQuestionOnPage, needsPageBreak, isFigurenQuestion, nonFigCounter);


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
            String number = Objects.toString(model.getValueAt(r, 0), "").trim();
            Object questionObj = model.getValueAt(r, 1);
            String solution = Objects.toString(model.getValueAt(r, 2), "");

            // Handle passage rows before question blocks
            if ((number.isEmpty() || !number.matches("\\d+")) && !number.matches("[A-E]\\)")
                    && questionObj != null) {
                String passage = questionObj.toString();
                if (!passage.trim().isEmpty()) {
                    P passageP = factory.createP();
                    R passageR = factory.createR();
                    Text passageT = factory.createText();
                    passageT.setValue(passage);
                    passageR.getContent().add(passageT);
                    passageP.getContent().add(passageR);
                    pkg.getMainDocumentPart().addObject(passageP);
                }
                continue;
            }

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

                // Page break for non-Figuren questions every 5 questions
                if (!isFigurenQuestion) {
                    nonFigCounter++;
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

                // Check if we need a page break BEFORE adding options (to prevent extra spacing before page break)
                boolean needsPageBreak = !isFigurenQuestion && nonFigCounter % 5 == 0 && nonFigCounter > 0 && r < model.getRowCount() - 1;
                
                // Add options/answers if they exist in the model, but control spacing for page breaks
                // Also skip option rows to avoid re-processing them
                r = addQuestionOptions(pkg, model, r, isFirstQuestionOnPage, needsPageBreak, isFigurenQuestion, nonFigCounter);

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

                // Add page break for non-Figuren questions after processing every 5th question
                if (needsPageBreak) {
                    // Remove any existing trailing breaks to avoid blank page
                    removeTrailingPageBreak(pkg);
                    addPageBreak(pkg);
                    isFirstQuestionOnPage = true; // Reset for new page
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

    /** Add a line break followed by a page break - for non-Figuren questions */
    public void addLineBreakThenPageBreak(WordprocessingMLPackage pkg) {
        // Create a single paragraph with line break + page break
        P p = factory.createP();
        R r = factory.createR();
        
        // Add line break first (like shift+enter)
        Br lineBreak = factory.createBr();
        lineBreak.setType(STBrType.TEXT_WRAPPING);
        r.getContent().add(lineBreak);
        
        // Then add page break
        Br pageBreak = factory.createBr();
        pageBreak.setType(STBrType.PAGE);
        r.getContent().add(pageBreak);
        
        p.getContent().add(r);
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
        String subheaderLine = "";
        String timingLine = "";
        List<String> instructionLines = new ArrayList<>();
        List<String> exampleLines = new ArrayList<>();
        
        boolean inInstructions = false;
        boolean inExample = false;
        boolean headerFound = false;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (headerLine.isEmpty() && (line.contains("Basiskenntnistest") || 
                line.contains("Figuren") || line.contains("Gedächtnis") || 
                line.contains("Zahlenfolgen") || line.contains("Wortflüssigkeit") ||
                line.contains("Implikationen") || line.contains("Emotionen") || 
                line.contains("Soziales") || line.contains("Textverständnis"))) {
                headerLine = line;
                headerFound = true;
            } else if (headerFound && subheaderLine.isEmpty() && 
                       (line.equals("Lernphase") || line.equals("Abrufphase"))) {
                // Detect subheader for memory sections
                subheaderLine = line;
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
        
        // 2. Add subheader if present - Bold, 14pt, line break instead of paragraph break
        if (!subheaderLine.isEmpty()) {
            addFormattedSubheader(pkg, subheaderLine);
        }
        
        // 3. Add timing line - Bold, Montserrat, 11pt, Vor 6pt, Nach 18pt, Mehrfach 1.15
        if (!timingLine.isEmpty()) {
            addFormattedParagraph(pkg, timingLine, true, 22, 120, 360, true, JcEnumeration.LEFT, "Montserrat");
        }
        
        // 4. Add instructions in a 1x1 table
        if (!instructionLines.isEmpty()) {
            addInstructionsTable(pkg, instructionLines);
        }
        
        // 5. Add example section with "Beispielaufgabe:" bold
        for (int idx = 0; idx < exampleLines.size(); idx++) {
            String exampleLine = exampleLines.get(idx);

            // Combine the two premise lines for Implikationen examples
            if (headerLine.contains("Implikationen") && exampleLine.startsWith("\"")
                    && idx + 1 < exampleLines.size() && exampleLines.get(idx + 1).startsWith("\"")) {
                String combined = exampleLine + "\n" + exampleLines.get(idx + 1);
                addFormattedParagraph(pkg, combined, false, 22, 200, 200, true, JcEnumeration.LEFT, "Aptos");
                idx++; // skip the next line as it's merged
                continue;
            }

            if (exampleLine.startsWith("Beispielaufgabe:") || exampleLine.startsWith("Beispieltext:") || exampleLine.startsWith("Beispielausweis:")) {
                // Example question header: Bold, Aptos 11pt, Vor 10pt, Nach 10pt, Mehrfach 1.15
                addFormattedParagraph(pkg, exampleLine, true, 22, 200, 200, true, JcEnumeration.LEFT, "Aptos");
                
                // If this is Beispielausweis for memory section, add example allergy card
                if (exampleLine.startsWith("Beispielausweis:") && headerLine.contains("Gedächtnis")) {
                    addExampleAllergyCard(pkg);
                }
            } else if (exampleLine.equals("Welche Figur lässt sich aus den folgenden Bausteinen zusammensetzen?")
                    && headerLine.contains("Figuren")) {
                // Add the question text for Figuren example
                addFormattedParagraph(pkg, exampleLine, false, 22, 0, 200, true, JcEnumeration.LEFT, "Aptos");
                addFigurenExampleImage(pkg);
            } else if (exampleLine.matches("^[A-E]\\).*")) {
                // Answer options
                addFormattedParagraph(pkg, exampleLine, false, 22, 60, 60, true, JcEnumeration.LEFT, "Aptos");
            } else if (exampleLine.endsWith("?")) {
                // Example question line: Vor 10pt, Nach 10pt, Mehrfach 1.15
                addFormattedParagraph(pkg, exampleLine, false, 22, 200, 200, true, JcEnumeration.LEFT, "Aptos");
            } else {
                // Other example content
                addFormattedParagraph(pkg, exampleLine, false, 22, 120, 120, false, JcEnumeration.LEFT, "Aptos");
            }
        }
        
        // Add a page break after the introduction
        addPageBreak(pkg);
    }
    
    /**
     * Add allergy cards for the learning phase (2 cards per page, 4 pages total)
     */
    private void addAllergyCards(WordprocessingMLPackage pkg, java.sql.Connection conn, Integer sessionId) {
        try {
            // Import the DAO class
            Class<?> allergyDAOClass = Class.forName("dao.AllergyCardDAO");
            
            // Create DAO instance
            Object allergyDAO = allergyDAOClass.getConstructor(java.sql.Connection.class).newInstance(conn);
            
            // Get allergy cards for this session
            // getBySessionId uses primitive int parameter
            java.lang.reflect.Method getBySessionMethod = allergyDAOClass.getMethod("getBySessionId", int.class);
            @SuppressWarnings("unchecked")
            java.util.List<Object> allergyCards = (java.util.List<Object>) getBySessionMethod.invoke(allergyDAO, sessionId);
            
            if (allergyCards.isEmpty()) {
                // Add placeholder text if no cards found
                P noCardsP = factory.createP();
                R noCardsR = factory.createR();
                Text noCardsT = factory.createText();
                noCardsT.setValue("Keine Allergieausweise für diese Simulation gefunden.");
                noCardsR.getContent().add(noCardsT);
                noCardsP.getContent().add(noCardsR);
                pkg.getMainDocumentPart().addObject(noCardsP);
                return;
            }
            
            // Add up to 8 cards, 2 per page
            int cardsAdded = 0;
            for (Object cardObj : allergyCards) {
                if (cardsAdded >= 8) break; // Maximum 8 cards
                
                if (cardsAdded > 0 && cardsAdded % 2 == 0) {
                    // Add page break after every 2 cards
                    addPageBreak(pkg);
                }
                
                
                addAllergyCard(pkg, cardObj);
                cardsAdded++;
            }
            
            // Add STOPP sign after all cards
            addStoppSign(pkg);
            
        } catch (Exception e) {
            // If loading fails, add error message
            P errorP = factory.createP();
            R errorR = factory.createR();
            Text errorT = factory.createText();
            errorT.setValue("Fehler beim Laden der Allergieausweise: " + e.getMessage());
            errorR.getContent().add(errorT);
            errorP.getContent().add(errorR);
            pkg.getMainDocumentPart().addObject(errorP);
        }
    }
    
    /**
     * Add a single allergy card to the document
     */
    private void addAllergyCard(WordprocessingMLPackage pkg, Object cardData) throws Exception {
        // Use reflection to extract data from the AllergyCardData record
        Class<?> cardClass = cardData.getClass();
        
        String name = (String) cardClass.getMethod("name").invoke(cardData);
        Object geburtsdatum = cardClass.getMethod("geburtsdatum").invoke(cardData);
        String medikamente = (String) cardClass.getMethod("medikamenteneinnahme").invoke(cardData);
        String blutgruppe = (String) cardClass.getMethod("blutgruppe").invoke(cardData);
        String allergien = (String) cardClass.getMethod("bekannteAllergien").invoke(cardData);
        String ausweisnummer = (String) cardClass.getMethod("ausweisnummer").invoke(cardData);
        String ausstellungsland = (String) cardClass.getMethod("ausstellungsland").invoke(cardData);
        byte[] bildBytes = (byte[]) cardClass.getMethod("bildPng").invoke(cardData);
        
        // Create a table for the allergy card layout - single row with photo left, text right
        Tbl cardTable = factory.createTbl();
        
        // Table properties with card-style borders
        TblPr tblPr = factory.createTblPr();
        TblWidth tblWidth = factory.createTblWidth();
        tblWidth.setType("pct");
        tblWidth.setW(BigInteger.valueOf(5000)); // 100% width
        tblPr.setTblW(tblWidth);
        
        // Create thick borders for card-like appearance with rounded effect
        TblBorders tblBorders = factory.createTblBorders();
        
        // Create clean straight borders
        CTBorder straightBorder = factory.createCTBorder();
        straightBorder.setVal(STBorder.SINGLE); // Simple straight line border
        straightBorder.setSz(BigInteger.valueOf(8)); // Standard border thickness
        straightBorder.setColor("4A5568"); // Professional dark gray color
        straightBorder.setSpace(BigInteger.valueOf(0)); // No extra spacing for clean look
        
        tblBorders.setTop(straightBorder);
        tblBorders.setLeft(straightBorder);
        tblBorders.setBottom(straightBorder);
        tblBorders.setRight(straightBorder);
        tblPr.setTblBorders(tblBorders);
        
        // Add subtle table shading for modern card background effect
        CTShd tblShd = factory.createCTShd();
        tblShd.setVal(STShd.CLEAR);
        tblShd.setColor("000000");
        tblShd.setFill("FAFAFA"); // Very light gray background for subtle card effect
        tblPr.setShd(tblShd);
        
        cardTable.setTblPr(tblPr);
        
        // Title row spanning across entire width first
        Tr titleRow = factory.createTr();
        Tc titleCell = factory.createTc();
        
        // Full width for title cell - spans both photo and text columns
        TcPr titleCellPr = factory.createTcPr();
        TblWidth titleCellWidth = factory.createTblWidth();
        titleCellWidth.setType("pct");
        titleCellWidth.setW(BigInteger.valueOf(5000)); // 100% width
        titleCellPr.setTcW(titleCellWidth);
        
        // Add column span for title to cover both photo and text columns
        org.docx4j.wml.TcPrInner.GridSpan gridSpan = factory.createTcPrInnerGridSpan();
        gridSpan.setVal(BigInteger.valueOf(2)); // Span 2 columns
        titleCellPr.setGridSpan(gridSpan);
        
        titleCell.setTcPr(titleCellPr);
        
        // Add title with medical cross - centered across entire card
        P titleP = factory.createP();
        PPr titlePPr = factory.createPPr();
        Jc titleJc = factory.createJc();
        titleJc.setVal(JcEnumeration.CENTER);
        titlePPr.setJc(titleJc);
        
        // Add padding for title
        PPrBase.Spacing titleSpacing = factory.createPPrBaseSpacing();
        titleSpacing.setBefore(BigInteger.valueOf(120)); // 6pt before
        titleSpacing.setAfter(BigInteger.valueOf(120)); // 6pt after
        titlePPr.setSpacing(titleSpacing);
        titleP.setPPr(titlePPr);
        
        R titleR = factory.createR();
        RPr titleRPr = factory.createRPr();
        BooleanDefaultTrue titleBold = factory.createBooleanDefaultTrue();
        titleRPr.setB(titleBold);
        
        // Set font to Aptos
        RFonts titleFonts = factory.createRFonts();
        titleFonts.setAscii("Aptos");
        titleFonts.setHAnsi("Aptos");
        titleRPr.setRFonts(titleFonts);
        
        HpsMeasure titleSize = factory.createHpsMeasure();
        titleSize.setVal(BigInteger.valueOf(28)); // 14pt for title
        titleRPr.setSz(titleSize);
        titleRPr.setSzCs(titleSize);
        
        titleR.setRPr(titleRPr);
        
        Text titleText = factory.createText();
        titleText.setValue("ALLERGIEAUSWEIS");
        titleR.getContent().add(titleText);
        
        // Add medical cross symbol in red
        R crossR = factory.createR();
        RPr crossRPr = factory.createRPr();
        org.docx4j.wml.Color crossColor = factory.createColor();
        crossColor.setVal("FF0000"); // Red color
        crossRPr.setColor(crossColor);
        HpsMeasure crossSize = factory.createHpsMeasure();
        crossSize.setVal(BigInteger.valueOf(24)); // 12pt font
        crossRPr.setSz(crossSize);
        crossRPr.setSzCs(crossSize);
        crossR.setRPr(crossRPr);
        
        Text crossText = factory.createText();
        crossText.setValue("  ✚"); // Medical cross with spacing
        crossR.getContent().add(crossText);
        
        titleP.getContent().add(titleR);
        titleP.getContent().add(crossR);
        titleCell.getContent().add(titleP);
        titleRow.getContent().add(titleCell);
        cardTable.getContent().add(titleRow);
        
        // Content row: photo left, text right
        Tr contentRow = factory.createTr();
        
        // Photo cell (left side) - smaller to give more space to text
        Tc photoCell = factory.createTc();
        TcPr photoCellPr = factory.createTcPr();
        TblWidth photoCellWidth = factory.createTblWidth();
        photoCellWidth.setType("pct");
        photoCellWidth.setW(BigInteger.valueOf(1200)); // 24% width for photo (reduced)
        photoCellPr.setTcW(photoCellWidth);
        
        // Vertical alignment for photo cell
        CTVerticalJc photoVAlign = factory.createCTVerticalJc();
        photoVAlign.setVal(STVerticalJc.CENTER);
        photoCellPr.setVAlign(photoVAlign);
        // No left margin - image directly at left edge of cell
        
        photoCell.setTcPr(photoCellPr);
        
        // Add photo or placeholder
        P photoP = factory.createP();
        PPr photoPPr = factory.createPPr();
        Jc photoJc = factory.createJc();
        photoJc.setVal(JcEnumeration.CENTER);
        photoPPr.setJc(photoJc);
        photoP.setPPr(photoPPr);
        
        if (bildBytes != null && bildBytes.length > 0) {
            try {
                BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(pkg, bildBytes);
                // Set photo height to 5cm (approximately 142 points, 1cm ≈ 28.35 points)
                // 5cm width & height = 1800000 EMU; use 7-arg overload to force exact size
                Inline inline = imagePart.createImageInline("Allergy Card Photo", "Photo", 0, 0, 2160000, 2160000, false);
                
                R photoR = factory.createR();
                Drawing drawing = factory.createDrawing();
                drawing.getAnchorOrInline().add(inline);
                photoR.getContent().add(drawing);
                photoP.getContent().add(photoR);
            } catch (Exception e) {
                // Placeholder text on error
                R photoR = factory.createR();
                Text photoT = factory.createText();
                photoT.setValue("[Foto]");
                photoR.getContent().add(photoT);
                photoP.getContent().add(photoR);
            }
        } else {
            // Placeholder text if no image
            R photoR = factory.createR();
            Text photoT = factory.createText();
            photoT.setValue("[Foto]");
            photoR.getContent().add(photoT);
            photoP.getContent().add(photoR);
        }
        
        photoCell.getContent().add(photoP);
        
        // Text cell (right side) - larger to prevent line breaks with margin for spacing
        Tc textCell = factory.createTc();
        TcPr textCellPr = factory.createTcPr();
        TblWidth textCellWidth = factory.createTblWidth();
        textCellWidth.setType("pct");
        textCellWidth.setW(BigInteger.valueOf(3800)); // 76% width for text (increased)
        textCellPr.setTcW(textCellWidth);
        
        // Add left margin to text cell for spacing from photo
        TcMar textCellMar = factory.createTcMar();
        TblWidth leftMargin = factory.createTblWidth();
        leftMargin.setType("dxa");
        leftMargin.setW(BigInteger.valueOf(144)); // Reduced from 288 to 144 twips (approximately 5px equivalent)
        textCellMar.setLeft(leftMargin);
        textCellPr.setTcMar(textCellMar);
        
        // Vertical alignment for text cell
        CTVerticalJc textVAlign = factory.createCTVerticalJc();
        textVAlign.setVal(STVerticalJc.TOP);
        textCellPr.setVAlign(textVAlign);
        
        textCell.setTcPr(textCellPr);
        
        // Format geburtsdatum as DD.Month
        String formattedDate = "";
        if (geburtsdatum != null) {
            String dateStr = geburtsdatum.toString();
            if (dateStr.length() >= 10) {
                try {
                    String[] dateParts = dateStr.split("-");
                    if (dateParts.length >= 3) {
                        String year = dateParts[0];
                        String month = dateParts[1];
                        String day = dateParts[2];
                        
                        // Convert month number to month name
                        String[] monthNames = {"Januar", "Februar", "März", "April", "Mai", "Juni",
                                              "Juli", "August", "September", "Oktober", "November", "Dezember"};
                        int monthNum = Integer.parseInt(month) - 1; // 0-based index
                        if (monthNum >= 0 && monthNum < 12) {
                            formattedDate = day + ". " + monthNames[monthNum];
                        } else {
                            formattedDate = day + "." + month;
                        }
                    } else {
                        formattedDate = dateStr;
                    }
                } catch (Exception e) {
                    formattedDate = dateStr;
                }
            } else {
                formattedDate = dateStr;
            }
        }
        
        // Add all data fields as simple text lines
        addCardTextField(textCell, "Name", name);
        addCardTextField(textCell, "Geburtsdatum", formattedDate);
        addCardTextField(textCell, "Medikamenteneinnahme", medikamente != null ? medikamente : "Keine");
        addCardTextField(textCell, "Blutgruppe", blutgruppe != null ? blutgruppe : "");
        addCardTextField(textCell, "Bekannte Allergien", allergien != null ? allergien : "Keine");
        addCardTextField(textCell, "Ausweisnummer", ausweisnummer != null ? ausweisnummer : "");
        addCardTextField(textCell, "Ausstellungsland", ausstellungsland != null ? ausstellungsland : "");
        
        contentRow.getContent().add(photoCell);
        contentRow.getContent().add(textCell);
        cardTable.getContent().add(contentRow);
        
        pkg.getMainDocumentPart().addObject(cardTable);
        
        // Add 4 line spacing after each card for proper vertical separation
        for (int i = 0; i < 4; i++) {
            P spacingP = factory.createP();
            PPr spacingPPr = factory.createPPr();
            PPrBase.Spacing spacing = factory.createPPrBaseSpacing();
            spacing.setAfter(BigInteger.valueOf(120)); // 6pt spacing per line
            spacingPPr.setSpacing(spacing);
            spacingP.setPPr(spacingPPr);
            pkg.getMainDocumentPart().addObject(spacingP);
        }
    }
    
    /**
     * Add a data row to the allergy card table
     */
    private void addCardDataRow(Tbl cardTable, String label, String value) {
        Tr dataRow = factory.createTr();
        
        // Label cell
        Tc labelCell = factory.createTc();
        TcPr labelCellPr = factory.createTcPr();
        TblWidth labelCellWidth = factory.createTblWidth();
        labelCellWidth.setType("pct");
        labelCellWidth.setW(BigInteger.valueOf(1500)); // 30% width
        labelCellPr.setTcW(labelCellWidth);
        labelCell.setTcPr(labelCellPr);
        
        P labelP = factory.createP();
        R labelR = factory.createR();
        RPr labelRPr = factory.createRPr();
        BooleanDefaultTrue labelBold = factory.createBooleanDefaultTrue();
        labelRPr.setB(labelBold);
        
        // Set font to Aptos
        RFonts labelFonts = factory.createRFonts();
        labelFonts.setAscii("Aptos");
        labelFonts.setHAnsi("Aptos");
        labelRPr.setRFonts(labelFonts);
        
        HpsMeasure labelSize = factory.createHpsMeasure();
        labelSize.setVal(BigInteger.valueOf(22)); // 11pt
        labelRPr.setSz(labelSize);
        labelRPr.setSzCs(labelSize);
        
        labelR.setRPr(labelRPr);
        
        Text labelText = factory.createText();
        labelText.setValue(label);
        labelR.getContent().add(labelText);
        labelP.getContent().add(labelR);
        labelCell.getContent().add(labelP);
        
        // Value cell
        Tc valueCell = factory.createTc();
        TcPr valueCellPr = factory.createTcPr();
        TblWidth valueCellWidth = factory.createTblWidth();
        valueCellWidth.setType("pct");
        valueCellWidth.setW(BigInteger.valueOf(3500)); // 70% width
        valueCellPr.setTcW(valueCellWidth);
        valueCell.setTcPr(valueCellPr);
        
        P valueP = factory.createP();
        R valueR = factory.createR();
        RPr valueRPr = factory.createRPr();
        
        // Set font to Aptos
        RFonts valueFonts = factory.createRFonts();
        valueFonts.setAscii("Aptos");
        valueFonts.setHAnsi("Aptos");
        valueRPr.setRFonts(valueFonts);
        
        HpsMeasure valueSize = factory.createHpsMeasure();
        valueSize.setVal(BigInteger.valueOf(22)); // 11pt
        valueRPr.setSz(valueSize);
        valueRPr.setSzCs(valueSize);
        
        valueR.setRPr(valueRPr);
        
        Text valueText = factory.createText();
        valueText.setValue(value != null ? value : "");
        valueR.getContent().add(valueText);
        valueP.getContent().add(valueR);
        valueCell.getContent().add(valueP);
        
        dataRow.getContent().add(labelCell);
        dataRow.getContent().add(valueCell);
        cardTable.getContent().add(dataRow);
    }
    
    /**
     * Add a text field to the allergy card text cell with extra line spacing
     */
    private void addCardTextField(Tc textCell, String label, String value) {
        P fieldP = factory.createP();
        PPr fieldPPr = factory.createPPr();
        
        // Add more spacing between fields - increased for better readability
        PPrBase.Spacing fieldSpacing = factory.createPPrBaseSpacing();
        fieldSpacing.setAfter(BigInteger.valueOf(240)); // 12pt spacing after each field (increased from 4pt)
        fieldPPr.setSpacing(fieldSpacing);
        fieldP.setPPr(fieldPPr);
        
        // Label part (bold)
        R labelR = factory.createR();
        RPr labelRPr = factory.createRPr();
        BooleanDefaultTrue labelBold = factory.createBooleanDefaultTrue();
        labelRPr.setB(labelBold);
        
        // Set font to Aptos
        RFonts labelFonts = factory.createRFonts();
        labelFonts.setAscii("Aptos");
        labelFonts.setHAnsi("Aptos");
        labelRPr.setRFonts(labelFonts);
        
        HpsMeasure labelSize = factory.createHpsMeasure();
        labelSize.setVal(BigInteger.valueOf(20)); // 10pt
        labelRPr.setSz(labelSize);
        labelRPr.setSzCs(labelSize);
        
        labelR.setRPr(labelRPr);
        
        Text labelText = factory.createText();
        labelText.setValue(label + ":  "); // Add 2 spaces after the colon
        // Preserve trailing spaces so Word does not trim them
        labelText.setSpace("preserve");
        labelR.getContent().add(labelText);
        fieldP.getContent().add(labelR);
        
        // Value part (normal)
        R valueR = factory.createR();
        RPr valueRPr = factory.createRPr();
        
        // Set font to Aptos
        RFonts valueFonts = factory.createRFonts();
        valueFonts.setAscii("Aptos");
        valueFonts.setHAnsi("Aptos");
        valueRPr.setRFonts(valueFonts);
        
        HpsMeasure valueSize = factory.createHpsMeasure();
        valueSize.setVal(BigInteger.valueOf(20)); // 10pt
        valueRPr.setSz(valueSize);
        valueRPr.setSzCs(valueSize);
        
        valueR.setRPr(valueRPr);
        
        Text valueText = factory.createText();
        valueText.setValue(value != null ? value : "");
        valueR.getContent().add(valueText);
        fieldP.getContent().add(valueR);
        
        textCell.getContent().add(fieldP);
    }
    
    /**
     * Add an image row to the allergy card table
     */
    private void addCardImageRow(Tbl cardTable, byte[] imageBytes, WordprocessingMLPackage pkg) {
        try {
            BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(pkg, imageBytes);
            Inline inline = imagePart.createImageInline("Allergy Card Photo", "Photo", 0, 142, false);
            
            Tr imageRow = factory.createTr();
            Tc imageCell = factory.createTc();
            
            TcPr imageCellPr = factory.createTcPr();
            TblWidth imageCellWidth = factory.createTblWidth();
            imageCellWidth.setType("pct");
            imageCellWidth.setW(BigInteger.valueOf(5000)); // 100% width
            imageCellPr.setTcW(imageCellWidth);
            imageCell.setTcPr(imageCellPr);
            
            P imageP = factory.createP();
            PPr imagePPr = factory.createPPr();
            Jc imageJc = factory.createJc();
            imageJc.setVal(JcEnumeration.CENTER);
            imagePPr.setJc(imageJc);
            imageP.setPPr(imagePPr);
            
            R imageR = factory.createR();
            Drawing drawing = factory.createDrawing();
            drawing.getAnchorOrInline().add(inline);
            imageR.getContent().add(drawing);
            imageP.getContent().add(imageR);
            imageCell.getContent().add(imageP);
            imageRow.getContent().add(imageCell);
            cardTable.getContent().add(imageRow);
            
        } catch (Exception e) {
            // If image fails to load, add placeholder text
            Tr imageRow = factory.createTr();
            Tc imageCell = factory.createTc();
            
            P placeholderP = factory.createP();
            R placeholderR = factory.createR();
            Text placeholderT = factory.createText();
            placeholderT.setValue("[Foto nicht verfügbar]");
            placeholderR.getContent().add(placeholderT);
            placeholderP.getContent().add(placeholderR);
            imageCell.getContent().add(placeholderP);
            imageRow.getContent().add(imageCell);
            cardTable.getContent().add(imageRow);
        }
    }
    
    /**
     * Add a STOPP sign after the learning phase
     */
    private void addStoppSign(WordprocessingMLPackage pkg) {
        // Add page break before STOPP sign
        addPageBreak(pkg);
        
        // Add centered STOPP text
        P stoppP = factory.createP();
        PPr stoppPPr = factory.createPPr();
        
        // Center alignment
        Jc stoppJc = factory.createJc();
        stoppJc.setVal(JcEnumeration.CENTER);
        stoppPPr.setJc(stoppJc);
        
        // Spacing
        PPrBase.Spacing stoppSpacing = factory.createPPrBaseSpacing();
        stoppSpacing.setBefore(BigInteger.valueOf(1440)); // 72pt = 1 inch from top
        stoppPPr.setSpacing(stoppSpacing);
        
        stoppP.setPPr(stoppPPr);
        
        R stoppR = factory.createR();
        RPr stoppRPr = factory.createRPr();
        
        // Bold text
        BooleanDefaultTrue stoppBold = factory.createBooleanDefaultTrue();
        stoppRPr.setB(stoppBold);
        
        // Large font size
        HpsMeasure stoppSize = factory.createHpsMeasure();
        stoppSize.setVal(BigInteger.valueOf(72)); // 36pt
        stoppRPr.setSz(stoppSize);
        stoppRPr.setSzCs(stoppSize);
        
        // Set font to Aptos
        RFonts stoppFonts = factory.createRFonts();
        stoppFonts.setAscii("Aptos");
        stoppFonts.setHAnsi("Aptos");
        stoppRPr.setRFonts(stoppFonts);
        
        stoppR.setRPr(stoppRPr);
        
        Text stoppText = factory.createText();
        stoppText.setValue("STOPP");
        stoppR.getContent().add(stoppText);
        stoppP.getContent().add(stoppR);
        
        //pkg.getMainDocumentPart().addObject(stoppP);
        
        // Add instruction text below
        P instructionP = factory.createP();
        PPr instructionPPr = factory.createPPr();
        
        // Center alignment
        Jc instructionJc = factory.createJc();
        instructionJc.setVal(JcEnumeration.CENTER);
        instructionPPr.setJc(instructionJc);
        
        // Spacing
        PPrBase.Spacing instructionSpacing = factory.createPPrBaseSpacing();
        instructionSpacing.setBefore(BigInteger.valueOf(240)); // 12pt spacing
        instructionPPr.setSpacing(instructionSpacing);
        
        instructionP.setPPr(instructionPPr);
        
        R instructionR = factory.createR();
        RPr instructionRPr = factory.createRPr();
        
        // Set font to Aptos
        RFonts instructionFonts = factory.createRFonts();
        instructionFonts.setAscii("Aptos");
        instructionFonts.setHAnsi("Aptos");
        instructionRPr.setRFonts(instructionFonts);
        
        HpsMeasure instructionSize = factory.createHpsMeasure();
        instructionSize.setVal(BigInteger.valueOf(24)); // 12pt
        instructionRPr.setSz(instructionSize);
        instructionRPr.setSzCs(instructionSize);
        
        instructionR.setRPr(instructionRPr);
        
        Text instructionText = factory.createText();
        instructionText.setValue("Bitte warten Sie auf weitere Anweisungen.");
        //instructionR.getContent().add(instructionText);
        instructionP.getContent().add(instructionR);
        
        pkg.getMainDocumentPart().addObject(instructionP);
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

        // Add text, supporting manual line breaks within the string
        String[] parts = text.split("\\n", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                Br br = factory.createBr();
                br.setType(STBrType.TEXT_WRAPPING);
                run.getContent().add(br);
            }
            Text textElement = factory.createText();
            textElement.setValue(parts[i]);
            run.getContent().add(textElement);
        }
        paragraph.getContent().add(run);
        
        pkg.getMainDocumentPart().addObject(paragraph);
    }
    
    /**
     * Helper method to add a formatted subheader with line break instead of paragraph break
     * Format: Bold, 14pt font, line break from previous content
     */
    private void addFormattedSubheader(WordprocessingMLPackage pkg, String text) {
        // Get the last paragraph and add the subheader as a line break + new content
        P paragraph = factory.createP();
        PPr pPr = factory.createPPr();
        
        // Set minimal spacing - no space before, minimal space after
        PPrBase.Spacing spacing = factory.createPPrBaseSpacing();
        spacing.setBefore(BigInteger.valueOf(0)); // No space before
        spacing.setAfter(BigInteger.valueOf(120)); // Minimal space after (6pt = 120 twips)
        pPr.setSpacing(spacing);
        
        paragraph.setPPr(pPr);
        
        // Create run with text formatting
        R run = factory.createR();
        RPr rPr = factory.createRPr();
        
        // Set font to Montserrat (same as header)
        RFonts fonts = factory.createRFonts();
        fonts.setAscii("Montserrat");
        fonts.setHAnsi("Montserrat");
        rPr.setRFonts(fonts);
        
        // Set font size to 14pt (28 half-points)
        HpsMeasure size = factory.createHpsMeasure();
        size.setVal(BigInteger.valueOf(28)); // 14pt = 28 half-points
        rPr.setSz(size);
        rPr.setSzCs(size); // For complex scripts
        
        // Set bold
        BooleanDefaultTrue boldProp = factory.createBooleanDefaultTrue();
        boldProp.setVal(true);
        rPr.setB(boldProp);
        rPr.setBCs(boldProp); // For complex scripts
        
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
            boolean isFirstQuestionOnPage, boolean needsPageBreak, boolean isFigurenQuestion, int nonFigCounter) {
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
                
                // For option E (i==4) of 5th non-Figuren question, remove spacing after to prevent gap before page break
                boolean isOptionE = (i == 4);
                boolean willHavePageBreak = !isFigurenQuestion && (nonFigCounter % 5 == 0);
                
                if (isOptionE && willHavePageBreak) {
                    // No spacing after option E when page break follows
                    spacing.setAfter(BigInteger.valueOf(0));
                } else {
                    spacing.setAfter(BigInteger.valueOf(60)); // Normal spacing after each option
                }
                
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
                
                // For option E of 5th non-Figuren question, add page break directly after text
                if (isOptionE && willHavePageBreak) {
                    Br pageBreak = factory.createBr();
                    pageBreak.setType(STBrType.PAGE);
                    optionR.getContent().add(pageBreak);
                }
                optionP.getContent().add(optionR);
                pkg.getMainDocumentPart().addObject(optionP);
            }

            // Add spacing after all options except for every 5th non-Figuren question (where a page break was just added after option E)
            boolean shouldAddSpacing = isFigurenQuestion || (nonFigCounter % 5 != 0);
            if (shouldAddSpacing) {
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
    
    /**
     * Add an example allergy card for the memory learning phase introduction
     */
    private void addExampleAllergyCard(WordprocessingMLPackage pkg) {
        try {
            // Create an example allergy card with sample data using new single-row layout
            Tbl cardTable = factory.createTbl();
            
            // Table properties with card-style borders
            TblPr tblPr = factory.createTblPr();
            TblWidth tblWidth = factory.createTblWidth();
            tblWidth.setType("pct");
            tblWidth.setW(BigInteger.valueOf(5000)); // 100% width
            tblPr.setTblW(tblWidth);
            
            // Create thick borders for card-like appearance
            TblBorders tblBorders = factory.createTblBorders();
            CTBorder cardBorder = factory.createCTBorder();
            cardBorder.setVal(STBorder.SINGLE);
            cardBorder.setSz(BigInteger.valueOf(12)); // Thick border
            cardBorder.setColor("000000"); // Black border like reference
            
            tblBorders.setTop(cardBorder);
            tblBorders.setLeft(cardBorder);
            tblBorders.setBottom(cardBorder);
            tblBorders.setRight(cardBorder);
            tblPr.setTblBorders(tblBorders);
            
            cardTable.setTblPr(tblPr);
            
            // Title row spanning across entire width first
            Tr titleRow = factory.createTr();
            Tc titleCell = factory.createTc();
            
            // Full width for title cell - spans both photo and text columns
            TcPr titleCellPr = factory.createTcPr();
            TblWidth titleCellWidth = factory.createTblWidth();
            titleCellWidth.setType("pct");
            titleCellWidth.setW(BigInteger.valueOf(5000)); // 100% width
            titleCellPr.setTcW(titleCellWidth);
            
            // Add column span for title to cover both photo and text columns
            org.docx4j.wml.TcPrInner.GridSpan gridSpan = factory.createTcPrInnerGridSpan();
            gridSpan.setVal(BigInteger.valueOf(2)); // Span 2 columns
            titleCellPr.setGridSpan(gridSpan);
            
            titleCell.setTcPr(titleCellPr);
            
            // Add title with medical cross - centered across entire card
            P titleP = factory.createP();
            PPr titlePPr = factory.createPPr();
            Jc titleJc = factory.createJc();
            titleJc.setVal(JcEnumeration.CENTER);
            titlePPr.setJc(titleJc);
            
            // Add padding for title
            PPrBase.Spacing titleSpacing = factory.createPPrBaseSpacing();
            titleSpacing.setBefore(BigInteger.valueOf(120)); // 6pt before
            titleSpacing.setAfter(BigInteger.valueOf(120)); // 6pt after
            titlePPr.setSpacing(titleSpacing);
            titleP.setPPr(titlePPr);
            
            R titleR = factory.createR();
            RPr titleRPr = factory.createRPr();
            BooleanDefaultTrue titleBold = factory.createBooleanDefaultTrue();
            titleRPr.setB(titleBold);
            
            // Set font to Aptos
            RFonts titleFonts = factory.createRFonts();
            titleFonts.setAscii("Aptos");
            titleFonts.setHAnsi("Aptos");
            titleRPr.setRFonts(titleFonts);
            
            HpsMeasure titleSize = factory.createHpsMeasure();
            titleSize.setVal(BigInteger.valueOf(28)); // 14pt for title
            titleRPr.setSz(titleSize);
            titleRPr.setSzCs(titleSize);
            
            titleR.setRPr(titleRPr);
            
            Text titleText = factory.createText();
            titleText.setValue("ALLERGIEAUSWEIS");
            titleR.getContent().add(titleText);
            
            // Add medical cross symbol in red
            R crossR = factory.createR();
            RPr crossRPr = factory.createRPr();
            org.docx4j.wml.Color crossColor = factory.createColor();
            crossColor.setVal("FF0000"); // Red color
            crossRPr.setColor(crossColor);
            HpsMeasure crossSize = factory.createHpsMeasure();
            crossSize.setVal(BigInteger.valueOf(24)); // 12pt font
            crossRPr.setSz(crossSize);
            crossRPr.setSzCs(crossSize);
            crossR.setRPr(crossRPr);
            
            Text crossText = factory.createText();
            crossText.setValue("  ✚"); // Medical cross with spacing
            crossR.getContent().add(crossText);
            
            titleP.getContent().add(titleR);
            titleP.getContent().add(crossR);
            titleCell.getContent().add(titleP);
            titleRow.getContent().add(titleCell);
            cardTable.getContent().add(titleRow);
            
            // Content row: photo left, text right
            Tr contentRow = factory.createTr();
            
            // Photo cell (left side) - placeholder for example card, smaller to give more space to text
            Tc photoCell = factory.createTc();
            TcPr photoCellPr = factory.createTcPr();
            TblWidth photoCellWidth = factory.createTblWidth();
            photoCellWidth.setType("pct");
            photoCellWidth.setW(BigInteger.valueOf(1200)); // 24% width for photo (reduced)
            photoCellPr.setTcW(photoCellWidth);
            
            // Vertical alignment for photo cell
            CTVerticalJc photoVAlign = factory.createCTVerticalJc();
            photoVAlign.setVal(STVerticalJc.CENTER);
            photoCellPr.setVAlign(photoVAlign);
            
            photoCell.setTcPr(photoCellPr);
            
            // Add photo placeholder
            P photoP = factory.createP();
            PPr photoPPr = factory.createPPr();
            Jc photoJc = factory.createJc();
            photoJc.setVal(JcEnumeration.CENTER);
            photoPPr.setJc(photoJc);
            photoP.setPPr(photoPPr);
            
            R photoR = factory.createR();
            RPr photoRPr = factory.createRPr();
            
            // Set font to Aptos
            RFonts photoFonts = factory.createRFonts();
            photoFonts.setAscii("Aptos");
            photoFonts.setHAnsi("Aptos");
            photoRPr.setRFonts(photoFonts);
            
            HpsMeasure photoSize = factory.createHpsMeasure();
            photoSize.setVal(BigInteger.valueOf(20)); // 10pt
            photoRPr.setSz(photoSize);
            photoRPr.setSzCs(photoSize);
            
            photoR.setRPr(photoRPr);
            
            Text photoT = factory.createText();
            photoT.setValue("[Foto]");
            photoR.getContent().add(photoT);
            photoP.getContent().add(photoR);
            photoCell.getContent().add(photoP);
            
            // Text cell (right side) - larger to prevent line breaks with margin for spacing
            Tc textCell = factory.createTc();
            TcPr textCellPr = factory.createTcPr();
            TblWidth textCellWidth = factory.createTblWidth();
            textCellWidth.setType("pct");
            textCellWidth.setW(BigInteger.valueOf(3800)); // 76% width for text (increased)
            textCellPr.setTcW(textCellWidth);
            
            // Add left margin to text cell for spacing from photo
            TcMar textCellMar = factory.createTcMar();
            TblWidth leftMargin = factory.createTblWidth();
            leftMargin.setType("dxa");
            leftMargin.setW(BigInteger.valueOf(288)); // 288 twips = approximately 1 tab (0.5cm)
            textCellMar.setLeft(leftMargin);
            textCellPr.setTcMar(textCellMar);
            
            // Vertical alignment for text cell
            CTVerticalJc textVAlign = factory.createCTVerticalJc();
            textVAlign.setVal(STVerticalJc.TOP);
            textCellPr.setVAlign(textVAlign);
            
            textCell.setTcPr(textCellPr);
            
            // Add all data fields as simple text lines (using DD.Month date format)
            addCardTextField(textCell, "Name", "Max Mustermann");
            addCardTextField(textCell, "Geburtsdatum", "15. März"); // DD.Month format
            addCardTextField(textCell, "Medikamenteneinnahme", "Keine");
            addCardTextField(textCell, "Blutgruppe", "AB");
            addCardTextField(textCell, "Bekannte Allergien", "Penicillin");
            addCardTextField(textCell, "Ausweisnummer", "45678");
            addCardTextField(textCell, "Ausstellungsland", "Deutschland");
            
            contentRow.getContent().add(photoCell);
            contentRow.getContent().add(textCell);
            cardTable.getContent().add(contentRow);
            
            pkg.getMainDocumentPart().addObject(cardTable);
            
            // Add 4 line spacing after example card for consistency
            for (int i = 0; i < 4; i++) {
                P spacingP = factory.createP();
                PPr spacingPPr = factory.createPPr();
                PPrBase.Spacing spacing = factory.createPPrBaseSpacing();
                spacing.setAfter(BigInteger.valueOf(120)); // 6pt spacing per line
                spacingPPr.setSpacing(spacing);
                spacingP.setPPr(spacingPPr);
                pkg.getMainDocumentPart().addObject(spacingP);
            }
            
        } catch (Exception e) {
            System.err.println("Error adding example allergy card: " + e.getMessage());
            // Add text fallback
            addFormattedParagraph(pkg, "[Hier würde ein Beispiel-Allergieausweis angezeigt werden]", 
                                false, 22, 120, 120, false, JcEnumeration.CENTER, "Aptos");
        }
    }
}
