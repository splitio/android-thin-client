package io.split.client.thin.internal.evaluation

import io.split.client.thin.http.contracts.HttpResponse
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.Observer
import io.split.client.thin.internal.secure.EvaluationFilters
import io.split.client.thin.internal.secure.EvaluationTarget
import io.split.client.thin.internal.secure.SecureHttpClient

// FakeSecureHttpClient
class FakeSecureHttpClient(
    private val responseBody: String? = "{}",
    private val statusCode: Int = 200,
    val throwOnFetch: Throwable? = null,
    private val responseQueue: ArrayDeque<String?> = ArrayDeque(),
) : SecureHttpClient {

    data class FetchEvaluationsCall(val target: EvaluationTarget, val filters: EvaluationFilters, val targetChangeNumber: Long?)

    val fetchCalls = mutableListOf<FetchEvaluationsCall>()
    val lastFetchTarget: EvaluationTarget? get() = fetchCalls.lastOrNull()?.target
    val lastFetchFilters: EvaluationFilters get() = fetchCalls.lastOrNull()?.filters ?: EvaluationFilters()
    val lastTargetChangeNumber: Long? get() = fetchCalls.lastOrNull()?.targetChangeNumber

    override suspend fun fetchEvaluations(target: EvaluationTarget, filters: EvaluationFilters, changeNumber: Long, targetChangeNumber: Long?): HttpResponse {
        fetchCalls.add(FetchEvaluationsCall(target, filters, targetChangeNumber))
        throwOnFetch?.let { throw it }
        val body = if (responseQueue.isNotEmpty()) responseQueue.removeFirst() else responseBody
        return FakeHttpResponse(statusCode, body)
    }

    override suspend fun postEvents(payload: String): HttpResponse = FakeHttpResponse(200, null)
    override suspend fun postTelemetry(payload: String): HttpResponse = FakeHttpResponse(200, null)
}

class FakeHttpResponse(
    private val status: Int,
    private val body: String?,
) : HttpResponse {
    override val isSuccess: Boolean = status in 200..299
    override val httpStatus: Int = status
    override fun getData(): String? = body
}

// FakeEvaluationProvider
/**
 * [changeToReturn] — when non-null, returns the given change; when null, returns a default
 *   non-null change unless [returnNullChange] is true.
 * [returnNullChange] — when true, fetch() returns null (simulates 304 / empty-body responses).
 * [responseQueue] — when non-empty, dequeues one response per call (may include nulls for 304);
 *   once exhausted, falls back to [changeToReturn]/[returnNullChange] behaviour.
 * [throwOnCallIndex] — throws [throwOnFetch] on the given 0-based call index (null = never).
 */
class FakeEvaluationProvider(
    private val changeToReturn: EvaluationChange? = null,
    val throwOnFetch: Throwable? = null,
    private val returnNullChange: Boolean = false,
    private val responseQueue: ArrayDeque<EvaluationChange?> = ArrayDeque(),
    private val throwOnCallIndex: Int? = null,
) : EvaluationProvider {

    data class FetchCall(val evalKey: EvaluationKey, val filters: EvaluationFilters, val changeNumber: Long, val targetChangeNumber: Long? = null)
    val fetchCalls = mutableListOf<FetchCall>()

    override suspend fun fetch(evalKey: EvaluationKey, filters: EvaluationFilters, changeNumber: Long, targetChangeNumber: Long?): EvaluationChange? {
        val callIndex = fetchCalls.size
        fetchCalls.add(FetchCall(evalKey, filters, changeNumber, targetChangeNumber))
        if (throwOnCallIndex != null && callIndex == throwOnCallIndex) throwOnFetch?.let { throw it }
        if (throwOnCallIndex == null) throwOnFetch?.let { throw it }
        if (responseQueue.isNotEmpty()) return responseQueue.removeFirst()
        if (returnNullChange) return null
        return changeToReturn ?: EvaluationChange(evalKey, changeNumber = -1L, evaluations = emptyList())
    }
}

