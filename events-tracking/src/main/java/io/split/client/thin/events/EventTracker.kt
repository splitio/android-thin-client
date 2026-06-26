package io.split.client.thin.events

import io.split.android.client.tracker.DefaultTracker
import io.split.android.client.tracker.Tracker
import io.split.android.client.validators.EventValidatorImpl
import io.split.android.client.validators.KeyValidatorImpl
import io.split.android.client.validators.PropertyValidatorImpl

class EventTracker internal constructor(
    val tracker: Tracker,
) {
    companion object {
        fun create(onEventPush: DefaultTracker.OnEventPush = DefaultTracker.OnEventPush { _ ->
            // no-op until storage is implemented
        }
        ): EventTracker {
            val logger = TrackerLoggerImpl()
            val keyValidator = KeyValidatorImpl()
            val trafficTypeValidator = NoOpTrafficTypeValidator()
            val eventValidator = EventValidatorImpl(keyValidator, trafficTypeValidator)
            val propertyValidator = PropertyValidatorImpl(logger)
            val tracker = DefaultTracker(
                eventValidator,
                logger,
                propertyValidator,
                onEventPush,
                null,
                null,
            )
            return EventTracker(tracker)
        }
    }
}
