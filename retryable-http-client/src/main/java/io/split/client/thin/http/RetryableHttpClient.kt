package io.split.client.thin.http

import io.split.client.thin.http.contracts.HttpResponse

fun interface RetryableHttpClient {

    suspend fun execute(request: HttpRequestDescriptor, category: RequestCategory): HttpResponse
}
