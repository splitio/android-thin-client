package io.split.client.thin.internal

import io.split.client.thin.SplitManager

internal class DefaultSplitManager : SplitManager {
    override val flagNames: List<String> = listOf("hardcoded-flag-1", "hardcoded-flag-2")
}
