package io.k2dv.garden.webhook.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.webhook.dto.CreateWebhookEndpointRequest;
import io.k2dv.garden.webhook.dto.UpdateWebhookEndpointRequest;
import io.k2dv.garden.webhook.dto.WebhookDeliveryResponse;
import io.k2dv.garden.webhook.dto.WebhookEndpointResponse;
import io.k2dv.garden.webhook.model.WebhookEventType;
import io.k2dv.garden.webhook.service.OutboundWebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Tag(name = "Admin Webhooks", description = "Outbound webhook endpoint management")
@RestController
@RequestMapping("/api/v1/admin/webhooks")
@RequiredArgsConstructor
public class AdminWebhookController {

    private final OutboundWebhookService webhookService;

    @GetMapping("/events")
    @HasPermission("webhook:read")
    public ResponseEntity<ApiResponse<List<String>>> listEventTypes() {
        List<String> types = Arrays.stream(WebhookEventType.values())
            .map(Enum::name).toList();
        return ResponseEntity.ok(ApiResponse.of(types));
    }

    @PostMapping
    @HasPermission("webhook:write")
    public ResponseEntity<ApiResponse<WebhookEndpointResponse>> create(
            @Valid @RequestBody CreateWebhookEndpointRequest req) {
        return ResponseEntity.ok(ApiResponse.of(webhookService.create(req)));
    }

    @GetMapping
    @HasPermission("webhook:read")
    public ResponseEntity<ApiResponse<List<WebhookEndpointResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.of(webhookService.list()));
    }

    @GetMapping("/{id}")
    @HasPermission("webhook:read")
    public ResponseEntity<ApiResponse<WebhookEndpointResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(webhookService.getById(id)));
    }

    @PutMapping("/{id}")
    @HasPermission("webhook:write")
    public ResponseEntity<ApiResponse<WebhookEndpointResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWebhookEndpointRequest req) {
        return ResponseEntity.ok(ApiResponse.of(webhookService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    @HasPermission("webhook:delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        webhookService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/deliveries")
    @HasPermission("webhook:read")
    public ResponseEntity<ApiResponse<PagedResult<WebhookDeliveryResponse>>> listDeliveries(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.of(webhookService.listDeliveries(id, pageable)));
    }
}
