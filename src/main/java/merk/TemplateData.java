package merk;

import java.util.List;

public record TemplateData(MerkTemplate id, String question, List<String> variants) {}
