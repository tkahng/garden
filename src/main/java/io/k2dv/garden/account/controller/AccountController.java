package io.k2dv.garden.account.controller;

import io.k2dv.garden.account.dto.AccountResponse;
import io.k2dv.garden.account.dto.AddressRequest;
import io.k2dv.garden.account.dto.AddressResponse;
import io.k2dv.garden.account.dto.UpdateAccountRequest;
import io.k2dv.garden.account.service.AccountService;
import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.user.model.User;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Account", description = "User account and address management")
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@Authenticated
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ApiResponse<AccountResponse> getAccount(@CurrentUser User user) {
        return ApiResponse.of(accountService.getAccount(user.getId()));
    }

    @PutMapping
    public ApiResponse<AccountResponse> updateAccount(
            @CurrentUser User user,
            @Valid @RequestBody UpdateAccountRequest req) {
        return ApiResponse.of(accountService.updateAccount(user.getId(), req));
    }

    @GetMapping("/addresses")
    public ApiResponse<List<AddressResponse>> listAddresses(@CurrentUser User user) {
        return ApiResponse.of(accountService.listAddresses(user.getId()));
    }

    @PostMapping("/addresses")
    public ApiResponse<AddressResponse> createAddress(
            @CurrentUser User user,
            @Valid @RequestBody AddressRequest req) {
        return ApiResponse.of(accountService.createAddress(user.getId(), req));
    }

    @PutMapping("/addresses/{id}")
    public ApiResponse<AddressResponse> updateAddress(
            @CurrentUser User user,
            @PathVariable UUID id,
            @Valid @RequestBody AddressRequest req) {
        return ApiResponse.of(accountService.updateAddress(user.getId(), id, req));
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(
            @CurrentUser User user,
            @PathVariable UUID id) {
        accountService.deleteAddress(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
