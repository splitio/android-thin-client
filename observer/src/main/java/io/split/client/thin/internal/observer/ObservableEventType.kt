package io.split.client.thin.internal.observer

object ObservableEventType {
    // Existing SDK events
    const val EVAL_STORAGE_UPDATED = "eval_storage_updated"
    const val SDK_READY_TIMEOUT_REACHED = "sdk_ready_timeout_reached"
    const val EVALUATIONS_UPDATED = "evaluations_updated"

    // Persistence events
    const val EVAL_STORAGE_LOAD_STARTED = "eval_storage_load_started"
    const val EVAL_STORAGE_LOAD_SUCCEEDED = "eval_storage_load_succeeded"
    const val EVAL_STORAGE_LOAD_FAILED = "eval_storage_load_failed"
    const val EVAL_STORAGE_WRITE_SCHEDULED = "eval_storage_write_scheduled"
    const val EVAL_STORAGE_WRITE_SUCCEEDED = "eval_storage_write_succeeded"
    const val EVAL_STORAGE_WRITE_FAILED = "eval_storage_write_failed"
    const val EVENT_PUSHED = "event_pushed"
    const val EVENT_POPPED = "event_popped"
    const val PERSISTENCE_FAILED = "persistence_failed"

    // Lifecycle events
    const val FACTORY_INIT_STARTED = "factory_init_started"
    const val FACTORY_INIT_COMPLETED = "factory_init_completed"
    const val CLIENT_CREATED = "client_created"
    const val TARGET_SWITCH_STARTED = "target_switch_started"
    const val TARGET_SWITCH_COMPLETED = "target_switch_completed"
    const val DESTROY_STARTED = "destroy_started"
    const val DESTROY_COMPLETE = "destroy_complete"
    const val FLUSH_STARTED = "flush_started"
    const val FLUSH_COMPLETED = "flush_completed"
    const val FLUSH_FAILED = "flush_failed"
    const val EVALUATION_REQUESTED = "evaluation_requested"

    // Auth events
    const val JWT_REQUEST_STARTED = "jwt_request_started"
    const val JWT_RETURNED_FROM_STORAGE = "jwt_returned_from_storage"
    const val JWT_FETCH_STARTED = "jwt_fetch_started"
    const val JWT_FETCH_SUCCEEDED = "jwt_fetch_succeeded"
    const val JWT_FETCH_FAILED_RETRYABLE = "jwt_fetch_failed_retryable"
    const val JWT_FETCH_FAILED_NON_RETRYABLE = "jwt_fetch_failed_non_retryable"
    const val JWT_STORED = "jwt_stored"
    const val JWT_EXPIRED_OR_INVALID = "jwt_expired_or_invalid"

    // HTTP events
    const val HTTP_REQUEST_STARTED = "http_request_started"
    const val HTTP_REQUEST_SUCCEEDED = "http_request_succeeded"
    const val HTTP_REQUEST_FAILED_RETRYABLE = "http_request_failed_retryable"
    const val HTTP_REQUEST_FAILED_NON_RETRYABLE = "http_request_failed_non_retryable"
    const val HTTP_RETRY_EXHAUSTED = "http_retry_exhausted"

    // Evaluations sync events
    const val EVAL_FETCH_REQUESTED = "eval_fetch_requested"
    const val EVAL_FETCH_DEDUPED = "eval_fetch_deduped"
    const val EVAL_FETCH_STARTED = "eval_fetch_started"
    const val EVAL_FETCH_SUCCEEDED = "eval_fetch_succeeded"
    const val EVAL_FETCH_FAILED = "eval_fetch_failed"
    const val EVAL_DESERIALIZE_FAILED = "eval_deserialize_failed"
    const val EVAL_EMPTY_RESPONSE_BODY = "eval_empty_response_body"

    // Track events
    const val TRACK_CALLED = "track_called"
    const val TRACK_DROPPED = "track_dropped"
    const val EVENTS_FLUSH_TRIGGERED = "events_flush_triggered"
    const val EVENTS_POST_SUCCEEDED = "events_post_succeeded"
    const val EVENTS_POST_FAILED = "events_post_failed"

    // App lifecycle
    const val SYNC_PAUSED = "sync_paused"
    const val SYNC_RESUMED = "sync_resumed"
    const val STREAMING_PAUSED = "streaming_paused"
    const val STREAMING_RESUMED = "streaming_resumed"

    // Polling
    const val POLL_TRIGGER = "poll_trigger"

    // Streaming
    const val STREAMING_CONNECT_STARTED = "streaming_connect_started"
    const val STREAMING_CONNECTED = "streaming_connected"
    const val STREAMING_DISCONNECTED = "streaming_disconnected"
    const val STREAMING_NOTIFICATION_RECEIVED = "streaming_notification_received"
}
