import java.util.ArrayList;
import java.util.Scanner;


public class Implikationen {
    private static ArrayList<String> erg = new ArrayList<String>(100);
    private static void implikation (String quantor1, String quantor2, String A, String B, String C, boolean opt){
        String ergebnis, antA, antB, antC, antD;
        ergebnis = antA = antB = antC = antD = "";
        ArrayList<String> ant = new ArrayList<String>();
        antworten(A, C, ant);
        antworten(B, C, ant);
        ant.add("Keine gültige Lösung");
        double zuweisen;

        String praem1;
        String praem2;

        if (opt) {
            if (quantor1.equals("Einige nicht")) praem1 = "Einige " + A + " sind nicht " + B;
            else praem1 = quantor1 + " " + A + " sind " + B;
            System.out.println(praem1);

            if (quantor2.equals("Einige nicht")) praem2 = "Einige " + A + " sind nicht " + C;
            else praem2 = quantor2 + " " + A + " sind " + C;
            System.out.println(praem2);
        } else {
            if (quantor1.equals("Einige nicht")) praem1 = "Einige " + A + " sind nicht " + B;
            else praem1 = quantor1 + " " + A + " sind " + B;
            System.out.println(praem1);

            if (quantor2.equals("Einige nicht")) praem2 = "Einige " + B + " sind nicht " + C;
            else praem2 = quantor2 + " " + B + " sind " + C;
            System.out.println(praem2);
        }

        ant.remove(praem1);
        ant.remove(praem2);

        if (quantor1.equals("Alle") && quantor2.equals("Alle")) {
            if (opt) ergebnis = "Einige " + A + " sind " + C;
            else ergebnis = "Alle " + A + " sind " + C;
        } else if(quantor1.equals("Alle") && quantor2.equals("Einige")) {
            if (opt) ergebnis = "Einige " + A + " sind " + C;
            else ergebnis = "Keine gültige Lösung";
        } else if(quantor1.equals("Einige") && quantor2.equals("Alle")) {
            ergebnis = "Einige " + A + " sind " + C;
        } else if(quantor1.equals("Alle") && quantor2.equals("Keine")) {
            if (opt) ergebnis = "Einige " + A + " sind nicht " + C;
            else ergebnis = "Keine " + A + " sind " + C;
        } else if(quantor1.equals("Keine") && quantor2.equals("Alle")) {
            ergebnis = "Einige " + C + " sind nicht " + A;
        } else if(quantor1.equals("Alle") && quantor2.equals("Einige nicht")) {
            if (opt) ergebnis = "Einige " + A + " sind nicht " + C;
            else ergebnis = "Keine gültige Lösung";
        } else if(quantor1.equals("Einige nicht") && quantor2.equals("Alle")) {
            if (opt) ergebnis = "Einige " + C + " sind nicht " + A;
            else ergebnis = "Keine gültige Lösung";
        } else if(quantor1.equals("Einige") && quantor2.equals("Keine")) {
            ergebnis = "Einige " + A + " sind nicht " + C;
        } else if(quantor1.equals("Keine") && quantor2.equals("Einige")) {
            ergebnis = "Einige " + C + " sind nicht " + A;
        }

        for (int i = 0; i < 4; i++) {
            zuweisen = Math.random() * (ant.size()-1);
            String out = ant.get((int)zuweisen);
            if (ergebnis.equals(out) || antA.equals(out) || antB.equals(out)|| antC.equals(out) || antD.equals(out)) {
                ant.remove((int)zuweisen);
                i--;
            } else {
                if (i == 0) antA = out;
                if (i == 1) antB = out;
                if (i == 2) antC = out;
                if (i == 3) antD = out;
            }
        }

        System.out.println();
        zuweisen = (int)(Math.random() * 100);
        if (zuweisen > 60) {
            if (zuweisen > 80) {
                System.out.println("A) " + ergebnis);
                System.out.println("B) " + antA);
                erg.add("A");
            } else {
                System.out.println("A) " + antA);
                System.out.println("B) " + ergebnis);
                erg.add("B");
            }
            System.out.println("C) " + antB);
            System.out.println("D) " + antC);
            System.out.println("E) " + antD);

        } else if (zuweisen > 20){
            System.out.println("A) " + antA);
            System.out.println("B) " + antB);
            if (zuweisen > 40) {
                System.out.println("C) " + ergebnis);
                System.out.println("D) " + antC);
                erg.add("C");
            } else {
                System.out.println("C) " + antC);
                System.out.println("D) " + ergebnis);
                erg.add("D");
            }
            System.out.println("E) " + antD);
        } else {
            System.out.println("A) " + antA);
            System.out.println("B) " + antB);
            System.out.println("C) " + antC);
            System.out.println("D) " + antD);
            System.out.println("E) " + ergebnis);
            erg.add("E");
        }
        System.out.println();
    }

    private static void antworten(String B, String C, ArrayList<String> ant) {
        ant.add("Alle " + B + " sind " + C);
        ant.add("Einige " + B + " sind " + C);
        ant.add("Keine " + B + " sind " + C);
        ant.add("Einige " + B + " sind nicht " + C);
        ant.add("Keine " + C + " sind " + B);
        ant.add("Einige " + C + " sind nicht " + B);
    }

