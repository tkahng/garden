package io.k2dv.garden.collection.service;

import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.collection.dto.request.AddCollectionProductRequest;
import io.k2dv.garden.collection.dto.request.CollectionFilterRequest;
import io.k2dv.garden.collection.dto.request.CollectionStatusRequest;
import io.k2dv.garden.collection.dto.request.CreateCollectionRequest;
import io.k2dv.garden.collection.dto.request.CreateCollectionRuleRequest;
import io.k2dv.garden.collection.dto.request.UpdateCollectionProductPositionRequest;
import io.k2dv.garden.collection.dto.request.UpdateCollectionRequest;
import io.k2dv.garden.collection.dto.response.AdminCollectionResponse;
import io.k2dv.garden.collection.dto.response.AdminCollectionSummaryResponse;
import io.k2dv.garden.collection.dto.response.CollectionDetailResponse;
import io.k2dv.garden.collection.dto.response.CollectionProductResponse;
import io.k2dv.garden.collection.dto.response.CollectionRuleResponse;
import io.k2dv.garden.collection.dto.response.CollectionSummaryResponse;
import io.k2dv.garden.collection.model.Collection;
import io.k2dv.garden.collection.model.CollectionProduct;
import io.k2dv.garden.collection.model.CollectionRule;
import io.k2dv.garden.collection.model.CollectionStatus;
import io.k2dv.garden.collection.model.CollectionType;
import io.k2dv.garden.collection.repository.CollectionProductRepository;
import io.k2dv.garden.collection.repository.CollectionRepository;
import io.k2dv.garden.collection.repository.CollectionRuleRepository;
import io.k2dv.garden.collection.specification.CollectionSpecification;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductImage;
import io.k2dv.garden.product.repository.ProductImageRepository;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Manages curated product collections used for merchandising (e.g., "Summer Sale",
 * "Featured"). Supports both manually curated ({@code MANUAL}) and rule-driven
 * ({@code AUTOMATED}) collection types, with separate read paths for the admin
 * back-office and the customer-facing storefront.
 */
@Service
@RequiredArgsConstructor
public class CollectionService {

    private final CollectionRepository collectionRepo;
    private final CollectionRuleRepository ruleRepo;
    private final CollectionProductRepository cpRepo;
    private final ProductRepository productRepo;
    private final ProductImageRepository imageRepo;
    private final CollectionMembershipService membershipService;
    private final BlobObjectRepository blobRepo;
    private final StorageService storageService;

    // --- Collection CRUD ---

    /**
     * Creates a new collection in DRAFT status, auto-generating a URL handle from the title
     * if one is not supplied. Rejects the request if the handle conflicts with an existing
     * non-deleted collection.
     */
    @Transactional
    public AdminCollectionResponse create(CreateCollectionRequest req) {
        String handle = req.handle() != null ? req.handle() : slugify(req.title());
        checkHandleUnique(handle, null);

        Collection c = new Collection();
        c.setTitle(req.title());
        c.setHandle(handle);
        c.setDescription(req.description());
        c.setCollectionType(req.collectionType());
        c.setDisjunctive(req.disjunctive());
        c.setFeaturedImageId(req.featuredImageId());
        c.setMetaTitle(req.metaTitle());
        c.setMetaDescription(req.metaDescription());
        c.setStatus(CollectionStatus.DRAFT);
        Collection saved = collectionRepo.save(c);
        return toAdminResponse(saved);
    }

    /**
     * Fetches a collection with its full detail (rules, product count) for the admin view.
     */
    @Transactional(readOnly = true)
    public AdminCollectionResponse getAdmin(UUID id) {
        return toAdminResponse(findActiveOrThrow(id));
    }

