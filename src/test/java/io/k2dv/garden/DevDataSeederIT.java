package io.k2dv.garden;

import io.k2dv.garden.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"test", "local"})
class DevDataSeederIT extends AbstractIntegrationTest {

    @SuppressWarnings("deprecation")
    static final MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2025-04-22T22-12-26Z");

    static {
        minio.start();
        // Buckets must exist before the context starts — DevDataSeeder runs as an
        // ApplicationRunner at startup and immediately uploads images to MinIO.
        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create(minio.getS3URL()))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            client.createBucket(b -> b.bucket("test"));
            client.createBucket(b -> b.bucket("test-private"));
        }
    }

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.endpoint", minio::getS3URL);
        registry.add("storage.access-key", minio::getUserName);
        registry.add("storage.secret-key", minio::getPassword);
        registry.add("storage.bucket", () -> "test");
        registry.add("storage.private-bucket", () -> "test-private");
        registry.add("storage.base-url", () -> minio.getS3URL() + "/test");
    }

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
        assertThat(blobCount).isEqualTo(35L); // 31 product images + 4 collection images

        Long imageCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM catalog.product_images", Long.class);
        assertThat(imageCount).isEqualTo(31L);
    }

    @Test
    void seeder_allCollectionsHaveFeaturedImage() {
        Long withoutImage = jdbc.queryForObject(
            "SELECT COUNT(*) FROM catalog.collections WHERE featured_image_id IS NULL", Long.class);
        assertThat(withoutImage).isEqualTo(0L);
    }
}
