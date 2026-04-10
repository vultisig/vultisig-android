# Transaction History

## How it works

### Recording

After a successful broadcast in `KeysignViewModel` the app inserts a row into `transaction_history` with status `BROADCASTED`. The primary key is deterministic (`chain:txHash`) so duplicate broadcasts are ignored via `OnConflictStrategy.IGNORE`. The transaction payload (amounts, addresses, tokens) is stored as a JSON blob in the `payload` column using kotlinx.serialization polymorphism (`@SerialName("send")` / `@SerialName("swap")`).

### Foreground polling

If the chain supports status checking a foreground service starts immediately after broadcast. `PollingTxStatusUseCase` polls the chain-specific API every few seconds (configurable per chain). Results flow back to the ViewModel which updates the DB row in real time. Polling stops on `Confirmed`, `Failed`, or timeout.

### Background polling

When the user opens the history screen `RefreshPendingTransactionsUseCase` picks up any rows still in `BROADCASTED`, `PENDING`, or `NotFound` and polls them in parallel. Exponential backoff prevents hammering APIs on repeated failures: 30s base doubling up to a 10 minute cap. Successful API responses (including `NotFound`) reset the backoff counter. Only network errors increment it.

### Terminal state guards

Every DAO update query includes `WHERE status NOT IN ('CONFIRMED', 'FAILED')` so a stale or out-of-order writer can never downgrade a finalized row. `demoteForReorg` is the only method that bypasses this guard (for chain reorganizations) and it requires `status = 'CONFIRMED'` explicitly.

### Status mapping

| DB Status | UI Display | Pollable? |
|-----------|-----------|-----------|
| BROADCASTED | Static badge | Yes |
| PENDING | "X minutes ago" | Yes |
| NotFound | Rendered as Pending | Yes |
| CONFIRMED | Confirmed badge | No |
| FAILED | Failed with reason | No |

### Forward compatibility

`TransactionHistoryDataConverter` catches `SerializationException` on unknown payload types and wraps them in `UnknownTransactionHistoryData`. This prevents a single row with a newer transaction type from crashing the entire history screen on older app versions. Unknown transactions are silently filtered out of the UI.

## Migration history

| Version | What changed |
|---------|-------------|
| 29 -> 30 | Created `transaction_history` table with wide schema (17 columns) |
| 30 -> 31 | Replaced wide schema with single JSON `payload` column (DROP + RECREATE safe because display data only) |
| 31 -> 32 | Added `retryCount` column for exponential backoff. Migrated legacy UUID ids to deterministic `chain:txHash` format |

## What is NOT implemented yet

These are tracked as separate issues and are not needed for the current feature to work correctly:

- **Chain backfill** (`upsertFromBackfill`): fetching historical transactions from chain APIs. The DAO method exists but has no caller yet.
- **Reorg demotion** (`demoteForReorg`): handling chain reorganizations that invalidate confirmed transactions. Infrastructure is in place but no watcher triggers it.
- `TransactionStatus.NotFound` rename to `NOT_FOUND` (#4020): cosmetic enum naming fix that requires a migration.
