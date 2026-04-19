package io.k2dv.garden.b2b.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.b2b.dto.InvitationResponse;
import io.k2dv.garden.b2b.service.CompanyInvitationService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.user.model.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Invitations", description = "Company invitation acceptance")
@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final CompanyInvitationService invitationService;

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<InvitationResponse>> get(@PathVariable UUID token) {
        return ResponseEntity.ok(ApiResponse.of(invitationService.getByToken(token)));
    }

    @PostMapping("/{token}/accept")
    @Authenticated
    public ResponseEntity<ApiResponse<InvitationResponse>> accept(
        @PathVariable UUID token,
        @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse.of(invitationService.accept(token, user.getId())));
    }
}
