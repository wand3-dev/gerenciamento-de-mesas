package com.garcom.appmesas;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class StringHelper {
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    public static String normalizar(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(normalized).replaceAll("").toLowerCase().trim();
    }
}
