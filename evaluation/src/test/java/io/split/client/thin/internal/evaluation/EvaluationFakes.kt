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
) : SecureHttpClient {

    val fetchCalls = mutableListOf<Pair<EvaluationTarget, EvaluationFilters?>>()
    val lastFetchTarget: EvaluationTarget? get() = fetchCalls.lastOrNull()?.first
    val lastFetchFilters: EvaluationFilters? get() = fetchCalls.lastOrNull()?.second

    override suspend fun fetchEvaluations(target: EvaluationTarget, filters: EvaluationFilters?, changeNumber: Long): HttpResponse {
        fetchCalls.add(target to filters)
        throwOnFetch?.let { throw it }
        return FakeHttpResponse(statusCode, responseBody)
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
 */
class FakeEvaluationProvider(
    private val changeToReturn: EvaluationChange? = null,
    val throwOnFetch: Throwable? = null,
    private val returnNullChange: Boolean = false,
) : EvaluationProvider {

    data class FetchCall(val evalKey: EvaluationKey, val filters: EvaluationFilters?, val changeNumber: Long)
    val fetchCalls = mutableListOf<FetchCall>()

    override suspend fun fetch(evalKey: EvaluationKey, filters: EvaluationFilters?, changeNumber: Long): EvaluationChange? {
        fetchCalls.add(FetchCall(evalKey, filters, changeNumber))
        throwOnFetch?.let { throw it }
        if (returnNullChange) return null
        return changeToReturn ?: EvaluationChange(evalKey, -1L, emptyList())
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
class FakeEvaluationWriteStorage(private val upsertUpdated: Boolean = true) : EvaluationWriteStorage {
    val upsertCalls = mutableListOf<EvaluationChange>()
    val clearCalls = mutableListOf<EvaluationKey>()

    override fun upsert(change: EvaluationChange): UpsertResult {
        upsertCalls.add(change)
        return UpsertResult(updated = upsertUpdated, emptyList())
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
    override fun unregisterAll() = Unit
}

// FakeEvaluationFetchCoordinator
open class FakeEvaluationFetchCoordinator(
    private val fetchIfNeededResult: Boolean = true,
) : EvaluationFetchCoordinator {

    val fetchCalls = mutableListOf<Triple<EvaluationKey, EvaluationFilters?, FetchReason>>()
    val refetchAllCalls = mutableListOf<Pair<EvaluationFilters?, FetchReason>>()

    override suspend fun fetchIfNeeded(evalKey: EvaluationKey, filters: EvaluationFilters?, reason: FetchReason, delayMs: Long): Boolean {
        fetchCalls.add(Triple(evalKey, filters, reason))
        return fetchIfNeededResult
    }

    override suspend fun refetchAll(filters: EvaluationFilters?, reason: FetchReason, delayProvider: ((EvaluationKey) -> Long)?) {
        refetchAllCalls.add(filters to reason)
    }
}
