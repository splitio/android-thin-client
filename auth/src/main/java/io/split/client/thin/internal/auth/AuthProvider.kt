package io.split.client.thin.internal.auth

interface AuthProvider<T : AuthParamsProvider> {
    suspend fun credential(targets: Set<T>): JwtCredential
    suspend fun invalidateAll(targets: Set<T>)
}
