package io.k2dv.garden.giftcard.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.giftcard.dto.GiftCardValidationResponse;
import io.k2dv.garden.giftcard.service.GiftCardService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Storefront: Gift Cards", description = "Gift card balance validation")
@RestController
@RequestMapping("/api/v1/storefront/gift-cards")
@RequiredArgsConstructor
@Authenticated
public class StorefrontGiftCardController {

    private final GiftCardService giftCardService;

    @GetMapping("/validate")
    public ApiResponse<GiftCardValidationResponse> validate(@RequestParam String code) {
        return ApiResponse.of(giftCardService.validate(code));
    }
}
