package io.split.client.thin.http

import io.split.client.thin.http.contracts.HttpMethod
import java.net.URI

data class HttpRequestDescriptor(
    val uri: URI,
    val method: HttpMethod,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
)