// FakeEvaluationReadStorage
class FakeEvaluationReadStorage(
    private val storedEvaluations: MutableMap<Pair<String, EvaluationKey>, StoredEvaluation> = mutableMapOf(),
    private val changeNumbers: MutableMap<EvaluationKey, Long> = mutableMapOf(),
) : EvaluationReadStorage {

    override fun get(flag: String, evalKey: EvaluationKey): StoredEvaluation? =
        storedEvaluations[flag to evalKey]

    override fun get(flags: Set<String>, evalKey: EvaluationKey): Map<String, StoredEvaluation> =
        flags.mapNotNull { flag -> storedEvaluations[flag to evalKey]?.let { flag to it } }.toMap()

    override fun getByFlagSets(flagSets: Set<String>, evalKey: EvaluationKey): Map<String, StoredEvaluation> =
        storedEvaluations.filter { (key, stored) -> key.second == evalKey && stored.flagSets.any { it in flagSets } }
            .mapKeys { it.key.first }

    override fun getFlagNames(evalKey: EvaluationKey): Set<String> =
        storedEvaluations.keys.filter { it.second == evalKey }.map { it.first }.toSet()

    override fun getFlagNames(): Set<String> =
        storedEvaluations.keys.map { it.first }.toSet()

    override fun lastChangeNumber(evalKey: EvaluationKey): Long =
        changeNumbers[evalKey] ?: -1L

    override fun lastUpdateTimestamp(evalKey: EvaluationKey): Long? = null

    fun store(flag: String, evalKey: EvaluationKey, stored: StoredEvaluation) {
        storedEvaluations[flag to evalKey] = stored
    }

    fun setChangeNumber(evalKey: EvaluationKey, number: Long) {
        changeNumbers[evalKey] = number
    }
}

// FakeEvaluationWriteStorage
class FakeEvaluationWriteStorage(
    private val upsertUpdated: Boolean = true,
    private val changedFlagNamesPerCall: List<List<String>> = emptyList(),
) : EvaluationWriteStorage {
    val upsertCalls = mutableListOf<EvaluationChange>()
    val clearCalls = mutableListOf<EvaluationKey>()

    override fun upsert(change: EvaluationChange): UpsertResult {
        val idx = upsertCalls.size
        upsertCalls.add(change)
        val names = if (idx < changedFlagNamesPerCall.size) changedFlagNamesPerCall[idx] else emptyList()
        return UpsertResult(updated = upsertUpdated, names)
    }

    override fun clear(evalKey: EvaluationKey) {
        clearCalls.add(evalKey)
    }
}

// FakePersistenceBackedStorage
class FakePersistenceBackedStorage : PersistenceBackedStorage {
    val ensureCacheLoadedCalls = mutableListOf<EvaluationKey>()

    override suspend fun ensureCacheLoaded(evalKey: EvaluationKey) {
        ensureCacheLoadedCalls.add(evalKey)
    }
}

// FakeCompositeObserver
class FakeCompositeObserver : CompositeObserver {
    val capturedEvents = mutableListOf<ObservableEvent>()
    override fun notifyEvent(event: ObservableEvent) { capturedEvents.add(event) }
    override fun register(observer: Observer) = Unit
    override fun unregister(observer: Observer) = Unit
    override fun unregisterAll() = Unit
}

// FakeEvaluationFetchCoordinator
open class FakeEvaluationFetchCoordinator(
    private val fetchIfNeededResult: Boolean = true,
    private val knownKeysResult: Set<EvaluationKey> = emptySet(),
) : EvaluationFetchCoordinator {

    val fetchCalls = mutableListOf<Triple<EvaluationKey, EvaluationFilters, FetchReason>>()
    val refetchAllCalls = mutableListOf<Pair<EvaluationFilters, FetchReason>>()
    val forgetCalls = mutableListOf<EvaluationKey>()

    override fun fetchedKeys(): Set<EvaluationKey> = knownKeysResult

    override suspend fun fetchIfNeeded(evalKey: EvaluationKey, filters: EvaluationFilters, reason: FetchReason, delayMs: Long, targetChangeNumber: Long?): Boolean {
        fetchCalls.add(Triple(evalKey, filters, reason))
        return fetchIfNeededResult
    }

    override suspend fun refetchAll(
        filters: EvaluationFilters,
        reason: FetchReason,
        delayProvider: ((EvaluationKey) -> Long)?,
        keyFilter: (EvaluationKey) -> Boolean,
    ) {
        refetchAllCalls.add(filters to reason)
    }

    override fun forget(evalKey: EvaluationKey) {
        forgetCalls.add(evalKey)
    }
}
