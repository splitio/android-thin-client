package io.split.client.thin.internal

import io.split.client.thin.SplitClient
import io.split.client.thin.Target

class DefaultClientFactory : (Target) -> SplitClient {

    override fun invoke(p1: Target): SplitClient {
        return DefaultSplitClient()
    }
}
