package io.split.client.thin.internal.auth

interface AuthProvider {
    fun addTarget(target: String): Boolean  // returns true if target was new
    fun removeTarget(target: String): Boolean  // returns true if set is now empty
    suspend fun credential(): JwtCredential
    suspend fun credential(targets: Set<String>): JwtCredential
    suspend fun invalidateAll()
}
