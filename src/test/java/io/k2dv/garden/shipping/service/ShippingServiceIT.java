package io.k2dv.garden.shipping.service;

import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shipping.dto.CreateShippingRateRequest;
import io.k2dv.garden.shipping.dto.CreateShippingZoneRequest;
import io.k2dv.garden.shipping.dto.ShippingRateResponse;
import io.k2dv.garden.shipping.dto.ShippingZoneResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShippingServiceIT extends AbstractIntegrationTest {

    @Autowired ShippingService shippingService;

    // ---- zones ----

    @Test
    void createZone_happyPath_storesZone() {
        var req = new CreateShippingZoneRequest("North America", null, List.of("US", "CA"), null);
        ShippingZoneResponse resp = shippingService.createZone(req);
        assertThat(resp.name()).isEqualTo("North America");
        assertThat(resp.isActive()).isTrue();
    }

    @Test
    void deleteZone_cascadesRates() {
        ShippingZoneResponse zone = shippingService.createZone(
            new CreateShippingZoneRequest("EU", null, List.of("DE", "FR"), null));
        shippingService.createRate(zone.id(),
            new CreateShippingRateRequest("Standard", new BigDecimal("5.99"),
                null, null, null, 5, 10, null));

        shippingService.deleteZone(zone.id());

        // Zone is gone
        assertThatThrownBy(() -> shippingService.getZone(zone.id()))
            .isInstanceOf(NotFoundException.class);

        // Rates are gone too — listing should 404 since zone no longer exists
        assertThatThrownBy(() -> shippingService.listRates(zone.id()))
            .isInstanceOf(NotFoundException.class);
    }

    // ---- rates ----

    @Test
    void createRate_happyPath_storesRate() {
        ShippingZoneResponse zone = shippingService.createZone(
            new CreateShippingZoneRequest("Pacific", null, List.of("AU"), null));

        ShippingRateResponse rate = shippingService.createRate(zone.id(),
            new CreateShippingRateRequest("Express", new BigDecimal("12.50"),
                null, null, null, 2, 4, "DHL"));

        assertThat(rate.name()).isEqualTo("Express");
        assertThat(rate.price()).isEqualByComparingTo("12.50");
        assertThat(rate.carrier()).isEqualTo("DHL");
    }

    // ---- findRatesForAddress ----

    @Test
    void findRatesForAddress_matchingCountry_returnsRates() {
        ShippingZoneResponse zone = shippingService.createZone(
            new CreateShippingZoneRequest("US Zone", null, List.of("US"), null));
        shippingService.createRate(zone.id(),
            new CreateShippingRateRequest("Ground", new BigDecimal("6.00"),
                null, null, null, null, null, null));

        List<ShippingRateResponse> rates = shippingService.findRatesForAddress("US", null, null);
        assertThat(rates).anyMatch(r -> r.zoneId().equals(zone.id()) && r.name().equals("Ground"));
    }

    @Test
    void findRatesForAddress_nonMatchingCountry_returnsEmpty() {
        ShippingZoneResponse zone = shippingService.createZone(
            new CreateShippingZoneRequest("UK Only", null, List.of("GB"), null));
        shippingService.createRate(zone.id(),
            new CreateShippingRateRequest("Royal Mail", new BigDecimal("4.00"),
                null, null, null, null, null, null));

        List<ShippingRateResponse> rates = shippingService.findRatesForAddress("DE", null, null);
        assertThat(rates).noneMatch(r -> r.name().equals("Royal Mail"));
    }

    @Test
    void findRatesForAddress_minOrderAmountNotMet_excludesRate() {
        ShippingZoneResponse zone = shippingService.createZone(
            new CreateShippingZoneRequest("Min Order Zone", null, List.of("US"), null));
        shippingService.createRate(zone.id(),
            new CreateShippingRateRequest("Free Shipping", BigDecimal.ZERO,
                null, null, new BigDecimal("100"), null, null, null));

        // Order amount below minimum — check only rates from this zone
        List<ShippingRateResponse> rates = shippingService.findRatesForAddress(
            "US", null, new BigDecimal("50"));
        assertThat(rates).noneMatch(r -> r.zoneId().equals(zone.id()));
    }

    @Test
    void findRatesForAddress_minOrderAmountMet_includesRate() {
        ShippingZoneResponse zone = shippingService.createZone(
            new CreateShippingZoneRequest("Min Order Zone 2", null, List.of("US"), null));
        shippingService.createRate(zone.id(),
            new CreateShippingRateRequest("Free Shipping 2", BigDecimal.ZERO,
                null, null, new BigDecimal("100"), null, null, null));

        List<ShippingRateResponse> rates = shippingService.findRatesForAddress(
            "US", null, new BigDecimal("150"));
        assertThat(rates).anyMatch(r -> r.name().equals("Free Shipping 2"));
    }

    @Test
    void findRatesForAddress_sortedByPriceAscending() {
        ShippingZoneResponse zone = shippingService.createZone(
            new CreateShippingZoneRequest("Sort Zone", null, List.of("US"), null));
        shippingService.createRate(zone.id(),
            new CreateShippingRateRequest("Overnight", new BigDecimal("25.00"),
                null, null, null, null, null, null));
        shippingService.createRate(zone.id(),
            new CreateShippingRateRequest("Standard 2", new BigDecimal("5.00"),
                null, null, null, null, null, null));

        List<ShippingRateResponse> rates = shippingService.findRatesForAddress("US", null, null);
        // Rates from this zone should be sorted cheapest first
        List<BigDecimal> prices = rates.stream()
            .filter(r -> r.name().equals("Overnight") || r.name().equals("Standard 2"))
            .map(ShippingRateResponse::price)
            .toList();
        assertThat(prices).isSortedAccordingTo(BigDecimal::compareTo);
    }

    @Test
    void findRatesForAddress_inactiveZone_excludesRates() {
        ShippingZoneResponse zone = shippingService.createZone(
            new CreateShippingZoneRequest("Inactive Zone", null, List.of("US"), null));
        shippingService.createRate(zone.id(),
            new CreateShippingRateRequest("Hidden Rate", new BigDecimal("10.00"),
                null, null, null, null, null, null));

        // Deactivate the zone
        shippingService.updateZone(zone.id(),
            new io.k2dv.garden.shipping.dto.UpdateShippingZoneRequest(
                null, null, null, null, false));

        List<ShippingRateResponse> rates = shippingService.findRatesForAddress("US", null, null);
        assertThat(rates).noneMatch(r -> r.name().equals("Hidden Rate"));
    }
}
