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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout.return_requests", Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout.order_templates", Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM marketing.newsletter_subscribers", Long.class)).isEqualTo(5L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout.gift_card_transactions", Long.class)).isEqualTo(3L);
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
        assertThat(statuses).contains("PAID", "FULFILLED", "PENDING_PAYMENT", "CANCELLED", "REFUNDED", "INVOICED", "PENDING_APPROVAL");
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

    // ─── Users ───────────────────────────────────────────────────────────────

    @Test
    void seeder_allTestUsersExist() {
        for (String email : java.util.List.of(
                "customer@garden.local", "staff@garden.local", "manager@garden.local",
                "b2b-manager@garden.local", "b2b-member@garden.local")) {
            Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth.users WHERE email = ?", Long.class, email);
            assertThat(count).as("user %s should exist", email).isEqualTo(1L);
        }
    }

    // ─── User tags ────────────────────────────────────────────────────────────

    @Test
    void seeder_customerUserHasTags() {
        String tags = jdbc.queryForObject("""
            SELECT array_to_string(tags, ',') FROM auth.users WHERE email = 'customer@garden.local'
            """, String.class);
        assertThat(tags).contains("vip", "repeat-buyer");
    }

    // ─── Inventory ───────────────────────────────────────────────────────────

    @Test
    void seeder_inventoryLevelsPopulated() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM inventory.inventory_levels WHERE quantity_on_hand = 50", Long.class);
        // 8 simple products + 8 gloves variants + 9 ceramic planter variants = 25 stockable variants
        assertThat(count).isGreaterThanOrEqualTo(25L);
    }

    @Test
    void seeder_singleWarehouseLocation() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM inventory.locations WHERE name = 'Main Warehouse'", Long.class);
        assertThat(count).isEqualTo(1L);
    }

    // ─── Shipping ─────────────────────────────────────────────────────────────

    @Test
    void seeder_shippingZonesExist() {
        var zones = jdbc.queryForList(
            "SELECT name FROM shipping.shipping_zones ORDER BY name", String.class);
        assertThat(zones).containsExactlyInAnyOrder("International", "United States");
    }

    @Test
    void seeder_usShippingHasThreeRates() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM shipping.shipping_rates sr
            JOIN shipping.shipping_zones sz ON sz.id = sr.zone_id
            WHERE sz.name = 'United States'
            """, Long.class);
        assertThat(count).isEqualTo(3L); // Standard, Free (over $50), Express
    }

    // ─── Discounts ────────────────────────────────────────────────────────────

    @Test
    void seeder_codeDiscountsExist() {
        var codes = jdbc.queryForList(
            "SELECT UPPER(code) FROM checkout.discounts WHERE code IS NOT NULL ORDER BY UPPER(code)",
            String.class);
        assertThat(codes).contains("SAVE5", "SUMMER25", "WELCOME10");
    }

    // ─── Gift card transactions ───────────────────────────────────────────────

    @Test
    void seeder_giftCardTransactionsExist() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM checkout.gift_card_transactions", Long.class);
        assertThat(count).isEqualTo(3L); // 1 for gc1, 2 for gc2 (load + spend)
    }

    @Test
    void seeder_partiallySpentGiftCardHasTwoTransactions() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM checkout.gift_card_transactions gct
            JOIN checkout.gift_cards gc ON gc.id = gct.gift_card_id
            WHERE LOWER(gc.code) = 'gift-2500-seed'
            """, Long.class);
        assertThat(count).isEqualTo(2L);
    }

    // ─── Automatic discount ───────────────────────────────────────────────────

    @Test
    void seeder_automaticDiscountExists() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM checkout.discounts WHERE automatic = true AND is_active = true",
            Long.class);
        assertThat(count).isEqualTo(1L);
    }

    // ─── B2B sales rep ────────────────────────────────────────────────────────

    @Test
    void seeder_companyHasSalesRep() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.companies c
            JOIN auth.users u ON u.id = c.sales_rep_user_id
            WHERE c.name = 'Green Thumb Nurseries LLC'
              AND u.email = 'staff@garden.local'
            """, Long.class);
        assertThat(count).isEqualTo(1L);
    }

    // ─── Company shipping addresses ───────────────────────────────────────────

    @Test
    void seeder_companyHasTwoShippingAddresses() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.company_shipping_addresses sa
            JOIN b2b.companies c ON c.id = sa.company_id
            WHERE c.name = 'Green Thumb Nurseries LLC'
            """, Long.class);
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void seeder_companyHasDefaultShippingAddress() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.company_shipping_addresses sa
            JOIN b2b.companies c ON c.id = sa.company_id
            WHERE c.name = 'Green Thumb Nurseries LLC' AND sa.is_default = true
            """, Long.class);
        assertThat(count).isEqualTo(1L);
    }

    // ─── Company product catalog ──────────────────────────────────────────────

    @Test
    void seeder_companyProductCatalogHasSevenProducts() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.company_product_catalogs cpc
            JOIN b2b.companies c ON c.id = cpc.company_id
            WHERE c.name = 'Green Thumb Nurseries LLC'
            """, Long.class);
        assertThat(count).isEqualTo(7L);
    }

    // ─── Price list with adjustment rule ─────────────────────────────────────

    @Test
    void seeder_companyHasTwoPriceLists() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.price_lists pl
            JOIN b2b.companies c ON c.id = pl.company_id
            WHERE c.name = 'Green Thumb Nurseries LLC'
            """, Long.class);
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void seeder_seasonalPriceListHasAdjustmentRule() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM b2b.price_lists pl
            JOIN b2b.companies c ON c.id = pl.company_id
            WHERE c.name = 'Green Thumb Nurseries LLC'
              AND pl.adjustment_type = 'PERCENTAGE_OFF'
              AND pl.adjustment_value = 15.00
            """, Long.class);
        assertThat(count).isEqualTo(1L);
    }

    // ─── PENDING_APPROVAL order ───────────────────────────────────────────────

    @Test
    void seeder_pendingApprovalOrderExists() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM checkout.orders o
            JOIN b2b.companies c ON c.id = o.company_id
            WHERE o.status = 'PENDING_APPROVAL'
              AND c.name = 'Green Thumb Nurseries LLC'
            """, Long.class);
        assertThat(count).isEqualTo(1L);
    }

    // ─── Return requests ─────────────────────────────────────────────────────

    @Test
    void seeder_returnRequestStatusesPresent() {
        var statuses = jdbc.queryForList(
            "SELECT DISTINCT status FROM checkout.return_requests ORDER BY status", String.class);
        assertThat(statuses).containsExactlyInAnyOrder("PENDING", "COMPLETED");
    }

    @Test
    void seeder_pendingReturnHasOneItem() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM checkout.return_request_items rri
            JOIN checkout.return_requests rr ON rr.id = rri.return_request_id
            WHERE rr.status = 'PENDING'
            """, Long.class);
        assertThat(count).isEqualTo(1L);
    }

    // ─── Return requests (continued) ─────────────────────────────────────────

    @Test
    void seeder_completedReturnHasItems() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM checkout.return_request_items rri
            JOIN checkout.return_requests rr ON rr.id = rri.return_request_id
            WHERE rr.status = 'COMPLETED'
            """, Long.class);
        assertThat(count).isEqualTo(1L);
    }

    // ─── Notification preferences ─────────────────────────────────────────────

    @Test
    void seeder_customerNotificationPreferencesSeeded() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM auth.notification_preferences np
            JOIN auth.users u ON u.id = np.user_id
            WHERE u.email = 'customer@garden.local'
            """, Long.class);
        assertThat(count).isEqualTo(6L); // 5 enabled + MARKETING disabled
    }

    @Test
    void seeder_customerMarketingPreferenceDisabled() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM auth.notification_preferences np
            JOIN auth.users u ON u.id = np.user_id
            WHERE u.email = 'customer@garden.local'
              AND np.notification_type = 'MARKETING' AND np.enabled = false
            """, Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void seeder_b2bManagerNotificationPreferencesSeeded() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM auth.notification_preferences np
            JOIN auth.users u ON u.id = np.user_id
            WHERE u.email = 'b2b-manager@garden.local'
            """, Long.class);
        assertThat(count).isEqualTo(3L); // ORDER_CONFIRMATION, ORDER_SHIPPED, QUOTE_UPDATE
    }

    // ─── Order templates ─────────────────────────────────────────────────────

    @Test
    void seeder_orderTemplatesExist() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM checkout.order_templates ot
            JOIN auth.users u ON u.id = ot.user_id
            WHERE u.email = 'customer@garden.local'
            """, Long.class);
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void seeder_orderTemplateItemsExist() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM checkout.order_template_items", Long.class);
        assertThat(count).isEqualTo(6L); // 3 items per template × 2 templates
    }

    // ─── Newsletter subscribers ───────────────────────────────────────────────

    @Test
    void seeder_newsletterSubscribersExist() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM marketing.newsletter_subscribers", Long.class);
        assertThat(count).isEqualTo(5L);
    }

    @Test
    void seeder_newsletterHasOneUnsubscribed() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM marketing.newsletter_subscribers WHERE unsubscribed_at IS NOT NULL",
            Long.class);
        assertThat(count).isEqualTo(1L);
    }

    // ─── Full-text search vectors ─────────────────────────────────────────────

    @Test
    void seeder_productsHaveSearchVectors() {
        Long withoutVector = jdbc.queryForObject(
            "SELECT COUNT(*) FROM catalog.products WHERE search_vector IS NULL", Long.class);
        assertThat(withoutVector).isEqualTo(0L);
    }

    @Test
    void seeder_searchVectorsMatchProductTitles() {
        // 'tomato' should match the Heirloom Tomato Seeds product
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM catalog.products
            WHERE search_vector @@ plainto_tsquery('english', 'tomato')
            """, Long.class);
        assertThat(count).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void seeder_articlesHaveSearchVectors() {
        Long withoutVector = jdbc.queryForObject(
            "SELECT COUNT(*) FROM content.articles WHERE search_vector IS NULL", Long.class);
        assertThat(withoutVector).isEqualTo(0L);
    }
}
