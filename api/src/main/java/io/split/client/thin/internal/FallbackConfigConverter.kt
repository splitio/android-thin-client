package io.split.client.thin.internal

import io.split.client.thin.FallbackTreatmentsConfiguration
import io.split.android.client.fallback.FallbackTreatmentsConfiguration as SDKFallbackTreatmentsConfig

internal fun FallbackTreatmentsConfiguration.toInternal(): SDKFallbackTreatmentsConfig {
    val builder = SDKFallbackTreatmentsConfig.builder()
    global?.let {
        builder.global(io.split.android.client.fallback.FallbackTreatment(it.treatment, it.config))
    }
    val internalByFlag = byFlag.mapValues { (_, v) ->
        io.split.android.client.fallback.FallbackTreatment(v.treatment, v.config)
    }
    builder.byFlag(internalByFlag)
    return builder.build()
}
