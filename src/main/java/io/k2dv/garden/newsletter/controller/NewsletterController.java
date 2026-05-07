package io.k2dv.garden.newsletter.controller;

import io.k2dv.garden.newsletter.dto.SubscribeRequest;
import io.k2dv.garden.newsletter.dto.SubscribeResponse;
import io.k2dv.garden.newsletter.service.NewsletterService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Newsletter", description = "Email newsletter subscription")
@RestController
@RequestMapping("/api/v1/newsletter")
@RequiredArgsConstructor
@SecurityRequirements({})
public class NewsletterController {

    private final NewsletterService newsletterService;

    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<SubscribeResponse>> subscribe(
            @Valid @RequestBody SubscribeRequest req) {
        SubscribeResponse result = newsletterService.subscribe(req);
        HttpStatus status = result.alreadySubscribed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.of(result));
    }
}
