package io.split.client.thin.http.contracts

interface HttpResponse {
    val isSuccess: Boolean
    val httpStatus: Int
    val headers: Map<String, List<String>>
    fun getData(): String?
}
