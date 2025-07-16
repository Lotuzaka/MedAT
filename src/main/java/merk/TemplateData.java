package merk;

import java.util.List;

public record TemplateData(MerkTemplate id, String question, List<String> variants) {
    public String randomVariant(java.util.Random rnd) {
        if (variants == null || variants.isEmpty()) {
            return question;
        }
        int total = variants.size() + 1; // +1 for the original question
        int choice = rnd.nextInt(total);
        if (choice == 0) {
            return question;
        } else {
            return variants.get(choice - 1);
        }
    }
}