    /**
     * Returns a paginated admin list of collections with product counts batch-loaded to
     * prevent N+1 queries per collection.
     */
    @Transactional(readOnly = true)
    public PagedResult<AdminCollectionSummaryResponse> listAdmin(CollectionFilterRequest filter, Pageable pageable) {
        Page<Collection> page = collectionRepo.findAll(CollectionSpecification.toSpec(filter), pageable);
        List<UUID> ids = page.getContent().stream().map(Collection::getId).toList();
        Map<UUID, Long> countMap = ids.isEmpty() ? Map.of() :
                cpRepo.countByCollectionIdIn(ids).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                CollectionProductRepository.CollectionCount::getCollectionId,
                                CollectionProductRepository.CollectionCount::getProductCount));
        return PagedResult.of(page, c ->
                new AdminCollectionSummaryResponse(c.getId(), c.getTitle(), c.getHandle(),
                        c.getCollectionType(), c.getStatus(),
                        countMap.getOrDefault(c.getId(), 0L), c.getCreatedAt()));
    }

    /**
     * Applies partial updates to a collection's display attributes and SEO fields.
     * Handle changes are validated for uniqueness before being applied.
     */
    @Transactional
    public AdminCollectionResponse update(UUID id, UpdateCollectionRequest req) {
        Collection c = findActiveOrThrow(id);
        if (req.title() != null)           c.setTitle(req.title());
        if (req.handle() != null) {
            checkHandleUnique(req.handle(), id);
            c.setHandle(req.handle());
        }
        if (req.description() != null)     c.setDescription(req.description());
        if (req.disjunctive() != null)     c.setDisjunctive(req.disjunctive());
        if (req.featuredImageId() != null) c.setFeaturedImageId(req.featuredImageId());
        if (req.metaTitle() != null) c.setMetaTitle(req.metaTitle());
        if (req.metaDescription() != null) c.setMetaDescription(req.metaDescription());
        return toAdminResponse(collectionRepo.save(c));
    }

    /**
     * Transitions a collection to a new publishing status (e.g., DRAFT to ACTIVE),
     * controlling its visibility on the storefront.
     */
    @Transactional
    public AdminCollectionResponse changeStatus(UUID id, CollectionStatusRequest req) {
        Collection c = findActiveOrThrow(id);
        c.setStatus(req.status());
        return toAdminResponse(collectionRepo.save(c));
    }

    /**
     * Soft-deletes a collection, first purging all product memberships and rules to leave
     * no orphaned data. The collection record is retained for historical reference.
     */
    @Transactional
    public void softDelete(UUID id) {
        Collection c = findActiveOrThrow(id);
        cpRepo.deleteByCollectionId(id);
        ruleRepo.deleteByCollectionId(id);
        c.setDeletedAt(Instant.now());
        collectionRepo.save(c);
    }

    // --- Rules ---

    /**
     * Lists the tag-matching rules that govern automated product membership for a collection.
     */
    @Transactional(readOnly = true)
    public List<CollectionRuleResponse> listRules(UUID collectionId) {
        findActiveOrThrow(collectionId);
        return ruleRepo.findByCollectionIdOrderByCreatedAtAsc(collectionId).stream()
                .map(this::toRuleResponse).toList();
    }

    /**
     * Adds a tag-matching rule to an AUTOMATED collection and immediately triggers a full
     * membership sync so the collection's product set reflects the new rule set.
     */
    @Transactional
    public CollectionRuleResponse addRule(UUID collectionId, CreateCollectionRuleRequest req) {
        Collection c = findActiveOrThrow(collectionId);
        if (c.getCollectionType() != CollectionType.AUTOMATED) {
            throw new ValidationException("RULES_NOT_SUPPORTED", "Rules are only supported on AUTOMATED collections");
        }
        CollectionRule rule = new CollectionRule();
        rule.setCollectionId(collectionId);
        rule.setField(req.field());
        rule.setOperator(req.operator());
        rule.setValue(req.value());
        CollectionRule saved = ruleRepo.save(rule);
        membershipService.syncCollectionMembership(collectionId);
        return toRuleResponse(saved);
    }

    /**
     * Removes a rule from an AUTOMATED collection and re-syncs membership so products that
     * no longer qualify are immediately removed from the collection.
     */
    @Transactional
    public void deleteRule(UUID collectionId, UUID ruleId) {
        Collection c = findActiveOrThrow(collectionId);
        if (c.getCollectionType() != CollectionType.AUTOMATED) {
            throw new ValidationException("RULES_NOT_SUPPORTED", "Rules are only supported on AUTOMATED collections");
        }
        CollectionRule rule = ruleRepo.findById(ruleId)
                .filter(r -> r.getCollectionId().equals(collectionId))
                .orElseThrow(() -> new NotFoundException("RULE_NOT_FOUND", "Rule not found"));
        ruleRepo.delete(rule);
        membershipService.syncCollectionMembership(collectionId);
    }

    // --- Manual product membership ---

    /**
     * Returns the ordered, paginated list of products in a collection, as seen by the admin.
     */
    @Transactional(readOnly = true)
    public PagedResult<CollectionProductResponse> listProducts(UUID collectionId, Pageable pageable) {
        findActiveOrThrow(collectionId);
        Page<CollectionProduct> page = cpRepo.findByCollectionIdOrderByPositionAscCreatedAtAsc(collectionId, pageable);
        List<UUID> productIds = page.getContent().stream().map(CollectionProduct::getProductId).toList();
        Map<UUID, Product> productMap = productIds.isEmpty() ? Map.of() :
                productRepo.findAllById(productIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));
        return PagedResult.of(page, cp -> {
            Product p = productMap.get(cp.getProductId());
            if (p == null) throw new NotFoundException("PRODUCT_NOT_FOUND", "Product not found");
            return toProductResponse(cp, p);
        });
    }

    /**
     * Manually adds a product to a MANUAL collection, appending it at the next available
     * position. Rejects duplicates and disallows direct membership edits on AUTOMATED collections.
     */
    @Transactional
    public CollectionProductResponse addProduct(UUID collectionId, AddCollectionProductRequest req) {
        Collection c = findActiveOrThrow(collectionId);
        if (c.getCollectionType() == CollectionType.AUTOMATED) {
            throw new ValidationException("AUTOMATED_MEMBERSHIP", "Cannot manually add products to an AUTOMATED collection");
        }
        if (cpRepo.existsByCollectionIdAndProductId(collectionId, req.productId())) {
            throw new ConflictException("PRODUCT_ALREADY_IN_COLLECTION", "Product is already in this collection");
        }
        Product product = productRepo.findByIdAndDeletedAtIsNull(req.productId())
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        Integer maxPos = cpRepo.findMaxPositionByCollectionId(collectionId);
        CollectionProduct cp = new CollectionProduct();
        cp.setCollectionId(collectionId);
        cp.setProductId(req.productId());
        cp.setPosition((maxPos != null ? maxPos : 0) + 1);
        CollectionProduct saved = cpRepo.save(cp);
        return toProductResponse(saved, product);
    }

    /**
     * Manually removes a product from a MANUAL collection. Throws if the product is not
     * a current member, or if the collection is AUTOMATED (managed by rules, not by hand).
     */
    @Transactional
    public void removeProduct(UUID collectionId, UUID productId) {
        Collection c = findActiveOrThrow(collectionId);
        if (c.getCollectionType() == CollectionType.AUTOMATED) {
            throw new ValidationException("AUTOMATED_MEMBERSHIP", "Cannot manually remove products from an AUTOMATED collection");
        }
        if (!cpRepo.existsByCollectionIdAndProductId(collectionId, productId)) {
            throw new NotFoundException("PRODUCT_NOT_IN_COLLECTION", "Product is not in this collection");
        }
        cpRepo.deleteByCollectionIdAndProductId(collectionId, productId);
    }

    /**
     * Updates the display position of a product within a collection, allowing merchandisers
     * to control the sort order of the product grid.
     */
    @Transactional
    public CollectionProductResponse updateProductPosition(UUID collectionId, UUID productId,
                                                           UpdateCollectionProductPositionRequest req) {
        findActiveOrThrow(collectionId);
        CollectionProduct cp = cpRepo.findByCollectionIdAndProductId(collectionId, productId)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_IN_COLLECTION", "Product is not in this collection"));
        cp.setPosition(req.position());
        CollectionProduct saved = cpRepo.save(cp);
        return toProductResponse(saved);
    }

    // --- Storefront ---

    /**
     * Returns a customer-facing paginated list of active collections, each decorated with a
     * resolved featured image URL.
     */
    @Transactional(readOnly = true)
    public PagedResult<CollectionSummaryResponse> listStorefront(Pageable pageable) {
        Page<Collection> page = collectionRepo.findAll(CollectionSpecification.storefrontSpec(), pageable);
        Map<UUID, String> imageUrls = resolveCollectionImageUrls(
                page.getContent().stream().map(Collection::getFeaturedImageId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return PagedResult.of(page, c -> new CollectionSummaryResponse(c.getId(), c.getTitle(), c.getHandle(),
                c.getFeaturedImageId() != null ? imageUrls.get(c.getFeaturedImageId()) : null));
    }

    /**
     * Fetches a single active collection by its URL handle for the storefront detail page,
     * including SEO metadata and a resolved featured image URL.
     */
    @Transactional(readOnly = true)
    public CollectionDetailResponse getByHandle(String handle) {
        Collection c = collectionRepo.findByHandleAndDeletedAtIsNullAndStatus(handle, CollectionStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("COLLECTION_NOT_FOUND", "Collection not found"));
        String imageUrl = null;
        if (c.getFeaturedImageId() != null) {
            Map<UUID, String> imageUrls = resolveCollectionImageUrls(Set.of(c.getFeaturedImageId()));
            imageUrl = imageUrls.get(c.getFeaturedImageId());
        }
        return new CollectionDetailResponse(c.getId(), c.getTitle(), c.getHandle(), c.getDescription(), imageUrl,
                c.getMetaTitle(), c.getMetaDescription());
    }

    /**
     * Returns the paginated, sorted product listing for a collection's storefront page.
     * Supports multiple sort modes including featured (position-based), date, and title.
     * B2B catalog restrictions are enforced via {@code companyId}: only products accessible
     * to the company are returned when a company context is present.
     */
    @Transactional(readOnly = true)
    public PagedResult<CollectionProductResponse> listProductsStorefront(String handle, int page, int size, String sortBy, String sortDir, UUID companyId) {
        Collection c = collectionRepo.findByHandleAndDeletedAtIsNullAndStatus(handle, CollectionStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("COLLECTION_NOT_FOUND", "Collection not found"));

        if ("title".equals(sortBy)) {
            return listProductsStorefrontSortedByTitle(c, page, size, sortDir, companyId);
        }

        Sort sort = switch (sortBy == null ? "featured" : sortBy) {
            case "date_asc" -> Sort.by(Sort.Order.asc("createdAt"));
            case "date_desc" -> Sort.by(Sort.Order.desc("createdAt"));
            default -> Sort.by(Sort.Order.asc("position"), Sort.Order.asc("createdAt"));
        };
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<CollectionProduct> cpPage = companyId != null
                ? cpRepo.findActiveProductsByCollectionIdForCompany(c.getId(), companyId, pageable)
                : cpRepo.findActivePublicProductsByCollectionId(c.getId(), pageable);
        return assembleCollectionProductPage(cpPage, pageable);
    }

    private PagedResult<CollectionProductResponse> listProductsStorefrontSortedByTitle(Collection c, int page, int size, String sortDir, UUID companyId) {
        List<UUID> allIds = companyId != null
                ? cpRepo.findActiveProductIdsByCollectionIdForCompany(c.getId(), companyId)
                : cpRepo.findActivePublicProductIdsByCollectionId(c.getId());
        Map<UUID, Product> productMap = productRepo.findAllById(allIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        Comparator<UUID> cmp = Comparator.comparing(
                id -> productMap.getOrDefault(id, new Product()).getTitle(),
                String.CASE_INSENSITIVE_ORDER);
        if ("desc".equalsIgnoreCase(sortDir)) cmp = cmp.reversed();

        List<UUID> sorted = allIds.stream().filter(productMap::containsKey).sorted(cmp).toList();
        int total = sorted.size();
        int from = page * size;
        int to = Math.min(from + size, total);
        List<UUID> pageIds = from < total ? sorted.subList(from, to) : List.of();

        Map<UUID, String> imageUrls = resolveProductFeaturedImageUrls(
                pageIds.stream().map(productMap::get).filter(Objects::nonNull).toList());

        List<CollectionProductResponse> content = new ArrayList<>();
        for (UUID id : pageIds) {
            Product p = productMap.get(id);
            if (p == null) continue;
            String imgUrl = p.getFeaturedImageId() != null ? imageUrls.get(p.getFeaturedImageId()) : null;
            content.add(new CollectionProductResponse(null, p.getId(), p.getTitle(), p.getHandle(), 0, imgUrl));
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<CollectionProductResponse> resultPage = new PageImpl<>(content, pageable, total);
        return PagedResult.of(resultPage, r -> r);
    }

    private PagedResult<CollectionProductResponse> assembleCollectionProductPage(Page<CollectionProduct> cpPage, Pageable pageable) {
        List<UUID> productIds = cpPage.getContent().stream().map(CollectionProduct::getProductId).toList();
        Map<UUID, Product> productMap = productRepo.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        Map<UUID, String> productImageUrls = resolveProductFeaturedImageUrls(productMap.values().stream().toList());
        return PagedResult.of(cpPage, cp -> {
            Product p = productMap.get(cp.getProductId());
            if (p == null) throw new NotFoundException("PRODUCT_NOT_FOUND", "Product not found");
            String imgUrl = p.getFeaturedImageId() != null ? productImageUrls.get(p.getFeaturedImageId()) : null;
            return toProductResponse(cp, p, imgUrl);
        });
    }

    // --- Helpers ---

    private Map<UUID, String> resolveCollectionImageUrls(Set<UUID> blobIds) {
        if (blobIds.isEmpty()) return Map.of();
        return blobRepo.findAllById(blobIds).stream()
                .collect(Collectors.toMap(b -> b.getId(), b -> storageService.resolveUrl(b.getKey())));
    }

    private Collection findActiveOrThrow(UUID id) {
        return collectionRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("COLLECTION_NOT_FOUND", "Collection not found"));
    }

    private void checkHandleUnique(String handle, UUID excludeId) {
        boolean conflict = excludeId == null
                ? collectionRepo.existsByHandleAndDeletedAtIsNull(handle)
                : collectionRepo.existsByHandleAndDeletedAtIsNullAndIdNot(handle, excludeId);
        if (conflict) {
            throw new ConflictException("HANDLE_CONFLICT", "A collection with this handle already exists");
        }
    }

    static String slugify(String title) {
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "collection" : slug;
    }

    private AdminCollectionResponse toAdminResponse(Collection c) {
        long productCount = cpRepo.countByCollectionId(c.getId());
        List<CollectionRuleResponse> rules = ruleRepo.findByCollectionIdOrderByCreatedAtAsc(c.getId())
                .stream().map(this::toRuleResponse).toList();
        return new AdminCollectionResponse(c.getId(), c.getTitle(), c.getHandle(), c.getDescription(),
                c.getCollectionType(), c.getStatus(), c.getFeaturedImageId(), c.isDisjunctive(),
                productCount, rules, c.getMetaTitle(), c.getMetaDescription(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getDeletedAt());
    }

    private CollectionRuleResponse toRuleResponse(CollectionRule r) {
        return new CollectionRuleResponse(r.getId(), r.getField(), r.getOperator(), r.getValue(), r.getCreatedAt());
    }

    private CollectionProductResponse toProductResponse(CollectionProduct cp) {
        Product p = productRepo.findByIdAndDeletedAtIsNull(cp.getProductId())
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        return new CollectionProductResponse(cp.getId(), cp.getProductId(), p.getTitle(), p.getHandle(), cp.getPosition(), null);
    }

    private CollectionProductResponse toProductResponse(CollectionProduct cp, Product p) {
        return new CollectionProductResponse(cp.getId(), cp.getProductId(), p.getTitle(), p.getHandle(), cp.getPosition(), null);
    }

    private CollectionProductResponse toProductResponse(CollectionProduct cp, Product p, String featuredImageUrl) {
        return new CollectionProductResponse(cp.getId(), cp.getProductId(), p.getTitle(), p.getHandle(), cp.getPosition(), featuredImageUrl);
    }

    private Map<UUID, String> resolveProductFeaturedImageUrls(List<Product> products) {
        Set<UUID> featuredImageIds = products.stream()
                .map(Product::getFeaturedImageId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (featuredImageIds.isEmpty()) return Map.of();
        Map<UUID, ProductImage> imagesById = imageRepo.findAllById(featuredImageIds).stream()
                .collect(Collectors.toMap(ProductImage::getId, img -> img));
        Set<UUID> blobIds = imagesById.values().stream()
                .map(ProductImage::getBlobId).collect(Collectors.toSet());
        Map<UUID, String> blobUrls = blobRepo.findAllById(blobIds).stream()
                .collect(Collectors.toMap(b -> b.getId(), b -> storageService.resolveUrl(b.getKey())));
        return imagesById.entrySet().stream()
                .filter(e -> blobUrls.containsKey(e.getValue().getBlobId()))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> blobUrls.get(e.getValue().getBlobId())));
    }
}
