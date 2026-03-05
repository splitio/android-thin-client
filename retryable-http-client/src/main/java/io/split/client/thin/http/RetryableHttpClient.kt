package io.split.client.thin.http

import io.split.android.client.network.HttpResponse

fun interface RetryableHttpClient {

    suspend fun execute(request: HttpRequestDescriptor, category: RequestCategory): HttpResponse
}
