package io.k2dv.garden.shared.validation;

import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountryCodeTest {

    @Test
    void normalize_trimsAndUppercasesIsoAlpha2() {
        assertThat(CountryCode.normalize(" us ")).isEqualTo("US");
    }

    @Test
    void normalize_rejectsIsoAlpha3() {
        assertThatThrownBy(() -> CountryCode.normalize("USA"))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode")
            .isEqualTo("INVALID_COUNTRY_CODE");
    }

    @Test
    void normalizeList_preservesNullAndNormalizesEntries() {
        assertThat(CountryCode.normalizeList(null)).isNull();
        assertThat(CountryCode.normalizeList(List.of(" us ", "ca"))).containsExactly("US", "CA");
    }
}
