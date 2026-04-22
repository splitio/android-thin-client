package io.split.client.thin.internal.http

import io.split.android.client.network.HttpClient as AndroidHttpClient
import io.split.android.client.network.HttpMethod as AndroidHttpMethod
import io.split.client.thin.http.contracts.HttpClient
import io.split.client.thin.http.contracts.HttpMethod
import io.split.client.thin.http.contracts.HttpRequest
import java.net.URI

internal class HttpClientAdapter(
    private val delegate: AndroidHttpClient
) : HttpClient {

    override fun request(uri: URI, method: HttpMethod): HttpRequest {
        val androidMethod = method.toAndroidHttpMethod()
        return HttpRequestAdapter(delegate.request(uri, androidMethod))
    }

    override fun request(
        uri: URI,
        method: HttpMethod,
        body: String?,
        headers: Map<String, String>
    ): HttpRequest {
        val androidMethod = method.toAndroidHttpMethod()
        return HttpRequestAdapter(delegate.request(uri, androidMethod, body, headers))
    }

    private fun HttpMethod.toAndroidHttpMethod(): AndroidHttpMethod {
        return when (this) {
            HttpMethod.GET -> AndroidHttpMethod.GET
            HttpMethod.POST -> AndroidHttpMethod.POST
        }
    }
}
