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

@Service
@RequiredArgsConstructor
public class OptionService {

    private final ProductOptionRepository optionRepo;
    private final ProductOptionValueRepository optionValueRepo;
    private final ProductVariantRepository variantRepo;
    private final VariantService variantService;

    @Transactional
    public ProductOptionResponse createOption(UUID productId, CreateOptionRequest req) {
        ProductOption opt = new ProductOption();
        opt.setProductId(productId);
        opt.setName(req.name());
        opt.setPosition(req.position());
        opt = optionRepo.save(opt);
        return new ProductOptionResponse(opt.getId(), opt.getName(), opt.getPosition(), List.of());
    }

    @Transactional
    public ProductOptionResponse renameOption(UUID productId, UUID optionId, RenameOptionRequest req) {
        ProductOption opt = optionRepo.findById(optionId)
            .filter(o -> o.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("OPTION_NOT_FOUND", "Option not found"));
        opt.setName(req.name());
        opt = optionRepo.save(opt);
        return new ProductOptionResponse(opt.getId(), opt.getName(), opt.getPosition(), List.of());
    }

    @Transactional
    public void deleteOption(UUID productId, UUID optionId) {
        ProductOption opt = optionRepo.findById(optionId)
            .filter(o -> o.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("OPTION_NOT_FOUND", "Option not found"));
        optionRepo.delete(opt);
    }

    @Transactional
    public ProductOptionValueResponse createOptionValue(UUID optionId, CreateOptionValueRequest req) {
        optionRepo.findById(optionId)
            .orElseThrow(() -> new NotFoundException("OPTION_NOT_FOUND", "Option not found"));
        ProductOptionValue val = new ProductOptionValue();
        val.setOptionId(optionId);
        val.setLabel(req.label());
        val.setPosition(req.position());
        val = optionValueRepo.save(val);
        return new ProductOptionValueResponse(val.getId(), val.getLabel(), val.getPosition());
    }

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
