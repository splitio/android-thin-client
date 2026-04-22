# Streaming Factory Refactor Design

**Date:** 2026-03-27
**Status:** Approved

## Context

The `SplitFactoryBuilder.build()` method currently contains complex streaming setup logic (lines 74-112) that:
- Manages mutable state (`latestStreamingTargets`, `streamingManager`)
- Has intricate callback wiring between streaming, auth, and evaluation components
- Makes the builder method hard to understand and extend

This refactor addresses maintainability and extensibility concerns by extracting streaming construction into a dedicated factory in the `streaming-thin` module while keeping the public API unchanged.

## Goals

1. **Maintainability**: Move streaming construction logic to the streaming module where it belongs
2. **Extensibility**: Make it easier to add new streaming features without touching `SplitFactoryBuilder`
3. **Minimal change**: Keep the public API identical and minimize code changes
4. **No new dependencies**: Avoid adding `evaluation` or `secure-http-client` dependencies to `streaming-thin`

## Architecture

### Component Extraction

Extract streaming setup from `SplitFactoryBuilder.build()` into a new factory function in the `streaming-thin` module.

**Key principle:** The factory remains generic — it doesn't know about `EvaluationTarget` or `FetchCoordinator`. The caller (`SplitFactoryBuilder`) manages mutable state and wiring through lambda closures.

### Module Boundaries

```
SplitFactoryBuilder (api module)
    ↓ calls
createStreamingComponents (streaming-thin module)
    ↓ returns
StreamingComponents { manager, startTrigger }
```

No new module dependencies required.

## Design

### New File: `streaming-thin/src/main/java/io/split/client/thin/internal/streaming/StreamingFactory.kt`

**Data class:**
```kotlin
data class StreamingComponents(
    val manager: DefaultStreamingManager,
    val startTrigger: suspend () -> Unit,
)
```

**Factory function:**
```kotlin
fun createStreamingComponents(
    streamingUrl: String,
    retryableHttpClient: RetryableHttpClient,
    tokenProvider: suspend () -> String,
    onEvaluationFetchNotification: suspend () -> Unit,
): StreamingComponents
```

**Parameters:**
- `streamingUrl`: SSE endpoint URL
- `retryableHttpClient`: HTTP client for SSE transport
- `tokenProvider`: Lambda that returns current JWT token (captures caller's `latestStreamingTargets`)
- `onEvaluationFetchNotification`: Lambda called when streaming triggers refetch (wired to `fetchCoordinator.refetchAll()`)

**Returns:**
- `manager`: Configured `DefaultStreamingManager` instance
- `startTrigger`: Lambda to invoke when streaming should start (calls `manager.start()`)

### Implementation Details

**StreamingFactory.kt:**
1. Create dedicated `CoroutineScope(SupervisorJob())` for streaming
2. Instantiate `DefaultStreamingManager` with:
   - Provided `tokenProvider` (captures caller's mutable state)
   - `eventSourceClientProvider` → creates `EventSourceClientImpl` with `StreamingTransportImpl`
   - `backoffCounterFactory` → creates `ExponentialBackoffCounter(1, 60)`
   - Provided `onEvaluationFetchNotification` callback
   - Placeholder `onOccupancyZero` (TODO comment)
3. Return `StreamingComponents(manager, startTrigger = { manager.start() })`

**SplitFactoryBuilder.build() changes:**
1. Replace lines 74-112 with conditional check and call to `createStreamingComponents()`
2. Pass `tokenProvider` lambda that captures `latestStreamingTargets`: `{ authProvider.credential(latestStreamingTargets).token }`
3. Pass `onEvaluationFetchNotification` lambda that will be set after evaluation components are created (forward reference via mutable var)
4. Wire returned `startTrigger` by calling it inside the `onStreamingTargetsChanged` callback passed to `createSecureHttpClient()`: `onStreamingTargetsChanged = { targets -> latestStreamingTargets = targets; streamingComponents.startTrigger() }`
5. Wire `onFetchNotification` after evaluation components are created: `onFetchNotification = { fetchCoordinator.refetchAll(null, FetchReason.PUSH) }`

**Code metrics:**
- `SplitFactoryBuilder`: ~155 lines → ~135 lines (20 lines removed)
- New `StreamingFactory.kt`: ~40 lines
- Net: Better separation with similar total line count

## Testing

**New test file: `streaming-thin/src/test/java/io/split/client/thin/internal/streaming/StreamingFactoryTest.kt`**

**Test cases:**

1. **`createStreamingComponents returns valid components`**
   - Verify non-null manager and startTrigger
   - Verify manager is properly configured

2. **`tokenProvider is called when manager starts`**
   - Fake tokenProvider
   - Call startTrigger
   - Verify tokenProvider invoked

3. **`onEvaluationFetchNotification is wired correctly`**
   - Create components with a fake callback that sets a flag
   - Trigger streaming manager's evaluation fetch notification (via test helper or by invoking the callback directly if accessible)
   - Verify the fake callback was invoked

4. **`startTrigger invokes manager start`**
   - Create components
   - Call startTrigger
   - Verify manager started (via side effects)

**Test approach:**
- Use fakes over mocks (following existing patterns in `EvaluationFakes.kt`)
- Use `kotlinx-coroutines-test` for coroutine testing
- Focus on factory contract, not internal implementation

## Verification

1. **Unit tests pass:**
   - `./gradlew :streaming-thin:testDebugUnitTest` (new tests)
   - `./gradlew :api:testDebugUnitTest` (ensure no regression)

2. **E2E tests pass:**
   - `./gradlew publishToMavenLocal`
   - `./gradlew :e2e:connectedAndroidTest`

3. **Build succeeds:**
   - `./gradlew build`
   - `./gradlew :android-thin-client:bundle`

## Critical Files

- **New:**
  - `streaming-thin/src/main/java/io/split/client/thin/internal/streaming/StreamingFactory.kt`
  - `streaming-thin/src/test/java/io/split/client/thin/internal/streaming/StreamingFactoryTest.kt`

- **Modified:**
  - `api/src/main/java/io/split/client/thin/SplitFactoryBuilder.kt` (lines 74-112 refactored)

## Non-Goals

- Testing `SplitFactoryBuilder` itself (out of scope — no existing tests for it)
- Refactoring other concerns in `build()` (HTTP, auth, evaluation) — focus only on streaming
- Changing public API or behavior
- Adding new streaming features (e.g., implementing `onOccupancyZero`)
