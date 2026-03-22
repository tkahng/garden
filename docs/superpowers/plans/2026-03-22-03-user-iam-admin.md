# Garden Plan 3: Account Management + Admin User/IAM Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement own-account profile/address management, admin user management, and admin IAM (role/permission CRUD) on top of the Plan 2 auth foundation.

**Architecture:** Package-by-feature under `io.k2dv.garden`. Three new feature packages: `account`, `admin.user`, `admin.iam`. `AccountService` owns profile + address logic with transactional default-swap. `AdminUserService` uses Spring Data `JpaSpecificationExecutor` for dynamic filtering. `AdminIamService` wraps role/permission CRUD with a guard against deleting predefined roles. All controllers use `@HasPermission` or `@Authenticated` from Plan 2.

**Tech Stack:** Spring Boot 4.0.4 · Spring Security 7 · Spring Data JPA (JpaSpecificationExecutor) · JUnit 5 · Testcontainers · MockMvc

**Spec:** `docs/superpowers/specs/2026-03-22-plan3-user-iam-admin-design.md`

**Note on `@WebMvcTest` slices:** Always use `@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})`. Use `@MockitoBean` from `org.springframework.test.context.bean.override.mockito`. Use `new ObjectMapper()` directly — do not `@Autowired` it. Import path for `@WebMvcTest`: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`.

---

## File Map

### New files — main sources

| File | Responsibility |
|------|----------------|
| `src/main/java/io/k2dv/garden/shared/dto/PagedResult.java` | Generic paged response (content + PageMeta) |
| `src/main/java/io/k2dv/garden/account/dto/AccountResponse.java` | Own profile response DTO |
| `src/main/java/io/k2dv/garden/account/dto/UpdateAccountRequest.java` | Own profile update DTO |
| `src/main/java/io/k2dv/garden/account/dto/AddressResponse.java` | Address response DTO |
| `src/main/java/io/k2dv/garden/account/dto/AddressRequest.java` | Address create/update DTO |
| `src/main/java/io/k2dv/garden/account/service/AccountService.java` | Profile + address CRUD |
| `src/main/java/io/k2dv/garden/account/controller/AccountController.java` | `/api/v1/account` endpoints |
| `src/main/java/io/k2dv/garden/admin/user/dto/AdminUserResponse.java` | Admin user response DTO |
| `src/main/java/io/k2dv/garden/admin/user/dto/UpdateUserRequest.java` | Admin user update DTO |
| `src/main/java/io/k2dv/garden/admin/user/dto/AssignRoleRequest.java` | Role assignment DTO |
| `src/main/java/io/k2dv/garden/admin/user/dto/UserFilter.java` | Filter params for admin user list |
| `src/main/java/io/k2dv/garden/admin/user/service/AdminUserService.java` | Admin user management |
| `src/main/java/io/k2dv/garden/admin/user/controller/AdminUserController.java` | `/api/v1/admin/users` endpoints |
| `src/main/java/io/k2dv/garden/admin/iam/dto/RoleResponse.java` | Role response DTO |
| `src/main/java/io/k2dv/garden/admin/iam/dto/PermissionResponse.java` | Permission response DTO |
| `src/main/java/io/k2dv/garden/admin/iam/dto/CreateRoleRequest.java` | Role creation DTO |
| `src/main/java/io/k2dv/garden/admin/iam/dto/UpdateRoleRequest.java` | Role update DTO |
| `src/main/java/io/k2dv/garden/admin/iam/dto/AssignPermissionRequest.java` | Permission assignment DTO |
| `src/main/java/io/k2dv/garden/admin/iam/service/AdminIamService.java` | Role/permission CRUD |
| `src/main/java/io/k2dv/garden/admin/iam/controller/AdminIamController.java` | `/api/v1/admin/iam` endpoints |

### Modified files — main sources

| File | Change |
|------|--------|
| `src/main/java/io/k2dv/garden/user/repository/UserRepository.java` | Add `JpaSpecificationExecutor<User>` |
| `src/main/java/io/k2dv/garden/user/repository/AddressRepository.java` | Add `findByUserIdAndIsDefaultTrue` |

### New files — test sources

| File | Responsibility |
|------|----------------|
| `src/test/java/io/k2dv/garden/account/service/AccountServiceIT.java` | Account + address integration tests |
| `src/test/java/io/k2dv/garden/account/controller/AccountControllerTest.java` | Account web layer tests |
| `src/test/java/io/k2dv/garden/admin/user/service/AdminUserServiceIT.java` | Admin user integration tests |
| `src/test/java/io/k2dv/garden/admin/user/controller/AdminUserControllerTest.java` | Admin user web layer tests |
| `src/test/java/io/k2dv/garden/admin/iam/service/AdminIamServiceIT.java` | Admin IAM integration tests |
| `src/test/java/io/k2dv/garden/admin/iam/controller/AdminIamControllerTest.java` | Admin IAM web layer tests |

---

## Task 1: PagedResult DTO + Repository Updates

**Files:**
- Create: `src/main/java/io/k2dv/garden/shared/dto/PagedResult.java`
- Modify: `src/main/java/io/k2dv/garden/user/repository/UserRepository.java`
- Modify: `src/main/java/io/k2dv/garden/user/repository/AddressRepository.java`

- [ ] **Step 1.1: Create PagedResult**

```java
package io.k2dv.garden.shared.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class PagedResult<T> {

    private final List<T> content;
    private final PageMeta meta;

    public PagedResult(List<T> content, PageMeta meta) {
        this.content = content;
        this.meta = meta;
    }

    public static <T> PagedResult<T> of(org.springframework.data.domain.Page<T> page) {
        return new PagedResult<>(
            page.getContent(),
            PageMeta.builder()
                .page(page.getNumber())
                .pageSize(page.getSize())
                .total(page.getTotalElements())
                .build()
        );
    }
}
```

- [ ] **Step 1.2: Add JpaSpecificationExecutor to UserRepository**

Edit `src/main/java/io/k2dv/garden/user/repository/UserRepository.java`. Change:
```java
public interface UserRepository extends JpaRepository<User, UUID> {
```
to:
```java
public interface UserRepository extends JpaRepository<User, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<User> {
```

- [ ] **Step 1.3: Add findByUserIdAndIsDefaultTrue to AddressRepository**

Edit `src/main/java/io/k2dv/garden/user/repository/AddressRepository.java`:
```java
package io.k2dv.garden.user.repository;

import io.k2dv.garden.user.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {
    List<Address> findByUserId(UUID userId);
    Optional<Address> findByUserIdAndIsDefaultTrue(UUID userId);
}
```

- [ ] **Step 1.4: Verify compile**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 1.5: Commit**

```bash
git add src/main/java/io/k2dv/garden/shared/dto/PagedResult.java \
        src/main/java/io/k2dv/garden/user/repository/UserRepository.java \
        src/main/java/io/k2dv/garden/user/repository/AddressRepository.java
git commit -m "feat(shared): add PagedResult DTO and extend repositories"
```

---

## Task 2: Account DTOs + AccountService

**Files:**
- Create: `src/main/java/io/k2dv/garden/account/dto/AccountResponse.java`
- Create: `src/main/java/io/k2dv/garden/account/dto/UpdateAccountRequest.java`
- Create: `src/main/java/io/k2dv/garden/account/dto/AddressResponse.java`
- Create: `src/main/java/io/k2dv/garden/account/dto/AddressRequest.java`
- Create: `src/main/java/io/k2dv/garden/account/service/AccountService.java`
- Create: `src/test/java/io/k2dv/garden/account/service/AccountServiceIT.java`

- [ ] **Step 2.1: Write failing integration test**

Create `src/test/java/io/k2dv/garden/account/service/AccountServiceIT.java`:

```java
package io.k2dv.garden.account.service;

import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.account.dto.AddressRequest;
import io.k2dv.garden.account.dto.UpdateAccountRequest;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountServiceIT extends AbstractIntegrationTest {

    @Autowired AccountService accountService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @MockitoBean EmailService emailService;

    UUID userId;

    @BeforeEach
    void setup() {
        authService.register(new RegisterRequest("acct@example.com", "password1", "Jane", "Doe"));
        userId = userRepo.findByEmail("acct@example.com").orElseThrow().getId();
    }

    @Test
    void getAccount_returnsProfile() {
        var resp = accountService.getAccount(userId);
        assertThat(resp.email()).isEqualTo("acct@example.com");
        assertThat(resp.firstName()).isEqualTo("Jane");
    }

    @Test
    void updateAccount_changesFields() {
        var req = new UpdateAccountRequest("Janet", "Smith", "+1234567890");
        var resp = accountService.updateAccount(userId, req);
        assertThat(resp.firstName()).isEqualTo("Janet");
        assertThat(resp.lastName()).isEqualTo("Smith");
        assertThat(resp.phone()).isEqualTo("+1234567890");
    }

    @Test
    void createAddress_setsDefault_clearsOld() {
        var addr1 = accountService.createAddress(userId,
            new AddressRequest("Jane", "Doe", null, "1 Main St", null, "Portland", null, "97201", "US", true));
        var addr2 = accountService.createAddress(userId,
            new AddressRequest("Jane", "Doe", null, "2 Oak Ave", null, "Portland", null, "97202", "US", true));

        var addresses = accountService.listAddresses(userId);
        long defaultCount = addresses.stream().filter(a -> a.isDefault()).count();
        assertThat(defaultCount).isEqualTo(1);
        assertThat(addresses.stream().filter(a -> a.isDefault()).findFirst().get().address1())
            .isEqualTo("2 Oak Ave");
    }

    @Test
    void deleteAddress_ownershipCheck() {
        var other = new io.k2dv.garden.user.model.User();
        other.setEmail("other@example.com");
        other.setFirstName("X");
        other.setLastName("Y");
        var otherId = userRepo.saveAndFlush(other).getId();

        var addr = accountService.createAddress(userId,
            new AddressRequest("Jane", "Doe", null, "1 Main St", null, "Portland", null, "97201", "US", false));

        assertThatThrownBy(() -> accountService.deleteAddress(otherId, addr.id()))
            .isInstanceOf(ForbiddenException.class);
    }
}
```

- [ ] **Step 2.2: Run — expect compile failure**

```bash
./mvnw test -pl . -Dtest=AccountServiceIT -q 2>&1 | grep "COMPILATION\|BUILD"
```

Expected: compilation error — `AccountService` not found.

- [ ] **Step 2.3: Create DTOs**

`src/main/java/io/k2dv/garden/account/dto/AccountResponse.java`:
```java
package io.k2dv.garden.account.dto;

import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.model.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String phone,
    UserStatus status,
    Instant emailVerifiedAt
) {
    public static AccountResponse from(User user) {
        return new AccountResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhone(),
            user.getStatus(),
            user.getEmailVerifiedAt()
        );
    }
}
```

`src/main/java/io/k2dv/garden/account/dto/UpdateAccountRequest.java`:
```java
package io.k2dv.garden.account.dto;

