package ui.merkfaehigkeit;

import model.AllergyCardData;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Panel containing 8 allergy cards in a grid. */
public class AllergyCardGridPanel extends JPanel {
    private final List<AllergyCardPanel> cards = new ArrayList<>(8);

    public AllergyCardGridPanel() {
        super(new GridLayout(4, 2, 5, 5)); // Minimal spacing for very compact cards
        setBackground(Color.WHITE); // Set background to white
        for (int i = 0; i < 8; i++) {
            AllergyCardPanel p = new AllergyCardPanel();
            cards.add(p);
            add(p);
        }
    }

    public List<AllergyCardData> getAllCards() {
        List<AllergyCardData> list = new ArrayList<>(cards.size());
        for (AllergyCardPanel p : cards) {
            list.add(p.toModel());
        }
        return list;
    }

    public void reset() {
        for (AllergyCardPanel p : cards) {
            p.reset();
        }
    }

    /**
     * Generate random data for all 8 allergy cards.
     */
    public void generateRandomData() {
        Random random = new Random();

        // Made-up names that sound like real names but aren't
        String[] madeUpNames = {
                "Maxim", "Annika", "Petrus", "Marianne", "Hansen", "Lisette", "Klausius", "Juliana",
                "Wernhard", "Sabina", "Thomsen", "Andrina", "Michaelis", "Petrina", "Stefanus", "Claudine",
                "Bernhart", "Katrina", "Joachimus", "Martinelle", "Wilhelmus", "Christiane", "Leonhard", "Franziska",
                "Rudolfus", "Margarete", "Antonius", "Elisabeth", "Dominikus", "Valentina", "Augustinus", "Karolina"
        };

        // Common allergies (1-3 random)
        String[] allergies = {
                "Erdnuss", "Haselnuss", "Walnuss", "Cashew", "Mandel", "Pistazie", "Paranuss", "Macadamia",
                "Sesam", "Sonnenblumenkerne", "Kürbiskerne", "Sojabohne", "Kuhmilch", "Ziegenmilch", "Hühnerei",
                "Wachtelei",
                "Weizen", "Roggen", "Gerste", "Hafer", "Reis", "Mais", "Buchweizen", "Quinoa",
                "Lachs", "Thunfisch", "Kabeljau", "Garnelen", "Krabben", "Hummer", "Miesmuschel", "Auster",
                "Kalmar", "Hühnerfleisch", "Rindfleisch", "Schweinefleisch", "Lammfleisch", "Kaninchenfleisch",
                "Gelatine", "Gluten",
                "Lupinenprotein", "Sellerie", "Karotte", "Petersilienwurzel", "Kartoffel", "Tomate", "Paprika",
                "Aubergine",
                "Gurke", "Zucchini", "Avocado", "Banane", "Kiwi", "Mango", "Pfirsich", "Aprikose",
                "Kirsche", "Pflaume", "Apfel", "Birne", "Erdbeere", "Heidelbeere", "Brombeere", "Himbeere",
                "Weintraube", "Orange", "Zitrone", "Limette", "Grapefruit", "Granatapfel", "Honigmelone",
                "Wassermelone",
                "Ananas", "Kokosnuss", "Kakao", "Kaffee", "Schwarzer Tee", "Grüner Tee", "Gewürznelke", "Zimt",
                "Muskatnuss", "Kurkuma", "Kreuzkümmel", "Koriander", "Dill", "Thymian", "Oregano", "Basilikum",
                "Rosmarin", "Ingwer", "Knoblauch", "Zwiebel", "Senf", "Meerrettich", "Schwarzer Pfeffer", "Chili",
                "Safran", "Vanillin", "Backhefe", "Aspartam", "Natriumbenzoat", "Sulfite", "Tartrazin", "Cochenille",
                "Glutamat", "Propylenglykol", "Formaldehyd", "Limonen", "Parabene", "Isothiazolinone", "Nickel",
                "Kobalt",
                "Chromat", "Latex", "Neomycin", "Bacitracin", "Penicillin", "Cephalosporine", "Sulfonamide",
                "Tetracycline",
                "Makrolide", "Acetylsalicylsäure", "Ibuprofen", "Naproxen", "Codein", "Morphin", "Lidocain",
                "Iodhaltiges Kontrastmittel",
                "Chlorhexidin", "Ethylenoxid", "Acrylate", "Paraphenylendiamin", "Lanolin", "Wollwachsalkohole",
                "Propolis", "Bienenstich",
                "Wespenstich", "Hornissenstich", "Feuerameise", "Hausstaubmilbe", "Vorratsmilbe", "Alternaria",
                "Cladosporium", "Aspergillus fumigatus",
                "Penicillium notatum", "Katzenhaare", "Hundehaare", "Pferdehaare", "Meerschweinchen-Schuppen",
                "Kaninchen-Schuppen", "Hamster-Schuppen", "Ratten-Schuppen",
                "Mäuse-Schuppen", "Wellensittich-Federn", "Tauben-Proteine", "Kakerlaken-Allergene", "Silberfischchen",
                "Roggengraspollen", "Timotheegras-Pollen", "Beifußpollen",
                "Ambrosiapollen", "Birkenpollen", "Haselpollen", "Erlenpollen", "Eichenpollen", "Olivenpollen",
                "Platanenpollen", "Ulmenpollen",
                "Eschenpollen", "Lindenpollen", "Zypressenpollen", "Pinienpollen", "Sonnenblumenpollen",
                "Baumwollstaub", "Mahagonistäube", "Buchen-Sägemehl",
                "Latexhandschuhe", "Isopropanol", "Eugenol", "Zementstaub", "Mehlstaub", "Fischmehl", "Tierfutterstaub",
                "α-Amylase",
                "Chymotrypsin", "Metallbearbeitungs-Kühlflüssigkeit", "Dieselabgase", "Ozon", "Formaldehydharze",
                "Epoxidharz", "UV-Härtungslacke", "Photoinitiator TPO",
                "Benzalkoniumchlorid", "Kaliumdichromat", "Thimerosal", "Ethanol", "Zitronensäure", "Carrageen",
                "Guarkernmehl", "Xanthan"
        };

        // All countries in the world (sample of 50+ major countries)
        String[] countries = {
                "Afghanistan", "Albanien", "Algerien", "Andorra", "Angola", "Antigua und Barbuda", "Argentinien",
                "Armenien",
                "Australien", "Österreich", "Aserbaidschan", "Bahamas", "Bahrain", "Bangladesch", "Barbados", "Belarus",
                "Belgien", "Belize", "Benin", "Bhutan", "Bolivien", "Bosnien und Herzegowina", "Botsuana", "Brasilien",
                "Brunei", "Bulgarien", "Burkina Faso", "Burundi", "Cabo Verde", "Kambodscha", "Kamerun", "Kanada",
                "Zentralafrikanische Republik", "Tschad", "Chile", "China", "Kolumbien", "Komoren", "Kongo (Republik)",
                "Demokratische Republik Kongo",
                "Costa Rica", "Côte d’Ivoire", "Kroatien", "Kuba", "Zypern", "Tschechien", "Dänemark", "Dschibuti",
                "Dominica", "Dominikanische Republik", "Ecuador", "Ägypten", "El Salvador", "Äquatorialguinea",
                "Eritrea", "Estland",
                "Eswatini", "Äthiopien", "Fidschi", "Finnland", "Frankreich", "Gabun", "Gambia", "Georgien",
                "Deutschland", "Ghana", "Griechenland", "Grenada", "Guatemala", "Guinea", "Guinea-Bissau", "Guyana",
                "Haiti", "Honduras", "Ungarn", "Island", "Indien", "Indonesien", "Iran", "Irak",
                "Irland", "Israel", "Italien", "Jamaika", "Japan", "Jordanien", "Kasachstan", "Kenia",
                "Kiribati", "Nordkorea", "Südkorea", "Kuwait", "Kirgisistan", "Laos", "Lettland", "Libanon",
                "Lesotho", "Liberia", "Libyen", "Liechtenstein", "Litauen", "Luxemburg", "Madagaskar", "Malawi",
                "Malaysia", "Malediven", "Mali", "Malta", "Marshallinseln", "Mauretanien", "Mauritius", "Mexiko",
                "Mikronesien", "Moldau", "Monaco", "Mongolei", "Montenegro", "Marokko", "Mosambik", "Myanmar",
                "Namibia", "Nauru", "Nepal", "Niederlande", "Neuseeland", "Nicaragua", "Niger", "Nigeria",
                "Nordmazedonien", "Norwegen", "Oman", "Pakistan", "Palau", "Panama", "Papua-Neuguinea", "Paraguay",
                "Peru", "Philippinen", "Polen", "Portugal", "Katar", "Rumänien", "Russland", "Ruanda",
                "St. Kitts und Nevis", "St. Lucia", "St. Vincent und die Grenadinen", "Samoa", "San Marino",
                "São Tomé und Príncipe", "Saudi-Arabien", "Senegal",
                "Serbien", "Seychellen", "Sierra Leone", "Singapur", "Slowakei", "Slowenien", "Salomonen", "Somalia",
                "Südafrika", "Südsudan", "Spanien", "Sri Lanka", "Sudan", "Suriname", "Schweden", "Schweiz",
                "Syrien", "Tadschikistan", "Tansania", "Thailand", "Timor-Leste", "Togo", "Tonga",
                "Trinidad und Tobago",
                "Tunesien", "Türkei", "Turkmenistan", "Tuvalu", "Uganda", "Ukraine", "Vereinigte Arabische Emirate",
                "Vereinigtes Königreich",
                "Vereinigte Staaten von Amerika", "Uruguay", "Usbekistan", "Vanuatu", "Vatikanstadt", "Venezuela",
                "Vietnam", "Jemen",
                "Sambia", "Simbabwe", "Staat Palästina"

        };

        // Blood groups
        String[] bloodGroups = { "A", "B", "AB", "0" };

        // Medications (yes/no)
        String[] medications = { "Ja", "Nein" };

        for (int i = 0; i < cards.size(); i++) {
            AllergyCardPanel card = cards.get(i);

            // Generate random single name (not first+last name)
            String singleName = madeUpNames[random.nextInt(madeUpNames.length)];

            // Generate random birth date (day 1-28, random month)
            int day = random.nextInt(28) + 1; // 1-28 to avoid month-specific issues
            int month = random.nextInt(12) + 1; // 1-12
            LocalDate birthDate = LocalDate.of(LocalDate.now().getYear(), month, day);

            // Generate random medication (Ja/Nein)
            String medication = medications[random.nextInt(medications.length)];

            // Generate random blood group
            String bloodGroup = bloodGroups[random.nextInt(bloodGroups.length)];

            // Generate 1-3 random allergies
            int allergyCount = random.nextInt(3) + 1; // 1-3 allergies
            StringBuilder allergyList = new StringBuilder();
            List<String> selectedAllergies = new ArrayList<>();

            for (int j = 0; j < allergyCount; j++) {
                String allergy;
                do {
                    allergy = allergies[random.nextInt(allergies.length)];
                } while (selectedAllergies.contains(allergy)); // Avoid duplicates

                selectedAllergies.add(allergy);
                if (j > 0)
                    allergyList.append(", ");
                allergyList.append(allergy);
            }

            // Generate random 5-digit ID number
            String idNumber = String.format("%05d", random.nextInt(100000));

            // Generate random country
            String country = countries[random.nextInt(countries.length)];

            // Create AllergyCardData and load it into the card
            AllergyCardData data = new AllergyCardData(
                    singleName,
                    birthDate,
                    medication,
                    bloodGroup,
                    allergyList.toString(),
                    idNumber,
                    country,
                    null // No image generated
            );

            card.load(data);
        }
    }

    /**
     * Load allergy card data from a list (typically from database).
     */
    public void loadCards(List<AllergyCardData> cardDataList) {
        // Clear existing data first
        reset();

        // Load the provided data into cards (up to 8 cards)
        for (int i = 0; i < Math.min(cardDataList.size(), cards.size()); i++) {
            AllergyCardData data = cardDataList.get(i);
            AllergyCardPanel card = cards.get(i);
            card.load(data);
        }
    }
}
