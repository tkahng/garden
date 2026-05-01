package io.k2dv.garden.discount.service;

import io.k2dv.garden.b2b.model.Company;
import io.k2dv.garden.b2b.repository.CompanyRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscountServiceIT extends AbstractIntegrationTest {

    @Autowired
    DiscountService discountService;
    @Autowired
    CompanyRepository companyRepo;

    private UUID savedCompanyId() {
        Company c = new Company();
        c.setName("Test Company " + UUID.randomUUID());
        return companyRepo.save(c).getId();
    }

    // ---- create ----

    @Test
    void create_happyPath_storesDiscount() {
        var req = new CreateDiscountRequest("SAVE10", false, DiscountType.PERCENTAGE,
            new BigDecimal("10"), null, null, null, null, null);
        var resp = discountService.create(req);
        assertThat(resp.code()).isEqualTo("SAVE10");
        assertThat(resp.type()).isEqualTo(DiscountType.PERCENTAGE);
    }

    @Test
    void create_uppercasesCode() {
        var req = new CreateDiscountRequest("lower20", false, DiscountType.FIXED_AMOUNT,
            new BigDecimal("5"), null, null, null, null, null);
        var resp = discountService.create(req);
        assertThat(resp.code()).isEqualTo("LOWER20");
    }

    @Test
    void create_duplicateCode_throwsConflict() {
        discountService.create(new CreateDiscountRequest("DUP1", false, DiscountType.PERCENTAGE,
            new BigDecimal("5"), null, null, null, null, null));

        assertThatThrownBy(() -> discountService.create(new CreateDiscountRequest("dup1",
            false, DiscountType.FIXED_AMOUNT, new BigDecimal("5"), null, null, null, null, null)))
            .isInstanceOf(ConflictException.class)
            .extracting("errorCode").isEqualTo("DISCOUNT_CODE_EXISTS");
    }

    @Test
    void create_startsAtAfterEndsAt_throwsValidation() {
        Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> discountService.create(new CreateDiscountRequest("BADDATE",
            false, DiscountType.PERCENTAGE, new BigDecimal("10"), null, null, future, past, null)))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode").isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void create_startsAtEqualsEndsAt_throwsValidation() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        assertThatThrownBy(() -> discountService.create(new CreateDiscountRequest("EQDATE",
            false, DiscountType.PERCENTAGE, new BigDecimal("10"), null, null, now, now, null)))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode").isEqualTo("INVALID_DATE_RANGE");
    }

    // ---- update ----

    @Test
    void update_invertingDates_throwsValidation() {
        var created = discountService.create(new CreateDiscountRequest("UPD1",
            false, DiscountType.PERCENTAGE, new BigDecimal("10"), null, null,
            Instant.now().minus(1, ChronoUnit.DAYS),
            Instant.now().plus(1, ChronoUnit.DAYS), null));

        assertThatThrownBy(() -> discountService.update(created.id(),
            new UpdateDiscountRequest(null, null, null, null, null, null,
                Instant.now().minus(2, ChronoUnit.DAYS), null, null)))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode").isEqualTo("INVALID_DATE_RANGE");
    }

    // ---- validate ----

    @Test
    void validate_activeDiscount_returnsValidWithAmount() {
        discountService.create(new CreateDiscountRequest("VALID10", false, DiscountType.FIXED_AMOUNT,
            new BigDecimal("10"), null, null, null, null, null));

        DiscountValidationResponse r = discountService.validate("VALID10", new BigDecimal("100"));
        assertThat(r.valid()).isTrue();
        assertThat(r.discountedAmount()).isEqualByComparingTo("10");
    }

    @Test
    void validate_percentageDiscount_calculatesCorrectly() {
        discountService.create(new CreateDiscountRequest("PCT20", false, DiscountType.PERCENTAGE,
            new BigDecimal("20"), null, null, null, null, null));

        DiscountValidationResponse r = discountService.validate("PCT20", new BigDecimal("50"));
        assertThat(r.valid()).isTrue();
        assertThat(r.discountedAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void validate_minOrderAmountNotMet_returnsInvalid() {
        discountService.create(new CreateDiscountRequest("MIN50", false, DiscountType.FIXED_AMOUNT,
            new BigDecimal("5"), new BigDecimal("50"), null, null, null, null));

        DiscountValidationResponse r = discountService.validate("MIN50", new BigDecimal("30"));
        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("minimum");
    }

    @Test
    void validate_notYetValid_returnsInvalid() {
        discountService.create(new CreateDiscountRequest("FUTURE1", false, DiscountType.FIXED_AMOUNT,
            new BigDecimal("5"), null, null,
            Instant.now().plus(1, ChronoUnit.DAYS),
            Instant.now().plus(2, ChronoUnit.DAYS), null));

        DiscountValidationResponse r = discountService.validate("FUTURE1", new BigDecimal("100"));
        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("not yet valid");
    }

    @Test
    void validate_expired_returnsInvalid() {
        discountService.create(new CreateDiscountRequest("EXPIRED1", false, DiscountType.FIXED_AMOUNT,
            new BigDecimal("5"), null, null,
            Instant.now().minus(2, ChronoUnit.DAYS),
            Instant.now().minus(1, ChronoUnit.DAYS), null));

        DiscountValidationResponse r = discountService.validate("EXPIRED1", new BigDecimal("100"));
        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("expired");
    }

    @Test
    void validate_unknownCode_returnsInvalid() {
        DiscountValidationResponse r = discountService.validate("NOPE", new BigDecimal("100"));
        assertThat(r.valid()).isFalse();
    }

    @Test
    void validate_companyScopedDiscount_wrongCompany_returnsInvalid() {
        UUID companyId = savedCompanyId();
        discountService.create(new CreateDiscountRequest("CORP10", false, DiscountType.FIXED_AMOUNT,
            new BigDecimal("10"), null, null, null, null, companyId));

        DiscountValidationResponse r = discountService.validate("CORP10", new BigDecimal("100"),
            savedCompanyId());
        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("not available");
    }

    @Test
    void validate_companyScopedDiscount_correctCompany_returnsValid() {
        UUID companyId = savedCompanyId();
        discountService.create(new CreateDiscountRequest("CORP20", false, DiscountType.FIXED_AMOUNT,
            new BigDecimal("20"), null, null, null, null, companyId));

        DiscountValidationResponse r = discountService.validate("CORP20", new BigDecimal("100"),
            companyId);
        assertThat(r.valid()).isTrue();
        assertThat(r.discountedAmount()).isEqualByComparingTo("20");
    }

    @Test
    void validate_companyScopedDiscount_nullCaller_returnsInvalid() {
        UUID companyId = savedCompanyId();
        discountService.create(new CreateDiscountRequest("CORP30", false, DiscountType.FIXED_AMOUNT,
            new BigDecimal("5"), null, null, null, null, companyId));

        DiscountValidationResponse r = discountService.validate("CORP30", new BigDecimal("100"), null);
        assertThat(r.valid()).isFalse();
    }

    // ---- redeem ----

    @Test
    void redeem_incrementsUsedCount() {
        discountService.create(new CreateDiscountRequest("REDEEM1", false, DiscountType.FIXED_AMOUNT,
            new BigDecimal("5"), null, null, null, null, null));

        discountService.redeem("REDEEM1", new BigDecimal("100"));

        var d = discountService.validate("REDEEM1", new BigDecimal("100"));
        assertThat(d.valid()).isTrue();
    }

    @Test
    void redeem_exhaustedMaxUses_throwsValidation() {
        discountService.create(new CreateDiscountRequest("ONCE", false, DiscountType.FIXED_AMOUNT,
            new BigDecimal("5"), null, 1, null, null, null));

        discountService.redeem("ONCE", new BigDecimal("100"));

        assertThatThrownBy(() -> discountService.redeem("ONCE", new BigDecimal("100")))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode").isEqualTo("DISCOUNT_INELIGIBLE");
    }

    @Test
    void redeem_fixedAmountCappedAtOrderTotal() {
        discountService.create(new CreateDiscountRequest("BIG50", false, DiscountType.FIXED_AMOUNT,
            new BigDecimal("50"), null, null, null, null, null));

        var app = discountService.redeem("BIG50", new BigDecimal("20"));
        assertThat(app.discountedAmount()).isEqualByComparingTo("20");
    }
}
