package io.k2dv.garden.shared.validation;

import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressJsonCountryTest {

    @Test
    void normalizeCountry_normalizesCountryProperty() {
        String json = AddressJsonCountry.normalizeCountry("{\"address1\":\"1 Main\",\"country\":\" us \"}");

        assertThat(json).contains("\"country\":\"US\"");
    }

    @Test
    void normalizeCountry_rejectsIsoAlpha3CountryProperty() {
        assertThatThrownBy(() -> AddressJsonCountry.normalizeCountry("{\"country\":\"USA\"}"))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode")
            .isEqualTo("INVALID_COUNTRY_CODE");
    }

    @Test
    void normalizeCountry_rejectsMalformedJson() {
        assertThatThrownBy(() -> AddressJsonCountry.normalizeCountry("{not-json"))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode")
            .isEqualTo("INVALID_ADDRESS_JSON");
    }
}
