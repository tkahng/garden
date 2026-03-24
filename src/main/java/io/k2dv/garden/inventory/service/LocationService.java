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

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepo;

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

    @Transactional
    public LocationResponse update(UUID id, UpdateLocationRequest req) {
        Location loc = findOrThrow(id);
        if (req.name() != null) loc.setName(req.name());
        if (req.address() != null) loc.setAddress(req.address());
        return toResponse(locationRepo.save(loc));
    }

    @Transactional
    public void deactivate(UUID id) {
        Location loc = findOrThrow(id);
        loc.setActive(false);
        locationRepo.save(loc);
    }

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

    LocationResponse toResponse(Location loc) {
        return new LocationResponse(loc.getId(), loc.getName(), loc.getAddress(),
            loc.isActive(), loc.getCreatedAt(), loc.getUpdatedAt());
    }
}
