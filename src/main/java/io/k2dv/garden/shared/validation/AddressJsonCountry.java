package io.k2dv.garden.shared.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.k2dv.garden.shared.exception.ValidationException;

public final class AddressJsonCountry {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AddressJsonCountry() {}

    public static String normalizeCountry(String addressJson) {
        if (addressJson == null) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(addressJson);
            JsonNode country = root.get("country");
            if (country == null || country.isNull()) {
                return addressJson;
            }
            if (!country.isTextual() || !(root instanceof ObjectNode objectNode)) {
                throw invalidAddressCountry();
            }
            objectNode.put("country", CountryCode.normalize(country.asText()));
            return MAPPER.writeValueAsString(objectNode);
        } catch (JsonProcessingException e) {
            throw new ValidationException("INVALID_ADDRESS_JSON", "Shipping address must be valid JSON");
        }
    }

    private static ValidationException invalidAddressCountry() {
        return new ValidationException("INVALID_COUNTRY_CODE",
            "Shipping address country must be a 2-letter ISO 3166-1 alpha-2 code");
    }
}
