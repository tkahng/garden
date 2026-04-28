package io.k2dv.garden.shipping.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.shipping.dto.CreateShippingRateRequest;
import io.k2dv.garden.shipping.dto.CreateShippingZoneRequest;
import io.k2dv.garden.shipping.dto.ShippingRateResponse;
import io.k2dv.garden.shipping.dto.ShippingZoneResponse;
import io.k2dv.garden.shipping.dto.UpdateShippingRateRequest;
import io.k2dv.garden.shipping.dto.UpdateShippingZoneRequest;
import io.k2dv.garden.shipping.service.ShippingService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin: Shipping", description = "Shipping zones and rates management")
@RestController
@RequestMapping("/api/v1/admin/shipping")
@RequiredArgsConstructor
public class AdminShippingController {

    private final ShippingService shippingService;

    // ---- Zones ----

    @GetMapping("/zones")
    @HasPermission("shipping:read")
    public ApiResponse<PagedResult<ShippingZoneResponse>> listZones(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.of(shippingService.listZones(PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/zones/{id}")
    @HasPermission("shipping:read")
    public ApiResponse<ShippingZoneResponse> getZone(@PathVariable UUID id) {
        return ApiResponse.of(shippingService.getZone(id));
    }

    @PostMapping("/zones")
    @HasPermission("shipping:write")
    public ResponseEntity<ApiResponse<ShippingZoneResponse>> createZone(
            @Valid @RequestBody CreateShippingZoneRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(shippingService.createZone(req)));
    }

    @PutMapping("/zones/{id}")
    @HasPermission("shipping:write")
    public ApiResponse<ShippingZoneResponse> updateZone(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShippingZoneRequest req) {
        return ApiResponse.of(shippingService.updateZone(id, req));
    }

    @DeleteMapping("/zones/{id}")
    @HasPermission("shipping:delete")
    public ResponseEntity<Void> deleteZone(@PathVariable UUID id) {
        shippingService.deleteZone(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Rates ----

    @GetMapping("/zones/{zoneId}/rates")
    @HasPermission("shipping:read")
    public ApiResponse<List<ShippingRateResponse>> listRates(@PathVariable UUID zoneId) {
        return ApiResponse.of(shippingService.listRates(zoneId));
    }

    @GetMapping("/zones/{zoneId}/rates/{rateId}")
    @HasPermission("shipping:read")
    public ApiResponse<ShippingRateResponse> getRate(
            @PathVariable UUID zoneId,
            @PathVariable UUID rateId) {
        return ApiResponse.of(shippingService.getRate(zoneId, rateId));
    }

    @PostMapping("/zones/{zoneId}/rates")
    @HasPermission("shipping:write")
    public ResponseEntity<ApiResponse<ShippingRateResponse>> createRate(
            @PathVariable UUID zoneId,
            @Valid @RequestBody CreateShippingRateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(shippingService.createRate(zoneId, req)));
    }

    @PutMapping("/zones/{zoneId}/rates/{rateId}")
    @HasPermission("shipping:write")
    public ApiResponse<ShippingRateResponse> updateRate(
            @PathVariable UUID zoneId,
            @PathVariable UUID rateId,
            @Valid @RequestBody UpdateShippingRateRequest req) {
        return ApiResponse.of(shippingService.updateRate(zoneId, rateId, req));
    }

    @DeleteMapping("/zones/{zoneId}/rates/{rateId}")
    @HasPermission("shipping:delete")
    public ResponseEntity<Void> deleteRate(
            @PathVariable UUID zoneId,
            @PathVariable UUID rateId) {
        shippingService.deleteRate(zoneId, rateId);
        return ResponseEntity.noContent().build();
    }
}
