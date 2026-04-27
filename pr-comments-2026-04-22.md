# PR Comments — 2026-04-22

Reviewers: **Nicolás Zelaya** (nicolas.zelaya@harness.io) and **Emmanuel Zamora** (emmanuel.zamora@harness.io)

---

## PR #56 — FME-13526: add track/flush and destroy E2E tests

**Nicolás Zelaya: REQUEST CHANGES**

### Must Fix
1. Telemetry assertion (`capturedTelemetryBodies.isNotEmpty()`) will fail every run — no production caller for `postTelemetry` exists; telemetry is PENDING per spec §600-604
2. Test 9 docblock claims "before destroy returns" but the assertion checks after `runBlocking { factory.destroy() }` returns — doesn't catch fire-and-forget regression
3. Test 9 missing `try/finally` — `server.shutdown()` not protected, port leaks on assertion failure

### Could Fix
4. Wire-format assertions too loose — `contains("99.0")` can false-match; parse JSON instead
5. 5s polling loop is redundant — `flush()` is already synchronous via `runBlocking`

### Consider
6. Test 9 doesn't cover "SDK unusable after destroy" half of spec case 9
7. Use named args on `client.track(...)` call
8. Comment on line 616 narrates rather than explains

---

## PR #57 — FME-13526: do not retry 4xx errors in RetryableHttpClient

**Nicolás Zelaya: REQUEST CHANGES**

### Must Fix
1. `429` must be dropped from `noRetryStatuses` — it's the canonical retryable 4xx; silently drops events/evaluations on throttling
2. Zero behavioral test coverage for the retry semantics change — add tests for each `RequestCategory`

### Could Fix
3. Comment "401 handled at higher level" is only half-true — SSE/AUTH categories don't have the re-auth wrapper
4. Extract to named constant `NON_RETRYABLE_CLIENT_ERROR_STATUSES`
5. README example (`429 → unlimited retry`) contradicts shipped defaults
6. Comment doesn't justify the exact subset chosen

### Consider
7. Same policy for every category hides intent — document that uniformity is deliberate

---

## PR #58 — FME-13526: min evaluation refresh rate 60→1; remove Room shadow repackaging

**Nicolás Zelaya: REQUEST CHANGES**

### Must Fix
1. Release behavior (min=60) has zero test coverage — debug tests now assert `1`, a typo in `build.gradle.kts` would ship undetected
2. KDoc `@property evaluationRefreshRate ... Min value: 60` is inaccurate for debug builds

### Could Fix
3. Verify Room dependency reaches consumers via POM — `implementation` scope under fused-library may not expose it transitively; consumers could hit `NoClassDefFoundError`
4. Consider `@VisibleForTesting` seam as alternative to `BuildConfig`
5. Log message says `>= 1` in debug vs `>= 60` in release — inconsistent
6. `verifyRepackaging` no longer guards against Room re-entering the AAR unshaded — invert assertion

### Consider
7. Rename "at minimum boundary" tests to reflect debug-vs-release semantics

---

## PR #59 — FME-13526: auth response null-safety in DefaultCredentialFetcher and JsonTokenDeserializer

**Nicolás Zelaya: REQUEST CHANGES**  
**Emmanuel Zamora: CRITICAL security comment**

### Must Fix (Nicolás)
1. **Silent auth failure** — `token ?: ""` + `expiresAt = Long.MAX_VALUE` poisons `CredentialStorage` for the process lifetime with an empty never-expiring credential; throw instead
2. `connDelay ?: 60` silently changes behavior from "connect immediately" to "wait 60s" — revert to `?: 0` unless spec mandates 60
3. `pushEnabled ?: true` inverts the safe default — missing field should degrade to polling, not SSE connect; flip to `?: false`
4. Zero test coverage for the new defaulting branches; deleted negative test not replaced

### Must Fix (Emmanuel)
- **CRITICAL** — `dto.token ?: ""` creates authentication bypass; empty JWT may pass to requests; removed test `deserialize throws when required field is missing` masks this

### Could Fix
5. `IllegalStateException` is wrong exception shape — use a dedicated `AuthException`
6. `Long.MAX_VALUE` fires from four code paths with different meanings — decide on one policy (malformed = throw or = 0)

### Consider
7. PR title says "null-safety" but diff changes business defaults — consider narrowing scope

---

## PR #60 — FME-13526: event key scoping, init reason, per-client event filtering, evaluation key threading

**Nicolás Zelaya: REQUEST CHANGES**

### Must Fix
1. `EVAL_LOADED_FROM_STORAGE` missing from `KEY_SCOPED_EVENTS` — `SDK_READY_FROM_CACHE` leaks across clients; burns `executionLimit=1` on wrong key
2. `ObserverEvaluationPersistenceCallbacks.onEvalStorageUpdated` emits without `matchingKey` property — silently dropped by new filter; persistence-driven path never reaches any scoped client's `EventsManager`
3. `SplitEventListener` — nullable metadata is source-breaking for Kotlin subclasses; adapter always passes `null`; KDoc example will NPE

