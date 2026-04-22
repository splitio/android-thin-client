package io.split.client.thin.http.contracts

interface HttpRequest {
    fun execute(): HttpResponse
}
