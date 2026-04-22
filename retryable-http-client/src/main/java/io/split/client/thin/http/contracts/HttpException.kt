package io.split.client.thin.http.contracts

class HttpException(
    message: String,
    val statusCode: Int? = null
) : Exception(message)