### Could Fix
4. `FetchReasonObserverMappingTest` duplicates and has drifted from production mapping
5. Observer lifecycle leak on per-client `destroy()` — `EventManagerObserver` never unregistered
6. `setTarget(isInitialization: Boolean = false)` default is a footgun — remove the default

### Consider
7. Extract `const val MATCHING_KEY_PROPERTY = "matchingKey"` 
8. `EventManagerObserverTest` missing coverage for global-observer fallback and scoped-type-with-no-key drop

---

## PR #61 — FME-13526: dedicated coroutine context for events, synchronous persistence push

**Emmanuel Zamora: CRITICAL issues (approved with comments)**

### Critical
1. FIFO ordering not guaranteed with `limitedParallelism(1)` under concurrent launches
2. **ANR risk** — `PersistentEventsStorage.push()` is now synchronous (10-50ms DB write); no `@WorkerThread` annotation or main-thread check
3. **Resource leak** — `eventsScope` is orphaned, not a child of `factoryScope`; won't be cancelled on `factory.destroy()`

### Additional
4. Performance regression — synchronous push is 5-50x slower than async
5. Test doesn't prove ordering — uses deterministic dispatcher, not real thread races

---

## PR #62 — FME-13526: E2E behavioral tests — MockSplitServer, SdkBehaviorAndroidTest

**Emmanuel Zamora: LGTM**  
**Nicolás Zelaya: APPROVE WITH COMMENTS**

### Could Fix
1. `assertNull("... for now", lastUpdateMetadata)` needs a Jira ticket reference — currently codifies a known gap against spec
2. Telemetry scaffolding (`_capturedTelemetryBodies`) is dead with no assertion — delete or add `@Ignore` placeholder
3. `_capturedEventBodies` / `_capturedTelemetryBodies` are plain `mutableListOf` — race between MockWebServer dispatcher thread and test thread; use `CopyOnWriteArrayList`
4. Stray `logLevel = VERBOSE` in polling helper only — debug leftover

### Consider
5. PR description omits the test-coverage downgrades (weakened FLAGS_UPDATE assertion, dropped telemetry assertion)
6. `MockSplitServer.shutdown()` swallow should at least log at WARN

---

## PR #63 — FME-13526: use fully-qualified name for TestActivity in manifest

**Emmanuel Zamora: LGTM / Approved**  
**Nicolás Zelaya: REQUEST CHANGES**

### Must Fix
1. `SSE_EVALUATION_UPDATE_WITH_DELAY` fixture is malformed JSON — extra `"` breaks the envelope; Test 10 cannot pass as submitted
2. Test 10 re-derives `expectedDelayMs` from the implementation it's validating — tautological; pin to a hand-computed literal and add upper bound
3. **Operator precedence bug** in `DefaultSyncDelayCalculator.kt:21` — `hash.toLong() and 0xFFFFFFFFL % intervalMs` should be `(hash.toLong() and 0xFFFFFFFFL) % intervalMs`

### Could Fix
4. `MockSplitServer` `ArrayDeque` is not thread-safe; `callCount` is a plain `Int` in a multi-thread context
5. Test 13's 3s pause window not anchored to actual events push rate
6. Extract `backgroundApp()` / `foregroundApp()` helpers — repeated 4 times with drift
7. New `MockSplitServer` counter fields should use private mutable backing + public read-only pattern

### Consider
8. Test 11's reconnect assertion is weak — doesn't verify socket was closed during pause

---

## PR #64 — FME-13526: use HttpClient.streamRequest() for live SSE streaming

**Emmanuel Zamora: LGTM / Approved**  
**Nicolás Zelaya: APPROVE WITH COMMENTS**

### Could Fix
1. KDoc parameter name drift — `@param retryableHttpClient` but actual param is now `httpClient`
2. README still references `RetryableHttpClient` for SSE bridge
3. Dead fakes `FakeRetryableHttpClient` / `FakeHttpResponse` in `StreamingFakes.kt` — no longer referenced anywhere

### Consider
4. `private var streamRequest` shadows the method name — rename to `currentStreamRequest`
5. No `@Volatile` on shared-mutable `streamRequest` field
6. `StreamingTransportImpl` is package-default public — tighten to `internal`

---

## PR #65 — FME-13526: Various fixes for lifecycle

**Emmanuel Zamora: LGTM / Approved**  
**Nicolás Zelaya: APPROVE WITH COMMENTS**

### Must Fix
1. Async `disconnect()` silently no-ops when `scope` is cancelled (destroy race) — socket never closed; need dedicated teardown scope or synchronous close fallback

