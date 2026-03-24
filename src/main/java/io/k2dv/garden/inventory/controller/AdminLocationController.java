package io.k2dv.garden.inventory.controller;

import io.k2dv.garden.inventory.dto.CreateLocationRequest;
import io.k2dv.garden.inventory.dto.LocationResponse;
import io.k2dv.garden.inventory.dto.UpdateLocationRequest;
import io.k2dv.garden.inventory.service.LocationService;
import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/locations")
@RequiredArgsConstructor
public class AdminLocationController {

    private final LocationService locationService;

    @PostMapping
    @HasPermission("location:write")
    public ResponseEntity<ApiResponse<LocationResponse>> create(
            @RequestBody @Valid CreateLocationRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.of(locationService.create(req)));
    }

    @GetMapping
    @HasPermission("location:read")
    public ResponseEntity<ApiResponse<List<LocationResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.of(locationService.list()));
    }

    @GetMapping("/{id}")
    @HasPermission("location:read")
    public ResponseEntity<ApiResponse<LocationResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(locationService.get(id)));
    }

    @PatchMapping("/{id}")
    @HasPermission("location:write")
    public ResponseEntity<ApiResponse<LocationResponse>> update(
            @PathVariable UUID id, @RequestBody @Valid UpdateLocationRequest req) {
        return ResponseEntity.ok(ApiResponse.of(locationService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    @HasPermission("location:write")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        locationService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    @HasPermission("location:write")
    public ResponseEntity<ApiResponse<LocationResponse>> reactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(locationService.reactivate(id)));
    }
}
