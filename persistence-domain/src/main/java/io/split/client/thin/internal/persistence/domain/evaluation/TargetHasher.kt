package io.split.client.thin.internal.persistence.domain.evaluation

import io.split.client.thin.internal.evaluation.EvaluationKey
import java.security.MessageDigest

internal class TargetHasher {

    fun hash(evalKey: EvaluationKey): String {
        val keyPart = "${evalKey.key.matchingKey}:${evalKey.key.bucketingKey}"
        val attrsPart = evalKey.attributes.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value}" }
        val input = "$keyPart|$attrsPart"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
