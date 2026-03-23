package io.split.client.thin.internal.auth

internal data class TestTarget(val key: String) : AuthParamsProvider {
    override fun getUsers(): String = key
}
