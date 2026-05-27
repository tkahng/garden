# Account, Wishlist & Notification Spec

**Status:** Current  
**Last updated:** 2026-05-26  
**Packages:** `io.k2dv.garden.account`, `io.k2dv.garden.wishlist`, `io.k2dv.garden.notification`

---

## Account

### AccountController (`/api/v1/account`, `@Authenticated`)

| Method | Path | Description |
|---|---|---|
| GET | `/` | Get current user profile |
| PUT | `/` | Update profile (firstName, lastName, phone) |
| GET | `/addresses` | List saved addresses |
| POST | `/addresses` | Add address |
| PUT | `/addresses/{id}` | Update address |
| DELETE | `/addresses/{id}` | Delete address |

**AccountService rules:**
- `updateAddress`: validates ownership (address.userId == caller); if `isDefault = true`, clears default on all other addresses first
- `deleteAddress`: validates ownership

---

## Wishlist

### Wishlist & WishlistItem
`catalog.wishlists`, `catalog.wishlist_items`

- One wishlist per user (`userId` unique on `wishlists`)
- `WishlistItem`: `wishlistId`, `variantId`

### WishlistController (`/api/v1/wishlist`, `@Authenticated`)

| Method | Path | Description |
|---|---|---|
| GET | `/` | Get wishlist with variant details |
| POST | `/` | Add variant to wishlist |
| DELETE | `/{itemId}` | Remove item |

**WishlistService:** `getOrCreateWishlist` — lazy creates. `addItem` is idempotent (no duplicate for same variantId). `removeItem` validates ownership.

---

## Notification Preferences

### NotificationPreference
`auth.notification_preferences`

| Field | Constraint | Notes |
|---|---|---|
| userId | UUID, not null | |
| notificationType | NotificationType, not null | |
| enabled | boolean, default true | |
| updatedAt | Instant | auto-updated |

**Unique index:** `(user_id, notification_type)`

**NotificationType enum:**  
`ORDER_CONFIRMATION` · `ORDER_SHIPPED` · `ORDER_DELIVERED` · `ORDER_CANCELLED` · `QUOTE_UPDATE` · `MARKETING`

### NotificationPreferenceController (`/api/v1/notification-preferences`, `@Authenticated`)

| Method | Path | Description |
|---|---|---|
| GET | `/` | List all preferences for caller |
| PUT | `/{type}` | Enable or disable a notification type |

**NotificationPreferenceGate:** `isNotificationEnabled(userId, type)` — called before each outbound email/push to gate delivery. If no preference record exists, defaults to `enabled = true`.
