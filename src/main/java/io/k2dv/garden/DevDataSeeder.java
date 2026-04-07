package io.k2dv.garden;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM catalog.products", Long.class);
        if (count != null && count > 0) {
            log.info("DevDataSeeder: data already present ({} products), skipping.", count);
            return;
        }
        log.info("DevDataSeeder: seeding dev data...");
        seedPage();
        List<UUID> productIds = seedProducts();
        List<UUID> collectionIds = seedCollections();
        seedCollectionProducts(collectionIds, productIds);
        log.info("DevDataSeeder: done.");
    }

    private void seedPage() {
        jdbc.update("""
            INSERT INTO content.pages (id, title, handle, body, status, published_at)
            VALUES (?, ?, 'home', ?, 'PUBLISHED', clock_timestamp())
            ON CONFLICT DO NOTHING
            """,
            UUID.randomUUID(),
            "Welcome to Garden",
            "Seasonal plants, seeds, and tools for every garden."
        );
    }

    private List<UUID> seedProducts() {
        record ProductSeed(String title, String handle, String vendor, String type, String sku, BigDecimal price) {}

        var products = List.of(
            new ProductSeed("Heirloom Tomato Seeds",  "heirloom-tomato-seeds",  "Garden Co", "Seeds", "SKU-001", new BigDecimal("3.99")),
            new ProductSeed("Lavender Starter Pack",  "lavender-starter-pack",  "Garden Co", "Seeds", "SKU-002", new BigDecimal("8.99")),
            new ProductSeed("Sunflower Mix",           "sunflower-mix",          "Garden Co", "Seeds", "SKU-003", new BigDecimal("4.49")),
            new ProductSeed("Garden Trowel",           "garden-trowel",          "Tools Co",  "Tools", "SKU-004", new BigDecimal("12.99")),
            new ProductSeed("Pruning Shears",          "pruning-shears",         "Tools Co",  "Tools", "SKU-005", new BigDecimal("24.99")),
            new ProductSeed("Watering Can 2L",         "watering-can-2l",        "Tools Co",  "Tools", "SKU-006", new BigDecimal("18.50")),
            new ProductSeed("Terracotta Pot 6in",      "terracotta-pot-6in",     "Pots Co",   "Pots",  "SKU-007", new BigDecimal("9.99")),
            new ProductSeed("Glazed Planter Large",    "glazed-planter-large",   "Pots Co",   "Pots",  "SKU-008", new BigDecimal("34.99"))
        );

        return products.stream().map(p -> {
            UUID productId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO catalog.products (id, title, handle, vendor, product_type, status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """, productId, p.title(), p.handle(), p.vendor(), p.type());

            UUID variantId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO catalog.product_variants
                  (id, product_id, title, sku, price, fulfillment_type, inventory_policy, lead_time_days)
                VALUES (?, ?, 'Default', ?, ?, 'IN_STOCK', 'DENY', 0)
                """, variantId, productId, p.sku(), p.price());

            jdbc.update("""
                INSERT INTO inventory.inventory_items (id, variant_id, requires_shipping)
                VALUES (?, ?, true)
                """, UUID.randomUUID(), variantId);

            return productId;
        }).toList();
    }

    private List<UUID> seedCollections() {
        record CollectionSeed(String title, String handle) {}

        var collections = List.of(
            new CollectionSeed("Seeds & Bulbs",     "seeds-bulbs"),
            new CollectionSeed("Tools & Supplies",  "tools-supplies"),
            new CollectionSeed("Pots & Planters",   "pots-planters")
        );

        return collections.stream().map(c -> {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO catalog.collections (id, title, handle, collection_type, status)
                VALUES (?, ?, ?, 'MANUAL', 'ACTIVE')
                """, id, c.title(), c.handle());
            return id;
        }).toList();
    }

    private void seedCollectionProducts(List<UUID> collectionIds, List<UUID> productIds) {
        // Seeds & Bulbs: products 0, 1, 2
        // Tools & Supplies: products 3, 4, 5
        // Pots & Planters: products 6, 7
        int[][] assignments = { {0, 1, 2}, {3, 4, 5}, {6, 7} };

        for (int ci = 0; ci < collectionIds.size(); ci++) {
            UUID collectionId = collectionIds.get(ci);
            int[] productIndices = assignments[ci];
            for (int pos = 0; pos < productIndices.length; pos++) {
                jdbc.update("""
                    INSERT INTO catalog.collection_products (id, collection_id, product_id, position)
                    VALUES (?, ?, ?, ?)
                    """, UUID.randomUUID(), collectionId, productIds.get(productIndices[pos]), pos + 1);
            }
        }
    }
}
