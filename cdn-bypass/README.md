# cdn-bypass

Retry logic for fetching fresh evaluations after a push notification, with CDN cache bypass on the final attempt.

## Overview

When a push notification arrives with a `targetChangeNumber`, the local evaluation cache may still be serving a stale CDN edge response. `CdnBypassFetcher` handles this by retrying with exponential backoff, and — if the cache hasn't caught up after all normal retries — issuing one final request with the change number forwarded to the caller so it can route around the CDN.

## How it works

For each key:

1. **Pre-check**: if local state is already at or past `targetChangeNumber`, exit immediately (no fetch needed).
2. **Normal retries** (up to 10): fetch without bypass, with exponential backoff between attempts. Re-check freshness before each attempt.
3. **Bypass attempt** (11th): if still stale after all normal retries, fetch once more with `targetChangeNumber` passed to `fetchAction`. The caller uses this to signal a cache bypass to the server.

## Configuration

The backoff base delay defaults to `CDN_BYPASS_BACKOFF_BASE_MS` (build config, default 1000 ms), overridable via Gradle property:

```
./gradlew ... -PcdnBypassBackoffBaseMs=500
```

Set to `0` in tests to skip delays.

## Usage

```kotlin
val fetcher = CdnBypassFetcher(
    fetchAction = { key, targetChangeNumber ->
        // pass targetChangeNumber to the server when non-null to bypass CDN cache
        coordinator.fetchIfNeeded(key, targetChangeNumber = targetChangeNumber)
    },
    freshnessChecker = { key, targetChangeNumber ->
        storage.lastChangeNumber(key) >= targetChangeNumber
    },
)

fetcher.fetch(targetChangeNumber = notification.changeNumber, keys = affectedKeys)
```
