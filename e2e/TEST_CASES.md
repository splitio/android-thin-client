# E2E Test Cases — Android Thin Client SDK

## Test 1 — SDK initializes and reaches ready
**`sdkInitializesAndReachesReady`**

- Given the SDK is configured in POLLING mode with no cached data
- When a factory is built with PUSH_DISABLED auth and RESPONSE_1 evaluations
- And a client is obtained and a listener registered
- Then onReady fires within timeout
- And getTreatment("flag_a") returns "on"
- And getTreatment("flag_b") returns "off"

---

## Test — Evaluations request includes content digest
**`evaluationsRequestIncludesTruncatedSha512ContentDigestForAttributes`**

- Given the SDK is configured in POLLING mode with a target that has attributes
- When the factory reaches onReady
- Then the evaluations request includes an X-Harness-FME-Content-Digest header
- And the digest value is a truncated SHA-512 of the canonical attribute payload
- And the request includes an X-Harness-FME-SDK-Thin-Version header matching the build config

---

## Test 2 — SDK emits readyFromCache and serves cached evaluations
**`sdkEmitsReadyFromCacheAndServesCachedEvaluations`**

- Given a factory has previously populated the Room cache with RESPONSE_1 evaluations
- When a second factory is created with the same storage prefix
- And the evaluations endpoint is configured to respond slowly (simulating network delay)
- Then onReadyFromCache fires before onReady
- And getTreatment("flag_a") returns "on" (from cache) when onReadyFromCache fires

---

## Test 3 — Multiple clients from the same factory have independent events
**`multipleClientsFromSameFactoryHaveIndependentEvents`**

- Given a factory is built with a custom evaluations dispatcher
- When two clients are created for different targets (user_a, user_b)
- Then both onReady callbacks fire independently
- And client1 getTreatment("flag_a") returns "on" (RESPONSE_1 for user_a)
- And client2 getTreatment("flag_a") returns "off" (RESPONSE_2 for user_b)
- And when only user_b's evaluations change on the next poll
- Then onUpdate fires on client2 but NOT on client1 (event isolation)

---

## Test 4a — SDK emits update when evaluations change (POLLING)
**`sdkEmitsUpdateWhenEvaluationsChangePolling`**

- Given the SDK is configured in POLLING mode with a 1-second refresh rate
- And the first evaluations call returns RESPONSE_1 (flag_a=on)
- And the second evaluations call returns RESPONSE_2 (flag_a=off)
- When a client reaches onReady
- Then onReady fires with flag_a == "on"
- And onUpdate fires on the next poll cycle
- And getTreatment("flag_a") returns "off" after the update
- And the update metadata type is FLAGS_UPDATE

---

## Test 4b — SDK emits update when evaluations change (STREAMING)
**`sdkEmitsUpdateWhenEvaluationsChangeStreaming`**

- Given the SDK is configured in STREAMING mode
- And auth returns PUSH_ENABLED with a valid streaming JWT
- And the first evaluations call returns RESPONSE_1 (flag_a=on)
- And subsequent evaluations calls return RESPONSE_2 (flag_a=off)
- And the SSE endpoint delivers an EVALUATION_UPDATE event 2 seconds after connection
- When a client reaches onReady
- Then onReady fires with flag_a == "on"
- And after the SSE event triggers a re-fetch, onUpdate fires
- And getTreatment("flag_a") returns "off" after the update

---

## Test CP1 — Control PAUSED keeps the SSE socket open and falls back to polling
**`controlPausedKeepsSocketOpenAndFallsBackToPolling`**

- Given the SDK is in STREAMING mode with a 1-second fallback polling rate
- And auth returns PUSH_ENABLED and the client reaches onReady with flag_a == "on"
- When the server sends STREAMING_PAUSED over the open SSE connection
- Then the SSE socket is NOT re-opened (sseConnectionCount stays 1)
- And fallback polling becomes live and drives onUpdate when evaluations change to RESPONSE_2
- And getTreatment("flag_a") returns "off" after the update
- And evaluation requests keep growing across a poll window (polling is live)

---

## Test CP2 — Control RESUMED stops polling and resumes push over the same socket
**`controlResumedStopsPollingAndResumesPushOverSameSocket`**

