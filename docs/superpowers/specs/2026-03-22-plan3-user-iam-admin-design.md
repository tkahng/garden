# Garden Plan 3: Account Management + Admin User/IAM Design

**Date:** 2026-03-22
**Status:** Approved
**Depends on:** Plan 2 (auth, JPA entities, IamService)

---

## Overview

Plan 3 adds three groups of functionality on top of the auth foundation from Plan 2:

1. **Own account** — authenticated users manage their own profile and addresses
2. **Admin users** — privileged users manage other users (list, update, suspend, reactivate, assign/remove roles)
3. **Admin IAM** — owners manage roles and permissions (full role CRUD, assign/remove permissions on roles)

---

## Section 1: Own Account (`/api/v1/account`)

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/account` | Authenticated | Get own profile |
| `PUT` | `/api/v1/account` | Authenticated | Update own profile |
| `GET` | `/api/v1/account/addresses` | Authenticated | List own addresses |
| `POST` | `/api/v1/account/addresses` | Authenticated | Create address |
| `PUT` | `/api/v1/account/addresses/{id}` | Authenticated | Update address |
| `DELETE` | `/api/v1/account/addresses/{id}` | Authenticated | Delete address |

### Address Default Logic

- A user can have many addresses; at most one has `isDefault = true`
- When creating or updating an address with `isDefault = true`, the service clears `isDefault` on all other addresses for that user in the same transaction
- Deleting the default address does not auto-assign a new default — the user must set one explicitly

### DTOs

- `AccountResponse` — id, email, firstName, lastName, phone, status, emailVerifiedAt
- `UpdateAccountRequest` — firstName, lastName, phone (all optional, validated `@NotBlank` if present)
- `AddressResponse` — id, firstName, lastName, company, address1, address2, city, province, zip, country, isDefault
- `AddressRequest` — same fields as response minus id; `isDefault` defaults to false

### Service: `AccountService`

Responsibilities:
- `getAccount(UUID userId)` → `AccountResponse`
- `updateAccount(UUID userId, UpdateAccountRequest)` → `AccountResponse`
- `listAddresses(UUID userId)` → `List<AddressResponse>`
- `createAddress(UUID userId, AddressRequest)` → `AddressResponse` (handles default-swap)
- `updateAddress(UUID userId, UUID addressId, AddressRequest)` → `AddressResponse` (handles default-swap, ownership check)
- `deleteAddress(UUID userId, UUID addressId)` (ownership check)

Ownership check: if the address does not belong to the requesting user, throw `ForbiddenException`.

### Controller: `AccountController`

- `@RequestMapping("/api/v1/account")`
- All methods annotated with `@Authenticated`
- `@CurrentUser User user` parameter resolver used throughout

---

## Section 2: Admin Users (`/api/v1/admin/users`)

### Endpoints

| Method | Path | Permission | Description |
|--------|------|-----------|-------------|
| `GET` | `/api/v1/admin/users` | `user:read` | Paginated user list with filters |
| `GET` | `/api/v1/admin/users/{id}` | `user:read` | Get user with roles |
| `PUT` | `/api/v1/admin/users/{id}` | `user:write` | Update user profile fields |
| `PUT` | `/api/v1/admin/users/{id}/suspend` | `staff:manage` | Set status to SUSPENDED |
| `PUT` | `/api/v1/admin/users/{id}/reactivate` | `staff:manage` | Set status to ACTIVE |
| `POST` | `/api/v1/admin/users/{id}/roles` | `iam:manage` | Assign role to user |
| `DELETE` | `/api/v1/admin/users/{id}/roles/{roleName}` | `iam:manage` | Remove role from user |

### Pagination & Filtering

`GET /api/v1/admin/users` accepts:
- `page` (default 0), `size` (default 20, max 100)
- `status` — filter by `UserStatus` (optional)
- `email` — partial case-insensitive match (optional)

Response: `ApiResponse<PagedResult<AdminUserResponse>>` using `PageMeta` (page, size, totalElements, totalPages).

### DTOs

- `AdminUserResponse` — id, email, firstName, lastName, phone, status, emailVerifiedAt, createdAt, roles (list of role names)
- `UpdateUserRequest` — firstName, lastName, phone, email (all optional)
- `AssignRoleRequest` — roleName (`@NotBlank`)
- `PagedResult<T>` — content (list), meta (`PageMeta`)

### Service: `AdminUserService`

Responsibilities:
- `listUsers(UserFilter filter, Pageable pageable)` → `Page<AdminUserResponse>`
- `getUser(UUID id)` → `AdminUserResponse`
- `updateUser(UUID id, UpdateUserRequest)` → `AdminUserResponse`
- `suspendUser(UUID id)`
- `reactivateUser(UUID id)`
- `assignRole(UUID userId, String roleName)` — delegates to `IamService`
- `removeRole(UUID userId, String roleName)` — delegates to `IamService`

Uses Spring Data JPA `Specification` for dynamic filtering on the user list.

### Controller: `AdminUserController`

- `@RequestMapping("/api/v1/admin/users")`
- Each method uses `@HasPermission` with the appropriate permission string

---

## Section 3: Admin IAM (`/api/v1/admin/iam`)

### Endpoints

| Method | Path | Permission | Description |
|--------|------|-----------|-------------|
| `GET` | `/api/v1/admin/iam/roles` | `iam:manage` | List all roles with permissions |
| `POST` | `/api/v1/admin/iam/roles` | `iam:manage` | Create custom role |
| `PUT` | `/api/v1/admin/iam/roles/{id}` | `iam:manage` | Update role name/description |
| `DELETE` | `/api/v1/admin/iam/roles/{id}` | `iam:manage` | Delete role (custom only) |
| `GET` | `/api/v1/admin/iam/permissions` | `iam:manage` | List all permissions |
| `POST` | `/api/v1/admin/iam/roles/{id}/permissions` | `iam:manage` | Assign permission to role |
| `DELETE` | `/api/v1/admin/iam/roles/{id}/permissions/{permissionId}` | `iam:manage` | Remove permission from role |

### Predefined Role Guard

The four predefined roles (CUSTOMER, STAFF, MANAGER, OWNER) cannot be deleted. Attempting to do so throws `ForbiddenException("PREDEFINED_ROLE", "Predefined roles cannot be deleted")`.

### DTOs

- `RoleResponse` — id, name, description, permissions (list of `PermissionResponse`)
- `PermissionResponse` — id, name, resource, action
- `CreateRoleRequest` — name (`@NotBlank`), description (optional)
- `UpdateRoleRequest` — name (optional), description (optional)
- `AssignPermissionRequest` — permissionId (`@NotNull`)

### Service: `AdminIamService`

Responsibilities:
- `listRoles()` → `List<RoleResponse>`
- `createRole(CreateRoleRequest)` → `RoleResponse` (throws `ConflictException` if name taken)
- `updateRole(UUID id, UpdateRoleRequest)` → `RoleResponse`
- `deleteRole(UUID id)` — guards against predefined role names
- `listPermissions()` → `List<PermissionResponse>`
- `assignPermission(UUID roleId, UUID permissionId)` → `RoleResponse`
- `removePermission(UUID roleId, UUID permissionId)`

### Controller: `AdminIamController`

- `@RequestMapping("/api/v1/admin/iam")`
- All endpoints annotated with `@HasPermission("iam:manage")`

---

## Testing Strategy

### Integration tests (extend `AbstractIntegrationTest`)
- `AccountServiceIT` — profile get/update, address CRUD, default-swap, ownership enforcement
- `AdminUserServiceIT` — list with filters/pagination, suspend/reactivate, role assignment
- `AdminIamServiceIT` — role CRUD, predefined role guard, permission assignment

### Web layer tests (`@WebMvcTest`)
- `AccountControllerTest` — HTTP shape, validation, 401 for unauthenticated
- `AdminUserControllerTest` — HTTP shape, pagination params, permission enforcement
- `AdminIamControllerTest` — HTTP shape, all endpoints

---

## File Map

### New files — main sources

| File | Responsibility |
|------|---------------|
| `src/main/java/io/k2dv/garden/account/dto/AccountResponse.java` | Own profile response |
| `src/main/java/io/k2dv/garden/account/dto/UpdateAccountRequest.java` | Own profile update |
| `src/main/java/io/k2dv/garden/account/dto/AddressResponse.java` | Address response |
| `src/main/java/io/k2dv/garden/account/dto/AddressRequest.java` | Address create/update |
| `src/main/java/io/k2dv/garden/account/service/AccountService.java` | Profile + address logic |
| `src/main/java/io/k2dv/garden/account/controller/AccountController.java` | `/api/v1/account` endpoints |
| `src/main/java/io/k2dv/garden/admin/user/dto/AdminUserResponse.java` | Admin user response |
| `src/main/java/io/k2dv/garden/admin/user/dto/UpdateUserRequest.java` | Admin user update |
| `src/main/java/io/k2dv/garden/admin/user/dto/AssignRoleRequest.java` | Role assignment |
| `src/main/java/io/k2dv/garden/shared/dto/PagedResult.java` | Generic paged response wrapper |
| `src/main/java/io/k2dv/garden/admin/user/service/AdminUserService.java` | Admin user management |
| `src/main/java/io/k2dv/garden/admin/user/controller/AdminUserController.java` | `/api/v1/admin/users` endpoints |
| `src/main/java/io/k2dv/garden/admin/iam/dto/RoleResponse.java` | Role response |
| `src/main/java/io/k2dv/garden/admin/iam/dto/PermissionResponse.java` | Permission response |
| `src/main/java/io/k2dv/garden/admin/iam/dto/CreateRoleRequest.java` | Role creation |
| `src/main/java/io/k2dv/garden/admin/iam/dto/UpdateRoleRequest.java` | Role update |
| `src/main/java/io/k2dv/garden/admin/iam/dto/AssignPermissionRequest.java` | Permission assignment |
| `src/main/java/io/k2dv/garden/admin/iam/service/AdminIamService.java` | Role/permission management |
| `src/main/java/io/k2dv/garden/admin/iam/controller/AdminIamController.java` | `/api/v1/admin/iam` endpoints |

### New files — test sources

| File | Responsibility |
|------|---------------|
| `src/test/java/io/k2dv/garden/account/service/AccountServiceIT.java` | Account + address integration tests |
| `src/test/java/io/k2dv/garden/account/controller/AccountControllerTest.java` | Account web layer tests |
| `src/test/java/io/k2dv/garden/admin/user/service/AdminUserServiceIT.java` | Admin user integration tests |
| `src/test/java/io/k2dv/garden/admin/user/controller/AdminUserControllerTest.java` | Admin user web layer tests |
| `src/test/java/io/k2dv/garden/admin/iam/service/AdminIamServiceIT.java` | Admin IAM integration tests |
| `src/test/java/io/k2dv/garden/admin/iam/controller/AdminIamControllerTest.java` | Admin IAM web layer tests |
