import docx.Docx4jPrinter;
import java.io.File;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;

public class test_memory_recall {
    public static void main(String[] args) {
        try {
            // Create a test document with memory recall phase content
            Docx4jPrinter printer = new Docx4jPrinter();
            WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
            
            // Test the memory recall template with example questions
            String testIntroText = """
                Gedächtnis und Merkfähigkeit
                Abrufphase
                
                Bearbeitungszeit für 25 Aufgaben: 15 Minuten
                
                In diesem Untertest wird überprüft, ob sie sich die Informationen der vorher gezeigten Allergieausweise der einzelnen Personen einprägen konnten. Bitte beantworten Sie die folgenden Fragen zu den Allergieausweisen.
                
                Die Aufgaben sind im Single-Choice Format gestellt und jeweils nur der gegebenen Antwortmöglichkeiten A) bis E) ist korrekt.
                Bitte markieren Sie für jede Aufgabe die korrekte Antwort in Ihrem Antwortbogen, da ausschließlich Auswertung Ihrer Ergebnisse herangezogen wird.
                
                Das Zurückblättern zum vorherigen Untertest, sowie das selbstständige Weiterblättern zum nächsten Untertest ist nicht erlaubt.
                
                Sie dürfen mit der Bearbeitung der Aufgaben erst beginnen, wenn der Testleiter den Untertest freigegeben hat!
                
                Beispielaufgabe:
                Welche Blutgruppe hat die Person mit der Ausweisnummer 32452?
                
                A) A
                B) AB
                C) 0
                D) B
                E) Keine der Antworten ist richtig.
                
                Lösung: D) B
                """;
            
            printer.addIntroductionPage(pkg, testIntroText);
            
            // Save the test document
            File outputFile = new File("test_memory_recall_output.docx");
            pkg.save(outputFile);
            
            System.out.println("Memory recall test document created successfully: " + outputFile.getAbsolutePath());
            System.out.println("Please check the document to verify:");
            System.out.println("1. Header 'Gedächtnis und Merkfähigkeit' in 18pt bold Montserrat");
            System.out.println("2. Subheader 'Abrufphase' in 14pt bold Montserrat with line break");
            System.out.println("3. Example question and answer options are properly formatted");
            System.out.println("4. Answer key (Lösung: D) B) is displayed");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
