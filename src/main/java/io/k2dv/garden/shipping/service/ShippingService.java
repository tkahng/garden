package io.k2dv.garden.shipping.service;

import io.k2dv.garden.shipping.dto.CreateShippingRateRequest;
import io.k2dv.garden.shipping.dto.CreateShippingZoneRequest;
import io.k2dv.garden.shipping.dto.ShippingRateResponse;
import io.k2dv.garden.shipping.dto.ShippingZoneResponse;
import io.k2dv.garden.shipping.dto.UpdateShippingRateRequest;
import io.k2dv.garden.shipping.dto.UpdateShippingZoneRequest;
import io.k2dv.garden.shipping.model.ShippingRate;
import io.k2dv.garden.shipping.model.ShippingZone;
import io.k2dv.garden.shipping.repository.ShippingRateRepository;
import io.k2dv.garden.shipping.repository.ShippingZoneRepository;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.shared.validation.CountryCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Manages shipping zones and their associated rates for the storefront checkout flow. Admins
 * define zones by country/province and attach named rates (flat, weight-banded, or
 * minimum-order-gated) to each zone; at checkout, {@link #findRatesForAddress} resolves the
 * eligible rates for a buyer's delivery address and order value.
 */
@Service
@RequiredArgsConstructor
public class ShippingService {

    private final ShippingZoneRepository zoneRepo;
    private final ShippingRateRepository rateRepo;

    // ---- Zones ----

    /** Returns a paginated list of all shipping zones. */
    @Transactional(readOnly = true)
    public PagedResult<ShippingZoneResponse> listZones(Pageable pageable) {
        return PagedResult.of(zoneRepo.findAll(pageable), ShippingZoneResponse::from);
    }

    /** Retrieves a single shipping zone by ID, throwing {@code NotFoundException} if absent. */
    @Transactional(readOnly = true)
    public ShippingZoneResponse getZone(UUID id) {
        return ShippingZoneResponse.from(findZoneOrThrow(id));
    }

    /**
     * Creates a new shipping zone covering the specified countries and optional provinces.
     * Country codes are normalised to uppercase ISO-3166-1 alpha-2 format before persistence.
     */
    @Transactional
    public ShippingZoneResponse createZone(CreateShippingZoneRequest req) {
        ShippingZone z = new ShippingZone();
        z.setName(req.name());
        z.setDescription(req.description());
        z.setCountryCodes(CountryCode.normalizeList(req.countryCodes()));
        z.setProvinces(req.provinces());
        return ShippingZoneResponse.from(zoneRepo.save(z));
    }

    /**
     * Updates mutable fields of an existing shipping zone (name, description, country codes,
     * provinces, active flag). Only non-null request fields are applied.
     */
    @Transactional
    public ShippingZoneResponse updateZone(UUID id, UpdateShippingZoneRequest req) {
        ShippingZone z = findZoneOrThrow(id);
        if (req.name() != null) z.setName(req.name());
        if (req.description() != null) z.setDescription(req.description());
        if (req.countryCodes() != null) z.setCountryCodes(CountryCode.normalizeList(req.countryCodes()));
        if (req.provinces() != null) z.setProvinces(req.provinces());
        if (req.isActive() != null) z.setActive(req.isActive());
        return ShippingZoneResponse.from(zoneRepo.save(z));
    }

    /**
     * Permanently deletes a shipping zone and all of its associated rates. Consider
     * deactivating the zone instead if historical order records reference these rates.
     */
    @Transactional
    public void deleteZone(UUID id) {
        ShippingZone z = findZoneOrThrow(id);
        rateRepo.deleteByZoneId(z.getId());
        zoneRepo.delete(z);
    }

    // ---- Rates ----

    /** Returns all rates defined for the given zone, throwing {@code NotFoundException} if the zone is absent. */
    @Transactional(readOnly = true)
    public List<ShippingRateResponse> listRates(UUID zoneId) {
        findZoneOrThrow(zoneId);
        return rateRepo.findByZoneId(zoneId).stream()
            .map(ShippingRateResponse::from).toList();
    }

    /** Retrieves a single rate, scoped to its parent zone for safety. */
    @Transactional(readOnly = true)
    public ShippingRateResponse getRate(UUID zoneId, UUID rateId) {
        return ShippingRateResponse.from(findRateOrThrow(zoneId, rateId));
    }

    /**
     * Adds a new rate to an existing zone. Rates can encode flat pricing, weight-band limits
     * ({@code minWeightGrams}/{@code maxWeightGrams}), and a minimum order amount threshold.
     */
    @Transactional
    public ShippingRateResponse createRate(UUID zoneId, CreateShippingRateRequest req) {
        findZoneOrThrow(zoneId);
        ShippingRate r = new ShippingRate();
        r.setZoneId(zoneId);
        r.setName(req.name());
        r.setPrice(req.price());
        r.setMinWeightGrams(req.minWeightGrams());
        r.setMaxWeightGrams(req.maxWeightGrams());
        r.setMinOrderAmount(req.minOrderAmount());
        r.setEstimatedDaysMin(req.estimatedDaysMin());
        r.setEstimatedDaysMax(req.estimatedDaysMax());
        r.setCarrier(req.carrier());
        return ShippingRateResponse.from(rateRepo.save(r));
    }

    /**
     * Updates mutable fields of an existing rate within a zone. Only non-null fields in the
     * request are applied; use {@code isActive = false} to hide a rate without deleting it.
     */
    @Transactional
    public ShippingRateResponse updateRate(UUID zoneId, UUID rateId, UpdateShippingRateRequest req) {
        ShippingRate r = findRateOrThrow(zoneId, rateId);
        if (req.name() != null) r.setName(req.name());
        if (req.price() != null) r.setPrice(req.price());
        if (req.minWeightGrams() != null) r.setMinWeightGrams(req.minWeightGrams());
        if (req.maxWeightGrams() != null) r.setMaxWeightGrams(req.maxWeightGrams());
        if (req.minOrderAmount() != null) r.setMinOrderAmount(req.minOrderAmount());
        if (req.estimatedDaysMin() != null) r.setEstimatedDaysMin(req.estimatedDaysMin());
        if (req.estimatedDaysMax() != null) r.setEstimatedDaysMax(req.estimatedDaysMax());
        if (req.carrier() != null) r.setCarrier(req.carrier());
        if (req.isActive() != null) r.setActive(req.isActive());
        return ShippingRateResponse.from(rateRepo.save(r));
    }

    /** Permanently removes a shipping rate from a zone. */
    @Transactional
    public void deleteRate(UUID zoneId, UUID rateId) {
        ShippingRate r = findRateOrThrow(zoneId, rateId);
        rateRepo.delete(r);
    }

    // ---- Storefront ----

    /**
     * Confirms that a previously selected shipping rate is still valid for the given delivery
     * address at order placement time. Throws {@code ValidationException} if the rate's parent
     * zone no longer covers the destination country/province, guarding against stale cart state.
     */
    @Transactional(readOnly = true)
    public void validateRateForAddress(UUID rateId, String country, String province) {
        String normalizedCountry = CountryCode.normalize(country);
        List<UUID> zoneIds = zoneRepo.findMatchingZones(normalizedCountry, province)
            .stream().map(ShippingZone::getId).toList();
        if (zoneIds.isEmpty() || !rateRepo.existsByIdAndZoneIdIn(rateId, zoneIds)) {
            throw new ValidationException("SHIPPING_RATE_NOT_AVAILABLE",
                "Selected shipping rate is not available for address country: " + normalizedCountry);
        }
    }

    /**
     * Resolves all active shipping rates applicable to the given destination and order value,
     * sorted by price ascending so the checkout UI can present options cheapest-first. Weight-
     * based filtering is noted but not yet enforced (cart weight calculation is pending).
     */
    @Transactional(readOnly = true)
    public List<ShippingRateResponse> findRatesForAddress(String country, String province,
                                                           BigDecimal orderAmount) {
        String normalizedCountry = CountryCode.normalize(country);
        List<ShippingZone> zones = zoneRepo.findMatchingZones(normalizedCountry, province);
        // Note: minWeightGrams / maxWeightGrams are stored on ShippingRate but weight-based
        // filtering is not applied here — cart weight calculation is not yet implemented.
        return zones.stream()
            .flatMap(z -> rateRepo.findByZoneId(z.getId()).stream())
            .filter(ShippingRate::isActive)
            .filter(r -> r.getMinOrderAmount() == null
                || orderAmount == null
                || orderAmount.compareTo(r.getMinOrderAmount()) >= 0)
            .sorted(Comparator.comparing(ShippingRate::getPrice))
            .map(ShippingRateResponse::from)
            .toList();
    }

    private ShippingZone findZoneOrThrow(UUID id) {
        return zoneRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("SHIPPING_ZONE_NOT_FOUND", "Shipping zone not found"));
    }

    private ShippingRate findRateOrThrow(UUID zoneId, UUID rateId) {
        return rateRepo.findByIdAndZoneId(rateId, zoneId)
            .orElseThrow(() -> new NotFoundException("SHIPPING_RATE_NOT_FOUND", "Shipping rate not found"));
    }
}
