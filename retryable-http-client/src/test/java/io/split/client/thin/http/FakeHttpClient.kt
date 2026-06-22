package io.split.client.thin.http

import io.split.client.thin.http.contracts.HttpClient
import io.split.client.thin.http.contracts.HttpException
import io.split.client.thin.http.contracts.HttpMethod
import io.split.client.thin.http.contracts.HttpRequest
import io.split.client.thin.http.contracts.HttpResponse
import java.net.URI

class FakeHttpRequest(
    private val onExecute: () -> HttpResponse
) : HttpRequest {
    var executeCallCount = 0

    override fun execute(): HttpResponse {
        executeCallCount++
        return onExecute()
    }
}

class FakeHttpResponse(
    override val isSuccess: Boolean,
    override val httpStatus: Int,
    private val data: String? = null
) : HttpResponse {
    override val headers: Map<String, List<String>> = emptyMap()
    override fun getData(): String? = data
}

class FakeHttpClient(
    private val requestBuilder: (uri: URI, method: HttpMethod, body: String?, headers: Map<String, String>) -> FakeHttpRequest
) : HttpClient {

    private var totalExecuteCalls = 0

    override fun request(uri: URI, method: HttpMethod): HttpRequest {
        val baseRequest = requestBuilder(uri, method, null, emptyMap())
        return CountingHttpRequest(baseRequest)
    }

    override fun request(
        uri: URI,
        method: HttpMethod,
        body: String?,
        headers: Map<String, String>
    ): HttpRequest {
        val baseRequest = requestBuilder(uri, method, body, headers)
        return CountingHttpRequest(baseRequest)
    }

    private inner class CountingHttpRequest(private val delegate: FakeHttpRequest) : HttpRequest {
        override fun execute(): HttpResponse {
            totalExecuteCalls++
            return delegate.execute()
        }
    }

    fun getTotalExecuteCalls(): Int = totalExecuteCalls
}
