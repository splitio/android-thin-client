package io.split.client.thin.internal

import io.split.client.thin.SplitClient
import io.split.client.thin.Target
import io.split.client.thin.events.EventTracker

internal class DefaultClientFactory : (Target) -> SplitClient {

    override fun invoke(target: Target): SplitClient {
        val eventTracker = EventTracker.create()
        return DefaultSplitClient(target, eventTracker.tracker)
    }
}
