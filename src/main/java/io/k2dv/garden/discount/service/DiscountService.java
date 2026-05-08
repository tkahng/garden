package io.k2dv.garden.discount.service;

import io.k2dv.garden.discount.dto.CreateDiscountRequest;
import io.k2dv.garden.discount.dto.DiscountApplication;
import io.k2dv.garden.discount.dto.DiscountFilter;
import io.k2dv.garden.discount.dto.DiscountResponse;
import io.k2dv.garden.discount.dto.DiscountValidationResponse;
import io.k2dv.garden.discount.dto.UpdateDiscountRequest;
import io.k2dv.garden.discount.model.Discount;
import io.k2dv.garden.discount.model.DiscountType;
import io.k2dv.garden.discount.repository.DiscountRepository;
import io.k2dv.garden.discount.specification.DiscountSpecification;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountRepository discountRepo;

    @Transactional(readOnly = true)
    public PagedResult<DiscountResponse> list(DiscountFilter filter, Pageable pageable) {
        return PagedResult.of(discountRepo.findAll(DiscountSpecification.toSpec(filter), pageable),
            DiscountResponse::from);
    }

    @Transactional(readOnly = true)
    public DiscountResponse getById(UUID id) {
        return DiscountResponse.from(findOrThrow(id));
    }

    @Transactional
    public DiscountResponse create(CreateDiscountRequest req) {
        if (req.startsAt() != null && req.endsAt() != null && !req.startsAt().isBefore(req.endsAt())) {
            throw new ValidationException("INVALID_DATE_RANGE", "startsAt must be before endsAt");
        }
        if (!req.automatic() && (req.code() == null || req.code().isBlank())) {
            throw new ValidationException("CODE_REQUIRED", "A discount code is required for non-automatic promotions");
        }
        if (req.code() != null && !req.code().isBlank()
                && discountRepo.findByCodeIgnoreCase(req.code()).isPresent()) {
            throw new ConflictException("DISCOUNT_CODE_EXISTS", "Discount code already exists: " + req.code());
        }
        Discount d = new Discount();
        d.setCode(req.code() != null && !req.code().isBlank() ? req.code().toUpperCase() : null);
        d.setAutomatic(req.automatic());
        d.setType(req.type());
        d.setValue(req.value());
        d.setMinOrderAmount(req.minOrderAmount());
        d.setMaxUses(req.maxUses());
        d.setStartsAt(req.startsAt());
        d.setEndsAt(req.endsAt());
        d.setCompanyId(req.companyId());
        return DiscountResponse.from(discountRepo.save(d));
    }

    @Transactional
    public DiscountResponse update(UUID id, UpdateDiscountRequest req) {
        Discount d = findOrThrow(id);
        Instant effectiveStart = req.startsAt() != null ? req.startsAt() : d.getStartsAt();
        Instant effectiveEnd = req.endsAt() != null ? req.endsAt() : d.getEndsAt();
        if (effectiveStart != null && effectiveEnd != null && !effectiveStart.isBefore(effectiveEnd)) {
            throw new ValidationException("INVALID_DATE_RANGE", "startsAt must be before endsAt");
        }
        if (req.code() != null) {
            String upper = req.code().toUpperCase();
            discountRepo.findByCodeIgnoreCase(req.code()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new ConflictException("DISCOUNT_CODE_EXISTS", "Discount code already exists: " + req.code());
                }
            });
            d.setCode(upper);
        }
        if (req.type() != null) d.setType(req.type());
        if (req.value() != null) d.setValue(req.value());
        if (req.minOrderAmount() != null) d.setMinOrderAmount(req.minOrderAmount());
        if (req.maxUses() != null) d.setMaxUses(req.maxUses());
        if (req.startsAt() != null) d.setStartsAt(req.startsAt());
        if (req.endsAt() != null) d.setEndsAt(req.endsAt());
        if (req.isActive() != null) d.setActive(req.isActive());
        if (req.companyId() != null) d.setCompanyId(req.companyId());
        return DiscountResponse.from(discountRepo.save(d));
    }

    @Transactional
    public void delete(UUID id) {
        Discount d = findOrThrow(id);
        discountRepo.delete(d);
    }

    @Transactional(readOnly = true)
    public DiscountValidationResponse validate(String code, BigDecimal orderAmount) {
        return validate(code, orderAmount, null);
    }

    @Transactional(readOnly = true)
    public DiscountValidationResponse validate(String code, BigDecimal orderAmount, UUID companyId) {
        Discount d = discountRepo.findByCodeIgnoreCase(code).orElse(null);
        if (d == null) {
            return new DiscountValidationResponse(false, code, null, null, null, "Discount code not found");
        }
        String reason = checkEligibility(d, orderAmount, companyId);
        if (reason != null) {
            return new DiscountValidationResponse(false, d.getCode(), d.getType(), d.getValue(), null, reason);
        }
        BigDecimal discountedAmount = calculateDiscount(d, orderAmount);
        return new DiscountValidationResponse(true, d.getCode(), d.getType(), d.getValue(), discountedAmount, null);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<DiscountApplication> findBestAutomatic(BigDecimal orderAmount, UUID companyId) {
        List<Discount> candidates = discountRepo.findActiveAutomatic(Instant.now(), companyId);
        return candidates.stream()
            .filter(d -> d.getMinOrderAmount() == null || orderAmount.compareTo(d.getMinOrderAmount()) >= 0)
            .map(d -> new DiscountApplication(d.getId(), d.getCode(), d.getType(), d.getValue(),
                calculateDiscount(d, orderAmount)))
            .max(java.util.Comparator.comparing(DiscountApplication::discountedAmount));
    }

    @Transactional
    public DiscountApplication applyAutomatic(UUID discountId, BigDecimal orderAmount) {
        Discount d = discountRepo.findById(discountId)
            .orElseThrow(() -> new NotFoundException("DISCOUNT_NOT_FOUND", "Promotion not found"));
        discountRepo.incrementUsedCount(d.getId());
        return new DiscountApplication(d.getId(), d.getCode(), d.getType(), d.getValue(),
            calculateDiscount(d, orderAmount));
    }

    @Transactional
    public DiscountApplication redeem(String code, BigDecimal orderAmount) {
        return redeem(code, orderAmount, null);
    }

    @Transactional
    public DiscountApplication redeem(String code, BigDecimal orderAmount, UUID companyId) {
        Discount d = discountRepo.findByCodeIgnoreCaseForUpdate(code)
            .orElseThrow(() -> new ValidationException("DISCOUNT_NOT_FOUND", "Discount code not found: " + code));

        String reason = checkEligibility(d, orderAmount, companyId);
        if (reason != null) {
            throw new ValidationException("DISCOUNT_INELIGIBLE", reason);
        }

        int updated = discountRepo.incrementUsedCount(d.getId());
        if (updated == 0) {
            throw new ConflictException("DISCOUNT_EXHAUSTED", "Discount code has reached its usage limit");
        }

        BigDecimal discountedAmount = calculateDiscount(d, orderAmount);
        return new DiscountApplication(d.getId(), d.getCode(), d.getType(), d.getValue(), discountedAmount);
    }

    private String checkEligibility(Discount d, BigDecimal orderAmount, UUID callerCompanyId) {
        if (!d.isActive()) return "Discount is not active";
        Instant now = Instant.now();
        if (d.getStartsAt() != null && now.isBefore(d.getStartsAt())) return "Discount is not yet valid";
        if (d.getEndsAt() != null && now.isAfter(d.getEndsAt())) return "Discount has expired";
        if (d.getMaxUses() != null && d.getUsedCount() >= d.getMaxUses()) return "Discount has reached its usage limit";
        if (d.getMinOrderAmount() != null && orderAmount.compareTo(d.getMinOrderAmount()) < 0) {
            return "Order amount does not meet the minimum required for this discount";
        }
        if (d.getCompanyId() != null && !d.getCompanyId().equals(callerCompanyId)) {
            return "Discount code is not available for your account";
        }
        return null;
    }

    private BigDecimal calculateDiscount(Discount d, BigDecimal orderAmount) {
        return switch (d.getType()) {
            case PERCENTAGE -> orderAmount
                .multiply(d.getValue())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> d.getValue().min(orderAmount);
            case FREE_SHIPPING -> BigDecimal.ZERO;
        };
    }

    private Discount findOrThrow(UUID id) {
        return discountRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("DISCOUNT_NOT_FOUND", "Discount not found"));
    }
}
