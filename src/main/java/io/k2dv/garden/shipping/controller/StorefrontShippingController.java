package io.k2dv.garden.shipping.controller;

import io.k2dv.garden.shipping.dto.ShippingRateResponse;
import io.k2dv.garden.shipping.service.ShippingService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Storefront: Shipping", description = "Available shipping rates for an address")
@RestController
@RequestMapping("/api/v1/storefront/shipping")
@RequiredArgsConstructor
@SecurityRequirements({})
public class StorefrontShippingController {

    private final ShippingService shippingService;

    @GetMapping("/rates")
    public ApiResponse<List<ShippingRateResponse>> getRates(
            @RequestParam String country,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) BigDecimal orderAmount) {
        return ApiResponse.of(shippingService.findRatesForAddress(country, province, orderAmount));
    }
}