public record UpdateAccountRequest(
    String firstName,
    String lastName,
    String phone
) {}
```

`src/main/java/io/k2dv/garden/account/dto/AddressResponse.java`:
```java
package io.k2dv.garden.account.dto;

import io.k2dv.garden.user.model.Address;

import java.util.UUID;

public record AddressResponse(
    UUID id,
    String firstName,
    String lastName,
    String company,
    String address1,
    String address2,
    String city,
    String province,
    String zip,
    String country,
    boolean isDefault
) {
    public static AddressResponse from(Address a) {
        return new AddressResponse(
            a.getId(),
            a.getFirstName(),
            a.getLastName(),
            a.getCompany(),
            a.getAddress1(),
            a.getAddress2(),
            a.getCity(),
            a.getProvince(),
            a.getZip(),
            a.getCountry(),
            a.isDefault()
        );
    }
}
```

`src/main/java/io/k2dv/garden/account/dto/AddressRequest.java`:
```java
package io.k2dv.garden.account.dto;

import jakarta.validation.constraints.NotBlank;

public record AddressRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    String company,
    @NotBlank String address1,
    String address2,
    @NotBlank String city,
    String province,
    @NotBlank String zip,
    @NotBlank String country,
    boolean isDefault
) {}
```

- [ ] **Step 2.4: Implement AccountService**

`src/main/java/io/k2dv/garden/account/service/AccountService.java`:
```java
package io.k2dv.garden.account.service;