    public static void main(String[] args) {
        String[] words = new String[]{"Stifte", "Lieder", "Mäuse", "Tiere", "Menschen", "Tastaturen", "Buchstaben", "Hefte", "Kulis", "Zettel", "Bücher", "Namen", "Monate", "Rechnungen", "Krankheiten", "Geburtstage", "Bälle", "Hosen", "Kinder", "Taschen", "Computer", "Tage", "Wochen", "Jahre", "Spiele", "Kartons", "Tafeln", "Süßigkeiten", "Kühe", "Katzen", "Hunde", " Mappen", "Karten", "Lieder", "Tablets", "Laptops", "Taschentücher", "Bleistifte", "Bildschirme", "Fenster", "Türen", "Einkäufe", "Schlösser", "Packungen", "Zimmer", "Wohnungen", "Häuser", "Bäume", "Blätter", "Blumen", "Äste", "Pavillons", "Mäntel", "Kittel", "Ketten", "Ohrringe", "Ringe", "Armbänder", "Uhren", "Kalender", "Tassen", "Teller", "Messer", "Löffel", "Rosen", "Anzüge", "Äpfel", "Bananen", "Lösungen", "Arme", "Ärzte", "Ausweise", "Bahnhöfe", "Berge", "Berufe", "Drucker", "Flure", "Füller", "Sommer", "Winter", "Gäste", "Häfen", "Hamburger", "Keller", "Kellner", "Köche", "Kuchen", "Läden", "Löcher", "Lehrer", "Märkte", "Pullover", "Röcke", "Schränke", "Stühle", "Sessel", "Supermärkte", "Teppiche", "Tests", "Zeiger", "Wünsche", "Bankkarten", "Adressen", "Gassen", "Straßen", "Zuschauer", "Patienten", "Operateure", "Gäste", "Bestellungen", "Bibliotheken", "Brillen", "Gläser", "Sonnenbrillen", "Brücken", "Disketten", "Dolmetscher", "Duschen", "Einladungen", "Hochzeiten", "Funktionen", "Gabeln", "Garagen", "Gärten", "Hütten", "Staubsauger", "Putzmittel", "Informationen", "Kreiden", "Bleistifte", "Radiergummis", "Kulturen", "Länder", "Städte", "Küchen", "Wohnzimmer", "Lampen", "Landkarten", "Mandarinen", "Landschaften", "Maschinen", "Studien", "Nachrichten", "Pausen", "Polizisten", "Lichter", "Pflanzen", "Krankheiten", "Prüfungen", "Ersatzleistungen", "Sachen", "Reservierungen", "Schulen", "Kindergärten", "Socken", "Schuhe", "Schlapfen", "Planeten", "Torten", "Handtücher", "Kinos", "Spiele", "Restaurants", "Cafés", "Tassen", "Räder", "Flugzeuge", "Verkehrsmittel", "Universitäten", "Hochschulen", "Uhren", "Verbindungen", "Zeitungen", "Autos", "Bilder", "Fotos", "Brötchen", "Fahrräder", "Handys", "Hotels", "Flaschen", "Tücher", "Stücke", "Birnen", "Orangen", "Anrufe", "Einwohner", "Flughäfen", "", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
        String wordA, wordB, wordC;
        Scanner sc = new Scanner(System.in);
        double zufall;
        byte easy = 0, medium = 0, hard = 0;
        int wCounter = 0;
        boolean variante;


        /*  20 einfache Beispiele
            variante: 50/50 Chance, dass zweite Option ausgewählt wird
            zufall: damit die Beispiele "zufällig" ausgewählt werden
        */

        System.out.println("---------- EASY ----------");
        for (; easy < 20; easy++) {
            variante = Math.random() * (100) < 50;
            zufall = Math.random()*(10);
            if (wCounter>=words.length-1) wCounter = 0;
            wordA = words[wCounter++];
            wordB = words[wCounter++];
            wordC = words[wCounter++];
            System.out.println(easy+1 +  ")");
            if (zufall <= 3) {
                implikation("Alle","Alle", wordA, wordB, wordC, variante);
            }
            if (zufall > 3 && zufall <= 6) {
                implikation("Alle","Einige", wordA, wordB, wordC, variante);
            }
            if (zufall > 6 && zufall <= 10) {
                implikation("Einige","Alle", wordA, wordB, wordC, variante);
            }
        }
        System.out.println("---------- MEDIUM ----------");
        for (; medium < 40; medium++) {
            variante = Math.random() * (100) < 50;
            zufall = Math.random()*(10);
            if (wCounter>=words.length-1) wCounter = 0;
            wordA = words[wCounter++];
            wordB = words[wCounter++];
            wordC = words[wCounter++];
            System.out.println(medium+1 + ")");
            if (zufall <= 3) {
                implikation("Alle","Keine", wordA, wordB, wordC, variante);
            }
            if (zufall > 3 && zufall <= 6) {
                implikation("Keine","Alle", wordA, wordB, wordC, variante);
            }
            if (zufall > 6 && zufall <= 10) {
                implikation("Alle","Einige nicht", wordA, wordB, wordC, variante);
            }
        }
        System.out.println("---------- HARD ----------");
        for (; hard < 40; hard++) {
            variante = Math.random() * (100) < 50;
            zufall = Math.random()*(10);
            if (wCounter>=words.length-3) wCounter = 0;
            wordA = words[wCounter++];
            wordB = words[wCounter++];
            wordC = words[wCounter++];
            System.out.println(hard+1 + ")");
            if (zufall <= 3) {
                implikation("Einige nicht","Alle", wordA, wordB, wordC, variante);
            }
            if (zufall > 3 && zufall <= 6) {
                implikation("Einige","Keine", wordA, wordB, wordC, variante);
            }
            if (zufall > 6 && zufall <= 10) {
                implikation("Keine","Einige", wordA, wordB, wordC, variante);
            }
        }

        System.out.println("---------- LÖSUNGEN ----------");
        int i = 1;
        for (String x: erg) {
            System.out.println(i + ") " + x);
            i++;
        }
    }
}
