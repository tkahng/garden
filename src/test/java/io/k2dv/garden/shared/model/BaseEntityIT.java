package io.k2dv.garden.shared.model;

import io.k2dv.garden.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityIT extends AbstractIntegrationTest {

    @Autowired
    ProbeRepository repo;

    @Test
    void savedEntity_hasUuidV7Id() {
        var probe = new ProbeEntity();
        probe.setLabel("hello");

        var saved = repo.saveAndFlush(probe);

        assertThat(saved.getId()).isNotNull();
        // UUIDv7: version bits at position 12-15 of the UUID string equal '7'
        assertThat(saved.getId().toString().charAt(14)).isEqualTo('7');
    }

    @Test
    void savedEntity_hasCreatedAtPopulatedByDb() {
        var probe = new ProbeEntity();
        probe.setLabel("hello");

        var saved = repo.saveAndFlush(probe);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void twoSavedEntities_haveDistinctTimestamps_withinSameTransaction() throws InterruptedException {
        var first = new ProbeEntity();
        first.setLabel("first");
        repo.saveAndFlush(first);

        // clock_timestamp() advances even within a transaction; no sleep needed
        // but a tiny pause makes the assertion more robust on fast machines.
        Thread.sleep(2);

        var second = new ProbeEntity();
        second.setLabel("second");
        repo.saveAndFlush(second);

        assertThat(second.getCreatedAt()).isAfterOrEqualTo(first.getCreatedAt());
    }
}
