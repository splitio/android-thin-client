# streaming-thin

Manages the SSE streaming connection for real-time flag update notifications.

## Overview

This module maintains a single persistent SSE connection to the Split streaming service. When the server pushes a notification, the module triggers a callback so the evaluation layer can re-fetch updated flags.

The module is auth-agnostic: it receives tokens via an injected `tokenProvider: suspend () -> String` lambda and does not interact with `AuthProvider` directly.

## Architecture

Two layers:

```
StreamingManager               (lifecycle coordinator — one per SDK instance)
└── StreamingConnectionManager (one active SSE connection)
    └── EventSourceClient      (SSE transport from android-client)
```

**`StreamingManager` / `DefaultStreamingManager`**
Top-level lifecycle interface. Holds at most one active `StreamingConnectionManager` at a time, protected by a `Mutex`. On `start()`, creates a connection if none exists (idempotent); on `stopAll()`, tears down and clears it. `pause()` and `resume()` delegate to the current connection.

**`StreamingConnectionManager`**
Manages the SSE connection lifecycle. Internal states: `Stopped → Started → Paused`. On `start()`:
1. Calls `tokenProvider()` to obtain a fresh JWT.
2. Opens an SSE connection via `EventSourceClient`.
3. On failure, backs off and retries via `BackoffCounter`.
4. Parses incoming events as typed `ThinNotification`s and fires `onEvaluationFetchNotification` or `onOccupancyZero` callbacks.

**`StreamingTransportImpl`**
Bridges `EventSourceClient.StreamingTransport` (from android-client) to `RetryableHttpClient`. Issues an HTTP GET with `Accept: text/event-stream`.

## Notification types

| Type | Description |
|------|-------------|
| `EvaluationUpdateNotification` | Flag definitions changed; triggers a re-fetch |
| `ThinControlNotification` | Server control signals (`RESUMED`, `PAUSED`, `DISABLED`, `RESET`) |
| `ThinOccupancyNotification` | Publisher count on the channel |
| `ThinStreamingError` | Server-side error with code and message |

## Token provider

The caller (currently `DefaultSecureHttpClient`) supplies a `tokenProvider` lambda. This lambda returns the current JWT, which may cover multiple active users — but `StreamingConnectionManager` does not know or care about that detail; it just passes the token as a Bearer header.

## Dependencies

- `:retryable-http-client` — HTTP transport
- `:streaming`, `:http-api`, `:backoff` — SSE client and backoff (from `android-client` submodule)
- `kotlinx-coroutines-android` — Coroutine support
- `kotlinx-serialization-json` — Notification deserialization

## Build

```bash
./gradlew :streaming-thin:assembleDebug
./gradlew :streaming-thin:testDebugUnitTest
```
