package io.k2dv.garden.giftcard.service;

import io.k2dv.garden.giftcard.dto.CreateGiftCardRequest;
import io.k2dv.garden.giftcard.dto.GiftCardTransactionRequest;
import io.k2dv.garden.giftcard.dto.GiftCardValidationResponse;
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

class GiftCardServiceIT extends AbstractIntegrationTest {

    @Autowired
    GiftCardService giftCardService;

    // ---- create ----

    @Test
    void create_withExplicitCode_storesUppercasedCode() {
        var req = new CreateGiftCardRequest("gc-hello", new BigDecimal("50"), "usd",
            null, null, null, null);
        var resp = giftCardService.create(req);
        assertThat(resp.code()).isEqualTo("GC-HELLO");
        assertThat(resp.currentBalance()).isEqualByComparingTo("50");
        assertThat(resp.isActive()).isTrue();
    }

    @Test
    void create_withoutCode_autoGeneratesGIFTCode() {
        var req = new CreateGiftCardRequest(null, new BigDecimal("25"), "usd",
            null, null, null, null);
        var resp = giftCardService.create(req);
        assertThat(resp.code()).startsWith("GIFT-");
        // Format: GIFT-XXXX-XXXX-XXXX-XXXX (24 chars)
        assertThat(resp.code()).hasSize(24);
    }

    @Test
    void create_duplicateCode_throwsConflict() {
        giftCardService.create(new CreateGiftCardRequest("DUPGC1", new BigDecimal("10"), "usd",
            null, null, null, null));

        assertThatThrownBy(() -> giftCardService.create(
            new CreateGiftCardRequest("dupgc1", new BigDecimal("10"), "usd",
                null, null, null, null)))
            .isInstanceOf(ConflictException.class)
            .extracting("errorCode").isEqualTo("GIFT_CARD_CODE_EXISTS");
    }

    // ---- validate ----

    @Test
    void validate_activeCard_returnsValid() {
        giftCardService.create(new CreateGiftCardRequest("VALGC1", new BigDecimal("100"), "usd",
            null, null, null, null));

        GiftCardValidationResponse r = giftCardService.validate("VALGC1");
        assertThat(r.valid()).isTrue();
        assertThat(r.currentBalance()).isEqualByComparingTo("100");
    }

    @Test
    void validate_unknownCode_returnsInvalid() {
        GiftCardValidationResponse r = giftCardService.validate("NOTEXISTS");
        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("not found");
    }

    @Test
    void validate_expiredCard_returnsInvalid() {
        giftCardService.create(new CreateGiftCardRequest("EXPGC1", new BigDecimal("50"), "usd",
            Instant.now().minus(1, ChronoUnit.DAYS), null, null, null));

        GiftCardValidationResponse r = giftCardService.validate("EXPGC1");
        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("expired");
    }

    @Test
    void validate_deactivatedCard_returnsInvalid() {
        var card = giftCardService.create(new CreateGiftCardRequest("DEACGC1",
            new BigDecimal("50"), "usd", null, null, null, null));
        giftCardService.deactivate(card.id());

        GiftCardValidationResponse r = giftCardService.validate("DEACGC1");
        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("not active");
    }

    // ---- redeem ----

    @Test
    void redeem_happyPath_debitsBalance() {
        giftCardService.create(new CreateGiftCardRequest("REEM1", new BigDecimal("100"), "usd",
            null, null, null, null));

        var app = giftCardService.redeem("REEM1", new BigDecimal("60"), UUID.randomUUID(), "usd");
        assertThat(app.appliedAmount()).isEqualByComparingTo("60");

        var resp = giftCardService.getById(app.giftCardId());
        assertThat(resp.currentBalance()).isEqualByComparingTo("40");
    }

    @Test
    void redeem_capsAtOrderAmount_whenBalanceExceedsOrder() {
        giftCardService.create(new CreateGiftCardRequest("REEM2", new BigDecimal("200"), "usd",
            null, null, null, null));

        var app = giftCardService.redeem("REEM2", new BigDecimal("75"), UUID.randomUUID(), "usd");
        assertThat(app.appliedAmount()).isEqualByComparingTo("75");

        var resp = giftCardService.getById(app.giftCardId());
        assertThat(resp.currentBalance()).isEqualByComparingTo("125");
    }