- Given the SDK is in STREAMING mode and has fallen back to polling after STREAMING_PAUSED
- And polling is confirmed live during the control pause
- When STREAMING_RESUMED arrives over the same SSE connection
- Then no new SSE connection is opened (sseConnectionCount stays 1)
- And fallback polling stops (no further fetches beyond the catch-up refetch)
- And a subsequent SSE EVALUATION_UPDATE still drives onUpdate (push is live again)
- And getTreatment("flag_a") returns "off" after the update

---

## Test CP3 — Paused survives background/foreground, then RESUMED stops polling
**`pausedSurvivesBackgroundForegroundThenResumeStopsPolling`**

- Given the SDK is in STREAMING mode, control-paused and polling in the foreground
- When the app backgrounds, then no SSE reconnect occurs and polling pauses
- And when the app foregrounds, the SSE socket reconnects but stays non-processing (still control-paused, no push re-enable)
- And polling resumes while still control-paused
- When STREAMING_RESUMED arrives over the reconnected socket
- Then polling stops — the two-axis consistency guarantee across lifecycle transitions

---

## Test 4c — SINGLE_SYNC mode never emits update
**`sdkInSingleSyncModeDoesNotEmitUpdate`**

- Given the SDK is configured in SINGLE_SYNC mode
- And auth returns PUSH_DISABLED and evaluations returns RESPONSE_1
- When a client reaches onReady
- Then getTreatment("flag_a") returns "on"
- And onUpdate never fires (no background refresh in SINGLE_SYNC)
- And getTreatment("flag_a") still returns "on" after the wait

---

## Test 4 — SDK emits timeout when ready conditions are not met
**`sdkEmitsTimeoutWhenReadyConditionsNotMet`**

- Given the SDK is configured with a 1-second ready timeout
- And the evaluations endpoint never responds (simulated by a 60-second delay)
- When a client is obtained and a listener registered
- Then onTimeout fires within the test's grace period
- And onReady never fires

---

## Test 5 — setTarget switches evaluation context
**`clientSetTargetSwitchesEvaluationContext`**

- Given a client is created with target user_1 and reaches ready with RESPONSE_1
- When setTarget is called with target user_2
- Then the SDK fetches evaluations for user_2
- And the evaluations request includes user=user_2 in the request body
- And getTreatment("flag_a") returns "off" (RESPONSE_2) for the new target

---

## Test 5b — setTarget evicts previous key from polling
**`setTargetEvictsPreviousKeyFromPolling`**

- Given a client is created with target user_1 and reaches ready with RESPONSE_1
- And the SDK is in POLLING mode with a 1-second refresh rate
- When setTarget is called with target user_2
- Then periodic polling no longer fetches evaluations for user_1 (orphan evicted)
- And periodic polling continues to fetch evaluations for user_2 (active key)

---

## Test 5c — setTarget with a new matchingKey triggers a fresh JWT fetch
**`setTargetRefreshesJwtForNewMatchingKey`**

- Given a client is created with target user_1 and reaches ready
- When setTarget is called with a different matchingKey (user_2)
- Then the auth endpoint is called a second time (JWT invalidated and refreshed for new key)

---

## Test 5d — setTarget does not leave manager.flagNames empty
**`setTargetDoesNotEmptyManagerFlagNames`**

- Given a client is created with target user_1 and reaches ready
- When setTarget is called with target user_2
- Then factory.getManager().flagNames is non-empty after the target switch

---

## Test 6 — Concurrent evaluation fetches for the same target are deduped
**`concurrentEvaluationFetchesAreDeduped`**

- Given a client is ready on user_1
- And the evaluations endpoint holds the user_2 response for 2 seconds
- When setTarget(user_2) is called from 5 threads simultaneously
- Then only 1 evaluations request for user_2 reaches the server
- And onUpdate fires once
- And getTreatment("flag_a") returns "off" (RESPONSE_2)

---

## Test 7 — Auth token is refreshed after evaluations returns 401
**`authTokenIsRefreshedAfterEvaluations401`**

