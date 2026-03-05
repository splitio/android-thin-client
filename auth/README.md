# auth

Provides JWT-based authentication.

## Overview

This module handles fetching, caching, and invalidating JWT credentials.

## Key Components

| Class / Interface | Description |
|---|---|
| `AuthProvider<T>` | Top-level interface. Returns a valid (non-expired) credential for a target, or fetches a new one. |
| `DefaultAuthProvider<T>` | Implements request deduplication: concurrent requests for the same target share a single in-flight fetch. |
| `CredentialFetcher<T>` | Functional interface that performs the actual HTTP fetch of a JWT credential. |
| `DefaultCredentialFetcher<T>` | Default implementation. Sends an HTTP request and deserializes the response via `TokenDeserializer`. |
| `CredentialStorage<T>` | Interface for reading and writing cached credentials. |
| `InMemoryCredentialStorage<T>` | In-memory implementation of `CredentialStorage`. |
| `SecureStorage` | Interface for persistent, secure credential storage (e.g. Android Keystore). |
| `NoOpSecureStorage` | No-op implementation used when secure storage is not configured. |
| `JwtCredential` | Data class holding the raw token string, expiry timestamp (Unix seconds), and `pushEnabled` flag. |
| `TokenDeserializer` | Interface for deserializing raw HTTP responses into `JwtCredential`. |
| `JsonTokenDeserializer` | `kotlinx.serialization`-based implementation of `TokenDeserializer`. |

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
