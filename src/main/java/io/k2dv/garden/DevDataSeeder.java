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
        record ProductSeed(String title, String handle, String vendor, String type, String sku, BigDecimal price, String description) {}

        var products = List.of(
            new ProductSeed("Heirloom Tomato Seeds",  "heirloom-tomato-seeds",  "Garden Co", "Seeds", "SKU-001", new BigDecimal("3.99"), """
                ## Heirloom Tomato Seeds

                Grow rich, flavorful tomatoes just like the ones passed down through generations.
                These open-pollinated heirloom varieties produce vibrant fruit in deep reds, \
                golds, and purples — perfect for slicing, roasting, or eating straight off the vine.

                ### Highlights
                - **Variety mix:** includes Brandywine, Cherokee Purple, and Yellow Pear
                - **Days to maturity:** 75–85 days from transplant
                - **Plant spacing:** 18–24 inches apart
                - **Sun:** full sun (6–8 hours daily)

                ### Growing Tips
                Start seeds indoors 6–8 weeks before your last frost date. Transplant once \
                nighttime temperatures stay above 50°F. Water deeply and consistently to prevent \
                blossom-end rot. Save seeds from your best fruit to replant next season.
                """),
            new ProductSeed("Lavender Starter Pack",  "lavender-starter-pack",  "Garden Co", "Seeds", "SKU-002", new BigDecimal("8.99"), """
                ## Lavender Starter Pack

                Fill your garden — and your home — with the calming fragrance of true English lavender.
                This starter pack includes a curated seed blend of classic *Lavandula angustifolia* \
                cultivars, ideal for borders, containers, and cutting gardens.

                ### Highlights
                - **Varieties:** Hidcote, Munstead, and Lady
                - **Bloom time:** early to midsummer
                - **Mature height:** 12–24 inches
                - **Hardiness:** USDA zones 5–8

                ### Growing Tips
                Lavender thrives in well-drained, slightly alkaline soil and full sun. \
                Sow seeds on the surface — they need light to germinate. \
                Once established, lavender is drought-tolerant. \
                Cut stems back by one-third after flowering to encourage bushy regrowth.
                """),
            new ProductSeed("Sunflower Mix",           "sunflower-mix",          "Garden Co", "Seeds", "SKU-003", new BigDecimal("4.49"), """
                ## Sunflower Mix

                Brighten any space with a rainbow of sunflower varieties, from classic golden giants \
                to compact bi-colored beauties. This mix is great for pollinators, cut flowers, \
                and bird-feeding in late summer.

                ### Highlights
                - **Variety mix:** Autumn Beauty, Velvet Queen, Lemon Queen, and Mammoth Grey Stripe
                - **Height range:** 2–8 feet depending on variety
                - **Days to bloom:** 60–80 days from direct sow
                - **Sun:** full sun

                ### Growing Tips
                Direct sow after last frost, 1 inch deep and 6 inches apart. \
                Thin to 12–18 inches for larger heads. \
                Sunflowers are heavy feeders — side-dress with compost mid-season. \
                Leave seed heads standing in autumn for wildlife.
                """),
            new ProductSeed("Garden Trowel",           "garden-trowel",          "Tools Co",  "Tools", "SKU-004", new BigDecimal("12.99"), """
                ## Garden Trowel

                The indispensable tool for every gardener. \
                This stainless-steel trowel features a pointed blade for easy soil penetration \
                and a comfortable, non-slip grip handle that reduces fatigue during extended planting sessions.

                ### Specifications
                - **Blade material:** rust-resistant stainless steel
                - **Handle:** ergonomic rubber-grip
                - **Total length:** 12 inches
                - **Weight:** 5 oz

                ### Best For
                - Transplanting seedlings and plugs
                - Digging small planting holes
                - Mixing amendments into container soil
                - Removing weeds with tap roots
                """),
            new ProductSeed("Pruning Shears",          "pruning-shears",         "Tools Co",  "Tools", "SKU-005", new BigDecimal("24.99"), """
                ## Pruning Shears

                Precision bypass pruning shears engineered for clean, healthy cuts. \
                The high-carbon steel blades stay sharp through seasons of use, \
                while the spring-loaded mechanism reduces hand fatigue on repetitive cuts.

                ### Specifications
                - **Blade type:** bypass (ideal for live stems)
                - **Blade material:** high-carbon steel with sap-groove
                - **Max cutting diameter:** ¾ inch
                - **Safety lock:** thumb-activated latch

                ### Best For
                - Shaping shrubs and perennials
                - Harvesting herbs and flowers
                - Deadheading roses and ornamentals
                - Light fruit-tree pruning

                > **Tip:** Wipe blades with rubbing alcohol between plants to prevent spreading disease.
                """),
            new ProductSeed("Watering Can 2L",         "watering-can-2l",        "Tools Co",  "Tools", "SKU-006", new BigDecimal("18.50"), """
                ## Watering Can 2L

                A compact, well-balanced watering can designed for indoor plants, seedling trays, \
                and delicate container gardens. The long, slender spout delivers water precisely \
                at the root zone without disturbing fragile stems or washing away seed compost.

                ### Specifications
                - **Capacity:** 2 liters
                - **Material:** BPA-free polypropylene
                - **Spout type:** narrow, curved neck with detachable rose head
                - **Dimensions:** 11 × 5 × 9 inches

                ### Best For
                - Indoor houseplants and orchids
                - Seed trays and propagation stations
                - Hanging baskets
                - Balcony and patio containers
                """),
            new ProductSeed("Terracotta Pot 6in",      "terracotta-pot-6in",     "Pots Co",   "Pots",  "SKU-007", new BigDecimal("9.99"), """
                ## Terracotta Pot — 6 inch

                Classic unglazed terracotta, kiln-fired at high temperature for durability. \
                The porous clay walls allow air and moisture to pass through, \
                promoting healthy root development and reducing the risk of overwatering.

                ### Specifications
                - **Diameter:** 6 inches (top opening)
                - **Height:** 5.5 inches
                - **Drainage:** single drainage hole with saucer notch
                - **Material:** natural terracotta clay

                ### Best For
                - Herbs (basil, thyme, rosemary)
                - Succulents and cacti
                - Spring bulbs (crocus, miniature daffodils)
                - Propagating cuttings

                > Ages beautifully — develops a natural mineral patina over time.
                """),
            new ProductSeed("Glazed Planter Large",    "glazed-planter-large",   "Pots Co",   "Pots",  "SKU-008", new BigDecimal("34.99"), """
                ## Glazed Planter — Large

                Make a statement on your patio or entryway with this generously sized glazed planter. \
                The rich, hand-applied glaze in deep forest green adds year-round visual interest, \
                while the frost-resistant ceramic construction means it can stay outdoors in most climates.

                ### Specifications
                - **Diameter:** 14 inches (top opening)
                - **Height:** 13 inches
                - **Drainage:** pre-drilled drainage hole
                - **Material:** frost-resistant glazed stoneware
                - **Color:** deep forest green

                ### Best For
                - Feature plants: dwarf citrus, olive trees, or standard roses
                - Large perennials and ornamental grasses
                - Seasonal displays (tulips in spring, dahlias in summer)
                - Entryway and patio focal points
                """)
        );

        return products.stream().map(p -> {
            UUID productId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO catalog.products (id, title, handle, vendor, product_type, description, status)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')
                """, productId, p.title(), p.handle(), p.vendor(), p.type(), p.description());

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
