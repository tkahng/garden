package io.k2dv.garden;

import io.k2dv.garden.blob.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        List<UUID> variantProductIds = seedVariantProducts();
        List<UUID> quoteOnlyProductIds = seedQuoteOnlyProducts();
        List<UUID> collectionIds = seedCollections();
        seedCollectionProducts(collectionIds, productIds, variantProductIds, quoteOnlyProductIds);
        seedImages();
        seedCollectionImages();
        seedInventory(productIds, variantProductIds);
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
                    INSERT INTO storage.blob_objects (id, key, filename, content_type, size)
                    VALUES (?, ?, ?, 'image/jpeg', ?)
                    """, blobId, objectKey, filename, imageBytes.length);

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
                INSERT INTO storage.blob_objects (id, key, filename, content_type, size)
                VALUES (?, ?, ?, 'image/jpeg', ?)
                """, blobId, objectKey, filename, imageBytes.length);

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
}
