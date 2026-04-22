package io.split.client.thin.internal.http

import io.split.android.client.network.HttpException as AndroidHttpException
import io.split.android.client.network.HttpRequest as AndroidHttpRequest
import io.split.client.thin.http.contracts.HttpException
import io.split.client.thin.http.contracts.HttpRequest
import io.split.client.thin.http.contracts.HttpResponse

internal class HttpRequestAdapter(
    private val delegate: AndroidHttpRequest
) : HttpRequest {

    override fun execute(): HttpResponse {
        return try {
            HttpResponseAdapter(delegate.execute())
        } catch (e: AndroidHttpException) {
            throw HttpException(
                e.message ?: "HTTP request failed",
                e.statusCode
            )
        }
    }
}
