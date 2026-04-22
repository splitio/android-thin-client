package io.split.client.thin.http.contracts

import java.net.URI

interface HttpClient {
    fun request(uri: URI, method: HttpMethod): HttpRequest
    fun request(uri: URI, method: HttpMethod, body: String?, headers: Map<String, String>): HttpRequest
}
