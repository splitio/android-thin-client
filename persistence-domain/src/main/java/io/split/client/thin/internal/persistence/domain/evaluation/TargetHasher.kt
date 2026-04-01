package io.split.client.thin.internal.persistence.domain.evaluation

import com.goncalossilva.murmurhash.MurmurHash3
import io.split.client.thin.internal.evaluation.EvaluationKey

internal data class HashedTarget(val keyHash: String, val attrsHash: String)

internal class TargetHasher {

    private val hasher = MurmurHash3()

    fun hash(evalKey: EvaluationKey): HashedTarget {
        val keyInput = "${evalKey.key.matchingKey}:${evalKey.key.bucketingKey}"
        val attrsInput = evalKey.attributes.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value}" }
        val keyHashArr = hasher.hash128x86(keyInput.toByteArray(Charsets.UTF_8))
        val attrsHashArr = hasher.hash128x86(attrsInput.toByteArray(Charsets.UTF_8))
        return HashedTarget(
            keyHash = "%08x%08x".format(keyHashArr[0].toLong() and 0xFFFFFFFFL, keyHashArr[1].toLong() and 0xFFFFFFFFL),
            attrsHash = "%08x%08x".format(attrsHashArr[0].toLong() and 0xFFFFFFFFL, attrsHashArr[1].toLong() and 0xFFFFFFFFL)
        )
    }
}
