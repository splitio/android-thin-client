package io.split.client.thin

import org.junit.Assert.assertTrue
import org.junit.Test

class SplitEventListenerTest {

    private val noOpClient = object : SplitClient {
        override fun getTreatment(flag: String, evaluationOptions: EvaluationOptions?) =
            EvaluationResult(flag, "control")

        override fun getTreatments(flags: List<String>, evaluationOptions: EvaluationOptions?) =
            flags.map { EvaluationResult(it, "control") }

        override fun getTreatmentsByFlagSets(
            flagSets: List<String>,
            evaluationOptions: EvaluationOptions?,
        ) = emptyList<EvaluationResult>()

        override suspend fun setTarget(target: Target) = Unit

        override fun setTargetAsync(target: Target, callback: SplitVoidCallback) = Unit

        override fun addEventListener(listener: SplitEventListener) = Unit

        override fun track(
            trafficType: String,
            eventType: String,
            value: Double?,
            properties: Map<String, Any?>?,
        ) = Unit

        override suspend fun destroy() = Unit

        override fun destroyAsync(callback: SplitVoidCallback) = Unit

        override suspend fun flush() = Unit

        override fun flushAsync(callback: SplitVoidCallback) = Unit
    }

    @Test
    fun `default listener callbacks are no-op`() {
        val listener = SplitEventListener()
        val readyMetadata = SdkReadyMetadata()
        val updateMetadata = SdkUpdateMetadata()

        listener.onReady(noOpClient, readyMetadata)
        listener.onReadyView(noOpClient, readyMetadata)
        listener.onReadyFromCache(noOpClient, readyMetadata)
        listener.onReadyFromCacheView(noOpClient, readyMetadata)
        listener.onUpdate(noOpClient, updateMetadata)
        listener.onUpdateView(noOpClient, updateMetadata)
    }

    @Test
    fun `overridden listener callback is invoked`() {
        var called = false
        val listener = object : SplitEventListener() {
            override fun onUpdate(client: SplitClient, metadata: SdkUpdateMetadata) {
                called = true
            }
        }

        listener.onUpdate(noOpClient, SdkUpdateMetadata())

        assertTrue(called)
    }
}
