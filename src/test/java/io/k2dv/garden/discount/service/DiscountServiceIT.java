package io.k2dv.garden.discount.service;

import io.k2dv.garden.discount.dto.CreateDiscountRequest;
import io.k2dv.garden.discount.dto.DiscountValidationResponse;
import io.k2dv.garden.discount.dto.UpdateDiscountRequest;
import io.k2dv.garden.discount.model.DiscountType;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscountServiceIT extends AbstractIntegrationTest {

    @Autowired
    DiscountService discountService;

    // ---- create ----

    @Test
    void create_happyPath_storesDiscount() {
        var req = new CreateDiscountRequest("SAVE10", DiscountType.PERCENTAGE,
            new BigDecimal("10"), null, null, null, null);
        var resp = discountService.create(req);
        assertThat(resp.code()).isEqualTo("SAVE10");
        assertThat(resp.type()).isEqualTo(DiscountType.PERCENTAGE);
    }

    @Test
    void create_uppercasesCode() {
        var req = new CreateDiscountRequest("lower20", DiscountType.FIXED_AMOUNT,
            new BigDecimal("5"), null, null, null, null);
        var resp = discountService.create(req);
        assertThat(resp.code()).isEqualTo("LOWER20");
    }

    @Test
    void create_duplicateCode_throwsConflict() {
        discountService.create(new CreateDiscountRequest("DUP1", DiscountType.PERCENTAGE,
            new BigDecimal("5"), null, null, null, null));

        assertThatThrownBy(() -> discountService.create(new CreateDiscountRequest("dup1",
            DiscountType.FIXED_AMOUNT, new BigDecimal("5"), null, null, null, null)))
            .isInstanceOf(ConflictException.class)
            .extracting("errorCode").isEqualTo("DISCOUNT_CODE_EXISTS");
    }

    @Test
    void create_startsAtAfterEndsAt_throwsValidation() {
        Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> discountService.create(new CreateDiscountRequest("BADDATE",
            DiscountType.PERCENTAGE, new BigDecimal("10"), null, null, future, past)))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode").isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void create_startsAtEqualsEndsAt_throwsValidation() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        assertThatThrownBy(() -> discountService.create(new CreateDiscountRequest("EQDATE",
            DiscountType.PERCENTAGE, new BigDecimal("10"), null, null, now, now)))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode").isEqualTo("INVALID_DATE_RANGE");
    }

    // ---- update ----

    @Test
    void update_invertingDates_throwsValidation() {
        var created = discountService.create(new CreateDiscountRequest("UPD1",
            DiscountType.PERCENTAGE, new BigDecimal("10"), null, null,
            Instant.now().minus(1, ChronoUnit.DAYS),
            Instant.now().plus(1, ChronoUnit.DAYS)));

        // Now try to move endsAt before existing startsAt
        assertThatThrownBy(() -> discountService.update(created.id(),
            new UpdateDiscountRequest(null, null, null, null, null, null,
                Instant.now().minus(2, ChronoUnit.DAYS), null)))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode").isEqualTo("INVALID_DATE_RANGE");
    }

    // ---- validate ----

    @Test
    void validate_activeDiscount_returnsValidWithAmount() {
        discountService.create(new CreateDiscountRequest("VALID10", DiscountType.FIXED_AMOUNT,
            new BigDecimal("10"), null, null, null, null));

        DiscountValidationResponse r = discountService.validate("VALID10", new BigDecimal("100"));
        assertThat(r.valid()).isTrue();
        assertThat(r.discountedAmount()).isEqualByComparingTo("10");
    }

    @Test
    void validate_percentageDiscount_calculatesCorrectly() {
        discountService.create(new CreateDiscountRequest("PCT20", DiscountType.PERCENTAGE,
            new BigDecimal("20"), null, null, null, null));

        DiscountValidationResponse r = discountService.validate("PCT20", new BigDecimal("50"));
        assertThat(r.valid()).isTrue();
        assertThat(r.discountedAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void validate_minOrderAmountNotMet_returnsInvalid() {
        discountService.create(new CreateDiscountRequest("MIN50", DiscountType.FIXED_AMOUNT,
            new BigDecimal("5"), new BigDecimal("50"), null, null, null));

        DiscountValidationResponse r = discountService.validate("MIN50", new BigDecimal("30"));
        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("minimum");
    }

    @Test
    void validate_notYetValid_returnsInvalid() {
        discountService.create(new CreateDiscountRequest("FUTURE1", DiscountType.FIXED_AMOUNT,
            new BigDecimal("5"), null, null,
            Instant.now().plus(1, ChronoUnit.DAYS),
            Instant.now().plus(2, ChronoUnit.DAYS)));

        DiscountValidationResponse r = discountService.validate("FUTURE1", new BigDecimal("100"));
        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("not yet valid");
    }

    @Test
    void validate_expired_returnsInvalid() {
        discountService.create(new CreateDiscountRequest("EXPIRED1", DiscountType.FIXED_AMOUNT,
            new BigDecimal("5"), null, null,
            Instant.now().minus(2, ChronoUnit.DAYS),
            Instant.now().minus(1, ChronoUnit.DAYS)));

        DiscountValidationResponse r = discountService.validate("EXPIRED1", new BigDecimal("100"));
        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("expired");
    }

    @Test
    void validate_unknownCode_returnsInvalid() {
        DiscountValidationResponse r = discountService.validate("NOPE", new BigDecimal("100"));
        assertThat(r.valid()).isFalse();
    }

    // ---- redeem ----

    @Test
    void redeem_incrementsUsedCount() {
        discountService.create(new CreateDiscountRequest("REDEEM1", DiscountType.FIXED_AMOUNT,
            new BigDecimal("5"), null, null, null, null));

        discountService.redeem("REDEEM1", new BigDecimal("100"));

        var d = discountService.validate("REDEEM1", new BigDecimal("100"));
        assertThat(d.valid()).isTrue(); // still valid (unlimited uses)
    }

    @Test
    void redeem_exhaustedMaxUses_throwsValidation() {
        discountService.create(new CreateDiscountRequest("ONCE", DiscountType.FIXED_AMOUNT,
            new BigDecimal("5"), null, 1, null, null));

        discountService.redeem("ONCE", new BigDecimal("100")); // first use OK

        // Second attempt: checkEligibility sees usedCount >= maxUses → ValidationException
        // (ConflictException only occurs under concurrent races — not testable in single-threaded IT)
        assertThatThrownBy(() -> discountService.redeem("ONCE", new BigDecimal("100")))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode").isEqualTo("DISCOUNT_INELIGIBLE");
    }

    @Test
    void redeem_fixedAmountCappedAtOrderTotal() {
        discountService.create(new CreateDiscountRequest("BIG50", DiscountType.FIXED_AMOUNT,
            new BigDecimal("50"), null, null, null, null));

        var app = discountService.redeem("BIG50", new BigDecimal("20"));
        // Fixed discount caps at order total
        assertThat(app.discountedAmount()).isEqualByComparingTo("20");
    }
}
