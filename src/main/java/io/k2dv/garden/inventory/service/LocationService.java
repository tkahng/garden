package io.k2dv.garden.inventory.service;

import io.k2dv.garden.inventory.dto.CreateLocationRequest;
import io.k2dv.garden.inventory.dto.LocationResponse;
import io.k2dv.garden.inventory.dto.UpdateLocationRequest;
import io.k2dv.garden.inventory.model.Location;
import io.k2dv.garden.inventory.repository.LocationRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Manages physical or logical stock-holding locations (e.g., warehouses, retail stores)
 * used by the inventory system to track per-location stock levels. Locations can be
 * deactivated without deletion to preserve historical transaction data.
 */
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepo;

    /**
     * Registers a new stock location that can subsequently receive inventory and be referenced
     * in fulfillment workflows.
     */
    @Transactional
    public LocationResponse create(CreateLocationRequest req) {
        Location loc = new Location();
        loc.setName(req.name());
        loc.setAddress(req.address());
        return toResponse(locationRepo.save(loc));
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> list() {
        return locationRepo.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LocationResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    /**
     * Partially updates a location's name or address without affecting its active status or
     * linked inventory levels.
     */
    @Transactional
    public LocationResponse update(UUID id, UpdateLocationRequest req) {
        Location loc = findOrThrow(id);
        if (req.name() != null) loc.setName(req.name());
        if (req.address() != null) loc.setAddress(req.address());
        return toResponse(locationRepo.save(loc));
    }

    /**
     * Marks a location as inactive so it no longer appears in receiving or fulfillment
     * workflows, while preserving its inventory history.
     */
    @Transactional
    public void deactivate(UUID id) {
        Location loc = findOrThrow(id);
        loc.setActive(false);
    }

    /**
     * Re-enables a previously deactivated location so it can once again be used for receiving
     * stock and fulfilling orders.
     */
    @Transactional
    public LocationResponse reactivate(UUID id) {
        Location loc = findOrThrow(id);
        loc.setActive(true);
        return toResponse(locationRepo.save(loc));
    }

    private Location findOrThrow(UUID id) {
        return locationRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("LOCATION_NOT_FOUND", "Location not found"));
    }

    private LocationResponse toResponse(Location loc) {
        return new LocationResponse(loc.getId(), loc.getName(), loc.getAddress(),
            loc.isActive(), loc.getCreatedAt(), loc.getUpdatedAt());
    }
}
