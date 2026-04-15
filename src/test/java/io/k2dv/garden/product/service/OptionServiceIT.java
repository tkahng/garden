package io.k2dv.garden.product.service;

import io.k2dv.garden.product.dto.CreateOptionRequest;
import io.k2dv.garden.product.dto.CreateOptionValueRequest;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.dto.RenameOptionRequest;
import io.k2dv.garden.product.dto.RenameOptionValueRequest;
import io.k2dv.garden.product.repository.ProductOptionRepository;
import io.k2dv.garden.product.repository.ProductOptionValueRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionServiceIT extends AbstractIntegrationTest {

    @Autowired OptionService optionService;
    @Autowired VariantService variantService;
    @Autowired ProductService productService;
    @Autowired ProductOptionRepository optionRepo;
    @Autowired ProductOptionValueRepository optionValueRepo;
    @Autowired ProductVariantRepository variantRepo;

    // ── createOption ──────────────────────────────────────────────────────────

    @Test
    void createOption_persistsNameAndPosition() {
        var product = productService.create(new CreateProductRequest("Shirt", null, null, null, null, List.of()));
        var opt = optionService.createOption(product.id(), new CreateOptionRequest("Color", 1));

        assertThat(opt.id()).isNotNull();
        assertThat(opt.name()).isEqualTo("Color");
        assertThat(opt.position()).isEqualTo(1);
        assertThat(optionRepo.findById(opt.id())).isPresent();
    }

    // ── renameOption ──────────────────────────────────────────────────────────

    @Test
    void renameOption_updatesName() {
        var product = productService.create(new CreateProductRequest("Pants", null, null, null, null, List.of()));
        var opt = optionService.createOption(product.id(), new CreateOptionRequest("Colour", 1));

        var renamed = optionService.renameOption(product.id(), opt.id(), new RenameOptionRequest("Color"));

        assertThat(renamed.name()).isEqualTo("Color");
        assertThat(optionRepo.findById(opt.id()).get().getName()).isEqualTo("Color");
    }

    @Test
    void renameOption_wrongProductId_throwsNotFoundException() {
        var product = productService.create(new CreateProductRequest("Jacket", null, null, null, null, List.of()));
        var opt = optionService.createOption(product.id(), new CreateOptionRequest("Size", 1));

        assertThatThrownBy(() ->
            optionService.renameOption(UUID.randomUUID(), opt.id(), new RenameOptionRequest("Size2"))
        ).isInstanceOf(NotFoundException.class);
    }

    @Test
    void renameOption_unknownOptionId_throwsNotFoundException() {
        var product = productService.create(new CreateProductRequest("Hat", null, null, null, null, List.of()));

        assertThatThrownBy(() ->
            optionService.renameOption(product.id(), UUID.randomUUID(), new RenameOptionRequest("X"))
        ).isInstanceOf(NotFoundException.class);
    }

    // ── deleteOption ──────────────────────────────────────────────────────────

    @Test
    void deleteOption_removesFromDb() {
        var product = productService.create(new CreateProductRequest("Scarf", null, null, null, null, List.of()));
        var opt = optionService.createOption(product.id(), new CreateOptionRequest("Material", 1));

        optionService.deleteOption(product.id(), opt.id());

        assertThat(optionRepo.findById(opt.id())).isEmpty();
    }

    @Test
    void deleteOption_wrongProductId_throwsNotFoundException() {
        var product = productService.create(new CreateProductRequest("Gloves", null, null, null, null, List.of()));
        var opt = optionService.createOption(product.id(), new CreateOptionRequest("Size", 1));

        assertThatThrownBy(() -> optionService.deleteOption(UUID.randomUUID(), opt.id()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteOption_unknownOptionId_throwsNotFoundException() {
        var product = productService.create(new CreateProductRequest("Belt", null, null, null, null, List.of()));

        assertThatThrownBy(() -> optionService.deleteOption(product.id(), UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }

    // ── createOptionValue ─────────────────────────────────────────────────────

    @Test
    void createOptionValue_persistsLabelAndPosition() {
        var product = productService.create(new CreateProductRequest("Dress", null, null, null, null, List.of()));
        var opt = optionService.createOption(product.id(), new CreateOptionRequest("Color", 1));

        var val = optionService.createOptionValue(product.id(), opt.id(), new CreateOptionValueRequest("Blue", 1));

        assertThat(val.id()).isNotNull();
        assertThat(val.label()).isEqualTo("Blue");
        assertThat(val.position()).isEqualTo(1);
        assertThat(optionValueRepo.findById(val.id())).isPresent();
    }

    @Test
    void createOptionValue_optionNotFound_throwsNotFoundException() {
        var product = productService.create(new CreateProductRequest("Coat", null, null, null, null, List.of()));

        assertThatThrownBy(() ->
            optionService.createOptionValue(product.id(), UUID.randomUUID(), new CreateOptionValueRequest("Red", 1))
        ).isInstanceOf(NotFoundException.class);
    }

    @Test
    void createOptionValue_wrongProductId_throwsNotFoundException() {
        var product = productService.create(new CreateProductRequest("Vest", null, null, null, null, List.of()));
        var opt = optionService.createOption(product.id(), new CreateOptionRequest("Color", 1));

        assertThatThrownBy(() ->
            optionService.createOptionValue(UUID.randomUUID(), opt.id(), new CreateOptionValueRequest("Green", 1))
        ).isInstanceOf(NotFoundException.class);
    }

    // ── deleteOptionValue ─────────────────────────────────────────────────────

    @Test
    void deleteOptionValue_removesValueFromDb() {
        var product = productService.create(new CreateProductRequest("Hoodie", null, null, null, null, List.of()));
        var opt = optionService.createOption(product.id(), new CreateOptionRequest("Color", 1));
        var val = optionService.createOptionValue(product.id(), opt.id(), new CreateOptionValueRequest("Black", 1));

        optionService.deleteOptionValue(product.id(), opt.id(), val.id());

        assertThat(optionValueRepo.findById(val.id())).isEmpty();
    }

    @Test
    void deleteOptionValue_stripsValueFromVariantAndRecomputesTitle() {
        var product = productService.create(new CreateProductRequest("Hoodie", null, null, null, null, List.of()));
        var colorOpt = optionService.createOption(product.id(), new CreateOptionRequest("Color", 1));
        var black = optionService.createOptionValue(product.id(), colorOpt.id(), new CreateOptionValueRequest("Black", 1));
        var sizeOpt = optionService.createOption(product.id(), new CreateOptionRequest("Size", 2));
        var med = optionService.createOptionValue(product.id(), sizeOpt.id(), new CreateOptionValueRequest("Medium", 1));

        variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("39.99"), null, null, null, null, null,
                List.of(black.id(), med.id())));

        optionService.deleteOptionValue(product.id(), colorOpt.id(), black.id());

        var variants = variantRepo.findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(product.id());
        assertThat(variants).hasSize(1);
        // Black stripped → only Medium remains
        assertThat(variants.get(0).getTitle()).isEqualTo("Medium");
    }

    @Test
    void deleteOptionValue_lastValueOnVariant_recomputesToDefaultTitle() {
        var product = productService.create(new CreateProductRequest("Sock", null, null, null, null, List.of()));
        var opt = optionService.createOption(product.id(), new CreateOptionRequest("Size", 1));
        var sm = optionService.createOptionValue(product.id(), opt.id(), new CreateOptionValueRequest("Small", 1));

        variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("5.00"), null, null, null, null, null, List.of(sm.id())));

        optionService.deleteOptionValue(product.id(), opt.id(), sm.id());

        var variants = variantRepo.findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(product.id());
        assertThat(variants).hasSize(1);
        assertThat(variants.get(0).getTitle()).isEqualTo("Default Title");
    }

    @Test
    void deleteOptionValue_optionNotFound_throwsNotFoundException() {
        var product = productService.create(new CreateProductRequest("Scarf", null, null, null, null, List.of()));

        assertThatThrownBy(() ->
            optionService.deleteOptionValue(product.id(), UUID.randomUUID(), UUID.randomUUID())
        ).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteOptionValue_valueNotFound_throwsNotFoundException() {
        var product = productService.create(new CreateProductRequest("Tie", null, null, null, null, List.of()));
        var opt = optionService.createOption(product.id(), new CreateOptionRequest("Color", 1));

        assertThatThrownBy(() ->
            optionService.deleteOptionValue(product.id(), opt.id(), UUID.randomUUID())
        ).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteOptionValue_valueBelongsToDifferentOption_throwsNotFoundException() {
        var product = productService.create(new CreateProductRequest("Glove", null, null, null, null, List.of()));
        var opt1 = optionService.createOption(product.id(), new CreateOptionRequest("Color", 1));
        var opt2 = optionService.createOption(product.id(), new CreateOptionRequest("Size", 2));
        var valOnOpt2 = optionService.createOptionValue(product.id(), opt2.id(), new CreateOptionValueRequest("L", 1));

        // valueId belongs to opt2, but we pass opt1 → should throw
        assertThatThrownBy(() ->
            optionService.deleteOptionValue(product.id(), opt1.id(), valOnOpt2.id())
        ).isInstanceOf(NotFoundException.class);
    }

    // ── renameOptionValue ─────────────────────────────────────────────────────

    @Test
    void renameOptionValue_updatesLabelAndRecomputesVariantTitle() {
        var product = productService.create(new CreateProductRequest("Cap", null, null, null, null, List.of()));
        var opt = optionService.createOption(product.id(), new CreateOptionRequest("Color", 1));
        var val = optionService.createOptionValue(product.id(), opt.id(), new CreateOptionValueRequest("Blu", 1));

        variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("25.00"), null, null, null, null, null, List.of(val.id())));

        optionService.renameOptionValue(opt.id(), val.id(), new RenameOptionValueRequest("Blue"));

        assertThat(optionValueRepo.findById(val.id()).get().getLabel()).isEqualTo("Blue");
        var variants = variantRepo.findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(product.id());
        assertThat(variants.get(0).getTitle()).isEqualTo("Blue");
    }

    @Test
    void renameOptionValue_valueNotFound_throwsNotFoundException() {
        var product = productService.create(new CreateProductRequest("Bag", null, null, null, null, List.of()));
        var opt = optionService.createOption(product.id(), new CreateOptionRequest("Color", 1));

        assertThatThrownBy(() ->
            optionService.renameOptionValue(opt.id(), UUID.randomUUID(), new RenameOptionValueRequest("Red"))
        ).isInstanceOf(NotFoundException.class);
    }

    @Test
    void renameOptionValue_valueBelongsToDifferentOption_throwsNotFoundException() {
        var product = productService.create(new CreateProductRequest("Scarf", null, null, null, null, List.of()));
        var opt1 = optionService.createOption(product.id(), new CreateOptionRequest("Color", 1));
        var opt2 = optionService.createOption(product.id(), new CreateOptionRequest("Size", 2));
        var valOnOpt2 = optionService.createOptionValue(product.id(), opt2.id(), new CreateOptionValueRequest("L", 1));

        assertThatThrownBy(() ->
            optionService.renameOptionValue(opt1.id(), valOnOpt2.id(), new RenameOptionValueRequest("XL"))
        ).isInstanceOf(NotFoundException.class);
    }
}
