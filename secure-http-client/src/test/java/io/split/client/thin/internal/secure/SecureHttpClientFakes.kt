package io.split.client.thin.internal.secure

import io.split.android.client.network.HttpResponse
import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.internal.auth.AuthProvider
import io.split.client.thin.internal.auth.JwtCredential
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

internal val testEvaluationsUrl = "https://api.split.io/v1/evaluations"
internal val testEventsUrl = "https://events.split.io/v1/events"
internal val testTelemetryUrl = "https://telemetry.split.io/v1/metrics"

internal val testDefaultTarget = EvaluationTarget(
    matchingKey = "user-1",
    bucketingKey = null,
    attributes = mapOf("plan" to "premium"),
)
internal val testDefaultFilters = EvaluationFilters(
    flagNames = setOf("flag-a", "flag-b"),
    flagSets = null,
)

internal fun makeClient(
    authProvider: FakeAuthProvider = FakeAuthProvider(),
    httpClient: FakeRetryableHttpClient = FakeRetryableHttpClient(),
    impressionsMode: Int? = null,
): Triple<DefaultSecureHttpClient, FakeAuthProvider, FakeRetryableHttpClient> = Triple(
    DefaultSecureHttpClient(
        authProvider = authProvider,
        retryableHttpClient = httpClient,
        defaultTarget = testDefaultTarget,
        evaluationsUrl = testEvaluationsUrl,
        eventsUrl = testEventsUrl,
        telemetryUrl = testTelemetryUrl,
        impressionsMode = impressionsMode,
    ),
    authProvider,
    httpClient,
)

internal class FakeAuthProvider(
    private val credential: JwtCredential = JwtCredential("default-token", 9999999L, false),
    private val credentialSequence: List<JwtCredential>? = null,
    val throwOnCredential: Throwable? = null,
) : AuthProvider<EvaluationTarget> {

    var lastCredentialTarget: EvaluationTarget? = null
        private set
    var invalidateCallCount = 0
        private set
    private var credentialCallIndex = 0

    override suspend fun credential(target: EvaluationTarget): JwtCredential {
        lastCredentialTarget = target
        throwOnCredential?.let { throw it }
        return credentialSequence?.getOrElse(credentialCallIndex++) { credential } ?: credential
    }

    override suspend fun invalidate(target: EvaluationTarget) {
        invalidateCallCount++
    }
}

internal class FakeRetryableHttpClient(
    private val statusCode: Int = 200,
    private val statusCodeSequence: List<Int>? = null,
    val throwOnExecute: Throwable? = null,
) : RetryableHttpClient {

    val requests = mutableListOf<HttpRequestDescriptor>()
    var lastCategory: RequestCategory? = null
        private set
    private var executeIndex = 0

    val lastRequest: HttpRequestDescriptor? get() = requests.lastOrNull()
    val executeCallCount: Int get() = requests.size

    override suspend fun execute(request: HttpRequestDescriptor, category: RequestCategory): HttpResponse {
        requests.add(request)
        lastCategory = category
        throwOnExecute?.let { throw it }
        val code = statusCodeSequence?.getOrElse(executeIndex++) { statusCode } ?: statusCode
        val response = mock(HttpResponse::class.java)
        `when`(response.getHttpStatus()).thenReturn(code)
        `when`(response.isCredentialsError()).thenReturn(code == 401)
        return response
    }
}

internal val HttpResponse.httpStatus: Int get() = getHttpStatus()
