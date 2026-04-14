package io.k2dv.garden.giftcard.service;

import io.k2dv.garden.giftcard.dto.CreateGiftCardRequest;
import io.k2dv.garden.giftcard.dto.GiftCardApplication;
import io.k2dv.garden.giftcard.dto.GiftCardFilter;
import io.k2dv.garden.giftcard.dto.GiftCardResponse;
import io.k2dv.garden.giftcard.dto.GiftCardTransactionRequest;
import io.k2dv.garden.giftcard.dto.GiftCardTransactionResponse;
import io.k2dv.garden.giftcard.dto.GiftCardValidationResponse;
import io.k2dv.garden.giftcard.dto.UpdateGiftCardRequest;
import io.k2dv.garden.giftcard.model.GiftCard;
import io.k2dv.garden.giftcard.model.GiftCardTransaction;
import io.k2dv.garden.giftcard.repository.GiftCardRepository;
import io.k2dv.garden.giftcard.repository.GiftCardTransactionRepository;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GiftCardService {

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GiftCardRepository giftCardRepo;
    private final GiftCardTransactionRepository txRepo;

    @Transactional(readOnly = true)
    public PagedResult<GiftCardResponse> list(GiftCardFilter filter, Pageable pageable) {
        Specification<GiftCard> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter != null) {
                if (filter.isActive() != null) predicates.add(cb.equal(root.get("isActive"), filter.isActive()));
                if (filter.codeContains() != null && !filter.codeContains().isBlank()) {
                    predicates.add(cb.like(cb.upper(root.get("code")),
                        "%" + filter.codeContains().toUpperCase() + "%"));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PagedResult.of(giftCardRepo.findAll(spec, pageable), GiftCardResponse::from);
    }

    @Transactional(readOnly = true)
    public GiftCardResponse getById(UUID id) {
        return GiftCardResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<GiftCardTransactionResponse> listTransactions(UUID id) {
        findOrThrow(id);
        return txRepo.findByGiftCardIdOrderByCreatedAtAsc(id)
            .stream().map(GiftCardTransactionResponse::from).toList();
    }

    @Transactional
    public GiftCardResponse create(CreateGiftCardRequest req) {
        String code = req.code() != null && !req.code().isBlank()
            ? req.code().toUpperCase()
            : generateCode();

        if (giftCardRepo.findByCodeIgnoreCase(code).isPresent()) {
            throw new ConflictException("GIFT_CARD_CODE_EXISTS", "Gift card code already exists: " + code);
        }

        GiftCard g = new GiftCard();
        g.setCode(code);
        g.setInitialBalance(req.initialBalance());
        g.setCurrentBalance(req.initialBalance());
        if (req.currency() != null) g.setCurrency(req.currency());
        g.setExpiresAt(req.expiresAt());
        g.setNote(req.note());
        g.setPurchaserUserId(req.purchaserUserId());
        g.setRecipientEmail(req.recipientEmail());
        return GiftCardResponse.from(giftCardRepo.save(g));
    }

    @Transactional
    public GiftCardResponse update(UUID id, UpdateGiftCardRequest req) {
        GiftCard g = findOrThrow(id);
        if (req.expiresAt() != null) g.setExpiresAt(req.expiresAt());
        if (req.note() != null) g.setNote(req.note());
        if (req.recipientEmail() != null) g.setRecipientEmail(req.recipientEmail());
        return GiftCardResponse.from(giftCardRepo.save(g));
    }

    @Transactional
    public GiftCardResponse deactivate(UUID id) {
        GiftCard g = findOrThrow(id);
        if (!g.isActive()) return GiftCardResponse.from(g);

        if (g.getCurrentBalance().compareTo(BigDecimal.ZERO) > 0) {
            recordTransaction(g.getId(), g.getCurrentBalance().negate(), null,
                "Deactivated by admin");
        }
        g.setActive(false);
        g.setCurrentBalance(BigDecimal.ZERO);
        return GiftCardResponse.from(giftCardRepo.save(g));
    }

    @Transactional
    public GiftCardTransactionResponse addTransaction(UUID id, GiftCardTransactionRequest req) {
        GiftCard g = findOrThrow(id);
        BigDecimal newBalance = g.getCurrentBalance().add(req.delta());
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("INSUFFICIENT_BALANCE",
                "Transaction would bring balance below zero");
        }
        g.setCurrentBalance(newBalance);
        giftCardRepo.save(g);
        return GiftCardTransactionResponse.from(recordTransaction(g.getId(), req.delta(), null, req.note()));
    }

    @Transactional(readOnly = true)
    public GiftCardValidationResponse validate(String code) {
        GiftCard g = giftCardRepo.findByCodeIgnoreCase(code).orElse(null);
        if (g == null) return new GiftCardValidationResponse(false, code, null, null, "Gift card not found");
        String reason = checkEligibility(g);
        if (reason != null) return new GiftCardValidationResponse(false, g.getCode(), g.getCurrentBalance(), g.getCurrency(), reason);
        return new GiftCardValidationResponse(true, g.getCode(), g.getCurrentBalance(), g.getCurrency(), null);
    }

    @Transactional
    public GiftCardApplication redeem(String code, BigDecimal orderAmount, UUID orderId, String orderCurrency) {
        GiftCard g = giftCardRepo.findByCodeIgnoreCase(code)
            .orElseThrow(() -> new ValidationException("GIFT_CARD_NOT_FOUND", "Gift card not found: " + code));

        if (!g.getCurrency().equalsIgnoreCase(orderCurrency)) {
            throw new ValidationException("CURRENCY_MISMATCH",
                "Gift card currency '" + g.getCurrency() + "' does not match order currency '" + orderCurrency + "'");
        }

        String reason = checkEligibility(g);
        if (reason != null) throw new ValidationException("GIFT_CARD_INELIGIBLE", reason);

        BigDecimal applied = g.getCurrentBalance().min(orderAmount);

        int updated = giftCardRepo.atomicDebit(g.getId(), applied);
        if (updated == 0) {
            throw new ConflictException("GIFT_CARD_INSUFFICIENT_BALANCE",
                "Gift card balance insufficient for the requested amount");
        }

        recordTransaction(g.getId(), applied.negate(), orderId, "Redeemed at checkout");

        return new GiftCardApplication(g.getId(), g.getCode(), applied);
    }

    private String checkEligibility(GiftCard g) {
        if (!g.isActive()) return "Gift card is not active";
        if (g.getExpiresAt() != null && Instant.now().isAfter(g.getExpiresAt())) return "Gift card has expired";
        if (g.getCurrentBalance().compareTo(BigDecimal.ZERO) <= 0) return "Gift card has no remaining balance";
        return null;
    }

    private GiftCardTransaction recordTransaction(UUID giftCardId, BigDecimal delta,
                                                   UUID orderId, String note) {
        GiftCardTransaction tx = new GiftCardTransaction();
        tx.setGiftCardId(giftCardId);
        tx.setDelta(delta);
        tx.setOrderId(orderId);
        tx.setNote(note);
        return txRepo.save(tx);
    }

    private GiftCard findOrThrow(UUID id) {
        return giftCardRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("GIFT_CARD_NOT_FOUND", "Gift card not found"));
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder("GIFT-");
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            if (i < 3) sb.append('-');
        }
        return sb.toString();
    }
}
