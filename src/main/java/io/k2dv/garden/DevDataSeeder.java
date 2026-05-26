package io.k2dv.garden;

import io.k2dv.garden.blob.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@Profile({"local", "demo"})
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final StorageService storageService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM catalog.products", Long.class);
        if (count != null && count > 0) {
            log.info("DevDataSeeder: data already present ({} products), skipping.", count);
            return;
        }
        log.info("DevDataSeeder: seeding dev data...");
        seedPage();
        // Quote-only products are seeded first so they receive older created_at timestamps.
        // The storefront home page queries the four newest products; seeding priced products
        // last ensures they appear there rather than the null-price quote-only items.
        List<UUID> quoteOnlyProductIds = seedQuoteOnlyProducts();
        List<UUID> productIds = seedProducts();
        List<UUID> variantProductIds = seedVariantProducts();
        List<UUID> collectionIds = seedCollections();
        seedCollectionProducts(collectionIds, productIds, variantProductIds, quoteOnlyProductIds);
        seedImages();
        seedCollectionImages();
        seedInventory(productIds, variantProductIds);
        UUID customerUserId = seedUsers();
        seedShipping();
        seedOrders(customerUserId, productIds, variantProductIds);
        seedDiscounts();
        List<UUID> gcIds = seedGiftCards();
        seedGiftCardTransactions(gcIds.get(0), gcIds.get(1));
        seedB2bCompany(customerUserId);
        seedPendingApprovalOrder();
        seedBlog();
        seedReviews(customerUserId, productIds, variantProductIds);
        seedWishlist(customerUserId, productIds, quoteOnlyProductIds);
        seedCustomerAddress(customerUserId);
        seedReturnRequests(customerUserId);
        seedNotificationPreferences(customerUserId);
        seedOrderTemplates(customerUserId, productIds, variantProductIds);
        seedNewsletterSubscribers();
        // ── Extended variety ───────────────────────────────────────────────────
        List<UUID> extendedProductIds = seedExtendedProducts();
        assignExtendedProductsToCollections(collectionIds, extendedProductIds);
        seedExtendedImages(extendedProductIds);
        seedExtendedInventory(extendedProductIds);
        List<UUID> additionalCustomerIds = seedAdditionalCustomers();
        UUID aliceId = additionalCustomerIds.get(0);
        UUID bobId   = additionalCustomerIds.get(1);
        seedAdditionalOrders(aliceId, bobId, productIds, variantProductIds);
        seedAdditionalReturnRequests(aliceId, bobId);
        seedAdditionalQuotes();
        seedCompanyApprovalRules();
        seedDepartments();
        seedInventoryTransactions(productIds, variantProductIds);
        seedAdditionalReviews(aliceId, bobId, productIds, variantProductIds);
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

    private List<UUID> seedQuoteOnlyProducts() {
        record QuoteProductSeed(String title, String handle, String vendor, String type, String sku, String description) {}

        var products = List.of(
            new QuoteProductSeed(
                "GFRC Trough Planter",
                "gfrc-trough-planter",
                "Stone & Form Co",
                "Planters",
                "SKU-QO-001",
                """
                ## GFRC Trough Planter

                Cast from Glass Fiber Reinforced Concrete, these trough planters bring an \
                architectural presence to any outdoor space — courtyards, rooftop terraces, \
                commercial lobbies, and estate gardens alike. Each piece is made to order \
                in your chosen dimensions, wall thickness, and finish.

                ### Material
                GFRC (Glass Fiber Reinforced Concrete) is significantly lighter than solid \
                precast concrete while retaining its structural integrity and weather resistance. \
                Suitable for year-round outdoor installation in all climates.

                ### Customization Options
                - **Size:** available from 24 in up to 96 in length; custom widths and heights on request
                - **Wall thickness:** standard 1.5 in or heavy-duty 2 in
                - **Finish:** smooth trowel, sand-blasted, acid-washed, or exposed aggregate
                - **Color:** natural grey, charcoal, warm white, or custom pigment match
                - **Drainage:** pre-drilled drainage holes included as standard

                ### Lead Time
                8–12 weeks from confirmed order. Freight delivery required; local placement \
                and installation quoted separately.

                > **Quote required.** Pricing depends on dimensions, finish, and quantity. \
                Submit a quote request with your preferred size and finish details.
                """
            ),
            new QuoteProductSeed(
                "Bluestone Outdoor Pavers",
                "bluestone-outdoor-pavers",
                "Stone & Form Co",
                "Hardscape",
                "SKU-QO-002",
                """
                ## Bluestone Outdoor Pavers

                Natural Pennsylvania bluestone for patios, pool surrounds, garden paths, \
                and outdoor living areas. Renowned for its dense, slip-resistant surface \
                and the cool blue-grey tones that deepen beautifully when wet.

                ### Available Cuts
                - **Sawn** — precise, uniform edges; ideal for formal or contemporary layouts
                - **Natural Cleft** — split along the stone's natural grain; classic, textured surface
                - **Tumbled** — softened edges and a worn, antique feel; suits rustic or cottage gardens

                ### Sizing
                Supplied in irregular flagging, select pattern sets (12×12, 18×18, 24×24 in), \
                or custom-cut to your project plan. Thickness: ¾ in (pedestrian) or 1.5 in (driveway-rated).

                ### Coverage
                Pricing and quantities are project-specific. Submit a quote request with your \
                approximate square footage, preferred cut, and thickness, and we will provide \
                a delivered price to your site.

                ### Notes
                - Natural stone varies in tone — sample slabs available on request
                - Freight delivery only; curbside or tailgate delivery included, offloading by others
                - Sealing recommended; we can supply penetrating stone sealer as an add-on

                > **Quote required.** Priced per project based on square footage, cut, and delivery location.
                """
            ),
            new QuoteProductSeed(
                "Custom Cedar Raised Garden Bed",
                "custom-cedar-raised-garden-bed",
                "Timber & Bloom Co",
                "Garden Beds",
                "SKU-QO-003",
                """
                ## Custom Cedar Raised Garden Bed

                Built to your exact footprint from select-grade Western Red Cedar — \
                naturally rot-resistant, aromatic, and beautiful without any treatment or staining. \
                Ideal for vegetable gardens, cut-flower beds, herb gardens, and accessible growing \
                setups with elevated heights.

                ### Why Cedar?
                Western Red Cedar contains natural oils that repel insects and resist moisture \
                without the need for chemical preservatives. Untreated cedar is safe for edible \
                crops and meets organic gardening standards.

                ### Customization Options
                - **Footprint:** any rectangular or L-shaped dimension up to 16 ft long
                - **Height:** standard 12 in, tall 24 in, or accessible 32 in
                - **Wall thickness:** 2×6 (standard) or 2×8 (heavy-duty)
                - **Corners:** traditional butt-joint or mortised corner posts
                - **Add-ons:** hardware cloth liner (gopher protection), trellis uprights, cover frame

                ### Delivery & Assembly
                Beds ship flat-packed with pre-drilled hardware. Assembly typically takes \
                30–60 minutes with two people and a drill. White-glove delivery and on-site \
                assembly available in select areas — ask when requesting your quote.

                > **Quote required.** Pricing depends on dimensions and selected options. \
                Typical beds range from 4×4 ft starter sizes to full 4×16 ft production rows.
                """
            ),
            new QuoteProductSeed(
                "Cast Stone Fountain",
                "cast-stone-fountain",
                "Stone & Form Co",
                "Water Features",
                "SKU-QO-004",
                """
                ## Cast Stone Fountain

                A centerpiece for formal gardens, courtyards, and estate landscapes. \
                Our cast stone fountains are hand-finished to replicate the look and feel of \
                natural limestone or sandstone, at a fraction of the weight of solid carved stone. \
                Each fountain is made to order and can be customized in size, basin depth, \
                and surface texture.

                ### Standard Models (all quote-to-order)
                - **Tiered Classic** — two or three-tier stacked basin; traditional garden aesthetic
                - **Millstone** — flat, horizontal millstone-style with center spout; contemporary and low-profile
                - **Wall-Mount** — projects from a wall or fence; ideal for smaller courtyard spaces
                - **Urn** — tall urn with continuous overflow; suits formal symmetrical layouts

                ### Material
                Cast stone aggregate with an integral pigment — will not chip, peel, or fade. \
                Frost-resistant to −20°F. Ships with submersible pump, tubing, and installation guide.

                ### Customization
                - Basin diameter: 18 in to 60 in
                - Finish: natural limestone, weathered sandstone, or charcoal
                - Pump specification: matched to basin size and head height

                > **Quote required.** Pricing varies by model, size, and finish. \
                Lead time 6–10 weeks. Freight delivery only.
                """
            )
        );

        return products.stream().map(p -> {
            UUID productId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO catalog.products (id, title, handle, vendor, product_type, description, status)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')
                """, productId, p.title(), p.handle(), p.vendor(), p.type(), p.description());

            UUID variantId = UUID.randomUUID();
            // price is intentionally NULL — these are quote-only products
            jdbc.update("""
                INSERT INTO catalog.product_variants
                  (id, product_id, title, sku, price, fulfillment_type, inventory_policy, lead_time_days)
                VALUES (?, ?, 'Default', ?, NULL, 'IN_STOCK', 'DENY', 0)
                """, variantId, productId, p.sku());

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
            new CollectionSeed("Seeds & Bulbs",       "seeds-bulbs"),
            new CollectionSeed("Tools & Supplies",    "tools-supplies"),
            new CollectionSeed("Pots & Planters",     "pots-planters"),
            new CollectionSeed("Outdoor & Custom",    "outdoor-custom")
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

    private List<UUID> seedVariantProducts() {
        record OptionSeed(String name, List<String> values) {}
        record VariantSeed(String sku, BigDecimal price, List<String> optionValues) {}
        record VariantProductSeed(String title, String handle, String vendor, String type,
                                   String description, List<OptionSeed> options, List<VariantSeed> variants) {}

        var variantProducts = List.of(
            new VariantProductSeed(
                "Gardening Gloves", "gardening-gloves", "Tools Co", "Tools",
                """
                ## Gardening Gloves

                Protect your hands without sacrificing dexterity. These lightweight, flexible gloves \
                feature a breathable mesh back and a nitrile-coated palm for a firm grip on tools, \
                pots, and thorny stems. Available in four sizes and two classic colorways.

                ### Highlights
                - **Palm coating:** micro-foam nitrile for grip and puncture resistance
                - **Back:** breathable stretch mesh keeps hands cool
                - **Cuff:** extended 2-inch cuff guards against soil and debris
                - **Machine washable:** air dry after washing

                ### Size Guide
                | Size | Hand Circumference |
                |------|--------------------|
                | S    | 6.5–7 inches       |
                | M    | 7–8 inches         |
                | L    | 8–9 inches         |
                | XL   | 9–10 inches        |

                ### Best For
                - Weeding and planting
                - Pruning roses and thorny shrubs
                - Handling potting mix and mulch
                - Light digging and transplanting
                """,
                List.of(
                    new OptionSeed("Size",  List.of("S", "M", "L", "XL")),
                    new OptionSeed("Color", List.of("Forest Green", "Charcoal"))
                ),
                List.of(
                    new VariantSeed("SKU-G-S-GRN",  new BigDecimal("14.99"), List.of("S",  "Forest Green")),
                    new VariantSeed("SKU-G-S-CHR",  new BigDecimal("14.99"), List.of("S",  "Charcoal")),
                    new VariantSeed("SKU-G-M-GRN",  new BigDecimal("14.99"), List.of("M",  "Forest Green")),
                    new VariantSeed("SKU-G-M-CHR",  new BigDecimal("14.99"), List.of("M",  "Charcoal")),
                    new VariantSeed("SKU-G-L-GRN",  new BigDecimal("14.99"), List.of("L",  "Forest Green")),
                    new VariantSeed("SKU-G-L-CHR",  new BigDecimal("14.99"), List.of("L",  "Charcoal")),
                    new VariantSeed("SKU-G-XL-GRN", new BigDecimal("14.99"), List.of("XL", "Forest Green")),
                    new VariantSeed("SKU-G-XL-CHR", new BigDecimal("14.99"), List.of("XL", "Charcoal"))
                )
            ),
            new VariantProductSeed(
                "Ceramic Planter", "ceramic-planter", "Pots Co", "Pots",
                """
                ## Ceramic Planter

                A versatile, minimalist ceramic planter that works as beautifully on a windowsill \
                as it does on a patio table. Each piece is hand-finished with a matte glaze in a \
                curated palette of botanically-inspired hues. Available in three sizes and three colors.

                ### Highlights
                - **Material:** high-fired stoneware ceramic
                - **Glaze:** matte, food-safe, frost-resistant finish
                - **Drainage:** pre-drilled hole with matching saucer included
                - **Colors:** White, Sage Green, Dusty Rose

                ### Size Guide
                | Size   | Diameter  | Height | Best For                         |
                |--------|-----------|--------|----------------------------------|
                | Small  | 4 inches  | 3.5 in | Succulents, herbs, propagation   |
                | Medium | 6 inches  | 5.5 in | Ferns, pothos, peace lily        |
                | Large  | 9 inches  | 8 in   | Fiddle-leaf figs, citrus, palms  |

                ### Care
                Wipe clean with a damp cloth. Avoid abrasive cleaners to preserve the glaze finish.
                """,
                List.of(
                    new OptionSeed("Color", List.of("White", "Sage Green", "Dusty Rose")),
                    new OptionSeed("Size",  List.of("Small", "Medium", "Large"))
                ),
                List.of(
                    new VariantSeed("SKU-CP-WHT-S",  new BigDecimal("19.99"), List.of("White",      "Small")),
                    new VariantSeed("SKU-CP-WHT-M",  new BigDecimal("29.99"), List.of("White",      "Medium")),
                    new VariantSeed("SKU-CP-WHT-L",  new BigDecimal("44.99"), List.of("White",      "Large")),
                    new VariantSeed("SKU-CP-SGE-S",  new BigDecimal("19.99"), List.of("Sage Green", "Small")),
                    new VariantSeed("SKU-CP-SGE-M",  new BigDecimal("29.99"), List.of("Sage Green", "Medium")),
                    new VariantSeed("SKU-CP-SGE-L",  new BigDecimal("44.99"), List.of("Sage Green", "Large")),
                    new VariantSeed("SKU-CP-DRS-S",  new BigDecimal("19.99"), List.of("Dusty Rose", "Small")),
                    new VariantSeed("SKU-CP-DRS-M",  new BigDecimal("29.99"), List.of("Dusty Rose", "Medium")),
                    new VariantSeed("SKU-CP-DRS-L",  new BigDecimal("44.99"), List.of("Dusty Rose", "Large"))
                )
            )
        );

        return variantProducts.stream().map(p -> {
            UUID productId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO catalog.products (id, title, handle, vendor, product_type, description, status)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')
                """, productId, p.title(), p.handle(), p.vendor(), p.type(), p.description());

            // Insert options and track option-value UUIDs keyed by "optionName:label"
            Map<String, UUID> optionValueIds = new HashMap<>();
            for (int oi = 0; oi < p.options().size(); oi++) {
                OptionSeed opt = p.options().get(oi);
                UUID optionId = UUID.randomUUID();
                jdbc.update("""
                    INSERT INTO catalog.product_options (id, product_id, name, position)
                    VALUES (?, ?, ?, ?)
                    """, optionId, productId, opt.name(), oi + 1);

                for (int vi = 0; vi < opt.values().size(); vi++) {
                    UUID valueId = UUID.randomUUID();
                    String label = opt.values().get(vi);
                    jdbc.update("""
                        INSERT INTO catalog.product_option_values (id, option_id, label, position)
                        VALUES (?, ?, ?, ?)
                        """, valueId, optionId, label, vi + 1);
                    optionValueIds.put(opt.name() + ":" + label, valueId);
                }
            }

            // Insert variants and link to option values
            for (VariantSeed v : p.variants()) {
                UUID variantId = UUID.randomUUID();
                String variantTitle = String.join(" / ", v.optionValues());
                jdbc.update("""
                    INSERT INTO catalog.product_variants
                      (id, product_id, title, sku, price, fulfillment_type, inventory_policy, lead_time_days)
                    VALUES (?, ?, ?, ?, ?, 'IN_STOCK', 'DENY', 0)
                    """, variantId, productId, variantTitle, v.sku(), v.price());

                for (int oi = 0; oi < p.options().size(); oi++) {
                    String optionName = p.options().get(oi).name();
                    UUID valueId = optionValueIds.get(optionName + ":" + v.optionValues().get(oi));
                    jdbc.update("""
                        INSERT INTO catalog.variant_option_values (variant_id, option_value_id)
                        VALUES (?, ?)
                        """, variantId, valueId);
                }

                jdbc.update("""
                    INSERT INTO inventory.inventory_items (id, variant_id, requires_shipping)
                    VALUES (?, ?, true)
                    """, UUID.randomUUID(), variantId);
            }

            return productId;
        }).toList();
    }

    private void seedCollectionProducts(List<UUID> collectionIds, List<UUID> productIds,
                                         List<UUID> variantProductIds, List<UUID> quoteOnlyProductIds) {
        // Seeds & Bulbs (0):     Tomato, Lavender, Sunflower
        // Tools & Supplies (1):  Trowel, Shears, Watering Can, Gardening Gloves
        // Pots & Planters (2):   Terracotta Pot, Glazed Planter, Ceramic Planter
        // Outdoor & Custom (3):  GFRC Trough Planter, Bluestone Pavers, Custom Cedar Raised Bed, Cast Stone Fountain
        assignToCollection(collectionIds.get(0), List.of(
            productIds.get(0), productIds.get(1), productIds.get(2)));
        assignToCollection(collectionIds.get(1), List.of(
            productIds.get(3), productIds.get(4), productIds.get(5), variantProductIds.get(0)));
        assignToCollection(collectionIds.get(2), List.of(
            productIds.get(6), productIds.get(7), variantProductIds.get(1)));
        assignToCollection(collectionIds.get(3), quoteOnlyProductIds);
    }

    private void seedImages() {
        // Maps product handle → ordered list of alt texts (1 entry = 1 image).
        // Picsum URLs are deterministic: https://picsum.photos/seed/{handle}-{n}/800/600
        Map<String, List<String>> imagesByHandle = new LinkedHashMap<>();

        // Seeds & Bulbs
        imagesByHandle.put("heirloom-tomato-seeds", List.of(
            "Heirloom tomato seeds packet",
            "Ripe heirloom tomatoes on the vine"));
        imagesByHandle.put("lavender-starter-pack", List.of(
            "Lavender seed packet",
            "Lavender in full bloom"));
        imagesByHandle.put("sunflower-mix", List.of(
            "Sunflower seed mix packet",
            "Sunflower field in full bloom"));

        // Tools & Supplies
        imagesByHandle.put("garden-trowel", List.of(
            "Stainless steel garden trowel",
            "Trowel in use planting seedlings"));
        imagesByHandle.put("pruning-shears", List.of(
            "Bypass pruning shears",
            "Pruning shears trimming a rose stem"));
        imagesByHandle.put("watering-can-2l", List.of(
            "2L watering can with detachable rose head"));
        imagesByHandle.put("gardening-gloves", List.of(
            "Gardening gloves in forest green",
            "Gardening gloves in charcoal",
            "Gloves worn during planting"));

        // Pots & Planters
        imagesByHandle.put("terracotta-pot-6in", List.of(
            "6 inch terracotta pot",
            "Terracotta pot planted with succulents"));
        imagesByHandle.put("glazed-planter-large", List.of(
            "Large glazed deep forest green planter",
            "Glazed planter with ornamental grass"));
        imagesByHandle.put("ceramic-planter", List.of(
            "White ceramic planter on windowsill",
            "Sage green ceramic planter outdoors",
            "Dusty rose ceramic planter with fern"));

        // Outdoor & Custom (quote-only)
        imagesByHandle.put("gfrc-trough-planter", List.of(
            "GFRC trough planter in natural grey finish",
            "Close-up of GFRC trough surface texture",
            "GFRC trough planter installed on a rooftop terrace"));
        imagesByHandle.put("bluestone-outdoor-pavers", List.of(
            "Natural cleft bluestone paver surface",
            "Bluestone patio installation around pool"));
        imagesByHandle.put("custom-cedar-raised-garden-bed", List.of(
            "Custom cedar raised garden bed with vegetables",
            "Cedar raised bed corner joinery detail"));
        imagesByHandle.put("cast-stone-fountain", List.of(
            "Tiered cast stone garden fountain",
            "Cast stone fountain water detail",
            "Fountain installed in formal garden courtyard"));

        HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

        for (var entry : imagesByHandle.entrySet()) {
            String handle = entry.getKey();
            List<String> altTexts = entry.getValue();

            UUID productId = jdbc.queryForObject(
                "SELECT id FROM catalog.products WHERE handle = ?", UUID.class, handle);

            UUID featuredImageId = null;
            for (int i = 0; i < altTexts.size(); i++) {
                String filename = handle + "-" + (i + 1) + ".jpg";
                String objectKey = "products/" + filename;
                String picsumUrl = "https://picsum.photos/seed/" + handle + "-" + (i + 1) + "/800/600";

                byte[] imageBytes = downloadImage(http, picsumUrl, handle, i);
                storageService.store(objectKey, "image/jpeg",
                    new ByteArrayInputStream(imageBytes), imageBytes.length);

                UUID blobId = UUID.randomUUID();
                jdbc.update("""
                    INSERT INTO storage.blob_objects (id, key, filename, content_type, size, alt, width, height)
                    VALUES (?, ?, ?, 'image/jpeg', ?, ?, 800, 600)
                    """, blobId, objectKey, filename, imageBytes.length, altTexts.get(i));

                UUID imageId = UUID.randomUUID();
                jdbc.update("""
                    INSERT INTO catalog.product_images (id, product_id, blob_id, alt_text, position)
                    VALUES (?, ?, ?, ?, ?)
                    """, imageId, productId, blobId, altTexts.get(i), i + 1);

                if (i == 0) {
                    featuredImageId = imageId;
                }
            }

            jdbc.update("UPDATE catalog.products SET featured_image_id = ? WHERE id = ?",
                featuredImageId, productId);
        }
    }

    private byte[] downloadImage(HttpClient http, String url, String handle, int index) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to download image for " + handle + "-" + (index + 1)
                    + ": HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to download image for " + handle + "-" + (index + 1), e);
        }
    }

    private void seedCollectionImages() {
        // handle → alt text
        Map<String, String> collections = new LinkedHashMap<>();
        collections.put("seeds-bulbs",    "Seeds and bulbs collection banner");
        collections.put("tools-supplies", "Garden tools and supplies collection banner");
        collections.put("pots-planters",  "Pots and planters collection banner");
        collections.put("outdoor-custom", "Outdoor and custom products collection banner");

        HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

        for (var entry : collections.entrySet()) {
            String handle = entry.getKey();
            String altText = entry.getValue();

            UUID collectionId = jdbc.queryForObject(
                "SELECT id FROM catalog.collections WHERE handle = ?", UUID.class, handle);

            String filename = "collection-" + handle + ".jpg";
            String objectKey = "collections/" + filename;
            String picsumUrl = "https://picsum.photos/seed/col-" + handle + "/1200/400";

            byte[] imageBytes = downloadImage(http, picsumUrl, handle, 0);
            storageService.store(objectKey, "image/jpeg",
                new ByteArrayInputStream(imageBytes), imageBytes.length);

            UUID blobId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO storage.blob_objects (id, key, filename, content_type, size, alt, width, height)
                VALUES (?, ?, ?, 'image/jpeg', ?, ?, 1200, 400)
                """, blobId, objectKey, filename, imageBytes.length, altText);

            jdbc.update("UPDATE catalog.collections SET featured_image_id = ? WHERE id = ?",
                blobId, collectionId);
        }
    }

    private void seedInventory(List<UUID> productIds, List<UUID> variantProductIds) {
        UUID locationId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO inventory.locations (id, name, is_active)
            VALUES (?, 'Main Warehouse', true)
            """, locationId);

        List<UUID> stockableProductIds = new java.util.ArrayList<>();
        stockableProductIds.addAll(productIds);
        stockableProductIds.addAll(variantProductIds);

        for (UUID productId : stockableProductIds) {
            List<UUID> itemIds = jdbc.queryForList("""
                SELECT ii.id
                FROM inventory.inventory_items ii
                JOIN catalog.product_variants pv ON pv.id = ii.variant_id
                WHERE pv.product_id = ?
                """, UUID.class, productId);

            for (UUID itemId : itemIds) {
                jdbc.update("""
                    INSERT INTO inventory.inventory_levels
                      (id, inventory_item_id, location_id, quantity_on_hand, quantity_committed)
                    VALUES (?, ?, ?, 50, 0)
                    """, UUID.randomUUID(), itemId, locationId);
            }
        }
    }

    private void assignToCollection(UUID collectionId, List<UUID> pids) {
        for (int pos = 0; pos < pids.size(); pos++) {
            jdbc.update("""
                INSERT INTO catalog.collection_products (id, collection_id, product_id, position)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), collectionId, pids.get(pos), pos + 1);
        }
    }

    // -------------------------------------------------------------------------
    // Users
    // -------------------------------------------------------------------------

    /** Seeds CUSTOMER, STAFF, and MANAGER test accounts (all with password "password").
     *  Returns the CUSTOMER user UUID for use by downstream seeders. */
    private UUID seedUsers() {
        record UserSeed(String email, String firstName, String lastName, String role) {}

        String hash = passwordEncoder.encode("password");

        var users = List.of(
            new UserSeed("customer@garden.local", "Carol",   "Customer", "CUSTOMER"),
            new UserSeed("staff@garden.local",    "Sam",     "Staff",    "STAFF"),
            new UserSeed("manager@garden.local",  "Marcus",  "Manager",  "MANAGER")
        );

        UUID customerUserId = null;
        for (UserSeed u : users) {
            UUID userId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO auth.users (id, email, first_name, last_name, status, email_verified_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', clock_timestamp())
                ON CONFLICT (email) DO NOTHING
                """, userId, u.email(), u.firstName(), u.lastName());

            // Re-fetch in case of conflict (idempotent)
            userId = jdbc.queryForObject(
                "SELECT id FROM auth.users WHERE email = ?", UUID.class, u.email());

            jdbc.update("""
                INSERT INTO auth.identities (id, user_id, provider, account_id, password_hash)
                VALUES (?, ?, 'CREDENTIALS', ?, ?)
                ON CONFLICT (provider, account_id) DO NOTHING
                """, UUID.randomUUID(), userId, userId.toString(), hash);

            jdbc.update("""
                INSERT INTO auth.user_roles (user_id, role_id)
                SELECT ?, r.id FROM auth.roles r WHERE r.name = ?
                ON CONFLICT DO NOTHING
                """, userId, u.role());

            if ("CUSTOMER".equals(u.role())) {
                customerUserId = userId;
                jdbc.update("""
                    UPDATE auth.users
                    SET tags = ARRAY['vip', 'repeat-buyer'],
                        admin_notes = 'Long-time customer, prefers email contact.'
                    WHERE id = ?
                    """, userId);
            }
        }

        log.info("DevDataSeeder: seeded test users (customer / staff / manager) with password='password'");
        return customerUserId;
    }

    // -------------------------------------------------------------------------
    // Shipping
    // -------------------------------------------------------------------------

    private void seedShipping() {
        UUID usZoneId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO shipping.shipping_zones (id, name, description, country_codes, is_active)
            VALUES (?, 'United States', 'Domestic US shipping', ARRAY['US'], true)
            """, usZoneId);

        // Standard Shipping $5.99
        jdbc.update("""
            INSERT INTO shipping.shipping_rates
              (id, zone_id, name, price, estimated_days_min, estimated_days_max, is_active)
            VALUES (?, ?, 'Standard Shipping', 5.99, 5, 7, true)
            """, UUID.randomUUID(), usZoneId);

        // Free Shipping on orders >= $50
        jdbc.update("""
            INSERT INTO shipping.shipping_rates
              (id, zone_id, name, price, min_order_amount, estimated_days_min, estimated_days_max, is_active)
            VALUES (?, ?, 'Free Shipping', 0.00, 50.00, 5, 7, true)
            """, UUID.randomUUID(), usZoneId);

        // Express Shipping $14.99
        jdbc.update("""
            INSERT INTO shipping.shipping_rates
              (id, zone_id, name, price, estimated_days_min, estimated_days_max, carrier, is_active)
            VALUES (?, ?, 'Express Shipping', 14.99, 1, 2, 'UPS', true)
            """, UUID.randomUUID(), usZoneId);

        UUID intlZoneId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO shipping.shipping_zones (id, name, description, country_codes, is_active)
            VALUES (?, 'International', 'Rest of world', ARRAY['CA','GB','AU','DE','FR'], true)
            """, intlZoneId);

        jdbc.update("""
            INSERT INTO shipping.shipping_rates
              (id, zone_id, name, price, estimated_days_min, estimated_days_max, is_active)
            VALUES (?, ?, 'International Standard', 24.99, 10, 21, true)
            """, UUID.randomUUID(), intlZoneId);

        log.info("DevDataSeeder: seeded shipping zones and rates");
    }

    // -------------------------------------------------------------------------
    // Orders
    // -------------------------------------------------------------------------

    private void seedOrders(UUID customerUserId, List<UUID> productIds, List<UUID> variantProductIds) {
        // Look up variant IDs for a few simple products by product_id
        // productIds: 0=Tomato($3.99), 1=Lavender($8.99), 2=Sunflower($4.49),
        //             3=Trowel($12.99), 4=Shears($24.99), 5=WateringCan($18.50)
        // variantProductIds: 0=Gloves, 1=CeramicPlanter
        UUID tomatoVariantId    = firstVariantOf(productIds.get(0));
        UUID lavenderVariantId  = firstVariantOf(productIds.get(1));
        UUID sunflowerVariantId = firstVariantOf(productIds.get(2));
        UUID trowelVariantId    = firstVariantOf(productIds.get(3));
        UUID shearsVariantId    = firstVariantOf(productIds.get(4));
        UUID canVariantId       = firstVariantOf(productIds.get(5));
        UUID glovesVariantId    = firstVariantOf(variantProductIds.get(0));  // S / Forest Green $14.99

        String shippingAddr = """
            {"firstName":"Carol","lastName":"Customer","address1":"123 Garden St",
             "city":"Portland","province":"OR","zip":"97201","country":"US"}
            """.strip();

        // Order 1: PAID — tomato seeds x2 + lavender x1
        UUID order1Id = UUID.randomUUID();
        BigDecimal order1Total = new BigDecimal("16.97");
        jdbc.update("""
            INSERT INTO checkout.orders
              (id, user_id, status, stripe_session_id, stripe_payment_intent_id,
               total_amount, currency, shipping_address, created_at, updated_at)
            VALUES (?, ?, 'PAID', 'cs_test_seed_001', 'pi_test_seed_001',
                    ?, 'usd', ?::jsonb, ?, ?)
            """, order1Id, customerUserId, order1Total, shippingAddr,
                Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)));
        insertOrderItem(order1Id, tomatoVariantId,   2, new BigDecimal("3.99"));
        insertOrderItem(order1Id, lavenderVariantId, 1, new BigDecimal("8.99"));
        insertOrderEvent(order1Id, "ORDER_PLACED",  "Order placed");
        insertOrderEvent(order1Id, "PAYMENT_RECEIVED", "Payment received via Stripe");

        // Order 2: PAID — trowel x1 + shears x1
        UUID order2Id = UUID.randomUUID();
        BigDecimal order2Total = new BigDecimal("37.98");
        jdbc.update("""
            INSERT INTO checkout.orders
              (id, user_id, status, stripe_session_id, stripe_payment_intent_id,
               total_amount, currency, shipping_address, created_at, updated_at)
            VALUES (?, ?, 'PAID', 'cs_test_seed_002', 'pi_test_seed_002',
                    ?, 'usd', ?::jsonb, ?, ?)
            """, order2Id, customerUserId, order2Total, shippingAddr,
                Timestamp.from(Instant.now().minus(5, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(5, ChronoUnit.DAYS)));
        insertOrderItem(order2Id, trowelVariantId, 1, new BigDecimal("12.99"));
        insertOrderItem(order2Id, shearsVariantId, 1, new BigDecimal("24.99"));
        insertOrderEvent(order2Id, "ORDER_PLACED",    "Order placed");
        insertOrderEvent(order2Id, "PAYMENT_RECEIVED","Payment received via Stripe");

        // Order 3: FULFILLED — watering can x1 + gloves M/Charcoal x1
        UUID order3Id = UUID.randomUUID();
        BigDecimal order3Total = new BigDecimal("33.49");
        jdbc.update("""
            INSERT INTO checkout.orders
              (id, user_id, status, stripe_session_id, stripe_payment_intent_id,
               total_amount, currency, shipping_address, created_at, updated_at)
            VALUES (?, ?, 'FULFILLED', 'cs_test_seed_003', 'pi_test_seed_003',
                    ?, 'usd', ?::jsonb, ?, ?)
            """, order3Id, customerUserId, order3Total, shippingAddr,
                Timestamp.from(Instant.now().minus(20, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(15, ChronoUnit.DAYS)));
        UUID item3aId = insertOrderItem(order3Id, canVariantId,    1, new BigDecimal("18.50"));
        UUID item3bId = insertOrderItem(order3Id, glovesVariantId, 1, new BigDecimal("14.99"));
        insertOrderEvent(order3Id, "ORDER_PLACED",    "Order placed");
        insertOrderEvent(order3Id, "PAYMENT_RECEIVED","Payment received via Stripe");
        insertOrderEvent(order3Id, "FULFILLMENT_CREATED", "Shipped via USPS — tracking: 9400111899223397910");

        UUID fulfillmentId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO checkout.fulfillments
              (id, order_id, status, tracking_number, tracking_company, tracking_url)
            VALUES (?, ?, 'DELIVERED', '9400111899223397910', 'USPS',
                    'https://tools.usps.com/go/TrackConfirmAction?tLabels=9400111899223397910')
            """, fulfillmentId, order3Id);
        jdbc.update("""
            INSERT INTO checkout.fulfillment_items (id, fulfillment_id, order_item_id, quantity)
            VALUES (?, ?, ?, 1)
            """, UUID.randomUUID(), fulfillmentId, item3aId);
        jdbc.update("""
            INSERT INTO checkout.fulfillment_items (id, fulfillment_id, order_item_id, quantity)
            VALUES (?, ?, ?, 1)
            """, UUID.randomUUID(), fulfillmentId, item3bId);

        // Order 4: PENDING_PAYMENT — lavender x2
        // stripe_session_id intentionally NULL: represents a customer who started checkout
        // but never reached the Stripe payment page. Leaving it NULL keeps the payment
        // reconciliation scheduler from polling Stripe every 10 minutes with a fake ID.
        UUID order4Id = UUID.randomUUID();
        BigDecimal order4Total = new BigDecimal("17.98");
        jdbc.update("""
            INSERT INTO checkout.orders
              (id, user_id, status,
               total_amount, currency, shipping_address, created_at, updated_at)
            VALUES (?, ?, 'PENDING_PAYMENT',
                    ?, 'usd', ?::jsonb, ?, ?)
            """, order4Id, customerUserId, order4Total, shippingAddr,
                Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)),
                Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)));
        insertOrderItem(order4Id, lavenderVariantId, 2, new BigDecimal("8.99"));
        insertOrderEvent(order4Id, "ORDER_PLACED", "Order placed, awaiting payment");

        // Order 5: CANCELLED — shears x1
        UUID order5Id = UUID.randomUUID();
        BigDecimal order5Total = new BigDecimal("24.99");
        jdbc.update("""
            INSERT INTO checkout.orders
              (id, user_id, status, total_amount, currency, shipping_address, created_at, updated_at)
            VALUES (?, ?, 'CANCELLED', ?, 'usd', ?::jsonb, ?, ?)
            """, order5Id, customerUserId, order5Total, shippingAddr,
                Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(29, ChronoUnit.DAYS)));
        insertOrderItem(order5Id, shearsVariantId, 1, new BigDecimal("24.99"));
        insertOrderEvent(order5Id, "ORDER_PLACED",   "Order placed");
        insertOrderEvent(order5Id, "ORDER_CANCELLED","Cancelled by customer");

        // Order 6: REFUNDED — sunflower x3
        UUID order6Id = UUID.randomUUID();
        BigDecimal order6Total = new BigDecimal("13.47");
        jdbc.update("""
            INSERT INTO checkout.orders
              (id, user_id, status, stripe_session_id, stripe_payment_intent_id,
               total_amount, currency, shipping_address, created_at, updated_at)
            VALUES (?, ?, 'REFUNDED', 'cs_test_seed_006', 'pi_test_seed_006',
                    ?, 'usd', ?::jsonb, ?, ?)
            """, order6Id, customerUserId, order6Total, shippingAddr,
                Timestamp.from(Instant.now().minus(7, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(6, ChronoUnit.DAYS)));
        insertOrderItem(order6Id, sunflowerVariantId, 3, new BigDecimal("4.49"));
        insertOrderEvent(order6Id, "ORDER_PLACED",    "Order placed");
        insertOrderEvent(order6Id, "PAYMENT_RECEIVED","Payment received via Stripe");
        insertOrderEvent(order6Id, "ORDER_REFUNDED",  "Full refund issued — customer request");

        log.info("DevDataSeeder: seeded 6 sample orders");
    }

    private UUID firstVariantOf(UUID productId) {
        return jdbc.queryForObject("""
            SELECT id FROM catalog.product_variants WHERE product_id = ? LIMIT 1
            """, UUID.class, productId);
    }

    private UUID insertOrderItem(UUID orderId, UUID variantId, int qty, BigDecimal unitPrice) {
        UUID itemId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO checkout.order_items (id, order_id, variant_id, quantity, unit_price)
            VALUES (?, ?, ?, ?, ?)
            """, itemId, orderId, variantId, qty, unitPrice);
        return itemId;
    }

    private void insertOrderEvent(UUID orderId, String type, String message) {
        jdbc.update("""
            INSERT INTO checkout.order_events (id, order_id, type, message, author_name)
            VALUES (?, ?, ?, ?, 'System')
            """, UUID.randomUUID(), orderId, type, message);
    }

    // -------------------------------------------------------------------------
    // Discounts
    // -------------------------------------------------------------------------

    private void seedDiscounts() {
        // WELCOME10 — 10% off, no minimum, active
        jdbc.update("""
            INSERT INTO checkout.discounts
              (id, code, type, value, max_uses, starts_at, is_active)
            VALUES (?, 'WELCOME10', 'PERCENTAGE', 10.00, 500,
                    clock_timestamp(), true)
            ON CONFLICT DO NOTHING
            """, UUID.randomUUID());

        // SAVE5 — $5 flat, min order $25, active
        jdbc.update("""
            INSERT INTO checkout.discounts
              (id, code, type, value, min_order_amount, max_uses, starts_at, is_active)
            VALUES (?, 'SAVE5', 'FIXED_AMOUNT', 5.00, 25.00, 200,
                    clock_timestamp(), true)
            ON CONFLICT DO NOTHING
            """, UUID.randomUUID());

        // SUMMER25 — 25% off, expired (useful to test expired-code UI)
        jdbc.update("""
            INSERT INTO checkout.discounts
              (id, code, type, value, starts_at, ends_at, is_active)
            VALUES (?, 'SUMMER25', 'PERCENTAGE', 25.00,
                    clock_timestamp() - INTERVAL '90 days',
                    clock_timestamp() - INTERVAL '1 day',
                    false)
            ON CONFLICT DO NOTHING
            """, UUID.randomUUID());

        // Automatic 10% off on orders >= $75 — no code needed
        jdbc.update("""
            INSERT INTO checkout.discounts
              (id, code, type, value, min_order_amount, automatic, starts_at, is_active)
            VALUES (?, NULL, 'PERCENTAGE', 10.00, 75.00, true, clock_timestamp(), true)
            ON CONFLICT DO NOTHING
            """, UUID.randomUUID());

        // FREESHIP50 — free shipping on orders >= $50
        jdbc.update("""
            INSERT INTO checkout.discounts
              (id, code, type, value, min_order_amount, max_uses, starts_at, is_active)
            VALUES (?, 'FREESHIP50', 'FREE_SHIPPING', 0.00, 50.00, 300,
                    clock_timestamp(), true)
            ON CONFLICT DO NOTHING
            """, UUID.randomUUID());

        log.info("DevDataSeeder: seeded discount codes (WELCOME10, SAVE5, SUMMER25, FREESHIP50) + 1 automatic discount");
    }

    // -------------------------------------------------------------------------
    // Gift cards
    // -------------------------------------------------------------------------

    private List<UUID> seedGiftCards() {
        UUID gcId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO checkout.gift_cards
              (id, code, initial_balance, current_balance, currency, is_active, note)
            VALUES (?, 'GIFT-5000-SEED', 50.00, 50.00, 'usd', true,
                    'Seeded gift card for local dev testing')
            ON CONFLICT DO NOTHING
            """, gcId);
        gcId = jdbc.queryForObject("SELECT id FROM checkout.gift_cards WHERE LOWER(code) = 'gift-5000-seed'", UUID.class);

        UUID gc2Id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO checkout.gift_cards
              (id, code, initial_balance, current_balance, currency, is_active, note)
            VALUES (?, 'GIFT-2500-SEED', 25.00, 12.50, 'usd', true,
                    'Partially spent seeded gift card')
            ON CONFLICT DO NOTHING
            """, gc2Id);
        gc2Id = jdbc.queryForObject("SELECT id FROM checkout.gift_cards WHERE LOWER(code) = 'gift-2500-seed'", UUID.class);

        log.info("DevDataSeeder: seeded gift cards (GIFT-5000-SEED, GIFT-2500-SEED)");
        return List.of(gcId, gc2Id);
    }

    private void seedGiftCardTransactions(UUID gc1Id, UUID gc2Id) {
        // GIFT-5000-SEED: initial load
        jdbc.update("""
            INSERT INTO checkout.gift_card_transactions (id, gift_card_id, delta, note)
            VALUES (?, ?, 50.00, 'Initial load')
            """, UUID.randomUUID(), gc1Id);

        // GIFT-2500-SEED: initial load then partial spend
        jdbc.update("""
            INSERT INTO checkout.gift_card_transactions (id, gift_card_id, delta, note)
            VALUES (?, ?, 25.00, 'Initial load')
            """, UUID.randomUUID(), gc2Id);
        jdbc.update("""
            INSERT INTO checkout.gift_card_transactions (id, gift_card_id, delta, note)
            VALUES (?, ?, -12.50, 'Applied at checkout')
            """, UUID.randomUUID(), gc2Id);

        log.info("DevDataSeeder: seeded gift card transactions");
    }

    // -------------------------------------------------------------------------
    // B2B
    // -------------------------------------------------------------------------

    private void seedB2bCompany(UUID ownerUserId) {
        // ─── Company ─────────────────────────────────────────────────────────
        UUID companyId = UUID.randomUUID();
        UUID staffId = jdbc.queryForObject("SELECT id FROM auth.users WHERE email = 'staff@garden.local'", UUID.class);
        jdbc.update("""
            INSERT INTO b2b.companies
              (id, name, tax_id, phone,
               billing_address_line1, billing_city, billing_state,
               billing_postal_code, billing_country,
               sales_rep_user_id, tax_exempt)
            VALUES (?, 'Green Thumb Nurseries LLC', '12-3456789', '+1-503-555-0100',
                    '456 Bloom Ave', 'Portland', 'OR', '97202', 'US',
                    ?, false)
            """, companyId, staffId);

        // ─── Memberships ─────────────────────────────────────────────────────
        // OWNER = existing customer user (carol@garden.local)
        jdbc.update("""
            INSERT INTO b2b.company_memberships (id, company_id, user_id, role)
            VALUES (?, ?, ?, 'OWNER') ON CONFLICT DO NOTHING
            """, UUID.randomUUID(), companyId, ownerUserId);

        // Seed two extra B2B users (MANAGER and MEMBER)
        UUID managerId = seedB2bUser("b2b-manager@garden.local", "Marcus", "Manager");
        UUID memberId  = seedB2bUser("b2b-member@garden.local",  "Maria",  "Member");

        jdbc.update("""
            INSERT INTO b2b.company_memberships (id, company_id, user_id, role)
            VALUES (?, ?, ?, 'MANAGER') ON CONFLICT DO NOTHING
            """, UUID.randomUUID(), companyId, managerId);

        // MEMBER with a $2,000 spending limit
        jdbc.update("""
            INSERT INTO b2b.company_memberships (id, company_id, user_id, role, spending_limit)
            VALUES (?, ?, ?, 'MEMBER', ?) ON CONFLICT DO NOTHING
            """, UUID.randomUUID(), companyId, memberId, new BigDecimal("2000.00"));

        // ─── Credit account (NET-30, $5,000 limit) ────────────────────────
        jdbc.update("""
            INSERT INTO b2b.credit_accounts (id, company_id, credit_limit, payment_terms_days)
            VALUES (?, ?, 5000.00, 30)
            """, UUID.randomUUID(), companyId);

        // ─── Price list + entries ─────────────────────────────────────────
        UUID priceListId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO b2b.price_lists (id, company_id, name, currency, priority)
            VALUES (?, ?, 'Contract Pricing 2026', 'USD', 10)
            """, priceListId, companyId);

        UUID trowelVid    = variantIdBySku("SKU-004");
        UUID shearsVid    = variantIdBySku("SKU-005");
        UUID glovesVid    = variantIdBySku("SKU-G-M-GRN");
        UUID lavenderVid  = variantIdBySku("SKU-002");
        UUID sunflowerVid = variantIdBySku("SKU-003");

        insertPriceListEntry(priceListId, trowelVid,   new BigDecimal("9.99"),  1);
        insertPriceListEntry(priceListId, shearsVid,   new BigDecimal("19.99"), 1);
        insertPriceListEntry(priceListId, glovesVid,   new BigDecimal("11.99"), 1);
        insertPriceListEntry(priceListId, glovesVid,   new BigDecimal("9.99"),  10); // volume tier
        insertPriceListEntry(priceListId, lavenderVid, new BigDecimal("6.99"),  1);
        insertPriceListEntry(priceListId, sunflowerVid,new BigDecimal("3.49"),  1);

        // ─── Shared delivery address ──────────────────────────────────────
        String addr  = "456 Bloom Ave";
        String city  = "Portland";
        String state = "OR";
        String zip   = "97202";
        String ctry  = "US";

        // ─── Quote 1: PENDING — just submitted, awaiting staff assignment ─
        UUID quote1Id = UUID.randomUUID();
        UUID grfcVid  = variantIdBySku("SKU-QO-001");
        jdbc.update("""
            INSERT INTO quote.quote_requests
              (id, user_id, company_id, status,
               delivery_address_line1, delivery_city, delivery_state,
               delivery_postal_code, delivery_country,
               customer_notes, created_at, updated_at)
            VALUES (?, ?, ?, 'PENDING', ?, ?, ?, ?, ?,
                    'Please include care instructions for the trough planter.', ?, ?)
            """, quote1Id, ownerUserId, companyId,
                addr, city, state, zip, ctry,
                Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)),
                Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)));
        insertQuoteItem(quote1Id, grfcVid, "GFRC Trough Planter — 48in × 18in × 20in, Charcoal finish", 2, null);

        // ─── Quote 2: SENT — priced by staff, expires in 14 days ─────────
        UUID quote2Id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO quote.quote_requests
              (id, user_id, company_id, status,
               delivery_address_line1, delivery_city, delivery_state,
               delivery_postal_code, delivery_country,
               customer_notes, staff_notes, expires_at, created_at, updated_at)
            VALUES (?, ?, ?, 'SENT', ?, ?, ?, ?, ?,
                    'Large-volume order for spring restock.',
                    'Contract pricing applied. Freight included in unit price.',
                    ?, ?, ?)
            """, quote2Id, managerId, companyId,
                addr, city, state, zip, ctry,
                Timestamp.from(Instant.now().plus(14, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(3, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)));
        insertQuoteItem(quote2Id, trowelVid,  "Garden Trowel",                        20, new BigDecimal("9.99"));
        insertQuoteItem(quote2Id, shearsVid,  "Pruning Shears",                       10, new BigDecimal("19.99"));
        insertQuoteItem(quote2Id, glovesVid,  "Gardening Gloves (M / Forest Green)",  50, new BigDecimal("9.99"));

        // ─── Quote 3: ACCEPTED — invoiced via net terms ───────────────────
        UUID quote3Id    = UUID.randomUUID();
        BigDecimal q3Total = new BigDecimal("296.95"); // 30×6.99 + 25×3.49
        jdbc.update("""
            INSERT INTO quote.quote_requests
              (id, user_id, company_id, status,
               delivery_address_line1, delivery_city, delivery_state,
               delivery_postal_code, delivery_country,
               expires_at, created_at, updated_at)
            VALUES (?, ?, ?, 'ACCEPTED', ?, ?, ?, ?, ?,
                    ?, ?, ?)
            """, quote3Id, memberId, companyId,
                addr, city, state, zip, ctry,
                Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(15, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(13, ChronoUnit.DAYS)));
        insertQuoteItem(quote3Id, lavenderVid,  "Lavender Starter Pack", 30, new BigDecimal("6.99"));
        insertQuoteItem(quote3Id, sunflowerVid, "Sunflower Mix",          25, new BigDecimal("3.49"));

        // Corresponding INVOICED order and invoice
        String invoiceAddr = """
            {"firstName":"Maria","lastName":"Member","address1":"456 Bloom Ave",
             "city":"Portland","province":"OR","zip":"97202","country":"US"}
            """.strip();
        UUID invoiceOrderId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO checkout.orders
              (id, user_id, status, total_amount, currency, shipping_address, created_at, updated_at)
            VALUES (?, ?, 'INVOICED', ?, 'usd', ?::jsonb, ?, ?)
            """, invoiceOrderId, memberId, q3Total, invoiceAddr,
                Timestamp.from(Instant.now().minus(13, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(13, ChronoUnit.DAYS)));
        insertOrderItem(invoiceOrderId, lavenderVid,  30, new BigDecimal("6.99"));
        insertOrderItem(invoiceOrderId, sunflowerVid, 25, new BigDecimal("3.49"));

        jdbc.update("UPDATE quote.quote_requests SET order_id = ? WHERE id = ?",
            invoiceOrderId, quote3Id);

        jdbc.update("""
            INSERT INTO b2b.invoices
              (id, company_id, order_id, quote_id, status,
               total_amount, paid_amount, currency, due_at)
            VALUES (?, ?, ?, ?, 'ISSUED', ?, 0, 'USD', ?)
            """, UUID.randomUUID(), companyId, invoiceOrderId, quote3Id,
                q3Total,
                Timestamp.from(Instant.now().minus(13, ChronoUnit.DAYS).plus(30, ChronoUnit.DAYS)));

        // ─── Quote 4: CANCELLED ───────────────────────────────────────────
        UUID quote4Id      = UUID.randomUUID();
        UUID bluestoneVid  = variantIdBySku("SKU-QO-002");
        jdbc.update("""
            INSERT INTO quote.quote_requests
              (id, user_id, company_id, status,
               delivery_address_line1, delivery_city, delivery_state,
               delivery_postal_code, delivery_country,
               created_at, updated_at)
            VALUES (?, ?, ?, 'CANCELLED', ?, ?, ?, ?, ?, ?, ?)
            """, quote4Id, ownerUserId, companyId,
                addr, city, state, zip, ctry,
                Timestamp.from(Instant.now().minus(20, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(18, ChronoUnit.DAYS)));
        insertQuoteItem(quote4Id, bluestoneVid,
            "Bluestone Outdoor Pavers — Natural Cleft, 18×18 in", 200, null);

        // ─── Pending invitation ───────────────────────────────────────────
        jdbc.update("""
            INSERT INTO b2b.company_invitations
              (id, company_id, email, role, spending_limit, token, invited_by, status, expires_at)
            VALUES (?, ?, 'newbuyer@example.com', 'MEMBER', 1500.00,
                    '00000000-b2b0-0000-0000-000000000001',
                    ?, 'PENDING', ?)
            ON CONFLICT DO NOTHING
            """, UUID.randomUUID(), companyId, ownerUserId,
                Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS)));

        // ─── Company shipping addresses ───────────────────────────────────
        jdbc.update("""
            INSERT INTO b2b.company_shipping_addresses
              (id, company_id, label, first_name, last_name,
               address1, city, province, zip, country, is_default)
            VALUES (?, ?, 'Main Warehouse', 'Green Thumb', 'Nurseries LLC',
                    '456 Bloom Ave', 'Portland', 'OR', '97202', 'US', true)
            """, UUID.randomUUID(), companyId);
        jdbc.update("""
            INSERT INTO b2b.company_shipping_addresses
              (id, company_id, label, first_name, last_name,
               address1, city, province, zip, country, is_default)
            VALUES (?, ?, 'Downtown Showroom', 'Green Thumb', 'Nurseries LLC',
                    '789 Rose Ave', 'Portland', 'OR', '97201', 'US', false)
            """, UUID.randomUUID(), companyId);

        // ─── Company product catalog (restrict to tools + seeds) ──────────
        for (String handle : List.of(
                "heirloom-tomato-seeds", "lavender-starter-pack", "sunflower-mix",
                "garden-trowel", "pruning-shears", "watering-can-2l", "gardening-gloves")) {
            UUID pid = jdbc.queryForObject(
                "SELECT id FROM catalog.products WHERE handle = ?", UUID.class, handle);
            jdbc.update("""
                INSERT INTO b2b.company_product_catalogs (id, company_id, product_id)
                VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), companyId, pid);
        }

        // ─── Seasonal price list with adjustment rule (15% off) ──────────
        UUID seasonalPriceListId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO b2b.price_lists
              (id, company_id, name, currency, priority, adjustment_type, adjustment_value)
            VALUES (?, ?, 'Seasonal 15% Off', 'USD', 5, 'PERCENTAGE_OFF', 15.00)
            """, seasonalPriceListId, companyId);

        log.info("DevDataSeeder: seeded B2B company 'Green Thumb Nurseries LLC' " +
            "with 3 members, 2 price lists, 4 quotes, credit account, shipping addresses, " +
            "product catalog, and pending invitation");
    }

    /** Seeds a CUSTOMER-role user with password "password" and returns their UUID. */
    private UUID seedB2bUser(String email, String firstName, String lastName) {
        String hash = passwordEncoder.encode("password");
        UUID userId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO auth.users (id, email, first_name, last_name, status, email_verified_at)
            VALUES (?, ?, ?, ?, 'ACTIVE', clock_timestamp())
            ON CONFLICT (email) DO NOTHING
            """, userId, email, firstName, lastName);
        userId = jdbc.queryForObject("SELECT id FROM auth.users WHERE email = ?", UUID.class, email);
        jdbc.update("""
            INSERT INTO auth.identities (id, user_id, provider, account_id, password_hash)
            VALUES (?, ?, 'CREDENTIALS', ?, ?)
            ON CONFLICT (provider, account_id) DO NOTHING
            """, UUID.randomUUID(), userId, userId.toString(), hash);
        jdbc.update("""
            INSERT INTO auth.user_roles (user_id, role_id)
            SELECT ?, r.id FROM auth.roles r WHERE r.name = 'CUSTOMER'
            ON CONFLICT DO NOTHING
            """, userId);
        return userId;
    }

    private UUID variantIdBySku(String sku) {
        return jdbc.queryForObject(
            "SELECT id FROM catalog.product_variants WHERE sku = ?", UUID.class, sku);
    }

    private void insertPriceListEntry(UUID priceListId, UUID variantId, BigDecimal price, int minQty) {
        jdbc.update("""
            INSERT INTO b2b.price_list_entries (id, price_list_id, variant_id, price, min_qty)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT ON CONSTRAINT uq_price_list_entry DO NOTHING
            """, UUID.randomUUID(), priceListId, variantId, price, minQty);
    }

    private void insertQuoteItem(UUID quoteRequestId, UUID variantId, String description,
                                  int quantity, BigDecimal unitPrice) {
        jdbc.update("""
            INSERT INTO quote.quote_items
              (id, quote_request_id, variant_id, description, quantity, unit_price)
            VALUES (?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), quoteRequestId, variantId, description, quantity, unitPrice);
    }

    // -------------------------------------------------------------------------
    // Blog + Articles
    // -------------------------------------------------------------------------

    private void seedBlog() {
        UUID blogId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO content.blogs (id, title, handle) VALUES (?, 'The Garden Journal', 'garden-journal')
            ON CONFLICT (handle) DO NOTHING
            """, blogId);
        blogId = jdbc.queryForObject("SELECT id FROM content.blogs WHERE handle = 'garden-journal'", UUID.class);

        jdbc.update("INSERT INTO content.content_tags (id, name) VALUES (?, 'Growing Tips') ON CONFLICT (name) DO NOTHING", UUID.randomUUID());
        jdbc.update("INSERT INTO content.content_tags (id, name) VALUES (?, 'Product Care') ON CONFLICT (name) DO NOTHING", UUID.randomUUID());
        jdbc.update("INSERT INTO content.content_tags (id, name) VALUES (?, 'Seasonal') ON CONFLICT (name) DO NOTHING",     UUID.randomUUID());
        UUID tagGrowingTips = jdbc.queryForObject("SELECT id FROM content.content_tags WHERE name = 'Growing Tips'", UUID.class);
        UUID tagProductCare = jdbc.queryForObject("SELECT id FROM content.content_tags WHERE name = 'Product Care'", UUID.class);
        UUID tagSeasonal    = jdbc.queryForObject("SELECT id FROM content.content_tags WHERE name = 'Seasonal'",     UUID.class);

        UUID staffId = jdbc.queryForObject("SELECT id FROM auth.users WHERE email = 'staff@garden.local'", UUID.class);

        UUID article1Id = seedArticle(blogId, staffId,
            "Getting Started with Heirloom Tomatoes", "getting-started-heirloom-tomatoes",
            "How to grow heirloom tomatoes from seed — variety selection, germination, transplanting, and care.",
            """
            ## Growing Heirloom Tomatoes from Seed

            Heirloom tomatoes are open-pollinated varieties passed down through generations of gardeners. \
            Unlike modern hybrids, their seeds can be saved and replanted year after year — and the flavour is unmatched.

            ### Starting Indoors

            Begin seeds 6–8 weeks before your last frost date. Sow into a well-draining seed-starting mix, \
            about ¼ inch deep. Keep the medium at 70–80°F for germination; a heat mat helps greatly.

            Once seedlings emerge (typically 5–10 days), move them to bright light — at least 14–16 hours of \
            artificial light per day if growing indoors.

            ### Transplanting

            Harden off seedlings over one to two weeks before transplanting outdoors. Plant deep: bury the stem \
            up to the first set of true leaves to encourage a strong root system.

            ### Watering and Feeding

            Inconsistent watering is the most common cause of blossom-end rot. Water deeply and regularly, \
            and mulch around the base to retain moisture.

            Side-dress with compost or balanced fertiliser every three to four weeks once flowering begins.

            ### Saving Seed

            Select your best fruit from the most vigorous plants. Ferment the seed gel for two to three days, \
            rinse, dry thoroughly, and store in a cool, dark place.
            """,
            "PUBLISHED", Instant.now().minus(21, ChronoUnit.DAYS));
        linkArticleTag(article1Id, tagGrowingTips);
        linkArticleTag(article1Id, tagSeasonal);

        UUID article2Id = seedArticle(blogId, staffId,
            "Essential Tools Every Gardener Needs", "essential-tools-every-gardener-needs",
            "A guide to the five tools that earn their keep in every garden, season after season.",
            """
            ## The Tools That Earn Their Keep

            A well-chosen garden tool lasts for decades. These are the five pieces we reach for every single day — \
            from sowing in spring to the final tidy before the first frost.

            ### 1. The Trowel

            For transplanting, mixing amendments, and scooping potting mix, nothing beats a good stainless-steel trowel. \
            Look for a pointed blade to cut through compacted soil easily, and a comfortable rubber-grip handle \
            that won't blister during a long session.

            ### 2. Bypass Pruning Shears

            A quality pair of bypass shears will outlast a dozen cheap ones. The bypass mechanism — two curved blades \
            that slide past each other like scissors — makes clean cuts that heal quickly, reducing disease entry points. \
            Keep them sharp, wiped clean with rubbing alcohol between plants.

            ### 3. Gardening Gloves

            Gloves do more than protect your hands from thorns. A nitrile-coated palm gives grip on wet tools and pots; \
            a breathable mesh back keeps hands cool. Get a snug fit — too large and you'll drop things, \
            too small and you'll tire quickly.

            ### 4. Watering Can

            For seedlings and indoor plants, a watering can beats a hose every time. The narrow spout lets you deliver \
            water precisely to the root zone without dislodging seeds or washing away a freshly sown surface.

            ### 5. Knee Pad or Kneeler

            It sounds unglamorous, but a good kneeler will save your knees through years of planting and weeding.
            """,
            "PUBLISHED", Instant.now().minus(14, ChronoUnit.DAYS));
        linkArticleTag(article2Id, tagProductCare);

        UUID article3Id = seedArticle(blogId, staffId,
            "How to Choose the Right Planter for Your Plant", "how-to-choose-the-right-planter",
            "Matching container material and size to plant type — terracotta, ceramic, and glazed planters explained.",
            """
            ## Matching Planters to Plants

            The container you choose affects drainage, moisture retention, root temperature, and ultimately \
            how your plant performs. Here's a quick guide to matching material to plant type.

            ### Terracotta: Mediterranean and Drought-Tolerant Plants

            Unglazed terracotta is porous — it breathes. Moisture evaporates through the walls, which means \
            the root zone stays cooler and better-aerated. This is ideal for plants that hate wet feet: \
            cacti, succulents, lavender, rosemary, and most herbs.

            The trade-off: terracotta dries out faster, so you'll water more frequently in summer.

            ### Ceramic and Glazed: Tropical Foliage Plants

            Glazed planters retain moisture longer than terracotta, making them a better fit for tropical plants \
            that prefer consistent moisture — pothos, ferns, peace lilies, and fiddle-leaf figs.

            ### Size Matters

            Always match pot size to root mass. Overpotting — placing a small plant in a large pot — leaves \
            too much wet compost around the roots with nowhere to go. Move up one pot size at a time.

            A rough guide:
            - **Small (4 in):** succulents, herbs, cuttings
            - **Medium (6–8 in):** most houseplants
            - **Large (10 in+):** feature plants, shrubs, statement tropicals
            """,
            "PUBLISHED", Instant.now().minus(7, ChronoUnit.DAYS));
        linkArticleTag(article3Id, tagProductCare);

        // Draft article — no published_at, not yet linked to tags
        seedArticle(blogId, staffId,
            "Preparing Your Garden for Winter", "preparing-garden-for-winter",
            "Autumn tasks that set your garden up for a strong spring — cutting back, mulching, and storing tools.",
            """
            ## Preparing Your Garden Before the First Frost

            Autumn is the most productive time to spend in the garden — the work you do now pays dividends \
            for years. Here's what to tackle before the ground freezes.

            ### Cut Back Perennials

            Most perennials benefit from being cut back in autumn. Remove diseased foliage entirely; \
            healthy stems can be left as habitat for overwintering insects or cut back to 4–6 inches.

            Notable exceptions: ornamental grasses and seed-head plants (echinacea, rudbeckia) — \
            leave these standing for winter interest and wildlife.

            ### Mulch Tender Roots

            A generous layer of mulch (3–4 inches of straw, shredded leaves, or wood chip) insulates root \
            systems against freeze-thaw cycles. Apply after the first hard frost to avoid creating a warm \
            haven for rodents.

            ### Plant Spring Bulbs

            October and November are prime bulb-planting months for tulips, daffodils, alliums, and crocuses. \
            Plant when soil temperature drops below 50°F but before the ground freezes solid.

            ### Clean and Store Tools

            Scrub off soil, sharpen blade edges, and rub metal surfaces with a light coat of oil to prevent \
            rust over winter.
            """,
            "DRAFT", null);

        log.info("DevDataSeeder: seeded blog 'The Garden Journal' with 4 articles and 3 content tags");
    }

    private UUID seedArticle(UUID blogId, UUID authorId, String title, String handle,
                              String excerpt, String body, String status, Instant publishedAt) {
        UUID articleId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO content.articles
              (id, blog_id, title, handle, body, excerpt, author_id, author_name, status, published_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'Sam Staff', ?, ?)
            ON CONFLICT DO NOTHING
            """, articleId, blogId, title, handle, body, excerpt, authorId, status,
                publishedAt != null ? Timestamp.from(publishedAt) : null);
        return articleId;
    }

    private void linkArticleTag(UUID articleId, UUID tagId) {
        jdbc.update("""
            INSERT INTO content.article_content_tags (article_id, content_tag_id) VALUES (?, ?)
            ON CONFLICT DO NOTHING
            """, articleId, tagId);
    }

    // -------------------------------------------------------------------------
    // Reviews
    // -------------------------------------------------------------------------

    private void seedReviews(UUID customerUserId, List<UUID> productIds, List<UUID> variantProductIds) {
        // Carol's verified purchases: tomato(0), lavender(1), trowel(3), shears(4), watering can(5)
        insertReview(productIds.get(0), customerUserId, (short) 5,
            "Incredible variety mix",
            "The Brandywine and Cherokee Purple seeds germinated perfectly. Already seeing strong seedlings — very happy.",
            true);
        insertReview(productIds.get(1), customerUserId, (short) 4,
            "Lovely scent, takes patience",
            "Lavender is slow to germinate but once established it's beautiful. Great mix of varieties.",
            true);
        insertReview(productIds.get(3), customerUserId, (short) 5,
            "Best trowel I've owned",
            "Stainless steel, well-balanced, comfortable grip. Worth every penny for daily use.",
            true);
        insertReview(productIds.get(4), customerUserId, (short) 4,
            "Sharp and ergonomic",
            "The bypass mechanism makes clean cuts. Spring-load reduces fatigue on long pruning sessions.",
            true);
        insertReview(productIds.get(5), customerUserId, (short) 5,
            "Perfect for indoor plants",
            "The narrow spout lets me water right at the root zone without splashing. Love the compact size.",
            true);
        // Not a verified purchase — Carol browsed and bought ceramic planter separately
        insertReview(variantProductIds.get(1), customerUserId, (short) 3,
            "Nice design, shipping could improve",
            "The sage green color is gorgeous but one arrived with a small chip. Still usable, but worth noting.",
            false);

        log.info("DevDataSeeder: seeded 6 product reviews");
    }

    private void insertReview(UUID productId, UUID userId, short rating, String title, String body, boolean verifiedPurchase) {
        jdbc.update("""
            INSERT INTO catalog.product_reviews
              (id, product_id, user_id, rating, title, body, verified_purchase, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'PUBLISHED')
            ON CONFLICT DO NOTHING
            """, UUID.randomUUID(), productId, userId, rating, title, body, verifiedPurchase);
    }

    // -------------------------------------------------------------------------
    // Wishlist
    // -------------------------------------------------------------------------

    private void seedWishlist(UUID customerUserId, List<UUID> productIds, List<UUID> quoteOnlyProductIds) {
        UUID wishlistId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO catalog.wishlists (id, user_id) VALUES (?, ?)
            ON CONFLICT (user_id) DO NOTHING
            """, wishlistId, customerUserId);
        wishlistId = jdbc.queryForObject("SELECT id FROM catalog.wishlists WHERE user_id = ?", UUID.class, customerUserId);

        for (UUID pid : List.of(productIds.get(7), productIds.get(2), quoteOnlyProductIds.get(0))) {
            jdbc.update("""
                INSERT INTO catalog.wishlist_items (id, wishlist_id, product_id) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), wishlistId, pid);
        }

        log.info("DevDataSeeder: seeded wishlist for Carol with 3 items (Glazed Planter, Sunflower Mix, GFRC Trough)");
    }

    // -------------------------------------------------------------------------
    // Saved address
    // -------------------------------------------------------------------------

    private void seedCustomerAddress(UUID customerUserId) {
        jdbc.update("""
            INSERT INTO auth.addresses
              (id, user_id, first_name, last_name, address1, city, province, zip, country, is_default)
            VALUES (?, ?, 'Carol', 'Customer', '123 Garden St', 'Portland', 'OR', '97201', 'US', true)
            """, UUID.randomUUID(), customerUserId);

        log.info("DevDataSeeder: seeded saved address for Carol");
    }

    // -------------------------------------------------------------------------
    // Return requests (RMA)
    // -------------------------------------------------------------------------

    private void seedReturnRequests(UUID customerUserId) {
        UUID staffId = jdbc.queryForObject(
            "SELECT id FROM auth.users WHERE email = 'staff@garden.local'", UUID.class);

        // Return 1: PENDING — cracked watering can from fulfilled order 3
        UUID fulfilledOrderId = jdbc.queryForObject(
            "SELECT id FROM checkout.orders WHERE stripe_session_id = 'cs_test_seed_003'", UUID.class);
        UUID canItemId = jdbc.queryForObject("""
            SELECT oi.id FROM checkout.order_items oi
            JOIN catalog.product_variants pv ON pv.id = oi.variant_id
            WHERE oi.order_id = ? AND pv.sku = 'SKU-006'
            """, UUID.class, fulfilledOrderId);

        UUID return1Id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO checkout.return_requests
              (id, order_id, user_id, reason, notes, resolution, status)
            VALUES (?, ?, ?, 'DAMAGED', 'Watering can arrived with a cracked spout.', 'REFUND', 'PENDING')
            """, return1Id, fulfilledOrderId, customerUserId);
        jdbc.update("""
            INSERT INTO checkout.return_request_items (id, return_request_id, order_item_id, quantity)
            VALUES (?, ?, ?, 1)
            """, UUID.randomUUID(), return1Id, canItemId);

        // Return 2: COMPLETED — seeds not as described (refunded order 6 sunflowers)
        UUID refundedOrderId = jdbc.queryForObject(
            "SELECT id FROM checkout.orders WHERE stripe_session_id = 'cs_test_seed_006'", UUID.class);
        UUID sunflowerItemId = jdbc.queryForObject("""
            SELECT oi.id FROM checkout.order_items oi
            JOIN catalog.product_variants pv ON pv.id = oi.variant_id
            WHERE oi.order_id = ? AND pv.sku = 'SKU-003'
            """, UUID.class, refundedOrderId);

        UUID return2Id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO checkout.return_requests
              (id, order_id, user_id, reason, notes, resolution, status,
               staff_notes, resolved_by, resolved_at)
            VALUES (?, ?, ?, 'NOT_AS_DESCRIBED', 'Seeds did not match the variety description.',
                    'REFUND', 'COMPLETED',
                    'Full refund approved and processed.', ?,
                    clock_timestamp() - INTERVAL '5 days')
            """, return2Id, refundedOrderId, customerUserId, staffId);
        jdbc.update("""
            INSERT INTO checkout.return_request_items (id, return_request_id, order_item_id, quantity)
            VALUES (?, ?, ?, 3)
            """, UUID.randomUUID(), return2Id, sunflowerItemId);

        log.info("DevDataSeeder: seeded 2 return requests (1 PENDING, 1 COMPLETED)");
    }

    // -------------------------------------------------------------------------
    // Notification preferences
    // -------------------------------------------------------------------------

    private void seedNotificationPreferences(UUID customerUserId) {
        // Customer: all enabled except MARKETING
        for (String type : List.of(
                "ORDER_CONFIRMATION", "ORDER_SHIPPED", "ORDER_DELIVERED",
                "ORDER_CANCELLED", "QUOTE_UPDATE")) {
            jdbc.update("""
                INSERT INTO auth.notification_preferences (id, user_id, notification_type, enabled)
                VALUES (?, ?, ?, true)
                ON CONFLICT ON CONSTRAINT uq_notification_pref DO NOTHING
                """, UUID.randomUUID(), customerUserId, type);
        }
        jdbc.update("""
            INSERT INTO auth.notification_preferences (id, user_id, notification_type, enabled)
            VALUES (?, ?, 'MARKETING', false)
            ON CONFLICT ON CONSTRAINT uq_notification_pref DO NOTHING
            """, UUID.randomUUID(), customerUserId);

        // B2B manager: order + quote notifications only
        UUID managerId = jdbc.queryForObject(
            "SELECT id FROM auth.users WHERE email = 'b2b-manager@garden.local'", UUID.class);
        for (String type : List.of("ORDER_CONFIRMATION", "ORDER_SHIPPED", "QUOTE_UPDATE")) {
            jdbc.update("""
                INSERT INTO auth.notification_preferences (id, user_id, notification_type, enabled)
                VALUES (?, ?, ?, true)
                ON CONFLICT ON CONSTRAINT uq_notification_pref DO NOTHING
                """, UUID.randomUUID(), managerId, type);
        }

        log.info("DevDataSeeder: seeded notification preferences for customer and B2B manager");
    }

    // -------------------------------------------------------------------------
    // Order templates (saved carts for repeat B2B ordering)
    // -------------------------------------------------------------------------

    private void seedOrderTemplates(UUID customerUserId, List<UUID> productIds, List<UUID> variantProductIds) {
        // Template 1: Spring Seed Restock
        UUID template1Id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO checkout.order_templates (id, user_id, name) VALUES (?, ?, 'Spring Seed Restock')
            """, template1Id, customerUserId);
        insertOrderTemplateItem(template1Id, firstVariantOf(productIds.get(0)), 2); // tomato x2
        insertOrderTemplateItem(template1Id, firstVariantOf(productIds.get(1)), 1); // lavender x1
        insertOrderTemplateItem(template1Id, firstVariantOf(productIds.get(2)), 1); // sunflower x1

        // Template 2: Tool Kit Reorder
        UUID template2Id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO checkout.order_templates (id, user_id, name) VALUES (?, ?, 'Tool Kit Reorder')
            """, template2Id, customerUserId);
        insertOrderTemplateItem(template2Id, firstVariantOf(productIds.get(3)), 1); // trowel x1
        insertOrderTemplateItem(template2Id, firstVariantOf(productIds.get(4)), 1); // shears x1
        insertOrderTemplateItem(template2Id, variantIdBySku("SKU-G-M-GRN"), 1);    // gloves M/Forest Green x1

        log.info("DevDataSeeder: seeded 2 order templates for Carol");
    }

    private void insertOrderTemplateItem(UUID templateId, UUID variantId, int quantity) {
        jdbc.update("""
            INSERT INTO checkout.order_template_items (id, template_id, variant_id, quantity)
            VALUES (?, ?, ?, ?)
            """, UUID.randomUUID(), templateId, variantId, quantity);
    }

    // -------------------------------------------------------------------------
    // Newsletter subscribers
    // -------------------------------------------------------------------------

    private void seedNewsletterSubscribers() {
        for (String email : List.of(
                "customer@garden.local",
                "newsletter-fan@example.com",
                "greenthumb@example.com",
                "gardenclub@example.org")) {
            jdbc.update("""
                INSERT INTO marketing.newsletter_subscribers (id, email, source)
                VALUES (?, ?, 'storefront')
                ON CONFLICT (email) DO NOTHING
                """, UUID.randomUUID(), email);
        }
        // One unsubscribed subscriber
        jdbc.update("""
            INSERT INTO marketing.newsletter_subscribers (id, email, source, unsubscribed_at)
            VALUES (?, 'opted-out@example.com', 'storefront',
                    clock_timestamp() - INTERVAL '10 days')
            ON CONFLICT (email) DO NOTHING
            """, UUID.randomUUID());

        log.info("DevDataSeeder: seeded 5 newsletter subscribers (4 active, 1 unsubscribed)");
    }

    // -------------------------------------------------------------------------
    // Pending-approval B2B order
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Extended products (pagination + DRAFT/ARCHIVED status coverage)
    // -------------------------------------------------------------------------

    private List<UUID> seedExtendedProducts() {
        record ProductSeed(String title, String handle, String vendor, String type,
                           String sku, BigDecimal price, String status, String description) {}

        var products = List.of(
            new ProductSeed("Seed Starting Mix", "seed-starting-mix", "Soil Co",
                "Soil & Amendments", "SKU-009", new BigDecimal("12.99"), "ACTIVE",
                """
                ## Seed Starting Mix

                A fine-textured, soilless blend designed for germinating seeds and rooting cuttings.
                Lightweight and free-draining, with added perlite and vermiculite to keep the root
                zone aerated and to prevent damping-off fungus.

                ### Highlights
                - **pH:** 5.5–6.5 (slightly acidic — ideal for most vegetable seedlings)
                - **Mix:** coco coir, perlite, vermiculite, wetting agent
                - **Weight:** 5 L bag (approx. 1.5 kg)
                - **Suitable for:** trays, plug cells, and propagation pots up to 4 inches

                ### Usage
                Fill seed trays to within ½ inch of the rim. Pre-moisten before sowing.
                Do not compress — seeds need contact, not pressure.
                """),
            new ProductSeed("Raised Bed Soil Mix", "raised-bed-soil-mix", "Soil Co",
                "Soil & Amendments", "SKU-010", new BigDecimal("24.99"), "ACTIVE",
                """
                ## Raised Bed Soil Mix

                Premium blended growing medium for raised beds and large containers.
                Combines screened topsoil, aged compost, and coarse perlite in a 40:40:20 ratio —
                the classic "Mel's mix" balance that supports deep root development and excellent drainage.

                ### Highlights
                - **Bag size:** 20 L
                - **Nutrients:** balanced N-P-K from compost; feeds plants for the first 6–8 weeks
                - **Drainage:** perlite content prevents compaction even after repeated watering
                - **Suitable for:** all vegetables, fruits, and cut flowers in raised beds or large planters

                ### Tip
                Top-dress with 2–3 cm of fresh compost each spring to replenish nutrients.
                """),
            new ProductSeed("Garden Kneeling Pad", "garden-kneeling-pad", "Tools Co",
                "Tools", "SKU-011", new BigDecimal("16.99"), "ACTIVE",
                """
                ## Garden Kneeling Pad

                High-density EVA foam pad, 1.5 inches thick and moisture-resistant.
                Cushions knees and wrists through hours of planting, weeding, and harvesting.
                The contoured non-slip base grips the ground on slopes and in damp conditions.

                ### Specifications
                - **Dimensions:** 18 × 11 × 1.5 inches
                - **Material:** closed-cell EVA foam (waterproof, easy to rinse clean)
                - **Colours:** garden green / charcoal grey
                - **Weight:** 280 g

                ### Best For
                - Row planting and transplanting
                - Container potting on patios
                - Bulb planting in autumn
                """),
            new ProductSeed("Plant Labels (Pack of 50)", "plant-labels", "Tools Co",
                "Tools", "SKU-012", new BigDecimal("4.99"), "ACTIVE",
                """
                ## Plant Labels — Pack of 50

                Reusable white plastic labels with a smooth writing surface that works with pencil,
                permanent marker, or plant label pen. Write it, wipe it off, and reuse season after
                season without the label degrading.

                ### Specifications
                - **Dimensions:** 15 cm × 2 cm
                - **Material:** UV-stabilised polypropylene (won't yellow or crack)
                - **Pack:** 50 labels

                ### Best For
                - Seed trays and propagation cells
                - Pot identification in the greenhouse
                - Perennial bed markers
                """),
            new ProductSeed("Drip Irrigation Starter Kit", "drip-irrigation-starter-kit",
                "Irrigation Co", "Irrigation", "SKU-013", new BigDecimal("39.99"), "ACTIVE",
                """
                ## Drip Irrigation Starter Kit

                A complete entry-level drip system for one raised bed or a cluster of large containers.
                Delivers water directly to the root zone — conserves up to 50% more water than overhead
                watering, and keeps foliage dry to reduce disease risk.

                ### Kit Contents
                - 10 m × 16 mm main distribution hose
                - 10 × adjustable drip emitters (0.5–8 L/h)
                - 10 × 3 mm micro-tube adapters
                - 5 × ground stakes
                - 1 × hose-tap connector with filter
                - Assembly guide

                ### Compatible With
                - Standard ½-inch garden taps
                - Gravity-fed water butts (min. 1 m head pressure)
                - Most automatic timer units (not included)
                """),
            new ProductSeed("Adjustable Hose Nozzle", "adjustable-hose-nozzle",
                "Irrigation Co", "Irrigation", "SKU-014", new BigDecimal("14.99"), "ACTIVE",
                """
                ## Adjustable Hose Nozzle

                Eight-pattern brass nozzle — twist the front collar to switch between fine mist,
                flat spray, jet, cone, centre, full, shower, and soaker settings.
                Comfortable rubber grip and thumb-control flow valve.

                ### Specifications
                - **Body:** zinc alloy with rubber over-mould
                - **Connection:** standard ½-inch hose fitting
                - **Patterns:** 8 (mist, flat, jet, cone, centre, full, shower, soaker)
                - **Max pressure:** 8 bar

                ### Best For
                - Watering seedlings (mist setting)
                - Washing down tools and pots (jet)
                - General garden watering (shower/flat)
                """),
            new ProductSeed("Neem Oil Concentrate", "neem-oil-concentrate",
                "Garden Co", "Pest Control", "SKU-015", new BigDecimal("11.99"), "ACTIVE",
                """
                ## Neem Oil Concentrate

                Cold-pressed neem oil for organic pest and disease control across the whole garden.
                Effective against aphids, spider mites, whitefly, powdery mildew, and black spot.
                Safe for beneficial insects when applied correctly (evening spray, avoid open flowers).

                ### Directions
                Dilute 2 ml per litre of water with a few drops of dish soap as an emulsifier.
                Apply as a thorough foliar spray in the evening. Repeat every 7–14 days or after rain.

                ### Highlights
                - **Concentration:** 100% cold-pressed (3000 ppm azadirachtin)
                - **Bottle:** 250 ml — makes up to 125 L of spray solution
                - **Certified:** OMRI-listed for organic use
                - **Safe on:** vegetables, fruit, ornamentals, and houseplants
                """),
            new ProductSeed("Organic Tomato Fertiliser", "organic-tomato-fertiliser",
                "Garden Co", "Fertilisers", "SKU-016", new BigDecimal("16.99"), "ACTIVE",
                """
                ## Organic Tomato Fertiliser (1 kg)

                High-potassium slow-release granules for tomatoes, peppers, aubergines, and other
                fruiting crops. The potassium-forward formula encourages flower set, fruit development,
                and deep colour — without the leafy overgrowth that high-nitrogen feeds produce.

                ### Analysis (NPK)
                - **N** 4 — **P** 3 — **K** 8

                ### Usage
                Apply 50 g per plant at transplanting, then top-dress every 4 weeks.
                Water in after application.

                ### Highlights
                - **Certified organic** — safe for edible crops
                - **Slow release** — feeds for 6–8 weeks per application
                - **1 kg bag** — sufficient for 20 plants per season
                """),
            new ProductSeed("Bamboo Grow Stakes (Pack of 20)", "bamboo-grow-stakes",
                "Tools Co", "Tools", "SKU-017", new BigDecimal("6.99"), "DRAFT",
                """
                ## Bamboo Grow Stakes — Pack of 20

                Natural bamboo stakes for supporting climbing and tall plants — runner beans,
                sweet peas, dahlias, and tomatoes. Renewable, biodegradable, and stronger per
                weight than many synthetic alternatives.

                ### Specifications
                - **Length:** 120 cm (4 ft)
                - **Diameter:** 10–12 mm
                - **Pack:** 20 stakes
                - **Finish:** natural cane — untreated

                > **Note:** This product listing is currently under review and not yet live on the storefront.
                """),
            new ProductSeed("Copper Watering Can 5L", "copper-watering-can-5l",
                "Tools Co", "Tools", "SKU-018", new BigDecimal("49.99"), "ARCHIVED",
                """
                ## Copper Watering Can 5L

                Solid copper 5-litre watering can with a long-reach spout and detachable brass rose.
                Develops a natural verdigris patina over time.

                > **Archived.** This model has been discontinued and replaced by our updated
                stainless-steel range. Stock is no longer available.
                """)
        );

        return products.stream().map(p -> {
            UUID productId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO catalog.products (id, title, handle, vendor, product_type, description, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (handle) DO NOTHING
                """, productId, p.title(), p.handle(), p.vendor(), p.type(), p.description(), p.status());

            productId = jdbc.queryForObject(
                "SELECT id FROM catalog.products WHERE handle = ?", UUID.class, p.handle());

            UUID variantId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO catalog.product_variants
                  (id, product_id, title, sku, price, fulfillment_type, inventory_policy, lead_time_days)
                VALUES (?, ?, 'Default', ?, ?, 'IN_STOCK', 'DENY', 0)
                ON CONFLICT (sku) DO NOTHING
                """, variantId, productId, p.sku(), p.price());

            // Inventory item (only for ACTIVE products — DRAFT/ARCHIVED skipped later)
            variantId = jdbc.queryForObject(
                "SELECT id FROM catalog.product_variants WHERE sku = ?", UUID.class, p.sku());
            Long itemCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory.inventory_items WHERE variant_id = ?", Long.class, variantId);
            if (itemCount == 0) {
                jdbc.update("""
                    INSERT INTO inventory.inventory_items (id, variant_id, requires_shipping)
                    VALUES (?, ?, true)
                    """, UUID.randomUUID(), variantId);
            }

            return productId;
        }).toList();
    }

    private void assignExtendedProductsToCollections(List<UUID> collectionIds, List<UUID> extendedIds) {
        // extendedIds[0..7] = ACTIVE products (indices match the list in seedExtendedProducts)
        // [0] seed-starting-mix, [1] raised-bed-soil-mix, [7] organic-tomato-fertiliser → Seeds & Bulbs
        // [2] kneeling-pad, [3] plant-labels, [4] drip-irrigation, [5] hose-nozzle, [6] neem-oil → Tools
        // [8] bamboo-grow-stakes (DRAFT), [9] copper-watering-can (ARCHIVED) — not assigned

        int startPos = jdbc.queryForObject(
            "SELECT COALESCE(MAX(position), 0) FROM catalog.collection_products WHERE collection_id = ?",
            Integer.class, collectionIds.get(0));
        for (UUID pid : List.of(extendedIds.get(0), extendedIds.get(1), extendedIds.get(7))) {
            startPos++;
            jdbc.update("""
                INSERT INTO catalog.collection_products (id, collection_id, product_id, position)
                VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), collectionIds.get(0), pid, startPos);
        }

        startPos = jdbc.queryForObject(
            "SELECT COALESCE(MAX(position), 0) FROM catalog.collection_products WHERE collection_id = ?",
            Integer.class, collectionIds.get(1));
        for (UUID pid : List.of(
                extendedIds.get(2), extendedIds.get(3), extendedIds.get(4),
                extendedIds.get(5), extendedIds.get(6))) {
            startPos++;
            jdbc.update("""
                INSERT INTO catalog.collection_products (id, collection_id, product_id, position)
                VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), collectionIds.get(1), pid, startPos);
        }
    }

    private void seedExtendedImages(List<UUID> extendedProductIds) {
        Map<String, List<String>> imagesByHandle = new LinkedHashMap<>();
        imagesByHandle.put("seed-starting-mix",          List.of("Seed starting mix bag", "Seed tray filled with starting mix"));
        imagesByHandle.put("raised-bed-soil-mix",         List.of("Raised bed soil mix bag", "Raised bed filled with soil mix"));
        imagesByHandle.put("garden-kneeling-pad",         List.of("Garden kneeling pad in green", "Kneeling pad in use in garden"));
        imagesByHandle.put("plant-labels",                List.of("Pack of 50 white plant labels"));
        imagesByHandle.put("drip-irrigation-starter-kit", List.of("Drip irrigation kit components", "Drip emitter close-up"));
        imagesByHandle.put("adjustable-hose-nozzle",      List.of("Eight-pattern hose nozzle"));
        imagesByHandle.put("neem-oil-concentrate",        List.of("Neem oil concentrate bottle", "Foliar spray application"));
        imagesByHandle.put("organic-tomato-fertiliser",   List.of("Organic tomato fertiliser bag", "Fertiliser granules close-up"));
        imagesByHandle.put("bamboo-grow-stakes",          List.of("Bundle of bamboo grow stakes"));
        imagesByHandle.put("copper-watering-can-5l",      List.of("Copper watering can 5L"));

        HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

        for (var entry : imagesByHandle.entrySet()) {
            String handle = entry.getKey();
            List<String> altTexts = entry.getValue();

            UUID productId = jdbc.queryForObject(
                "SELECT id FROM catalog.products WHERE handle = ?", UUID.class, handle);

            Long existingImages = jdbc.queryForObject(
                "SELECT COUNT(*) FROM catalog.product_images WHERE product_id = ?", Long.class, productId);
            if (existingImages > 0) continue;

            UUID featuredImageId = null;
            for (int i = 0; i < altTexts.size(); i++) {
                String filename = handle + "-" + (i + 1) + ".jpg";
                String objectKey = "products/" + filename;
                String picsumUrl = "https://picsum.photos/seed/" + handle + "-" + (i + 1) + "/800/600";

                byte[] imageBytes = downloadImage(http, picsumUrl, handle, i);
                storageService.store(objectKey, "image/jpeg",
                    new ByteArrayInputStream(imageBytes), imageBytes.length);

                UUID blobId = UUID.randomUUID();
                jdbc.update("""
                    INSERT INTO storage.blob_objects (id, key, filename, content_type, size, alt, width, height)
                    VALUES (?, ?, ?, 'image/jpeg', ?, ?, 800, 600)
                    """, blobId, objectKey, filename, imageBytes.length, altTexts.get(i));

                UUID imageId = UUID.randomUUID();
                jdbc.update("""
                    INSERT INTO catalog.product_images (id, product_id, blob_id, alt_text, position)
                    VALUES (?, ?, ?, ?, ?)
                    """, imageId, productId, blobId, altTexts.get(i), i + 1);

                if (i == 0) featuredImageId = imageId;
            }

            jdbc.update("UPDATE catalog.products SET featured_image_id = ? WHERE id = ?",
                featuredImageId, productId);
        }
        log.info("DevDataSeeder: seeded images for extended products");
    }

    private void seedExtendedInventory(List<UUID> extendedProductIds) {
        UUID locationId = jdbc.queryForObject(
            "SELECT id FROM inventory.locations WHERE name = 'Main Warehouse'", UUID.class);

        // Handles and their target stock levels (null = skip inventory — DRAFT/ARCHIVED)
        record StockSeed(String sku, Integer qty) {}
        var stocks = List.of(
            new StockSeed("SKU-009", 50),   // seed-starting-mix
            new StockSeed("SKU-010", 50),   // raised-bed-soil-mix
            new StockSeed("SKU-011", 50),   // kneeling-pad
            new StockSeed("SKU-012", 50),   // plant-labels
            new StockSeed("SKU-013", 50),   // drip-irrigation-starter-kit
            new StockSeed("SKU-014", 0),    // adjustable-hose-nozzle — out of stock
            new StockSeed("SKU-015", 3),    // neem-oil-concentrate — low stock
            new StockSeed("SKU-016", 50)    // organic-tomato-fertiliser
            // SKU-017 (DRAFT) and SKU-018 (ARCHIVED) intentionally excluded
        );

        for (var s : stocks) {
            UUID itemId = jdbc.queryForObject("""
                SELECT ii.id FROM inventory.inventory_items ii
                JOIN catalog.product_variants pv ON pv.id = ii.variant_id
                WHERE pv.sku = ?
                """, UUID.class, s.sku());

            Long existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory.inventory_levels WHERE inventory_item_id = ? AND location_id = ?",
                Long.class, itemId, locationId);
            if (existing == 0) {
                jdbc.update("""
                    INSERT INTO inventory.inventory_levels
                      (id, inventory_item_id, location_id, quantity_on_hand, quantity_committed)
                    VALUES (?, ?, ?, ?, 0)
                    """, UUID.randomUUID(), itemId, locationId, s.qty());
            }
        }

        log.info("DevDataSeeder: seeded extended inventory (including out-of-stock and low-stock items)");
    }

    // -------------------------------------------------------------------------
    // Additional customers
    // -------------------------------------------------------------------------

    private List<UUID> seedAdditionalCustomers() {
        record CustomerSeed(String email, String firstName, String lastName) {}
        var customers = List.of(
            new CustomerSeed("alice@garden.local", "Alice", "Buyer"),
            new CustomerSeed("bob@garden.local",   "Bob",   "Shopper")
        );

        String hash = passwordEncoder.encode("password");
        List<UUID> ids = new ArrayList<>();

        for (var c : customers) {
            UUID userId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO auth.users (id, email, first_name, last_name, status, email_verified_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', clock_timestamp())
                ON CONFLICT (email) DO NOTHING
                """, userId, c.email(), c.firstName(), c.lastName());
            userId = jdbc.queryForObject(
                "SELECT id FROM auth.users WHERE email = ?", UUID.class, c.email());
            jdbc.update("""
                INSERT INTO auth.identities (id, user_id, provider, account_id, password_hash)
                VALUES (?, ?, 'CREDENTIALS', ?, ?)
                ON CONFLICT (provider, account_id) DO NOTHING
                """, UUID.randomUUID(), userId, userId.toString(), hash);
            jdbc.update("""
                INSERT INTO auth.user_roles (user_id, role_id)
                SELECT ?, r.id FROM auth.roles r WHERE r.name = 'CUSTOMER'
                ON CONFLICT DO NOTHING
                """, userId);
            ids.add(userId);
        }

        log.info("DevDataSeeder: seeded additional customers (alice, bob) with password='password'");
        return ids;
    }

    // -------------------------------------------------------------------------
    // Additional orders (discounted order, PARTIALLY_FULFILLED)
    // -------------------------------------------------------------------------

    private void seedAdditionalOrders(UUID aliceId, UUID bobId,
                                       List<UUID> productIds, List<UUID> variantProductIds) {
        String aliceAddr = """
            {"firstName":"Alice","lastName":"Buyer","address1":"42 Fern Lane",
             "city":"Seattle","province":"WA","zip":"98101","country":"US"}
            """.strip();
        String bobAddr = """
            {"firstName":"Bob","lastName":"Shopper","address1":"7 Oak Street",
             "city":"Denver","province":"CO","zip":"80201","country":"US"}
            """.strip();

        UUID trowelVariantId    = firstVariantOf(productIds.get(3));
        UUID canVariantId       = firstVariantOf(productIds.get(5));
        UUID sunflowerVariantId = firstVariantOf(productIds.get(2));
        UUID lavenderVariantId  = firstVariantOf(productIds.get(1));
        UUID glovesVariantId    = firstVariantOf(variantProductIds.get(0));
        UUID terracottaVariantId = firstVariantOf(productIds.get(6));

        UUID welcome10Id = jdbc.queryForObject(
            "SELECT id FROM checkout.discounts WHERE UPPER(code) = 'WELCOME10'", UUID.class);

        // Alice order 1: PAID with WELCOME10 discount applied
        // trowel x2 ($25.98) + watering-can x1 ($18.50) = $44.48 → -10% ($4.45) → $40.03
        UUID aliceOrder1Id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO checkout.orders
              (id, user_id, status, stripe_session_id, stripe_payment_intent_id,
               total_amount, currency, shipping_address, discount_id, discount_amount,
               created_at, updated_at)
            VALUES (?, ?, 'PAID', 'cs_test_seed_alice_001', 'pi_test_seed_alice_001',
                    40.03, 'usd', ?::jsonb, ?, 4.45, ?, ?)
            """, aliceOrder1Id, aliceId, aliceAddr, welcome10Id,
                Timestamp.from(Instant.now().minus(8, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(8, ChronoUnit.DAYS)));
        insertOrderItem(aliceOrder1Id, trowelVariantId, 2, new BigDecimal("12.99"));
        insertOrderItem(aliceOrder1Id, canVariantId,    1, new BigDecimal("18.50"));
        insertOrderEvent(aliceOrder1Id, "ORDER_PLACED",     "Order placed");
        insertOrderEvent(aliceOrder1Id, "PAYMENT_RECEIVED", "Payment received via Stripe");

        // Increment used_count on WELCOME10
        jdbc.update("UPDATE checkout.discounts SET used_count = used_count + 1 WHERE id = ?",
            welcome10Id);

        // Alice order 2: PARTIALLY_FULFILLED — sunflower x2 + lavender x1, first sunflower shipped
        UUID aliceOrder2Id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO checkout.orders
              (id, user_id, status, stripe_session_id, stripe_payment_intent_id,
               total_amount, currency, shipping_address, created_at, updated_at)
            VALUES (?, ?, 'PARTIALLY_FULFILLED', 'cs_test_seed_alice_002', 'pi_test_seed_alice_002',
                    17.97, 'usd', ?::jsonb, ?, ?)
            """, aliceOrder2Id, aliceId, aliceAddr,
                Timestamp.from(Instant.now().minus(4, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(3, ChronoUnit.DAYS)));
        UUID aliceSunflowerItemId = insertOrderItem(aliceOrder2Id, sunflowerVariantId, 2, new BigDecimal("4.49"));
        insertOrderItem(aliceOrder2Id, lavenderVariantId, 1, new BigDecimal("8.99"));
        insertOrderEvent(aliceOrder2Id, "ORDER_PLACED",     "Order placed");
        insertOrderEvent(aliceOrder2Id, "PAYMENT_RECEIVED", "Payment received via Stripe");
        insertOrderEvent(aliceOrder2Id, "FULFILLMENT_CREATED", "Partial shipment — sunflower seeds dispatched");

        UUID partialFulfillmentId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO checkout.fulfillments
              (id, order_id, status, tracking_number, tracking_company, tracking_url)
            VALUES (?, ?, 'SHIPPED', 'UPSTRK9900001', 'UPS',
                    'https://www.ups.com/track?tracknum=UPSTRK9900001')
            """, partialFulfillmentId, aliceOrder2Id);
        jdbc.update("""
            INSERT INTO checkout.fulfillment_items (id, fulfillment_id, order_item_id, quantity)
            VALUES (?, ?, ?, 1)
            """, UUID.randomUUID(), partialFulfillmentId, aliceSunflowerItemId);

        // Bob order 1: PAID — gloves (M/Forest Green) x1 + terracotta pot x1
        UUID bobOrder1Id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO checkout.orders
              (id, user_id, status, stripe_session_id, stripe_payment_intent_id,
               total_amount, currency, shipping_address, created_at, updated_at)
            VALUES (?, ?, 'PAID', 'cs_test_seed_bob_001', 'pi_test_seed_bob_001',
                    24.98, 'usd', ?::jsonb, ?, ?)
            """, bobOrder1Id, bobId, bobAddr,
                Timestamp.from(Instant.now().minus(12, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(12, ChronoUnit.DAYS)));
        insertOrderItem(bobOrder1Id, glovesVariantId,    1, new BigDecimal("14.99"));
        insertOrderItem(bobOrder1Id, terracottaVariantId, 1, new BigDecimal("9.99"));
        insertOrderEvent(bobOrder1Id, "ORDER_PLACED",     "Order placed");
        insertOrderEvent(bobOrder1Id, "PAYMENT_RECEIVED", "Payment received via Stripe");

        log.info("DevDataSeeder: seeded 3 additional orders (Alice ×2 incl. discounted+partial, Bob ×1)");
    }

    // -------------------------------------------------------------------------
    // Additional return requests (APPROVED + REJECTED status coverage)
    // -------------------------------------------------------------------------

    private void seedAdditionalReturnRequests(UUID aliceId, UUID bobId) {
        UUID staffId = jdbc.queryForObject(
            "SELECT id FROM auth.users WHERE email = 'staff@garden.local'", UUID.class);

        // APPROVED: Alice returns 1 trowel from her discounted order — wrong item, exchange
        UUID aliceOrderId = jdbc.queryForObject(
            "SELECT id FROM checkout.orders WHERE stripe_session_id = 'cs_test_seed_alice_001'",
            UUID.class);
        UUID trowelItemId = jdbc.queryForObject("""
            SELECT oi.id FROM checkout.order_items oi
            JOIN catalog.product_variants pv ON pv.id = oi.variant_id
            WHERE oi.order_id = ? AND pv.sku = 'SKU-004'
            """, UUID.class, aliceOrderId);

        UUID return3Id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO checkout.return_requests
              (id, order_id, user_id, reason, notes, resolution, status,
               staff_notes, resolved_by, resolved_at)
            VALUES (?, ?, ?, 'WRONG_ITEM',
                    'Received the 10-inch trowel — I ordered the 12-inch.',
                    'EXCHANGE', 'APPROVED',
                    'Correct size dispatched as replacement. No return of original required.',
                    ?, clock_timestamp() - INTERVAL '5 days')
            """, return3Id, aliceOrderId, aliceId, staffId);
        jdbc.update("""
            INSERT INTO checkout.return_request_items (id, return_request_id, order_item_id, quantity)
            VALUES (?, ?, ?, 1)
            """, UUID.randomUUID(), return3Id, trowelItemId);

        // REJECTED: Bob claims gloves are damaged — staff rejects after reviewing dispatch photo
        UUID bobOrderId = jdbc.queryForObject(
            "SELECT id FROM checkout.orders WHERE stripe_session_id = 'cs_test_seed_bob_001'",
            UUID.class);
        UUID glovesItemId = jdbc.queryForObject("""
            SELECT oi.id FROM checkout.order_items oi
            JOIN catalog.product_variants pv ON pv.id = oi.variant_id
            WHERE oi.order_id = ? AND pv.sku = 'SKU-G-M-GRN'
            """, UUID.class, bobOrderId);

        UUID return4Id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO checkout.return_requests
              (id, order_id, user_id, reason, notes, resolution, status,
               staff_notes, resolved_by, resolved_at)
            VALUES (?, ?, ?, 'DAMAGED',
                    'Gloves arrived with a torn seam on the right thumb.',
                    'REFUND', 'REJECTED',
                    'Dispatch photo shows gloves in perfect condition. Claim declined — signs of use damage.',
                    ?, clock_timestamp() - INTERVAL '2 days')
            """, return4Id, bobOrderId, bobId, staffId);
        jdbc.update("""
            INSERT INTO checkout.return_request_items (id, return_request_id, order_item_id, quantity)
            VALUES (?, ?, ?, 1)
            """, UUID.randomUUID(), return4Id, glovesItemId);

        log.info("DevDataSeeder: seeded 2 additional return requests (APPROVED exchange, REJECTED refund)");
    }

    // -------------------------------------------------------------------------
    // Additional B2B quotes (REJECTED + EXPIRED coverage)
    // -------------------------------------------------------------------------

    private void seedAdditionalQuotes() {
        UUID companyId = jdbc.queryForObject(
            "SELECT id FROM b2b.companies WHERE name = 'Green Thumb Nurseries LLC'", UUID.class);
        UUID ownerUserId = jdbc.queryForObject(
            "SELECT id FROM auth.users WHERE email = 'customer@garden.local'", UUID.class);

        UUID cedarVid = variantIdBySku("SKU-QO-003");
        UUID fountainVid = variantIdBySku("SKU-QO-004");

        String addr = "456 Bloom Ave"; String city = "Portland";
        String state = "OR"; String zip = "97202"; String ctry = "US";

        // Quote 5: REJECTED — staff reviewed and declined the price expectation
        UUID quote5Id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO quote.quote_requests
              (id, user_id, company_id, status,
               delivery_address_line1, delivery_city, delivery_state,
               delivery_postal_code, delivery_country,
               customer_notes, staff_notes, created_at, updated_at)
            VALUES (?, ?, ?, 'REJECTED', ?, ?, ?, ?, ?,
                    'Looking for a price closer to $800 for the cedar bed.',
                    'Requested price is below our cost — quote declined.',
                    ?, ?)
            """, quote5Id, ownerUserId, companyId,
                addr, city, state, zip, ctry,
                Timestamp.from(Instant.now().minus(25, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(23, ChronoUnit.DAYS)));
        insertQuoteItem(quote5Id, cedarVid,
            "Custom Cedar Raised Garden Bed — 4×8 ft, standard height", 1, null);

        // Quote 6: EXPIRED — was sent but customer never responded
        UUID quote6Id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO quote.quote_requests
              (id, user_id, company_id, status,
               delivery_address_line1, delivery_city, delivery_state,
               delivery_postal_code, delivery_country,
               staff_notes, expires_at, created_at, updated_at)
            VALUES (?, ?, ?, 'EXPIRED', ?, ?, ?, ?, ?,
                    'Tiered-basin fountain, charcoal finish. No response from customer.',
                    ?, ?, ?)
            """, quote6Id, ownerUserId, companyId,
                addr, city, state, zip, ctry,
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(45, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(31, ChronoUnit.DAYS)));
        insertQuoteItem(quote6Id, fountainVid,
            "Cast Stone Fountain — Tiered Classic, charcoal, 36in basin", 1, null);

        log.info("DevDataSeeder: seeded 2 additional quotes (REJECTED, EXPIRED)");
    }

    // -------------------------------------------------------------------------
    // Company approval rules
    // -------------------------------------------------------------------------

    private void seedCompanyApprovalRules() {
        UUID companyId = jdbc.queryForObject(
            "SELECT id FROM b2b.companies WHERE name = 'Green Thumb Nurseries LLC'", UUID.class);

        Long existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM b2b.company_approval_rules WHERE company_id = ?", Long.class, companyId);
        if (existing > 0) return;

        jdbc.update("""
            INSERT INTO b2b.company_approval_rules
              (id, company_id, name, threshold_amount, required_role, is_active)
            VALUES (?, ?, 'Large-order approval', 500.00, 'MANAGER', true)
            """, UUID.randomUUID(), companyId);

        log.info("DevDataSeeder: seeded company approval rule (orders >= $500 require MANAGER)");
    }

    // -------------------------------------------------------------------------
    // Company departments
    // -------------------------------------------------------------------------

    private void seedDepartments() {
        UUID companyId = jdbc.queryForObject(
            "SELECT id FROM b2b.companies WHERE name = 'Green Thumb Nurseries LLC'", UUID.class);

        Long existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM b2b.departments WHERE company_id = ?", Long.class, companyId);
        if (existing > 0) return;

        UUID procurementId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO b2b.departments (id, company_id, name) VALUES (?, ?, 'Procurement')
            """, procurementId, companyId);

        UUID retailId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO b2b.departments (id, company_id, name) VALUES (?, ?, 'Retail')
            """, retailId, companyId);

        // Assign B2B manager to Procurement
        UUID managerId = jdbc.queryForObject(
            "SELECT id FROM auth.users WHERE email = 'b2b-manager@garden.local'", UUID.class);
        jdbc.update("""
            UPDATE b2b.company_memberships SET department_id = ?
            WHERE user_id = ? AND company_id = ?
            """, procurementId, managerId, companyId);

        log.info("DevDataSeeder: seeded 2 departments (Procurement, Retail); manager assigned to Procurement");
    }

    // -------------------------------------------------------------------------
    // Inventory transactions (RECEIVED, SOLD, DAMAGED, ADJUSTED)
    // -------------------------------------------------------------------------

    private void seedInventoryTransactions(List<UUID> productIds, List<UUID> variantProductIds) {
        UUID locationId = jdbc.queryForObject(
            "SELECT id FROM inventory.locations WHERE name = 'Main Warehouse'", UUID.class);

        Long existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM inventory.inventory_transactions WHERE location_id = ?",
            Long.class, locationId);
        if (existing > 0) return;

        record TxSeed(String sku, int qty, String reason, String note) {}
        var txns = List.of(
            // Trowel: initial receive, then units sold
            new TxSeed("SKU-004", 100, "RECEIVED",  "Initial warehouse receipt"),
            new TxSeed("SKU-004",  -50, "SOLD",     "Units allocated to fulfilled orders"),
            // Shears: receive, damaged write-off
            new TxSeed("SKU-005", 80,  "RECEIVED",  "Initial warehouse receipt"),
            new TxSeed("SKU-005",  -5,  "DAMAGED",  "Blade defect — batch 22B, scrapped"),
            // Tomato seeds: receive, cycle-count adjustment
            new TxSeed("SKU-001", 200, "RECEIVED",  "Spring stock arrival"),
            new TxSeed("SKU-001",   3,  "ADJUSTED", "Cycle count — found loose units in bin"),
            // Lavender: receive, sold
            new TxSeed("SKU-002", 150, "RECEIVED",  "Spring stock arrival"),
            new TxSeed("SKU-002",  -80, "SOLD",     "Units allocated to fulfilled orders")
        );

        for (var t : txns) {
            UUID itemId = jdbc.queryForObject("""
                SELECT ii.id FROM inventory.inventory_items ii
                JOIN catalog.product_variants pv ON pv.id = ii.variant_id
                WHERE pv.sku = ?
                """, UUID.class, t.sku());
            jdbc.update("""
                INSERT INTO inventory.inventory_transactions
                  (id, inventory_item_id, location_id, quantity, reason, note)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), itemId, locationId, t.qty(), t.reason(), t.note());
        }

        log.info("DevDataSeeder: seeded {} inventory transactions", txns.size());
    }

    // -------------------------------------------------------------------------
    // Additional reviews (1-star, 2-star, HIDDEN)
    // -------------------------------------------------------------------------

    private void seedAdditionalReviews(UUID aliceId, UUID bobId,
                                        List<UUID> productIds, List<UUID> variantProductIds) {
        // Alice: 1-star on sunflower mix (verified purchase — she ordered it)
        insertExtendedReview(productIds.get(2), aliceId, (short) 1,
            "Very disappointing germination rate",
            "Barely a quarter of the seeds germinated despite following the instructions exactly. " +
            "Expected much better from a premium seed mix.",
            true, "PUBLISHED");

        // Bob: 2-star on lavender (not a verified purchase)
        insertExtendedReview(productIds.get(1), bobId, (short) 2,
            "Slow and sparse germination",
            "Only about a third of the seeds sprouted after three weeks. " +
            "Lavender is notoriously slow but this felt below average even for that.",
            false, "PUBLISHED");

        // B2B member: 1-star on glazed planter — moderated HIDDEN (suspected competitor post)
        UUID memberId = jdbc.queryForObject(
            "SELECT id FROM auth.users WHERE email = 'b2b-member@garden.local'", UUID.class);
        insertExtendedReview(productIds.get(7), memberId, (short) 1,
            "Terrible quality",
            "Do not buy — check out [competitor] instead. Much better prices.",
            false, "HIDDEN");

        log.info("DevDataSeeder: seeded 3 additional reviews (1-star, 2-star, HIDDEN)");
    }

    private void insertExtendedReview(UUID productId, UUID userId, short rating,
                                       String title, String body,
                                       boolean verifiedPurchase, String status) {
        jdbc.update("""
            INSERT INTO catalog.product_reviews
              (id, product_id, user_id, rating, title, body, verified_purchase, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """, UUID.randomUUID(), productId, userId, rating, title, body, verifiedPurchase, status);
    }

    private void seedPendingApprovalOrder() {
        UUID memberId = jdbc.queryForObject(
            "SELECT id FROM auth.users WHERE email = 'b2b-member@garden.local'", UUID.class);
        UUID companyId = jdbc.queryForObject(
            "SELECT id FROM b2b.companies WHERE name = 'Green Thumb Nurseries LLC'", UUID.class);

        UUID trowelVid = variantIdBySku("SKU-004");
        UUID glovesVid = variantIdBySku("SKU-G-M-GRN");

        String shippingAddr = """
            {"firstName":"Maria","lastName":"Member","address1":"456 Bloom Ave",
             "city":"Portland","province":"OR","zip":"97202","country":"US"}
            """.strip();

        UUID orderId = UUID.randomUUID();
        BigDecimal total = new BigDecimal("99.87"); // 4×$9.99 + 5×$11.99 contract pricing
        jdbc.update("""
            INSERT INTO checkout.orders
              (id, user_id, company_id, status, total_amount, currency,
               shipping_address, created_at, updated_at)
            VALUES (?, ?, ?, 'PENDING_APPROVAL', ?, 'usd', ?::jsonb, ?, ?)
            """, orderId, memberId, companyId, total, shippingAddr,
                Timestamp.from(Instant.now().minus(30, ChronoUnit.MINUTES)),
                Timestamp.from(Instant.now().minus(30, ChronoUnit.MINUTES)));
        insertOrderItem(orderId, trowelVid, 4, new BigDecimal("9.99"));
        insertOrderItem(orderId, glovesVid, 5, new BigDecimal("11.99"));
        insertOrderEvent(orderId, "ORDER_PLACED", "B2B order submitted for manager approval");

        log.info("DevDataSeeder: seeded PENDING_APPROVAL B2B order for Maria Member");
    }
}
