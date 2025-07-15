package model;

import java.time.LocalDate;

public record AllergyCardData(
    String name,
    LocalDate geburtsdatum,
    String medikamenteneinnahme,
    String blutgruppe,
    String bekannteAllergien,
    String ausweisnummer,
    String ausstellungsland,
    byte[] bildPng
) {}
