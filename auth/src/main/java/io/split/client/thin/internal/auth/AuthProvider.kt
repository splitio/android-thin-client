package io.split.client.thin.internal.auth

internal interface AuthProvider<T : Any> {
    suspend fun credential(target: T): JwtCredential
    suspend fun invalidate(target: T)
}
