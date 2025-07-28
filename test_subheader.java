import docx.Docx4jPrinter;
import java.io.File;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;

public class test_subheader {
    public static void main(String[] args) {
        try {
            // Create a test document with memory learning phase content
            Docx4jPrinter printer = new Docx4jPrinter();
            WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
            
            // Test the subheader formatting with memory learning phase content
            String testIntroText = """
                Gedächtnis und Merkfähigkeit
                Lernphase
                
                Lernzeit für 8 Ausweise: 8 Minuten
                
                Die folgenden Aufgaben überprüfen Ihre Fähigkeit Bilder und Fakten zu merken und später Fragen diesbezüglich zu beantworten.
                Ihnen werden insgesamt 8 Allergieausweise mit je 8 Merkmalen angezeigt. Bitte prägen Sie sich alle Fakten in der zu Ihnen zur Verfügung stehenden Zeit gut ein.
                
                Bitte beachten Sie, dass während der Lernphase keinerlei Hilfsmaterialien erlaubt sind!
                Bitte legen Sie Ihr Schreibgerät auf den Tisch vor Ihnen.
                
                Beispielausweis:
                
                Die Person mit der Blutgruppe B hat welche Allergie?
                A) Pollen
                B) Staub  
                C) Penicillin
                D) Keine
                E) Milben
                """;
            
            printer.addIntroductionPage(pkg, testIntroText);
            
            // Save the test document
            File outputFile = new File("test_subheader_output.docx");
            pkg.save(outputFile);
            
            System.out.println("Test document created successfully: " + outputFile.getAbsolutePath());
            System.out.println("Please check the document to verify:");
            System.out.println("1. Header 'Gedächtnis und Merkfähigkeit' in 18pt bold Montserrat");
            System.out.println("2. Subheader 'Lernphase' in 14pt bold Montserrat with line break (not paragraph break)");
            System.out.println("3. Example allergy card displayed after 'Beispielausweis:'");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