### Could Fix
2. `DefaultLifecycleManager.destroy()` never clears `components` — strong refs now leak closures
3. `register()` / `mutableListOf` iteration not thread-safe — `CME` crash path on main thread; use `CopyOnWriteArrayList`
4. Drop `logLevel = VERBOSE` from `buildStreamingFactory` — debug residue
5. Duplicate keepalive SSE body construction in two places
6. Test 13 still uses `Thread.sleep(500)` after `pressHome()` — inconsistent with Test 12's 1000ms fix

### Consider
7. KDoc the `resume()` / `pause()` contract — semantics are load-bearing

---

## PR #66 — FME-13526: Fix backoff for streaming; consumer tests connect to mock web server

**Emmanuel Zamora: LGTM / Approved**  
**Nicolás Zelaya: APPROVE WITH COMMENTS**

### Could Fix
1. `factoryBuilderWithConfig` in Kotlin duplicates mock-endpoint wiring instead of calling `mockConfig()`
2. `FakeBackoffCounter` seeded delay values (100, 200, 400) now mean 100s/200s/400s after the fix — change to small second values (1, 2, 4)

### Consider
3. Seconds→millis conversion duplicated across both `BackoffCounter` callers — worth centralizing

---

## PR #67 — FME-13526: simplify persistence schema

**Emmanuel Zamora: LGTM / Approved**  
**Nicolás Zelaya: APPROVE WITH COMMENTS**

### Could Fix
1. `persistence/README.md` still describes old schema — update tables, entities, and behavior description
2. Two spec divergences (`general_info` removed, `lastUpdateTimestamp` dropped) need Confluence update or follow-up tickets
3. `EvaluationEntity` / `EvaluationDao` should be package-private to match new `Attributes*` precedent
4. `replaceForKey` wrapper is redundant now — inline the two DAO calls
5. Test gap — assert `loadForKey(key, oldAttrHash)` returns null after overwrite

### Consider
6. Document null contract on `PersistentEvaluationStorage.loadForKey`
7. Method name mismatch — `AttributesDao.getByKey` returns single row, `EvaluationDao.getByKey` returns list

---

## PR #68 — FME-13526: add X-Harness-FME-Content-Digest header to evaluations requests

**Emmanuel Zamora: Required changes before approval**

### Critical (Emmanuel)
1. **Attribute serialization mismatch** — `ContentDigest` uses typed JSON (numbers as numbers) but `buildEvaluationsBody()` stringifies all values; digest won't match request body
2. **Missing key sorting** — `ContentDigest` sorts keys alphabetically but `buildEvaluationsBody` uses insertion order
3. **Inconsistent null handling** — `ContentDigest` filters nulls but `buildEvaluationsBody` includes them (`null -> JsonPrimitive(null)`)
4. **List null handling mismatch** — `ContentDigest` uses `filterNotNull()`, body builder doesn't

---

## PR #69 — FME-13526: filter invalid attribute types from Target at construction time

**Emmanuel Zamora: Required changes before approval**

### Critical (Emmanuel)
1. **Stack overflow vulnerability** — recursive collection validation has no depth limit; add `depth > 10` guard
2. **Breaking API change** — converting from `data class` to regular class removes `copy()` method; add deprecated `copy()` method

### Missing Tests
3. Add tests for: empty collection, collection of nulls, nested collections, deep nesting (100 levels)

---

## PR #70 — FME-15063: include SDK key in database name

**Emmanuel Zamora: Required changes before approval**

### Must Fix
1. **Race condition** — `INSTANCES` uses `HashMap` not `ConcurrentHashMap`; thread B can call `put()` inside synchronized block while thread A calls `get()`
2. No input sanitization for `sdkKey` — special filesystem chars (`/`, `:`, `*`) not handled
3. `first4+last4` approach has collision risk — consider hash of full key
4. Breaking change for upgrading users — old `split_thin.db` → new `{first4}{last4}.db`; needs migration or documentation

---

## PR #71 — FME-15063: defensive error handling and 304 support for evaluations endpoint

**Emmanuel Zamora: LGTM**

Minor suggestion: Add more context to `onEvalFetchFailed` callback logging — include `matchingKey`, fetch reason, and error message.

---

## PR #73 — Compile SDK to 34 and attributes fix

**Emmanuel Zamora: Required changes before approval**

### Critical
1. **Missing key sorting** — `buildEvaluationsBody` uses insertion order; `ContentDigest` sorts alphabetically
2. **Inconsistent null handling** — body builder includes nulls; `ContentDigest` filters them
3. **List null handling mismatch** — body builder includes null list elements; `ContentDigest` uses `filterNotNull()`

---

## PR #74 — FME-15063: fix occupancy tracking and wire streaming observability events

