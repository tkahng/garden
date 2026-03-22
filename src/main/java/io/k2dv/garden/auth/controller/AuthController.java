package io.k2dv.garden.auth.controller;

import io.k2dv.garden.auth.dto.*;
import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.user.model.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.of(authService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.of(authService.login(req));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.of(authService.refresh(req));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody PasswordResetRequest req) {
        authService.resendVerification(req.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/request-password-reset")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest req) {
        authService.requestPasswordReset(req.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/confirm-password-reset/{token}")
    public ResponseEntity<Void> confirmPasswordReset(
            @PathVariable String token,
            @Valid @RequestBody PasswordResetConfirmRequest req) {
        authService.confirmPasswordReset(token, req);
        return ResponseEntity.noContent().build();
    }

    @Authenticated
    @PostMapping("/update-password")
    public ResponseEntity<Void> updatePassword(
            @CurrentUser User user,
            @Valid @RequestBody UpdatePasswordRequest req) {
        authService.updatePassword(user.getId(), req);
        return ResponseEntity.noContent().build();
    }
}