- Given the SDK is configured in POLLING mode with a 1-second refresh rate
- And the first evaluations call returns RESPONSE_1 successfully (onReady fires)
- And the second evaluations call returns HTTP 401 (token expired)
- And a fresh auth token is available for re-authentication
- And the retry evaluations call (immediately after re-auth) returns RESPONSE_2
- When the second poll cycle occurs
- Then the auth endpoint is called a second time (token refresh)
- And onUpdate fires with the new evaluations
- And getTreatment("flag_a") returns "off" (RESPONSE_2)

---

## Test 7b — Auth 401 switches runtime streaming to SINGLE_SYNC
**`auth401DuringCredentialRefreshStopsStreamingRuntimeSync`**

- Given the SDK starts in STREAMING mode
- And the initial auth and evaluations requests succeed
- When the streaming on-open evaluation fetch receives HTTP 401 and forces credential refresh
- And that credential refresh returns HTTP 401 (bad SDK key)
- Then the SDK stops streaming and behaves like SINGLE_SYNC at runtime
- And lifecycle resume does not reconnect streaming or process queued SSE updates

---

## Test 8 — track and flush submit events
**`trackAndFlushSubmitEvents`**

- Given the SDK is ready
- When client.track("purchase", 99.0, mapOf("item" to "book")) is called
- And client.flush() is called
- Then the events endpoint receives a POST containing the tracked event
- And the event body includes eventTypeId="purchase", value=99.0, key="user_a"

---

## Test 9 — destroy flushes events before tearing down
**`destroyFlushesEventsBeforeTeardown`**

- Given the SDK is ready
- And an event has been tracked but not yet flushed
- When factory.destroy() is called
- Then the events endpoint receives a POST containing the tracked event before destroy returns

---

## Test 10 — SDK delays fetch after SSE update with hashing params
**`sdkDelaysFetchAfterSseUpdateWithHashingParams`**

- Given the SDK is in STREAMING mode and receives an SSE EVALUATION_UPDATE with hashing params
- When the delay is computed by DefaultSyncDelayCalculator for "user_a"
- Then the evaluations re-fetch happens no sooner than sseTs + expectedDelay (±500ms tolerance)

---

## Test 10b — Streaming start is deferred while app is backgrounded
**`streamingStartAfterInitialSyncWaitsForForeground`**

- Given the SDK is in STREAMING mode and the initial evaluations fetch is still in flight
- When the app goes to background before initial sync completes
- Then onReady can fire from the completed initial sync
- But streaming does not connect until the app returns to foreground

---

## Test 11 — Streaming connection pauses and resumes on lifecycle
**`streamingConnectionPausesAndResumesOnLifecycle`**

- Given the SDK is in STREAMING mode
- When the app goes to background (CREATED) then foreground (RESUMED)
- Then the SSE connection is closed on pause and re-established on resume
- And evaluations are re-fetched after reconnect

---

## Test 12 — Polling scheduler pauses and resumes on lifecycle
**`pollingSchedulerPausesAndResumesOnLifecycle`**

- Given the SDK is in POLLING mode with a 1-second refresh rate
- When the app goes to background (CREATED)
- Then no evaluation fetches occur during the pause
- And when the app comes to foreground (RESUMED), polling resumes and onUpdate fires

---

## Test 13 — Events periodic posting pauses and resumes on lifecycle
**`eventsPeriodicPostingPausesAndResumesOnLifecycle`**

- Given the SDK is in POLLING mode and an event has been tracked
- When the app goes to background (CREATED)
- Then no events are posted during the pause
- And when the app returns to foreground (RESUMED), the events can be flushed

---

## Test 14 — SINGLE_SYNC mode: lifecycle transitions are no-ops
**`singleSyncModeLifecycleTransitionsAreNoOps`**

- Given the SDK is in SINGLE_SYNC mode
- When the app cycles through background and foreground
- Then no additional evaluation fetches are triggered
- And the flag treatment remains unchanged

---

## Test 15 — Delayed PUSH notification fetches cancelled on background
**`delayedPushFetchCancelledOnBackground`**

- Given the SDK is in STREAMING mode and receives a PUSH notification with delay params
- When the app goes to background before the delayed fetch executes
- Then the delayed fetch is cancelled and does not execute
- And when the app returns to foreground, evaluations can be re-fetched normally

---

## Test 16a — configsEnabled=true sends evaluatorWithConfigs capability on auth
**`configsEnabledTrueSendsEvaluatorWithConfigsCapabilityOnAuthRequest`**

