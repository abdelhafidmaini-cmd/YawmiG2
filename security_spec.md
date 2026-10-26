# YAWMI Security Specification (Payload-First TDD)

## 1. Data Invariants
1. All user data is strictly isolated under `/users/{userId}/...` where `request.auth != null && request.auth.uid == userId`.
2. Document IDs (`{docId}`, `{recordId}`, `{logId}`, `{workoutId}`, `{taskId}`) must satisfy `isValidId(id)` (`id is string && id.size() <= 128 && id.matches('^[a-zA-Z0-9_\\-]+$')`).
3. Every write (`create` and `update`) must pass a dedicated `isValid*` schema helper enforcing field types, string/number bounds, and allowed keys (`hasOnly`).
4. `createdAt` and `updatedAt` must be server timestamps (`is timestamp && <= request.time`), and `createdAt` is immutable on `update`.

## 2. The "Dirty Dozen" Payloads
1. Unauthenticated read/list on `/users/alice_123/health_records` -> REJECTED
2. Cross-user read by `bob_456` on `/users/alice_123/health_records/rec_1` -> REJECTED
3. Cross-user write by `bob_456` on `/users/alice_123/food_logs/log_1` -> REJECTED
4. Shadow update adding ghost field `isAdmin: true` to `/users/alice_123/health_records/rec_1` -> REJECTED
5. Missing required field `metricType` on `/users/alice_123/health_records/rec_1` -> REJECTED
6. Invalid `metricType` (non-string or > 60 chars) on `health_records` -> REJECTED
7. Negative `calories` or `grams` on `food_logs` -> REJECTED
8. String overflow (> 500 chars) on `foodName` or `healthTip` in `food_logs` -> REJECTED
9. Mutating immutable `createdAt` during `update` on `health_records` or `food_logs` -> REJECTED
10. Future timestamp (`createdAt > request.time`) on `workouts` -> REJECTED
11. Invalid `itemType` enum value on `tasks` -> REJECTED
12. Poisoned document ID with special characters or > 128 chars -> REJECTED
