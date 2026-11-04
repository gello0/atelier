# Security Specification: Atelier Digital Closet

## 1. Data Invariants

1. **Strict Multi-Tenant Ownership**: Every `ClosetItem`, `Outfit`, and `WearLog` lives under `/users/{userId}/...` and its `userId` field must strictly match both the path parameter `userId` and `request.auth.uid`.
2. **Immutable Provenance**: Once created, `userId` and `createdAt` cannot be modified on any document.
3. **Bounded Payloads**: All strings and arrays have explicit size bounds (`name <= 100`, `notes <= 500`, `imageBase64 <= 120000`, `occasions <= 10`, `seasons <= 6`, `itemIds <= 12`).
4. **Query Enforcer**: Every `list` rule validates `request.auth.uid == userId && resource.data.userId == request.auth.uid`.

## 2. The "Dirty Dozen" Payloads

1. **Unauthenticated Read**: `auth = null` attempting `get` or `list` on `/users/alice_123/closetItems`.
2. **Cross-User Read**: `auth.uid = "bob_456"` attempting `get` on `/users/alice_123/closetItems/item_1`.
3. **Identity Spoofing on Create**: `auth.uid = "alice_123"` creating `/users/alice_123/closetItems/item_1` with `userId: "bob_456"`.
4. **Ghost Field Injection (Shadow Create/Update)**: Payload containing unauthorized key `isAdmin: true` or `isVerified: true`.
5. **Immutable Field Mutation**: Updating `createdAt` or `userId` on an existing `ClosetItem`.
6. **Enum Poisoning**: Creating a `ClosetItem` with `category: "INVALID_CATEGORY"`.
7. **Warmth Out of Range**: Creating a `ClosetItem` with `warmthLevel: 99` (must be 1..5).
8. **Array Overflow**: Creating an `Outfit` with 50 `itemIds` or empty `itemIds: []`.
9. **Oversized String**: Creating a `ClosetItem` with `name` of 500 characters.
10. **Invalid Date Format**: Creating a `WearLog` with `dateIso: "10/30/2026"` instead of `YYYY-MM-DD`.
11. **Future Timestamp Spoofing**: Creating a document with `createdAt` in the future (`> request.time`).
12. **Unfiltered List Query**: Querying a collection without matching `userId == request.auth.uid`.