- Given the SDK is configured with configsEnabled=true
- When the factory reaches onReady
- Then the auth request URL contains capabilities=evaluatorWithConfigs

---

## Test 16b — configsEnabled=true sends configs param on evaluations request
**`configsEnabledTrueSendsConfigsParamOnEvaluationsRequest`**

- Given the SDK is configured with configsEnabled=true
- When the factory reaches onReady
- Then the evaluations request body contains "configs":true

---

## Test 16c — configsEnabled=false sends evaluator capability on auth
**`configsEnabledFalseSendsEvaluatorCapabilityOnAuthRequest`**

- Given the SDK is configured with configsEnabled=false
- When the factory reaches onReady
- Then the auth request URL contains capabilities=evaluator
- And the auth request URL does not contain evaluatorWithConfigs

---

## Test 16d — configsEnabled=false does not send configs param on evaluations
**`configsEnabledFalseDoesNotSendConfigsParamOnEvaluationsRequest`**

- Given the SDK is configured with configsEnabled=false
- When the factory reaches onReady
- Then the evaluations request body contains "configs":false

---

## Test 17 — configsEnabled change across restarts clears cached evaluations
**`configsEnabledChangeAcrossRestartClearsCacheAndRefetches`**

- Given a factory has run with configsEnabled=false and populated the Room cache
- When a new factory is created with the same prefix but configsEnabled=true
- Then the evaluations request uses since=-1 (cache cleared)
- And the evaluations request body contains "configs":true
- And the treatment config field is non-null (config was returned and stored)

---

## Test — flagSets config sends sets param on evaluations request
**`flagSetsConfigSendsSetsParamOnEvaluationsRequest`**

- Given the SDK is configured with flagSets={"set_b", "set_a"}
- When the factory reaches onReady
- Then the evaluations request body contains "sets":["set_a","set_b"] (alphabetically sorted)

---

## Test — no flagSets config sends empty sets param on evaluations request
**`noFlagSetsConfigDoesNotSendSetsParamOnEvaluationsRequest`**

- Given the SDK is configured with no flagSets filter
- When the factory reaches onReady
- Then the evaluations request body contains "sets":[] (empty array)

---

## Test — auth request uses repeated key= params for multiple clients
**`authRequestUsesRepeatedUsersParamsForMultipleClients`**

- Given two clients are created for different targets (user_a, user_b)
- When both reach ready (causing the auth token to be fetched with both users)
- Then the auth request URL contains two separate key= params (e.g. key=user_a&key=user_b)
- And the params appear as separate repeated entries, not a composite value (e.g. key=user_a%2Cuser_b)

---

## Test A — invalid SDK key → no requests
**`blankSdkKeyDoesNotSendAnyRequests`**

- Given a factory is built with a blank SDK key ("")
- When the factory is created and 2 seconds elapse
- Then no evaluation or auth requests are sent to the server
- And SDK_READY never fires

---

## Test B — setTarget with invalid matchingKey → no evaluation requests fired
**`setTargetWithBlankMatchingKeyDoesNotFireEvaluationRequest`**

- Given the SDK is ready with a valid key and target
- When setTarget is called with a blank matchingKey ("")
- Then no new evaluation requests are sent to the server

---

## CDN Bypass — fetches with till param after stale CDN responses
**`cdnBypassFetchesWithTillParamAfterStaleCdnResponses`**

- Given the SDK is in STREAMING mode and has received initial evaluations (flag_a=on, till=1000)
- When an SSE update arrives with changeNumber=2000
- And the CDN serves stale responses (till=1000) for the first 10 retries
- And on the 11th attempt (with till=2000 query param) the server returns fresh data (flag_a=off)
- Then onUpdate fires and flag_a returns "off"

---

## CDN Bypass — stops retrying once fresh data received
**`cdnBypassStopsRetryingOnceFreshDataReceived`**

- Given the SDK is in STREAMING mode
- When an SSE update arrives with changeNumber=2000
- And the first retry already returns fresh data (till=2000)
- Then onUpdate fires after just one retry — no bypass needed

---

## Test — Destroying one of several clients keeps sync alive for the rest
**`destroyingOneClientKeepsSyncAliveForRemainingClients`**

