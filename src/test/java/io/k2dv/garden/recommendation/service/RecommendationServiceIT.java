package io.k2dv.garden.recommendation.service;

import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.product.dto.AdminProductResponse;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.ProductStatusRequest;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationServiceIT extends AbstractIntegrationTest {

    @Autowired RecommendationService recommendationService;
    @Autowired ProductService productService;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private AdminProductResponse activeProduct(String title, String handle, List<String> tags) {
        AdminProductResponse p = productService.create(
                new CreateProductRequest(title, null, handle, null, null, tags, null, null));
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        return p;
    }

    private String handle(String base) {
        return base + "-" + counter.incrementAndGet();
    }

    @Test
    void findRelated_unknownHandle_throwsNotFoundException() {
        assertThatThrownBy(() -> recommendationService.findRelated("no-such-product-handle", 4))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findRelated_productWithNoSharedTags_returnsEmpty() {
        String src = handle("lone-shovel");
        activeProduct("Lone Shovel", src, List.of());
        activeProduct("Other Plant", handle("other-plant"), List.of());

        var results = recommendationService.findRelated(src, 4);

        assertThat(results).isEmpty();
    }

    @Test
    void findRelated_sharedTagsReturnsRelatedProducts() {
        String srcHandle = handle("rose-bush-a");
        activeProduct("Rose Bush A", srcHandle, List.of("flowers", "perennial"));
        activeProduct("Rose Bush B", handle("rose-bush-b"), List.of("flowers"));
        activeProduct("Unrelated Shovel", handle("unrelated-shovel"), List.of());

        var results = recommendationService.findRelated(srcHandle, 10);

        assertThat(results).anyMatch(r -> r.title().equals("Rose Bush B"));
        assertThat(results).noneMatch(r -> r.title().equals("Unrelated Shovel"));
        assertThat(results).noneMatch(r -> r.title().equals("Rose Bush A"));
    }

    @Test
    void findRelated_respectsLimit() {
        String srcHandle = handle("seed-mix-a");
        activeProduct("Seed Mix A", srcHandle, List.of("seeds"));
        for (int i = 1; i <= 5; i++) {
            activeProduct("Seed Mix " + (i + 1), handle("seed-mix-" + (i + 1)), List.of("seeds"));
        }

        var results = recommendationService.findRelated(srcHandle, 3);

        assertThat(results).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void findRelated_excludesSourceProduct() {
        String srcHandle = handle("self-ref-plant");
        activeProduct("Self Ref Plant", srcHandle, List.of("herbs"));

        var results = recommendationService.findRelated(srcHandle, 10);

        assertThat(results).noneMatch(r -> r.title().equals("Self Ref Plant"));
    }
}
