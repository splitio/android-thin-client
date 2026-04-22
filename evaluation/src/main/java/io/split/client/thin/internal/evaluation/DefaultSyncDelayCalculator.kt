package io.split.client.thin.internal.evaluation

import com.goncalossilva.murmurhash.MurmurHash3

class DefaultSyncDelayCalculator : SyncDelayCalculator {

    companion object {
        private const val DEFAULT_INTERVAL_MS = 60_000L
        private const val HASHING_NONE = 0
    }

    override fun calculateDelay(key: String, updateIntervalMs: Long?, algorithmSeed: Int?, hashingAlgorithm: Int?): Long {
        if (hashingAlgorithm == null || hashingAlgorithm == HASHING_NONE) {
            return 0L
        }

        val intervalMs = if (updateIntervalMs == null || updateIntervalMs <= 0) DEFAULT_INTERVAL_MS else updateIntervalMs
        val seed = algorithmSeed ?: 0

        val hash = MurmurHash3(seed.toUInt()).hash32x86(key.toByteArray(Charsets.UTF_8))
        return (hash.toLong() and 0xFFFFFFFFL) % intervalMs
    }
}
