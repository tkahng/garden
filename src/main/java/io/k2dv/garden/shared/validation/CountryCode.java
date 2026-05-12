package io.k2dv.garden.shared.validation;

import io.k2dv.garden.shared.exception.ValidationException;

import java.util.List;

public final class CountryCode {

    private static final String ISO_ALPHA_2_PATTERN = "[A-Z]{2}";

    private CountryCode() {}

    public static String normalize(String value) {
        if (value == null) {
            throw invalid();
        }
        String normalized = value.trim().toUpperCase();
        if (!normalized.matches(ISO_ALPHA_2_PATTERN)) {
            throw invalid();
        }
        return normalized;
    }

    public static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream()
            .map(CountryCode::normalize)
            .toList();
    }

    private static ValidationException invalid() {
        return new ValidationException("INVALID_COUNTRY_CODE",
            "Country must be a 2-letter ISO 3166-1 alpha-2 code");
    }
}
