package io.k2dv.garden.inventory.service;

import io.k2dv.garden.inventory.dto.CreateLocationRequest;
import io.k2dv.garden.inventory.dto.UpdateLocationRequest;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationServiceIT extends AbstractIntegrationTest {

    @Autowired LocationService locationService;

    @Test
    void createLocation_returnsResponse() {
        var req = new CreateLocationRequest("Main Warehouse", "123 Garden St");
        var resp = locationService.create(req);
        assertThat(resp.id()).isNotNull();
        assertThat(resp.name()).isEqualTo("Main Warehouse");
        assertThat(resp.address()).isEqualTo("123 Garden St");
        assertThat(resp.isActive()).isTrue();
    }

    @Test
    void updateLocation_changesFields() {
        var created = locationService.create(new CreateLocationRequest("Old Name", null));
        var updated = locationService.update(created.id(), new UpdateLocationRequest("New Name", "456 Ave"));
        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.address()).isEqualTo("456 Ave");
    }

    @Test
    void deactivateLocation_setsIsActiveFalse() {
        var created = locationService.create(new CreateLocationRequest("Warehouse", null));
        locationService.deactivate(created.id());
        var fetched = locationService.get(created.id());
        assertThat(fetched.isActive()).isFalse();
    }

    @Test
    void getLocation_notFound_throwsNotFoundException() {
        assertThatThrownBy(() -> locationService.get(UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }
}