- Given the SDK is in POLLING mode with a 1-second refresh rate
- And two clients are created for different targets (user_a, user_b) and both reach onReady
- When client_a is destroyed via client.destroy()
- Then periodic polling continues to fetch evaluations for user_b
- And no polling/auth request is made for user_a after destroy (its target was deregistered)
- And client_b still receives onUpdate when user_b evaluations change
- And client_a receives no further events (its EventManagerObserver was unregistered)

---

## Test — Destroying the last client stops streaming (onTargetsEmpty)
**`destroyingLastClientStopsStreaming`**

- Given the SDK is in STREAMING mode with an established SSE connection
- And a single default-target client has reached onReady
- When the client is destroyed via client.destroy()
- Then the SSE connection is closed (streaming stopAll)
- And no streaming reconnect occurs on a subsequent app foreground (lifecycle dormant)
- And a queued SSE EVALUATION_UPDATE delivered after destroy does not trigger a re-fetch

---

## Test — Destroying the last client stops polling (onTargetsEmpty)
**`destroyingLastClientStopsPolling`**

- Given the SDK is in POLLING mode with a 1-second refresh rate
- And a single default-target client has reached onReady
- When the client is destroyed via client.destroy()
- Then no further evaluation fetches occur after destroy
- And no polling resumes on a subsequent app foreground (lifecycle dormant)

---

# iOS Parity Analysis

Comparison of Android `TEST_CASES.md` against iOS E2E tests in `ios-thin-client/SplitThinTests/E2E/`.

## Coverage Status

