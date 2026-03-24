package io.k2dv.garden.product.service;

import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.model.*;
import io.k2dv.garden.product.repository.*;
import io.k2dv.garden.product.specification.ProductSpecification;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepo;
    private final ProductTagRepository tagRepo;
    private final ProductImageRepository imageRepo;
    private final ProductOptionRepository optionRepo;
    private final ProductVariantRepository variantRepo;
    private final BlobObjectRepository blobRepo;
    private final StorageService storageService;

    @Transactional
    public AdminProductResponse create(CreateProductRequest req) {
        if (req.title() == null) {
            throw new ValidationException("TITLE_REQUIRED", "Title is required");
        }
        String handle = req.handle() != null ? req.handle() : slugify(req.title());
        checkHandleUnique(handle, null);

        Product product = new Product();
        product.setTitle(req.title());
        product.setDescription(req.description());
        product.setHandle(handle);
        product.setVendor(req.vendor());
        product.setProductType(req.productType());
        product.setStatus(ProductStatus.DRAFT);
        if (req.tags() != null) {
            req.tags().forEach(name -> product.getTags().add(findOrCreateTag(name)));
        }
        Product saved = productRepo.save(product);
        return toAdminResponse(saved);
    }

    @Transactional(readOnly = true)
    public AdminProductResponse getAdmin(UUID id) {
        Product p = productRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        return toAdminResponse(p);
    }

    @Transactional(readOnly = true)
    public PagedResult<AdminProductResponse> listAdmin(ProductFilterRequest filter, Pageable pageable) {
        Page<Product> page = productRepo.findAll(ProductSpecification.toSpec(filter), pageable);
        return PagedResult.of(page, this::toAdminResponse);
    }

    @Transactional
    public AdminProductResponse update(UUID id, UpdateProductRequest req) {
        Product p = productRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        if (req.title() != null) p.setTitle(req.title());
        if (req.description() != null) p.setDescription(req.description());
        if (req.handle() != null) {
            checkHandleUnique(req.handle(), id);
            p.setHandle(req.handle());
        }
        if (req.vendor() != null) p.setVendor(req.vendor());
        if (req.productType() != null) p.setProductType(req.productType());
        if (req.featuredImageId() != null) p.setFeaturedImageId(req.featuredImageId());
        if (req.tags() != null) {
            p.getTags().clear();
            req.tags().forEach(name -> p.getTags().add(findOrCreateTag(name)));
        }
        return toAdminResponse(productRepo.save(p));
    }

    @Transactional
    public AdminProductResponse changeStatus(UUID id, ProductStatusRequest req) {
        Product p = productRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        p.setStatus(req.status());
        return toAdminResponse(productRepo.save(p));
    }

    @Transactional
    public void softDelete(UUID id) {
        Product p = productRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        p.setDeletedAt(Instant.now());
        productRepo.save(p);
    }

    @Transactional(readOnly = true)
    public PagedResult<ProductSummaryResponse> listStorefront(StorefrontProductFilterRequest filter, Pageable pageable) {
        Page<Product> page = productRepo.findAll(ProductSpecification.storefrontSpec(filter), pageable);
        return PagedResult.of(page, p -> new ProductSummaryResponse(p.getId(), p.getTitle(), p.getHandle(), p.getVendor()));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getByHandle(String handle) {
        Product p = productRepo.findByHandle(handle)
            .filter(prod -> prod.getStatus() == ProductStatus.ACTIVE && prod.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        return toDetailResponse(p);
    }

    // --- helpers ---

    private void checkHandleUnique(String handle, UUID excludeId) {
        boolean conflict = excludeId == null
            ? productRepo.existsByHandleAndDeletedAtIsNull(handle)
            : productRepo.existsByHandleAndDeletedAtIsNullAndIdNot(handle, excludeId);
        if (conflict) {
            throw new ConflictException("HANDLE_CONFLICT", "A product with this handle already exists");
        }
    }

    private ProductTag findOrCreateTag(String name) {
        return tagRepo.findByName(name).orElseGet(() -> {
            ProductTag t = new ProductTag();
            t.setName(name);
            return tagRepo.save(t);
        });
    }

    static String slugify(String title) {
        String slug = title.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "product" : slug;
    }

    private AdminProductResponse toAdminResponse(Product p) {
        List<ProductVariant> variants = variantRepo.findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(p.getId());
        List<ProductImage> images = imageRepo.findByProductIdOrderByPositionAsc(p.getId());
        List<ProductOption> options = optionRepo.findByProductIdOrderByPositionAsc(p.getId());
        Map<UUID, String> optionNameById = options.stream()
            .collect(Collectors.toMap(ProductOption::getId, ProductOption::getName));

        List<AdminVariantResponse> variantResponses = variants.stream().map(v -> {
            List<OptionValueLabel> labels = v.getOptionValues().stream()
                .map(ov -> new OptionValueLabel(
                    optionNameById.getOrDefault(ov.getOptionId(), ""),
                    ov.getLabel()))
                .toList();
            return new AdminVariantResponse(v.getId(), v.getTitle(), v.getSku(), v.getBarcode(),
                v.getPrice(), v.getCompareAtPrice(), v.getWeight(), v.getWeightUnit(),
                labels, v.getFulfillmentType(), v.getInventoryPolicy(), v.getLeadTimeDays(), v.getDeletedAt());
        }).toList();

        Set<UUID> blobIds = images.stream().map(ProductImage::getBlobId).collect(Collectors.toSet());
        Map<UUID, String> blobUrls = blobRepo.findAllById(blobIds).stream()
            .collect(Collectors.toMap(b -> b.getId(), b -> storageService.resolveUrl(b.getKey())));

        List<ProductImageResponse> imageResponses = images.stream().map(img -> {
            String url = blobUrls.getOrDefault(img.getBlobId(), "");
            return new ProductImageResponse(img.getId(), url, img.getAltText(), img.getPosition());
        }).toList();

        List<String> tagNames = p.getTags().stream().map(ProductTag::getName).toList();

        return new AdminProductResponse(p.getId(), p.getTitle(), p.getDescription(), p.getHandle(),
            p.getVendor(), p.getProductType(), p.getStatus(), p.getFeaturedImageId(),
            variantResponses, imageResponses, tagNames,
            p.getCreatedAt(), p.getUpdatedAt(), p.getDeletedAt());
    }

    private ProductDetailResponse toDetailResponse(Product p) {
        List<ProductVariant> variants = variantRepo.findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(p.getId());
        List<ProductImage> images = imageRepo.findByProductIdOrderByPositionAsc(p.getId());
        List<ProductOption> options = optionRepo.findByProductIdOrderByPositionAsc(p.getId());
        Map<UUID, String> optionNameById = options.stream()
            .collect(Collectors.toMap(ProductOption::getId, ProductOption::getName));

        List<ProductVariantResponse> variantResponses = variants.stream().map(v -> {
            List<OptionValueLabel> labels = v.getOptionValues().stream()
                .map(ov -> new OptionValueLabel(
                    optionNameById.getOrDefault(ov.getOptionId(), ""),
                    ov.getLabel()))
                .toList();
            return new ProductVariantResponse(v.getId(), v.getTitle(), v.getSku(),
                v.getPrice(), v.getCompareAtPrice(), labels,
                v.getFulfillmentType(), v.getInventoryPolicy(), v.getLeadTimeDays());
        }).toList();

        Set<UUID> blobIds = images.stream().map(ProductImage::getBlobId).collect(Collectors.toSet());
        Map<UUID, String> blobUrls = blobRepo.findAllById(blobIds).stream()
            .collect(Collectors.toMap(b -> b.getId(), b -> storageService.resolveUrl(b.getKey())));

        List<ProductImageResponse> imageResponses = images.stream().map(img -> {
            String url = blobUrls.getOrDefault(img.getBlobId(), "");
            return new ProductImageResponse(img.getId(), url, img.getAltText(), img.getPosition());
        }).toList();

        List<String> tagNames = p.getTags().stream().map(ProductTag::getName).toList();

        return new ProductDetailResponse(p.getId(), p.getTitle(), p.getDescription(), p.getHandle(),
            p.getVendor(), p.getProductType(), variantResponses, imageResponses, tagNames);
    }
}
