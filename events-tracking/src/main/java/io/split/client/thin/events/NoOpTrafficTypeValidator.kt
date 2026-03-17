package io.split.client.thin.events

import io.split.android.client.tracker.TrafficTypeValidator

internal class NoOpTrafficTypeValidator : TrafficTypeValidator {
    override fun isValid(trafficTypeName: String?): Boolean = true
}