**Emmanuel Zamora: Required changes before approval**

Confirm notification type `"EVALUATIONS_UPDATE"` (plural) matches backend. The change from `"EVALUATION_UPDATE"` (singular) needs to align with what the backend actually sends.

Multi-channel occupancy fix is correct. Observability wiring looks excellent.

---

## PR #75 — FME-15063: log fetch delay in PUSH fetch request

**Emmanuel Zamora: LGTM / Approved** — no issues

---

## PR #76 — FME-15063: seed activeTargets with defaultTarget to prevent duplicate JWT fetches

**Emmanuel Zamora: LGTM / Approved** — no issues

---

## PR #77 — FME-15063: config change detection & injectable detector

**Emmanuel Zamora: LGTM / Approved** — no issues

---

## PR #78 — main_12-7: add Maven build support to observer module

**Emmanuel Zamora: Required changes before approval**

1. **CRITICAL — `runBlocking` on initialization thread** in `PersistenceDomainFactory` — blocks calling thread during factory init; potential ANR
2. **Missing database migration** — new `GeneralPropertiesEntity` table added but DB version not bumped
3. **Query parameter rename** — `withDynamicConfig` → `configs`; verify backend supports new name

---

## PR #79 — retryable-refactor: add HTTP contracts interfaces for client abstraction

**Emmanuel Zamora: Required changes before approval**

1. **Breaking API change** — `HttpResponse` changed from methods (`getHttpStatus()`) to Kotlin properties; all Java consumers break
2. **PR scope too large** — combines HTTP contracts refactoring with Maven `pom.xml` for observer; should be separate PRs
3. **Factory module organization** — moving `RetryableHttpClientFactory` from `:retryable-http-client` to `:api` creates concerning dependency pattern

---

## PR #80 — retryable-refactor: update retryable-http-client to use contract types

**Emmanuel Zamora: LGTM / Approved** — no issues

---

## PR #81 — retryable-refactor: move factory to api, add adapters, remove :http/:observer from retryable-http-client

**Emmanuel Zamora: Required changes before approval**

1. **CRITICAL — Factory breaks module boundaries** — `RetryableHttpClientFactory` in `:api` creates circular coupling with the module it creates
2. **Adapter pattern adds unnecessary indirection** — `HttpClientAdapter` wraps Android types in `:api` where Android types are already available; double-wrapping overhead
3. **Internal class made public** — `DefaultRetryableHttpClient` changed from `internal` to `class` without justification

---

## PR #82 — FME-15063-metadata: add payload field to ObservableEvent

**Emmanuel Zamora: LGTM / Approved** — no issues

---

## PR #83 — FME-15063-metadata: introduce UpsertResult with changedFlagNames diff

**Emmanuel Zamora: LGTM / Approved** — no issues

---

## PR #84 — FME-15063-metadata: add lastUpdateTimestamp to persistence layer

**Emmanuel Zamora: Required changes before approval**

1. **CRITICAL — Missing database migration** — DB version bumped 2→3 but no `Migration_2_3` provided; existing users will crash
2. **Timestamp captured at wrong time** — `System.currentTimeMillis()` at persistence time, not fetch time; should be passed as parameter from caller
3. **Timestamp semantics unclear** — `CacheLoadResult.lastUpdateTimestamp` lacks KDoc

---

## PR #85 — FME-15063-metadata: populate SdkReadyMetadata and SdkUpdateMetadata in event pipeline

**Emmanuel Zamora: Required changes before approval**

1. **CRITICAL — Event suppression bug** — `EvaluationFactory` conditionally suppresses `EVALUATIONS_UPDATED` when `payload == null`; breaks event contract; listeners expect the event on every non-INITIALIZATION fetch
2. **Payload builders in wrong layer** — `buildEvaluationsUpdatedPayload()` / `buildCacheLoadedPayload()` defined in `SplitFactoryBuilder` (API layer) but implement core business logic; move to `EvaluationFactory`
3. **Concurrent cache load race** — `cacheLoadedKeys` Set has a race between cache load and `fetchIfNeeded(INITIALIZATION)`

---

## PR #86 — pr-review-fixes: address 14 code review findings

**Emmanuel Zamora: Required changes before approval (then approved)**

1. Fix build check failures — lint and message-check failing
2. `buildEvaluationsBody()` — verify it always runs on IO context; consider `withContext(Dispatchers.Default)` for large attribute maps
3. Verify `factoryScope.coroutineContext[Job]` cannot be null — document assumption or add null safety check

**Spec compliance:** All changes correctly implement spec requirements (null filtering for Content-Digest, operator precedence for sync delay, event properties).  
**Code quality:** Excellent fixes — eliminates ANR risk, improves thread safety, comprehensive retry tests, clean dead code removal.
