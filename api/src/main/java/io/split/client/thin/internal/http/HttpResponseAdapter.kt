package io.split.client.thin.internal.http

import io.split.android.client.network.HttpResponse as AndroidHttpResponse
import io.split.client.thin.http.contracts.HttpResponse

internal class HttpResponseAdapter(
    private val delegate: AndroidHttpResponse
) : HttpResponse {

    override val isSuccess: Boolean
        get() = delegate.isSuccess

    override val httpStatus: Int
        get() = delegate.httpStatus

    override fun getData(): String? = delegate.data
}
