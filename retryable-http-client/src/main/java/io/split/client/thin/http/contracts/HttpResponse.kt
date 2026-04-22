package io.split.client.thin.http.contracts

interface HttpResponse {
    val isSuccess: Boolean
    val httpStatus: Int
    fun getData(): String?
}
