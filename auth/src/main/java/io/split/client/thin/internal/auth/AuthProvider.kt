package io.split.client.thin.internal.auth

interface AuthProvider<T : AuthParamsProvider> {
    suspend fun credential(target: T): JwtCredential
    suspend fun invalidate(target: T)
}