| Android Test | iOS E2E Equivalent | Status |
|---|---|---|
| Test 1 — `sdkInitializesAndReachesReady` | `testSdkReadyFiresOnSuccessfulFetch` | ✅ covered |
| `evaluationsRequestIncludesContentDigest` | `SecureHttpClientTests` (unit only) | ⚠️ unit only, no E2E |
| Test 2 — `sdkEmitsReadyFromCacheAndServesCachedEvaluations` | — | ❓ iOS may not have persistence layer |
| Test 3 — `multipleClientsFromSameFactoryHaveIndependentEvents` | `testMulticlientEventsAreIsolated` | ✅ covered |
| Test 4a — `sdkEmitsUpdateWhenEvaluationsChangePolling` | `testPollingUpdatesClientTreatments` | ✅ covered |
| Test 4b — `sdkEmitsUpdateWhenEvaluationsChangeStreaming` | `testStreamingPushTriggersEvaluationUpdateAndOnUpdate` | ✅ covered |
| Test CP1 — `controlPausedKeepsSocketOpenAndFallsBackToPolling` | — | ❌ missing |
| Test CP2 — `controlResumedStopsPollingAndResumesPushOverSameSocket` | — | ❌ missing |
| Test CP3 — `pausedSurvivesBackgroundForegroundThenResumeStopsPolling` | — | ❌ missing |
| Test 4c — `sdkInSingleSyncModeDoesNotEmitUpdate` | `testSingleSyncOnlyFetchesOnce` | ✅ covered |
| Test 4 — `sdkEmitsTimeoutWhenReadyConditionsNotMet` | `testSdkReadyTimedOutWhenFetchFails` | ✅ covered |
| Test 5 — `clientSetTargetSwitchesEvaluationContext` | `testSetTargetTriggersFetch` | ✅ covered |
| Test 5b — `setTargetEvictsPreviousKeyFromPolling` | — | ❌ missing |
| Test 5c — `setTargetRefreshesJwtForNewMatchingKey` | — | ❌ missing |
| Test 5d — `setTargetDoesNotEmptyManagerFlagNames` | — | ❌ missing |
| Test 6 — `concurrentEvaluationFetchesAreDeduped` | `EvaluationFetchCoordinatorTests` (unit only) | ⚠️ unit only, no E2E |
| Test 7 — `authTokenIsRefreshedAfterEvaluations401` | `testAuth401OnEvaluationTriggersReauth` | ✅ covered |
| Test 7b — `auth401DuringCredentialRefreshStopsStreamingRuntimeSync` | — | ❌ missing |
| Test 8 — `trackAndFlushSubmitEvents` | `testTrackAndFlushSubmitsEventToBackend` | ✅ covered |
| Test 9 — `destroyFlushesEventsBeforeTeardown` | `testDestroyFlushesRemainingEvents` | ✅ covered |
| Test 10 — `sdkDelaysFetchAfterSseUpdateWithHashingParams` | `testStreamingPushAppliesStaggeredDelayBeforeFetch` | ✅ covered |
| Test 10b — `streamingStartAfterInitialSyncWaitsForForeground` | — | ❓ iOS lifecycle model differs |
| Test 11 — `streamingConnectionPausesAndResumesOnLifecycle` | `testStreamingPauseAndResume` | ✅ covered |
| Test 12 — `pollingSchedulerPausesAndResumesOnLifecycle` | — | ❓ iOS lifecycle model differs |
| Test 13 — `eventsPeriodicPostingPausesAndResumesOnLifecycle` | — | ❓ iOS lifecycle model differs |
| Test 14 — `singleSyncModeLifecycleTransitionsAreNoOps` | — | ❓ iOS lifecycle model differs |
| Test 15 — `delayedPushFetchCancelledOnBackground` | — | ❓ iOS lifecycle model differs |
| Test 16a — `configsEnabledTrueSendsEvaluatorWithConfigsCapabilityOnAuthRequest` | `CredentialFetcherTests` (unit only) | ⚠️ unit only, no E2E |
| Test 16b — `configsEnabledTrueSendsConfigsParamOnEvaluationsRequest` | `SecureHttpClientTests` (unit only) | ⚠️ unit only, no E2E |
| Test 16c — `configsEnabledFalseSendsEvaluatorCapabilityOnAuthRequest` | `CredentialFetcherTests` (unit only) | ⚠️ unit only, no E2E |
| Test 16d — `configsEnabledFalseDoesNotSendConfigsParamOnEvaluationsRequest` | `SecureHttpClientTests` (unit only) | ⚠️ unit only, no E2E |
| Test 17 — `configsEnabledChangeAcrossRestartClearsCacheAndRefetches` | — | ❓ iOS may not have persistence layer |
| `flagSetsConfigSendsSetsParamOnEvaluationsRequest` | `SecureHttpClientTests` (unit only) | ⚠️ unit only, no E2E |
| `noFlagSetsConfigDoesNotSendSetsParamOnEvaluationsRequest` | `SecureHttpClientTests` (unit only) | ⚠️ unit only, no E2E |
| `authRequestUsesRepeatedUsersParamsForMultipleClients` | `CredentialFetcherTests` (unit only) | ⚠️ unit only, no E2E |
| Test A — `blankSdkKeyDoesNotSendAnyRequests` | — | ❌ missing |
| Test B — `setTargetWithBlankMatchingKeyDoesNotFireEvaluationRequest` | — | ❌ missing |
| CDN bypass — `cdnBypassFetchesWithTillParamAfterStaleCdnResponses` | — | ❌ missing |
| CDN bypass — `cdnBypassStopsRetryingOnceFreshDataReceived` | — | ❌ missing |

## Gaps Summary

### Missing E2E Tests (need to be added to iOS)
- **5b** — `setTargetEvictsPreviousKeyFromPolling`
- **5c** — `setTargetRefreshesJwtForNewMatchingKey`
- **5d** — `setTargetDoesNotEmptyManagerFlagNames`
- **7b** — `auth401DuringCredentialRefreshStopsStreamingRuntimeSync`
- **CP1** — `controlPausedKeepsSocketOpenAndFallsBackToPolling`
- **CP2** — `controlResumedStopsPollingAndResumesPushOverSameSocket`
- **CP3** — `pausedSurvivesBackgroundForegroundThenResumeStopsPolling`
- **Test A** — blank SDK key sends no requests
- **Test B** — blank matchingKey on setTarget sends no evaluation requests
- **CDN bypass** — both stale-CDN retry and early-exit cases

### Unit-Only Coverage (E2E would add confidence)
- Content-digest header on evaluations request
- Concurrent fetch deduplication (full stack)
- `configsEnabled` true/false — auth capability and evaluations body params
- `flagSets` param on evaluations request
- Multi-user repeated `key=` params on auth request

