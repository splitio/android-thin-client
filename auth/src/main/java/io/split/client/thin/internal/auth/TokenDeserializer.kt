package io.split.client.thin.internal.auth

internal fun interface TokenDeserializer {
    fun deserialize(json: String): JwtCredential
}
