# Network Error Handling

This document describes the two-layer architecture for handling network errors
without crashing the app (new changes only, existing code may still remain as is).

## Architecture Overview

```text
┌─────────────────────────────────────────────────────┐
│  ViewModel                                          │
│  viewModelScope.safeLaunch(onError = { ... }) {     │  ← Layer 2: catches ALL exceptions
│      val response = api.fetchData()                 │
│      _uiState.value = response                      │
│  }                                                  │
└────────────────────┬────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────┐
│  Ktor HttpClient (HttpClientConfigurator)           │
│  ┌────────────────────────────────────────────────┐ │
│  │ HttpRequestRetry                               │ │  retries IOException 3× with backoff
│  │   → retryOnException(maxRetries = 3)           │ │
│  │   → retryIf { GET/HEAD/OPTIONS + 5xx/429/408 }│ │
│  └────────────────────┬───────────────────────────┘ │
│  ┌────────────────────▼───────────────────────────┐ │
│  │ HttpCallValidator                              │ │  ← Layer 1: IOException → NetworkException(0)
│  │   IOException → NetworkException(httpStatus=0) │ │
│  └────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

## Layer 1: HttpCallValidator (transport errors)

Installed in `HttpClientConfigurator`. Catches `IOException` from the transport
layer **after** `HttpRequestRetry` exhausts its retries, and converts it to a
`NetworkException` with `httpStatusCode = 0`.

This covers all transport failures:
- `UnknownHostException` — DNS resolution failure
- `ConnectException` — connection refused
- `SocketTimeoutException` — read/connect timeout
- `SSLHandshakeException` — TLS errors
- Any other `IOException` subclass

### httpStatusCode convention

| Value | Meaning                                       | Source              |
|-------|-----------------------------------------------|---------------------|
| `0`   | Transport failure (no HTTP response received) | `HttpCallValidator` |
| `4xx` | Client error (bad request, not found, etc.)   | `bodyOrThrow()`     |
| `5xx` | Server error                                  | `bodyOrThrow()`     |

### Why not an OkHttp interceptor?

The previous approach caught `IOException` at the OkHttp level and returned a
synthetic HTTP 503 response. This broke downstream code because:

1. Synthetic 503 is indistinguishable from a real server 503.
2. Callers trying `response.body<T>()` on the synthetic body get `SerializationException`.
3. `bodyOrThrow()` reports `NetworkException(503)` — looks like a server error.
4. Status checks like `if (response.status != OK)` show "broadcast failed" instead of "no internet".

The `HttpCallValidator` approach throws **before any response exists**, so none of
these issues can occur.

## Layer 2: safeLaunch (application errors)

`HttpCallValidator` only catches transport-level `IOException`. Other crash vectors
exist that the HTTP client cannot handle:

| Crash source    | Example                                               | Layer 1 catches it? |
|-----------------|-------------------------------------------------------|---------------------|
| Deserialization | `.body<T>()` on unexpected JSON                       | No                  |
| Business logic  | `error("fail to broadcast")`                          | No                  |
| Null safety     | `response.data!!.value` on null                       | No                  |
| `bodyOrThrow()` | `NetworkException(500, ...)` without caller try-catch | No                  |

`safeLaunch` is the ViewModel-level safety net. It catches **all** exceptions
except `CancellationException` (which must propagate for coroutine lifecycle).

### Usage

```kotlin
// Before (can crash on any unhandled exception):
viewModelScope.launch {
    val data = repository.fetchData()
    _uiState.update { it.copy(data = data) }
}

// After (bulletproof):
viewModelScope.safeLaunch(
    onError = { e ->
        _uiState.update { it.copy(error = e.message) }
    }
) {
    val data = repository.fetchData()
    _uiState.update { it.copy(data = data) }
}
```

### Distinguishing error types in onError

```kotlin
viewModelScope.safeLaunch(
    onError = { e ->
        val message = when (e) {
            is NetworkException -> when {
                e.httpStatusCode == 0 -> "No internet connection"
                e.httpStatusCode in 400..499 -> "Request error: ${e.message}"
                else -> "Server error: ${e.message}"
            }
            else -> "Unexpected error: ${e.message}"
        }
        _uiState.update { it.copy(error = message) }
    }
) {
    // ...
}
```

### When to use safeLaunch vs launch

| Scenario                                            | Use                         |
|-----------------------------------------------------|-----------------------------|
| Network calls, API responses, deserialization       | `safeLaunch`                |
| Navigation, UI state toggles, local-only operations | `launch` (no risk of crash) |

### CancellationException

`safeLaunch` always re-throws `CancellationException`. This ensures:
- `viewModelScope` cancellation on `ViewModel.onCleared()` works correctly.
- `job.cancel()` works correctly.
- Structured concurrency is preserved.

## bodyOrThrow

The existing `bodyOrThrow()` extension (in `RpcExtensions.kt`) handles HTTP error
responses and deserialization errors for individual calls:

```kotlin
val data = response.bodyOrThrow<MyType>()
// Throws NetworkException(statusCode, message) on non-2xx
// Wraps deserialization errors in NetworkException on 2xx
```

Prefer `bodyOrThrow()` over raw `.body<T>()` in API methods — it provides
consistent error handling at the call site.

## Quick-start: migrating a ViewModel

1. Import `safeLaunch`:
2. Replace `viewModelScope.launch` with `viewModelScope.safeLaunch` for any block
   that does network calls, deserialization, or calls that can `error()`:
   ```kotlin
   viewModelScope.safeLaunch(
       onError = { e -> Timber.e(e, "Operation failed") }
   ) {
       // risky code here
   }
   ```

3. Use `bodyOrThrow()` instead of `.body<T>()` in API/repository methods:
   ```kotlin
   val data = response.bodyOrThrow<MyResponseType>()
   ```

## Test coverage

- **`NetworkStateInterceptorContractTest`** (15 unit tests) — verifies Layer 1:
  all IOException subtypes, server response passthrough, deserialization escape.
- **`SafeLaunchTest`** (18 unit tests) — verifies Layer 2:
  catches all exception types, CancellationException propagation, Ktor integration.
- **`NetworkErrorHandlingTest`** (3 instrumented tests) — verifies the full
  OkHttp → Ktor → HttpCallValidator pipeline on a real Android device/emulator.
- **`MockHttpClient`** — shared test utility that mirrors the production
  `HttpClientConfigurator` setup.
