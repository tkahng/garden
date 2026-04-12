package io.k2dv.garden;

import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"test", "local"})
class DevDataSeederIT extends AbstractIntegrationTest {

    @MockitoBean StorageService storageService;

    @Autowired JdbcTemplate jdbc;
    @Autowired DevDataSeeder seeder;

    @Test
    void seeder_populatesExpectedRowCounts() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog.products", Long.class)).isEqualTo(14L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog.collections", Long.class)).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog.collection_products", Long.class)).isEqualTo(14L);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM content.pages WHERE handle = 'home'", Long.class)).isEqualTo(1L);
    }

    @Test
    void seeder_isIdempotent() throws Exception {
        seeder.run(null); // second run — should not insert duplicates
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog.products", Long.class)).isEqualTo(14L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog.collections", Long.class)).isEqualTo(4L);
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

    @Test
    void seeder_quoteOnlyVariantsHaveNullPrice() {
        Long nullPriceCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM catalog.product_variants WHERE price IS NULL", Long.class);
        assertThat(nullPriceCount).isEqualTo(4L); // GFRC Planter, Bluestone Pavers, Cedar Raised Bed, Cast Stone Fountain
    }

    @Test
    void seeder_allProductsHaveFeaturedImage() {
        Long withoutImage = jdbc.queryForObject(
            "SELECT COUNT(*) FROM catalog.products WHERE featured_image_id IS NULL", Long.class);
        assertThat(withoutImage).isEqualTo(0L);
    }

    @Test
    void seeder_imageCountsAreCorrect() {
        Long blobCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM storage.blob_objects", Long.class);
        assertThat(blobCount).isEqualTo(31L);

        Long imageCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM catalog.product_images", Long.class);
        assertThat(imageCount).isEqualTo(31L);
    }
}
