package io.split.client.thin.internal.evaluation

internal interface SyncDelayCalculator {
    fun calculateDelay(key: String, updateIntervalMs: Long?, algorithmSeed: Int?, hashingAlgorithm: Int?): Long
}