### Potentially N/A on iOS
Tests 10b, 12, 13, 14, 15 rely on Android `Lifecycle` (CREATED/RESUMED) events. iOS uses `UIApplication` background/foreground notifications — equivalent tests may exist under different names or may need to be written against the iOS lifecycle API.

---

# JavaScript Parity Analysis

Comparison of Android `TEST_CASES.md` against JavaScript thin client tests in `javascript-thin-client/__tests__/`.

**Note:** The JS repo has no dedicated E2E test directory. Coverage comes entirely from unit and integration tests (51 files, ~700+ cases). The table below maps Android E2E scenarios to the closest JS equivalents.

## Coverage Status

| Android Test | JS Test Equivalent | Status |
|---|---|---|
| Test 1 — `sdkInitializesAndReachesReady` | `splitFactory.test.ts` — factory init + onReady; `observability/integration.test.ts` — factory_init_completed | ⚠️ unit only |
| `evaluationsRequestIncludesContentDigest` | `contentDigest.test.ts`, `secureHttpClient.test.ts` | ⚠️ unit only |
| Test 2 — `sdkEmitsReadyFromCacheAndServesCachedEvaluations` | `persistentEvaluationStorage.test.ts`, `cacheValidator.test.ts`, `splitFactory.test.ts` (persistent storage) | ⚠️ unit only |
| Test 3 — `multipleClientsFromSameFactoryHaveIndependentEvents` | `clientManager.test.ts` — different keys; `splitFactory.test.ts` — multi-client | ⚠️ unit only |
| Test 4a — `sdkEmitsUpdateWhenEvaluationsChangePolling` | `evaluationPeriodicScheduler.test.ts`, `internalEventBridge.test.ts` — PERIODIC reason | ⚠️ unit only |
| Test 4b — `sdkEmitsUpdateWhenEvaluationsChangeStreaming` | `streamingManager.test.ts`, `streamingConnectionManager.test.ts` | ⚠️ unit only |
| Test CP1 — `controlPausedKeepsSocketOpenAndFallsBackToPolling` | `notificationManagerKeeper.test.ts` / `streamingConnectionManager.test.ts` (unit only) | ⚠️ unit only |
| Test CP2 — `controlResumedStopsPollingAndResumesPushOverSameSocket` | `notificationManagerKeeper.test.ts` / `streamingConnectionManager.test.ts` (unit only) | ⚠️ unit only |
| Test CP3 — `pausedSurvivesBackgroundForegroundThenResumeStopsPolling` | — | ❓ browser has no mobile lifecycle equivalent |
| Test 4c — `sdkInSingleSyncModeDoesNotEmitUpdate` | `syncManager.test.ts` — single_sync mode; `evaluationPeriodicScheduler.test.ts` — no repeat | ⚠️ unit only |
| Test 4 — `sdkEmitsTimeoutWhenReadyConditionsNotMet` | `splitFactory.test.ts` — timeout scheduling; `observability/integration.test.ts` — timeout_reached | ⚠️ unit only |
| Test 5 — `clientSetTargetSwitchesEvaluationContext` | `clientManager.test.ts` — target change; `observability/integration.test.ts` — target_switch_started/completed | ⚠️ unit only |
| Test 5b — `setTargetEvictsPreviousKeyFromPolling` | `evaluationPeriodicScheduler.test.ts` — target updates; `clientManager.test.ts` | ⚠️ unit only |
| Test 5c — `setTargetRefreshesJwtForNewMatchingKey` | `authProvider.test.ts` — invalidation + re-fetch | ⚠️ unit only |
| Test 5d — `setTargetDoesNotEmptyManagerFlagNames` | `splitManager.test.ts` | ⚠️ unit only |
| Test 6 — `concurrentEvaluationFetchesAreDeduped` | `evaluationFetchCoordinator.test.ts` — concurrent deduplication; `eventSubmissionCoordinator.test.ts` | ⚠️ unit only |
| Test 7 — `authTokenIsRefreshedAfterEvaluations401` | `auth/integration.test.ts` — 401 retry flow; `secureHttpClient.test.ts` — 401 re-auth | ⚠️ unit/integration only |
| Test 7b — `auth401DuringCredentialRefreshStopsStreamingRuntimeSync` | — | ❌ missing |
| Test 8 — `trackAndFlushSubmitEvents` | `eventsTrackerAndScheduler.test.ts`, `observability/integration.test.ts` — track_called + events_flush_triggered | ⚠️ unit only |
| Test 9 — `destroyFlushesEventsBeforeTeardown` | `observability/integration.test.ts` — flush_started/flush_completed during destroy | ⚠️ unit only |
| Test 10 — `sdkDelaysFetchAfterSseUpdateWithHashingParams` | `streamingConnectionManager.test.ts` — delay/retry logic | ⚠️ unit only |
| Test 10b — `streamingStartAfterInitialSyncWaitsForForeground` | — | ❓ browser has no mobile lifecycle equivalent |
| Test 11 — `streamingConnectionPausesAndResumesOnLifecycle` | `streamingManager.test.ts` — pause/resume; `syncManager.test.ts` | ⚠️ unit only |
| Test 12 — `pollingSchedulerPausesAndResumesOnLifecycle` | `evaluationPeriodicScheduler.test.ts` — pause/resume | ⚠️ unit only |
| Test 13 — `eventsPeriodicPostingPausesAndResumesOnLifecycle` | `eventsTrackerAndScheduler.test.ts` | ⚠️ unit only |
| Test 14 — `singleSyncModeLifecycleTransitionsAreNoOps` | `syncManager.test.ts` — single_sync mode | ⚠️ unit only |
| Test 15 — `delayedPushFetchCancelledOnBackground` | — | ❓ browser has no mobile lifecycle equivalent |
| Test 16a — `configsEnabledTrueSendsEvaluatorWithConfigsCapabilityOnAuthRequest` | `credentialFetcher.test.ts` | ⚠️ unit only |
| Test 16b — `configsEnabledTrueSendsConfigsParamOnEvaluationsRequest` | `secureHttpClient.test.ts` | ⚠️ unit only |
| Test 16c — `configsEnabledFalseSendsEvaluatorCapabilityOnAuthRequest` | `credentialFetcher.test.ts` | ⚠️ unit only |
| Test 16d — `configsEnabledFalseDoesNotSendConfigsParamOnEvaluationsRequest` | `secureHttpClient.test.ts` | ⚠️ unit only |
| Test 17 — `configsEnabledChangeAcrossRestartClearsCacheAndRefetches` | `cacheValidator.test.ts` — cache invalidation on filter change | ⚠️ unit only |
| `flagSetsConfigSendsSetsParamOnEvaluationsRequest` | `secureHttpClient.test.ts` | ⚠️ unit only |
| `noFlagSetsConfigDoesNotSendSetsParamOnEvaluationsRequest` | `secureHttpClient.test.ts` | ⚠️ unit only |
| `authRequestUsesRepeatedUsersParamsForMultipleClients` | `credentialFetcher.test.ts` | ⚠️ unit only |
| Test A — `blankSdkKeyDoesNotSendAnyRequests` | — | ❌ missing |
| Test B — `setTargetWithBlankMatchingKeyDoesNotFireEvaluationRequest` | — | ❌ missing |
| CDN bypass — `cdnBypassFetchesWithTillParamAfterStaleCdnResponses` | — | ❌ missing |
| CDN bypass — `cdnBypassStopsRetryingOnceFreshDataReceived` | — | ❌ missing |

## Gaps Summary

### Missing Tests (no coverage at any level)
- **7b** — `auth401DuringCredentialRefreshStopsStreamingRuntimeSync`
- **Test A** — blank SDK key sends no requests
- **Test B** — blank matchingKey on setTarget sends no evaluation requests
- **CDN bypass** — both stale-CDN retry and early-exit cases

### Unit-Only Coverage (no full-stack E2E)
All other Android test cases have some unit-level coverage in JS but no end-to-end test that exercises the full stack from factory initialization through real HTTP interactions. The JS test suite relies exclusively on mocked/stubbed collaborators.

### Potentially N/A on JavaScript
Tests 10b, 12, 13, 14, 15 depend on mobile app lifecycle (background/foreground). The browser equivalent would be `visibilitychange` / `Page Visibility API` events — whether JS implements pause/resume via those events should be verified, but they cannot be exercised in Jest unit tests.
