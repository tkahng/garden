package io.k2dv.garden.product.service;

import io.k2dv.garden.product.dto.CreateOptionRequest;
import io.k2dv.garden.product.dto.CreateOptionValueRequest;
import io.k2dv.garden.product.dto.ProductOptionResponse;
import io.k2dv.garden.product.dto.ProductOptionValueResponse;
import io.k2dv.garden.product.dto.RenameOptionRequest;
import io.k2dv.garden.product.dto.RenameOptionValueRequest;
import io.k2dv.garden.product.model.ProductOption;
import io.k2dv.garden.product.model.ProductOptionValue;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductOptionRepository;
import io.k2dv.garden.product.repository.ProductOptionValueRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Manages product options (e.g., "Size", "Color") and their selectable values, which together
 * define the variant matrix for a product. Mutations to option values cascade to keep variant
 * titles consistent across the board.
 */
@Service
@RequiredArgsConstructor
public class OptionService {

    private final ProductOptionRepository optionRepo;
    private final ProductOptionValueRepository optionValueRepo;
    private final ProductVariantRepository variantRepo;
    private final VariantService variantService;

    /**
     * Adds a new option axis (e.g., "Material") to a product at the specified display position.
     * Values are added separately via {@link #createOptionValue}.
     */
    @Transactional
    public ProductOptionResponse createOption(UUID productId, CreateOptionRequest req) {
        ProductOption opt = new ProductOption();
        opt.setProductId(productId);
        opt.setName(req.name());
        opt.setPosition(req.position());
        opt = optionRepo.save(opt);
        return new ProductOptionResponse(opt.getId(), opt.getName(), opt.getPosition(), List.of());
    }

    /**
     * Renames an option axis label (e.g., "Colour" to "Color") for display purposes.
     * Does not affect the underlying variant data.
     */
    @Transactional
    public ProductOptionResponse renameOption(UUID productId, UUID optionId, RenameOptionRequest req) {
        ProductOption opt = optionRepo.findById(optionId)
            .filter(o -> o.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("OPTION_NOT_FOUND", "Option not found"));
        opt.setName(req.name());
        opt = optionRepo.save(opt);
        return new ProductOptionResponse(opt.getId(), opt.getName(), opt.getPosition(), List.of());
    }

    /**
     * Permanently deletes an option axis and all its values from the product.
     */
    @Transactional
    public void deleteOption(UUID productId, UUID optionId) {
        ProductOption opt = optionRepo.findById(optionId)
            .filter(o -> o.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("OPTION_NOT_FOUND", "Option not found"));
        optionRepo.delete(opt);
    }

    /**
     * Adds a selectable value (e.g., "XL") to an existing option axis. This value can then
     * be referenced when creating variants to form the product's purchasable combinations.
     */
    @Transactional
    public ProductOptionValueResponse createOptionValue(UUID productId, UUID optionId, CreateOptionValueRequest req) {
        optionRepo.findById(optionId)
            .filter(o -> o.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("OPTION_NOT_FOUND", "Option not found"));
        ProductOptionValue val = new ProductOptionValue();
        val.setOptionId(optionId);
        val.setLabel(req.label());
        val.setPosition(req.position());
        val = optionValueRepo.save(val);
        return new ProductOptionValueResponse(val.getId(), val.getLabel(), val.getPosition());
    }

    /**
     * Removes an option value and detaches it from any variants that reference it, recomputing
     * those variants' titles. The join-table rows are flushed before the entity delete to avoid
     * foreign-key constraint violations.
     */
    @Transactional
    public void deleteOptionValue(UUID productId, UUID optionId, UUID valueId) {
        optionRepo.findById(optionId)
            .filter(o -> o.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("OPTION_NOT_FOUND", "Option not found"));
        ProductOptionValue val = optionValueRepo.findById(valueId)
            .filter(v -> v.getOptionId().equals(optionId))
            .orElseThrow(() -> new NotFoundException("OPTION_VALUE_NOT_FOUND", "Option value not found"));

        List<ProductVariant> affected = variantRepo.findByOptionValueIdAndDeletedAtIsNull(valueId);
        for (ProductVariant v : affected) {
            v.getOptionValues().remove(val);
            v.setTitle(variantService.buildTitle(v.getOptionValues()));
        }
        variantRepo.saveAll(affected);
        variantRepo.flush(); // ensure join-table rows are deleted before the entity delete
        optionValueRepo.delete(val);
    }

    /**
     * Renames an option value label and propagates the change to the display title of every
     * active variant that uses this value. Dirty-checking handles the variant saves implicitly.
     */
    @Transactional
    public ProductOptionValueResponse renameOptionValue(UUID optionId, UUID valueId, RenameOptionValueRequest req) {
        ProductOptionValue val = optionValueRepo.findById(valueId)
            .filter(v -> v.getOptionId().equals(optionId))
            .orElseThrow(() -> new NotFoundException("OPTION_VALUE_NOT_FOUND", "Option value not found"));
        val.setLabel(req.label());
        val = optionValueRepo.save(val);

        // Recompute title of all non-deleted variants linked to this value
        List<ProductVariant> affected = variantRepo.findByOptionValueIdAndDeletedAtIsNull(valueId);
        for (ProductVariant v : affected) {
            v.setTitle(variantService.buildTitle(v.getOptionValues()));
        }
        // No save/flush needed — @Transactional dirty-checking handles it

        return new ProductOptionValueResponse(val.getId(), val.getLabel(), val.getPosition());
    }
}
