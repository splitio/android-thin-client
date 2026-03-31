# auth

Provides JWT-based authentication.

## Overview

This module handles fetching, caching, and invalidating JWT credentials.

## Key Components

| Class / Interface | Description |
|---|---|
| `AuthProvider` | Top-level interface. Returns a valid (non-expired) credential for a target, or fetches a new one. |
| `DefaultAuthProvider` | Implements request deduplication: concurrent requests for the same target share a single in-flight fetch. |
| `CredentialFetcher` | Functional interface that performs the actual HTTP fetch of a JWT credential. |
| `DefaultCredentialFetcher` | Default implementation. Sends an HTTP request and deserializes the response via `TokenDeserializer`. |
| `CredentialStorage` | Interface for reading and writing cached credentials. |
| `InMemoryCredentialStorage` | In-memory implementation of `CredentialStorage`. |
| `SecureStorage` | Interface for persistent, secure credential storage (e.g. Android Keystore). |
| `NoOpSecureStorage` | No-op implementation used when secure storage is not configured. |
| `JwtCredential` | Data class holding the raw token string, expiry timestamp (Unix seconds), and `pushEnabled` flag. |
| `TokenDeserializer` | Interface for deserializing raw HTTP responses into `JwtCredential`. |
| `JsonTokenDeserializer` | `kotlinx.serialization`-based implementation of `TokenDeserializer`. |

## Composite keys (multi-user JWT)

The auth endpoint supports fetching a single JWT that is valid for multiple users via a comma-separated `users` parameter (e.g. `?users=alice,bob`). This is used when multiple `SplitClient` instances are active simultaneously — one shared JWT covers all of them, avoiding redundant auth requests.

`DefaultAuthProvider` handles this via the `compositeKeyBuilder: (Set<String>) -> String` constructor parameter. When `credential(targets)` is called:

1. `compositeKeyBuilder` combines the individual target strings into a single composite key (e.g. `"alice,bob"`).
2. The JWT is fetched using the composite key — one HTTP request, one token.
3. The credential is cached under the composite key.

This means a subsequent call for any target set that produces the same composite key hits the cache immediately, returning the already-fetched multi-user token without a new HTTP request.

The composite key logic is owned by the caller. In production (`SplitFactoryBuilder`) this is:

```kotlin
compositeKeyBuilder = { targets -> targets.sorted().joinToString(",") }
```

## Dependencies

- `:retryable-http-client` — HTTP transport with built-in retry logic
- `kotlinx-serialization-json` — JSON deserialization
- `kotlinx-coroutines-android` — Coroutine support

## Build

```bash
# Build this module
./gradlew :auth:assembleDebug

# Run unit tests
./gradlew :auth:testDebugUnitTest
```