import io.k2dv.garden.account.dto.AccountResponse;
import io.k2dv.garden.account.dto.AddressRequest;
import io.k2dv.garden.account.dto.AddressResponse;
import io.k2dv.garden.account.dto.UpdateAccountRequest;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.user.model.Address;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.AddressRepository;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepo;
    private final AddressRepository addressRepo;

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID userId) {
        return AccountResponse.from(findUser(userId));
    }

    @Transactional
    public AccountResponse updateAccount(UUID userId, UpdateAccountRequest req) {
        User user = findUser(userId);
        if (req.firstName() != null) user.setFirstName(req.firstName());
        if (req.lastName() != null) user.setLastName(req.lastName());
        if (req.phone() != null) user.setPhone(req.phone());
        return AccountResponse.from(userRepo.save(user));
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(UUID userId) {
        return addressRepo.findByUserId(userId).stream()
            .map(AddressResponse::from)
            .toList();
    }

    @Transactional
    public AddressResponse createAddress(UUID userId, AddressRequest req) {
        findUser(userId); // verify user exists
        if (req.isDefault()) {
            clearDefault(userId);
        }
        Address address = new Address();
        address.setUser(userRepo.getReferenceById(userId));
        populateAddress(address, req);
        return AddressResponse.from(addressRepo.save(address));
    }

    @Transactional
    public AddressResponse updateAddress(UUID userId, UUID addressId, AddressRequest req) {
        Address address = findAddress(addressId);
        verifyOwnership(userId, address);
        if (req.isDefault()) {
            clearDefault(userId);
        }
        populateAddress(address, req);
        return AddressResponse.from(addressRepo.save(address));
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        Address address = findAddress(addressId);
        verifyOwnership(userId, address);
        addressRepo.delete(address);
    }

    private User findUser(UUID userId) {
        return userRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
    }

    private Address findAddress(UUID addressId) {
        return addressRepo.findById(addressId)
            .orElseThrow(() -> new NotFoundException("ADDRESS_NOT_FOUND", "Address not found"));
    }

    private void verifyOwnership(UUID userId, Address address) {
        if (!address.getUser().getId().equals(userId)) {
            throw new ForbiddenException("ACCESS_DENIED", "Address does not belong to this user");
        }
    }

    private void clearDefault(UUID userId) {
        addressRepo.findByUserIdAndIsDefaultTrue(userId).ifPresent(a -> {
            a.setDefault(false);
            addressRepo.save(a);
        });
    }

    private void populateAddress(Address address, AddressRequest req) {
        address.setFirstName(req.firstName());
        address.setLastName(req.lastName());
        address.setCompany(req.company());
        address.setAddress1(req.address1());
        address.setAddress2(req.address2());
        address.setCity(req.city());
        address.setProvince(req.province());
        address.setZip(req.zip());
        address.setCountry(req.country());
        address.setDefault(req.isDefault());
    }
}
```

**Note:** `Address.setDefault(boolean)` — Lombok generates `setDefault` from the field `isDefault` (boolean primitive with field name `isDefault`). Verify the generated setter name by checking the entity. If Lombok generates `setIsDefault`, use that instead.

- [ ] **Step 2.5: Run — expect pass**

```bash
./mvnw test -pl . -Dtest=AccountServiceIT -q 2>&1 | grep -E "Tests run|BUILD"
```

Expected: `Tests run: 4, Failures: 0` and `BUILD SUCCESS`.

- [ ] **Step 2.6: Commit**

```bash
git add src/main/java/io/k2dv/garden/account/ \
        src/test/java/io/k2dv/garden/account/service/
