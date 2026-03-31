package io.split.client.thin.internal.auth

interface AuthProvider<T : AuthParamsProvider> {
    fun addTarget(target: T): Boolean  // returns true if target was new
    fun removeTarget(target: T): Boolean  // returns true if set is now empty
    suspend fun credential(): JwtCredential
    suspend fun credential(targets: Set<T>): JwtCredential
    suspend fun invalidateAll()
}
