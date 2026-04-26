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

    // ─── B2B ──────────────────────────────────────────────────────────────────

    @Test
    void seeder_b2bCompanyExists() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM b2b.companies WHERE name = 'Green Thumb Nurseries LLC'", Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void seeder_b2bCompanyHasThreeMembers() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.company_memberships m
            JOIN b2b.companies c ON c.id = m.company_id
            WHERE c.name = 'Green Thumb Nurseries LLC'
            """, Long.class);
        assertThat(count).isEqualTo(3L);
    }

    @Test
    void seeder_b2bMembershipRolesAreCorrect() {
        Long ownerCount = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.company_memberships m
            JOIN b2b.companies c ON c.id = m.company_id
            WHERE c.name = 'Green Thumb Nurseries LLC' AND m.role = 'OWNER'
            """, Long.class);
        assertThat(ownerCount).isEqualTo(1L);

        Long managerCount = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.company_memberships m
            JOIN b2b.companies c ON c.id = m.company_id
            WHERE c.name = 'Green Thumb Nurseries LLC' AND m.role = 'MANAGER'
            """, Long.class);
        assertThat(managerCount).isEqualTo(1L);

        Long memberCount = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.company_memberships m
            JOIN b2b.companies c ON c.id = m.company_id
            WHERE c.name = 'Green Thumb Nurseries LLC' AND m.role = 'MEMBER'
            """, Long.class);
        assertThat(memberCount).isEqualTo(1L);
    }

    @Test
    void seeder_b2bMemberHasSpendingLimit() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.company_memberships m
            JOIN b2b.companies c ON c.id = m.company_id
            WHERE c.name = 'Green Thumb Nurseries LLC'
              AND m.role = 'MEMBER' AND m.spending_limit = 2000.00
            """, Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void seeder_b2bCreditAccountExists() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.credit_accounts ca
            JOIN b2b.companies c ON c.id = ca.company_id
            WHERE c.name = 'Green Thumb Nurseries LLC'
              AND ca.credit_limit = 5000.00 AND ca.payment_terms_days = 30
            """, Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void seeder_b2bPriceListHasSixEntries() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.price_list_entries e
            JOIN b2b.price_lists pl ON pl.id = e.price_list_id
            JOIN b2b.companies c ON c.id = pl.company_id
            WHERE c.name = 'Green Thumb Nurseries LLC'
            """, Long.class);
        assertThat(count).isEqualTo(6L);
    }

    @Test
    void seeder_b2bQuoteRequestStatusesPresent() {
        var statuses = jdbc.queryForList("""
            SELECT DISTINCT qr.status FROM quote.quote_requests qr
            JOIN b2b.companies c ON c.id = qr.company_id
            WHERE c.name = 'Green Thumb Nurseries LLC'
            ORDER BY qr.status
            """, String.class);
        assertThat(statuses).containsExactlyInAnyOrder("ACCEPTED", "CANCELLED", "PENDING", "SENT");
    }

    @Test
    void seeder_b2bAcceptedQuoteHasInvoice() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.invoices i
            JOIN b2b.companies c ON c.id = i.company_id
            WHERE c.name = 'Green Thumb Nurseries LLC' AND i.status = 'ISSUED'
            """, Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void seeder_b2bInvoiceLinkedToInvoicedOrder() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.invoices i
            JOIN checkout.orders o ON o.id = i.order_id
            WHERE o.status = 'INVOICED'
            """, Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void seeder_b2bPendingInvitationExists() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.company_invitations ci
            JOIN b2b.companies c ON c.id = ci.company_id
            WHERE c.name = 'Green Thumb Nurseries LLC'
              AND ci.email = 'newbuyer@example.com' AND ci.status = 'PENDING'
            """, Long.class);
        assertThat(count).isEqualTo(1L);
    }

    // ─── Orders ───────────────────────────────────────────────────────────────

    @Test
    void seeder_allOrderStatusesPresent() {
        var statuses = jdbc.queryForList(
            "SELECT DISTINCT status FROM checkout.orders ORDER BY status", String.class);
        assertThat(statuses).contains("PAID", "FULFILLED", "PENDING_PAYMENT", "CANCELLED", "REFUNDED", "INVOICED");
    }

    @Test
    void seeder_fulfilledOrderHasFulfillmentRecord() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM checkout.fulfillments f
            JOIN checkout.orders o ON o.id = f.order_id
            WHERE o.status = 'FULFILLED' AND f.status = 'DELIVERED'
            """, Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void seeder_fulfillmentHasTwoItems() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM checkout.fulfillment_items fi
            JOIN checkout.fulfillments f ON f.id = fi.fulfillment_id
            JOIN checkout.orders o ON o.id = f.order_id
            WHERE o.status = 'FULFILLED'
            """, Long.class);
        assertThat(count).isEqualTo(2L);
    }

    // ─── Blog & Content ───────────────────────────────────────────────────────

    @Test
    void seeder_blogExists() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM content.blogs WHERE handle = 'garden-journal'", Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void seeder_articlesCount() {
        Long published = jdbc.queryForObject(
            "SELECT COUNT(*) FROM content.articles WHERE status = 'PUBLISHED'", Long.class);
        assertThat(published).isEqualTo(3L);

        Long draft = jdbc.queryForObject(
            "SELECT COUNT(*) FROM content.articles WHERE status = 'DRAFT'", Long.class);
        assertThat(draft).isEqualTo(1L);
    }

    @Test
    void seeder_contentTagsExist() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM content.content_tags", Long.class);
        assertThat(count).isEqualTo(3L);
    }

    // ─── Reviews ─────────────────────────────────────────────────────────────

    @Test
    void seeder_productReviewsExist() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM catalog.product_reviews WHERE status = 'PUBLISHED'", Long.class);
        assertThat(count).isEqualTo(6L);
    }

    @Test
    void seeder_verifiedPurchaseReviewsPresent() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM catalog.product_reviews WHERE verified_purchase = true", Long.class);
        assertThat(count).isEqualTo(5L);
    }

    // ─── Wishlist ─────────────────────────────────────────────────────────────

    @Test
    void seeder_customerHasWishlistWithThreeItems() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM catalog.wishlist_items wi
            JOIN catalog.wishlists w ON w.id = wi.wishlist_id
            JOIN auth.users u ON u.id = w.user_id
            WHERE u.email = 'customer@garden.local'
            """, Long.class);
        assertThat(count).isEqualTo(3L);
    }

    // ─── Address ─────────────────────────────────────────────────────────────

    @Test
    void seeder_customerHasSavedAddress() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM auth.addresses a
            JOIN auth.users u ON u.id = a.user_id
            WHERE u.email = 'customer@garden.local' AND a.is_default = true
            """, Long.class);
        assertThat(count).isEqualTo(1L);
    }

    // ─── User tags ────────────────────────────────────────────────────────────

    @Test
    void seeder_customerUserHasTags() {
        String tags = jdbc.queryForObject("""
            SELECT array_to_string(tags, ',') FROM auth.users WHERE email = 'customer@garden.local'
            """, String.class);
        assertThat(tags).contains("vip", "repeat-buyer");
    }
}
