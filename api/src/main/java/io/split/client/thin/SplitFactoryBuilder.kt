package io.split.client.thin

import io.split.client.thin.internal.AsyncBridge
import io.split.client.thin.internal.DefaultSplitFactory

/**
 * Builder for creating a [SplitFactory] instance.
 */
object SplitFactoryBuilder {

    /**
     * Creates a factory configured with the SDK key, default target and optional config.
     */
    @JvmStatic
    @JvmOverloads
    fun build(
        sdkKey: SdkKey,
        defaultTarget: Target,
        config: SplitClientConfig? = null,
    ): SplitFactory {
        return DefaultSplitFactory(
            sdkKey = sdkKey,
            defaultTarget = defaultTarget,
            config = config,
            asyncBridge = AsyncBridge(),
        )
    }
}
