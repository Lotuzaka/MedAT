package docx;

import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.*;

import javax.swing.table.DefaultTableModel;
import java.io.File;
import java.util.*;

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
    public List<List<Object>> loadIntroductionPages(File docx) throws Docx4JException {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.load(docx);
        List<Object> content = pkg.getMainDocumentPart().getContent();
        List<List<Object>> pages = new ArrayList<>();
        List<Object> current = new ArrayList<>();
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
                                                 List<String> order,
                                                 List<List<Object>> introPages) throws Docx4JException {
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
     * Append the given question table to the document. Only basic text is
     * exported; options and images are ignored for brevity.
     */
    public void addQuestions(WordprocessingMLPackage pkg, DefaultTableModel model) {
        for (int r = 0; r < model.getRowCount(); r++) {
            String number = Objects.toString(model.getValueAt(r, 0), "");
            String text = Objects.toString(model.getValueAt(r, 1), "");
            if (!number.isEmpty() && !text.isEmpty()) {
                P p = factory.createP();
                R rObj = factory.createR();
                Text t = factory.createText();
                t.setValue(number + ". " + text);
                rObj.getContent().add(t);
                p.getContent().add(rObj);
                pkg.getMainDocumentPart().addObject(p);
            }
        }
    }

    /**
     * Add questions with solutions to the document.
     */
    public void addQuestionsSolution(WordprocessingMLPackage pkg, DefaultTableModel model) {
        for (int r = 0; r < model.getRowCount(); r++) {
            String number = Objects.toString(model.getValueAt(r, 0), "");
            String text = Objects.toString(model.getValueAt(r, 1), "");
            String solution = Objects.toString(model.getValueAt(r, 2), "");
            
            if (!number.isEmpty() && !text.isEmpty()) {
                // Add question
                P questionP = factory.createP();
                R questionR = factory.createR();
                Text questionT = factory.createText();
                questionT.setValue(number + ". " + text);
                questionR.getContent().add(questionT);
                questionP.getContent().add(questionR);
                pkg.getMainDocumentPart().addObject(questionP);
                
                // Add solution if available
                if (!solution.isEmpty()) {
                    P solutionP = factory.createP();
                    R solutionR = factory.createR();
                    Text solutionT = factory.createText();
                    solutionT.setValue("Solution: " + solution);
                    solutionR.getContent().add(solutionT);
                    solutionP.getContent().add(solutionR);
                    pkg.getMainDocumentPart().addObject(solutionP);
                }
            }
        }
    }

    /**
     * Add a stop sign page to the document.
     */
    public void addStopSignPage(WordprocessingMLPackage pkg) {
        P p = factory.createP();
        R rObj = factory.createR();
        Text t = factory.createText();
        t.setValue("STOP");
        rObj.getContent().add(t);
        p.getContent().add(rObj);
        pkg.getMainDocumentPart().addObject(p);
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
    public void appendPage(WordprocessingMLPackage pkg, List<Object> page) {
        for (Object o : page) {
            pkg.getMainDocumentPart().addObject(o);
        }
    }
}
