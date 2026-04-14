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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShippingService {

    private final ShippingZoneRepository zoneRepo;
    private final ShippingRateRepository rateRepo;

    // ---- Zones ----

    @Transactional(readOnly = true)
    public PagedResult<ShippingZoneResponse> listZones(Pageable pageable) {
        return PagedResult.of(zoneRepo.findAll(pageable), ShippingZoneResponse::from);
    }

    @Transactional(readOnly = true)
    public ShippingZoneResponse getZone(UUID id) {
        return ShippingZoneResponse.from(findZoneOrThrow(id));
    }

    @Transactional
    public ShippingZoneResponse createZone(CreateShippingZoneRequest req) {
        ShippingZone z = new ShippingZone();
        z.setName(req.name());
        z.setDescription(req.description());
        z.setCountryCodes(req.countryCodes());
        z.setProvinces(req.provinces());
        return ShippingZoneResponse.from(zoneRepo.save(z));
    }

    @Transactional
    public ShippingZoneResponse updateZone(UUID id, UpdateShippingZoneRequest req) {
        ShippingZone z = findZoneOrThrow(id);
        if (req.name() != null) z.setName(req.name());
        if (req.description() != null) z.setDescription(req.description());
        if (req.countryCodes() != null) z.setCountryCodes(req.countryCodes());
        if (req.provinces() != null) z.setProvinces(req.provinces());
        if (req.isActive() != null) z.setActive(req.isActive());
        return ShippingZoneResponse.from(zoneRepo.save(z));
    }

    @Transactional
    public void deleteZone(UUID id) {
        ShippingZone z = findZoneOrThrow(id);
        rateRepo.deleteByZoneId(z.getId());
        zoneRepo.delete(z);
    }

    // ---- Rates ----

    @Transactional(readOnly = true)
    public List<ShippingRateResponse> listRates(UUID zoneId) {
        findZoneOrThrow(zoneId);
        return rateRepo.findByZoneId(zoneId).stream()
            .map(ShippingRateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ShippingRateResponse getRate(UUID zoneId, UUID rateId) {
        return ShippingRateResponse.from(findRateOrThrow(zoneId, rateId));
    }

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

    @Transactional
    public void deleteRate(UUID zoneId, UUID rateId) {
        ShippingRate r = findRateOrThrow(zoneId, rateId);
        rateRepo.delete(r);
    }

    // ---- Storefront ----

    @Transactional(readOnly = true)
    public List<ShippingRateResponse> findRatesForAddress(String country, String province,
                                                           BigDecimal orderAmount) {
        List<ShippingZone> zones = zoneRepo.findMatchingZones(country, province);
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
