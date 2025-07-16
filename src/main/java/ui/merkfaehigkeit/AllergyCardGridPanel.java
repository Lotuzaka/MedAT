package ui.merkfaehigkeit;

import model.AllergyCardData;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Panel containing 8 allergy cards in a grid. */
public class AllergyCardGridPanel extends JPanel {
    private final List<AllergyCardPanel> cards = new ArrayList<>(8);

    public AllergyCardGridPanel() {
        super(new GridLayout(4,2,8,8)); // Reduced spacing for smaller cards
        for(int i=0;i<8;i++) {
            AllergyCardPanel p = new AllergyCardPanel();
            cards.add(p);
            add(p);
        }
    }

    public List<AllergyCardData> getAllCards() {
        List<AllergyCardData> list = new ArrayList<>(cards.size());
        for(AllergyCardPanel p : cards) {
            list.add(p.toModel());
        }
        return list;
    }

    public void reset() {
        for(AllergyCardPanel p : cards) {
            p.reset();
        }
    }
}