    @Test
    void redeem_currencyMismatch_throwsValidation() {
        giftCardService.create(new CreateGiftCardRequest("CURR1", new BigDecimal("50"), "usd",
            null, null, null, null));

        assertThatThrownBy(() ->
            giftCardService.redeem("CURR1", new BigDecimal("50"), UUID.randomUUID(), "eur"))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode").isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void redeem_insufficientBalance_throwsException() {
        giftCardService.create(new CreateGiftCardRequest("LOWBAL1", new BigDecimal("10"), "usd",
            null, null, null, null));
        // Drain the balance first
        giftCardService.redeem("LOWBAL1", new BigDecimal("10"), UUID.randomUUID(), "usd");

        // After draining: eligibility check sees balance = 0 → GIFT_CARD_INELIGIBLE (ValidationException)
        // or if cache was stale → atomicDebit fails → GIFT_CARD_INSUFFICIENT_BALANCE (ConflictException)
        // With clearAutomatically = true on atomicDebit the cache is refreshed,
        // so checkEligibility sees the real balance = 0 and throws ValidationException.
        assertThatThrownBy(() ->
            giftCardService.redeem("LOWBAL1", new BigDecimal("1"), UUID.randomUUID(), "usd"))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode").isEqualTo("GIFT_CARD_INELIGIBLE");
    }

    @Test
    void redeem_inactiveCard_throwsValidation() {
        var card = giftCardService.create(new CreateGiftCardRequest("INACT1",
            new BigDecimal("50"), "usd", null, null, null, null));
        giftCardService.deactivate(card.id());

        assertThatThrownBy(() ->
            giftCardService.redeem("INACT1", new BigDecimal("10"), UUID.randomUUID(), "usd"))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode").isEqualTo("GIFT_CARD_INELIGIBLE");
    }

    // ---- deactivate ----

    @Test
    void deactivate_withBalance_recordsWriteOffTransaction() {
        var card = giftCardService.create(new CreateGiftCardRequest("DEAC2",
            new BigDecimal("80"), "usd", null, null, null, null));
        giftCardService.deactivate(card.id());

        var transactions = giftCardService.listTransactions(card.id());
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).delta()).isEqualByComparingTo("-80");
        assertThat(transactions.get(0).note()).contains("Deactivated");
    }

    @Test
    void deactivate_zeroBalance_doesNotRecordTransaction() {
        var card = giftCardService.create(new CreateGiftCardRequest("DEAC3",
            new BigDecimal("20"), "usd", null, null, null, null));
        giftCardService.redeem("DEAC3", new BigDecimal("20"), UUID.randomUUID(), "usd");
        giftCardService.deactivate(card.id());

        var transactions = giftCardService.listTransactions(card.id());
        // Only the redeem transaction; no write-off since balance was already 0
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).delta()).isNegative();
    }

    @Test
    void deactivate_alreadyInactive_isIdempotent() {
        var card = giftCardService.create(new CreateGiftCardRequest("DEAC4",
            new BigDecimal("50"), "usd", null, null, null, null));
        giftCardService.deactivate(card.id());
        giftCardService.deactivate(card.id()); // second call should not throw
        var resp = giftCardService.getById(card.id());
        assertThat(resp.isActive()).isFalse();
    }

    // ---- addTransaction ----

    @Test
    void addTransaction_credit_increasesBalance() {
        var card = giftCardService.create(new CreateGiftCardRequest("TX1",
            new BigDecimal("50"), "usd", null, null, null, null));

        giftCardService.addTransaction(card.id(),
            new GiftCardTransactionRequest(new BigDecimal("25"), "top-up"));

        var resp = giftCardService.getById(card.id());
        assertThat(resp.currentBalance()).isEqualByComparingTo("75");
    }

    @Test
    void addTransaction_debit_decreasesBalance() {
        var card = giftCardService.create(new CreateGiftCardRequest("TX2",
            new BigDecimal("50"), "usd", null, null, null, null));

        giftCardService.addTransaction(card.id(),
            new GiftCardTransactionRequest(new BigDecimal("-30"), "admin adjustment"));

        var resp = giftCardService.getById(card.id());
        assertThat(resp.currentBalance()).isEqualByComparingTo("20");
    }

    @Test
    void addTransaction_wouldGoBelowZero_throwsValidation() {
        var card = giftCardService.create(new CreateGiftCardRequest("TX3",
            new BigDecimal("10"), "usd", null, null, null, null));

        assertThatThrownBy(() -> giftCardService.addTransaction(card.id(),
            new GiftCardTransactionRequest(new BigDecimal("-50"), "bad debit")))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode").isEqualTo("INSUFFICIENT_BALANCE");
    }
}
