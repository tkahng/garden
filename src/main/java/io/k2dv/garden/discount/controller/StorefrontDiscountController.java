package io.k2dv.garden.discount.controller;

import io.k2dv.garden.discount.dto.DiscountValidationResponse;
import io.k2dv.garden.discount.service.DiscountService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@Tag(name = "Storefront: Discounts", description = "Storefront discount validation")
@RestController
@RequestMapping("/api/v1/storefront/discounts")
@RequiredArgsConstructor
public class StorefrontDiscountController {

    private final DiscountService discountService;

    @GetMapping("/validate")
    public ApiResponse<DiscountValidationResponse> validate(
            @RequestParam String code,
            @RequestParam(defaultValue = "0") BigDecimal orderAmount,
            @RequestParam(required = false) UUID companyId) {
        return ApiResponse.of(discountService.validate(code, orderAmount, companyId));
    }
}