git commit -m "feat(account): add AccountService with profile and address management"
```

---

## Task 3: AccountController

**Files:**
- Create: `src/main/java/io/k2dv/garden/account/controller/AccountController.java`
- Create: `src/test/java/io/k2dv/garden/account/controller/AccountControllerTest.java`

- [ ] **Step 3.1: Write failing slice test**

Create `src/test/java/io/k2dv/garden/account/controller/AccountControllerTest.java`:

```java
package io.k2dv.garden.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.account.dto.AccountResponse;
import io.k2dv.garden.account.dto.AddressRequest;
import io.k2dv.garden.account.dto.AddressResponse;
import io.k2dv.garden.account.service.AccountService;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.user.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AccountController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AccountControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AccountService accountService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID userId = UUID.randomUUID();

    @Test
    void getAccount_returns200() throws Exception {
        var resp = new AccountResponse(userId, "user@example.com", "Jane", "Doe", null, UserStatus.ACTIVE, null);
        when(accountService.getAccount(any())).thenReturn(resp);

        mvc.perform(get("/api/v1/account"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("user@example.com"));
    }

    @Test
    void listAddresses_returns200() throws Exception {
        when(accountService.listAddresses(any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/account/addresses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void createAddress_invalidBody_returns400() throws Exception {
        // missing required fields
        mvc.perform(post("/api/v1/account/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 3.2: Run — expect compile failure**

```bash
./mvnw test -pl . -Dtest=AccountControllerTest -q 2>&1 | grep "COMPILATION\|BUILD"
```

- [ ] **Step 3.3: Implement AccountController**

`src/main/java/io/k2dv/garden/account/controller/AccountController.java`:

```java
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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
```

**Note:** `@Authenticated` on the class applies to all methods. `@CurrentUser` resolves the user from the JWT via `CurrentUserArgumentResolver`. In `@WebMvcTest` slices, `TestSecurityConfig` permits all so `@CurrentUser` injection won't be called — mock the service return directly.

- [ ] **Step 3.4: Run — expect pass**

```bash
./mvnw test -pl . -Dtest=AccountControllerTest -q 2>&1 | grep -E "Tests run|BUILD"
```

Expected: `Tests run: 3, Failures: 0` and `BUILD SUCCESS`.

- [ ] **Step 3.5: Commit**

```bash
git add src/main/java/io/k2dv/garden/account/controller/ \
        src/test/java/io/k2dv/garden/account/controller/
git commit -m "feat(account): add AccountController for profile and address endpoints"
```

---

## Task 4: Admin User DTOs + AdminUserService

**Files:**
- Create: `src/main/java/io/k2dv/garden/admin/user/dto/AdminUserResponse.java`
- Create: `src/main/java/io/k2dv/garden/admin/user/dto/UpdateUserRequest.java`
- Create: `src/main/java/io/k2dv/garden/admin/user/dto/AssignRoleRequest.java`
- Create: `src/main/java/io/k2dv/garden/admin/user/service/AdminUserService.java`
- Create: `src/test/java/io/k2dv/garden/admin/user/service/AdminUserServiceIT.java`

- [ ] **Step 4.1: Write failing integration test**

Create `src/test/java/io/k2dv/garden/admin/user/service/AdminUserServiceIT.java`:

```java
package io.k2dv.garden.admin.user.service;

import io.k2dv.garden.admin.user.dto.UserFilter;
import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.user.model.UserStatus;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUserServiceIT extends AbstractIntegrationTest {

    @Autowired AdminUserService adminUserService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @MockitoBean EmailService emailService;

    UUID userId;

    @BeforeEach
    void setup() {
        authService.register(new RegisterRequest("admin-test@example.com", "password1", "Test", "User"));
        userId = userRepo.findByEmail("admin-test@example.com").orElseThrow().getId();
    }

    @Test
    void listUsers_returnsPage() {
        var page = adminUserService.listUsers(new UserFilter(null, null), PageRequest.of(0, 20));
        assertThat(page.content()).isNotEmpty();
        assertThat(page.meta().getTotal()).isGreaterThan(0);
    }

    @Test
    void listUsers_filterByEmail_returnsMatch() {
        var page = adminUserService.listUsers(new UserFilter(null, "admin-test"), PageRequest.of(0, 20));
        assertThat(page.content()).anyMatch(u -> u.email().equals("admin-test@example.com"));
    }

    @Test
    void getUser_returnsWithRoles() {
        var user = adminUserService.getUser(userId);
        assertThat(user.email()).isEqualTo("admin-test@example.com");
        assertThat(user.roles()).contains("CUSTOMER");
    }

    @Test
    void suspendAndReactivate_changesStatus() {
        adminUserService.suspendUser(userId);
        assertThat(userRepo.findById(userId).get().getStatus()).isEqualTo(UserStatus.SUSPENDED);

        adminUserService.reactivateUser(userId);
        assertThat(userRepo.findById(userId).get().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void assignAndRemoveRole() {
        adminUserService.assignRole(userId, "STAFF");
        var roles = userRepo.findRoleNamesByUserId(userId);
        assertThat(roles).contains("STAFF");

        adminUserService.removeRole(userId, "STAFF");
        var rolesAfter = userRepo.findRoleNamesByUserId(userId);
        assertThat(rolesAfter).doesNotContain("STAFF");
    }
}
```

- [ ] **Step 4.2: Run — expect compile failure**

```bash
./mvnw test -pl . -Dtest=AdminUserServiceIT -q 2>&1 | grep "COMPILATION\|BUILD"
```

- [ ] **Step 4.3: Create DTOs**

`src/main/java/io/k2dv/garden/admin/user/dto/AdminUserResponse.java`:
```java
package io.k2dv.garden.admin.user.dto;

import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.model.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String phone,
    UserStatus status,
    Instant emailVerifiedAt,
    Instant createdAt,
    List<String> roles
) {
    public static AdminUserResponse from(User user, List<String> roles) {
        return new AdminUserResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhone(),
            user.getStatus(),
            user.getEmailVerifiedAt(),
            user.getCreatedAt(),
            roles
        );
    }
}
```

`src/main/java/io/k2dv/garden/admin/user/dto/UpdateUserRequest.java`:
```java
package io.k2dv.garden.admin.user.dto;

public record UpdateUserRequest(
    String firstName,
    String lastName,
    String phone,
    String email
) {}
```

`src/main/java/io/k2dv/garden/admin/user/dto/AssignRoleRequest.java`:
```java
package io.k2dv.garden.admin.user.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignRoleRequest(@NotBlank String roleName) {}
```

`src/main/java/io/k2dv/garden/admin/user/dto/UserFilter.java`:
```java
package io.k2dv.garden.admin.user.dto;

import io.k2dv.garden.user.model.UserStatus;

public record UserFilter(UserStatus status, String email) {}
```

- [ ] **Step 4.4: Implement AdminUserService**

`src/main/java/io/k2dv/garden/admin/user/service/AdminUserService.java`:

```java
package io.k2dv.garden.admin.user.service;

import io.k2dv.garden.admin.user.dto.AdminUserResponse;
import io.k2dv.garden.admin.user.dto.UpdateUserRequest;
import io.k2dv.garden.admin.user.dto.UserFilter;
import io.k2dv.garden.iam.service.IamService;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.model.UserStatus;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepo;
    private final IamService iamService;

    @Transactional(readOnly = true)
    public PagedResult<AdminUserResponse> listUsers(UserFilter filter, Pageable pageable) {
        Specification<User> spec = Specification.where(null);
        if (filter.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (filter.email() != null && !filter.email().isBlank()) {
            spec = spec.and((root, query, cb) ->
                cb.like(cb.lower(root.get("email")), "%" + filter.email().toLowerCase() + "%"));
        }
        return PagedResult.of(userRepo.findAll(spec, pageable)
            .map(u -> AdminUserResponse.from(u, userRepo.findRoleNamesByUserId(u.getId()))));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(UUID id) {
        User user = findUser(id);
        return AdminUserResponse.from(user, userRepo.findRoleNamesByUserId(id));
    }

    @Transactional
    public AdminUserResponse updateUser(UUID id, UpdateUserRequest req) {
        User user = findUser(id);
        if (req.firstName() != null) user.setFirstName(req.firstName());
        if (req.lastName() != null) user.setLastName(req.lastName());
        if (req.phone() != null) user.setPhone(req.phone());
        if (req.email() != null) user.setEmail(req.email());
        user = userRepo.save(user);
        return AdminUserResponse.from(user, userRepo.findRoleNamesByUserId(id));
    }

    @Transactional
    public void suspendUser(UUID id) {
        User user = findUser(id);
        user.setStatus(UserStatus.SUSPENDED);
        userRepo.save(user);
    }

    @Transactional
    public void reactivateUser(UUID id) {
        User user = findUser(id);
        user.setStatus(UserStatus.ACTIVE);
        userRepo.save(user);
    }

    @Transactional
    public void assignRole(UUID userId, String roleName) {
        iamService.assignRoleByName(userId, roleName);
    }

    @Transactional
    public void removeRole(UUID userId, String roleName) {
        iamService.removeRoleByName(userId, roleName);
    }

    private User findUser(UUID id) {
        return userRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
    }
}
```

- [ ] **Step 4.5: Run — expect pass**

```bash
./mvnw test -pl . -Dtest=AdminUserServiceIT -q 2>&1 | grep -E "Tests run|BUILD"
```

Expected: `Tests run: 5, Failures: 0` and `BUILD SUCCESS`.

- [ ] **Step 4.6: Commit**

```bash
git add src/main/java/io/k2dv/garden/admin/user/ \
        src/test/java/io/k2dv/garden/admin/user/service/
git commit -m "feat(admin): add AdminUserService with pagination, filtering, suspend/reactivate"
```

---

## Task 5: AdminUserController

**Files:**
- Create: `src/main/java/io/k2dv/garden/admin/user/controller/AdminUserController.java`
- Create: `src/test/java/io/k2dv/garden/admin/user/controller/AdminUserControllerTest.java`

- [ ] **Step 5.1: Write failing slice test**

Create `src/test/java/io/k2dv/garden/admin/user/controller/AdminUserControllerTest.java`:

```java
package io.k2dv.garden.admin.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.admin.user.dto.AdminUserResponse;
import io.k2dv.garden.admin.user.dto.AssignRoleRequest;
import io.k2dv.garden.admin.user.service.AdminUserService;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.user.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminUserController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminUserControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AdminUserService adminUserService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listUsers_returns200WithPage() throws Exception {
        var user = new AdminUserResponse(UUID.randomUUID(), "u@example.com", "U", "U",
            null, UserStatus.ACTIVE, null, null, List.of("CUSTOMER"));
        var paged = new PagedResult<>(List.of(user), PageMeta.builder().page(0).pageSize(20).total(1).build());
        when(adminUserService.listUsers(any(), any())).thenReturn(paged);

        mvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].email").value("u@example.com"))
            .andExpect(jsonPath("$.data.meta.total").value(1));
    }

    @Test
    void assignRole_invalidBody_returns400() throws Exception {
        mvc.perform(post("/api/v1/admin/users/{id}/roles", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void suspendUser_returns204() throws Exception {
        mvc.perform(put("/api/v1/admin/users/{id}/suspend", UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }
}
```

- [ ] **Step 5.2: Run — expect compile failure**

```bash
./mvnw test -pl . -Dtest=AdminUserControllerTest -q 2>&1 | grep "COMPILATION\|BUILD"
```

- [ ] **Step 5.3: Implement AdminUserController**

`src/main/java/io/k2dv/garden/admin/user/controller/AdminUserController.java`:

```java
package io.k2dv.garden.admin.user.controller;

import io.k2dv.garden.admin.user.dto.AdminUserResponse;
import io.k2dv.garden.admin.user.dto.AssignRoleRequest;
import io.k2dv.garden.admin.user.dto.UpdateUserRequest;
import io.k2dv.garden.admin.user.dto.UserFilter;
import io.k2dv.garden.admin.user.service.AdminUserService;
import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.user.model.UserStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @HasPermission("user:read")
    public ApiResponse<PagedResult<AdminUserResponse>> listUsers(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        return ApiResponse.of(adminUserService.listUsers(new UserFilter(status, email), PageRequest.of(page, clampedSize)));
    }

    @GetMapping("/{id}")
    @HasPermission("user:read")
    public ApiResponse<AdminUserResponse> getUser(@PathVariable UUID id) {
        return ApiResponse.of(adminUserService.getUser(id));
    }

    @PutMapping("/{id}")
    @HasPermission("user:write")
    public ApiResponse<AdminUserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest req) {
        return ApiResponse.of(adminUserService.updateUser(id, req));
    }

    @PutMapping("/{id}/suspend")
    @HasPermission("staff:manage")
    public ResponseEntity<Void> suspendUser(@PathVariable UUID id) {
        adminUserService.suspendUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reactivate")
    @HasPermission("staff:manage")
    public ResponseEntity<Void> reactivateUser(@PathVariable UUID id) {
        adminUserService.reactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/roles")
    @HasPermission("iam:manage")
    public ResponseEntity<Void> assignRole(
            @PathVariable UUID id,
            @Valid @RequestBody AssignRoleRequest req) {
        adminUserService.assignRole(id, req.roleName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/roles/{roleName}")
    @HasPermission("iam:manage")
    public ResponseEntity<Void> removeRole(
            @PathVariable UUID id,
            @PathVariable String roleName) {
        adminUserService.removeRole(id, roleName);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 5.4: Run — expect pass**

```bash
./mvnw test -pl . -Dtest=AdminUserControllerTest -q 2>&1 | grep -E "Tests run|BUILD"
```

Expected: `Tests run: 3, Failures: 0` and `BUILD SUCCESS`.

- [ ] **Step 5.5: Commit**

```bash
git add src/main/java/io/k2dv/garden/admin/user/controller/ \
        src/test/java/io/k2dv/garden/admin/user/controller/
git commit -m "feat(admin): add AdminUserController for /api/v1/admin/users endpoints"
```

---

## Task 6: Admin IAM DTOs + AdminIamService

**Files:**
- Create: `src/main/java/io/k2dv/garden/admin/iam/dto/RoleResponse.java`
- Create: `src/main/java/io/k2dv/garden/admin/iam/dto/PermissionResponse.java`
- Create: `src/main/java/io/k2dv/garden/admin/iam/dto/CreateRoleRequest.java`
- Create: `src/main/java/io/k2dv/garden/admin/iam/dto/UpdateRoleRequest.java`
- Create: `src/main/java/io/k2dv/garden/admin/iam/dto/AssignPermissionRequest.java`
- Create: `src/main/java/io/k2dv/garden/admin/iam/service/AdminIamService.java`
- Create: `src/test/java/io/k2dv/garden/admin/iam/service/AdminIamServiceIT.java`

- [ ] **Step 6.1: Write failing integration test**

Create `src/test/java/io/k2dv/garden/admin/iam/service/AdminIamServiceIT.java`:

```java
package io.k2dv.garden.admin.iam.service;

import io.k2dv.garden.admin.iam.dto.CreateRoleRequest;
import io.k2dv.garden.admin.iam.dto.AssignPermissionRequest;
import io.k2dv.garden.iam.repository.PermissionRepository;
import io.k2dv.garden.iam.repository.RoleRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminIamServiceIT extends AbstractIntegrationTest {

    @Autowired AdminIamService adminIamService;
    @Autowired RoleRepository roleRepo;
    @Autowired PermissionRepository permissionRepo;

    @Test
    void listRoles_returnsAllSeededRoles() {
        var roles = adminIamService.listRoles();
        assertThat(roles).extracting("name")
            .contains("CUSTOMER", "STAFF", "MANAGER", "OWNER");
    }

    @Test
    void createRole_thenListContainsIt() {
        adminIamService.createRole(new CreateRoleRequest("ANALYST", "Read-only analyst"));
        var roles = adminIamService.listRoles();
        assertThat(roles).extracting("name").contains("ANALYST");
    }

    @Test
    void createRole_duplicateName_throwsConflict() {
        adminIamService.createRole(new CreateRoleRequest("TESTER", null));
        assertThatThrownBy(() -> adminIamService.createRole(new CreateRoleRequest("TESTER", null)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteRole_predefined_throwsForbidden() {
        var owner = roleRepo.findByName("OWNER").orElseThrow();
        assertThatThrownBy(() -> adminIamService.deleteRole(owner.getId()))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteRole_custom_succeeds() {
        var role = adminIamService.createRole(new CreateRoleRequest("CUSTOM_DELETE", null));
        adminIamService.deleteRole(role.id());
        assertThat(roleRepo.findByName("CUSTOM_DELETE")).isEmpty();
    }

    @Test
    void assignAndRemovePermission() {
        var role = adminIamService.createRole(new CreateRoleRequest("CUSTOM_PERMS", null));
        var perm = permissionRepo.findAll().stream()
            .filter(p -> p.getName().equals("product:read"))
            .findFirst().orElseThrow();

        var updated = adminIamService.assignPermission(role.id(), new AssignPermissionRequest(perm.getId()));
        assertThat(updated.permissions()).extracting("name").contains("product:read");

        adminIamService.removePermission(role.id(), perm.getId());
        var afterRemove = adminIamService.listRoles().stream()
            .filter(r -> r.name().equals("CUSTOM_PERMS")).findFirst().orElseThrow();
        assertThat(afterRemove.permissions()).extracting("name").doesNotContain("product:read");
    }

    @Test
    void listPermissions_returns14() {
        var perms = adminIamService.listPermissions();
        assertThat(perms).hasSize(14);
    }
}
```

- [ ] **Step 6.2: Run — expect compile failure**

```bash
./mvnw test -pl . -Dtest=AdminIamServiceIT -q 2>&1 | grep "COMPILATION\|BUILD"
```

- [ ] **Step 6.3: Create DTOs**

`src/main/java/io/k2dv/garden/admin/iam/dto/PermissionResponse.java`:
```java
package io.k2dv.garden.admin.iam.dto;

import io.k2dv.garden.iam.model.Permission;

import java.util.UUID;

public record PermissionResponse(UUID id, String name, String resource, String action) {
    public static PermissionResponse from(Permission p) {
        return new PermissionResponse(p.getId(), p.getName(), p.getResource(), p.getAction());
    }
}
```

`src/main/java/io/k2dv/garden/admin/iam/dto/RoleResponse.java`:
```java
package io.k2dv.garden.admin.iam.dto;

import io.k2dv.garden.iam.model.Role;

import java.util.List;
import java.util.UUID;

public record RoleResponse(UUID id, String name, String description, List<PermissionResponse> permissions) {
    public static RoleResponse from(Role role) {
        return new RoleResponse(
            role.getId(),
            role.getName(),
            role.getDescription(),
            role.getPermissions().stream().map(PermissionResponse::from).toList()
        );
    }
}
```

`src/main/java/io/k2dv/garden/admin/iam/dto/CreateRoleRequest.java`:
```java
package io.k2dv.garden.admin.iam.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRoleRequest(@NotBlank String name, String description) {}
```

`src/main/java/io/k2dv/garden/admin/iam/dto/UpdateRoleRequest.java`:
```java
package io.k2dv.garden.admin.iam.dto;

public record UpdateRoleRequest(String name, String description) {}
```

`src/main/java/io/k2dv/garden/admin/iam/dto/AssignPermissionRequest.java`:
```java
package io.k2dv.garden.admin.iam.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignPermissionRequest(@NotNull UUID permissionId) {}
```

- [ ] **Step 6.4: Implement AdminIamService**

`src/main/java/io/k2dv/garden/admin/iam/service/AdminIamService.java`:

```java
package io.k2dv.garden.admin.iam.service;

import io.k2dv.garden.admin.iam.dto.*;
import io.k2dv.garden.iam.model.Role;
import io.k2dv.garden.iam.repository.PermissionRepository;
import io.k2dv.garden.iam.repository.RoleRepository;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminIamService {

    private static final Set<String> PREDEFINED_ROLES = Set.of("CUSTOMER", "STAFF", "MANAGER", "OWNER");

    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepo.findAll().stream().map(RoleResponse::from).toList();
    }

    @Transactional
    public RoleResponse createRole(CreateRoleRequest req) {
        if (roleRepo.findByName(req.name()).isPresent()) {
            throw new ConflictException("ROLE_NAME_TAKEN", "A role with this name already exists");
        }
        Role role = new Role();
        role.setName(req.name());
        role.setDescription(req.description());
        return RoleResponse.from(roleRepo.save(role));
    }

    @Transactional
    public RoleResponse updateRole(UUID id, UpdateRoleRequest req) {
        Role role = findRole(id);
        if (req.name() != null) role.setName(req.name());
        if (req.description() != null) role.setDescription(req.description());
        return RoleResponse.from(roleRepo.save(role));
    }

    @Transactional
    public void deleteRole(UUID id) {
        Role role = findRole(id);
        if (PREDEFINED_ROLES.contains(role.getName())) {
            throw new ForbiddenException("PREDEFINED_ROLE", "Predefined roles cannot be deleted");
        }
        roleRepo.delete(role);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepo.findAll().stream().map(PermissionResponse::from).toList();
    }

    @Transactional
    public RoleResponse assignPermission(UUID roleId, AssignPermissionRequest req) {
        Role role = findRole(roleId);
        var permission = permissionRepo.findById(req.permissionId())
            .orElseThrow(() -> new NotFoundException("PERMISSION_NOT_FOUND", "Permission not found"));
        role.getPermissions().add(permission);
        return RoleResponse.from(roleRepo.save(role));
    }

    @Transactional
    public void removePermission(UUID roleId, UUID permissionId) {
        Role role = findRole(roleId);
        role.getPermissions().removeIf(p -> p.getId().equals(permissionId));
        roleRepo.save(role);
    }

    private Role findRole(UUID id) {
        return roleRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("ROLE_NOT_FOUND", "Role not found"));
    }
}
```

- [ ] **Step 6.5: Run — expect pass**

```bash
./mvnw test -pl . -Dtest=AdminIamServiceIT -q 2>&1 | grep -E "Tests run|BUILD"
```

Expected: `Tests run: 7, Failures: 0` and `BUILD SUCCESS`.

- [ ] **Step 6.6: Commit**

```bash
git add src/main/java/io/k2dv/garden/admin/iam/ \
        src/test/java/io/k2dv/garden/admin/iam/service/
git commit -m "feat(admin): add AdminIamService with role/permission CRUD and predefined role guard"
```

---

## Task 7: AdminIamController

**Files:**
- Create: `src/main/java/io/k2dv/garden/admin/iam/controller/AdminIamController.java`
- Create: `src/test/java/io/k2dv/garden/admin/iam/controller/AdminIamControllerTest.java`

- [ ] **Step 7.1: Write failing slice test**

Create `src/test/java/io/k2dv/garden/admin/iam/controller/AdminIamControllerTest.java`:

```java
package io.k2dv.garden.admin.iam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.admin.iam.dto.CreateRoleRequest;
import io.k2dv.garden.admin.iam.dto.PermissionResponse;
import io.k2dv.garden.admin.iam.dto.RoleResponse;
import io.k2dv.garden.admin.iam.service.AdminIamService;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminIamController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminIamControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AdminIamService adminIamService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listRoles_returns200() throws Exception {
        when(adminIamService.listRoles()).thenReturn(List.of(
            new RoleResponse(UUID.randomUUID(), "CUSTOMER", "Storefront customer", List.of())));

        mvc.perform(get("/api/v1/admin/iam/roles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("CUSTOMER"));
    }

    @Test
    void createRole_validBody_returns200() throws Exception {
        var roleId = UUID.randomUUID();
        when(adminIamService.createRole(any()))
            .thenReturn(new RoleResponse(roleId, "ANALYST", "Analyst role", List.of()));

        mvc.perform(post("/api/v1/admin/iam/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateRoleRequest("ANALYST", "Analyst role"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("ANALYST"));
    }

    @Test
    void createRole_missingName_returns400() throws Exception {
        mvc.perform(post("/api/v1/admin/iam/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listPermissions_returns200() throws Exception {
        when(adminIamService.listPermissions()).thenReturn(List.of(
            new PermissionResponse(UUID.randomUUID(), "product:read", "product", "read")));

        mvc.perform(get("/api/v1/admin/iam/permissions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("product:read"));
    }
}
```

- [ ] **Step 7.2: Run — expect compile failure**

```bash
./mvnw test -pl . -Dtest=AdminIamControllerTest -q 2>&1 | grep "COMPILATION\|BUILD"
```

- [ ] **Step 7.3: Implement AdminIamController**

`src/main/java/io/k2dv/garden/admin/iam/controller/AdminIamController.java`:

```java
package io.k2dv.garden.admin.iam.controller;

import io.k2dv.garden.admin.iam.dto.*;
import io.k2dv.garden.admin.iam.service.AdminIamService;
import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/iam")
@RequiredArgsConstructor
@HasPermission("iam:manage")
public class AdminIamController {

    private final AdminIamService adminIamService;

    @GetMapping("/roles")
    public ApiResponse<List<RoleResponse>> listRoles() {
        return ApiResponse.of(adminIamService.listRoles());
    }

    @PostMapping("/roles")
    public ApiResponse<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest req) {
        return ApiResponse.of(adminIamService.createRole(req));
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<RoleResponse> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest req) {
        return ApiResponse.of(adminIamService.updateRole(id, req));
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        adminIamService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionResponse>> listPermissions() {
        return ApiResponse.of(adminIamService.listPermissions());
    }

    @PostMapping("/roles/{id}/permissions")
    public ApiResponse<RoleResponse> assignPermission(
            @PathVariable UUID id,
            @Valid @RequestBody AssignPermissionRequest req) {
        return ApiResponse.of(adminIamService.assignPermission(id, req));
    }

    @DeleteMapping("/roles/{id}/permissions/{permissionId}")
    public ResponseEntity<Void> removePermission(
            @PathVariable UUID id,
            @PathVariable UUID permissionId) {
        adminIamService.removePermission(id, permissionId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 7.4: Run — expect pass**

```bash
./mvnw test -pl . -Dtest=AdminIamControllerTest -q 2>&1 | grep -E "Tests run|BUILD"
```

Expected: `Tests run: 4, Failures: 0` and `BUILD SUCCESS`.

- [ ] **Step 7.5: Commit**

```bash
git add src/main/java/io/k2dv/garden/admin/iam/controller/ \
        src/test/java/io/k2dv/garden/admin/iam/controller/
git commit -m "feat(admin): add AdminIamController for /api/v1/admin/iam endpoints"
```

---

## Task 8: Full Test Suite

- [ ] **Step 8.1: Run all tests**

```bash
./mvnw clean test 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`. All tests pass including all previous tests plus:
- `AccountServiceIT` (4 tests)
- `AccountControllerTest` (3 tests)
- `AdminUserServiceIT` (5 tests)
- `AdminUserControllerTest` (3 tests)
- `AdminIamServiceIT` (7 tests)
- `AdminIamControllerTest` (4 tests)

- [ ] **Step 8.2: If any test fails, diagnose**

If `BUILD FAILURE`, run the failing test class in isolation to see full output:

```bash
./mvnw test -pl . -Dtest=<FailingTestClass> 2>&1 | grep -A 20 "FAILED\|Exception\|Error"
```

Common failure causes:
- Missing import for `UserFilter` → add `import io.k2dv.garden.admin.user.dto.UserFilter;`
- `@MockitoBean` import wrong package → must be `org.springframework.test.context.bean.override.mockito`
- `ObjectMapper` not found in `@WebMvcTest` → use `new ObjectMapper()`, not `@Autowired`
- Flyway migration conflict → check migration files V1–V8 are all present and in order

- [ ] **Step 8.3: Re-run after each fix**

```bash
./mvnw test -pl . -Dtest=<FixedTestClass> -q 2>&1 | grep -E "Tests run|BUILD"
```

Repeat until all classes pass, then re-run the full suite.

- [ ] **Step 8.4: Commit final state**

```bash
git add -A
git commit -m "test: verify full Plan 3 test suite passes"
```

---

## Done

Plan 3 is complete. The project now has:

- `PagedResult<T>` — generic paged response wrapper using `PageMeta`
- `AccountService` + `AccountController` — own profile and address CRUD with default-swap logic
- `AdminUserService` + `AdminUserController` — paginated/filtered user list, update, suspend, reactivate, role assignment
- `AdminIamService` + `AdminIamController` — full role CRUD (with predefined role guard), permission listing, assign/remove permissions on roles

**Next plan:** `2026-03-22-04-product-catalog.md` — product variants, options, collections, images.
