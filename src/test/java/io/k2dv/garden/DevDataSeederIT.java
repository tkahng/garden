package io.k2dv.garden;

import io.k2dv.garden.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"test", "local"})
class DevDataSeederIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired DevDataSeeder seeder;

    @Test
    void seeder_populatesExpectedRowCounts() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog.products", Long.class)).isEqualTo(8L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog.collections", Long.class)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog.collection_products", Long.class)).isEqualTo(8L);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM content.pages WHERE handle = 'home'", Long.class)).isEqualTo(1L);
    }

    @Test
    void seeder_isIdempotent() throws Exception {
        seeder.run(null); // second run — should not insert duplicates
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog.products", Long.class)).isEqualTo(8L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog.collections", Long.class)).isEqualTo(3L);
    }

    @Test
    void seeder_allProductsAreActive() {
        Long draft = jdbc.queryForObject(
            "SELECT COUNT(*) FROM catalog.products WHERE status != 'ACTIVE'", Long.class);
        assertThat(draft).isEqualTo(0L);
    }

    @Test
    void seeder_allCollectionsAreActive() {
        Long draft = jdbc.queryForObject(
            "SELECT COUNT(*) FROM catalog.collections WHERE status != 'ACTIVE'", Long.class);
        assertThat(draft).isEqualTo(0L);
    }

    @Test
    void seeder_homePageIsPublished() {
        Long published = jdbc.queryForObject(
            "SELECT COUNT(*) FROM content.pages WHERE handle = 'home' AND status = 'PUBLISHED'", Long.class);
        assertThat(published).isEqualTo(1L);
    }
}
