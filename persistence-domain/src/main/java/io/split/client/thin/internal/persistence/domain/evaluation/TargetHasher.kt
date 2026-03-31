package io.split.client.thin.internal.persistence.domain.evaluation

import com.goncalossilva.murmurhash.MurmurHash3
import io.split.client.thin.internal.evaluation.EvaluationKey

internal class TargetHasher {

    private val hasher = MurmurHash3()

    fun hash(evalKey: EvaluationKey): String {
        val keyPart = "${evalKey.key.matchingKey}:${evalKey.key.bucketingKey}"
        val attrsPart = evalKey.attributes.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value}" }
        val input = "$keyPart|$attrsPart"
        val bytes = input.toByteArray(Charsets.UTF_8)
        val hash = hasher.hash128x86(bytes)
        return "%08x%08x".format(hash[0].toLong() and 0xFFFFFFFFL, hash[1].toLong() and 0xFFFFFFFFL)
    }
}
